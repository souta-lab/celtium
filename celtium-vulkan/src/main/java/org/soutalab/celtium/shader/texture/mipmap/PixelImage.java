package org.soutalab.celtium.shader.texture.mipmap;

/**
 * A minimal CPU-side RGBA pixel buffer for mipmap generation, replacing the Angelica
 * {@code NativeImage} compat type the generators were absorbed with. That type extends
 * {@code BufferedImage} and drags in GL texture download machinery; the generators only
 * ever need dimensions and per-pixel access, so this class is exactly that surface.
 *
 * <p>Pixel ints use the ABGR packing the mipmap blend functions are written against:
 * alpha in bits 24-31, blue in bits 16-23, green in bits 8-15, red in bits 0-7.
 * {@link #combine}, the channel extractors, and the pixel accessors all share this one
 * packing, so a blend round-trip is channel-exact.
 */
public final class PixelImage {
	private final int width;
	private final int height;
	private final int[] pixels;

	/**
	 * @param useAlpha accepted for parity with the replaced type's constructor; every
	 *                 pixel always carries an alpha channel here, so it changes nothing.
	 */
	public PixelImage(int width, int height, boolean useAlpha) {
		if (width <= 0 || height <= 0) {
			throw new IllegalArgumentException("Image dimensions must be positive: " + width + "x" + height);
		}
		this.width = width;
		this.height = height;
		this.pixels = new int[width * height];
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public int getPixelRGBA(int x, int y) {
		return pixels[y * width + x];
	}

	public void setPixelRGBA(int x, int y, int rgba) {
		pixels[y * width + x] = rgba;
	}

	public static int combine(int alpha, int blue, int green, int red) {
		return (alpha & 255) << 24 | (blue & 255) << 16 | (green & 255) << 8 | (red & 255);
	}

	public static int getA(int pixel) {
		return pixel >> 24 & 255;
	}

	public static int getB(int pixel) {
		return pixel >> 16 & 255;
	}

	public static int getG(int pixel) {
		return pixel >> 8 & 255;
	}

	public static int getR(int pixel) {
		return pixel & 255;
	}
}
