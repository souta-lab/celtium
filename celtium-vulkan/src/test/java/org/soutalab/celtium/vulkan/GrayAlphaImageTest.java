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

    @Test
    void blitMatchesDrawImageOnATransparentArgbCanvas() {
        int dim = 32;
        BufferedImage gray = GrayAlphaImage.create(dim * 2, dim);
        BufferedImage argb = new BufferedImage(dim * 2, dim, BufferedImage.TYPE_INT_ARGB);
        Graphics g = argb.getGraphics();
        for (int i = 0; i < 2; i++) {
            BufferedImage page = neutralPage(dim, 7 + i);
            assertTrue(GrayAlphaImage.blit(gray, page, i * dim, 0));
            g.drawImage(page, i * dim, 0, null);
        }
        g.dispose();
        int[] want = argb.getRGB(0, 0, dim * 2, dim, null, 0, dim * 2);
        int[] got = gray.getRGB(0, 0, dim * 2, dim, null, 0, dim * 2);
        int off = 0;
        for (int i = 0; i < want.length; i++) {
            if (want[i] != got[i]) {
                off++;
            }
        }
        assertEquals(0, off, "pixels differing from the ARGB path");
        assertEquals(want[5], gray.getRGB(5, 0));
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
