package org.soutalab.celtium.shader.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.soutalab.celtium.shader.model.AlphaTest;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.transform.Patch;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;

public class TextureStageParameters extends Parameters {
	private final TextureStage stage;
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public TextureStageParameters(Patch patch, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, textureMap);
		this.stage = stage;
	}

	@Override
	public TextureStage getTextureStage() {
		return stage;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((stage == null) ? 0 : stage.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		TextureStageParameters other = (TextureStageParameters) obj;
		return stage == other.stage;
	}

	@Override
	public AlphaTest getAlphaTest() {
		return AlphaTest.ALWAYS;
	}
}
