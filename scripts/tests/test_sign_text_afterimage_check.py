"""The sign-text checker separates "the text was removed and stayed removed" from "the text is still there".

`scripts/cpu-contract/sign-text-afterimage-check.py` reads the three presented dumps one tick apart that
`-Dceltium.validation.signTextAbTick=T` produces (on / off / off) and asks whether the first off frame still carries
the text's pixels. The leg that produces the triple needs a GPU and a two-minute game launch, so this test pins the
reading itself: a text present only in the on frame has to pass, a text still present in the first off frame has to
fail as the afterimage, a scene with no text at all has to fail as "nothing was measured", a scene that moves between
the two off frames has to fail on its noise floor, and a report that never wrote the text has to fail before its
pictures are read. A checker that cannot fail would make the leg's PASSED meaningless.
"""
import importlib.util
import pathlib
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "sign-text-afterimage-check.py"


def load_checker():
    spec = importlib.util.spec_from_file_location("sign_text_afterimage_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=160, height=120):
    """A sign board: a wood-coloured rectangle on a sky and ground, so black glyphs are distinguishable."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 3:
                pixels[x, y] = (120, 170, 230)
            else:
                pixels[x, y] = ((x * 5) % 90 + 40, (y * 7) % 80 + 60, (x * y) % 60 + 30)
    for y in range(40, 80):
        for x in range(50, 110):
            pixels[x, y] = (150, 110, 60)
    return image


def text(image, colour=(0, 0, 0)):
    """Four lines of blocky glyphs on the board: the shape the sign renderer draws."""
    out = image.copy()
    pixels = out.load()
    for line in range(4):
        top = 46 + line * 8
        for x in range(56, 104, 4):
            for y in range(top, top + 5):
                pixels[x, y] = colour
                pixels[x + 1, y] = colour
    return out


class SignTextAfterimageCheckTest(unittest.TestCase):
    def setUp(self):
        self.check = load_checker()
        self.base = base_picture()
        self.with_text = text(self.base)

    def test_a_text_only_the_on_frame_has_passes(self):
        result = self.check.analyse(self.with_text, self.base, self.base)
        self.assertGreaterEqual(result["ink"], 200)
        self.assertEqual(result["ghost"], 0)
        self.assertEqual(result["noise"], 0)
        self.assertEqual(self.check.verdict(result), [])

    def test_a_text_still_in_the_first_off_frame_is_the_afterimage(self):
        result = self.check.analyse(self.with_text, self.with_text, self.base)
        self.assertGreaterEqual(result["ink"], 200)
        self.assertEqual(result["ghost"], result["ink"])
        problems = self.check.verdict(result)
        self.assertTrue(any("afterimage" in problem for problem in problems), problems)

    def test_a_scene_with_no_text_fails_as_nothing_measured(self):
        result = self.check.analyse(self.base, self.base, self.base)
        self.assertEqual(result["ink"], 0)
        problems = self.check.verdict(result)
        self.assertTrue(any("nothing was measured" in problem for problem in problems), problems)

    def test_a_scene_that_moves_between_the_off_frames_fails_on_noise(self):
        moved = base_picture()
        pixels = moved.load()
        for y in range(0, 120, 2):
            for x in range(0, 160):
                pixels[x, y] = (10, 10, 10)
        # The mask comes from the on frame against the *second* off frame, so the moving scene is the first off
        # frame: the text is still the only difference between the on frame and the last one, and everything else
        # that moved is the noise floor.
        result = self.check.analyse(self.with_text, moved, self.base)
        self.assertGreater(result["noiseFraction"], 0.25)
        problems = self.check.verdict(result)
        self.assertTrue(any("not static enough" in problem for problem in problems), problems)

    def test_a_report_that_never_wrote_the_text_fails_before_its_pictures(self):
        problems = self.check.report_problems({})
        self.assertTrue(any("signTextAbb" in problem for problem in problems), problems)
        self.assertTrue(any("signTextScene" in problem for problem in problems), problems)

    def test_a_report_that_says_the_text_was_never_placed_fails(self):
        report = {
            "signTextAba": "160 on=true lines=4 block=minecraft:standing_sign",
            "signTextAbb": "161 on=false lines=4 block=minecraft:standing_sign",
            "signTextAbc": "162 on=false lines=4 block=minecraft:standing_sign",
            "signTextScene": "pos=BlockPos{x=1, y=2, z=3} placed=false lines=4",
        }
        problems = self.check.report_problems(report)
        self.assertTrue(any("not placed" in problem for problem in problems), problems)

    def test_a_report_that_says_the_text_was_never_cleared_fails(self):
        report = {
            "signTextAba": "160 on=true lines=4 block=minecraft:standing_sign",
            "signTextAbb": "161 on=true lines=4 block=minecraft:standing_sign",
            "signTextAbc": "162 on=false lines=4 block=minecraft:standing_sign",
            "signTextScene": "pos=BlockPos{x=1, y=2, z=3} placed=true lines=4",
        }
        problems = self.check.report_problems(report)
        self.assertTrue(any("signTextAbb" in problem for problem in problems), problems)


if __name__ == "__main__":
    unittest.main()
