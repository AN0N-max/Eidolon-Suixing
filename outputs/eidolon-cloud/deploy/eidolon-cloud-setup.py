#!/usr/bin/env python3
"""Install/control the verified private framework. No model keys or public ports.

Run as the normal Ubuntu user, without sudo:
  python3 eidolon-cloud-setup.py install
  python3 eidolon-cloud-setup.py status
  python3 eidolon-cloud-setup.py start|stop|restart|logs
  python3 eidolon-cloud-setup.py service-install
  python3 eidolon-cloud-setup.py backup
  python3 eidolon-cloud-setup.py backup-check --backup SNAPSHOT_DIRECTORY
  python3 eidolon-cloud-setup.py restore --backup SNAPSHOT_DIRECTORY --confirm-sha256 HASH
Only system-package/service administration uses sudo; the runtime uses the current user.
"""

from __future__ import annotations

import argparse
import base64
import contextlib
import hashlib
import io
import json
import os
import re
import signal
import socket
import sqlite3
import stat
import subprocess
import sys
import time
import urllib.error
import urllib.request
import zipfile
from pathlib import Path, PurePosixPath
from uuid import UUID, uuid4

PACKAGE = "Eidolon-Cloud-0.1.0-dev1-source.zip"
SHA256 = "c24a498b83090f5d5f84de84d9ba60a4ec0a78b8afb6a4c0acca50ccdc9874be"
PACKAGE_COMMIT = "83578ebd87389f9cfb40ec7b7ba7005a579150f0"
API_URL = (
    "https://api.github.com/repos/AN0N-max/Eidolon-Suixing/contents/downloads/"
    + PACKAGE
    + "?ref="
    + PACKAGE_COMMIT
)
RAW_URL = (
    "https://raw.githubusercontent.com/AN0N-max/Eidolon-Suixing/"
    + PACKAGE_COMMIT
    + "/downloads/"
    + PACKAGE
)
VERSION = "0.1.0.dev1"
TOOL_VERSION = "2026.11.05.1"
SERVICE = "eidolon-cloud.service"
CLOUD_TABLES = {"cloud_meta", "cloud_devices", "cloud_diagnostic_jobs", "cloud_inbox"}


def file_digest(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def write_json_new(path: Path, value: dict) -> None:
    with path.open("x", encoding="utf-8") as output:
        json.dump(value, output, ensure_ascii=False, indent=2)
        output.flush()
        os.fsync(output.fileno())


def inspect_database(path: Path) -> dict:
    """Read-only schema, integrity and FK checks; never initialize an unknown DB."""
    if path.is_symlink() or not path.is_file():
        raise RuntimeError("Database must be an existing regular file")
    with contextlib.closing(
        sqlite3.connect(path.resolve().as_uri() + "?mode=ro", uri=True, timeout=5)
    ) as db:
        db.execute("PRAGMA trusted_schema=OFF")
        deadline = time.monotonic() + 30
        db.set_progress_handler(lambda: int(time.monotonic() > deadline), 10000)
        if db.execute("PRAGMA integrity_check").fetchall() != [("ok",)]:
            raise RuntimeError("Database integrity check failed")
        tables = {
            row[0]
            for row in db.execute(
                "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
            )
        }
        if tables != CLOUD_TABLES:
            raise RuntimeError("Not the supported cloud infrastructure database")
        metadata = dict(db.execute("SELECT key,value FROM cloud_meta"))
        if metadata.get("schema") != "1":
            raise RuntimeError("Unsupported cloud database schema; preserved")
        try:
            UUID(metadata["stream"])
        except (ValueError, KeyError):
            raise RuntimeError("Invalid cloud message stream") from None
        if db.execute("PRAGMA foreign_key_check").fetchone() is not None:
            raise RuntimeError("Database foreign key check failed")
        return {"schema": "1", "streamId": metadata["stream"]}


def copy_database(source: Path, target: Path, *, create: bool) -> None:
    """SQLite backup includes committed WAL pages; an interrupted copy rolls back."""
    if source.resolve() == target.resolve() or (
        source.exists() and target.exists() and source.samefile(target)
    ):
        raise RuntimeError("Backup source and destination must differ")
    if source.is_symlink() or target.is_symlink():
        raise RuntimeError("Database symlinks are not supported")
    if create:
        with target.open("xb"):
            pass
    elif not target.is_file():
        raise RuntimeError("Restore target must already exist")
    deadline = time.monotonic() + 30

    def progress(_status, _remaining, _total):
        if time.monotonic() > deadline:
            raise RuntimeError("Database copy timed out; inspect retained files")

    with contextlib.closing(
        sqlite3.connect(source.resolve().as_uri() + "?mode=ro", uri=True, timeout=5)
    ) as src:
        with contextlib.closing(
            sqlite3.connect(target.resolve().as_uri() + "?mode=rw", uri=True, timeout=5)
        ) as dst:
            src.backup(dst, pages=128, progress=progress, sleep=0.05)


def backup_database(source: Path, folder: Path, *, reason: str = "manual") -> Path:
    inspect_database(source)
    if folder.resolve() == source.parent.resolve() or source.resolve().is_relative_to(
        folder.resolve()
    ):
        raise RuntimeError("Backup directory must be separate from live data")
    folder.mkdir(parents=True, exist_ok=True, mode=0o700)
    snapshot = folder / (
        time.strftime("%Y%m%dT%H%M%S", time.gmtime()) + "Z-" + uuid4().hex[:12]
    )
    snapshot.mkdir(mode=0o700)
    database = snapshot / "database.sqlite3"
    copy_database(source, database, create=True)
    details = inspect_database(database)
    with database.open("r+b") as file:
        os.fsync(file.fileno())
    write_json_new(
        snapshot / "manifest.json",
        {
            "format": "eidolon-cloud-backup-v1",
            "toolVersion": TOOL_VERSION,
            "createdUtc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            "reason": reason,
            "database": "database.sqlite3",
            "sha256": file_digest(database),
            "bytes": database.stat().st_size,
            "packageSha256": SHA256,
            **details,
        },
    )
    return snapshot


def check_backup(folder: Path) -> dict:
    if folder.is_symlink() or (folder / "manifest.json").is_symlink():
        raise RuntimeError("Backup symlinks are not supported")
    manifest = json.loads((folder / "manifest.json").read_text(encoding="utf-8"))
    if (
        not isinstance(manifest, dict)
        or manifest.get("format") != "eidolon-cloud-backup-v1"
        or manifest.get("database") != "database.sqlite3"
        or manifest.get("packageSha256") != SHA256
    ):
        raise RuntimeError("Unsupported backup manifest")
    database = folder / "database.sqlite3"
    details = inspect_database(database)
    if (
        database.stat().st_size != manifest.get("bytes")
        or file_digest(database) != manifest.get("sha256")
        or any(manifest.get(key) != value for key, value in details.items())
    ):
        raise RuntimeError("Backup checksum or metadata mismatch")
    return manifest


def restore_database(
    source: Path, target: Path, backups: Path, confirmation: str
) -> dict:
    """Caller must hold the live runtime lock and require all service owners stopped."""
    manifest = check_backup(source)
    if confirmation != manifest["sha256"]:
        raise RuntimeError("Restore requires --confirm-sha256 from backup-check")
    if (source / "database.sqlite3").resolve() == target.resolve():
        raise RuntimeError("Cannot restore a database onto itself")
    inspect_database(target)
    # Freeze and recheck the chosen bytes before changing live data.
    staged = backups / ("restore-input-" + uuid4().hex)
    staged.mkdir(parents=True, mode=0o700)
    frozen = staged / "database.sqlite3"
    with (
        (source / "database.sqlite3").open("rb") as incoming,
        frozen.open("xb") as output,
    ):
        while part := incoming.read(1024 * 1024):
            output.write(part)
        output.flush()
        os.fsync(output.fileno())
    if file_digest(frozen) != confirmation:
        raise RuntimeError("Selected backup changed; live database preserved")
    inspect_database(frozen)
    before = backup_database(target, backups, reason="before-restore")
    copy_database(frozen, target, create=False)
    if inspect_database(target) != {
        key: manifest[key] for key in ("schema", "streamId")
    }:
        raise RuntimeError(
            "Restore verification failed; before-restore snapshot retained"
        )
    result = {
        "restored": True,
        "sourceSha256": confirmation,
        "beforeRestore": str(before),
        "serviceStarted": False,
    }
    write_json_new(staged / "receipt.json", result)
    return result


def unit_text(root: Path, data: Path, port: int, user: str) -> str:
    # Keep unit specifier/environment expansion impossible; legacy mode accepts more paths.
    for value in (str(root), str(data)):
        if not re.fullmatch(r"/[A-Za-z0-9_./-]+", value):
            raise RuntimeError(
                "systemd directories must be absolute simple Linux paths"
            )
    if not re.fullmatch(r"[a-z_][a-z0-9_-]*", user) or not 1 <= port <= 65535:
        raise RuntimeError("Invalid service user/port")
    source = root / "eidolon-cloud"
    return f"""# Managed by Eidolon cloud setup; no credentials in this file.
[Unit]
Description=Eidolon Suixing private cloud framework
After=network.target
StartLimitIntervalSec=60
StartLimitBurst=3

[Service]
Type=exec
User={user}
WorkingDirectory={source}
Environment=EIDOLON_CLOUD_DATA={data}
Environment=EIDOLON_CLOUD_HOST=127.0.0.1
Environment=EIDOLON_CLOUD_PORT={port}
Environment=EIDOLON_CLOUD_DIAGNOSTICS=0
Environment=PYTHONDONTWRITEBYTECODE=1
ExecStart={source}/.venv/bin/eidolon-cloud serve
Restart=on-failure
RestartSec=5s
TimeoutStopSec=20s
SendSIGKILL=no
UMask=0077
NoNewPrivileges=true
PrivateTmp=true
ProtectSystem=strict
ProtectHome=read-only
ReadWritePaths={data}
StandardOutput=journal
StandardError=journal
SyslogIdentifier=eidolon-cloud
LogRateLimitIntervalSec=30s
LogRateLimitBurst=100

[Install]
WantedBy=multi-user.target
"""


class ServiceManager:
    """Only adopt the exact unit generated for this user and this installation."""

    def __init__(self, setup, backups: Path):
        import pwd

        self.setup, self.backups = setup, backups
        self.user = pwd.getpwuid(os.getuid()).pw_name
        self.unit = Path("/etc/systemd/system") / SERVICE
        self.marker = setup.root / "service-manager.json"
        self.text = unit_text(setup.root, setup.data, setup.port, self.user)

    def command(self, *args: str, changing: bool = False, check: bool = True):
        command = (["sudo", "-n"] if changing else []) + [
            "systemctl",
            "--no-pager",
            *args,
        ]
        result = subprocess.run(
            command, check=False, text=True, capture_output=True, timeout=40
        )
        if check and result.returncode:
            detail = (
                result.stderr or result.stdout or "no controller error text"
            ).strip()[:1500]
            raise RuntimeError(
                f"systemctl {args[0]} failed ({result.returncode}): {detail}"
            )
        return result

    def state(self) -> dict:
        result = self.command(
            "show",
            SERVICE,
            "--property=LoadState,FragmentPath,DropInPaths,ActiveState,SubState,MainPID,UnitFileState,NeedDaemonReload,Result,ExecMainCode,ExecMainStatus",
            check=False,
        )
        if result.returncode and "LoadState=not-found" not in result.stdout:
            raise RuntimeError("Cannot query systemd; no fallback process started")
        return dict(
            line.split("=", 1) for line in result.stdout.splitlines() if "=" in line
        )

    def present(self) -> bool:
        return self.unit.exists() or self.unit.is_symlink() or self.marker.exists()

    def checked(self) -> dict:
        if (
            self.unit.is_symlink()
            or not self.unit.is_file()
            or self.unit.read_text(encoding="utf-8") != self.text
        ):
            raise RuntimeError(
                "Service unit missing or modified; preserved, no fallback"
            )
        state = self.state()
        if (
            state.get("LoadState") != "loaded"
            or state.get("FragmentPath") != str(self.unit)
            or state.get("DropInPaths")
            or state.get("NeedDaemonReload") != "no"
        ):
            raise RuntimeError("Service has unexpected origin/drop-ins; preserved")
        return state

    def verify_pid(self, state: dict) -> int:
        pid = int(state.get("MainPID", "0"))
        details = process_info(pid) if pid > 1 else None
        if details is None or not matches_runtime(*details, self.setup.source):
            raise RuntimeError("Service PID does not identify this framework")
        return pid

    def install(self) -> None:
        s = self.setup
        if not Path("/run/systemd/system").is_dir():
            raise RuntimeError("This host is not running systemd")
        if not s.dependencies_ok():
            raise RuntimeError("Install the existing framework dependencies first")
        subprocess.run(["sudo", "-n", "true"], check=True, timeout=10)
        if self.present():
            self.checked()
        else:
            state = self.state()
            if state.get("LoadState") != "not-found":
                raise RuntimeError("An existing service with this name was preserved")
        s.data.mkdir(parents=True, exist_ok=True, mode=0o700)
        candidate = s.root / ("service-candidate-" + uuid4().hex)
        candidate.mkdir(mode=0o700)
        file = candidate / SERVICE
        with file.open("x", encoding="utf-8", newline="\n") as output:
            output.write(self.text)
        subprocess.run(
            ["systemd-analyze", "verify", str(file)],
            check=True,
            capture_output=True,
            timeout=30,
        )
        database = s.data / "cloud-control.sqlite3"
        if database.exists():
            snapshot = backup_database(database, self.backups, reason="before-service")
            info("Verified pre-service backup: " + str(snapshot))
        if not self.present():
            # Exclusive creation; never overwrite a service installed by somebody else.
            code = "import os,sys; p=sys.argv[1]; f=open(p,'x',encoding='utf-8'); f.write(sys.stdin.read()); f.flush(); os.fsync(f.fileno()); f.close(); os.chmod(p,0o644)"
            subprocess.run(
                ["sudo", "-n", "/usr/bin/python3", "-c", code, str(self.unit)],
                input=self.text,
                text=True,
                check=True,
                timeout=15,
            )
        self.command("daemon-reload", changing=True)
        current = self.checked()
        old = s.owned_pid()
        if old is not None and old != int(current.get("MainPID", "0")):
            s.stop()
        if s.pidfile.exists():
            s.pidfile.rename(s.root / ("framework.pid.legacy-" + uuid4().hex))
        if not self.marker.exists():
            write_json_new(
                self.marker,
                {
                    "manager": "systemd",
                    "unit": SERVICE,
                    "unitSha256": digest(self.text.encode()),
                    "root": str(s.root),
                    "data": str(s.data),
                    "port": s.port,
                    "toolVersion": TOOL_VERSION,
                },
            )
        try:
            self.start()
            self.command("enable", SERVICE, changing=True)
        except (OSError, RuntimeError, subprocess.SubprocessError) as error:
            # Keep the validated unit and backups for retry; no legacy double-start.
            cleanup = "stop attempted"
            try:
                stopped = self.command("stop", SERVICE, changing=True, check=False)
                cleanup = (
                    "stop accepted"
                    if stopped.returncode == 0
                    else "stop failed; inspect status"
                )
            except (OSError, RuntimeError, subprocess.SubprocessError):
                cleanup = "stop could not be confirmed; inspect status"
            raise RuntimeError(
                f"systemd activation failed: {error}; {cleanup}; unit/backups retained"
            ) from None
        self.status(required=True)

    def start(self) -> None:
        initial = self.checked()
        if self.setup.owned_pid() is not None:
            raise RuntimeError(
                "Legacy process still exists; run service-install to finish migration"
            )
        # An inactive unit may be garbage-collected by systemd. ResetFailedUnit
        # need not load it, so a mandatory reset can fail before StartUnit runs.
        if initial.get("ActiveState") == "failed":
            self.command("reset-failed", SERVICE, changing=True)
        self.command("start", SERVICE, changing=True)
        last_error = "service has not reached active state"
        for _ in range(30):
            state = self.checked()
            if state.get("ActiveState") == "failed":
                break
            try:
                if state.get("ActiveState") == "active":
                    self.verify_pid(state)
                    self.setup.health()
                    return
            except (OSError, ValueError, RuntimeError) as error:
                last_error = str(error)
            time.sleep(0.5)
        raise RuntimeError(
            "Service readiness failed: " + last_error + "; state=" + json.dumps(state)
        )

    def stop(self) -> None:
        self.checked()
        self.command("stop", SERVICE, changing=True)
        self.require_stopped()
        info("systemd service stopped; source, database and backups retained")

    def require_stopped(self) -> None:
        if (
            self.checked().get("ActiveState") not in {"inactive", "failed"}
            or self.setup.owned_pid() is not None
        ):
            raise RuntimeError("Stop this framework before restoring data")

    def status(self, *, required: bool = False) -> dict:
        state = self.checked()
        running = state.get("ActiveState") == "active"
        if required and not running:
            raise RuntimeError("Service is not active")
        report = {
            "manager": "systemd",
            "toolVersion": TOOL_VERSION,
            "running": running,
            "pid": int(state.get("MainPID", "0")),
            "activeState": state.get("ActiveState"),
            "autostart": state.get("UnitFileState") == "enabled",
            "url": f"http://127.0.0.1:{self.setup.port}/api/health",
            "packageSha256": SHA256,
        }
        if running:
            self.verify_pid(state)
            report["health"] = self.setup.health()
            report["authenticationCheck"] = "401 without a device token"
        print(json.dumps(report, indent=2), flush=True)
        receipt = self.setup.root / ("service-receipt-" + uuid4().hex + ".json")
        write_json_new(receipt, report)
        return report

    def logs(self) -> None:
        self.checked()
        subprocess.run(
            [
                "sudo",
                "-n",
                "journalctl",
                "--no-pager",
                "--unit=" + SERVICE,
                "--lines=80",
                "--output=short-iso",
            ],
            check=True,
            timeout=15,
        )

    def disable(self) -> None:
        self.stop()
        self.command("disable", SERVICE, changing=True)
        info("Autostart disabled; unit, source, data and backups retained")


def info(message: str) -> None:
    print(time.strftime("[%H:%M:%S] ") + message, flush=True)


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def checked_package(data: bytes) -> bytes:
    if digest(data) != SHA256:
        raise RuntimeError("Source ZIP checksum mismatch; nothing will be extracted")
    return data


def get_package(target: Path, uploaded: Path | None = None) -> bytes:
    if uploaded is not None:
        return checked_package(uploaded.read_bytes())
    if target.exists():
        return checked_package(target.read_bytes())
    data = None
    for url, api in ((API_URL, True), (RAW_URL, False)):
        try:
            with urllib.request.urlopen(url, timeout=25) as response:
                content = response.read(1_000_001)
            if len(content) > 1_000_000:
                raise RuntimeError("Download exceeded size limit")
            data = checked_package(
                base64.b64decode(json.loads(content)["content"], validate=False)
                if api
                else content
            )
            break
        except (OSError, ValueError, KeyError, RuntimeError):
            info("Official download route failed; checking the other official route")
    if data is None:
        raise RuntimeError(
            "Download failed. Upload the verified ZIP and use --package PATH"
        )
    with target.open("xb") as output:
        output.write(data)
    return data


def extract_preserving(data: bytes, root: Path) -> Path:
    """Preflight the entire archive before adding files. Never overwrite local changes."""
    checked_package(data)
    writes: list[tuple[Path, bytes]] = []
    names: set[str] = set()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        if sum(entry.file_size for entry in archive.infolist()) > 20_000_000:
            raise RuntimeError("Unpacked size exceeded limit")
        for entry in archive.infolist():
            name = PurePosixPath(entry.filename)
            if (
                entry.filename in names
                or not name.parts
                or name.parts[0] != "eidolon-cloud"
                or ".." in name.parts
                or name.is_absolute()
                or "\\" in entry.filename
                or stat.S_ISLNK(entry.external_attr >> 16)
            ):
                raise RuntimeError("Unsafe or duplicate archive path")
            names.add(entry.filename)
            target = root.joinpath(*name.parts)
            if not target.resolve().is_relative_to(root.resolve()):
                raise RuntimeError("Archive target leaves install directory")
            if entry.is_dir():
                continue
            body = archive.read(entry)
            if target.exists():
                if not target.is_file() or target.read_bytes() != body:
                    raise RuntimeError(
                        "Existing source differs; preserved: " + entry.filename
                    )
            else:
                writes.append((target, body))
    for target, body in writes:
        target.parent.mkdir(parents=True, exist_ok=True)
        with target.open("xb") as output:
            output.write(body)
    return root / "eidolon-cloud"


def matches_runtime(args: list[str], cwd: Path, source: Path) -> bool:
    if not args or args[-1] != "serve" or cwd.resolve() != source.resolve():
        return False
    command = os.path.abspath(os.path.join(str(cwd), args[0]))
    expected = str(source / ".venv/bin/eidolon-cloud")
    # Python shebang launches can show the interpreter as argv[0].
    return command == expected or (len(args) > 1 and args[1] == expected)


def legacy_installer(args: list[str], cwd: Path, source: Path) -> bool:
    if not args:
        return False
    if Path(args[0]).name in {"bash", "sh"} and "-c" in args:
        return any(
            "dependencies.ready" in arg
            and "eidolon-cloud-dev1" in arg
            and "-m pip install" in arg
            for arg in args
        )
    return (
        cwd.resolve() == source.resolve()
        and len(args) > 3
        and args[1:4] == ["-m", "pip", "install"]
    )


def process_info(pid: int) -> tuple[list[str], Path] | None:
    proc = Path("/proc") / str(pid)
    try:
        if proc.stat().st_uid != os.getuid():
            return None
        args = [
            part.decode("utf-8", "replace")
            for part in (proc / "cmdline").read_bytes().split(b"\0")
            if part
        ]
        return args, (proc / "cwd").resolve(strict=True)
    except (OSError, ValueError):
        return None


class Setup:
    def __init__(self, root: Path, data: Path, port: int, index: str):
        self.root, self.data = root.resolve(), data.resolve()
        self.source = self.root / "eidolon-cloud"
        self.python = self.source / ".venv/bin/python"
        self.command = self.source / ".venv/bin/eidolon-cloud"
        self.pidfile = self.root / "framework.pid"
        self.log = self.root / "framework.log"
        self.port, self.index = port, index

    def owned_pid(self) -> int | None:
        if not self.pidfile.exists():
            return None
        try:
            pid = int(self.pidfile.read_text().strip())
            if pid <= 1:
                raise ValueError()
        except ValueError:
            raise RuntimeError("Invalid PID record; preserved for inspection") from None
        details = process_info(pid)
        if details is None:
            if (Path("/proc") / str(pid)).exists():
                raise RuntimeError("PID belongs to a different user; no action taken")
            return None
        if not matches_runtime(*details, self.source):
            raise RuntimeError("PID does not identify this framework; no action taken")
        return pid

    def wait_for_previous_install(self) -> None:
        last: list[int] = []
        deadline = time.monotonic() + 900
        while True:
            running = []
            for proc in Path("/proc").iterdir():
                if not proc.name.isdigit() or int(proc.name) == os.getpid():
                    continue
                details = process_info(int(proc.name))
                if details and legacy_installer(*details, self.source):
                    running.append(int(proc.name))
            if not running:
                return
            if running != last:
                info(
                    "Waiting for the previous installer, PID(s): "
                    + ",".join(map(str, running))
                )
                last = running
            if time.monotonic() >= deadline:
                raise RuntimeError(
                    "Previous installer is still active; left it running"
                )
            time.sleep(3)

    def dependencies_ok(self) -> bool:
        if not self.python.exists() or not self.command.exists():
            return False
        code = (
            "import importlib.metadata as m;"
            "assert m.version('eidolon-suixing-cloud')=='0.1.0.dev1';"
            "assert m.version('fastapi')=='0.143.0';"
            "assert m.version('uvicorn')=='0.54.0';"
            "from eidolon_cloud.app import create_app"
        )
        result = subprocess.run(
            [str(self.python), "-c", code], capture_output=True, timeout=30, check=False
        )
        return result.returncode == 0

    def install(self, package: Path | None) -> None:
        self.wait_for_previous_install()
        info("Checking the pinned source ZIP")
        source_zip = Path.home() / PACKAGE
        data = get_package(source_zip, package)
        extract_preserving(data, self.root)
        if self.dependencies_ok():
            info("Existing virtual environment is ready; skipping installation")
            return
        if self.owned_pid() is not None:
            raise RuntimeError("Stop this framework before changing its environment")
        ensurepip = subprocess.run(
            [sys.executable, "-c", "import ensurepip"], capture_output=True, check=False
        )
        if ensurepip.returncode:
            info("Installing Ubuntu's venv component")
            subprocess.run(["sudo", "-n", "apt-get", "update"], check=True)
            subprocess.run(
                [
                    "sudo",
                    "-n",
                    "env",
                    "DEBIAN_FRONTEND=noninteractive",
                    "apt-get",
                    "install",
                    "-y",
                    "python3-venv",
                ],
                check=True,
            )
        environment = self.source / ".venv"
        if not (environment / "pyvenv.cfg").exists():
            subprocess.run([sys.executable, "-m", "venv", str(environment)], check=True)
        wheel = (
            self.source / "artifacts/eidolon_suixing_cloud-0.1.0.dev1-py3-none-any.whl"
        )
        command = [
            str(self.python),
            "-m",
            "pip",
            "install",
            "--disable-pip-version-check",
            "--no-input",
            "--timeout",
            "25",
            "--retries",
            "1",
            "--index-url",
            self.index,
            "fastapi==0.143.0",
            "uvicorn==0.54.0",
            str(wheel),
        ]
        info("Installing framework dependencies into its own virtual environment")
        subprocess.run(command, check=True)
        subprocess.run([str(self.python), "-m", "pip", "check"], check=True)
        if not self.dependencies_ok():
            raise RuntimeError(
                "Installed environment did not pass its import/version check"
            )
        (self.root / "dependencies.ready").touch()

    def health(self) -> dict:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        with opener.open(
            f"http://127.0.0.1:{self.port}/api/health", timeout=2
        ) as response:
            result = json.load(response)
        if result.get("status") != "ok" or result.get("version") != VERSION:
            raise RuntimeError("Unexpected health response")
        try:
            with opener.open(f"http://127.0.0.1:{self.port}/api/inbox", timeout=2):
                raise RuntimeError("Unauthenticated inbox was not refused")
        except urllib.error.HTTPError as error:
            if error.code != 401:
                raise RuntimeError("Unexpected authentication check response") from None
        return result

    def status(self, *, required: bool = False) -> dict:
        pid = self.owned_pid()
        report = {
            "running": pid is not None,
            "pid": pid,
            "url": f"http://127.0.0.1:{self.port}/api/health",
            "source": str(self.source),
            "data": str(self.data),
            "packageSha256": SHA256,
            "autostart": False,
        }
        if pid is not None:
            report["health"] = self.health()
            report["authenticationCheck"] = "401 without a device token"
            try:
                memory = (Path("/proc") / str(pid) / "status").read_text()
                report["memory"] = next(
                    line.strip()
                    for line in memory.splitlines()
                    if line.startswith("VmRSS:")
                )
            except (OSError, StopIteration):
                pass
        elif required:
            raise RuntimeError("Framework is not running")
        print(json.dumps(report, ensure_ascii=False, indent=2), flush=True)
        if pid is not None:
            (self.root / "server-receipt.json").write_text(json.dumps(report, indent=2))
        return report

    def start(self) -> None:
        if self.owned_pid() is not None:
            info("Framework already running; no duplicate process started")
            self.status(required=True)
            return
        if not self.dependencies_ok():
            raise RuntimeError("Run install first")
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", self.port))
        self.data.mkdir(parents=True, exist_ok=True, mode=0o700)
        database = self.data / "cloud-control.sqlite3"
        if database.exists():
            with contextlib.closing(
                sqlite3.connect(database.as_uri() + "?mode=ro", uri=True)
            ) as db:
                version = db.execute(
                    "SELECT value FROM cloud_meta WHERE key='schema'"
                ).fetchone()
                if version is None or version[0] != "1":
                    raise RuntimeError(
                        "Existing cloud database is not supported; preserved"
                    )
        environment = dict(
            os.environ,
            EIDOLON_CLOUD_DATA=str(self.data),
            EIDOLON_CLOUD_HOST="127.0.0.1",
            EIDOLON_CLOUD_PORT=str(self.port),
            EIDOLON_CLOUD_DIAGNOSTICS="0",
        )
        with self.log.open("ab", buffering=0) as log:
            process = subprocess.Popen(
                [str(self.command), "serve"],
                cwd=self.source,
                env=environment,
                stdin=subprocess.DEVNULL,
                stdout=log,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        self.pidfile.write_text(str(process.pid))
        info("Started detached framework PID " + str(process.pid))
        for _ in range(30):
            if process.poll() is not None:
                raise RuntimeError("Framework exited; inspect framework.log")
            try:
                self.health()
                self.status(required=True)
                return
            except (OSError, ValueError, RuntimeError):
                time.sleep(0.5)
        raise RuntimeError(
            "Health check timed out; process/log retained for inspection"
        )

    def stop(self) -> None:
        pid = self.owned_pid()
        if pid is None:
            info("Framework already stopped; no other process touched")
            return
        # A pidfd keeps the signal tied to this process even if its numeric PID is reused.
        descriptor = os.pidfd_open(pid)
        try:
            if self.owned_pid() != pid:
                raise RuntimeError("Runtime identity changed; no signal sent")
            signal.pidfd_send_signal(descriptor, signal.SIGTERM)
        finally:
            os.close(descriptor)
        for _ in range(40):
            if process_info(pid) is None:
                info("Framework stopped; database, source and logs retained")
                return
            time.sleep(0.25)
        raise RuntimeError("Graceful stop is still pending; no forced kill was used")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "action",
        choices=(
            "install",
            "start",
            "status",
            "stop",
            "restart",
            "logs",
            "service-install",
            "service-disable",
            "check",
            "backup",
            "backup-check",
            "restore",
        ),
        nargs="?",
        default="install",
    )
    parser.add_argument("--root", type=Path, default=Path.home() / "eidolon-cloud-dev1")
    parser.add_argument("--data", type=Path, default=Path.home() / "eidolon-cloud-data")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--package", type=Path)
    parser.add_argument(
        "--backups", type=Path, default=Path.home() / "eidolon-cloud-backups"
    )
    parser.add_argument(
        "--backup", type=Path, help="An existing snapshot directory, never a live DB"
    )
    parser.add_argument(
        "--confirm-sha256", help="Exact SHA256 printed by backup-check; restore only"
    )
    parser.add_argument(
        "--version", action="version", version="Eidolon setup " + TOOL_VERSION
    )
    parser.add_argument(
        "--pip-index", default="https://pypi.tuna.tsinghua.edu.cn/simple"
    )
    args = parser.parse_args()
    os.umask(0o077)
    try:
        if sys.platform != "linux" or os.geteuid() == 0:
            raise RuntimeError(
                "Run with python3 as the normal Ubuntu user, without sudo"
            )
        home, root, data = (
            Path.home().resolve(),
            args.root.resolve(),
            args.data.resolve(),
        )
        if (
            root == home
            or data == home
            or not root.is_relative_to(home)
            or not data.is_relative_to(home)
            or root.is_relative_to(data)
            or data.is_relative_to(root)
            or not 1 <= args.port <= 65535
        ):
            raise RuntimeError(
                "Use separate install/data directories beneath the current home"
            )
        root.mkdir(parents=True, exist_ok=True, mode=0o700)
        backups = args.backups.resolve()
        if (
            backups == home
            or not backups.is_relative_to(home)
            or any(
                backups.is_relative_to(path) or path.is_relative_to(backups)
                for path in (root, data)
            )
        ):
            raise RuntimeError(
                "Use a separate backup directory beneath the current home"
            )
        if args.backup is not None and not args.backup.resolve().is_relative_to(home):
            raise RuntimeError("The chosen backup must stay beneath the current home")
        import fcntl

        with (root / "setup.lock").open("a+b") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            setup = Setup(root, data, args.port, args.pip_index)
            service = ServiceManager(setup, backups)
            manager = service if service.present() else setup
            database = data / "cloud-control.sqlite3"
            if args.action == "service-install":
                service.install()
            elif args.action == "service-disable":
                service.disable()
            elif args.action == "backup":
                snapshot = backup_database(database, backups)
                print(
                    json.dumps(
                        {"backup": str(snapshot), **check_backup(snapshot)}, indent=2
                    )
                )
            elif args.action == "backup-check":
                if args.backup is None:
                    raise RuntimeError("Specify --backup SNAPSHOT_DIRECTORY")
                print(json.dumps(check_backup(args.backup), indent=2))
            elif args.action == "restore":
                if args.backup is None or args.confirm_sha256 is None:
                    raise RuntimeError(
                        "Specify --backup and --confirm-sha256; stop first"
                    )
                if manager is service:
                    service.require_stopped()
                elif setup.owned_pid() is not None:
                    raise RuntimeError("Stop this framework before restoring data")
                if not database.is_file():
                    raise RuntimeError("Live database missing; no files changed")
                with (data / "runtime.lock").open("a+b") as runtime_lock:
                    fcntl.flock(runtime_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                    print(
                        json.dumps(
                            restore_database(
                                args.backup, database, backups, args.confirm_sha256
                            ),
                            indent=2,
                        )
                    )
            elif args.action == "check":
                report = {
                    "toolVersion": TOOL_VERSION,
                    "dependenciesReady": setup.dependencies_ok(),
                    "database": inspect_database(database)
                    if database.exists()
                    else None,
                    "manager": "systemd" if manager is service else "legacy",
                    "source": str(setup.source),
                    "data": str(data),
                    "backups": str(backups),
                }
                if manager is service:
                    report["serviceState"] = service.checked()
                print(json.dumps(report, indent=2))
            elif args.action == "install" and manager is service:
                if not setup.dependencies_ok():
                    raise RuntimeError(
                        "Managed environment changed; preserve it and inspect before reinstalling"
                    )
                service.start()
                service.status(required=True)
            elif args.action == "install":
                setup.install(args.package)
                setup.start()
            elif args.action == "restart":
                manager.stop()
                manager.start()
                manager.status(required=True)
            elif args.action == "start" and manager is service:
                service.start()
                service.status(required=True)
            elif args.action == "logs" and manager is setup:
                print(
                    setup.log.read_text(errors="replace")[-8000:]
                    if setup.log.exists()
                    else "No runtime log yet"
                )
            else:
                getattr(manager, args.action)()
        return 0
    except (
        OSError,
        RuntimeError,
        ValueError,
        subprocess.SubprocessError,
        sqlite3.Error,
        KeyError,
        TypeError,
    ) as error:
        print("SETUP STOPPED: " + str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
