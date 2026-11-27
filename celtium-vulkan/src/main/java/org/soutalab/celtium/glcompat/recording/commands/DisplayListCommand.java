package org.soutalab.celtium.glcompat.recording.commands;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * A single command recorded during display list compilation.
 * Commands are executed in sequence during glCallList() to replay the display list.
 */
public interface DisplayListCommand {
    /**
     * Execute this command by calling the appropriate GL function.
     */
    void execute();

    /**
     * Release any resources held by this command (e.g., VBOs).
     * Called when the display list is deleted.
     */
    default void delete() {
        // Most commands don't hold resources
    }
}
