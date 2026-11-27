package org.soutalab.celtium.vulkan.lane2;

import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.vulkan.VulkanImmediateFormats;

/**
 * Lane 2's fixed pipeline catalog (design V040-FINAL-PLAN section 4.2, stage S3-a item 2): the
 * non-terrain lane's pipelines are a closed, precompiled set - one entry per
 * (immediate vertex format, state variant) pair - instead of the old records pipelines'
 * open-ended per-state creation. Resolving a draw's pipeline is a table lookup into this
 * catalog; a (format, variant) pair outside the catalog has no pipeline and is refused, never
 * created on the spot. Dynamic generation is zero by construction: the catalog is a pure
 * function of the format table in {@link VulkanImmediateFormats}.
 *
 * <p>The state variants are the three blend/depth classes the lane's draws actually fall
 * into, derived from the register's {@link RenderStateKey} so the state mirror and the
 * catalog can never disagree about which class a draw belongs to:
 * <ul>
 *   <li>{@link #VARIANT_OPAQUE} - blending off (depth write follows the key);</li>
 *   <li>{@link #VARIANT_BLEND_DEPTH_WRITE} - blending on, depth write on;</li>
 *   <li>{@link #VARIANT_BLEND_NO_DEPTH_WRITE} - blending on, depth write off (translucent).</li>
 * </ul>
 *
 * <p>K5 (plan section 5.4): the fixed pipeline total must stay at or below 64. The catalog is
 * 13 formats x 3 variants = {@value #COUNT} entries, inside the plan's 32..48 band; the
 * contract test asserts both the count and the cap so a future format or variant addition
 * that would break K5 fails in the test suite, not in a frame.
 */
public final class Lane2FixedPipelines {

    /** Blending off. */
    public static final int VARIANT_OPAQUE = 0;
    /** Blending on, depth write on. */
    public static final int VARIANT_BLEND_DEPTH_WRITE = 1;
    /** Blending on, depth write off. */
    public static final int VARIANT_BLEND_NO_DEPTH_WRITE = 2;
    /** State variants per format. */
    public static final int VARIANTS = 3;

    /** K5 cap from the plan: the fixed pipeline total must not exceed this. */
    public static final int K5_MAX_PIPELINES = 64;

    /** Total fixed pipelines: every accepted immediate format in every state variant. */
    public static final int COUNT = (VulkanImmediateFormats.COUNT - 1) * VARIANTS;

    private Lane2FixedPipelines() {
    }

    /** Whether the format id names an accepted immediate layout (1..13 today). */
    public static boolean isFormat(int formatId) {
        return formatId > 0 && formatId < VulkanImmediateFormats.COUNT;
    }

    /**
     * The catalog index of a (format, variant) pair, or -1 when either half is outside the
     * catalog. This is the only resolution the lane performs: a -1 is a refusal, and no code
     * path turns it into a creation.
     */
    public static int indexOf(int formatId, int variant) {
        if (!isFormat(formatId) || variant < 0 || variant >= VARIANTS) {
            return -1;
        }
        return (formatId - 1) * VARIANTS + variant;
    }

    /** The format id a catalog index belongs to. */
    public static int formatOf(int index) {
        checkIndex(index);
        return index / VARIANTS + 1;
    }

    /** The state variant a catalog index belongs to. */
    public static int variantOf(int index) {
        checkIndex(index);
        return index % VARIANTS;
    }

    /**
     * The state variant a register key falls into. Depth test is not part of the class: a
     * depth-test-off draw (GUI, overlays) still resolves to the variant its blend and depth
     * write bits name, and the executor applies the key's depth-test bit as dynamic state -
     * the variant is about the baked blend/depth-write shape only.
     */
    public static int variantFor(RenderStateKey key) {
        if (!key.blendEnabled()) {
            return VARIANT_OPAQUE;
        }
        return key.depthWriteEnabled()
                ? VARIANT_BLEND_DEPTH_WRITE
                : VARIANT_BLEND_NO_DEPTH_WRITE;
    }

    /** The catalog index for a draw in the given format under the given register key, or -1. */
    public static int indexFor(int formatId, RenderStateKey key) {
        return indexOf(formatId, variantFor(key));
    }

    /**
     * The stable program name of a catalog entry, the name the pack-target resolution and the
     * diagnostics use: {@code lane2/<format-name>/<variant-name>}. Names are a pure function
     * of the index, so a name read from a log line resolves back to exactly one entry.
     */
    public static String programName(int index) {
        int formatId = formatOf(index);
        return "lane2/" + VulkanImmediateFormats.nameOf(formatId) + "/" + variantName(variantOf(index));
    }

    public static String variantName(int variant) {
        switch (variant) {
            case VARIANT_OPAQUE:
                return "opaque";
            case VARIANT_BLEND_DEPTH_WRITE:
                return "blend-depth-write";
            case VARIANT_BLEND_NO_DEPTH_WRITE:
                return "blend-no-depth-write";
            default:
                throw new IllegalArgumentException("unknown variant " + variant);
        }
    }

    private static void checkIndex(int index) {
        if (index < 0 || index >= COUNT) {
            throw new IllegalArgumentException("catalog index out of range: " + index);
        }
    }
}
