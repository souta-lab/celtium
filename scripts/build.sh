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

# The lock names a process *instance*, not just a pid. A pid alone is recycled by the kernel: if a build died
# without cleaning up (kill -9, a dead VM) and its pid now belongs to an unrelated live process, a kill -0
# check alone would wait out the whole CELTIUM_BUILD_WAIT for a slot that is actually free. The start time
# from /proc/<pid>/stat (field 22) identifies the instance; where /proc is unavailable, or for lock files
# written by older versions of this script (pid only), the check degrades to the pid alone.
proc_start_time() {
    local stat
    stat="$(cat "/proc/$1/stat" 2>/dev/null)" || return 0
    stat="${stat##*) }"
    set -- $stat
    if [ "$#" -ge 20 ]; then
        echo "${20}"
    fi
}

proc_state() {
    local stat
    stat="$(cat "/proc/$1/stat" 2>/dev/null)" || return 0
    stat="${stat##*) }"
    echo "${stat%% *}"
}

# Whether the recorded holder is still the process instance that took the lock. $1 is the lock file's
# content: "pid [starttime]".
holder_alive() {
    local pid="${1%% *}" recorded=""
    case "$1" in
        *" "*) recorded="${1#* }" ;;
    esac
    if [ -z "$pid" ] || [ "$pid" = "0" ]; then
        return 1
    fi
    if ! kill -0 "$pid" 2>/dev/null; then
        return 1
    fi
    # A zombie is a finished build whose parent has not reaped it yet (a Python harness that is busy
    # elsewhere, say): it runs nothing and holds nothing, but kill -0 still succeeds on it.
    if [ "$(proc_state "$pid")" = "Z" ]; then
        return 1
    fi
    if [ -n "$recorded" ]; then
        local current
        current="$(proc_start_time "$pid")"
        if [ -n "$current" ] && [ "$current" != "$recorded" ]; then
            return 1
        fi
    fi
    return 0
}

while true; do
    if ( set -o noclobber; echo "$$ $(proc_start_time $$)" > "$lock" ) 2>/dev/null; then
        break
    fi
    holder="$(cat "$lock" 2>/dev/null || echo 0)"
    if ! holder_alive "$holder"; then
        echo "Reclaiming the build slot from pid ${holder%% *}, which is gone or recycled"
        rm -f "$lock"
        continue
    fi
    if [ "$(date +%s)" -ge "$deadline" ]; then
        echo "The build slot is held by pid ${holder%% *} and CELTIUM_BUILD_WAIT expired" >&2
        exit 1
    fi
    echo "Waiting for the build slot (held by pid ${holder%% *}) ..."
    sleep 5
done
trap 'rm -f "$lock"' EXIT

# build-1122/settings.gradle.kts requires JDK 25+ to run Gradle; check standard paths when JAVA_HOME is unset.
if [ -z "${JAVA_HOME:-}" ]; then
    if [ -d /usr/lib/jvm/java-25-openjdk-amd64 ]; then
        JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
    elif [ -d "$HOME/.local/opt/jdk25" ]; then
        JAVA_HOME="$HOME/.local/opt/jdk25"
    else
        JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64
    fi
fi
export JAVA_HOME
# A default has to be two arguments, not one string: `"${@:-a b}"` passes "a b" as a single task name and Gradle answers
# "project 'packageJar ' not found".
if [ "$#" -eq 0 ]; then
    set -- :forge122:packageJar :forge122:validationRemapJar
fi
exec "$root/gradlew" -p "$root/build-1122" "$@" \
    --console=plain --no-daemon \
    "-Porg.gradle.java.installations.paths=$JAVA_HOME,$root/.celtium-work/jdks/21/jdk-21.0.12.1+1,$root/.celtium-work/jdks/8/jdk8u504-b01"
