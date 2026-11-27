package org.soutalab.celtium.shader.shaderpack.option.menu;

import org.soutalab.celtium.shader.shaderpack.ShaderProperties;
import org.soutalab.celtium.shader.shaderpack.option.StringOption;
import org.soutalab.celtium.shader.shaderpack.option.values.OptionValues;

public class OptionMenuStringOptionElement extends OptionMenuOptionElement {
	public final StringOption option;

	public OptionMenuStringOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, StringOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
