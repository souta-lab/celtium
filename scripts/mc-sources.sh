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

# The runtime is Cleanroom, not the MCP tree this build compiles against, and Cleanroom
# patches Minecraft (539 files). Reading the unpatched sources and then checking, one
# patch at a time, whether the code you just read is the code that runs is the same
# mistake as reading no sources at all - so the patched tree is what this command
# offers by default, and the unpatched tree is the fallback for what Cleanroom leaves
# alone.
CLEANROOM_REPO="https://github.com/CleanroomMC/Cleanroom.git"
CLEANROOM_PATCHES="$ROOT/.celtium-work/cleanroom-patches"
CLEANROOM_SRC="$ROOT/.celtium-work/mc-sources-cleanroom"

fetch_cleanroom_patches() {
    if [ -d "$CLEANROOM_PATCHES/module/minecraft/patches" ]; then
        return 0
    fi
    mkdir -p "$(dirname "$CLEANROOM_PATCHES")"
    git clone --filter=blob:none --no-checkout --depth 1 "$CLEANROOM_REPO" "$CLEANROOM_PATCHES" >/dev/null 2>&1 || return 1
    (cd "$CLEANROOM_PATCHES" && git sparse-checkout init --cone >/dev/null 2>&1 \
        && git sparse-checkout set module/minecraft/patches >/dev/null 2>&1 \
        && git checkout >/dev/null 2>&1) || return 1
    [ -d "$CLEANROOM_PATCHES/module/minecraft/patches" ]
}

# The extracted tree is ALREADY the patched tree, and this is the measurement that says
# so rather than a claim: Cleanroom's module/minecraft/patches is the same patch set this
# build applies through RetroFuturaGradle (Cleanroom is a Forge fork and keeps Forge's
# Minecraft patches), so applying them again reports "reversed or previously applied" for
# files it touches, and line-offset failures for files where the patch and the tree are
# the same content in a different order. Checked on 2026-10-11: FontRenderer.java.patch
# applies in reverse (17/17 hunks already present), and the added lines of
# RenderGlobal.java.patch and GuiIngame.java.patch are present verbatim in the tree.
#
# What is NOT in the tree is Cleanroom's own runtime patching - Kirino's classes inside
# cleanroom-0.6.13-alpha.jar, which inject into obfuscated Minecraft at launch
# (MinecraftCulling.collectEntitiesInView(Entity, ICamera, ChunkProviderClient, float),
# MinecraftEntityRendering, MinecraftTESRRendering.renderTESRs(...)). Those are read from
# the jar, and their obfuscated type names are mapped with --notch below.
report_cleanroom() {
    fetch_cleanroom_patches || {
        echo "mc-sources: could not fetch Cleanroom's patches from $CLEANROOM_REPO" >&2
        return 1
    }
    local patches
    patches="$(find "$CLEANROOM_PATCHES/module/minecraft/patches" -name '*.patch' | wc -l)"
    echo "Cleanroom patch set          $CLEANROOM_PATCHES/module/minecraft/patches ($patches files)"
    echo "  The extracted tree already contains this set: it is the patch set the build applies."
    echo "  Files it touches: client=$(find "$CLEANROOM_PATCHES/module/minecraft/patches/net/minecraft/client" -name '*.patch' | wc -l),"
    echo "    renderer=$(find "$CLEANROOM_PATCHES/module/minecraft/patches/net/minecraft/client/renderer" -name '*.patch' | wc -l)."
    local cleanroom_jar
    cleanroom_jar="$(ls "$HOME"/.local/share/PrismLauncher/libraries/com/cleanroommc/cleanroom/*/cleanroom-*.jar 2>/dev/null | head -1)"
    if [ -n "$cleanroom_jar" ]; then
        echo "Cleanroom's own runtime patches  $cleanroom_jar"
        unzip -l "$cleanroom_jar" 2>/dev/null | grep -oE "com/cleanroommc/kirino/engine/render/platform/minecraft/patch/[A-Za-z$]+.class" | sort -u | sed 's/^/    /'
    fi
}

# Obfuscated (notch) names are what Cleanroom's runtime patches are compiled against, so
# reading them means mapping them: vg is Entity, bxy is ICamera, brx is ChunkProviderClient.
notch_lookup() {
    local srg_dir="${HOME}/.gradle/caches/minecraft/de/oceanlabs/mcp/mcp_stable/39/rfg_srgs"
    if [ ! -f "$srg_dir/notch-mcp.srg" ]; then
        echo "mc-sources: no notch-mcp.srg at $srg_dir" >&2
        return 1
    fi
    for name in "$@"; do
        local mapped
        mapped="$(grep -m1 "^CL: $name " "$srg_dir/notch-mcp.srg" | awk '{print $3}')"
        printf '%-8s %s\n' "$name" "${mapped:-<not a class in this mapping>}"
    done
}

case "${1:-}" in
    -h|--help) usage; exit 0 ;;
    --cleanroom)
        extract || exit 1
        report_cleanroom || exit 1
        exit 0
        ;;
    --notch)
        shift
        [ $# -ge 1 ] || { echo "mc-sources --notch needs a name (vg, bxy, ...)" >&2; exit 2; }
        notch_lookup "$@"
        exit 0
        ;;
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

Cleanroom-patched tree:  scripts/mc-sources.sh --cleanroom   (applies Cleanroom's 539 patches)

Search it:   scripts/mc-sources.sh --grep 'begin(7, DefaultVertexFormats'
SRG lookup:  scripts/mc-sources.sh --srg func_181679_a

Do not guess a signature, a draw mode, a vertex format or a call order: read it here
first. The sources are extracted on demand from the jar the build produces, so this
directory is disposable - run the command again if it is gone.
EOF
        ;;
esac
