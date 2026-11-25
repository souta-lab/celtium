package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 16-byte Tier-A vertex: positions survive the 1/128-block quantization, the UV
 * packing is bit-identical to the compact format's, and normals collapse to the
 * facing-bucket index space the culling and lighting code shares.
 */
class TerrainVertex16Test {

    private static Material material() {
        TerrainRenderPass pass = TerrainRenderPass.builder()
                .name("tier-a-test")
                .vertexType(ChunkMeshFormats.TERRAIN_A16)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
        return new Material(pass, AlphaCutoffParameter.ZERO, true);
    }

    @Test
    void positionQuantizationRoundTripsWithinOneStep() {
        assertEquals(8 * 128, TerrainVertex16.encodePositionComponent(0.0f));
        assertEquals(0, TerrainVertex16.encodePositionComponent(-8.0f));
        assertEquals(4095, TerrainVertex16.encodePositionComponent(24.0f - 1e-4f));
        float decoded = TerrainVertex16.decodePositionComponent(TerrainVertex16.encodePositionComponent(3.14159f));
        assertTrue(Math.abs(decoded - 3.14159f) <= 1.0f / 128.0f, "decoded " + decoded);
    }

    @Test
    void normalIdsFollowTheBucketOrder() {
        assertEquals(0, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(127, 0, 0)));
        assertEquals(1, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(0, 127, 0)));
        assertEquals(2, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(0, 0, 127)));
        assertEquals(3, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(-127, 0, 0)));
        assertEquals(4, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(0, -127, 0)));
        assertEquals(5, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(0, 0, -127)));
        assertEquals(6, TerrainVertex16.normalId(org.embeddedt.embeddium.impl.util.NormI8.pack(90, 90, 0)));
    }

    @Test
    void theEncoderWritesTheDocumentedWords() {
        TerrainVertex16.Encoder encoder = (TerrainVertex16.Encoder) ChunkMeshFormats.TERRAIN_A16.getEncoder();
        assertTrue(encoder.capturesRdhFactorSideStream());
        ByteBuffer buffer = MemoryUtil.memAlloc(TerrainVertex16.STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            int light = (15 << 16) | 7;
            encoder.write(address, 1.0f, 2.0f, 3.0f, 0xFF804020, 0.25f, 0.75f, light,
                    org.embeddedt.embeddium.impl.util.NormI8.pack(0, 127, 0), material(), 0.5f);
            int word0 = MemoryUtil.memGetInt(address);
            assertEquals(TerrainVertex16.encodePositionComponent(1.0f), word0 & 0xFFF);
            assertEquals(TerrainVertex16.encodePositionComponent(2.0f), (word0 >>> 12) & 0xFFF);
            assertEquals(TerrainVertex16.encodePositionComponent(3.0f), (word0 >>> 24) & 0xFF);
            int word1 = MemoryUtil.memGetInt(address + 4);
            assertEquals(0xFF804020, word1);
            int word2 = MemoryUtil.memGetInt(address + 8);
            assertEquals((int) (0.25f * (1 << 18)) & 0x3FFFF, word2 & 0x3FFFF);
            assertEquals((int) (0.75f * (1 << 18)) & 0x3FFFF, (word2 >>> 18) & 0x3FFF);
            int word3 = MemoryUtil.memGetInt(address + 12);
            assertEquals(7, word3 & 0xFF, "block light");
            assertEquals(15, (word3 >>> 8) & 0xFF, "sky light");
            assertEquals(1, (word3 >>> 16) & 0x7, "normal id +Y");
            assertEquals(material().bits() & 0x1FF, (word3 >>> 19) & 0x1FF, "material bits");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
