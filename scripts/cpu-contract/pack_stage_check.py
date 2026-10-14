#!/usr/bin/env python3
"""Exercise pack_stage.py's decisions on synthetic packs: the budget gate, the dry run, and the jar reading.

No network and no real pack: every case builds its own archive in a temp directory, so the checks are about
the script's own logic rather than about a CurseForge download that may or may not answer.

The cases that matter are the ones where being wrong is expensive. A staging run that ignores its budget
fills the disk. A dry run that writes leaves the tree changed when it promised not to. A pack archive that
gets written to is a user's original, modified. And an entry with no URL must be reported as not fetched
rather than counted as staged, because a mod that is silently missing looks exactly like a mod that passed.
"""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "scripts/pack_stage.py"


def make_jar(path: Path, plugin: str | None, at: str | None, mixin: str | None) -> None:
    """A jar with the three declarations the script reports, so it can be read back without a real mod."""
    mf = "Manifest-Version: 1.0\n"
    if plugin:
        mf += f"FMLCorePlugin: {plugin}\nFMLCorePluginContainsFMLMod: true\n"
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("META-INF/MANIFEST.MF", mf)
        if at:
            z.writestr(at, "# access transformer\n")
        if mixin:
            z.writestr(mixin, "{}\n")


def make_pack(path: Path, files: list[dict]) -> None:
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.json", json.dumps({"minecraft": {"version": "1.12.2"}, "files": files}))
        z.writestr("overrides/config/example.cfg", "x=1\n")


def run(*args: str) -> subprocess.CompletedProcess:
    return subprocess.run([sys.executable, str(SCRIPT), *args], capture_output=True, text=True)


def check(name: str, got, want) -> bool:
    ok = got == want
    print(f"{'ok  ' if ok else 'FAIL'} {name}: got {got!r} want {want!r}")
    return ok


def main() -> int:
    results = []
    with tempfile.TemporaryDirectory() as td:
        td = Path(td)

        # A pack whose entries have no URL at all: the RLCraft shape.
        nourl = td / "nourl.zip"
        make_pack(nourl, [{"required": True, "projectID": 1, "fileID": 2}])
        before = nourl.read_bytes()

        # The budget gate: an unknown-size plan must not be silently treated as fitting.
        out = td / "out-budget"
        r = run("--pack", str(nourl), "--out", str(out), "--budget-gb", "0.0001")
        # Every entry is unknown size, so the plan is 0 bytes and the gate lets it through; the important
        # part is that the run says the total may be larger rather than claiming a size it does not have.
        results.append(check("unknown sizes are announced", "may be larger" in r.stderr, True))

        # The dry run writes nothing.
        out2 = td / "out-dry"
        r = run("--pack", str(nourl), "--out", str(out2), "--budget-gb", "4", "--dry-run")
        results.append(check("dry run exits 0", r.returncode, 0))
        results.append(check("dry run writes nothing", out2.exists(), False))

        # A pack that is not a pack.
        notpack = td / "notpack.zip"
        with zipfile.ZipFile(notpack, "w") as z:
            z.writestr("hello.txt", "hi")
        r = run("--pack", str(notpack), "--out", str(td / "x"), "--budget-gb", "1")
        results.append(check("a pack without manifest.json exits 2", r.returncode, 2))

        # A missing pack.
        r = run("--pack", str(td / "absent.zip"), "--out", str(td / "x"), "--budget-gb", "1")
        results.append(check("a missing pack exits 2", r.returncode, 2))

        # The jar reader, driven through the script's own module so the parsing is the production one.
        jar = td / "Thing.jar"
        make_jar(jar, "com.example.ThingPlugin", "thing_at.cfg", "thing.mixins.json")
        sys.path.insert(0, str(ROOT / "scripts"))
        import pack_stage  # noqa: E402

        info = pack_stage.inspect_jar(jar)
        results.append(check("coremod plugin read", info["coremod"], "com.example.ThingPlugin"))
        results.append(check("containsMod read", info["containsMod"], True))
        results.append(check("access transformer read", info["accessTransformers"], ["thing_at.cfg"]))
        results.append(check("mixin config read", info["mixinConfigs"], ["thing.mixins.json"]))

        # A plugin name is read whole: a value must not be truncated by a display column.
        long_plugin = "org.embeddedt.vintagefix.core.VintageFixCore"
        jar2 = td / "Long.jar"
        make_jar(jar2, long_plugin, None, None)
        info2 = pack_stage.inspect_jar(jar2)
        results.append(check("a long plugin name is read untruncated", info2["coremod"], long_plugin))

        # No URL means not fetched, and the manifest entry keeps its identity.
        entries = pack_stage.plan_entries(json.loads(
            json.dumps({"files": [{"required": True, "projectID": 284754, "fileID": 2725062}]})), None)
        results.append(check("an entry without a URL is planned, not dropped", len(entries), 1))
        results.append(check("its url is empty", entries[0]["url"], ""))
        results.append(check("its name carries the ids", entries[0]["name"], "project284754-2725062.jar"))

        # The pack archive is only read.
        results.append(check("the pack archive is unchanged", nourl.read_bytes() == before, True))

    failed = results.count(False)
    print(f"\npack_stage contract: {len(results) - failed}/{len(results)} checks passed")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
