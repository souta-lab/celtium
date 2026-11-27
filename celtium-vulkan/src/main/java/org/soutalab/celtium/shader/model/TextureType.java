package org.soutalab.celtium.shader.model;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL31;

import java.util.Optional;

public enum TextureType {
	TEXTURE_1D(GL11.GL_TEXTURE_1D),
	TEXTURE_2D(GL11.GL_TEXTURE_2D),
	TEXTURE_3D(GL12.GL_TEXTURE_3D),
	TEXTURE_RECTANGLE(GL31.GL_TEXTURE_RECTANGLE);

	private final int glType;

	TextureType(int glType) {
		this.glType = glType;
	}

	public static Optional<TextureType> fromString(String name) {
		try {
			return Optional.of(TextureType.valueOf(name));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	public int getGlType() {
		return glType;
	}

	// No upload method here by design: the original applied this type through the GL-side
	// RenderSystem texImage calls. Texture upload belongs to the executor's own texture
	// model (S5-b); this enum stays a pure pack-model description of the target kind.
}
