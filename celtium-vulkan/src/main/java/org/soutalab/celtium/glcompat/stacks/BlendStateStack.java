package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.GLStateManager;
import org.soutalab.celtium.glcompat.StateSet;
import org.soutalab.celtium.glcompat.hooks.VanillaStateLayer;
import org.soutalab.celtium.glcompat.states.BlendState;

public final class BlendStateStack extends BlendState implements CowStateStack<BlendStateStack> {
    protected final BlendState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_BLEND);
    private boolean funcUnknown;

     private VanillaStateLayer<BlendState> vanillaLayer;

    public BlendStateStack(int id) {
        cow.id = id;
        stack = new BlendState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new BlendState();
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return funcUnknown || VanillaStateLayer.isHeld(vanillaLayer) || !sameAs(stack[slot]);
    }

    @Override
    public void captureSlot(int slot) {
        VanillaStateLayer.capture(vanillaLayer, stack[slot].set(this));
    }

    @Override
    public void restoreSlot(int slot) {
        final BlendState saved = stack[slot];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptFunc(saved);
        } else {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public void setFuncUnknownState() {
        funcUnknown = true;
    }

    public void clearFuncUnknownState() {
        funcUnknown = false;
    }

    public boolean isFuncUnknown() {
        return funcUnknown;
    }

    public BlendState readEffective(BlendState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    public boolean isEnabled() {
        return GLStateManager.getBlendMode().isEnabled();
    }

    @Override
    public int stackId() {
        return cow.id;
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public void setVanillaLayer(VanillaStateLayer<BlendState> vanillaLayer) { this.vanillaLayer = vanillaLayer; }
}
