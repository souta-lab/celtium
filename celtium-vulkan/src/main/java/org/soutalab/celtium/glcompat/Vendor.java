package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/// Matches
public enum Vendor {
    AMD("amd"),
    INTEL("intel"),
    MESA("mesa"),
    NVIDIA("nvidia"),
    UNKNOWN("");

    final String[] names;

    Vendor(String... names) {
        this.names = names;
    }

    public static Vendor getVendor(String vendorString) {
        for (var v : values()) {
            for (var name : v.names) {
                if (vendorString.contains(name)) return v;
            }
        }

        // Shouldn't be needed as "" matches everything, but sanity checks good.
        return UNKNOWN;
    }
}
