#!/usr/bin/env python3
"""Is the enchant glint in the picture, is it additive, and is it only on the armour that should have it?

The armour glint is the same model drawn twice more: vanilla's ``LayerArmorBase`` re-renders the armour
with the glint texture, lighting off, depth test EQUAL with the depth mask off, an additive-leaning blend
(``SRC_COLOR, ONE``) and a texture matrix that scrolls between the two passes. A sentence read off a
picture ("the armour sparkles") cannot fail. This checker turns it into a measurement with controls, on
the fixture's within-run triple (``entityGlintAbTick``: the chestplate's ``ench`` tag off / on / off over
three consecutive frames, stages ``glint-a`` / ``glint-b`` / ``glint-c``; the carrier wears the same
chestplate in all three, so the armour's own pixels are the control):

* **the brightening** - pixels whose luminance in the middle frame beats *both* outer frames by a margin.
  The glint only adds light, so a pixel the middle frame has and neither outer frame does is the glint's;
* **additivity** - over that mask, ``b - a`` has to be positive in all three channels almost everywhere.
  An additive blend raises every channel it touches; a replacement (a wrong blend factor, a substituted
  texture) moves at least one channel down somewhere, and that is a different defect with a different fix;
* **the skin invariance** - inside the armour's region but outside the brightened mask, the middle frame
  has to equal the first: if the armour's own pixels moved, the triple is measuring the armour changing
  (a swapped stack, a re-rendered model), not a pass drawn over it;
* **the instruments** - the run's final ``CELTIUM_VERIFY`` line has to show the texture-matrix groups the
  two passes are (``texMatrixGroups`` at least twice the glint targets in frame) with no model draw
  refused and no ``CELTIUM_MODEL_MISSING`` line anywhere: celtium has no fallback renderer, so a refused
  draw is a hole in the picture, not a detail.
"""
from __future__ import annotations

import argparse
import glob
import json
import os
import re
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the legs have Pillow
    Image = None

BRIGHTEN = 6
SKIN_TOLERANCE = 2


def _require_pillow() -> None:
    if Image is None:
        raise SystemExit("enchant-glint-check needs Pillow (python3 -c 'import PIL')")


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


def _luminance(pixel) -> float:
    return (pixel[0] * 299 + pixel[1] * 587 + pixel[2] * 114) / 1000.0


def _brightened(pa, pb, pc) -> bool:
    return _luminance(pb) - max(_luminance(pa), _luminance(pc)) > BRIGHTEN


def analyse(off_a, on, off_c, dilate: int = 8) -> dict:
    """Counts the glint's brightening in the middle frame and reads the controls around it.

    The armour's region is this window's own brightened mask's bounding box, dilated - which on its own
    would be circular (a mask is always inside its own box), so the verdict also binds the box's shape:
    its height and width have to be a chestplate's at the carrier distance, and the mask's size is capped
    at three times its floor. The region's real work is the skin-invariance test inside it.
    """
    if off_a.size != on.size or off_a.size != off_c.size:
        raise SystemExit(f"the three pictures are different sizes: {off_a.size}, {on.size}, {off_c.size}")
    width, height = on.size
    a = off_a.tobytes()
    b = on.tobytes()
    c = off_c.tobytes()
    mask: list[tuple[int, int]] = []
    control_a = 0
    control_c = 0
    additive = 0
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            pa = (a[i], a[i + 1], a[i + 2])
            pb = (b[i], b[i + 1], b[i + 2])
            pc = (c[i], c[i + 1], c[i + 2])
            if _brightened(pa, pb, pc):
                mask.append((x, y))
                if pb[0] > pa[0] and pb[1] > pa[1] and pb[2] > pa[2]:
                    additive += 1
            if _luminance(pa) - max(_luminance(pb), _luminance(pc)) > BRIGHTEN:
                control_a += 1
            if _luminance(pc) - max(_luminance(pa), _luminance(pb)) > BRIGHTEN:
                control_c += 1
    region = None
    skin_violations = 0
    skin_pixels = 0
    if mask:
        xs = [p[0] for p in mask]
        ys = [p[1] for p in mask]
        region = (max(0, min(xs) - dilate), max(0, min(ys) - dilate),
                  min(width - 1, max(xs) + dilate), min(height - 1, max(ys) + dilate))
        mask_set = set(mask)
        for y in range(region[1], region[3] + 1):
            row = y * width * 3
            for x in range(region[0], region[2] + 1):
                if (x, y) in mask_set:
                    continue
                i = row + x * 3
                pa = (a[i], a[i + 1], a[i + 2])
                pb = (b[i], b[i + 1], b[i + 2])
                skin_pixels += 1
                if max(abs(pa[0] - pb[0]), abs(pa[1] - pb[1]), abs(pa[2] - pb[2])) > SKIN_TOLERANCE:
                    skin_violations += 1
    return {
        "width": width,
        "height": height,
        "brightened": len(mask),
        "additiveFraction": additive / len(mask) if mask else 0.0,
        "controlA": control_a,
        "controlC": control_c,
        "region": region,
        "regionWidth": region[2] - region[0] + 1 if region else 0,
        "regionHeight": region[3] - region[1] + 1 if region else 0,
        "skinPixels": skin_pixels,
        "skinViolations": skin_violations,
    }


def read_instruments(run_dir: str) -> dict | None:
    """The run's final CELTIUM_VERIFY counters and its CELTIUM_MODEL_MISSING count, from its console logs.

    Returns None when the run carries no console log at all: the picture tests still stand, but the
    instrument conditions (C2-6/C2-7) are then unproven and the caller has to say so rather than pass them
    silently - a zero read from a missing instrument is how a refused draw hides.
    """
    logs = sorted(glob.glob(os.path.join(run_dir, "**", "*.log"), recursive=True))
    if not logs:
        return None
    verify_lines: list[str] = []
    missing = 0
    refusals = None
    tex_groups = None
    for path in logs:
        with open(path, encoding="utf-8", errors="replace") as handle:
            for line in handle:
                if "CELTIUM_MODEL_MISSING" in line:
                    missing += 1
                if "CELTIUM_VERIFY" in line:
                    verify_lines.append(line)
    if verify_lines:
        last = verify_lines[-1]
        match = re.search(r"texMatrixGroups=(\d+)", last)
        if match:
            tex_groups = int(match.group(1))
        match = re.search(r"modelDrawRefusals=(\d+)", last)
        if match:
            refusals = int(match.group(1))
    return {"texMatrixGroups": tex_groups, "modelDrawRefusals": refusals,
            "modelMissingLines": missing, "verifyLines": len(verify_lines)}


def verdict(result: dict, instruments: dict | None = None, min_brightened: int = 300,
            min_region_height: int = 40, min_region_width: int = 20,
            glint_targets: int = 1) -> list[str]:
    """The problems the triple has, or an empty list when the glint brightened only the armour, additively.

    ``min_brightened`` (F2) and the region band are provisional defaults: the D15 leg registers the
    measured values from its first run and passes them explicitly, the way the glow checker's floors were
    registered from its leg.
    """
    problems: list[str] = []
    count = result["brightened"]
    if count < min_brightened:
        problems.append(f"only {count} pixels are brighter in the glint frame than in both outer frames "
                        f"(need {min_brightened}): the glint's passes did not reach the picture")
        return problems
    if count > 3 * min_brightened:
        problems.append(f"{count} pixels brightened (limit {3 * min_brightened} = 3x the floor): the "
                        "brightening is the scene changing, not one armour's glint")
    if result["regionHeight"] < min_region_height or result["regionWidth"] < min_region_width:
        problems.append(f"the brightened region is {result['regionWidth']}x{result['regionHeight']} (need "
                        f"at least {min_region_width}x{min_region_height}): a patch that small is not a "
                        "chestplate at the carrier distance")
    for name in ("controlA", "controlC"):
        if result[name] >= 0.1 * min_brightened:
            problems.append(f"{result[name]} pixels satisfy the same brightening with {name[-1]} as the "
                            f"middle frame (limit {0.1 * min_brightened:.0f}): the criterion is describing "
                            "the scene, not the glint")
    if result["additiveFraction"] < 0.9:
        problems.append(f"only {result['additiveFraction'] * 100:.1f}% of the brightened pixels rose in "
                        "all three channels (need 90%): the glint's blend is a replacement somewhere, not "
                        "the additive SRC_COLOR/ONE the vanilla pass uses")
    if result["skinViolations"] > 0:
        problems.append(f"{result['skinViolations']} pixels inside the armour's region but outside the "
                        f"brightened mask moved by more than {SKIN_TOLERANCE}: the armour's own pixels "
                        "changed between the frames, so the triple is not measuring a pass over a still "
                        "armour")
    if instruments is not None:
        tex = instruments["texMatrixGroups"]
        if tex is None or tex < 2 * glint_targets:
            problems.append(f"texMatrixGroups is {tex} (need at least {2 * glint_targets} = 2x the glint "
                            "targets): the glint's two texture-matrix passes did not draw")
        if instruments["modelDrawRefusals"] is None or instruments["modelDrawRefusals"] != 0:
            problems.append(f"modelDrawRefusals is {instruments['modelDrawRefusals']} (need 0): a refused "
                            "model draw is a hole in the picture - celtium has no fallback renderer")
        if instruments["modelMissingLines"] != 0:
            problems.append(f"{instruments['modelMissingLines']} CELTIUM_MODEL_MISSING lines: a model list "
                            "did not resolve")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run", required=True, help="the leg's run directory (.gv-verify/run-<leg>)")
    parser.add_argument("--json", action="store_true", help="print the numbers as JSON")
    parser.add_argument("--min-brightened", type=int, default=300)
    parser.add_argument("--glint-targets", type=int, default=1)
    args = parser.parse_args(argv)

    a = load_rgb(find_presented(args.run, "glint-a"))
    b = load_rgb(find_presented(args.run, "glint-b"))
    c = load_rgb(find_presented(args.run, "glint-c"))
    result = analyse(a, b, c)
    instruments = read_instruments(args.run)
    problems = verdict(result, instruments=instruments, min_brightened=args.min_brightened,
                       glint_targets=args.glint_targets)
    if instruments is None:
        problems.append("no console log in this run: texMatrixGroups / modelDrawRefusals / "
                        "CELTIUM_MODEL_MISSING (C2-6/C2-7) are unproven")
    printable = dict(result)
    printable["instruments"] = instruments
    printable["problems"] = problems
    printable["verdict"] = "PASS" if not problems else "FAIL"
    if args.json:
        print(json.dumps(printable, indent=1))
    else:
        print(f"frame {result['width']}x{result['height']}")
        print(f"brightened={result['brightened']} additive={result['additiveFraction'] * 100:.1f}% "
              f"region={result['regionWidth']}x{result['regionHeight']}")
        print(f"controlA={result['controlA']} controlC={result['controlC']} "
              f"skinViolations={result['skinViolations']}/{result['skinPixels']}")
        print(f"instruments={instruments}")
        for problem in problems:
            print(f"PROBLEM: {problem}")
        print("verdict=" + ("PASS" if not problems else "FAIL"))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
