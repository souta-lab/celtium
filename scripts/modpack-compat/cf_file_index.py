#!/usr/bin/env python3
"""Resolve CurseForge manifest ``(projectID, fileID)`` pins to real download URLs **without an API key**.

Why this exists
---------------
A CurseForge pack archive normally carries a ``downloadUrl`` per manifest entry. RLCraft 2.9.3's
manifest carries none: all 187 entries are bare ``{projectID, fileID, required}``. The pack's own
``modlist.html`` links only to *project* pages (and only 178 of them), so it cannot name the pinned
file either. Something has to turn a numeric pin into a fetchable URL.

What was measured on this host (2026-10-15; ``probe_cf_resolution.py`` re-runs every row):

* ``api.curseforge.com/v1/mods/{p}/files/{f}/download-url`` -> **403** with no key *and* with a
  wrong key, so it is key-gated rather than merely header-gated. Rejected: the task forbids
  requiring a key.
* ``www.curseforge.com`` file pages -> **403** Cloudflare unless the request carries the two
  ``Sec-Fetch-*`` navigation headers a browser sends, in which case they answer **200** and do
  contain the exact file name. That route needs a project *slug*, which the manifest does not have.
* The official CDN needs the *exact* file name: ``edge.forgecdn.net/files/{f//1000}/{f%1000}/x.jar``
  redirects to ``mediafilez.forgecdn.net/...`` which answers **200** for the true name and **403**
  for any other name. The numeric path alone is therefore not enough.
* The CDN's backing S3 bucket refuses listing (``?list-type=2`` -> 403 AccessDenied).

So the only key-free, reproducible route found is: read the exact file name for each pin from the
public ``api.cfwidget.com`` index (one GET per project, no key, no account), then build the
**official CurseForge CDN** URL from the pin and that name.

Trust shape - and why a third party is acceptable here: the *artifact* always comes from the
official CDN, and the CDN is itself the verifier of the name, because a wrong name cannot return
200 for a path that encodes the file ID. A stale or wrong upstream name therefore fails loudly
(403) instead of silently yielding a different mod. ``probe_cf_resolution.py --index`` additionally
spot-checks resolved names against the first-party file page.

This script only performs read-only GETs and writes the index it is asked to write.

Usage:
    cf_file_index.py --pack <pack.zip> --out <index.json> [--cache-dir DIR]
                     [--delay 1.0] [--offline] [--report <path>]

Exit codes: 0 every pin resolved; 2 usage/unreadable pack; 4 some pins unresolved (index and report
are still written, and the unresolved pins are classified rather than guessed).
"""
from __future__ import annotations

import argparse
import hashlib
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

USER_AGENT = "celtium-cf-file-index/1.0 (modpack compatibility research; read-only)"
API = "https://api.cfwidget.com/{project}"
CDN = "https://edge.forgecdn.net/files/{bucket}/{leaf}/{name}"
MAX_INDEX_BYTES = 1 << 20
# States a pin can end in. "ok" is the only one that yields a URL. "not-fetched" is deliberately
# separate from "project-error": a network failure or an --offline cache miss says nothing about
# whether the pin exists upstream, and calling it an upstream error would be a guess.
OK, PROJECT_ERROR, PROJECT_MISSING, FILE_MISSING, UNSAFE_NAME, NOT_FETCHED = (
    "ok", "project-error", "project-missing", "file-missing", "unsafe-name", "not-fetched")


class ResolveError(Exception):
    """The pack or an index is unusable. main turns this into exit 2."""


def cdn_url(file_id: int, name: str) -> str:
    """The official CurseForge CDN layout: first digits / last three digits / exact file name."""
    if not isinstance(file_id, int) or file_id <= 0:
        raise ResolveError(f"bad fileID: {file_id!r}")
    return CDN.format(bucket=file_id // 1000, leaf=file_id % 1000, name=urllib.parse.quote(name, safe=""))


def validate_name(name: object) -> str:
    """A file name we are willing to put in a URL and later in a directory.

    Mirrors pack_stage.plan_entries: the resolved name must stay a single, plain ``*.jar``
    component, so a hostile or malformed upstream name cannot escape the mods directory.
    """
    if not isinstance(name, str) or not name:
        raise ResolveError(f"missing file name: {name!r}")
    if name in (".", "..") or any(c in name for c in "/\\\x00") or any(ord(c) < 32 for c in name):
        raise ResolveError(f"unsafe resolved file name: {name[:100]!r}")
    if not name.lower().endswith(".jar"):
        raise ResolveError(f"resolved name is not a jar: {name[:100]!r}")
    return name


def classify_name(name: object) -> str:
    """``"jar"`` for a mod jar, ``"other"`` for a safe name that is not one, else raise.

    The distinction is load-bearing, not cosmetic. RLCraft 2.9.3 pins 187 required entries of which
    **10 are resource packs** (``.zip``); the pack's ``modlist.html`` lists those under
    ``texture-packs``. A resolver that called them "unsafe" would be mislabelling a legitimate
    artifact, and one that called them mods would put a texture pack in ``mods/``. Only genuinely
    dangerous names raise.
    """
    if not isinstance(name, str) or not name:
        raise ResolveError(f"missing file name: {name!r}")
    if name in (".", "..") or any(c in name for c in "/\\\x00") or any(ord(c) < 32 for c in name):
        raise ResolveError(f"unsafe resolved file name: {name[:100]!r}")
    return "jar" if name.lower().endswith(".jar") else "other"


def sha256(path: Path) -> str:
    """The archive fingerprint the index is anchored to, so the pin set is provably the documented one."""
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def load_pins(pack: Path) -> list[dict]:
    """The numeric pins, read out of the pack archive without extracting anything."""
    try:
        with zipfile.ZipFile(pack) as z:
            if "manifest.json" not in z.namelist():
                raise ResolveError(f"{pack}: no manifest.json (not a CurseForge pack archive?)")
            manifest = json.loads(z.read("manifest.json"))
    except zipfile.BadZipFile as e:
        raise ResolveError(f"{pack}: not a readable zip ({e})") from e
    pins = []
    for item in manifest.get("files", []):
        if not item.get("required", True):
            continue
        pid, fid = item.get("projectID"), item.get("fileID")
        if not isinstance(pid, int) or not isinstance(fid, int) or pid <= 0 or fid <= 0:
            raise ResolveError(f"non-integer or invalid pin: {item!r}")
        pins.append({"projectID": pid, "fileID": fid})
    if not pins:
        raise ResolveError(f"{pack}: no required manifest entries")
    return pins


def fetch_project(project_id: int, timeout: int = 60) -> tuple[int, object]:
    """GET one project's public metadata. Returns (http_status, parsed_json_or_None)."""
    req = urllib.request.Request(API.format(project=project_id), headers={"User-Agent": USER_AGENT})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            raw = r.read()
            status = r.status
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception:
        return 0, None
    if status != 200 or len(raw) > 64 << 20:
        return status, None
    try:
        return status, json.loads(raw)
    except ValueError:
        return status, None


def slim_project(project_id: int, payload: object, wanted: set[int]) -> dict:
    """Keep only what the index needs, so the on-disk cache stays small and reviewable."""
    if not isinstance(payload, dict):
        return {"projectID": project_id, "title": None, "files": {}}
    files = {}
    for f in payload.get("files") or []:
        if isinstance(f, dict) and f.get("id") in wanted:
            files[str(f["id"])] = {"name": f.get("name"), "filesize": f.get("filesize"),
                                   "uploaded_at": f.get("uploaded_at"), "type": f.get("type")}
    urls = payload.get("urls") or {}
    return {"projectID": project_id, "title": payload.get("title"),
            "curseforgeUrl": urls.get("curseforge") or urls.get("project"), "files": files}


def resolve_pins(pins: list[dict], get_project, delay: float, sleep=time.sleep,
                 progress=None) -> list[dict]:
    """One result per pin, in input order. ``get_project(project_id)`` returns the slim payload.

    Kept free of sockets and sleeps-by-default so the contract is testable offline; the caller
    injects both the fetcher and the sleep.
    """
    by_project: dict[int, dict] = {}
    for i, pin in enumerate(pins):
        pid = pin["projectID"]
        if pid not in by_project:
            if i and delay:
                sleep(delay)
            by_project[pid] = get_project(pid)
            if progress:
                progress(pid, by_project[pid])
    results = []
    for pin in pins:
        pid, fid = pin["projectID"], pin["fileID"]
        entry = {"projectID": pid, "fileID": fid, "url": None, "name": None,
                 "filesize": None, "state": None, "projectTitle": None}
        project = by_project.get(pid) or {}
        entry["projectTitle"] = project.get("title")
        if project.get("status") != 200:
            status = project.get("status")
            if status == 404:
                entry["state"] = PROJECT_MISSING
            elif not status:
                entry["state"] = NOT_FETCHED
                entry["offlineMiss"] = bool(project.get("offline-miss"))
            else:
                entry["state"] = PROJECT_ERROR
            entry["httpStatus"] = status or None  # 0 means no HTTP answer was observed at all
            results.append(entry)
            continue
        found = (project.get("files") or {}).get(str(fid))
        if not found:
            entry["state"] = FILE_MISSING
            entry["httpStatus"] = 200
            results.append(entry)
            continue
        try:
            kind = classify_name(found.get("name"))
        except ResolveError as e:
            entry["state"] = UNSAFE_NAME
            entry["error"] = str(e)[:120]
            results.append(entry)
            continue
        name = found["name"]
        size = found.get("filesize")
        entry.update({"state": OK, "httpStatus": 200, "name": name, "kind": kind,
                      "filesize": size if isinstance(size, int) and size > 0 else None,
                      "url": cdn_url(fid, name)})
        results.append(entry)
    return results


def read_cache(cache_dir: Path, project_id: int) -> dict | None:
    path = cache_dir / f"{project_id}.json"
    if not path.is_file() or path.is_symlink():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def write_cache(cache_dir: Path, project_id: int, slim: dict) -> None:
    cache_dir.mkdir(parents=True, exist_ok=True)
    path = cache_dir / f"{project_id}.json"
    path.write_text(json.dumps(slim, indent=1, sort_keys=True) + "\n", encoding="utf-8")


def make_fetcher(cache_dir: Path | None, offline: bool, wanted_by_project: dict[int, set[int]],
                 delay: float, sleep=time.sleep):
    """A caching, politely rate-limited ``get_project``; records the HTTP status it observed."""
    def get_project(project_id: int) -> dict:
        wanted = wanted_by_project[project_id]
        if cache_dir is not None:
            cached = read_cache(cache_dir, project_id)
            if cached is not None and (offline or set((cached.get("files") or {})) >= {str(x) for x in wanted}):
                cached["status"] = 200
                cached["cached"] = True
                return cached
            if offline:
                return {"status": 0, "offline-miss": True, "files": {}}
        status, payload = fetch_project(project_id)
        if status == 429 or status >= 500:
            # One polite retry after a longer pause; still no key, still read-only.
            sleep(max(delay, 1.0) * 5)
            status, payload = fetch_project(project_id)
        slim = slim_project(project_id, payload, wanted)
        slim["status"] = status
        slim["cached"] = False
        if cache_dir is not None and status == 200:
            write_cache(cache_dir, project_id, slim)
        return slim
    return get_project


def summarize(results: list[dict]) -> dict:
    counts: dict[str, int] = {}
    kinds: dict[str, int] = {}
    for r in results:
        counts[r["state"]] = counts.get(r["state"], 0) + 1
        if r["state"] == OK:
            kinds[r.get("kind") or "jar"] = kinds.get(r.get("kind") or "jar", 0) + 1
    resolved = [r for r in results if r["state"] == OK]
    return {
        "pins": len(results),
        "resolved": len(resolved),
        "unresolved": len(results) - len(resolved),
        "byState": dict(sorted(counts.items())),
        # A pin can resolve to something that is not a mod jar; that is a kind, not a failure.
        "byKind": dict(sorted(kinds.items())),
        "resolvedBytes": sum(r["filesize"] or 0 for r in resolved),
        "unresolvedPins": [{"projectID": r["projectID"], "fileID": r["fileID"], "state": r["state"],
                            "httpStatus": r.get("httpStatus")} for r in results if r["state"] != OK],
    }


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--pack", type=Path, required=True, help="Pack archive; it is only read")
    ap.add_argument("--out", type=Path, required=True, help="Where the resolve index is written")
    ap.add_argument("--cache-dir", type=Path, default=None, help="Slim per-project cache (makes reruns offline)")
    ap.add_argument("--report", type=Path, default=None, help="Default: <out>.report.json")
    ap.add_argument("--delay", type=float, default=1.0, help="Seconds between upstream GETs (politeness)")
    ap.add_argument("--offline", action="store_true", help="Serve from --cache-dir only; never touch the network")
    args = ap.parse_args()

    if not args.pack.is_file():
        print(f"no such pack: {args.pack}", file=sys.stderr)
        return 2
    try:
        pins = load_pins(args.pack)
    except ResolveError as e:
        print(e, file=sys.stderr)
        return 2
    if args.offline and args.cache_dir is None:
        print("--offline needs --cache-dir", file=sys.stderr)
        return 2

    wanted_by_project: dict[int, set[int]] = {}
    for pin in pins:
        wanted_by_project.setdefault(pin["projectID"], set()).add(pin["fileID"])
    print(f"pack      : {args.pack.name} sha256={sha256(args.pack)}")
    print(f"pins      : {len(pins)} required entries, {len(wanted_by_project)} distinct projects")

    def progress(pid, slim):
        origin = "cache" if slim.get("cached") else "fetched"
        print(f"  project {pid:<8} http={slim.get('status')} ({origin}) "
              f"{(slim.get('title') or '')[:48]}", flush=True)

    # The delay exists to be polite to the upstream host. Reading a local cache is not a request,
    # so an --offline run must not pace itself against a network it never touches.
    effective_delay = 0.0 if args.offline else args.delay
    get_project = make_fetcher(args.cache_dir, args.offline, wanted_by_project, effective_delay)
    results = resolve_pins(pins, get_project, effective_delay, progress=progress)
    report = summarize(results)

    index = {"schema": 1, "source": "api.cfwidget.com (key-free metadata) -> edge.forgecdn.net",
             "packName": args.pack.name, "packSha256": sha256(args.pack),
             "generatedBy": "scripts/modpack-compat/cf_file_index.py",
             "entries": results}
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_text(json.dumps(index, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    report_path = args.report or args.out.with_suffix(args.out.suffix + ".report.json")
    report_path.parent.mkdir(parents=True, exist_ok=True)
    report_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")

    print(f"resolved  : {report['resolved']} of {report['pins']} pins "
          f"({report['resolvedBytes'] / (1 << 20):.1f} MiB reported by upstream)")
    print(f"by state  : {report['byState']}")
    print(f"by kind   : {report['byKind']}  (a resolved pin need not be a mod jar)")
    print(f"index     : {args.out}")
    print(f"report    : {report_path}")
    for u in report["unresolvedPins"][:20]:
        print(f"  UNRESOLVED {u['projectID']}/{u['fileID']} {u['state']} http={u.get('httpStatus')}")
    return 4 if report["unresolved"] else 0


if __name__ == "__main__":
    sys.exit(main())
