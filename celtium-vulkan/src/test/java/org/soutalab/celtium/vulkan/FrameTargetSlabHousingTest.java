package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R-54 housing of the GL-less frame target's images, pinned without a device.
 * <p>
 * Every image this target creates - the frame's own colour and depth, each pair's colour, depth and
 * extra colours, and the shared depth store's - used to carry a dedicated {@code vkAllocateMemory}
 * (census site {@code frameTarget}: 12 live allocations in the Stage 0 scene, one per image). They are
 * now sub-allocations of the context's device-local image slab pool, the mirrored textures' shape, so
 * the live allocation count is bounded by the pool's slab cap rather than by the image count. These
 * pins read the source so a dedicated route cannot reappear silently: the allocator goes through the
 * pool, no {@code VulkanContext.allocateMemory} / {@code freeMemory} call remains anywhere in the
 * target, releases are keyed on the slab completion currency, and the after-bind fault site exists so
 * a leg can exercise the half-created rollback.
 */
class FrameTargetSlabHousingTest {

    @Test
    void theAllocatorGoesThroughTheDeviceLocalImageSlabPool() throws IOException {
        String allocator = methodSlice(frameTargetSource(),
                "private AllocatedImage allocateImage(boolean color", 6000);
        assertTrue(allocator.contains("celtium$deviceLocalImageSlabs(requirements.memoryTypeBits())"),
                "the backing memory comes from the device-local image slab pool");
        assertTrue(allocator.contains(".allocate(requirements.size(), requirements.alignment())"),
                "the image's own requirements size the sub-allocation");
        assertTrue(allocator.contains("bindImage(image, allocation)"),
                "the bind goes through the allocator, keeping the offset arithmetic in one place");
        assertFalse(allocator.contains("VulkanContext.allocateMemory"),
                "no dedicated allocation remains in the allocator");
        assertFalse(allocator.contains("\"frameTarget\""),
                "the frameTarget census site is gone with the dedicated route");
    }

    @Test
    void noDedicatedAllocationOrFreeRemainsAnywhereInTheTarget() throws IOException {
        String source = frameTargetSource();
        assertFalse(source.contains("VulkanContext.allocateMemory"),
                "no path in the target allocates dedicated memory");
        assertFalse(source.contains("VulkanContext.freeMemory"),
                "no path in the target frees dedicated memory - the slab is the pool's to free");
    }

    @Test
    void releasesAreKeyedOnTheSlabReleaseValue() throws IOException {
        String helper = methodSlice(frameTargetSource(), "private void releaseAllocation", 1600);
        assertTrue(helper.contains("allocation.owner().release(allocation"),
                "a release goes back to the allocation's own pool");
        assertTrue(helper.contains("celtium$slabReleaseValue()"),
                "the release is keyed on the context's slab completion currency, the one every tenant uses");
    }

    @Test
    void theAfterBindFaultSiteKeepsTheRollbackExercisable() throws IOException {
        String allocator = methodSlice(frameTargetSource(),
                "private AllocatedImage allocateImage(boolean color", 6000);
        assertTrue(allocator.contains("VulkanAllocationFault.shouldFail(\"GllessFrameTarget:afterBind\")"),
                "a failure can be injected after the bind, where the rollback has an image and an allocation");
        assertTrue(allocator.contains("releaseAllocation(allocation)"),
                "the rollback hands the allocation back before the failure propagates");
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
