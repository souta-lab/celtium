#!/usr/bin/env bash
# CPU check: transform real LWJGL native declarations with the GL-less transformer and let the JVM verifier load the
# result. Catches route bytecode that would fail a GL class at runtime - the crash class a build cannot see.
#
# Requires: a completed build (forge122/celtium-vulkan/common classes), JDK 21+, the LWJGL 3.4.3 jars, ASM,
# launchwrapper, and a Sponge Mixin + log4j-api jar (from the Prism library directory by default).
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
out="$root/.celtium-work/cpu-contract/transformer-route-verify"
mkdir -p "$out"

find_one() { { find "$1" -name "$2" -type f 2>/dev/null | grep -v sources | sort -V | tail -1; } || true; }
have() { [ -f "$1" ] || { echo "missing dependency: $1" >&2; exit 2; }; }

classes="$root/forge122/build/classes/java/main:$root/celtium-vulkan/build/classes/java/main:$root/common/build/classes/java/main"
for dir in forge122 celtium-vulkan common; do
    [ -d "$root/$dir/build/classes/java/main" ] || { echo "build the modules first: $dir classes are missing" >&2; exit 2; }
done

modules="$HOME/.gradle/caches/modules-2"
lwjgl_opengl="$(find_one "$modules" 'lwjgl-opengl-3.4.3.jar')"
lwjgl_core="$(find_one "$modules" 'lwjgl-3.4.3.jar')"
asm="$(find_one "$modules" 'asm-9.*.jar')"
asm_tree="$(find_one "$modules" 'asm-tree-9.*.jar')"
fastutil="$(find_one "$modules" 'fastutil-8.*.jar')"
launchwrapper="$(find_one "$modules" 'launchwrapper-*.jar')"
mixin="$(find_one "$HOME/.local/share/PrismLauncher/libraries/org/spongepowered/mixin" 'mixin-*.jar')"
log4j="$(find_one "$HOME/.local/share/PrismLauncher/libraries/org/apache/logging/log4j/log4j-api" 'log4j-api-*.jar')"
# The query phase drives the stub's own dispatch entry, and the stub's static initialiser builds libffi trampolines -
# so the LWJGL natives have to be loadable. They are LWJGL's own library (no GL context and no driver is touched); the
# alternative was a check that skips itself on the machines that need it least.
lwjgl_natives="$(find_one "$modules" 'lwjgl-3.4.3-natives-linux.jar')"
for jar in "$lwjgl_opengl" "$lwjgl_core" "$asm" "$asm_tree" "$fastutil" "$launchwrapper" "$mixin" "$log4j" "$lwjgl_natives"; do
    have "$jar"
done
classpath="$classes:$lwjgl_core:$lwjgl_opengl:$lwjgl_natives:$asm:$asm_tree:$fastutil:$launchwrapper:$mixin:$log4j"

rm -rf "$out/classes"
javac -proc:none -cp "$classpath" -d "$out/classes" "$root/scripts/cpu-contract/TransformerRouteVerifierCheck.java"
java -cp "$out/classes:$classpath" TransformerRouteVerifierCheck "$lwjgl_opengl"
# The legacy immediate-mode routes are installed only when the switch is on, so the default run above never exercises
# them. The second run transforms and loads the same surface with them enabled - the configuration a run that wants
# glBegin geometry actually uses - and checks the same "no route fails the JVM verifier" property.
java -Dceltium.gless.legacyImmediate=true -cp "$out/classes:$classpath" TransformerRouteVerifierCheck "$lwjgl_opengl"
