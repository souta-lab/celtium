#!/usr/bin/env python3
"""Exercise the production off-target rendered-texture name resolver.

The off-target copy-back used to ask GL45C.glGetNamedFramebufferAttachmentParameteri
for the colour attachment of the bound framebuffer unconditionally. Under the GL stub
that query is unanswerable by contract (the stub writes GL_NONE for every attachment
pname), so the invalidation silently targeted texture 0. The resolver answers from the
publication census the framebuffer mixin fills at every bind, and there is no other arm:
a future change that reaches for GL45 here must fail this test. The real method body and
the real PublishedFrameTextures are copied verbatim into the harness and executed.
"""
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[2]
BACKEND = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java'
CENSUS = ROOT / 'celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PublishedFrameTextures.java'


def method(source: str, signature: str) -> str:
    pos = source.index(signature)
    start = source.index('{', pos)
    depth, end = 1, start + 1
    while depth:
        if end >= len(source):
            raise RuntimeError('Unterminated method ' + signature)
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[pos:end]


def main():
    backend_source = BACKEND.read_text()
    resolver = method(backend_source,
                      'static int offTargetRenderedTextureName(long identity, PublishedFrameTextures recorded)')
    # The resolver is the only named-attachment query left on the off-target path: the old inline
    # block (glGetInteger + glGetNamedFramebufferAttachmentParameteri + invalidate) is gone, and the
    # call site delegates.
    assert 'offTargetRenderedTextureName(identity, this.publishedTextures)' in backend_source
    assert 'glGetNamedFramebufferAttachmentParameteri' not in resolver
    # The two remaining queries are the framebuffer-adoption probe, which reads the bound framebuffer's
    # own attachment; the off-target name path no longer contains one.
    assert len(re.findall(r'glGetNamedFramebufferAttachmentParameteri', backend_source)) == 2

    harness = '''package org.soutalab.celtium.vulkan;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;
class OffTargetRenderedNameContract {
''' + resolver + '''
    static void assertValue(int actual, int expected, String name) {
        if (actual != expected) throw new AssertionError(name + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        PublishedFrameTextures census = new PublishedFrameTextures();
        // The measured entity-outline target's identity shape: a non-zero framebuffer identity whose
        // bind reported colour texture 42 at 256x256.
        census.bindColorTexture(0x15f76244L, 42, 256, 256);
        // GL-less: the name comes from the recorded association and GL is never asked.
        CeltiumGlStub.installed = new CeltiumGlStub();
        assertValue(offTargetRenderedTextureName(0x15f76244L, census), 42, "gl-less recorded attachment");
        assertValue(offTargetRenderedTextureName(0xdeadbeefL, census), 0, "gl-less unrecorded identity");
        assertValue(offTargetRenderedTextureName(0L, census), 0, "gl-less identity zero");
        assertValue(org.lwjgl.opengl.GL45C.calls, 0, "GL45C queries under the stub");
        assertValue(org.lwjgl.opengl.GL11.calls, 0, "GL11 queries under the stub");
        // A bind reporting no colour attachment clears the record; the resolver then answers 0 again.
        census.bindColorTexture(0x15f76244L, 0, 0, 0);
        assertValue(offTargetRenderedTextureName(0x15f76244L, census), 0, "cleared record");
        System.out.println("off-target rendered texture: recorded census only PASS");
    }
}
'''
    census = CENSUS.read_text()
    stub = '''package org.soutalab.celtium.vulkan.gl;
public class CeltiumGlStub {
    public static CeltiumGlStub installed;
    public static boolean isInstalled() { return installed != null; }
}
'''
    gltexture = '''package org.soutalab.celtium.vulkan;
public class VulkanGlTexture {
    public String currentLayoutName() { return "GENERAL"; }
}
'''
    gl11 = '''package org.lwjgl.opengl;
public class GL11 {
    public static int calls;
    public static int nextBinding;
    public static int glGetInteger(int pname) { calls++; return nextBinding; }
}
'''
    gl30 = '''package org.lwjgl.opengl;
public class GL30C {
    public static final int GL_DRAW_FRAMEBUFFER_BINDING = 0x8CA6;
    public static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;
    public static final int GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME = 0x8CD1;
}
'''
    gl45 = '''package org.lwjgl.opengl;
public class GL45C {
    public static int calls;
    public static int lastFbo;
    public static int nextName;
    public static int glGetNamedFramebufferAttachmentParameteri(int fbo, int attachment, int pname) {
        calls++;
        lastFbo = fbo;
        return nextName;
    }
}
'''
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        pkg = root / 'org/soutalab/celtium/vulkan'
        pkg.mkdir(parents=True)
        (pkg / 'OffTargetRenderedNameContract.java').write_text(harness)
        (pkg / 'PublishedFrameTextures.java').write_text(census)
        (pkg / 'VulkanGlTexture.java').write_text(gltexture)
        (pkg / 'gl').mkdir()
        (pkg / 'gl' / 'CeltiumGlStub.java').write_text(stub)
        lwjgl = root / 'org/lwjgl/opengl'
        lwjgl.mkdir(parents=True)
        (lwjgl / 'GL11.java').write_text(gl11)
        (lwjgl / 'GL30C.java').write_text(gl30)
        (lwjgl / 'GL45C.java').write_text(gl45)
        sources = [str(p) for p in root.rglob('*.java')]
        subprocess.run(['javac', '--release', '21', '-d', d] + sources, check=True)
        subprocess.run(['java', '-cp', d, 'org.soutalab.celtium.vulkan.OffTargetRenderedNameContract'],
                       check=True)


if __name__ == '__main__':
    main()
