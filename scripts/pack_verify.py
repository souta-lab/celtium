#!/usr/bin/env python3
"""Verify a modpack leg beyond the validation count: classify its log problems and diff its world picture.

Usage:
    pack_verify.py logs RUN_DIR [--json OUT]
    pack_verify.py picture CELTIUM_RUN REFERENCE_RUN --out DIFF_PNG [--json OUT]
    pack_verify.py review CELTIUM_RUN REFERENCE_RUN --out REVIEW.jpg [--title TEXT]   (an image for eyes)

`logs` reads RUN_DIR/logs/latest.log, RUN_DIR/logs/debug.log and RUN_DIR/validation-console.log and collects every
ERROR/FATAL line, every exception line ("...Exception:"/"...Error:" at the start of a message or a "Caused by:") and
every Celtium line that reports a refusal or a failure. Each is classified:

* ``celtium`` - the message, its logger or the first frames of its stack name Celtium/Celeritas code
  (``org.soutalab``, ``org.taumc.celeritas``, ``[Celtium]``);
* ``pack`` - everything else (a pack mod, Forge, Cleanroom).

Identical messages are grouped after numbers and hex addresses are normalised, so a line repeated every frame is one
row with a count. The legs must run with ``-Dceltium.debug.logLinesPerTag`` raised, or Celtium's own lines stop after
16 per tag and the census undercounts (docs/modpacks.md, "What verified means").

`picture` compares the two runs' world pictures (the Celtium run's presented dump and the reference run's game
screenshot, both 1280x720, same seed, pose and world time) and reports the mean absolute difference, the share of
pixels that differ by more than 24 levels in any channel, and a heatmap PNG for review. The numbers alone do not
decide a verdict: clouds, particles and mobs move between runs, so the heatmap is read before calling a difference a
renderer defect.
"""
import argparse
import collections
import json
import re
import sys
from pathlib import Path

CELTIUM_MARKERS = ("org.soutalab", "org/soutalab", "org.taumc.celeritas", "org/taumc/celeritas", "[Celtium]",
                   "Celtium Vulkan validation")
CELTIUM_PROBLEM = re.compile(
    r"\[Celtium\][^\n]*(REFUS|FAIL|_DROPPED|NOT_DRAWN|_SKIPPED|_MISSING|DEAD_TEXTURE|_ERROR|refused|failed|missing)")
LEVEL = re.compile(r"\[[^\]]*/(ERROR|FATAL)\]")
EXCEPTION = re.compile(r"^(?:\s*Caused by: )?([a-zA-Z_$][\w$.]*(?:Exception|Error)\b)(?::|$)")
NORMALISE = [(re.compile(r"0x[0-9a-fA-F]+"), "0x#"), (re.compile(r"@[0-9a-f]{6,}"), "@#"),
             (re.compile(r"\b\d+(\.\d+)?\b"), "#"), (re.compile(r"^\[[^\]]*\] "), "")]


def normalise(line):
    text = line.strip()
    for pattern, replacement in NORMALISE:
        text = pattern.sub(replacement, text)
    return text[:220]


PROBLEM_VALUE = re.compile(r"(\w*(?:[Rr]efus|[Ff]ail|[Mm]issing|[Dd]ropped|[Ss]kipped)\w*)=(\[[^\]]*\]|\{[^}]*\}|[^\s,]+)")
EMPTY_VALUES = {"0", "none", "[none]", "{}", "[]", "[0]", "0.0"}


def reports_nothing(line):
    """A Celtium counter line whose every problem-named value is zero or empty (``refused=0``, ``failure=[none]``,
    ``CELTIUM_REFUSALS_BY_FORMAT {}``) reports nothing and is not a problem."""
    if re.search(r"CELTIUM_REFUSALS_BY_FORMAT \{\}\s*$", line):
        return True
    values = PROBLEM_VALUE.findall(line)
    if not values:
        return False
    stripped = PROBLEM_VALUE.sub("", line)
    if re.search(r"REFUS|FAIL|_DROPPED|NOT_DRAWN|_SKIPPED|_MISSING|DEAD_TEXTURE|_ERROR|refused|failed|missing", stripped):
        return False
    return all(v.strip().lower() in EMPTY_VALUES for _, v in values)


def classify(line, following):
    window = line + "\n" + "\n".join(following[:6])
    return "celtium" if any(marker in window for marker in CELTIUM_MARKERS) else "pack"


def scan(path):
    if not path.is_file():
        return []
    lines = path.read_text(encoding="utf-8", errors="replace").splitlines()
    found = []
    for index, line in enumerate(lines):
        kind = None
        if CELTIUM_PROBLEM.search(line):
            if reports_nothing(line):
                continue
            kind = "celtium-report"
        elif LEVEL.search(line):
            kind = "error-level"
        elif EXCEPTION.match(line.strip()):
            kind = "exception"
        if kind is None:
            continue
        following = [l for l in lines[index + 1:index + 8] if l.lstrip().startswith(("at ", "Caused by"))]
        origin = "celtium" if kind == "celtium-report" else classify(line, following)
        text = normalise(line)
        if kind == "celtium-report":
            # Name the counters that are not zero, with their real values: a summary line carries dozens of them.
            nonzero = [f"{k}={v}" for k, v in PROBLEM_VALUE.findall(line) if v.strip().lower() not in EMPTY_VALUES]
            tag = re.search(r"CELTIUM_[A-Z_]+", line)
            text = (tag.group(0) if tag else text[:80]) + (" :: " + " ".join(nonzero)[:400] if nonzero else "")
        found.append({"file": path.name, "kind": kind, "origin": origin, "text": text})
    return found


def logs(run):
    run = Path(run)
    entries = []
    for path in (run / "logs/latest.log", run / "logs/debug.log", run / "validation-console.log"):
        entries.extend(scan(path))
    # debug.log repeats latest.log, so a message counts as often as the file that holds it most often, not the sum.
    per_file = collections.Counter((e["file"], e["origin"], e["kind"], e["text"]) for e in entries)
    grouped = collections.Counter()
    for (_, o, k, t), n in per_file.items():
        grouped[(o, k, t)] = max(grouped[(o, k, t)], n)
    rows = [{"origin": o, "kind": k, "count": c, "text": t} for (o, k, t), c in grouped.most_common()]
    summary = collections.Counter()
    for (o, k, _), n in grouped.items():
        summary[(o, k)] += n
    return {"run": str(run), "files": [p.name for p in (run / "logs/latest.log", run / "logs/debug.log",
                                                         run / "validation-console.log") if p.is_file()],
            "totals": {f"{o}/{k}": n for (o, k), n in sorted(summary.items())}, "groups": rows}


def world_picture(run):
    run = Path(run)
    kept = sorted(run.glob("screenshots/celtium-presented-world-*-kept.png"))
    if kept:
        return kept[-1]
    screenshot = run / "screenshots/celtium-validation.png"
    return screenshot if screenshot.is_file() else None


def picture(celtium_run, reference_run, out):
    from PIL import Image, ImageChops
    a_path, b_path = world_picture(celtium_run), world_picture(reference_run)
    if a_path is None or b_path is None:
        return {"error": f"missing picture: celtium={a_path} reference={b_path}"}
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB")
    if a.size != b.size:
        return {"error": f"size differs: {a.size} vs {b.size}"}
    diff = ImageChops.difference(a, b)
    channels = diff.split()
    peak = ImageChops.lighter(ImageChops.lighter(channels[0], channels[1]), channels[2])
    histogram = peak.histogram()
    total = a.size[0] * a.size[1]
    mean = sum(i * n for i, n in enumerate(histogram)) / total
    over = sum(histogram[25:]) / total
    heat = peak.point(lambda v: min(255, v * 4)).convert("RGB")
    composite = Image.new("RGB", (a.size[0] * 3, a.size[1]))
    composite.paste(a, (0, 0))
    composite.paste(b, (a.size[0], 0))
    composite.paste(heat, (a.size[0] * 2, 0))
    composite.save(out)
    return {"celtium": str(a_path), "reference": str(b_path), "meanAbsDiff": round(mean, 2),
            "fractionOver24": round(over, 4), "composite": str(out)}


def review(celtium_run, reference_run, out, title):
    """A labelled JPEG for eyes: Celtium | Celeritas on top (half size), the difference heatmap and a 2x crop of the
    region that differs most below - small enough to embed in a Markdown file and read on GitHub."""
    from PIL import Image, ImageChops, ImageDraw
    a_path, b_path = world_picture(celtium_run), world_picture(reference_run)
    if a_path is None or b_path is None:
        return {"error": f"missing picture: celtium={a_path} reference={b_path}"}
    a = Image.open(a_path).convert("RGB")
    b = Image.open(b_path).convert("RGB")
    w, h = a.size
    half = (w // 2, h // 2)
    peak = ImageChops.difference(a, b).convert("L")
    heat = peak.point(lambda v: min(255, v * 4)).convert("RGB")
    # The 160x90 window with the largest summed difference, shown at 2x from both renderers.
    grid = peak.resize((w // 40, h // 40), Image.BOX)
    gw, gh = grid.size
    best, bx, by = -1, 0, 0
    for y in range(gh - 2):
        for x in range(gw - 3):
            total = sum(grid.getpixel((x + i, y + j)) for i in range(4) for j in range(3))
            if total > best:
                best, bx, by = total, x, y
    box = (bx * 40, by * 40, bx * 40 + 160, by * 40 + 120)
    crop = Image.new("RGB", (half[0], half[1]), "black")
    crop.paste(a.crop(box).resize((320, 240), Image.NEAREST), (0, 0))
    crop.paste(b.crop(box).resize((320, 240), Image.NEAREST), (320, 0))
    sheet = Image.new("RGB", (half[0] * 2, half[1] * 2 + 28), "white")
    sheet.paste(a.resize(half), (0, 28))
    sheet.paste(b.resize(half), (half[0], 28))
    sheet.paste(heat.resize(half), (0, 28 + half[1]))
    sheet.paste(crop, (half[0], 28 + half[1]))
    draw = ImageDraw.Draw(sheet)
    draw.text((6, 8), f"{title}   top: Celtium | Celeritas 2.5.0-dev   bottom: |difference| x4 | "
              f"most-different region {box} at 2x (Celtium left, Celeritas right)", fill="black")
    for x, y, label in ((6, 32, "Celtium"), (half[0] + 6, 32, "Celeritas"), (6, 32 + half[1], "difference x4"),
                        (half[0] + 6, 32 + half[1], "crop: Celtium | Celeritas")):
        draw.rectangle((x - 2, y - 2, x + 8 * len(label), y + 12), fill="black")
        draw.text((x, y), label, fill="white")
    sheet.save(out, "JPEG", quality=82)
    return {"review": str(out), "mostDifferentRegion": box}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = parser.add_subparsers(dest="command", required=True)
    p_logs = sub.add_parser("logs")
    p_logs.add_argument("run")
    p_logs.add_argument("--json")
    p_pic = sub.add_parser("picture")
    p_pic.add_argument("celtium_run")
    p_pic.add_argument("reference_run")
    p_pic.add_argument("--out", required=True)
    p_pic.add_argument("--json")
    p_rev = sub.add_parser("review")
    p_rev.add_argument("celtium_run")
    p_rev.add_argument("reference_run")
    p_rev.add_argument("--out", required=True)
    p_rev.add_argument("--title", default="")
    args = parser.parse_args(argv)
    if args.command == "review":
        print(json.dumps(review(args.celtium_run, args.reference_run, args.out, args.title)))
        return 0
    result = logs(args.run) if args.command == "logs" else picture(args.celtium_run, args.reference_run, args.out)
    text = json.dumps(result, indent=1, ensure_ascii=False)
    if args.json:
        Path(args.json).write_text(text + "\n", encoding="utf-8")
    if args.command == "logs":
        print(json.dumps(result["totals"]))
        for row in result["groups"][:40]:
            print(f'{row["count"]:6} {row["origin"]:8} {row["kind"]:15} {row["text"]}')
    else:
        print(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
