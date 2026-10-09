#!/usr/bin/env bash
# Same fixture, same flags, same GPU, one after the other: the pinned upstream Celeritas (OpenGL) as the reference and
# the current Celtium jar as the candidate. The two must not run at the same time - concurrent runs starve each other
# and the slower one hits the harness duration cap, which was misread as an Intel regression once already.
set -euo pipefail
case "${1:?usage: run-gl-comparison.sh amd|intel SUFFIX}" in
  amd) node=/dev/dri/by-path/pci-0000:03:00.0-render; gpu='RX 6400' ;;
  intel) node=/dev/dri/by-path/pci-0000:00:02.0-render; gpu='UHD Graphics 630' ;;
  *) exit 2 ;;
esac
suffix="${2:?a suffix naming this comparison is required}"
[[ "$suffix" =~ ^[A-Za-z0-9._-]+$ ]] || exit 2
reference=.celtium-work/upstream-baseline/build-1122/build/libs/2.5.12-upstream.989ebf2e/celeritas-forge-mc12.2-2.5.12-upstream.989ebf2e.jar
candidate=build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar
fixture=forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar
bindings=.celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar
[[ -f "$reference" ]] || { echo "missing pinned upstream jar: $reference" >&2; exit 2; }

export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export VK_LAYER_ENABLES=VK_VALIDATION_FEATURE_ENABLE_SYNCHRONIZATION_VALIDATION_EXT
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"

python3 scripts/prism_validation.py /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output ".celtium-work/p32-glref-$suffix-lock.json" --game-dir ".celtium-work/runtime/p32-glref-$suffix" \
  --mod "$reference" --mod "$fixture" --library "$bindings" \
  --jvm-arg=-Dceltium.validation.weather=true --jvm-arg=-Dceltium.validation.particles=true \
  --run --validate --duration 150 --headless --force-x11 --render-node "$node" --require-gpu "$gpu"

python3 scripts/prism_validation.py /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output ".celtium-work/p32-celtium-$suffix-lock.json" --game-dir ".celtium-work/runtime/p32-celtium-$suffix" \
  --mod "$candidate" --mod "$fixture" --library "$bindings" \
  --jvm-arg=-Dceltium.vulkan.validation=true --jvm-arg=-Dceltium.validation.expectVulkan=true \
  --jvm-arg=-Dceltium.validation.expectVulkanTerrain=true \
  --jvm-arg=-Dceltium.validation.weather=true --jvm-arg=-Dceltium.validation.particles=true \
  --run --validate --duration 150 --headless --force-x11 --render-node "$node" --require-gpu "$gpu"

python3 scripts/compare_validation.py \
  ".celtium-work/runtime/p32-glref-$suffix" ".celtium-work/runtime/p32-celtium-$suffix" \
  --reference-label upstream-989ebf2e --candidate-label celtium-current \
  --tolerance 0 --max-diff-ratio 0.0 \
  --output ".celtium-work/p32-comparison-$suffix.json" || true
python3 - "$suffix" <<'PY'
import hashlib, json, sys
from pathlib import Path
suffix = sys.argv[1]
for label in (f"p32-glref-{suffix}", f"p32-celtium-{suffix}"):
    root = Path(".celtium-work/runtime") / label
    process = json.loads((root / "validation-process.json").read_text())
    shot = root / "screenshots/celtium-validation.png"
    print(label, "| exit", process.get("validationExitCode"), "| clean", process.get("validationClean"),
          "| msgs", process.get("validationMessageCount"), "| fixture", process.get("fixtureStatus"),
          "| png", hashlib.sha256(shot.read_bytes()).hexdigest()[:16] if shot.exists() else None)
report = json.loads(Path(f".celtium-work/p32-comparison-{suffix}.json").read_text())
print("comparison:", json.dumps({k: report.get(k) for k in
      ("status", "problems", "diffPixels", "diffRatio", "maxChannelDifference", "tolerance", "maxDiffRatio")},
      ensure_ascii=False)[:900])
PY
