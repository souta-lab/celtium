package org.taumc.celeritas.mixin.core;

import net.minecraft.client.gui.FontRenderer;
import org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Opens the glyph capture window around the vanilla font's two glyph methods.
 * <p>
 * These are the only places Minecraft draws a character, and both draw it with immediate mode, which the GL-less stub
 * turns into a no-op: {@code renderDefaultChar} ({@code func_78266_a}, the 128x128 {@code ascii.png} page) and
 * {@code renderUnicodeChar} ({@code func_78277_a}, the per-page unicode glyph sheets). Everything else in
 * {@code FontRenderer} - underline, strikethrough - already goes through the Tessellator and is drawn natively.
 * <p>
 * The window is a scope marker, not a replacement: the vanilla body still runs (there is nothing to cancel - the GL
 * calls it makes are no-ops under the stub), and {@link CeltiumFontGlyphCapture} only looks at the immediate calls
 * that arrive between these two injections. Every other immediate-mode user in the game - a mod's own
 * {@code glBegin}/{@code glVertex3f} - is outside the window and stays exactly as refused as it is today.
 * <p>
 * {@code require = 0} on every injection: a method this build does not have must degrade to "not captured", never to
 * a client that will not start.
 */
@Mixin(FontRenderer.class)
public abstract class FontRendererMixin {

    @Inject(method = "func_78266_a", at = @At("HEAD"), require = 0)
    private void celtium$openDefaultGlyph(int ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.openWindow(false);
    }

    @Inject(method = "func_78266_a", at = @At("RETURN"), require = 0)
    private void celtium$closeDefaultGlyph(int ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.closeWindow();
    }

    @Inject(method = "func_78277_a", at = @At("HEAD"), require = 0)
    private void celtium$openUnicodeGlyph(char ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.openWindow(true);
    }

    @Inject(method = "func_78277_a", at = @At("RETURN"), require = 0)
    private void celtium$closeUnicodeGlyph(char ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.closeWindow();
    }
}
