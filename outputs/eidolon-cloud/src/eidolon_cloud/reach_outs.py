"""Infrastructure worker lifecycle; actual reach-out policy stays behind DomainPort.

This stage executes only explicit diagnostic jobs, never character business jobs.
It does not decide schedules, affinity, quiet hours, busy states or contact frequency.
"""

import asyncio
import logging
import sqlite3

from .store import Store

log = logging.getLogger(__name__)


class ReachOutRuntime:
    def __init__(self, store: Store, interval: float):
        self.store, self.interval = store, interval
        self.task: asyncio.Task | None = None

    def start_diagnostics(self) -> None:
        if self.task is not None:
            raise RuntimeError("Runtime already started")
        self.task = asyncio.create_task(self._diagnostic_loop())

    async def _diagnostic_loop(self) -> None:
        while True:
            try:
                for _ in range(10):
                    if not await self.store.deliver_diagnostic():
                        break
            except sqlite3.Error:
                log.warning("cloud_diagnostic_database_unavailable")
            await asyncio.sleep(self.interval)

    async def close(self) -> None:
        if self.task is not None:
            self.task.cancel()
            await asyncio.gather(self.task, return_exceptions=True)
            self.task = None
