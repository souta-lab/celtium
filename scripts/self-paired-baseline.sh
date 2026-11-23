#!/usr/bin/env bash
# Self-paired baseline (item 693): 3+ pairs, alternating order, quiet window, exclusions recorded.
# The registered protocol and the driver contract live in self_paired_baseline.py's docstring.
# Usage: scripts/self-paired-baseline.sh run --tag T --scene flight --jar-a A.jar --jar-aprime A.jar --driver-cmd '...'
set -euo pipefail
exec python3 -B -E "$(dirname -- "$0")/self_paired_baseline.py" "$@"
