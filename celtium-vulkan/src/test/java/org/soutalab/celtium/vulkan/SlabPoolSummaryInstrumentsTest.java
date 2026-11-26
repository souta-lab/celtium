package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BO convergence design §5.1 (2026-11-26): the slab pool summary gains the two readings the
 * convergence measurement turns on - every oversized slab's size next to its count, and the
 * terrain geometry allocator's row (device live slabs and used bytes against the logical
 * ledger's slab count and live bytes, with each pass' created slab buffer count). The device
 * side cannot be built without a GPU, so the wiring is pinned as source contracts, in the
 * style of {@link PackLiveWorkGateTest}; the size list's text form is a pure function and is
 * exercised directly.
 */
class SlabPoolSummaryInstrumentsTest {

    @Test
    void oversizedSlabSizesTextFormatsTheList() {
        assertEquals("[]", VulkanContext.oversizedSlabSizesText(new long[0]));
        assertEquals("[7]", VulkanContext.oversizedSlabSizesText(new long[]{7L}));
        assertEquals("[1,22,333]", VulkanContext.oversizedSlabSizesText(new long[]{1L, 22L, 333L}));
    }

    @Test
    void oversizedSlabSizesReadsEveryHeldSlabsRecordSize() throws IOException {
        String allocator = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/hal/SlabAllocator.java");
        String getter = methodSlice(allocator, "public long[] oversizedSlabSizes()", 600);
        assertTrue(getter.contains("this.oversizedSlabs.get(i).size"),
                "the sizes come from the held oversized slabs' records, in order");
    }

    @Test
    void poolSummaryAppendsOversizedSizesNextToTheCount() throws IOException {
        String context = contextSource();
        String append = methodSlice(context, "private static boolean appendSlabPoolSummary", 3200);
        assertTrue(append.contains(".append(\",oversizedSlabs=\").append(allocator.oversizedSlabCount())"),
                "the existing count key is unchanged");
        assertTrue(append.contains(".append(\",oversizedSlabSizes=\")"),
                "the sizes key is appended after the count");
        assertTrue(append.contains("oversizedSlabSizesText(allocator.oversizedSlabSizes())"),
                "the sizes are the allocator's own held oversized slabs");
    }

    @Test
    void poolSummaryAppendsTheRegisteredTerrainEntry() throws IOException {
        String context = contextSource();
        String summary = methodSlice(context, "public String celtium$slabPoolSummary()", 1600);
        assertTrue(summary.contains("this.terrainSlabSummarySource"),
                "the summary consults the registered terrain source");
        assertTrue(summary.contains("terrainSource.get()"),
                "the terrain entry is read from the source at summary time");
        assertTrue(context.contains(
                        "public void celtium$setTerrainSlabSummarySource(java.util.function.Supplier<String> source)"),
                "the registration setter exists for the terrain backend");
    }

    @Test
    void terrainEntrySetsDeviceAgainstLogicalWithPerPassBufferCounts() throws IOException {
        String host = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java");
        String entry = methodSlice(host, "String terrainSlabSummary()", 2600);
        assertTrue(entry.contains("terrainGeometry(type="), "the entry names the terrain pool");
        assertTrue(entry.contains("allocator.usedBytes()") && entry.contains("allocator.liveSlabCount()"),
                "the device side is the shared allocator's used bytes and live slab count");
        assertTrue(entry.contains("allocator.oversizedSlabSizes()"),
                "the terrain allocator's oversized sizes ride the same entry");
        assertTrue(entry.contains("this.store.geometryAllocator()"),
                "the logical side is the shared store's geometry ledger");
        assertTrue(entry.contains(",logicalSlabs=") && entry.contains("logical.slabCount()"),
                "the logical slab count is reported");
        assertTrue(entry.contains(",logicalLiveBytes=") && entry.contains("logical.liveBytes()"),
                "the logical live bytes are reported");
        assertTrue(entry.contains(",engineSlabBuffers=[") && entry.contains("liveSlabBufferCount()"),
                "each executor's created slab buffer count is listed");
    }

    @Test
    void liveSlabBufferCountCountsCreatedBuffersOnly() throws IOException {
        String engine = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java");
        String count = methodSlice(engine, "int liveSlabBufferCount()", 400);
        assertTrue(count.contains("for (SlabBuffer buffer : this.slabBuffers)"),
                "the count walks this pass' slab buffer slots");
        assertTrue(count.contains("if (buffer != null)"),
                "only created (non-null) buffers count");
    }

    @Test
    void backendRegistersTheTerrainSummarySource() throws IOException {
        String backend = readSource(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java");
        assertTrue(backend.contains("context.celtium$setTerrainSlabSummarySource("),
                "the backend registers the source with its context");
        assertTrue(backend.contains("host.terrainSlabSummary()"),
                "the registered source reads the terrain host's entry");
    }

    private static String contextSource() throws IOException {
        return readSource("celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanContext.java");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

    /**
     * Reads a main source file with line endings normalised to LF: the pinned shapes span
     * lines, and a CRLF checkout (the Windows default) must pin the same shapes as an LF one.
     */
    private static String readSource(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative)).replace("\r\n", "\n");
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
