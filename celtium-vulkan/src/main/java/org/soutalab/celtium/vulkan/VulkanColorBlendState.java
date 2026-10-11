package org.soutalab.celtium.vulkan;

import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_A_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_B_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_G_BIT;
import static org.lwjgl.vulkan.VK10.VK_COLOR_COMPONENT_R_BIT;

/**
 * The colour-blend and colour-write state that bakes into an immediate pipeline.
 * <p>
 * This exists because two audit findings are the same defect seen twice: state that a pipeline bakes was not carried
 * from the GL call that set it to the pipeline that must honour it.
 * <ul>
 *   <li><b>BH-07</b>: the four blend factors (RGB <em>and</em> alpha) and the two equations. The pipelines fixed the
 *       alpha pair to {@code ONE / ONE_MINUS_SRC_ALPHA} and the RGB pair came from the mirror, so a batch drawn under
 *       a different alpha pair got a pipeline that could not express it - and the state key could not tell the two
 *       apart, so they could even share one. Vanilla's own callers ask for {@code ONE / ZERO} almost everywhere
 *       ({@code GuiIngame}, {@code Gui}, {@code GuiButton}, the HUD item path), so this was not a corner case.</li>
 *   <li><b>BH-10</b>: the per-channel colour write mask. The Fancy cloud is drawn in two passes and the first one
 *       clears every channel ({@code GlStateManager.colorMask(false,false,false,false)} at {@code RenderGlobal:1655})
 *       to build depth; with the mask fixed at "write everything" that pass painted cloud colour too.</li>
 * </ul>
 * The values are Vulkan enums, converted by the caller: the pipeline classes should never have to know a GL constant,
 * and the one place that owns the GL-to-Vulkan conversion is the backend that reads the mirror.
 *
 * <p>Threading: immutable, read on the render thread only.
 */
public final class VulkanColorBlendState {

    /** Straight-alpha blending with every channel written: the shape every immediate pipeline baked before BH-07/BH-10. */
    private static final VulkanColorBlendState LEGACY = new VulkanColorBlendState(true, VK_BLEND_FACTOR_SRC_ALPHA,
            VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
            VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, allChannels());

    /** Every channel writable, as Vulkan's own component bits. */
    public static int allChannels() {
        return VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT
                | VK_COLOR_COMPONENT_A_BIT;
    }

    private final boolean blend;
    private final int srcColor;
    private final int dstColor;
    private final int srcAlpha;
    private final int dstAlpha;
    private final int colorOp;
    private final int alphaOp;
    private final int writeMask;

    private VulkanColorBlendState(boolean blend, int srcColor, int dstColor, int srcAlpha, int dstAlpha, int colorOp,
            int alphaOp, int writeMask) {
        this.blend = blend;
        this.srcColor = srcColor;
        this.dstColor = dstColor;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
        this.colorOp = colorOp;
        this.alphaOp = alphaOp;
        this.writeMask = writeMask;
    }

    /**
     * Builds one state. The factors are the Vulkan enums the mirror already carries (so an unknown GL factor has
     * been substituted by the caller and cannot reach Vulkan as an invalid enum), the equations likewise, and the
     * mask is the {@code VK_COLOR_COMPONENT_*} nibble.
     */
    public static VulkanColorBlendState of(boolean blend, int srcColor, int dstColor, int srcAlpha, int dstAlpha,
            int colorOp, int alphaOp, int writeMask) {
        return new VulkanColorBlendState(blend, srcColor, dstColor, srcAlpha, dstAlpha, colorOp, alphaOp, writeMask);
    }

    /** The pre-BH-07/BH-10 shape, used by the no-argument factories and by the one-flag controls. */
    public static VulkanColorBlendState legacy() {
        return LEGACY;
    }

    public boolean blend() {
        return this.blend;
    }

    public int srcColor() {
        return this.srcColor;
    }

    public int dstColor() {
        return this.dstColor;
    }

    public int srcAlpha() {
        return this.srcAlpha;
    }

    public int dstAlpha() {
        return this.dstAlpha;
    }

    public int colorOp() {
        return this.colorOp;
    }

    public int alphaOp() {
        return this.alphaOp;
    }

    public int writeMask() {
        return this.writeMask;
    }

    /** {@return the four alpha factors as one comparable number}, so a census can group batches by them. */
    public long alphaPairKey() {
        return (((long) this.srcAlpha) << 32) | (this.dstAlpha & 0xFFFFFFFFL);
    }

    /** {@return a one-line description, for the diagnostics that have to name the state a pipeline was built with}. */
    public String describe() {
        return (this.blend ? "blend" : "noBlend") + "[rgb=" + this.srcColor + "," + this.dstColor
                + " a=" + this.srcAlpha + "," + this.dstAlpha + " op=" + this.colorOp + "/" + this.alphaOp
                + " mask=0x" + Integer.toHexString(this.writeMask) + "]";
    }
}
