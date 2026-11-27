package org.soutalab.celtium.shader.transform.parameter;

import org.soutalab.celtium.shader.transform.ShaderInstancing;
import org.soutalab.celtium.shader.model.InputAvailability;
import org.soutalab.celtium.shader.transform.Patch;
import org.soutalab.celtium.shader.shaderpack.texture.TextureStage;

public class AttributeParameters extends Parameters {
	public final boolean hasGeometry;
	/**
	 * Whether the program has a tessellation stage. The entity id and overlay varyings are passed on
	 * with a different name from the evaluation shader than from the vertex shader, so the patcher
	 * has to know which chain to continue.
	 */
	public final boolean hasTesselation;
	public final InputAvailability inputs;
	/**
	 * Per-instance attribute layout the vertex stage reads instead of the per-vertex ones. It is part
	 * of {@link #hashCode()} and {@link #equals(Object)} so the transform cache keeps one result per
	 * layout instead of returning the same shader for {@code NONE}, {@code TEMPLATE} and {@code CUBE}.
	 */
	public final ShaderInstancing instancing;
	/** Selects a vertex/fragment pair that clips fragments against the enabled planes. */
	public final boolean clipDiscard;

	public AttributeParameters(Patch patch, boolean hasGeometry, InputAvailability inputs) {
		this(patch, hasGeometry, false, inputs, ShaderInstancing.NONE);
	}

	public AttributeParameters(Patch patch, boolean hasGeometry, InputAvailability inputs, ShaderInstancing instancing) {
		this(patch, hasGeometry, false, inputs, instancing);
	}

	public AttributeParameters(Patch patch, boolean hasGeometry, boolean hasTesselation, InputAvailability inputs, ShaderInstancing instancing) {
		this(patch, hasGeometry, hasTesselation, inputs, instancing, false);
	}

	public AttributeParameters(Patch patch, boolean hasGeometry, boolean hasTesselation, InputAvailability inputs, ShaderInstancing instancing, boolean clipDiscard) {
		super(patch, null);
		this.hasGeometry = hasGeometry;
		this.hasTesselation = hasTesselation;
		this.inputs = inputs;
		this.instancing = instancing;
		this.clipDiscard = clipDiscard;
	}

	@Override
	public TextureStage getTextureStage() {
		return TextureStage.GBUFFERS_AND_SHADOW;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + (hasGeometry ? 1231 : 1237);
		result = prime * result + (hasTesselation ? 1231 : 1237);
		result = prime * result + instancing.ordinal();
		result = prime * result + Boolean.hashCode(clipDiscard);
		result = prime * result + ((inputs == null) ? 0 : inputs.hashCode());
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
		AttributeParameters other = (AttributeParameters) obj;
		if (hasGeometry != other.hasGeometry)
			return false;
		if (hasTesselation != other.hasTesselation)
			return false;
		if (instancing != other.instancing || clipDiscard != other.clipDiscard)
			return false;
		if (inputs == null) {
			if (other.inputs != null)
				return false;
		} else if (!inputs.equals(other.inputs))
			return false;
		return true;
	}
}
