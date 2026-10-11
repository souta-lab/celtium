#!/usr/bin/env python3
"""Fails if the picture gate's reference calibration key can be moved by the candidate.

The audit's A03: referenceCalibrationKey called calibrationKey with the reference pose and
scale, and calibrationKey reads celtium.validation.worldLayers / blockSweep / allBlocks and
the entity flags from the running JVM. So the reference variant followed the candidate, the
two keys always matched, and sceneCalibrationKeyMatches=true meant nothing - a run with
allBlocks reported its reference as allBlocks too, against references measured on plain
terrain.

This check reads the method body out of the source and fails if it can reach a live
property. It is a lint rather than a behavioural test, which is stated rather than hidden:
the behavioural version needs the fixture's Scene instances, and this catches the exact
regression the audit found - the property reads coming back.
"""
import pathlib
import re
import sys

SOURCE = pathlib.Path(__file__).resolve().parents[2] / "validation/client/src/main/java/org/soutalab/celtium/validation/ValidationClient.java"
LIVE = ("System.getProperty", "Boolean.getBoolean", "Integer.getInteger", "Long.getLong")
CALLEES = ("celtium$calibrationKey", "celtium$entityVariant", "celtium$runCalibrationKey")


def body_of(text, signature):
    start = text.find(signature)
    if start < 0:
        return None
    brace = text.find("{", start)
    depth = 0
    for i in range(brace, len(text)):
        if text[i] == "{":
            depth += 1
        elif text[i] == "}":
            depth -= 1
            if depth == 0:
                return text[brace:i + 1]
    return None


def main():
    text = SOURCE.read_text()
    body = body_of(text, "private static String celtium$referenceCalibrationKey(Scene scene)")
    if body is None:
        print("reference_key_check: could not find celtium$referenceCalibrationKey in " + str(SOURCE))
        return 1
    failures = []
    for needle in LIVE:
        if needle in body:
            failures.append("the reference key reads a live property: " + needle)
    for needle in CALLEES:
        if needle in body:
            failures.append("the reference key calls " + needle + ", which reads the candidate's properties")
    print("reference_key_check: reference key is constant-built" if not failures
          else "reference_key_check FAILURES:")
    for failure in failures:
        print("  - " + failure)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
