"""Check an explicitly MOCK-only TTS service on loopback; never retry a request."""

import argparse
import hashlib
import json
import os
import urllib.error
import urllib.request
from uuid import uuid4


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        raise ValueError("redirect_refused")


def check(port, token):
    if not 1 <= port <= 65535 or not token:
        raise ValueError("configuration")
    base = f"http://127.0.0.1:{port}"
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(path, value=None):
        body = None if value is None else json.dumps(value).encode()
        return opener.open(
            urllib.request.Request(
                base + path,
                data=body,
                headers={
                    "Authorization": "Bearer " + token,
                    "Content-Type": "application/json",
                },
            ),
            timeout=10,
        )

    def read_json(path):
        with request(path) as response:
            return json.loads(response.read(32769))

    capabilities = read_json("/api/tts/capabilities")
    if capabilities != {
        "enabled": True,
        "mode": "mock",
        "paidRequestsSupported": False,
    }:
        raise ValueError("not_mock")
    identifier = str(uuid4())
    with request(
        "/api/tts/stream",
        {
            "requestId": identifier,
            "text": "人工协议检查",
            "voice": "a" * 32,
            "model": "s2.1-pro-free",
        },
    ) as response:
        if response.headers.get("X-Eidolon-TTS-Mode") != "mock":
            raise ValueError("not_mock")
        audio = response.read(17649)
        if audio != b"\0" * 17648:
            raise ValueError("unexpected_mock_audio")
    state = read_json("/api/tts/requests/" + identifier)
    if not (
        state["mock"] is True
        and state["state"] == "completed"
        and state["bytes"] == len(audio)
        and state["sha256"] == hashlib.sha256(audio).hexdigest()
    ):
        raise ValueError("incomplete_mock_audio")
    return {"ok": True, "mode": "mock", "bytes": len(audio), "paidRequest": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8788)
    args = parser.parse_args()
    try:
        result = check(args.port, os.environ.get("EIDOLON_CLOUD_DEVICE_TOKEN", ""))
    except (ValueError, KeyError, OSError, urllib.error.URLError):
        # Never echo the token, server body or user input.
        print(
            json.dumps(
                {"ok": False, "error": "mock_smoke_failed", "paidRequest": False}
            )
        )
        return 1
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
