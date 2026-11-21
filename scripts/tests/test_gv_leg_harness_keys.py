"""Completeness of the gv_leg -D -> harness-file conversion table against the Java readers.

A HarnessConfig reader only ever looks at celtium-harness.properties. When a leg names the key in
its natural -D form instead, make_plan moves it into the file only if the conversion knows it;
an unknown -D stays a plain JVM argument and the control silently does nothing - the run records
a control it never felt. Six renderer keys were in exactly that state (bughunt 09-06: beCensus,
beLos, beLosCoverage, occluderBoxStats, commandBufferTargets, dumpLoadingProgress), and nothing
checked the table against the readers. This test is that check, in both directions:

- every short key read on the renderer side (common / celtium-vulkan / forge122) must convert
  from its celtium.vulkan.* (or celtium.debug.*) -D form through gv_leg's table;
- every short key read by the validation client must convert through the celtium.validation.*
  prefix rule;
- every row of the table must name a key some Java reader actually reads, so the table cannot
  rot in the other direction either.
"""
import re
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import gv_leg as runner

ROOT = Path(__file__).resolve().parents[2]
READER = re.compile(r'HarnessConfig\.(?:bool|integer|longValue|string|path)\("([A-Za-z0-9_]+)"')
RENDERER_MODULES = ("common", "celtium-vulkan", "forge122")


def keys_in(module):
    found = set()
    for path in (ROOT / module).rglob("*.java"):
        found.update(READER.findall(path.read_text(errors="replace")))
    return found


class HarnessKeyTableCompleteness(unittest.TestCase):
    def test_renderer_keys_are_convertible(self):
        renderer_keys = set()
        for module in RENDERER_MODULES:
            renderer_keys |= keys_in(module)
        # Coverage is by table value, not by -D spelling: one row's -D name differs from its
        # short key (celtium.vulkan.validation -> vulkanValidation), so a roundtrip through
        # harness_short_key with a guessed name would report that row's key as missing.
        missing = sorted(renderer_keys - set(runner.HARNESS_KEYS.values()))
        self.assertEqual([], missing,
                         "renderer HarnessConfig keys with no gv_leg conversion row: "
                         "their -D form stays a JVM argument the reader never sees")

    def test_validation_keys_use_the_prefix_rule(self):
        for key in keys_in("validation"):
            self.assertEqual(key, runner.harness_short_key("celtium.validation." + key))

    def test_table_rows_name_real_readers(self):
        all_keys = set()
        for module in RENDERER_MODULES + ("validation",):
            all_keys |= keys_in(module)
        stale = sorted(set(runner.HARNESS_KEYS.values()) - all_keys)
        self.assertEqual([], stale, "gv_leg conversion rows no Java reader reads")


if __name__ == "__main__":
    unittest.main()
