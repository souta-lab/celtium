package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The VUID-vkCmdDrawIndexedIndirect-None-08114 fix (diagnosed 2026-11-26): the Tier-A
 * section-descriptor and factor-base buffers opt into superseded-buffer retention, so a growth no
 * longer hands the old handle to the retired-buffer drain while a Tier-A descriptor set can still
 * name it. The buffers themselves moved from the per-pass engines to the shared host with R-15;
 * the retention opt-in moved with them and is pinned where it now lives.
 * <p>
 * The device-bound half (the actual {@code vkDestroyBuffer} calls in {@code delete()}) cannot run on
 * a CPU-only instance, so the sequence is pinned at its two testable layers, the shape
 * {@code ImmediateTextureTwinRetireContractTest} and {@code LandedFixGuardContractTest} established:
 * the pure retention bookkeeping the growth path drives, and source contracts for the wiring.
 */
class HostBufferGrowthRetentionTest {

    private static final String HOST_BUFFER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanHostBuffer.java";
    private static final String TERRAIN_ENGINE_A_HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";

    @Test
    @DisplayName("growth retains every superseded pair, and delete's drain hands each off exactly once")
    void growthRetainsAndDeleteDrainsEverySupersededPairOnce() {
        VulkanHostBuffer.SupersededBuffers superseded = new VulkanHostBuffer.SupersededBuffers();
        // The sequence ensureCapacity drives under retention: one retain per growth, handles in
        // growth order. The allocation is opaque to the store, so null stands in for it here.
        superseded.retain(0x100L, null);
        superseded.retain(0x200L, null);
        superseded.retain(0x400L, null);
        assertEquals(3, superseded.size(), "every growth's superseded buffer is still held");

        List<VulkanHostBuffer.SupersededBuffer> drained = superseded.drain();
        assertEquals(3, drained.size(), "delete destroys everything growth retained - nothing leaks");
        assertEquals(0x100L, drained.get(0).handle, "pairs hand off in growth order");
        assertEquals(0x200L, drained.get(1).handle);
        assertEquals(0x400L, drained.get(2).handle);
        assertEquals(0, superseded.size(), "the drain leaves the store empty");
        assertTrue(superseded.drain().isEmpty(),
                "a repeated delete() hands nothing off twice - no double destroy");
    }

    @Test
    @DisplayName("a buffer that never grew retains nothing")
    void noGrowthRetainsNothing() {
        VulkanHostBuffer.SupersededBuffers superseded = new VulkanHostBuffer.SupersededBuffers();
        assertTrue(superseded.isEmpty());
        assertTrue(superseded.drain().isEmpty());
    }

    @Test
    @DisplayName("clear drops retained pairs without handing them off (the context is already gone)")
    void clearDropsRetainedPairs() {
        VulkanHostBuffer.SupersededBuffers superseded = new VulkanHostBuffer.SupersededBuffers();
        superseded.retain(0x100L, null);
        superseded.clear();
        assertEquals(0, superseded.size());
        assertTrue(superseded.drain().isEmpty(), "cleared pairs are not destroyed after their context is gone");
    }

    @Test
    @DisplayName("growth retires the old handle only when retention is off")
    void growthRetiresOnlyWhenRetentionIsOff() throws IOException {
        String source = read(HOST_BUFFER);
        assertTrue(source.contains("this.superseded.retain(oldHandle, oldAllocation)"),
                "the retention branch keeps the superseded pair on the buffer");
        assertEquals(1, count(source, "\"hostBuffer.growth\""),
                "the growth retire survives exactly once, in the non-retention branch");
    }

    @Test
    @DisplayName("delete drains the retained pairs on both its deferred and its immediate route")
    void deleteDrainsRetainedPairs() throws IOException {
        String source = read(HOST_BUFFER);
        assertTrue(source.contains("this.superseded.drain()"), "delete hands the retained pairs off");
        assertEquals(2, count(source, "\"hostBuffer.delete\""),
                "the current buffer and each retained one take the recording-open retire route");
        assertTrue(source.contains("destroyAndRelease(device, old.handle, old.allocation)"),
                "the immediate route destroys each retained buffer and releases its slab range");
    }

    @Test
    @DisplayName("both Tier-A image buffers opt into retention at creation")
    void terrainEngineOptsBothBuffersIntoRetention() throws IOException {
        String source = read(TERRAIN_ENGINE_A_HOST);
        assertTrue(source.contains("this.descBuffer.retainSupersededBuffers()"),
                "the section-descriptor buffer (binding 6) retains its superseded buffers");
        assertTrue(source.contains("this.factorBaseBuffer.retainSupersededBuffers()"),
                "the factor-base buffer (binding 7) retains its superseded buffers");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    private static int count(String text, String needle) {
        int found = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            found++;
            from += needle.length();
        }
        return found;
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
