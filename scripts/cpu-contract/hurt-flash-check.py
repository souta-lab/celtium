#!/usr/bin/env python3
"""Is the hurt flash in the picture, is it one flash, and is it only on the mob that should have it?

The damage flash is a texture-environment effect, not geometry: vanilla's ``RenderLivingBase`` writes a
constant (1, 0, 0, 0.3) into the lightmap unit's combine while a living entity's ``hurtTime`` is non-zero,
so the mob's own pixels are interpolated toward red by one weight and nothing is drawn that was not already
there. A sentence read off a picture ("the zombie looks red") cannot fail - a red-ish texture, a lighting
change and the real flash all look alike. This checker turns it into a measurement with controls, on the
fixture's within-run triple (``entityHurtAbTick``: off / on / off over three consecutive frames, stages
``hurt-a`` / ``hurt-b`` / ``hurt-c``, carrier F a bare zombie and carrier G an armoured one in the same
frames):

* **the fit** - for every pixel that moved in the flash carrier's band, solve ``b = a + (target - a) * w``
  per pixel against ``target = (255, 0, 0)``. A real flash is *one* weight: the median sits in the band
  vanilla's constant implies (0.2-0.4) or within 0.05 of the reference arm's weight, the p90-p10 spread is
  narrow, and the residual is at rounding distance. Two weights, or a large residual, is a shape that moved
  or a colour that changed for another reason;
* **the armour control** - vanilla restores the texture environment *before* the layers are drawn, so the
  armoured carrier in the same frame must not flash: the median weight inside the armour's region (taken
  from the same run's glint triple, a different effect in different frames, so the region cannot be
  circular) has to be ~0. A weight there is the combine state leaking into the layer pass - a renderer gap,
  not a fixture detail;
* **the outer-frame controls** - the same mask computed between the two outer frames is the noise floor:
  a scene that moves by itself between a and c cannot attribute the middle frame's movement to the flash.

The reference arm (``--pair <ref-on dir> <ref-off dir>``) is two single captures of the same scene with the
flash held on and off; its weight is computed with the same fit and reported as ``wRef``. It is a cross-run
pair, so it is only ever compared as a weight, never as pixels.
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

TARGET = (255, 0, 0)
DIFF = 8


def _require_pillow() -> None:
    if Image is None:
        raise SystemExit("hurt-flash-check needs Pillow (python3 -c 'import PIL')")


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


def find_capture(run_dir: str) -> str:
    """Finds a reference arm's single captured picture (the GL arm presents nothing, so it screenshots)."""
    patterns = [
        os.path.join(run_dir, "screenshots", "celtium-validation.png"),
        os.path.join(run_dir, "screenshots", "celtium-validation-world.png"),
        os.path.join(run_dir, "screenshots", "celtium-presented-validation-*-kept.png"),
        os.path.join(run_dir, "screenshots", "celtium-presented-*.png"),
    ]
    for pattern in patterns:
        found = sorted(glob.glob(pattern))
        if found:
            return found[0]
    raise SystemExit(f"no captured picture under {run_dir}")


def _percentile(sorted_values: list[float], q: float) -> float:
    if not sorted_values:
        return 0.0
    index = min(len(sorted_values) - 1, max(0, int(q * len(sorted_values))))
    return sorted_values[index]


def _median(values: list[float]) -> float:
    return _percentile(sorted(values), 0.5)


def _fit_pixel(pa, pb):
    """One pixel's flash weight and residual, or None when the pixel cannot carry a weight at all."""
    num = 0.0
    den = 0.0
    for channel in range(3):
        delta = TARGET[channel] - pa[channel]
        num += (pb[channel] - pa[channel]) * delta
        den += delta * delta
    if den <= 0.0:
        return None
    w = num / den
    residual = 0.0
    for channel in range(3):
        fitted = pa[channel] + (TARGET[channel] - pa[channel]) * w
        residual = max(residual, abs(pb[channel] - fitted))
    return w, residual


def _moved(pa, pb) -> bool:
    return max(abs(pa[0] - pb[0]), abs(pa[1] - pb[1]), abs(pa[2] - pb[2])) > DIFF


def _luminance(pixel) -> float:
    return (pixel[0] * 299 + pixel[1] * 587 + pixel[2] * 114) / 1000.0


def glint_region(glint_a, glint_b, glint_c, dilate: int = 8):
    """The armoured carrier's region, from the glint triple's own mask: a different effect in different
    frames from the flash triple, which is what keeps the flash checker's armour control from being circular."""
    if glint_a is None or glint_b is None or glint_c is None:
        return None
    width, height = glint_b.size
    a = glint_a.tobytes()
    b = glint_b.tobytes()
    c = glint_c.tobytes()
    xs: list[int] = []
    ys: list[int] = []
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            pa = (a[i], a[i + 1], a[i + 2])
            pb = (b[i], b[i + 1], b[i + 2])
            pc = (c[i], c[i + 1], c[i + 2])
            if _luminance(pb) - max(_luminance(pa), _luminance(pc)) > 6:
                xs.append(x)
                ys.append(y)
    if not xs:
        return None
    return (max(0, min(xs) - dilate), max(0, min(ys) - dilate),
            min(width - 1, max(xs) + dilate), min(height - 1, max(ys) + dilate))


def analyse(off_a, on, off_c, region_g=None, band_fraction: float = 0.5) -> dict:
    """Fits the flash weight on the middle frame and reads the controls that make the fit mean something.

    The analysis band is the left ``band_fraction`` of the frame: carrier F stands left of centre and
    carrier G right of it (fixture constants), so the band separates the two without any projection code.
    """
    if off_a.size != on.size or off_a.size != off_c.size:
        raise SystemExit(f"the three pictures are different sizes: {off_a.size}, {on.size}, {off_c.size}")
    width, height = on.size
    band_x = int(width * band_fraction)
    a = off_a.tobytes()
    b = on.tobytes()
    c = off_c.tobytes()
    weights: list[float] = []
    residuals: list[float] = []
    mask_box = None
    control = 0
    noise = 0
    outside = 0
    g_weights: list[float] = []
    mask_in_g = 0
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            pa = (a[i], a[i + 1], a[i + 2])
            pb = (b[i], b[i + 1], b[i + 2])
            pc = (c[i], c[i + 1], c[i + 2])
            in_band = x < band_x
            in_g = region_g is not None and region_g[0] <= x <= region_g[2] and region_g[1] <= y <= region_g[3]
            if in_g:
                fit = _fit_pixel(pa, pb)
                if fit is not None:
                    g_weights.append(fit[0])
            if _moved(pa, pc):
                if in_band:
                    noise += 1
                    control += 1
                if not in_band and not in_g:
                    outside += 1
            if in_band and _moved(pa, pb):
                fit = _fit_pixel(pa, pb)
                if fit is not None:
                    weights.append(fit[0])
                    residuals.append(fit[1])
                    if in_g:
                        mask_in_g += 1
                    if mask_box is None:
                        mask_box = [x, y, x, y]
                    else:
                        mask_box[0] = min(mask_box[0], x)
                        mask_box[1] = min(mask_box[1], y)
                        mask_box[2] = max(mask_box[2], x)
                        mask_box[3] = max(mask_box[3], y)
    weights_sorted = sorted(weights)
    residuals_sorted = sorted(residuals)
    return {
        "width": width,
        "height": height,
        "bandX": band_x,
        "mask": len(weights),
        "maskBox": mask_box,
        "medianW": _median(weights),
        "p10W": _percentile(weights_sorted, 0.1),
        "p90W": _percentile(weights_sorted, 0.9),
        "p90Residual": _percentile(residuals_sorted, 0.9),
        "control": control,
        "noise": noise,
        "outsideDiff": outside,
        "regionG": region_g,
        "maskInG": mask_in_g,
        "gMedianW": _median(g_weights),
        "gPixels": len(g_weights),
    }


def analyse_pair(ref_on, ref_off, dilate: int = 12) -> dict:
    """The reference arm's weight: the same fit over the pair's own changed mask, plus the mask's region.

    The region (the mask's bounding box, dilated) is returned so a candidate run's mask can be required to
    sit inside the reference's - a region derived from the reference, never from the candidate it judges.
    """
    if ref_on.size != ref_off.size:
        raise SystemExit(f"the reference pair are different sizes: {ref_on.size}, {ref_off.size}")
    width, height = ref_on.size
    on = ref_on.tobytes()
    off = ref_off.tobytes()
    weights: list[float] = []
    box = None
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            pa = (off[i], off[i + 1], off[i + 2])
            pb = (on[i], on[i + 1], on[i + 2])
            if _moved(pa, pb):
                fit = _fit_pixel(pa, pb)
                if fit is not None:
                    weights.append(fit[0])
                    if box is None:
                        box = [x, y, x, y]
                    else:
                        box[0] = min(box[0], x)
                        box[1] = min(box[1], y)
                        box[2] = max(box[2], x)
                        box[3] = max(box[3], y)
    region = None
    if box is not None:
        region = (max(0, box[0] - dilate), max(0, box[1] - dilate),
                  min(width - 1, box[2] + dilate), min(height - 1, box[3] + dilate))
    return {"mask": len(weights), "medianW": _median(weights), "regionF": region}


def verdict(result: dict, min_mask: int = 1000, noise_floor: int = 2000,
            w_ref: float | None = None, region_f=None) -> list[str]:
    """The problems the triple has, or an empty list when one flash weight explains the right region only.

    ``min_mask`` (F1) and ``noise_floor`` (N1) are provisional defaults: the D15 leg registers the measured
    values from its first run (the mask a 4 m carrier actually fills, the outer-frame movement of this
    scene) and passes them explicitly, the way the glow checker's floors were registered from its leg.
    """
    problems: list[str] = []
    mask = result["mask"]
    if mask < min_mask:
        problems.append(f"only {mask} pixels in the flash carrier's band moved toward the middle frame "
                        f"(need {min_mask}): the flash did not reach the picture")
        return problems
    if mask > 3 * min_mask:
        problems.append(f"{mask} pixels moved in the band (limit {3 * min_mask} = 3x the floor): the mask is "
                        "the scene changing, not one mob's flash")
    in_band = result["medianW"] >= 0.2 and result["medianW"] <= 0.4
    near_ref = w_ref is not None and abs(result["medianW"] - w_ref) <= 0.05
    if not in_band and not near_ref:
        problems.append(f"the median flash weight is {result['medianW']:.3f}: neither in vanilla's band "
                        f"0.2-0.4 nor within 0.05 of the reference weight {w_ref}")
    if result["p90W"] - result["p10W"] > 0.15:
        problems.append(f"the weight spread p90-p10 is {result['p90W'] - result['p10W']:.3f} (limit 0.15): "
                        "no single weight explains the difference, so it is not one flash")
    if result["p90Residual"] > 8:
        problems.append(f"the fit's p90 residual is {result['p90Residual']:.1f} (limit 8): the pixels moved "
                        "in shape, not just in colour toward red")
    if result["regionG"] is not None:
        if result["maskInG"] > 0:
            problems.append(f"{result['maskInG']} flash-mask pixels fall inside the armour carrier's region: "
                            "the two carriers' regions overlap, so the armour control cannot be read")
        if result["gPixels"] > 0 and result["gMedianW"] > 0.05:
            problems.append(f"the median weight inside the armour carrier's region is "
                            f"{result['gMedianW']:.3f} (limit 0.05): the armour flashed too, which vanilla "
                            "does not do - the combine state leaked into the layer pass")
    if region_f is not None and result["maskBox"] is not None:
        box = result["maskBox"]
        if box[0] < region_f[0] or box[1] < region_f[1] or box[2] > region_f[2] or box[3] > region_f[3]:
            problems.append(f"the flash mask's box {box} is not inside the reference region {region_f}: "
                            "the flash is somewhere the reference does not have it")
    if result["control"] >= 0.1 * min_mask:
        problems.append(f"the outer frames differ by {result['control']} pixels in the band (limit "
                        f"{0.1 * min_mask:.0f}): the same criterion fires without the flash")
    if result["noise"] > noise_floor:
        problems.append(f"{result['noise']} pixels differ between the two outer frames in the band (limit "
                        f"{noise_floor}): the scene is not static enough for this triple")
    if result["outsideDiff"] > noise_floor:
        problems.append(f"{result['outsideDiff']} pixels outside both carriers' regions differ between the "
                        f"outer frames (limit {noise_floor}): the background moved, so the triple is not "
                        "measuring the carriers")
    return problems


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--run", help="the leg's run directory (.gv-verify/run-<leg>)")
    parser.add_argument("--pair", nargs=2, metavar=("REF_ON", "REF_OFF"),
                        help="two reference run directories: flash held on, flash held off")
    parser.add_argument("--json", action="store_true", help="print the numbers as JSON")
    parser.add_argument("--min-mask", type=int, default=1000)
    parser.add_argument("--noise-floor", type=int, default=2000)
    args = parser.parse_args(argv)

    if args.pair:
        ref = analyse_pair(load_rgb(find_capture(args.pair[0])), load_rgb(find_capture(args.pair[1])))
        if args.run is None:
            printable = {"wRef": ref["medianW"], "refMask": ref["mask"], "regionF": ref["regionF"]}
            if args.json:
                print(json.dumps(printable, indent=1))
            else:
                print(f"wRef={ref['medianW']:.3f} refMask={ref['mask']} regionF={ref['regionF']}")
            return 0
    else:
        ref = None

    if args.run is None:
        parser.error("--run is required (with or without --pair)")

    a = load_rgb(find_presented(args.run, "hurt-a"))
    b = load_rgb(find_presented(args.run, "hurt-b"))
    c = load_rgb(find_presented(args.run, "hurt-c"))
    region_g = None
    try:
        region_g = glint_region(load_rgb(find_presented(args.run, "glint-a")),
                                load_rgb(find_presented(args.run, "glint-b")),
                                load_rgb(find_presented(args.run, "glint-c")))
    except SystemExit:
        region_g = None
    result = analyse(a, b, c, region_g=region_g)
    w_ref = ref["medianW"] if ref else None
    region_f = ref["regionF"] if ref else None
    problems = verdict(result, min_mask=args.min_mask, noise_floor=args.noise_floor,
                       w_ref=w_ref, region_f=region_f)
    if region_g is None:
        problems.append("no glint triple in this run: the armour control (C1-7) cannot be read, so the "
                        "flash's region separation is unproven")
    printable = {k: v for k, v in result.items()}
    printable["wRef"] = w_ref
    printable["problems"] = problems
    printable["verdict"] = "PASS" if not problems else "FAIL"
    if args.json:
        print(json.dumps(printable, indent=1))
    else:
        print(f"frame {result['width']}x{result['height']} band<{result['bandX']}")
        print(f"mask={result['mask']} medianW={result['medianW']:.3f} p10={result['p10W']:.3f} "
              f"p90={result['p90W']:.3f} p90Residual={result['p90Residual']:.1f}")
        print(f"control={result['control']} noise={result['noise']} outsideDiff={result['outsideDiff']}")
        print(f"armour gMedianW={result['gMedianW']:.3f} over {result['gPixels']} px, "
              f"maskInG={result['maskInG']}")
        if w_ref is not None:
            print(f"wRef={w_ref:.3f}")
        for problem in problems:
            print(f"PROBLEM: {problem}")
        print("verdict=" + ("PASS" if not problems else "FAIL"))
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
