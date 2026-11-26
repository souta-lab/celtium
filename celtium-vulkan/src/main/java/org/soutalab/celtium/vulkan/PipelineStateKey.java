package org.soutalab.celtium.vulkan;

/**
 * The pipeline-selection state of one drawing state, packed into a single 64-bit key
 * (Stage 5 stage 7, S5-5: the state unification's key form).
 * <p>
 * This is the canonical encoding of the question {@link VulkanGlState#pipelineKey(int)} used
 * to answer with a {@code *31} mix hash: <em>which of the values that bake into a pipeline
 * object does this state hold?</em> The mix hash discriminated everything and decoded
 * nothing; this packing discriminates the same state fields <em>exactly</em> in the low 48
 * bits - two states that differ in any packed field never share a key - and folds the vertex
 * format hash into the high 16 bits. The fields, their domains and the erasure rules are the
 * ones {@code pipelineKey} itself applies (a disabled blend erases its factors and
 * equations, a disabled test erases its function, matrices / colour / texture bindings are
 * not pipeline state and are absent), so the two keys agree on equality over every state
 * either can express in its documented domains; outside those domains the packing keeps one
 * shared escape slot per field where the mix hash kept full discrimination, and each escape
 * is named at its field below. The domains were read from the producers, not assumed:
 * <ul>
 *   <li>blend factors reach the mirror as GL enums ({@code 0}, {@code 1},
 *   {@code 0x0300..0x0307}, {@code 0x8001..0x8004} - the fifteen values
 *   {@code glBlendFactorToVk} maps, an unknown factor's batch is refused) and, as the
 *   raster's initial values, as the legacy ordinals {@code 0..9} of
 *   {@link VulkanGlState}'s {@code BLEND_*} constants. The 5-bit factor code below is
 *   injective over the union of both domains.</li>
 *   <li>blend equations reach the mirror as GL enums ({@code 0x8006..0x800B}); only their
 *   low nibble is stored, which is injective over the five defined equations and consistent
 *   with {@code glBlendEquationToVk}'s ADD default for unknown values in that family.</li>
 *   <li>depth and alpha functions reach the mirror as GL enums {@code 512..519}
 *   ({@code celtium$vkCompareOp}'s domain; anything outside it bakes as LESS_OR_EQUAL). The
 *   alpha function's pre-first-call value is the ordinal {@code 4}, which lands in the
 *   escape slot like any other out-of-domain value.</li>
 *   <li>the cull slot stores {@link VulkanCullState#encode}'s own output (0 when culling
 *   is off, 1..7 otherwise), the shade slot the mirror's 0/1 ordinals (the backend converts
 *   GL's {@code 0x1D00/0x1D01} before writing).</li>
 * </ul>
 * The key is a primitive {@code long} by design: it is stored in {@code long[]} arrays at
 * the capture and cache sites that already keep keys that way, and wrapped by this class
 * only where a caller wants the fields back. {@link #value()} is the storage form;
 * {@link #fromValue(long)} re-wraps it.
 * <p>
 * Bit layout (bit 0 = least significant):
 * <pre>
 *  bits  field
 *  0-3   colour write mask (the VK_COLOR_COMPONENT nibble)
 *  4     depth write mask
 *  5-8   depth function: 0 = depth test off, 1-8 = GL 512-519, 15 = test on, other value
 *  9-12  alpha function: 0 = alpha test off, 1-8 = GL 512-519, 15 = test on, other value
 *  13    lighting enabled
 *  14-15 shade model: 0 = flat, 1 = smooth, 3 = other value
 *  16-18 cull state ({@link VulkanCullState} encoding, 0 = culling off)
 *  19    blend enabled
 *  20-24 blend source RGB factor code (0 when blending is off)
 *  25-29 blend destination RGB factor code
 *  30-34 blend source alpha factor code
 *  35-39 blend destination alpha factor code
 *  40-43 blend equation RGB, low nibble of the GL enum (0 when blending is off)
 *  44-47 blend equation alpha, low nibble of the GL enum
 *  48-63 vertex format hash fold: (h ^ (h &gt;&gt;&gt; 16)) &amp; 0xFFFF
 * </pre>
 * Threading: immutable; the factory reads the mirror on the render thread, like the key it
 * replaces.
 */
public final class PipelineStateKey {

    /** The escape slot of a 4-bit function field: the test is on, the value is out of domain. */
    private static final int FUNCTION_OTHER = 15;
    /** The escape code of a 5-bit blend factor field: not one of the defined factors. */
    private static final int FACTOR_OTHER = 31;
    /** The escape slot of the 2-bit shade field. */
    private static final int SHADE_OTHER = 3;

    private final long value;

    private PipelineStateKey(long value) {
        this.value = value;
    }

    /** {@return the key of the state the mirror currently holds, for one vertex format}. */
    public static PipelineStateKey of(VulkanGlState state, int vertexFormatHash) {
        return pack(vertexFormatHash,
                state.blendEnabled(), state.blendSrcRgb(), state.blendDstRgb(),
                state.blendSrcAlpha(), state.blendDstAlpha(),
                state.blendEquationRgb(), state.blendEquationAlpha(),
                state.colorMask(),
                state.depthTestEnabled(), state.depthFunc(), state.depthMask(),
                state.cullState(),
                state.alphaTestEnabled(), state.alphaFunc(),
                state.lightingEnabled(), state.shadeModel());
    }

    /**
     * Packs one drawing state from its primitive values, under the same erasure rules
     * {@link VulkanGlState#pipelineKey(int)} applies: a disabled blend erases the factors
     * and equations, a disabled test erases its function.
     */
    public static PipelineStateKey pack(int vertexFormatHash,
                                        boolean blendEnabled, int blendSrcRgb, int blendDstRgb,
                                        int blendSrcAlpha, int blendDstAlpha,
                                        int blendEquationRgb, int blendEquationAlpha,
                                        int colorMask,
                                        boolean depthTestEnabled, int depthFunc, boolean depthMask,
                                        int cullState,
                                        boolean alphaTestEnabled, int alphaFunc,
                                        boolean lightingEnabled, int shadeModel) {
        long key = (long) (colorMask & 0xF);
        if (depthMask) {
            key |= 1L << 4;
        }
        key |= (long) functionSlot(depthTestEnabled, depthFunc) << 5;
        key |= (long) functionSlot(alphaTestEnabled, alphaFunc) << 9;
        if (lightingEnabled) {
            key |= 1L << 13;
        }
        key |= (long) shadeSlot(shadeModel) << 14;
        key |= (long) (cullState & 0x7) << 16;
        if (blendEnabled) {
            key |= 1L << 19;
            key |= (long) factorCode(blendSrcRgb) << 20;
            key |= (long) factorCode(blendDstRgb) << 25;
            key |= (long) factorCode(blendSrcAlpha) << 30;
            key |= (long) factorCode(blendDstAlpha) << 35;
            key |= (long) (blendEquationRgb & 0xF) << 40;
            key |= (long) (blendEquationAlpha & 0xF) << 44;
        }
        key |= (long) formatFold(vertexFormatHash) << 48;
        return new PipelineStateKey(key);
    }

    /** {@return the key stored in a primitive slot (a {@code long[]} cell, a sort key)}. */
    public static PipelineStateKey fromValue(long value) {
        return new PipelineStateKey(value);
    }

    /** The 4-bit slot of one comparison function: 0 off, 1-8 the GL domain, 15 the escape. */
    private static int functionSlot(boolean testEnabled, int glFunc) {
        if (!testEnabled) {
            return 0;
        }
        return glFunc >= 512 && glFunc <= 519 ? glFunc - 511 : FUNCTION_OTHER;
    }

    /** The 2-bit slot of the shade model: the mirror's own 0/1, 3 for anything else. */
    private static int shadeSlot(int shadeModel) {
        return shadeModel == 0 || shadeModel == 1 ? shadeModel : SHADE_OTHER;
    }

    /**
     * The 5-bit code of one blend factor, injective over the union of the two domains the
     * mirror can hold: the legacy ordinals 0-9 keep their own values, the GL factors
     * 0x0300-0x0308 map to 10-18, the constant factors 0x8001-0x8004 map to 19-22, and
     * anything else shares the escape code (an unknown factor's batch is refused before a
     * pipeline is ever chosen for it, so the escape never has to discriminate in practice).
     */
    private static int factorCode(int factor) {
        if (factor >= 0 && factor <= 9) {
            return factor;
        }
        if (factor >= 0x0300 && factor <= 0x0308) {
            return factor - 0x0300 + 10;
        }
        if (factor >= 0x8001 && factor <= 0x8004) {
            return factor - 0x8001 + 19;
        }
        return FACTOR_OTHER;
    }

    /** The factor a code came from, or -1 for the escape code. */
    private static int factorFromCode(int code) {
        if (code >= 0 && code <= 9) {
            return code;
        }
        if (code >= 10 && code <= 18) {
            return code - 10 + 0x0300;
        }
        if (code >= 19 && code <= 22) {
            return code - 19 + 0x8001;
        }
        return -1;
    }

    /** The 16-bit fold of the vertex format hash: every input bit moves the fold. */
    private static int formatFold(int vertexFormatHash) {
        return (vertexFormatHash ^ (vertexFormatHash >>> 16)) & 0xFFFF;
    }

    /** {@return the packed key, the form stored in primitive arrays and sort keys}. */
    public long value() {
        return this.value;
    }

    /** {@return the colour write mask nibble}. */
    public int colorMask() {
        return (int) (this.value & 0xF);
    }

    /** {@return whether the depth write mask is on}. */
    public boolean depthMask() {
        return (this.value & (1L << 4)) != 0L;
    }

    /** {@return whether the depth test is on}. */
    public boolean depthTestEnabled() {
        return functionSlotValue(5) != 0;
    }

    /**
     * {@return the depth function as a GL enum (512-519)}, or 0 when the test is off and
     * -1 when the packed value was the out-of-domain escape.
     */
    public int depthFunc() {
        return functionFromSlot(functionSlotValue(5));
    }

    /** {@return whether the alpha test is on}. */
    public boolean alphaTestEnabled() {
        return functionSlotValue(9) != 0;
    }

    /**
     * {@return the alpha function as a GL enum (512-519)}, or 0 when the test is off and
     * -1 when the packed value was the out-of-domain escape.
     */
    public int alphaFunc() {
        return functionFromSlot(functionSlotValue(9));
    }

    private int functionSlotValue(int shift) {
        return (int) ((this.value >>> shift) & 0xF);
    }

    private static int functionFromSlot(int slot) {
        if (slot == 0) {
            return 0;
        }
        return slot == FUNCTION_OTHER ? -1 : slot + 511;
    }

    /** {@return whether lighting is enabled}. */
    public boolean lightingEnabled() {
        return (this.value & (1L << 13)) != 0L;
    }

    /**
     * {@return the shade model}: 0 flat, 1 smooth, or -1 when the packed value was the
     * out-of-domain escape.
     */
    public int shadeModel() {
        int slot = (int) ((this.value >>> 14) & 0x3);
        return slot == SHADE_OTHER ? -1 : slot;
    }

    /** {@return the cull state in {@link VulkanCullState}'s encoding}. */
    public int cullState() {
        return (int) ((this.value >>> 16) & 0x7);
    }

    /** {@return whether blending is enabled}. */
    public boolean blendEnabled() {
        return (this.value & (1L << 19)) != 0L;
    }

    /** {@return the source RGB blend factor (a GL enum or a legacy ordinal)}, or -1 for the escape. */
    public int blendSrcRgb() {
        return factorFromCode((int) ((this.value >>> 20) & 0x1F));
    }

    /** {@return the destination RGB blend factor}, or -1 for the escape. */
    public int blendDstRgb() {
        return factorFromCode((int) ((this.value >>> 25) & 0x1F));
    }

    /** {@return the source alpha blend factor}, or -1 for the escape. */
    public int blendSrcAlpha() {
        return factorFromCode((int) ((this.value >>> 30) & 0x1F));
    }

    /** {@return the destination alpha blend factor}, or -1 for the escape. */
    public int blendDstAlpha() {
        return factorFromCode((int) ((this.value >>> 35) & 0x1F));
    }

    /**
     * {@return the RGB blend equation}, reconstructed as {@code 0x8000 | nibble}: exact for
     * every equation in GL's 0x8xxx family, which is the whole defined domain.
     */
    public int blendEquationRgb() {
        return 0x8000 | (int) ((this.value >>> 40) & 0xF);
    }

    /** {@return the alpha blend equation}, under the same reconstruction as the RGB one. */
    public int blendEquationAlpha() {
        return 0x8000 | (int) ((this.value >>> 44) & 0xF);
    }

    /** {@return the folded vertex format hash (the raw hash is not recoverable, by design)}. */
    public int formatFold() {
        return (int) (this.value >>> 48);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof PipelineStateKey that && this.value == that.value;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(this.value);
    }

    @Override
    public String toString() {
        return "PipelineStateKey[0x" + Long.toHexString(this.value) + "]";
    }
}
