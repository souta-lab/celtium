package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL11;

public class DepthState implements ISettableState<DepthState> {
    protected boolean enabled = true;
    protected int func = GL11.GL_LESS;
    protected boolean maskEnabled = true;
    protected double clearValue = 1.0;

    @Override
    public DepthState set(DepthState state) {
        this.enabled = state.enabled;
        this.func = state.func;
        this.maskEnabled = state.maskEnabled;
        this.clearValue = state.clearValue;
        return this;
    }

    /** Restores depth comparison state while retaining the active depth-write mask. */
    public void setExceptMask(DepthState state) {
        final boolean savedMask = maskEnabled;
        set(state);
        maskEnabled = savedMask;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof DepthState depthState)) return false;
        return enabled == depthState.enabled
            && func == depthState.func
            && maskEnabled == depthState.maskEnabled
            && Double.compare(clearValue, depthState.clearValue) == 0;
    }
    @Override
    public DepthState copy() {
        return new DepthState().set(this);
    }


    // -- explicit accessors (delomboked during the S4 native conversion) --
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getFunc() { return func; }
    public void setFunc(int func) { this.func = func; }
    public boolean isMaskEnabled() { return maskEnabled; }
    public void setMaskEnabled(boolean maskEnabled) { this.maskEnabled = maskEnabled; }
    public double getClearValue() { return clearValue; }
    public void setClearValue(double clearValue) { this.clearValue = clearValue; }
}
