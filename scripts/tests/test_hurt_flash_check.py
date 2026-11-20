"""The hurt-flash checker separates "the flash put one red weight on the flash carrier" from everything else.

`scripts/cpu-contract/hurt-flash-check.py` reads the three presented dumps the fixture's
`entityHurtAbTick` produces (off / on / off) and fits the flash's mix weight per pixel. The leg that
produces the triple needs a GPU and a game launch, so this test pins the reading itself: a single
weight on the bare carrier has to pass; two weights, a flashed armour carrier, a scene that moves
between its outer frames, and a flash present in every frame all have to fail, each on the condition
that names it. A checker that cannot fail would make the leg's PASSED meaningless.
"""
import importlib.util
import pathlib
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "hurt-flash-check.py"

# The carriers as the fixture places them: F (flash) left of centre, G (glint, armoured) right of it.
F_RECT = (20, 45, 49, 94)
G_RECT = (105, 45, 134, 94)
G_REGION = (100, 40, 140, 100)


def load_checker():
    spec = importlib.util.spec_from_file_location("hurt_flash_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=160, height=120):
    """A sky band, a textured ground, and the two carriers as flat-shaded rectangles."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 3:
                pixels[x, y] = (120, 170, 230)
            else:
                pixels[x, y] = ((x * 5) % 90 + 40, (y * 7) % 80 + 60, (x * y) % 60 + 30)
    for x in range(F_RECT[0], F_RECT[2] + 1):
        for y in range(F_RECT[1], F_RECT[3] + 1):
            pixels[x, y] = (95, 115, 70)
    for x in range(G_RECT[0], G_RECT[2] + 1):
        for y in range(G_RECT[1], G_RECT[3] + 1):
            pixels[x, y] = (105, 105, 95)
    return image


def flash(image, rect, w, y_split=None, w_top=None):
    """Applies the flash's mix toward (255, 0, 0) at weight w over a rectangle (or two weights split by y)."""
    out = image.copy()
    pixels = out.load()
    for x in range(rect[0], rect[2] + 1):
        for y in range(rect[1], rect[3] + 1):
            weight = w
            if y_split is not None and y < y_split:
                weight = w_top
            r, g, b = pixels[x, y]
            pixels[x, y] = (round(r + (255 - r) * weight), round(g + (0 - g) * weight),
                            round(b + (0 - b) * weight))
    return out


class HurtFlashCheckTest(unittest.TestCase):
    def setUp(self):
        self.check = load_checker()
        self.base = base_picture()

    def verdict(self, result):
        return self.check.verdict(result, min_mask=600, noise_floor=100)

    def test_one_weight_on_the_flash_carrier_passes(self):
        on = flash(self.base, F_RECT, 0.3)
        result = self.check.analyse(self.base, on, self.base, region_g=G_REGION)
        self.assertGreaterEqual(result["mask"], 600)
        self.assertAlmostEqual(result["medianW"], 0.3, delta=0.03)
        self.assertEqual(result["maskInG"], 0)
        self.assertLessEqual(result["gMedianW"], 0.05)
        self.assertEqual(self.verdict(result), [])

    def test_two_weights_fail_the_single_weight_condition(self):
        on = flash(self.base, F_RECT, 0.45, y_split=70, w_top=0.15)
        result = self.check.analyse(self.base, on, self.base, region_g=G_REGION)
        self.assertGreater(result["p90W"] - result["p10W"], 0.15)
        problems = self.verdict(result)
        self.assertTrue(any("single weight" in problem for problem in problems), problems)

    def test_a_flashed_armour_carrier_fails_the_armour_control(self):
        on = flash(flash(self.base, F_RECT, 0.3), G_RECT, 0.3)
        result = self.check.analyse(self.base, on, self.base, region_g=G_REGION)
        self.assertGreater(result["gMedianW"], 0.05)
        problems = self.verdict(result)
        self.assertTrue(any("armour flashed" in problem for problem in problems), problems)

    def test_a_scene_that_moves_between_the_outer_frames_fails(self):
        moved = self.base.copy()
        pixels = moved.load()
        for x in range(F_RECT[0], F_RECT[2] + 1):
            for y in range(F_RECT[1], F_RECT[3] + 1):
                r, g, b = pixels[x, y]
                pixels[x, y] = (r + 40, g, b)
        on = flash(self.base, F_RECT, 0.3)
        result = self.check.analyse(self.base, on, moved, region_g=G_REGION)
        self.assertGreater(result["noise"], 100)
        problems = self.verdict(result)
        self.assertTrue(any("outer frames" in problem for problem in problems), problems)

    def test_a_flash_in_every_frame_is_not_a_flash(self):
        flashed = flash(self.base, F_RECT, 0.3)
        result = self.check.analyse(flashed, flashed, flashed, region_g=G_REGION)
        self.assertEqual(result["mask"], 0)
        problems = self.verdict(result)
        self.assertTrue(any("did not reach the picture" in problem for problem in problems), problems)

    def test_the_reference_pair_fits_the_same_weight(self):
        on = flash(self.base, F_RECT, 0.3)
        ref = self.check.analyse_pair(on, self.base)
        self.assertAlmostEqual(ref["medianW"], 0.3, delta=0.03)
        self.assertIsNotNone(ref["regionF"])
        self.assertLessEqual(ref["regionF"][0], F_RECT[0])
        self.assertGreaterEqual(ref["regionF"][2], F_RECT[2])


if __name__ == "__main__":
    unittest.main()
