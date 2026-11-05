#!/usr/bin/env bash
# CPU profile of one world leg's warm window (fixture ticks 400..600) with async-profiler.
# Usage: scripts/warm-profile.sh LEG STAGE_DIR [GPU=amd] [extra JVM args...]
#   CELTIUM_LEG_REFERENCE_RENDERER=<jar> profiles the reference renderer instead (same fixture, same flags).
#   ASPROF=<path to asprof> (default: .celtium-work/tools/async-profiler-4.6-linux-x64/bin/asprof)
# Output: .gv-verify/LEG.collapsed (async-profiler collapsed stacks) and .gv-verify/LEG.asprof.txt.
# Why each step is the way it is: docs/development.md, "warm窓だけをasync-profilerで取る".
set -u
leg=${1:?leg name}; stage=${2:?stage dir}; gpu=${3:-amd}; shift 3 2>/dev/null || shift $#
root=$(cd "$(dirname -- "$0")/.." && pwd); cd "$root"
as=${ASPROF:-$root/.celtium-work/tools/async-profiler-4.6-linux-x64/bin/asprof}
[ -x "$as" ] || { echo "asprof not found: $as" >&2; exit 2; }
[ -e ".gv-verify/run-$leg" ] && { echo "run-$leg exists; use a fresh leg name" >&2; exit 2; }
scripts/gv-leg.sh "$leg" "$stage" world "$gpu" 300 \
  -Dceltium.vulkan.validation=false -Dceltium.vulkan.presentReadback=false -Dceltium.vulkan.presentedDumpEvery=0 \
  -Dceltium.validation.warmFromTick=400 -Dceltium.validation.warmToTick=600 -Dceltium.validation.holdUntilTick=620 \
  "$@" > ".gv-verify/$leg.log" 2>&1 &
legpid=$!
log=".gv-verify/run-$leg/logs/latest.log"
for _ in $(seq 1 400); do grep -q 'CELTIUM_WARM_WINDOW begin' "$log" 2>/dev/null && break; kill -0 $legpid 2>/dev/null || break; sleep 1; done
pid=""; best=0
for p in $(pgrep -x java); do
  case "$(readlink /proc/$p/cwd 2>/dev/null)" in *"run-$leg"*)
    rss=$(awk '/VmRSS/{print $2}' /proc/$p/status 2>/dev/null); [ "${rss:-0}" -gt "$best" ] && { best=$rss; pid=$p; };;
  esac
done
if [ -n "$pid" ] && grep -q 'CELTIUM_WARM_WINDOW begin' "$log" 2>/dev/null; then
  "$as" -e itimer -i 1ms -d 10 -o collapsed -f "$root/.gv-verify/$leg.collapsed" "$pid" > ".gv-verify/$leg.asprof.txt" 2>&1
  echo "profiled pid=$pid asprof=$? $(tail -1 .gv-verify/$leg.asprof.txt)"
else
  echo "no warm window or no game JVM (pid=$pid); nothing profiled" >&2
fi
wait $legpid; status=$?
echo "leg exit=$status $(grep -o '"fixtureStatus": "[A-Z_]*"' ".gv-verify/$leg.out" 2>/dev/null)"
exit $status
