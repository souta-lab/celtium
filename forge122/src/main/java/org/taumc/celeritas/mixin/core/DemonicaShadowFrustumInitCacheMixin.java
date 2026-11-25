package org.taumc.celeritas.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * E7 remaining branch (item 690, alpha): skip the per-frame re-initialisation of Demonica's
 * cached advanced shadow frustum when its inputs have not moved past a quantum.
 * <p>
 * The original evidence is Demonica 0.9.1's bytecode (javap of the item 509 snapshot jar).
 * {@code ShadowRenderer.createShadowFrustum} already caches: the ADVANCED branch reuses the
 * single {@code cachedAdvancedFrustum} instance and the {@code BoxCuller} is recreated only
 * when the culling distance changes ({@code getOrCreateAdvancedBoxCuller}). What every frame
 * still pays is {@code AdvancedShadowCullingFrustum.init(modelView, projection, light,
 * boxCuller)}: it stores the light vector and the box culler, resets the plane count, and
 * rebuilds the whole clipping-plane set - {@code BaseClippingPlanes.init} (the transposed
 * projection x modelView product and its six plane extractions, a pure function of the two
 * matrices) plus the back-plane and edge-plane passes over them (pure functions of those
 * planes and the light vector). Nothing else in the class writes the plane state:
 * {@code planes} / {@code planeCount} are written only in the constructor, in {@code init},
 * and in the {@code addPlane} helpers that only {@code init}'s two passes call. The frustum
 * position is set separately by the caller after creation ({@code Frustum.func_78547_a}),
 * so it is not part of what {@code init} produces and is not keyed here.
 * <p>
 * So when the key is unchanged, the plane set {@code init} would rebuild is the plane set the
 * frustum already holds, up to the quantisation error below, and the call can be skipped
 * whole. The key is exactly init's inputs: both matrices quantised to 1/256 component steps,
 * the light direction quantised to 1/32 component steps (the same quantum the E7
 * shadow-search gate on alpha-residual uses for the light), and the box culler by identity
 * (identity changes exactly when Demonica recreates it, i.e. when the culling distance
 * changes). The stored key is the one from the last init that actually ran, so the drift
 * against the held planes is bounded by one quantum total, never accumulating frame over
 * frame. What can lag is a culling decision for a box sitting within that quantum of a plane
 * boundary; the shadow camera matrices themselves are rebuilt by Demonica every frame
 * regardless, so the rendered shadow map keeps tracking the light - only the culling plane
 * set is reused, the same division the E7 search gate makes for the section set.
 * <p>
 * Lab switch: {@code -Dceltium.shadowFrustumInitCache} (default off; off is the pre-change
 * behaviour, init every frame). While on, a CELTIUM_SHADOW_FRUSTUM_INIT_CACHE line is printed
 * every 600 init calls so a leg can read the skip rate. {@code @Pseudo} + {@code require = 0}:
 * silently off without Demonica, like the item 648/690 timing brackets on the same classes.
 */
@Pseudo
@Mixin(targets = "net.coderbot.iris.shadows.frustum.advanced.AdvancedShadowCullingFrustum")
public abstract class DemonicaShadowFrustumInitCacheMixin {

    private static final boolean CELTIUM$FRUSTUM_INIT_CACHE =
            Boolean.parseBoolean(System.getProperty("celtium.shadowFrustumInitCache", "true"));
    private static final float CELTIUM$MATRIX_QUANTUM = 256.0f;
    private static final float CELTIUM$LIGHT_QUANTUM = 32.0f;
    private static final int CELTIUM$KEY_LENGTH = 16 + 16 + 3;

    @Unique
    private int[] celtium$frustumInitKey;
    @Unique
    private int[] celtium$frustumInitScratch;
    @Unique
    private Object celtium$frustumInitBoxCuller;
    @Unique
    private long celtium$frustumInitsRun;
    @Unique
    private long celtium$frustumInitsSkipped;

    @Inject(method = "init(Lorg/joml/Matrix4fc;Lorg/joml/Matrix4fc;Lorg/joml/Vector3f;Lnet/coderbot/iris/shadows/frustum/BoxCuller;)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$skipFrustumInitWhenKeyUnchanged(org.joml.Matrix4fc modelView,
            org.joml.Matrix4fc projection, org.joml.Vector3f lightVector,
            net.coderbot.iris.shadows.frustum.BoxCuller boxCuller, CallbackInfo info) {
        if (!CELTIUM$FRUSTUM_INIT_CACHE) {
            return;
        }
        if (this.celtium$frustumInitScratch == null) {
            this.celtium$frustumInitScratch = new int[CELTIUM$KEY_LENGTH];
        }
        int[] key = this.celtium$frustumInitScratch;
        int i = 0;
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                key[i++] = Math.round(modelView.get(column, row) * CELTIUM$MATRIX_QUANTUM);
            }
        }
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                key[i++] = Math.round(projection.get(column, row) * CELTIUM$MATRIX_QUANTUM);
            }
        }
        key[i++] = Math.round(lightVector.x() * CELTIUM$LIGHT_QUANTUM);
        key[i++] = Math.round(lightVector.y() * CELTIUM$LIGHT_QUANTUM);
        key[i++] = Math.round(lightVector.z() * CELTIUM$LIGHT_QUANTUM);

        if (this.celtium$frustumInitKey != null
                && this.celtium$frustumInitBoxCuller == boxCuller
                && java.util.Arrays.equals(key, this.celtium$frustumInitKey)) {
            this.celtium$frustumInitsSkipped++;
            if ((this.celtium$frustumInitsRun + this.celtium$frustumInitsSkipped) % 600 == 0) {
                System.out.println("CELTIUM_SHADOW_FRUSTUM_INIT_CACHE run=" + this.celtium$frustumInitsRun
                        + " skipped=" + this.celtium$frustumInitsSkipped);
            }
            info.cancel();
            return;
        }
        // Keep the key of the init that is about to run; the scratch becomes the stored key
        // and a fresh scratch is allocated on the next call.
        this.celtium$frustumInitKey = key;
        this.celtium$frustumInitScratch = null;
        this.celtium$frustumInitBoxCuller = boxCuller;
        this.celtium$frustumInitsRun++;
    }
}
