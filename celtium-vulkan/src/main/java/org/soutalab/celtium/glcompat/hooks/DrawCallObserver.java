package org.soutalab.celtium.glcompat.hooks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/** Observes the vertex inputs selected immediately before a native draw submission. */
@FunctionalInterface
public interface DrawCallObserver {
    /**
     * Receives the vertex flags derived for the pending draw.
     *
     * @param vertexFlags GLSM vertex-format flags active for the draw
     */
    void beforeDraw(int vertexFlags);
}
