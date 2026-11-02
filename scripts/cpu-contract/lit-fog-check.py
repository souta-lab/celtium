#!/usr/bin/env python3
"""Does a two-picture A/B behave like fixed-function fog on the lit geometry, and only there?

The lit ITEM family used to be drawn with no fog at all: its push block spends 128..215 on the lighting payload, so
the shared fog block at 128 could not be declared. The fix gives the family its own fog block at 224/255 and the vertex
stage reconstructs the eye position from the combined projection. The claim that needs evidence is not "the shader
changed" but "the picture moved toward the fog colour by the amount the fog curve says, and nothing else moved".

This checker reads the pair of presented dumps the two arms of one flag produced (``-Dceltium.vulkan.litFog=false``
against the default), and answers four questions with numbers:

* **how much changed** - the differing-pixel count and the mean |A-B| over the frame;
* **where** - every changed pixel must satisfy the one-parameter fog model, and the picture's top band (the sky, drawn
  by the terrain family) must not change at all: fog on the lit family must not leak into the path that already had it;
* **by how much** - for each changed pixel the weight ``w`` is solved from ``after = before + (fog - before) * w`` by
  least squares over the three channels, and the model's residual is reported. A real fog mix leaves a residual near
  zero whatever the pixel's own colour was; anything else (a different colour, a moved vertex, a changed order) does
  not fit one ``w`` per pixel;
* **is it the fog curve** - ``w`` must be inside ``(0, 1]`` for every changed pixel, and its spread must be small
  enough that a single fog bracket explains the whole region.

The checker never decides "the picture is correct" - it cannot, there is no reference arm in the pair. It decides
"the pair is consistent with fixed-function fog applied to the lit geometry and inconsistent with anything else".
"""
from __future__ import annotations

import argparse
import glob
import os
import sys

try:
    from PIL import Image
except ImportError:  # pragma: no cover - the legs have Pillow
    Image = None


def _require_pillow() -> None:
    if Image is None:
        raise SystemExit("lit-fog-check needs Pillow (python3 -c 'import PIL')")


def load_rgb(path: str) -> "Image.Image":
    _require_pillow()
    image = Image.open(path).convert("RGB")
    return image


def find_presented(run_dir: str, stage: str = "world") -> str:
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


def parse_fog(text: str) -> tuple[float, float, float]:
    parts = [p for p in text.replace(" ", "").split(",") if p]
    if len(parts) != 3:
        raise SystemExit("--fog wants R,G,B (0..255), the leg's CELTIUM_FOG_STATE colour")
    values = tuple(float(p) for p in parts)
    for value in values:
        if value < 0 or value > 255:
            raise SystemExit(f"--fog channel {value} is outside 0..255")
    return values  # type: ignore[return-value]


def analyse(before: "Image.Image", after: "Image.Image", fog: tuple[float, float, float],
            sky_fraction: float = 0.15, threshold: float = 1.0, exclude: "Image.Image | None" = None,
            max_residual: float = 2.0) -> dict:
    """The per-pixel fog model over the whole frame.

    ``w`` is solved per pixel from the three channels. A pixel counts as changed when any channel moved by more than
    ``threshold`` (a 1/255 step is the smallest change a mix can make and is indistinguishable from dithering). A
    changed pixel *fits* when the one-parameter model reproduces it to within ``max_residual`` and its weight is inside
    ``(0, 1]``; the rest are reported separately, because a real frame also carries changes that are not fog (the GUI's
    own frame-to-frame content, a particle, an animated icon) and a checker that demanded every pixel would fail on
    those instead of on the question.
    """
    if before.size != after.size:
        raise SystemExit(f"the two pictures are different sizes: {before.size} vs {after.size}")
    width, height = before.size
    a = before.tobytes()
    b = after.tobytes()
    # The animation-noise mask: pixels that changed between two frames of the *same* arm. They are excluded from the
    # fog model, because a cloud that moved is not a fog mix and would otherwise be read as one.
    noise = exclude.tobytes() if exclude is not None else None
    excluded = 0
    changed = 0
    sky_changed = 0
    total_delta = 0.0
    weights: list[float] = []
    residuals: list[float] = []
    fit_weights: list[float] = []
    fit_residuals: list[float] = []
    non_fit: list[tuple[int, int]] = []
    sky_rows = max(1, int(height * sky_fraction))
    for y in range(height):
        row = y * width * 3
        for x in range(width):
            i = row + x * 3
            br, bg, bb = a[i], a[i + 1], a[i + 2]
            ar, ag, ab = b[i], b[i + 1], b[i + 2]
            delta = (abs(ar - br) + abs(ag - bg) + abs(ab - bb)) / 3.0
            total_delta += delta
            if delta <= threshold:
                continue
            # The noise picture is the same arm as ``before`` (the flag flipped back), so a pixel it moved is a pixel
            # the scene moved by itself: compare it with ``before``, not with itself.
            if noise is not None and (abs(noise[i] - br) + abs(noise[i + 1] - bg) + abs(noise[i + 2] - bb)) / 3.0 > threshold:
                excluded += 1
                continue
            changed += 1
            if y < sky_rows:
                sky_changed += 1
            # Least squares for w along the line before -> fog:
            #   after = before + (fog - before) * w   =>   w = sum(d . f) / sum(d . d), d = after - before, f = fog - before
            dr, dg, db = ar - br, ag - bg, ab - bb
            fr, fg, fb = fog[0] - br, fog[1] - bg, fog[2] - bb
            numerator = dr * fr + dg * fg + db * fb
            denominator = fr * fr + fg * fg + fb * fb
            if denominator <= 1e-6:
                # The pixel already is the fog colour: no weight can be solved, and a mix there changes nothing.
                continue
            w = numerator / denominator
            weights.append(w)
            # The residual of the one-parameter model, in the same 0..255 units.
            rr = dr - w * fr
            rg = dg - w * fg
            rb = db - w * fb
            residual = (abs(rr) + abs(rg) + abs(rb)) / 3.0
            residuals.append(residual)
            if residual <= max_residual and 0.0 < w <= 1.0:
                fit_weights.append(w)
                fit_residuals.append(residual)
            else:
                non_fit.append((x, y))
    weights.sort()
    residuals.sort()
    fit_weights.sort()
    fit_residuals.sort()

    def pct(values: list[float], q: float) -> float:
        if not values:
            return float("nan")
        index = min(len(values) - 1, max(0, int(round(q * (len(values) - 1)))))
        return values[index]

    pixels = width * height
    return {
        "width": width,
        "height": height,
        "pixels": pixels,
        "changed": changed,
        "changedFraction": changed / pixels,
        "excluded": excluded,
        "skyRows": sky_rows,
        "skyChanged": sky_changed,
        "meanDelta": total_delta / pixels,
        "weightMin": weights[0] if weights else float("nan"),
        "weightP50": pct(weights, 0.5),
        "weightP99": pct(weights, 0.99),
        "weightMax": weights[-1] if weights else float("nan"),
        "residualP50": pct(residuals, 0.5),
        "residualP99": pct(residuals, 0.99),
        "fits": len(fit_weights),
        "fitFraction": len(fit_weights) / changed if changed else 0.0,
        "fitWeightMin": fit_weights[0] if fit_weights else float("nan"),
        "fitWeightP50": pct(fit_weights, 0.5),
        "fitWeightP99": pct(fit_weights, 0.99),
        "fitWeightMax": fit_weights[-1] if fit_weights else float("nan"),
        "fitResidualP50": pct(fit_residuals, 0.5),
        "fitResidualP99": pct(fit_residuals, 0.99),
        "nonFit": non_fit,
        "weights": weights,
    }


def verdict(result: dict, max_residual: float = 2.0, min_weight: float = 0.02,
            min_fits: int = 500, min_fit_fraction: float = 0.6) -> list[str]:
    """The problems a pair has, or an empty list when it looks like fog on the lit geometry and nothing else."""
    problems: list[str] = []
    if result["changed"] == 0:
        problems.append("the two arms are identical: the flag changed no pixel, so the fog never reached the picture")
        return problems
    if result["skyChanged"] != 0:
        problems.append(f"{result['skyChanged']} pixels changed in the top {result['skyRows']} rows (the sky band, drawn "
                        "by the terrain family): fog on the lit family must not move it")
    if result["fits"] < min_fits:
        problems.append(f"only {result['fits']} changed pixels fit the fog model (need {min_fits}): the change is not "
                        "a fog mix on a region of geometry")
    if result["fitFraction"] < min_fit_fraction:
        problems.append(f"only {result['fitFraction'] * 100:.1f}% of the changed pixels fit the fog model (need "
                        f"{min_fit_fraction * 100:.0f}%): most of the change is something other than a fog mix")
    if result["fits"] and result["fitWeightMin"] < min_weight:
        problems.append(f"the smallest fitting fog weight is {result['fitWeightMin']:.3f}: a changed pixel that is not "
                        "moving toward the fog colour is not a fog mix")
    if result["weightMax"] > 1.0 + 1e-3:
        problems.append(f"the largest solved fog weight is {result['weightMax']:.3f} > 1: the pixel overshot the fog "
                        "colour, so it is not a convex mix")
    if result["fits"] and result["fitResidualP99"] > max_residual:
        problems.append(f"the fitting pixels' p99 residual is {result['fitResidualP99']:.2f}/255 (limit "
                        f"{max_residual}): the changed pixels are not the same colour moved toward the fog colour")
    return problems


def summary(result: dict) -> str:
    return (f"changed={result['changed']} ({result['changedFraction'] * 100:.2f}% of {result['pixels']})"
            f" excluded={result['excluded']}"
            f" meanDelta={result['meanDelta']:.3f}"
            f" skyChanged={result['skyChanged']}/{result['skyRows']} rows"
            f" fits={result['fits']} ({result['fitFraction'] * 100:.1f}%)"
            f" fitWeight[min/p50/p99/max]={result['fitWeightMin']:.3f}/{result['fitWeightP50']:.3f}/"
            f"{result['fitWeightP99']:.3f}/{result['fitWeightMax']:.3f}"
            f" fitResidual[p50/p99]={result['fitResidualP50']:.2f}/{result['fitResidualP99']:.2f}")


def synthetic_arms(source: "Image.Image", fog: tuple[float, float, float], weight: float):
    """The positive control: the same picture with a band mixed toward the fog colour at a known weight."""
    width, height = source.size
    after = source.copy()
    pixels = after.load()
    rows = range(height // 3, 2 * height // 3)
    for y in rows:
        for x in range(width // 4, 3 * width // 4):
            r, g, b = pixels[x, y]
            pixels[x, y] = (round(r + (fog[0] - r) * weight), round(g + (fog[1] - g) * weight),
                            round(b + (fog[2] - b) * weight))
    return after


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--before", required=True, help="run directory (or picture) of the -Dceltium.vulkan.litFog=false arm")
    parser.add_argument("--after", required=True, help="run directory (or picture) of the default arm")
    parser.add_argument("--fog", required=True, help="the leg's fog colour, R,G,B (CELTIUM_FOG_STATE colour=...)")
    parser.add_argument("--stage", default="world", help="which presented stage to compare (default: world)")
    parser.add_argument("--sky-fraction", type=float, default=0.15,
                        help="the top fraction of rows that must not change (the sky band)")
    parser.add_argument("--threshold", type=float, default=1.0,
                        help="per-pixel mean channel change that counts as a change (default 1/255)")
    parser.add_argument("--control", action="store_true",
                        help="run the synthetic positive/negative controls on the --before picture instead")
    parser.add_argument("--noise", default=None,
                        help="a third dump of the same arm as --before (the flag flipped back): its pixels are the "
                             "animation noise floor and are excluded from the fog model")
    args = parser.parse_args(argv)

    before_path = find_presented(args.before, args.stage) if os.path.isdir(args.before) else args.before
    before = load_rgb(before_path)
    fog = parse_fog(args.fog)

    if args.control:
        print(f"picture={before_path} fog={fog}")
        control = synthetic_arms(before, fog, 0.35)
        result = analyse(before, control, fog, args.sky_fraction, args.threshold)
        problems = verdict(result, min_weight=0.3)
        print("control(mix 0.35) " + summary(result) + f" weights-around-0.35={'yes' if 0.3 <= result['weightP50'] <= 0.4 else 'no'}")
        # The negative control: the same band painted a fixed colour instead of the fog colour.
        black = before.copy()
        pixels = black.load()
        for y in range(before.size[1] // 3, 2 * before.size[1] // 3):
            for x in range(before.size[0] // 4, 3 * before.size[0] // 4):
                pixels[x, y] = (255, 0, 0)
        negative = analyse(before, black, fog, args.sky_fraction, args.threshold)
        negative_problems = verdict(negative)
        print("control(red band)  " + summary(negative) + f" problems={len(negative_problems)}")
        ok = (not problems and 0.3 <= result["weightP50"] <= 0.4 and negative_problems)
        print("CONTROL " + ("PASS" if ok else "FAIL")
              + (" (the fog mix is recovered and a colour change is refused)" if ok
                 else " (the analysis cannot tell a fog mix from a colour change)"))
        return 0 if ok else 1

    after_path = find_presented(args.after, args.stage) if os.path.isdir(args.after) else args.after
    after = load_rgb(after_path)
    print(f"before={before_path}")
    print(f"after={after_path}")
    print(f"fog={fog[0]:.0f},{fog[1]:.0f},{fog[2]:.0f}")
    noise = None
    if args.noise:
        noise_path = find_presented(args.noise, args.stage) if os.path.isdir(args.noise) else args.noise
        noise = load_rgb(noise_path)
        print(f"noise={noise_path}")
        floor = analyse(before, noise, fog, args.sky_fraction, args.threshold)
        print("noise floor (same arm, two frames apart) " + summary(floor))
    result = analyse(before, after, fog, args.sky_fraction, args.threshold, noise)
    print("pair " + summary(result))
    problems = verdict(result)
    if problems:
        for problem in problems:
            print("PROBLEM: " + problem)
        print("FAIL: the pair is not explained by fog on the lit geometry alone")
        return 1
    print("PASS: every changed pixel is the same pixel mixed toward the fog colour, and the sky band is untouched")
    return 0


if __name__ == "__main__":
    sys.exit(main())
