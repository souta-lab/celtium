#!/usr/bin/env bash
# Celtium's frame time against Celeritas, measured as one pair in one session.
#
# The KPI is "frame time at or below Celeritas", so the measurement has to be a pair: same scene, same GPU, same flags,
# back to back, with the renderer under test and the reference arm differing only in the jar that draws. Absolute frame
# times move by tens of percent with machine state, so a number without its pair says nothing.
#
# Usage: scripts/gv-kpi.sh TAG [SECONDS=100] [shared -D flags...] [-- celtium-only -D flags...]
#   CELTIUM_KPI_REPS  how many pairs to run (default 3); the median is reported
#   TAG      the leg-name prefix; the two legs are TAG-celtium and TAG-celeritas
#
# The flags before "--" go to *both* arms, because they define the comparison: a camera, a scene, a fixture option.
# Flags after "--" go to the Celtium arm alone - the renderer's own switches, which the reference jar does not have -
# and a run that uses them reports the ratio without a speed verdict, because the two arms are then not drawing the
# same frame (#391). The old form appended every extra flag to the Celtium arm only, so "-Dceltium.validation.cameraYaw=42"
# compared two different cameras and still printed a verdict.
# Sets CELTIUM_LEG_REFERENCE_RENDERER for the second arm. Prints both p50/p95 and the ratio, and exits 1 if Celtium is
# slower than the reference (that is the KPI failing, not the script failing).
set -u
# The numbers only mean something on an idle machine: run back to back under load average 9 the same jar measured 33.7 ms
# where it measures 3.0 ms idle, and a 10x move swamps every optimisation being tested. One core of headroom is the most
# this allows - the game itself uses a core - so the one-minute load average has to be under the threshold.
LOAD_LIMIT=${CELTIUM_KPI_LOAD_LIMIT:-2.0}
LOAD_ONE=$(awk '{print $1}' /proc/loadavg 2>/dev/null || echo 0)
# The variable is not called "load": gawk reserves that name for its own extension loader and refuses it as a -v name
# ("gawk: fatal: `load' is not a variable name"), which made the guard exit non-zero in a way the shell read as "do not
# skip" - so a leg ran under load 5.9 with the guard in place. Measured on the fixed script below.
if [ "${CELTIUM_KPI_FORCE:-0}" != "1" ] && awk -v oneMinute="$LOAD_ONE" -v limit="$LOAD_LIMIT" 'BEGIN { exit !(oneMinute > limit) }'; then
    echo "refusing to measure: one-minute load average is $LOAD_ONE (limit $LOAD_LIMIT)."
    echo "A loaded machine moves the frame time more than any change being tested; wait for it to settle, raise"
    echo "CELTIUM_KPI_LOAD_LIMIT, or set CELTIUM_KPI_FORCE=1 when a comparison under load is what is wanted."
    exit 3
fi

TAG=${1:?usage: scripts/gv-kpi.sh TAG [SECONDS] [shared flags...] [-- celtium-only flags...]}
SECONDS_=${2:-100}
shift 2 2>/dev/null || shift
SHARED=()
ARM_ONLY=()
seen_separator=0
for arg in "$@"; do
    if [ "$arg" = "--" ]; then
        seen_separator=1
        continue
    fi
    if [ "$seen_separator" = "1" ]; then
        ARM_ONLY+=("$arg")
    else
        SHARED+=("$arg")
    fi
done
REFERENCE=${CELTIUM_KPI_REFERENCE:-/home/s0u7a/ダウンロード/01-celeritas-forge-mc12.2-2.5.0-dev.jar}
JARS=${CELTIUM_KPI_JARS:-.gv-verify/jars/batchfs}
SCENE=${CELTIUM_KPI_SCENE:-world}
GPU=${CELTIUM_KPI_GPU:-amd}

# The flags both arms share: no validation, no present readback, no periodic dump. A leg that pays the fixture's
# present-dump tax is measuring the harness, not the renderer (each dump was 240-300 ms before frame dumps went opt-in).
COMMON=(-Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0)

# Run-to-run noise on this pairing is about +-6% (two back-to-back pairs of the same jar moved both arms: 2.965/1.294
# and 3.136/1.322 ms), which is larger than most single optimisations. A pair is therefore repeated and the median ratio
# is reported; a single pair is a smoke test, not a measurement.
# A Celtium-only flag means the arms are not the same frame, so the run may report a ratio but never a speed verdict.
if [ "${#ARM_ONLY[@]}" -gt 0 ]; then
    echo "note: ${#ARM_ONLY[@]} celtium-only flag(s) - the ratio is reported, no speed verdict is given (#391)."
fi
LEG_FAILURES=0
for rep in $(seq 1 "${CELTIUM_KPI_REPS:-3}"); do
    if ! scripts/gv-leg.sh "${TAG}-celtium-r$rep" "$JARS" "$SCENE" "$GPU" "$SECONDS_" \
            "${COMMON[@]}" "${SHARED[@]}" "${ARM_ONLY[@]}" >/dev/null 2>&1; then
        echo "leg ${TAG}-celtium-r$rep failed" >&2
        LEG_FAILURES=$((LEG_FAILURES + 1))
    fi
    if ! CELTIUM_LEG_REFERENCE_RENDERER="$REFERENCE" \
            scripts/gv-leg.sh "${TAG}-celeritas-r$rep" "$JARS" "$SCENE" "$GPU" "$SECONDS_" \
            "${COMMON[@]}" "${SHARED[@]}" >/dev/null 2>&1; then
        echo "leg ${TAG}-celeritas-r$rep failed" >&2
        LEG_FAILURES=$((LEG_FAILURES + 1))
    fi
done

python3 - "$TAG" "${CELTIUM_KPI_REPS:-3}" "$LEG_FAILURES" "${#ARM_ONLY[@]}" <<'PY'
import json, math, os, statistics, sys

tag, reps, leg_failures, arm_only = sys.argv[1], int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4])
# A p50 below this is not a frame time; it is a report that never recorded one (a FAILED leg, an empty metric, a run
# that stopped before the window). The old version read a missing metric as 0.0 ms, so a leg with no samples at all
# became the fastest arm and the pair "passed" (#390).
MIN_PLAUSIBLE_P50_MS = 0.05
MIN_SAMPLES_PER_LEG = 50


def read_leg(name, rep):
    """Returns (report, reason) - report is None when this leg cannot be used in a comparison."""
    base = '.gv-verify/run-%s-%s-r%d' % (tag, name, rep)
    validation = os.path.join(base, 'logs', 'celtium-validation.json')
    process = os.path.join(base, 'validation-process.json')
    if not os.path.exists(validation):
        return None, 'no validation report at %s' % validation
    if not os.path.exists(process):
        return None, 'no process report at %s' % process
    try:
        with open(validation, encoding='utf-8') as handle:
            report = json.load(handle)
        with open(process, encoding='utf-8') as handle:
            run = json.load(handle)
    except (OSError, ValueError) as failure:
        return None, 'unreadable report (%s)' % failure
    if run.get('exitCode') not in (0, None):
        return None, 'the game exited with %s' % run.get('exitCode')
    fixture = run.get('fixtureStatus')
    if fixture is not None and str(fixture) not in ('PASSED', 'None', ''):
        return None, 'fixtureStatus=%s' % fixture
    verdict = report.get('presentedImageVerdict')
    if verdict is not None and str(verdict) != 'PASSED':
        return None, 'presentedImageVerdict=%s' % verdict
    samples = report.get('frameIntervalSampleCount')
    if not isinstance(samples, int) or samples < MIN_SAMPLES_PER_LEG:
        return None, 'frameIntervalSampleCount=%r (needs >= %d)' % (samples, MIN_SAMPLES_PER_LEG)
    p50 = report.get('frameIntervalP50Ms')
    if not isinstance(p50, (int, float)) or not math.isfinite(p50) or p50 < MIN_PLAUSIBLE_P50_MS:
        return None, 'frameIntervalP50Ms=%r is not a frame time' % (p50,)
    p95 = report.get('frameIntervalP95Ms')
    return {'p50': float(p50),
            'p95': float(p95) if isinstance(p95, (int, float)) else 0.0,
            'samples': samples, 'verdict': verdict}, None


def arm(name):
    usable, rejected = [], []
    for rep in range(1, reps + 1):
        report, reason = read_leg(name, rep)
        if report is None:
            rejected.append('r%d: %s' % (rep, reason))
        else:
            usable.append(report)
    return usable, rejected


problems = []
if leg_failures:
    problems.append('%d leg(s) returned a non-zero status' % leg_failures)
rows = {}
for name in ('celtium', 'celeritas'):
    usable, rejected = arm(name)
    rows[name] = usable
    if rejected:
        problems.append('%s: %d of %d rep(s) unusable (%s)'
                        % (name, len(rejected), reps, '; '.join(rejected[:3])))
    if usable and len(usable) != reps:
        problems.append('%s: only %d of %d rep(s) usable, so the median is over a partial set'
                        % (name, len(usable), reps))

for name in ('celtium', 'celeritas'):
    usable = rows[name]
    if not usable:
        print('  %-9s UNUSABLE' % name)
        continue
    p50s = [r['p50'] for r in usable]
    p95s = [r['p95'] for r in usable]
    print('  %-9s p50 %7.3f ms (runs %s..%s)  p95 %7.3f ms  frames %5d  %s'
          % (name, statistics.median(p50s), round(min(p50s), 3), round(max(p50s), 3),
             statistics.median(p95s), sum(r['samples'] for r in usable),
             ','.join(sorted(str(r['verdict']) for r in usable))))

if problems:
    print('  comparison NOT usable - no speed verdict:')
    for problem in problems:
        print('    - %s' % problem)
    sys.exit(2)

celeritas = statistics.median([r['p50'] for r in rows['celeritas']])
celtium = statistics.median([r['p50'] for r in rows['celtium']])
if celeritas <= 0.0:
    print('  comparison NOT usable - the reference measured %r ms' % celeritas)
    sys.exit(2)
ratio = celtium / celeritas
print('  ratio     %.2fx over %d pair(s)' % (ratio, len(rows['celtium'])))
if arm_only:
    # The arms were not given the same flags, so the ratio is information and not a verdict (#391).
    print('  verdict   NO SPEED VERDICT: %d celtium-only flag(s) were used; re-run without "--" to judge the KPI.'
          % arm_only)
    sys.exit(2)
print('  verdict   %s' % ('PASS: not slower than the reference' if ratio <= 1.0
                         else 'FAIL: %.0f%% slower than the reference' % ((ratio - 1.0) * 100.0)))
sys.exit(0 if ratio <= 1.0 else 1)
PY
