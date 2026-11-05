import asyncio
import hashlib
import importlib.util
import json
import sqlite3
from pathlib import Path
from uuid import uuid4

import httpx
import pytest

from eidolon_cloud.app import create_app
from eidolon_cloud.assembly import Backend
from eidolon_cloud.config import Settings
from eidolon_cloud.store import Refused
from eidolon_cloud.tts import MAX_BYTES, MockAudio, validate


def payload(**changes):
    return {
        "requestId": str(uuid4()),
        "text": "人工测试",
        "voice": "a" * 32,
        "model": "s2.1-pro-free",
        **changes,
    }


def test_existing_dictionary_group_shape_is_accepted():
    value = payload(
        pronunciation=[
            {"items": [{"key": "人工", "value": "test", "case_sensitive": True}]}
        ]
    )
    assert validate(value) == value


@pytest.fixture
async def rig(tmp_path):
    backend = Backend(Settings(tmp_path / "data", tts_mock=True))
    await backend.start()
    device = await backend.store.add_device("fixture")
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=create_app(backend)),
        base_url="http://test",
        headers={"Authorization": "Bearer " + device["token"]},
    ) as client:
        yield backend, device, client
    await backend.close()


async def test_mock_stream_complete_metadata_and_no_replay(rig):
    backend, device, client = rig
    value = payload()
    response = await client.post("/api/tts/stream", json=value)
    assert response.status_code == 200
    assert response.headers["x-eidolon-tts-mode"] == "mock"
    assert response.content == b"\0" * 17648
    result = (await client.get("/api/tts/requests/" + value["requestId"])).json()
    assert result["state"] == "completed" and result["bytes"] == len(response.content)
    assert result["sha256"] == hashlib.sha256(response.content).hexdigest()
    assert (await client.post("/api/tts/stream", json=value)).status_code == 409
    assert (
        await client.post("/api/tts/stream", json={**value, "text": "changed"})
    ).json()["error"] == "tts_request_conflict"
    with sqlite3.connect(backend.settings.database_path) as db:
        assert (
            db.execute("SELECT value FROM cloud_meta WHERE key='schema'").fetchone()[0]
            == "2"
        )
        assert "人工测试" not in " ".join(
            str(row) for row in db.execute("SELECT * FROM cloud_tts_requests")
        )


@pytest.mark.parametrize(
    "change",
    [
        {"text": ""},
        {"text": "a" * 5001},
        {"voice": "bad"},
        {"model": []},
        {"requestId": "bad"},
        {"url": "https://arbitrary.invalid"},
        {"pronunciation": [{"key": "x", "value": "y", "case_sensitive": "true"}]},
    ],
)
async def test_invalid_input_never_admitted(rig, change):
    backend, device, client = rig
    assert (
        await client.post("/api/tts/stream", json=payload(**change))
    ).status_code == 400
    assert not backend.tts.active
    with sqlite3.connect(backend.settings.database_path) as db:
        assert db.execute("SELECT count(*) FROM cloud_tts_requests").fetchone()[0] == 0


async def test_auth_revocation_isolation_and_bounded_body(rig):
    backend, device, client = rig
    value = payload()
    assert (
        await client.post(
            "/api/tts/stream", json=value, headers={"Authorization": "wrong"}
        )
    ).status_code == 401
    assert (
        await client.post(
            "/api/tts/stream",
            content=b"x" * 65537,
            headers={"Content-Type": "application/json"},
        )
    ).status_code == 413
    assert (
        await client.post(
            "/api/tts/stream",
            content='{"text":"x","text":"y"}',
            headers={"Content-Type": "application/json"},
        )
    ).status_code == 400
    await client.post("/api/tts/stream", json=value)
    other = await backend.store.add_device("other")
    headers = {"Authorization": "Bearer " + other["token"]}
    assert (
        await client.get("/api/tts/requests/" + value["requestId"], headers=headers)
    ).status_code == 404
    assert (
        await client.post(
            "/api/tts/requests/" + value["requestId"] + "/cancel", headers=headers
        )
    ).status_code == 404
    await backend.store.revoke(device["deviceId"])
    assert (await client.post("/api/tts/stream", json=payload())).status_code == 401


async def test_off_mode_does_not_migrate_or_enable_paid_access(tmp_path):
    b = Backend(Settings(tmp_path))
    await b.start()
    d = await b.store.add_device("test")
    async with httpx.AsyncClient(
        transport=httpx.ASGITransport(app=create_app(b)),
        base_url="http://test",
        headers={"Authorization": "Bearer " + d["token"]},
    ) as client:
        assert (await client.post("/api/tts/stream", json=payload())).status_code == 503
        assert (await client.get("/api/tts/capabilities")).json() == {
            "enabled": False,
            "mode": "off",
            "paidRequestsSupported": False,
        }
    with sqlite3.connect(b.settings.database_path) as db:
        assert (
            db.execute("SELECT value FROM cloud_meta WHERE key='schema'").fetchone()[0]
            == "1"
        )
    await b.close()


class Audio:
    def __init__(self, kind):
        self.kind, self.closed = kind, False

    async def chunks(self, value):
        try:
            if self.kind == "empty":
                return
            if self.kind == "first-error":
                raise OSError("private upstream error body")
            yield b"abc"
            if self.kind == "block":
                await asyncio.Event().wait()
            if self.kind == "error":
                raise OSError("private upstream error body")
            if self.kind == "odd":
                return
            yield b"d"
        finally:
            self.closed = True


@pytest.mark.parametrize("kind", ["odd", "error", "block"])
async def test_partial_error_or_timeout_never_completes(rig, kind):
    backend, device, client = rig
    upstream = Audio(kind)
    backend.tts.upstream, backend.tts.deadline = upstream, 0.05
    value = payload()
    stream = await backend.tts.open(device["deviceId"], value)
    assert await anext(stream) == b"abc"
    with pytest.raises(RuntimeError, match="tts_stream_incomplete"):
        async for chunk in stream:
            pass
    assert (await backend.tts.state(device["deviceId"], value["requestId"]))[
        "state"
    ] == "unknown"
    assert upstream.closed and not backend.tts.active


@pytest.mark.parametrize("kind", ["empty", "first-error"])
async def test_first_chunk_failure_is_controlled_json(rig, kind):
    b, d, client = rig
    b.tts.upstream = Audio(kind)
    value = payload()
    response = await client.post("/api/tts/stream", json=value)
    assert response.status_code == 502 and "private" not in response.text
    assert not b.tts.active


async def test_cancel_interrupts_blocked_read_and_preserves_capacity(rig):
    b, d, client = rig
    upstream = Audio("block")
    b.tts.upstream = upstream
    value = payload()
    stream = await b.tts.open(d["deviceId"], value)
    assert await anext(stream) == b"abc"
    with pytest.raises(Refused) as failure:
        await b.tts.open(d["deviceId"], payload())
    assert failure.value.status == 429
    pending = asyncio.create_task(anext(stream))
    await asyncio.sleep(0)
    response = await client.post("/api/tts/requests/" + value["requestId"] + "/cancel")
    assert response.json()["state"] == "canceled"
    with pytest.raises(RuntimeError):
        await asyncio.wait_for(pending, timeout=1)
    assert upstream.closed and not b.tts.active


async def test_restart_marks_unfinished_unknown_and_never_replays(tmp_path):
    b = Backend(Settings(tmp_path, tts_mock=True))
    await b.start()
    d = await b.store.add_device("test")
    value = payload()
    await b.tts.reserve(d["deviceId"], value)
    await b.close()
    b = Backend(Settings(tmp_path, tts_mock=True))
    await b.start()
    try:
        assert (await b.tts.state(d["deviceId"], value["requestId"]))[
            "state"
        ] == "unknown"
        with pytest.raises(Refused) as failure:
            await b.tts.open(d["deviceId"], value)
        assert failure.value.code == "tts_request_exists"
    finally:
        await b.close()


async def test_schema2_backup_restore_retains_no_replay_ledger(rig, tmp_path):
    b, d, client = rig
    value = payload()
    await client.post("/api/tts/stream", json=value)
    spec = importlib.util.spec_from_file_location(
        "backup_ops", Path(__file__).parents[1] / "deploy/eidolon-cloud-setup.py"
    )
    ops = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(ops)
    snapshot = ops.backup_database(b.settings.database_path, tmp_path / "backups")
    assert ops.check_backup(snapshot)["schema"] == "2"
    target = tmp_path / "copy.sqlite3"
    ops.copy_database(snapshot / "database.sqlite3", target, create=True)
    assert ops.inspect_database(target)["schema"] == "2"
    with sqlite3.connect(target) as db:
        assert (
            db.execute(
                "SELECT state FROM cloud_tts_requests WHERE id=?", (value["requestId"],)
            ).fetchone()[0]
            == "completed"
        )


async def test_response_abandoned_before_body_releases_slot(rig):
    b, d, client = rig
    value = payload()
    stream = await b.tts.open(d["deviceId"], value)
    await stream.aclose()
    await b.tts.abandon(d["deviceId"], value["requestId"])
    assert not b.tts.active
    assert (await b.tts.state(d["deviceId"], value["requestId"]))["state"] == "unknown"


async def test_even_chunk_boundaries_and_terminal_cancel_are_consistent(rig):
    b, d, client = rig
    b.tts.upstream = Audio("ok")
    value = payload()
    response = await client.post("/api/tts/stream", json=value)
    assert response.content == b"abcd"
    for _ in range(2):
        result = await client.post(
            "/api/tts/requests/" + value["requestId"] + "/cancel"
        )
        assert result.json()["state"] == "completed"


@pytest.mark.parametrize("point", ["start", "body"])
async def test_asgi_broken_connection_cleans_stream_and_ledger(rig, point):
    b, d, client = rig
    app = create_app(b)
    value = payload()
    raw = json.dumps(value).encode()
    sent_body = False

    async def receive():
        nonlocal sent_body
        if not sent_body:
            sent_body = True
            return {"type": "http.request", "body": raw, "more_body": False}
        await asyncio.Event().wait()

    async def send(message):
        if (point == "start" and message["type"] == "http.response.start") or (
            point == "body"
            and message["type"] == "http.response.body"
            and message.get("body")
        ):
            raise OSError("fixture disconnected socket")

    scope = {
        "type": "http",
        "asgi": {"version": "3.0", "spec_version": "2.4"},
        "http_version": "1.1",
        "method": "POST",
        "scheme": "http",
        "path": "/api/tts/stream",
        "raw_path": b"/api/tts/stream",
        "query_string": b"",
        "headers": [
            (b"content-type", b"application/json"),
            (b"authorization", ("Bearer " + d["token"]).encode()),
        ],
        "server": ("127.0.0.1", 80),
        "client": ("fixture", 1234),
    }
    try:
        await asyncio.wait_for(app(scope, receive, send), timeout=2)
    except (OSError, RuntimeError):
        pass
    await asyncio.wait_for(b.tts.idle.wait(), timeout=1)
    assert not b.tts.active
    assert (await b.tts.state(d["deviceId"], value["requestId"]))["state"] == "unknown"


@pytest.mark.parametrize("status", [301, 401, 402, 429, 500])
async def test_httpx_mock_errors_never_redirect_retry_or_expose_body(rig, status):
    b, d, client = rig
    calls = []

    def respond(request):
        calls.append(request)
        assert str(request.url) == "https://tts-mock.invalid/v1/tts"
        assert "authorization" not in request.headers
        return httpx.Response(
            status,
            headers={"Location": "https://must-not-follow.invalid"},
            content=b"private upstream detail",
        )

    await b.tts.upstream.close()
    b.tts.upstream = MockAudio(transport=httpx.MockTransport(respond))
    value = payload()
    result = await client.post("/api/tts/stream", json=value)
    assert result.status_code == 502 and "private" not in result.text
    assert len(calls) == 1
    assert (await b.tts.state(d["deviceId"], value["requestId"]))["state"] == "unknown"
    assert not b.tts.active


@pytest.mark.parametrize("kind", ["content-type", "read-timeout"])
async def test_httpx_mock_rejects_bad_response_and_timeout(rig, kind):
    b, d, client = rig

    def respond(request):
        if kind == "read-timeout":
            raise httpx.ReadTimeout("private network detail")
        return httpx.Response(200, headers={"Content-Type": "text/html"}, content=b"xx")

    await b.tts.upstream.close()
    b.tts.upstream = MockAudio(transport=httpx.MockTransport(respond))
    response = await client.post("/api/tts/stream", json=payload())
    assert response.status_code == 502 and "private" not in response.text
    assert not b.tts.active


async def test_backpressure_size_limit_and_upstream_close(rig):
    b, d, client = rig

    class Large:
        reads = 0
        closed = False

        async def chunks(self, value):
            try:
                for _ in range(18):
                    self.reads += 1
                    yield b"x" * (MAX_BYTES // 16)
            finally:
                self.closed = True

    await b.tts.upstream.close()
    source = b.tts.upstream = Large()
    value = payload()
    stream = await b.tts.open(d["deviceId"], value)
    assert source.reads == 1  # Priming, then at most one consumer-requested chunk.
    assert len(await anext(stream)) == MAX_BYTES // 16
    await asyncio.sleep(0.01)
    assert source.reads == 1
    with pytest.raises(RuntimeError, match="tts_stream_incomplete"):
        async for _ in stream:
            pass
    assert source.reads == 17 and source.closed and not b.tts.active
    assert (await b.tts.state(d["deviceId"], value["requestId"]))["state"] == "unknown"


async def test_revoke_during_stream_cannot_complete(rig):
    b, d, client = rig
    value = payload()
    stream = await b.tts.open(d["deviceId"], value)
    await anext(stream)
    await b.store.revoke(d["deviceId"])
    with pytest.raises(RuntimeError, match="tts_stream_incomplete"):
        async for _ in stream:
            pass
    assert not b.tts.active
    with sqlite3.connect(b.settings.database_path) as db:
        assert (
            db.execute("SELECT state FROM cloud_tts_requests").fetchone()[0]
            == "unknown"
        )
