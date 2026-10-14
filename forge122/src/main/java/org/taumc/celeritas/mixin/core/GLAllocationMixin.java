package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GLAllocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.core.CeltiumGllessHint;

/**
 * Hands out a display-list id when the GL surface cannot, so vanilla's own check does not abort the client.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-e2e-268-fix128w}, the 268-mod pack): once the atlas was built the pack died with
 * {@code java.lang.IllegalStateException: glGenLists returned an ID of 0 for a count of 1, GL error (0): No error code
 * reported}, thrown by {@code GLAllocation.generateDisplayLists} because {@code GlStateManager.glGenLists} answered
 * zero. The path is not this renderer's LWJGL3 replacement - {@code glGenLists} does not exist in LWJGL3's GL11C at
 * all, checked with javap - so the zero comes from the LWJGL2 shim that Cleanroom provides for the old API, where
 * display lists have no modern equivalent. The stub's own generator starts at 1 and is not the source.
 * <p>
 * Display lists are a fixed-function feature this renderer never uses: it keeps its own command buffers and ignores
 * every list id it is handed. What matters is that the id is non-zero and distinct, which is all the callers do with
 * it, so the mixin answers the call itself instead of letting a zero abort startup.
 */
@Mixin(GLAllocation.class)
public class GLAllocationMixin {
    @Inject(method = "generateDisplayLists", at = @At("HEAD"), cancellable = true)
    private static void celtium$generateDisplayLists(int range, CallbackInfoReturnable<Integer> cir) {
        if (CeltiumGllessHint.isGllessClaimed()) {
            cir.setReturnValue(Integer.valueOf(CeltiumGllessHint.glGenName()));
        }
    }
}
