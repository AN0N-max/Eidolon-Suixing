import asyncio
import json
import os
import socket
import subprocess
import sys
import threading
import time
from contextlib import asynccontextmanager
from pathlib import Path
from uuid import uuid4

import httpx
import pytest

from eidolon_cloud.app import create_app
from eidolon_cloud.assembly import Backend
from eidolon_cloud.config import Settings
from eidolon_cloud.domain import DomainUnavailable, LocalDecision
from eidolon_cloud.store import Refused, token_hash


@asynccontextmanager
async def running(path: Path, diagnostics: bool = False, domain=None):
    backend = Backend(
        Settings(path, diagnostics=diagnostics, tick_seconds=0.01), domain=domain
    )
    app = create_app(backend)
    async with app.router.lifespan_context(app):
        async with httpx.AsyncClient(
            transport=httpx.ASGITransport(app), base_url="http://test"
        ) as http:
            yield backend, http


def authorization(device):
    return {"Authorization": "Bearer " + device["token"]}


async def enqueue(store, key="test/1", due=None):
    at = int(time.time() * 1000) - 1 if due is None else due
    return await store.diagnostic(key, at, at + 60_000)


async def test_authentication_and_no_http_administration(tmp_path):
    async with running(tmp_path) as (backend, http):
        device = await backend.store.add_device("phone")
        assert (await http.get("/api/health")).json()["domainConnected"] is False
        assert (await http.get("/api/inbox")).status_code == 401
        assert (
            await http.get("/api/inbox", headers={"Authorization": "Bearer password"})
        ).status_code == 401
        assert (
            await http.get("/api/device", headers=authorization(device))
        ).status_code == 200
        for path in ("/docs", "/openapi.json", "/api/admin", "/api/plan", "/api/model"):
            assert (
                await http.get(path, headers=authorization(device))
            ).status_code == 404
        saved = await backend.database.read(
            lambda db: db.execute("SELECT token_hash FROM cloud_devices").fetchone()[0]
        )
        assert saved == token_hash(device["token"])
        assert (
            device["token"]
            not in (await http.get("/api/device", headers=authorization(device))).text
        )
        assert await backend.store.revoke(device["deviceId"])
        assert (
            await http.get("/api/inbox", headers=authorization(device))
        ).status_code == 401


async def test_message_replay_ack_and_database_restart(tmp_path):
    async with running(tmp_path) as (backend, http):
        device = await backend.store.add_device("phone")
        await enqueue(backend.store)
        assert await backend.store.deliver_diagnostic()
        assert not await backend.store.deliver_diagnostic()
        page = (await http.get("/api/inbox", headers=authorization(device))).json()
        stream, message_id = page["streamId"], page["messages"][0]["id"]
        assert page["messages"][0]["kind"] == "DIAGNOSTIC"
        ack = await http.post(
            "/api/inbox/ack",
            headers=authorization(device),
            json={"streamId": stream, "through": 1},
        )
        assert ack.json()["storedThrough"] == 1
        assert (
            await http.post(
                "/api/inbox/ack",
                headers=authorization(device),
                json={"streamId": stream, "through": 0},
            )
        ).json()["storedThrough"] == 1
    async with running(tmp_path) as (backend, http):
        assert (await http.get("/api/device", headers=authorization(device))).json()[
            "storedThrough"
        ] == 1
        replay = (await http.get("/api/inbox", headers=authorization(device))).json()
        assert (
            replay["streamId"] == stream and replay["messages"][0]["id"] == message_id
        )
        assert (
            len(replay["messages"]) == 1
        )  # ack did not delete the message or mark a role read.
        assert (
            await http.get(
                "/api/inbox",
                headers=authorization(device),
                params={"after": 1, "streamId": stream},
            )
        ).json()["messages"] == []


async def test_per_device_cursor_and_stream_identity(tmp_path):
    async with running(tmp_path) as (backend, http):
        first, second = (
            await backend.store.add_device("first"),
            await backend.store.add_device("second"),
        )
        await enqueue(backend.store)
        await backend.store.deliver_diagnostic()
        page = (await http.get("/api/inbox", headers=authorization(first))).json()
        for key, value in (("through", 2), ("streamId", str(uuid4()))):
            body = {"through": 1, "streamId": page["streamId"]}
            body[key] = value
            assert (
                await http.post(
                    "/api/inbox/ack", headers=authorization(first), json=body
                )
            ).status_code == 409
        assert (
            await http.post(
                "/api/inbox/ack",
                headers=authorization(second),
                json={"through": 1, "streamId": page["streamId"]},
            )
        ).status_code == 409
        assert (
            await http.get("/api/inbox?after=1", headers=authorization(first))
        ).status_code == 400
        assert (
            await http.get(
                "/api/inbox?streamId=" + str(uuid4()), headers=authorization(first)
            )
        ).status_code == 409


async def test_http_limits_ambiguous_json_and_invalid_cursors(tmp_path):
    async with running(tmp_path) as (backend, http):
        device = await backend.store.add_device("phone")
        headers = authorization(device)
        stream = (await http.get("/api/device", headers=headers)).json()["streamId"]
        for query in (
            "after=-1",
            "after=01",
            "after=9223372036854775808",
            "limit=0",
            "limit=101",
            "after=0&after=1",
            "unknown=0",
        ):
            assert (
                await http.get("/api/inbox?" + query, headers=headers)
            ).status_code == 400
        for through in (-1, True, "0", 1.5, 2**63):
            assert (
                await http.post(
                    "/api/inbox/ack",
                    headers=headers,
                    json={"through": through, "streamId": stream},
                )
            ).status_code == 400
        duplicate = '{"through":0,"through":1,"streamId":"' + stream + '"}'
        assert (
            await http.post(
                "/api/inbox/ack",
                headers=headers | {"Content-Type": "application/json"},
                content=duplicate,
            )
        ).status_code == 400
        assert (
            await http.post(
                "/api/inbox/ack",
                headers=headers | {"Content-Type": "application/json"},
                content=b" " * 2049,
            )
        ).status_code == 413
        assert (
            await http.post(
                "/api/inbox/ack",
                headers=headers | {"Content-Type": "text/plain"},
                content="{}",
            )
        ).status_code == 415
        response = await http.get("/api/inbox", headers=headers)
        assert response.headers["Cache-Control"] == "no-store"


async def test_diagnostic_idempotency_cancellation_and_expiry(tmp_path):
    async with running(tmp_path) as (backend, http):
        now = int(time.time() * 1000)
        await backend.store.diagnostic("same", now, now + 1000)
        assert await backend.store.diagnostic("same", now, now + 1000) == "same"
        with pytest.raises(Refused, match="idempotency_conflict"):
            await backend.store.diagnostic("same", now + 1, now + 1000)
        assert await backend.store.cancel_diagnostic("same")
        await backend.store.diagnostic("expired", now - 2000, now - 1000)
        assert await backend.store.deliver_diagnostic(now)
        assert not await backend.store.deliver_diagnostic(now)
        states = await backend.database.read(
            lambda db: dict(db.execute("SELECT id,status FROM cloud_diagnostic_jobs"))
        )
        assert states == {"same": "CANCELED", "expired": "EXPIRED"}
        assert (
            await backend.database.read(
                lambda db: db.execute("SELECT count(*) FROM cloud_inbox").fetchone()[0]
            )
            == 0
        )


async def test_concurrent_publishers_preserve_commit_order_and_unique_identity(
    tmp_path,
):
    async with running(tmp_path) as (backend, http):
        now = int(time.time() * 1000)
        await asyncio.gather(
            *(
                backend.store.diagnostic("job/" + str(i), now, now + 60_000)
                for i in range(30)
            )
        )
        await asyncio.gather(
            *(backend.store.deliver_diagnostic(now) for _ in range(50))
        )
        device = await backend.store.add_device("phone")
        messages = (
            await http.get("/api/inbox?limit=100", headers=authorization(device))
        ).json()["messages"]
        assert [message["sequence"] for message in messages] == list(range(1, 31))
        assert len({message["id"] for message in messages}) == 30


async def test_write_failure_rolls_back_as_one_transaction(tmp_path):
    async with running(tmp_path) as (backend, http):

        def interrupted(db):
            db.execute(
                "INSERT INTO cloud_diagnostic_jobs(id,due,expires) VALUES('rollback',1,2)"
            )
            db.execute(
                "INSERT INTO cloud_inbox(id,job_id,kind,text,created) VALUES('rollback','rollback','DIAGNOSTIC','synthetic',1)"
            )
            raise RuntimeError("injected crash")

        with pytest.raises(RuntimeError, match="injected crash"):
            await backend.database.write(interrupted)
        assert (
            await backend.database.read(
                lambda db: db.execute("SELECT count(*) FROM cloud_inbox").fetchone()[0]
            )
            == 0
        )
        assert (
            await backend.database.read(
                lambda db: db.execute(
                    "SELECT count(*) FROM cloud_diagnostic_jobs"
                ).fetchone()[0]
            )
            == 0
        )
        await enqueue(backend.store)
        await backend.store.deliver_diagnostic()
        assert (
            await backend.database.read(
                lambda db: db.execute("SELECT sequence FROM cloud_inbox").fetchone()[0]
            )
            == 1
        )


async def test_snapshot_reader_is_not_held_by_pending_write(tmp_path):
    async with running(tmp_path) as (backend, http):
        await enqueue(backend.store)
        entered, release = threading.Event(), threading.Event()

        def blocked(db):
            db.execute("UPDATE cloud_diagnostic_jobs SET status='CANCELED'")
            entered.set()
            if not release.wait(5):
                raise RuntimeError("test timeout")

        writer = asyncio.create_task(backend.database.write(blocked))
        try:
            assert await asyncio.to_thread(entered.wait, 2)
            state = await asyncio.wait_for(
                backend.database.read(
                    lambda db: db.execute(
                        "SELECT status FROM cloud_diagnostic_jobs"
                    ).fetchone()[0]
                ),
                1,
            )
            assert state == "PENDING"
        finally:
            release.set()
            await writer


async def test_canceling_http_awaiter_does_not_cancel_an_accepted_transaction(tmp_path):
    async with running(tmp_path) as (backend, http):
        entered, release = threading.Event(), threading.Event()

        def pending(db):
            db.execute(
                "INSERT INTO cloud_diagnostic_jobs(id,due,expires) VALUES('accepted',1,2)"
            )
            entered.set()
            if not release.wait(5):
                raise RuntimeError("test timeout")

        task = asyncio.create_task(backend.database.write(pending))
        try:
            assert await asyncio.to_thread(entered.wait, 2)
            task.cancel()
            with pytest.raises(asyncio.CancelledError):
                await task
        finally:
            release.set()
        await backend.database.write(
            lambda db: None
        )  # barrier after the accepted write
        assert (
            await backend.database.read(
                lambda db: db.execute(
                    "SELECT count(*) FROM cloud_diagnostic_jobs WHERE id='accepted'"
                ).fetchone()[0]
            )
            == 1
        )


async def test_framework_never_substitutes_its_own_business_decisions(tmp_path):
    class ExistingDomainSpy:
        def __init__(self):
            self.calls = 0

        async def run(self, decision):
            self.calls += 1

    domain = ExistingDomainSpy()
    async with running(tmp_path, True, domain) as (backend, http):
        await enqueue(backend.store)
        for _ in range(100):
            count = await backend.database.read(
                lambda db: db.execute("SELECT count(*) FROM cloud_inbox").fetchone()[0]
            )
            if count:
                break
            await asyncio.sleep(0.01)
        assert count == 1 and domain.calls == 0
    async with running(tmp_path / "unbound") as (backend, http):
        decision = LocalDecision(
            "task", "role", "session", 1, 1, 1, 2, {"mode": "daily", "time": "12:00"}
        )
        with pytest.raises(DomainUnavailable):
            await backend.domain.run(decision)
        assert (
            await backend.database.read(
                lambda db: db.execute("SELECT count(*) FROM cloud_inbox").fetchone()[0]
            )
            == 0
        )


def test_runtime_configuration_cannot_expose_cleartext_listener(tmp_path):
    assert (
        Settings.environment({"EIDOLON_CLOUD_DATA": str(tmp_path)}).diagnostics is False
    )
    with pytest.raises(ValueError):
        Settings(tmp_path, host="0.0.0.0")
    with pytest.raises(ValueError):
        Settings.environment({})
    with pytest.raises(ValueError):
        Settings.environment(
            {"EIDOLON_CLOUD_DATA": str(tmp_path), "EIDOLON_CLOUD_DIAGNOSTICS": "yes"}
        )


async def test_second_scheduler_cannot_take_the_same_data_directory(tmp_path):
    first, second = Backend(Settings(tmp_path)), Backend(Settings(tmp_path))
    try:
        await first.start()
        with pytest.raises(RuntimeError, match="Another runtime"):
            await second.start()
        await first.close()
        await second.start()  # lock is released after normal shutdown
    finally:
        await first.close()
        await second.close()


async def test_framework_refuses_unrelated_or_future_database_schemas(tmp_path):
    import sqlite3

    for name, sql in (
        ("other", "CREATE TABLE messages(body TEXT)"),
        (
            "future",
            "CREATE TABLE cloud_meta(key TEXT PRIMARY KEY,value TEXT); INSERT INTO cloud_meta VALUES('schema','99')",
        ),
    ):
        directory = tmp_path / name
        directory.mkdir()
        with sqlite3.connect(directory / "cloud-control.sqlite3") as db:
            db.executescript(sql)
        backend = Backend(Settings(directory))
        try:
            with pytest.raises(RuntimeError):
                await backend.start()
        finally:
            await backend.close()


def test_independent_cli_and_actual_uvicorn_process_survive_restart(tmp_path):
    env = dict(
        os.environ, EIDOLON_CLOUD_DATA=str(tmp_path), EIDOLON_CLOUD_DIAGNOSTICS="1"
    )
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    env["EIDOLON_CLOUD_PORT"] = str(port)
    env["PYTHONPATH"] = str(Path(__file__).resolve().parents[1] / "src")

    def cli(*args):
        result = subprocess.run(
            [sys.executable, "-m", "eidolon_cloud", *args],
            env=env,
            capture_output=True,
            timeout=10,
        )
        assert result.returncode == 0, (
            "CLI failed; outputs containing device tokens are intentionally not printed"
        )
        return json.loads(result.stdout)

    device = cli("device-add", "process-phone")
    from datetime import datetime, timezone

    cli("diagnostic-add", "process/1", datetime.now(timezone.utc).isoformat())
    process = None

    def start():
        child = subprocess.Popen(
            [sys.executable, "-m", "eidolon_cloud", "serve"],
            env=env,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
        )
        try:
            for _ in range(100):
                assert child.poll() is None
                try:
                    response = httpx.get(
                        f"http://127.0.0.1:{port}/api/health",
                        timeout=0.3,
                        trust_env=False,
                    )
                    if response.status_code == 200:
                        return child
                except httpx.TransportError:
                    pass
                time.sleep(0.05)
            pytest.fail("Actual Uvicorn process did not become ready")
        except BaseException:
            child.terminate()
            child.wait(timeout=10)
            raise

    try:
        process = start()
        with httpx.Client(
            base_url=f"http://127.0.0.1:{port}",
            headers=authorization(device),
            trust_env=False,
        ) as http:
            page = http.get("/api/inbox").json()
            assert len(page["messages"]) == 1
            message_id = page["messages"][0]["id"]
            assert (
                http.post(
                    "/api/inbox/ack", json={"streamId": page["streamId"], "through": 1}
                ).status_code
                == 200
            )
            process.terminate()
            process.wait(timeout=10)
            process = start()
            assert http.get("/api/device").json()["storedThrough"] == 1
            assert http.get("/api/inbox").json()["messages"][0]["id"] == message_id
            assert cli("device-revoke", device["deviceId"])["revoked"] is True
            assert http.get("/api/inbox").status_code == 401
    finally:
        if process is not None:
            process.terminate()
            process.wait(timeout=10)
