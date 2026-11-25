package org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl;

import org.embeddedt.embeddium.api.util.NormI8;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttributeFormat;
import org.embeddedt.embeddium.impl.gl.attribute.GlVertexFormat;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexEncoder;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkVertexType;
import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

import java.util.Map;

/**
 * The Tier-A terrain vertex (new rendering architecture, Phase 2/3): 16 bytes per vertex,
 * four uint32 words, laid out by {@code SECTION-DESC-SPEC-2026-11-25.md}:
 *
 * <pre>
 * word 0: posX 12b [0..11] | posY 12b [12..23] | posZ low 8b [24..31]
 * word 1: posZ high 4b [0..3] | normalId 3b [4..6] | material 4b [7..10]
 *       | lightBlock 8b [11..18] | lightSky 8b [19..26] | uvLowU 2b [27..28] | uvLowV 2b [29..30]
 * word 2: texU high 16b [0..15] | texV high 16b [16..31]
 * word 3: color RGBA8 (identical to the compact format's a_Color)
 * </pre>
 *
 * Against {@link CompactChunkVertex} (24 B) the position quantisation drops from 21 bits to
 * 12 bits per axis (step 32/4096 = 1/128 block, same MODEL_ORIGIN/MODEL_RANGE space); the
 * 18-bit texture encoding is kept bit-for-bit. Two fields leave the vertex entirely: the
 * per-vertex section index (delivered by the SectionDesc record plus firstInstance on the
 * GPU path) and {@code a_RdhFactor} — the bilinear colour correction is constant over a
 * quad, so it travels in a per-quad side stream instead (spec recommendation (ii)): this
 * encoder declares {@link ChunkVertexEncoder#capturesRdhFactorSideStream()} and the buffer
 * builder records one int per quad, in bucket order, next to the vertex data.
 *
 * The normal id follows the SectionDesc bucket order (ModelQuadFacing.VALUES):
 * 0=+X, 1=+Y, 2=+Z, 3=-X, 4=-Y, 5=-Z, 6=none (flat derivation), 7=reserved. It is derived
 * from the vertex' vanilla normal; a normal that is not axis-dominant maps to 6.
 */
public class TerrainVertex16 implements ChunkVertexType {
    public static final GlVertexFormat VERTEX_FORMAT = GlVertexFormat.builder(16)
            .addElement("a_VertexData", 0, GlVertexAttributeFormat.UNSIGNED_INT, 4, false, true)
            .build();

    public static final int STRIDE = 16;

    public static final int POSITION_BITS = 12;
    private static final int POSITION_MASK = (1 << POSITION_BITS) - 1;
    private static final int POSITION_MAX_VALUE = 1 << POSITION_BITS;

    private static final int TEXTURE_BITS = 18;
    private static final int TEXTURE_LOW_BITS = 2;
    private static final int TEXTURE_MASK = (1 << TEXTURE_BITS) - 1;
    private static final int TEXTURE_LOW_MASK = (1 << TEXTURE_LOW_BITS) - 1;
    private static final int TEXTURE_MAX_VALUE = 1 << (TEXTURE_BITS - 1);

    private static final int MATERIAL_BITS = 4;
    private static final int MATERIAL_MASK = (1 << MATERIAL_BITS) - 1;

    private static final float MODEL_ORIGIN = 8.0f;
    private static final float MODEL_RANGE = 32.0f;
    private static final float MODEL_SCALE = MODEL_RANGE / POSITION_MAX_VALUE;
    private static final float MODEL_SCALE_INV = POSITION_MAX_VALUE / MODEL_RANGE;

    private static final float TEXTURE_SCALE = (1.0f / TEXTURE_MAX_VALUE);

    /** Normal ids, in ModelQuadFacing.VALUES order; 6 is "no axis normal". */
    public static final int NORMAL_NONE = 6;

    @Override
    public float getTextureScale() {
        return TEXTURE_SCALE;
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
    public GlVertexFormat getVertexFormat() {
        return VERTEX_FORMAT;
    }

    @Override
    public ChunkVertexEncoder createEncoder() {
        return new ChunkVertexEncoder() {
            @Override
            public long write(long ptr, Material material, Vertex vertex, int sectionIndex) {
                int x = encodePosition(vertex.x);
                int y = encodePosition(vertex.y);
                int z = encodePosition(vertex.z);

                int u = encodeTexture(vertex.u);
                int v = encodeTexture(vertex.v);

                int word0 = (x & POSITION_MASK)
                        | ((y & POSITION_MASK) << 12)
                        | ((z & 0xFF) << 24);
                int word1 = ((z >>> 8) & 0xF)
                        | (normalId(vertex.vanillaNormal) << 4)
                        | ((material.bits() & MATERIAL_MASK) << 7)
                        | ((vertex.light & 0xFF) << 11)
                        | (((vertex.light >>> 16) & 0xFF) << 19)
                        | ((u & TEXTURE_LOW_MASK) << 27)
                        | ((v & TEXTURE_LOW_MASK) << 29);
                int word2 = ((u >>> TEXTURE_LOW_BITS) & 0xFFFF)
                        | (((v >>> TEXTURE_LOW_BITS) & 0xFFFF) << 16);

                LWJGL.memPutInt(ptr + 0, word0);
                LWJGL.memPutInt(ptr + 4, word1);
                LWJGL.memPutInt(ptr + 8, word2);
                LWJGL.memPutInt(ptr + 12, vertex.color);

                return ptr + STRIDE;
            }

            @Override
            public boolean capturesRdhFactorSideStream() {
                return true;
            }
        };
    }

    @Override
    public Map<String, String> getDefines() {
        var map = ChunkVertexType.super.getDefines();
        map.put("USE_VERTEX_COMPRESSION", "");
        return map;
    }

    private static int encodePosition(float value) {
        int encoded = (int) ((MODEL_ORIGIN + value) * MODEL_SCALE_INV);
        if (encoded < 0) {
            return 0;
        }
        return Math.min(encoded, POSITION_MASK);
    }

    /** Reference decoder for the 12-bit position field; mirrors the shader's expansion. */
    public static float decodePosition(int encoded) {
        return ((float) (encoded & POSITION_MASK) / MODEL_SCALE_INV) - MODEL_ORIGIN;
    }

    private static int encodeTexture(float value) {
        float scaled = value * TEXTURE_MAX_VALUE;
        int rounded = scaled >= 0.0f ? (int) (scaled + 0.5f) : Math.round(scaled);
        return rounded & TEXTURE_MASK;
    }

    /** Reference decoder for the 18-bit texture field. */
    public static float decodeTexture(int encoded) {
        return (encoded & TEXTURE_MASK) * TEXTURE_SCALE;
    }

    /**
     * Maps a NormI8-packed vanilla normal to a bucket-order normal id. Axis-dominant
     * normals take their direction's id; anything else (including the zero normal) is
     * {@link #NORMAL_NONE}.
     */
    public static int normalId(int packedNormal) {
        float x = NormI8.unpackX(packedNormal);
        float y = NormI8.unpackY(packedNormal);
        float z = NormI8.unpackZ(packedNormal);
        float ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        float max = Math.max(ax, Math.max(ay, az));
        if (max < 0.5f) {
            return NORMAL_NONE;
        }
        if (max == ax) {
            return x > 0.0f ? 0 : 3;
        }
        if (max == ay) {
            return y > 0.0f ? 1 : 4;
        }
        return z > 0.0f ? 2 : 5;
    }

    /** Packs four already-encoded words; the exact inverse of the reference unpackers below. */
    public static int packWord0(int x, int y, int z) {
        return (x & POSITION_MASK) | ((y & POSITION_MASK) << 12) | ((z & 0xFF) << 24);
    }

    public static int unpackPosX(int word0) {
        return word0 & POSITION_MASK;
    }

    public static int unpackPosY(int word0) {
        return (word0 >>> 12) & POSITION_MASK;
    }

    public static int unpackPosZ(int word0, int word1) {
        return (word0 >>> 24) | ((word1 & 0xF) << 8);
    }
}
