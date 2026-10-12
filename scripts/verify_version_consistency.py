#!/usr/bin/env python3
"""Checks that a distributed jar's version agrees with its own name, the build's default and the plan.

The trap this exists for (docs/plan.md row 47, §0.365): the rebrand moved the development version from
`2.5.12-celtium.0.1.0-dev.1` to `0.0.2-alpha-dev`, the old build output stayed on disk, and a helper script that
copied the old path ran a leg against a 13:08 jar whose sha256 matched the old `2.5.12-...` artifact - so a hook that
was not in that jar read as "the hook does not fire". Nothing compared the name, the contents and the documents.

One command, no GPU, no game:

    python3 scripts/verify_version_consistency.py            # newest jar under build-1122/build/libs/*/
    python3 scripts/verify_version_consistency.py --jar PATH  # a specific artifact

It prints the measured value of every field it reads and exits non-zero on the first inconsistency, naming the
artifact, the field, what was expected and what was found. `--json` prints the same as JSON.
"""
import argparse
import glob
import json
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
# The artifact family this project ships, and the old family the rebrand removed: any survivor is a stale output a
# recipe can still copy, which is exactly how one leg was run against the wrong jar.
ARTIFACT_PATTERN = "celtium-forge-mc1.12.2-*.jar"
STALE_PATTERNS = ["*2.5.12-celtium*", "*celtium.0.1.0-dev*"]
SEARCH_DIRS = ["build-1122/build/libs", "forge122/build/libs"]


def newest_jar():
    """The distributed artifact, newest first.

    `build-1122/build/libs/<version>/` is where `packageJar` puts the distributable, and it is preferred over the
    intermediate jars `forge122/build/libs` also holds (a `-reobf` or a doubled-suffix name is not what ships, and
    picking one of those would report a version problem that the distributable does not have).
    """
    packaged = glob.glob(os.path.join(ROOT, "build-1122/build/libs", "*", ARTIFACT_PATTERN))
    if packaged:
        return max(packaged, key=os.path.getmtime)
    for directory in SEARCH_DIRS:
        flat = glob.glob(os.path.join(ROOT, directory, ARTIFACT_PATTERN))
        if flat:
            return max(flat, key=os.path.getmtime)
    return None


def version_from_name(path):
    match = re.search(r"celtium-forge-mc1\.12\.2-(.+)\.jar$", os.path.basename(path))
    return match.group(1) if match else None


def jar_fields(path):
    """The version fields inside the artifact: mcmod.info's `version` and any manifest version attribute.

    An artifact that cannot be opened is reported as a named failure rather than raised: the first run of this check
    against a truncated file ended in a `BadZipFile` traceback, which is the shape of unhelpful failure this whole
    script exists to remove.
    """
    fields = {"mcmod.info version": None, "manifest version": None, "readable": True}
    try:
        return read_jar_fields(path, fields)
    except (zipfile.BadZipFile, OSError, EOFError) as failure:
        fields["readable"] = False
        fields["mcmod.info version"] = "unreadable: %s" % failure
        return fields


def read_jar_fields(path, fields):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if "mcmod.info" in names:
            try:
                info = json.loads(jar.read("mcmod.info").decode("utf-8", "replace"))
                entry = info[0] if isinstance(info, list) and info else info
                fields["mcmod.info version"] = entry.get("version") if isinstance(entry, dict) else None
            except (ValueError, KeyError, IndexError) as failure:
                fields["mcmod.info version"] = "unreadable: %s" % failure
        manifest = jar.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in names else ""
        for attribute in ("Celtium-Version", "Implementation-Version"):
            match = re.search(r"^%s:\s*(.+)$" % re.escape(attribute), manifest, re.MULTILINE)
            if match:
                fields["manifest version"] = "%s=%s" % (attribute, match.group(1).strip())
    return fields


def configured_dev_version():
    """The default development version in build-1122/build.gradle.kts (its `orElse("...")`)."""
    path = os.path.join(ROOT, "build-1122/build.gradle.kts")
    text = open(path, encoding="utf-8").read()
    match = re.search(r'providers\.gradleProperty\("celtium_release_version"\)\s*\.orElse\("([^"]+)"\)', text)
    return match.group(1) if match else None


def plan_release_versions():
    """The release versions the plan's header table names.

    The table sits between the document's front matter and the first `## ` section, so the slice cannot be "everything
    before the first heading" - the first heading is the table's own (`## 0. 直近の到達点`) and the version row is
    below it. The first lines are read instead, which is where the header table lives.
    """
    path = os.path.join(ROOT, "docs/plan.md")
    lines = open(path, encoding="utf-8").read().splitlines()
    header = "\n".join(lines[:80])
    return re.findall(r"\*\*(\d+\.\d+\.\d+(?:-[A-Za-z0-9.]+)?)\*\*", header)


def stale_artifacts():
    """Every artifact of the removed family, once: two patterns can match the same file."""
    found = set()
    for directory in SEARCH_DIRS:
        for pattern in STALE_PATTERNS:
            found.update(glob.glob(os.path.join(ROOT, directory, "**", pattern), recursive=True))
    return sorted(found)


def release_of(version):
    """`0.0.2-alpha-dev` and `0.0.2-alpha-reobf` are builds of release `0.0.2-alpha`."""
    return re.sub(r"-(dev|reobf)$", "", version or "")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", help="artifact to check (default: the newest one under the build outputs)")
    parser.add_argument("--json", action="store_true", help="print the report as JSON")
    args = parser.parse_args()

    jar = os.path.abspath(args.jar) if args.jar else newest_jar()
    checks = []
    if jar is None or not os.path.exists(jar):
        report = {"verified": False, "artifact": jar,
                  "failures": ["no artifact found: build-1122/build/libs/*/%s" % ARTIFACT_PATTERN]}
        print(json.dumps(report, indent=2) if args.json else "FAIL: no artifact found")
        return 1

    name_version = version_from_name(jar)
    parent = os.path.basename(os.path.dirname(jar))
    fields = jar_fields(jar)
    dev = configured_dev_version()
    plan = plan_release_versions()
    stale = stale_artifacts()

    # 1. The artifact's own name, its directory and its contents.
    checks.append({"check": "file name carries a version", "artifact": jar, "measured": name_version,
                   "ok": name_version is not None,
                   "detail": "expected celtium-forge-mc1.12.2-<version>.jar"})
    checks.append({"check": "artifact is a readable zip", "artifact": jar,
                   "measured": "readable" if fields.get("readable", True) else fields["mcmod.info version"],
                   "expected": "readable", "ok": fields.get("readable", True),
                   "detail": "a truncated or non-jar file under the build output is named here, not raised"})
    checks.append({"check": "mcmod.info version equals the file name", "artifact": jar,
                   "measured": fields["mcmod.info version"], "expected": name_version,
                   "ok": fields["mcmod.info version"] == name_version,
                   "detail": "field mcmod.info[0].version"})
    checks.append({"check": "version directory equals the file name", "artifact": jar, "measured": parent,
                   "expected": name_version, "ok": parent in ("libs", name_version),
                   "detail": "build-1122 puts the artifact under build/libs/<version>/; forge122/build/libs is flat"})
    if fields["manifest version"] is not None:
        manifest_version = fields["manifest version"].split("=", 1)[1]
        checks.append({"check": "manifest version equals the file name", "artifact": jar,
                       "measured": fields["manifest version"], "expected": name_version,
                       "ok": manifest_version == name_version, "detail": "manifest attribute"})

    # 2. The build's default and the plan's header table.
    checks.append({"check": "build default is a development build of a named release",
                   "artifact": "build-1122/build.gradle.kts", "measured": dev, "expected": "<x.y.z>-dev",
                   "ok": dev is not None and dev.endswith("-dev"),
                   "detail": "providers.gradleProperty('celtium_release_version').orElse(...)"})
    checks.append({"check": "the artifact is a build of the configured default", "artifact": jar,
                   "measured": name_version, "expected": dev,
                   "ok": name_version == dev or release_of(name_version) in plan,
                   "detail": "a release jar (no -dev) is accepted when the plan's header names its release"})
    checks.append({"check": "the plan's header names the artifact's release", "artifact": "docs/plan.md",
                   "measured": plan[:4], "expected": release_of(name_version),
                   "ok": release_of(name_version) in plan,
                   "detail": "versions in the header table before the first '## '"})

    # 3. The old artifact family, which is how a leg was once run against the wrong jar.
    checks.append({"check": "no artifact of the old 2.5.12-celtium family", "artifact": ", ".join(SEARCH_DIRS),
                   "measured": [os.path.relpath(p, ROOT) for p in stale], "expected": [],
                   "ok": not stale, "detail": "patterns: %s" % ", ".join(STALE_PATTERNS)})

    failures = [c for c in checks if not c["ok"]]
    report = {"verified": not failures, "artifact": os.path.relpath(jar, ROOT),
              "nameVersion": name_version, "mcmodInfoVersion": fields["mcmod.info version"],
              "manifestVersion": fields["manifest version"], "configuredDefault": dev,
              "planReleaseVersions": plan[:6], "staleArtifacts": [os.path.relpath(p, ROOT) for p in stale],
              "checks": checks,
              "failures": ["%s: %s is %r, expected %r (%s)" % (c["artifact"], c["check"], c["measured"],
                                                               c.get("expected"), c.get("detail")) for c in failures]}
    if args.json:
        print(json.dumps(report, indent=2))
    else:
        for check in checks:
            print("%s %-58s %s = %r%s" % ("ok  " if check["ok"] else "FAIL", check["check"], check["artifact"],
                                          check["measured"], "" if check["ok"] else "  expected %r" % check.get("expected")))
        print("verified: %s" % report["verified"])
    return 0 if report["verified"] else 1


if __name__ == "__main__":
    sys.exit(main())
