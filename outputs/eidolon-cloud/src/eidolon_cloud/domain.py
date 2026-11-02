"""The boundary to existing Android business logic; no reimplementation of its rules.

Schedule selection, sleep/bath/busy judgement, relationship, memory and generation
remain in the existing Java modules. The eventual shared-core/native adapter belongs
behind this interface. No real task is admitted until that adapter is implemented.
"""

from dataclasses import dataclass
from typing import Any, Mapping, Protocol


class DomainUnavailable(RuntimeError):
    pass


@dataclass(frozen=True)
class LocalDecision:
    task_id: str
    character_id: str
    conversation_id: str
    config_revision: int
    ownership_generation: int
    due_ms: int
    expires_ms: int
    # This is an input contract, not another set of server-side settings/defaults.
    configuration: Mapping[str, Any]


class DomainPort(Protocol):
    async def run(self, decision: LocalDecision) -> None:
        """Delegate an admitted task to the original shared business core."""
        ...


class UnboundDomain:
    async def run(self, decision: LocalDecision) -> None:
        raise DomainUnavailable("Existing business-core adapter is not connected")
