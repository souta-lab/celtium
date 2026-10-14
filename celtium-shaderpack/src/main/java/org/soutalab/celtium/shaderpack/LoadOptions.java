package org.soutalab.celtium.shaderpack;

import java.util.Map;

/**
 * Knobs for one pack load.
 *
 * @param limits           resource bounds; {@link PackLimits#DEFAULT} normally
 * @param minecraftVersion numeric MC version exposed to conditions as
 *                         {@code MC_VERSION} (11202 for 1.12.2)
 * @param profile          {@code profile.NAME} to apply to the option set, or
 *                         null for the pack-as-shipped defaults
 * @param optionOverrides  explicit option overrides ({@code NAME->value},
 *                         empty-string value means "defined, no value")
 * @param extraDefines     additional builtin-style defines for all
 *                         preprocessing (e.g. MC_GL_VERSION once the native ABI
 *                         contract decides what to report)
 */
public record LoadOptions(
        PackLimits limits,
        int minecraftVersion,
        String profile,
        Map<String, String> optionOverrides,
        Map<String, String> extraDefines) {

    public LoadOptions {
        limits = limits == null ? PackLimits.DEFAULT : limits;
        optionOverrides = optionOverrides == null ? Map.of() : Map.copyOf(optionOverrides);
        extraDefines = extraDefines == null ? Map.of() : Map.copyOf(extraDefines);
    }

    public static LoadOptions defaults() {
        return new LoadOptions(PackLimits.DEFAULT, 11202, null, Map.of(), Map.of());
    }

    public LoadOptions withProfile(String profileName) {
        return new LoadOptions(limits, minecraftVersion, profileName, optionOverrides, extraDefines);
    }
}
