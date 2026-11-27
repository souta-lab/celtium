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
import org.soutalab.celtium.glcompat.states.IntegerState;

public final class IntegerStateStack extends IntegerState implements CowStateStack<IntegerStateStack> {
    protected final IntegerState[] stack;
    private CowDepths cow = new CowDepths();

    public IntegerStateStack(int value, int id) {
        cow.id = id;
        setValue(value);
        stack = new IntegerState[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new IntegerState();
            stack[i].setValue(value);
        }
    }

    public IntegerStateStack restoreBit(int bit) {
        final int id = cow.id;
        cow = new CowDepths(bit);
        cow.id = id;
        return this;
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return !sameAs(stack[slot]);
    }

    @Override
    public void captureSlot(int slot) {
        stack[slot].set(this);
    }

    @Override
    public void restoreSlot(int slot) {
        set(stack[slot]);
    }

    @Override
    public boolean topSlotChanged() {
        return !cow.isEmpty() && !sameAs(stack[cow.top()]);
    }

    public IntegerStateStack push(int value) {
        push().setValue(value);
        return this;
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
