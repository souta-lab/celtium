#!/usr/bin/env python3
"""Conservatively remove reproducible dependency copies from completed GPU legs.

Dry-run is the default. Logs, crashes, screenshots, framedumps, configuration,
options, worlds and unknown files are evidence/reproduction inputs, never trash.
Only individual mods/*.jar and test-libraries/*.jar may be removed, and only
when a successful calibrated run has a runtime lock, its recorded checksum
matches, and a separate original with the same checksum still exists.
Missing provenance, failed/incomplete runs and symlinks are left alone. There
is no force option. Keep the newest ten run directories untouched by default.

    python3 scripts/gv-prune.py
    python3 scripts/gv-prune.py --apply --keep 20
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys

from prism_validation import shared_game_slot

ROOT = Path(__file__).absolute().parent.parent
DEPENDENCIES = {"mods": "testMods", "test-libraries": "extraTestLibraries"}


class SkipRun(Exception):
    """A run is not safely eligible; do not infer success from absent evidence."""


def checked_path(path: Path, boundary: Path | None = None) -> Path:
    """Verify absolute containment and every ancestor before using an existing path."""
    path = Path(os.path.abspath(path))
    if boundary is not None:
        boundary = Path(os.path.abspath(boundary))
        if path == boundary or not path.is_relative_to(boundary):
            raise SkipRun(f"outside intended directory: {path}")
    for part in (*reversed(path.parents), path):
        if stat.S_ISLNK(part.lstat().st_mode):
            raise SkipRun(f"symlink refused: {part}")
    if path.resolve(strict=True) != path:
        raise SkipRun(f"non-canonical path refused: {path}")
    return path


def read_json(path: Path, boundary: Path) -> dict:
    path = checked_path(path, boundary)
    if not stat.S_ISREG(path.lstat().st_mode):
        raise SkipRun(f"not a regular report: {path}")
    with path.open(encoding="utf-8") as stream:
        data = json.load(stream)
    if not isinstance(data, dict):
        raise ValueError(f"expected a JSON object: {path}")
    return data


def checksum(path: Path) -> str:
    result = hashlib.sha256()
    # O_NOFOLLOW also refuses a final-component symlink substituted after checking.
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
    with os.fdopen(fd, "rb") as stream:
        if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
            raise SkipRun(f"not a regular dependency: {path}")
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def successful_lock(run: Path, runs: Path) -> tuple[dict, dict]:
    outcome = read_json(run / "validation-process.json", runs)
    required = {
        "endedBy": "process-exit", "fixtureStatus": "PASSED",
        "fixtureUncalibrated": False, "validationClean": True,
        "gpuRequirementMet": True, "picturePresent": True,
    }
    if any(type(outcome.get(key)) is not type(value) or outcome[key] != value
           for key, value in required.items()):
        raise SkipRun("process is incomplete, failed, uncalibrated or lacks a required verdict")
    if any(type(outcome.get(key)) is not int or outcome[key] != 0
           for key in ("exitCode", "validationExitCode", "validationMessageCount")):
        raise SkipRun("process exit or validation is not clean")
    logs = checked_path(run / "logs", run)
    if not stat.S_ISDIR(logs.lstat().st_mode):
        raise SkipRun("logs is not a directory")
    fixture = read_json(logs / "celtium-validation.json", run)
    if (fixture.get("status") != "PASSED" or fixture.get("presentedImageVerdict") != "PASSED"
            or any(fixture.get(key) is not True for key in
                   ("sceneGateCalibrated", "sceneGateFloorsEnforced", "sceneCalibrationKeyMatches"))):
        raise SkipRun("fixture is incomplete, failed or uncalibrated")
    picture = outcome.get("pictureFile")
    if not isinstance(picture, str) or Path(picture).is_absolute() or ".." in Path(picture).parts:
        raise SkipRun("missing or unsafe picture path")
    picture_path = checked_path(run / picture, run / "screenshots")
    if not stat.S_ISREG(picture_path.lstat().st_mode):
        raise SkipRun("picture evidence is missing")
    console = checked_path(run / "validation-console.log", run)
    if not stat.S_ISREG(console.lstat().st_mode):
        raise SkipRun("console evidence is missing")

    lock_path = run / "runtime-lock.json"
    if not os.path.lexists(lock_path):
        lock_path = runs / (run.name.removeprefix("run-") + "-lock.json")
    lock = read_json(lock_path, runs)
    if lock.get("isolatedGameDirectory") != str(run) or not isinstance(lock.get("effectiveConfig"), dict):
        raise SkipRun("runtime lock does not identify this run and its effective configuration")
    manifest = {}
    sibling = runs / (run.name.removeprefix("run-") + "-manifest.json")
    if os.path.lexists(sibling):
        manifest = read_json(sibling, runs)
        result = read_json(runs / (run.name.removeprefix("run-") + "-result.json"), runs)
        if type(result.get("exitCode")) is not int or result["exitCode"] != 0 or result.get("accepted") is not True:
            raise SkipRun("runner result does not record an accepted completed run")
    elif os.path.lexists(run / "gv-leg.json"):
        manifest = read_json(run / "gv-leg.json", run)
        if manifest.get("status") not in ("completed", "passed"):
            raise SkipRun("runner manifest does not record a completed run")
    return lock, manifest


def recorded_dependencies(lock: dict, manifest: dict, root: Path) -> dict[str, tuple[str, list[Path]]]:
    """Runtime-lock hashes are authoritative; source hints never override them."""
    records = {}
    for directory, field in DEPENDENCIES.items():
        entries = lock.get(field)
        if not isinstance(entries, list):
            raise SkipRun(f"missing dependency provenance: {field}")
        for entry in entries:
            if not isinstance(entry, dict):
                raise SkipRun(f"invalid dependency provenance: {field}")
            name, sha = entry.get("file"), entry.get("sha256")
            if (not isinstance(name, str) or Path(name).name != name or not name.endswith(".jar")
                    or not isinstance(sha, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", sha)):
                raise SkipRun(f"invalid dependency checksum/name: {field}")
            key = f"{directory}/{name}"
            if key in records:
                raise SkipRun(f"duplicate dependency provenance: {key}")
            sources = []
            if isinstance(entry.get("source"), str):
                source = Path(entry["source"])
                sources.append(source if source.is_absolute() else root / source)
            records[key] = (sha.lower(), sources)

    # Older locks identify original instance mods and the separately supplied fixture.
    instance = lock.get("sourceInstance")
    if isinstance(instance, str) and Path(instance).is_absolute():
        for entry in lock.get("mods", []):
            if not isinstance(entry, dict):
                continue
            key = f"mods/{entry.get('file')}"
            if key in records and records[key][0] == entry.get("sha256"):
                records[key][1].append(Path(instance) / "minecraft" / key)
    fixture = lock.get("fixture")
    if isinstance(fixture, dict):
        key = f"mods/{fixture.get('file')}"
        if key in records and records[key][0] == fixture.get("sha256") and isinstance(fixture.get("source"), str):
            source = Path(fixture["source"])
            records[key][1].append(source if source.is_absolute() else root / source)
    for entry in manifest.get("dependencies", []):
        if not isinstance(entry, dict):
            continue
        key = entry.get("path")
        if key in records and records[key][0] == entry.get("sha256") and isinstance(entry.get("source"), str):
            source = Path(entry["source"])
            if source.is_absolute():
                records[key][1].append(source)
    return records


def original_available(sources: list[Path], sha: str, runs: Path) -> bool:
    for source in sources:
        try:
            source = checked_path(source)
            # Another run's copy is not a durable original: it could itself be pruned.
            if source == runs or any(parent.name.startswith("run-") and parent.parent.name == ".gv-verify"
                                     for parent in source.parents):
                continue
            if stat.S_ISREG(source.lstat().st_mode) and checksum(source) == sha:
                return True
        except (FileNotFoundError, SkipRun):
            continue
    return False


def candidates(run: Path, runs: Path, root: Path) -> list[tuple[Path, str, list[Path]]]:
    try:
        lock, manifest = successful_lock(run, runs)
    except FileNotFoundError as exc:
        raise SkipRun(f"required evidence/provenance is missing: {exc.filename}") from exc
    records = recorded_dependencies(lock, manifest, root)
    result = []
    for directory in DEPENDENCIES:
        folder = run / directory
        if not os.path.lexists(folder):
            continue
        folder = checked_path(folder, run)
        if not stat.S_ISDIR(folder.lstat().st_mode):
            raise SkipRun(f"dependency directory is not a directory: {folder}")
        for path in sorted(folder.iterdir()):
            path = checked_path(path, folder)
            # Preserve unknown files and subdirectories, including disabled mods.
            if not stat.S_ISREG(path.lstat().st_mode) or not path.name.endswith(".jar"):
                continue
            record = records.get(f"{directory}/{path.name}")
            if record is None:
                continue
            sha, sources = record
            if checksum(path) == sha and original_available(sources, sha, runs):
                result.append((path, sha, sources))
    return result


def validation_may_be_active(root: Path, owner: Path) -> bool:
    # The harness uses one owner file across git worktrees. Never reclaim it here;
    # even a stale/invalid owner is a reason to leave evidence alone, not a force flag.
    for lock in (root / ".celtium-work" / "gpu.lock", owner):
        for part in (*reversed(lock.parents), lock):
            try:
                if stat.S_ISLNK(part.lstat().st_mode):
                    raise SkipRun(f"symlink in validation lock path: {part}")
            except FileNotFoundError:
                continue
        if os.path.lexists(lock):
            return True
    return False


def main(argv: list[str] | None = None, *, root: Path = ROOT) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apply", action="store_true", help="remove verified dependency copies (default: dry-run)")
    parser.add_argument("--keep", type=int, default=10, help="leave the newest N run directories untouched (default: 10)")
    args = parser.parse_args(argv)
    if args.keep < 0:
        parser.error("--keep must be zero or greater")
    total = 0
    errors = 0
    try:
        root = checked_path(root)
        runs = checked_path(root / ".gv-verify", root)
        if not stat.S_ISDIR(runs.lstat().st_mode):
            raise ValueError(f"not a run directory: {runs}")
        owner = shared_game_slot(root) / "owner.pid"
        if validation_may_be_active(root, owner):
            print("SKIP: validation owner/legacy GPU lock exists (possibly stale); "
                  "no pruning. The canonical validation harness manages ownership.")
            return 0
        ordered = []
        for run in runs.iterdir():
            if not run.name.startswith("run-"):
                continue
            try:
                run = checked_path(run, runs)
                if stat.S_ISDIR(run.lstat().st_mode):
                    ordered.append(run)
            except SkipRun as exc:
                print(f"SKIP: {exc}")
        ordered.sort(key=lambda path: (path.stat().st_mtime_ns, path.name), reverse=True)
        print(f"{len(ordered)} runs; keeping newest {args.keep}; {'APPLY' if args.apply else 'DRY-RUN'}")
        for run in ordered[args.keep:]:
            try:
                planned = candidates(run, runs, root)
                if not planned:
                    print(f"SKIP {run.name}: no dependency copies have matching hashes and separate originals")
                for path, sha, sources in planned:
                    if validation_may_be_active(root, owner):
                        raise SkipRun("validation owner/legacy GPU lock appeared; stop pruning")
                    # Repeat containment, type and checksum/source checks at deletion time.
                    checked_path(run, runs)
                    checked_path(path, run)
                    if checksum(path) != sha or not original_available(sources, sha, runs):
                        raise SkipRun(f"dependency or original changed: {path}")
                    size = path.lstat().st_size
                    if args.apply:
                        path.unlink()
                    total += size
                    print(f"{'REMOVED' if args.apply else 'WOULD REMOVE'} {path.relative_to(root)} ({size} bytes)")
            except SkipRun as exc:
                print(f"SKIP {run.name}: {exc}")
            except (OSError, ValueError, TypeError) as exc:
                errors += 1
                print(f"ERROR {run.name}: {exc}", file=sys.stderr)
    except (OSError, ValueError, RuntimeError, SkipRun) as exc:
        errors += 1
        print(f"ERROR: {exc}", file=sys.stderr)
    # Payload bytes, not a claim about physical free space (hardlinks/compression may differ).
    print(f"{'Removed' if args.apply else 'Planned'} dependency payload: {total} bytes; errors: {errors}")
    return 1 if errors else 0


if __name__ == "__main__":
    raise SystemExit(main())
