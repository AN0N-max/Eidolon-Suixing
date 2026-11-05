"""Offline-only TTS relay protocol. No credentials, Fish transport or paid requests."""

import asyncio
import hashlib
import json
import re
import time
from uuid import UUID

import anyio
import httpx

from .store import Refused, Store

MODELS = {"s2.1-pro-free", "s2.1-pro", "drama-3", "s2-pro", "s1"}
MAX_BYTES = 16 * 1024 * 1024


def request_id(value: str) -> str:
    try:
        if str(UUID(value)) != value:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise Refused(400, "invalid_tts_request_id") from None
    return value


def validate(value: dict) -> dict:
    if (
        not isinstance(value, dict)
        or set(value) - {"requestId", "text", "voice", "model", "pronunciation"}
        or not {"requestId", "text", "voice", "model"} <= set(value)
    ):
        raise Refused(400, "invalid_tts_request")
    request_id(value["requestId"])
    if (
        not isinstance(value["text"], str)
        or not value["text"].strip()
        or len(value["text"]) > 5000
    ):
        raise Refused(400, "invalid_tts_text")
    if not isinstance(value["voice"], str) or not re.fullmatch(
        r"[a-fA-F0-9]{32}", value["voice"]
    ):
        raise Refused(400, "invalid_tts_voice")
    if not isinstance(value["model"], str) or value["model"] not in MODELS:
        raise Refused(400, "invalid_tts_model")
    dictionary = value.get("pronunciation", [])
    if not isinstance(dictionary, list) or len(dictionary) > 256:
        raise Refused(400, "invalid_tts_dictionary")
    entries = []
    for group in dictionary:
        if (
            not isinstance(group, dict)
            or set(group) != {"items"}
            or not isinstance(group["items"], list)
        ):
            raise Refused(400, "invalid_tts_dictionary")
        entries.extend(group["items"])
        if len(entries) > 256:
            raise Refused(400, "invalid_tts_dictionary")
    for entry in entries:
        if (
            not isinstance(entry, dict)
            or set(entry) != {"key", "value", "case_sensitive"}
            or any(
                not isinstance(entry[k], str) or not 1 <= len(entry[k]) <= 200
                for k in ("key", "value")
            )
            or type(entry["case_sensitive"]) is not bool
        ):
            raise Refused(400, "invalid_tts_dictionary")
    return value


class MockPcm(httpx.AsyncByteStream):
    """Fixed silence; the mock never speaks the supplied text/voice."""

    async def __aiter__(self):
        for _ in range(8):
            await asyncio.sleep(0.02)
            yield b"\0" * 2206


class MockAudio:
    """HTTPX streaming over an in-process transport: no socket or Fish endpoint."""

    def __init__(self, *, transport=None):
        self.client = httpx.AsyncClient(
            transport=transport or httpx.MockTransport(self.respond),
            trust_env=False,
            follow_redirects=False,
            timeout=httpx.Timeout(10, connect=5, pool=1),
        )

    @staticmethod
    def respond(request):
        return httpx.Response(
            200, headers={"Content-Type": "audio/pcm"}, stream=MockPcm()
        )

    async def chunks(self, value):
        async with self.client.stream(
            "POST", "https://tts-mock.invalid/v1/tts", json=value
        ) as response:
            if response.status_code != 200 or (
                response.headers.get("content-type", "").split(";", 1)[0].lower()
                != "audio/pcm"
            ):
                raise ValueError("invalid mock response")
            async for chunk in response.aiter_bytes():
                yield chunk

    async def close(self):
        await self.client.aclose()


class MockRelay:
    def __init__(self, store: Store, *, upstream=None, deadline: float = 30):
        self.store, self.upstream, self.deadline = (
            store,
            upstream or MockAudio(),
            deadline,
        )
        self.active: dict[tuple[str, str], asyncio.Event] = {}
        self.streams = {}
        self.idle = asyncio.Event()
        self.idle.set()
        self.closed = False

    async def initialize(self):
        def migrate(db):
            schema = db.execute(
                "SELECT value FROM cloud_meta WHERE key='schema'"
            ).fetchone()[0]
            if schema not in {"1", "2"}:
                raise RuntimeError("Unsupported TTS infrastructure schema")
            existing = db.execute(
                "SELECT 1 FROM sqlite_master WHERE name='cloud_tts_requests'"
            ).fetchone()
            if (schema == "1" and existing) or (schema == "2" and not existing):
                raise RuntimeError("TTS table/schema mismatch")
            db.execute("""CREATE TABLE IF NOT EXISTS cloud_tts_requests(
                device TEXT NOT NULL REFERENCES cloud_devices(id),id TEXT NOT NULL,
                input_hash TEXT NOT NULL,created INTEGER NOT NULL,
                state TEXT NOT NULL CHECK(state IN ('reserved','sending','completed','canceled','unknown','failed_before_send')),
                bytes INTEGER NOT NULL DEFAULT 0,sha256 TEXT NOT NULL DEFAULT '',
                PRIMARY KEY(device,id))""")
            db.execute("UPDATE cloud_meta SET value='2' WHERE key='schema'")
            # A process restart never replays a request, including a prior reservation.
            db.execute(
                "UPDATE cloud_tts_requests SET state='unknown' WHERE state IN ('reserved','sending')"
            )

        await self.store.db.write(migrate)

    async def state(self, device, identifier):
        request_id(identifier)

        def read(db):
            self.store._device(db, device)
            row = db.execute(
                "SELECT id,state,bytes,sha256 FROM cloud_tts_requests WHERE device=? AND id=?",
                (device, identifier),
            ).fetchone()
            if row is None:
                raise Refused(404, "tts_request_not_found")
            return {
                "requestId": row[0],
                "state": row[1],
                "bytes": row[2],
                "sha256": row[3],
                "mock": True,
                "sampleRate": 44100,
                "channels": 1,
                "sampleBits": 16,
            }

        return await self.store.db.read(read)

    async def finish(self, device, identifier, state, size=0, digest=""):
        await self.store.db.write(
            lambda db: (
                db.execute(
                    "UPDATE cloud_tts_requests SET state=?,bytes=?,sha256=? WHERE device=? AND id=? AND state IN ('reserved','sending')",
                    (state, size, digest, device, identifier),
                ).rowcount
            )
        )

    async def cancel(self, device, identifier):
        await self.state(device, identifier)
        event = self.active.get((device, identifier))
        if event is not None:
            event.set()
        await self.finish(device, identifier, "canceled")
        return await self.state(device, identifier)

    async def reserve(self, device, value):
        value = validate(value)
        identifier = value["requestId"]
        fingerprint = hashlib.sha256(
            json.dumps(
                value, sort_keys=True, ensure_ascii=False, separators=(",", ":")
            ).encode()
        ).hexdigest()

        def insert(db):
            self.store._device(db, device)
            row = db.execute(
                "SELECT input_hash FROM cloud_tts_requests WHERE device=? AND id=?",
                (device, identifier),
            ).fetchone()
            if row:
                raise Refused(
                    409,
                    "tts_request_exists"
                    if row[0] == fingerprint
                    else "tts_request_conflict",
                )
            count = db.execute(
                "SELECT count(*) FROM cloud_tts_requests WHERE device=?", (device,)
            ).fetchone()[0]
            if count >= 1000:
                raise Refused(429, "tts_mock_request_limit")
            db.execute(
                "INSERT INTO cloud_tts_requests(device,id,input_hash,created,state) VALUES(?,?,?,?,'reserved')",
                (device, identifier, fingerprint, time.time_ns() // 1_000_000),
            )

        await self.store.db.write(insert)
        return identifier

    async def open(self, device, value):
        if self.closed:
            raise Refused(503, "tts_stopping")
        # No awaits between the capacity check and claim. One active stream total.
        if self.active:
            raise Refused(429, "tts_busy")
        validate(value)
        key = (device, value["requestId"])
        canceled = asyncio.Event()
        self.active[key] = canceled
        self.idle.clear()
        stream = None
        reserved = False
        deadline = asyncio.get_running_loop().time() + self.deadline
        try:
            identifier = await self.reserve(device, value)
            reserved = True
            await self.store.db.write(
                lambda db: (
                    db.execute(
                        "UPDATE cloud_tts_requests SET state='sending' WHERE device=? AND id=? AND state='reserved'",
                        key,
                    ).rowcount
                )
            )
            stream = self.upstream.chunks(value).__aiter__()
            first = await self._next(stream, canceled, deadline)
            if not first:
                raise ValueError("empty audio")
        except BaseException as error:
            with anyio.CancelScope(shield=True):
                try:
                    if reserved:
                        await self.finish(
                            device,
                            key[1],
                            "canceled" if canceled.is_set() else "unknown",
                        )
                finally:
                    try:
                        if stream is not None:
                            await stream.aclose()
                    finally:
                        self.active.pop(key, None)
                        self.idle.set()
            if isinstance(error, Refused):
                raise
            raise Refused(
                409 if canceled.is_set() else 502,
                "tts_canceled" if canceled.is_set() else "tts_mock_upstream_failed",
            ) from None
        self.streams[key] = stream
        return self._body(device, identifier, key, canceled, stream, first, deadline)

    async def _next(self, stream, canceled, deadline):
        take = asyncio.create_task(anext(stream))
        stop = asyncio.create_task(canceled.wait())
        try:
            ready, _ = await asyncio.wait(
                {take, stop},
                timeout=max(0, deadline - asyncio.get_running_loop().time()),
                return_when=asyncio.FIRST_COMPLETED,
            )
            if stop in ready or canceled.is_set():
                raise asyncio.CancelledError()
            if take not in ready:
                raise TimeoutError()
            return take.result()
        finally:
            for task in (take, stop):
                if not task.done():
                    task.cancel()
            await asyncio.gather(take, stop, return_exceptions=True)

    async def _body(self, device, identifier, key, canceled, stream, first, deadline):
        size, digest = 0, hashlib.sha256()
        try:
            chunk = first
            while True:
                if canceled.is_set():
                    raise asyncio.CancelledError()
                if not isinstance(chunk, bytes) or size + len(chunk) > MAX_BYTES:
                    raise ValueError("invalid audio")
                await self.store.device(device)
                size += len(chunk)
                digest.update(chunk)
                yield chunk
                try:
                    chunk = await self._next(stream, canceled, deadline)
                except StopAsyncIteration:
                    break
            if not size or size % 2:
                raise ValueError("incomplete PCM")
            await self.store.device(device)
            await self.finish(device, identifier, "completed", size, digest.hexdigest())
        except (Exception, asyncio.CancelledError):
            # Never append JSON to audio or report a truncated transfer as complete.
            raise RuntimeError("tts_stream_incomplete") from None
        finally:
            await self.abandon(device, identifier)

    async def abandon(self, device, identifier):
        key = (device, identifier)
        event = self.active.get(key)
        if event is None:
            return
        # A disconnected HTTP request cancels its enclosing AnyIO scope. Finish
        # the transaction before advertising capacity for the next request.
        with anyio.CancelScope(shield=True):
            try:
                await self.finish(
                    device, identifier, "canceled" if event.is_set() else "unknown"
                )
            finally:
                try:
                    stream = self.streams.pop(key, None)
                    if stream is not None:
                        await stream.aclose()
                finally:
                    self.active.pop(key, None)
                    self.idle.set()

    async def close(self):
        self.closed = True
        for event in self.active.values():
            event.set()
        try:
            if self.active:
                await asyncio.wait_for(self.idle.wait(), timeout=5)
        finally:
            close = getattr(self.upstream, "close", None)
            if close is not None:
                await close()
