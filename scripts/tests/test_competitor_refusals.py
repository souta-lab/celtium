"""Wiring pins for the CeltiumCompetitors startup refusals (item 439).

The refusal itself runs in the forge122 coremod, which has no test source
set, so the pins live here, in the same source-reading style as
test_patch_error_wiring.py. What is pinned is the Phosphor entry's shape:
the marker class only Phosphor's jar carries, the message carrying the
Pulsar pointer, the check actually being called at coremod setup, and
the docs row that the message points at. Whether the marker class is
really in Phosphor's jar and really absent from Hesperus's was verified
against both jars when the entry was added (ITEM439-FIX-2026-11-20.md);
that is a jar fact, not something a source pin can re-check. The pointer
originally named Hesperus (the F7 landing); it was corrected to Pulsar
on 2026-11-20 to match the README's Install section, which names Pulsar
and marks Alfheim unsupported (ITEM439-GUIDANCE-FIX-2026-11-20.md).
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
COMPETITORS = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeltiumCompetitors.java"
LOADING_PLUGIN = ROOT / "forge122/src/main/java/org/taumc/celeritas/core/CeleritasLoadingPlugin.java"
COMPAT_DOC = ROOT / "docs/mod-compatibility.md"

PHOSPHOR_MARKER = "me/jellysquid/mods/phosphor/core/PhosphorFMLLoadingPlugin.class"
HESPERUS_PLUGIN = "me/jellysquid/mods/phosphor/core/PhosphorFMLPlugin.class"


class PhosphorEntryTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.source = COMPETITORS.read_text(encoding="utf-8")

    def test_the_phosphor_entry_is_present_with_its_marker_and_modid(self):
        self.assertIn(PHOSPHOR_MARKER, self.source)
        self.assertIn('"Phosphor (phosphorlighting)"', self.source)

    def test_the_refusal_message_points_at_pulsar_and_the_docs(self):
        # The entry's description is what check() prints after "replaces ";
        # it must carry the unmaintained note, the replacement the README's
        # Install section names (Pulsar) and the docs pointer, not just
        # name the conflict.
        self.assertIn("unmaintained", self.source)
        self.assertIn("use Pulsar Lighting Engine instead", self.source)
        self.assertIn("docs/mod-compatibility.md", self.source)

    def test_hesperus_itself_is_not_a_refusal_marker(self):
        # Hesperus renamed its plugin class to PhosphorFMLPlugin; keying the
        # refusal on that class would refuse a fork measured to coexist
        # with Celtium (leg 439b).
        self.assertNotIn(HESPERUS_PLUGIN, self.source)


class RefusalWiringTests(unittest.TestCase):
    def test_the_check_runs_from_the_loading_plugin(self):
        plugin = LOADING_PLUGIN.read_text(encoding="utf-8")
        self.assertIn("CeltiumCompetitors.check(", plugin)

    def test_the_escape_hatch_is_still_there(self):
        source = COMPETITORS.read_text(encoding="utf-8")
        self.assertIn('"celtium.allowCompetitors"', source)


class CompatDocTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.doc = COMPAT_DOC.read_text(encoding="utf-8")

    def test_phosphor_is_in_the_refusal_table_with_the_reason(self):
        self.assertIn("| Phosphor (phosphorlighting) | Refused at startup (`CeltiumCompetitors`)", self.doc)
        self.assertIn("InjectionError", self.doc)

    def test_the_docs_name_pulsar_as_the_recommended_replacement(self):
        self.assertIn("the recommended replacement is Pulsar Lighting Engine", self.doc)
        # The 439b coexistence measurement for Hesperus stays recorded.
        self.assertIn("Hesperus 0.2.9.3-SNAPSHOT", self.doc)
        self.assertIn("439b", self.doc)


if __name__ == "__main__":
    unittest.main()
