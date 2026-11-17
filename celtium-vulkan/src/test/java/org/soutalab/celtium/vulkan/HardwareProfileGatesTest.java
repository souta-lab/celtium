package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.soutalab.celtium.vulkan.HardwareProfileGates.GatedFeature;

/**
 * The hardware-profile gate table (survey V-02): a profile gates only where it was measured, and every gate
 * carries its citation. The async-present entry is the table's first: it is what
 * {@code VulkanContext.asyncPresentDefaultForOs} has always decided, now recorded in one place.
 */
class HardwareProfileGatesTest {

    @Test
    void windowsGatesTheAsyncPresenterDefault() {
        assertTrue(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, "Windows 11", null));
        assertTrue(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, "Windows 10", null));
        assertTrue(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, "windows 11", null),
                "OS matching is case-insensitive, like the check it replaces");
    }

    @Test
    void unmeasuredProfilesGateNothing() {
        assertFalse(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, "Linux", null));
        assertFalse(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, "Mac OS X", null));
        assertFalse(HardwareProfileGates.gated(GatedFeature.ASYNC_PRESENT, null, null),
                "an unknown OS matches no profile: the default stands, as the old check had it");
    }

    @Test
    void aGateCarriesItsMeasurementCitation() {
        String citation = HardwareProfileGates.citation(GatedFeature.ASYNC_PRESENT, "Windows 11", null);
        assertNotNull(citation);
        assertTrue(citation.contains("RTX3070"), "the citation names the measured profile: " + citation);
        assertNull(HardwareProfileGates.citation(GatedFeature.ASYNC_PRESENT, "Linux", null));
    }
}
