"""The glow checker separates "the glowing tag put an outline in the picture" from "this scene has white pixels".

`scripts/cpu-contract/glow-outline-check.py` reads the three presented dumps one tick apart that
`-Dceltium.validation.glowAbTick=T` produces (off / on / off) and asks whether the middle frame has bright neutral
pixels the outer two do not, in a shape an outline can have. The leg that produces the triple needs a GPU and a
two-minute game launch, so this test pins the reading itself: a synthetic ring has to pass, a ring present in every
frame has to fail, a filled white box has to fail on its fill ratio, a scene that moves between the outer frames has to
fail on its noise floor, and a white ring that only the *outer* frame has must fail the control. A checker that cannot
fail would make the leg's PASSED meaningless.
"""
import importlib.util
import pathlib
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "glow-outline-check.py"


def load_checker():
    spec = importlib.util.spec_from_file_location("glow_outline_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=120, height=90):
    """A scene with a sky band, a textured ground and two mob-coloured blobs, so white is distinguishable."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 3:
                pixels[x, y] = (120, 170, 230)                       # sky
            else:
                pixels[x, y] = ((x * 5) % 90 + 40, (y * 7) % 80 + 60, (x * y) % 60 + 30)
    for cx, cy in ((40, 55), (80, 60)):
        for y in range(cy - 8, cy + 8):
            for x in range(cx - 5, cx + 5):
                pixels[x, y] = (30, 120, 40)                         # a mob body
    return image


def ring(image, cx, cy, half_w, half_h, colour=(255, 255, 255)):
    """Draws a one-pixel rectangle outline: the shape an entity outline has."""
    out = image.copy()
    pixels = out.load()
    for x in range(cx - half_w, cx + half_w + 1):
        pixels[x, cy - half_h] = colour
        pixels[x, cy + half_h] = colour
    for y in range(cy - half_h, cy + half_h + 1):
        pixels[cx - half_w, y] = colour
        pixels[cx + half_w, y] = colour
    return out


def box(image, left, top, right, bottom, colour=(255, 255, 255)):
    out = image.copy()
    pixels = out.load()
    for y in range(top, bottom):
        for x in range(left, right):
            pixels[x, y] = colour
    return out


class GlowOutlineCheckTest(unittest.TestCase):
    def setUp(self):
        self.check = load_checker()
        self.base = base_picture()

    def test_a_ring_only_the_glowing_frame_has_passes(self):
        off = self.base
        on = ring(ring(self.base, 40, 45, 7, 15), 80, 70, 7, 15)
        result = self.check.analyse(off, on, off)
        self.assertEqual(result["outlineInA"], 0)
        self.assertEqual(result["outlineInC"], 0)
        self.assertEqual(result["noise"], 0)
        self.assertGreaterEqual(result["outline"], 60)
        self.assertEqual(self.check.verdict(result, min_outline=60), [])

    def test_a_ring_in_every_frame_is_not_an_outline(self):
        ringed = ring(self.base, 40, 55, 7, 15)
        result = self.check.analyse(ringed, ringed, ringed)
        self.assertEqual(result["outline"], 0)
        problems = self.check.verdict(result, min_outline=60)
        self.assertTrue(problems)
        self.assertIn("did not put an outline", problems[0])

    def test_a_filled_white_box_fails_on_its_fill_ratio(self):
        on = box(self.base, 30, 40, 60, 70)
        result = self.check.analyse(self.base, on, self.base)
        self.assertGreater(result["outline"], 60)
        self.assertGreater(result["fillRatio"], 0.5)
        problems = self.check.verdict(result, min_outline=60)
        self.assertTrue(any("filled" in problem for problem in problems), problems)

    def test_a_scene_that_moves_between_the_outer_frames_fails_on_noise(self):
        moved = base_picture()
        pixels = moved.load()
        for y in range(0, 90, 2):
            for x in range(0, 120):
                pixels[x, y] = (10, 10, 10)
        on = ring(self.base, 40, 55, 7, 15)
        result = self.check.analyse(self.base, on, moved)
        self.assertGreater(result["noiseFraction"], 0.25)
        problems = self.check.verdict(result, min_outline=60)
        self.assertTrue(any("static enough" in problem for problem in problems), problems)

    def test_a_ring_only_the_outer_frame_has_fails_the_control(self):
        ringed = ring(self.base, 40, 55, 7, 15)
        result = self.check.analyse(ringed, self.base, self.base)
        self.assertEqual(result["outline"], 0)
        self.assertGreater(result["outlineInA"], 0)
        problems = self.check.verdict(result, min_outline=1)
        self.assertTrue(problems)

    def test_a_small_patch_fails_the_bounding_box(self):
        on = ring(self.base, 40, 55, 3, 3)
        result = self.check.analyse(self.base, on, self.base)
        problems = self.check.verdict(result, min_outline=10)
        self.assertTrue(any("bounding box" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
