"""Read the existing Java day-boundary rule via a bounded local process adapter.

No Python version of the 03:00, timezone, year-rollover or DST rule is implemented.
This proves the shared-rule seam; it is not the full schedule/generation adapter.
"""

import asyncio
import json
from pathlib import Path

from .domain import DomainUnavailable


class SharedClock:
    def __init__(self, java: str, jar: Path):
        self.java, self.jar = java, jar
        self._one = asyncio.Semaphore(1)

    async def read(self, at_ms: int, zone: str) -> dict:
        if type(at_ms) is not int or not -(2**63) <= at_ms < 2**63:
            raise ValueError("Invalid epoch milliseconds")
        if (
            not isinstance(zone, str)
            or not zone
            or len(zone) > 80
            or any(ord(c) < 32 for c in zone)
        ):
            raise ValueError("Invalid timezone")
        if not self.jar.is_file():
            raise DomainUnavailable("Shared clock artifact is not built")
        async with self._one:
            try:
                process = await asyncio.create_subprocess_exec(
                    self.java,
                    "-Xms8m",
                    "-Xmx32m",
                    "-jar",
                    str(self.jar),
                    str(at_ms),
                    zone,
                    stdout=asyncio.subprocess.PIPE,
                    stderr=asyncio.subprocess.DEVNULL,
                )
            except OSError:
                raise DomainUnavailable("Shared Java runtime is unavailable") from None
            try:
                output, _ = await asyncio.wait_for(process.communicate(), timeout=5)
                if process.returncode != 0 or len(output) > 2048:
                    raise DomainUnavailable("Original clock rule could not run")
                try:
                    value = json.loads(output)
                except (ValueError, UnicodeError):
                    raise DomainUnavailable("Invalid shared clock response") from None
                if (
                    not isinstance(value, dict)
                    or value.get("schema") != 1
                    or set(value) != {"schema", "displayDay", "nextBoundaryMs"}
                    or not isinstance(value["displayDay"], str)
                    or type(value["nextBoundaryMs"]) is not int
                ):
                    raise DomainUnavailable("Invalid shared clock response")
                return value
            finally:
                if process.returncode is None:
                    process.kill()
                    await process.wait()
