package org.soutalab.celtium.shader.shaderpack.option.menu;

import org.soutalab.celtium.shader.shaderpack.ShaderProperties;
import org.soutalab.celtium.shader.shaderpack.option.ShaderPackOptions;

import java.util.List;
import java.util.Optional;

public class OptionMenuMainElementScreen extends OptionMenuElementScreen {
	public OptionMenuMainElementScreen(OptionMenuContainer container, ShaderProperties shaderProperties, ShaderPackOptions shaderPackOptions, List<String> elementStrings, Optional<Integer> columnCount) {
		super(container, shaderProperties, shaderPackOptions, elementStrings, columnCount);
	}
}
