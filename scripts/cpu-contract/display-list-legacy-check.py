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
over the red one and requiring the single-picture check to reject the result.

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
    if (len(panel) != 4 or len(slots) != 2 or len(triangle) != 3 or len(direct) != 3
            or len(even) != 2 or len(odd) != 2 or len(call_colour) != 3 or len(inside_colour) != 3):
        raise SystemExit("displayListLayout is not the expected shape (even=/odd= slot pairs included): " + text)
    return {
        "gui": (gui_width, gui_height),
        "panel": panel,
        "half": int(fields.get("square", "30")),
        "slots": slots,
        "even": even,
        "odd": odd,
        "triangle": triangle,
        "direct": direct,
        "call_colour": call_colour,
        "inside_colour": inside_colour,
        "frames": int(fields.get("frames", "0")),
        "compiles": int(fields.get("compiles", "0")),
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


def count_green(image, rect):
    return count_colour(image, rect, GREEN)


def count_red(image, rect):
    return count_colour(image, rect, RED)


def count_yellow(image, rect):
    return count_colour(image, rect, YELLOW)


def count_cyan(image, rect):
    return count_colour(image, rect, CYAN)


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
    return reading


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
    reading = {"stage": label, "band0_red": bands[0][0], "band0_green": bands[0][1],
               "band1_red": bands[1][0], "band1_green": bands[1][1], "triangle_cyan": cyan, "direct_yellow": yellow,
               "outer_band": red_bands[0] if len(red_bands) == 1 else -1}
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
        print("layout: " + layout["text"])
        for line in lines:
            print(line)
        if failures:
            print("FAILED:")
            for failure in failures:
                print("  - " + failure)
        else:
            print("PASSED: one square in the call-time colour, one in the list's own, the line loop and the control"
                  + ("" if control_ok is None else "; the baked-colour positive control is rejected"))
        if args.json:
            pathlib.Path(args.json).write_text(json.dumps({
                "leg": args.leg, "picture": str(args.picture), "passed": not failures,
                "layout": layout["text"], "reading": reading,
                "control_baked_colour_rejected": control_ok, "failures": failures}, indent=2) + "\n")
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

    print("layout: " + layout["text"])
    for line in report_lines:
        print(line)
    if failures:
        print("FAILED:")
        for failure in failures:
            print("  - " + failure)
    else:
        print("PASSED: the outer list draws in the call-time colour, the inner list keeps its own, both move with the"
              + " frame and the line loop replays"
              + ("" if control_ok is None else "; the positive control reads as the baked-colour failure"))
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps({
            "leg": args.leg, "passed": passed and not failures, "layout": layout["text"],
            "readings": {stage: reading for stage, (_, _, reading) in readings.items()},
            "control_baked_colour_detected": control_ok, "failures": failures}, indent=2) + "\n")
    return 0 if passed and not failures else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
