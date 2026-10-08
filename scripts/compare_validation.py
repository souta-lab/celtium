#!/usr/bin/env python3
"""Compare two validation game directories and report renderer-visible differences.

This is the P2 acceptance check: the same validation fixture is run against the
pinned upstream Celeritas jar and against the Celtium jar on the same GPU, and
the two runs must agree on the framebuffer contract, the camera, the visible
section counts per render pass and the rendered scene itself.

The reports always come from ``logs/celtium-validation.json`` and the scene from
``screenshots/celtium-validation.png``. Both are produced by the fixture that
lives in ``validation/client``; nothing here re-renders or edits an image.
"""

import argparse
import json
from pathlib import Path
import sys

from PIL import Image, ImageChops

# Fields that must match exactly between the reference and the candidate run.
REQUIRED_EQUAL = (
    "status",
    "glRenderer",
    "seed",
    "renderDistance",
    "worldRendererClass",
)

# Framebuffer properties that must match; a different contract means the two
# runs did not render into the same kind of target and cannot be compared.
FRAMEBUFFER_EQUAL = (
    "width",
    "height",
    "colorInternalFormat",
    "colorRedBits",
    "colorGreenBits",
    "colorBlueBits",
    "depthInternalFormat",
    "depthBits",
    "stencilBits",
    "sampleCount",
)

# Floating point camera fields are compared with a tolerance instead of equality.
CAMERA_FIELDS = ("x", "y", "z", "yaw", "pitch")
CAMERA_TOLERANCE = 1.0e-9

# A pass line looks like "solid - 62 sections, 1.2 ms" or "solid - 62 sections, ? ms".
def pass_counts(debug_strings):
    counts = {}
    for line in debug_strings or []:
        if " sections," not in line:
            continue
        name, rest = line.split(" - ", 1)
        counts[name.strip()] = int(rest.split(" sections,", 1)[0].strip())
    return counts


def buffer_stats(debug_strings):
    """Parse "G: 3/6, I: 0/0 MiB (4 buffers)" into geometry/index usage and the arena count.

    Only the *used* memory and the buffer count are comparable: the allocated capacity depends on how many
    chunks happened to be uploaded together, so two runs of the same jar can legitimately differ there.
    """
    for line in debug_strings or []:
        if not line.startswith("G: "):
            continue
        try:
            geometry, rest = line[3:].split(", ", 1)
            index, rest = rest.split(" MiB ", 1)
            buffers = rest.strip().lstrip("(").split(" ", 1)[0]
            geometry_used, geometry_allocated = (int(value) for value in geometry.split("/"))
            index_used, index_allocated = (int(value) for value in index.split("/"))
        except (ValueError, IndexError):
            return {"raw": line.strip()}
        return {
            "raw": line.strip(),
            "geometryUsed": geometry_used,
            "geometryAllocated": geometry_allocated,
            "indexUsed": index_used,
            "indexAllocated": index_allocated,
            "buffers": int(buffers),
        }
    return None


def load(directory):
    directory = Path(directory)
    report_path = directory / "logs" / "celtium-validation.json"
    image_path = directory / "screenshots" / "celtium-validation.png"
    for path in (report_path, image_path):
        if not path.is_file():
            raise SystemExit(f"missing {path}")
    result = {
        "directory": str(directory),
        "report": json.loads(report_path.read_text()),
        "report_path": str(report_path),
        "image_path": str(image_path),
    }
    return result


def compare_images(reference, candidate, tolerance, max_diff_ratio):
    first = Image.open(reference["image_path"]).convert("RGBA")
    second = Image.open(candidate["image_path"]).convert("RGBA")
    if first.size != second.size:
        return {"equal": False, "reason": f"size mismatch {first.size} != {second.size}"}

    difference = ImageChops.difference(first, second)
    # Per-pixel maximum channel delta, then the number of pixels that exceed the
    # accepted tolerance. A flat, seed-locked world rendered by the same backend
    # should be identical; the ratio allows a little driver-level noise while
    # still failing on a genuinely different scene.
    extrema = difference.getextrema()
    max_channel_delta = max(channel[1] for channel in extrema)
    if max_channel_delta == 0:
        return {
            "equal": True,
            "size": list(first.size),
            "maxChannelDelta": 0,
            "differingPixels": 0,
            "differingRatio": 0.0,
            "tolerance": tolerance,
            "maxDiffRatio": max_diff_ratio,
        }

    mask = difference.convert("L").point(lambda value: 255 if value > tolerance else 0)
    total = first.size[0] * first.size[1]
    # histogram()[0] counts pixels whose largest channel delta stayed within the tolerance.
    differing = total - mask.histogram()[0]
    ratio = differing / float(total)
    return {
        "equal": ratio <= max_diff_ratio,
        "size": list(first.size),
        "maxChannelDelta": max_channel_delta,
        "differingPixels": differing,
        "differingRatio": ratio,
        "tolerance": tolerance,
        "maxDiffRatio": max_diff_ratio,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reference", help="game directory of the reference run")
    parser.add_argument("candidate", help="game directory of the candidate run")
    parser.add_argument("--reference-label", default="reference")
    parser.add_argument("--candidate-label", default="candidate")
    parser.add_argument("--tolerance", type=int, default=0,
                        help="largest accepted per-channel difference before a pixel counts as different")
    parser.add_argument("--max-diff-ratio", type=float, default=0.0,
                        help="largest accepted fraction of differing pixels; the default requires an exact match")
    parser.add_argument("--output", help="write the comparison as JSON to this path")
    arguments = parser.parse_args()

    reference = load(arguments.reference)
    candidate = load(arguments.candidate)
    reference["label"] = arguments.reference_label
    candidate["label"] = arguments.candidate_label

    problems = []
    checks = {}

    for field in REQUIRED_EQUAL:
        left = reference["report"].get(field)
        right = candidate["report"].get(field)
        checks[field] = {"reference": left, "candidate": right, "equal": left == right}
        if left != right:
            problems.append(f"{field}: {left!r} != {right!r}")

    for field in FRAMEBUFFER_EQUAL:
        left = reference["report"].get("framebuffer", {}).get(field)
        right = candidate["report"].get("framebuffer", {}).get(field)
        checks[f"framebuffer.{field}"] = {"reference": left, "candidate": right, "equal": left == right}
        if left != right:
            problems.append(f"framebuffer.{field}: {left!r} != {right!r}")

    for field in CAMERA_FIELDS:
        left = reference["report"].get("camera", {}).get(field)
        right = candidate["report"].get("camera", {}).get(field)
        equal = isinstance(left, (int, float)) and isinstance(right, (int, float)) \
            and abs(float(left) - float(right)) <= CAMERA_TOLERANCE
        checks[f"camera.{field}"] = {"reference": left, "candidate": right, "equal": equal}
        if not equal:
            problems.append(f"camera.{field}: {left!r} != {right!r}")

    reference_counts = pass_counts(reference["report"].get("rendererDebug"))
    candidate_counts = pass_counts(candidate["report"].get("rendererDebug"))
    checks["passSections"] = {
        "reference": reference_counts,
        "candidate": candidate_counts,
        "equal": reference_counts == candidate_counts,
    }
    if reference_counts != candidate_counts:
        problems.append(f"visible sections per pass: {reference_counts} != {candidate_counts}")

    reference_memory = buffer_stats(reference["report"].get("rendererDebug"))
    candidate_memory = buffer_stats(candidate["report"].get("rendererDebug"))
    memory_fields = ("geometryUsed", "indexUsed", "buffers")
    memory_equal = all((reference_memory or {}).get(field) == (candidate_memory or {}).get(field)
                       for field in memory_fields)
    checks["geometryMemory"] = {
        "reference": reference_memory,
        "candidate": candidate_memory,
        "comparedFields": list(memory_fields),
        "equal": memory_equal,
    }
    if not memory_equal:
        problems.append(f"geometry memory (used MiB and buffer count): {reference_memory} != {candidate_memory}")

    images = compare_images(reference, candidate, arguments.tolerance, arguments.max_diff_ratio)
    checks["screenshot"] = images
    if not images["equal"]:
        problems.append("screenshot: " + images.get("reason", f"{images['differingPixels']} differing pixels "
                        f"({images['differingRatio']:.4%}), max channel delta {images['maxChannelDelta']}"))

    if reference["report"].get("status") != "PASSED":
        problems.append(f"{arguments.reference_label} fixture did not pass")
    if candidate["report"].get("status") != "PASSED":
        problems.append(f"{arguments.candidate_label} fixture did not pass")

    for side in (reference, candidate):
        if side["report"].get("nativeTerrainVerified"):
            problems.append(f"{side['label']} claims verified native terrain; this check does not prove that")
        if not side["report"].get("lightmapFlickerPinned"):
            problems.append(f"{side['label']} did not pin the vanilla lightmap flicker; the capture is not "
                            "comparable pixel by pixel")
        if side["report"].get("nonPlayerEntitiesAtCapture") not in (0, None):
            problems.append(f"{side['label']} still had {side['report'].get('nonPlayerEntitiesAtCapture')} "
                            "non-player entities at capture")

    result = {
        "reference": {"label": reference["label"], "directory": reference["directory"],
                      "report": reference["report_path"], "image": reference["image_path"],
                      "terrainBackendDescription": reference["report"].get("terrainBackendDescription")},
        "candidate": {"label": candidate["label"], "directory": candidate["directory"],
                      "report": candidate["report_path"], "image": candidate["image_path"],
                      "terrainBackendDescription": candidate["report"].get("terrainBackendDescription")},
        "checks": checks,
        "problems": problems,
        "status": "MATCH" if not problems else "MISMATCH",
    }

    if arguments.output:
        Path(arguments.output).write_text(json.dumps(result, indent=2) + "\n")

    print(f"reference {reference['label']}: {reference['directory']} -> "
          f"{reference['report'].get('status')} / {reference['report'].get('terrainBackendDescription')}")
    print(f"candidate {candidate['label']}: {candidate['directory']} -> "
          f"{candidate['report'].get('status')} / {candidate['report'].get('terrainBackendDescription')}")
    print(f"gpu: {reference['report'].get('glRenderer')}")
    print(f"sections per pass: {reference_counts}")
    print(f"screenshot: {images}")
    if problems:
        print("PROBLEMS:")
        for problem in problems:
            print(f"  - {problem}")
    print(f"status: {result['status']}")
    return 0 if not problems else 1


if __name__ == "__main__":
    sys.exit(main())
