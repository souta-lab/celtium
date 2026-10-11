#!/usr/bin/env python3
"""Report the counters that decide whether the particle family (format 5) draws natively.

Format 5 is the only family whose acceptance gate needs a descriptor probe, and that probe is the only
place in the offer path that used to refuse without naming a reason. This script reads a finished run and
prints, in one place, everything needed to tell the three named conditions apart from the silent one:

  * the per-format arrivals and the backend's own offered/accepted pair,
  * the refusal census (``CELTIUM_REFUSALS_BY_FORMAT``) with its stage markers,
  * ``particleDescriptorRefusals`` and the last ``particleDescriptorFailure`` the probe recorded,
  * the two counters that would move if the texture or its content were the cause
    (``textureRefusalsBeforeAccept``, ``partialMirrorRefusals``),
  * the fixture/validation/picture verdicts.

Usage:
    scripts/format5_particle_report.py .celtium-work/runtime/<run>
    scripts/format5_particle_report.py --json .celtium-work/particles.json

A run directory is expected to hold ``validation-process.json``, ``validation-console.log`` and
``logs/celtium-validation.json``; the ``--json`` form reads the run's validation output copy and still
needs ``--console`` when the refusal census is wanted, because that census is printed to stdout only.
"""
import argparse
import json
import os
import re
import sys


def load_json(path):
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as handle:
            return json.load(handle)
    except (OSError, ValueError) as error:
        print("cannot read {}: {}".format(path, error), file=sys.stderr)
        return None


def backend_summary(validation):
    """The one rendererDebug entry that carries the backend's counters (it contains 'particleDescriptorRefusals')."""
    if not isinstance(validation, dict):
        return ""
    for entry in validation.get("rendererDebug", []) or []:
        if isinstance(entry, str) and "particleDescriptorRefusals" in entry:
            return entry
    return ""


def field(text, name):
    """One counter out of the summary. Quoted values keep their content, and comma-separated lists are kept whole:
    acceptedByFormat and droppedByFormat are lists, and cutting them at the first comma hides the format-5 entry."""
    match = re.search(re.escape(name) + r'="([^"]*)"', text)
    if match:
        return match.group(1)
    match = re.search(re.escape(name) + r"=([^ |]+)", text)
    return match.group(1) if match else None


def last_match(pattern, text):
    found = None
    for found in re.finditer(pattern, text):
        pass
    return found.group(0) if found else None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("run", nargs="?", help="run directory (e.g. .celtium-work/runtime/particles)")
    parser.add_argument("--json", help="validation JSON path (the run's --output copy)")
    parser.add_argument("--console", help="console log path; defaults to <run>/validation-console.log")
    args = parser.parse_args()

    run = args.run
    validation_path = args.json or (os.path.join(run, "logs", "celtium-validation.json") if run else None)
    process_path = os.path.join(run, "validation-process.json") if run else None
    console_path = args.console or (os.path.join(run, "validation-console.log") if run else None)

    validation = load_json(validation_path) if validation_path else None
    process = load_json(process_path) if process_path and os.path.exists(process_path) else None
    console = ""
    if console_path and os.path.exists(console_path):
        with open(console_path, "r", encoding="utf-8", errors="replace") as handle:
            console = handle.read()

    summary = backend_summary(validation)

    print("== run ==")
    print("  validation json : {}".format(validation_path))
    if process:
        for key in ("fixtureStatus", "validationClean", "validationMessageCount", "validationAllowedCount",
                    "validationExitCode", "nativeDeviceName", "endedBy", "elapsedSeconds"):
            if key in process:
                print("  {:<16}: {}".format(key, process[key]))

    print("== offers ==")
    decisions = last_match(r"celtium offer decisions: offers=\d+ accepted=\d+ glBodyCompleted=\d+", console)
    print("  {}".format(decisions or "not printed (the counter prints every 1000 offers)"))
    arrivals = last_match(r"CELTIUM_ARRIVALS total=\d+ byFormat=\[[^\]]*\]", console)
    print("  arrivals : {}".format(arrivals or "not printed"))

    print("== format 5 ==")
    offered = last_match(r"immediate formats offered/accepted: [^|]*", summary)
    print("  backend  : {}".format(offered or "absent from rendererDebug"))
    print("  accepted : {}".format(field(summary, "acceptedByFormat")))
    print("  probe refusals          : {}".format(field(summary, "particleDescriptorRefusals")))
    print("  last descriptor failure : {}".format(field(summary, "particleDescriptorFailure")))
    print("  last offer refusal      : {}".format(field(summary, "immediateRefusal")))
    print("  textureRefusalsBeforeAccept : {}".format(field(summary, "textureRefusalsBeforeAccept")))
    print("  partialMirrorRefusals       : {}".format(field(summary, "partialMirrorRefusals")))
    print("  partialMirrorsCompleted     : {}".format(field(summary, "partialMirrorsCompleted")))
    print("  postAcceptDroppedGroups     : {}".format(field(summary, "postAcceptDroppedGroups")))
    print("  droppedByFormat             : {}".format(field(summary, "droppedByFormat")))
    print("  immediateOnlyPasses/terrain : {} / {}".format(field(summary, "immediateOnlyPasses"),
                                                         field(summary, "terrainPasses")))
    census = last_match(r"CELTIUM_REFUSALS_BY_FORMAT \{[^}]*\}", console)
    print("  refusal census : {}".format(census or "not printed"))

    print("== picture ==")
    if isinstance(validation, dict):
        for key in ("sceneGateCalibrated", "sceneGateReference", "worldFrameGate", "pictureVerdict",
                    "presentedImageVerdict", "presentedImageAssertions", "worldFrameDistinctColours",
                    "worldFrameNonBackgroundFraction", "worldFrameVerticalBands", "worldFrameBlackFraction"):
            value = validation.get(key)
            if value is not None:
                text = value if isinstance(value, str) else json.dumps(value)
                print("  {:<32}: {}".format(key, text[:200]))


if __name__ == "__main__":
    main()
