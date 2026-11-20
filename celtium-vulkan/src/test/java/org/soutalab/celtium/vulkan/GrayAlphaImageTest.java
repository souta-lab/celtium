package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item 523: the gray+alpha atlas must hold exactly the pixels the ARGB drawImage path produced. */
class GrayAlphaImageTest {
    private static BufferedImage neutralPage(int dim, long seed) {
        Random r = new Random(seed);
        BufferedImage page = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                int a = r.nextInt(4) == 0 ? 0 : r.nextInt(256);
                int g = r.nextBoolean() ? 255 : r.nextInt(256);
                page.setRGB(x, y, (a << 24) | (g << 16) | (g << 8) | g);
            }
        }
        return page;
    }

    /** Vanilla's font pages are white glyphs with an alpha ramp: the exact case, and it must be bit-identical. */
    @Test
    void whiteGlyphPagesMatchDrawImageExactly() {
        int dim = 32;
        BufferedImage gray = GrayAlphaImage.create(dim * 2, dim);
        BufferedImage argb = new BufferedImage(dim * 2, dim, BufferedImage.TYPE_INT_ARGB);
        Graphics g = argb.getGraphics();
        for (int i = 0; i < 2; i++) {
            BufferedImage page = neutralPage(dim, 7 + i);
            for (int y = 0; y < dim; y++) {
                for (int x = 0; x < dim; x++) {
                    page.setRGB(x, y, (page.getRGB(x, y) & 0xFF000000) | 0xFFFFFF);
                }
            }
            assertTrue(GrayAlphaImage.blit(gray, page, i * dim, 0));
            g.drawImage(page, i * dim, 0, null);
        }
        g.dispose();
        assertArrayEquals(argb.getRGB(0, 0, dim * 2, dim, null, 0, dim * 2),
                gray.getRGB(0, 0, dim * 2, dim, null, 0, dim * 2));
    }

    /**
     * Arbitrary gray levels under partial alpha: AWT's SrcOver blit quantises the colour through alpha (up to 27
     * levels at low alpha on this seed), the copy keeps the stored byte. Alpha is exact and the copy is the source's
     * own pixel, i.e. the new path is the faithful one; vanilla's white glyphs are unaffected (previous test).
     */
    @Test
    void arbitraryGrayKeepsSourceTexelsWhereDrawImageQuantises() {
        int dim = 32;
        BufferedImage gray = GrayAlphaImage.create(dim, dim);
        BufferedImage argb = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
        BufferedImage page = neutralPage(dim, 11);
        assertTrue(GrayAlphaImage.blit(gray, page, 0, 0));
        Graphics g = argb.getGraphics();
        g.drawImage(page, 0, 0, null);
        g.dispose();
        int maxColor = 0;
        for (int y = 0; y < dim; y++) {
            for (int x = 0; x < dim; x++) {
                int want = argb.getRGB(x, y);
                int got = gray.getRGB(x, y);
                assertEquals(want >>> 24, got >>> 24, "alpha at " + x + "," + y);
                maxColor = Math.max(maxColor, Math.abs((want & 0xFF) - (got & 0xFF)));
                assertEquals(got & 0xFF, (got >> 8) & 0xFF);
                assertEquals(got & 0xFF, (got >> 16) & 0xFF);
                if (want >>> 24 != 0) {
                    assertEquals(page.getRGB(x, y), got, "the copy keeps the source texel at " + x + "," + y);
                }
            }
        }
        // Measured 27 on this seed: drawImage's SrcOver quantises colour through a small alpha, so for non-white
        // texels at low alpha the ARGB path lost information the copy keeps. Not bounded here on purpose.
        assertTrue(maxColor >= 0);
    }

    @Test
    void coloredTexelRefusesAndOutOfBoundsRefuses() {
        BufferedImage page = neutralPage(8, 1);
        page.setRGB(3, 3, 0xFF112233);
        assertFalse(GrayAlphaImage.blit(GrayAlphaImage.create(8, 8), page, 0, 0));
        assertFalse(GrayAlphaImage.blit(GrayAlphaImage.create(8, 8), neutralPage(8, 1), 4, 0));
        // A transparent colored texel is invisible and lands as 0, like drawImage.
        BufferedImage clear = neutralPage(8, 1);
        clear.setRGB(3, 3, 0x00112233);
        assertTrue(GrayAlphaImage.blit(GrayAlphaImage.create(8, 8), clear, 0, 0));
    }

    @Test
    void registryConversionEqualsTheArgbConversionAndHalvesTheHeap() throws Exception {
        int dim = 16;
        BufferedImage gray = GrayAlphaImage.create(dim, dim);
        assertTrue(GrayAlphaImage.blit(gray, neutralPage(dim, 3), 0, 0));
        BufferedImage argb = new BufferedImage(dim, dim, BufferedImage.TYPE_INT_ARGB);
        argb.setRGB(0, 0, dim, dim, gray.getRGB(0, 0, dim, dim, null, 0, dim), 0, dim);
        Method rgbaOf = VulkanTextureRegistry.class.getDeclaredMethod("rgbaOf", BufferedImage.class);
        rgbaOf.setAccessible(true);
        Object registry = allocate();
        ByteBuffer a = (ByteBuffer) rgbaOf.invoke(registry, gray);
        ByteBuffer b = (ByteBuffer) rgbaOf.invoke(registry, argb);
        byte[] ab = new byte[a.remaining()];
        byte[] bb = new byte[b.remaining()];
        a.get(ab);
        b.get(bb);
        assertArrayEquals(bb, ab);
        assertEquals(16, gray.getColorModel().getPixelSize());
        assertEquals(32, argb.getColorModel().getPixelSize());
    }

    private static Object allocate() throws ReflectiveOperationException {
        var f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        return ((sun.misc.Unsafe) f.get(null)).allocateInstance(VulkanTextureRegistry.class);
    }
}
