#!/usr/bin/env python3
"""The creative inventory's item grid: does every slot the container holds carry an icon in the picture?

Owner: **GUI fidelity**. Unit: "the reported 'only some creative inventory tabs rendering wrong' is measured on the
picture, not eyeballed".

## What this checks

`-Dceltium.validation.screen=creative -Dceltium.validation.creativeTab=<index>` opens the real
`GuiContainerCreative`, selects a tab through the screen's own `setCurrentCreativeTab`, and keeps it open through the
capture. The report then carries two facts that have to agree:

* `creativeGridFilled` - how many of the container's slots hold a stack (a container fact), and
* `creativeGridLayout` - the grid's geometry in GUI units (`gui=WxH left=L top=T first=x,y pitch=P cols=C rows=R`).

The picture is the presented dump. The scale is `picture_width / guiWidth`, so nothing here assumes guiScale 3. Each
slot becomes a cell of `pitch` GUI units, inset by one unit on every side so the slot's own border is not counted, and
a cell carries an icon when enough of its pixels differ from the cell's **modal** colour - the slot background, read
from the picture rather than assumed. The number of cells that carry an icon must be at least `creativeGridFilled`.

That is the measurement the 2026-11-02 evidence could not make: it compared two arms' pictures by eye and reported
"19 of 24 cells differ", which cannot tell a slot that is empty in the container from a slot whose item the renderer
did not draw. This check reads both numbers from the same run.

## The positive control

A check that cannot fail proves nothing. For every cell the analysis calls empty, the control pastes a copy of the
busiest cell's interior into it and requires the same analysis to then call it occupied. The control is built from the
run's own pixels, so it fails if the ink threshold, the geometry or the scale is wrong.

Runs without a GPU:

    python3 scripts/cpu-contract/creative-grid-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/creative-grid-check.py \
        --report <run>/logs/celtium-validation.json \
        --picture <run>/screenshots/<presented tick200>.png
"""
import argparse
import json
import pathlib
import re
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel is ink when any channel differs from the cell's modal colour by more than this. The slot background is a
#: flat grey in the creative container texture and the item icons are drawn over it; 25 is above the texture's own
#: dithering and well below any icon's contrast against it.
INK_DIFF = 25
#: A cell carries an icon when at least this many of its pixels are ink. A 16x16 icon at scale 3 is 48x48 = 2304
#: pixels and even a thin item (a rail, a stick) covers several hundred; an empty slot's inset area is the flat
#: background and measures 0-20.
MIN_CELL_INK = 40
#: The cell is inset by this many GUI units on every side, so the slot's own border is not counted as ink.
INSET = 1


def load_layout(report):
    """The grid geometry, from `creativeGridLayout` or the last `creativeGridAt*Layout`."""
    text = report.get("creativeGridLayout")
    if not isinstance(text, str):
        candidates = [k for k in report if re.fullmatch(r"creativeGridAt\d+Layout", k)]
        candidates.sort(key=lambda k: int(re.search(r"At(\d+)", k).group(1)))
        text = report.get(candidates[-1]) if candidates else None
    if not isinstance(text, str):
        return None
    fields = dict(re.findall(r"(\w+)=([^\s]+)", text))
    try:
        gui_w, gui_h = (int(v) for v in fields["gui"].split("x"))
        first_x, first_y = (int(v) for v in fields["first"].split(","))
        return {
            "gui": (gui_w, gui_h),
            "left": int(fields["left"]),
            "top": int(fields["top"]),
            "first": (first_x, first_y),
            "pitch": int(fields["pitch"]),
            "cols": int(fields["cols"]),
            "rows": int(fields["rows"]),
            "slots": int(fields["slots"]),
            "text": text,
        }
    except (KeyError, ValueError):
        return None


def load_filled_slots(report):
    """The slot indices the container held a stack in, from the last `creativeGridAt*Contents`, or None.

    The per-slot list is the precise form: it names which cells must carry an icon, so a cell the tooltip happens to
    cover is not counted as evidence either way. The count alone (`creativeGridFilled`) is the fallback, and it is
    weaker for exactly that reason - a tooltip over an empty cell raises the occupied count without an icon.
    """
    candidates = [k for k in report if re.fullmatch(r"creativeGridAt\d+Contents", k)]
    candidates.sort(key=lambda k: int(re.search(r"At(\d+)", k).group(1)))
    for key in reversed(candidates):
        text = report.get(key)
        if not isinstance(text, str) or text.startswith("failed:"):
            continue
        slots = []
        for entry in text.split():
            match = re.match(r"(\d+)=", entry)
            if match:
                slots.append(int(match.group(1)))
        return slots, key
    return None, None


def cell_rect(layout, index, scale):
    """The cell's pixel rectangle in the picture, inset by INSET GUI units."""
    col = index % layout["cols"]
    row = index // layout["cols"]
    x = layout["left"] + layout["first"][0] + col * layout["pitch"]
    y = layout["top"] + layout["first"][1] + row * layout["pitch"]
    size = layout["pitch"]
    x0 = int(round((x + INSET) * scale))
    y0 = int(round((y + INSET) * scale))
    x1 = int(round((x + size - INSET) * scale))
    y1 = int(round((y + size - INSET) * scale))
    return x0, y0, x1, y1


def cell_ink(image, rect, background):
    """(ink pixels) for one cell, against the grid's own background colour."""
    x0, y0, x1, y1 = rect
    crop = image.crop((x0, y0, x1, y1)).convert("RGB")
    raw = crop.tobytes()
    ink = 0
    for i in range(0, len(raw), 3):
        if (max(abs(raw[i] - background[0]), abs(raw[i + 1] - background[1]),
                abs(raw[i + 2] - background[2])) > INK_DIFF):
            ink += 1
    return ink


def grid_background(image, layout, scale):
    """The slot background, as the modal colour of every cell interior together.

    Read from the whole grid rather than per cell: an item icon that covered its entire cell would be its own modal
    colour, and the cell would then measure zero ink. The slot background is one flat colour across the grid, so the
    mode over all cells is that colour whatever any single cell holds.
    """
    counts = {}
    for index in range(layout["slots"]):
        x0, y0, x1, y1 = cell_rect(layout, index, scale)
        raw = image.crop((x0, y0, x1, y1)).convert("RGB").tobytes()
        for i in range(0, len(raw), 3):
            pixel = (raw[i], raw[i + 1], raw[i + 2])
            counts[pixel] = counts.get(pixel, 0) + 1
    if not counts:
        return (0, 0, 0)
    return max(counts.items(), key=lambda item: item[1])[0]


def analyse(image, layout, scale):
    background = grid_background(image, layout, scale)
    return [cell_ink(image, cell_rect(layout, index, scale), background)
            for index in range(layout["slots"])]


def main(argv):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run", type=pathlib.Path, default=None,
                        help="a .gv-verify/run-<leg> directory (default: the newest one)")
    parser.add_argument("--report", type=pathlib.Path, default=None)
    parser.add_argument("--picture", type=pathlib.Path, default=None)
    parser.add_argument("--leg", default=None)
    args = parser.parse_args(argv)

    run = args.run
    if run is None and args.report is None:
        runs = sorted((p for p in DEFAULT_RUN.glob("run-*") if p.is_dir()),
                      key=lambda p: p.stat().st_mtime)
        if not runs:
            print("no run directory under %s" % DEFAULT_RUN, file=sys.stderr)
            return 2
        run = runs[-1]
    report_path = args.report or (run / "logs" / "celtium-validation.json")
    if not report_path.is_file():
        print("no report at %s" % report_path, file=sys.stderr)
        return 2
    report = json.loads(report_path.read_text(encoding="utf-8"))

    picture = args.picture
    if picture is None:
        if run is None:
            print("--picture is required with --report", file=sys.stderr)
            return 2
        candidates = sorted((run / "screenshots").glob("*tick200*.png"))
        if not candidates:
            candidates = sorted((run / "screenshots").glob("*world*.png"))
        if not candidates:
            print("no presented dump under %s" % (run / "screenshots"), file=sys.stderr)
            return 2
        picture = candidates[0]
    if not picture.is_file():
        print("no picture at %s" % picture, file=sys.stderr)
        return 2

    layout = load_layout(report)
    if layout is None:
        print("FAIL: the report carries no creativeGridLayout; the creative scene did not run")
        return 1
    filled = report.get("creativeGridFilled")
    if not isinstance(filled, int):
        print("FAIL: the report carries no creativeGridFilled (%r)" % (filled,))
        return 1

    image = Image.open(picture)
    scale = image.width / float(layout["gui"][0])
    inks = analyse(image, layout, scale)
    occupied = [i for i, ink in enumerate(inks) if ink >= MIN_CELL_INK]
    filled_slots, filled_key = load_filled_slots(report)

    print("leg=%s" % (args.leg or (run.name if run else "?")))
    print("report=%s" % report_path)
    print("picture=%s (%dx%d)" % (picture, image.width, image.height))
    print("layout=%s" % layout["text"])
    print("scale=%.4f inset=%d inkDiff=%d minCellInk=%d" % (scale, INSET, INK_DIFF, MIN_CELL_INK))
    print("container: slots=%d filled=%d" % (layout["slots"], filled))
    print("picture:   cells with an icon=%d" % len(occupied))
    print("per-cell ink: %s" % " ".join(str(ink) for ink in inks))

    # The positive control: paste the busiest cell's interior into every empty cell and require the analysis to call
    # them occupied. Built from this run's own pixels, so a wrong threshold or geometry fails it.
    if occupied:
        source = max(occupied, key=lambda i: inks[i])
        sx0, sy0, sx1, sy1 = cell_rect(layout, source, scale)
        patch = image.crop((sx0, sy0, sx1, sy1))
        control = image.copy()
        for index in range(layout["slots"]):
            if index in occupied:
                continue
            x0, y0, x1, y1 = cell_rect(layout, index, scale)
            control.paste(patch.resize((x1 - x0, y1 - y0)), (x0, y0))
        control_inks = analyse(control, layout, scale)
        control_occupied = [i for i, ink in enumerate(control_inks) if ink >= MIN_CELL_INK]
        control_ok = len(control_occupied) == layout["slots"]
        print("control:   cells with an icon=%d of %d (every empty cell painted from cell %d) -> %s"
              % (len(control_occupied), layout["slots"], source, "ok" if control_ok else "FAILED"))
    else:
        control_ok = False
        print("control:   no occupied cell to copy from, so the control cannot be built -> FAILED")

    problems = []
    if filled_slots is not None:
        print("filled slots: %s (%s)" % (filled_key, " ".join(str(i) for i in filled_slots)))
        missing = [i for i in filled_slots if inks[i] < MIN_CELL_INK]
        if missing:
            problems.append("the container holds a stack in %d slot(s) whose cell carries no icon: %s"
                            % (len(missing), " ".join(str(i) for i in missing)))
    elif len(occupied) < filled:
        problems.append("the container holds %d stacks but only %d cells carry an icon"
                        % (filled, len(occupied)))
    if not control_ok:
        problems.append("the positive control did not report every cell occupied")
    if problems:
        for problem in problems:
            print("FAIL: %s" % problem)
        return 1
    if filled_slots is not None:
        print("PASS: every one of the container's %d stacks has an icon in its own cell" % len(filled_slots))
    else:
        print("PASS: every one of the container's %d stacks has an icon in the picture" % filled)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
