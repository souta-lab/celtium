package org.soutalab.celtium.glcompat.compat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Simple holder for fog color values captured from vanilla rendering.
 * Used by terrain renderers to access fog state.
 * Ported from Angelica.
 */
public class FogHelper {
    public static float red;
    public static float green;
    public static float blue;

    private FogHelper() {
    }

    public static void setFogColor(float r, float g, float b) {
        red = r;
        green = g;
        blue = b;
    }
}
