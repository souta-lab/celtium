#!/usr/bin/env bash
# Build with a lock, because two concurrent --no-daemon builds in this checkout corrupt each other.
#
# Measured: pipelines' build at 14:03:47 raced another worker's identical build and failed with ten errors
# ("ImmediateGroupQueue にアクセスできません", "class file ... is invalid", NoSuchFileException) while every source file
# was present and correct. Gradle's output directory is shared, so the failure is in the build tree, not the code -
# and a "the tree is red" report from that state costs everyone a round. The game slot got a lock for the same reason;
# this is that lock's sibling.
#
# Usage: scripts/build.sh [gradle args...]   (defaults to the two packaging tasks)
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
slot="$root/.celtium-work/build-slot"
mkdir -p "$slot"
lock="$slot/owner.pid"
deadline=$(( $(date +%s) + ${CELTIUM_BUILD_WAIT:-1800} ))

while true; do
    if ( set -o noclobber; echo "$$" > "$lock" ) 2>/dev/null; then
        break
    fi
    holder="$(cat "$lock" 2>/dev/null || echo 0)"
    if [ -n "$holder" ] && [ "$holder" != "0" ] && ! kill -0 "$holder" 2>/dev/null; then
        echo "Reclaiming the build slot from pid $holder, which is gone"
        rm -f "$lock"
        continue
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
        echo "The build slot is held by pid ${holder:-unknown} and CELTIUM_BUILD_WAIT expired" >&2
        exit 1
    fi
    echo "Waiting for the build slot (held by pid ${holder:-unknown}) ..."
    sleep 5
done
trap 'rm -f "$lock"' EXIT

export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-25-openjdk-amd64}"
exec "$root/gradlew" -p "$root/build-1122" "${@:-:forge122:packageJar :forge122:validationRemapJar}" \
    --console=plain --no-daemon \
    "-Porg.gradle.java.installations.paths=$JAVA_HOME,$root/.celtium-work/jdks/21/jdk-21.0.12.1+1,$root/.celtium-work/jdks/8/jdk8u504-b01"
