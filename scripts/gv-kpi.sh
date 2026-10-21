#!/usr/bin/env bash
# Explicit staged inputs and complete warm windows; the checked implementation is testable Python.
# Usage: CELTIUM_KPI_JARS=stage CELTIUM_KPI_REFERENCE=reference.jar scripts/gv-kpi.sh TAG [SECONDS=240]
set -euo pipefail
exec python3 -B -E "$(dirname -- "$0")/gv_kpi.py" "$@"
