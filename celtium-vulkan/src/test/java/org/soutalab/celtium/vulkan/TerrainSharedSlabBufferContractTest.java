package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * T3 slice S4 (full sharing, 2026-11-26): the geometry slabs' device buffers
 * are the host's, one per logical slab for every pass. Each pass' executor
 * used to materialize its own {@code VkBuffer} over the same shared allocator
 * slab, so the device buffer count multiplied the logical slab count by the
 * pass count; now the single buffer a slab gets is created, held and deleted
 * by the host, and the executors only borrow it at the upload and draw-bind
 * seats. The invariants are the sharing review's: INV-S4a (at most one device
 * buffer per logical slab, host-wide), INV-S4b (the write ranges stay the
 * single store ledger's disjoint ranges — the sharing changes which handle
 * names the memory, never the address arithmetic), and INV-S4c (a creation
 * failure keeps prescription A's classification at the creation seat, so the
 * upload two-phase rollback runs unchanged). The device side needs a Vulkan
 * context, so the shape is pinned here as source contracts, in the style of
 * {@link TerrainEmptySlabCopyContractTest}.
 */
class TerrainSharedSlabBufferContractTest {

    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";
    private static final String ENGINE =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineA.java";

    @Test
    void oneBufferPerLogicalSlabIsHostOwnedAndSharedByEveryCaller() throws IOException {
        String host = readSource(HOST);
        String engine = readSource(ENGINE);
        assertTrue(host.contains("private final List<SlabBuffer> geometrySlabBuffers = new ArrayList<>();"),
                "the host holds the one list of shared slab buffers");
        assertTrue(host.contains("SlabBuffer geometrySlabBuffer(VulkanContext context, int slabId)"),
                "the host carries the shared accessor");
        String accessor = methodSlice(host, "SlabBuffer geometrySlabBuffer(VulkanContext context, int slabId)", 800);
        assertTrue(accessor.contains("this.geometrySlabBuffers.get(slabId)"),
                "the accessor reads the standing slot for the slab");
        assertTrue(accessor.contains("if (buffer == null)") && accessor.contains("new SlabBuffer(context,"),
                "creation is lazy, only for a slab no caller has materialized yet");
        assertTrue(accessor.contains("this.geometrySlabBuffers.set(slabId, buffer)"),
                "the created buffer takes the slab's slot, so every later caller gets the same instance");
        assertFalse(engine.contains("geometrySlabBuffers"),
                "no executor names the shared list: it borrows through the accessor alone");
    }

    @Test
    void bothMaterializationSeatsGoThroughTheHostAccessor() throws IOException {
        String engine = readSource(ENGINE);
        assertTrue(engine.contains(
                        "TerrainEngineAHost.SlabBuffer slab = this.host.geometrySlabBuffer(this.context, registration.geometry.slabId);"),
                "the upload writes into the host's buffer for the registration's slab");
        assertTrue(engine.contains("slab = this.host.geometrySlabBuffer(this.context, s);"),
                "the draw bind binds the host's buffer for the armed slab");
    }

    @Test
    void creationFailureIsClassifiedAtTheCreationSeatAndUnwinds() throws IOException {
        String host = readSource(HOST);
        String constructor = region(host,
                "SlabBuffer(VulkanContext context, long sizeBytes, int usageFlags)", "long buffer() {");
        int allocate = constructor.indexOf("allocator.allocate(");
        int wrap = constructor.indexOf("throw new TerrainEngineA.SlabCapacityException(refusal);");
        assertTrue(allocate >= 0 && wrap > allocate,
                "the allocator's serve refusal is classified at the creation seat (INV-S4c),"
                        + " so the upload catch and the draw skip keep their prescription A form");
        assertTrue(constructor.contains("catch (IllegalStateException refusal)"),
                "the classification catches the refusal type at the allocate call, never its message");
        int destroy = constructor.indexOf("vkDestroyBuffer(device, createdBuffer, null);");
        int release = constructor.indexOf("createdAllocation.owner().release(createdAllocation");
        assertTrue(destroy > allocate && release > allocate,
                "a part-created buffer is destroyed and a taken allocation released before the failure propagates");
    }

    @Test
    void theEngineKeepsNoSlabBufferOfItsOwn() throws IOException {
        String engine = readSource(ENGINE);
        assertFalse(engine.contains("private final List<SlabBuffer> slabBuffers"),
                "the per-pass buffer list is gone");
        assertFalse(engine.contains("private SlabBuffer slabBuffer(int slab)"),
                "the per-pass lazy creator is gone");
        assertFalse(engine.contains("int liveSlabBufferCount()"),
                "the per-pass census count is gone (the host counts the shared set)");
        assertFalse(engine.contains("final class SlabBuffer"),
                "the buffer class itself lives on the host now");
        assertFalse(engine.contains("slabReleaseValue()"),
                "the release-currency delegate went with the buffer: releases read the context directly");
    }

    @Test
    void theHostDestroysTheSharedSlabBuffersInStepFourBeforeTheAllocator() throws IOException {
        String host = readSource(HOST);
        int deleteStart = host.indexOf("boolean delete(VulkanContext context)");
        assertTrue(deleteStart >= 0, "the host's delete exists");
        String delete = host.substring(deleteStart);
        int enginesDeleted = delete.indexOf("engine.delete()");
        int geometryDeleted = delete.indexOf("geometrySlab.delete()");
        int allocatorDeleted = delete.indexOf("this.geometrySlabAllocator.delete()");
        assertTrue(enginesDeleted >= 0 && geometryDeleted > enginesDeleted,
                "the shared slab buffers are destroyed only after every engine (step 4)");
        assertTrue(allocatorDeleted > geometryDeleted,
                "the allocator whose slabs are the buffers' memory goes last (step 5)");
        assertTrue(delete.contains("this.geometrySlabBuffers.clear();"),
                "the shared list is cleared with the epoch, so the next world re-creates from empty");
    }

    @Test
    void theCensusReportsTheSharedCountAgainstTheLogicalCount() throws IOException {
        String host = readSource(HOST);
        String summary = methodSlice(host, "String terrainSlabSummary()", 1600);
        int logical = summary.indexOf(",logicalSlabs=");
        int shared = summary.indexOf(",hostSlabBuffers=");
        assertTrue(logical >= 0 && shared > logical,
                "the shared device count stands against the logical slab count in the one census row");
        assertFalse(host.contains("engineSlabBuffers"),
                "the per-pass count list is gone from the census");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

    /** The region between two markers, both required to exist in order. */
    private static String region(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start);
        assertTrue(start >= 0 && end > start, "the region " + startMarker + " is located");
        return source.substring(start, end);
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
