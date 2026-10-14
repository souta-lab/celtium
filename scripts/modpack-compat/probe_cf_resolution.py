#!/usr/bin/env python3
"""Re-measure, live, why a key-free CurseForge pin resolution is shaped the way it is.

Every claim in ``docs/modpack-compatibility-p0.md`` about *why* the resolver works the way it does
should be reproducible by running this one command. It only issues read-only GET/HEAD requests and
prints the status codes it observed; it downloads no mod bytes and writes nothing.

The interesting rows are the negative ones:

* the official API answers 403 with no key *and* with a wrong key, so it is key-gated, not
  header-gated - and the task forbids requiring a key;
* the public web pages answer 403 (Cloudflare) unless the request carries the two ``Sec-Fetch-*``
  navigation headers a browser sends, in which case the same URL answers 200. The headers are the
  difference, not the user agent - measured 0/10 without them and 6/6 with them;
* the CDN answers 200 only for the exact file name and 403 for any other, which is what makes the
  numeric path alone useless and the name mandatory;
* the CDN's S3 origin refuses bucket listing.

Usage:
    probe_cf_resolution.py [--project 238222] [--file 3043174] [--name jei_1.12.2-4.16.1.302.jar]
                           [--slug jei] [--json <path>]
    probe_cf_resolution.py --index <resolve-index.json> [--sample 8]

With ``--index`` it additionally spot-checks the resolved names against the **first-party** file
page. That page is reachable, but only when the request carries the two ``Sec-Fetch-*`` navigation
headers a browser sends: with them it answers 200 and contains the exact file name; without them the
same URL answers 403 from Cloudflare. The spot-check exists because the resolved name itself comes
from a third-party index, and a name that the first-party page agrees with is a stronger claim than
one that only a size happens to match.

Exit code: 0 if the expected shape was observed, 1 if any row disagreed with it (the CDN is an
external system and may change; a disagreement is a finding, not a script failure to hide).
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Safari/537.36"
BROWSER_HEADERS = {"User-Agent": UA, "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                   "Accept-Language": "en-US,en;q=0.9", "Sec-Fetch-Dest": "document",
                   "Sec-Fetch-Mode": "navigate"}


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def probe(url: str, headers: dict | None = None, method: str = "GET", follow: bool = False) -> dict:
    """One read-only request. Returns the observed status, final URL and byte count."""
    opener = urllib.request.build_opener() if follow else urllib.request.build_opener(NoRedirect())
    req = urllib.request.Request(url, headers=headers or {"User-Agent": UA}, method=method)
    try:
        with opener.open(req, timeout=45) as r:
            body = r.read(2048)
            return {"url": url, "status": r.status, "finalUrl": r.geturl(),
                    "bytes": int(r.headers.get("Content-Length") or 0), "read": len(body),
                    "contentType": r.headers.get("Content-Type")}
    except urllib.error.HTTPError as e:
        return {"url": url, "status": e.code, "finalUrl": e.geturl(), "bytes": 0, "read": 0,
                "contentType": e.headers.get("Content-Type") if e.headers else None}
    except Exception as e:
        return {"url": url, "status": 0, "error": type(e).__name__, "finalUrl": url, "bytes": 0, "read": 0}


def fetch_text(url: str, headers: dict | None = None, timeout: int = 45) -> tuple[int, str]:
    """GET and decode, returning (status, text). Never raises for an HTTP status."""
    req = urllib.request.Request(url, headers=headers or {"User-Agent": UA})
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read(4 << 20).decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, ""
    except Exception:
        return 0, ""


def cfwidget_project_url(project_id: int) -> str | None:
    """The project's own page URL, taken from the key-free index's ``urls`` field.

    The section matters: a pin may be a resource pack, and ``.../texture-packs/{slug}`` is not
    ``.../mc-mods/{slug}``. Forcing the mods section turns a resolvable pack into a 404 and would
    make the cross-check blame the resolved name for the caller's mistake.
    """
    status, text = fetch_text(f"https://api.cfwidget.com/{project_id}")
    if status != 200:
        return None
    try:
        urls = json.loads(text).get("urls") or {}
    except ValueError:
        return None
    url = urls.get("curseforge") or urls.get("project") or ""
    return url.rstrip("/") or None


def crosscheck(index_path: Path, sample: int, delay: float) -> list[dict]:
    """Confirm, on the first-party site, that sampled resolved names are the names of their pins."""
    index = json.loads(index_path.read_text(encoding="utf-8"))
    entries = [e for e in index.get("entries", []) if e.get("state") == "ok" and e.get("url")]
    if not entries:
        print("cross-check: the index has no resolved entries")
        return []
    step = max(1, len(entries) // sample)
    picked = entries[::step][:sample]
    results = []
    for e in picked:
        pid, fid, want = e["projectID"], e["fileID"], e["name"]
        project_url = cfwidget_project_url(pid)
        if not project_url:
            results.append({"projectID": pid, "fileID": fid, "name": want,
                            "verdict": "no-project-url", "pageStatus": None})
            continue
        section, slug = project_url.rsplit("/", 2)[-2:]
        status, page = fetch_text(f"{project_url}/files/{fid}", BROWSER_HEADERS)
        verdict = ("page-confirms" if want in page else
                   "page-lacks-name" if status == 200 else f"page-http-{status}")
        results.append({"projectID": pid, "fileID": fid, "name": want, "slug": slug,
                        "section": section, "verdict": verdict,
                        "pageStatus": status, "pageBytes": len(page)})
        print(f"  {verdict:18s} {section}/{slug}/{fid}  {want[:56]}", flush=True)
        time.sleep(delay)
    confirmed = sum(1 for r in results if r["verdict"] == "page-confirms")
    print(f"cross-check: {confirmed}/{len(results)} sampled names confirmed on the first-party page")
    return results


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--project", type=int, default=238222, help="A project that is really in the pack")
    ap.add_argument("--file", type=int, default=3043174)
    ap.add_argument("--name", default="jei_1.12.2-4.16.1.302.jar")
    ap.add_argument("--slug", default="jei")
    ap.add_argument("--json", type=Path, default=None, help="Also write the raw observations here")
    ap.add_argument("--index", type=Path, default=None, help="Resolve index to spot-check")
    ap.add_argument("--sample", type=int, default=8, help="How many resolved pins to spot-check")
    ap.add_argument("--delay", type=float, default=3.0, help="Seconds between first-party requests")
    args = ap.parse_args()

    p, f, name, slug = args.project, args.file, args.name, args.slug
    cdn = f"https://edge.forgecdn.net/files/{f // 1000}/{f % 1000}/{urllib.parse.quote(name, safe='')}"
    cdn_wrong = f"https://edge.forgecdn.net/files/{f // 1000}/{f % 1000}/definitely-not-the-name.jar"
    rows = [
        # label, url, headers, method, follow, expected status
        ("api.curseforge.com, no key", f"https://api.curseforge.com/v1/mods/{p}/files/{f}/download-url",
         {"User-Agent": UA}, "GET", False, 403),
        ("api.curseforge.com, wrong key", f"https://api.curseforge.com/v1/mods/{p}/files/{f}/download-url",
         {"User-Agent": UA, "x-api-key": "not-a-real-key"}, "GET", False, 403),
        ("curseforge.com file page, no navigation headers",
         f"https://www.curseforge.com/minecraft/mc-mods/{slug}/files/{f}",
         {"User-Agent": UA}, "GET", False, 403),
        ("curseforge.com file page, browser navigation headers",
         f"https://www.curseforge.com/minecraft/mc-mods/{slug}/files/{f}",
         BROWSER_HEADERS, "GET", False, 200),
        ("curseforge.com download page, browser headers",
         f"https://www.curseforge.com/minecraft/mc-mods/{slug}/download/{f}",
         BROWSER_HEADERS, "GET", False, 200),
        ("curseforge.com file page, wrong slug",
         f"https://www.curseforge.com/minecraft/mc-mods/definitely-not-this-slug/files/{f}",
         BROWSER_HEADERS, "GET", False, 404),
        ("curseforge.com file page, wrong file id",
         f"https://www.curseforge.com/minecraft/mc-mods/{slug}/files/99999999",
         BROWSER_HEADERS, "GET", False, 404),
        ("legacy forgesvc api", f"https://addons-ecs.forgesvc.net/api/v2/addon/{p}/file/{f}",
         {"User-Agent": UA}, "GET", False, 0),
        ("CDN, exact name (followed)", cdn, {"User-Agent": UA}, "GET", True, 200),
        ("CDN, wrong name (followed)", cdn_wrong, {"User-Agent": UA}, "GET", True, 403),
        ("CDN, numeric path only", f"https://mediafilez.forgecdn.net/files/{f // 1000}/{f % 1000}/",
         {"User-Agent": UA}, "GET", False, 403),
        ("CDN S3 bucket listing", "https://mediafilez.forgecdn.net/?list-type=2&prefix=files/&max-keys=1",
         {"User-Agent": UA}, "GET", False, 403),
        ("api.cfwidget.com, key-free metadata", f"https://api.cfwidget.com/{p}",
         {"User-Agent": UA}, "GET", False, 200),
    ]
    observations, disagreements = [], 0
    for label, url, headers, method, follow, expected in rows:
        obs = probe(url, headers, method, follow)
        obs["label"], obs["expected"] = label, expected
        agrees = obs["status"] == expected
        disagreements += 0 if agrees else 1
        observations.append(obs)
        print(f"{'ok  ' if agrees else 'DIFF'} {label:46s} status={obs['status']:<4} expected={expected}"
              + (f"  {obs.get('error')}" if obs.get("error") else ""))
    print(f"\n{len(rows) - disagreements}/{len(rows)} probes matched the documented shape")
    crosschecked = crosscheck(args.index, args.sample, args.delay) if args.index else []
    if args.json:
        args.json.parent.mkdir(parents=True, exist_ok=True)
        args.json.write_text(json.dumps({"projectID": p, "fileID": f, "name": name,
                                         "observations": observations,
                                         "firstPartyCrossCheck": crosschecked}, indent=2) + "\n")
        print(f"wrote {args.json}")
    return 1 if disagreements else 0


if __name__ == "__main__":
    sys.exit(main())
