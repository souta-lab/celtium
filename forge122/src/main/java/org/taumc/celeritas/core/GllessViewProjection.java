package org.taumc.celeritas.core;

import net.minecraft.client.Minecraft;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The one place the GL-less camera matrices are built.
 * <p>
 * Two callers need them and they must agree: the frustum that decides visibility, and the MODELVIEW/PROJECTION
 * buffers the terrain renderer hands to the chunk renderer. Measured 2026-10-15: with the buffers left to whatever
 * the stub held, 47142 native draws were recorded, the window draw was adopted into the frame image, and 92% of the
 * presented image was still black - the OpenGL convention puts NDC depth in [-1,1] and Vulkan clips at [0,1], so
 * every terrain vertex was clipped away while the 2D path, which does not use depth, kept drawing.
 * <p>
 * The angles are the player's, which are the same angles {@code ActiveRenderInfo.updateRenderInfo} derives its
 * rotation fields from, so this agrees with the fields the rest of the renderer reads rather than introducing a
 * second convention.
 */
public final class GllessViewProjection {
    private GllessViewProjection() {
    }

    /** {@return the camera's look vector, or null when the player or the window is not ready} */
    public static Vector3f lookVector() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayWidth <= 0 || mc.displayHeight <= 0) {
            return null;
        }

        // The rotation fields rather than the player: they are the same angles (ActiveRenderInfo derives them
        // from the entity's yaw and pitch) and they exist even when there is no player yet, which is the state the
        // world is drawn in while it loads. Requiring mc.player left the model view unwritten there, and a zero
        // model view sends every vertex to the origin: measured 2026-10-15 (leg gv-cohort-mat19) as
        // CELTIUM_GROUP_DEPTH row1=0.0,0.0,-1.0,-0.05 (the projection, correct) against mvT=0.0,0.0,0.0.
        float rotationX = ActiveRenderInfoAccessor.celtium$getRotationX();
        float rotationZ = ActiveRenderInfoAccessor.celtium$getRotationZ();
        float rotationXZ = ActiveRenderInfoAccessor.celtium$getRotationXZ();
        float rotationXY = ActiveRenderInfoAccessor.celtium$getRotationXY();

        if (rotationX == 0.0f && rotationZ == 0.0f && rotationXZ == 0.0f) {
            // Nothing has written the fields yet; without them there is no camera to build.
            return null;
        }

        Vector3f look = new Vector3f(
                -rotationZ * rotationXZ,
                -rotationXY,
                rotationX * rotationXZ);

        if (!look.isFinite() || look.lengthSquared() < 1.0e-6f) {
            return null;
        }
        return look;
    }

    /** {@return the aspect ratio, or 0 when it is not usable} */
    public static float aspect() {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null || mc.displayHeight <= 0) {
            return 0.0f;
        }
        float aspect = (float) mc.displayWidth / (float) mc.displayHeight;
        return Float.isFinite(aspect) && aspect > 0.0f ? aspect : 0.0f;
    }

    /**
     * {@return the Vulkan-convention projection matrix, or null when it cannot be built}.
     * <p>
     * {@code zZeroToOne} is true because Vulkan clips at [0,1]; the OpenGL form would put everything below zero
     * outside the view volume.
     */
    public static Matrix4f projection() {
        float aspect = aspect();
        if (aspect <= 0.0f) {
            return null;
        }
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0f), aspect, 0.05f, 1024.0f, true);
        return projection.isFinite() ? projection : null;
    }

    /** {@return the camera rotation matrix, or null when the look vector is not usable} */
    public static Matrix4f modelView() {
        Vector3f look = lookVector();
        if (look == null) {
            return null;
        }
        Matrix4f modelView = new Matrix4f().lookAlong(look, new Vector3f(0.0f, 1.0f, 0.0f), new Matrix4f());
        return modelView.isFinite() ? modelView : null;
    }

    /**
     * {@return the view-projection matrix in Vulkan's clip space, or null when it cannot be built}.
     * <p>
     * Vulkan does not invert Y the way OpenGL's window-space convention does, so clip-space Y is flipped here. JOML's
     * Vulkan note records that this does not change vertex winding order and so does not affect backface culling.
     */
    public static Matrix4f viewProjection() {
        Matrix4f projection = projection();
        Matrix4f modelView = modelView();
        if (projection == null || modelView == null) {
            return null;
        }
        Matrix4f vp = new Matrix4f().scale(1.0f, -1.0f, 1.0f).mul(projection).mul(modelView);
        return vp.isFinite() ? vp : null;
    }
}
