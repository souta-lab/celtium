#!/usr/bin/env python3
"""The pack menu's top band: do the menu floors separate drawn art from the two measured defects?

Owner: **menu fidelity**. Unit: "the title screen's background/banner is drawn".

## The contract this checks

Legs that ask for the title screen's presented frame (`-Dceltium.validation.dumpPresentedMenuTick`) are judged by
`ValidationClient#celtium$gateMenuStage` on one band of the 1280x720 picture: the top strip where the pack's
background and banner sit, x in [0.03, 0.97], y in [0.05, 0.31]. Two numbers are measured there - mean luma, and the
share of pixels whose luma is above the black threshold (8) - and the floors are:

    mean luma >= 20.0 and non-black share >= 0.30

The floors are only worth anything if they separate the frames where the art drew from the frames where it did not.
This check measures four committed pictures with the same rule:

* `menu-frame-art-2026-11-02.png` - AMD RX 6400, r14e, pack resources carried, the colour-cache fix. Working.
* `menu-region-intel-2026-11-02.png` - Intel UHD 630, r16d, same fix (kept copy of presented frame 100; identity
  imageHash -210821403, source presented-dump, 1280x720). Working, second vendor.
* `menu-frame-black-colour-2026-11-02.png` - AMD, r14c/d, before the fix: the background and banner quad reached the
  frame and were drawn black. The defect the gate exists for.
* `menu-frame-no-pack-2026-11-02.png` - AMD, r14b, pack resources not carried: the missing-texture checkerboard.
  The other way the menu's art can be absent.

It also reads the four constants and two floors out of `ValidationClient.java` and fails if they are not the values
this check uses, so the committed pictures and the gate cannot drift apart silently.

Runs without a GPU. `python3 scripts/cpu-contract/menu-region-floor-check.py`.
"""
import pathlib
import re
import sys

from PIL import Image

ROOT = pathlib.Path(__file__).resolve().parents[2]
EVIDENCE = ROOT / "docs" / "evidence"
CLIENT = ROOT / "validation" / "client" / "src" / "main" / "java" / "org" / "soutalab" / "celtium" / "validation" / "ValidationClient.java"

X0, Y0, X1, Y1 = 0.03, 0.05, 0.97, 0.31
MIN_MEAN_LUMA = 20.0
MIN_NON_BLACK = 0.30
BLACK_LUMA = 8

WORKING = [
    ("AMD r14e", "menu-frame-art-2026-11-02.png"),
    ("Intel r16d", "menu-region-intel-2026-11-02.png"),
]
DEFECTS = [
    ("pre-fix black art", "menu-frame-black-colour-2026-11-02.png"),
    ("no pack resources", "menu-frame-no-pack-2026-11-02.png"),
]


def band(path):
    image = Image.open(path).convert("RGB")
    width, height = image.size
    if (width, height) != (1280, 720):
        raise SystemExit(f"{path}: expected the calibrated 1280x720 picture, got {width}x{height}")
    x0, y0 = round(X0 * width), round(Y0 * height)
    x1, y1 = round(X1 * width), round(Y1 * height)
    pixels = list(image.crop((x0, y0, x1, y1)).getdata())
    total = 0
    non_black = 0
    for r, g, b in pixels:
        luma = (299 * r + 587 * g + 114 * b) // 1000
        total += luma
        if luma > BLACK_LUMA:
            non_black += 1
    return round(total / len(pixels), 3), round(non_black / len(pixels), 6), (x0, y0, x1, y1)


def java_constants():
    source = CLIENT.read_text(encoding="utf-8")
    wanted = {
        "MENU_TOP_X0": X0,
        "MENU_TOP_Y0": Y0,
        "MENU_TOP_X1": X1,
        "MENU_TOP_Y1": Y1,
        "MENU_TOP_MIN_MEAN_LUMA": MIN_MEAN_LUMA,
        "MENU_TOP_MIN_NON_BLACK": MIN_NON_BLACK,
    }
    found = {}
    for name, value in wanted.items():
        match = re.search(
            rf"private static final (?:double|int) {name} = ([0-9.]+)D?\s*;", source)
        if match is None:
            raise SystemExit(f"ValidationClient.java does not declare {name}")
        found[name] = float(match.group(1))
    mismatched = {name: (found[name], value) for name, value in wanted.items() if found[name] != value}
    if mismatched:
        raise SystemExit(f"the gate's constants differ from this check: {mismatched}")
    return found


def main():
    java_constants()
    print(f"menu top band x={X0}..{X1} y={Y0}..{Y1}; floors mean>={MIN_MEAN_LUMA} "
          f"nonBlack>={MIN_NON_BLACK} (black luma<={BLACK_LUMA})")
    failures = []
    for vendor, name in WORKING:
        path = EVIDENCE / name
        if not path.is_file():
            failures.append(f"{vendor}: missing evidence picture {path}")
            continue
        mean, non_black, rect = band(path)
        passed = mean >= MIN_MEAN_LUMA and non_black >= MIN_NON_BLACK
        print(f"  WORKING {vendor:12s} mean={mean:8.3f} nonBlack={non_black:8.6f} rect={rect} -> "
              f"{'PASSED' if passed else 'FAILED'}")
        if not passed:
            failures.append(f"{vendor}: a working capture ({mean}, {non_black}) falls below the floors")
    for label, name in DEFECTS:
        path = EVIDENCE / name
        if not path.is_file():
            failures.append(f"{label}: missing evidence picture {path}")
            continue
        mean, non_black, rect = band(path)
        caught = mean < MIN_MEAN_LUMA or non_black < MIN_NON_BLACK
        print(f"  DEFECT  {label:16s} mean={mean:8.3f} nonBlack={non_black:8.6f} rect={rect} -> "
              f"{'CAUGHT' if caught else 'MISSED'}")
        if not caught:
            failures.append(f"{label}: the defect ({mean}, {non_black}) passes the floors")
    if failures:
        print("\nFAILED:")
        for failure in failures:
            print("  " + failure)
        return 1
    print("\nOK: both GPUs' working menu frames clear the floors and both measured defects are below them.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
