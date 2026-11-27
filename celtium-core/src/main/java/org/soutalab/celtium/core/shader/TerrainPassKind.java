package org.soutalab.celtium.core.shader;

/**
 * The closed set of terrain passes a shader pack can ask celtium to draw: three for the main view,
 * three for the shadow view (design V040-FINAL-PLAN section 2.3, interface 1). The pack executor
 * drives the passes in this declaration order within their stage; a pack that defines no shadow
 * programs has no shadow pass at all ({@code TerrainPackProgramSet.hasShadowPass()}).
 */
public enum TerrainPassKind {
    MAIN_SOLID,
    MAIN_CUTOUT,
    MAIN_TRANSLUCENT,
    SHADOW_SOLID,
    SHADOW_CUTOUT,
    SHADOW_TRANSLUCENT;

    public boolean isShadow() {
        return ordinal() >= SHADOW_SOLID.ordinal();
    }

    public boolean isMain() {
        return ordinal() < SHADOW_SOLID.ordinal();
    }
}
