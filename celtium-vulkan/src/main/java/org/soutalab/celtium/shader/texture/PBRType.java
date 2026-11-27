/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 * Adapted natively for Celtium Vulkan 1.3 (MC/GLSM-free).
 */
package org.soutalab.celtium.shader.texture;

import org.jetbrains.annotations.Nullable;

public enum PBRType {
    NORMAL("_n", 0x7F7FFFFF),
    SPECULAR("_s", 0x00000000);

    private static final PBRType[] VALUES = values();

    private final String suffix;
    private final int defaultValue;

    PBRType(String suffix, int defaultValue) {
        this.suffix = suffix;
        this.defaultValue = defaultValue;
    }

    public String getSuffix() {
        return suffix;
    }

    public int getDefaultValue() {
        return defaultValue;
    }

    public String appendToFileLocation(String fileLocation) {
        int extensionIndex = fileLocation.lastIndexOf('.');
        if (extensionIndex != -1) {
            return fileLocation.substring(0, extensionIndex) + suffix + fileLocation.substring(extensionIndex);
        }
        return fileLocation + suffix;
    }

    @Nullable
    public static PBRType fromFileLocation(String fileLocation) {
        int extensionIndex = fileLocation.lastIndexOf('.');
        String base = extensionIndex != -1 ? fileLocation.substring(0, extensionIndex) : fileLocation;
        for (PBRType type : VALUES) {
            if (base.endsWith(type.suffix)) {
                return type;
            }
        }
        return null;
    }
}
