package org.taumc.celeritas.mixin.core.frustum;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ActiveRenderInfo;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.ClippingHelperImpl;
import org.joml.FrustumIntersection;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.render.frustum.IClippingHelper;

/**
 * Builds the JOML frustum the terrain culler uses, from matrices that exist without a GL context.
 * <p>
 * Measured 2026-10-15 (legs {@code gv-cohort-vis6} and {@code gv-cohort-vis7}, on merged source): the renderer
 * reported {@code sections=3952}, then {@code visible=1}, and asked whether a box at the camera's own block was
 * visible - it was not. The cause is in vanilla: {@code ClippingHelperImpl.init()} fills its projection and
 * modelview buffers with {@code GlStateManager.getFloat(2983, ...)} and {@code getFloat(2982, ...)}, which read the
 * <em>GL</em> matrices. Under the GL-less window there is no context, the stub answers those reads with zeros, and
 * this class then set the frustum from a zero matrix, whose planes reject every box - so the walk kept only its
 * root and nothing was ever drawn.
 * <p>
 * 1.12.2's {@code GlStateManager} keeps no Java-side matrix stacks, so the values cannot be read; they are computed
 * here instead, from state Minecraft itself holds in Java. The camera basis comes from the rotation fields
 * {@code ActiveRenderInfo} writes in {@code updateRenderInfo}: {@code rotationX = cos(yaw)},
 * {@code rotationZ = sin(yaw)}, {@code rotationXY = rotationX * sin(pitch)}, {@code rotationXZ = cos(pitch)}. The
 * look vector those encode is {@code (-rotationZ * rotationXZ, -rotationXY, rotationX * rotationXZ)}, which points
 * along +Z at yaw zero, as Minecraft's yaw does.
 * <p>
 * The frustum is deliberately generous. Its job here is to reject what is plainly behind the camera, not to be the
 * culling authority: the search already applies the distance test, and the cost of keeping a section that turns out
 * to be outside is a draw the old path made anyway, while the cost of dropping one is a hole in the world. So the
 * far plane is far, the field of view is widened rather than narrowed, and any degenerate input - a zero-height
 * window, a NaN, no player - leaves the frustum <em>permissive</em> instead of rejecting everything. A culler that
 * cannot compute its planes must not answer "nothing is visible".
 */
@Mixin(ClippingHelperImpl.class)
public abstract class ClippingHelperImplMixin extends ClippingHelper implements IClippingHelper {
    @Unique
    private final FrustumIntersection celeritas$frustum = new FrustumIntersection();

    /** Bounded report counter for the frustum build. */
    @Unique
    private static int celtium$frustumReports;

    @Inject(method = "init", at = @At("RETURN"))
    private void updateJoml(CallbackInfo ci) {
        Matrix4f vp = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                ? celtium$buildGllessViewProjection()
                : celtium$fromVanillaBuffers();

        // Bounded report: which branch ran, and whether the matrix it produced is finite. The first attempt at this
        // fix changed the jar and left cameraBoxVisible false, so the next question is whether the computed matrix
        // was used at all.
        if (celtium$frustumReports++ < 6) {
            System.out.println("[Celtium] CELTIUM_FRUSTUM glless=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()
                    + " source=" + (org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled() ? "computed" : "vanilla")
                    + " vp=" + (vp == null ? "null" : (vp.isFinite() ? "finite" : "notfinite")));
        }

        if (vp == null) {
            // Permissive rather than empty: see the class comment.
            this.celeritas$frustum.set(new Matrix4f(), false);
            return;
        }

        this.celeritas$frustum.set(vp, true);

        // Self-test at the build site: a box at the camera's own position must intersect. If this is true here and the
        // culler still reads false, the culler is looking at a different frustum instance; if it is false here, the
        // matrix built above is wrong. Bounded.
        if (celtium$frustumReports < 8) {
            System.out.println("[Celtium] CELTIUM_FRUSTUM selfTest=" + this.celeritas$frustum.testAab(-1, -1, -1, 1, 1, 1)
                    + " aspect=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled());
        }
    }

    /** {@return the frustum from the vanilla buffers, which are valid when a GL context exists} */
    @Unique
    private Matrix4f celtium$fromVanillaBuffers() {
        Matrix4f jomlProjection = new Matrix4f();
        jomlProjection.set(projectionMatrix);
        Matrix4f jomlModelview = new Matrix4f();
        jomlModelview.set(modelviewMatrix);
        return jomlProjection.mul(jomlModelview);
    }

    /**
     * {@return the view-projection matrix computed from Java-side state, or null when it cannot be built}.
     * <p>
     * Null is not a failure to report: the caller turns it into a permissive frustum, because a culler that cannot
     * compute its planes must not claim that nothing is visible.
     */
    @Unique
    private Matrix4f celtium$buildGllessViewProjection() {
        // One source of truth: the same builder writes the terrain's MODELVIEW/PROJECTION buffers, so the frustum
        // and the draws cannot disagree about the camera.
        return org.taumc.celeritas.core.GllessViewProjection.viewProjection();
    }

    @Override
    public FrustumIntersection celeritas$getJomlFrustum() {
        return this.celeritas$frustum;
    }
}
