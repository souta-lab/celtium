#!/usr/bin/env bash
# gv-leg.sh -- one GPU leg (受入の1本). **このファイルが正典**（git 管理下）。
#
# 2026-10-17 の監査より前、leg ランナーは `.gv-verify/gv-leg.sh` にしか無く未追跡だった
# （worktree を失えば受入の手順ごと消える）。さらに `.gv-verify/prism_validation.py` は
# `scripts/prism_validation.py` の**古い分岐**（631行 vs 682行・`--copy-pack-dir` が無い）で、
# leg はそちらを走らせていた。ここでは追跡側のハーネスを使う。
#
# 前提（この機械の外では自分で用意する）:
#   - $ROOT/.gv-verify/lib/lwjgl-vulkan-3.4.1.jar   … LWJGL の Vulkan バインディング
#   - $ROOT/.celtium-work/ 配下の instance（cohort は PrismLauncher の cleanroom-0.6.13-alpha）
#   - 環境変数で差し替え可: CELTIUM_ROOT / CELTIUM_INSTANCE_{COHORT,E2E,RECIPE}
#
# gv-leg.sh -- one GPU leg for round 721 verification (gpu-verifier / task-3).
#
#   gv-leg.sh <leg-name> <jar-dir> <mode> <gpu> [duration] [extra-jvm-arg]
#
# <jar-dir> must hold the two jars under their build names:
#   celtium-forge-mc1.12.2-*.jar  and  celtium-validation-client-*.jar
# The names are the ones the harness and the fixture expect; renaming them made the
# run's fixture field read as absent in the first attempt, so they stay as built.
#
# mode: cohort (7-mod cohort, cleanroom instance) | plain | e2e (268-mod staged pack)
# gpu : amd | intel
#
# Serialises on .celtium-work/gpu.lock (mkdir loop) and refuses to start while a
# foundation.boot.Foundation process is alive. Records the jar hashes it measured and
# verifies them against the run directory's mods/ *before* reading any result.
set -u

LEG="${1:?leg name}"
JARDIR="${2:?jar dir}"
MODE="${3:?mode}"
GPU="${4:?gpu}"
DUR="${5:-900}"
# Every argument after the duration is forwarded. One was the old shape, and it silently dropped the rest:
# the RLCraft Nether leg needs both dimensionAtTick and dimension, and only the first arrived, so the
# run came back as a terrain leg that passed.
EXTRA=("${@:6}")

ROOT="${CELTIUM_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
GV=$ROOT/.gv-verify
# 正典は追跡されている scripts/prism_validation.py。$GV 側のコピーは古い分岐なので使わない。
HARNESS=$ROOT/scripts/prism_validation.py
LIB=$GV/lib/lwjgl-vulkan-3.4.1.jar
if [ ! -f "$LIB" ]; then
  echo "FATAL: $LIB が無い。LWJGL 3.4.1 の Vulkan バインディングを置く（README の検証節を参照）。" >&2
  exit 5
fi
if [ ! -f "$HARNESS" ]; then
  echo "FATAL: $HARNESS が無い。scripts/prism_validation.py は追跡ファイル。" >&2
  exit 5
fi
GPU_LOCK=$ROOT/.celtium-work/gpu.lock
OUT=$GV/$LEG.out
LOCKJSON=$GV/$LEG-lock.json
GD=$GV/run-$LEG

JAR=$(ls "$JARDIR"/celtium-forge-*.jar 2>/dev/null | head -1)
FIX=$(ls "$JARDIR"/celtium-validation-client-*.jar 2>/dev/null | head -1)

case "$MODE" in
  cohort|plain)
    INSTANCE="${CELTIUM_INSTANCE_COHORT:-$HOME/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha}"
    ;;
  e2e)
    INSTANCE="${CELTIUM_INSTANCE_E2E:-$ROOT/.celtium-work/modpack-p0/prism-e2e-private/instances/e2e-1.93a-no-stellar-parallel}"
    ;;
  recipe)
    # RLCraft 2.9.3, the Cleanroom migration recipe applied (165 mods), staged under its own profile with
    # its own libraries and assets. This is the goal's second complete-modpack target: the mods come from
    # the profile's own instance, so the mode adds no --mod list of its own. Measured when it was built:
    # P1 PASSED on AMD and Intel (leg7-recipe, leg8-intel). Re-run after any change to a hot path.
    INSTANCE="${CELTIUM_INSTANCE_RECIPE:-$ROOT/.celtium-work/modpack-p0/recipe-profile/instances/rlcraft-2.9.3-recipe}"
    ;;
  *) echo "bad mode $MODE" >&2; exit 2 ;;
esac

case "$GPU" in
  amd)
    RENDER=/dev/dri/by-path/pci-0000:03:00.0-render
    REQUIRE='RX 6400'
    unset DRI_PRIME MESA_VK_DEVICE_SELECT
    ;;
  intel)
    # AGENTS.md: the render node alone does not select the device; Mesa's device
    # selection does, and the harness passes the environment through.
    RENDER=/dev/dri/by-path/pci-0000:00:02.0-render
    REQUIRE='UHD Graphics 630'
    export DRI_PRIME=pci-0000_00_02_0!
    ;;
  *) echo "bad gpu $GPU" >&2; exit 2 ;;
esac

# ---- single-game discipline -------------------------------------------------
for _ in $(seq 1 480); do mkdir "$GPU_LOCK" 2>/dev/null && break; sleep 5; done
if [ ! -d "$GPU_LOCK" ]; then echo "FATAL: could not take $GPU_LOCK" >&2; exit 3; fi
cleanup() { rmdir "$GPU_LOCK" 2>/dev/null; }
trap cleanup EXIT
game_busy() {
  for jp in $(pgrep -x java 2>/dev/null); do
    tr '\0' '\n' < /proc/$jp/cmdline 2>/dev/null | grep -q foundation.boot.Foundation && return 0
  done
  return 1
}
for _ in $(seq 1 240); do game_busy || break; sleep 15; done
if game_busy; then echo "FATAL: another foundation instance is running" >&2; exit 4; fi

# ---- jars under test --------------------------------------------------------
[ -f "${JAR:-}" ] || { echo "FATAL: no celtium jar in $JARDIR" >&2; exit 5; }
[ -f "${FIX:-}" ] || { echo "FATAL: no fixture jar in $JARDIR" >&2; exit 5; }
JAR_SHA=$(sha256sum "$JAR" | cut -d' ' -f1)
FIX_SHA=$(sha256sum "$FIX" | cut -d' ' -f1)
HARNESS_SHA=$(sha256sum "$HARNESS" | cut -d' ' -f1)
echo "=== $LEG mode=$MODE gpu=$GPU $(date -Is)"
echo "    celtium=$(basename "$JAR") $JAR_SHA"
echo "    fixture=$(basename "$FIX") $FIX_SHA"
echo "    harness=$HARNESS_SHA"

rm -rf "$GD"
rm -f "$OUT" "$LOCKJSON"
# the frozen harness imports its sibling headless_wayland module; pin sys.path
# explicitly rather than relying on how python resolves the script directory.
export PYTHONPATH="$GV${PYTHONPATH:+:$PYTHONPATH}"
MODARGS=()
case "$MODE" in
  cohort)
    COHORT=$ROOT/.celtium-work/modcohort
    for m in "$COHORT"/*.jar; do MODARGS+=(--mod "$m"); done
    for m in "${CLASH_DIR:-/nonexistent}"/*.jar; do [ -e "$m" ] && MODARGS+=(--mod "$m"); done
    ;;
  e2e)
    # the 268-mod staged pack minus Nothirium/naughthirium (the pr92-stack-scope leg's set).
    # E2E_EXCLUDE is a space-separated list of extra glob patterns, used by the task-6
    # bisect (pack minus the mods that reference org/lwjgl/opengl/Display).
    for m in "$ROOT"/.celtium-work/modpack-p0/e2e-staged/mods/*.jar; do
      base=$(basename "$m")
      skip=0
      for pat in ${E2E_EXCLUDE:-}; do
        case "$base" in $pat) skip=1 ;; esac
      done
      [ "$skip" = 1 ] && continue
      case "$(basename "$m")" in
        Nothirium-*|naughthirium-*) continue ;;
      esac
      MODARGS+=(--mod "$m")
    done
    ;;
esac

# ---- launch, then verify the run directory's mods before reading results ----
# The 268-mod pack needs a bigger heap than the harness default (-Xmx2g): JEI's
# recipe registry dies with OutOfMemoryError during "Initializing game" without it.
# runtime-e2e-268-amd-pr92-stack-scope carried -Xmx5g; the first attempt here did
# not, and the leg failed with exactly that OOM. Later -Xmx wins.
MODE_JVM=()
[ "$MODE" = e2e ] && MODE_JVM=(--jvm-arg=-Xmx5g)
# The recipe profile's 165 mods need the extra heap the e2e set carries (the E2E 1.93a legs measured it).
[ "$MODE" = recipe ] && MODE_JVM=(--jvm-arg=-Xmx5g)

# Forge's splash needs a shared GL context a GLFW_NO_API window cannot provide, and its thread dying
# takes the game down with it. Writing enabled=false into the run's config/splash.properties is the
# recommended fix, but it cannot be done here: creating $GD/config creates $GD, and the harness
# refuses a game directory that already exists ("preserve it and choose a fresh --game-dir"). The
# splash is stubbed by CeltiumGllessSplashTransformer in the coremod chain instead; if that ever
# fails to reach the class, the harness would need a supported way to pre-seed the run's config.
: # (no pre-launch config write here - see the comment above)
timeout 2400 python3 "$HARNESS" "$INSTANCE" \
  --output "$LOCKJSON" \
  --game-dir "$GD" --mod "$JAR" --mod "$FIX" "${MODARGS[@]}" --library "$LIB" --slot-wait 1800 \
  --exclude-instance-mod 'celtium-*pre-stub-changes.jar' \
  "${MODE_JVM[@]}" \
  --jvm-arg=-Dceltium.vulkan.glless=true --jvm-arg=-Dceltium.vulkan.presentReadback=true \
  --jvm-arg=-Dfml.readTimeout=120 \
  --jvm-arg=-Dceltium.vulkan.validation=true \
  --jvm-arg=-Dceltium.validation=true --jvm-arg=-Dceltium.validation.scene=terrain \
  --jvm-arg=-Dceltium.validation.keepGui=true --jvm-arg=-Dceltium.validation.minWorldFrames=5 \
  --jvm-arg=-Dceltium.validation.pinSections=64 \
  --jvm-arg=-Dceltium.validation.screenshotAtTick=200 \
  --jvm-arg=-Dceltium.validation.dumpPresentedImage=true \
  --jvm-arg=-Dceltium.vulkan.frameDumpDir="$GD/framedump" \
  ${EXTRA[@]/#/--jvm-arg=} \
  --run --validate --duration "$DUR" --headless --force-x11 \
  --render-node "$RENDER" --require-gpu "$REQUIRE" > "$OUT" 2>&1 &
HARNESS_PID=$!

# the harness copies the instance's mods into the game dir at startup; read the
# celtium jars there before the run produces a result (round 705 / round 212).
RUNDIR_JAR=""
for _ in $(seq 1 60); do
  sleep 2
  RUNDIR_JAR=$(ls "$GD"/mods/celtium-forge-*.jar 2>/dev/null | head -1)
  [ -n "$RUNDIR_JAR" ] && break
  kill -0 "$HARNESS_PID" 2>/dev/null || break
done
N_CELTIUM=$(ls "$GD"/mods/celtium-forge-*.jar 2>/dev/null | wc -l)
N_FIXTURE=$(ls "$GD"/mods/celtium-validation-client-*.jar 2>/dev/null | wc -l)
if [ -n "$RUNDIR_JAR" ]; then
  RUNDIR_SHA=$(sha256sum "$RUNDIR_JAR" | cut -d' ' -f1)
  echo "    run-dir celtium=$(basename "$RUNDIR_JAR") $RUNDIR_SHA count=$N_CELTIUM"
  [ "$RUNDIR_SHA" = "$JAR_SHA" ] && echo "    HASH-MATCH: the measured jar is the one named on the command line" \
                                || echo "    HASH-MISMATCH: run dir jar is a different artifact"
else
  echo "    run-dir celtium jar NOT FOUND (run dir $GD)"
fi
echo "    run-dir fixture count=$N_FIXTURE"

wait $HARNESS_PID
RC=$?
echo "=== $LEG done exit=$RC $(date -Is)"
SUMMARY=$(grep -o '{"exitCode".*' "$OUT" | head -1)
if [ -z "$SUMMARY" ]; then
  echo "    NO SUMMARY: the harness produced no result line; last output:"
  tail -5 "$OUT" | sed 's/^/      /'
else
  echo "    summary: $SUMMARY"
fi
exit $RC
