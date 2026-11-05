"""HTTP adaptation over Backend; business rules do not belong in route handlers."""

import json
import logging
import re
import sqlite3
import time
from collections import OrderedDict
from contextlib import asynccontextmanager
from uuid import UUID, uuid4

import anyio
from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse
from starlette.responses import StreamingResponse

from . import __version__
from .assembly import Backend
from .store import Refused

log = logging.getLogger(__name__)


def _error(status: int, code: str) -> JSONResponse:
    headers = {"Cache-Control": "no-store", "X-Content-Type-Options": "nosniff"}
    if status == 401:
        headers["WWW-Authenticate"] = "Bearer"
    if status == 429:
        headers["Retry-After"] = "60"
    return JSONResponse(
        {"error": code, "requestId": str(uuid4())}, status_code=status, headers=headers
    )


def _integer(text: str) -> int:
    if not re.fullmatch(r"0|[1-9][0-9]{0,18}", text) or int(text) > 2**63 - 1:
        raise Refused(400, "invalid_cursor")
    return int(text)


def _stream(text: str) -> str:
    try:
        if str(UUID(text)) != text:
            raise ValueError()
    except (ValueError, TypeError, AttributeError):
        raise Refused(400, "invalid_stream") from None
    return text


def _pairs(items: list[tuple[str, object]]) -> dict:
    result: dict = {}
    for key, value in items:
        if key in result:
            raise ValueError("duplicate field")
        result[key] = value
    return result


def create_app(backend: Backend) -> FastAPI:
    @asynccontextmanager
    async def lifespan(_: FastAPI):
        try:
            await backend.start()
            yield
        finally:
            await backend.close()

    app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)
    app.state.backend = backend
    peers: OrderedDict[str, tuple[float, int]] = OrderedDict()

    @app.middleware("http")
    async def budget_and_headers(request: Request, call_next):
        # The network peer is used, never an untrusted forwarding header.
        peer = request.client.host if request.client else "local"
        now = time.monotonic()
        began, count = peers.get(peer, (now, 0))
        if now - began >= 60:
            began, count = now, 0
        if count >= 120:
            return _error(429, "rate_limited")
        if len(peers) >= 1024 and peer not in peers:
            peers.popitem(last=False)
        peers[peer] = began, count + 1
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        response.headers["X-Content-Type-Options"] = "nosniff"
        return response

    @app.exception_handler(Refused)
    async def refused(_: Request, error: Refused):
        return _error(error.status, error.code)

    @app.exception_handler(sqlite3.Error)
    async def unavailable(_: Request, error: sqlite3.Error):
        log.warning("cloud_database_unavailable")
        return _error(503, "temporarily_unavailable")

    async def device(request: Request) -> str:
        authorization = request.headers.getlist("authorization")
        if len(authorization) != 1 or not re.fullmatch(
            r"Bearer esc1_[A-Za-z0-9_-]{43}", authorization[0]
        ):
            raise Refused(401, "unauthorized")
        return await backend.store.authenticate(authorization[0][7:])

    @app.get("/api/health")
    async def health():
        await backend.store.health()
        return {"status": "ok", "version": __version__, "domainConnected": False}

    def tts():
        if backend.tts is None:
            raise Refused(503, "tts_disabled")
        return backend.tts

    @app.get("/api/tts/capabilities")
    async def tts_capabilities(device_id: str = Depends(device)):
        return {
            "enabled": backend.tts is not None,
            "mode": "mock" if backend.tts else "off",
            "paidRequestsSupported": False,
        }

    @app.get("/api/tts/requests/{identifier}")
    async def tts_status(identifier: str, device_id: str = Depends(device)):
        return await tts().state(device_id, identifier)

    @app.post("/api/tts/requests/{identifier}/cancel")
    async def tts_cancel(identifier: str, device_id: str = Depends(device)):
        return await tts().cancel(device_id, identifier)

    @app.post("/api/tts/stream")
    async def tts_stream(request: Request, device_id: str = Depends(device)):
        relay = tts()
        if request.query_params:
            raise Refused(400, "invalid_query")
        if (
            request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
            != "application/json"
        ):
            raise Refused(415, "json_required")
        body = bytearray()
        async for part in request.stream():
            if len(body) + len(part) > 65536:
                raise Refused(413, "body_too_large")
            body.extend(part)
        try:
            value = json.loads(body.decode("utf-8"), object_pairs_hook=_pairs)
        except (ValueError, UnicodeError, RecursionError):
            raise Refused(400, "invalid_json") from None
        iterator = await relay.open(device_id, value)

        class AudioResponse(StreamingResponse):
            async def __call__(self, scope, receive, send):
                try:
                    await super().__call__(scope, receive, send)
                finally:

                    async def cleanup():
                        try:
                            await iterator.aclose()
                        finally:
                            await relay.abandon(device_id, value["requestId"])

                    # Starlette cancels an AnyIO scope on disconnect. Shield the
                    # cleanup itself so the ledger commit precedes slot release.
                    with anyio.CancelScope(shield=True):
                        await cleanup()

        return AudioResponse(
            iterator,
            media_type="audio/pcm",
            headers={
                "X-Eidolon-TTS-Mode": "mock",
                "X-Eidolon-Sample-Rate": "44100",
                "Cache-Control": "no-store",
            },
        )

    @app.get("/api/device")
    async def device_status(device_id: str = Depends(device)):
        return await backend.store.device(device_id)

    @app.get("/api/inbox")
    async def inbox(request: Request, device_id: str = Depends(device)):
        items = request.query_params.multi_items()
        if len(items) != len(dict(items)) or any(
            key not in {"after", "limit", "streamId"} for key, _ in items
        ):
            raise Refused(400, "invalid_query")
        query = dict(items)
        stream = _stream(query["streamId"]) if "streamId" in query else None
        return await backend.store.page(
            device_id,
            _integer(query.get("after", "0")),
            _integer(query.get("limit", "50")),
            stream,
        )

    @app.post("/api/inbox/ack")
    async def acknowledge(request: Request, device_id: str = Depends(device)):
        if request.query_params:
            raise Refused(400, "invalid_query")
        if (
            request.headers.get("content-type", "").split(";", 1)[0].strip().lower()
            != "application/json"
        ):
            raise Refused(415, "json_required")
        data = bytearray()
        async for part in request.stream():
            if len(data) + len(part) > 2048:
                raise Refused(413, "body_too_large")
            data.extend(part)
        try:
            value = json.loads(data.decode("utf-8"), object_pairs_hook=_pairs)
        except (ValueError, UnicodeError, RecursionError):
            raise Refused(400, "invalid_json") from None
        if (
            not isinstance(value, dict)
            or set(value) != {"through", "streamId"}
            or type(value["through"]) is not int
        ):
            raise Refused(400, "invalid_ack")
        return await backend.store.acknowledge(
            device_id, value["through"], _stream(value["streamId"])
        )

    # There is no HTTP endpoint for registration, arbitrary task submission, model calls,
    # configuration override, schedule calculation, or the current Android database.
    return app
