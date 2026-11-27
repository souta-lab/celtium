package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DP-27: a character outside the ASCII font page is drawn and measured as '?', not skipped silently.
 * <p>
 * The font is synthetic: a 128x128 page (16x16 cells of 8x8) where only the '?' cell carries ink, so
 * its width is the full-cell width and every other glyph is one unit. The substitution is checked on
 * both sides of the pair that has to agree - {@code stringWidth} (what centres the progress text) and
 * {@code drawString} (what draws it, and where the substitution count lives).
 */
class NativeLoadingScreenNonAsciiTest {

    private static BufferedImage fontPage() {
        BufferedImage page = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        // '?' is character 63: column 15, row 3 of the grid.
        for (int y = 3 * 8; y < 4 * 8; y++) {
            for (int x = 15 * 8; x < 16 * 8; x++) {
                page.setRGB(x, y, 0xFF000000);
            }
        }
        return page;
    }

    private static NativeLoadingScreen screenWithFont() throws Exception {
        NativeLoadingScreen screen = new NativeLoadingScreen(null);
        Method readFont = NativeLoadingScreen.class.getDeclaredMethod("readFont", BufferedImage.class);
        readFont.setAccessible(true);
        readFont.invoke(screen, fontPage());
        return screen;
    }

    private static int stringWidth(NativeLoadingScreen screen, String text) throws Exception {
        Method width = NativeLoadingScreen.class.getDeclaredMethod("stringWidth", String.class);
        width.setAccessible(true);
        return ((Integer) width.invoke(screen, text)).intValue();
    }

    /** A non-ASCII character measures exactly what the '?' substitution draws. */
    @Test
    void nonAsciiMeasuresAsQuestionMark() throws Exception {
        NativeLoadingScreen screen = screenWithFont();
        int question = stringWidth(screen, "?");
        assertTrue(question > 1, "the inked '?' cell is wide, width=" + question);
        assertEquals(question, stringWidth(screen, "あ"),
                "a non-ASCII character is measured as '?' rather than skipped");
        assertEquals(question + stringWidth(screen, "A"), stringWidth(screen, "Aあ"),
                "the substitution composes with ASCII text");
    }

    /** Drawing a non-ASCII character counts one substitution per character and does not throw. */
    @Test
    void nonAsciiDrawsAsQuestionMarkAndCounts() throws Exception {
        NativeLoadingScreen screen = screenWithFont();
        BufferedImage canvas = new BufferedImage(64, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        long before = NativeLoadingScreen.nonAsciiSubstitutions();
        try {
            Method draw = NativeLoadingScreen.class.getDeclaredMethod(
                    "drawString", Graphics2D.class, String.class, int.class, int.class);
            draw.setAccessible(true);
            draw.invoke(screen, g, "aあbい", 0, 0);
        } finally {
            g.dispose();
        }
        assertEquals(before + 2, NativeLoadingScreen.nonAsciiSubstitutions(),
                "one counted substitution per non-ASCII character");
    }
}
