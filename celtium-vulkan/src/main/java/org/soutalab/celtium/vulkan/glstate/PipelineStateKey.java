package org.soutalab.celtium.vulkan.glstate;

/**
 * 64-bit packed pipeline state key for the redefined {@code GlsmFrontend} (spec §2.6, M13).
 *
 * <p>Every GL raster/blend/depth/cull/program state mutation becomes a single bitwise operation
 * on a primitive {@code long} with zero heap allocations. The packed key drives both
 * {@code PipelinePrewarm} lookup and {@code ImmediateBatcher} draw sorting.
 *
 * <p>Bit layout (all 64 bits explicitly assigned, zero overlap):
 * <pre>
 *   [0]      blendEnable       (1 bit)
 *   [1..4]   blendSrcRgb       (4 bits, compact factor 0..15)
 *   [5..8]   blendDstRgb       (4 bits)
 *   [9..12]  blendSrcAlpha     (4 bits)
 *   [13..16] blendDstAlpha     (4 bits)
 *   [17]     depthTestEnable   (1 bit)
 *   [18]     depthWriteEnable  (1 bit)
 *   [19..21] depthFunc         (3 bits, 0..7: NEVER..ALWAYS)
 *   [22]     cullEnable        (1 bit)
 *   [23..24] cullFace          (2 bits: 0=BACK, 1=FRONT, 2=FRONT_AND_BACK)
 *   [25]     alphaTestEnable   (1 bit)
 *   [26..28] alphaFunc         (3 bits, 0..7)
 *   [29]     polygonOffsetFill (1 bit)
 *   [30..33] colorMask         (4 bits: R=1, G=2, B=4, A=8)
 *   [34..36] topology          (3 bits: 0=TRIANGLES, 1=QUADS, 2=LINES, 3=LINE_STRIP,
 *                                      4=TRI_STRIP, 5=TRI_FAN, 6=POINTS)
 *   [37..40] vertexFormatId    (4 bits: 0..15)
 *   [41]     texture2dEnable   (1 bit)
 *   [42]     lightmapEnable    (1 bit)
 *   [43]     fogEnable         (1 bit)
 *   [44]     lightingEnable    (1 bit)
 *   [45]     scissorEnable     (1 bit)
 *   [46..47] blendEquation     (2 bits: 0=ADD, 1=SUBTRACT, 2=REVERSE_SUBTRACT, 3=MIN/MAX)
 *   [48..63] programId         (16 bits: 0=fixed-function, 1..65535=custom GLSL program)
 * </pre>
 */
public final class PipelineStateKey {

    // Compact blend factor codes (4 bits)
    public static final int BLEND_ZERO = 0;
    public static final int BLEND_ONE = 1;
    public static final int BLEND_SRC_COLOR = 2;
    public static final int BLEND_ONE_MINUS_SRC_COLOR = 3;
    public static final int BLEND_DST_COLOR = 4;
    public static final int BLEND_ONE_MINUS_DST_COLOR = 5;
    public static final int BLEND_SRC_ALPHA = 6;
    public static final int BLEND_ONE_MINUS_SRC_ALPHA = 7;
    public static final int BLEND_DST_ALPHA = 8;
    public static final int BLEND_ONE_MINUS_DST_ALPHA = 9;
    public static final int BLEND_CONSTANT_COLOR = 10;
    public static final int BLEND_ONE_MINUS_CONSTANT_COLOR = 11;
    public static final int BLEND_CONSTANT_ALPHA = 12;
    public static final int BLEND_ONE_MINUS_CONSTANT_ALPHA = 13;
    public static final int BLEND_SRC_ALPHA_SATURATE = 14;

    // Compact compare ops (3 bits, matching GL_NEVER..GL_ALWAYS - 0x0200)
    public static final int CMP_NEVER = 0;
    public static final int CMP_LESS = 1;
    public static final int CMP_EQUAL = 2;
    public static final int CMP_LEQUAL = 3;
    public static final int CMP_GREATER = 4;
    public static final int CMP_NOTEQUAL = 5;
    public static final int CMP_GEQUAL = 6;
    public static final int CMP_ALWAYS = 7;

    // Cull face codes (2 bits)
    public static final int CULL_BACK = 0;
    public static final int CULL_FRONT = 1;
    public static final int CULL_FRONT_AND_BACK = 2;

    // Topology codes (3 bits)
    public static final int TOPO_TRIANGLES = 0;
    public static final int TOPO_QUADS = 1;
    public static final int TOPO_LINES = 2;
    public static final int TOPO_LINE_STRIP = 3;
    public static final int TOPO_TRIANGLE_STRIP = 4;
    public static final int TOPO_TRIANGLE_FAN = 5;
    public static final int TOPO_POINTS = 6;

    // Bit shifts
    public static final int SHIFT_BLEND_ENABLE = 0;
    public static final int SHIFT_BLEND_SRC_RGB = 1;
    public static final int SHIFT_BLEND_DST_RGB = 5;
    public static final int SHIFT_BLEND_SRC_ALPHA = 9;
    public static final int SHIFT_BLEND_DST_ALPHA = 13;
    public static final int SHIFT_DEPTH_TEST = 17;
    public static final int SHIFT_DEPTH_WRITE = 18;
    public static final int SHIFT_DEPTH_FUNC = 19;
    public static final int SHIFT_CULL_ENABLE = 22;
    public static final int SHIFT_CULL_FACE = 23;
    public static final int SHIFT_ALPHA_TEST = 25;
    public static final int SHIFT_ALPHA_FUNC = 26;
    public static final int SHIFT_POLY_OFFSET = 29;
    public static final int SHIFT_COLOR_MASK = 30;
    public static final int SHIFT_TOPOLOGY = 34;
    public static final int SHIFT_VERTEX_FORMAT = 37;
    public static final int SHIFT_TEX2D = 41;
    public static final int SHIFT_LIGHTMAP = 42;
    public static final int SHIFT_FOG = 43;
    public static final int SHIFT_LIGHTING = 44;
    public static final int SHIFT_SCISSOR = 45;
    public static final int SHIFT_BLEND_EQ = 46;
    public static final int SHIFT_PROGRAM_ID = 48;

    public static final long BIT_BLEND_ENABLE = 1L << SHIFT_BLEND_ENABLE;
    public static final long BIT_DEPTH_TEST = 1L << SHIFT_DEPTH_TEST;
    public static final long BIT_DEPTH_WRITE = 1L << SHIFT_DEPTH_WRITE;
    public static final long BIT_CULL_ENABLE = 1L << SHIFT_CULL_ENABLE;
    public static final long BIT_ALPHA_TEST = 1L << SHIFT_ALPHA_TEST;
    public static final long BIT_POLY_OFFSET = 1L << SHIFT_POLY_OFFSET;
    public static final long BIT_TEX2D = 1L << SHIFT_TEX2D;
    public static final long BIT_LIGHTMAP = 1L << SHIFT_LIGHTMAP;
    public static final long BIT_FOG = 1L << SHIFT_FOG;
    public static final long BIT_LIGHTING = 1L << SHIFT_LIGHTING;
    public static final long BIT_SCISSOR = 1L << SHIFT_SCISSOR;

    public static final long MASK_BLEND_BITS = 0x1FFFFL; // bits 0..16
    public static final long MASK_DEPTH_BITS = 0x1FL << SHIFT_DEPTH_TEST; // bits 17..21
    public static final long MASK_CULL_BITS = 0x7L << SHIFT_CULL_ENABLE; // bits 22..24
    public static final long MASK_ALPHA_BITS = 0xFL << SHIFT_ALPHA_TEST; // bits 25..28
    public static final long MASK_COLOR_MASK = 0xFL << SHIFT_COLOR_MASK; // bits 30..33

    /** Standard OpenGL 2.1 initial state: blend off (ONE/ZERO), depth write on (LESS), RGBA mask on. */
    public static final long DEFAULT_GL_KEY = buildDefault();

    private PipelineStateKey() {
    }

    private static long buildDefault() {
        long k = 0L;
        k = withBlendFuncSeparate(k, BLEND_ONE, BLEND_ZERO, BLEND_ONE, BLEND_ZERO);
        k = withDepthWrite(k, true);
        k = withDepthFunc(k, CMP_LESS);
        k = withCullFace(k, CULL_BACK);
        k = withAlphaFunc(k, CMP_ALWAYS);
        k = withColorMask(k, 0xF);
        k = withTopology(k, TOPO_QUADS);
        return k;
    }

    private static long setBit(long key, long bit, boolean value) {
        return value ? (key | bit) : (key & ~bit);
    }

    private static long setField(long key, int shift, long mask, int value) {
        return (key & ~(mask << shift)) | (((long) value & mask) << shift);
    }

    public static long withBlendEnable(long key, boolean enable) {
        return setBit(key, BIT_BLEND_ENABLE, enable);
    }

    public static boolean blendEnable(long key) {
        return (key & BIT_BLEND_ENABLE) != 0L;
    }

    public static long withBlendFuncSeparate(long key, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        key = setField(key, SHIFT_BLEND_SRC_RGB, 0xFL, srcRgb);
        key = setField(key, SHIFT_BLEND_DST_RGB, 0xFL, dstRgb);
        key = setField(key, SHIFT_BLEND_SRC_ALPHA, 0xFL, srcAlpha);
        key = setField(key, SHIFT_BLEND_DST_ALPHA, 0xFL, dstAlpha);
        return key;
    }

    public static int blendSrcRgb(long key) {
        return (int) ((key >>> SHIFT_BLEND_SRC_RGB) & 0xFL);
    }

    public static int blendDstRgb(long key) {
        return (int) ((key >>> SHIFT_BLEND_DST_RGB) & 0xFL);
    }

    public static int blendSrcAlpha(long key) {
        return (int) ((key >>> SHIFT_BLEND_SRC_ALPHA) & 0xFL);
    }

    public static int blendDstAlpha(long key) {
        return (int) ((key >>> SHIFT_BLEND_DST_ALPHA) & 0xFL);
    }

    public static long withDepthTest(long key, boolean enable) {
        return setBit(key, BIT_DEPTH_TEST, enable);
    }

    public static boolean depthTest(long key) {
        return (key & BIT_DEPTH_TEST) != 0L;
    }

    public static long withDepthWrite(long key, boolean enable) {
        return setBit(key, BIT_DEPTH_WRITE, enable);
    }

    public static boolean depthWrite(long key) {
        return (key & BIT_DEPTH_WRITE) != 0L;
    }

    public static long withDepthFunc(long key, int cmp) {
        return setField(key, SHIFT_DEPTH_FUNC, 0x7L, cmp);
    }

    public static int depthFunc(long key) {
        return (int) ((key >>> SHIFT_DEPTH_FUNC) & 0x7L);
    }

    public static long withCullEnable(long key, boolean enable) {
        return setBit(key, BIT_CULL_ENABLE, enable);
    }

    public static boolean cullEnable(long key) {
        return (key & BIT_CULL_ENABLE) != 0L;
    }

    public static long withCullFace(long key, int face) {
        return setField(key, SHIFT_CULL_FACE, 0x3L, face);
    }

    public static int cullFace(long key) {
        return (int) ((key >>> SHIFT_CULL_FACE) & 0x3L);
    }

    public static long withAlphaTest(long key, boolean enable) {
        return setBit(key, BIT_ALPHA_TEST, enable);
    }

    public static boolean alphaTest(long key) {
        return (key & BIT_ALPHA_TEST) != 0L;
    }

    public static long withAlphaFunc(long key, int cmp) {
        return setField(key, SHIFT_ALPHA_FUNC, 0x7L, cmp);
    }

    public static int alphaFunc(long key) {
        return (int) ((key >>> SHIFT_ALPHA_FUNC) & 0x7L);
    }

    public static long withPolygonOffsetFill(long key, boolean enable) {
        return setBit(key, BIT_POLY_OFFSET, enable);
    }

    public static boolean polygonOffsetFill(long key) {
        return (key & BIT_POLY_OFFSET) != 0L;
    }

    public static long withColorMask(long key, int rgbaMask) {
        return setField(key, SHIFT_COLOR_MASK, 0xFL, rgbaMask);
    }

    public static int colorMask(long key) {
        return (int) ((key >>> SHIFT_COLOR_MASK) & 0xFL);
    }

    public static long withTopology(long key, int topology) {
        return setField(key, SHIFT_TOPOLOGY, 0x7L, topology);
    }

    public static int topology(long key) {
        return (int) ((key >>> SHIFT_TOPOLOGY) & 0x7L);
    }

    public static long withVertexFormat(long key, int formatId) {
        return setField(key, SHIFT_VERTEX_FORMAT, 0xFL, formatId);
    }

    public static int vertexFormat(long key) {
        return (int) ((key >>> SHIFT_VERTEX_FORMAT) & 0xFL);
    }

    public static long withTexture2d(long key, boolean enable) {
        return setBit(key, BIT_TEX2D, enable);
    }

    public static boolean texture2d(long key) {
        return (key & BIT_TEX2D) != 0L;
    }

    public static long withLightmap(long key, boolean enable) {
        return setBit(key, BIT_LIGHTMAP, enable);
    }

    public static boolean lightmap(long key) {
        return (key & BIT_LIGHTMAP) != 0L;
    }

    public static long withFog(long key, boolean enable) {
        return setBit(key, BIT_FOG, enable);
    }

    public static boolean fog(long key) {
        return (key & BIT_FOG) != 0L;
    }

    public static long withLighting(long key, boolean enable) {
        return setBit(key, BIT_LIGHTING, enable);
    }

    public static boolean lighting(long key) {
        return (key & BIT_LIGHTING) != 0L;
    }

    public static long withScissor(long key, boolean enable) {
        return setBit(key, BIT_SCISSOR, enable);
    }

    public static boolean scissor(long key) {
        return (key & BIT_SCISSOR) != 0L;
    }

    public static long withBlendEquation(long key, int eq) {
        return setField(key, SHIFT_BLEND_EQ, 0x3L, eq);
    }

    public static int blendEquation(long key) {
        return (int) ((key >>> SHIFT_BLEND_EQ) & 0x3L);
    }

    public static long withProgramId(long key, int programId) {
        return setField(key, SHIFT_PROGRAM_ID, 0xFFFFL, programId);
    }

    public static int programId(long key) {
        return (int) ((key >>> SHIFT_PROGRAM_ID) & 0xFFFFL);
    }

    /**
     * Returns {@code true} if draws under this key require order-preserving translucent batching
     * (blend enabled and destination factor is not {@link #BLEND_ZERO}).
     */
    public static boolean isTranslucent(long key) {
        return blendEnable(key) && blendDstRgb(key) != BLEND_ZERO;
    }

    /**
     * Folds the 64-bit pipeline key into a 16-bit hash suitable for {@code DrawSortKey.pipelineKey}
     * when grouping opaque draws in {@code ImmediateBatcher}.
     */
    public static int toCompact16BitKey(long key) {
        long h = key ^ (key >>> 32);
        h *= 0x9E3779B97F4A7C15L;
        h ^= (h >>> 32);
        return (int) (h & 0xFFFFL);
    }
}
