package org.soutalab.celtium.shader.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.soutalab.celtium.shader.model.AlphaTest;
import org.soutalab.celtium.shader.model.ShaderType;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.transform.Patch;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;

public abstract class Parameters {
	public final Patch patch;
	private final Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap;
	public ShaderType type;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public Parameters(Patch patch, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		this.patch = patch;
		this.textureMap = textureMap;
	}

	public AlphaTest getAlphaTest() {
		return AlphaTest.ALWAYS;
	}

	public abstract TextureStage getTextureStage();

	public Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> getTextureMap() {
		return textureMap;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((patch == null) ? 0 : patch.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
		result = prime * result + ((textureMap == null) ? 0 : textureMap.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final Parameters other = (Parameters) obj;
		if (patch != other.patch)
			return false;
		if (type != other.type)
			return false;
		if (textureMap == null) {
			return other.textureMap == null;
		} else return textureMap.equals(other.textureMap);
	}
}
