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
import org.soutalab.celtium.glcompat.states.MatrixMode;

public final class MatrixModeStack extends MatrixMode implements CowStateStack<MatrixModeStack> {
    protected final MatrixMode[] stack;
    private final CowDepths cow = new CowDepths();

    public MatrixModeStack(int id) {
        cow.id = id;
        stack = new MatrixMode[GLStateManager.STATE_SLOTS];
        for (int i = 0; i < GLStateManager.STATE_SLOTS; i++) {
            stack[i] = new MatrixMode();
        }
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
    public int stackId() {
        return cow.id;
    }
}
