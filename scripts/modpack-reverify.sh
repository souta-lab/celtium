#!/usr/bin/env bash
# Modpack re-verification on a fixed generated world (docs/modpacks.md, "What verified means").
#
# Usage: scripts/modpack-reverify.sh STAGE_DIR PACK [PACK...]
#   STAGE_DIR  a staged Celtium build (.gv-verify/jars/<tag>, with build-provenance.json)
#   PACK       an E2E_PACKS preset in scripts/gv_leg.py (sf4, atm3, rlcraft, sevtech, nomi, po3, e2ee)
#
# Per pack: one generation leg (Celtium, AMD) writes the world, which is copied into the pack instance as
# saves/celtium-gen; then Celtium on AMD, Celtium on Intel and Celeritas 2.5.0-dev (AMD) all load that same save, so
# mod world generation (not seeded reproducibly by every mod) cannot differ between the arms. Every leg runs with the
# 16-line log cap lifted and the observational refusal reports on; never the fail*/refuse* fault-injection switches.
# Afterwards scripts/pack_verify.py classifies each Celtium leg's logs and diffs its world picture against Celeritas:
#   .gv-verify/w-<pack>-{amd,intel,ref}-logs.json, .gv-verify/w-<pack>-{amd,intel}-picture.json, w-<pack>-*-diff.png
# A leg whose game writes a crash report is stopped (its own process group only) instead of waiting out 2400 s.
set -u
root=$(cd "$(dirname -- "$0")/.." && pwd); cd "$root"
stage=${1:?stage dir}; shift
ref=${CELTIUM_REVERIFY_REFERENCE:-$root/.gv-verify/lib/01-celeritas-forge-mc12.2-2.5.0-dev.jar}
out=${CELTIUM_REVERIFY_LOG:-$root/.gv-verify/modpack-reverify.out}
prism=$HOME/.local/share/PrismLauncher/instances
instance_of() {
  python3 - "$1" <<'EOF'
import sys
sys.path.insert(0, "scripts")
from pathlib import Path
import gv_leg
print(gv_leg.E2E_PACKS[sys.argv[1]]["instance"](Path(".")))
EOF
}
base="-Dceltium.validation.worldType=default -Dceltium.validation.cameraY=100 -Dceltium.validation.fixedWorldTime=true -Dceltium.validation.worldTime=6000 -Dceltium.debug.logLinesPerTag=1000000 -Dceltium.vulkan.frameStats=true -Dceltium.glless.census=true"

runleg() {  # pack leg gpu [extra flags...]
  local pack=$1 leg=$2 gpu=$3; shift 3
  CELTIUM_E2E_PACK=$pack setsid nice -n 15 scripts/gv-leg.sh "$leg" "$stage" e2e "$gpu" 2400 "$@" \
    > ".gv-verify/$leg.log" 2>&1 &
  local pid=$!
  while kill -0 $pid 2>/dev/null; do
    if ls ".gv-verify/run-$leg/crash-reports/"* >/dev/null 2>&1; then
      echo "$leg: crash report, stopping the leg" >> "$out"; sleep 20
      kill -TERM -- -$pid 2>/dev/null; sleep 15; kill -KILL -- -$pid 2>/dev/null
    fi
    sleep 15
  done
  wait $pid; local code=$?
  echo "$leg exit=$code $(grep -o '"fixtureStatus": "[A-Z_]*"\|"validationMessageCount": [0-9]*' ".gv-verify/$leg.out" 2>/dev/null | tr '\n' ' ')" >> "$out"
  rm -rf ".gv-verify/run-$leg/mods" ".gv-verify/run-$leg/cache" ".gv-verify/run-$leg/resourcepacks"
}

for pack in "$@"; do
  inst=$(instance_of "$pack")/minecraft
  rm -rf "$inst/saves/celtium-gen"
  # shellcheck disable=SC2086
  runleg "$pack" "w-$pack-gen" amd $base
  gen=.gv-verify/run-w-$pack-gen/saves/celtium-validation
  if [ ! -d "$gen" ]; then echo "NO WORLD $pack" >> "$out"; continue; fi
  mkdir -p "$inst/saves" && cp -r "$gen" "$inst/saves/celtium-gen"
  flags="$base -Dceltium.validation.saveSlot=celtium-gen"
  # shellcheck disable=SC2086
  runleg "$pack" "w-$pack-amd" amd $flags
  # shellcheck disable=SC2086
  runleg "$pack" "w-$pack-intel" intel $flags
  # shellcheck disable=SC2086
  CELTIUM_LEG_REFERENCE_RENDERER=$ref runleg "$pack" "w-$pack-ref" amd $flags
  for g in amd intel; do
    python3 scripts/pack_verify.py logs ".gv-verify/run-w-$pack-$g" --json ".gv-verify/w-$pack-$g-logs.json" >/dev/null 2>&1
    python3 scripts/pack_verify.py picture ".gv-verify/run-w-$pack-$g" ".gv-verify/run-w-$pack-ref" \
      --out ".gv-verify/w-$pack-$g-diff.png" --json ".gv-verify/w-$pack-$g-picture.json" >/dev/null 2>&1
    # For eyes: a labelled Celtium | Celeritas | difference | 2x crop sheet, embedded in the handoff Markdown.
    mkdir -p docs/evidence/modpack-reverify
    python3 scripts/pack_verify.py review ".gv-verify/run-w-$pack-$g" ".gv-verify/run-w-$pack-ref" \
      --out "docs/evidence/modpack-reverify/$pack-$g.jpg" --title "$pack $g (fixed world, seed 12345678, noon)" >/dev/null 2>&1
  done
  python3 scripts/pack_verify.py logs ".gv-verify/run-w-$pack-ref" --json ".gv-verify/w-$pack-ref-logs.json" >/dev/null 2>&1
  for d in .gv-verify/run-w-"$pack"-*; do rm -rf "$d/saves"; done
  echo "done $pack $(date +%T)" >> "$out"
done
