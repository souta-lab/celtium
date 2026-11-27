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
import org.soutalab.celtium.glcompat.states.DepthState;

public final class DepthStateStack extends DepthState implements CowStateStack<DepthStateStack> {
    protected final DepthState[] stack;
    private final CowDepths cow = new CowDepths(StateSet.R_DEPTH);

     private VanillaStateLayer<DepthState> vanillaLayer;
    private final DepthState effective = new DepthState();

    public DepthStateStack(int id) {
        cow.id = id;
        stack = new DepthState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new DepthState();
        }
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return VanillaStateLayer.isHeld(vanillaLayer) || !sameAs(stack[slot]);
    }

    @Override
    public void captureSlot(int slot) {
        VanillaStateLayer.capture(vanillaLayer, stack[slot].set(this));
    }

    @Override
    public void restoreSlot(int slot) {
        final DepthState saved = stack[slot];
        if (VanillaStateLayer.restore(vanillaLayer, saved)) {
            setExceptMask(saved);
        } else {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public boolean isEffectiveMaskEnabled() {
        if (!VanillaStateLayer.isHeld(vanillaLayer)) {
            return isMaskEnabled();
        }
        vanillaLayer.readVanilla(effective);
        return effective.isMaskEnabled();
    }

    @Override
    public int stackId() {
        return cow.id;
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public void setVanillaLayer(VanillaStateLayer<DepthState> vanillaLayer) { this.vanillaLayer = vanillaLayer; }
}
