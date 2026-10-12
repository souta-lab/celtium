#!/usr/bin/env python3
"""Compares a run's `CELTIUM_VERIFY` line against the baseline the extraction must not change.

task-65 moves `verificationSummary()`, its `*Summary` helpers, `describe()` and `record()` out of
`VulkanTerrainGpuBackend.java` into `BackendReport.java`. The acceptance is that the line does not change by a single
character - past acceptances quote it - so the check is a byte comparison with a field-level diff for the case where
it does change: it names every key whose value differs, every key present on one side only, and the first offset at
which the two strings diverge.

    python3 scripts/cpu-contract/verify_line_check.py                       # newest run's console vs the baseline
    python3 scripts/cpu-contract/verify_line_check.py --run DIR             # a specific run directory
    python3 scripts/cpu-contract/verify_line_check.py --from-log FILE       # any log or console file
    python3 scripts/cpu-contract/verify_line_check.py --save --from-log F   # re-baseline deliberately
    python3 scripts/cpu-contract/verify_line_check.py --structural          # diagnose a re-take (not acceptance)

Exit status is 0 only for a byte-identical line; `--structural` exits 0 when the key sets match and only listed
volatile keys differ, and says so in its own output.
"""
import argparse
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
BASELINE = os.path.join(ROOT, "scripts/cpu-contract/verify_line_baseline.txt")
MARKER = "CELTIUM_VERIFY"
# Values that a re-take of the same leg may legitimately change while the extraction must not: they count frames,
# draws and passes of that particular run. `--structural` lists them instead of calling the difference a defect.
VOLATILE_HINTS = ("frameId", "lastDraw", "lastSections", "passes", "skipped", "errors", "Millis", "Nanos",
                  "Count", "Calls", "Draws", "Flushes", "flushCount")


def extract_line(path):
    with open(path, encoding="utf-8", errors="replace") as handle:
        for raw in handle:
            match = re.search(r"%s.*" % MARKER, raw)
            if match:
                return match.group(0).rstrip("\n")
    return None


def newest_run_line():
    candidates = sorted(glob.glob(os.path.join(ROOT, ".celtium-work/runtime/*/validation-console.log")),
                        key=os.path.getmtime, reverse=True)
    for path in candidates:
        line = extract_line(path)
        if line:
            return path, line
    return None, None


def tokens(line):
    """The line's space-separated tokens, in order.

    The same key occurs more than once (`frameId=` is on the line twice), so a dict would keep only the last
    occurrence and hide a change in the first - measured: a perturbed `frameId` reported no changed key at all while
    the strings differed at offset 181. The diff below is positional for that reason.
    """
    return [token for token in line.split(" ") if token]


def as_field(token):
    """(key, value) for a `key=value` token, (None, token) for a bare one."""
    if "=" in token:
        key, _, value = token.partition("=")
        return key, value
    return None, token


def key_counts(sequence):
    counts = {}
    for token in sequence:
        key, _ = as_field(token)
        if key is not None:
            counts[key] = counts.get(key, 0) + 1
    return counts


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", default=BASELINE)
    parser.add_argument("--run", help="run directory to read (validation-console.log, then logs/latest.log)")
    parser.add_argument("--from-log", help="a specific log or console file")
    parser.add_argument("--save", action="store_true", help="write the candidate as the new baseline")
    parser.add_argument("--structural", action="store_true",
                        help="tolerate the listed volatile keys; diagnostic, not the acceptance")
    parser.add_argument("--json", action="store_true")
    args = parser.parse_args()

    source = args.from_log
    if source is None and args.run:
        for name in ("validation-console.log", os.path.join("logs", "latest.log")):
            candidate = os.path.join(args.run, name)
            if os.path.exists(candidate):
                source = candidate
                break
    if source is None:
        source, candidate_line = newest_run_line()
        if candidate_line is None:
            print("FAIL no CELTIUM_VERIFY line found under .celtium-work/runtime/*/")
            return 1
    else:
        candidate_line = extract_line(source)
        if candidate_line is None:
            print("FAIL no %s line in %s" % (MARKER, source))
            return 1

    if args.save:
        with open(args.baseline, "w", encoding="utf-8") as handle:
            handle.write(candidate_line + "\n")
        print("baseline written: %s (%d chars) from %s" % (args.baseline, len(candidate_line), source))
        return 0

    if not os.path.exists(args.baseline):
        print("FAIL baseline missing: %s" % args.baseline)
        return 1
    baseline_line = open(args.baseline, encoding="utf-8").read().rstrip("\n")

    identical = baseline_line == candidate_line
    base_tokens, cand_tokens = tokens(baseline_line), tokens(candidate_line)
    base_counts, cand_counts = key_counts(base_tokens), key_counts(cand_tokens)
    offset = next((i for i, (a, b) in enumerate(zip(baseline_line, candidate_line)) if a != b), None)

    # Positional diff: every token that differs, with the key when both sides carry the same one.
    changed = []
    for index, (before, after) in enumerate(zip(base_tokens, cand_tokens)):
        if before != after:
            base_key, base_value = as_field(before)
            cand_key, cand_value = as_field(after)
            changed.append({"index": index, "key": base_key if base_key == cand_key else None,
                            "baselineKey": base_key, "candidateKey": cand_key,
                            "baseline": base_value, "candidate": cand_value,
                            "baselineToken": before, "candidateToken": after,
                            "volatile": base_key is not None and any(h in base_key for h in VOLATILE_HINTS)})
    tail_missing = base_tokens[len(cand_tokens):] if len(base_tokens) > len(cand_tokens) else []
    tail_added = cand_tokens[len(base_tokens):] if len(cand_tokens) > len(base_tokens) else []
    key_diff = {k: (base_counts.get(k, 0), cand_counts.get(k, 0))
                for k in set(base_counts) | set(cand_counts) if base_counts.get(k, 0) != cand_counts.get(k, 0)}
    volatile = [c["key"] for c in changed if c["volatile"]]
    # `clean` means every difference is a listed volatile token: no missing or added tail.
    clean = not tail_missing and not tail_added

    report = {"identical": identical, "source": os.path.relpath(source, ROOT),
              "baseline": os.path.relpath(args.baseline, ROOT),
              "baselineLength": len(baseline_line), "candidateLength": len(candidate_line),
              "tokensBaseline": len(base_tokens), "tokensCandidate": len(cand_tokens),
              "keyOccurrencesBaseline": sum(base_counts.values()), "keyOccurrencesCandidate": sum(cand_counts.values()),
              "firstDifferenceOffset": offset if not identical else None,
              "changedTokens": changed, "tailMissing": tail_missing, "tailAdded": tail_added,
              "keyOccurrenceDifferences": key_diff, "volatileAmongChanged": volatile}
    report["verdict"] = ("IDENTICAL" if identical else
                         "STRUCTURALLY-EQUAL (volatile tokens differ: %s)" % ", ".join(volatile)
                         if args.structural and clean and changed and all(c["volatile"] for c in changed)
                         else "DIFFERENT")

    if args.json:
        print(json.dumps(report, indent=2))
    else:
        print("baseline %s (%d chars) vs %s (%d chars)" % (report["baseline"], report["baselineLength"],
                                                           report["source"], report["candidateLength"]))
        print("tokens: baseline %d, candidate %d (key=value occurrences %d / %d)" % (
            report["tokensBaseline"], report["tokensCandidate"],
            report["keyOccurrencesBaseline"], report["keyOccurrencesCandidate"]))
        if identical:
            print("IDENTICAL: the %s line is byte-for-byte the baseline" % MARKER)
        else:
            print("first difference at offset %s" % offset)
            for change in changed:
                label = change["key"] or "%s->%s" % (change["baselineKey"], change["candidateKey"])
                print("  CHANGED  token %-4d %-40s baseline=%r candidate=%r%s" % (
                    change["index"], label, change["baseline"], change["candidate"],
                    "   (volatile)" if change["volatile"] else ""))
            for token in tail_missing:
                print("  MISSING  %s" % token)
            for token in tail_added:
                print("  ADDED    %s" % token)
            for key, (before, after) in sorted(key_diff.items()):
                print("  OCCURRENCES %-36s baseline=%d candidate=%d" % (key, before, after))
        print("verdict: %s" % report["verdict"])

    if identical:
        return 0
    if args.structural and report["verdict"].startswith("STRUCTURALLY-EQUAL"):
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
