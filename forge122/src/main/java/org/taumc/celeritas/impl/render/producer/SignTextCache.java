package org.taumc.celeritas.impl.render.producer;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiUtilRenderComponents;
import net.minecraft.util.text.ITextComponent;

import java.util.List;
import java.util.Map;
import java.util.IdentityHashMap;

/**
 * The sign renderer's per-line text work, kept between frames.
 * <p>
 * Measured need (warm JFR, AMD, Xaero instance): {@code TileEntitySignRenderer.render} re-split every line to the sign's
 * width ({@code GuiUtilRenderComponents.splitText}, which measures each character) and rebuilt its formatted string
 * ({@code getFormattedText}, an iterator walk over the component tree) on every frame - about a quarter of the signs'
 * cost, for text that only changes when the sign's components are replaced. Both results depend only on the component
 * and the font's character widths, so they are cached by component and the cache is dropped when the font reloads.
 * <p>
 * Keys are the component objects, by identity: a sign's components are replaced, not mutated, when its text changes,
 * so the same object always gives the same answer. Value keys are not usable - vanilla's {@code Style.hashCode} throws
 * for a style without a colour (measured: a sign leg crashed in {@code WeakHashMap.get}). The maps are bounded instead of
 * weak: past {@link #LIMIT} entries they are cleared, which also lets go of components no sign holds any more.
 * Render thread only.
 */
public final class SignTextCache {
    private static final int LIMIT = 4096;
    private static final Map<ITextComponent, List<ITextComponent>> SPLIT = new IdentityHashMap<>();
    private static final Map<ITextComponent, String> FORMATTED = new IdentityHashMap<>();
    private static long hits;
    private static long misses;
    private static boolean reported;

    private SignTextCache() {
    }

    /** {@code GuiUtilRenderComponents.splitText}, answered from the cache when the component was split before. */
    public static List<ITextComponent> split(ITextComponent component, int width, FontRenderer font,
            boolean trimSpace, boolean forceTextColor) {
        if (component == null) {
            return GuiUtilRenderComponents.splitText(component, width, font, trimSpace, forceTextColor);
        }
        List<ITextComponent> lines = SPLIT.get(component);
        if (lines == null) {
            misses++;
            lines = GuiUtilRenderComponents.splitText(component, width, font, trimSpace, forceTextColor);
            if (SPLIT.size() >= LIMIT) {
                SPLIT.clear();
            }
            SPLIT.put(component, lines);
        } else {
            hits++;
        }
        if (!reported && hits + misses >= 50_000L) {
            // Once, so a leg can tell the redirect applied (the mixin is optional) and how often it answered.
            reported = true;
            System.out.println("[Celtium] CELTIUM_SIGN_TEXT_CACHE hits=" + hits + " misses=" + misses
                    + " entries=" + SPLIT.size());
        }
        return lines;
    }

    /** {@code component.getFormattedText()}, answered from the cache when it was built before. */
    public static String formatted(ITextComponent component) {
        String text = FORMATTED.get(component);
        if (text == null) {
            text = component.getFormattedText();
            if (FORMATTED.size() >= LIMIT) {
                FORMATTED.clear();
            }
            FORMATTED.put(component, text);
        }
        return text;
    }

    /** Drops every cached answer: the font's widths (and so the line splits) may have changed. */
    public static void invalidate() {
        SPLIT.clear();
        FORMATTED.clear();
    }
}
