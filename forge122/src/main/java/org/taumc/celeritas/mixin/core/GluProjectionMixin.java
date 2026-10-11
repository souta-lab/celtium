package org.taumc.celeritas.mixin.core;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds {@code Project.gluPerspective} into the projection mirror, which no {@code GlStateManager} hook can reach.
 * <p>
 * The mirrored projection is what the immediate path draws every batch with, and it is built from the calls
 * {@code GlStateManager} carries: {@code matrixMode} and {@code loadIdentity} are hooked, so the game's own
 * {@code matrixMode(GL_PROJECTION); loadIdentity();} - the pair in front of <em>every</em> {@code gluPerspective} call
 * site in {@code EntityRenderer} (lines 715/729, 795/803, 1346/1347, 1351/1352, 1502/1503, 1511/1512) - leaves the
 * mirror's projection at the identity, and the call that refills it is
 * {@code Project.gluPerspective(fovy, aspect, zNear, zFar)}: an LWJGL 2 utility class outside the transforming loader,
 * ending in {@code GL11.glMultMatrixf} of a freshly built frustum (measured against
 * {@code lwjgl_util-2.9.4-nightly-20150209}, {@code javap -c org.lwjgl.util.glu.Project}). It never passes through
 * Minecraft, so nothing else reports it, and the mirror keeps whatever it had - the identity in the frames whose sky
 * draw was refused as such ({@code CELTIUM_DRAWARRAYS refusedByReason=[... the mirrored projection is the identity at
 * draw time ...]}), which is the 768-block sky dome drawn by the model-view alone and clipped into slivers.
 * <p>
 * The receiving end already existed with no caller: {@link TerrainGpuBackend#mirrorGluPerspective}. It is a call and
 * not a mixin-to-mixin call on purpose: a mixin's methods are merged into the class it targets, so this class has no
 * {@code GlStateManagerMixin.celtium$mirror} to call at runtime, and the first version of this hook died at class load
 * with {@code NoClassDefFoundError} during {@code Framebuffer}'s construction for exactly that reason.
 * <p>
 * This hook fires for every {@code gluPerspective} in the tree, and deliberately so: the mirror is a mirror, and what
 * separates the camera's own call from the sky's is not a call site but where the projection stack stands when the call
 * arrives. That is decided inside the backend, from the state the mirrored {@code loadIdentity} left, and the
 * acceptance for it is the terrain's own section counts - the failure that got this hook reverted once
 * ({@code f82bf4612}: visible sections 23 -> 8) was a bare multiply onto a stack the derived projection had already
 * filled in.
 * <p>
 * {@code require = 0}: a target this build does not have must degrade to "not mirrored", never to a client that will
 * not start. The lab switch ({@code -Dceltium.vulkan.mirrorGluPerspective=false}) exists so the same jar can be run
 * with and without the channel, which is the A/B the section counts are read against.
 */
@Mixin(org.lwjgl.util.glu.Project.class)
public class GluProjectionMixin {
    /**
     * The channel's lab switch, read once. Default on: the hook is the correction, and the flag is there to measure it
     * against the same jar rather than against a second build.
     */
    private static final boolean MIRROR_ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.mirrorGluPerspective", "true"));

    @Inject(method = "gluPerspective", at = @At("HEAD"), require = 0)
    private static void celtium$gluPerspective(float fovy, float aspect, float near, float far, CallbackInfo info) {
        if (!MIRROR_ENABLED) {
            return;
        }
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            backend.mirrorGluPerspective(fovy, aspect, near, far);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
