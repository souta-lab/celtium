import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_agent_docs.py"
spec = importlib.util.spec_from_file_location("check_agent_docs", SCRIPT)
docs = importlib.util.module_from_spec(spec)
spec.loader.exec_module(docs)


class AgentDocsTests(unittest.TestCase):
    def test_misspelled_dump_flag_is_detected(self):
        keys = {"celtium.vulkan.presentedDumpEvery"}
        self.assertTrue(docs.property_problems("`-Dceltium.validation.presentedDumpEvery=0`", keys))
        self.assertEqual(docs.property_problems("`-Dceltium.vulkan.presentedDumpEvery=0`", keys), [])

    def test_values_and_wildcard_examples_are_not_keys(self):
        keys = {"celtium.vulkan.mirrorDump"}
        self.assertEqual(docs.property_problems("-Dceltium.vulkan.mirrorDump=/tmp/dump", keys), [])
        self.assertEqual(docs.property_problems("`-Dceltium.*`", keys), [])

    def test_local_link_uses_document_directory(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "docs/plan.md").write_text("state")
            page = root / "docs/guide.md"
            self.assertEqual(docs.link_problems(page, "[state](<plan.md>)"), [])
            self.assertTrue(docs.link_problems(page, "[state](../plan.md)"))
            self.assertEqual(docs.link_problems(page, "[web](https://example.invalid/a) [anchor](#x)"), [])


if __name__ == "__main__":
    unittest.main()
