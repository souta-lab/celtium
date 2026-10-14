package org.taumc.celeritas.mixin.core;

import net.minecraftforge.fml.client.SplashProgress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.core.CeltiumGllessHint;

/**
 * Answers Forge's cached maximum texture size when the GL-less path means it was never measured.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-fix128w}): the cohort died at 28.5 s with
 * {@code net.minecraft.client.renderer.StitcherException: Unable to fit: immersiveengineering:blocks/...}, and vanilla's
 * own line above it read {@code Max texture size: -1}. The chain is in the sources: {@code TextureMap.loadTextureAtlas}
 * takes {@code Minecraft.getGLMaximumTextureSize()} and builds {@code new Stitcher(i, i, 0, mipmapLevels)} - with -1
 * nothing fits - and {@code Minecraft.getGLMaximumTextureSize()} is redirected by Forge to
 * {@code net.minecraftforge.fml.client.SplashProgress.getMaxTextureSize()}, whose comment says the value is cached
 * "before any splash screen stuff is done". Under this renderer there is no GL context and no splash progress to run
 * that measurement, so the cache stays at its -1 sentinel and every atlas build fails.
 * <p>
 * The stub already answers {@code GL_MAX_TEXTURE_SIZE} with 16384 for the LWJGL2 surface and the LWJGL3 replacement
 * answers the same, so the value this returns is the one the renderer claims everywhere else.
 */
@Mixin(SplashProgress.class)
public class SplashProgressMixin {
    @Inject(method = "getMaxTextureSize", at = @At("RETURN"), cancellable = true, remap = false)
    private static void celtium$answerMaxTextureSize(CallbackInfoReturnable<Integer> cir) {
        // At RETURN, not HEAD: calling SplashProgress.getMaxTextureSize() from inside that same method would re-enter
        // this injector forever. Here the original has already produced its value and only a non-positive one - the
        // -1 sentinel it was never able to replace - is substituted.
        Integer measured = cir.getReturnValue();
        if (measured == null || measured.intValue() <= 0) {
            cir.setReturnValue(Integer.valueOf(CeltiumGllessHint.claimedMaxTextureSize()));
        }
    }
}
