package org.taumc.celeritas.impl.render.terrain;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.taumc.celeritas.mixin.core.terrain.ActiveRenderInfoAccessor;

public class CameraHelper {
    /**
     * {@return the optical camera's offset from the interpolated render-view entity position, or zero when unavailable}.
     * <p>
     * The historical name is not a restriction to third person: EntityRenderer.orientCamera also puts the first-person
     * eye height and near-plane shift in MODELVIEW. Third-person distance and camera effects can add several blocks.
     * Inverting that matrix locates the optical camera relative to the entity's feet, which is the origin passed to
     * Frustum.setPosition. The visibility graph needs that optical position, not the feet position.
     * <p>
     * The frustum's P * MV planes already include this translation. A predicate accepting optical-camera-relative
     * boxes must add the offset back before testing those planes, as FrustumMixin does; moving its origin alone would
     * apply the eye/third-person shift twice.
     * <p>
     * A missing or singular model-view cannot locate a camera. Keep the finite entity origin in that case instead of
     * propagating a NaN from the inverse into every visibility test.
     */
    public static Vector3f getThirdPersonOffset() {
        final Matrix4f modelView = new Matrix4f(ActiveRenderInfoAccessor.getModelViewMatrix());

        if (!modelView.isFinite()) {
            return new Vector3f();
        }

        final Vector3f offset = new Vector3f();
        final Matrix4f inverseModelView = new Matrix4f(modelView).invert();

        if (!inverseModelView.isFinite()) {
            return new Vector3f();
        }

        inverseModelView.transformPosition(offset);
        return offset.isFinite() ? offset : new Vector3f();
    }
}
