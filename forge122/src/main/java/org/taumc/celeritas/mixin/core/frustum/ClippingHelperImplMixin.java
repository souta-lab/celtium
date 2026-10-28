package org.taumc.celeritas.mixin.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.frustum.IClippingHelper;

/** Uses the same camera matrices as vanilla and the terrain, including Forge camera transforms. */
@Mixin(ClippingHelperImpl.class)
public abstract class ClippingHelperImplMixin extends ClippingHelper implements IClippingHelper {
    /**
     * Replaced, never rewritten: a viewport captures this object and the terrain search tests boxes against it on its
     * own thread, while the next ClippingHelperImpl.init (the next frame, or another pass's camera) runs on the render
     * thread. Rewriting the planes in place let a search see them change under it - surface sections were culled for
     * a frame and the caves and sea floor behind them showed as the view moved.
     */
    @Unique
    private FrustumIntersection celeritas$frustum = new FrustumIntersection();
    @Unique
    private Matrix4f celeritas$viewProjection;

    @Inject(method = "init", at = @At("RETURN"))
    private void updateJoml(CallbackInfo ci) {
        // ClippingHelperImpl reads both matrices through GlStateManager.getFloat. The GL-less native query
        // now returns the real mirrored state, rather than clearing only the first word. Do not reconstruct
        // another camera from billboard angles or hard-coded FOV: that loses eye height, pitch and mod events.
        Matrix4f projection = new Matrix4f().set(projectionMatrix);
        Matrix4f modelView = new Matrix4f().set(modelviewMatrix);
        Matrix4f vp = projection.mul(modelView);
        this.celeritas$viewProjection = vp.isFinite() ? new Matrix4f(vp) : null;
        if (!vp.isFinite() || vp.determinant() == 0.0f) {
            // A zero, non-normalized plane accepts every box. Identity would instead cull outside [-1,1]^3.
            this.celeritas$frustum = new FrustumIntersection(new Matrix4f().zero(), false);
        } else {
            // These are OpenGL clip coordinates; only the draw backend remaps depth to Vulkan [0,1].
            this.celeritas$frustum = new FrustumIntersection(vp, true);
        }
    }

    @Override
    public FrustumIntersection celeritas$getJomlFrustum() {
        return this.celeritas$frustum;
    }

    @Override
    public org.joml.Matrix4fc celeritas$getViewProjection() {
        return this.celeritas$viewProjection;
    }
}
