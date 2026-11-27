package org.soutalab.celtium.vulkan;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.data.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.ChunkSortOutput;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The deferred queue's buffer lifetime, exposed by the budget-C confirmation leg
 * (OVERHAUL-BUDGET-C-CONFIRMATION-LEG-2026-11-27 §5.4): {@code RenderSectionManager} deletes every
 * result as soon as {@code uploadMeshes} returns, so a deferred entry that referenced the originals
 * drained into freed buffers - {@code IllegalStateException: Buffer has been deleted}, 266 failed
 * region uploads, and sections lost because they had already been committed as built.
 * <p>
 * The queue now holds copies taken at deferral time ({@code celtium$copyForDeferral}). The behavioural
 * half pins the order - copy, then the caller's delete, then the drain's read - against the real copy
 * function; the wiring half is pinned on the sources, because the producer and the drain live on the
 * terrain backend, which needs a device to instantiate.
 */
class DeferredUploadLifetimeTest {

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static NativeBuffer filledBuffer(int length, int seed) {
        NativeBuffer buffer = new NativeBuffer(length);
        ByteBuffer view = buffer.getDirectBuffer();
        for (int i = 0; i < length; i++) {
            view.put(i, (byte) (seed + i));
        }
        return buffer;
    }

    private static void assertContents(NativeBuffer buffer, int length, int seed) {
        ByteBuffer view = buffer.getDirectBuffer();
        assertEquals(length, view.remaining(), "the buffer keeps its length");
        for (int i = 0; i < length; i++) {
            assertEquals((byte) (seed + i), view.get(i), "byte " + i + " survives");
        }
    }

    // ------------------------------------------------------------------ the hazard, as a control

    /** Without a copy, the caller's delete is the end of the payload: this is what the drain used to read. */
    @Test
    void theOriginalIsDeadAfterTheCallersDelete() {
        TerrainRenderPass firstPass = pass("first");
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(firstPass, new BuiltSectionMeshParts(filledBuffer(64, 5), null, null, Map.of()));
        ChunkBuildOutput original = new ChunkBuildOutput(null, null, meshes, 0);

        original.delete();

        assertThrows(IllegalStateException.class,
                () -> meshes.get(firstPass).vertexBuffer().getDirectBuffer(),
                "a queued original would be read after its buffers were freed");
    }

    // ------------------------------------------------------------------ the fix, behaviourally

    /** Defer (copy) → the caller deletes the originals → the drain's read still finds the payload intact. */
    @Test
    void buildCopySurvivesTheCallersDelete() {
        Map<ModelQuadFacing, VertexRange> ranges = new java.util.HashMap<>();
        int[] rdhFactors = new int[]{3, 1, 4};
        TerrainRenderPass firstPass = pass("first");
        TerrainRenderPass secondPass = pass("second");
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(firstPass, new BuiltSectionMeshParts(filledBuffer(100, 3), filledBuffer(40, 90),
                null, ranges, rdhFactors));
        meshes.put(secondPass, new BuiltSectionMeshParts(filledBuffer(50, 7), null, null, Map.of()));
        ChunkBuildOutput original = new ChunkBuildOutput(null, null, meshes, 12345);

        ChunkBuildOutput copy = (ChunkBuildOutput) VulkanTerrainGpuBackend.celtium$copyForDeferral(original);
        original.delete();

        BuiltSectionMeshParts first = copy.meshes.get(firstPass);
        assertContents(first.vertexBuffer(), 100, 3);
        assertContents(first.indexBuffer(), 40, 90);
        assertContents(copy.meshes.get(secondPass).vertexBuffer(), 50, 7);
        // The non-buffer payload is carried over, not rebuilt: the commit path reads the same objects.
        assertSame(ranges, first.ranges(), "the ranges are carried by reference");
        assertSame(rdhFactors, first.rdhFactors(), "the correction factors are carried by reference");
        assertEquals(12345, copy.buildTime, "the build time is carried over");

        copy.delete();
    }

    /** Sort outputs ride the same queue; their index payload gets the same lifetime. */
    @Test
    void sortCopySurvivesTheCallersDelete() {
        TerrainRenderPass sortPass = pass("sort");
        Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(sortPass, new ChunkSortOutput.SortedMesh(filledBuffer(64, 21)));
        ChunkSortOutput original = new ChunkSortOutput(null, 777, meshes);

        ChunkSortOutput copy = (ChunkSortOutput) VulkanTerrainGpuBackend.celtium$copyForDeferral(original);
        original.delete();

        assertContents(copy.meshes.get(sortPass).indexData(), 64, 21);
        assertEquals(777, copy.buildTime, "the build time is carried over");

        copy.delete();
    }

    /** The copy weighs what the original weighed, so the account's entry bytes stay the payload's bytes. */
    @Test
    void copyKeepsTheDeferralWeight() {
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(pass("first"), new BuiltSectionMeshParts(filledBuffer(100, 3), filledBuffer(40, 90),
                null, Map.of()));
        meshes.put(pass("second"), new BuiltSectionMeshParts(filledBuffer(50, 7), null, null, Map.of()));
        ChunkBuildOutput original = new ChunkBuildOutput(null, null, meshes, 0);

        ChunkBuildOutput copy = (ChunkBuildOutput) VulkanTerrainGpuBackend.celtium$copyForDeferral(original);

        assertEquals(VulkanTerrainGpuBackend.celtium$deferredUploadWeight(original),
                VulkanTerrainGpuBackend.celtium$deferredUploadWeight(copy),
                "the copy's weight is the original's weight");
        assertEquals(190L, VulkanTerrainGpuBackend.celtium$deferredUploadWeight(copy));

        original.delete();
        copy.delete();
    }

    /** The queue's own delete ends the copy's lifetime: the ownership the queue takes is real, not a leak. */
    @Test
    void theCopyIsFreedWhenTheEntryLeaves() {
        TerrainRenderPass firstPass = pass("first");
        Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts> meshes =
                new Reference2ReferenceOpenHashMap<>();
        meshes.put(firstPass, new BuiltSectionMeshParts(filledBuffer(32, 11), null, null, Map.of()));
        ChunkBuildOutput original = new ChunkBuildOutput(null, null, meshes, 0);
        ChunkBuildOutput copy = (ChunkBuildOutput) VulkanTerrainGpuBackend.celtium$copyForDeferral(original);
        original.delete();

        copy.delete();

        assertThrows(IllegalStateException.class,
                () -> copy.meshes.get(firstPass).vertexBuffer().getDirectBuffer(),
                "once the entry has left the queue, nothing holds its buffers");
    }

    // ------------------------------------------------------------------ wiring pins

    /** The producer queues the copy, inside the budgeted branch and after the account's judgment. */
    @Test
    void theProducerQueuesCopiesNotOriginals() throws IOException {
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int judgment = backend.indexOf("celtium$tryDeferBurstArrival(account, frameKey, weight)");
        int copy = backend.indexOf("celtium$copyForDeferral(holder.output())");
        assertTrue(judgment >= 0, "the producer judgment exists");
        assertTrue(copy > judgment, "the copy is taken in the branch the judgment admitted");
        assertTrue(backend.contains("this.deferredUploads.addLast(new DeferredUpload("),
                "the grouped copies are what the queue receives");
    }

    /** Every exit from the queue frees the entry's copies: the drain, the teardown drop, the copy fallback. */
    @Test
    void everyQueueExitFreesTheCopies() throws IOException {
        String backend = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        assertEquals(3, countOccurrences(backend, "celtium$deleteDeferredCopy(output)"),
                "the drain, the world-teardown drop and the copy-failure cleanup each free the copies");
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
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
        return direct;
    }
}
