"""Measure what a compositor capture actually holds, so a picture can be bound to a presented frame.

`compositor_capture.py` writes numbered PNGs plus, for each one, the wall and monotonic clock at the shutter,
grim's exit status, the PNG hash and the compositor's window tree at that moment. This reads that record back
and answers the two questions the capture exists for:

  * Is the captured rectangle the game's own frame? A frame the renderer wrote has structure (many colours,
    many distinct rows); an output with no mapped window, or a window that never presented, is one flat
    colour. A deliberately uniform marker is the strongest case: with `-Dceltium.vulkan.presentMarker=true`
    the swapchain image is cleared to magenta (1,0,1), so any capture that is uniformly magenta can only have
    come from that present path.
  * Which presented frame is it? The renderer can dump the presented image it just handed over, numbered by
    its own presented-frame counter (`-Dceltium.vulkan.frameDumpDir`, files `frame-<presentedFrame>.png`). This
    compares each numbered capture against those numbered frames by pixel content and by time, and reports the
    pairing it found rather than asserting one.

The comparison is exact per pixel at the same extent, and reports the fraction of differing pixels and the
largest channel difference, so an "almost equal" pair cannot be reported as equal.
"""

import argparse
import json
from pathlib import Path

import numpy as np
from PIL import Image


def measure(path):
    """The facts about one PNG that decide what it is: extent, colour, uniformity and hash."""
    image = Image.open(path).convert("RGB")
    pixels = np.asarray(image, dtype=np.uint8)
    flat = pixels.reshape(-1, 3)
    unique = np.unique(flat, axis=0)
    mean = flat.mean(axis=0)
    magenta = flat[(flat[:, 0] > 200) & (flat[:, 1] < 60) & (flat[:, 2] > 200)]
    return {
        "file": path.name,
        "width": int(pixels.shape[1]),
        "height": int(pixels.shape[0]),
        "meanRGB": [round(float(value), 3) for value in mean],
        "distinctColours": int(unique.shape[0]),
        "distinctRows": int(np.unique(pixels, axis=1).shape[0]),
        "uniform": bool(unique.shape[0] == 1),
        "uniformRGB": [int(v) for v in unique[0]] if unique.shape[0] == 1 else None,
        "magentaFraction": round(float(magenta.shape[0] / flat.shape[0]), 6),
    }


def compare(left, right):
    """How two images at the same extent differ, exactly: count and worst channel."""
    a = np.asarray(Image.open(left).convert("RGB"), dtype=np.int16)
    b = np.asarray(Image.open(right).convert("RGB"), dtype=np.int16)
    if a.shape != b.shape:
        return {"comparable": False, "reason": f"extent differs: {a.shape} vs {b.shape}"}
    difference = np.abs(a - b).max(axis=2)
    return {
        "comparable": True,
        "differentPixels": int((difference > 0).sum()),
        "totalPixels": int(difference.size),
        "differentFraction": round(float((difference > 0).sum() / difference.size), 6),
        "maxChannelDifference": int(difference.max()),
        "identical": bool((difference == 0).all()),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--captures", type=Path, required=True,
                        help="The --capture-dir compositor_capture.py was given (holds captures.json)")
    parser.add_argument("--frames", type=Path, default=None,
                        help="The renderer's frameDumpDir, holding frame-<presentedFrame>.png")
    parser.add_argument("--compare-with-frame", default=None,
                        help="A capture file name to compare against the nearest numbered frame dump")
    parser.add_argument("--output", type=Path, default=None, help="Where the report JSON goes")
    args = parser.parse_args()

    records = json.loads((args.captures / "captures.json").read_text())["records"]
    measured = []
    for record in records:
        entry = dict(record)
        image = args.captures / record["image"] if record.get("image") else None
        if image is not None and image.is_file():
            entry["measured"] = measure(image)
        else:
            entry["measured"] = None
        measured.append(entry)

    mapped = [entry for entry in measured if entry.get("windows")]
    unmapped = [entry for entry in measured if not entry.get("windows")]
    content = [entry for entry in measured
               if entry["measured"] and not entry["measured"]["uniform"]]
    magenta = [entry for entry in measured
               if entry["measured"] and entry["measured"]["magentaFraction"] > 0.999]
    summary = {
        "captures": len(measured),
        "capturesWithMappedWindows": len(mapped),
        "capturesWithoutMappedWindows": len(unmapped),
        "capturesWithStructure": len(content),
        "capturesUniformlyMagenta": len(magenta),
        "firstMappedCaptureWall": mapped[0]["shutterWall"] if mapped else None,
        "firstMagentaCaptureWall": magenta[0]["shutterWall"] if magenta else None,
        "distinctImageHashes": len({entry["pngSha256"] for entry in measured if entry.get("pngSha256")}),
        "firstCaptureWall": measured[0]["shutterWall"] if measured else None,
        "lastCaptureWall": measured[-1]["shutterWall"] if measured else None,
    }

    frames = []
    if args.frames and args.frames.is_dir():
        for path in sorted(args.frames.glob("frame-*.png"), key=lambda p: p.stat().st_mtime):
            entry = measure(path)
            entry["presentedFrame"] = int(path.stem.split("-", 1)[1])
            entry["mtimeWall"] = path.stat().st_mtime
            frames.append(entry)

    binding = None
    if args.compare_with_frame and frames:
        capture = args.captures / args.compare_with_frame
        record = next((entry for entry in measured if entry.get("image") == args.compare_with_frame), None)
        if capture.is_file() and record is not None:
            before = [frame for frame in frames if frame["mtimeWall"] <= record["shutterWallNs"] / 1e9]
            nearest = (before or frames)[-1]
            binding = {
                "capture": args.compare_with_frame,
                "captureShutterWall": record["shutterWall"],
                "nearestPresentedFrame": nearest["presentedFrame"],
                "nearestFrameFile": nearest["file"],
                "nearestFrameWrittenWall": nearest["mtimeWall"],
                "comparison": compare(capture, args.frames / nearest["file"]),
                "captureMeasured": record["measured"],
                "frameMeasured": nearest,
            }

    report = {"summary": summary, "records": measured, "frames": frames, "binding": binding}
    text = json.dumps(report, indent=2) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8")
    print(json.dumps({"summary": summary, "frames": [{k: f[k] for k in
          ("file", "presentedFrame", "meanRGB", "distinctColours", "uniform", "uniformRGB", "magentaFraction")}
          for f in frames], "binding": binding and {k: binding[k] for k in
          ("capture", "captureShutterWall", "nearestPresentedFrame", "nearestFrameFile", "comparison")}}, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
