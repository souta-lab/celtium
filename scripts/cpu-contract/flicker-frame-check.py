#!/usr/bin/env python3
"""Two consecutive presented frames of a scene that cannot move: do they differ?

Owner: **temporal stability**. Unit: "the reported block flicker is measured on a still camera, not argued from the
code".

## What this checks

`-Dceltium.validation.flickerArmTick=T` hides the HUD, clears the world's particles and sets
`-Dceltium.debug.flickerArm=true` at world tick `T`. The swapchain (`-Dceltium.debug.flickerSample`) then reads back
the next two presented frames and compares them, and (`-Dceltium.debug.flickerDump`) writes them as
`celtium-flicker-prev.png` and `celtium-flicker-cur.png` in the run directory.

The scene is chosen so that nothing in it can change by itself: the camera pose is pinned by the fixture, the world
time is pinned, the clouds are off, the flat stack is solid blocks (no water, lava or fire, whose textures animate),
the HUD is hidden and the particles are cleared. A pixel that differs between the two frames is therefore something
the renderer did differently on the second one - draw order, a rebuilt mesh, an alternating sample - which is the
flicker a player reports as "the blocks flicker".

## Why a fraction and not a picture

The renderer's own sampler already prints `CELTIUM_FLICKER changed=N/M fraction=F maxDelta=D`. This script reads the
two dumped frames and says the same thing independently, plus *where* the difference is: a difference on one object's
edges is an ordering fault, a difference across a whole surface is a shading or sampling fault, and a difference in
one corner is the sky or the sun. The bounding box is the cheapest form of that answer.

## The floor

`--max-fraction` defaults to 0.0005 (0.05% of the frame). The measured baseline for a scene with the HUD, clouds and
animated textures in it was 0.137% (docs/development.md, 2026-10-23), so a scene that is actually static has to come
in an order of magnitude below that to be worth calling static. The number is reported either way; the verdict is
only a convenience.

Runs without a GPU:

    python3 scripts/cpu-contract/flicker-frame-check.py --run .gv-verify/run-<leg>

or with explicit inputs:

    python3 scripts/cpu-contract/flicker-frame-check.py --prev a.png --cur b.png
"""
import argparse
import json
import pathlib
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
DEFAULT_RUN = ROOT / ".gv-verify"

#: A pixel differs when any channel is at least this far apart. The same threshold the renderer's own sampler uses.
DELTA = 8
#: The share of the frame that may differ and still be called static.
MAX_FRACTION = 0.0005


def load(path):
    """Returns the image as packed RGB bytes, or None when it cannot be read."""
    try:
        with Image.open(path) as image:
            return image.convert("RGB").tobytes()
    except (OSError, ValueError):
        return None


def compare(previous, current, delta=DELTA):
    """Compares two packed-RGB frames and returns the census the verdict is made from."""
    if previous is None or current is None:
        return {"status": "INCONCLUSIVE", "reason": "a frame could not be read"}
    if len(previous) != len(current):
        return {"status": "INCONCLUSIVE",
                "reason": "the frames are different sizes (%d and %d bytes)" % (len(previous), len(current))}
    changed = 0
    max_delta = 0
    first = last = None
    # Packed RGB, three bytes a pixel: the index of a pixel is its byte offset divided by three, which is the
    # row-major position, so the reported box is a place in the frame.
    for offset in range(0, len(previous), 3):
        dr = previous[offset] - current[offset]
        dg = previous[offset + 1] - current[offset + 1]
        db = previous[offset + 2] - current[offset + 2]
        if dr < 0:
            dr = -dr
        if dg < 0:
            dg = -dg
        if db < 0:
            db = -db
        d = dr if dr > dg else dg
        if db > d:
            d = db
        if d > max_delta:
            max_delta = d
        if d > delta:
            changed += 1
            pixel = offset // 3
            if first is None:
                first = pixel
            last = pixel
    total = len(previous) // 3
    return {
        "status": "MEASURED",
        "changed": changed,
        "total": total,
        "fraction": changed / float(total) if total else 0.0,
        "maxDelta": max_delta,
        "firstChangedIndex": first,
        "lastChangedIndex": last,
    }


def verdict(census, max_fraction=MAX_FRACTION):
    """Turns the census into PASS/FAIL, keeping the number in the answer."""
    if census.get("status") != "MEASURED":
        return "INCONCLUSIVE"
    return "PASS" if census["fraction"] <= max_fraction else "FAIL"


def find_frames(run):
    """Returns the two dumped frames of a run, or (None, None) when the run has none."""
    run = pathlib.Path(run)
    previous = run / "celtium-flicker-prev.png"
    current = run / "celtium-flicker-cur.png"
    if previous.is_file() and current.is_file():
        return previous, current
    return None, None


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run", default=None, help="a leg's run directory (the game's working directory)")
    parser.add_argument("--prev", default=None, help="the first of the two frames")
    parser.add_argument("--cur", default=None, help="the second of the two frames")
    parser.add_argument("--max-fraction", type=float, default=MAX_FRACTION)
    parser.add_argument("--json", action="store_true", help="print the census as JSON")
    args = parser.parse_args(argv)

    if args.prev and args.cur:
        previous, current = pathlib.Path(args.prev), pathlib.Path(args.cur)
    elif args.run:
        previous, current = find_frames(args.run)
        if previous is None:
            print("INCONCLUSIVE: no celtium-flicker-prev.png / -cur.png in %s" % args.run)
            return 2
    else:
        parser.error("give --run or both --prev and --cur")

    census = compare(load(previous), load(current))
    result = verdict(census, args.max_fraction)
    if args.json:
        print(json.dumps({"verdict": result, "census": census}, indent=1))
        return 0 if result == "PASS" else 2 if result == "INCONCLUSIVE" else 1
    if result == "INCONCLUSIVE":
        print("INCONCLUSIVE: %s" % census.get("reason"))
        return 2
    print("%s: %d of %d pixels differ (fraction=%.5f, maxDelta=%d, first=%s, last=%s)"
          % (result, census["changed"], census["total"], census["fraction"], census["maxDelta"],
             census["firstChangedIndex"], census["lastChangedIndex"]))
    return 0 if result == "PASS" else 1


if __name__ == "__main__":
    sys.exit(main())
