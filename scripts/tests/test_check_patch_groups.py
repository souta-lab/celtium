import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_patch_groups.py"
spec = importlib.util.spec_from_file_location("check_patch_groups", SCRIPT)
groups = importlib.util.module_from_spec(spec)
spec.loader.exec_module(groups)

TABLE = "celtium-vulkan/src/main/java/org/soutalab/celtium/patch/PatchGroups.java"
MIXINS = "forge122/src/main/java/org/taumc/celeritas/mixin"
CONFIG_JSON = "forge122/src/main/resources/mixins.celeritas.json"
PLUGIN = "forge122/src/main/java/org/taumc/celeritas/mixin/CeleritasVintageMixinPlugin.java"
SHADOW_JSON = '{\n  "client": [\n    "core.SplashProgressMixin",\n    "core.GLAllocationMixin"\n  ]\n}\n'
CLEAN_PLUGIN = "package org.taumc.celeritas.mixin;\npublic class CeleritasVintageMixinPlugin {}\n"

# A mixin is a file carrying the annotation in code; the body is irrelevant to the checker.
MIXIN = "package org.taumc.celeritas.mixin;\n@Mixin(Target.class)\npublic class {} {}\n"


class PatchGroupTableTests(unittest.TestCase):
    def table(self, entries):
        return "package org.soutalab.celtium.patch;\npublic final class PatchGroups {\n" \
               "    private static void group(String id, String title, boolean essential, String cost, String... members) {}\n" \
               "    private static void table() {\n" + entries + "    }\n}\n"

    def entry(self, identifier, essential, members, cost="one sentence"):
        return (f'        group("{identifier}", "{identifier} title", {str(essential).lower()},\n'
                f'              "{cost}",\n'
                + "".join(f'              "{member}",\n' for member in members)
                + "        );\n")

    def tree(self, tmp, entries, mixins, config_json=SHADOW_JSON, plugin=CLEAN_PLUGIN):
        root = Path(tmp)
        table = root / TABLE
        table.parent.mkdir(parents=True)
        table.write_text(self.table(entries), encoding="utf-8")
        for name in mixins:
            path = root / MIXINS / (name.replace(".", "/") + ".java")
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(MIXIN.replace("{}", name.rsplit(".", 1)[-1]), encoding="utf-8")
        if config_json is not None:
            config = root / CONFIG_JSON
            config.parent.mkdir(parents=True, exist_ok=True)
            config.write_text(config_json, encoding="utf-8")
        if plugin is not None:
            plugin_path = root / PLUGIN
            plugin_path.parent.mkdir(parents=True, exist_ok=True)
            plugin_path.write_text(plugin, encoding="utf-8")
        return root

    def test_consistent_table_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.AlphaMixin"])
                             + self.entry("sky", False, ["core.CloudMixin", "features.render.RainMixin"]),
                             ["core.AlphaMixin", "core.CloudMixin", "features.render.RainMixin"])
            self.assertEqual(groups.check(root), [])
            problems, summary = groups.analyze(root)
            self.assertEqual(problems, [])
            self.assertEqual([group["id"] for group in summary["groups"]], ["mechanism", "sky"])
            self.assertEqual(summary["discovered"],
                             ["core.AlphaMixin", "core.CloudMixin", "features.render.RainMixin"])

    def test_unlisted_mixin_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.AlphaMixin"]),
                             ["core.AlphaMixin", "core.NewcomerMixin"])
            problems = groups.check(root)
            self.assertEqual(len(problems), 1)
            self.assertIn("core.NewcomerMixin", problems[0])
            self.assertIn("not in the patch-group table", problems[0])

    def test_stale_entry_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.AlphaMixin", "core.GoneMixin"]),
                             ["core.AlphaMixin"])
            problems = groups.check(root)
            self.assertEqual(len(problems), 1)
            self.assertIn("core.GoneMixin", problems[0])
            self.assertIn("not a mixin in the source tree", problems[0])

    def test_member_listed_twice_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.AlphaMixin"])
                             + self.entry("sky", False, ["core.AlphaMixin"]),
                             ["core.AlphaMixin"])
            problems = groups.check(root)
            self.assertTrue(any("core.AlphaMixin is listed twice" in problem for problem in problems))
            self.assertIn("groups mechanism and sky", " ".join(problems))

    def test_duplicate_group_id_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("sky", False, ["core.CloudMixin"])
                             + self.entry("sky", False, ["core.RainMixin"]),
                             ["core.CloudMixin", "core.RainMixin"])
            problems = groups.check(root)
            self.assertTrue(any("duplicate group id: sky" in problem for problem in problems))

    def test_empty_group_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("sky", False, []), [])
            problems = groups.check(root)
            self.assertTrue(any("group sky lists no mixins" in problem for problem in problems))

    def test_ambiguous_simple_name_must_be_qualified(self):
        mixins = ["core.RenderItemMixin", "features.textures.RenderItemMixin"]
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.RenderItemMixin"])
                             + self.entry("textures", True, ["features.textures.RenderItemMixin"]), mixins)
            self.assertEqual(groups.check(root), [])
            _, summary = groups.analyze(root)
            self.assertEqual(summary["collisions"], {"RenderItemMixin": mixins})
            self.assertIn("RenderItemMixin = core.RenderItemMixin, features.textures.RenderItemMixin",
                          groups.describe(summary))
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("textures", True, ["RenderItemMixin"]), mixins)
            problems = groups.check(root)
            self.assertTrue(any("addresses RenderItemMixin by simple name" in problem for problem in problems))
            self.assertTrue(any("core.RenderItemMixin" in problem and "features.textures.RenderItemMixin" in problem
                                for problem in problems))

    def test_prose_and_strings_are_not_parsed(self):
        prose = ("    // group(\"fake\", \"comment\", true, \"cost\", \"core.AlphaMixin\");\n"
                 "    /* group(\"block\", \"comment\", true, \"cost\", \"core.AlphaMixin\"); */\n"
                 "    // @Mixin(Target.class) in a comment is not a mixin\n")
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, prose + self.entry("mechanism", True, ["core.AlphaMixin"]),
                             ["core.AlphaMixin", "core.MentionedMixin"])
            table = root / TABLE
            # A javadoc mention of the table form, and an @Mixin inside a string literal, must both be ignored.
            table.write_text(table.read_text(encoding="utf-8").replace(
                "public final class PatchGroups {",
                "public final class PatchGroups {\n    /** Mentions group(\"doc\", \"x\", true, \"y\", \"core.Z\"). */"),
                encoding="utf-8")
            (root / MIXINS / "core/MentionedMixin.java").write_text(
                'package org.taumc.celeritas.mixin;\nclass MentionedMixin { String s = "@Mixin(Target.class)"; }\n',
                encoding="utf-8")
            problems, summary = groups.analyze(root)
            # A comment or a string literal names nothing: the tree is clean and no phantom group or mixin appeared.
            self.assertEqual(problems, [])
            self.assertNotIn("core.MentionedMixin", summary["discovered"])
            self.assertEqual([group["id"] for group in summary["groups"]], ["mechanism"])

    def test_cost_string_with_punctuation_does_not_split_arguments(self):
        cost = "cast by (two) callers, so it throws: ClassCastException"
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, self.entry("mechanism", True, ["core.AlphaMixin"], cost=cost),
                             ["core.AlphaMixin"])
            self.assertEqual(groups.check(root), [])
            _, summary = groups.analyze(root)
            self.assertEqual(summary["groups"][0]["cost"], cost)

    def test_declaration_alone_is_not_a_table(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, "", ["core.AlphaMixin"])
            problems = groups.check(root)
            self.assertTrue(any("no group( entries found" in problem for problem in problems))

    def test_missing_table_and_empty_tree_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self.assertTrue(groups.check(root))
            table = root / TABLE
            table.parent.mkdir(parents=True)
            table.write_text(self.table(self.entry("mechanism", True, [])), encoding="utf-8")
            problems = groups.check(root)
            self.assertTrue(any("no mixins discovered" in problem for problem in problems))

    def test_shadow_json_is_pinned(self):
        entries = self.entry("mechanism", True, ["core.AlphaMixin"])
        mixins = ["core.AlphaMixin"]
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, entries, mixins)
            self.assertEqual(groups.check(root), [])
            _, summary = groups.analyze(root)
            self.assertEqual(summary["shadow"], ["core.GLAllocationMixin", "core.SplashProgressMixin"])
            self.assertIn("shadow-json=2 entries (unchanged)", groups.describe(summary))
        with tempfile.TemporaryDirectory() as tmp:
            # One shadow entry removed: the companion pass would apply that mixin for the first time.
            root = self.tree(tmp, entries, mixins,
                             config_json='{\n  "client": [\n    "core.SplashProgressMixin"\n  ]\n}\n')
            problems = groups.check(root)
            self.assertTrue(any("pinned shadow list" in problem for problem in problems), problems)
        with tempfile.TemporaryDirectory() as tmp:
            # A third name added: it would be shadowed exactly like the known two.
            root = self.tree(tmp, entries, mixins,
                             config_json='{\n  "client": [\n    "core.SplashProgressMixin",\n'
                                         '    "core.GLAllocationMixin",\n    "core.AlphaMixin"\n  ]\n}\n')
            problems = groups.check(root)
            self.assertTrue(any("pinned shadow list" in problem for problem in problems), problems)

    def test_plugin_property_literal_is_reported(self):
        entries = self.entry("mechanism", True, ["core.AlphaMixin"])
        mixins = ["core.AlphaMixin"]
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, entries, mixins)
            _, summary = groups.analyze(root)
            self.assertEqual(summary["plugin_property_reads"], 0)
            self.assertIn("plugin property reads=0", groups.describe(summary))
        with tempfile.TemporaryDirectory() as tmp:
            root = self.tree(tmp, entries, mixins,
                             plugin='class P { String s = "celtium.patch.tweaks"; }\n')
            problems = groups.check(root)
            self.assertTrue(any("patch property literal" in problem for problem in problems), problems)

    def test_repository_table_matches_the_mixin_tree(self):
        self.assertEqual(groups.check(), [])
        _, summary = groups.analyze()
        self.assertEqual(len(summary["discovered"]), 102)
        self.assertEqual(sorted(summary["collisions"]),
                         ["RenderItemMixin", "TextureAtlasSpriteMixin", "TextureUtilMixin"])
        self.assertEqual(summary["shadow"], ["core.GLAllocationMixin", "core.SplashProgressMixin"])
        self.assertEqual(summary["plugin_property_reads"], 0)


if __name__ == "__main__":
    unittest.main()
