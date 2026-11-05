"""Persistence for cloud infrastructure only: devices, diagnostic jobs and receipts.

No character, relationship, memory, daily-schedule or user-profile schema lives here.
The existing Android database and its backup format are not opened or migrated.
"""

import hashlib
import secrets
import sqlite3
import time
from uuid import uuid4

from .data_access import Database


class Refused(Exception):
    def __init__(self, status: int, code: str):
        super().__init__(code)
        self.status, self.code = status, code


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("utf-8")).hexdigest()


class Store:
    def __init__(self, database: Database):
        self.db = database

    async def initialize(self) -> None:
        def open_schema(db: sqlite3.Connection) -> None:
            tables = {
                row[0]
                for row in db.execute(
                    "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'"
                )
            }
            if tables and "cloud_meta" not in tables:
                raise RuntimeError("Refusing to adopt an unrelated database")
            db.execute(
                "CREATE TABLE IF NOT EXISTS cloud_meta(key TEXT PRIMARY KEY,value TEXT NOT NULL)"
            )
            version = db.execute(
                "SELECT value FROM cloud_meta WHERE key='schema'"
            ).fetchone()
            if (version is None and tables) or (
                version is not None and version[0] not in {"1", "2"}
            ):
                raise RuntimeError("Unsupported cloud infrastructure schema")
            if (
                version is not None
                and version[0] == "2"
                and "cloud_tts_requests" not in tables
            ):
                raise RuntimeError("Missing TTS ledger in schema2")
            db.execute("INSERT OR IGNORE INTO cloud_meta VALUES('schema','1')")
            db.execute(
                "INSERT OR IGNORE INTO cloud_meta VALUES('stream',?)", (str(uuid4()),)
            )
            db.execute("""CREATE TABLE IF NOT EXISTS cloud_devices(
                id TEXT PRIMARY KEY,name TEXT NOT NULL,token_hash TEXT NOT NULL UNIQUE,
                active INTEGER NOT NULL DEFAULT 1 CHECK(active IN (0,1)),
                issued INTEGER NOT NULL DEFAULT 0,stored INTEGER NOT NULL DEFAULT 0,
                CHECK(stored>=0 AND stored<=issued))""")
            db.execute("""CREATE TABLE IF NOT EXISTS cloud_diagnostic_jobs(
                id TEXT PRIMARY KEY,due INTEGER NOT NULL,expires INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING'
                CHECK(status IN ('PENDING','DELIVERED','CANCELED','EXPIRED')),CHECK(expires>due))""")
            db.execute("""CREATE TABLE IF NOT EXISTS cloud_inbox(
                sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,
                job_id TEXT NOT NULL UNIQUE REFERENCES cloud_diagnostic_jobs(id),
                kind TEXT NOT NULL CHECK(kind='DIAGNOSTIC'),text TEXT NOT NULL,created INTEGER NOT NULL)""")
            db.execute(
                "CREATE INDEX IF NOT EXISTS cloud_job_due ON cloud_diagnostic_jobs(status,due)"
            )

        await self.db.write(open_schema)

    async def health(self) -> None:
        await self.db.read(
            lambda db: db.execute(
                "SELECT value FROM cloud_meta WHERE key='schema'"
            ).fetchone()[0]
        )

    async def add_device(self, name: str) -> dict:
        if not name.strip() or len(name) > 80 or any(ord(c) < 32 for c in name):
            raise Refused(400, "invalid_name")
        token = "esc1_" + secrets.token_urlsafe(32)
        device_id = str(uuid4())
        await self.db.write(
            lambda db: (
                db.execute(
                    "INSERT INTO cloud_devices(id,name,token_hash) VALUES(?,?,?)",
                    (device_id, name, token_hash(token)),
                ).rowcount
            )
        )
        return {"deviceId": device_id, "name": name, "token": token}

    async def revoke(self, device_id: str) -> bool:
        return bool(
            await self.db.write(
                lambda db: (
                    db.execute(
                        "UPDATE cloud_devices SET active=0 WHERE id=? AND active=1",
                        (device_id,),
                    ).rowcount
                )
            )
        )

    async def authenticate(self, token: str) -> str:
        row = await self.db.read(
            lambda db: db.execute(
                "SELECT id FROM cloud_devices WHERE token_hash=? AND active=1",
                (token_hash(token),),
            ).fetchone()
        )
        if row is None:
            raise Refused(401, "unauthorized")
        return row[0]

    @staticmethod
    def _device(db: sqlite3.Connection, device_id: str) -> sqlite3.Row:
        row = db.execute(
            "SELECT * FROM cloud_devices WHERE id=? AND active=1", (device_id,)
        ).fetchone()
        if row is None:
            raise Refused(401, "unauthorized")
        return row

    @staticmethod
    def _stream(db: sqlite3.Connection, expected: str | None = None) -> str:
        actual = db.execute(
            "SELECT value FROM cloud_meta WHERE key='stream'"
        ).fetchone()[0]
        if expected is not None and expected != actual:
            raise Refused(409, "stream_mismatch")
        return actual

    async def device(self, device_id: str) -> dict:
        def read(db: sqlite3.Connection) -> dict:
            row = self._device(db, device_id)
            return {
                "deviceId": row["id"],
                "name": row["name"],
                "storedThrough": row["stored"],
                "streamId": self._stream(db),
            }

        return await self.db.read(read)

    async def page(
        self, device_id: str, after: int, limit: int, stream: str | None
    ) -> dict:
        if (
            type(after) is not int
            or not 0 <= after <= 2**63 - 1
            or type(limit) is not int
            or not 1 <= limit <= 100
        ):
            raise Refused(400, "invalid_cursor")
        if after and stream is None:
            raise Refused(400, "stream_required")

        def read_and_issue(db: sqlite3.Connection) -> dict:
            row = self._device(db, device_id)
            actual = self._stream(db, stream)
            if after > row["issued"]:
                raise Refused(409, "cursor_not_issued")
            rows = db.execute(
                "SELECT * FROM cloud_inbox WHERE sequence>? ORDER BY sequence LIMIT ?",
                (after, limit + 1),
            ).fetchall()
            messages = [
                {
                    "sequence": r["sequence"],
                    "id": r["id"],
                    "kind": r["kind"],
                    "text": r["text"],
                    "createdAtMs": r["created"],
                }
                for r in rows[:limit]
            ]
            cursor = messages[-1]["sequence"] if messages else after
            db.execute(
                "UPDATE cloud_devices SET issued=MAX(issued,?) WHERE id=?",
                (cursor, device_id),
            )
            return {
                "streamId": actual,
                "messages": messages,
                "nextCursor": cursor,
                "hasMore": len(rows) > limit,
            }

        return await self.db.write(read_and_issue)

    async def acknowledge(self, device_id: str, through: int, stream: str) -> dict:
        if (
            type(through) is not int
            or not 0 <= through <= 2**63 - 1
            or not isinstance(stream, str)
        ):
            raise Refused(400, "invalid_cursor")

        def acknowledge(db: sqlite3.Connection) -> dict:
            row = self._device(db, device_id)
            actual = self._stream(db, stream)
            if through > row["issued"]:
                raise Refused(409, "cursor_not_issued")
            stored = max(row["stored"], through)
            db.execute(
                "UPDATE cloud_devices SET stored=? WHERE id=?", (stored, device_id)
            )
            return {"streamId": actual, "storedThrough": stored}

        return await self.db.write(acknowledge)

    async def diagnostic(self, job_id: str, due: int, expires: int) -> str:
        if (
            not job_id
            or len(job_id) > 128
            or type(due) is not int
            or type(expires) is not int
            or not 0 <= due < expires <= 2**63 - 1
            or expires - due > 86_400_000
        ):
            raise Refused(400, "invalid_diagnostic")

        def enqueue(db: sqlite3.Connection) -> str:
            row = db.execute(
                "SELECT due,expires FROM cloud_diagnostic_jobs WHERE id=?", (job_id,)
            ).fetchone()
            if row is not None:
                if (row[0], row[1]) != (due, expires):
                    raise Refused(409, "idempotency_conflict")
                return job_id
            db.execute(
                "INSERT INTO cloud_diagnostic_jobs(id,due,expires) VALUES(?,?,?)",
                (job_id, due, expires),
            )
            return job_id

        return await self.db.write(enqueue)

    async def cancel_diagnostic(self, job_id: str) -> bool:
        return bool(
            await self.db.write(
                lambda db: (
                    db.execute(
                        "UPDATE cloud_diagnostic_jobs SET status='CANCELED' WHERE id=? AND status='PENDING'",
                        (job_id,),
                    ).rowcount
                )
            )
        )

    async def deliver_diagnostic(self, now: int | None = None) -> bool:
        at = time.time_ns() // 1_000_000 if now is None else now

        def deliver(db: sqlite3.Connection) -> bool:
            row = db.execute(
                "SELECT * FROM cloud_diagnostic_jobs WHERE status='PENDING' AND due<=? ORDER BY due,id LIMIT 1",
                (at,),
            ).fetchone()
            if row is None:
                return False
            if row["expires"] <= at:
                db.execute(
                    "UPDATE cloud_diagnostic_jobs SET status='EXPIRED' WHERE id=?",
                    (row["id"],),
                )
            else:
                db.execute(
                    "INSERT INTO cloud_inbox(id,job_id,kind,text,created) VALUES(?,?,'DIAGNOSTIC',?,?)",
                    (
                        str(uuid4()),
                        row["id"],
                        "云端框架连接测试：固定测试消息，未调用模型，未改变日程、关系或记忆。",
                        at,
                    ),
                )
                db.execute(
                    "UPDATE cloud_diagnostic_jobs SET status='DELIVERED' WHERE id=?",
                    (row["id"],),
                )
            return True

        return await self.db.write(deliver)
