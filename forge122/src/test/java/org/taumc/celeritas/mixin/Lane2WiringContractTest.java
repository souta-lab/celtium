package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * S3-a wiring, pinned at the source seams the offline suite can check (the shape
 * {@code DrawPathLossWiringTest} established): the register's single writer must stay
 * wired at the GlStateManager hooks, and the K6 probe must stay consulted on the
 * uploader's offer path before the backend call. What these seams do inside a running
 * game is covered by the gates' legs; what must not silently regress is the wiring.
 */
class Lane2WiringContractTest {

    private static String read(String repoRelative, String moduleRelative) throws Exception {
        return Files.readString(findRepoFile(repoRelative, moduleRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative, String moduleRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get(moduleRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        Path up = Paths.get("..").resolve(repoRelative);
        if (Files.exists(up)) {
            return up;
        }
        return direct;
    }

    private static String glStateManager() throws Exception {
        return read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/GlStateManagerMixin.java");
    }

    @Test
    void stateMirrorIsFedFromTheStateHooks() throws Exception {
        String mixin = glStateManager();
        String[] feeds = {
                "celtium$lane2().setBlendEnabled(true)",
                "celtium$lane2().setBlendEnabled(false)",
                "celtium$lane2().setDepthWriteEnabled(mask)",
                "celtium$lane2().setDepthTestEnabled(false)",
                "celtium$lane2().setDepthTestEnabled(true)",
                "celtium$lane2().setAlphaFunc(glFunc, reference)",
                "celtium$lane2().setTexture2dEnabled(true)",
                "celtium$lane2().setTexture2dEnabled(false)",
                "celtium$lane2().bindTexture(glTextureId)",
                "celtium$lane2().setColor(red, green, blue, alpha)",
                "celtium$lane2().setDepthFunc(func)",
                "celtium$lane2().setCullEnabled(true)",
                "celtium$lane2().setCullEnabled(false)",
                "celtium$lane2().setCullFace(face)",
                "celtium$lane2().setBlendFunc(sourceFactor, destinationFactor)",
                "celtium$lane2().setBlendFuncSeparate(sourceRgb, destRgb, sourceAlpha, destAlpha)",
                "celtium$lane2().setBlendFuncSeparate(src, dst, srcAlpha, dstAlpha)",
                "celtium$lane2().setColorMask(red, green, blue, alpha)",
                "celtium$lane2().pushMatrix()",
                "celtium$lane2().popMatrix()",
                "celtium$lane2().setMatrixMode(mode)",
                "celtium$lane2().loadIdentity()",
                "celtium$lane2().ortho(left, right, bottom, top, near, far)",
                "celtium$lane2().setActiveTextureUnit(texture",
                "celtium$lane2().translate(x, y, z)",
                "celtium$lane2().scale(x, y, z)",
                "celtium$lane2().rotate(angle, x, y, z)",
                "celtium$lane2().multMatrix(matrix)",
        };
        for (String feed : feeds) {
            assertTrue(mixin.contains(feed), "missing Lane 2 mirror feed: " + feed);
        }
        assertTrue(mixin.contains("Lane2StateMirror.current()"),
                "the mirror accessor must resolve the singleton register owner");
    }

    @Test
    void uploaderReadsStateFromTheRegisterOnly() throws Exception {
        String uploader = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java");
        // G2-07 / G2-14 (NEON ruling 2026-11-28): the uploader's state reads ride the
        // shared register only - no old-frontend or converter references, no GL query
        // fallback, and no writes to the compiling-list id from the reading lane.
        String[] forbidden = {
                "GlsmFrontend",
                "org.soutalab.celtium.vulkan.gl",
                "org.soutalab.celtium.glcompat",
                "GL_LIST_INDEX",
                "celtium$getInteger",
                "setCompilingListId",
        };
        for (String word : forbidden) {
            assertFalse(uploader.contains(word), "forbidden reference in the uploader mixin: " + word);
        }
        assertTrue(uploader.contains("compilingListId()"),
                "the compiling-list read must ride the register");
        assertTrue(uploader.contains("boundTextureId()"),
                "the bound-texture read must ride the register");
        assertTrue(uploader.contains("key().activeTextureUnit()"),
                "the active-unit read must compose from the register key");
    }

    @Test
    void k6ProbeIsConsultedBeforeTheBackendOffer() throws Exception {
        String uploader = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java");
        int probe = uploader.indexOf("BufferBuilderIntegrityProbe.decide(");
        int offer = uploader.indexOf("backend.submitImmediateBatch(");
        assertTrue(probe >= 0, "the K6 probe decision must be consulted on the offer path");
        assertTrue(offer > probe, "the probe must run before the batch is offered");
        assertTrue(uploader.contains(".hasShadowStorage(buffer.getClass(), BufferBuilder.class)"),
                "the per-class shadow-storage scan must feed the decision");
        assertTrue(uploader.contains("CELTIUM_K6 path=ONE_COPY_SIMD"),
                "a diversion must leave a bounded CELTIUM_K6 evidence line");
        assertTrue(uploader.contains("CopyPath.ONE_COPY_SIMD"),
                "the one-copy path must be reachable from the offer");
    }
}
