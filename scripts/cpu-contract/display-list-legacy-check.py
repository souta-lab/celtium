#!/usr/bin/env python3
"""A display list compiled out of glBegin/glEnd geometry: does glCallList draw it, and with whose colour?

Owner: **custom rendering fidelity**. Unit: "the invisible-custom-rendering report is measured on the code path a mod
in this pack actually uses".

## What this checks

`-Dceltium.validation.screen=displaylist` opens `DisplayListLegacyScreen`, which compiles three `GL_COMPILE` display
lists out of immediate-mode geometry and draws them with `glCallList` under a `glPushMatrix`/`glTranslatef`:

* **the outer list** - a filled `GL_QUADS` square compiled after green was set *outside* it, so the list carries no
  colour command of its own;
* **the inner list** - the same square, but green is set *inside* the compile;
* **the line loop** - the primitive GregTech's tool overlays use, whose cyan is set inside its own list.

Both squares are called under `glColor4f(red)` in opposite slots of the same frame, and the pair swaps slots on the
next frame. Read from the class file, `gregtech/common/ToolEventHandlers.drawRotationMarker` does exactly the
line-loop shape: `glNewList(4864=GL_COMPILE)`, `glBegin(GL_LINE_LOOP)`, 121 `glVertex3d`, `glEnd`, `glEndList`, and
`glCallList` per frame. The renderer used to refuse the run because a list was being compiled, so the list was stored
empty and every replay drew nothing - the reported "some custom rendering is invisible".

The picture answers five questions, and each is a way the replay can be wrong:

1. **The geometry is there at all.** Nothing in either slot means the list replay still draws nothing.
2. **It is at the call transform, not the compile one.** All lists are compiled at the origin; a renderer that drew a
   run at compile time would paint the square at the screen's top-left corner, where no slot is.
3. **It moves with the frame.** The two squares swap slots between the two dumped frames, so a picture cannot hold a
   retained copy of the previous frame and pass.
4. **An attribute the list never set comes from the caller.** GL compiles the commands, not the values, so the outer
   square must be **red** (the colour current at `glCallList`). A renderer that baked the compile-time colour puts
   green there instead; that is what this project shipped until now, and the previous version of this scene asserted
   the baked answer as if it were GL's.
5. **An attribute the list did set is the list's.** The inner square must stay **green** under the same red call, which
   is what separates "the replay applies the caller's state" from "the replay loses the list's own commands".

The line loop is counted separately because the renderer's line pipeline is a separate path: a square alone would not
answer the GregTech primitive. A fourth quad - yellow, drawn directly with glBegin/glEnd in the same frame, under the
same matrix and the same untextured state - is the control that separates "the list replay draws nothing" from "the
immediate path draws nothing": if the yellow quad is missing, the list's absence is not attributable.

The scene draws its backdrop as a dark gradient and disables texture around its own geometry, both for measured
reasons rather than looks. The gradient is what lets the probe's own frame clear the leg picture gate's smoke floor
("presentedDistinctColours=3 is not above the smoke floor of 16", the first run of this scene), and texture is off
because the first run compiled the runs as POSITION_TEX_COLOR with no glTexCoord of their own - the GUI leaves
GL_TEXTURE_2D on - so the vertices sampled whatever coordinate was current and a transparent texel left no ink.

## The layout arms

GL chooses the vertex layout from the *enabled* state when the vertex executes, so a list compiled with texturing on
and called with it off draws untextured. Two small lists measure both directions in every frame, each called with a
magenta texture bound so that the compile-time layout's answer is the other arm's colour:

* **arm A - white** - compiled with `GL_TEXTURE_2D` on and a recorded coordinate, called with it off: GL draws the
  colour the list carries (white). A renderer that kept the layout samples the bound texture instead (magenta).
* **arm B - magenta** - compiled with `GL_TEXTURE_2D` off, called with it on: GL samples the caller's texture at the
  caller's coordinate (magenta). A renderer that kept the layout draws it untextured (white).
* **texControl - magenta** - a directly-drawn textured quad in the same state, so "arm B has no magenta" can be told
  from "the texture never reached the sampler at all".

## The compile-colour arm

The other half of GL's display-list rule is that a recorded colour is *stored*, not executed: a `glColor` between
`glNewList` and `glEndList` has no effect on the caller's current colour. "Arm C" sets blue outside a `GL_COMPILE`
compile, records red inside it, and then draws a quad with **no colour call of its own** - so the quad has to be blue,
and a renderer whose colour mirror follows the recorded command draws it red. The same frame also reports
`colourAfterCompile`, the answer `glGetFloatv(GL_CURRENT_COLOR)` gives right after the `glEndList`, so the state and the
picture are two instruments on one rule. Its list is recompiled every frame because the state it must leave behind is
only observable immediately after its own close; the rest of the scene sets colours of its own before the dump.

## The cull arms

The state model carried the cull *enable* while every pipeline baked GL's default pair - cull back with a
counter-clockwise front face. A mod that asks for `GL_FRONT` (the face an inside-out or mirrored model is drawn with)
was therefore culled exactly the wrong way round, and every state query agreed with it: the mirror was faithful and
nothing read the face. Two arms measure both directions in one frame, with one green 20x20 quad each:

* **arm F** - the winding the rest of this scene does not use (the back-facing one), drawn under `glCullFace(GL_FRONT)`:
  GL culls the other face, so this quad **must stay**;
* **arm G** - the winding every other quad here uses (front-facing), under the same `GL_FRONT`: it **must leave no
  ink**.

A renderer that bakes cull-back draws exactly the opposite pair, and one that ignores the enable draws both. The scene
also reports `cullArmFace`, the answer `glGetIntegerv(GL_CULL_FACE_MODE)` gave inside the arm, so "the face is captured
but not read" is separable from "the face never reached the mirror". The positive control pastes arm F's ink into arm G,
which is the cull-back picture, and requires that reading to be rejected.

## The reference arm

The same fixture runs under Celeritas (real OpenGL) through `CELTIUM_LEG_REFERENCE_RENDERER`, which is what settles the
rule without an argument about the specification: that arm's picture comes from the game's own screenshot
(`-Dceltium.validation.screenshotAtTick=120`), because a GL arm has no presented-image dump. `--picture` reads such a
single picture and asks the parity-free form of the same question: one band is the call-time colour and the other is
the list's own colour. Which band is which is not asserted there - the two squares swap slots every frame, and a still
screenshot carries whichever parity the tick landed on.

## The positive control

A check that cannot fail proves nothing. The script pastes one frame's **inner** (green) square into the other frame's
**outer** slot - the exact shape a renderer that baked the compile-time colour would leave, green in both slots - and
requires the same analysis to report green in both. The control is built from the run's own pixels, not painted by
hand, and it is the failure this round's fix removes. `--picture` runs the same control by pasting the green square
over the red one and requiring the single-picture check to reject the result. The layout arms have their own control,
for the same reason: exchanging the white and magenta squares is exactly the compile-time layout's answer, and the
check has to reject that picture in both modes. The compile-colour arm has one too: painting the outer square's red -
the list's colour after the call - over arm C is the picture the leak leaves, and it has to be rejected.

Runs without a GPU:

    python3 scripts/cpu-contract/display-list-legacy-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/display-list-legacy-check.py \
        --report <run>/logs/celtium-validation.json \
        --pictures <run>/screenshots --leg r27a-displaylist
"""
import argparse
import json
import pathlib
import re
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel is "the inner list's green" when green dominates and red/blue are low, and "the outer list's red" when red
#: dominates and green/blue are low. The two colours are the two possible answers for the same square (green set before
#: the compile, red current at glCallList), so the two counts are each other's failure mode.
BRIGHT = 160
DIM = 90
#: The square is 2*SQUARE_HALF GUI units on a side; a quarter of its own band is a safe interior sample.
SLOT_INK = 40
#: The line loop is thin - a 1-pixel outline - so it needs its own, lower floor, and its colour is cyan.
CYAN_INK = 40
#: How far outside the nominal rect a slot's ink is still attributed to it, in GUI units.
MARGIN = 2
#: The layout arms are 2*ARM_HALF GUI units on a side, 20 at scale 3, so about 3600 pixels; a ninth is a safe floor.
#: It is lower than SLOT_INK because the arms are deliberately small: they share the middle band with the four elements
#: the earlier scene already placed there.
ARM_INK = 400
#: The attribute arms draw one green quad each, the same 20x20 GUI units as the other arms, so the layout arms' floor is
#: theirs too. The second arm's quad must leave *no* ink, so its counter is judged against a floor instead of a count.
ATTRIB_EMPTY_INK = 40


def parse_args(argv):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--run", type=pathlib.Path, default=None, help="the leg's run directory")
    parser.add_argument("--report", type=pathlib.Path, default=None, help="celtium-validation.json")
    parser.add_argument("--pictures", type=pathlib.Path, default=None, help="the run's screenshots directory")
    parser.add_argument("--picture", type=pathlib.Path, default=None,
                        help="one picture to judge on its own (a reference GL arm's game screenshot, parity-free)")
    parser.add_argument("--leg", default="", help="the leg name, for the output")
    parser.add_argument("--json", type=pathlib.Path, default=None, help="write the measurement as JSON here")
    args = parser.parse_args(argv)
    if args.run is not None:
        args.report = args.report or (args.run / "logs" / "celtium-validation.json")
        args.pictures = args.pictures or (args.run / "screenshots")
    if args.picture is not None:
        if args.report is None and args.run is not None:
            args.report = args.run / "logs" / "celtium-validation.json"
        if args.report is None:
            parser.error("--picture needs --report (the layout is the scene's, not the picture's)")
        return args
    if not args.report or not args.pictures:
        parser.error("give --run, or both --report and --pictures")
    return args


def numbers(fields, key):
    """{@return} a comma-separated integer field as a list, or [] when the scene did not print it."""
    value = fields.get(key, "")
    return [int(v) for v in value.split(",")] if value else []


#: The state keys the attribute arms read around their push and pop. They are the groups the two arms differ in:
#: colour write mask, depth write mask, blend source/destination, depth-test enable, cull mode, scissor box and polygon
#: offset factor. Each arm writes all of them inside its push, so "the probe changed nothing" is a failure too.
ATTRIB_STATE_KEYS = ("cm", "dm", "bs", "bd", "dt", "cf", "sb", "po")


def parse_attrib_state(text):
    """{@return} one arm's readback as {key: value}, or {} when the scene did not report it.

    The values are the GL queries' answers joined with commas inside a single report token, so the split is on the
    comma that starts a new key - `sb=0,0,1280,720` is one value, and a plain `text.split(",")` would cut it in four.
    The key pattern accepts capitals: `cmB` (the boolean width of the colour write mask) is camel-cased, and a
    lowercase-only pattern silently swallowed it into the `cm` value - the key read as absent while the string showed it.
    """
    if not text or text == "none":
        return {}
    state = {}
    for token in re.split(r",(?=[A-Za-z][A-Za-z0-9]*=)", text):
        if "=" in token:
            key, value = token.split("=", 1)
            state[key] = value
    return state


def layout_of(report):
    """{@return} the scene's resolved layout: GUI size, panel rect, slot centres, square half, triangle, both slots."""
    text = report.get("displayListLayout")
    if not text:
        raise SystemExit("the report has no displayListLayout: the scene never opened (check displayListDrawnFrames)")
    # The layout is a flat list of key=value pairs with no spaces inside a value.
    fields = {}
    for token in text.split():
        if "=" in token:
            key, value = token.split("=", 1)
            fields[key] = value
    gui = fields.get("gui", "")
    if "x" not in gui:
        raise SystemExit("displayListLayout has no gui=WxH: " + text)
    gui_width, gui_height = (int(v) for v in gui.split("x"))
    panel = numbers(fields, "panel")
    slots = numbers(fields, "slots")
    triangle = numbers(fields, "triangle")
    direct = numbers(fields, "direct")
    even = numbers(fields, "even")
    odd = numbers(fields, "odd")
    call_colour = numbers(fields, "callColour")
    inside_colour = numbers(fields, "insideColour")
    arm_a = numbers(fields, "armA")
    arm_b = numbers(fields, "armB")
    tex_control = numbers(fields, "texControl")
    arm_c = numbers(fields, "armC")
    arm_c_outside = numbers(fields, "armCOutside")
    arm_c_inside = numbers(fields, "armCInside")
    arm_d = numbers(fields, "armD")
    arm_e = numbers(fields, "armE")
    arm_f = numbers(fields, "armF")
    arm_g = numbers(fields, "armG")
    attrib_colour = numbers(fields, "attribColour")
    attrib_before = parse_attrib_state(fields.get("attribBefore", "none"))
    attrib_inside = parse_attrib_state(fields.get("attribInside", "none"))
    attrib_after = parse_attrib_state(fields.get("attribAfter", "none"))
    attrib_outside_after = parse_attrib_state(fields.get("attribOutsideAfter", "none"))
    if (len(panel) != 4 or len(slots) != 2 or len(triangle) != 3 or len(direct) != 3
            or len(even) != 2 or len(odd) != 2 or len(call_colour) != 3 or len(inside_colour) != 3
            or len(arm_a) != 3 or len(arm_b) != 3 or len(tex_control) != 3
            or len(arm_c) != 3 or len(arm_c_outside) != 3 or len(arm_c_inside) != 3
            or len(arm_d) != 3 or len(arm_e) != 3 or len(arm_f) != 3 or len(arm_g) != 3
            or len(attrib_colour) != 3):
        raise SystemExit("displayListLayout is not the expected shape (the layout arms armA=/armB=/texControl=, the"
                         " compile-colour arm armC=/armCOutside=/armCInside=, the attribute arms"
                         " armD=/armE=/attribColour= and the cull arms armF=/armG=/cullArms= included; a dump from"
                         " before they existed cannot be judged): " + text)
    return {
        "gui": (gui_width, gui_height),
        "panel": panel,
        "half": int(fields.get("square", "30")),
        "slots": slots,
        "even": even,
        "odd": odd,
        "triangle": triangle,
        "direct": direct,
        "armA": arm_a,
        "armB": arm_b,
        "texControl": tex_control,
        "armC": arm_c,
        "arm_c_outside": arm_c_outside,
        "arm_c_inside": arm_c_inside,
        "armD": arm_d,
        "armE": arm_e,
        "armF": arm_f,
        "armG": arm_g,
        "cull_arms": int(fields.get("cullArms", "0")),
        "cull_arm_face": int(fields.get("cullArmFace", "-1")),
        "cull_arm_mismatches": int(fields.get("cullArmMismatches", "0")),
        "attrib_colour": attrib_colour,
        "attrib_mask": fields.get("attribMask", ""),
        "attrib_before": attrib_before,
        "attrib_inside": attrib_inside,
        "attrib_after": attrib_after,
        "attrib_outside_after": attrib_outside_after,
        "attrib_frames": int(fields.get("attribFrames", "0")),
        "boolean_query_mismatches": int(fields.get("booleanQueryMismatches", "0")),
        "attrib_frames_reported": "attribFrames" in fields,
        "texture_filter": int(fields.get("textureFilter", "-1")),
        "texture_filter_expected": int(fields.get("textureFilterExpected", "-1")),
        "call_colour": call_colour,
        "inside_colour": inside_colour,
        "frames": int(fields.get("frames", "0")),
        "compiles": int(fields.get("compiles", "0")),
        "compile_colour_leaks": int(fields.get("compileColourLeaks", "0")),
        "text": text,
    }


def parse_png(path):
    return Image.open(path).convert("RGB")


def count_colour(image, rect, wanted):
    """{@return} how many pixels in the rect are the wanted (bright, saturated) colour."""
    left, top, right, bottom = [int(round(v)) for v in rect]
    left = max(0, left)
    top = max(0, top)
    right = min(image.width, right)
    bottom = min(image.height, bottom)
    if right <= left or bottom <= top:
        return 0
    pixels = image.load()
    found = 0
    for y in range(top, bottom):
        for x in range(left, right):
            red, green, blue = pixels[x, y]
            hit = True
            for channel, (value, high) in enumerate(zip((red, green, blue), wanted)):
                if high and value < BRIGHT:
                    hit = False
                    break
                if not high and value > DIM:
                    hit = False
                    break
            if hit:
                found += 1
    return found


#: The two colours the outer square can carry: the call-time red (correct) and the compile-time green (baked).
RED = (True, False, False)
GREEN = (False, True, False)
CYAN = (False, True, True)
YELLOW = (True, True, False)
#: The two colours the layout arms can carry. Arm A was compiled textured and is called untextured, so GL draws the
#: white colour it stored; arm B was compiled untextured and is called textured, so GL samples the magenta texture the
#: call binds. Each is the other's failure mode: a renderer that keeps the compile-time layout paints them swapped.
WHITE = (True, True, True)
MAGENTA = (True, False, True)


def count_green(image, rect):
    return count_colour(image, rect, GREEN)


def count_red(image, rect):
    return count_colour(image, rect, RED)


def count_yellow(image, rect):
    return count_colour(image, rect, YELLOW)


def count_cyan(image, rect):
    return count_colour(image, rect, CYAN)


def count_white(image, rect):
    return count_colour(image, rect, WHITE)


def count_magenta(image, rect):
    return count_colour(image, rect, MAGENTA)


def pixel(image, x, y):
    if x < 0 or y < 0 or x >= image.width or y >= image.height:
        return (0, 0, 0)
    return image.load()[int(x), int(y)]


def marker_is(image, x, y, wanted):
    """{@return} whether the pixel at (x, y) is close enough to the marker colour to be that marker."""
    red, green, blue = pixel(image, x, y)
    return abs(red - wanted[0]) <= 40 and abs(green - wanted[1]) <= 40 and abs(blue - wanted[2]) <= 40


def rect_for(scale, cx, cy, half):
    return ((cx - half) * scale[0], (cy - half) * scale[1], (cx + half) * scale[0], (cy + half) * scale[1])


def copy_square(target, source, scale, from_gui, to_gui, half):
    """Copies the square-sized region around `from_gui` from source and pastes it at `to_gui` in target.

    The positive controls are built with this: moving one list's square onto the other's position is exactly the
    picture the defect leaves, so the analysis has to report it.
    """
    source_rect = [int(round(v)) for v in rect_for(scale, from_gui[0], from_gui[1], half)]
    left = max(0, source_rect[0])
    top = max(0, source_rect[1])
    right = min(min(target.width, source.width), source_rect[2])
    bottom = min(min(target.height, source.height), source_rect[3])
    if right <= left or bottom <= top:
        return False
    dest = (int(round((to_gui[0] - half) * scale[0])), int(round((to_gui[1] - half) * scale[1])))
    target.paste(source.crop((left, top, right, bottom)), dest)
    return True


def analyse(image, layout, scale, stage):
    """{@return} the frame's reading: each slot's green and red ink, the direct control and the line loop.

    The slots are named by layout position (`band0` = the first entry of `slots`, `band1` = the second), not by which
    list is in them - that depends on the frame's parity and is decided in the check, never assumed here.
    """
    half = layout["half"]
    centres = layout["slots"]
    gui_centre_x = (layout["panel"][0] + layout["panel"][2]) / 2
    reading = {"stage": stage, "band0_green": 0, "band0_red": 0, "band1_green": 0, "band1_red": 0,
               "triangle_cyan": 0, "direct_yellow": 0}
    for index, centre in enumerate(centres):
        rect = rect_for(scale, gui_centre_x, centre, half + MARGIN)
        reading["band%d_green" % index] = count_green(image, rect)
        reading["band%d_red" % index] = count_red(image, rect)
    triangle_x, triangle_y, triangle_r = layout["triangle"]
    reading["triangle_cyan"] = count_cyan(image, rect_for(scale, triangle_x, triangle_y, triangle_r + MARGIN + 2))
    direct_x, direct_y, direct_half = layout["direct"]
    reading["direct_yellow"] = count_yellow(image, rect_for(scale, direct_x, direct_y, direct_half + MARGIN))
    reading.update(arm_reading(image, layout, scale))
    return reading


def arm_rect_for(scale, centre, half):
    """{@return} the pixel rect of one layout arm, with the margin a mispositioned quad is still counted in."""
    return rect_for(scale, centre[0], centre[1], half + MARGIN)


def arm_reading(image, layout, scale):
    """{@return} the white and magenta ink in each layout arm, the direct textured control, and arm C's two colours."""
    reading = {}
    for name, key in (("armA", "armA"), ("armB", "armB"), ("texControl", "texControl")):
        x, y, half = layout[key]
        rect = arm_rect_for(scale, (x, y), half)
        reading[name + "_white"] = count_white(image, rect)
        reading[name + "_magenta"] = count_magenta(image, rect)
    # Arm C's colours come from the scene's own layout, not from constants here: the quad has to be drawn in the colour
    # the scene set *outside* the compile and must not be drawn in the one it recorded *inside* it, and a checker that
    # hard-coded those could drift away from the fixture without anything failing.
    x, y, half = layout["armC"]
    rect = arm_rect_for(scale, (x, y), half)
    reading["armC_outside"] = count_colour(image, rect, colour_mask(layout["arm_c_outside"]))
    reading["armC_inside"] = count_colour(image, rect, colour_mask(layout["arm_c_inside"]))
    # The attribute arms are one colour, so the count is the whole question: arm D's quad appears because its push
    # selected the colour group, arm E's does not because its push did not.
    for name, key in (("armD", "armD"), ("armE", "armE"), ("armF", "armF"), ("armG", "armG")):
        ax, ay, ahalf = layout[key]
        reading[name + "_green"] = count_colour(image, arm_rect_for(scale, (ax, ay), ahalf),
                                                colour_mask(layout["attrib_colour"]))
    return reading


def colour_mask(rgb):
    """{@return} the (high, high, high) tuple `count_colour` wants, for an RGB triple the scene reported."""
    return tuple(value >= BRIGHT for value in rgb)


def compile_colour_failures(layout, label):
    """{@return} the named failure for a run whose own state query saw the compile-time colour, or [] when clean.

    Separate from the picture on purpose: `GL_CURRENT_COLOR` right after the `glEndList` is the state the next
    `glBegin` will use, so a non-zero count is the same defect the arm's pixels show - two instruments on one rule,
    and the state answer does not depend on the frame reaching a dump.
    """
    if layout.get("compile_colour_leaks", 0) <= 0:
        return []
    return [label + ": " + str(layout["compile_colour_leaks"]) + " frame(s) reported the colour recorded inside a "
            "GL_COMPILE list as current right after its glEndList (colourAfterCompile); a recorded glColor is stored, "
            "not executed, until glCallList"]


def arm_c_failures(reading, label):
    """{@return} the arm C failures: the compile recorded a colour and the caller's has to survive it."""
    failures = []
    if reading["armC_outside"] < ARM_INK:
        failures.append(label + ": the quad drawn after a GL_COMPILE list recorded a colour is not the caller's colour "
                        "(armC_outside=" + str(reading["armC_outside"]) + " px); the recorded glColor became current")
    if reading["armC_inside"] >= ARM_INK:
        failures.append(label + ": the colour recorded inside GL_COMPILE is the current colour after glEndList "
                        "(armC_inside=" + str(reading["armC_inside"]) + " px); GL runs that command at glCallList, not "
                        "at the compile")
    return failures


#: GL_FRONT, the face the cull arms ask for; the readback has to answer it or the picture is not measuring the arm.
GL_FRONT = 0x0404
#: The cull arms' empty quad may still leave a few pixels of edge, so its counter is judged against a floor.
CULL_EMPTY_INK = 40


def cull_arm_failures(reading, layout, label):
    """{@return} the named failures of the cull arms, or [] when the face reached the rasteriser.

    The state and the picture are two instruments on one rule. `cullArmFace` says the scene's own
    `glGetIntegerv(GL_CULL_FACE_MODE)` answered GL_FRONT inside the arm - a renderer whose mirror ignores the call fails
    here before the picture is read - and the two counts say whether the pipeline that drew the two quads culled the
    face GL names: the back-facing quad has to survive a front-face cull and the front-facing one has to leave nothing.
    Both quads are drawn in the same frame, in the same colour and state, so a renderer that ignores the *enable* draws
    both and one that bakes cull-back draws exactly the opposite pair.
    """
    failures = []
    if layout["cull_arms"] <= 0:
        return [label + ": the cull arms never ran (cullArms=0), so the face the pipelines cull was not measured"]
    if layout["cull_arm_face"] != GL_FRONT:
        failures.append(label + ": glGetIntegerv(GL_CULL_FACE_MODE) answered " + str(layout["cull_arm_face"])
                        + " inside the cull arms, not GL_FRONT (" + str(GL_FRONT) + "); the arm did not set the face")
    if layout["cull_arm_mismatches"] > 0:
        failures.append(label + ": " + str(layout["cull_arm_mismatches"]) + " frame(s) answered a cull mode other"
                        + " than GL_FRONT after the arm set it")
    if reading.get("armF_green", 0) < ARM_INK:
        failures.append(label + ": the back-facing quad drawn under glCullFace(GL_FRONT) left no ink (armF_green="
                        + str(reading.get("armF_green", 0)) + " px); GL_FRONT culls the other face, so this quad has"
                        + " to stay - a renderer that bakes cull-back loses exactly this one")
    if reading.get("armG_green", 0) >= CULL_EMPTY_INK:
        failures.append(label + ": the front-facing quad drawn under glCullFace(GL_FRONT) left ink (armG_green="
                        + str(reading.get("armG_green", 0)) + " px); GL_FRONT culls it, so the pipeline that drew it"
                        + " is not culling the face the mirror reports")
    return failures


def attrib_state_failures(layout, label):
    """{@return} the named failures of the attribute arms' state readback, or [] when the mask rule held.

    Three claims, each a way the stack can be wrong:

    * the probe changed the state it reports (a renderer that ignored the writes would otherwise "restore" trivially);
    * the pop put every group its mask selected back (`attribAfter` == `attribBefore`);
    * the push that selected only `GL_ENABLE_BIT` did *not* put the colour write mask back - GL's rule, and the exact
      superset the old "restore everything" stack got wrong.
    """
    label = label + ": "
    if layout["attrib_frames"] <= 0:
        return [label + "the attribute arms never ran (attribFrames=0), so the mask rule was not measured"]
    before = layout["attrib_before"]
    inside = layout["attrib_inside"]
    after = layout["attrib_after"]
    outside = layout["attrib_outside_after"]
    if not before or not inside or not after or not outside:
        return [label + "the attribute arms reported no state readback (attribBefore/attribInside/attribAfter/"
                        "attribOutsideAfter missing)"]
    missing = [key for key in ATTRIB_STATE_KEYS if key not in before or key not in inside
               or key not in after or key not in outside]
    if missing:
        return [label + "the attribute arms' readback is missing " + ",".join(missing)]
    failures = []
    unchanged = [key for key in ATTRIB_STATE_KEYS if inside.get(key) == before.get(key)]
    if unchanged:
        failures.append(label + "the pushed block did not change " + ",".join(unchanged)
                        + " (before=" + str(before) + " inside=" + str(inside) + "); the probe measured nothing")
    stale = [key for key in ATTRIB_STATE_KEYS if after.get(key) != before.get(key)]
    if stale:
        failures.append(label + "glPopAttrib did not put back " + ",".join(stale) + " (before=" + str(before)
                        + " after=" + str(after) + "); the mask selected those groups")
    if outside.get("cm") != inside.get("cm"):
        failures.append(label + "a glPushAttrib(GL_ENABLE_BIT) restored the colour write mask (inside="
                        + str(inside.get("cm")) + " after=" + str(outside.get("cm")) + "); GL_ENABLE_BIT saves "
                        "capabilities only, and GL_COLOR_WRITEMASK is GL_COLOR_BUFFER_BIT state")
    return failures


def query_width_failures(layout, label):
    """{@return} the failures of the two state queries that must agree with their sibling width, or [].

    GL answers one boolean-state parameter through every `glGet*` width, so these are not extra features:

    * `glGetBooleanv(GL_COLOR_WRITEMASK)` must give the same four channels as `glGetFloatv` (the scene reports the
      float form as `cm` and the boolean one as `cmB`). Measured on leg r30b: `cm=1111` with `cmB=0000` in one frame,
      because the native boolean query never reached the stub and the zero-writer answered one byte. A renderer that
      answers the float form and not the boolean one corrupts every mod that saves the mask in a byte array.
    * `glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER)` must answer the filter the scene set. It read 0 for
      the same reason, and a save/restore around it put back a filter GL never had.

    Both claims need the scene's own report: a dump from before these fields existed cannot be judged, and the missing
    key is named rather than treated as agreement.
    """
    label = label + ": "
    failures = []
    for name, state in (("attribBefore", layout["attrib_before"]), ("attribInside", layout["attrib_inside"]),
                        ("attribAfter", layout["attrib_after"]),
                        ("attribOutsideAfter", layout["attrib_outside_after"])):
        if not state:
            continue
        if "cmB" not in state:
            failures.append(label + name + " has no cmB= readback; the boolean width of the colour write mask query "
                            "was not measured")
        elif state.get("cmB") != state.get("cm"):
            failures.append(label + name + " answers the colour write mask as cm=" + str(state.get("cm"))
                            + " through glGetFloatv and cmB=" + str(state.get("cmB")) + " through glGetBooleanv; GL "
                            "answers both widths from one state, so the boolean query is not reading it")
    if layout.get("boolean_query_mismatches", 0) > 0:
        failures.append(label + "the scene counted " + str(layout["boolean_query_mismatches"])
                        + " frame(s) in which the two query widths disagreed")
    expected_filter = layout.get("texture_filter_expected", -1)
    if expected_filter <= 0:
        failures.append(label + "the scene did not report the filter it set (textureFilterExpected="
                        + str(expected_filter) + ")")
    elif layout.get("texture_filter", -1) != expected_filter:
        failures.append(label + "glGetTexParameteriv(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER) answered "
                        + str(layout.get("texture_filter")) + ", not the " + str(expected_filter)
                        + " (GL_LINEAR) the scene set; the tex-parameter query is not reading the recorded state")
    return failures


def attrib_picture_failures(reading, label):
    """{@return} the attribute arms' picture failures: the restored push draws, the unrestricted push does not."""
    failures = []
    drawn = reading.get("armD_green", 0)
    suppressed = reading.get("armE_green", 0)
    if drawn < ARM_INK:
        failures.append(label + ": the quad drawn after a push that selected GL_COLOR_BUFFER_BIT left no ink "
                        "(armD_green=" + str(drawn) + " px); the pop did not put the colour write mask back")
    if suppressed >= ATTRIB_EMPTY_INK:
        failures.append(label + ": the quad drawn after a push that selected only GL_ENABLE_BIT is in the picture "
                        "(armE_green=" + str(suppressed) + " px); the pop restored a group the mask did not select, "
                        "which GL does not do")
    return failures


def arm_failures(reading, label):
    """{@return} the layout-arm failures in one reading, named.

    The two arms answer the two directions of the same rule - the layout follows the texturing state at `glCallList` -
    and each is the other's defect colour, so a swapping renderer fails both. The direct textured control is the frame's
    proof that magenta can appear at all; without it a white arm A would also read as "the texture never arrived".
    """
    failures = []
    if reading["armA_white"] < ARM_INK:
        failures.append(label + ": the textured-compiled list drawn untextured is not white (armA_white="
                        + str(reading["armA_white"]) + " px); the replay kept the compile-time layout")
    if reading["armA_magenta"] >= ARM_INK:
        failures.append(label + ": the textured-compiled list drawn untextured sampled the bound texture (armA_magenta="
                        + str(reading["armA_magenta"]) + " px); the layout came from the compile, not the call")
    if reading["armB_magenta"] < ARM_INK:
        failures.append(label + ": the untextured-compiled list drawn textured did not sample the caller's texture "
                        "(armB_magenta=" + str(reading["armB_magenta"]) + " px); the replay kept the untextured layout")
    if reading["armB_white"] >= ARM_INK:
        failures.append(label + ": the untextured-compiled list drawn textured drew untextured (armB_white="
                        + str(reading["armB_white"]) + " px); the layout came from the compile, not the call")
    if reading["texControl_magenta"] < ARM_INK:
        failures.append(label + ": the direct textured control left no magenta ink (texControl_magenta="
                        + str(reading["texControl_magenta"]) + " px); the texture or the sampler failed in this frame, "
                        "so the arms' colours are not attributable")
    failures.extend(arm_c_failures(reading, label))
    return failures


def arm_c_contaminated(image, layout, scale, outer_band_y):
    """{@return} a copy of the picture with the list's own colour painted over arm C.

    That is the picture a renderer whose colour mirror follows the recorded command leaves, so the arm-C check has to
    reject it. The paint is the run's own pixels - the outer square, which is the list's colour after the call - rather
    than a colour chosen here.
    """
    painted = image.copy()
    centre_x = (layout["panel"][0] + layout["panel"][2]) / 2
    if not copy_square(painted, image, scale, (centre_x, outer_band_y),
                       (layout["armC"][0], layout["armC"][1]), layout["armC"][2]):
        return None
    return painted


def swap_arms(image, layout, scale):
    """{@return} a copy of the picture with the two layout arms' squares exchanged.

    That is the picture a renderer that keeps the compile-time layout leaves - white where the sampled texture should be
    and magenta where the stored colour should be - so the arm check has to reject it.
    """
    swapped = image.copy()
    a = [int(round(v)) for v in arm_rect_for(scale, (layout["armA"][0], layout["armA"][1]), layout["armA"][2])]
    b = [int(round(v)) for v in arm_rect_for(scale, (layout["armB"][0], layout["armB"][1]), layout["armB"][2])]
    if a[2] <= a[0] or a[3] <= a[1] or b[2] <= b[0] or b[3] <= b[1]:
        return None
    piece_a = image.crop((a[0], a[1], a[2], a[3]))
    piece_b = image.crop((b[0], b[1], b[2], b[3]))
    swapped.paste(piece_b, (a[0], a[1]))
    swapped.paste(piece_a, (b[0], b[1]))
    return swapped


def even_frame(stage):
    """{@return} whether the stage's frame has even parity, from the suffix the screen appends ('-a' / '-b')."""
    return stage.endswith("-a")


def single_picture_report(image, layout, scale, label):
    """{@return} (passed, failures, lines, reading) for one picture, without assuming a frame parity.

    The scene swaps the two squares between slots every frame, so a single picture holds one square in each colour but
    not necessarily the outer one in the first band. What is asserted is the property that does not depend on that:
    one band carries the call-time colour with no green in it, the other carries the list's own green with no red, and
    the line loop and the direct control are both in the picture. A renderer that bakes the compile-time colour puts
    green in both bands, which this rejects.
    """
    half = layout["half"]
    gui_centre_x = (layout["panel"][0] + layout["panel"][2]) / 2
    bands = []
    for centre in layout["slots"]:
        rect = rect_for(scale, gui_centre_x, centre, half + MARGIN)
        bands.append((count_red(image, rect), count_green(image, rect)))
    lines = [label + " band0_red=%d band0_green=%d band1_red=%d band1_green=%d" % (
        bands[0][0], bands[0][1], bands[1][0], bands[1][1])]
    failures = []
    red_bands = [i for i, (red, green) in enumerate(bands) if red >= SLOT_INK and green < SLOT_INK]
    green_bands = [i for i, (red, green) in enumerate(bands) if green >= SLOT_INK and red < SLOT_INK]
    if len(red_bands) != 1:
        failures.append(label + ": the call-time colour is in " + str(len(red_bands)) + " band(s), not one (red="
                        + str([b[0] for b in bands]) + ")")
    if len(green_bands) != 1:
        failures.append(label + ": the list's own colour is in " + str(len(green_bands)) + " band(s), not one (green="
                        + str([b[1] for b in bands]) + "); a compile-time colour baked into both squares reads here")
    triangle_x, triangle_y, triangle_r = layout["triangle"]
    cyan = count_cyan(image, rect_for(scale, triangle_x, triangle_y, triangle_r + MARGIN + 2))
    direct_x, direct_y, direct_half = layout["direct"]
    yellow = count_yellow(image, rect_for(scale, direct_x, direct_y, direct_half + MARGIN))
    lines.append(label + " triangle_cyan=%d direct_yellow=%d" % (cyan, yellow))
    if cyan < CYAN_INK:
        failures.append(label + ": the GL_LINE_LOOP list left no ink (" + str(cyan) + " px)")
    if yellow < SLOT_INK:
        failures.append(label + ": the direct glBegin control left no ink (" + str(yellow) + " px)")
    arms = arm_reading(image, layout, scale)
    lines.append(label + " armA_white=%d armA_magenta=%d armB_white=%d armB_magenta=%d texControl_magenta=%d"
                 " armC_outside=%d armC_inside=%d" % (
                     arms["armA_white"], arms["armA_magenta"], arms["armB_white"], arms["armB_magenta"],
                     arms["texControl_magenta"], arms["armC_outside"], arms["armC_inside"]))
    lines.append(label + " armD_green=%d armE_green=%d armF_green=%d armG_green=%d cullArmFace=%d" % (
        arms["armD_green"], arms["armE_green"], arms["armF_green"], arms["armG_green"], layout["cull_arm_face"]))
    failures.extend(arm_failures(arms, label))
    failures.extend(compile_colour_failures(layout, label))
    failures.extend(attrib_state_failures(layout, label))
    failures.extend(query_width_failures(layout, label))
    failures.extend(attrib_picture_failures(arms, label))
    failures.extend(cull_arm_failures(arms, layout, label))
    reading = {"stage": label, "band0_red": bands[0][0], "band0_green": bands[0][1],
               "band1_red": bands[1][0], "band1_green": bands[1][1], "triangle_cyan": cyan, "direct_yellow": yellow,
               "outer_band": red_bands[0] if len(red_bands) == 1 else -1}
    reading.update(arms)
    return not failures, failures, lines, reading


def main(argv):
    args = parse_args(argv)
    report = json.loads(pathlib.Path(args.report).read_text())
    layout = layout_of(report)
    if args.picture is not None:
        if layout["compiles"] <= 0:
            print("FAILED: the scene compiled no display list (displayListCompiles="
                  + str(report.get("displayListCompiles")) + ")")
            return 1
        image = parse_png(args.picture)
        scale = (image.width / float(layout["gui"][0]), image.height / float(layout["gui"][1]))
        passed, failures, lines, reading = single_picture_report(image, layout, scale,
                                                                 args.picture.stem)
        # Positive control: the same picture with the list's green pasted over the call-time square, which is the
        # baked-colour picture. The single-picture rule has to reject it.
        control_ok = None
        if reading["outer_band"] >= 0:
            green_band = 1 - reading["outer_band"]
            baked = image.copy()
            centre_x = (layout["panel"][0] + layout["panel"][2]) / 2
            if copy_square(baked, image, scale, (centre_x, layout["slots"][green_band]),
                           (centre_x, layout["slots"][reading["outer_band"]]), layout["half"]):
                control_passed, control_failures, control_lines, control = single_picture_report(
                    baked, layout, scale, "control(baked)")
                control_ok = not control_passed
                lines.extend(control_lines)
                if control_passed:
                    failures.append("the positive control was accepted: a picture with the list's green in both "
                                    "squares read as correct, so the single-picture check cannot fail")
        # The same second control as the two-frame mode: the compile-time layout's answer is the two arms exchanged.
        layout_control_ok = None
        swapped = swap_arms(image, layout, scale)
        if swapped is not None:
            swapped_reading = analyse(swapped, layout, scale, "control(compile-layout)")
            layout_control_ok = bool(arm_failures(swapped_reading, "control"))
            lines.append("control (layout arms exchanged) armA_white=" + str(swapped_reading["armA_white"])
                         + " armA_magenta=" + str(swapped_reading["armA_magenta"]) + " armB_white="
                         + str(swapped_reading["armB_white"]) + " armB_magenta="
                         + str(swapped_reading["armB_magenta"]) + " rejected=" + str(layout_control_ok))
            if not layout_control_ok:
                failures.append("the layout-arm positive control was accepted: a picture with the two arms' colours "
                                "exchanged - the compile-time layout's answer - read as correct")
        # Arm C's control: the list's own colour painted over the caller-colour quad is the picture a mirror that
        # follows the recorded glColor leaves, so the arm-C check has to reject it.
        compile_colour_control_ok = None
        outer_band_y = layout["slots"][reading["outer_band"]] if reading["outer_band"] >= 0 else None
        if outer_band_y is not None:
            painted = arm_c_contaminated(image, layout, scale, outer_band_y)
            if painted is not None:
                painted_reading = analyse(painted, layout, scale, "control(compile-colour)")
                compile_colour_control_ok = bool(arm_c_failures(painted_reading, "control"))
                lines.append("control (list colour painted over arm C) armC_outside="
                             + str(painted_reading["armC_outside"]) + " armC_inside="
                             + str(painted_reading["armC_inside"]) + " rejected="
                             + str(compile_colour_control_ok))
                if not compile_colour_control_ok:
                    failures.append("the arm-C positive control was accepted: a picture with the colour recorded "
                                    "inside the compile over the caller-colour quad read as correct")
        # Arm D's restored green pasted over arm E: the picture a pop that ignores its mask leaves. The arm-E rule
        # above has to reject it.
        attrib_control_ok = None
        if reading.get("armD_green", 0) >= ARM_INK:
            painted = image.copy()
            if copy_square(painted, image, scale, (layout["armD"][0], layout["armD"][1]),
                           (layout["armE"][0], layout["armE"][1]), layout["armE"][2]):
                painted_reading = analyse(painted, layout, scale, "control(attrib-mask)")
                attrib_control_ok = bool(attrib_picture_failures(painted_reading, "control"))
                lines.append("control (restored quad pasted into arm E) armD_green="
                             + str(painted_reading["armD_green"]) + " armE_green="
                             + str(painted_reading["armE_green"]) + " rejected=" + str(attrib_control_ok))
                if not attrib_control_ok:
                    failures.append("the attribute-mask positive control was accepted: a picture with the restored "
                                    "quad in the arm whose push did not select the colour group read as correct")
        # And the cull-face control: the surviving quad pasted into the arm GL_FRONT has to cull.
        cull_control_ok = None
        if reading.get("armF_green", 0) >= ARM_INK:
            painted = image.copy()
            if copy_square(painted, image, scale, (layout["armF"][0], layout["armF"][1]),
                           (layout["armG"][0], layout["armG"][1]), layout["armG"][2]):
                painted_reading = analyse(painted, layout, scale, "control(cull-face)")
                cull_control_ok = bool(cull_arm_failures(painted_reading, layout, "control"))
                lines.append("control (front-culled quad pasted into arm G) armF_green="
                             + str(painted_reading["armF_green"]) + " armG_green="
                             + str(painted_reading["armG_green"]) + " rejected=" + str(cull_control_ok))
                if not cull_control_ok:
                    failures.append("the cull-face positive control was accepted: a picture with ink in the quad"
                                    " glCullFace(GL_FRONT) has to cull read as correct")
        print("layout: " + layout["text"])
        for line in lines:
            print(line)
        if failures:
            print("FAILED:")
            for failure in failures:
                print("  - " + failure)
        else:
            print("PASSED: one square in the call-time colour, one in the list's own, each layout arm in the colour the"
                  + " call-time state selects, the compile-colour arm in the caller's colour, the line loop and the"
                  + " controls"
                  + ("" if control_ok is None else "; the baked-colour positive control is rejected")
                  + ("" if layout_control_ok is None else "; the compile-layout arms control is rejected")
                  + ("" if compile_colour_control_ok is None else "; the compile-colour arm control is rejected")
                  + ("" if attrib_control_ok is None else "; the attribute-mask control is rejected")
                  + ("" if cull_control_ok is None else "; the cull-face control is rejected"))
        if args.json:
            pathlib.Path(args.json).write_text(json.dumps({
                "leg": args.leg, "picture": str(args.picture), "passed": not failures,
                "layout": layout["text"], "reading": reading,
                "control_baked_colour_rejected": control_ok,
                "control_compile_layout_rejected": layout_control_ok,
                "control_compile_colour_rejected": compile_colour_control_ok,
                "control_attrib_mask_rejected": attrib_control_ok,
                "control_cull_face_rejected": cull_control_ok,
                "failures": failures}, indent=2) + "\n")
        return 0 if not failures else 1
    
    if layout["compiles"] <= 0:
        print("FAILED: the scene compiled no display list (displayListCompiles="
              + str(report.get("displayListCompiles")) + "); a black picture would prove nothing")
        return 1
    count = report.get("displayListDumpCount")
    if not count:
        print("FAILED: the scene requested no presented dump (displayListDumpCount=" + str(count) + ")")
        return 1
    stages = []
    for key, value in report.items():
        if key.startswith("displayListDump_displaylist-"):
            stages.append((key[len("displayListDump_"):], value))
    if len(stages) < 2:
        print("FAILED: fewer than two consecutive dumped frames (" + str(len(stages)) + "); the slot swap is the point")
        return 1

    report_lines = []
    failures = []
    readings = {}
    gui_width = layout["gui"][0]
    for stage, value in sorted(stages):
        # The report's first token is the picture path (celtium$relative); the path's basename is what the run's
        # screenshots directory holds, which is how the tooltip check resolves the same field.
        path = pathlib.Path(str(value).split()[0].split(" completed=", 1)[0])
        if not path.is_absolute():
            path = pathlib.Path(args.pictures).parent / path
        if not path.exists():
            path = pathlib.Path(args.pictures) / path.name
        if not path.exists():
            # The harness keeps a completed dump under a "-kept" copy of the same name. A report line written just
            # before the completion was recorded names the plain file (measured on leg r29a: the second stage's line
            # said completed=false while the image, its identity file and presentedFrame=43 were all written), and the
            # kept copy is that picture - so the check must not call the frame missing when it is on disk.
            kept = path.with_name(path.stem + "-kept" + path.suffix)
            if kept.exists():
                path = kept
        if not path.exists():
            failures.append(stage + ": the dump file is missing (" + str(path) + ")")
            continue
        image = parse_png(path)
        scale = (image.width / float(gui_width), image.height / float(layout["gui"][1]))
        reading = analyse(image, layout, scale, stage)
        readings[stage] = (image, scale, reading)
        report_lines.append(stage + " " + " ".join(
            "%s=%d" % (key, value) for key, value in reading.items() if key != "stage"))

    passed = True
    if not readings:
        print("FAILED: no dumped frame could be opened")
        return 1

    for stage, (image, scale, reading) in sorted(readings.items()):
        # Which band holds which list on this frame, reported by the scene (`even=` / `odd=`), not inferred here.
        outer_band, inner_band = (0, 1) if even_frame(stage) else (1, 0)
        outer = "band%d" % outer_band
        inner = "band%d" % inner_band
        if reading[outer + "_red"] < SLOT_INK:
            passed = False
            failures.append(stage + ": the outer list's square is not in the call-time colour (" + outer + "_red="
                            + str(reading[outer + "_red"]) + " px); the replay kept the compile-time colour or drew "
                            "nothing")
        if reading[outer + "_green"] >= SLOT_INK:
            passed = False
            failures.append(stage + ": the outer list's square is green (" + outer + "_green="
                            + str(reading[outer + "_green"]) + " px); the colour set outside the compile was baked into "
                            "the vertices, which is not what GL draws")
        if reading[inner + "_green"] < SLOT_INK:
            passed = False
            failures.append(stage + ": the inner list's square is not green (" + inner + "_green="
                            + str(reading[inner + "_green"]) + " px); the glColor the list carries was not replayed")
        if reading[inner + "_red"] >= SLOT_INK:
            passed = False
            failures.append(stage + ": the inner list's square is red (" + inner + "_red="
                            + str(reading[inner + "_red"]) + " px); the caller's colour overrode the list's own command")
        if reading["triangle_cyan"] < CYAN_INK:
            passed = False
            failures.append(stage + ": the GL_LINE_LOOP list left no ink (" + str(reading["triangle_cyan"])
                            + " px); GregTech's primitive is still not replayed")
        if reading["direct_yellow"] < SLOT_INK:
            # The control is drawn by the same code as any other immediate run, in the same frame. If it is missing
            # the immediate path itself did not draw, and the list's absence says nothing about the list.
            passed = False
            failures.append(stage + ": the direct glBegin control left no ink (" + str(reading["direct_yellow"])
                            + " px); this frame's immediate path drew nothing, so the list result is not attributable")
        arm_failed = arm_failures(reading, stage)
        if arm_failed:
            passed = False
            failures.extend(arm_failed)
        leak_failed = compile_colour_failures(layout, stage)
        if leak_failed:
            passed = False
            failures.extend(leak_failed)
        attrib_failed = attrib_picture_failures(reading, stage)
        if attrib_failed:
            passed = False
            failures.extend(attrib_failed)
        cull_failed = cull_arm_failures(reading, layout, stage)
        if cull_failed:
            passed = False
            failures.extend(cull_failed)

    # The attribute arms' state readback: the same in every frame, so it is judged once. The picture half ran per stage
    # above, where the ink is.
    state_failed = attrib_state_failures(layout, next(iter(sorted(readings)), "measurement"))
    if state_failed:
        passed = False
        failures.extend(state_failed)
    # And the two queries that must agree with their sibling width: glGetBooleanv against glGetFloatv for the colour
    # write mask, and glGetTexParameteriv against the filter the scene set. Both measured dead on leg r30b/r31a.
    width_failed = query_width_failures(layout, next(iter(sorted(readings)), "measurement"))
    if width_failed:
        passed = False
        failures.extend(width_failed)

    # The panel's markers must be where the layout says, or the picture is of something else.
    for stage, (image, scale, reading) in sorted(readings.items()):
        left, top, right, bottom = layout["panel"]
        if not marker_is(image, (left + 1) * scale[0], (top + 1) * scale[1], (0, 255, 0)):
            passed = False
            failures.append(stage + ": no green panel marker at the panel's top-left; the scene is not the one the "
                            "layout describes")
        if not marker_is(image, (right - 2) * scale[0], (bottom - 2) * scale[1], (0, 255, 255)):
            passed = False
            failures.append(stage + ": no cyan panel marker at the panel's bottom-right")

    # Positive control: put one frame's inner (green) square into the other frame's outer slot. That is exactly the
    # picture a renderer that baked the compile-time colour leaves, so the analysis has to report it as green in both
    # bands - at which point the outer-band assertion above would fail, which is the point.
    control_ok = None
    ordered = sorted(readings.items())
    if len(ordered) >= 2:
        (stage_a, (image_a, scale_a, _)) = ordered[0]
        (stage_b, (image_b, scale_b, _)) = ordered[1]
        inner_source_band = 1 if even_frame(stage_a) else 0
        outer_target_band = 0 if even_frame(stage_b) else 1
        target = image_b.copy()
        stamped = copy_square(target, image_a, scale_b,
                              ((layout["panel"][0] + layout["panel"][2]) / 2, layout["slots"][inner_source_band]),
                              ((layout["panel"][0] + layout["panel"][2]) / 2, layout["slots"][outer_target_band]),
                              layout["half"])
        if stamped:
            control = analyse(target, layout, scale_b, "control")
            both = control["band0_green"] >= SLOT_INK and control["band1_green"] >= SLOT_INK
            control_ok = both
            report_lines.append("control (" + stage_a + "'s inner square pasted into " + stage_b + "'s outer band) "
                                "band0_green=" + str(control["band0_green"]) + " band1_green="
                                + str(control["band1_green"]) + " band%d_red=%d" % (outer_target_band,
                                    control["band%d_red" % outer_target_band]) + " both=" + str(both))
            if not both:
                passed = False
                failures.append("the positive control did not read as a baked colour: the analysis cannot see green "
                                "in both bands, so a clean result would not mean the call-time colour was applied")

    # Second positive control: the picture the compile-time layout leaves, built by exchanging the two layout arms'
    # squares in a frame that passed. The arm assertions above have to reject it.
    layout_control_ok = None
    if ordered:
        (stage_first, (image_first, scale_first, _)) = ordered[0]
        swapped = swap_arms(image_first, layout, scale_first)
        if swapped is not None:
            swapped_reading = analyse(swapped, layout, scale_first, "control(compile-layout)")
            layout_control_ok = bool(arm_failures(swapped_reading, "control"))
            report_lines.append("control (" + stage_first + "'s layout arms exchanged) armA_white="
                                + str(swapped_reading["armA_white"]) + " armA_magenta="
                                + str(swapped_reading["armA_magenta"]) + " armB_white="
                                + str(swapped_reading["armB_white"]) + " armB_magenta="
                                + str(swapped_reading["armB_magenta"]) + " rejected=" + str(layout_control_ok))
            if not layout_control_ok:
                passed = False
                failures.append("the layout-arm positive control was accepted: a picture with the two arms' colours "
                                "exchanged - the compile-time layout's answer - read as correct")

    # Third positive control: the colour recorded inside the compile painted over arm C, which is the picture a renderer
    # whose mirror follows the recorded glColor leaves. The arm-C assertions above have to reject it.
    compile_colour_control_ok = None
    if ordered:
        (stage_first, (image_first, scale_first, reading_first)) = ordered[0]
        outer_band_first = 0 if even_frame(stage_first) else 1
        painted = arm_c_contaminated(image_first, layout, scale_first,
                                     layout["slots"][outer_band_first])
        if painted is not None:
            painted_reading = analyse(painted, layout, scale_first, "control(compile-colour)")
            compile_colour_control_ok = bool(arm_c_failures(painted_reading, "control"))
            report_lines.append("control (" + stage_first + "'s list colour painted over arm C) armC_outside="
                                + str(painted_reading["armC_outside"]) + " armC_inside="
                                + str(painted_reading["armC_inside"]) + " rejected="
                                + str(compile_colour_control_ok))
            if not compile_colour_control_ok:
                passed = False
                failures.append("the arm-C positive control was accepted: a picture with the colour recorded inside the "
                                "compile over the caller-colour quad read as correct")

    # Fourth positive control: arm D's restored green pasted over arm E, which is the picture a renderer whose pop
    # ignores the mask leaves. The arm-E assertion above has to reject it.
    attrib_control_ok = None
    if ordered:
        (stage_first, (image_first, scale_first, reading_first)) = ordered[0]
        if reading_first.get("armD_green", 0) >= ARM_INK:
            painted = image_first.copy()
            if copy_square(painted, image_first, scale_first, (layout["armD"][0], layout["armD"][1]),
                           (layout["armE"][0], layout["armE"][1]), layout["armE"][2]):
                painted_reading = analyse(painted, layout, scale_first, "control(attrib-mask)")
                attrib_control_ok = bool(attrib_picture_failures(painted_reading, "control"))
                report_lines.append("control (" + stage_first + "'s restored quad pasted into arm E) armD_green="
                                    + str(painted_reading["armD_green"]) + " armE_green="
                                    + str(painted_reading["armE_green"]) + " rejected=" + str(attrib_control_ok))
                if not attrib_control_ok:
                    passed = False
                    failures.append("the attribute-mask positive control was accepted: a picture with the restored "
                                    "quad in the arm whose push did not select the colour group read as correct")

    # Fifth positive control: arm F's surviving quad pasted into arm G. That is exactly the picture a renderer that
    # bakes cull-back leaves (the back-facing quad gone, the front-facing one drawn), so the cull rule has to reject it.
    cull_control_ok = None
    if ordered:
        (stage_first, (image_first, scale_first, reading_first)) = ordered[0]
        if reading_first.get("armF_green", 0) >= ARM_INK:
            painted = image_first.copy()
            if copy_square(painted, image_first, scale_first, (layout["armF"][0], layout["armF"][1]),
                           (layout["armG"][0], layout["armG"][1]), layout["armG"][2]):
                painted_reading = analyse(painted, layout, scale_first, "control(cull-face)")
                cull_control_ok = bool(cull_arm_failures(painted_reading, layout, "control"))
                report_lines.append("control (" + stage_first + "'s front-culled quad pasted into arm G) armF_green="
                                    + str(painted_reading["armF_green"]) + " armG_green="
                                    + str(painted_reading["armG_green"]) + " rejected=" + str(cull_control_ok))
                if not cull_control_ok:
                    passed = False
                    failures.append("the cull-face positive control was accepted: a picture with ink in the quad"
                                    " glCullFace(GL_FRONT) has to cull read as correct")

    print("layout: " + layout["text"])
    for line in report_lines:
        print(line)
    if failures:
        print("FAILED:")
        for failure in failures:
            print("  - " + failure)
    else:
        print("PASSED: the outer list draws in the call-time colour, the inner list keeps its own, both move with the"
              + " frame, the line loop replays, each layout arm is drawn in the colour the call-time state selects and"
              + " a compile's recorded colour does not survive its glEndList"
              + ("" if control_ok is None else "; the positive control reads as the baked-colour failure")
              + ("" if layout_control_ok is None else "; the compile-layout arms control is rejected")
              + ("" if compile_colour_control_ok is None else "; the compile-colour arm control is rejected")
              + ("" if attrib_control_ok is None else "; the attribute-mask control is rejected")
              + ("" if cull_control_ok is None else "; the cull-face control is rejected"))
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps({
            "leg": args.leg, "passed": passed and not failures, "layout": layout["text"],
            "readings": {stage: reading for stage, (_, _, reading) in readings.items()},
            "control_baked_colour_detected": control_ok,
            "control_compile_layout_rejected": layout_control_ok,
            "control_compile_colour_rejected": compile_colour_control_ok,
            "control_attrib_mask_rejected": attrib_control_ok,
            "control_cull_face_rejected": cull_control_ok, "failures": failures}, indent=2) + "\n")
    return 0 if passed and not failures else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
