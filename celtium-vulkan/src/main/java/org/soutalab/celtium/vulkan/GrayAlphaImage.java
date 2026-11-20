package org.soutalab.celtium.vulkan;

import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ComponentColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * A two-byte-per-pixel (gray, alpha) {@link BufferedImage} for textures whose every texel is neutral (r == g == b),
 * such as the font atlas (item 523 / 361-D): half the heap of {@code TYPE_INT_ARGB} for the same pixels.
 * <p>
 * The stock gray colour space is <em>linear</em>, so {@code getRGB} on a stock gray+alpha image gamma-converts mid
 * values. This model returns the stored byte as the sRGB value of all three channels instead, which is exactly what
 * the ARGB image it replaces held; {@link #isGrayAlpha} is what lets the announced-image conversion read the bytes
 * directly under that same contract.
 */
public final class GrayAlphaImage {
    private static final class Model extends ComponentColorModel {
        Model() {
            super(ColorSpace.getInstance(ColorSpace.CS_GRAY), true, false, TRANSLUCENT, DataBuffer.TYPE_BYTE);
        }

        @Override
        public boolean isCompatibleRaster(Raster raster) {
            return raster.getNumBands() == 2 && raster.getTransferType() == DataBuffer.TYPE_BYTE;
        }

        private static int gray(Object inData) {
            return ((byte[]) inData)[0] & 0xFF;
        }

        private static int alpha(Object inData) {
            return ((byte[]) inData)[1] & 0xFF;
        }

        @Override
        public int getRGB(Object inData) {
            int g = gray(inData);
            return (alpha(inData) << 24) | (g << 16) | (g << 8) | g;
        }

        @Override
        public int getRed(Object inData) {
            return gray(inData);
        }

        @Override
        public int getGreen(Object inData) {
            return gray(inData);
        }

        @Override
        public int getBlue(Object inData) {
            return gray(inData);
        }

        @Override
        public int getAlpha(Object inData) {
            return alpha(inData);
        }

        @Override
        public Object getDataElements(int rgb, Object pixel) {
            byte[] out = pixel instanceof byte[] b && b.length >= 2 ? b : new byte[2];
            out[0] = (byte) (rgb & 0xFF);
            out[1] = (byte) (rgb >>> 24);
            return out;
        }
    }

    private static final Model MODEL = new Model();

    private GrayAlphaImage() {
    }

    /** {@return a blank (transparent black) w x h image in this format} */
    public static BufferedImage create(int w, int h) {
        WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, w, h, 2 * w, 2,
                new int[] {0, 1}, null);
        return new BufferedImage(MODEL, raster, false, null);
    }

    /** {@return whether {@code image} is one this class made} */
    public static boolean isGrayAlpha(BufferedImage image) {
        return image.getColorModel() instanceof Model && image.getRaster().getDataBuffer() instanceof DataBufferByte;
    }

    /** {@return the interleaved (gray, alpha) bytes of an image {@link #isGrayAlpha} accepted} */
    public static byte[] data(BufferedImage image) {
        return ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
    }

    /** Whether the raster is the plain 0-offset, full-extent layout {@link #data} can be walked linearly. */
    public static boolean isPlain(BufferedImage image) {
        Raster r = image.getRaster();
        return r.getMinX() == 0 && r.getMinY() == 0 && data(image).length >= 2 * image.getWidth() * image.getHeight()
                && r.getSampleModel().getWidth() == image.getWidth();
    }
}
