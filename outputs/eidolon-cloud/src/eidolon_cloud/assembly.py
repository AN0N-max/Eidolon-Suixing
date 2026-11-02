"""Explicit dependency assembly and one owner of startup/shutdown, as upstream does."""

from .config import Settings
from .data_access import Database
from .domain import DomainPort, UnboundDomain
from .reach_outs import ReachOutRuntime
from .runtime_lock import RuntimeLock
from .store import Store


class Backend:
    def __init__(self, settings: Settings, *, domain: DomainPort | None = None):
        self.settings = settings
        self.database = Database(settings.database_path)
        self.store = Store(self.database)
        self.domain = domain if domain is not None else UnboundDomain()
        self.reach_outs = ReachOutRuntime(self.store, settings.tick_seconds)
        self._runtime_lock = RuntimeLock(settings.data / "runtime.lock")
        self._started = False

    async def start(self) -> None:
        if self._started:
            raise RuntimeError("Backend already started")
        self._runtime_lock.acquire()
        try:
            await self.store.initialize()
            if self.settings.diagnostics:
                self.reach_outs.start_diagnostics()
            self._started = True
        except BaseException:
            self._runtime_lock.release()
            raise

    async def close(self) -> None:
        try:
            await self.reach_outs.close()
            await self.database.close()
        finally:
            self._runtime_lock.release()
            self._started = False
