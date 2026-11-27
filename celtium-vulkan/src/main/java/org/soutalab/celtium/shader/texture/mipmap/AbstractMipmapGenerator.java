package org.soutalab.celtium.shader.texture.mipmap;



public abstract class AbstractMipmapGenerator implements CustomMipmapGenerator {
	@Override
	public PixelImage[] generateMipLevels(PixelImage image, int mipLevel) {
		PixelImage[] images = new PixelImage[mipLevel + 1];
		images[0] = image;
		if (mipLevel > 0) {
			for (int level = 1; level <= mipLevel; ++level) {
				PixelImage prevMipmap = images[level - 1];
				PixelImage mipmap = new PixelImage(prevMipmap.getWidth() >> 1, prevMipmap.getHeight() >> 1, false);
				int width = mipmap.getWidth();
				int height = mipmap.getHeight();
				for (int x = 0; x < width; ++x) {
					for (int y = 0; y < height; ++y) {
						mipmap.setPixelRGBA(x, y, blend(
								prevMipmap.getPixelRGBA(x * 2 + 0, y * 2 + 0),
								prevMipmap.getPixelRGBA(x * 2 + 1, y * 2 + 0),
								prevMipmap.getPixelRGBA(x * 2 + 0, y * 2 + 1),
								prevMipmap.getPixelRGBA(x * 2 + 1, y * 2 + 1)
						));
					}
				}
				images[level] = mipmap;
			}
		}
		return images;
	}

	public abstract int blend(int c0, int c1, int c2, int c3);
}
