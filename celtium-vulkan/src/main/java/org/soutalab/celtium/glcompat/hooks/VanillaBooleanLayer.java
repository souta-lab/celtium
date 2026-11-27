package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/** Separates a vanilla boolean value from a shader-owned effective value. */
public interface VanillaBooleanLayer {
    /** Returns whether the shader currently owns the effective value. */
    boolean isOverrideHeld();

    /** Returns the deferred vanilla value. */
    boolean getVanilla();

    /** Stores a vanilla value without replacing the shader override. */
    void setVanilla(boolean enabled);
}
