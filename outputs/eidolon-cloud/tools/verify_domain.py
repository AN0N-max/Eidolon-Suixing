"""Verify protected existing rule sources against the reviewed Android 1.22 baseline."""

import argparse
import hashlib
import json
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--android-source", type=Path, required=True)
    args = parser.parse_args()
    root = args.android_source.resolve()
    baseline = json.loads(
        (Path(__file__).resolve().parents[1] / "domain-baseline.json").read_text(
            encoding="utf-8-sig"
        )
    )
    failures = []
    for item in baseline["files"]:
        path = (root / item["path"]).resolve()
        if (
            not path.is_relative_to(root)
            or not path.is_file()
            or hashlib.sha256(path.read_bytes()).hexdigest() != item["sha256"]
        ):
            failures.append(item["path"])
    if failures:
        print("Protected source differences: " + ", ".join(failures))
        return 1
    print(
        f"Protected Android sources unchanged: {len(baseline['files'])}; version {baseline['androidVersion']}, database/backup v{baseline['databaseVersion']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
