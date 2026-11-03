"""The flicker checker separates "two frames of a still scene agree" from "something changed between them".

`scripts/cpu-contract/flicker-frame-check.py` reads the two presented frames
`-Dceltium.validation.flickerArmTick=T` + `-Dceltium.debug.flickerSample` produce and asks whether they differ. The
leg that produces them needs a GPU and a four-minute game launch, so this test pins the reading itself: identical
frames have to pass, a difference above the floor has to fail, a difference below it has to pass with its number
reported, frames of different sizes have to be inconclusive rather than compared, and a run with no dumped frames has
to be inconclusive rather than read as "no flicker". A checker that cannot fail would make the leg's PASSED
meaningless.
"""
import importlib.util
import pathlib
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "flicker-frame-check.py"


def load_checker():
    spec = importlib.util.spec_from_file_location("flicker_frame_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def base_picture(width=160, height=120):
    """A still scene: sky over a blocky ground, so a changed patch is a place and not a uniform field."""
    image = Image.new("RGB", (width, height))
    pixels = image.load()
    for y in range(height):
        for x in range(width):
            if y < height // 3:
                pixels[x, y] = (120, 170, 230)
            else:
                pixels[x, y] = ((x * 5) % 90 + 40, (y * 7) % 80 + 60, (x * y) % 60 + 30)
    return image


def pixels_of(image):
    return image.convert("RGB").tobytes()


class FlickerFrameCheckTest(unittest.TestCase):
    def setUp(self):
        self.checker = load_checker()

    def test_identical_frames_pass_with_zero_changed(self):
        frame = pixels_of(base_picture())
        census = self.checker.compare(frame, frame)
        self.assertEqual("MEASURED", census["status"])
        self.assertEqual(0, census["changed"])
        self.assertEqual(0.0, census["fraction"])
        self.assertEqual("PASS", self.checker.verdict(census))

    def test_a_changed_patch_above_the_floor_fails(self):
        previous = base_picture()
        current = previous.copy()
        pixels = current.load()
        for y in range(40, 80):
            for x in range(50, 110):
                pixels[x, y] = (255, 0, 255)
        census = self.checker.compare(pixels_of(previous), pixels_of(current))
        self.assertEqual(60 * 40, census["changed"])
        self.assertEqual("FAIL", self.checker.verdict(census))
        # The box is the patch, in row-major pixel indices: the first changed pixel is its top-left corner.
        self.assertEqual(40 * 160 + 50, census["firstChangedIndex"])
        self.assertEqual(79 * 160 + 109, census["lastChangedIndex"])

    def test_a_change_below_the_floor_passes_and_reports_its_number(self):
        previous = base_picture()
        current = previous.copy()
        pixels = current.load()
        # One pixel of 19,200 is 0.005%, an order of magnitude under the 0.05% floor.
        pixels[10, 10] = (255, 0, 255)
        census = self.checker.compare(pixels_of(previous), pixels_of(current))
        self.assertEqual(1, census["changed"])
        self.assertEqual("PASS", self.checker.verdict(census))
        self.assertGreater(census["maxDelta"], 8)

    def test_a_difference_at_the_threshold_is_not_counted(self):
        previous = base_picture()
        current = previous.copy()
        pixels = current.load()
        red, green, blue = pixels[10, 10]
        # Exactly the threshold is "the same": the sampler counts a pixel only when it is further apart than this.
        pixels[10, 10] = (red + 8, green, blue)
        census = self.checker.compare(pixels_of(previous), pixels_of(current))
        self.assertEqual(0, census["changed"])

    def test_frames_of_different_sizes_are_inconclusive(self):
        census = self.checker.compare(pixels_of(base_picture()), pixels_of(base_picture(80, 60)))
        self.assertEqual("INCONCLUSIVE", census["status"])
        self.assertEqual("INCONCLUSIVE", self.checker.verdict(census))

    def test_a_missing_frame_is_inconclusive(self):
        census = self.checker.compare(None, pixels_of(base_picture()))
        self.assertEqual("INCONCLUSIVE", census["status"])
        self.assertEqual("INCONCLUSIVE", self.checker.verdict(census))

    def test_a_run_without_dumped_frames_is_inconclusive(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            self.assertEqual((None, None), self.checker.find_frames(directory))
            self.assertEqual(2, self.checker.main(["--run", directory]))

    def test_a_run_with_dumped_frames_is_read(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            base_picture().save(pathlib.Path(directory) / "celtium-flicker-prev.png")
            base_picture().save(pathlib.Path(directory) / "celtium-flicker-cur.png")
            previous, current = self.checker.find_frames(directory)
            self.assertIsNotNone(previous)
            self.assertIsNotNone(current)
            self.assertEqual(0, self.checker.main(["--run", directory]))


if __name__ == "__main__":
    unittest.main()
