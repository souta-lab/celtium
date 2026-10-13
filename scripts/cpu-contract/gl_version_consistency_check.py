#!/usr/bin/env python3
"""Execute real GL-less version query methods; reject contradictory GL capabilities.

BH-35's GL-less version boundary is 3.0. Mods can choose different code paths
based on glGetString(GL_VERSION) or glGetIntegerv(GL_MAJOR/MINOR_VERSION).
The production methods are transplanted into a minimal Java harness, rather
than replacing either method with a duplicate implementation.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUB = ROOT / "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java"


def production_method(src: str, signature: str) -> str:
    pos = src.index(signature)
    start = src.index("{", pos)
    depth = 1
    end = start + 1
    while depth:
        if end >= len(src):
            raise ValueError(f"unterminated production method: {signature}")
        depth += (src[end] == "{") - (src[end] == "}")
        end += 1
    return src[pos:end]


def main() -> int:
    src = STUB.read_text(encoding="utf-8")
    string_for = production_method(src, "private long stringFor(int pname)")
    integer_for = production_method(src, "private int getIntegerValue(int pname)")
    java = """import java.util.*;
public class CeltiumGlStub {
    private static class BindingMap { int get(int key) { return -1; } }
    private final BindingMap textureBindings = new BindingMap();
    private int activeTexture, drawFbo, readFbo, compilingList, currentProgram, boundVertexArray;
    private int texBindKey(int target) { return target; }
    private static String celtium$nativeRendererName() { return "test device"; }
    private final Map<Long,String> interned = new HashMap<>();
    private long nextPointer = 1;
    private long intern(String value) {
        long ptr = nextPointer++;
        interned.put(ptr, value);
        return ptr;
    }
    private String queryString(int pname) { return interned.get(stringFor(pname)); }
""" + string_for + "\n" + integer_for + """
    private static void expect(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        CeltiumGlStub stub = new CeltiumGlStub();
        String gl = stub.queryString(0x1F02); // GL_VERSION
        String[] version = gl.split(" ")[0].split("[.]");
        int major = stub.getIntegerValue(0x821B); // GL_MAJOR_VERSION
        int minor = stub.getIntegerValue(0x821C); // GL_MINOR_VERSION
        expect(major == Integer.parseInt(version[0]) && minor == Integer.parseInt(version[1]),
               "inconsistent GL version: string=" + gl + ", integer=" + major + "." + minor);
        expect(major == 3 && minor == 0, "unexpected core GL capability floor");
        expect("1.20".equals(stub.queryString(0x8B8C)), "unsupported GLSL version was advertised");
        expect("".equals(stub.queryString(0x1F03)), "GL_EXTENSIONS should be empty");
        expect(stub.getIntegerValue(0x821D) == 0, "indexed extensions count must match empty extension list");
        expect(stub.getIntegerValue(0x123456) == Integer.MIN_VALUE, "unknown scalar query should be untracked");
        System.out.println("GL-less version contracts: GL_VERSION=" + gl
                + ", integer=" + major + "." + minor
                + ", GLSL=" + stub.queryString(0x8B8C) + ", extensions=0 PASS");
    }
}
"""
    with tempfile.TemporaryDirectory() as d:
        path = Path(d) / "CeltiumGlStub.java"
        path.write_text(java, encoding="utf-8")
        subprocess.run([shutil.which("javac") or "javac", "--release", "21", str(path)], check=True)
        subprocess.run([shutil.which("java") or "java", "-cp", d, "CeltiumGlStub"], check=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
