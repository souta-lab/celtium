#!/usr/bin/env python3
"""Exercise production GL-less named texture queries against a fake GL45 implementation.

A future replacement of GL-less routing with an actual GL45C call must fail the test;
real Java methods are copied verbatim into the harness and called with recorded state.
"""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
STUB = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/gl/CeltiumGlStub.java'
REGISTRY = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTextureRegistry.java'


def method(source: str, signature: str) -> str:
    pos = source.index(signature)
    start = source.index('{', pos)
    depth, end = 1, start + 1
    while depth:
        if end >= len(source):
            raise RuntimeError('Unterminated method '+signature)
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[pos:end]


def main():
    stub_source = STUB.read_text()
    reg_source = REGISTRY.read_text()
    stub_query = method(stub_source, 'public static int textureLevelParameter(int name, int level, int pname)')
    stub_record = method(stub_source, 'private int texLevelParamForName(int name, int level, int pname)')
    reg_level = method(reg_source, 'private static int textureLevelParameter(int glTextureId, int level, int pname)')
    reg_sampler = method(reg_source, 'private static int textureParameter(int glTextureId, int pname)')
    assert len(re.findall(r'textureLevelParameter\(glTextureId, 0,', reg_source)) == 6
    assert len(re.findall(r'GL45C\.glGetTextureLevelParameteri', reg_source)) == 1
    assert len(re.findall(r'GL45C\.glGetTextureParameteri', reg_source)) == 1
    assert 'textureParameter(glTextureId, pname)' in reg_source
    assert 'textureParameter(glTextureId, org.lwjgl.opengl.GL11.GL_TEXTURE_MAG_FILTER)' in reg_source

    stub = '''import java.util.*;
class CeltiumGlStub {
    static CeltiumGlStub installed;
    final Map<Long, int[]> texLevels = new HashMap<>();
    static long texLevelKey(int id, int level) {return ((long)id << 16) | (level & 0xFFL);}
    static boolean isInstalled() {return installed != null;}
    static int textureParameter(int id,int pname) { return (id == 42 && pname == 0x2800) ? 0x2600 : 0x2601; }
'''+stub_query+'\n'+stub_record+'\n}\n'
    registry = '''class VulkanTextureRegistry {
'''+reg_level+'\n'+reg_sampler+'''\n
    static void assertValue(int actual, int expected, String name) {
        if (actual != expected) throw new AssertionError(name+": "+actual+" != "+expected);
    }
    public static void main(String[] args) {
        CeltiumGlStub.installed = new CeltiumGlStub();
        CeltiumGlStub.installed.texLevels.put(CeltiumGlStub.texLevelKey(42,0), new int[]{128,64,0x8058});
        assertValue(textureLevelParameter(42,0,0x1000),128,"width");
        assertValue(textureLevelParameter(42,0,0x1001),64,"height");
        assertValue(textureLevelParameter(42,0,0x1003),0x8058,"format");
        assertValue(textureLevelParameter(43,0,0x1000),0,"unknown texture");
        assertValue(textureLevelParameter(42,1,0x1000),0,"unknown level");
        assertValue(textureParameter(42,0x2800),0x2600,"recorded nearest");
        assertValue(textureParameter(42,0x2802),0x2601,"default wrap");
        // When GL-less is not installed, native GL remains available for interop.
        CeltiumGlStub.installed = null;
        assertValue(textureLevelParameter(42,0,0x1000),777,"interop native width");
        assertValue(textureParameter(42,0x2800),888,"interop native filter");
        System.out.println("GL-less texture registry: recorded extents/filter + interop fallback PASS");
    }
}
'''
    gl = '''package org.lwjgl.opengl;
public class GL45C {
    public static int glGetTextureLevelParameteri(int id,int level,int pname) {
        return 777;
    }
    public static int glGetTextureParameteri(int id,int pname) {
        return 888;
    }
}
'''
    with tempfile.TemporaryDirectory() as d:
        root=Path(d)
        (root/'CeltiumGlStub.java').write_text(stub)
        (root/'VulkanTextureRegistry.java').write_text(registry)
        out=root/'org/lwjgl/opengl/GL45C.java';out.parent.mkdir(parents=True);out.write_text(gl)
        subprocess.run(['javac','--release','21','-d',d,str(root/'CeltiumGlStub.java'),str(root/'VulkanTextureRegistry.java'),str(out)],check=True)
        subprocess.run(['java','-cp',d,'VulkanTextureRegistry'],check=True)


if __name__=='__main__':
    main()
