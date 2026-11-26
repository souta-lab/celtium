package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.terrain.TerrainSlabAllocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * BO convergence landing A (2026-11-27): the terrain geometry slabs were
 * resized (logical 64 MiB x 8 -> 16 MiB x 32) while the device pool behind
 * them - the context's terrainGeometry SlabAllocator - kept its own
 * dimensions (64 MiB x 8). The decoupling is the landing's load-bearing half:
 * while the pool read the logical slab size, its live slab count equalled the
 * logical slab count, so shrinking the logical slabs would have multiplied
 * the pool slabs (one device buffer per logical slab, each opening its own
 * pool slab) and handed the buffer-object count back. These pins hold the
 * two sizes apart: the compiled constants, and the source shape that the
 * pool's creation never reads the logical size. The device side needs a
 * Vulkan context, so the creation shape is pinned as a source contract, in
 * the style of {@link TerrainSharedSlabBufferContractTest}.
 */
class TerrainGeometryPoolDecouplingContractTest {

    private static final String HOST =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/TerrainEngineAHost.java";

    @Test
    void thePoolKeepsItsOwnDimensionsApartFromTheResizedLogicalSlabs() {
        assertEquals(16L * 1024 * 1024, TerrainSlabAllocator.DEFAULT_SLAB_BYTES,
                "the logical terrain slabs are 16 MiB since landing A");
        assertEquals(32, TerrainSlabAllocator.DEFAULT_MAX_SLABS,
                "the logical terrain slab cap is 32 since landing A");
        assertEquals(512L * 1024 * 1024,
                TerrainSlabAllocator.DEFAULT_SLAB_BYTES * TerrainSlabAllocator.DEFAULT_MAX_SLABS,
                "the logical span is unchanged by the resize");
        assertEquals(64L * 1024 * 1024, TerrainEngineAHost.GEOMETRY_POOL_SLAB_BYTES,
                "the device pool's slabs stay 64 MiB, decoupled from the logical size");
        assertEquals(8, TerrainEngineAHost.GEOMETRY_POOL_MAX_SLABS,
                "the device pool's slab cap stays 8, decoupled from the logical cap");
    }

    @Test
    void poolCreationNeverReadsTheLogicalSlabSize() throws IOException {
        String host = readSource(HOST);
        String creation = methodSlice(host, "SlabAllocator geometrySlabAllocator(VulkanContext context", 450);
        assertTrue(creation.contains("GEOMETRY_POOL_SLAB_BYTES, GEOMETRY_POOL_MAX_SLABS"),
                "the pool is created from its own dimension constants");
        assertFalse(creation.contains("slabBytes()"),
                "the pool creation does not read the logical slab size");
        assertFalse(creation.contains("DEFAULT_MAX_SLABS"),
                "the pool creation does not read the logical slab cap either");
        // The one legitimate read of the logical size left in the host: a slab
        // buffer's own size, at its creation seat in the shared accessor.
        assertEquals(1, countOccurrences(host, "store.geometryAllocator().slabBytes()"),
                "the logical slab size is read exactly once: for the buffer's own size");
        String accessor = methodSlice(host, "SlabBuffer geometrySlabBuffer(VulkanContext context, int slabId)", 800);
        assertTrue(accessor.contains("store.geometryAllocator().slabBytes()"),
                "that one read is the buffer creation seat in geometrySlabBuffer");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(needle, from)) >= 0) {
            count++;
            from += needle.length();
        }
        return count;
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
