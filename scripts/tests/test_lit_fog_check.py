"""The lit-fog checker separates "the fog reached the picture" from "something else changed".

`scripts/cpu-contract/lit-fog-check.py` reads the two presented dumps one flag apart (`-Dceltium.vulkan.litFog=false`
against the default) and asks whether every changed pixel is the same pixel mixed toward the fog colour, with the sky
band untouched. The leg that produces the pair needs a GPU and a five-minute game launch, so this test pins the reading
itself: a synthetic mix at a known weight has to pass and be recovered, a band painted a fixed colour has to fail, and a
change in the sky band has to fail even when the rest of the frame is a perfect fog mix. A checker that cannot fail
would make the leg's PASSED meaningless.
"""
import importlib.util
import pathlib
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "lit-fog-check.py"

FOG = (128.0, 180.0, 220.0)


def load_checker():
    spec = importlib.util.spec_from_file_location("lit_fog_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=64, height=48):
    """A picture with a sky band on top and a textured-looking body below, so a mix is not a constant colour."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 6:
                pixels[x, y] = (90, 140, 200)          # sky
            else:
                pixels[x, y] = ((x * 7) % 200 + 20, (y * 11) % 180 + 30, (x * y) % 160 + 40)
    return image


def mix(image, weight, box=None):
    out = image.copy()
    pixels = out.load()
    width, height = out.size
    left, top, right, bottom = box or (0, height // 6, width, height)
    for y in range(top, bottom):
        for x in range(left, right):
            r, g, b = pixels[x, y]
            pixels[x, y] = (round(r + (FOG[0] - r) * weight), round(g + (FOG[1] - g) * weight),
                            round(b + (FOG[2] - b) * weight))
    return out


class LitFogCheckTest(unittest.TestCase):
    def setUp(self):
        self.checker = load_checker()
        self.before = base_picture()

    def test_a_known_mix_is_recovered_and_passes(self):
        after = mix(self.before, 0.35)
        result = self.checker.analyse(self.before, after, FOG)
        self.assertEqual([], self.checker.verdict(result))
        self.assertAlmostEqual(0.35, result["weightP50"], delta=0.02)
        self.assertLess(result["residualP99"], 1.0)
        self.assertEqual(0, result["skyChanged"])

    def test_a_band_painted_a_fixed_colour_fails(self):
        after = self.before.copy()
        pixels = after.load()
        for y in range(after.size[1] // 6, after.size[1]):
            for x in range(after.size[0]):
                pixels[x, y] = (255, 0, 0)
        result = self.checker.analyse(self.before, after, FOG)
        self.assertTrue(self.checker.verdict(result), "a colour change must not read as a fog mix")

    def test_a_change_in_the_sky_band_fails(self):
        after = mix(self.before, 0.35)
        pixels = after.load()
        for x in range(after.size[0]):
            pixels[x, 0] = (0, 0, 0)
        result = self.checker.analyse(self.before, after, FOG)
        problems = self.checker.verdict(result)
        self.assertTrue(any("sky band" in problem for problem in problems), problems)

    def test_identical_arms_fail(self):
        result = self.checker.analyse(self.before, self.before.copy(), FOG)
        problems = self.checker.verdict(result)
        self.assertTrue(any("identical" in problem for problem in problems), problems)

    def test_a_mix_that_overshoots_the_fog_colour_fails(self):
        after = mix(self.before, 1.4)
        result = self.checker.analyse(self.before, after, FOG)
        problems = self.checker.verdict(result)
        self.assertTrue(any("overshot" in problem for problem in problems), problems)

    def test_the_positive_control_passes_and_the_negative_one_fails(self):
        control = self.checker.synthetic_arms(self.before, FOG, 0.35)
        result = self.checker.analyse(self.before, control, FOG)
        self.assertEqual([], self.checker.verdict(result, min_weight=0.3))
        self.assertGreaterEqual(result["weightP50"], 0.3)
        self.assertLessEqual(result["weightP50"], 0.4)

    def test_the_fog_colour_parser_refuses_a_short_triple(self):
        with self.assertRaises(SystemExit):
            self.checker.parse_fog("128,180")
        self.assertEqual((128.0, 180.0, 220.0), self.checker.parse_fog("128, 180, 220"))


if __name__ == "__main__":
    unittest.main()
