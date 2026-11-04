"""Real SQLite/WAL recovery and fake systemd failure-path tests; no paid services."""

import importlib.util
import json
import sqlite3
import subprocess
from pathlib import Path, PurePosixPath
from types import SimpleNamespace
from uuid import uuid4

import pytest

SPEC = importlib.util.spec_from_file_location(
    "ops_setup", Path(__file__).parents[1] / "deploy/eidolon-cloud-setup.py"
)
ops = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ops)


def make_db(path, label="original"):
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path)
    db.execute("PRAGMA journal_mode=WAL")
    db.executescript("""
        CREATE TABLE cloud_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE cloud_devices(id TEXT PRIMARY KEY, name TEXT NOT NULL);
        CREATE TABLE cloud_diagnostic_jobs(id TEXT PRIMARY KEY);
        CREATE TABLE cloud_inbox(id TEXT PRIMARY KEY, job TEXT REFERENCES cloud_diagnostic_jobs(id));
    """)
    db.executemany(
        "INSERT INTO cloud_meta VALUES(?,?)",
        [("schema", "1"), ("stream", str(uuid4()))],
    )
    db.execute("INSERT INTO cloud_devices VALUES('test-device',?)", (label,))
    db.commit()
    return db


def label(path):
    db = sqlite3.connect(path)
    try:
        return db.execute("SELECT name FROM cloud_devices").fetchone()[0]
    finally:
        db.close()


def test_online_backup_includes_committed_wal_and_is_repeatable(tmp_path):
    source = tmp_path / "data/cloud-control.sqlite3"
    db = make_db(source)
    try:
        db.execute("UPDATE cloud_devices SET name='committed-in-wal'")
        db.commit()
        assert Path(str(source) + "-wal").stat().st_size > 0
        first = ops.backup_database(source, tmp_path / "backups")
        second = ops.backup_database(source, tmp_path / "backups")
        assert first != second
        assert label(first / "database.sqlite3") == "committed-in-wal"
        assert ops.check_backup(first)["sha256"] == ops.file_digest(
            first / "database.sqlite3"
        )
        assert ops.check_backup(second)["schema"] == "1"
        assert label(source) == "committed-in-wal"
    finally:
        db.close()


@pytest.mark.parametrize("change", ["digest", "path", "schema", "unknown-table"])
def test_invalid_backup_rejected_before_restore(tmp_path, change):
    live = tmp_path / "data/live.sqlite3"
    db = make_db(live)
    db.close()
    folder = ops.backup_database(live, tmp_path / "backups")
    manifest_path = folder / "manifest.json"
    manifest = json.loads(manifest_path.read_text())
    if change == "digest":
        manifest["sha256"] = "0" * 64
    elif change == "path":
        manifest["database"] = "../../data/live.sqlite3"
    else:
        with sqlite3.connect(folder / "database.sqlite3") as copy:
            if change == "schema":
                copy.execute("UPDATE cloud_meta SET value='2' WHERE key='schema'")
            else:
                copy.execute("CREATE TABLE private_other_data(x)")
        manifest["sha256"] = ops.file_digest(folder / "database.sqlite3")
        manifest["bytes"] = (folder / "database.sqlite3").stat().st_size
    manifest_path.write_text(json.dumps(manifest))
    with pytest.raises(RuntimeError):
        ops.restore_database(folder, live, tmp_path / "backups", manifest["sha256"])
    assert label(live) == "original"


def test_restore_requires_exact_confirmation_and_preserves_before_snapshot(tmp_path):
    live = tmp_path / "data/live.sqlite3"
    db = make_db(live, "earlier")
    db.close()
    folder = ops.backup_database(live, tmp_path / "backups")
    with sqlite3.connect(live) as db:
        db.execute("UPDATE cloud_devices SET name='current'")
    with pytest.raises(RuntimeError, match="confirm-sha256"):
        ops.restore_database(folder, live, tmp_path / "backups", "wrong")
    assert label(live) == "current"
    result = ops.restore_database(
        folder, live, tmp_path / "backups", ops.check_backup(folder)["sha256"]
    )
    assert label(live) == "earlier"
    assert result["serviceStarted"] is False
    before = Path(result["beforeRestore"])
    assert label(before / "database.sqlite3") == "current"
    assert ops.check_backup(before)["reason"] == "before-restore"


def test_restore_cannot_write_if_preservation_backup_fails(tmp_path, monkeypatch):
    live = tmp_path / "data/live.sqlite3"
    db = make_db(live)
    db.close()
    folder = ops.backup_database(live, tmp_path / "backups")
    confirmation = ops.check_backup(folder)["sha256"]

    def failed(*args, **kwargs):
        raise OSError("simulated disk failure")

    monkeypatch.setattr(ops, "backup_database", failed)
    with pytest.raises(OSError):
        ops.restore_database(folder, live, tmp_path / "backups", confirmation)
    assert label(live) == "original"


def test_interrupted_sqlite_copy_rolls_back_destination(tmp_path, monkeypatch):
    source, target = tmp_path / "source.sqlite3", tmp_path / "target.sqlite3"
    db = make_db(source, "x" * 2_000_000)
    db.close()
    db = make_db(target, "retained")
    db.close()
    clock = iter([0, 100, 200])
    monkeypatch.setattr(ops.time, "monotonic", lambda: next(clock, 1000))
    with pytest.raises(RuntimeError, match="timed out"):
        ops.copy_database(source, target, create=False)
    assert label(target) == "retained"


def test_wrong_database_or_self_destination_is_refused(tmp_path):
    wrong = tmp_path / "unrelated.sqlite3"
    with sqlite3.connect(wrong) as db:
        db.execute("CREATE TABLE unrelated(x)")
    with pytest.raises(RuntimeError, match="supported"):
        ops.inspect_database(wrong)
    with pytest.raises(RuntimeError, match="must differ"):
        ops.copy_database(wrong, wrong, create=False)


async def test_actual_framework_snapshot_preserves_inbox_and_authentication(tmp_path):
    from eidolon_cloud.assembly import Backend
    from eidolon_cloud.config import Settings

    data = tmp_path / "live"
    backend = Backend(Settings(data))
    await backend.start()
    device = await backend.store.add_device("artificial fixture")
    await backend.store.diagnostic("fixture-job", 1000, 2000)
    await backend.store.deliver_diagnostic(1000)
    snapshot = ops.backup_database(data / "cloud-control.sqlite3", tmp_path / "backups")
    await backend.store.revoke(device["deviceId"])
    await backend.close()
    ops.restore_database(
        snapshot,
        data / "cloud-control.sqlite3",
        tmp_path / "backups",
        ops.check_backup(snapshot)["sha256"],
    )
    restored = Backend(Settings(data))
    await restored.start()
    try:
        assert await restored.store.authenticate(device["token"]) == device["deviceId"]
        page = await restored.store.page(device["deviceId"], 0, 50, None)
        assert len(page["messages"]) == 1
        assert page["messages"][0]["kind"] == "DIAGNOSTIC"
    finally:
        await restored.close()


@pytest.mark.parametrize(
    "root,user",
    [
        ("/home/admin/bad%h", "admin"),
        ("/home/admin/a\nExecStart=bad", "admin"),
        ("/home/admin/a", "root\nOther=bad"),
    ],
)
def test_systemd_unit_rejects_expansion_and_directive_injection(root, user):
    with pytest.raises(RuntimeError):
        ops.unit_text(
            PurePosixPath(root), PurePosixPath("/home/admin/data"), 8787, user
        )


def service_fixture(tmp_path, monkeypatch, *, fail_verify=False, fail_start=False):
    events = []
    root, data, backups = tmp_path / "install", tmp_path / "data", tmp_path / "backups"
    root.mkdir()
    database = data / "cloud-control.sqlite3"
    db = make_db(database)
    db.close()
    live_pid = [111]
    pidfile = root / "framework.pid"
    pidfile.write_text("111")

    def legacy_stop():
        # The old process must not be stopped until a valid backup exists.
        assert any(ops.check_backup(path) for path in backups.iterdir())
        events.append("legacy-stop")
        live_pid[0] = None

    setup = SimpleNamespace(
        root=root,
        data=data,
        source=root / "eidolon-cloud",
        port=8787,
        pidfile=pidfile,
        dependencies_ok=lambda: True,
        owned_pid=lambda: live_pid[0],
        stop=legacy_stop,
        health=lambda: {"status": "ok"},
    )
    service = ops.ServiceManager.__new__(ops.ServiceManager)
    service.setup, service.backups = setup, backups
    service.user = "admin"
    service.unit = tmp_path / ops.SERVICE
    service.marker = root / "service-manager.json"
    service.text = "fixture unit content\n"
    state = {
        "LoadState": "not-found",
        "FragmentPath": str(service.unit),
        "DropInPaths": "",
        "ActiveState": "inactive",
        "MainPID": "0",
        "UnitFileState": "disabled",
        "NeedDaemonReload": "no",
    }
    service.state = lambda: dict(state)

    def command(*args, **kwargs):
        events.append(args[0])
        if args[0] == "daemon-reload":
            state["LoadState"] = "loaded"
        elif args[0] == "start":
            if fail_start:
                raise subprocess.CalledProcessError(1, "systemctl start")
            state.update(ActiveState="active", MainPID="222")
        elif args[0] == "stop":
            state.update(ActiveState="inactive", MainPID="0")
        elif args[0] == "enable":
            state["UnitFileState"] = "enabled"
        return subprocess.CompletedProcess(args, 0, "", "")

    service.command = command
    real_is_dir = Path.is_dir
    monkeypatch.setattr(
        Path,
        "is_dir",
        lambda p: True if p.as_posix() == "/run/systemd/system" else real_is_dir(p),
    )

    def run(args, **kwargs):
        if args[0] == "systemd-analyze":
            events.append("verify")
            if fail_verify:
                raise subprocess.CalledProcessError(1, args)
        if "/usr/bin/python3" in args:
            events.append("unit-created")
            with service.unit.open("x") as out:
                out.write(kwargs["input"])
        return subprocess.CompletedProcess(args, 0, "", "")

    monkeypatch.setattr(ops.subprocess, "run", run)
    monkeypatch.setattr(
        ops,
        "process_info",
        lambda pid: (
            ([str(setup.source / ".venv/bin/eidolon-cloud"), "serve"], setup.source)
            if pid == 222
            else None
        ),
    )
    return service, state, events, live_pid


def test_preflight_failure_leaves_original_process_running(tmp_path, monkeypatch):
    service, state, events, pid = service_fixture(
        tmp_path, monkeypatch, fail_verify=True
    )
    with pytest.raises(subprocess.CalledProcessError):
        service.install()
    assert pid[0] == 111
    assert "legacy-stop" not in events
    assert not service.unit.exists()


def test_activation_failure_retains_backup_without_legacy_double_start(
    tmp_path, monkeypatch
):
    service, state, events, pid = service_fixture(
        tmp_path, monkeypatch, fail_start=True
    )
    with pytest.raises(RuntimeError, match="activation failed"):
        service.install()
    assert events.index("verify") < events.index("legacy-stop") < events.index("start")
    assert state["ActiveState"] == "inactive"
    assert "enable" not in events
    assert pid[0] is None
    assert service.unit.exists() and service.marker.exists()
    assert list(service.backups.glob("*/manifest.json"))


def test_successful_service_migration_and_repeat_install(tmp_path, monkeypatch):
    service, state, events, pid = service_fixture(tmp_path, monkeypatch)
    service.install()
    assert state["UnitFileState"] == "enabled" and state["ActiveState"] == "active"
    assert not service.setup.pidfile.exists()
    service.install()
    assert events.count("legacy-stop") == 1
    assert events.count("unit-created") == 1


def test_modified_unit_or_dropin_is_never_adopted(tmp_path, monkeypatch):
    service, state, events, pid = service_fixture(tmp_path, monkeypatch)
    service.unit.write_text("someone else's unit")
    with pytest.raises(RuntimeError, match="modified"):
        service.install()
    assert "legacy-stop" not in events and pid[0] == 111
    service.unit.write_text(service.text)
    state.update(LoadState="loaded", DropInPaths="/unexpected/override.conf")
    with pytest.raises(RuntimeError, match="drop-ins"):
        service.checked()


def test_restore_guard_rejects_running_service(tmp_path, monkeypatch):
    service, state, events, pid = service_fixture(tmp_path, monkeypatch)
    service.unit.write_text(service.text)
    state.update(LoadState="loaded", ActiveState="active")
    with pytest.raises(RuntimeError, match="Stop"):
        service.require_stopped()
