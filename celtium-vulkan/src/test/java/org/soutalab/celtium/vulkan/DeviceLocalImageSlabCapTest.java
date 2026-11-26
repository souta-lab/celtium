package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The device-local image pool's ordinary slab cap, pinned without a device.
 * <p>
 * The cap was 8 (512 MiB) until the GL-less frame target's images were housed in this pool (R-54):
 * a Demonica+BSL scene builds dozens of chain pairs, and the 2026-11-26 leg measured live demand at
 * 517 MB of the 512 MiB cap with no deferred releases pending, so a chain pair's colour image was
 * refused and Demonica disabled the shader pack for the whole run. The diagnosis attributed 97.8% of
 * the occupancy to the housed frame targets and prescribed raising this pool's cap alone to 12
 * (768 MiB) - a ceiling, not a reservation, so a scene that never grows past 8 slabs allocates
 * exactly what it did before. These pins read the source so the raise cannot silently revert and
 * cannot silently spread to the buffer pools, whose caps carry their own separate histories.
 */
class DeviceLocalImageSlabCapTest {

    @Test
    void theDeviceLocalImagePoolIsCappedAtTwelveOrdinarySlabs() throws IOException {
        String factory = methodSlice(contextSource(),
                "public SlabAllocator celtium$deviceLocalImageSlabs", 1200);
        assertTrue(factory.contains(
                        "\"deviceLocalImage\", type, SlabAllocator.DEFAULT_SLAB_SIZE, 12,"),
                "the device-local image pool is created with an ordinary slab cap of 12");
    }

    @Test
    void theBufferPoolsKeepTheirCapOfEight() throws IOException {
        String source = contextSource();
        String deviceLocal = methodSlice(source,
                "public SlabAllocator celtium$deviceLocalSlabs", 1200);
        assertTrue(deviceLocal.contains(
                        "\"deviceLocal\", type, SlabAllocator.DEFAULT_SLAB_SIZE, 8, false)"),
                "the device-local buffer pool keeps its cap of 8");
        String hostVisible = methodSlice(source,
                "public SlabAllocator celtium$hostVisibleSlabs", 1200);
        assertTrue(hostVisible.contains(
                        "\"hostVisible\", type, SlabAllocator.DEFAULT_SLAB_SIZE, 8, true)"),
                "the host-visible buffer pool keeps its cap of 8");
    }

    private static String contextSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java"));
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
