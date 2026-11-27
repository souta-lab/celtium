package org.soutalab.celtium.glcompat.ffp;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


public enum Instancing {
    NONE(""),
    TEMPLATE("_instanced"),
    CUBE("_cubeinstanced"),
    PARTICLE("_particleinstanced"),
    WEATHER("_weatherinstanced");

    public static final Instancing[] VALUES = values();

    public final String variantSuffix;

    Instancing(String variantSuffix) {
        this.variantSuffix = variantSuffix;
    }

    public boolean hasInstanceHead() {
        return this == TEMPLATE || this == CUBE;
    }
}
