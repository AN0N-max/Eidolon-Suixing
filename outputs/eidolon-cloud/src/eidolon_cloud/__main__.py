"""Local administration and a single-worker server. No model/network service is opened."""

import argparse
import asyncio
import json
import logging
import os
from datetime import datetime, timezone

from .assembly import Backend
from .config import Settings
from .store import Refused


async def administer(settings: Settings, args: argparse.Namespace) -> dict:
    # Administration uses the same store/transactions but does not start a scheduler.
    backend = Backend(settings)
    try:
        await backend.store.initialize()
        if args.command == "device-add":
            return await backend.store.add_device(args.name)
        if args.command == "device-revoke":
            return {"revoked": await backend.store.revoke(args.id)}
        if args.command == "diagnostic-cancel":
            return {"canceled": await backend.store.cancel_diagnostic(args.id)}
        if args.command == "diagnostic-add":
            if not settings.diagnostics:
                raise Refused(409, "diagnostics_disabled")
            due = datetime.fromisoformat(args.due.replace("Z", "+00:00"))
            if due.tzinfo is None:
                raise ValueError("Use a timestamp with a timezone")
            at = int(due.timestamp() * 1000)
            now = int(datetime.now(timezone.utc).timestamp() * 1000)
            if not now - 300_000 <= at <= now + 86_400_000:
                raise ValueError("Diagnostic due time outside the allowed range")
            return {"taskId": await backend.store.diagnostic(args.id, at, at + 900_000)}
        raise ValueError("Unknown command")
    finally:
        await backend.close()


def main() -> None:
    os.umask(0o077)
    parser = argparse.ArgumentParser(
        description="Eidolon Suixing cloud framework (business adapter not yet connected)"
    )
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("serve")
    sub.add_parser("device-add").add_argument("name")
    sub.add_parser("device-revoke").add_argument("id")
    command = sub.add_parser("diagnostic-add")
    command.add_argument("id")
    command.add_argument("due", help="Timezone-aware ISO timestamp")
    sub.add_parser("diagnostic-cancel").add_argument("id")
    args = parser.parse_args()
    try:
        settings = Settings.environment()
        if args.command == "serve":
            import uvicorn

            from .app import create_app

            # No forwarding-header trust. TLS is provided outside this loopback listener.
            uvicorn.run(
                create_app(Backend(settings)),
                host=settings.host,
                port=settings.port,
                workers=1,
                proxy_headers=False,
                access_log=False,
                server_header=False,
                limit_concurrency=16,
                backlog=32,
                timeout_keep_alive=5,
            )
        else:
            # A newly provisioned token is returned only on this local console, once.
            print(
                json.dumps(asyncio.run(administer(settings, args)), ensure_ascii=False)
            )
    except Refused as error:
        parser.exit(2, "cloud_command_refused: " + error.code + "\n")
    except (ValueError, OSError):
        parser.exit(2, "Invalid runtime configuration/command; see README\n")
    except Exception:
        logging.error(
            "cloud_command_failed (credentials and request bodies are not logged)"
        )
        parser.exit(1)


if __name__ == "__main__":
    main()
