package org.soutalab.celtium.shader.texture.mipmap;

import org.soutalab.celtium.shader.texture.PBRType;

import javax.annotation.Nullable;

public interface CustomMipmapGenerator {
	PixelImage[] generateMipLevels(PixelImage image, int mipLevel);

	/**
	 * Supplies the mipmap generator for one sprite of an atlas. Revived in S5-b against
	 * Celtium's own texture model: where the supplier's shape keyed this on its PBR loader's
	 * sprite-info type (a surface Celtium does not carry), the native contract needs only the
	 * sprite's PBR type and the atlas dimensions - the facts a generator choice can depend on.
	 */
	interface Provider {
		@Nullable
		CustomMipmapGenerator getMipmapGenerator(PBRType pbrType, int atlasWidth, int atlasHeight);
	}
}
