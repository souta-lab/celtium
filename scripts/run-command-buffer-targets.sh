#!/usr/bin/env bash
# Run from the checkout root. Preserve every run; the Python/fixture/verdict must all succeed.
set -euo pipefail
case "${1:?usage: run-command-buffer-targets.sh amd|intel NEW_RUN_NAME [fail-immediate-submits]}" in
  amd) node=/dev/dri/by-path/pci-0000:03:00.0-render; gpu='RX 6400'; duration=180 ;;
  intel) node=/dev/dri/by-path/pci-0000:00:02.0-render; gpu='UHD Graphics 630'; duration=480 ;;
  *) exit 2 ;;
esac
name="${2:?a new isolated run name is required}"
[[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || exit 2
export VK_LAYER_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/share/vulkan/explicit_layer.d"
export VK_LAYER_VALIDATE_SYNC=1
export LD_LIBRARY_PATH="$PWD/.celtium-work/gpu-tools/extracted/usr/lib/x86_64-linux-gnu"
mkdir -p .celtium-work/sol-command-buffer
python3 scripts/prism_validation.py /home/s0u7a/.local/share/PrismLauncher/instances/cleanroom-0.6.13-alpha \
  --output ".celtium-work/sol-command-buffer/$name-lock.json" \
  --game-dir ".celtium-work/runtime/$name" \
  --mod build-1122/build/libs/2.5.12-celtium.0.1.0-dev.1/celtium-forge-mc1.12.2-2.5.12-celtium.0.1.0-dev.1.jar \
  --mod forge122/build/libs/celtium-validation-client-2.5.12-celtium.0.1.0-dev.1.jar \
  --library .celtium-work/runtime/celtium-headless-interop/test-libraries/lwjgl-vulkan-3.4.1.jar \
  --jvm-arg=-Dceltium.vulkan.validation=true \
  --jvm-arg=-Dceltium.validation.expectVulkan=true \
  --jvm-arg=-Dceltium.validation.expectVulkanTerrain=true \
  --jvm-arg=-Dceltium.validation.commandBufferTargets=true \
  --jvm-arg="-Dceltium.vulkan.failImmediateSubmits=${3:-0}" \
  --run --validate --duration "$duration" --headless --force-x11 --render-node "$node" --require-gpu "$gpu"
python3 - "$name" <<'PY'
import json, sys
from pathlib import Path
root=Path('.celtium-work/runtime')/sys.argv[1]
p=json.loads((root/'validation-process.json').read_text())
d=json.loads((root/'logs/celtium-validation.json').read_text())
assert type(p.get('validationExitCode')) is int and p['validationExitCode']==0, p
assert p.get('exitCode')==0 and p.get('fixtureStatus')=='PASSED' and p.get('validationClean') is True and p.get('validationMessageCount')==0, p
assert p.get('gpuRequirementMet') is True, p
assert d.get('commandBufferTargets',{}).get('passed') is True, d.get('commandBufferTargets')
assert d.get('nativeTerrainVerified') is True, d.get('nativeTerrainVerified')
for name in ('celtium-command-buffer-main-before.png','celtium-command-buffer-main.png','celtium-command-buffer-secondary.png'):
    assert (root/'screenshots'/name).is_file(), name
print(json.dumps({'process':p,'commandBufferTargets':d['commandBufferTargets']},ensure_ascii=False,indent=2))
PY
