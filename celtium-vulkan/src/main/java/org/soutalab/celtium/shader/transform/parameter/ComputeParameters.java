package org.soutalab.celtium.shader.transform.parameter;

import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.soutalab.celtium.shader.model.AlphaTest;
import org.soutalab.celtium.shader.model.TextureType;
import org.soutalab.celtium.shader.model.Tri;
import org.soutalab.celtium.shader.transform.Patch;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;

public class ComputeParameters extends TextureStageParameters {
	// WARNING: adding new fields requires updating hashCode and equals methods!

	public ComputeParameters(Patch patch, TextureStage stage, Object2ObjectMap<Tri<String, TextureType, TextureStage>, String> textureMap) {
		super(patch, stage, textureMap);
	}

	@Override
	public AlphaTest getAlphaTest() {
		return AlphaTest.ALWAYS;
	}

	// since this class has no fields, hashCode() and equals() are inherited from
	// TextureStageParameters
}
