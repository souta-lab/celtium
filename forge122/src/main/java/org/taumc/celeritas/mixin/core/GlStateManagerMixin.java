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

    @Inject(method = "func_179144_i", at = @At("HEAD"), require = 0)
    private static void celtium$bindTexture(int glTextureId, CallbackInfo info) {
        celtium$mirror(backend -> backend.bindTexture(glTextureId));
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
        // The GL constant each constant carries, not its ordinal: the enum stores the real factor (SRC_ALPHA is 770)
        // and reading that table once keeps the mirror correct even if the declaration order ever changes.
        int src = celtium$glFactor(SOURCE_FACTORS, sourceRgb);
        int dst = celtium$glFactor(DEST_FACTORS, destRgb);
        int srcAlpha = celtium$glFactor(SOURCE_FACTORS, sourceAlpha);
        int dstAlpha = celtium$glFactor(DEST_FACTORS, destAlpha);
        celtium$mirror(backend -> backend.setBlendFunc(src, dst, srcAlpha, dstAlpha));
    }

    // Matrix *contents* matter as much as the stack: without them an immediate draw uses identities and lands in
    // the wrong place (which is exactly what the first end-to-end attempt measured).
    @Inject(method = "func_179128_n", at = @At("HEAD"), require = 0)
    private static void celtium$matrixMode(int mode, CallbackInfo info) {
        celtium$mirror(backend -> backend.matrixMode(mode));
    }

    @Inject(method = "func_179096_D", at = @At("HEAD"), require = 0)
    private static void celtium$loadIdentity(CallbackInfo info) {
        celtium$mirror(TerrainGpuBackend::loadIdentity);
    }

    @Inject(method = "func_179130_a", at = @At("HEAD"), require = 0)
    private static void celtium$ortho(double left, double right, double bottom, double top, double near, double far,
                                      CallbackInfo info) {
        celtium$mirror(backend -> backend.ortho(left, right, bottom, top, near, far));
    }

    @Inject(method = "func_179137_b", at = @At("HEAD"), require = 0)
    private static void celtium$translate(double x, double y, double z, CallbackInfo info) {
        celtium$mirror(backend -> backend.translate(x, y, z));
    }

    @Inject(method = "func_179114_b", at = @At("HEAD"), require = 0)
    private static void celtium$rotate(float angle, float x, float y, float z, CallbackInfo info) {
        celtium$mirror(backend -> backend.rotate(angle, x, y, z));
    }

    @Inject(method = "func_179152_a", at = @At("HEAD"), require = 0)
    private static void celtium$scale(float x, float y, float z, CallbackInfo info) {
        celtium$mirror(backend -> backend.scale(x, y, z));
    }

    @Inject(method = "func_179110_a", at = @At("HEAD"), require = 0)
    private static void celtium$multiplyMatrix(java.nio.FloatBuffer buffer, CallbackInfo info) {
        celtium$mirror(backend -> backend.multiplyMatrix(buffer));
    }

    private static final int[] SOURCE_FACTORS = celtium$factorTable(GlStateManager.SourceFactor.values());
    private static final int[] DEST_FACTORS = celtium$factorTable(GlStateManager.DestFactor.values());

    /**
     * Reads the OpenGL constant out of each blend-factor enum constant.
     * <p>
     * Both enums declare exactly one {@code int} field, which is the GL factor they wrap, so finding it by type is
     * robust whereas assuming the declaration order is not. The table is built once.
     */
    private static int[] celtium$factorTable(Enum<?>[] values) {
        int[] table = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            try {
                for (java.lang.reflect.Field field : values[i].getDeclaringClass().getDeclaredFields()) {
                    if (field.getType() == int.class) {
                        field.setAccessible(true);
                        table[i] = field.getInt(values[i]);
                        break;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                table[i] = 0;
            }
        }
        return table;
    }

    private static int celtium$glFactor(int[] table, Enum<?> value) {
        if (value == null) {
            return 0;
        }
        int ordinal = value.ordinal();
        return ordinal >= 0 && ordinal < table.length ? table[ordinal] : 0;
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
