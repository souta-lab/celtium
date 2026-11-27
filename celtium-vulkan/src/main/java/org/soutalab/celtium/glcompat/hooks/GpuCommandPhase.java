package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Describes whether a GPU command breadcrumb is instantaneous or a low-frequency boundary.
 */
public enum GpuCommandPhase {
    /** The command is about to be submitted and has no paired return breadcrumb. */
    ISSUED,
    /** A low-frequency native command is about to be submitted. */
    BEGIN,
    /** A low-frequency native command returned to Java. */
    END
}
