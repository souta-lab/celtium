package org.soutalab.celtium.shader.shaderpack.option.menu;

import org.soutalab.celtium.shader.shaderpack.ShaderProperties;
import org.soutalab.celtium.shader.shaderpack.option.BooleanOption;
import org.soutalab.celtium.shader.shaderpack.option.values.OptionValues;

public class OptionMenuBooleanOptionElement extends OptionMenuOptionElement {
	public final BooleanOption option;

	public OptionMenuBooleanOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues values, BooleanOption option) {
		super(elementString, container, shaderProperties, values);
		this.option = option;
	}
}
