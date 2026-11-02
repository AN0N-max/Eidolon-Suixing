import os
from datetime import datetime
from pathlib import Path

import pytest

from eidolon_cloud.domain import DomainUnavailable
from eidolon_cloud.shared_clock import SharedClock


@pytest.mark.parametrize(
    "at,zone,day,next_at",
    [
        (
            "2026-10-28T18:30:00+00:00",
            "Asia/Shanghai",
            "2026-10-28",
            "2026-10-28T19:00:00+00:00",
        ),
        (
            "2026-10-28T18:30:00+00:00",
            "Asia/Tokyo",
            "2026-10-29",
            "2026-10-29T18:00:00+00:00",
        ),
        (
            "2026-10-29T03:00:00+08:00",
            "Asia/Shanghai",
            "2026-10-29",
            "2026-10-30T03:00:00+08:00",
        ),
        (
            "2027-01-01T02:00:00+08:00",
            "Asia/Shanghai",
            "2026-12-31",
            "2027-01-01T03:00:00+08:00",
        ),
        (
            "2028-03-01T02:00:00+08:00",
            "Asia/Shanghai",
            "2028-02-29",
            "2028-03-01T03:00:00+08:00",
        ),
        (
            "2026-03-29T04:30:00+03:00",
            "Europe/Helsinki",
            "2026-03-29",
            "2026-03-30T03:00:00+03:00",
        ),
    ],
)
async def test_real_original_rule_through_cloud_adapter(at, zone, day, next_at):
    artifact = os.environ.get("EIDOLON_TEST_RULE_JAR")
    java = os.environ.get("EIDOLON_TEST_JAVA")
    if not artifact or not java:
        pytest.skip(
            "Build the shared clock and set EIDOLON_TEST_RULE_JAR/EIDOLON_TEST_JAVA for actual Java adapter verification"
        )
    clock = SharedClock(java, Path(artifact))
    result = await clock.read(int(datetime.fromisoformat(at).timestamp() * 1000), zone)
    assert result["displayDay"] == day
    assert result["nextBoundaryMs"] == int(
        datetime.fromisoformat(next_at).timestamp() * 1000
    )


async def test_missing_shared_rule_is_unavailable_instead_of_python_fallback(tmp_path):
    clock = SharedClock("java", tmp_path / "missing.jar")
    with pytest.raises(DomainUnavailable):
        await clock.read(0, "Asia/Shanghai")
