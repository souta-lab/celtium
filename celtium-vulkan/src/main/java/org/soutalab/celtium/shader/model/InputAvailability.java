/*
 * Derived from Iris / Angelica shader pack interpreter (LGPL-3.0).
 */
package org.soutalab.celtium.shader.model;

public record InputAvailability(boolean texture, boolean lightmap, boolean color) {
    public static final InputAvailability ALL = new InputAvailability(true, true, true);
    public static final InputAvailability TEXTURE_AND_LIGHTMAP = new InputAvailability(true, true, false);
    public static final InputAvailability TEXTURE_ONLY = new InputAvailability(true, false, false);
    public static final InputAvailability NONE = new InputAvailability(false, false, false);

    public InputAvailability withoutColor() {
        return new InputAvailability(texture, lightmap, false);
    }
}
