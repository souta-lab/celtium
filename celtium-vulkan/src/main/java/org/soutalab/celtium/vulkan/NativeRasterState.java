package org.soutalab.celtium.vulkan;

/**
 * Render-thread raster state owned by the Vulkan frontend. Minecraft's API and the raw-GL adapter both mutate this
 * owner; drawing never imports a second set of GL records. It exists before device creation and survives swapchain
 * recreation. A queued draw still copies its values at acceptance, so later mutations cannot change earlier draws.
 */
public final class NativeRasterState {
    private static final NativeRasterState CURRENT = new NativeRasterState();

    public static NativeRasterState current() { return CURRENT; }

    /** {@return the version of the values a queued draw copies}; see {@link #version}. */
    public long version() {
        return this.version;
    }

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
    // GLSM M-4 (INT-2 remainder): the stencil family, polygon mode, sample coverage and the constant blend
    // colour. Front and back stencil state is kept separately; the non-Separate setters write both faces.
    // Defaults are GL's initial values: func GL_ALWAYS (0x0207), ref 0, read/write masks all bits, ops
    // GL_KEEP (0x1E00), polygon mode GL_FILL (0x1B02), coverage 1.0 not inverted, blend colour (0,0,0,0).
    // This renderer has no stencil attachment (GL_STENCIL_BITS answers 0), so these fields are the state
    // record only: nothing applies them to a pipeline yet, but the record is what the GL_STENCIL_* queries
    // and a future stencil-capable pipeline read.
    private int stencilFuncFront = 0x0207, stencilFuncBack = 0x0207;
    private int stencilRefFront, stencilRefBack;
    private int stencilValueMaskFront = ~0, stencilValueMaskBack = ~0;
    private int stencilFailFront = 0x1E00, stencilFailBack = 0x1E00;
    private int stencilDepthFailFront = 0x1E00, stencilDepthFailBack = 0x1E00;
    private int stencilDepthPassFront = 0x1E00, stencilDepthPassBack = 0x1E00;
    private int stencilWriteMaskFront = ~0, stencilWriteMaskBack = ~0;
    private int polygonModeFront = 0x1B02, polygonModeBack = 0x1B02;
    private float sampleCoverageValue = 1.0f;
    private boolean sampleCoverageInvert;
    private float blendColorRed, blendColorGreen, blendColorBlue, blendColorAlpha;
    private Runnable beforeChange;
    private boolean notifying;
    /**
     * Bumped by each setter whose value a model replay's tuple carries, and only when the value actually changes.
     * <p>
     * The state model wraps this owner but is not its only writer: the raw-GL adapter writes blend factors, equations,
     * cull and the colour mask here directly, so a version kept beside the wrapper's fields would miss exactly the
     * mutations a direct-GL mod makes between two model replays. It lives here, next to the fields, for that reason -
     * and {@link VulkanGlState#stateVersion()} folds it into the number the replay path compares.
     */
    private long version;

    /** The callback submits deferred glyph geometry under the OLD state. It must run before changing any value. */
    public void beforeChange(Runnable callback) { this.beforeChange = callback; }

    /** Also used by adapter state that has not yet moved into this owner (for example depth-test capability). */
    public void flushBeforeChange() { changing(); }

    private void changing() {
        // GAP-FL-1 (F-7): every actual raster change records into the one flush census.
        org.soutalab.celtium.vulkan.gl.FlushBoundaryCensus.current()
                .note(org.soutalab.celtium.vulkan.gl.FlushBoundaryCensus.Reason.RASTER_CHANGE);
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
        this.version++;
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
        this.version++;
    }
    public int blendEquationRgb() { return this.blendEquationRgb; }
    public int blendEquationAlpha() { return this.blendEquationAlpha; }
    public void blendEquation(int rgb, int alpha) {
        if (this.blendEquationRgb == rgb && this.blendEquationAlpha == alpha) return;
        changing();
        this.blendEquationRgb = rgb; this.blendEquationAlpha = alpha;
        this.version++;
    }
    public int colorMask() { return this.colorMask; }
    public void colorMask(int mask) {
        if (this.colorMask == mask) return;
        changing();
        this.colorMask = mask;
        this.version++;
    }
    public boolean cullEnabled() { return this.cullEnabled; }
    public void cullEnabled(boolean enabled) {
        if (this.cullEnabled == enabled) return;
        changing();
        this.cullEnabled = enabled;
        this.version++;
    }
    public int cullFace() { return this.cullFace; }
    public void cullFace(int face) {
        if (this.cullFace == face) return;
        changing();
        this.cullFace = face;
        // The face decides a pipeline now (see VulkanCullState), so a model replay's tuple has to see it move. These
        // two setters were the only raster writers that did not bump the version: a replay that changed only the culled
        // face between two frames kept serving the previous tuple - and with it the previous cull mode - while the
        // mirror and every glGet answered the new one.
        this.version++;
    }
    public int frontFace() { return this.frontFace; }
    public void frontFace(int face) {
        if (this.frontFace == face) return;
        changing();
        this.frontFace = face;
        this.version++;
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

    /** Scratch the fill-in variant returns; callers that keep the values must copy it before the next ask. */
    private final int[] scissorBoxScratch = new int[4];

    /**
     * {@return the live scissor box in a shared array}, or null when none was set. The group capture reads it and
     * copies it immediately; going through {@link #scissorBox()} there allocated the box once for the getter and again
     * for the defensive copy, on every accepted group.
     */
    public int[] scissorBoxScratch() {
        if (!this.scissorCalled) {
            return null;
        }
        this.scissorBoxScratch[0] = this.scissorX;
        this.scissorBoxScratch[1] = this.scissorY;
        this.scissorBoxScratch[2] = this.scissorWidth;
        this.scissorBoxScratch[3] = this.scissorHeight;
        return this.scissorBoxScratch;
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

    /** GL_FRONT (0x0404) and GL_FRONT_AND_BACK (0x0408) both name the front face here; anything else is back. */
    private static boolean isFrontFace(int face) { return face != 0x0405; }

    public int stencilFunc(boolean front) { return front ? this.stencilFuncFront : this.stencilFuncBack; }
    public int stencilRef(boolean front) { return front ? this.stencilRefFront : this.stencilRefBack; }
    public int stencilValueMask(boolean front) { return front ? this.stencilValueMaskFront : this.stencilValueMaskBack; }
    /** {@code glStencilFunc}: the non-Separate form sets both faces. */
    public void stencilFunc(int func, int ref, int mask) {
        stencilFuncSeparate(0x0408, func, ref, mask);
    }
    public void stencilFuncSeparate(int face, int func, int ref, int mask) {
        boolean front = isFrontFace(face), back = face != 0x0404;
        boolean changed = (front && (this.stencilFuncFront != func || this.stencilRefFront != ref
                || this.stencilValueMaskFront != mask))
                || (back && (this.stencilFuncBack != func || this.stencilRefBack != ref
                || this.stencilValueMaskBack != mask));
        if (!changed) return;
        changing();
        if (front) { this.stencilFuncFront = func; this.stencilRefFront = ref; this.stencilValueMaskFront = mask; }
        if (back) { this.stencilFuncBack = func; this.stencilRefBack = ref; this.stencilValueMaskBack = mask; }
        this.version++;
    }
    public int stencilFail(boolean front) { return front ? this.stencilFailFront : this.stencilFailBack; }
    public int stencilDepthFail(boolean front) { return front ? this.stencilDepthFailFront : this.stencilDepthFailBack; }
    public int stencilDepthPass(boolean front) { return front ? this.stencilDepthPassFront : this.stencilDepthPassBack; }
    /** {@code glStencilOp}: the non-Separate form sets both faces. */
    public void stencilOp(int fail, int depthFail, int depthPass) {
        stencilOpSeparate(0x0408, fail, depthFail, depthPass);
    }
    public void stencilOpSeparate(int face, int fail, int depthFail, int depthPass) {
        boolean front = isFrontFace(face), back = face != 0x0404;
        boolean changed = (front && (this.stencilFailFront != fail || this.stencilDepthFailFront != depthFail
                || this.stencilDepthPassFront != depthPass))
                || (back && (this.stencilFailBack != fail || this.stencilDepthFailBack != depthFail
                || this.stencilDepthPassBack != depthPass));
        if (!changed) return;
        changing();
        if (front) { this.stencilFailFront = fail; this.stencilDepthFailFront = depthFail; this.stencilDepthPassFront = depthPass; }
        if (back) { this.stencilFailBack = fail; this.stencilDepthFailBack = depthFail; this.stencilDepthPassBack = depthPass; }
        this.version++;
    }
    public int stencilWriteMask(boolean front) { return front ? this.stencilWriteMaskFront : this.stencilWriteMaskBack; }
    /** {@code glStencilMask}: the non-Separate form sets both faces' write masks. */
    public void stencilMask(int mask) { stencilMaskSeparate(0x0408, mask); }
    public void stencilMaskSeparate(int face, int mask) {
        boolean front = isFrontFace(face), back = face != 0x0404;
        boolean changed = (front && this.stencilWriteMaskFront != mask) || (back && this.stencilWriteMaskBack != mask);
        if (!changed) return;
        changing();
        if (front) this.stencilWriteMaskFront = mask;
        if (back) this.stencilWriteMaskBack = mask;
        this.version++;
    }
    public int polygonMode(boolean front) { return front ? this.polygonModeFront : this.polygonModeBack; }
    /** {@code glPolygonMode}: GL_FRONT / GL_BACK / GL_FRONT_AND_BACK select the recorded face(s). */
    public void polygonMode(int face, int mode) {
        boolean front = isFrontFace(face), back = face != 0x0404;
        boolean changed = (front && this.polygonModeFront != mode) || (back && this.polygonModeBack != mode);
        if (!changed) return;
        changing();
        if (front) this.polygonModeFront = mode;
        if (back) this.polygonModeBack = mode;
        this.version++;
    }
    public float sampleCoverageValue() { return this.sampleCoverageValue; }
    public boolean sampleCoverageInvert() { return this.sampleCoverageInvert; }
    public void sampleCoverage(float value, boolean invert) {
        if (this.sampleCoverageValue == value && this.sampleCoverageInvert == invert) return;
        changing();
        this.sampleCoverageValue = value; this.sampleCoverageInvert = invert;
        this.version++;
    }
    public float blendColorRed() { return this.blendColorRed; }
    public float blendColorGreen() { return this.blendColorGreen; }
    public float blendColorBlue() { return this.blendColorBlue; }
    public float blendColorAlpha() { return this.blendColorAlpha; }
    /** {@code glBlendColor}: the constant colour the GL_CONSTANT_* blend factors read. */
    public void blendColor(float red, float green, float blue, float alpha) {
        if (this.blendColorRed == red && this.blendColorGreen == green
                && this.blendColorBlue == blue && this.blendColorAlpha == alpha) return;
        changing();
        this.blendColorRed = red; this.blendColorGreen = green;
        this.blendColorBlue = blue; this.blendColorAlpha = alpha;
        this.version++;
    }
}
