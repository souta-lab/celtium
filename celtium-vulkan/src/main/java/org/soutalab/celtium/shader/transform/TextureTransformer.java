package org.soutalab.celtium.shader.transform;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;
import org.taumc.glsl.Transformer;
import org.taumc.glsl.grammar.GLSLLexer;

final class TextureTransformer {
	private TextureTransformer() {
	}

	static void transform(Transformer transformer, Parameters parameters) {
		final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap = parameters.getTextureMap();
		if (textureMap == null || textureMap.isEmpty()) {
			return;
		}

		final TextureStage stage = parameters.getTextureStage();
		textureMap.forEach((key, replacement) -> {
			if (key.third() != stage) {
				return;
			}

			final String name = key.first();
			final int type = transformer.findType(name);
			if (type != 0 && isTypeValid(key.second(), type)) {
				transformer.rename(name, replacement);
			}
		});
	}

	private static boolean isTypeValid(TextureType expectedType, int extractedType) {
		return switch (expectedType) {
			case TEXTURE_1D -> extractedType == GLSLLexer.SAMPLER1D ||
				extractedType == GLSLLexer.ISAMPLER1D ||
				extractedType == GLSLLexer.USAMPLER1D;
			case TEXTURE_RECTANGLE -> extractedType == GLSLLexer.SAMPLER2DRECT ||
				extractedType == GLSLLexer.ISAMPLER2DRECT ||
				extractedType == GLSLLexer.USAMPLER2DRECT;
			case TEXTURE_2D -> extractedType == GLSLLexer.SAMPLER2D ||
				extractedType == GLSLLexer.ISAMPLER2D ||
				extractedType == GLSLLexer.USAMPLER2D;
			case TEXTURE_3D -> extractedType == GLSLLexer.SAMPLER3D ||
				extractedType == GLSLLexer.ISAMPLER3D ||
				extractedType == GLSLLexer.USAMPLER3D;
		};
	}
}
