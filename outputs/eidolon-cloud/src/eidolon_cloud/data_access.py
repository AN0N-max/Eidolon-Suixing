"""One serialized transactional writer and a small pool for snapshot reads.

Architecture inspired by upstream's responsibility boundary. This implementation is
written for Suixing and imports no upstream application or domain module.
"""

import asyncio
import sqlite3
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Callable, TypeVar

T = TypeVar("T")


class Database:
    def __init__(self, path: Path):
        path.parent.mkdir(parents=True, exist_ok=True)
        self.path = path.resolve()
        self._writer = ThreadPoolExecutor(
            max_workers=1, thread_name_prefix="cloud-write"
        )
        self._readers = ThreadPoolExecutor(
            max_workers=2, thread_name_prefix="cloud-read"
        )
        self._closed = False
        self._write_slots = asyncio.Semaphore(64)

    def _connect(self, read_only: bool) -> sqlite3.Connection:
        target = self.path.as_uri() + "?mode=ro" if read_only else str(self.path)
        connection = sqlite3.connect(
            target, uri=read_only, timeout=5, isolation_level=None
        )
        connection.row_factory = sqlite3.Row
        connection.execute("PRAGMA foreign_keys=ON")
        if read_only:
            connection.execute("PRAGMA query_only=ON")
        return connection

    def _run(self, body: Callable[[sqlite3.Connection], T], writing: bool) -> T:
        connection = self._connect(not writing)
        try:
            if writing:
                connection.execute("PRAGMA journal_mode=WAL")
                connection.execute("PRAGMA synchronous=FULL")
            connection.execute("BEGIN IMMEDIATE" if writing else "BEGIN")
            try:
                result = body(connection)
                connection.commit()
                return result
            except BaseException:
                connection.rollback()
                raise
        finally:
            connection.close()

    async def write(self, body: Callable[[sqlite3.Connection], T]) -> T:
        if self._closed:
            raise RuntimeError("Database is closed")
        await self._write_slots.acquire()
        try:
            if self._closed:
                raise RuntimeError("Database is closed")
            future = asyncio.get_running_loop().run_in_executor(
                self._writer, self._run, body, True
            )
        except BaseException:
            self._write_slots.release()
            raise

        # The queue slot belongs to the transaction, not its cancelable HTTP awaiter.
        def finished(result):
            self._write_slots.release()
            if not result.cancelled():
                result.exception()

        future.add_done_callback(finished)
        return await asyncio.shield(future)

    async def read(self, body: Callable[[sqlite3.Connection], T]) -> T:
        if self._closed:
            raise RuntimeError("Database is closed")
        return await asyncio.get_running_loop().run_in_executor(
            self._readers, self._run, body, False
        )

    async def close(self) -> None:
        if self._closed:
            return
        self._closed = True
        await asyncio.to_thread(self._writer.shutdown, wait=True)
        await asyncio.to_thread(self._readers.shutdown, wait=True)
