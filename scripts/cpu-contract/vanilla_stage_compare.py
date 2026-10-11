#!/usr/bin/env python3
"""Compare a GL-less presented frame against the GL bridge picture, stage by stage.

The vanilla completion condition is a picture comparison against the bridge, and "the frame is
94% different" is not a usable statement: it is one number covering the sky, the terrain, the
HUD and the GUI at once, and it has already been misread once in this project. This tool reports
each stage separately so a difference can be attributed to a stage rather than to "the frame".

Usage:
    vanilla_stage_compare.py <gl-less.png> <bridge.png> [--label NAME] [--write-crops DIR]

Both images must be 1280x720. The GL-less picture is the presented dump
(-Dceltium.vulkan.presentReadback=true -Dceltium.validation.dumpPresentedImage=true); the bridge
picture is the game's own screenshot, which is that configuration's picture of the window. Both
legs must be at the same scene, pose and tick - the fixture's camera overrides now apply before
the frame (2849c1ec8), and the tick-scheduled scene steps are "at or after" (890817893), so this
is achievable; a comparison across different poses is not a comparison.
"""
import argparse
import os
import sys

try:
    from PIL import Image, ImageChops
except ImportError:
    print("Pillow is required: python3 -m pip install pillow")
    sys.exit(2)

# Regions are in 1280x720 pixels. They are named for what a reader is looking at, and the
# coordinates are the ones the workers have been quoting so the numbers line up with the crops.
STAGES = {
    "sky band (top)": (0, 0, 1280, 120),
    "horizon band": (0, 120, 1280, 300),
    "terrain centre": (320, 300, 960, 640),
    "bottom-left corner": (0, 560, 320, 720),
    "bottom-right corner": (960, 560, 1280, 720),
    "hud strip (bottom)": (0, 640, 1280, 720),
    "chat block": (0, 320, 660, 620),
    "hotbar strip": (490, 660, 790, 720),
    "tooltip region": (660, 315, 820, 355),
    "whole frame": (0, 0, 1280, 720),
}


def region_stats(diff, box):
    region = diff.crop(box)
    pixels = list(region.getdata())
    total = len(pixels)
    changed = 0
    acc = 0
    for pixel in pixels:
        value = max(pixel)
        acc += value
        if value > 8:
            changed += 1
    return acc / float(total), changed, total


def mean_colour(image, box):
    region = image.crop(box)
    pixels = list(region.getdata())
    n = float(len(pixels))
    r = sum(p[0] for p in pixels) / n
    g = sum(p[1] for p in pixels) / n
    b = sum(p[2] for p in pixels) / n
    return r, g, b


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("gl_less")
    parser.add_argument("bridge")
    parser.add_argument("--label", default="")
    parser.add_argument("--write-crops", default="")
    args = parser.parse_args()

    a = Image.open(args.gl_less).convert("RGB")
    b = Image.open(args.bridge).convert("RGB")
    if a.size != b.size:
        print("sizes differ: %s vs %s - the two legs must be the same resolution" % (a.size, b.size))
        return 1

    diff = ImageChops.difference(a, b)
    print("gl-less: %s" % args.gl_less)
    print("bridge : %s" % args.bridge)
    if args.label:
        print("label  : %s" % args.label)
    print()
    print("%-22s %9s %13s %13s %-22s %-22s" % (
        "stage", "mean", ">8 fraction", "pixels>8", "gl-less mean rgb", "bridge mean rgb"))
    for name, box in STAGES.items():
        mean, changed, total = region_stats(diff, box)
        gl = mean_colour(a, box)
        br = mean_colour(b, box)
        print("%-22s %9.3f %12.4f%% %13d (%-6.1f,%-6.1f,%-6.1f) (%-6.1f,%-6.1f,%-6.1f)" % (
            name, mean, 100.0 * changed / total, changed, gl[0], gl[1], gl[2], br[0], br[1], br[2]))

    if args.write_crops:
        os.makedirs(args.write_crops, exist_ok=True)
        for name, box in STAGES.items():
            slug = name.split(" (")[0].replace(" ", "-")
            a.crop(box).save(os.path.join(args.write_crops, "glless-" + slug + ".png"))
            b.crop(box).save(os.path.join(args.write_crops, "bridge-" + slug + ".png"))
        print()
        print("crops written to %s" % args.write_crops)
    return 0


if __name__ == "__main__":
    sys.exit(main())
