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

A manifest entry may carry its own ``downloadUrl`` (the ordinary CurseForge case) or be a bare
``(projectID, fileID)`` pin, which is what RLCraft 2.9.3 ships for all 187 entries. A pin is never
guessed at from its name or its neighbours: it is filled in only from an explicit ``--resolve-index``
produced by ``scripts/modpack-compat/cf_file_index.py`` and re-validated here (approved HTTPS CDN
host, plain single-component name, and - when the index records one - a matching archive hash).
Without an index, or for any pin the index did not resolve, the entry is still reported as ``no-url``.

Ten of RLCraft's 187 pins resolve to resource packs rather than mod jars. Those are never written
into ``mods/``: with ``--resourcepacks-out`` they are fetched into that directory under the same
budget and atomicity rules, and without it they are reported as not staged. Either way the run does
not pretend a partial install is a complete one.

Usage:
    pack_stage.py --pack <pack.zip> --out <dir> --budget-gb 8 [--only SUBSTR] [--dry-run]
                  [--resolve-index <index.json>] [--resourcepacks-out <dir>]

Exit codes: 0 staged (or planned, for --dry-run); 2 usage, unsafe manifest URL, unusable resolve
index or unreadable pack; 3 the preflight or streaming download exceeded the budget; 4 a download
failed (the report is still written, listing what landed); 5 every fetch that was attempted landed,
but the pack also pins artifacts that are not mod jars and this run was not asked to stage them.
Fetch URLs must be trusted HTTPS *.forgecdn.net hosts (including redirects).
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


def load_resolve_index(path: Path, pack: Path) -> dict:
    """An explicit ``(projectID, fileID) -> {url, name, bytes, kind}`` map for a pack with no URLs.

    The index is a reviewed input, not an authority: every URL must still pass the same
    ``validate_url`` gate as a manifest URL, every name must be a plain single-component name, the
    name must agree with the URL it came from, and if the index records the archive it was built
    from that hash must match this pack. Anything else is a usage error, not a silent substitution.
    """
    try:
        index = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError) as e:
        raise PackError(f"{path}: unreadable resolve index ({e})") from e
    if not isinstance(index, dict) or index.get("schema") != 1 or not isinstance(index.get("entries"), list):
        raise PackError(f"{path}: not a resolve index (expected schema 1 with an entries list)")
    recorded = index.get("packSha256")
    if recorded:
        actual = sha256(pack)
        if recorded != actual:
            raise PackError(f"{path}: resolve index was built from a different archive "
                            f"({recorded[:12]}... != {actual[:12]}...)")
    out = {}
    for e in index["entries"]:
        if not isinstance(e, dict) or e.get("state") != "ok":
            continue
        pid, fid, url = e.get("projectID"), e.get("fileID"), e.get("url")
        if not isinstance(pid, int) or not isinstance(fid, int) or not isinstance(url, str):
            raise PackError(f"{path}: malformed resolved entry: {e!r}")
        parsed = validate_url(url)
        name = urllib.parse.unquote(parsed.path.rsplit("/", 1)[-1])
        if name != e.get("name"):
            raise PackError(f"{path}: resolved name disagrees with its URL: {e.get('name')!r} vs {name!r}")
        if (not name or name in (".", "..") or any(c in name for c in "/\\\x00") or
                any(ord(c) < 32 for c in name)):
            raise PackError(f"{path}: unsafe resolved file name: {name[:100]!r}")
        # A pin may resolve to something that is not a mod jar (RLCraft pins 10 resource packs).
        # That is a kind, not an error - but it must never be written into mods/.
        kind = e.get("kind") or ("jar" if name.lower().endswith(".jar") else "other")
        if kind not in ("jar", "other"):
            raise PackError(f"{path}: unknown artifact kind {kind!r} for pin {pid}/{fid}")
        if kind == "jar" and not name.lower().endswith(".jar"):
            raise PackError(f"{path}: entry claims to be a mod jar but is not: {name[:100]!r}")
        if (pid, fid) in out:
            raise PackError(f"{path}: duplicate pin {pid}/{fid} in resolve index")
        size = e.get("filesize")
        out[(pid, fid)] = {"url": url, "name": name, "kind": kind,
                           "bytes": size if isinstance(size, int) and size > 0 else None}
    if not out:
        raise PackError(f"{path}: resolve index contains no resolved entries")
    return out


def plan_entries(manifest: dict, only: list[str] | None, resolve_index: dict | None = None) -> list[dict]:
    """One entry per manifest file, with the name it would be saved as.

    A pin without a manifest URL is filled in only from ``resolve_index``; when the index has
    nothing for it the entry keeps an empty URL and is reported as ``no-url`` downstream. A pin the
    index resolved to a non-jar artifact keeps its URL and name but is marked ``kind: other``, so
    the download loop can route it away from ``mods/`` instead of either rejecting the whole pack or
    dropping a texture pack among the mods.
    """
    out = []
    for f in manifest.get("files", []):
        if not f.get("required", True):
            continue
        url = f.get("downloadUrl") or ""
        resolved = None
        if not url and resolve_index:
            resolved = resolve_index.get((f["projectID"], f["fileID"]))
            if resolved:
                url = resolved["url"]
        if url:
            parsed = validate_url(url)
            name = urllib.parse.unquote(parsed.path.rsplit("/", 1)[-1])
        else:
            name = f"project{f['projectID']}-{f['fileID']}.jar"
        if only and not any(s.lower() in name.lower() for s in only):
            continue
        entry = {"name": name, "url": url, "projectID": f["projectID"], "fileID": f["fileID"]}
        if resolved:
            entry["urlSource"] = "resolve-index"
            entry["expectedBytes"] = resolved["bytes"]
            entry["kind"] = resolved["kind"]
            if resolved["kind"] != "jar":
                out.append(entry)
                continue
        elif url:
            entry["urlSource"] = "manifest"
        if (not name.lower().endswith(".jar") or name in (".", "..") or
                any(c in name for c in "/\\\x00") or
                any(ord(c) < 32 for c in name)):
            raise PackError(f"unsafe mod jar filename: {name[:100]!r}")
        out.append(entry)
    jars = [e["name"].casefold() for e in out if e.get("kind") != "other"]
    if len(set(jars)) != len(jars):
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
    ap.add_argument("--resolve-index", type=Path, default=None,
                    help="Resolve bare (projectID,fileID) pins from this validated index")
    ap.add_argument("--resourcepacks-out", type=Path, default=None,
                    help="Where resolved non-jar pins (resource packs) go; without it they are reported, not staged")
    ap.add_argument("--fingerprint-report", type=Path, default=None,
                    help="Also write a path-free per-artifact fingerprint summary, safe to commit")
    args = ap.parse_args()

    if not args.pack.is_file():
        print(f"no such pack: {args.pack}", file=sys.stderr)
        return 2

    try:
        manifest = read_manifest(args.pack)
    except PackError as e:
        print(e, file=sys.stderr)
        return 2
    resolve_index = None
    if args.resolve_index is not None:
        if not args.resolve_index.is_file():
            print(f"no such resolve index: {args.resolve_index}", file=sys.stderr)
            return 2
        try:
            resolve_index = load_resolve_index(args.resolve_index, args.pack)
        except PackError as e:
            print(e, file=sys.stderr)
            return 2
    try:
        entries = plan_entries(manifest, args.only or None, resolve_index)
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

    def target_for(e: dict) -> Path | None:
        """Where this entry lands, or None when this run is not asked to stage it.

        A non-jar pin must never land in ``mods/``: Forge would ignore a texture pack there at best,
        and calling it a staged mod would be worse. It goes to ``--resourcepacks-out`` if given.
        """
        if e.get("kind") == "other":
            return (args.resourcepacks_out / e["name"]) if args.resourcepacks_out else None
        return mods_dir / e["name"]

    print(f"pack      : {args.pack}")
    print(f"entries   : {len(entries)} of {len(manifest.get('files', []))} manifest files")
    if resolve_index is not None:
        print(f"index     : {args.resolve_index} ({len(resolve_index)} resolved pins)")
    print(f"out       : {args.out}")

    # Size the whole plan before writing anything, so the budget is a decision and not a discovery.
    # A resolved pin already carries the size its upstream reported, which is a better estimate than
    # a HEAD that may not answer at all; both are only an estimate, and the stream is still capped.
    planned = 0
    failed = 0
    unknown = 0
    deferred = [e for e in entries if target_for(e) is None]
    for e in entries:
        target = target_for(e)
        if target is None:
            e["state"] = "not-a-mod-jar"
            continue
        if target.is_symlink():
            print(f"REFUSING: existing mod is a symlink: {target}", file=sys.stderr)
            return 2
        if target.is_file():
            e["bytes"] = target.stat().st_size
            # A file that is already there is not automatically the file this pin resolves to.
            # Re-checking it against the resolved size is what makes a re-run a verification rather
            # than a no-op, and it is the only integrity check a resumed run would otherwise skip.
            expected = e.get("expectedBytes")
            if expected and e["bytes"] != expected:
                e["state"] = "size-mismatch"
                e["error"] = f"{e['bytes']} bytes on disk, {expected} resolved"
                failed += 1
                continue
            e["state"] = "present"
            planned += e["bytes"]
            continue
        if e.get("expectedBytes"):
            n = e["expectedBytes"]
            e["sizeSource"] = "resolve-index"
        else:
            n = head_size(e["url"]) if e["url"] else None
            e["sizeSource"] = "head" if n else None
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
        print(f"note      : {unknown} entries have no size estimate; the real total may be larger",
              file=sys.stderr)

    if args.dry_run:
        for e in entries:
            size = f"{e.get('bytes') / 1024:.0f} KiB" if e.get("bytes") else "?"
            if target_for(e) is None:
                size = "deferred"
            print(f"  {size:>10s}  {e['name']}")
        return 0

    mods_dir.mkdir(parents=True, exist_ok=True)
    if args.resourcepacks_out is not None:
        args.resourcepacks_out.mkdir(parents=True, exist_ok=True)
    consumed = sum(e["bytes"] for e in entries if e["state"] == "present")
    for e in entries:
        target = target_for(e)
        # Only entries the pre-flight left as "planned" are fetched. "present" already matches, and
        # "size-mismatch" is deliberately left alone: an existing file that disagrees with the
        # resolution is evidence to investigate, not something to silently overwrite.
        if target is None or e["state"] != "planned":
            continue
        if not e["url"]:
            # The manifest carries no URL and no resolve index covered this pin: the file cannot be
            # fetched from what is on hand, so it is reported rather than substituted.
            e["state"] = "no-url"
            failed += 1
            continue
        try:
            size = download_bounded(e["url"], target, budget - consumed)
            expected = e.get("expectedBytes")
            if expected and size != expected:
                # The CDN proves the *name*; a size disagreement means these bytes are not the
                # resolved file. Nothing is kept and the entry is not called staged.
                target.unlink(missing_ok=True)
                raise PackError(f"{e['name']}: {size} bytes on the wire, {expected} resolved")
            e["bytes"] = size
            consumed += size
            e["state"] = "staged"
        except BudgetExceeded as ex:
            e["state"] = "budget-exceeded"
            e["error"] = str(ex)[:160]
            failed += 1
            print(f"REFUSING: {ex}", file=sys.stderr)
            break
        except Exception as ex:
            e["state"] = "failed"
            e["error"] = str(ex)[:160]
            failed += 1

    # The transformer surface is read from the jars that actually landed. A jar that cannot be read
    # is recorded separately: its parse error must not overwrite the entry's *classification* reason,
    # which is what says why the pin did not land.
    for e in entries:
        target = target_for(e)
        if target is not None and target.is_file():
            e["sha256"] = sha256(target)
            if e["name"].lower().endswith(".jar"):
                info = inspect_jar(target)
                if "error" in info:
                    e["jarReadError"] = info.pop("error")
                e.update(info)

    coremods = [e for e in entries if e.get("coremod")]
    ats = [e for e in entries if e.get("accessTransformers")]
    mixins = [e for e in entries if e.get("mixinConfigs")]
    url_sources = {}
    for e in entries:
        if e.get("urlSource"):
            url_sources[e["urlSource"]] = url_sources.get(e["urlSource"], 0) + 1
    unresolved = [e for e in entries if not e["url"]]
    not_staged = [e for e in entries if target_for(e) is None]
    report = {
        "pack": str(args.pack),
        "packSha256": sha256(args.pack),
        "out": str(args.out),
        "resolveIndex": str(args.resolve_index) if args.resolve_index else None,
        "resourcepacksOut": str(args.resourcepacks_out) if args.resourcepacks_out else None,
        "entries": len(entries),
        "staged": sum(1 for e in entries if e.get("state") == "staged"),
        "present": sum(1 for e in entries if e.get("state") == "present"),
        "failed": failed,
        "urlSources": dict(sorted(url_sources.items())),
        "resolvedByIndex": sum(1 for e in entries if e.get("urlSource") == "resolve-index"),
        # Not fetched, and *why*: a pin no index covered, versus a fetch that was attempted and lost.
        "unresolved": [{"projectID": e["projectID"], "fileID": e["fileID"], "name": e["name"],
                        "state": e.get("state"), "error": e.get("error")} for e in unresolved],
        # Resolved, but not a mod jar and no --resourcepacks-out was given: reported, never dropped.
        "notStagedNonJar": [{"projectID": e["projectID"], "fileID": e["fileID"], "name": e["name"],
                             "url": e["url"], "bytes": e.get("expectedBytes")} for e in not_staged],
        "bytesOnDisk": sum(e.get("bytes") or 0 for e in entries
                           if (target_for(e) is not None and target_for(e).is_file())),
        "coremods": [{"name": e["name"], "plugin": e["coremod"], "containsMod": e.get("containsMod")} for e in coremods],
        "accessTransformers": [{"name": e["name"], "entries": e["accessTransformers"]} for e in ats],
        "mixinConfigs": [{"name": e["name"], "entries": e["mixinConfigs"]} for e in mixins],
        "files": entries,
    }
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")

    print(f"staged    : {report['staged']} new, {report['present']} already present, {failed} not fetched")
    print(f"on disk   : {report['bytesOnDisk'] / (1 << 20):.1f} MiB")
    if url_sources:
        print(f"url source: {report['urlSources']}")
    if not_staged:
        print(f"note      : {len(not_staged)} resolved pins are not mod jars "
              f"({sum(e.get('expectedBytes') or 0 for e in not_staged) / (1 << 20):.1f} MiB) and were not "
              f"staged; pass --resourcepacks-out to fetch them", file=sys.stderr)
    print(f"report    : {report_path}")
    if args.fingerprint_report is not None:
        # The full report records local absolute paths and every transformer entry; this one is the
        # reviewable half - pack identity, per-artifact hashes and the classification - with no
        # machine-specific path in it, so it can be committed next to the pack's lock.
        fingerprint = {
            "schema": 1,
            "pack": args.pack.name,
            "packSha256": report["packSha256"],
            "entries": report["entries"],
            "staged": report["staged"],
            "present": report["present"],
            "failed": report["failed"],
            "bytesOnDisk": report["bytesOnDisk"],
            "urlSources": report["urlSources"],
            "unresolved": report["unresolved"],
            "notStagedNonJar": [{"projectID": e["projectID"], "fileID": e["fileID"], "name": e["name"],
                                 "url": e["url"], "bytes": e.get("expectedBytes")} for e in not_staged],
            "artifacts": [{"projectID": e["projectID"], "fileID": e["fileID"], "name": e["name"],
                           "kind": e.get("kind") or "jar", "bytes": e.get("bytes"),
                           "sha256": e.get("sha256"), "coremod": e.get("coremod"),
                           "accessTransformers": e.get("accessTransformers") or [],
                           "mixinConfigs": e.get("mixinConfigs") or []} for e in entries],
        }
        args.fingerprint_report.parent.mkdir(parents=True, exist_ok=True)
        args.fingerprint_report.write_text(json.dumps(fingerprint, indent=2, sort_keys=True) + "\n",
                                           encoding="utf-8")
        print(f"fingerprints: {args.fingerprint_report} "
              f"({sum(1 for e in entries if e.get('sha256'))} hashed artifacts)")
    print(f"transformers: {len(coremods)} coremods, {len(ats)} with access transformers, {len(mixins)} with mixin configs")
    for c in report["coremods"]:
        print(f"  coremod  {c['name']:44s} {c['plugin']}")
    for a in report["accessTransformers"]:
        print(f"  at       {a['name']:44s} {','.join(x.split('/')[-1] for x in a['entries'])}")
    if any(e.get("state") == "budget-exceeded" for e in entries):
        return 3
    if failed:
        return 4
    if not_staged:
        # Every fetch that was attempted landed, but the pack pins artifacts this run was not asked
        # to stage. Saying 0 here would report a partial install as a complete one.
        return 5
    return 0


if __name__ == "__main__":
    sys.exit(main())
