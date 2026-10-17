#!/usr/bin/env bash
# Tracked native GL-less leg runner. Fresh names only; never deletes old evidence.
# Usage: scripts/gv-leg.sh LEG JAR_DIR {cohort|plain|e2e|recipe} {amd|intel} [SECONDS=900] [JVM args...]
# JAR_DIR must contain exactly one packaged celtium-forge-mc1.12.2-*.jar and one
# celtium-validation-client-*.jar. Each extra -D... or -X... is one quoted argument.
# Requires .gv-verify/lib/lwjgl-vulkan-3.4.1.jar and a local patched Prism instance.
# CELTIUM_ROOT / CELTIUM_INSTANCE_{COHORT,E2E,RECIPE} override machine-local paths.
# Intel device selection is set by the runner; the actual native device is checked.
# The canonical prism_validation.py owns the shared worktree game slot (no gpu.lock).
# Results: .gv-verify/run-LEG/, LEG.out, LEG-lock.json, LEG-manifest.json, LEG-result.json.
# Exit 0 is calibrated acceptance; 2 is inconclusive; failures/timeouts/cancellation are nonzero.
set -eu
exec python3 "$(dirname -- "${BASH_SOURCE[0]}")/gv_leg.py" "$@"
