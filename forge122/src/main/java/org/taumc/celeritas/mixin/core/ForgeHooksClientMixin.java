package org.taumc.celeritas.mixin.core;

import java.nio.FloatBuffer;

import javax.vecmath.Matrix4f;

import net.minecraftforge.client.ForgeHooksClient;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The owner of the item camera transform in the mirror.
 * <p>
 * Forge applies an item's {@code ItemCameraTransforms} with a raw GL call that bypasses {@code GlStateManager}:
 * {@code handleCameraTransforms} calls this method (Forge 1.12.2-14.23.5.2847, {@code ForgeHooksClient.java:425-440}),
 * whose body is {@code GL11.glMultMatrix(matrixBuf)} (ibid.:444-455). Every other matrix call in the game goes through
 * {@code GlStateManager}, which {@code GlStateManagerMixin} already mirrors - this one had no owner, so a GUI item, a
 * held sword and a dropped item all lost their own transform (measured: the JEI slot grid filled edge to edge with a
 * flat texture, and the dropped item large and dark because its GROUND translate/rotate/scale 0.5 never arrived).
 * <p>
 * This is the public entry every transform type passes through - {@code GUI}, {@code FIRST_PERSON},
 * {@code THIRD_PERSON} and {@code GROUND} - so hooking it here covers all of them with one owner and no per-item
 * special case. The stub's raw {@code glMultMatrixf} stays a census on purpose: {@code Project.gluPerspective} reaches
 * that same raw call <em>after</em> {@link GluProjectionMixin} has mirrored the frustum into the projection stack, and
 * applying both would reflect one operation twice. One operation, one owner.
 */
@Mixin(ForgeHooksClient.class)
public class ForgeHooksClientMixin {
    /** Reused per call: this runs for every item drawn, and the matrix is 16 floats. */
    private static final float[] celtium$matrix = new float[16];
    private static final float[] celtium$column = new float[4];

    @Inject(method = "multiplyCurrentGlMatrix", at = @At("HEAD"), require = 0)
    private static void celtium$multiplyCurrentGlMatrix(Matrix4f matrix, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            // The same column-major order Forge itself copies into the FloatBuffer, so the mirror's multiply sees
            // exactly the matrix GL would have received.
            for (int i = 0; i < 4; i++) {
                matrix.getColumn(i, celtium$column);
                System.arraycopy(celtium$column, 0, celtium$matrix, i * 4, 4);
            }
            backend.multiplyMatrix(FloatBuffer.wrap(celtium$matrix));
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
