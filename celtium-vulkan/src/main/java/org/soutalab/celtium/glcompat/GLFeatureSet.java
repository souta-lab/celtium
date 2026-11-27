package org.soutalab.celtium.glcompat;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


/**
 * Simple holder for a set of GL feature flags.
 * Used to track which GL state groups have been modified.
 * Ported from Angelica.
 */
public class GLFeatureSet {
    private int mask;

    public GLFeatureSet() {
        this.mask = 0;
    }

    public GLFeatureSet(int mask) {
        this.mask = mask;
    }

    public int getMask() {
        return mask;
    }

    public void setMask(int mask) {
        this.mask = mask;
    }

    public void add(int feature) {
        mask |= feature;
    }

    public void remove(int feature) {
        mask &= ~feature;
    }

    public boolean contains(int feature) {
        return (mask & feature) != 0;
    }

    public void clear() {
        mask = 0;
    }

    public boolean isEmpty() {
        return mask == 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GLFeatureSet that)) return false;
        return mask == that.mask;
    }

    @Override
    public int hashCode() {
        return mask;
    }
}
