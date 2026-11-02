#!/usr/bin/env python3
"""Is the entity-outline glow in the picture, and is it the glow that put it there?

The glow is the one rendering feature whose evidence has always been a sentence read off a picture ("outlines still
drawn when entities glow"). That sentence cannot fail: a white pixel near a mob looks the same whether the outline
chain drew it, whether the mob's own texture has a bright edge, or whether a cloud happens to sit behind it. This
checker turns it into a measurement with a control.

The fixture's ``-Dceltium.validation.glowAbTick=T`` tags every non-player entity glowing at tick ``T+1`` and clears the
tag at ``T`` and ``T+2``, dumping the frame each tick draws (stages ``glow-a``, ``glow-b``, ``glow-c``). The camera is
pinned and the stack is static, so the only thing that differs between the three frames is the tag. The outline is
drawn in the entity's team colour, which for a teamless mob is white, so the measurement is:

* **outline** - pixels that are bright and neutral in ``b`` and are *not* bright and neutral in either outer frame.
  Bright-and-neutral is what the outline is; requiring it to be absent from both outer frames is what excludes the
  clouds, the HUD's white text and the hotbar, all of which are white in every frame;
* **the control** - the same count computed with ``a`` or ``c`` as the middle frame. A criterion that fires on the
  outer frames too is measuring "white things in this scene", not the glow;
* **the noise floor** - the pixels that differ between the two outer frames, which is what a same-arm pair of this
  scene moves by itself (particles, the F3 digits, an animated icon). The outline has to be large against it;
* **the shape** - an outline is sparse and spread over the mobs, so its bounding box has to be wide and tall and its
  fill ratio low. A filled rectangle is a HUD panel or a missing texture, not an outline.

The checker never claims the outline is the *right* colour or width - there is no reference arm in the triple. It
decides "the glow tag put bright neutral pixels around the mobs and nothing else did".
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the legs have Pillow
    Image = None


def _require_pillow() -> None:
    if Image is None:
        raise SystemExit("glow-outline-check needs Pillow (python3 -c 'import PIL')")


def load_rgb(path: str) -> "Image.Image":
    _require_pillow()
    return Image.open(path).convert("RGB")


def find_presented(run_dir: str, stage: str) -> str:
    """Finds the presented dump a run kept for ``stage``; the -kept copy is the one the identity file describes."""
    patterns = [
        os.path.join(run_dir, "screenshots", f"celtium-presented-{stage}-*-kept.png"),
        os.path.join(run_dir, "screenshots", f"celtium-presented-{stage}-*.png"),
    ]
    for pattern in patterns:
        found = sorted(glob.glob(pattern))
        if found:
            return found[0]
    raise SystemExit(f"no presented dump for stage {stage!r} under {run_dir}")


def bright_neutral(pixel: tuple[int, int, int], floor: int = 200, spread: int = 40) -> bool:
    """Whether a pixel is the outline's own colour: bright, and neutral enough to be white rather than a mob's edge."""
    low = min(pixel)
    high = max(pixel)
    return low >= floor and high - low <= spread


def analyse(off_a: "Image.Image", on: "Image.Image", off_c: "Image.Image",
            floor: int = 200, spread: int = 40, threshold: int = 1) -> dict:
    """Counts the outline the middle frame has and the outer frames do not, plus the controls that make it mean something."""
    if off_a.size != on.size or off_a.size != off_c.size:
        raise SystemExit(f"the three pictures are different sizes: {off_a.size}, {on.size}, {off_c.size}")
    width, height = on.size
    a = off_a.tobytes()
    b = on.tobytes()
    c = off_c.tobytes()
    outline: list[tuple[int, int]] = []
    outline_in_a: list[tuple[int, int]] = []
    outline_in_c: list[tuple[int, int]] = []
    noise = 0
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            pa = (a[i], a[i + 1], a[i + 2])
            pb = (b[i], b[i + 1], b[i + 2])
            pc = (c[i], c[i + 1], c[i + 2])
            if (abs(pa[0] - pc[0]) > threshold or abs(pa[1] - pc[1]) > threshold
                    or abs(pa[2] - pc[2]) > threshold):
                noise += 1
            if bright_neutral(pb, floor, spread) and not bright_neutral(pa, floor, spread) \
                    and not bright_neutral(pc, floor, spread):
                outline.append((x, y))
            # The control: the same criterion with an outer frame as the middle one. It has to find nothing, or the
            # criterion is describing the scene rather than the glow.
            if bright_neutral(pa, floor, spread) and not bright_neutral(pb, floor, spread) \
                    and not bright_neutral(pc, floor, spread):
                outline_in_a.append((x, y))
            if bright_neutral(pc, floor, spread) and not bright_neutral(pa, floor, spread) \
                    and not bright_neutral(pb, floor, spread):
                outline_in_c.append((x, y))
    pixels = width * height
    box = (0, 0, 0, 0)
    if outline:
        xs = [p[0] for p in outline]
        ys = [p[1] for p in outline]
        box = (min(xs), min(ys), max(xs), max(ys))
    box_area = max(1, (box[2] - box[0] + 1) * (box[3] - box[1] + 1)) if outline else 0
    return {
        "width": width,
        "height": height,
        "pixels": pixels,
        "outline": len(outline),
        "outlineFraction": len(outline) / pixels,
        "outlineInA": len(outline_in_a),
        "outlineInC": len(outline_in_c),
        "noise": noise,
        "noiseFraction": noise / pixels,
        "box": box,
        "boxWidth": box[2] - box[0] + 1 if outline else 0,
        "boxHeight": box[3] - box[1] + 1 if outline else 0,
        "fillRatio": len(outline) / box_area if outline else 0.0,
        "outlinePixels": outline,
    }


def verdict(result: dict, min_outline: int = 300, max_control_ratio: float = 0.1,
            min_box: int = 40, max_fill: float = 0.5, max_noise_fraction: float = 0.25) -> list[str]:
    """The problems the triple has, or an empty list when the glow tag is what put the bright pixels there."""
    problems: list[str] = []
    if result["outline"] < min_outline:
        problems.append(f"only {result['outline']} bright neutral pixels appear in the glowing frame and in neither "
                        f"outer frame (need {min_outline}): the tag did not put an outline in the picture")
        return problems
    for name in ("outlineInA", "outlineInC"):
        if result[name] > max(1, int(result["outline"] * max_control_ratio)):
            problems.append(f"{result[name]} pixels satisfy the same criterion with {name[-1].upper()} as the middle "
                            f"frame (limit {max_control_ratio * 100:.0f}% of {result['outline']}): the criterion is "
                            "describing the scene, not the glow")
    if result["boxWidth"] < min_box or result["boxHeight"] < min_box:
        problems.append(f"the outline's bounding box is {result['boxWidth']}x{result['boxHeight']} (need at least "
                        f"{min_box}x{min_box}): a small patch is a HUD element or a texture, not an outline around mobs")
    if result["fillRatio"] > max_fill:
        problems.append(f"{result['fillRatio'] * 100:.1f}% of the outline's bounding box is filled (limit "
                        f"{max_fill * 100:.0f}%): an outline is sparse, a filled box is a panel")
    if result["noiseFraction"] > max_noise_fraction:
        problems.append(f"{result['noiseFraction'] * 100:.1f}% of the frame differs between the two outer frames "
                        f"(limit {max_noise_fraction * 100:.0f}%): the scene is not static enough for this triple")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run", required=True, help="the leg's run directory (.gv-verify/run-<leg>)")
    parser.add_argument("--json", action="store_true", help="print the numbers as JSON")
    parser.add_argument("--min-outline", type=int, default=300)
    parser.add_argument("--max-fill", type=float, default=0.5)
    args = parser.parse_args(argv)

    a = load_rgb(find_presented(args.run, "glow-a"))
    b = load_rgb(find_presented(args.run, "glow-b"))
    c = load_rgb(find_presented(args.run, "glow-c"))
    result = analyse(a, b, c)
    problems = verdict(result, min_outline=args.min_outline, max_fill=args.max_fill)
    if args.json:
        printable = {k: v for k, v in result.items() if k != "outlinePixels"}
        printable["problems"] = problems
        printable["verdict"] = "PASS" if not problems else "FAIL"
        print(json.dumps(printable, indent=1))
    else:
        print(f"frame {result['width']}x{result['height']} pixels={result['pixels']}")
        print(f"outline={result['outline']} ({result['outlineFraction'] * 100:.3f}%) "
              f"box={result['box']} {result['boxWidth']}x{result['boxHeight']} fill={result['fillRatio'] * 100:.1f}%")
        print(f"control outlineInA={result['outlineInA']} outlineInC={result['outlineInC']}")
        print(f"noise={result['noise']} ({result['noiseFraction'] * 100:.2f}%)")
        for problem in problems:
            print(f"PROBLEM: {problem}")
        print("verdict=" + ("PASS" if not problems else "FAIL"))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
