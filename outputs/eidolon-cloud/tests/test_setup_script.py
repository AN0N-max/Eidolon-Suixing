"""Deployment safety checks, with no network, pip or process mutations."""

import importlib.util
import io
import zipfile
from pathlib import Path

import pytest

SPEC = importlib.util.spec_from_file_location(
    "cloud_setup", Path(__file__).parents[1] / "deploy/eidolon-cloud-setup.py"
)
setup = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(setup)


def archive(monkeypatch, files):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w") as package:
        for name, body in files:
            package.writestr(name, body)
    data = output.getvalue()
    monkeypatch.setattr(setup, "SHA256", setup.digest(data))
    return data


def test_bad_checksum_leaves_install_empty(tmp_path):
    with pytest.raises(RuntimeError, match="checksum"):
        setup.extract_preserving(b"invalid", tmp_path)
    assert list(tmp_path.iterdir()) == []


def test_existing_changes_abort_before_any_file_is_added(tmp_path, monkeypatch):
    original = tmp_path / "eidolon-cloud/existing.py"
    original.parent.mkdir()
    original.write_bytes(b"user's change")
    data = archive(
        monkeypatch,
        [("eidolon-cloud/new.py", "new"), ("eidolon-cloud/existing.py", "upstream")],
    )
    with pytest.raises(RuntimeError, match="Existing source differs"):
        setup.extract_preserving(data, tmp_path)
    assert original.read_bytes() == b"user's change"
    assert not (original.parent / "new.py").exists()


def test_same_package_is_idempotent(tmp_path, monkeypatch):
    data = archive(monkeypatch, [("eidolon-cloud/a.py", "a")])
    root = setup.extract_preserving(data, tmp_path)
    file = root / "a.py"
    before = file.stat().st_mtime_ns
    assert setup.extract_preserving(data, tmp_path) == root
    assert file.stat().st_mtime_ns == before


@pytest.mark.parametrize(
    "name",
    [
        "eidolon-cloud/../../escape",
        "/eidolon-cloud/a",
        "other/a",
        "eidolon-cloud/..\\escape",
    ],
)
def test_archive_escape_is_refused_before_writes(tmp_path, monkeypatch, name):
    data = archive(monkeypatch, [("eidolon-cloud/safe", "ok"), (name, "no")])
    with pytest.raises(RuntimeError, match="Unsafe"):
        setup.extract_preserving(data, tmp_path)
    assert list(tmp_path.iterdir()) == []


def test_runtime_pid_must_match_executable_workdir_and_action(tmp_path):
    source = tmp_path / "source"
    source.mkdir()
    entry = str(source / ".venv/bin/eidolon-cloud")
    assert setup.matches_runtime([entry, "serve"], source, source)
    assert setup.matches_runtime(["python", entry, "serve"], source, source)
    assert not setup.matches_runtime([entry, "serve"], tmp_path, source)
    assert not setup.matches_runtime(["other", "serve"], source, source)
    assert not setup.matches_runtime([entry, "status"], source, source)


def test_previous_installer_detection(tmp_path):
    body = (
        "cd $HOME/eidolon-cloud-dev1; python -m pip install x; touch dependencies.ready"
    )
    assert setup.legacy_installer(["bash", "-c", body], tmp_path, tmp_path)
    assert setup.legacy_installer(
        ["python", "-m", "pip", "install", "x"], tmp_path, tmp_path
    )
    assert not setup.legacy_installer(
        ["python", "-m", "pip", "install", "x"], tmp_path.parent, tmp_path
    )
    assert not setup.legacy_installer(["bash", "-c", "echo safe"], tmp_path, tmp_path)
