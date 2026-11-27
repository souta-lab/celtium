import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

SCRIPT = Path(__file__).resolve().parents[1] / "check_celeritas_zero.py"
spec = importlib.util.spec_from_file_location("check_celeritas_zero", SCRIPT)
zero = importlib.util.module_from_spec(spec)
spec.loader.exec_module(zero)


def write(root, rel, text):
    path = Path(root) / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    return path


COMPETITORS_BODY = """package org.taumc.celeritas.core;

public final class CeltiumCompetitors {
    private static final String[][] KNOWN = {
            {"me/jellysquid/mods/sodium/client/SodiumClientMod.class", "Vintagium (vintagium)", "terrain"},
            {"com/dhj/actinium/Actinium.class", "Actinium (actinium)", "terrain"},
    };

    private CeltiumCompetitors() {
    }
}
"""


class CompetitorsBlockTests(unittest.TestCase):
    def test_block_covers_known_table_only(self):
        lines = COMPETITORS_BODY.splitlines()
        excluded = zero.competitors_block_lines(lines)
        known_line = next(i for i, l in enumerate(lines) if "jellysquid" in l)
        package_line = next(i for i, l in enumerate(lines) if l.startswith("package"))
        ctor_line = next(i for i, l in enumerate(lines) if "private CeltiumCompetitors()" in l)
        self.assertIn(known_line, excluded)
        self.assertNotIn(package_line, excluded)
        self.assertNotIn(ctor_line, excluded)


class Check1Tests(unittest.TestCase):
    def test_present_and_absent_paths(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "common/src/main/resources/assets/sodium/shaders/x.glsl", "// x\n")
            files = zero.tracked_files(root)
            failures = zero.check1_banned_paths(root, files)
            self.assertTrue(any(f.startswith("banned path present: common") for f in failures), failures)
            self.assertTrue(any("assets/sodium" in f for f in failures), failures)
            self.assertFalse(any("celeritas_at.cfg" in f for f in failures), failures)


class Check2Tests(unittest.TestCase):
    def test_banned_identifier_and_whitelist_seats(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "mod/src/main/java/org/soutalab/celtium/X.java",
                  "package org.soutalab.celtium;\nimport org.embeddedt.embeddium.Foo;\n")
            write(root, "docs/notes.md", "org.embeddedt.embeddium mentioned in docs\n")
            write(root, "docs/x.java", "org.embeddedt.embeddium in docs is excluded by path\n")
            write(root, zero.COMPETITORS_REL, COMPETITORS_BODY)
            write(root, "celtium-core/build.gradle", 'key = "com.gtnewhorizons.retrofuturagradle.obfuscation"\n')
            write(root, "other/build.gradle", 'key = "com.gtnewhorizons.retrofuturagradle.obfuscation"\n')
            files = zero.tracked_files(root)
            failures, notes = zero.check2_banned_identifiers(root, files)
            embeddium = [f for f in failures if f.startswith("org.embeddedt.embeddium")]
            self.assertEqual(len(embeddium), 1, failures)
            self.assertIn("in 1 files", embeddium[0], failures)  # X.java only; docs/ is path-excluded
            self.assertFalse(any("jellysquid" in f for f in failures), failures)
            self.assertTrue(any("unregistered file: other/build.gradle" in f for f in failures), failures)
            self.assertTrue(any("approved in 1 registered files" in n for n in notes), notes)


class JarCheckTests(unittest.TestCase):
    def make_jar(self, root, entries):
        jar = Path(root) / "celtium-forge-mc1.12.2-0.4.0.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return jar

    def good_entries(self):
        manifest = (
            "Manifest-Version: 1.0\r\n"
            "FMLCorePlugin: org.soutalab.celtium.core.CeltiumLoadingPlugin\r\n"
            "FMLAT: celtium_at.cfg\r\n"
            "Celtium-Vulkan-Minimum: 1.3\r\n"
            "Celtium-License: LGPL-3.0\r\n\r\n"
        )
        return {
            "META-INF/MANIFEST.MF": manifest,
            "mcmod.info": '[{"modid": "celtium", "name": "Celtium"}]',
            "org/soutalab/celtium/CeltiumMod.class": b"\xca\xfe\xba\xbe org/soutalab/celtium",
        }

    def test_clean_jar_passes_checks_3_and_4(self):
        with tempfile.TemporaryDirectory() as tmp:
            jar = self.make_jar(tmp, self.good_entries())
            self.assertEqual(zero.check3_jar_references([jar]), [])
            self.assertEqual(zero.check4_jar_metadata([jar]), [])

    def test_banned_class_and_competitors_exception(self):
        with tempfile.TemporaryDirectory() as tmp:
            entries = self.good_entries()
            entries["org/taumc/celeritas/core/Foo.class"] = b"\xca\xfe\xba\xbe"
            entries["org/soutalab/celtium/core/CeltiumCompetitors.class"] = (
                b"\xca\xfe\xba\xbe me/jellysquid/mods/sodium org.taumc.celeritas"
            )
            jar = self.make_jar(tmp, entries)
            failures = zero.check3_jar_references([jar])
            self.assertTrue(any("org.taumc.celeritas" in f and "Foo.class" in f for f in failures), failures)
            self.assertFalse(any("CeltiumCompetitors" in f for f in failures), failures)

    def test_bad_metadata_fails_check_4(self):
        with tempfile.TemporaryDirectory() as tmp:
            entries = self.good_entries()
            entries["mcmod.info"] = '[{"modid": "celeritas"}]'
            jar = self.make_jar(tmp, entries)
            failures = zero.check4_jar_metadata([jar])
            self.assertTrue(any("modid" in f for f in failures), failures)

    def test_missing_jar_fails(self):
        self.assertTrue(zero.check3_jar_references([]))
        self.assertTrue(zero.check4_jar_metadata([]))


class Check5Tests(unittest.TestCase):
    def test_provenance_classification(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root, "mod/src/main/java/org/soutalab/celtium/A.java", "package org.soutalab.celtium;\n")
            write(root, "mod/src/main/java/org/taumc/celeritas/B.java", "package org.taumc.celeritas;\n")
            write(root, "mod/src/main/java/grondag/bitraster/C.java", "package grondag.bitraster;\n")
            write(root, "mod/src/main/java/org/soutalab/celtium/D.java",
                  'package org.soutalab.celtium;\nString s = "com.dhj.actinium.Actinium";\n')
            write(root, zero.COMPETITORS_REL, COMPETITORS_BODY)
            write(root, "mod/src/test/java/org/taumc/celeritas/T.java", "package org.taumc.celeritas;\n")
            files = zero.tracked_files(root)
            failures, notes = zero.check5_provenance(root, files)
            # B.java plus CeltiumCompetitors.java itself (package org.taumc.celeritas.core).
            self.assertTrue(any("org.taumc.celeritas: 2 production files" in f for f in failures), failures)
            self.assertTrue(any("grondag.bitraster: 1 production files" in f for f in failures), failures)
            gpl = [f for f in failures if "com.dhj.actinium" in f]
            self.assertEqual(len(gpl), 1, failures)  # D.java only; the KNOWN table marker is whitelisted
            self.assertIn("D.java", gpl[0])
            self.assertTrue(any("approved provenance" in n and "2 production files" in n for n in notes), notes)


if __name__ == "__main__":
    unittest.main()
