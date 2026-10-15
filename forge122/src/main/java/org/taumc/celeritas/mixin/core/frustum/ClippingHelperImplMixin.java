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
        Minecraft mc = Minecraft.getMinecraft();

        if (mc == null || mc.player == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return null;
        }

        float yaw = mc.player.rotationYaw;
        float pitch = mc.player.rotationPitch;

        // The same basis ActiveRenderInfo writes, from the same angles, so this agrees with the fields the rest of
        // the renderer reads rather than introducing a second convention.
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        float rotationX = (float) Math.cos(yawRadians);
        float rotationZ = (float) Math.sin(yawRadians);
        float rotationXZ = (float) Math.cos(pitchRadians);
        float rotationXY = rotationX * (float) Math.sin(pitchRadians);

        Vector3f look = new Vector3f(
                -rotationZ * rotationXZ,
                -rotationXY,
                rotationX * rotationXZ);

        if (!look.isFinite() || look.lengthSquared() < 1.0e-6f) {
            return null;
        }

        float aspect = (float) mc.displayWidth / (float) mc.displayHeight;
        if (!Float.isFinite(aspect) || aspect <= 0.0f) {
            return null;
        }

        // Widened rather than narrowed, so the frustum never rejects something the camera can see.
        float fovDegrees = Math.max(mc.gameSettings.fovSetting, 70.0f) + 20.0f;
        float near = 0.05f;
        float far = 1024.0f;

        // zZeroToOne must be true: Vulkan clips at NDC z in [0,1] while OpenGL's convention is [-1,1]. With the
        // OpenGL range the terrain falls entirely below zero and is clipped away, and the frame still looks
        // plausible because the 2D path - which does not use depth - draws normally. Measured 2026-10-15 (leg
        // gv-cohort-atlas11): 77711 native draws were recorded, the window draw was adopted into the frame image
        // (attachedImage == frameImage), and 92% of the presented image was still black with a correct HUD.
        // See the Vulkan clip-space note in the Veldrid backend-differences table and the Mesa/NVIDIA report of
        // an OpenGL projection matrix making a globe vanish.
        // Vulkan's clip space differs from OpenGL's in two ways and both have to be applied. The depth range is
        // [0,1] rather than [-1,1] - zZeroToOne=true asks JOML for that form - and Vulkan does not invert Y the
        // way OpenGL's window-space convention does, so the clip-space Y axis has to be flipped by hand or the
        // world arrives upside-down. JOML's own Vulkan note states the flip does not change vertex winding order,
        // so the cull mode is untouched.
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fovDegrees), aspect, near, far, true);
        Matrix4f view = new Matrix4f().lookAlong(look, new Vector3f(0.0f, 1.0f, 0.0f), new Matrix4f());

        Matrix4f vp = new Matrix4f().scale(1.0f, -1.0f, 1.0f).mul(projection).mul(view);
        return vp.isFinite() ? vp : null;
    }

    @Override
    public FrustumIntersection celeritas$getJomlFrustum() {
        return this.celeritas$frustum;
    }
}
