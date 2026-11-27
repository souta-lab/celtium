package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.lwjgl.opengl.GL11;

import static org.soutalab.celtium.glcompat.backend.DirectGlExecutor.EXECUTOR;

public class BooleanState implements ISettableState<BooleanState> {
    protected final int glCap;
    protected final boolean ffpStateOnly;

     protected boolean enabled;
    protected boolean stateUnknown;

    public BooleanState(int glCap) {
        this(glCap, false);
    }

    public BooleanState(int glCap, boolean ffpStateOnly) {
        this.glCap = glCap;
        this.ffpStateOnly = ffpStateOnly;
    }

    public void setUnknownState() {
        stateUnknown = true;
    }

    protected boolean tracksStateWhenCacheBypassed() {
        return false;
    }

    protected boolean forwardsToBackend() {
        return !ffpStateOnly;
    }

    /** Re-applies the cached enable state to the backend; used when replaying state onto a fresh GL context. */
    public void applyToBackend() {
        if (!forwardsToBackend()) return;
        if (enabled) {
            EXECUTOR.enable(glCap);
        } else {
            EXECUTOR.disable(glCap);
        }
    }

    public void disable() {
        this.setEnabled(false);
    }

    public void enable() {
        this.setEnabled(true);
    }

    public void setEnabled(boolean enabled) {
        final boolean bypass = GLStateManager.shouldBypassCache();
        if (stateUnknown || bypass || enabled != this.enabled || (this.glCap == GL11.GL_BLEND && GLStateManager.vendorIsAMD() && GLStateManager.isPoppingAttributes())) {
            GLStateManager.beforeUncapturedCapabilityChange(glCap);
            stateUnknown = false;
            if (!bypass || tracksStateWhenCacheBypassed()) {
                this.enabled = enabled;
                onEnabledChanged();
            }
            if (forwardsToBackend()) {
                if (enabled) {
                    EXECUTOR.enable(this.glCap);
                } else {
                    EXECUTOR.disable(this.glCap);
                }
            }
        }
    }

    protected void onEnabledChanged() {}

    @Override
    public BooleanState set(BooleanState state) {
        this.enabled = state.enabled;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof BooleanState booleanState)) return false;
        return enabled == booleanState.enabled;
    }

    @Override
    public BooleanState copy() {
        return new BooleanState(this.glCap).set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public boolean isEnabled() { return enabled; }
}
