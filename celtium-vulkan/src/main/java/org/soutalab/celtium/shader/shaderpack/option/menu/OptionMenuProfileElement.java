package org.soutalab.celtium.shader.shaderpack.option.menu;

import org.soutalab.celtium.shader.ShaderLogger;
import org.soutalab.celtium.shader.shaderpack.option.OptionSet;
import org.soutalab.celtium.shader.shaderpack.option.ProfileSet;
import org.soutalab.celtium.shader.shaderpack.option.values.MutableOptionValues;
import org.soutalab.celtium.shader.shaderpack.option.values.OptionValues;

public class OptionMenuProfileElement extends OptionMenuElement {
	public final ProfileSet profiles;
	public final OptionSet options;

	private final OptionValues packAppliedValues;

	public OptionMenuProfileElement(ProfileSet profiles, OptionSet options, OptionValues packAppliedValues) {
		this.profiles = profiles;
		this.options = options;
		this.packAppliedValues = packAppliedValues;
	}

	/**
	 * @return an {@link OptionValues} that also contains values currently
	 * pending application.
	 */
	public OptionValues getPendingOptionValues() {
		MutableOptionValues values = packAppliedValues.mutableCopy();
		values.addAll(Iris.getShaderPackOptionQueue());

		return values;
	}
}
