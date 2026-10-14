#!/usr/bin/env python3
"""Synthetic, offline contracts for the key-free CurseForge pin resolver.

No test here touches the network: ``fetch_project`` is monkeypatched or a cache is pre-seeded.
The properties that matter are that a pin either yields a *safe* URL built from the official CDN
layout, or is classified as unresolved - never silently substituted - and that a pin resolving to
something that is not a mod jar is reported as that kind rather than as an unsafe name or a mod.
"""
import json
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import cf_file_index as cf
from cf_file_index import (cdn_url, classify_name, load_pins, make_fetcher, resolve_pins,
                           summarize, validate_name)


def fixture_zip(path, mods=None):
    if mods is None:
        mods = [{"projectID": 238222, "fileID": 3043174, "required": True},
                {"projectID": 60028, "fileID": 2659258, "required": True},
                {"projectID": 9, "fileID": 9, "required": False}]
    manifest = {"manifestType": "minecraftModpack", "manifestVersion": 1, "name": "Synthetic",
                "version": "0.1", "minecraft": {"version": "1.12.2",
                                                 "modLoaders": [{"id": "forge-14.23.5.2860", "primary": True}]},
                "files": mods}
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.json", json.dumps(manifest))


def project(status=200, title="Fixture", files=None):
    return {"status": status, "title": title, "files": files or {}}


class CdnUrlTest(unittest.TestCase):
    def test_official_cdn_layout(self):
        self.assertEqual(cdn_url(3043174, "jei_1.12.2-4.16.1.302.jar"),
                         "https://edge.forgecdn.net/files/3043/174/jei_1.12.2-4.16.1.302.jar")
        self.assertEqual(cdn_url(1000000, "a.jar"), "https://edge.forgecdn.net/files/1000/0/a.jar")
        self.assertEqual(cdn_url(999, "a.jar"), "https://edge.forgecdn.net/files/0/999/a.jar")

    def test_name_is_percent_encoded_into_a_single_path_segment(self):
        url = cdn_url(1234567, "a b#c.jar")
        self.assertIn("/files/1234/567/a%20b%23c.jar", url)
        self.assertEqual(url.count("/"), 6)

    def test_rejects_bad_file_id(self):
        for bad in (0, -1, "3043174", None):
            with self.subTest(bad=bad):
                with self.assertRaises(cf.ResolveError):
                    cdn_url(bad, "a.jar")


class NameClassificationTest(unittest.TestCase):
    def test_validate_name_accepts_plain_jars(self):
        for good in ("jei_1.12.2-4.16.1.302.jar", "Ice and Fire-1.12.2-1.9.1.jar", "A.JAR"):
            self.assertEqual(validate_name(good), good)

    def test_validate_name_refuses_escape_and_non_jar(self):
        for bad in ("../evil.jar", "a/b.jar", "a\\b.jar", "a\x00b.jar", "a\nb.jar",
                    "..", ".", "", "notajar.zip", "x.jar.exe", None, 7):
            with self.subTest(bad=bad):
                with self.assertRaises(cf.ResolveError):
                    validate_name(bad)

    def test_classify_name_separates_kind_from_danger(self):
        self.assertEqual(classify_name("mod-1.0.jar"), "jar")
        # RLCraft pins 10 resource packs; a texture pack is a legitimate artifact, not an unsafe name.
        self.assertEqual(classify_name("Wilhelm.zip"), "other")
        self.assertEqual(classify_name("Emis_Rlcraft.zip"), "other")
        self.assertEqual(classify_name("pack.mcmeta"), "other")

    def test_classify_name_still_refuses_escapes(self):
        for bad in ("../evil.zip", "a/b.jar", "a\\b.zip", "a\x00b.zip", "..", "", None, 7):
            with self.subTest(bad=bad):
                with self.assertRaises(cf.ResolveError):
                    classify_name(bad)


class LoadPinsTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.path = Path(self.dir.name) / "pack.zip"

    def test_required_only_and_order_preserved(self):
        fixture_zip(self.path)
        pins = load_pins(self.path)
        self.assertEqual(pins, [{"projectID": 238222, "fileID": 3043174},
                                {"projectID": 60028, "fileID": 2659258}])

    def test_refuses_bad_pins_and_non_pack(self):
        fixture_zip(self.path, mods=[{"projectID": "x", "fileID": 1}])
        with self.assertRaises(cf.ResolveError):
            load_pins(self.path)
        fixture_zip(self.path, mods=[{"projectID": 0, "fileID": 1}])
        with self.assertRaises(cf.ResolveError):
            load_pins(self.path)
        fixture_zip(self.path, mods=[])
        with self.assertRaises(cf.ResolveError):
            load_pins(self.path)
        with zipfile.ZipFile(self.path, "w") as z:
            z.writestr("other.txt", "x")
        with self.assertRaises(cf.ResolveError):
            load_pins(self.path)


class ResolvePinsTest(unittest.TestCase):
    def setUp(self):
        self.calls = []

    def getter(self, mapping):
        def get(pid):
            self.calls.append(pid)
            return mapping.get(pid, project(status=404))
        return get

    def test_one_fetch_per_project_and_state_classification(self):
        pins = [{"projectID": 1, "fileID": 10}, {"projectID": 1, "fileID": 11},
                {"projectID": 2, "fileID": 20}, {"projectID": 3, "fileID": 30},
                {"projectID": 4, "fileID": 40}]
        mapping = {
            1: project(files={"10": {"name": "one-10.jar", "filesize": 111},
                              "11": {"name": "one-11.jar", "filesize": 222}}),
            2: project(files={}),                      # file id absent
            3: project(status=500),                    # upstream error
            4: project(files={"40": {"name": "../evil.jar"}}),
        }
        out = resolve_pins(pins, self.getter(mapping), delay=0)
        self.assertEqual(self.calls, [1, 2, 3, 4])
        self.assertEqual([r["state"] for r in out],
                         [cf.OK, cf.OK, cf.FILE_MISSING, cf.PROJECT_ERROR, cf.UNSAFE_NAME])
        self.assertEqual(out[0]["url"], "https://edge.forgecdn.net/files/0/10/one-10.jar")
        self.assertEqual(out[0]["filesize"], 111)
        self.assertEqual(out[0]["kind"], "jar")
        self.assertEqual(out[0]["projectTitle"], "Fixture")
        for r in out[2:]:
            self.assertIsNone(r["url"])
            self.assertIsNone(r["name"])

    def test_a_resolved_resource_pack_is_ok_with_kind_other(self):
        mapping = {5: project(files={"50": {"name": "Wilhelm.zip", "filesize": 10682288}})}
        out = resolve_pins([{"projectID": 5, "fileID": 50}], self.getter(mapping), delay=0)
        self.assertEqual(out[0]["state"], cf.OK)
        self.assertEqual(out[0]["kind"], "other")
        self.assertEqual(out[0]["url"], "https://edge.forgecdn.net/files/0/50/Wilhelm.zip")

    def test_project_404_is_distinct_from_upstream_error(self):
        out = resolve_pins([{"projectID": 7, "fileID": 70}], self.getter({}), delay=0)
        self.assertEqual(out[0]["state"], cf.PROJECT_MISSING)
        self.assertEqual(out[0]["httpStatus"], 404)

    def test_bad_filesize_is_not_trusted_but_url_still_resolves(self):
        mapping = {1: project(files={"10": {"name": "a.jar", "filesize": -5}})}
        out = resolve_pins([{"projectID": 1, "fileID": 10}], self.getter(mapping), delay=0)
        self.assertEqual(out[0]["state"], cf.OK)
        self.assertIsNone(out[0]["filesize"])

    def test_sleep_is_injected_and_rate_limited(self):
        slept = []
        pins = [{"projectID": p, "fileID": p} for p in (1, 2, 3)]
        resolve_pins(pins, self.getter({p: project(files={str(p): {"name": "a.jar", "filesize": 1}})
                                        for p in (1, 2, 3)}), delay=2.0, sleep=slept.append)
        self.assertEqual(slept, [2.0, 2.0])

    def test_summary_counts_kinds_and_unresolved_classification(self):
        pins = [{"projectID": 1, "fileID": 10}, {"projectID": 2, "fileID": 20},
                {"projectID": 3, "fileID": 30}]
        mapping = {1: project(files={"10": {"name": "a.jar", "filesize": 7}}),
                   3: project(files={"30": {"name": "pack.zip", "filesize": 9}})}
        out = resolve_pins(pins, self.getter(mapping), delay=0)
        s = summarize(out)
        self.assertEqual((s["pins"], s["resolved"], s["unresolved"], s["resolvedBytes"]), (3, 2, 1, 16))
        self.assertEqual(s["byState"], {cf.OK: 2, cf.PROJECT_MISSING: 1})
        self.assertEqual(s["byKind"], {"jar": 1, "other": 1})
        self.assertEqual(s["unresolvedPins"], [{"projectID": 2, "fileID": 20,
                                                "state": cf.PROJECT_MISSING, "httpStatus": 404}])


class CachingFetcherTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.cache = Path(self.dir.name) / "cache"
        self.orig = cf.fetch_project
        self.addCleanup(setattr, cf, "fetch_project", self.orig)
        self.seen = []

    def fake(self, status, files):
        def fetch(pid, timeout=60):
            self.seen.append(pid)
            return status, ({"title": f"p{pid}", "files": files} if status == 200 else None)
        return fetch

    def test_second_call_is_served_from_cache_without_network(self):
        cf.fetch_project = self.fake(200, [{"id": 10, "name": "a.jar", "filesize": 5}])
        get = make_fetcher(self.cache, False, {1: {10}}, 0)
        first = get(1)
        self.assertFalse(first["cached"])
        self.assertEqual(first["status"], 200)
        cf.fetch_project = self.fake(200, [])
        second = get(1)
        self.assertTrue(second["cached"])
        self.assertEqual(self.seen, [1])
        self.assertEqual(second["files"]["10"]["name"], "a.jar")

    def test_cache_holds_only_the_wanted_files(self):
        cf.fetch_project = self.fake(200, [{"id": 10, "name": "a.jar", "filesize": 5},
                                           {"id": 99, "name": "huge.jar", "filesize": 1 << 30}])
        get = make_fetcher(self.cache, False, {1: {10}}, 0)
        self.assertEqual(list(get(1)["files"]), ["10"])
        self.assertLess((self.cache / "1.json").stat().st_size, cf.MAX_INDEX_BYTES)

    def test_offline_miss_is_reported_not_faked(self):
        get = make_fetcher(self.cache, True, {1: {10}}, 0)
        slim = get(1)
        self.assertNotEqual(slim["status"], 200)
        self.assertTrue(slim["offline-miss"])

    def test_429_is_retried_once(self):
        codes = iter([429, 200])

        def fetch(pid, timeout=60):
            code = next(codes)
            return code, ({"title": "t", "files": [{"id": 10, "name": "a.jar", "filesize": 5}]}
                          if code == 200 else None)
        cf.fetch_project = fetch
        get = make_fetcher(self.cache, False, {1: {10}}, 0.0, sleep=lambda _: None)
        self.assertEqual(get(1)["status"], 200)

    def test_cache_file_is_never_followed_through_a_symlink(self):
        self.cache.mkdir(parents=True)
        target = Path(self.dir.name) / "outside.json"
        target.write_text(json.dumps({"title": "poisoned", "files": {"10": {"name": "a.jar"}}}))
        (self.cache / "1.json").symlink_to(target)
        cf.fetch_project = self.fake(200, [{"id": 10, "name": "real.jar", "filesize": 5}])
        get = make_fetcher(self.cache, False, {1: {10}}, 0)
        self.assertEqual(get(1)["files"]["10"]["name"], "real.jar")


class CliTest(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.root = Path(self.dir.name)
        self.pack = self.root / "pack.zip"
        fixture_zip(self.pack)
        self.script = Path(__file__).parent / "cf_file_index.py"

    def run_cli(self, *args):
        return subprocess.run([sys.executable, str(self.script), *args], capture_output=True, text=True)

    def test_offline_requires_a_cache(self):
        r = self.run_cli("--pack", str(self.pack), "--out", str(self.root / "i.json"), "--offline")
        self.assertEqual(r.returncode, 2)
        self.assertIn("--offline needs --cache-dir", r.stderr)

    def test_missing_pack_is_usage_error(self):
        r = self.run_cli("--pack", str(self.root / "nope.zip"), "--out", str(self.root / "i.json"))
        self.assertEqual(r.returncode, 2)

    def test_offline_run_from_seeded_cache_writes_index_and_anchors_the_pack_hash(self):
        cache = self.root / "cache"
        cache.mkdir()
        (cache / "238222.json").write_text(json.dumps(
            {"projectID": 238222, "title": "Just Enough Items",
             "files": {"3043174": {"name": "jei_1.12.2-4.16.1.302.jar", "filesize": 653210}}}))
        (cache / "60028.json").write_text(json.dumps(
            {"projectID": 60028, "title": "Other",
             "files": {"2659258": {"name": "other-1.0.jar", "filesize": 42}}}))
        out, report = self.root / "i.json", self.root / "r.json"
        r = self.run_cli("--pack", str(self.pack), "--out", str(out), "--cache-dir", str(cache),
                         "--report", str(report), "--offline")
        self.assertEqual(r.returncode, 0, r.stderr)
        index = json.loads(out.read_text())
        self.assertEqual(index["packSha256"], cf.sha256(self.pack))
        self.assertEqual(index["packName"], "pack.zip")
        self.assertEqual(len(index["entries"]), 2)
        self.assertEqual(index["entries"][0]["url"],
                         "https://edge.forgecdn.net/files/3043/174/jei_1.12.2-4.16.1.302.jar")
        self.assertEqual(json.loads(report.read_text())["resolved"], 2)

    def test_offline_does_not_pace_itself_against_a_network_it_never_touches(self):
        """A cache read is not a request: 40 pins must not cost 40 sleeps."""
        mods = [{"projectID": p, "fileID": p, "required": True} for p in range(1, 41)]
        fixture_zip(self.pack, mods=mods)
        cache = self.root / "cache"
        cache.mkdir()
        for p in range(1, 41):
            (cache / f"{p}.json").write_text(json.dumps(
                {"projectID": p, "title": f"p{p}", "files": {str(p): {"name": f"m{p}.jar", "filesize": 1}}}))
        r = subprocess.run([sys.executable, str(self.script), "--pack", str(self.pack),
                            "--out", str(self.root / "i.json"), "--cache-dir", str(cache),
                            "--report", str(self.root / "r.json"), "--offline"],
                           capture_output=True, text=True, timeout=30)
        self.assertEqual(r.returncode, 0, r.stderr)

    def test_unresolved_pin_exits_4_and_is_classified(self):
        cache = self.root / "cache"
        cache.mkdir()
        (cache / "238222.json").write_text(json.dumps(
            {"projectID": 238222, "title": "JEI", "files": {"3043174": {"name": "jei.jar", "filesize": 1}}}))
        out = self.root / "i.json"
        r = self.run_cli("--pack", str(self.pack), "--out", str(out), "--cache-dir", str(cache),
                         "--report", str(self.root / "r.json"), "--offline")
        self.assertEqual(r.returncode, 4)
        entries = {e["projectID"]: e for e in json.loads(out.read_text())["entries"]}
        # An --offline cache miss must not be reported as an upstream failure.
        self.assertEqual(entries[60028]["state"], cf.NOT_FETCHED)
        self.assertTrue(entries[60028]["offlineMiss"])
        self.assertIsNone(entries[60028]["url"])
        self.assertIsNone(entries[60028]["httpStatus"])

    def test_upstream_404_and_network_failure_are_different_states(self):
        out = resolve_pins([{"projectID": 1, "fileID": 1}, {"projectID": 2, "fileID": 2}],
                           lambda pid: project(status=404) if pid == 1 else project(status=0),
                           delay=0)
        self.assertEqual([r["state"] for r in out], [cf.PROJECT_MISSING, cf.NOT_FETCHED])
        self.assertEqual(out[0]["httpStatus"], 404)


if __name__ == "__main__":
    unittest.main(verbosity=2)
