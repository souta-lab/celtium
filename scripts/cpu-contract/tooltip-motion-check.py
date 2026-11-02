#!/usr/bin/env python3
"""The moving tooltip: does one presented frame hold one tooltip, or last frame's glyphs as well?

Owner: **font/GUI fidelity**. Unit: "the reported tooltip afterimage is measured in motion, not assumed fixed".

## What this checks

`-Dceltium.validation.screen=tooltipmotion` opens `TooltipMotionScreen`, which draws an opaque black panel and then a
real vanilla tooltip (the same `GuiScreen.drawHoveringText` -> Forge `GuiUtils` path the reported defect was seen on)
at one of two positions, alternating every frame. The screen asks for four consecutive frames itself, so both
parities and both arms are in the set whatever frame the leg's own screenshot catches.

Every frame therefore has to hold exactly one tooltip box. If a glyph run (or the box) from the previous frame were
drawn again, the other position would carry ink as well - which is the ghosted tooltip shape, and it is measurable
without a reference picture because the panel is black by construction: ink is any pixel above the black threshold.

The measurement is scale-free: the layout comes out of the run's report (`tooltipMotionLayout`, in GUI units), the
picture's scale is `picture_width / tooltipMotionGuiWidth`, and the panel's own green/cyan corner markers must be
where the layout says. An absent or shifted panel is a failure, not "no ink, so no ghost".

## The positive control

A check that cannot fail proves nothing. For each pair of frames with different parities the script composites the
other frame's tooltip region into this frame's other slot - the shape a retained run would leave - and requires the
same analysis to report **two** tooltips. The control is built from the run's own pixels, not painted by hand.

## The two arms

The screen draws the same commands twice: straight onto the main target, and through a framebuffer it creates with raw
`glGenFramebuffers`/`glFramebufferTexture2D` - a mod's own render target, and the case the retained-glyph-run drop was
written for. The stage names the arm (`-main` / `-fbo`), and the check requires each arm to show both positions and to
carry the **same ink** at the same position: the framebuffer arm composites its panel back one texel to one pixel, so
the two pictures are the same pixels unless the renderer lost or misplaced the glyphs on the way through the mod
target. A lost run shows up as the box border alone (a few hundred pixels against ~17,000); a run offered a frame late
shows up as two tooltips in one frame. The report's `tooltipMotionFbo` has to say `complete`, so a leg whose
framebuffer was never usable cannot be read as "the mod-target arm drew nothing".

Runs without a GPU:

    python3 scripts/cpu-contract/tooltip-motion-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/tooltip-motion-check.py \
        --report <run>/logs/celtium-validation.json \
        --pictures <run>/screenshots --leg r25d-tooltipmotion
"""
import argparse
import json
import pathlib
import re
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel is ink when any channel is at least this. The tooltip's border blends to 80 in blue over black and its text
#: is 255; the box's interior fill blends to 15 and is deliberately below this, so the count is the border and glyphs
#: rather than a filled rectangle (a filled rectangle would hide a glyph-only ghost inside itself).
INK = 16
#: Where the box actually lands relative to the origin. Vanilla's GuiUtils draws the tooltip twelve GUI units *above*
#: the mouse Y it is given, and the box starts four above that (0x505000FF border): the top edge is origin-16. With
#: three lines the box is ~44 tall (8 + 2*10 plus 4 above and 3 below), so its bottom is origin+28. The band is two
#: GUI units wider on each side than the box, and the gap between the bands is the rest of the panel.
BAND_TOP = 18
BAND_BOTTOM = 32
#: A band counts as carrying a tooltip when it has at least this many ink pixels: a stray marker or a two-pixel
#: antialias edge is not a tooltip, and a box border alone is hundreds of pixels at the calibrated scale.
MIN_TOOLTIP_INK = 60
#: How far the two arms' ink *energy* may differ at the same position, as a fraction of the main arm's. Energy (the
#: sum of each pixel's brightest channel) is what a resample preserves: the framebuffer arm's composite lands on a
#: slightly different sub-pixel grid than the main target's own drawing (the GUI's 427 units over 1280 px against the
#: framebuffer's own scale), which moves antialiased glyph edges across the ink threshold - measured on leg
#: `r27a-tooltipfbo`, the ink count differed by 10.9% while the energy agreed to 0.07%. A lost glyph run is ~80% of the
#: energy and a ghost doubles it, so the tolerance is far from either.
ARM_ENERGY_TOLERANCE = 0.02
#: How far the two arms' ink bounding boxes may differ, in pixels. The same drawing at the same position has the same
#: box; a run drawn a frame late would be at the other position and fail the per-frame test instead.
ARM_BOX_TOLERANCE = 1


def arm_of(stage):
    """{@return} which target drew the frame: ``fbo`` for the mod framebuffer, ``main`` otherwise.

    The suffix is what the screen writes; a stage without one is an older run whose only arm was the main target, and
    reading it as ``main`` keeps those runs measurable.
    """
    return "fbo" if stage.endswith("-fbo") else "main"


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
    """{@return} the screen's resolved layout: GUI size, panel rect, slot origins, text X."""
    text = report.get("tooltipMotionLayout")
    if not isinstance(text, str) or not text:
        raise SystemExit("the report has no tooltipMotionLayout: the tooltipmotion screen never opened")
    gui = re.search(r"gui=(\d+)x(\d+)", text)
    panel = re.search(r"panel=(-?\d+),(-?\d+),(-?\d+),(-?\d+)", text)
    slots = re.search(r"slots=(-?\d+),(-?\d+)", text)
    text_x = re.search(r"textX=(-?\d+)", text)
    drawn = re.search(r"frames=(\d+)", text)
    if not (gui and panel and slots and text_x and drawn):
        raise SystemExit(f"tooltipMotionLayout could not be parsed: {text}")
    return {
        "gui_width": int(gui.group(1)),
        "gui_height": int(gui.group(2)),
        "panel": tuple(int(panel.group(i)) for i in range(1, 5)),
        "slot_a": int(slots.group(1)),
        "slot_b": int(slots.group(2)),
        "text_x": int(text_x.group(1)),
        "drawn_frames": int(drawn.group(1)),
        "raw": text,
    }


def dumps_of(report):
    """{@return} [(stage, picture path)] for the frames the screen asked for, in report order."""
    pictures = []
    for key, value in report.items():
        if not key.startswith("tooltipMotionDump_") or not isinstance(value, str):
            continue
        stage = key[len("tooltipMotionDump_"):]
        path = value.split(" completed=", 1)[0]
        pictures.append((stage, path))
    return pictures


def picture_path(pictures_dir, relative):
    candidate = pictures_dir / pathlib.Path(relative).name
    return candidate


class Measurement:
    def __init__(self, path):
        self.path = path
        self.image = Image.open(path).convert("RGB")
        self.width, self.height = self.image.size
        self.pixels = self.image.load()

    def channel(self, x, y):
        r, g, b = self.pixels[x, y]
        return max(r, g, b)

    def ink_rect(self, left, top, right, bottom, exclude=()):
        """{@return} (count, bbox or None) of ink pixels in the rectangle, both ends exclusive."""
        count = 0
        min_x = min_y = None
        max_x = max_y = None
        for y in range(max(0, top), min(self.height, bottom)):
            for x in range(max(0, left), min(self.width, right)):
                skipped = False
                for box in exclude:
                    if box[0] <= x < box[2] and box[1] <= y < box[3]:
                        skipped = True
                        break
                if skipped:
                    continue
                if self.channel(x, y) >= INK:
                    count += 1
                    if min_x is None or x < min_x:
                        min_x = x
                    if max_x is None or x > max_x:
                        max_x = x
                    if min_y is None or y < min_y:
                        min_y = y
                    if max_y is None or y > max_y:
                        max_y = y
        box = None if min_x is None else (min_x, min_y, max_x, max_y)
        return count, box

    def ink_energy(self, left, top, right, bottom, exclude=()):
        """{@return} the sum of each pixel's brightest channel over the rectangle.

        The quantity a resample preserves: moving a glyph edge half a pixel across the ink threshold changes the
        *count* but not the total, which is why the two arms are compared on this and not on the count.
        """
        total = 0
        for y in range(max(0, top), min(self.height, bottom)):
            for x in range(max(0, left), min(self.width, right)):
                skipped = False
                for box in exclude:
                    if box[0] <= x < box[2] and box[1] <= y < box[3]:
                        skipped = True
                        break
                if skipped:
                    continue
                total += self.channel(x, y)
        return total


def analyse(measurement, layout):
    scale = measurement.width / float(layout["gui_width"])
    left, top, right, bottom = (int(round(v * scale)) for v in layout["panel"])
    marker = max(2, int(round(4 * scale)))

    def gui_y(value):
        return int(round(value * scale))

    result = {
        "picture": measurement.path.name,
        "scale": round(scale, 4),
        "panel_px": [left, top, right, bottom],
    }
    # The panel and its markers: an absent screen, or one laid out elsewhere, must fail rather than measure nothing.
    green = measurement.channel(left + marker // 2, top + marker // 2)
    cyan = measurement.channel(right - 1 - marker // 2, bottom - 1 - marker // 2)
    result["marker_green"] = green
    result["marker_cyan"] = cyan
    if green < 200 or cyan < 200:
        result["error"] = (f"the panel's corner markers are not where the layout says "
                           f"(green={green} at {left},{top}; cyan={cyan} at {right},{bottom})")
        return result
    # The markers are ink too, and the upper band starts at the panel's own top edge, so they are excluded by rect
    # rather than by hoping the tooltip never overlaps a corner. Two corners: green top-left, cyan bottom-right. The
    # rects carry a two-pixel margin: the framebuffer arm's composite lands on a slightly different sub-pixel grid, so
    # a marker's antialiased edge can reach one pixel past the marker itself - measured on leg `r27a-tooltipfbo`, where
    # the un-margined rect left one marker pixel in the band and moved the ink box 57 px.
    margin = 2
    markers = [(left - margin, top - margin, left + marker + margin, top + marker + margin),
               (right - marker - margin, bottom - marker - margin, right + margin, bottom + margin)]
    band_a = (left, gui_y(layout["slot_a"] - BAND_TOP), right, gui_y(layout["slot_a"] + BAND_BOTTOM))
    band_b = (left, gui_y(layout["slot_b"] - BAND_TOP), right, gui_y(layout["slot_b"] + BAND_BOTTOM))
    gap = (left, gui_y(layout["slot_a"] + BAND_BOTTOM + 2), right, gui_y(layout["slot_b"] - BAND_TOP - 2))
    # Both bands have to be wholly inside the panel. A band that reaches past it counts the world above the panel as
    # ink - measured on leg r25g-tooltip, where the upper box's top border was drawn sixteen units above its origin at
    # y=102 while the panel started at 120, and the sky in the band read as 20496 pixels of the other position.
    if band_a[1] < top or band_b[3] > bottom:
        result["error"] = (f"the band the layout implies is not inside the panel: band_a top {band_a[1]} against"
                           f" panel top {top}, band_b bottom {band_b[3]} against panel bottom {bottom}")
        return result
    count_a, box_a = measurement.ink_rect(*band_a, exclude=markers)
    count_b, box_b = measurement.ink_rect(*band_b, exclude=markers)
    count_gap, box_gap = measurement.ink_rect(*gap, exclude=markers)
    result["band_a"] = {"ink": count_a, "box": box_a,
                        "energy": measurement.ink_energy(*band_a, exclude=markers)}
    result["band_b"] = {"ink": count_b, "box": box_b,
                        "energy": measurement.ink_energy(*band_b, exclude=markers)}
    result["gap"] = {"ink": count_gap, "box": box_gap}
    carries_a = count_a >= MIN_TOOLTIP_INK
    carries_b = count_b >= MIN_TOOLTIP_INK
    result["carries"] = ("a" if carries_a else "") + ("b" if carries_b else "")
    result["one_tooltip"] = carries_a != carries_b and count_gap <= 4
    for band in (box_a, box_b):
        if band is not None:
            result["tooltip_height_px"] = band[3] - band[1]
    return result


def band_rects(measurement, layout):
    """{@return} the two tooltip bands in picture pixels: (band_a, band_b)."""
    scale = measurement.width / float(layout["gui_width"])
    left = int(round(layout["panel"][0] * scale))
    right = int(round(layout["panel"][2] * scale))
    band_a = (left, int(round((layout["slot_a"] - BAND_TOP) * scale)), right,
              int(round((layout["slot_a"] + BAND_BOTTOM) * scale)))
    band_b = (left, int(round((layout["slot_b"] - BAND_TOP) * scale)), right,
              int(round((layout["slot_b"] + BAND_BOTTOM) * scale)))
    return band_a, band_b


def composited_control(measurement_paths, layout):
    """Build a ghost out of two real frames: this frame's tooltip plus the other position's from the other parity."""
    if len(measurement_paths) < 2:
        return None
    pictures = [Measurement(path) for path in measurement_paths]
    base = pictures[0]
    base_result = analyse(base, layout)
    carried = base_result.get("carries") or ""
    if carried not in ("a", "b"):
        return None
    empty = "b" if carried == "a" else "a"
    for source in pictures[1:]:
        if analyse(source, layout).get("carries") != empty:
            continue
        source_bands = band_rects(source, layout)
        base_bands = band_rects(base, layout)
        index = 1 if empty == "b" else 0
        # The other frame's occupied band, copied into this frame's empty one: exactly the shape a glyph run that
        # survived the frame boundary would leave, built from the run's own pixels rather than painted by hand.
        ghost = base.image.copy()
        ghost.paste(source.image.crop(source_bands[index]), base_bands[index][:2])
        path = base.path.with_name(base.path.stem + "-composited-ghost.png")
        ghost.save(path)
        return path
    return None


def main(argv):
    args = parse_args(argv)
    report = json.loads(pathlib.Path(args.report).read_text(encoding="utf-8"))
    layout = layout_of(report)
    dumps = dumps_of(report)
    if not dumps:
        raise SystemExit("the report lists no tooltipmotion dumps: the screen never asked for a frame")
    print(f"leg {args.leg or report.get('leg', '?')}: {layout['raw']}")
    print(f"report: {args.report}")
    measurements = []
    failures = []
    for stage, relative in dumps:
        path = picture_path(pathlib.Path(args.pictures), relative)
        if not path.is_file():
            failures.append(f"{stage}: the dump file is not there ({path})")
            continue
        measurement = Measurement(path)
        result = analyse(measurement, layout)
        result["stage"] = stage
        measurements.append((measurement, result))
        if "error" in result:
            failures.append(f"{stage}: {result['error']}")
            continue
        print(f"  {stage}: {result['picture']} arm={arm_of(stage)} carries={result['carries'] or 'none'}"
              f" a={result['band_a']['ink']} b={result['band_b']['ink']} gap={result['gap']['ink']}"
              f" height={result.get('tooltip_height_px')}px one_tooltip={result['one_tooltip']}")
        if not result["one_tooltip"]:
            failures.append(f"{stage}: one frame must hold one tooltip, carried={result['carries'] or 'none'}"
                            f" gap={result['gap']['ink']}")
    parities = {result["carries"] for _, result in measurements if result.get("carries")}
    if parities and parities != {"a", "b"}:
        failures.append(f"the dumped frames only ever showed position(s) {sorted(parities)}:"
                        " both parities have to be measured")
    # The two arms: each has to show both positions, and the mod-framebuffer arm has to carry the same ink at the same
    # position as the main arm. A run the renderer lost on the way through the mod target leaves the box border alone;
    # a run offered a frame late puts the other position's box in the same frame, which the per-frame test above sees.
    by_arm = {}
    for _, result in measurements:
        if "error" in result:
            continue
        by_arm.setdefault(arm_of(result["stage"]), []).append(result)
    for arm, results in sorted(by_arm.items()):
        carried = {result["carries"] for result in results if result.get("carries")}
        if carried != {"a", "b"}:
            failures.append(f"the {arm} arm only ever showed position(s) {sorted(carried)}:"
                            " both parities have to be measured in each arm")
    fbo_report = report.get("tooltipMotionFbo")
    if "fbo" in by_arm:
        if not isinstance(fbo_report, str) or not fbo_report.startswith("complete"):
            failures.append(f"the mod-framebuffer arm's target was not complete: {fbo_report!r}")
        for position in ("a", "b"):
            main = next((result for result in by_arm.get("main", [])
                         if result.get("carries") == position), None)
            fbo = next((result for result in by_arm["fbo"] if result.get("carries") == position), None)
            if main is None or fbo is None:
                failures.append(f"position {position} is not in both arms (main={main is not None}"
                                f" fbo={fbo is not None}): the two targets cannot be compared")
                continue
            main_band = main["band_" + position]
            fbo_band = fbo["band_" + position]
            energy_delta = abs(fbo_band["energy"] - main_band["energy"]) / float(max(1, main_band["energy"]))
            box_delta = (0 if main_band["box"] is None or fbo_band["box"] is None
                         else max(abs(a - b) for a, b in zip(main_band["box"], fbo_band["box"])))
            print(f"  arm comparison at {position}: ink main={main_band['ink']} fbo={fbo_band['ink']}"
                  f" energy main={main_band['energy']} fbo={fbo_band['energy']} delta={energy_delta:.4f}"
                  f" box_delta={box_delta}px")
            if energy_delta > ARM_ENERGY_TOLERANCE:
                failures.append(f"the mod-framebuffer arm's tooltip at position {position} carries"
                                f" {fbo_band['energy']} ink energy against the main arm's {main_band['energy']}"
                                f" ({energy_delta:.1%}): the same drawing through a mod target has to produce the"
                                " same picture")
            if box_delta > ARM_BOX_TOLERANCE:
                failures.append(f"the mod-framebuffer arm's tooltip at position {position} is drawn"
                                f" {box_delta}px away from the main arm's (boxes {main_band['box']} against"
                                f" {fbo_band['box']})")
    elif isinstance(fbo_report, str) and "frames=0" not in fbo_report:
        failures.append(f"the report says the mod-framebuffer arm drew ({fbo_report!r}) but no dumped frame names it")
    # Positive control: the same analysis on a picture with the other position composited in must see two tooltips.
    control_path = composited_control([measurement.path for measurement, _ in measurements], layout)
    control = None
    if control_path is None:
        failures.append("no pair of frames with different parities: the ghost control cannot be built")
    else:
        control = analyse(Measurement(control_path), layout)
        control["stage"] = "composited-ghost"
        print(f"  control: {control['picture']} carries={control['carries'] or 'none'}"
              f" a={control['band_a']['ink']} b={control['band_b']['ink']} gap={control['gap']['ink']}"
              f" one_tooltip={control['one_tooltip']}")
        if control["one_tooltip"]:
            failures.append("the ghost control was read as a clean frame: the check cannot fail and proves nothing")
    payload = {"leg": args.leg or report.get("leg", ""), "layout": layout["raw"],
               "report": str(args.report), "frames": [result for _, result in measurements],
               "control": control, "failures": failures, "passed": not failures}
    if args.json is not None:
        pathlib.Path(args.json).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print("PASSED" if not failures else "FAILED")
    for failure in failures:
        print(f"  - {failure}")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
