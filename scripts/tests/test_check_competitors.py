import importlib.util
from pathlib import Path
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "check_competitors.py"
spec = importlib.util.spec_from_file_location("check_competitors", SCRIPT)
competitors = importlib.util.module_from_spec(spec)
spec.loader.exec_module(competitors)


class CompetitorRefusalCheckTests(unittest.TestCase):
    def test_refusal_drift_is_reported(self):
        self.assertEqual(competitors.check_refusals(), [])
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            comp = root / competitors.COMPETITORS
            comp.parent.mkdir(parents=True, exist_ok=True)
            comp.write_text('{"foo/Bar.class", "FakeMod (fakemod)", "renderer"}\n', encoding="utf-8")
            docs = root / "docs/mod-compatibility.md"
            docs.parent.mkdir(parents=True, exist_ok=True)
            docs.write_text("# None\n", encoding="utf-8")
            (root / "notworkingmods.md").write_text("# None\n", encoding="utf-8")
            problems = competitors.check_refusals(root)
            self.assertEqual(len(problems), 2, problems)
            self.assertTrue(all("FakeMod" in p for p in problems), problems)


if __name__ == "__main__":
    unittest.main()
