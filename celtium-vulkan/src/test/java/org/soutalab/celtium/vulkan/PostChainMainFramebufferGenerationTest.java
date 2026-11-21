package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The frame-target generation is part of the {@code VulkanPostChain} main-framebuffer cache key,
 * pinned as a source contract (audit-vulkan-core F2).
 * <p>
 * The cached framebuffer is built from the frame target's {@code colorView}, but the old key was
 * only (image, width, height). A frame target replaced at the same size can hand back the
 * destroyed image's handle value while its view is a different object, and the cache then returns
 * a framebuffer that names a destroyed view. Every target move arrives through
 * {@code adoptFrameTarget} with a fresh {@code frameTargetGeneration}, so keying on the
 * generation rebuilds against the live view on exactly the moves that can invalidate it. The
 * rebuild itself needs a device, so what is pinned here is the key's shape.
 */
class PostChainMainFramebufferGenerationTest {

    private static String postChainSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanPostChain.java"));
    }

    @Test
    void theCacheRemembersTheGenerationItWasBuiltUnder() throws IOException {
        assertTrue(postChainSource().contains("private long mainFramebufferGeneration;"),
                "the cache key has a generation slot");
    }

    @Test
    void aGenerationChangeRebuildsTheFramebuffer() throws IOException {
        String body = methodSlice(postChainSource(), "private long mainFramebuffer(int width, int height) {", 2600);
        assertTrue(body.contains("this.frameTargetGeneration != this.mainFramebufferGeneration"),
                "the rebuild condition compares the current generation with the cached one");
    }

    @Test
    void creationStampsTheCurrentGeneration() throws IOException {
        String body = methodSlice(postChainSource(), "private long mainFramebuffer(int width, int height) {", 2600);
        int created = body.indexOf("this.mainFramebuffer = handle.get(0);");
        int stamped = body.indexOf("this.mainFramebufferGeneration = this.frameTargetGeneration;");
        assertTrue(created >= 0 && stamped > created,
                "a freshly created framebuffer records the generation it was built under");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
