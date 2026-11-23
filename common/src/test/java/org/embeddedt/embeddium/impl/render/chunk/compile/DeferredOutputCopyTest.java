package org.embeddedt.embeddium.impl.render.chunk.compile;

import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.sorting.SortState;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Item 730: a deferred upload must own the data it drains.
 * <p>
 * The sequence pinned here is the defect's own, in the caller's order: a consumer that defers an output copies
 * it with {@link ChunkTaskOutput#copyForDeferral()}, the caller then deletes the original - which
 * {@code RenderSectionManager.uploadChunks} does for every output on the same call - and the copy must still
 * hold the original bytes when the drain reaches it a frame or more later. Before the fix the queue held the
 * original itself, the drain's first read threw "Buffer has been deleted", and the section, already committed
 * as built, stayed missing permanently.
 */
class DeferredOutputCopyTest {

    @Test
    void aDeferredBuildOutputSurvivesTheCallerDeletingTheOriginal() {
        TerrainRenderPass pass = testPass("solid");
        NativeBuffer vertices = filledBuffer(64, (byte) 0x5A);
        NativeBuffer indices = filledBuffer(24, (byte) 0xC3);
        var meshes = new Reference2ReferenceOpenHashMap<TerrainRenderPass, BuiltSectionMeshParts>();
        meshes.put(pass, new BuiltSectionMeshParts(vertices, indices, SortState.NONE, Map.of()));
        ChunkBuildOutput original = new ChunkBuildOutput(null, null, meshes, 7);

        ChunkBuildOutput copy = original.copyForDeferral();
        assertNotSame(original, copy);
        assertEquals(7, copy.buildTime);

        original.delete();
        assertThrows(IllegalStateException.class, vertices::getDirectBuffer,
                "the original really is freed - the pre-fix drain read exactly this state");

        BuiltSectionMeshParts copyParts = copy.meshes.get(pass);
        assertNotSame(vertices, copyParts.vertexBuffer(), "the copy owns its own vertex buffer");
        assertBytes(copyParts.vertexBuffer(), 64, (byte) 0x5A);
        assertBytes(copyParts.indexBuffer(), 24, (byte) 0xC3);
        copy.delete();
    }

    @Test
    void aDeferredSortOutputSurvivesTheCallerDeletingTheOriginal() {
        TerrainRenderPass pass = testPass("translucent");
        NativeBuffer indices = filledBuffer(48, (byte) 0x3C);
        var meshes = new Reference2ReferenceOpenHashMap<TerrainRenderPass, ChunkSortOutput.SortedMesh>();
        meshes.put(pass, new ChunkSortOutput.SortedMesh(indices));
        ChunkSortOutput original = new ChunkSortOutput(null, 9, meshes);

        ChunkSortOutput copy = original.copyForDeferral();
        assertNotSame(original, copy);
        assertEquals(9, copy.buildTime);

        original.delete();
        assertThrows(IllegalStateException.class, indices::getDirectBuffer,
                "the original really is freed");

        assertBytes(copy.meshes.get(pass).indexData(), 48, (byte) 0x3C);
        copy.delete();
    }

    private static TerrainRenderPass testPass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static NativeBuffer filledBuffer(int bytes, byte pattern) {
        NativeBuffer buffer = new NativeBuffer(bytes);
        ByteBuffer direct = buffer.getDirectBuffer();
        for (int i = 0; i < bytes; i++) {
            direct.put(i, pattern);
        }
        return buffer;
    }

    private static void assertBytes(NativeBuffer buffer, int bytes, byte pattern) {
        assertEquals(bytes, buffer.getLength());
        ByteBuffer direct = buffer.getDirectBuffer();
        for (int i = 0; i < bytes; i++) {
            assertEquals(pattern, direct.get(i), "byte " + i + " of the deferred copy");
        }
    }
}
