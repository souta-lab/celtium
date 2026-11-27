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
 * Receives allocation-free GPU command breadcrumbs immediately before native submission.
 */
public interface GpuCommandRecorder {
    /**
     * Records one command with the GLSM state active at submission time.
     *
     * @param type stable command type
     * @param phase submission boundary represented by this breadcrumb
     * @param program active GLSL program requested through GLSM
     * @param drawFramebuffer active draw framebuffer
     * @param operand0 command-specific first operand
     * @param operand1 command-specific second operand
     */
    void record(
        GpuCommandType type,
        GpuCommandPhase phase,
        int program,
        int drawFramebuffer,
        int operand0,
        int operand1
    );

    /**
     * Records a checkpoint lifecycle event without performing any native operation.
     *
     * @param type checkpoint lifecycle type
     * @param checkpointId monotonically increasing per-context checkpoint id
     * @param commandCode command or pass-boundary code associated with the fence
     */
    void checkpoint(GpuCheckpointType type, long checkpointId, int commandCode);
}
