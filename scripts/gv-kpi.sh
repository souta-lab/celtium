#!/usr/bin/env bash
# Celtium's frame time against Celeritas, measured as one pair in one session.
#
# The KPI is "frame time at or below Celeritas", so the measurement has to be a pair: same scene, same GPU, same flags,
# back to back, with the renderer under test and the reference arm differing only in the jar that draws. Absolute frame
# times move by tens of percent with machine state, so a number without its pair says nothing.
#
# Usage: scripts/gv-kpi.sh TAG [SECONDS=100] [extra -D flags...]
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

scripts/gv-leg.sh "${TAG}-celtium" "$JARS" "$SCENE" "$GPU" "$SECONDS_" "${COMMON[@]}" "$@" >/dev/null 2>&1
CELTIUM_LEG_REFERENCE_RENDERER="$REFERENCE" \
    scripts/gv-leg.sh "${TAG}-celeritas" "$JARS" "$SCENE" "$GPU" "$SECONDS_" "${COMMON[@]}" >/dev/null 2>&1

python3 - "$TAG" <<'PY'
import json, sys
tag = sys.argv[1]
rows = {}
for arm in ('celtium', 'celeritas'):
    path = '.gv-verify/run-%s-%s/logs/celtium-validation.json' % (tag, arm)
    try:
        with open(path, encoding='utf-8') as handle:
            data = json.load(handle)
    except OSError:
        print('  %-9s MISSING (%s)' % (arm, path))
        continue
    rows[arm] = (data.get('frameIntervalP50Ms'), data.get('frameIntervalP95Ms'),
                 data.get('frameIntervalSampleCount'), data.get('presentedImageVerdict'))
for arm in ('celtium', 'celeritas'):
    if arm in rows:
        p50, p95, count, verdict = rows[arm]
        print('  %-9s p50 %7.3f ms  p95 %7.3f ms  frames %5s  %s'
              % (arm, p50 or 0.0, p95 or 0.0, count, verdict))
if 'celtium' in rows and 'celeritas' in rows:
    ratio = (rows['celtium'][0] or 0.0) / (rows['celeritas'][0] or 1.0)
    print('  ratio     %.2fx  (%s)' % (ratio, 'PASS: not slower than the reference' if ratio <= 1.0
                                       else 'FAIL: %.0f%% slower than the reference' % ((ratio - 1.0) * 100.0)))
    sys.exit(0 if ratio <= 1.0 else 1)
sys.exit(2)
PY
