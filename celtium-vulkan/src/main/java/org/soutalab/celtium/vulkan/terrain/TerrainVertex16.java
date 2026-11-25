package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * 16-byte (4 x uint32) compressed terrain vertex format for the new architecture
 * ({@code CELTIUM-NEW-ARCH-2026-11-25.md} §2.4, {@code SECTION-DESC-SPEC-2026-11-25.md}).
 *
 * <p>Reduces terrain vertex fetch bandwidth by 33% relative to Celeritas's 24-byte
 * {@code CompactChunkVertex} by quantizing section-local XYZ to 12 bits/axis (step 1/128 block
 * over {@code [-8, 24)}) while preserving full 18-bit UV precision and RGBA8 color.
 *
 * <pre>
 *   word 0 : posX 12b [0..11] | posY 12b [12..23] | posZ_lo 8b [24..31]
 *   word 1 : posZ_hi 4b [0..3] | normalId 3b [4..6] | material 4b [7..10]
 *            | lightBlock 8b [11..18] | lightSky 8b [19..26]
 *            | uvLowU 2b [27..28] | uvLowV 2b [29..30] | reserved 1b [31]
 *   word 2 : texU_hi 16b [0..15] | texV_hi 16b [16..31]
 *   word 3 : color RGBA8 (identical to CompactChunkVertex a_Color)
 * </pre>
 */
public final class TerrainVertex16 implements ChunkVertexType {
    public static final int STRIDE = 16;
    public static final int STRIDE_INTS = 4;

    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(STRIDE)
            .addElement("a_Packed", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .build();

    public static final int POSITION_BITS = 12;
    public static final int POSITION_MASK = (1 << POSITION_BITS) - 1;
    public static final int POSITION_MAX_VALUE = 1 << POSITION_BITS; // 4096

    public static final int TEXTURE_BITS = 18;
    public static final int TEXTURE_LOW_BITS = 2;
    public static final int TEXTURE_MASK = (1 << TEXTURE_BITS) - 1;
    public static final int TEXTURE_LOW_MASK = (1 << TEXTURE_LOW_BITS) - 1;
    public static final int TEXTURE_MAX_VALUE = 1 << (TEXTURE_BITS - 1); // 131072

    public static final int MATERIAL_MASK = 0xF;
    public static final int NORMAL_MASK = 0x7;

    public static final int NORMAL_DOWN = SectionDesc.BUCKET_DOWN;
    public static final int NORMAL_UP = SectionDesc.BUCKET_UP;
    public static final int NORMAL_NORTH = SectionDesc.BUCKET_NORTH;
    public static final int NORMAL_SOUTH = SectionDesc.BUCKET_SOUTH;
    public static final int NORMAL_WEST = SectionDesc.BUCKET_WEST;
    public static final int NORMAL_EAST = SectionDesc.BUCKET_EAST;
    public static final int NORMAL_UNASSIGNED = SectionDesc.BUCKET_UNASSIGNED;

    public static final float MODEL_ORIGIN = 8.0f;
    public static final float MODEL_RANGE = 32.0f;
    public static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE; // 1/128
    public static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE; // 128.0f
    public static final float TEXTURE_SCALE = 1.0f / TEXTURE_MAX_VALUE; // 2^-17

    public static final TerrainVertex16 INSTANCE = new TerrainVertex16();

    public record DecodedVertex(
            float x,
            float y,
            float z,
            int normalId,
            int materialBits,
            int lightBlock,
            int lightSky,
            float u,
            float v,
            int color
    ) {
    }

    @Override
    public float getPositionScale() {
        return MODEL_SCALE;
    }

    @Override
    public float getPositionOffset() {
        return -MODEL_ORIGIN;
    }

    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
    }

    @Override
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return (ptr, material, vertex, sectionIndex) -> {
            int normalId = normalIdFromPackedNormal(vertex.vanillaNormal);
            encodeToAddress(
                    ptr,
                    vertex.x, vertex.y, vertex.z,
                    normalId,
                    material.bits(),
                    vertex.light,
                    vertex.u, vertex.v,
                    vertex.color
            );
            return ptr + STRIDE;
        };
    }

    @Override
    public Map<String, String> getDefines() {
        var map = ChunkVertexType.super.getDefines();
        map.put("USE_TERRAIN_VERTEX_16", "");
        return map;
    }

    public static int encodePosition(float value) {
        int q = Math.round((MODEL_ORIGIN + value) * MODEL_SCALE_INV);
        if (q < 0) {
            return 0;
        }
        if (q > POSITION_MASK) {
            return POSITION_MASK;
        }
        return q;
    }

    public static float decodePosition(int quantized) {
        return ((quantized & POSITION_MASK) * MODEL_SCALE) - MODEL_ORIGIN;
    }

    public static int encodeTexture(float value) {
        float scaled = value * TEXTURE_MAX_VALUE;
        int rounded = scaled >= 0.0f ? (int) (scaled + 0.5f) : Math.round(scaled);
        return rounded & TEXTURE_MASK;
    }

    public static float decodeTexture(int quantized) {
        return (quantized & TEXTURE_MASK) * TEXTURE_SCALE;
    }

    /**
     * Maps a signed 8-bit packed XYZ normal ({@code x=[0..7], y=[8..15], z=[16..23]})
     * to a 3-bit cardinal face ID matching {@link SectionDesc} buckets ({@code 0..5}),
     * or {@link #NORMAL_UNASSIGNED} ({@code 6}) for non-axis-aligned / zero normals.
     */
    public static int normalIdFromPackedNormal(int packedNormal) {
        byte nx = (byte) (packedNormal & 0xFF);
        byte ny = (byte) ((packedNormal >>> 8) & 0xFF);
        byte nz = (byte) ((packedNormal >>> 16) & 0xFF);
        int ax = Math.abs((int) nx);
        int ay = Math.abs((int) ny);
        int az = Math.abs((int) nz);
        if (ax == 0 && ay == 0 && az == 0) {
            return NORMAL_UNASSIGNED;
        }
        // Only classify as a cardinal face when one axis is dominant (> 0.9 of max byte 127).
        if (ay > ax && ay > az && ay >= 114) {
            return ny < 0 ? NORMAL_DOWN : NORMAL_UP;
        }
        if (az > ax && az > ay && az >= 114) {
            return nz < 0 ? NORMAL_NORTH : NORMAL_SOUTH;
        }
        if (ax > ay && ax > az && ax >= 114) {
            return nx < 0 ? NORMAL_WEST : NORMAL_EAST;
        }
        return NORMAL_UNASSIGNED;
    }

    public static int packWord0(int qx, int qy, int qz) {
        return (qx & POSITION_MASK)
                | ((qy & POSITION_MASK) << 12)
                | ((qz & 0xFF) << 24);
    }

    public static int packWord1(
            int qz,
            int normalId,
            int materialBits,
            int lightBlock,
            int lightSky,
            int qU,
            int qV
    ) {
        return ((qz >>> 8) & 0xF)
                | ((normalId & NORMAL_MASK) << 4)
                | ((materialBits & MATERIAL_MASK) << 7)
                | ((lightBlock & 0xFF) << 11)
                | ((lightSky & 0xFF) << 19)
                | ((qU & TEXTURE_LOW_MASK) << 27)
                | ((qV & TEXTURE_LOW_MASK) << 29);
    }

    public static int packWord2(int qU, int qV) {
        return ((qU >>> TEXTURE_LOW_BITS) & 0xFFFF)
                | (((qV >>> TEXTURE_LOW_BITS) & 0xFFFF) << 16);
    }

    public static void encodeToAddress(
            long ptr,
            float x, float y, float z,
            int normalId,
            int materialBits,
            int packedLight,
            float u, float v,
            int color
    ) {
        int qx = encodePosition(x);
        int qy = encodePosition(y);
        int qz = encodePosition(z);
        int qU = encodeTexture(u);
        int qV = encodeTexture(v);
        int lightBlock = packedLight & 0xFF;
        int lightSky = (packedLight >>> 16) & 0xFF;

        MemoryUtil.memPutInt(ptr + 0, packWord0(qx, qy, qz));
        MemoryUtil.memPutInt(ptr + 4, packWord1(qz, normalId, materialBits, lightBlock, lightSky, qU, qV));
        MemoryUtil.memPutInt(ptr + 8, packWord2(qU, qV));
        MemoryUtil.memPutInt(ptr + 12, color);
    }

    public static void encodeToBuffer(
            ByteBuffer dst, int byteOffset,
            float x, float y, float z,
            int normalId,
            int materialBits,
            int packedLight,
            float u, float v,
            int color
    ) {
        int qx = encodePosition(x);
        int qy = encodePosition(y);
        int qz = encodePosition(z);
        int qU = encodeTexture(u);
        int qV = encodeTexture(v);
        int lightBlock = packedLight & 0xFF;
        int lightSky = (packedLight >>> 16) & 0xFF;

        dst.putInt(byteOffset + 0, packWord0(qx, qy, qz));
        dst.putInt(byteOffset + 4, packWord1(qz, normalId, materialBits, lightBlock, lightSky, qU, qV));
        dst.putInt(byteOffset + 8, packWord2(qU, qV));
        dst.putInt(byteOffset + 12, color);
    }

    public static void encodeToInts(
            int[] dst, int intOffset,
            float x, float y, float z,
            int normalId,
            int materialBits,
            int packedLight,
            float u, float v,
            int color
    ) {
        int qx = encodePosition(x);
        int qy = encodePosition(y);
        int qz = encodePosition(z);
        int qU = encodeTexture(u);
        int qV = encodeTexture(v);
        int lightBlock = packedLight & 0xFF;
        int lightSky = (packedLight >>> 16) & 0xFF;

        dst[intOffset + 0] = packWord0(qx, qy, qz);
        dst[intOffset + 1] = packWord1(qz, normalId, materialBits, lightBlock, lightSky, qU, qV);
        dst[intOffset + 2] = packWord2(qU, qV);
        dst[intOffset + 3] = color;
    }

    public static DecodedVertex decode(int w0, int w1, int w2, int w3) {
        int qx = w0 & POSITION_MASK;
        int qy = (w0 >>> 12) & POSITION_MASK;
        int qz = ((w0 >>> 24) & 0xFF) | ((w1 & 0xF) << 8);
        int normalId = (w1 >>> 4) & NORMAL_MASK;
        int materialBits = (w1 >>> 7) & MATERIAL_MASK;
        int lightBlock = (w1 >>> 11) & 0xFF;
        int lightSky = (w1 >>> 19) & 0xFF;
        int uvLowU = (w1 >>> 27) & TEXTURE_LOW_MASK;
        int uvLowV = (w1 >>> 29) & TEXTURE_LOW_MASK;
        int qU = ((w2 & 0xFFFF) << TEXTURE_LOW_BITS) | uvLowU;
        int qV = (((w2 >>> 16) & 0xFFFF) << TEXTURE_LOW_BITS) | uvLowV;

        return new DecodedVertex(
                decodePosition(qx),
                decodePosition(qy),
                decodePosition(qz),
                normalId,
                materialBits,
                lightBlock,
                lightSky,
                decodeTexture(qU),
                decodeTexture(qV),
                w3
        );
    }

    public static DecodedVertex decodeFromBuffer(ByteBuffer src, int byteOffset) {
        return decode(
                src.getInt(byteOffset + 0),
                src.getInt(byteOffset + 4),
                src.getInt(byteOffset + 8),
                src.getInt(byteOffset + 12)
        );
    }

    public static DecodedVertex decodeFromAddress(long ptr) {
        return decode(
                MemoryUtil.memGetInt(ptr + 0),
                MemoryUtil.memGetInt(ptr + 4),
                MemoryUtil.memGetInt(ptr + 8),
                MemoryUtil.memGetInt(ptr + 12)
        );
    }
}
