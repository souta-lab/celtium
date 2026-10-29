package org.taumc.celeritas.mixin.core.frustum;

import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.client.renderer.culling.Frustum;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.ViewportProvider;
import org.spongepowered.asm.mixin.*;
import org.taumc.celeritas.impl.render.frustum.IClippingHelper;
import org.taumc.celeritas.impl.render.terrain.CameraHelper;

@Mixin(Frustum.class)
public class FrustumMixin implements ViewportProvider {
    @Shadow
    @Final
    private ClippingHelper clippingHelper;

    @Shadow
    private double x, y, z;

    @Overwrite
    public boolean isBoxInFrustum(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        if (Double.isInfinite(minX) || Double.isInfinite(minY) || Double.isInfinite(minZ) || Double.isInfinite(maxX) || Double.isInfinite(maxY) || Double.isInfinite(maxZ)) {
            return true;
        }
        return ((IClippingHelper)clippingHelper).celeritas$getJomlFrustum().testAab((float) (minX - this.x), (float) (minY - this.y), (float) (minZ - this.z), (float) (maxX - this.x), (float) (maxY - this.y), (float) (maxZ - this.z));
    }

    @Override
    public Viewport sodium$createViewport() {
        var frustum = ((IClippingHelper)clippingHelper).celeritas$getJomlFrustum();
        var offset = CameraHelper.getThirdPersonOffset();
        // The graph starts at the optical camera, but the planes were built from P * MV, where MV already
        // contains the offset from the entity's feet to that camera (EntityRenderer.orientCamera). Viewport
        // subtracts its own position before calling this predicate, so put the offset back to recover the
        // feet-relative coordinates isBoxInFrustum above supplies. Otherwise eye/third-person translation applies twice.
        return new Viewport((minX, minY, minZ, maxX, maxY, maxZ) -> frustum.testAab(
                minX + offset.x, minY + offset.y, minZ + offset.z,
                maxX + offset.x, maxY + offset.y, maxZ + offset.z),
                new org.joml.Vector3d(this.x, this.y, this.z).add(offset),
                // The view the planes came from, so the terrain search can tell whether the lists it published were
                // searched from (nearly) this view; see RenderListManager.publishedListsMatch.
                ((IClippingHelper)clippingHelper).celeritas$getViewProjection(), offset);
    }
}
