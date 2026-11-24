package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chain-pair framebuffer must declare as many attachments as the render pass it is created for:
 * the pass's colour count plus its depth (VUID-VkFramebufferCreateInfo-attachmentCount-00876).
 * <p>
 * The pack terrain path builds its pass for the target's colour count
 * ({@code frameVariantRenderPass(targetColors, ...)}) and ensures that many colour targets on the
 * pair, but fetched the framebuffer through the one-colour cache entry, which builds two
 * attachments whatever the pass declares - a two-colour target asked for a three-attachment
 * framebuffer and got a two-attachment one, and the driver faulted after the validation errors.
 * The window path already splits its ask by count ({@code chainPairMulti} for several targets);
 * the pack path's ask must split the same way, and the one-colour ask must keep the legacy entry
 * and its shape exactly.
 * <p>
 * Creating a framebuffer needs a device, so what is pinned here is the split's decision and the
 * wiring's shape: which entry serves which count, that the multi entry assembles exactly the
 * asked number of colour views ahead of the depth, and that the legacy entry still builds over a
 * single colour view.
 */
class ChainPairMultiFramebufferTest {

    @Test
    void oneColourAsksStayOnTheLegacyEntry() {
        assertFalse(VulkanTerrainGpuBackend.celtium$chainPairMultiTarget(0));
        assertFalse(VulkanTerrainGpuBackend.celtium$chainPairMultiTarget(1),
                "one colour target is the legacy chain pair's own shape");
    }

    @Test
    void multiColourAsksGoToTheMultiEntry() {
        assertTrue(VulkanTerrainGpuBackend.celtium$chainPairMultiTarget(2),
                "two colour targets need a three-attachment framebuffer");
        assertTrue(VulkanTerrainGpuBackend.celtium$chainPairMultiTarget(3));
        assertTrue(VulkanTerrainGpuBackend.celtium$chainPairMultiTarget(8), "the pack ledger's full width");
    }

    @Test
    void thePackTerrainFetchPassesItsColourCount() throws IOException {
        String body = methodSlice(backendSource(),
                "long targetRenderPass = frameVariantRenderPass(targetColors, pairClass);", 16000);
        assertTrue(body.contains("framebuffer = ensureChainPairFramebuffer(drawIdentity, targetRenderPass, targetColors);"),
                "the pack terrain path asks for its framebuffer at the pass's own colour count");
    }

    @Test
    void theIdentityCountedAskDispatchesByCount() throws IOException {
        String body = methodSlice(backendSource(),
                "private long ensureChainPairFramebuffer(long identity, long renderPass, int colorCount) {", 2200);
        assertTrue(body.contains("framebufferCache().chainPair(identity, renderPass, glless, this.targetWidth,"),
                "a one-colour ask on an explicit identity delegates to the legacy entry for that identity");
        assertTrue(body.contains("framebufferCache().chainPairMulti(identity, renderPass, glless, colorCount,"),
                "a multi-colour ask on an explicit identity goes to the multi-target entry with its count");
    }

    @Test
    void theCountedAskDispatchesByCount() throws IOException {
        String body = methodSlice(backendSource(),
                "private long ensureChainPairFramebuffer(long renderPass, int colorCount) {", 2200);
        assertTrue(body.contains("return ensureChainPairFramebuffer(renderPass);"),
                "a one-colour ask delegates to the legacy entry");
        assertTrue(body.contains("framebufferCache().chainPairMulti(renderPass, glless, colorCount,"),
                "a multi-colour ask goes to the multi-target entry with its count");
    }

    @Test
    void theLegacyEntryKeepsItsSingleColourShape() throws IOException {
        String body = methodSlice(backendSource(),
                "private long ensureChainPairFramebuffer(long renderPass) {", 600);
        assertTrue(body.contains("framebufferCache().chainPair(renderPass, glless, this.targetWidth,"),
                "the uncounted ask still serves the one-colour entry");
        assertFalse(body.contains(".chainPairMulti(renderPass"),
                "the legacy entry does not grow a multi-target branch");
        String cacheBody = methodSlice(cacheSource(),
                "long chainPair(long renderPass, GllessFrameTarget glless", 3200);
        assertTrue(cacheBody.contains(
                "create(renderPass, glless.chainPairColorView(identity), glless.chainPairDepthView(identity),"),
                "the legacy cache entry still builds over the pair's single colour view and its depth");
    }

    @Test
    void theMultiEntryBuildsTheAskedCountPlusDepth() throws IOException {
        String body = methodSlice(cacheSource(),
                "long chainPairMulti(long renderPass, GllessFrameTarget glless", 3000);
        assertTrue(body.contains("long[] colorViews = new long[colorCount];"),
                "the multi entry assembles exactly the asked number of colour views");
        assertTrue(body.contains("colorViews[slot] = glless.chainPairColorViewAt(identity, slot);"),
                "each slot takes the pair's own view for that slot");
        assertTrue(body.contains("create(renderPass, colorViews, glless.chainPairDepthView(identity),"),
                "the framebuffer is created over those views and the pair's depth");
        String createBody = methodSlice(cacheSource(),
                "private long create(long renderPass, long[] colorViews", 1600);
        assertTrue(createBody.contains("stack.mallocLong(colorViews.length + 1)"),
                "creation declares the colour views plus one depth attachment");
    }

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
    }

    private static String cacheSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FramebufferCache.java"));
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
