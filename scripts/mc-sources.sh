#!/usr/bin/env bash
# The reference material this project already builds, in one command.
#
# Why this exists: the build had been producing the decompiled Minecraft sources
# (forge122/build/rfg/mcp_patched_minecraft-sources.jar, 7561 files) for weeks, and
# nobody read them. Signatures, draw modes, vertex formats, call order and the SRG
# names were guessed at - and argued about across several rounds - while the answers
# sat on disk. The fix is not a promise to remember; it is that the answers are one
# command away and the command is named in the places people read first.
#
# Usage:
#   scripts/mc-sources.sh                 # extract if needed, print every path
#   scripts/mc-sources.sh --grep PATTERN  # search the Minecraft and Forge sources
#   scripts/mc-sources.sh --srg NAME      # look a name up in the SRG mappings
#
# Exit codes: 0 with the paths; 1 when the material is not there yet (the message
# says which Gradle task produces it).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/.celtium-work/mc-sources"
RFG="$ROOT/forge122/build/rfg"
JAR="$RFG/mcp_patched_minecraft-sources.jar"
FORGE_UNPACKED="${HOME}/.gradle/caches/minecraft/net/minecraftforge/forge/1.12.2-14.23.5.2847/unpacked"

usage() {
    sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

extract() {
    if [ -d "$OUT/net/minecraft" ] && [ -n "$(find "$OUT/net/minecraft" -name '*.java' -print -quit 2>/dev/null)" ]; then
        return 0
    fi
    if [ ! -f "$JAR" ]; then
        cat >&2 <<EOF
mc-sources: the decompiled sources are not built yet.
  expected: $JAR
  produce it with:
    JAVA_HOME=/usr/lib/jvm/java-25-openjdk-amd64 ./gradlew -p build-1122 :forge122:decompileSrgJar --console=plain --no-daemon \\
      "-Porg.gradle.java.installations.paths=\$JAVA_HOME,\$PWD/.celtium-work/jdks/21/jdk-21.0.12.1+1,\$PWD/.celtium-work/jdks/8/jdk8u504-b01"
  (RFG writes that jar as part of an ordinary :forge122:packageJar as well, so a normal
   build usually leaves it in place.)
EOF
        return 1
    fi
    mkdir -p "$OUT"
    # Only the Minecraft tree: the Forge side is read from the MCP cache, which is
    # already extracted and stays in sync with the same Forge version.
    (cd "$OUT" && unzip -qo "$JAR" 'net/minecraft/*')
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
esac

extract || exit 1

MINECRAFT_FILES="$(find "$OUT/net/minecraft" -name '*.java' | wc -l)"
FORGE_SRC="$FORGE_UNPACKED/src/main/java"
# The real mapping tables live with MCP, not in the Forge unpacked directory: merged.srg
# there is a 423-byte fragment, and pointing at it is how a name lookup "found nothing"
# while the answer was one directory away. mcp-srg.srg is deobf -> SRG (what a mixin
# target has to be written as); srg-mcp.srg is the reverse (what an SRG name means).
SRG_DIR="${HOME}/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/39/rfg_srgs"
SRG="$SRG_DIR/mcp-srg.srg"
SRG_REVERSE="$SRG_DIR/srg-mcp.srg"
PATCHES="$FORGE_UNPACKED/patches.zip"

case "${1:-}" in
    --grep)
        shift
        [ $# -ge 1 ] || { echo "mc-sources --grep needs a pattern" >&2; exit 2; }
        grep -rn --include='*.java' "$@" "$OUT/net/minecraft" ${FORGE_SRC:+"$FORGE_SRC"} 2>/dev/null || true
        ;;
    --srg)
        shift
        [ $# -ge 1 ] || { echo "mc-sources --srg needs a name" >&2; exit 2; }
        if [ -f "$SRG" ]; then
            found=0
            # Both directions: a deobf name finds its SRG name, an SRG name finds the
            # deobf one, and the caller does not have to know which they hold.
            if grep -m20 "/$1 " "$SRG" 2>/dev/null; then found=1; fi
            if [ -f "$SRG_REVERSE" ] && grep -m20 "/$1 " "$SRG_REVERSE" 2>/dev/null; then found=1; fi
            [ "$found" = 1 ] || echo "no SRG entry for $1 (searched $SRG and $SRG_REVERSE)"
        else
            echo "mc-sources: no SRG file at $SRG" >&2
            exit 1
        fi
        ;;
    *)
        cat <<EOF
mc-sources: reference material for this project, already built.

Minecraft (decompiled, patched)   $OUT/net/minecraft        ($MINECRAFT_FILES .java files)
  the client renderer, the shaders, the GUI, the font:
    $OUT/net/minecraft/client/renderer/RenderGlobal.java
    $OUT/net/minecraft/client/renderer/EntityRenderer.java
    $OUT/net/minecraft/client/gui/FontRenderer.java
    $OUT/net/minecraft/client/shader/ShaderGroup.java

Forge sources                     ${FORGE_SRC:-<missing>}
SRG mappings (deobf -> SRG)       ${SRG:-<missing>}
SRG mappings (SRG -> deobf)       ${SRG_REVERSE:-<missing>}
MCP patches                       ${PATCHES:-<missing>}

Search it:   scripts/mc-sources.sh --grep 'begin(7, DefaultVertexFormats'
SRG lookup:  scripts/mc-sources.sh --srg func_181679_a

Do not guess a signature, a draw mode, a vertex format or a call order: read it here
first. The sources are extracted on demand from the jar the build produces, so this
directory is disposable - run the command again if it is gone.
EOF
        ;;
esac
