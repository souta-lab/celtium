/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (no GLSM / Minecraft I18n dependency).
 */
package org.soutalab.celtium.shader.features;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public enum FeatureFlags {
    SEPARATE_HARDWARE_SAMPLERS(true, caps -> true),
    PER_BUFFER_BLENDING(true, PackCompilerCapabilities::supportsBufferBlending),
    COMPUTE_SHADERS(true, PackCompilerCapabilities::supportsCompute),
    ENTITY_TRANSLUCENT(true, caps -> true),
    BLOCK_EMISSION_ATTRIBUTE(true, caps -> true),
    CUSTOM_IMAGES(true, PackCompilerCapabilities::supportsImageLoadStore),
    SSBO(true, PackCompilerCapabilities::supportsSSBO),
    HIGHER_SHADOWCOLOR(true, caps -> true),
    TESSELLATION_SHADERS(true, PackCompilerCapabilities::supportsTesselation),
    REVERSED_CULLING(true, caps -> true),
    CAN_DISABLE_WEATHER(true, caps -> true),
    UNKNOWN(false, caps -> false);

    private final boolean engineSupported;
    private final Predicate<PackCompilerCapabilities> hardwareRequirement;

    FeatureFlags(boolean engineSupported, Predicate<PackCompilerCapabilities> hardwareRequirement) {
        this.engineSupported = engineSupported;
        this.hardwareRequirement = hardwareRequirement;
    }

    public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags) {
        return getInvalidStatus(invalidFeatureFlags, PackCompilerCapabilities.active());
    }

    public static String getInvalidStatus(List<FeatureFlags> invalidFeatureFlags, PackCompilerCapabilities caps) {
        boolean unsupportedHardware = false;
        boolean unsupportedEngine = false;
        for (FeatureFlags flag : invalidFeatureFlags) {
            unsupportedEngine |= !flag.engineSupported;
            unsupportedHardware |= !flag.hardwareRequirement.test(caps);
        }

        if (unsupportedEngine) {
            if (unsupportedHardware) {
                return "Unsupported by Celtium shader engine and hardware";
            }
            return "Unsupported by Celtium shader engine";
        } else if (unsupportedHardware) {
            return "Unsupported by Vulkan device hardware";
        } else {
            return null;
        }
    }

    public String getHumanReadableName() {
        String lower = name().replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        boolean cap = true;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == ' ') {
                cap = true;
                out.append(c);
            } else if (cap) {
                out.append(Character.toUpperCase(c));
                cap = false;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    public boolean isUsable() {
        return isUsable(PackCompilerCapabilities.active());
    }

    public boolean isUsable(PackCompilerCapabilities caps) {
        return engineSupported && hardwareRequirement.test(caps);
    }

    public static boolean isInvalid(String name) {
        return isInvalid(name, PackCompilerCapabilities.active());
    }

    public static boolean isInvalid(String name, PackCompilerCapabilities caps) {
        FeatureFlags flag = getValue(name);
        return flag == UNKNOWN || !flag.isUsable(caps);
    }

    public static FeatureFlags getValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        if (value.equalsIgnoreCase("TESSELATION_SHADERS")) {
            value = "TESSELLATION_SHADERS";
        }
        try {
            return FeatureFlags.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
