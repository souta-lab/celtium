package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.gl.GlsmFrontend;

/**
 * Snapshot/restore bracket for one pack terrain execution
 * ({@link VulkanTerrainGpuBackend#celtium$tryRenderPackTerrainPass}).
 * <p>
 * The guest shader interface's {@code setupState} writes execution state into the shared mirror (cull, the depth
 * trio, blend, the alpha test, the bound framebuffer, the viewport) and its {@code restoreState} is under no
 * obligation to put all of it back: Demonica's implementation restores only the depth trio (to the values its
 * FIRST setup saved, not this call's), the alpha test and blend only when the executing program itself carried
 * an override, and cull - which its shadow-stage setup disables - never. The pipeline one level up happens to
 * re-enable cull after the shadow stage today, but that is the guest's pipeline behaviour, not the contract at
 * the surface celtium invokes; a guest whose setup writes more than its restore returns leaks into every later
 * execution that reads the mirror, the pack path's own pipeline cache key first of all ({@code cullState()} is
 * part of it). This bracket makes the host indifferent to the guest's restore coverage: capture the mirror
 * fields a later celtium execution can read, restore them after the guest's own {@code restoreState} has run.
 * <p>
 * Texture bindings and the active texture unit are deliberately NOT part of the bracket. Every pack execution's
 * own setup re-binds the atlas, the lightmap and all sampler textures before the backend reads them, so there
 * is no stale read to close - and restoring them behind the guest state manager's back would desync its bind
 * cache from the mirror: it skips a bind whose cached value already equals the request, so a mirror-side
 * restore could make the NEXT execution's sampler binds skip while the mirror holds the older texture,
 * manufacturing the wrong read this bracket exists to prevent. Leaving the guest's binds in place keeps the
 * guest cache and the mirror in agreement on those units.
 * <p>
 * Cost: capture reads about twenty scalars; restore is the same number of change-guarded setter calls, each a
 * comparison when the guest already put the value back (the common case). One reusable instance per backend -
 * pack executions run on the render thread and never nest - so the bracket allocates nothing and does no work
 * per draw or per batch, only per stage execution.
 */
final class PackStageStateGuard {
    private static final int GL_FRAMEBUFFER = 0x8D40;
    private static final int GL_DRAW_FRAMEBUFFER = 0x8CA9;
    private static final int GL_READ_FRAMEBUFFER = 0x8CA8;

    private boolean cullEnabled;
    private int cullFace;
    private int frontFace;
    private boolean blendEnabled;
    private int blendSrcRgb;
    private int blendDstRgb;
    private int blendSrcAlpha;
    private int blendDstAlpha;
    private int blendEquationRgb;
    private int blendEquationAlpha;
    private int colorMask;
    private boolean depthTestEnabled;
    private int depthFunc;
    private boolean depthMask;
    private boolean alphaTestEnabled;
    private int alphaFunc;
    private float alphaRef;
    /** The stub's bound framebuffer names at capture; negative when no stub is installed (nothing to restore). */
    private int drawFramebuffer = -1;
    private int readFramebuffer = -1;
    /** The stub's packed viewport at capture; negative when no viewport was ever recorded. */
    private long viewportPacked = -1L;

    /** Records the mirror state a pack execution is about to be allowed to disturb. */
    void capture(VulkanGlState state) {
        this.cullEnabled = state.cullEnabled();
        this.cullFace = state.cullFace();
        this.frontFace = state.frontFace();
        this.blendEnabled = state.blendEnabled();
        this.blendSrcRgb = state.blendSrcRgb();
        this.blendDstRgb = state.blendDstRgb();
        this.blendSrcAlpha = state.blendSrcAlpha();
        this.blendDstAlpha = state.blendDstAlpha();
        this.blendEquationRgb = state.blendEquationRgb();
        this.blendEquationAlpha = state.blendEquationAlpha();
        this.colorMask = state.colorMask();
        this.depthTestEnabled = state.depthTestEnabled();
        this.depthFunc = state.depthFunc();
        this.depthMask = state.depthMask();
        this.alphaTestEnabled = state.alphaTestEnabled();
        this.alphaFunc = state.alphaFunc();
        this.alphaRef = state.alphaFuncReference();
        this.drawFramebuffer = GlsmFrontend.celtium$drawFramebuffer();
        this.readFramebuffer = GlsmFrontend.celtium$readFramebuffer();
        this.viewportPacked = GlsmFrontend.celtium$viewportPacked();
    }

    /**
     * The backend's depth/alpha setters, abstracted so the bracket can be exercised without a device: a real
     * change through one of these fires the same font-draw state boundary the guest's own restores fire when
     * they arrive through the stub. {@link VulkanTerrainGpuBackend} implements this with its existing setters.
     */
    interface DepthAlphaSink {
        void setDepthTest(boolean enabled);

        void depthFunc(int func);

        void setDepthMask(boolean mask);

        void setAlphaTestEnabled(boolean enabled);

        void setAlphaFunc(int glFunc, float reference);
    }

    /**
     * Puts the captured state back, after the guest's own {@code restoreState}. Depth and the alpha test go
     * through the sink; the raster fields go through the state model's setters, whose {@code changing()} flush
     * submits pending glyph geometry under the old state before the value moves. The framebuffer and viewport
     * are re-issued through the stub's own entries, the routes any bind takes.
     */
    void restore(VulkanGlState state, DepthAlphaSink sink) {
        if (state.cullEnabled() != this.cullEnabled) {
            state.cullEnabled(this.cullEnabled);
        }
        if (state.cullFace() != this.cullFace) {
            state.cullFace(this.cullFace);
        }
        if (state.frontFace() != this.frontFace) {
            state.frontFace(this.frontFace);
        }
        if (state.blendEnabled() != this.blendEnabled) {
            state.blendEnabled(this.blendEnabled);
        }
        if (state.blendSrcRgb() != this.blendSrcRgb || state.blendDstRgb() != this.blendDstRgb
                || state.blendSrcAlpha() != this.blendSrcAlpha || state.blendDstAlpha() != this.blendDstAlpha) {
            state.blendFunc(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
        }
        if (state.blendEquationRgb() != this.blendEquationRgb
                || state.blendEquationAlpha() != this.blendEquationAlpha) {
            state.blendEquation(this.blendEquationRgb, this.blendEquationAlpha);
        }
        if (state.colorMask() != this.colorMask) {
            state.colorMask(this.colorMask);
        }
        if (state.depthTestEnabled() != this.depthTestEnabled) {
            sink.setDepthTest(this.depthTestEnabled);
        }
        if (state.depthFunc() != this.depthFunc) {
            sink.depthFunc(this.depthFunc);
        }
        if (state.depthMask() != this.depthMask) {
            sink.setDepthMask(this.depthMask);
        }
        if (state.alphaTestEnabled() != this.alphaTestEnabled) {
            sink.setAlphaTestEnabled(this.alphaTestEnabled);
        }
        if (state.alphaFunc() != this.alphaFunc || state.alphaFuncReference() != this.alphaRef) {
            sink.setAlphaFunc(this.alphaFunc, this.alphaRef);
        }
        if (this.drawFramebuffer >= 0
                && (GlsmFrontend.celtium$drawFramebuffer() != this.drawFramebuffer
                        || GlsmFrontend.celtium$readFramebuffer() != this.readFramebuffer)) {
            if (this.drawFramebuffer == this.readFramebuffer) {
                GlsmFrontend.celtium$bindFramebuffer(GL_FRAMEBUFFER, this.drawFramebuffer);
            } else {
                GlsmFrontend.celtium$bindFramebuffer(GL_DRAW_FRAMEBUFFER, this.drawFramebuffer);
                GlsmFrontend.celtium$bindFramebuffer(GL_READ_FRAMEBUFFER, this.readFramebuffer);
            }
        }
        if (this.viewportPacked >= 0 && GlsmFrontend.celtium$viewportPacked() != this.viewportPacked) {
            long packed = this.viewportPacked;
            GlsmFrontend.celtium$viewport((int) ((packed >>> 48) & 0xFFFF), (int) ((packed >>> 32) & 0xFFFF),
                    (int) ((packed >>> 16) & 0xFFFF), (int) (packed & 0xFFFF));
        }
    }
}
