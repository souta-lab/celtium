package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/** Separates a vanilla structured state from a shader-owned effective state. */
public interface VanillaStateLayer<T> {
    /** Returns whether the shader currently owns the effective value. */
    boolean isOverrideHeld();

    /** Copies the deferred vanilla value into {@code into}. */
    void readVanilla(T into);

    /** Stores a vanilla value without replacing the shader override. */
    void writeVanilla(T from);

    static boolean isHeld(VanillaStateLayer<?> layer) {
        return layer != null && layer.isOverrideHeld();
    }

    static <T> void capture(VanillaStateLayer<T> layer, T slot) {
        if (isHeld(layer)) {
            layer.readVanilla(slot);
        }
    }

    static <T> boolean restore(VanillaStateLayer<T> layer, T saved) {
        if (!isHeld(layer)) {
            return false;
        }
        layer.writeVanilla(saved);
        return true;
    }
}
