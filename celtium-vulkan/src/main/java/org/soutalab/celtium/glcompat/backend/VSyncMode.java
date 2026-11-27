package org.soutalab.celtium.glcompat.backend;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Presentation mode requested from the window system.
 *
 * <p>The mode decides whether the driver owns the frame cadence or the pacer has to fill it in software. {@link #ON}
 * blocks inside the swap call until the next vblank, {@link #OFF} presents immediately, and {@link #MAILBOX} replaces
 * the queued frame with the newest one without blocking.
 */
public enum VSyncMode {
    OFF,
    ON,
    MAILBOX;

    /** Returns whether the mode prevents tearing, which is the same as requiring a vblank-aligned presentation. */
    public boolean tearFree() {
        return this != OFF;
    }
}
