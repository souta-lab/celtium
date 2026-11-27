package org.soutalab.celtium.shader.shaderpack.option.menu;

import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.shaderpack.ShaderProperties;
import org.soutalab.celtium.shader.shaderpack.option.values.MutableOptionValues;
import org.soutalab.celtium.shader.shaderpack.option.values.OptionValues;

public abstract class OptionMenuOptionElement extends OptionMenuElement {
	public final boolean slider;
	public final OptionMenuContainer container;
	public final String optionId;

	private final OptionValues packAppliedValues;

	public OptionMenuOptionElement(String elementString, OptionMenuContainer container, ShaderProperties shaderProperties, OptionValues packAppliedValues) {
		this.slider = shaderProperties.getSliderOptions().contains(elementString);
		this.container = container;
		this.optionId = elementString;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return the {@link OptionValues} currently in use by the shader pack
	 */
	public OptionValues getAppliedOptionValues() {
		return packAppliedValues;
	}

	/**
	 * @param pendingValues values currently pending application, owned by the
	 * runtime's pack-options flow (the Iris facade queue in the original design;
	 * wiring it up belongs to the pack runtime, not this option model)
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues(java.util.Map<String, String> pendingValues) {
		MutableOptionValues values = getAppliedOptionValues().mutableCopy();
		values.addAll(pendingValues);

		return values;
	}
}
