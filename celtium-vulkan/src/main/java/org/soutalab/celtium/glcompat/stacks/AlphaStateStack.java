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
import org.soutalab.celtium.glcompat.hooks.VanillaStateLayer;
import org.soutalab.celtium.glcompat.states.AlphaState;

public final class AlphaStateStack extends AlphaState implements CowStateStack<AlphaStateStack> {
    protected final AlphaState[] stack;
    private final CowDepths cow = new CowDepths();

     private VanillaStateLayer<AlphaState> vanillaLayer;

    public AlphaStateStack(int id) {
        cow.id = id;
        stack = new AlphaState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new AlphaState();
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
        final AlphaState saved = stack[slot];
        if (!VanillaStateLayer.restore(vanillaLayer, saved)) {
            set(saved);
        }
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public AlphaState readEffective(AlphaState out) {
        out.set(this);
        VanillaStateLayer.capture(vanillaLayer, out);
        return out;
    }

    @Override
    public int stackId() {
        return cow.id;
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public void setVanillaLayer(VanillaStateLayer<AlphaState> vanillaLayer) { this.vanillaLayer = vanillaLayer; }
}
