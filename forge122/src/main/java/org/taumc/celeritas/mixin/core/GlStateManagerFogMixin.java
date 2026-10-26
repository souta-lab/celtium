package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.FloatBuffer;

/** Vanilla fog setters terminate at the native owner; direct mod GL uses the compatibility adapter. */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerFogMixin {

    /** {@code GL_FOG_COLOR}: the pname {@code setupFogColor} passes to {@code glFog}. */
    private static final int GL_FOG_COLOR = 2918;

    @Inject(method = "func_179127_m", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$enableFog(CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().enabled(true); info.cancel();
    }

    @Inject(method = "func_179106_n", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$disableFog(CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().enabled(false); info.cancel();
    }

    @Inject(method = "func_179093_d", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$setFogMode(int glMode, CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().parameter(0x0B65, glMode); info.cancel();
    }

    @Inject(method = "func_179095_a", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$setFogDensity(float density, CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().parameter(0x0B62, density); info.cancel();
    }

    @Inject(method = "func_179102_b", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$setFogStart(float start, CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().parameter(0x0B63, start); info.cancel();
    }

    @Inject(method = "func_179153_c", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$setFogEnd(float end, CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().parameter(0x0B64, end); info.cancel();
    }

    /**
     * {@code glFog(GL_FOG_COLOR, buffer)}: the colour is read out of the caller's buffer at its current position,
     * because that is exactly what the GL call would have read. Scalar fog parameters use the first value.
     */
    @Inject(method = "func_187402_b", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$glFog(int pname, FloatBuffer params, CallbackInfo info) {
        if (!celtiumFog$native() || params == null || !params.hasRemaining()) return;
        int position = params.position();
        if (pname == GL_FOG_COLOR) {
            if (params.remaining() < 4) return;
            state().color(params.get(position), params.get(position + 1), params.get(position + 2), params.get(position + 3));
        } else state().parameter(pname, params.get(position));
        info.cancel();
    }

    @Inject(method = "func_187412_c", at = @At("HEAD"), cancellable = true, require = 1)
    private static void celtiumFog$glFogi(int pname, int param, CallbackInfo info) {
        if (!celtiumFog$native()) return;
        state().parameter(pname, param);
        info.cancel();
    }

    private static boolean celtiumFog$native() { return org.taumc.celeritas.core.CeltiumNativeDraw.enabled(); }
    private static org.soutalab.celtium.vulkan.ImmediateFogState state() {
        return org.soutalab.celtium.vulkan.ImmediateFogState.current();
    }
}
