"""Create a reviewable allowlisted source package, excluding all private/runtime files."""

import argparse
import hashlib
import json
import zipfile
from pathlib import Path


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--wheel", type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if args.output.exists():
        raise SystemExit("Output exists; keep older packages and select a new name")
    files = [
        root / name
        for name in (
            "README.md",
            "LICENSE",
            "pyproject.toml",
            ".gitignore",
            ".env.example",
            "domain-baseline.json",
        )
    ]
    for directory in ("src", "tests", "tools", "docs", "shared-clock"):
        files.extend(
            path
            for path in (root / directory).rglob("*")
            if path.is_file()
            and path.suffix in {".py", ".ps1", ".md", ".java"}
            and "__pycache__" not in path.parts
        )
    entries = [
        ("eidolon-cloud/" + path.relative_to(root).as_posix(), path) for path in files
    ]
    if args.wheel:
        wheel = args.wheel.resolve(strict=True)
        if wheel.suffix != ".whl":
            raise SystemExit("Expected a wheel")
        entries.append(("eidolon-cloud/artifacts/" + wheel.name, wheel))
    manifest = {
        name: {
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "bytes": path.stat().st_size,
        }
        for name, path in entries
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, "x", zipfile.ZIP_DEFLATED) as archive:
        for name, path in sorted(entries):
            archive.write(path, name)
        archive.writestr(
            "eidolon-cloud/source-manifest.json",
            json.dumps(manifest, ensure_ascii=False, indent=2),
        )
    with zipfile.ZipFile(args.output) as archive:
        if archive.testzip() is not None:
            raise SystemExit("Archive verification failed")
        for name, details in manifest.items():
            if hashlib.sha256(archive.read(name)).hexdigest() != details["sha256"]:
                raise SystemExit("Archive content hash mismatch")
    print(
        json.dumps(
            {
                "file": str(args.output.resolve()),
                "files": len(entries),
                "sha256": hashlib.sha256(args.output.read_bytes()).hexdigest(),
            },
            ensure_ascii=False,
        )
    )


if __name__ == "__main__":
    main()
