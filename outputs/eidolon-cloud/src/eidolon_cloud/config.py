"""Runtime settings, separate from existing character/schedule/business settings."""

import os
from dataclasses import dataclass
from pathlib import Path
from typing import Mapping


@dataclass(frozen=True)
class Settings:
    data: Path
    host: str = "127.0.0.1"
    port: int = 8787
    diagnostics: bool = False
    tick_seconds: float = 1.0
    tts_mock: bool = False

    def __post_init__(self) -> None:
        if self.host not in {"127.0.0.1", "::1"}:
            raise ValueError(
                "The HTTP runtime must stay behind a local HTTPS proxy/tunnel"
            )
        if not 1 <= self.port <= 65535 or self.tick_seconds <= 0:
            raise ValueError("Invalid runtime port or polling interval")

    @classmethod
    def environment(cls, env: Mapping[str, str] | None = None) -> "Settings":
        values = os.environ if env is None else env
        data = values.get("EIDOLON_CLOUD_DATA", "").strip()
        if not data:
            raise ValueError(
                "Set EIDOLON_CLOUD_DATA to a dedicated cloud data directory"
            )
        diagnostic = values.get("EIDOLON_CLOUD_DIAGNOSTICS", "0")
        mock = values.get("EIDOLON_CLOUD_TTS_MOCK", "0")
        if mock not in {"0", "1"}:
            raise ValueError("EIDOLON_CLOUD_TTS_MOCK must be 0 or 1")
        if diagnostic not in {"0", "1"}:
            raise ValueError("EIDOLON_CLOUD_DIAGNOSTICS must be 0 or 1")
        return cls(
            Path(data),
            values.get("EIDOLON_CLOUD_HOST", "127.0.0.1"),
            int(values.get("EIDOLON_CLOUD_PORT", "8787")),
            diagnostic == "1",
            tts_mock=mock == "1",
        )

    @property
    def database_path(self) -> Path:
        # A separate infrastructure DB; never the phone's eidolon.db / backup v18.
        return self.data / "cloud-control.sqlite3"
