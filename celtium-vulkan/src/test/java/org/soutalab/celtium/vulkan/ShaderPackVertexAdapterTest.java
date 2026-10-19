package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The conversion a pack needs from the renderer's own vertex formats: position, colour, texture coordinates and a normal,
 * with the pack-only attributes given the values that keep the shader from blacking a surface out.
 */
class ShaderPackVertexAdapterTest {
    @Test
    void blockFormatBecomesTheOptifineAttributes() {
        // pos3f col4ub tex2f lightmap2s, stride 28
        ByteBuffer source = ByteBuffer.allocate(28).order(ByteOrder.nativeOrder());
        source.putFloat(0, 1.0f).putFloat(4, 2.0f).putFloat(8, 3.0f);
        source.put(12, (byte) 255).put(13, (byte) 128).put(14, (byte) 0).put(15, (byte) 255);
        source.putFloat(16, 0.25f).putFloat(20, 0.75f);
        source.putShort(24, (short) 100).putShort(26, (short) 200);
        Map<String, float[]> attributes = ShaderPackVertexAdapter.adapt(3, source, 28, 1);
        assertEquals(1.0f, attributes.get("a_Position")[0], 0.0001f);
        assertEquals(3.0f, attributes.get("a_Position")[2], 0.0001f);
        assertEquals(1.0f, attributes.get("a_Position")[3], 0.0001f, "position w must be 1");
        assertEquals(1.0f, attributes.get("a_Colour")[0], 0.0001f);
        assertEquals(128.0f / 255.0f, attributes.get("a_Colour")[1], 0.0001f);
        assertEquals(0.25f, attributes.get("a_TexCoord0")[0], 0.0001f);
        assertEquals(0.0f, attributes.get("a_Normal")[0], 0.0001f);
        assertEquals(1.0f, attributes.get("a_Normal")[2], 0.0001f, "a missing normal faces the viewer");
        assertEquals(0.0f, attributes.get("mc_Entity")[3], 0.0001f);
    }

    @Test
    void texturedFormatGetsOpaqueWhiteWhereThereIsNoColour() {
        // pos3f tex2f, stride 20
        ByteBuffer source = ByteBuffer.allocate(20).order(ByteOrder.nativeOrder());
        source.putFloat(0, 0.5f).putFloat(4, 0.5f).putFloat(8, 0.5f);
        source.putFloat(12, 0.1f).putFloat(16, 0.2f);
        Map<String, float[]> attributes = ShaderPackVertexAdapter.adapt(6, source, 20, 1);
        assertArrayEquals(new float[] {1.0f, 1.0f, 1.0f, 1.0f}, attributes.get("a_Colour"), 0.0001f);
        assertEquals(0.1f, attributes.get("a_TexCoord0")[0], 0.0001f);
    }

    @Test
    void refusesAFormatItDoesNotUnderstand() {
        assertNull(ShaderPackVertexAdapter.adapt(99, ByteBuffer.allocate(4), 4, 1));
    }

    @Test
    void convertsSeveralVerticesIndependently() {
        ByteBuffer source = ByteBuffer.allocate(40).order(ByteOrder.nativeOrder());
        for (int vertex = 0; vertex < 2; vertex++) {
            source.putFloat(vertex * 20, vertex);
            source.putFloat(vertex * 20 + 4, vertex + 1);
            source.putFloat(vertex * 20 + 8, vertex + 2);
        }
        Map<String, float[]> attributes = ShaderPackVertexAdapter.adapt(6, source, 20, 2);
        assertEquals(0.0f, attributes.get("a_Position")[0], 0.0001f);
        assertEquals(1.0f, attributes.get("a_Position")[4], 0.0001f);
        assertEquals(3.0f, attributes.get("a_Position")[6], 0.0001f);
    }

    /** Encodes one vertex exactly as CompactChunkVertex.createEncoder does, then checks the decode. */
    @Test
    void terrainVerticesDecodeToThePackAttributes() {
        java.nio.ByteBuffer source = java.nio.ByteBuffer.allocate(24).order(java.nio.ByteOrder.nativeOrder());
        float xValue = 1.5f;
        float yValue = 2.25f;
        float zValue = -3.0f;
        int x = encodeTerrainPosition(xValue);
        int y = encodeTerrainPosition(yValue);
        int z = encodeTerrainPosition(zValue);
        source.putShort(0, (short) (x >>> 5));
        source.putShort(2, (short) (y >>> 5));
        source.putShort(4, (short) (z >>> 5));
        source.putShort(6, (short) ((x & 31) | ((y & 31) << 5) | ((z & 31) << 10)));
        source.putInt(8, (0x80) | (0x40 << 8) | (0x20 << 16) | (0xFF << 24));
        int u = 0x2A5A1;
        int v = 0x1B2C3;
        source.putShort(12, (short) (u >>> 2));
        source.putShort(14, (short) (v >>> 2));
        int drawParameters = ((u & 3) << 4) | ((v & 3) << 6) | (0x07 << 8);
        int light = (0x0F) | (0xF0 << 8);
        source.putInt(16, drawParameters | (light << 16));
        source.putInt(20, 0);

        Map<String, float[]> attributes = ShaderPackVertexAdapter.adaptTerrain(source, 24, 1);
        // The quantisation step of the position field is 32 / 2^21, far below these tolerances.
        assertEquals(xValue, attributes.get("a_Position")[0], 0.001f);
        assertEquals(yValue, attributes.get("a_Position")[1], 0.001f);
        assertEquals(zValue, attributes.get("a_Position")[2], 0.001f);
        assertEquals(1.0f, attributes.get("a_Position")[3], 0.0001f);
        assertEquals(128.0f / 255.0f, attributes.get("a_Colour")[0], 0.001f);
        assertEquals(64.0f / 255.0f, attributes.get("a_Colour")[1], 0.001f);
        assertEquals(32.0f / 255.0f, attributes.get("a_Colour")[2], 0.001f);
        assertEquals(255.0f / 255.0f, attributes.get("a_Colour")[3], 0.001f);
        assertEquals(u / (float) (1 << 17), attributes.get("a_TexCoord0")[0], 0.0001f);
        assertEquals(v / (float) (1 << 17), attributes.get("a_TexCoord0")[1], 0.0001f);
        assertEquals(15.0f / 255.0f, attributes.get("mc_midTexCoord")[0], 0.001f);
        assertEquals(240.0f / 255.0f, attributes.get("mc_midTexCoord")[1], 0.001f);
        assertEquals(1.0f, attributes.get("a_Normal")[2], 0.0001f, "the format has no normal: face-on default");
    }

    @Test
    void terrainDecodeRefusesAShortStride() {
        assertNull(ShaderPackVertexAdapter.adaptTerrain(ByteBuffer.allocate(24), 16, 1));
    }

    /** {@return the packed position field for a value}, matching CompactChunkVertex's encoder. */
    private static int encodeTerrainPosition(float value) {
        return (int) ((8.0f + value) * ((float) (1 << 21) / 32.0f)) & ((1 << 21) - 1);
    }
}
