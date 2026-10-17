import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "instrument-index.py"
spec = importlib.util.spec_from_file_location("instrument_index", SCRIPT)
index = importlib.util.module_from_spec(spec)
spec.loader.exec_module(index)


class InstrumentIndexTests(unittest.TestCase):
    def collect(self, sources):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            paths = []
            for name, text in sources.items():
                path = root / name
                path.write_text(text, encoding="utf-8")
                paths.append(path)
            return index.collect(paths=paths, root=root)

    def test_actual_readers_only_and_multiline(self):
        props, _ = self.collect({"Example.java": '''
            text("celeritas.options.fake.name");
            System.setProperty("celtium.writerOnly", "true");
            Boolean.getBoolean("celtium.enabled");
            Integer.getInteger(
                "celtium.count", 40);
            Long.getLong("celtium.limit", -1L);
            System.getProperty("celtium.mode", "safe");
            other.getBoolean("celtium.notSystemProperty");
        '''})
        self.assertEqual(set(props), {"celtium.enabled", "celtium.count", "celtium.limit", "celtium.mode"})
        self.assertEqual(props["celtium.count"][0], ("Example.java", 5, "Integer.getInteger"))

    def test_comments_and_reader_text_in_strings_are_not_code(self):
        props, markers = self.collect({"Example.java": r'''
            // System.getProperty("celtium.comment"); "CELTIUM_COMMENT"
            /* Boolean.getBoolean("celtium.block"); */
            String fake = "System.getProperty(\"celtium.string\")";
            String url = "https://example.invalid/";
            System.getProperty("celtium.real");
            System.out.println("[Celtium] CELTIUM_NATIVE_DEVICE name=...");
        '''})
        self.assertEqual(set(props), {"celtium.real"})
        self.assertEqual(set(markers), {"CELTIUM_NATIVE_DEVICE"})

    def test_local_and_qualified_constants_not_unrelated_literal(self):
        props, _ = self.collect({
            "Settings.java": '''
                public static final String KEY = "celtium.indirect";
                public static final String UNUSED = "celtium.unused";
                Boolean.getBoolean(KEY);
            ''',
            "Consumer.java": 'System.getProperty(Settings.KEY, "false");',
            "Other.java": '''
                private static final String KEY = "celtium.other";
                Boolean.getBoolean(KEY);
            ''',
        })
        self.assertEqual(set(props), {"celtium.indirect", "celtium.other"})
        self.assertEqual(len(props["celtium.indirect"]), 2)

    def test_computed_keys_are_not_invented_exact_keys(self):
        props, _ = self.collect({"Example.java": '''
            System.getProperty("celtium.dynamic." + name);
            System.getProperty(factory());
        '''})
        self.assertFalse(props)

    def test_prefixed_markers_and_stable_order(self):
        props, markers = self.collect({
            "Z.java": 'Boolean.getBoolean("celtium.x"); log("prefix CELTIUM_DRAW_SITE #1");',
            "A.java": 'Boolean.getBoolean("celtium.x"); log("[Celtium] CELTIUM_DRAW_SITE #2");',
        })
        self.assertEqual([p[0] for p in props["celtium.x"]], ["A.java", "Z.java"])
        self.assertEqual([p[0] for p in markers["CELTIUM_DRAW_SITE"]], ["A.java", "Z.java"])
        self.assertEqual(index.render(props, markers), index.render(props, markers))
        self.assertNotIn("既定 |", index.render(props, markers))

    def test_real_index_contains_controls_and_markers_not_translation_keys(self):
        props, markers = index.collect()
        self.assertIn("celtium.vulkan.presentedDumpEvery", props)
        self.assertNotIn("celtium.validation.presentedDumpEvery", props)
        self.assertNotIn("celeritas.options.chunk_fade_in_duration.name", props)
        self.assertIn("CELTIUM_NATIVE_DEVICE", markers)
        self.assertIn("CELTIUM_DRAW_SITE", markers)
        self.assertIn("CELTIUM_GROUP_BOUNDS", markers)


if __name__ == "__main__":
    unittest.main()
