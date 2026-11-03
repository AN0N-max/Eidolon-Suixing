#!/usr/bin/env python3
"""Install/control the verified private framework. No model keys or public ports.

Run as the normal Ubuntu user, without sudo:
  python3 eidolon-cloud-setup.py install
  python3 eidolon-cloud-setup.py status
  python3 eidolon-cloud-setup.py start|stop|restart|logs
Only apt uses sudo; files and the runtime stay owned by the current user.
"""

from __future__ import annotations

import argparse
import base64
import contextlib
import hashlib
import io
import json
import os
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
        choices=("install", "start", "status", "stop", "restart", "logs"),
        nargs="?",
        default="install",
    )
    parser.add_argument("--root", type=Path, default=Path.home() / "eidolon-cloud-dev1")
    parser.add_argument("--data", type=Path, default=Path.home() / "eidolon-cloud-data")
    parser.add_argument("--port", type=int, default=8787)
    parser.add_argument("--package", type=Path)
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
        import fcntl

        with (root / "setup.lock").open("a+b") as lock:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            setup = Setup(root, data, args.port, args.pip_index)
            if args.action == "install":
                setup.install(args.package)
                setup.start()
            elif args.action == "restart":
                setup.stop()
                setup.start()
            elif args.action == "logs":
                print(
                    setup.log.read_text(errors="replace")[-8000:]
                    if setup.log.exists()
                    else "No runtime log yet"
                )
            else:
                getattr(setup, args.action)()
        return 0
    except (
        OSError,
        RuntimeError,
        ValueError,
        subprocess.SubprocessError,
        sqlite3.Error,
    ) as error:
        print("SETUP STOPPED: " + str(error), file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
