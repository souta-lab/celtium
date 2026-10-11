package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.GlStateManager;
import org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Records the immediate-mode calls the vanilla font's glyph methods make, and nothing else.
 * <p>
 * These four methods are the whole of Minecraft's fixed-function path for a character: {@code glBegin}
 * ({@code func_187447_r}), {@code glTexCoord2f} ({@code func_187426_b}), {@code glVertex3f} ({@code func_187435_e})
 * and {@code glEnd} ({@code func_187437_J}). Under the GL-less stub all four are state-set no-ops, which is exactly
 * why every glyph is missing from the presented frame while the Tessellator geometry around it is drawn natively.
 * <p>
 * The hooks are unconditional - they have to be, since the stub is what normally swallows these calls - but they do
 * nothing unless {@link FontRendererMixin} has opened the glyph window, so the scope of the capture is the two vanilla
 * glyph methods rather than immediate mode in general. The SRG names are the ones the compiled Minecraft calls:
 * {@code func_187447_r} is the body that calls {@code GL11.glBegin}, {@code func_187437_J} the one that calls
 * {@code GL11.glEnd}, and the other two are their {@code glTexCoord2f}/{@code glVertex3f} counterparts. All four are
 * mirrored by the same mixin package and verified against {@code javap} rather than guessed.
 * <p>
 * {@code require = 0} everywhere: a build without one of these names degrades to "not captured".
 */
@Mixin(GlStateManager.class)
public abstract class GlStateManagerImmediateMixin {

    @Inject(method = "func_187447_r", at = @At("HEAD"), require = 0)
    private static void celtium$immediateBegin(int mode, CallbackInfo info) {
        CeltiumFontGlyphCapture.immediateBegin(mode);
    }

    @Inject(method = "func_187426_b", at = @At("HEAD"), require = 0)
    private static void celtium$immediateTexCoord(float s, float t, CallbackInfo info) {
        CeltiumFontGlyphCapture.immediateTexCoord(s, t);
    }

    @Inject(method = "func_187435_e", at = @At("HEAD"), require = 0)
    private static void celtium$immediateVertex(float x, float y, float z, CallbackInfo info) {
        CeltiumFontGlyphCapture.immediateVertex(x, y, z);
    }

    @Inject(method = "func_187437_J", at = @At("HEAD"), require = 0)
    private static void celtium$immediateEnd(CallbackInfo info) {
        CeltiumFontGlyphCapture.immediateEnd();
    }
}
