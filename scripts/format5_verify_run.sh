#!/usr/bin/env bash
# One GL-less allBlocks verification for the format-5 particle fix.
#
# Refuses to start when another game instance is running (the rule is one instance at a time: pgrep -f matches
# waiting shells and deadlocks everyone, so this asks /proc/<pid>/cmdline instead), refuses when the patch is
# not in the source or the jars are older than it, and then prints exactly the counters the mission asks for.
#
#   scripts/format5_verify_run.sh [run-name]
#
# Run name defaults to "particles". Outputs, as the mission's run shape asks:
#   .celtium-work/runtime/<run-name>/            the game directory and its logs
#   .celtium-work/<run-name>.json                the validation output copy
set -uo pipefail

cd "$(dirname "$0")/.." || exit 1
ROOT=$PWD
NAME=${1:-particles}
GD="$ROOT/.celtium-work/runtime/$NAME"
BACKEND="celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"
MOD_JAR="build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar"
CLIENT_JAR="forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar"

for pid in $(pgrep -x java); do
    if tr '\0' '\n' < "/proc/$pid/cmdline" 2>/dev/null | grep -q foundation.boot.Foundation; then
        echo "refusing to start: a game instance is running (pid $pid)" >&2
        exit 2
    fi
done

if ! grep -q "the particle descriptor probe needs a pass and none has run yet" "$BACKEND"; then
    echo "refusing to start: $BACKEND does not carry the format-5 probe fix" >&2
    exit 3
fi
if [ ! -f "$MOD_JAR" ] || [ "$MOD_JAR" -ot "$BACKEND" ]; then
    echo "refusing to start: $MOD_JAR is missing or older than the patched source (rebuild first)" >&2
    exit 3
fi

export VK_LAYER_PATH="$ROOT/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export LD_LIBRARY_PATH="$ROOT/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"

timeout 300 python3 scripts/prism_validation.py /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output ".celtium-work/$NAME.json" --game-dir "$GD" \
  --mod "$MOD_JAR" \
  --mod "$CLIENT_JAR" \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.vulkan.glless=true --jvm-arg=-Dceltium.vulkan.validation=true \
  --jvm-arg=-Dceltium.vulkan.presentReadback=true --jvm-arg=-Dceltium.validation.dumpPresentedImage=true \
  --jvm-arg=-Dceltium.validation.minWorldFrames=5 --jvm-arg=-Dceltium.validation.allBlocks=true \
  --run --validate --duration 110 --headless --force-x11 \
  --render-node /dev/dri/by-path/pci-0000:03:00.0-render

status=$?
echo "prism_validation exit=$status"
python3 scripts/format5_particle_report.py "$GD" || true
exit $status
