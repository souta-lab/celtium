#!/usr/bin/env python3
"""Offline contracts for resolving bare manifest pins through an explicit, validated resolve index.

No test here touches the network. ``head_size`` is replaced with a tripwire so that a test which
unexpectedly tries to size a download fails loudly instead of silently depending on a CDN.

The properties under test are the ones whose failure would be expensive and quiet: a pin that was
never resolved must stay ``no-url`` rather than borrow a neighbour's jar, a resolved URL must clear
the same forgecdn gate as a manifest URL, an index built from a different archive must be refused,
bytes whose size disagrees with the resolution must not be kept and must not be called staged, and a
pin that resolves to a resource pack must never be written into ``mods/``.
"""
import contextlib
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / "scripts"))
import pack_stage  # noqa: E402

GOOD_URL = "https://edge.forgecdn.net/files/3043/174/jei_1.12.2-4.16.1.302.jar"
ZIP_URL = "https://edge.forgecdn.net/files/1068/228/Wilhelm.zip"


def make_pack(path: Path, files=None, extra=None):
    files = files if files is not None else [{"required": True, "projectID": 238222, "fileID": 3043174},
                                             {"required": True, "projectID": 60028, "fileID": 2659258}]
    manifest = {"minecraft": {"version": "1.12.2"}, "files": files}
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("manifest.json", json.dumps(manifest))
        for name, data in (extra or {}).items():
            z.writestr(name, data)


def index_doc(pack: Path, entries, schema=1, pack_sha=None):
    return {"schema": schema, "packSha256": pack_sha if pack_sha is not None else pack_stage.sha256(pack),
            "entries": entries}


def ok_entry(pid, fid, url=GOOD_URL, name="jei_1.12.2-4.16.1.302.jar", size=653210, kind="jar"):
    return {"projectID": pid, "fileID": fid, "url": url, "name": name, "filesize": size,
            "kind": kind, "state": "ok"}


class Harness(unittest.TestCase):
    def setUp(self):
        self.dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.dir.cleanup)
        self.root = Path(self.dir.name)
        self.pack = self.root / "pack.zip"
        self.orig_argv, self.orig_head = sys.argv, pack_stage.head_size
        self.addCleanup(setattr, sys, "argv", self.orig_argv)
        self.addCleanup(setattr, pack_stage, "head_size", self.orig_head)

    def tripwire_head(self):
        pack_stage.head_size = lambda url: self.fail(f"unexpected network sizing of {url}")

    def patch_download(self, fake):
        """Replace the streaming download, restoring the production one afterwards."""
        original = pack_stage.download_bounded
        pack_stage.download_bounded = fake
        self.addCleanup(setattr, pack_stage, "download_bounded", original)

    def write_index(self, entries, **kw):
        path = self.root / "index.json"
        path.write_text(json.dumps(index_doc(self.pack, entries, **kw)))
        return path

    def run_main(self, *args):
        sys.argv = ["pack_stage.py", *map(str, args)]
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = pack_stage.main()
        return code, out.getvalue(), err.getvalue()

    def report(self, out_dir):
        return json.loads((out_dir / "stage-report.json").read_text())


class LoadResolveIndexTest(Harness):
    def setUp(self):
        super().setUp()
        make_pack(self.pack)

    def test_accepts_a_valid_index_and_keeps_only_resolved_entries(self):
        path = self.write_index([ok_entry(238222, 3043174),
                                 {"projectID": 60028, "fileID": 2659258, "state": "file-missing",
                                  "url": None, "name": None, "filesize": None}])
        got = pack_stage.load_resolve_index(path, self.pack)
        self.assertEqual(list(got), [(238222, 3043174)])
        self.assertEqual(got[(238222, 3043174)]["url"], GOOD_URL)
        self.assertEqual(got[(238222, 3043174)]["bytes"], 653210)
        self.assertEqual(got[(238222, 3043174)]["kind"], "jar")

    def test_accepts_a_non_jar_pin_and_records_its_kind(self):
        path = self.write_index([ok_entry(678889, 4370067, url=ZIP_URL, name="Wilhelm.zip",
                                          size=10682288, kind="other")])
        got = pack_stage.load_resolve_index(path, self.pack)
        self.assertEqual(got[(678889, 4370067)]["kind"], "other")

    def test_refuses_an_index_built_from_another_archive(self):
        path = self.write_index([ok_entry(238222, 3043174)], pack_sha="0" * 64)
        with self.assertRaisesRegex(pack_stage.PackError, "different archive"):
            pack_stage.load_resolve_index(path, self.pack)

    def test_refuses_untrusted_or_mismatched_urls(self):
        cases = {
            "plain http": ok_entry(238222, 3043174, url="http://edge.forgecdn.net/files/3043/174/x.jar"),
            "foreign host": ok_entry(238222, 3043174, url="https://evil.example/files/3043/174/x.jar"),
            "host suffix trick": ok_entry(238222, 3043174, url="https://notforgecdn.net/x.jar"),
            "traversal name": ok_entry(238222, 3043174,
                                       url="https://edge.forgecdn.net/files/1/2/%2e%2e%2fout.jar",
                                       name="../out.jar"),
            "name disagrees with url": ok_entry(238222, 3043174, name="other.jar"),
            "jar kind but not a jar": ok_entry(238222, 3043174, url=ZIP_URL, name="Wilhelm.zip"),
            "unknown kind": ok_entry(238222, 3043174, kind="tarball"),
        }
        for label, entry in cases.items():
            with self.subTest(label=label):
                path = self.write_index([entry])
                with self.assertRaises(pack_stage.PackError):
                    pack_stage.load_resolve_index(path, self.pack)

    def test_refuses_malformed_indexes(self):
        bad = [{"schema": 2, "entries": []}, {"schema": 1, "entries": "nope"},
               {"schema": 1, "entries": []},
               {"schema": 1, "entries": [ok_entry(238222, 3043174), ok_entry(238222, 3043174)]},
               {"schema": 1, "entries": [{"state": "ok", "projectID": "x", "fileID": 1, "url": GOOD_URL}]},
               {"schema": 1, "entries": [{"state": "ok", "projectID": 1, "fileID": 1, "url": None}]}]
        for doc in bad:
            with self.subTest(doc=str(doc)[:60]):
                path = self.root / "bad.json"
                path.write_text(json.dumps(doc))
                with self.assertRaises(pack_stage.PackError):
                    pack_stage.load_resolve_index(path, self.pack)

    def test_unreadable_index_is_a_pack_error_not_a_crash(self):
        path = self.root / "notjson.json"
        path.write_text("{not json")
        with self.assertRaises(pack_stage.PackError):
            pack_stage.load_resolve_index(path, self.pack)


class PlanEntriesTest(Harness):
    def test_a_pin_without_an_index_stays_unresolved(self):
        entries = pack_stage.plan_entries({"files": [{"required": True, "projectID": 1, "fileID": 2}]}, None)
        self.assertEqual(entries[0]["url"], "")
        self.assertNotIn("urlSource", entries[0])
        self.assertEqual(entries[0]["name"], "project1-2.jar")

    def test_a_pin_is_filled_only_from_the_index(self):
        idx = {(1, 2): {"url": GOOD_URL, "name": "jei_1.12.2-4.16.1.302.jar", "bytes": 10, "kind": "jar"}}
        entries = pack_stage.plan_entries({"files": [{"required": True, "projectID": 1, "fileID": 2}]}, None, idx)
        self.assertEqual(entries[0]["url"], GOOD_URL)
        self.assertEqual(entries[0]["urlSource"], "resolve-index")
        self.assertEqual(entries[0]["expectedBytes"], 10)

    def test_an_index_entry_for_another_pin_is_not_borrowed(self):
        idx = {(9, 9): {"url": GOOD_URL, "name": "jei_1.12.2-4.16.1.302.jar", "bytes": 10, "kind": "jar"}}
        entries = pack_stage.plan_entries({"files": [{"required": True, "projectID": 1, "fileID": 2}]}, None, idx)
        self.assertEqual(entries[0]["url"], "")

    def test_a_manifest_url_wins_and_is_labelled(self):
        url = "https://edge.forgecdn.net/files/1/2/from-manifest.jar"
        idx = {(1, 2): {"url": GOOD_URL, "name": "jei_1.12.2-4.16.1.302.jar", "bytes": 10, "kind": "jar"}}
        entries = pack_stage.plan_entries(
            {"files": [{"required": True, "projectID": 1, "fileID": 2, "downloadUrl": url}]}, None, idx)
        self.assertEqual(entries[0]["url"], url)
        self.assertEqual(entries[0]["urlSource"], "manifest")

    def test_a_resource_pack_pin_is_kept_and_marked_rather_than_rejected(self):
        idx = {(1, 2): {"url": ZIP_URL, "name": "Wilhelm.zip", "bytes": 7, "kind": "other"}}
        entries = pack_stage.plan_entries({"files": [{"required": True, "projectID": 1, "fileID": 2}]}, None, idx)
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["kind"], "other")
        self.assertEqual(entries[0]["name"], "Wilhelm.zip")

    def test_a_resource_pack_does_not_collide_with_a_mod_of_the_same_basename(self):
        idx = {(1, 2): {"url": ZIP_URL, "name": "Thing.jar", "bytes": 7, "kind": "other"},
               (3, 4): {"url": "https://edge.forgecdn.net/files/0/5/thing.jar",
                        "name": "thing.jar", "bytes": 7, "kind": "jar"}}
        entries = pack_stage.plan_entries(
            {"files": [{"required": True, "projectID": 1, "fileID": 2},
                       {"required": True, "projectID": 3, "fileID": 4}]}, None, idx)
        self.assertEqual([e["kind"] for e in entries], ["other", "jar"])


class MainIntegrationTest(Harness):
    def setUp(self):
        super().setUp()
        make_pack(self.pack)
        self.tripwire_head()

    def test_dry_run_with_a_full_index_needs_no_network_and_sizes_from_the_index(self):
        path = self.write_index([ok_entry(238222, 3043174),
                                 ok_entry(60028, 2659258, url="https://edge.forgecdn.net/files/2659/258/other.jar",
                                          name="other.jar", size=1000)])
        out = self.root / "out"
        code, stdout, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                        "--dry-run", "--resolve-index", path)
        self.assertEqual(code, 0)
        self.assertIn("resolved pins", stdout)
        self.assertFalse(out.exists(), "a dry run must not create the output tree")

    def test_without_an_index_every_pin_is_reported_no_url_and_nothing_is_faked(self):
        out = self.root / "out"
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1")
        self.assertEqual(code, 4)
        report = self.report(out)
        self.assertEqual(report["staged"], 0)
        self.assertEqual(report["resolvedByIndex"], 0)
        self.assertEqual(len(report["unresolved"]), 2)
        self.assertEqual({u["state"] for u in report["unresolved"]}, {"no-url"})
        self.assertEqual(sorted(p.name for p in (out / "mods").iterdir()), [])

    def test_index_built_from_a_different_archive_is_refused_before_any_write(self):
        path = self.write_index([ok_entry(238222, 3043174)], pack_sha="1" * 64)
        out = self.root / "out"
        code, _, err = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                     "--resolve-index", path)
        self.assertEqual(code, 2)
        self.assertIn("different archive", err)
        self.assertFalse(out.exists())

    def test_staged_download_is_recorded_and_index_sized(self):
        path = self.write_index([ok_entry(238222, 3043174, size=64),
                                 ok_entry(60028, 2659258, url="https://edge.forgecdn.net/files/2659/258/other.jar",
                                          name="other.jar", size=32)])

        def fake_download(url, target, remaining):
            n = 64 if "jei" in url else 32
            target.write_bytes(b"x" * n)
            return n
        self.patch_download(fake_download)
        out = self.root / "out"
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path)
        self.assertEqual(code, 0)
        report = self.report(out)
        self.assertEqual((report["staged"], report["failed"], report["resolvedByIndex"]), (2, 0, 2))
        self.assertEqual(report["urlSources"], {"resolve-index": 2})
        self.assertEqual(report["bytesOnDisk"], 96)

    def test_a_size_disagreement_keeps_nothing_and_is_not_called_staged(self):
        path = self.write_index([ok_entry(238222, 3043174, size=64)])

        def fake_download(url, target, remaining):
            target.write_bytes(b"x" * 63)   # one byte short of the resolution
            return 63
        self.patch_download(fake_download)
        out = self.root / "out"
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path, "--only", "jei")
        self.assertEqual(code, 4)
        report = self.report(out)
        entry = next(e for e in report["files"] if e["projectID"] == 238222)
        self.assertEqual(entry["state"], "failed")
        self.assertIn("63 bytes on the wire", entry["error"])
        self.assertFalse((out / "mods" / entry["name"]).exists(), "mismatched bytes must not be kept")
        self.assertEqual(report["bytesOnDisk"], 0)

    def test_budget_refusal_writes_nothing_and_exits_3(self):
        path = self.write_index([ok_entry(238222, 3043174, size=2 << 30)])
        out = self.root / "out"
        code, _, err = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                     "--resolve-index", path)
        self.assertEqual(code, 3)
        self.assertIn("exceeds the budget", err)
        self.assertFalse((out / "mods").exists())

    def test_a_resource_pack_is_never_written_into_mods_and_exits_5(self):
        make_pack(self.pack, files=[{"required": True, "projectID": 238222, "fileID": 3043174},
                                    {"required": True, "projectID": 678889, "fileID": 4370067}])
        path = self.write_index([ok_entry(238222, 3043174, size=64),
                                 ok_entry(678889, 4370067, url=ZIP_URL, name="Wilhelm.zip",
                                          size=128, kind="other")])

        def fake_download(url, target, remaining):
            n = 128 if url == ZIP_URL else 64
            target.write_bytes(b"x" * n)
            return n
        self.patch_download(fake_download)
        out = self.root / "out"
        code, _, err = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                     "--resolve-index", path)
        # Exit 5, not 0: the pack pins an artifact this run was not asked to stage.
        self.assertEqual(code, 5)
        self.assertIn("not mod jars", err)
        report = self.report(out)
        self.assertEqual([e["name"] for e in report["notStagedNonJar"]], ["Wilhelm.zip"])
        self.assertEqual(report["notStagedNonJar"][0]["url"], ZIP_URL)
        self.assertFalse((out / "mods" / "Wilhelm.zip").exists(), "a texture pack must not land in mods/")
        self.assertEqual(sorted(p.name for p in (out / "mods").iterdir()), ["jei_1.12.2-4.16.1.302.jar"])
        self.assertEqual(report["staged"], 1)

    def test_resourcepacks_out_stages_the_non_jar_pin_into_its_own_directory(self):
        make_pack(self.pack, files=[{"required": True, "projectID": 238222, "fileID": 3043174},
                                    {"required": True, "projectID": 678889, "fileID": 4370067}])
        path = self.write_index([ok_entry(238222, 3043174, size=64),
                                 ok_entry(678889, 4370067, url=ZIP_URL, name="Wilhelm.zip",
                                          size=128, kind="other")])

        def fake_download(url, target, remaining):
            n = 128 if url == ZIP_URL else 64
            target.write_bytes(b"x" * n)
            return n
        self.patch_download(fake_download)
        out, rp = self.root / "out", self.root / "out" / "resourcepacks"
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path, "--resourcepacks-out", rp)
        self.assertEqual(code, 0)
        report = self.report(out)
        self.assertEqual(report["notStagedNonJar"], [])
        self.assertEqual(report["staged"], 2)
        self.assertEqual((rp / "Wilhelm.zip").read_bytes(), b"x" * 128)
        self.assertFalse((out / "mods" / "Wilhelm.zip").exists())
        self.assertEqual(report["bytesOnDisk"], 192)


    def test_an_already_present_file_is_re_checked_against_the_resolution(self):
        """A resumed run must not accept whatever is on disk just because the name matches."""
        make_pack(self.pack, files=[{"required": True, "projectID": 238222, "fileID": 3043174}])
        path = self.write_index([ok_entry(238222, 3043174, size=64)])
        out = self.root / "out"
        (out / "mods").mkdir(parents=True)
        (out / "mods" / "jei_1.12.2-4.16.1.302.jar").write_bytes(b"x" * 63)   # one byte short
        self.patch_download(lambda *a: self.fail("a size-mismatched file must not be silently overwritten"))
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path)
        self.assertEqual(code, 4)
        report = self.report(out)
        entry = report["files"][0]
        self.assertEqual(entry["state"], "size-mismatch")
        self.assertIn("on disk, 64 resolved", entry["error"])
        self.assertEqual((out / "mods" / "jei_1.12.2-4.16.1.302.jar").read_bytes(), b"x" * 63,
                         "the mismatched file is left for a human, not replaced")

    def test_a_matching_present_file_is_reported_present(self):
        make_pack(self.pack, files=[{"required": True, "projectID": 238222, "fileID": 3043174}])
        path = self.write_index([ok_entry(238222, 3043174, size=64)])
        out = self.root / "out"
        (out / "mods").mkdir(parents=True)
        (out / "mods" / "jei_1.12.2-4.16.1.302.jar").write_bytes(b"x" * 64)
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path)
        self.assertEqual(code, 0)
        report = self.report(out)
        self.assertEqual((report["present"], report["staged"], report["failed"]), (1, 0, 0))

    def test_fingerprint_report_is_written_and_free_of_local_paths(self):
        make_pack(self.pack, files=[{"required": True, "projectID": 238222, "fileID": 3043174}])
        path = self.write_index([ok_entry(238222, 3043174, size=64)])

        def fake_download(url, target, remaining):
            target.write_bytes(b"x" * 64)
            return 64
        self.patch_download(fake_download)
        out, fp = self.root / "out", self.root / "fingerprints.json"
        code, _, _ = self.run_main("--pack", self.pack, "--out", out, "--budget-gb", "1",
                                   "--resolve-index", path, "--fingerprint-report", fp)
        self.assertEqual(code, 0)
        doc = json.loads(fp.read_text())
        self.assertEqual(doc["pack"], "pack.zip")
        self.assertEqual(doc["packSha256"], pack_stage.sha256(self.pack))
        self.assertEqual(doc["bytesOnDisk"], 64)
        self.assertEqual(len(doc["artifacts"]), 1)
        self.assertEqual(len(doc["artifacts"][0]["sha256"]), 64)
        self.assertNotIn(str(self.root), fp.read_text(), "a committable report must not carry local paths")

    def test_a_dry_run_does_not_write_a_fingerprint_report(self):
        path = self.write_index([ok_entry(238222, 3043174, size=64)])
        fp = self.root / "fingerprints.json"
        code, _, _ = self.run_main("--pack", self.pack, "--out", self.root / "out", "--budget-gb", "1",
                                   "--dry-run", "--resolve-index", path, "--fingerprint-report", fp)
        self.assertEqual(code, 0)
        self.assertFalse(fp.exists())


if __name__ == "__main__":
    unittest.main(verbosity=2)
