#!/usr/bin/env python3
"""A display list compiled out of glBegin/glEnd geometry: does glCallList draw it, at the caller's transform?

Owner: **custom rendering fidelity**. Unit: "the invisible-custom-rendering report is measured on the code path a mod
in this pack actually uses".

## What this checks

`-Dceltium.validation.screen=displaylist` opens `DisplayListLegacyScreen`, which compiles two `GL_COMPILE` display lists
out of immediate-mode geometry - a filled `GL_QUADS` square compiled green, and a `GL_LINE_LOOP` triangle, which is the
primitive GregTech's tool overlays use - and then draws them with `glCallList` under a `glPushMatrix`/`glTranslatef`
that moves the square between two slots on consecutive frames. The screen asks for two consecutive frames itself.

Read from the class file, `gregtech/common/ToolEventHandlers.drawRotationMarker` does exactly this:
`glNewList(4864=GL_COMPILE)`, `glBegin(GL_LINE_LOOP)`, 121 `glVertex3d`, `glEnd`, `glEndList`, and `glCallList` per
frame. The renderer used to refuse the run because a list was being compiled, so the list was stored empty and every
replay drew nothing - the reported "some custom rendering is invisible".

The picture therefore answers four questions at once, and each of them is a way the fix can be wrong:

1. **The geometry is there at all.** Nothing in either slot means the list replay still draws nothing.
2. **It is at the call transform, not the compile one.** Both lists are compiled at the origin; a renderer that drew the
   run at compile time would paint the square at the screen's top-left corner, where no slot is.
3. **It moves with the frame.** An even frame must have the square in the upper slot and the lower slot empty, an odd
   frame the reverse. Two slots carrying ink at once would be a retained copy from the previous frame.
4. **The colour is the compiled one.** The list is compiled green and the call sets red first; GL compiles the colour
   that preceded the vertices, so a red square is a renderer applying call-time state to compiled geometry.

The line loop is counted separately because the renderer's line pipeline is a separate path: a square alone would not
answer the GregTech primitive. A fourth quad - yellow, drawn directly with glBegin/glEnd in the same frame, under the
same matrix and the same untextured state - is the control that separates "the list replay draws nothing" from "the
immediate path draws nothing": if the yellow quad is missing, the list's absence is not attributable.

The scene draws its backdrop as a dark gradient and disables texture around its own geometry, both for measured
reasons rather than looks. The gradient is what lets the probe's own frame clear the leg picture gate's smoke floor
("presentedDistinctColours=3 is not above the smoke floor of 16", the first run of this scene), and texture is off
because the first run compiled the runs as POSITION_TEX_COLOR with no glTexCoord of their own - the GUI leaves
GL_TEXTURE_2D on - so the vertices sampled whatever coordinate was current and a transparent texel left no ink.

## The positive control

A check that cannot fail proves nothing. The script composites the square's band from one frame into the other frame's
empty band - the shape a renderer that ignored the translation would leave - and requires the same analysis to report
**both** slots carrying. The control is built from the run's own pixels, not painted by hand.

Runs without a GPU:

    python3 scripts/cpu-contract/display-list-legacy-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/display-list-legacy-check.py \
        --report <run>/logs/celtium-validation.json \
        --pictures <run>/screenshots --leg r26a-displaylist
"""
import argparse
import json
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel is "the compiled square" when green dominates and red/blue are low. The list is compiled with
#: glColor4f(0,1,0,1) and the call sets glColor4f(1,0,0,1), so the two possible colours are far apart: a red square is
#: exactly the mistake this separates, and it is counted as its own kind of failure rather than as "no ink".
GREEN = 160
RED = 90
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
    parser.add_argument("--leg", default="", help="the leg name, for the output")
    parser.add_argument("--json", type=pathlib.Path, default=None, help="write the measurement as JSON here")
    args = parser.parse_args(argv)
    if args.run is not None:
        args.report = args.report or (args.run / "logs" / "celtium-validation.json")
        args.pictures = args.pictures or (args.run / "screenshots")
    if not args.report or not args.pictures:
        parser.error("give --run, or both --report and --pictures")
    return args


def layout_of(report):
    """{@return} the scene's resolved layout: GUI size, panel rect, slot centres, square half, triangle, colour."""
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
    panel = [int(v) for v in fields.get("panel", "").split(",")]
    slots = [int(v) for v in fields.get("slots", "").split(",")]
    triangle = [int(v) for v in fields.get("triangle", "").split(",")]
    direct = [int(v) for v in fields.get("direct", "").split(",")]
    colour = [int(v) for v in fields.get("colour", "").split(",")]
    if len(panel) != 4 or len(slots) != 2 or len(triangle) != 3 or len(direct) != 3:
        raise SystemExit("displayListLayout is not the expected shape: " + text)
    return {
        "gui": (gui_width, gui_height),
        "panel": panel,
        "half": int(fields.get("square", "30")),
        "slots": slots,
        "triangle": triangle,
        "direct": direct,
        "colour": colour,
        "frames": int(fields.get("frames", "0")),
        "compiles": int(fields.get("compiles", "0")),
        "text": text,
    }


def parse_png(path):
    return Image.open(path).convert("RGB")


def count_green(image, rect):
    """{@return} how many pixels in the rect are the compiled square's green."""
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
            if green >= GREEN and red <= RED and blue <= RED:
                found += 1
    return found


def count_red(image, rect):
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
            if red >= GREEN and green <= RED and blue <= RED:
                found += 1
    return found


def count_yellow(image, rect):
    """{@return} how many pixels are the direct-immediate control's yellow (red and green high, blue low)."""
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
            if red >= GREEN and green >= GREEN and blue <= RED:
                found += 1
    return found


def count_cyan(image, rect):
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
            if green >= GREEN and blue >= GREEN and red <= RED:
                found += 1
    return found


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


def stamp_square(target, source, scale, cx, cy, half):
    """Copies the square-sized region around (cx, cy) from source into target: the positive control's ghost."""
    left, top, right, bottom = [int(round(v)) for v in rect_for(scale, cx, cy, half)]
    left = max(0, left)
    top = max(0, top)
    right = min(min(target.width, source.width), right)
    bottom = min(min(target.height, source.height), bottom)
    if right <= left or bottom <= top:
        return False
    target.paste(source.crop((left, top, right, bottom)), (left, top))
    return True


def analyse(image, layout, scale, stage):
    """{@return} the frame's reading: both slots' green ink, a red square if there is one, and the line loop."""
    half = layout["half"]
    centres = layout["slots"]
    gui_centre_x = (layout["panel"][0] + layout["panel"][2]) / 2
    reading = {"stage": stage, "slot_a_green": 0, "slot_b_green": 0, "slot_a_red": 0, "slot_b_red": 0,
               "triangle_cyan": 0, "direct_yellow": 0}
    names = ("slot_a", "slot_b")
    for index, centre in enumerate(centres):
        rect = rect_for(scale, gui_centre_x, centre, half + MARGIN)
        reading[names[index] + "_green"] = count_green(image, rect)
        reading[names[index] + "_red"] = count_red(image, rect)
    triangle_x, triangle_y, triangle_r = layout["triangle"]
    reading["triangle_cyan"] = count_cyan(image, rect_for(scale, triangle_x, triangle_y, triangle_r + MARGIN + 2))
    direct_x, direct_y, direct_half = layout["direct"]
    reading["direct_yellow"] = count_yellow(image, rect_for(scale, direct_x, direct_y, direct_half + MARGIN))
    return reading


def expectation(stage):
    """{@return} which slot the stage's frame should carry the square in: 'a' for even frames, 'b' for odd."""
    return "a" if stage.endswith("-a") else "b"


def main(argv):
    args = parse_args(argv)
    report = json.loads(pathlib.Path(args.report).read_text())
    layout = layout_of(report)
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
        print("FAILED: fewer than two consecutive dumped frames (" + str(len(stages)) + "); alternation is the point")
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
        wanted = expectation(stage)
        other = "slot_b" if wanted == "a" else "slot_a"
        mine = "slot_" + wanted + "_green"
        if reading[mine] < SLOT_INK:
            passed = False
            failures.append(stage + ": the square is not in slot " + wanted + " (" + mine + "=" + str(reading[mine])
                            + "); the list replay is drawing nothing where it was called")
        if reading[other + "_green"] >= SLOT_INK:
            passed = False
            failures.append(stage + ": the other slot also carries the square (" + other + "_green="
                            + str(reading[other + "_green"]) + "); a transform was baked in or a frame was retained")
        wrong = reading["slot_a_red"] + reading["slot_b_red"]
        if wrong >= SLOT_INK:
            passed = False
            failures.append(stage + ": the square is red (" + str(wrong) + " px); the call-time colour was applied to "
                            "compiled geometry instead of the compiled one")
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

    # Positive control: paste one frame's square into the other frame's empty slot and require both slots to carry.
    control_ok = None
    ordered = sorted(readings.items())
    if len(ordered) >= 2:
        (stage_a, (image_a, scale_a, _)) = ordered[0]
        (stage_b, (image_b, scale_b, _)) = ordered[1]
        wanted_a = expectation(stage_a)
        target = image_b.copy()
        source_centre = layout["slots"][0] if wanted_a == "a" else layout["slots"][1]
        stamped = stamp_square(target, image_a, scale_b, (layout["panel"][0] + layout["panel"][2]) / 2,
                               source_centre, layout["half"])
        if stamped:
            control = analyse(target, layout, scale_b, "control")
            both = control["slot_a_green"] >= SLOT_INK and control["slot_b_green"] >= SLOT_INK
            control_ok = both
            report_lines.append("control (" + stage_a + " pasted into " + stage_b + ") slot_a_green="
                                + str(control["slot_a_green"]) + " slot_b_green=" + str(control["slot_b_green"])
                                + " both=" + str(both))
            if not both:
                passed = False
                failures.append("the positive control did not read as both slots carrying: the analysis cannot see a "
                                "duplicate, so a clean result would not mean anything")

    print("layout: " + layout["text"])
    for line in report_lines:
        print(line)
    if failures:
        print("FAILED:")
        for failure in failures:
            print("  - " + failure)
    else:
        print("PASSED: one square, at the slot the frame called it to, compiled green with the line loop replayed"
              + ("" if control_ok is None else "; the positive control reads as both slots carrying"))
    if args.json:
        pathlib.Path(args.json).write_text(json.dumps({
            "leg": args.leg, "passed": passed and not failures, "layout": layout["text"],
            "readings": {stage: reading for stage, (_, _, reading) in readings.items()},
            "control_both_slots": control_ok, "failures": failures}, indent=2) + "\n")
    return 0 if passed and not failures else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
