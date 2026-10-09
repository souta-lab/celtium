package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * Mirrors the {@code GlStateManager} calls that select drawing state into the Vulkan backend.
 * <p>
 * This is the state half of the Forge API swap: a mod (or vanilla) that sets blend, depth, colour or the matrix
 * stack through Minecraft's own API is captured here, without changing its code and without touching any public
 * Minecraft signature. The calls are mirrored only for now - the original body still runs - so the game keeps
 * rendering while the Vulkan pipelines that consume this state are added; once those land, the same injectors become
 * cancellable and the GL call stops happening.
 * <p>
 * Every injector uses {@code require = 0}: a method name that this particular build does not have must degrade to
 * "not mirrored", never to a failed mixin that stops the client from starting. (An earlier revision used a wrong SRG
 * name and the client exited during startup, which is exactly what this guards against.) Every mirror is also
 * null-checked and exception-guarded, because it runs inside vanilla rendering.
 * <p>
 * The names are taken from the decompiled sources: {@code func_179147_l}/{@code func_179084_k} enable and disable
 * blend, {@code func_179132_a} sets the depth mask, {@code func_179097_i}/{@code func_179126_j} disable and enable
 * the depth test, {@code func_179131_c} sets the colour, {@code func_179094_E}/{@code func_179121_F} push and pop the
 * matrix, and {@code func_187428_a} sets the separate blend factors.
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerMixin {
    @Inject(method = "func_179147_l", at = @At("HEAD"), require = 0)
    private static void celtium$enableBlend(CallbackInfo info) {
        celtium$mirror(backend -> backend.setBlendEnabled(true));
    }

    @Inject(method = "func_179084_k", at = @At("HEAD"), require = 0)
    private static void celtium$disableBlend(CallbackInfo info) {
        celtium$mirror(backend -> backend.setBlendEnabled(false));
    }

    @Inject(method = "func_179132_a", at = @At("HEAD"), require = 0)
    private static void celtium$depthMask(boolean mask, CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthMask(mask));
    }

    @Inject(method = "func_179097_i", at = @At("HEAD"), require = 0)
    private static void celtium$disableDepth(CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthTest(false));
    }

    @Inject(method = "func_179126_j", at = @At("HEAD"), require = 0)
    private static void celtium$enableDepth(CallbackInfo info) {
        celtium$mirror(backend -> backend.setDepthTest(true));
    }

    @Inject(method = "func_179131_c", at = @At("HEAD"), require = 0)
    private static void celtium$color(float red, float green, float blue, float alpha, CallbackInfo info) {
        celtium$mirror(backend -> backend.setColor(red, green, blue, alpha));
    }

    @Inject(method = "func_179094_E", at = @At("HEAD"), require = 0)
    private static void celtium$pushMatrix(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::pushMatrix);
    }

    @Inject(method = "func_179121_F", at = @At("HEAD"), require = 0)
    private static void celtium$popMatrix(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::popMatrix);
    }

    /** Blend factors arrive as enum ordinals, which is what the backend's state model records. */
    @Inject(method = "func_187428_a", at = @At("HEAD"), require = 0)
    private static void celtium$tryBlendFuncSeparate(GlStateManager.SourceFactor sourceRgb,
                                                    GlStateManager.DestFactor destRgb,
                                                    GlStateManager.SourceFactor sourceAlpha,
                                                    GlStateManager.DestFactor destAlpha, CallbackInfo info) {
        int src = sourceRgb == null ? 0 : sourceRgb.ordinal();
        int dst = destRgb == null ? 0 : destRgb.ordinal();
        int srcAlpha = sourceAlpha == null ? 0 : sourceAlpha.ordinal();
        int dstAlpha = destAlpha == null ? 0 : destAlpha.ordinal();
        celtium$mirror(backend -> backend.setBlendFunc(src, dst, srcAlpha, dstAlpha));
    }

    private static void celtium$mirror(Consumer<TerrainGpuBackend> action) {
        TerrainGpuBackend backend = TerrainGpuBackends.current();
        if (backend == null) {
            return;
        }
        try {
            action.accept(backend);
        } catch (Throwable ignored) {
            // The backend records its own diagnostics; nothing here may break vanilla rendering.
        }
    }
}
