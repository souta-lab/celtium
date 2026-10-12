package org.taumc.celeritas.mixin.core;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.ResourceLocation;
import org.taumc.celeritas.impl.font.CeltiumFontGlyphCapture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /** The sheet's own width table, the pen position and the sheet's location: the four inputs the fast path needs. */
    @Shadow
    @Final
    private int[] charWidth;
    /** The unicode sheet's per-character left/right edge table {@code renderUnicodeChar} indexes. */
    @Shadow
    @Final
    private byte[] glyphWidth;
    @Shadow
    private float posX;
    @Shadow
    private float posY;
    @Shadow
    @Final
    private ResourceLocation locationFontTexture;
    @Shadow
    protected abstract void bindTexture(ResourceLocation location);

    @Shadow
    private ResourceLocation getUnicodePageLocation(int page) {
        // Shadowed private method: the body never runs, Mixin generates an invoker.
        throw new UnsupportedOperationException();
    }

    /**
     * The GL name the sheet is bound under, cached after the first bind: vanilla re-binds it for every glyph, and the
     * fast path only needs to when the unit no longer holds it (a unicode page between two ASCII glyphs of one string,
     * a mod's own texture, the light map's unit dance). Zero means "not resolved yet".
     */
    private int celtium$fontTextureName;

    /**
     * The same binding cache for {@code unicode_page_00.png}, whose glyphs the unicode path draws every ASCII
     * character with when {@code unicodeFlag} routes the whole string through {@code renderUnicodeChar}.
     */
    private int celtium$unicodeTextureName;

    /**
     * The default-page glyph: vanilla's body, or the Celtium path that writes the same quad straight into the capture.
     * <p>
     * The fast path is taken only when the capture is armed for GL-less drawing and the property is on; otherwise this
     * injection is the window bracket it always was. The advance, the four vertices and their texture coordinates are
     * vanilla's ({@code renderDefaultChar}, {@code FontRenderer.java:230-249}), and the submit is the capture's own -
     * format 2, stride 24, mode 5, the coalesced run with its strip separators - so nothing downstream changes. What
     * disappears is the per-glyph {@code TextureManager.bindTexture} chain and the nine {@code GlStateManager} wrapper
     * calls, which are the measured 3.15 µs/glyph of vanilla glyph body.
     */
    /**
     * {@return the texture bound to TEXTURE_2D on the active unit}, asking the installed GL stub directly rather than
     * paying a trampoline round trip for a field it already holds - the per-string bind check below used to pay two
     * FFI queries per drawString, which the fn census measured as the largest share of all glGetIntegerv arrivals.
     * A real-GL build still gets the answer from GL, so the fallback is the call itself.
     */
    private static int celtium$boundTexture2D() {
        int stub = org.soutalab.celtium.vulkan.gl.CeltiumGlStub.celtium$getInteger(0x8069);
        return stub != Integer.MIN_VALUE ? stub
                : org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL11.GL_TEXTURE_BINDING_2D);
    }

    @Inject(method = "func_78266_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$openDefaultGlyph(int ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.beginGlyph();
        if (CeltiumFontGlyphCapture.fastPathAvailable()) {
            if (celtium$fontTextureName == 0 || celtium$boundTexture2D() != celtium$fontTextureName) {
                bindTexture(this.locationFontTexture);
                celtium$fontTextureName = celtium$boundTexture2D();
                CeltiumFontGlyphCapture.noteFontTextureRebind();
            }
            float advance = CeltiumFontGlyphCapture.fastDefaultGlyph(this.charWidth, this.posX, this.posY, ch, italic);
            if (advance >= 0.0F) {
                info.setReturnValue(Float.valueOf(advance));
                return;
            }
        }
        CeltiumFontGlyphCapture.openWindow(false);
    }

    @Inject(method = "func_78266_a", at = @At("RETURN"), require = 0)
    private void celtium$closeDefaultGlyph(int ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.closeWindow();
        CeltiumFontGlyphCapture.endGlyph();
    }

    @Inject(method = "func_78277_a", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$openUnicodeGlyph(char ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.beginGlyph();
        if (CeltiumFontGlyphCapture.fastPathAvailable() && ch < 256) {
            // unicodeFlag sends every ASCII char through here too; page 0 is the sheet the fast path needs.
            if (celtium$unicodeTextureName == 0 || celtium$boundTexture2D() != celtium$unicodeTextureName) {
                bindTexture(getUnicodePageLocation(0));
                celtium$unicodeTextureName = celtium$boundTexture2D();
                CeltiumFontGlyphCapture.noteFontTextureRebind();
            }
            float advance = CeltiumFontGlyphCapture
                    .fastUnicodeGlyph(this.glyphWidth, this.posX, this.posY, ch, italic);
            if (advance >= 0.0F) {
                info.setReturnValue(Float.valueOf(advance));
                return;
            }
        }
        CeltiumFontGlyphCapture.openWindow(true);
    }

    @Inject(method = "func_78277_a", at = @At("RETURN"), require = 0)
    private void celtium$closeUnicodeGlyph(char ch, boolean italic, CallbackInfoReturnable<Float> info) {
        CeltiumFontGlyphCapture.closeWindow();
        CeltiumFontGlyphCapture.endGlyph();
    }

    /**
     * The string bracket: {@code FontRenderer.drawString(String, float, float, int, boolean)}, SRG
     * {@code func_175065_a}, resolved from the SRG table rather than guessed. It wraps the shadow pass, the per-character
     * loop and the per-glyph calls, so per frame {@code stringMillis - glyphMillis} is the caller's own work and
     * {@code glyphMillis - (windowMillis + hookMillis + submitMillis)} is the vanilla glyph body - the split the F3
     * overlay's 135 ms/frame needed and that no aggregate could give.
     */
    @Inject(method = "func_175065_a", at = @At("HEAD"), require = 0)
    private void celtium$beginString(String text, float x, float y, int color, boolean dropShadow,
            CallbackInfoReturnable<Integer> info) {
        CeltiumFontGlyphCapture.beginString();
    }

    @Inject(method = "func_175065_a", at = @At("RETURN"), require = 0)
    private void celtium$endString(String text, float x, float y, int color, boolean dropShadow,
            CallbackInfoReturnable<Integer> info) {
        CeltiumFontGlyphCapture.endString();
    }

    /**
     * The innermost bracket: {@code FontRenderer.renderString(String, float, float, int, boolean)}, SRG
     * {@code func_180455_b} - the method that holds the per-character loop, reached by {@code renderStringAligned} and
     * the styled paths as well as by {@code drawString}. The outer {@code func_175065_a} bracket measured
     * {@code stringMillis - glyphMillis} at ~0 because those callers bypass it, so this is the scope that makes the
     * caller's own work separable: {@code renderStringMillis - glyphMillis}.
     */
    @Inject(method = "func_180455_b", at = @At("HEAD"), require = 0)
    private void celtium$beginRenderString(String text, float x, float y, int color, boolean dropShadow,
            CallbackInfoReturnable<Integer> info) {
        CeltiumFontGlyphCapture.beginRenderString();
    }

    @Inject(method = "func_180455_b", at = @At("RETURN"), require = 0)
    private void celtium$endRenderString(String text, float x, float y, int color, boolean dropShadow,
            CallbackInfoReturnable<Integer> info) {
        CeltiumFontGlyphCapture.endRenderString();
    }

    /**
     * The per-character {@code String.indexOf} against the font-page table: every glyph pays a linear scan of a
     * ~256-char constant in {@code renderChar} ({@code func_181559_a}), {@code renderStringAtPos}
     * ({@code func_78255_a}) and {@code getCharWidth} ({@code func_78263_a}), measured at ~7% of the F3 leg's warm
     * samples between {@code String.indexOf} and {@code StringUTF16.indexOf}. The redirect answers the same lookup
     * from a direct-index map built once per constant. {@code require = 0} keeps a build whose methods differ alive.
     */
    @Redirect(method = "func_181559_a", at = @At(value = "INVOKE", target = "Ljava/lang/String;indexOf(I)I"),
            require = 0)
    private int celtium$tableCharIndexRenderChar(String table, int ch) {
        return CeltiumFontGlyphCapture.tableCharIndex(table, ch);
    }

    @Redirect(method = "func_78255_a", at = @At(value = "INVOKE", target = "Ljava/lang/String;indexOf(I)I"),
            require = 0)
    private int celtium$tableCharIndexRenderStringAtPos(String table, int ch) {
        return CeltiumFontGlyphCapture.tableCharIndex(table, ch);
    }

    @Redirect(method = "func_78263_a", at = @At(value = "INVOKE", target = "Ljava/lang/String;indexOf(I)I"),
            require = 0)
    private int celtium$tableCharIndexGetCharWidth(String table, int ch) {
        return CeltiumFontGlyphCapture.tableCharIndex(table, ch);
    }
}
