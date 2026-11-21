#!/usr/bin/env python3
"""Runs GlyphAddressCheck, the CPU contract for the glyph capture's pointer and byte layout.

MemoryUtil.memAddress(ByteBuffer) returns the buffer's base address plus its POSITION. The glyph capture
filled its vertex buffer and then took the address without flipping, so the position equalled the capacity
and the backend was handed a pointer one past the last written byte: glyphs were accepted and the text was
missing or garbage, with no validation error. The check exercises both vertex layouts the capture offers
(POSITION_TEX_COLOR stride 24, POSITION_COLOR stride 16), reads every field back from the offered address,
and demonstrates the unflipped-address defect itself. Until now nothing ran it: it was referenced by no
script, no workflow and no test (roam-hunt-4, QUEUE 640).

The check touches no production class - it pins LWJGL's addressing semantics and the layout arithmetic - so
unlike the texture and font contracts (which moved into the Gradle tests, where the built classes exist) it
stays a standalone probe-job check. What it needs beyond a JDK 21 is the LWJGL core jar and its natives jar,
at the version the project itself builds against (celtium_mod_lwjgl_version in gradle.properties). The
resolution order is: the local Gradle cache first (the exact artifacts the project's own resolution fetched),
then Maven Central into ~/.cache/celtium-cpu-contract, with the SHA-256 of the 3.4.3 artifacts pinned below
(measured from the project's Gradle cache). A different version is refused rather than fetched unpinned:
bump the pins with the version.
"""
import hashlib
import os
import pathlib
import platform
import shutil
import subprocess
import sys
import tempfile
import urllib.request

ROOT = pathlib.Path(__file__).resolve().parents[2]
CHECK = ROOT / "scripts/cpu-contract/GlyphAddressCheck.java"
MAVEN = "https://repo1.maven.org/maven2/org/lwjgl/lwjgl"

# SHA-256 of the pinned LWJGL artifacts, measured from the project's Gradle cache (which resolved them from
# Maven Central under the project's own dependency resolution).
PINNED = {
    ("3.4.3", "lwjgl-3.4.3.jar"):
        "46eeca5471833c3cf5da3c1da015b41e3bb3eb16dd3da03f366886da10096751",
    ("3.4.3", "lwjgl-3.4.3-natives-linux.jar"):
        "d719e545a6db4ea87eb64d55630ba628755572bb5b56d51a1548347b5354cadb",
}


def lwjgl_version():
    properties = ROOT / "gradle.properties"
    if properties.is_file():
        for line in properties.read_text().splitlines():
            if line.startswith("celtium_mod_lwjgl_version="):
                return line.split("=", 1)[1].strip()
    return "3.4.3"


def natives_classifier():
    # The same mapping celtium-vulkan/build.gradle uses for its test natives.
    system = platform.system().lower()
    arm = platform.machine().lower() in ("aarch64", "arm64")
    if system == "windows":
        return "natives-windows"
    if system == "darwin":
        return "natives-macos-arm64" if arm else "natives-macos"
    return "natives-linux-arm64" if arm else "natives-linux"


def sha256(path):
    digest = hashlib.sha256()
    with open(path, "rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def resolve(version, filename):
    """Finds one LWJGL artifact: Gradle cache, then the download cache, then Maven Central (pinned only)."""
    cached = list((pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/org.lwjgl/lwjgl"
                   / version).glob("*/" + filename))
    if cached:
        return cached[0]
    expected = PINNED.get((version, filename))
    if expected is None:
        print("glyph_address_check: no pinned SHA-256 for LWJGL " + version + " " + filename
              + "; refusing to fetch it unpinned (pin it in this wrapper when the version moves)")
        return None
    cache_dir = pathlib.Path.home() / ".cache/celtium-cpu-contract" / version
    target = cache_dir / filename
    if target.is_file() and sha256(target) == expected:
        return target
    cache_dir.mkdir(parents=True, exist_ok=True)
    url = MAVEN + "/" + version + "/" + filename
    print("glyph_address_check: downloading " + url)
    try:
        with urllib.request.urlopen(url) as response, open(target, "wb") as out:
            shutil.copyfileobj(response, out)
    except Exception as error:  # the download failing is a check failure, not a pass
        print("glyph_address_check: download failed: " + str(error))
        return None
    if sha256(target) != expected:
        print("glyph_address_check: SHA-256 mismatch for " + filename + "; refusing to run against it")
        target.unlink()
        return None
    return target


def javac():
    home = os.environ.get("JAVA_HOME")
    if home and pathlib.Path(home, "bin", "javac").is_file():
        return str(pathlib.Path(home, "bin", "javac")), str(pathlib.Path(home, "bin", "java"))
    found = shutil.which("javac")
    if found:
        return found, str(pathlib.Path(found).with_name("java"))
    return None, None


def main():
    compiler, runner = javac()
    if compiler is None:
        print("glyph_address_check: no javac found (JAVA_HOME or PATH); the check cannot run")
        return 1
    version = lwjgl_version()
    core = resolve(version, "lwjgl-" + version + ".jar")
    natives = resolve(version, "lwjgl-" + version + "-" + natives_classifier() + ".jar")
    if core is None or natives is None:
        return 1
    classpath = str(core) + os.pathsep + str(natives)
    with tempfile.TemporaryDirectory() as tmp:
        compile_result = subprocess.run(
            [compiler, "-nowarn", "-cp", classpath, "-d", tmp, str(CHECK)],
            capture_output=True, text=True)
        if compile_result.returncode != 0:
            print("glyph_address_check: compilation failed")
            print(compile_result.stdout)
            print(compile_result.stderr)
            return 1
        run_result = subprocess.run([runner, "-cp", tmp + os.pathsep + classpath, "GlyphAddressCheck"],
                                    capture_output=True, text=True)
        sys.stdout.write(run_result.stdout)
        sys.stderr.write(run_result.stderr)
        if run_result.returncode != 0:
            print("glyph_address_check: FAIL")
            return 1
    print("glyph_address_check: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
