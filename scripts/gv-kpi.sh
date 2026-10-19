#!/usr/bin/env bash
# Celtium's frame time against Celeritas, measured as one pair in one session.
#
# The KPI is "frame time at or below Celeritas", so the measurement has to be a pair: same scene, same GPU, same flags,
# back to back, with the renderer under test and the reference arm differing only in the jar that draws. Absolute frame
# times move by tens of percent with machine state, so a number without its pair says nothing.
#
# Usage: scripts/gv-kpi.sh TAG [SECONDS=100] [extra -D flags...]
#   CELTIUM_KPI_REPS  how many pairs to run (default 3); the median is reported
#   TAG      the leg-name prefix; the two legs are TAG-celtium and TAG-celeritas
# Sets CELTIUM_LEG_REFERENCE_RENDERER for the second arm. Prints both p50/p95 and the ratio, and exits 1 if Celtium is
# slower than the reference (that is the KPI failing, not the script failing).
set -u
TAG=${1:?usage: scripts/gv-kpi.sh TAG [SECONDS] [flags...]}
SECONDS_=${2:-100}
shift 2 2>/dev/null || shift
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
for rep in $(seq 1 "${CELTIUM_KPI_REPS:-3}"); do
    scripts/gv-leg.sh "${TAG}-celtium-r$rep" "$JARS" "$SCENE" "$GPU" "$SECONDS_" "${COMMON[@]}" "$@" >/dev/null 2>&1
    CELTIUM_LEG_REFERENCE_RENDERER="$REFERENCE" \
        scripts/gv-leg.sh "${TAG}-celeritas-r$rep" "$JARS" "$SCENE" "$GPU" "$SECONDS_" "${COMMON[@]}" >/dev/null 2>&1
done

python3 - "$TAG" "${CELTIUM_KPI_REPS:-3}" <<'PY'
import json, statistics, sys
tag, reps = sys.argv[1], int(sys.argv[2])


def arm(name):
    p50s, p95s, verdicts, counts = [], [], set(), []
    for rep in range(1, reps + 1):
        path = '.gv-verify/run-%s-%s-r%d/logs/celtium-validation.json' % (tag, name, rep)
        try:
            with open(path, encoding='utf-8') as handle:
                data = json.load(handle)
        except OSError:
            continue
        p50s.append(data.get('frameIntervalP50Ms') or 0.0)
        p95s.append(data.get('frameIntervalP95Ms') or 0.0)
        counts.append(data.get('frameIntervalSampleCount') or 0)
        verdicts.add(data.get('presentedImageVerdict'))
    if not p50s:
        return None
    return (statistics.median(p50s), statistics.median(p95s), min(p50s), max(p50s), len(p50s),
            ','.join(sorted(str(v) for v in verdicts)), sum(counts))


rows = {name: arm(name) for name in ('celtium', 'celeritas')}
for name in ('celtium', 'celeritas'):
    row = rows[name]
    if row is None:
        print('  %-9s MISSING' % name)
        continue
    print('  %-9s p50 %7.3f ms (runs %s..%s)  p95 %7.3f ms  frames %5s  %s'
          % (name, row[0], round(row[2], 3), round(row[3], 3), row[1], row[6], row[5]))
if rows['celtium'] and rows['celeritas']:
    ratio = rows['celtium'][0] / (rows['celeritas'][0] or 1.0)
    print('  ratio     %.2fx over %d pair(s)  (%s)'
          % (ratio, rows['celtium'][4],
             'PASS: not slower than the reference' if ratio <= 1.0
             else 'FAIL: %.0f%% slower than the reference' % ((ratio - 1.0) * 100.0)))
    sys.exit(0 if ratio <= 1.0 else 1)
sys.exit(2)
PY
