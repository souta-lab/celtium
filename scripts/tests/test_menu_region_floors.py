"""The menu top-band floor separates the two working GPU captures from the two measured defects.

`scripts/cpu-contract/menu-region-floor-check.py` is the executable calibration for
`ValidationClient#celtium$gateMenuStage`: it re-measures the four committed pictures with the gate's rule and
cross-checks the Java constants. This test keeps it in the suite the round is required to run, so the gate's floors
cannot be changed (or its evidence pictures removed) without a failing unit test.
"""
import importlib.util
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "menu-region-floor-check.py"


def load_check():
    spec = importlib.util.spec_from_file_location("menu_region_floor_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class MenuRegionFloorTest(unittest.TestCase):
    def test_the_committed_menu_frames_clear_or_fall_below_the_floors(self):
        self.assertEqual(0, load_check().main(), "the menu top-band floors no longer separate the committed pictures")

    def test_the_java_gate_carries_the_same_constants(self):
        check = load_check()
        constants = check.java_constants()
        self.assertEqual(check.X0, constants["MENU_TOP_X0"])
        self.assertEqual(check.Y1, constants["MENU_TOP_Y1"])
        self.assertEqual(check.MIN_MEAN_LUMA, constants["MENU_TOP_MIN_MEAN_LUMA"])
        self.assertEqual(check.MIN_NON_BLACK, constants["MENU_TOP_MIN_NON_BLACK"])


if __name__ == "__main__":
    unittest.main()
