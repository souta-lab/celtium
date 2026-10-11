package org.taumc.celeritas.mixin.core;

import java.util.function.Consumer;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fills the one projection channel the state mirror cannot reach.
 * <p>
 * {@code GlStateManager.matrixMode} and {@code loadIdentity} are hooked, so the mirrored projection becomes the
 * identity whenever the game replaces it - and the call that refills it is {@code Project.gluPerspective}, whose class
 * sits outside the transforming loader, which is why the counter the entity-renderer mixin installed for it has stayed
 * at zero. The mirror is therefore left with the identity for the rest of the pass: measured as
 * {@code CELTIUM_IMMEDIATE_ENQ proj=0.0,0.0,0.5,0.5} for the sunrise fan, the sun and moon and the fancy cloud cells,
 * against {@code (0.0,0.0,-1.001,-0.1)} for the particle batches in the same frames. Batches accepted in that window
 * are drawn with a projection that is not the camera's, which is the shape the sky-band artefacts have.
 * <p>
 * The receiving end already existed and had no caller: {@code TerrainGpuBackend.perspective} multiplies the
 * projection stack and has counters of its own.
 * <p>
 * The guard is written out here rather than shared with {@code GlStateManagerMixin}: a mixin's methods are merged into
 * the class it targets, so calling {@code GlStateManagerMixin.celtium$mirror(...)} from another mixin is a reference to
 * a class that has no such method at runtime - which is exactly how the first version of this file failed to load, with
 * {@code NoClassDefFoundError: net/minecraft/client/renderer/GlStateManager} during Framebuffer's construction.
 */
@Mixin(org.lwjgl.util.glu.Project.class)
public class GluProjectionMixin {

    @Inject(method = "gluPerspective", at = @At("HEAD"), require = 0)
    private static void celtium$gluPerspective(float fovy, float aspect, float near, float far, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            // The same action GlStateManagerMixin performs for the hooked matrix calls, so the mirror sees one
            // projection channel whatever route the game used to set it.
            Consumer<TerrainGpuBackend> action = b -> b.perspective(fovy, aspect, near, far);
            action.accept(backend);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
