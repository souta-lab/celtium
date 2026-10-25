package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.shader.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips vanilla's entity-outline composite when the outline framebuffer can only hold its transparent clear.
 * <p>
 * {@code renderEntityOutlineFramebuffer} blends the outline framebuffer over the frame on every frame the outline
 * shader exists, outlined entities or not. Vanilla's own bookkeeping says when that framebuffer is empty:
 * {@code renderEntities} clears it and leaves {@code entityOutlinesRendered} false in the frame the last outlined entity
 * went away, and draws nothing into it while the flag stays false. Its clear colour's alpha is 0 by default, and the
 * composite blends colour with SRC_ALPHA / ONE_MINUS_SRC_ALPHA and alpha with ZERO / ONE - so a transparent source
 * leaves every destination pixel exactly as it was, and the pass is skipped instead of drawn. Measured need (GPU marks,
 * Intel UHD 630, world without glowing entities): the composite was one full-screen blended quad at ~250 us of a
 * 2.0-2.4 ms GPU frame. {@code -Dceltium.native.skipEmptyOutlineComposite=false} keeps the composite.
 */
@Mixin(RenderGlobal.class)
public abstract class RenderGlobalOutlineCompositeMixin {
    @Unique
    private static final boolean CELTIUM_SKIP_EMPTY = !"false".equalsIgnoreCase(
            System.getProperty("celtium.native.skipEmptyOutlineComposite"));

    @Unique
    private static long celtium$skippedComposites;

    @Shadow
    private boolean entityOutlinesRendered;

    @Shadow
    private Framebuffer entityOutlineFramebuffer;

    @Inject(method = "func_174975_c", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$skipTransparentOutlineComposite(CallbackInfo info) {
        if (!CELTIUM_SKIP_EMPTY) {
            return;
        }
        Framebuffer outline = this.entityOutlineFramebuffer;
        // The clear colour is checked rather than assumed: a framebuffer whose clear leaves alpha above 0 is not a
        // no-op to blend, and that is the one input to the argument above the game could have changed.
        if (outline != null && !this.entityOutlinesRendered && outline.framebufferColor != null
                && outline.framebufferColor.length > 3 && outline.framebufferColor[3] == 0.0F) {
            celtium$skippedComposites++;
            if (celtium$skippedComposites == 1L) {
                System.out.println("[Celtium] CELTIUM_OUTLINE_COMPOSITE skipped (no outlined entity drawn; the outline"
                        + " framebuffer holds its transparent clear)");
            }
            info.cancel();
        }
    }
}
