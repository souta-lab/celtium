#!/usr/bin/env python3
"""Stage a CurseForge pack's mods for testing, inside a disk budget, and report the transformer surface.

Two things make a full pack different from a handful of test mods, and this script exists for both.

The first is disk. E2E is 270 mods and RLCraft is 187; a shared download cache plus two instances is
hundreds of megabytes of jars and gigabytes of game directory. So the plan is sized with a HEAD request
per mod *before* anything is written. HEAD may under-report or omit sizes, so every streaming download
has the same hard byte budget, preserves 1 GiB filesystem free, and either renames a complete temporary
file into place or cleans it on failure. A staging run that fills the disk is worse than no staging run.

The second is that a pack's compatibility is decided before the game draws anything, by what its mods
transform. A jar that declares ``FMLCorePlugin`` installs a class transformer; one that ships an
``*_at.cfg`` or ``*.accesswidener`` rewrites field and method access; one that ships a mixin config adds
its own injections. Those are the mods that can collide with the renderer and with the platform, and the
script reports them per mod so the surface is read from real artifacts instead of guessed from names.

The pack archive itself is only ever read. Mods are written under ``--out`` and nowhere else.

Usage:
    pack_stage.py --pack <pack.zip> --out <dir> --budget-gb 8 [--only SUBSTR] [--dry-run]

Exit codes: 0 staged (or planned, for --dry-run); 2 usage, unsafe manifest URL or unreadable pack;
3 the preflight or streaming download exceeded the budget; 4 a download failed (the report is still
written, listing what landed). Fetch URLs must be trusted HTTPS *.forgecdn.net hosts (including redirects).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sys
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

USER_AGENT = {"User-Agent": "celtium-pack-stage/1.0 (renderer compatibility staging)"}
CORE_PLUGIN = re.compile(r"FMLCorePlugin:\s*(\S+)")
CORE_PLUGIN_MOD = "FMLCorePluginContainsFMLMod"


class PackError(Exception):
    """The archive is not a pack this script can plan from. main turns this into exit 2.

    A SystemExit carrying a message exits 1, not the documented code, so the failure is raised as a
    normal exception and the exit code is set at the one place that owns it.
    """


class BudgetExceeded(Exception):
    """A streaming download would exceed the caller's absolute byte budget."""


def validate_url(url: str) -> urllib.parse.SplitResult:
    """Only fetch the official HTTPS CurseForge CDN, never arbitrary manifest URLs."""
    parsed = urllib.parse.urlsplit(url)
    host = (parsed.hostname or "").lower()
    if (parsed.scheme != "https" or not host.endswith(".forgecdn.net") or
            parsed.username or parsed.password or parsed.port not in (None, 443)):
        raise PackError(f"untrusted mod download URL: {url[:200]}")
    return parsed


class ForgeCdnRedirectHandler(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        validate_url(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


FETCHER = urllib.request.build_opener(ForgeCdnRedirectHandler())


def download_bounded(url: str, target: Path, remaining: int) -> int:
    """Download atomically and refuse to write even 1 byte beyond the budget."""
    validate_url(url)
    partial = target.with_name(target.name + ".part")
    if partial.exists() or partial.is_symlink():
        raise PackError(f"existing download partial must be investigated: {partial}")
    size = 0
    try:
        req = urllib.request.Request(url, headers=USER_AGENT)
        with FETCHER.open(req, timeout=300) as response, partial.open("xb") as dest:
            while True:
                chunk = response.read(1 << 20)
                if not chunk:
                    break
                if len(chunk) > remaining - size:
                    raise BudgetExceeded(f"{target.name}: remaining disk budget exhausted")
                if shutil.disk_usage(target.parent).free < len(chunk) + 1024 ** 3:
                    raise BudgetExceeded(f"{target.name}: preserving 1 GiB disk free")
                dest.write(chunk)
                size += len(chunk)
        if not size:
            raise PackError(f"{target.name}: empty download")
        partial.replace(target)
        return size
    finally:
        partial.unlink(missing_ok=True)


def read_manifest(pack: Path) -> dict:
    """The pack's own manifest, read out of the archive without extracting it."""
    try:
        z = zipfile.ZipFile(pack)
    except zipfile.BadZipFile as e:
        raise PackError(f"{pack}: not a readable zip ({e})") from e
    with z:
        if "manifest.json" not in z.namelist():
            raise PackError(f"{pack}: no manifest.json (not a CurseForge pack archive?)")
        return json.loads(z.read("manifest.json"))


def plan_entries(manifest: dict, only: list[str] | None) -> list[dict]:
    """One entry per manifest file, with the name it would be saved as."""
    out = []
    for f in manifest.get("files", []):
        if not f.get("required", True):
            continue
        url = f.get("downloadUrl") or ""
        if url:
            parsed = validate_url(url)
            name = urllib.parse.unquote(parsed.path.rsplit("/", 1)[-1])
        else:
            name = f"project{f['projectID']}-{f['fileID']}.jar"
        if (not name.lower().endswith(".jar") or name in (".", "..") or
                any(c in name for c in "/\\\x00") or
                any(ord(c) < 32 for c in name)):
            raise PackError(f"unsafe mod jar filename: {name[:100]!r}")
        if only and not any(s.lower() in name.lower() for s in only):
            continue
        out.append({"name": name, "url": url, "projectID": f["projectID"], "fileID": f["fileID"]})
    if len({e["name"].casefold() for e in out}) != len(out):
        raise PackError("duplicate downloaded jar basename would overwrite another mod")
    return out


def head_size(url: str) -> int | None:
    """Content-Length without downloading, so the budget is decided before any bytes land."""
    try:
        req = urllib.request.Request(url, headers=USER_AGENT, method="HEAD")
        validate_url(url)
        with FETCHER.open(req, timeout=30) as r:
            n = r.headers.get("Content-Length")
            return int(n) if n else None
    except Exception:
        return None


def inspect_jar(path: Path) -> dict:
    """What this jar asks the platform to do: transformers, access rewrites, mixin configs."""
    info = {"coremod": None, "containsMod": False, "accessTransformers": [], "mixinConfigs": []}
    try:
        with zipfile.ZipFile(path) as z:
            names = z.namelist()
            if "META-INF/MANIFEST.MF" in names:
                mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace")
                m = CORE_PLUGIN.search(mf)
                if m:
                    info["coremod"] = m.group(1)
                info["containsMod"] = CORE_PLUGIN_MOD in mf
            info["accessTransformers"] = [n for n in names if n.endswith("_at.cfg") or n.endswith(".accesswidener")]
            info["mixinConfigs"] = [n for n in names if n.endswith("mixins.json")]
    except Exception as e:
        info["error"] = str(e)[:80]
    return info


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pack", type=Path, required=True, help="The pack archive; it is only read")
    ap.add_argument("--out", type=Path, required=True, help="Where the staged mods go")
    ap.add_argument("--budget-gb", type=float, default=4.0, help="Refuse to stage if the plan exceeds this")
    ap.add_argument("--only", action="append", default=[], help="Stage only names containing this (repeatable)")
    ap.add_argument("--dry-run", action="store_true", help="Size the plan and print it, write nothing")
    ap.add_argument("--report", type=Path, default=None, help="Default: <out>/stage-report.json")
    args = ap.parse_args()

    if not args.pack.is_file():
        print(f"no such pack: {args.pack}", file=sys.stderr)
        return 2

    try:
        manifest = read_manifest(args.pack)
    except PackError as e:
        print(e, file=sys.stderr)
        return 2
    try:
        entries = plan_entries(manifest, args.only or None)
    except (PackError, KeyError, ValueError, TypeError) as e:
        print(f"unusable mod manifest: {e}", file=sys.stderr)
        return 2
    if not (0 < args.budget_gb <= 1000):
        print("--budget-gb must be positive and finite", file=sys.stderr)
        return 2
    if not entries:
        print("nothing to stage (no matching manifest entries)", file=sys.stderr)
        return 2

    mods_dir = args.out / "mods"
    report_path = args.report or (args.out / "stage-report.json")

    print(f"pack      : {args.pack}")
    print(f"entries   : {len(entries)} of {len(manifest.get('files', []))} manifest files")
    print(f"out       : {args.out}")

    # Size the whole plan before writing anything, so the budget is a decision and not a discovery.
    planned = 0
    unknown = 0
    for e in entries:
        target = mods_dir / e["name"]
        if target.is_symlink():
            print(f"REFUSING: existing mod is a symlink: {target}", file=sys.stderr)
            return 2
        if target.is_file():
            e["bytes"] = target.stat().st_size
            e["state"] = "present"
            planned += e["bytes"]
            continue
        n = head_size(e["url"]) if e["url"] else None
        e["bytes"] = n
        e["state"] = "planned"
        if n is None:
            unknown += 1
        else:
            planned += n

    budget = int(args.budget_gb * (1 << 30))
    print(f"plan      : {planned / (1 << 20):.1f} MiB"
          + (f" + {unknown} of unknown size" if unknown else "")
          + f"  (budget {args.budget_gb:.1f} GiB)")
    if planned > budget:
        print(f"REFUSING: the plan exceeds the budget by {(planned - budget) / (1 << 20):.1f} MiB",
              file=sys.stderr)
        return 3
    if unknown:
        print(f"note      : {unknown} entries have no Content-Length; the real total may be larger",
              file=sys.stderr)

    if args.dry_run:
        for e in entries:
            size = f"{e['bytes'] / 1024:.0f} KiB" if e["bytes"] else "?"
            print(f"  {size:>10s}  {e['name']}")
        return 0

    mods_dir.mkdir(parents=True, exist_ok=True)
    failed = 0
    consumed = sum(e["bytes"] for e in entries if e["state"] == "present")
    for e in entries:
        target = mods_dir / e["name"]
        if e["state"] == "present":
            continue
        if not e["url"]:
            # RLCraft's manifest carries no URL: the file cannot be fetched from it alone.
            e["state"] = "no-url"
            failed += 1
            continue
        try:
            e["bytes"] = download_bounded(e["url"], target, budget - consumed)
            consumed += e["bytes"]
            e["state"] = "staged"
        except BudgetExceeded as ex:
            e["state"] = "budget-exceeded"
            e["error"] = str(ex)[:160]
            failed += 1
            print(f"REFUSING: {ex}", file=sys.stderr)
            break
        except Exception as ex:
            e["state"] = "failed"
            e["error"] = str(ex)[:80]
            failed += 1

    # The transformer surface is read from the jars that actually landed.
    for e in entries:
        target = mods_dir / e["name"]
        if target.is_file():
            e["sha256"] = sha256(target)
            e.update(inspect_jar(target))

    coremods = [e for e in entries if e.get("coremod")]
    ats = [e for e in entries if e.get("accessTransformers")]
    mixins = [e for e in entries if e.get("mixinConfigs")]
    report = {
        "pack": str(args.pack),
        "out": str(args.out),
        "entries": len(entries),
        "staged": sum(1 for e in entries if e.get("state") == "staged"),
        "present": sum(1 for e in entries if e.get("state") == "present"),
        "failed": failed,
        "bytesOnDisk": sum(e.get("bytes") or 0 for e in entries if (mods_dir / e["name"]).is_file()),
        "coremods": [{"name": e["name"], "plugin": e["coremod"], "containsMod": e.get("containsMod")} for e in coremods],
        "accessTransformers": [{"name": e["name"], "entries": e["accessTransformers"]} for e in ats],
        "mixinConfigs": [{"name": e["name"], "entries": e["mixinConfigs"]} for e in mixins],
        "files": entries,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"staged    : {report['staged']} new, {report['present']} already present, {failed} not fetched")
    print(f"on disk   : {report['bytesOnDisk'] / (1 << 20):.1f} MiB")
    print(f"report    : {report_path}")
    print(f"transformers: {len(coremods)} coremods, {len(ats)} with access transformers, {len(mixins)} with mixin configs")
    for c in report["coremods"]:
        print(f"  coremod  {c['name']:44s} {c['plugin']}")
    for a in report["accessTransformers"]:
        print(f"  at       {a['name']:44s} {','.join(x.split('/')[-1] for x in a['entries'])}")
    return 3 if any(e.get("state") == "budget-exceeded" for e in entries) else (4 if failed else 0)


if __name__ == "__main__":
    sys.exit(main())
