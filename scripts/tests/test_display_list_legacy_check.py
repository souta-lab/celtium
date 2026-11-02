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
#: The compile-colour arm, from the screen: the direct control's column, on the bottom row with the texControl.
ARM_C_X = DIRECT_X
ARM_C_Y = TEX_CONTROL_Y
ARM_C_OUTSIDE = (0, 0, 255)
ARM_C_INSIDE = (255, 0, 0)
#: The attribute arms: arm D at a third of the width, arm E at four fifths, both on the bottom row (from the screen).
ARM_D_X = GUI_WIDTH // 3
ARM_E_X = 4 * GUI_WIDTH // 5
ATTRIB_COLOUR = (0, 255, 0)
#: The cull arms: three and seven tenths across, twenty GUI units above the bottom row (from the screen).
ARM_F_X = 3 * GUI_WIDTH // 10
ARM_G_X = 7 * GUI_WIDTH // 10
ARM_F_Y = TEX_CONTROL_Y - 24
#: GL_FRONT, the face the cull arms set and the readback has to answer.
GL_FRONT = 0x0404
#: The state strings the arms report: the pushed block changes every key, and the pop has to put them all back. `cmB`
#: is the boolean width of the same colour-write-mask state; GL answers it from the same state, so it must equal `cm`.
#: `lp`/`ld`/`la` are `GL_LIGHTING_BIT` - light 0's eye-space position and diffuse, and the light-model ambient - the
#: group the calibrated world scene measured 1038 pushes of with zero restores.
ATTRIB_LIGHT_BEFORE = "lp=0.20,1.00,-0.70,ld=0.60,0.60,0.60,la=0.40,0.40,0.40"
ATTRIB_LIGHT_INSIDE = "lp=0.37,0.11,-0.92,ld=0.25,0.50,0.75,la=0.33,0.22,0.11"
ATTRIB_BEFORE = ("cm=1111,cmB=1111,dm=1,bs=770,bd=771,dt=1,cf=1029,sb=0,0,1280,720,po=0.0,vw=0,0,1280,720,"
                 + ATTRIB_LIGHT_BEFORE)
ATTRIB_INSIDE = ("cm=0000,cmB=0000,dm=0,bs=1,bd=0,dt=0,cf=1028,sb=1,2,3,4,po=7.0,vw=11,13,17,19,"
                 + ATTRIB_LIGHT_INSIDE)
ATTRIB_AFTER = ATTRIB_BEFORE
#: The ENABLE_BIT-only arm: only the colour write mask, the viewport and the lighting are written inside it, so only
#: those may survive the pop - GL_ENABLE_BIT selects none of those groups.
ATTRIB_OUTSIDE = ("cm=0000,cmB=0000,dm=1,bs=770,bd=771,dt=1,cf=1029,sb=0,0,1280,720,po=0.0,vw=23,29,31,37,"
                  + ATTRIB_LIGHT_INSIDE)
#: The two rectangles the arms set: the masked push must be undone, the unmasked one must survive (from the screen).
ATTRIB_VIEWPORT_MASKED = "11,13,17,19"
ATTRIB_VIEWPORT_UNMASKED = "23,29,31,37"
#: GL_LINEAR, the filter the scene sets on its probe texture (not GL's initial value and not the zero-writer's 0).
TEXTURE_FILTER = 9729
WHITE = (255, 255, 255)
MAGENTA = (255, 0, 255)


def layout(frames=42, leaks=0, attrib_before=ATTRIB_BEFORE, attrib_inside=ATTRIB_INSIDE, attrib_after=ATTRIB_AFTER,
           attrib_outside=ATTRIB_OUTSIDE, attrib_frames=None, boolean_mismatches=0, texture_filter=TEXTURE_FILTER,
           filter_expected=TEXTURE_FILTER, cull_frames=None, cull_face=GL_FRONT, cull_mismatches=0,
           viewport_masked=ATTRIB_VIEWPORT_MASKED, viewport_unmasked=ATTRIB_VIEWPORT_UNMASKED):
    """The layout `DisplayListLegacyScreen.describeLayout` prints, for a 427x240 GUI at scale 3."""
    return ("gui=%dx%d panel=%s square=%d slots=%d,%d triangle=%d,%d,%d direct=%d,%d,%d even=%d,%d odd=%d,%d "
            "armA=%d,%d,%d armB=%d,%d,%d texControl=%d,%d,%d "
            "armC=%d,%d,%d armCOutside=0,0,255 armCInside=255,0,0 "
            "colourAfterCompile=0,0,255 compileColourLeaks=%d compileColourFrames=%d "
            "attribMask=0x4f20c armD=%d,%d,%d armE=%d,%d,%d attribColour=0,255,0 "
            "attribViewportCaller=0,0,1280,720 attribViewportMasked=%s attribViewportUnmasked=%s "
            "armF=%d,%d,%d armG=%d,%d,%d cullArms=%d cullArmFace=%d cullArmMismatches=%d "
            "attribBefore=%s attribInside=%s attribAfter=%s attribOutsideAfter=%s attribFrames=%d "
            "booleanQueryMismatches=%d textureFilter=%d textureFilterExpected=%d "
            "callColour=255,0,0 insideColour=0,255,0 frames=%d compiles=1 dumps=2 stages=displaylist-f40-a "
            "displaylist-f41-b" % (GUI_WIDTH, GUI_HEIGHT, PANEL, HALF, SLOT_A, SLOT_B, TRIANGLE_X, TRIANGLE_Y,
                                   TRIANGLE_R, DIRECT_X, TRIANGLE_Y, DIRECT_HALF, SLOT_A, SLOT_B, SLOT_B, SLOT_A,
                                   ARM_A_X, ARM_Y, ARM_HALF, ARM_B_X, ARM_Y, ARM_HALF, CENTRE_X, TEX_CONTROL_Y,
                                   ARM_HALF, ARM_C_X, ARM_C_Y, ARM_HALF, leaks, frames,
                                   ARM_D_X, ARM_C_Y, ARM_HALF, ARM_E_X, ARM_C_Y, ARM_HALF,
                                   viewport_masked, viewport_unmasked,
                                   ARM_F_X, ARM_F_Y, ARM_HALF, ARM_G_X, ARM_F_Y, ARM_HALF,
                                   frames if cull_frames is None else cull_frames, cull_face, cull_mismatches,
                                   attrib_before, attrib_inside, attrib_after, attrib_outside,
                                   frames if attrib_frames is None else attrib_frames,
                                   boolean_mismatches, texture_filter, filter_expected, frames))


def load_check():
    spec = importlib.util.spec_from_file_location("display_list_legacy_check", CHECK)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def frame(path, outer_y, inner_y, outer_colour, inner_colour, compile_layout=False, compile_colour_leak=False,
          attrib_restored=True, attrib_leaked=False, cull_back_drawn=True, cull_front_drawn=False):
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
    # Arm C: the caller's blue, or the colour the compile recorded - the failure shape.
    arm(ARM_C_X, ARM_C_Y, ARM_C_INSIDE if compile_colour_leak else ARM_C_OUTSIDE)
    # The attribute arms: arm D's quad appears because its push selected GL_COLOR_BUFFER_BIT, arm E's does not because
    # its push selected GL_ENABLE_BIT only. `attrib_leaked` paints arm E, which is the pop that ignores its mask.
    if attrib_restored:
        arm(ARM_D_X, ARM_C_Y, ATTRIB_COLOUR)
    if attrib_leaked:
        arm(ARM_E_X, ARM_C_Y, ATTRIB_COLOUR)
    # The cull arms: the back-facing quad survives GL_FRONT (arm F) and the front-facing one is culled (arm G).
    # `cull_front_drawn` paints arm G, which is the cull-back pipeline's answer; `cull_back_drawn=False` removes arm F.
    if cull_back_drawn:
        arm(ARM_F_X, ARM_F_Y, ATTRIB_COLOUR)
    if cull_front_drawn:
        arm(ARM_G_X, ARM_F_Y, ATTRIB_COLOUR)
    image.save(path)


class DisplayListLegacyCheckTest(unittest.TestCase):
    def run_check(self, outer_colour, inner_colour, compile_time_green_in_both=False, compile_layout=False,
                  compile_colour_leak=False, leaks=0, second_stage_kept_only=False, attrib_restored=True,
                  attrib_leaked=False, attrib_before=ATTRIB_BEFORE, attrib_inside=ATTRIB_INSIDE,
                  attrib_after=ATTRIB_AFTER, attrib_outside=ATTRIB_OUTSIDE, attrib_frames=None,
                  viewport_masked=ATTRIB_VIEWPORT_MASKED, viewport_unmasked=ATTRIB_VIEWPORT_UNMASKED,
                  boolean_mismatches=0, texture_filter=TEXTURE_FILTER, filter_expected=TEXTURE_FILTER,
                  cull_back_drawn=True, cull_front_drawn=False, cull_face=GL_FRONT, cull_frames=None,
                  cull_mismatches=0):
        """Builds the two frames and runs the checker; returns (exit code, output)."""
        check = load_check()
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            pictures = tmp / "screenshots"
            pictures.mkdir()
            if compile_time_green_in_both:
                # The failure shape: the colour set outside the compile was baked, so both squares are green.
                frame(pictures / "displaylist-f40-a.png", SLOT_A, SLOT_B, (0, 255, 0), (0, 255, 0),
                      compile_layout=compile_layout, compile_colour_leak=compile_colour_leak,
                      attrib_restored=attrib_restored, attrib_leaked=attrib_leaked,
                      cull_back_drawn=cull_back_drawn, cull_front_drawn=cull_front_drawn)
                frame(pictures / "displaylist-f41-b.png", SLOT_B, SLOT_A, (0, 255, 0), (0, 255, 0),
                      compile_layout=compile_layout, compile_colour_leak=compile_colour_leak,
                      attrib_restored=attrib_restored, attrib_leaked=attrib_leaked,
                      cull_back_drawn=cull_back_drawn, cull_front_drawn=cull_front_drawn)
            else:
                frame(pictures / "displaylist-f40-a.png", SLOT_A, SLOT_B, outer_colour, inner_colour,
                      compile_layout=compile_layout, compile_colour_leak=compile_colour_leak,
                      attrib_restored=attrib_restored, attrib_leaked=attrib_leaked,
                      cull_back_drawn=cull_back_drawn, cull_front_drawn=cull_front_drawn)
                frame(pictures / "displaylist-f41-b.png", SLOT_B, SLOT_A, outer_colour, inner_colour,
                      compile_layout=compile_layout, compile_colour_leak=compile_colour_leak,
                      attrib_restored=attrib_restored, attrib_leaked=attrib_leaked,
                      cull_back_drawn=cull_back_drawn, cull_front_drawn=cull_front_drawn)
            if second_stage_kept_only:
                # A report line serialized just before the completion was recorded names the plain file; the harness
                # only kept the completed copy. The check has to read it rather than call the frame missing.
                plain = pictures / "displaylist-f41-b.png"
                plain.rename(pictures / "displaylist-f41-b-kept.png")
            report = {
                "displayListLayout": layout(leaks=leaks, attrib_before=attrib_before, attrib_inside=attrib_inside,
                                            attrib_after=attrib_after, attrib_outside=attrib_outside,
                                            attrib_frames=attrib_frames, boolean_mismatches=boolean_mismatches,
                                            texture_filter=texture_filter, filter_expected=filter_expected,
                                            cull_frames=cull_frames, cull_face=cull_face,
                                            cull_mismatches=cull_mismatches,
                                            viewport_masked=viewport_masked, viewport_unmasked=viewport_unmasked),
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

    def run_single_picture(self, outer_colour, inner_colour, compile_layout=False, compile_colour_leak=False,
                           leaks=0, attrib_restored=True, attrib_leaked=False):
        """Judges one picture the way the reference (real GL) arm's game screenshot is judged."""
        check = load_check()
        with tempfile.TemporaryDirectory() as tmp:
            tmp = pathlib.Path(tmp)
            picture = tmp / "gl-screenshot.png"
            frame(picture, SLOT_A, SLOT_B, outer_colour, inner_colour, compile_layout=compile_layout,
                  compile_colour_leak=compile_colour_leak, attrib_restored=attrib_restored,
                  attrib_leaked=attrib_leaked)
            report = tmp / "celtium-validation.json"
            report.write_text(json.dumps({"displayListLayout": layout(leaks=leaks), "displayListCompiles": 1}))
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

    def test_a_stage_whose_report_line_was_written_before_completion_is_read_from_the_kept_copy(self):
        code, output = self.run_check((255, 0, 0), (0, 255, 0), second_stage_kept_only=True)
        self.assertEqual(0, code, "the kept copy of a completed dump is the picture: " + output)
        self.assertIn("displaylist-f41-b", output)

    def test_the_compile_colour_leak_fails(self):
        """Arm C drawn in the colour the list recorded is the picture a mirror that follows the command leaves."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), compile_colour_leak=True)
        self.assertEqual(1, code, "the compile-time colour surviving glEndList must not pass: " + output)
        self.assertIn("recorded inside GL_COMPILE", output)
        self.assertIn("armC_inside=", output)

    def test_a_reference_picture_with_the_compile_colour_leak_fails(self):
        code, output = self.run_single_picture((255, 0, 0), (0, 255, 0), compile_colour_leak=True)
        self.assertEqual(1, code, "a real-GL picture with the compile colour escaping must not pass: " + output)
        self.assertIn("recorded inside GL_COMPILE", output)

    def test_a_state_query_that_saw_the_leak_fails(self):
        """The state answer stands on its own: a clean picture cannot excuse a run that read the list's colour back."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), leaks=1)
        self.assertEqual(1, code, "the run's own colourAfterCompile must be judged: " + output)
        self.assertIn("colourAfterCompile", output)

    def test_the_cull_arms_are_judged_and_their_control_is_rejected(self):
        """The clean picture draws the back-facing quad and leaves the front-facing one empty, and the control is refused."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0))
        self.assertEqual(0, code, output)
        self.assertIn("the cull-face control is rejected", output)
        self.assertIn("front-culled quad pasted into arm G", output)

    def test_a_culled_back_facing_quad_fails(self):
        """The shape a pipeline that bakes cull-back leaves: the back-facing quad under GL_FRONT is gone."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), cull_back_drawn=False)
        self.assertEqual(1, code, "GL_FRONT culls the other face, so the back-facing quad must stay: " + output)
        self.assertIn("armF_green=", output)
        self.assertIn("GL_FRONT culls the other face", output)

    def test_a_drawn_front_facing_quad_fails(self):
        """The same defect from the other side: cull-back draws the quad GL_FRONT has to cull."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), cull_front_drawn=True)
        self.assertEqual(1, code, "GL_FRONT culls the front-facing quad, so ink there is the wrong pipeline: " + output)
        self.assertIn("armG_green=", output)

    def test_a_cull_readback_that_did_not_answer_gl_front_fails(self):
        """The state half stands alone: a picture cannot excuse a run whose own query never saw the face it set."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), cull_face=0x0405)
        self.assertEqual(1, code, output)
        self.assertIn("GL_CULL_FACE_MODE", output)
        code, output = self.run_check((255, 0, 0), (0, 255, 0), cull_mismatches=2)
        self.assertEqual(1, code, output)
        self.assertIn("frame(s) answered a cull mode other", output)

    def test_a_scene_without_the_cull_arms_is_refused(self):
        """A run from before the arms existed must not read as "the face is fine"."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), cull_frames=0)
        self.assertEqual(1, code, output)
        self.assertIn("cull arms never ran", output)

    def test_the_compile_colour_control_is_rejected(self):
        code, output = self.run_check((255, 0, 0), (0, 255, 0))
        self.assertEqual(0, code, output)
        self.assertIn("the compile-colour arm control is rejected", output)
        self.assertIn("'s list colour painted over arm C)", output)

    def test_the_attribute_arms_are_judged_and_their_control_is_rejected(self):
        """A clean run names the attribute arms; the control proves the arm-E rule can fail."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0))
        self.assertEqual(0, code, output)
        self.assertIn("the attribute-mask control is rejected", output)
        self.assertIn("'s restored quad pasted into arm E)", output)
        self.assertIn("armD_green=", output)
        self.assertIn("armE_green=", output)

    def test_a_missing_restored_quad_fails(self):
        """The pop put the colour write mask back but the picture does not show the quad: the pipeline disagrees."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), attrib_restored=False)
        self.assertEqual(1, code, "a restored push whose quad is absent must not pass: " + output)
        self.assertIn("armD_green=0", output)
        self.assertIn("the pop did not put the colour write mask back", output)

    def test_a_quad_from_an_enable_only_push_fails(self):
        """GL_ENABLE_BIT does not save the colour write mask, so arm E's quad must not be in the picture."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), attrib_leaked=True)
        self.assertEqual(1, code, "a pop that restored a group its mask did not select must not pass: " + output)
        self.assertIn("the pop restored a group the mask did not select", output)

    def test_a_pop_that_did_not_restore_the_selected_groups_fails(self):
        """The state readback is its own instrument: a restore that misses a group is named even if the picture looks
        right, and the group is named by key."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_after="cm=0000,cmB=0000,dm=1,bs=770,bd=771,dt=1,cf=1029,"
                                                   "sb=0,0,1280,720,po=0.0,vw=0,0,1280,720,"
                                                   + ATTRIB_LIGHT_BEFORE)
        self.assertEqual(1, code, "a pop that left the colour write mask off must not pass: " + output)
        self.assertIn("glPopAttrib did not put back cm", output)

    def test_a_pop_that_did_not_restore_the_lighting_fails_by_key(self):
        """GL_LIGHTING_BIT is the group the calibrated world scene pushes 1038 times, so its key is claimed too.

        The values are the ones the masked arm sets: a pop that put every other group back and left the light at the
        block's direction is named by key rather than passing on the picture, which a light change does not move in
        this scene (nothing here is lit).
        """
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_after=ATTRIB_BEFORE.replace(ATTRIB_LIGHT_BEFORE, ATTRIB_LIGHT_INSIDE))
        self.assertEqual(1, code, "a pop that left the light at the block's value must not pass: " + output)
        self.assertIn("glPopAttrib did not put back lp,ld,la", output)

    def test_an_enable_only_push_that_restored_the_lighting_fails(self):
        """The mask rule for the same group: GL_ENABLE_BIT saves capabilities, not GL_LIGHTING_BIT."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_outside=ATTRIB_OUTSIDE.replace(ATTRIB_LIGHT_INSIDE, ATTRIB_LIGHT_BEFORE))
        self.assertEqual(1, code, "a GL_ENABLE_BIT push that restored the lighting must not pass: " + output)
        self.assertIn("GL_LIGHTING_BIT is not saved by GL_ENABLE_BIT", output)

    def test_a_pop_that_did_not_restore_the_viewport_fails_by_key(self):
        """GL_VIEWPORT_BIT is the group this pack's mods actually select and change, so the key is claimed too.

        The rectangle is the one the screen's masked arm sets: a pop that put every other group back and left this one
        is named by key rather than passing on the picture, which a 17x19 viewport does not change in this scene.
        """
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_after=ATTRIB_BEFORE.replace("vw=0,0,1280,720", "vw=11,13,17,19"))
        self.assertEqual(1, code, "a pop that left the viewport at the block's rectangle must not pass: " + output)
        self.assertIn("glPopAttrib did not put back vw", output)

    def test_an_enable_only_push_that_restored_the_viewport_fails(self):
        """The mask rule for the same group: GL_ENABLE_BIT saves capabilities, not GL_VIEWPORT."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_outside=ATTRIB_OUTSIDE.replace("vw=23,29,31,37", "vw=0,0,1280,720"))
        self.assertEqual(1, code, "a GL_ENABLE_BIT push that restored the viewport must not pass: " + output)
        self.assertIn("GL_VIEWPORT is GL_VIEWPORT_BIT state", output)

    def test_a_scene_without_the_viewport_rectangles_is_refused(self):
        """A dump whose layout does not name the rectangles it set cannot be judged, and the missing field is named."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), viewport_masked="", viewport_unmasked="")
        self.assertEqual(1, code, "a dump without the forced viewport rectangles must not pass: " + output)
        self.assertIn("attribViewportMasked/attribViewportUnmasked missing", output)

    def test_a_boolean_query_that_answers_another_state_fails(self):
        """GL answers the colour write mask through every width. `cmB=0000` with `cm=1111` is the shape leg r30b
        measured: the native boolean query never reached the stub and the zero-writer answered one byte."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_before="cm=1111,cmB=0000,dm=1,bs=770,bd=771,dt=1,cf=1029,"
                                                    "sb=0,0,1280,720,po=0.0," + ATTRIB_LIGHT_BEFORE)
        self.assertEqual(1, code, "a boolean query that answered another state must not pass: " + output)
        self.assertIn("cm=1111 through glGetFloatv and cmB=0000 through glGetBooleanv", output)

    def test_a_missing_boolean_readback_is_named_not_read_as_agreement(self):
        """A scene that does not report the boolean width cannot prove the query works, so the key is named."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0),
                                      attrib_before=ATTRIB_BEFORE.replace("cmB=1111,", ""),
                                      attrib_inside=ATTRIB_INSIDE.replace("cmB=0000,", ""),
                                      attrib_after=ATTRIB_AFTER.replace("cmB=1111,", ""),
                                      attrib_outside=ATTRIB_OUTSIDE.replace("cmB=0000,", ""))
        self.assertEqual(1, code, "a dump without the boolean readback must not pass: " + output)
        self.assertIn("has no cmB= readback", output)

    def test_a_tex_parameter_query_that_answered_zero_fails(self):
        """The same dead-route shape: the query wrote one zero int instead of the filter the scene set."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), texture_filter=0)
        self.assertEqual(1, code, "a tex-parameter query that answered 0 must not pass: " + output)
        self.assertIn("answered 0, not the " + str(TEXTURE_FILTER), output)

    def test_a_scene_without_the_filter_probe_is_refused(self):
        code, output = self.run_check((255, 0, 0), (0, 255, 0), filter_expected=-1)
        self.assertEqual(1, code, "a report without the filter probe must not pass: " + output)
        self.assertIn("did not report the filter it set", output)

    def test_an_enable_only_push_that_restored_the_colour_mask_fails(self):
        """The old behaviour - restore everything regardless of the mask - is exactly this readback."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), attrib_outside=ATTRIB_BEFORE)
        self.assertEqual(1, code, "restoring a group outside the pushed mask must not pass: " + output)
        self.assertIn("a glPushAttrib(GL_ENABLE_BIT) restored the colour write mask", output)

    def test_a_probe_that_changed_nothing_fails(self):
        """A readback whose inside state equals its before state measured nothing, and must not be read as a restore."""
        code, output = self.run_check((255, 0, 0), (0, 255, 0), attrib_inside=ATTRIB_BEFORE)
        self.assertEqual(1, code, "a vacuous attribute probe must not pass: " + output)
        self.assertIn("the pushed block did not change", output)

    def test_a_layout_without_the_attribute_arms_is_refused(self):
        """A dump from before arms D and E existed cannot answer the mask rule, so it is refused rather than guessed."""
        check = load_check()
        old = layout().replace("attribMask=0x4f20c armD=%d,%d,%d armE=%d,%d,%d attribColour=0,255,0 " % (
            ARM_D_X, ARM_C_Y, ARM_HALF, ARM_E_X, ARM_C_Y, ARM_HALF), "")
        with self.assertRaises(SystemExit):
            check.layout_of({"displayListLayout": old})

    def test_a_layout_without_the_compile_colour_arm_is_refused(self):
        """A dump from before arm C existed has no rectangle for it, so the compile-colour rule cannot be judged."""
        check = load_check()
        old = layout().replace("armC=%d,%d,%d armCOutside=0,0,255 armCInside=255,0,0 " % (
            ARM_C_X, ARM_C_Y, ARM_HALF), "")
        with self.assertRaises(SystemExit):
            check.layout_of({"displayListLayout": old})

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
