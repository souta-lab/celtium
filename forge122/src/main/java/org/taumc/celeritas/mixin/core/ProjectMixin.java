package org.taumc.celeritas.mixin.core;

import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mirrors Minecraft's world projection into the Vulkan renderer.
 * <p>
 * This is the case the inventory predicted and the first end-to-end attempt measured: the projection Minecraft draws
 * the world with is built by {@code org.lwjgl.util.glu.Project.gluPerspective} and installed through
 * {@code GL11.glFrustum} - it never passes through {@code GlStateManager}, so a Mixin on the state wrapper alone
 * cannot see it and an immediate draw mirrored without it lands at a stale projection and is invisible.
 * <p>
 * The target is named as a string ({@code targets = ...}) because that class comes from LWJGL 2's utility jar, which
 * this module does not compile against; naming it avoids adding a build dependency just to reference it. The method is
 * matched by name and descriptor, the injector is {@code require = 0} and the mirror is exception-guarded, so an
 * unexpected shape degrades to "not mirrored" instead of breaking startup. Nothing public is changed: the method, its
 * body and its callers are untouched.
 */
@Mixin(targets = "org.lwjgl.util.glu.Project")
public abstract class ProjectMixin {
    @Inject(method = "gluPerspective(FFFF)V", at = @At("HEAD"), require = 0)
    private static void celtium$perspective(float fov, float aspect, float near, float far, CallbackInfo info) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            backend.perspective(fov, aspect, near, far);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; never break the caller.
        }
    }
}
