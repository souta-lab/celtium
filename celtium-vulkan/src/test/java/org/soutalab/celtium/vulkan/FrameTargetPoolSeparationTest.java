package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prescription (f), pinned without a device: the GL-less frame target's chain-pair population gets a
 * slab pool of its own, so a shader pack's framebuffer identities cannot starve the bounded image
 * tenants again.
 * <p>
 * The failure this answers was arithmetic, not fragmentation: at the cap-8 refusal the shared
 * device-local image pool sat at 96.4 % occupancy, and 97.8 % of the holding was the frame-target
 * family R-54 had housed into it; raising the cap to 12 only moved the refusal to 773 MB live, because
 * chain pairs are held per framebuffer identity with no count bound and a pack build keeps minting
 * identities until it completes. These pins read the sources so the separation cannot dissolve
 * silently: the context owns a {@code frameTargetImage} pool capped at 48 slabs beside the shared
 * image pool (cap unchanged at 8), the one allocator dispatches on the caller's pool flag, chain pairs
 * and the shared depth store are the dedicated population, the frame itself and the default pair stay
 * in the shared pool, and the dedicated pool is destroyed, collected and summarised with the others.
 */
class FrameTargetPoolSeparationTest {

    @Test
    void theContextOwnsADedicatedFrameTargetImagePoolCappedAt48() throws IOException {
        String factory = methodSlice(contextSource(),
                "public SlabAllocator celtium$frameTargetImageSlabs(int memoryTypeBits)", 1200);
        assertTrue(factory.contains("new SlabAllocator(this, \"frameTargetImage\", type, "
                        + "SlabAllocator.DEFAULT_SLAB_SIZE, 48,"),
                "the dedicated pool is named frameTargetImage, uses the standard 64 MiB slab and caps at 48");
        assertTrue(factory.contains("this.frameTargetImageSlabsByType"),
                "the dedicated pool is held per memory type, like the other image pool");
    }

    @Test
    void theSharedImagePoolKeepsItsCapOf8() throws IOException {
        String factory = methodSlice(contextSource(),
                "public SlabAllocator celtium$deviceLocalImageSlabs(int memoryTypeBits)", 1200);
        assertTrue(factory.contains("new SlabAllocator(this, \"deviceLocalImage\", type, "
                        + "SlabAllocator.DEFAULT_SLAB_SIZE, 8,"),
                "separating the chain pairs does not raise the shared image pool's cap");
    }

    @Test
    void theOneAllocatorDispatchesOnThePoolFlag() throws IOException {
        String allocator = methodSlice(frameTargetSource(),
                "private AllocatedImage allocateImage(boolean color", 7000);
        assertTrue(allocator.contains("boolean frameTargetPool"),
                "the allocator takes the pool choice as an argument, so there is still one creation path");
        assertTrue(allocator.contains("celtium$frameTargetImageSlabs(requirements.memoryTypeBits())"),
                "the dedicated population allocates from the frame-target image pool");
        assertTrue(allocator.contains("celtium$deviceLocalImageSlabs(requirements.memoryTypeBits())"),
                "the bounded population still allocates from the shared image pool");
    }

    @Test
    void chainPairsAndTheSharedDepthStoreAreTheDedicatedPopulation() throws IOException {
        String source = frameTargetSource();
        assertTrue(source.contains("new Pair(\"chain-\" + Long.toHexString(identity), true)"),
                "a chain pair is born marked for the dedicated pool");
        assertTrue(source.contains("new Pair(\"default\", false)"),
                "the default pair is born marked for the shared pool");
        String sharedDepth = methodSlice(source, "private AllocatedImage ensureSharedDepth", 2000);
        assertTrue(sharedDepth.contains(
                        "allocateImage(false, width, height, TargetFormatClass.RGBA8_UNORM, true)"),
                "the shared depth store allocates into the dedicated pool");
    }

    @Test
    void aPairsAllocationsFollowItsMark() throws IOException {
        String ensure = methodSlice(frameTargetSource(),
                "private void ensure(int requestedWidth, int requestedHeight, long sharedKey, "
                        + "AllocatedImage sharedDepth,\n", 4500);
        assertTrue(ensure.contains("wantedClass,\n                        this.dedicatedPool)"),
                "a pair's colour image follows the pair's pool mark");
        assertTrue(ensure.contains("TargetFormatClass.RGBA8_UNORM, this.dedicatedPool)"),
                "a pair's own depth image follows the pair's pool mark");
        String extras = methodSlice(frameTargetSource(), "private void ensureExtras(int attachments)", 2600);
        assertTrue(extras.contains("this.formatClass,\n                            this.dedicatedPool)"),
                "a pair's extra colour images follow the pair's pool mark");
    }

    @Test
    void theFrameItselfStaysInTheSharedPool() throws IOException {
        String create = methodSlice(frameTargetSource(), "public static GllessFrameTarget create", 2200);
        assertTrue(create.contains("target.allocateImage(true, width, height, target.frameFormatClass, false)"),
                "the frame's own colour allocates into the shared pool");
        assertTrue(create.contains("TargetFormatClass.RGBA8_UNORM,\n                    false)"),
                "the frame's own depth allocates into the shared pool");
    }

    @Test
    void theDedicatedPoolIsSummarisedCollectedAndDestroyedWithTheOthers() throws IOException {
        String context = contextSource();
        assertTrue(context.contains(
                        "appendSlabPoolSummary(summary, \"frameTargetImage\", "
                                + "this.frameTargetImageSlabsByType, first)"),
                "the per-pool census summary names the dedicated pool, so a refusal is attributable");
        String collect = methodSlice(context, "public void celtium$collectSlabReleases()", 1800);
        assertTrue(collect.contains("this.frameTargetImageSlabsByType"),
                "deferred slab releases are collected from the dedicated pool too");
        assertTrue(context.contains("for (SlabAllocator allocator : this.frameTargetImageSlabsByType.values()) {\n"
                        + "                allocator.delete();"),
                "the dedicated pool's slabs are freed with the device, like the other pools'");
    }

    private static String contextSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
    }

    private static String frameTargetSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/GllessFrameTarget.java"));
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
