package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.parameters.AlphaCutoffParameter;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The 16-byte Tier-A vertex: positions survive the 1/128-block quantization, the UV
 * packing is bit-identical to the compact format's 18-bit encoding, and normals
 * collapse to the facing-bucket index space the culling and lighting code shares.
 * The word layout asserted here is the one documented on {@link TerrainVertex16}.
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
    void positionDecodingCoversTheSectionLocalRange() {
        assertEquals(-8.0f, TerrainVertex16.decodePosition(0));
        assertEquals(0.0f, TerrainVertex16.decodePosition(1024));
        assertEquals(24.0f - 1.0f / 128.0f, TerrainVertex16.decodePosition(4095), 1e-6f);
    }

    @Test
    void word0PackingRoundTrips() {
        int word0 = TerrainVertex16.packWord0(1024, 2048, 3000);
        int word1 = (3000 >>> 8) & 0xF;
        assertEquals(1024, TerrainVertex16.unpackPosX(word0));
        assertEquals(2048, TerrainVertex16.unpackPosY(word0));
        assertEquals(3000, TerrainVertex16.unpackPosZ(word0, word1));
    }

    @Test
    void normalIdsFollowTheBucketOrder() {
        assertEquals(0, TerrainVertex16.normalId(NormI8.pack(1.0f, 0.0f, 0.0f)));
        assertEquals(1, TerrainVertex16.normalId(NormI8.pack(0.0f, 1.0f, 0.0f)));
        assertEquals(2, TerrainVertex16.normalId(NormI8.pack(0.0f, 0.0f, 1.0f)));
        assertEquals(3, TerrainVertex16.normalId(NormI8.pack(-1.0f, 0.0f, 0.0f)));
        assertEquals(4, TerrainVertex16.normalId(NormI8.pack(0.0f, -1.0f, 0.0f)));
        assertEquals(5, TerrainVertex16.normalId(NormI8.pack(0.0f, 0.0f, -1.0f)));
        assertEquals(TerrainVertex16.NORMAL_NONE, TerrainVertex16.normalId(NormI8.pack(0.3f, 0.3f, 0.3f)));
        assertEquals(TerrainVertex16.NORMAL_NONE, TerrainVertex16.normalId(NormI8.pack(0.0f, 0.0f, 0.0f)));
    }

    @Test
    void theEncoderWritesTheDocumentedWords() {
        ChunkVertexEncoder encoder = ChunkMeshFormats.TERRAIN_A16.createEncoder();
        assertTrue(encoder.capturesRdhFactorSideStream());
        ByteBuffer buffer = MemoryUtil.memAlloc(TerrainVertex16.STRIDE);
        try {
            long address = MemoryUtil.memAddress(buffer);
            ChunkVertexEncoder.Vertex vertex = new ChunkVertexEncoder.Vertex();
            vertex.x = 1.0f;
            vertex.y = 2.0f;
            vertex.z = 3.0f;
            vertex.color = 0xFF804020;
            vertex.u = 0.25f;
            vertex.v = 0.75f;
            vertex.light = (15 << 16) | 7;
            vertex.vanillaNormal = NormI8.pack(0.0f, 1.0f, 0.0f);
            Material material = material();
            long next = encoder.write(address, material, vertex, 0);
            assertEquals(address + TerrainVertex16.STRIDE, next);

            int word0 = MemoryUtil.memGetInt(address);
            int word1 = MemoryUtil.memGetInt(address + 4);
            int word2 = MemoryUtil.memGetInt(address + 8);
            int word3 = MemoryUtil.memGetInt(address + 12);

            // Positions decode back to the inputs within one quantization step.
            int encX = TerrainVertex16.unpackPosX(word0);
            int encY = TerrainVertex16.unpackPosY(word0);
            int encZ = TerrainVertex16.unpackPosZ(word0, word1);
            assertEquals(1.0f, TerrainVertex16.decodePosition(encX), 1.0f / 128.0f);
            assertEquals(2.0f, TerrainVertex16.decodePosition(encY), 1.0f / 128.0f);
            assertEquals(3.0f, TerrainVertex16.decodePosition(encZ), 1.0f / 128.0f);

            assertEquals(1, (word1 >>> 4) & 0x7, "normal id +Y");
            assertEquals(material.bits() & 0xF, (word1 >>> 7) & 0xF, "material bits");
            assertEquals(7, (word1 >>> 11) & 0xFF, "block light");
            assertEquals(15, (word1 >>> 19) & 0xFF, "sky light");

            int encU = ((word2 & 0xFFFF) << 2) | ((word1 >>> 27) & 0x3);
            int encV = (((word2 >>> 16) & 0xFFFF) << 2) | ((word1 >>> 29) & 0x3);
            assertEquals(0.25f, TerrainVertex16.decodeTexture(encU), 1e-5f);
            assertEquals(0.75f, TerrainVertex16.decodeTexture(encV), 1e-5f);

            assertEquals(0xFF804020, word3, "colour word is the compact format's a_Color");
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }
}
