"""The glint checker separates "the glint brightened the armour, additively" from everything else.

`scripts/cpu-contract/enchant-glint-check.py` reads the three presented dumps the fixture's
`entityGlintAbTick` produces (the chestplate's ench tag off / on / off; the armour itself is in every
frame) and asks whether the middle frame's brightening is the glint's two additive passes. The leg
needs a GPU and a game launch, so this test pins the reading itself: an additive brightening of the
armour has to pass; a replacement brightening, an armour whose own pixels moved, and a brightening
present in every frame all have to fail, and the instrument conditions have to bite on a fabricated
console log. A checker that cannot fail would make the leg's PASSED meaningless.
"""
import importlib.util
import pathlib
import tempfile
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "enchant-glint-check.py"

ARMOR_RECT = (107, 55, 132, 80)
G_RECT = (105, 45, 134, 94)


def load_checker():
    spec = importlib.util.spec_from_file_location("enchant_glint_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=160, height=120):
    """A sky band, a textured ground, and the glint carrier with a diamond-coloured chestplate."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 3:
                pixels[x, y] = (120, 170, 230)
            else:
                pixels[x, y] = ((x * 5) % 90 + 40, (y * 7) % 80 + 60, (x * y) % 60 + 30)
    for x in range(G_RECT[0], G_RECT[2] + 1):
        for y in range(G_RECT[1], G_RECT[3] + 1):
            pixels[x, y] = (105, 105, 95)
    for x in range(ARMOR_RECT[0], ARMOR_RECT[2] + 1):
        for y in range(ARMOR_RECT[1], ARMOR_RECT[3] + 1):
            pixels[x, y] = (75, 140, 165)
    return image


def brighten_additive(image, rect, fraction=0.5):
    """The glint's shape: every channel rises, in proportion to what is already there."""
    out = image.copy()
    pixels = out.load()
    for x in range(rect[0], rect[2] + 1):
        for y in range(rect[1], rect[3] + 1):
            r, g, b = pixels[x, y]
            pixels[x, y] = (min(255, round(r + r * fraction)), min(255, round(g + g * fraction)),
                            min(255, round(b + b * fraction)))
    return out


def brighten_replacement(image, rect, colour=(200, 120, 200)):
    """A substituted colour: brighter overall, but one channel goes down."""
    out = image.copy()
    pixels = out.load()
    for x in range(rect[0], rect[2] + 1):
        for y in range(rect[1], rect[3] + 1):
            pixels[x, y] = colour
    return out


class EnchantGlintCheckTest(unittest.TestCase):
    def setUp(self):
        self.check = load_checker()
        self.base = base_picture()

    def verdict(self, result, instruments=None):
        return self.check.verdict(result, instruments=instruments, min_brightened=300)

    def test_an_additive_brightening_of_the_armour_passes(self):
        on = brighten_additive(self.base, ARMOR_RECT)
        result = self.check.analyse(self.base, on, self.base)
        self.assertGreaterEqual(result["brightened"], 300)
        self.assertEqual(result["additiveFraction"], 1.0)
        self.assertEqual(result["skinViolations"], 0)
        self.assertEqual(self.verdict(result), [])

    def test_a_replacement_brightening_fails_additivity(self):
        on = brighten_replacement(self.base, ARMOR_RECT)
        result = self.check.analyse(self.base, on, self.base)
        self.assertGreaterEqual(result["brightened"], 300)
        self.assertLess(result["additiveFraction"], 0.9)
        problems = self.verdict(result)
        self.assertTrue(any("all three channels" in problem for problem in problems), problems)

    def test_armour_pixels_that_move_fail_the_skin_invariance(self):
        on = brighten_additive(self.base, ARMOR_RECT)
        pixels = on.load()
        # The carrier's skin just above the chestplate, inside the mask's dilated region. The shift
        # keeps the luminance (so the pixels do not join the brightened mask themselves) while moving
        # every pixel well past the skin tolerance: what changed is the armour's own colour.
        for x in range(ARMOR_RECT[0], ARMOR_RECT[2] + 1):
            for y in range(ARMOR_RECT[1] - 4, ARMOR_RECT[1]):
                r, g, b = pixels[x, y]
                pixels[x, y] = (r + 15, g - 15, b)
        result = self.check.analyse(self.base, on, self.base)
        self.assertGreater(result["skinViolations"], 0)
        problems = self.verdict(result)
        self.assertTrue(any("own pixels changed" in problem for problem in problems), problems)

    def test_a_brightening_in_every_frame_is_not_a_glint(self):
        bright = brighten_additive(self.base, ARMOR_RECT)
        result = self.check.analyse(bright, bright, bright)
        self.assertEqual(result["brightened"], 0)
        problems = self.verdict(result)
        self.assertTrue(any("did not reach the picture" in problem for problem in problems), problems)

    def test_the_instrument_conditions_bite(self):
        with tempfile.TemporaryDirectory() as tmp:
            log = pathlib.Path(tmp) / "validation-console.log"
            log.write_text("[Celtium] CELTIUM_VERIFY texMatrixGroups=2 modelDrawRefusals=0\n",
                           encoding="utf-8")
            instruments = self.check.read_instruments(tmp)
            self.assertEqual(instruments["texMatrixGroups"], 2)
            self.assertEqual(instruments["modelDrawRefusals"], 0)
            self.assertEqual(instruments["modelMissingLines"], 0)
            on = brighten_additive(self.base, ARMOR_RECT)
            result = self.check.analyse(self.base, on, self.base)
            self.assertEqual(self.verdict(result, instruments=instruments), [])
            dead = dict(instruments, texMatrixGroups=0)
            problems = self.verdict(result, instruments=dead)
            self.assertTrue(any("texMatrixGroups" in problem for problem in problems), problems)
        with tempfile.TemporaryDirectory() as tmp:
            self.assertIsNone(self.check.read_instruments(tmp))


if __name__ == "__main__":
    unittest.main()
