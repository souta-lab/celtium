package org.soutalab.celtium.shader.transform;

import org.soutalab.celtium.shader.transform.parameter.Parameters;
import org.taumc.glsl.Transformer;

public class ComputeTransformer {

	public static void transform(Transformer transformer, Parameters parameters, int glslVersion) {
		CommonTransformer.transform(transformer, parameters, true, glslVersion);
	}
}
