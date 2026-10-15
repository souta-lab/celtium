package org.taumc.celeritas.impl.render.terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;

public class CameraHelper {
    /**
     * {@return the sub-pixel camera offset the third-person view adds, or zero when it cannot be recovered}.
     * <p>
     * Measured 2026-10-15 (leg {@code gv-cohort-fru6}, on the build with the frustum fix): the culler reported
     * {@code sections=3824}, {@code visible=1}, and a box at the camera's own block was still rejected while the
     * frustum itself accepted the same box asked of it directly. The difference is this offset: it is recovered by
     * inverting the model-view matrix, and under the GL-less window that matrix is the one
     * {@code ActiveRenderInfo.MODELVIEW} holds, which vanilla fills with {@code GlStateManager.getFloat(2982, ...)} -
     * a GL read the stub answers with zeros. Inverting a zero matrix produces not-a-number, the viewport's position
     * becomes not-a-number, and every box the culler tests is turned away however correct the frustum is.
     * <p>
     * Zero is the honest answer here, not a fallback: the camera's position already comes from
     * {@code Frustum.setPosition}, and this offset only corrects the sub-pixel difference the third-person view
     * introduces. Without it the viewport is at most a fraction of a block off, which is a section test's tolerance,
     * while a not-a-number viewport rejects the entire world.
     */
    public static Vector3f getThirdPersonOffset() {
        final Matrix4f modelView = new Matrix4f(ActiveRenderInfoAccessor.getModelViewMatrix());

        if (!modelView.isFinite()) {
            return new Vector3f();
        }

        final Vector3f offset = new Vector3f(); // third person offset
        final Matrix4f inverseModelView = new Matrix4f(modelView).invert();

        if (!inverseModelView.isFinite()) {
            return new Vector3f();
        }

        inverseModelView.transformPosition(offset);
        return offset.isFinite() ? offset : new Vector3f();
    }
}
