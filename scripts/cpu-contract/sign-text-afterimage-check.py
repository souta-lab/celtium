#!/usr/bin/env python3
"""The sign's text: does the frame after a text frame still carry the text?

Owner: **world-pass text fidelity**. Unit: "the reported afterimage is measured on a sign, not only on a tooltip".

## What this checks

`-Dceltium.validation.signTextAbTick=T` places a standing sign on the ground four blocks in front of the pinned
camera, writes four lines of text into its tile entity, and then draws three consecutive frames:

| stage | tick | text |
|---|---|---|
| `signtext-a` | T | on |
| `signtext-b` | T+1 | off |
| `signtext-c` | T+2 | off |

The sign's text is drawn by `TileEntitySignRenderer` inside the world pass, through the same font capture the GUI
uses, so this is the world-pass counterpart of the tooltip question - and the one a named screen cannot produce.

The text's pixels are the pixels `a` and `c` disagree on: `a` drew the text, `c` did not, and the camera is pinned so
nothing else moved. The afterimage is the part of that mask where `b` and `c` disagree: `b` is the first frame after
the text was removed, so a text still drawn there is a run that outlived the state that produced it. `b` and `c` are
both off, so a correct renderer makes them identical inside the mask.

The check is one-sided in the right direction: a *missing* text makes `ink` zero and fails as "nothing was measured",
which is not a pass. A text drawn a frame late makes `b` carry the mask and `c` not, which is the ghost.

## The positive control

A check that cannot fail proves nothing. `a` against `c` is the same analysis with the text present in the first
frame, and it must report the whole mask as a difference. The control is the run's own pixels, not a painted mask.

## The noise floor

Animated textures, clouds and the player's arm move between any two frames. The script reports the share of pixels
*outside* the mask that differ between `b` and `c`, so a leg whose scene is not static says so instead of hiding it
inside the ghost count.

Runs without a GPU:

    python3 scripts/cpu-contract/sign-text-afterimage-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/sign-text-afterimage-check.py \
        --report <run>/logs/celtium-validation.json \
        --pictures <run>/screenshots --leg r28a-signtext
"""
import argparse
import json
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel differs when any channel is at least this far apart. The sign's text is black on wood, so the glyph edges
#: are the only place a small difference can come from; 8 is the same threshold the renderer's own flicker sampler uses.
DIFF = 8
#: The text has to be big enough to measure. Four lines of ten characters at four blocks are thousands of pixels; a
#: few hundred is the floor below which "no ghost" would mean "no text".
MIN_INK = 200
#: The share of the mask that may differ between the two off frames. Antialiasing on a pinned camera is exact, so the
#: tolerance is for a stray animated pixel inside the mask, not for a ghost (which is the whole mask).
GHOST_TOLERANCE = 0.02
#: Above this the leg fails: a text drawn a frame late carries most of the mask.
GHOST_FAIL = 0.10
#: The share of the frame outside the mask that may move between the two off frames before the scene is called
#: non-static. A pinned camera over a static world is 0; clouds and animated textures are the usual few percent.
NOISE_LIMIT = 0.05


def load(path):
    with Image.open(path) as image:
        return image.convert("RGB")


def diff_mask(first, second):
    """Pixels where any channel differs by more than DIFF, as a list of (x, y, delta)."""
    a = first.load()
    b = second.load()
    width, height = first.size
    out = []
    for y in range(height):
        for x in range(width):
            pa = a[x, y]
            pb = b[x, y]
            delta = max(abs(pa[0] - pb[0]), abs(pa[1] - pb[1]), abs(pa[2] - pb[2]))
            if delta > DIFF:
                out.append((x, y, delta))
    return out


def bbox(points):
    if not points:
        return None
    xs = [p[0] for p in points]
    ys = [p[1] for p in points]
    return (min(xs), min(ys), max(xs), max(ys))


def analyse(on, off1, off2):
    """The three frames as numbers: the text's mask, the ghost inside it, the control and the noise floor."""
    ink = diff_mask(on, off2)
    ink_pixels = {(p[0], p[1]) for p in ink}
    between_off = diff_mask(off1, off2)
    ghost = [p for p in between_off if (p[0], p[1]) in ink_pixels]
    noise = [p for p in between_off if (p[0], p[1]) not in ink_pixels]
    total = on.size[0] * on.size[1]
    outside = max(1, total - len(ink_pixels))
    return {
        "ink": len(ink),
        "inkBox": bbox(ink),
        "ghost": len(ghost),
        "ghostBox": bbox(ghost),
        "ghostFraction": len(ghost) / float(len(ink)) if ink else 0.0,
        "control": len(ink),
        "noise": len(noise),
        "noiseFraction": len(noise) / float(outside),
        "size": on.size,
    }


def verdict(result, min_ink=MIN_INK, ghost_tolerance=GHOST_TOLERANCE, ghost_fail=GHOST_FAIL,
            noise_limit=NOISE_LIMIT):
    """The problems the numbers name, in the order a reader needs them. Empty means the check passed."""
    problems = []
    if result["ink"] < min_ink:
        problems.append("the text's mask is " + str(result["ink"]) + " px, below the " + str(min_ink)
                        + " px floor - nothing was measured, which is not a pass")
        return problems
    if result["control"] < 0.9 * result["ink"]:
        problems.append("the control (a vs c) does not carry the mask, so the analysis cannot see the text")
    if result["ghostFraction"] > ghost_fail:
        problems.append("the first frame after the text was removed still carries "
                        + format(100.0 * result["ghostFraction"], ".1f") + "% of it - the afterimage")
    elif result["ghostFraction"] > ghost_tolerance:
        problems.append(format(100.0 * result["ghostFraction"], ".2f") + "% of the text's pixels differ between"
                        " the two off frames, above the " + format(100.0 * ghost_tolerance, ".1f") + "% tolerance")
    if result["noiseFraction"] > noise_limit:
        problems.append("the scene is not static enough: " + format(100.0 * result["noiseFraction"], ".2f")
                        + "% of the pixels outside the text moved between the two off frames")
    return problems


def find_picture(pictures, stage):
    matches = sorted(pictures.glob("*presented-" + stage + "-*kept.png"))
    if not matches:
        matches = sorted(pictures.glob("*presented-" + stage + "*.png"))
    return matches[0] if matches else None


def report_problems(report):
    """What the run's report has to say before its pictures can be read: the text was written and then removed."""
    problems = []
    for stage, expected_on in (("a", True), ("b", False), ("c", False)):
        key = "signTextAb" + stage
        value = report.get(key)
        if value is None:
            problems.append("the report has no " + key + " (the scene did not run)")
            continue
        text = str(value)
        if ("on=" + str(expected_on).lower()) not in text:
            problems.append(key + " does not say on=" + str(expected_on).lower() + ": " + text)
        if "lines=4" not in text:
            problems.append(key + " does not say lines=4: " + text)
        if "block=minecraft:standing_sign" not in text:
            problems.append(key + " does not say the sign was still there: " + text)
    scene = report.get("signTextScene")
    if scene is None:
        problems.append("the report has no signTextScene (the sign was never placed)")
    elif "placed=true" not in str(scene):
        problems.append("the sign was not placed: " + str(scene))
    return problems


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", type=pathlib.Path, default=None,
                        help="a leg's run directory (report and screenshots are found inside it)")
    parser.add_argument("--report", type=pathlib.Path, default=None)
    parser.add_argument("--pictures", type=pathlib.Path, default=None)
    parser.add_argument("--leg", default="")
    args = parser.parse_args()

    if args.run is not None:
        report_path = args.run / "logs" / "celtium-validation.json"
        pictures = args.run / "screenshots"
    else:
        report_path = args.report
        pictures = args.pictures
    if report_path is None or pictures is None:
        parser.error("give --run, or both --report and --pictures")
    if not report_path.is_file():
        print("FAIL: no report at " + str(report_path))
        return 2

    report = json.loads(report_path.read_text())
    problems = report_problems(report)
    paths = {stage: find_picture(pictures, "signtext-" + stage) for stage in ("a", "b", "c")}
    for stage, path in paths.items():
        if path is None:
            problems.append("no presented dump for signtext-" + stage + " in " + str(pictures))
    if problems:
        for problem in problems:
            print("FAIL: " + problem)
        return 2

    on = load(paths["a"])
    off1 = load(paths["b"])
    off2 = load(paths["c"])
    if not (on.size == off1.size == off2.size):
        print("FAIL: the three dumps are not the same size: " + str(on.size) + " " + str(off1.size) + " "
              + str(off2.size))
        return 2

    result = analyse(on, off1, off2)
    print("leg " + (args.leg or str(report_path.parent.parent.name)))
    print("pictures " + ", ".join(paths[s].name for s in ("a", "b", "c")))
    print("ink (a vs c) = " + str(result["ink"]) + " px, box=" + str(result["inkBox"]))
    print("ghost (b vs c inside the ink) = " + str(result["ghost"]) + " px, box=" + str(result["ghostBox"]))
    print("noise (b vs c outside the ink) = " + str(result["noise"]) + " px ("
          + format(100.0 * result["noiseFraction"], ".4f") + "%)")
    print("control (a vs c inside the ink) = " + str(result["control"]) + " px of " + str(result["ink"]))
    print("ghost fraction = " + format(100.0 * result["ghostFraction"], ".3f") + "% of the text's pixels")
    problems = verdict(result)
    if problems:
        for problem in problems:
            print("FAIL: " + problem)
        return 1
    print("PASS: the two off frames agree on the text's pixels; no afterimage")
    return 0


if __name__ == "__main__":
    sys.exit(main())
