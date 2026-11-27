package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


public class ColorMask implements ISettableState<ColorMask> {
    public boolean red = true;
    public boolean green = true;
    public boolean blue = true;
    public boolean alpha = true;

    public void setAll(boolean red, boolean green, boolean blue, boolean alpha) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
    }

    @Override
    public ColorMask set(ColorMask state) {
        this.red = state.red;
        this.green = state.green;
        this.blue = state.blue;
        this.alpha = state.alpha;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ColorMask colorMask)) return false;
        return red == colorMask.red && green == colorMask.green && blue == colorMask.blue && alpha == colorMask.alpha;
    }
    @Override
    public ColorMask copy() {
        return new ColorMask().set(this);
    }
}
