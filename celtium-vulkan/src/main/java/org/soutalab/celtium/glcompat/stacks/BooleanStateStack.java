package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.hooks.VanillaBooleanLayer;
import org.soutalab.celtium.glcompat.states.BooleanState;

public class BooleanStateStack extends BooleanState implements CowStateStack<BooleanStateStack> {
    protected final boolean[] stack;
    private final CowDepths cow = new CowDepths();

     private VanillaBooleanLayer vanillaLayer;

    public BooleanStateStack(int glCap) {
        this(glCap, false, false);
    }

    public BooleanStateStack(int glCap, boolean initialState) {
        this(glCap, initialState, false);
    }

    public BooleanStateStack(int glCap, boolean initialState, boolean ffpStateOnly) {
        super(glCap, ffpStateOnly);
        this.enabled = initialState;
        stack = new boolean[GLStateManager.STATE_SLOTS];
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return stateUnknown || (vanillaLayer != null && vanillaLayer.isOverrideHeld()) || enabled != stack[slot];
    }

    @Override
    public void captureSlot(int slot) {
        stack[slot] = vanillaValue();
    }

    @Override
    public void restoreSlot(int slot) {
        restore(stack[slot]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }

    public boolean isEffectivelyEnabled() {
        return vanillaLayer != null && vanillaLayer.isOverrideHeld() ? vanillaLayer.getVanilla() : enabled;
    }

    private boolean vanillaValue() {
        return isEffectivelyEnabled();
    }

    private void restore(boolean value) {
        if (vanillaLayer != null && vanillaLayer.isOverrideHeld()) {
            vanillaLayer.setVanilla(value);
        } else {
            setEnabledDirect(value);
        }
    }

    @Override
    public int getDepth() {
        return GLStateManager.getAttribDepth();
    }

    @Override
    public void setEnabled(boolean enabled) {
        final GLContextState o = cow.owner;
        GLStateManager.recordExitCapability(o, glCap, -1);
        if (GLStateManager.getAttribDepth() > 0
            && (stateUnknown || !GLStateManager.isCachingEnabled() || enabled != vanillaValue())) {
            beforeModify();
        }
        super.setEnabled(enabled);
    }

    private void setEnabledDirect(boolean enabled) {
        super.setEnabled(enabled);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public void setVanillaLayer(VanillaBooleanLayer vanillaLayer) { this.vanillaLayer = vanillaLayer; }
}
