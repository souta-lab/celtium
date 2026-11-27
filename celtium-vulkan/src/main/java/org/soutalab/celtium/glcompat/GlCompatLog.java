package org.soutalab.celtium.glcompat;

/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** celtium-side replacement for the supplier's root mod logger handle. */
public final class GlCompatLog {
    public static final Logger LOGGER = LogManager.getLogger("Celtium");

    private GlCompatLog() {
    }
}
