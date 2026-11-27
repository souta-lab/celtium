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
 * A shader pipeline that deferred a program selection and has to run it later.
 *
 * <p>The host compares the slot's identity with itself, so a pipeline only resolves requests it
 * published and never another pipeline's. A request is published while a foreign renderer or an
 * active batch owns the program, and resolved once that scope ends.</p>
 */
public interface PendingProgramSelection {

    /** Runs the deferred program selection, if one is still pending and nothing owns the program. */
    void resolvePendingProgram();
}
