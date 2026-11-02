"""The display-list scene's checker separates the call-time colour from the compile-time one.

`scripts/cpu-contract/display-list-legacy-check.py` reads the two frames `DisplayListLegacyScreen` asks for and decides
whether a compiled `glBegin` run drew (a) at all, (b) in the caller's colour when the list never set one, and (c) in the
list's own colour when it did. The leg that takes that picture needs a GPU and a five-minute game launch, so this test
pins the reading itself: two synthetic frames that hold the expected picture have to pass, and the same frames with the
compile-time green baked into *both* squares - the shape this round's fix removes - have to fail. A checker that cannot
fail would make the leg's PASSED meaningless, which is the trap this file exists to close.
"""
import importlib.util
import json
import pathlib
import tempfile
import unittest

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts" / "cpu-contract" / "display-list-legacy-check.py"

GUI_WIDTH = 427
GUI_HEIGHT = 240
PANEL = "0,0,427,240"
SLOT_A = 60
SLOT_B = 180
HALF = 30
CENTRE_X = 213
DIRECT_X = 85
TRIANGLE_X = 341
TRIANGLE_Y = 120
TRIANGLE_R = 24
DIRECT_HALF = 20
#: The layout arms, from DisplayListLegacyScreen: half extent 10, arm A at centre-90, arm B at centre+90, and the
#: direct textured control 15 GUI units above the bottom of the panel.
ARM_HALF = 10
ARM_A_X = CENTRE_X - 90
ARM_B_X = CENTRE_X + 90
ARM_Y = 120
TEX_CONTROL_Y = GUI_HEIGHT - 15
WHITE = (255, 255, 255)
MAGENTA = (255, 0, 255)


def layout(frames=42):
    """The layout `DisplayListLegacyScreen.describeLayout` prints, for a 427x240 GUI at scale 3."""
    return ("gui=%dx%d panel=%s square=%d slots=%d,%d triangle=%d,%d,%d direct=%d,%d,%d even=%d,%d odd=%d,%d "
            "armA=%d,%d,%d armB=%d,%d,%d texControl=%d,%d,%d "
            "callColour=255,0,0 insideColour=0,255,0 frames=%d compiles=1 dumps=2 stages=displaylist-f40-a "
            "displaylist-f41-b" % (GUI_WIDTH, GUI_HEIGHT, PANEL, HALF, SLOT_A, SLOT_B, TRIANGLE_X, TRIANGLE_Y,
                                   TRIANGLE_R, DIRECT_X, TRIANGLE_Y, DIRECT_HALF, SLOT_A, SLOT_B, SLOT_B, SLOT_A,
                                   ARM_A_X, ARM_Y, ARM_HALF, ARM_B_X, ARM_Y, ARM_HALF, CENTRE_X, TEX_CONTROL_Y,
                                   ARM_HALF, frames))


def load_check():
    spec = importlib.util.spec_from_file_location("display_list_legacy_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def frame(path, outer_y, inner_y, outer_colour, inner_colour, compile_layout=False):
    """Draws one frame of the scene at scale 3: backdrop, markers, the two squares, the loop and the controls.

    `compile_layout` paints the layout arms the way a renderer that keeps the compile-time vertex layout leaves them -
    white where the sampled texture should be and magenta where the stored colour should be - which is the picture the
    new arm check has to reject.
    """
    image = Image.new("RGB", (GUI_WIDTH * 3, GUI_HEIGHT * 3), (16, 16, 32))
    pixels = image.load()
    # A two-tone backdrop, so the frame is not a single colour plus ink (the leg's own picture gate wants variety).
    for y in range(image.height):
        for x in range(image.width):
            pixels[x, y] = (16 + (x * 20) // image.width, 16, 32 + (y * 40) // image.height)
    image.paste((0, 255, 0), (3, 3, 15, 15))
    image.paste((0, 255, 255), (image.width - 15, image.height - 15, image.width - 3, image.height - 3))

    def square(centre_y, colour):
        left = int((CENTRE_X - HALF) * 3)
        top = int((centre_y - HALF) * 3)
        image.paste(colour, (left, top, left + 2 * HALF * 3, top + 2 * HALF * 3))

    square(outer_y, outer_colour)
    square(inner_y, inner_colour)
    # The line loop: a square ring is enough ink at the layout coordinates the checker reads.
    left = int((TRIANGLE_X - TRIANGLE_R) * 3)
    top = int((TRIANGLE_Y - TRIANGLE_R) * 3)
    image.paste((0, 255, 255), (left, top, left + 2 * TRIANGLE_R * 3, top + 2 * 3))
    image.paste((0, 255, 255), (left, top, left + 2 * 3, top + 2 * TRIANGLE_R * 3))
    # The direct immediate control, yellow.
    left = int((DIRECT_X - DIRECT_HALF) * 3)
    top = int((TRIANGLE_Y - DIRECT_HALF) * 3)
    image.paste((255, 255, 0), (left, top, left + 2 * DIRECT_HALF * 3, top + 2 * DIRECT_HALF * 3))

    def arm(centre_x, centre_y, colour):
        left = int((centre_x - ARM_HALF) * 3)
        top = int((centre_y - ARM_HALF) * 3)
        image.paste(colour, (left, top, left + 2 * ARM_HALF * 3, top + 2 * ARM_HALF * 3))

    arm(ARM_A_X, ARM_Y, MAGENTA if compile_layout else WHITE)
    arm(ARM_B_X, ARM_Y, WHITE if compile_layout else MAGENTA)
    arm(CENTRE_X, TEX_CONTROL_Y, MAGENTA)
    image.save(path)


class DisplayListLegacyCheckTest(unittest.TestCase):
    def run_check(self, outer_colour, inner_colour, compile_time_green_in_both=False, compile_layout=False):
        """Builds the two frames and runs the checker; returns (exit code, output)."""
        check = load_check()
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            pictures = tmp / "screenshots"
            pictures.mkdir()
            if compile_time_green_in_both:
                # The failure shape: the colour set outside the compile was baked, so both squares are green.
                frame(pictures / "displaylist-f40-a.png", SLOT_A, SLOT_B, (0, 255, 0), (0, 255, 0),
                      compile_layout=compile_layout)
                frame(pictures / "displaylist-f41-b.png", SLOT_B, SLOT_A, (0, 255, 0), (0, 255, 0),
                      compile_layout=compile_layout)
            else:
                frame(pictures / "displaylist-f40-a.png", SLOT_A, SLOT_B, outer_colour, inner_colour,
                      compile_layout=compile_layout)
                frame(pictures / "displaylist-f41-b.png", SLOT_B, SLOT_A, outer_colour, inner_colour,
                      compile_layout=compile_layout)
            report = {
                "displayListLayout": layout(),
                "displayListCompiles": 1,
                "displayListDumpCount": 2,
                "displayListDump_displaylist-f40-a": "screenshots/displaylist-f40-a.png completed=true frame=40",
                "displayListDump_displaylist-f41-b": "screenshots/displaylist-f41-b.png completed=true frame=41",
            }
            path = tmp / "celtium-validation.json"
            path.write_text(json.dumps(report))
            import contextlib
            import io
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                code = check.main(["--report", str(path), "--pictures", str(pictures), "--leg", "synthetic"])
            return code, buffer.getvalue()

    def test_the_expected_picture_passes(self):
        code, output = self.run_check((255, 0, 0), (0, 255, 0))
        self.assertEqual(0, code, output)
        self.assertIn("PASSED", output)
        self.assertIn("the positive control reads as the baked-colour failure", output)
        self.assertIn("the compile-layout arms control is rejected", output)

    def test_the_compile_time_layout_fails(self):
        """The arms painted with the compile-time layout's answer - each in the other's colour - must not pass."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), compile_layout=True)
        self.assertEqual(1, code, "a renderer that keeps the compile-time layout must not pass: " + output)
        self.assertIn("the layout came from the compile, not the call", output)
        self.assertIn("FAILED", output)
        self.assertIn("armA_magenta=", output, "the failing arm's wrong colour is named, not just the expected one")

    def test_the_baked_colour_fails(self):
        code, output = self.run_check((255, 0, 0), (0, 255, 0), compile_time_green_in_both=True)
        self.assertEqual(1, code, "the picture this round removes must not pass: " + output)
        self.assertIn("baked into", output)
        self.assertIn("FAILED", output)

    def run_single_picture(self, outer_colour, inner_colour, compile_layout=False):
        """Judges one picture the way the reference (real GL) arm's game screenshot is judged."""
        check = load_check()
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            picture = tmp / "gl-screenshot.png"
            frame(picture, SLOT_A, SLOT_B, outer_colour, inner_colour, compile_layout=compile_layout)
            report = tmp / "celtium-validation.json"
            report.write_text(json.dumps({"displayListLayout": layout(), "displayListCompiles": 1}))
            import contextlib
            import io
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer):
                code = check.main(["--report", str(report), "--picture", str(picture), "--leg", "synthetic-ref"])
            return code, buffer.getvalue()

    def test_a_reference_picture_with_one_of_each_colour_passes(self):
        code, output = self.run_single_picture((255, 0, 0), (0, 255, 0))
        self.assertEqual(0, code, output)
        self.assertIn("the baked-colour positive control is rejected", output)

    def test_a_reference_picture_with_the_compile_time_layout_fails(self):
        code, output = self.run_single_picture((255, 0, 0), (0, 255, 0), compile_layout=True)
        self.assertEqual(1, code, "a real-GL picture that kept the compile layout must not pass: " + output)
        self.assertIn("the layout came from the compile, not the call", output)

    def test_a_reference_picture_with_the_baked_colour_fails(self):
        code, output = self.run_single_picture((0, 255, 0), (0, 255, 0))
        self.assertEqual(1, code, "a picture with green in both squares must not pass: " + output)
        self.assertIn("not one", output)

    def test_a_layout_without_the_slot_map_is_refused(self):
        check = load_check()
        with self.assertRaises(SystemExit):
            check.layout_of({"displayListLayout": "gui=427x240 panel=0,0,427,240 square=30 slots=60,180 "
                                                 "triangle=341,120,24 direct=85,120,20 colour=0,255,0"})

    def test_a_layout_before_the_arms_existed_is_refused(self):
        """A dump from the previous scene version has no arm rectangles, so its picture cannot be judged."""
        check = load_check()
        old = layout().replace("armA=%d,%d,%d armB=%d,%d,%d texControl=%d,%d,%d " % (
            ARM_A_X, ARM_Y, ARM_HALF, ARM_B_X, ARM_Y, ARM_HALF, CENTRE_X, TEX_CONTROL_Y, ARM_HALF), "")
        with self.assertRaises(SystemExit):
            check.layout_of({"displayListLayout": old})


if __name__ == "__main__":
    unittest.main()
