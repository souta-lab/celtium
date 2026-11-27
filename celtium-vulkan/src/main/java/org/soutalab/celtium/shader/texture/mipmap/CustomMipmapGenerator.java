package org.soutalab.celtium.shader.texture.mipmap;

public interface CustomMipmapGenerator {
	PixelImage[] generateMipLevels(PixelImage image, int mipLevel);
}
