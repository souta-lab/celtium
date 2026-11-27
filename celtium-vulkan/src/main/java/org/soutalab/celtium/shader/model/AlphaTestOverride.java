/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

import org.jetbrains.annotations.Nullable;

public class AlphaTestOverride {
    public static final AlphaTestOverride OFF = new AlphaTestOverride(null);

    @Nullable
    private final AlphaTest alphaTest;

    public AlphaTestOverride(@Nullable AlphaTest alphaTest) {
        this.alphaTest = alphaTest;
    }

    @Nullable
    public AlphaTest getAlphaTest() {
        return alphaTest;
    }

    public float getReference() {
        return alphaTest == null ? -1.0f : alphaTest.getReference();
    }
}
