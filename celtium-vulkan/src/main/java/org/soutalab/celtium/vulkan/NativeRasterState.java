package org.soutalab.celtium.vulkan;

/**
 * Render-thread raster state owned by the Vulkan frontend. Minecraft's API and the raw-GL adapter both mutate this
 * owner; drawing never imports a second set of GL records. It exists before device creation and survives swapchain
 * recreation. A queued draw still copies its values at acceptance, so later mutations cannot change earlier draws.
 */
public final class NativeRasterState {
    private static final NativeRasterState CURRENT = new NativeRasterState();

    public static NativeRasterState current() { return CURRENT; }

    private boolean blendEnabled;
    private int blendSrcRgb = VulkanGlState.BLEND_SRC_ALPHA;
    private int blendDstRgb = VulkanGlState.BLEND_ONE_MINUS_SRC_ALPHA;
    private int blendSrcAlpha = VulkanGlState.BLEND_ONE;
    private int blendDstAlpha = VulkanGlState.BLEND_ONE_MINUS_SRC_ALPHA;
    private int blendEquationRgb = VulkanGlState.BLEND_FUNC_ADD;
    private int blendEquationAlpha = VulkanGlState.BLEND_FUNC_ADD;
    private int colorMask = 0xF;
    private boolean cullEnabled;
    private int cullFace = VulkanGlState.CULL_BACK;
    private int frontFace = 0x0901;
    private boolean scissorEnabled;
    private boolean scissorCalled;
    private int scissorX, scissorY, scissorWidth = 1280, scissorHeight = 720;
    private boolean polygonOffsetEnabled;
    private boolean polygonOffsetCalled;
    private float polygonOffsetFactor, polygonOffsetUnits;
    private Runnable beforeChange;
    private boolean notifying;

    /** The callback submits deferred glyph geometry under the OLD state. It must run before changing any value. */
    public void beforeChange(Runnable callback) { this.beforeChange = callback; }

    /** Also used by adapter state that has not yet moved into this owner (for example depth-test capability). */
    public void flushBeforeChange() { changing(); }

    private void changing() {
        if (this.beforeChange != null && !this.notifying) {
            this.notifying = true;
            try { this.beforeChange.run(); }
            finally { this.notifying = false; }
        }
    }

    public boolean blendEnabled() { return this.blendEnabled; }
    public void blendEnabled(boolean enabled) {
        if (this.blendEnabled == enabled) return;
        changing();
        this.blendEnabled = enabled;
    }
    public int blendSrcRgb() { return this.blendSrcRgb; }
    public int blendDstRgb() { return this.blendDstRgb; }
    public int blendSrcAlpha() { return this.blendSrcAlpha; }
    public int blendDstAlpha() { return this.blendDstAlpha; }
    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        if (this.blendSrcRgb == srcRgb && this.blendDstRgb == dstRgb
                && this.blendSrcAlpha == srcAlpha && this.blendDstAlpha == dstAlpha) return;
        changing();
        this.blendSrcRgb = srcRgb; this.blendDstRgb = dstRgb;
        this.blendSrcAlpha = srcAlpha; this.blendDstAlpha = dstAlpha;
    }
    public int blendEquationRgb() { return this.blendEquationRgb; }
    public int blendEquationAlpha() { return this.blendEquationAlpha; }
    public void blendEquation(int rgb, int alpha) {
        if (this.blendEquationRgb == rgb && this.blendEquationAlpha == alpha) return;
        changing();
        this.blendEquationRgb = rgb; this.blendEquationAlpha = alpha;
    }
    public int colorMask() { return this.colorMask; }
    public void colorMask(int mask) {
        if (this.colorMask == mask) return;
        changing();
        this.colorMask = mask;
    }
    public boolean cullEnabled() { return this.cullEnabled; }
    public void cullEnabled(boolean enabled) {
        if (this.cullEnabled == enabled) return;
        changing();
        this.cullEnabled = enabled;
    }
    public int cullFace() { return this.cullFace; }
    public void cullFace(int face) {
        if (this.cullFace == face) return;
        changing();
        this.cullFace = face;
    }
    public int frontFace() { return this.frontFace; }
    public void frontFace(int face) {
        if (this.frontFace == face) return;
        changing();
        this.frontFace = face;
    }
    public boolean scissorTest() { return this.scissorEnabled; }
    public void scissorTest(boolean enabled) {
        if (this.scissorEnabled == enabled) return;
        changing();
        this.scissorEnabled = enabled;
    }
    public boolean scissorCalled() { return this.scissorCalled; }
    public int scissorX() { return this.scissorX; }
    public int scissorY() { return this.scissorY; }
    public int scissorWidth() { return this.scissorWidth; }
    public int scissorHeight() { return this.scissorHeight; }
    public int[] scissorBox() {
        return this.scissorCalled ? new int[] {this.scissorX, this.scissorY, this.scissorWidth, this.scissorHeight} : null;
    }
    public void initializeScissor(int width, int height) {
        if (this.scissorCalled) return;
        this.scissorWidth = width; this.scissorHeight = height;
    }
    public void scissor(int x, int y, int width, int height) {
        if (!this.scissorCalled || this.scissorX != x || this.scissorY != y
                || this.scissorWidth != width || this.scissorHeight != height) changing();
        this.scissorX = x; this.scissorY = y; this.scissorWidth = width; this.scissorHeight = height;
        this.scissorCalled = true;
    }
    public boolean polygonOffsetFillEnabled() { return this.polygonOffsetEnabled; }
    public void polygonOffsetFillEnabled(boolean enabled) {
        if (this.polygonOffsetEnabled == enabled) return;
        changing();
        this.polygonOffsetEnabled = enabled;
    }
    public boolean polygonOffsetCalled() { return this.polygonOffsetCalled; }
    public float polygonOffsetFactor() { return this.polygonOffsetFactor; }
    public float polygonOffsetUnits() { return this.polygonOffsetUnits; }
    public void polygonOffset(float factor, float units) {
        if (!this.polygonOffsetCalled || this.polygonOffsetFactor != factor || this.polygonOffsetUnits != units) changing();
        this.polygonOffsetFactor = factor; this.polygonOffsetUnits = units; this.polygonOffsetCalled = true;
    }
}
