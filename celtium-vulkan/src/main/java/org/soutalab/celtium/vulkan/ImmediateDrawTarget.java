package org.soutalab.celtium.vulkan;

/**
 * The Vulkan side of Minecraft's immediate-mode drawing, as seen by the Mixins that replace the GL implementation.
 * <p>
 * This is the narrow contract behind the Forge API swap: the state methods are what a {@code GlStateManager} Mixin
 * mirrors instead of issuing GL calls, and {@link #submitBatch} is what the
 * {@code WorldVertexBufferUploader.func_181679_a} Mixin calls for a finished {@code BufferBuilder}. Keeping it this
 * narrow means the Mixin layer has no opinion about pipeline selection, which stays a Vulkan-side decision made
 * lazily when a batch arrives.
 * <p>
 * Implementations must never throw: a failure here runs inside vanilla rendering, so the contract is to return
 * {@code false} (batch not taken) or to record a diagnostic, never to break the caller.
 */
public interface ImmediateDrawTarget {
    /** Mirrors {@code GlStateManager.func_179147_l()} / {@code func_179146_i()} (enable/disable blend). */
    void setBlendEnabled(boolean enabled);

    /** Mirrors {@code GlStateManager.func_187428_a(...)} / {@code func_179120_a(...)}. */
    void setBlendFunc(int sourceRgb, int destRgb, int sourceAlpha, int destAlpha);

    /** Mirrors {@code GlStateManager.func_179132_a(boolean)}. */
    void setDepthMask(boolean mask);

    /** Mirrors {@code GlStateManager.func_179097_i()} / {@code func_179126_j()} (depth test on/off). */
    void setDepthTest(boolean enabled);

    /** Mirrors {@code GlStateManager.func_179131_c(float, float, float, float)}. */
    void setColor(float red, float green, float blue, float alpha);

    /** Mirrors {@code GlStateManager.func_179094_E()} / {@code func_179121_F()}. */
    void pushMatrix();

    void popMatrix();

    /**
     * Offers a finished immediate-mode batch to Vulkan.
     *
     * @param vertexFormat  Minecraft's {@code VertexFormat} instance for the batch
     * @param vertexAddress native address of the first vertex
     * @param vertexCount   number of vertices
     * @param drawMode      Minecraft/OpenGL draw mode
     * @return {@code true} when Vulkan consumed the batch; {@code false} means the caller must draw it itself
     */
    boolean submitBatch(Object vertexFormat, long vertexAddress, int vertexCount, int drawMode);
}
