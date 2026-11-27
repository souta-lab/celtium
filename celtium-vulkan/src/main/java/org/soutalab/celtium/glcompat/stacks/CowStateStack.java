package org.soutalab.celtium.glcompat.stacks;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.gtnh.client.renderer.stacks.IStateStack;
import org.soutalab.celtium.glcompat.GLContextState;
import org.soutalab.celtium.glcompat.GLStateManager;

public interface CowStateStack<T> extends IStateStack<T> {
    CowDepths cowDepths();

    void captureSlot(int slot);

    void restoreSlot(int slot);

    boolean slotChanged(int slot);

    default int stackId() {
        return cowDepths().id;
    }

    default void setStackId(int id) {
        cowDepths().id = id;
    }

    default int restoreBit() {
        return cowDepths().restoreBit;
    }

    default int restoreUnit() {
        return cowDepths().restoreUnit;
    }

    default boolean topSlotChanged() {
        return false;
    }

    @Override
    default int pushDepth() {
        return GLStateManager.getAttribDepth();
    }

    @Override
    default void beforeModify() {
        final CowDepths depths = cowDepths();
        final GLContextState glCtx = GLStateManager.ctx();
        final int depth = glCtx.attribDepth;
        if (depth == 0 || depths.claimedAt(depth) || !glCtx.savesState(stackId())) return;
        captureSlot(depths.claim(depth));
        GLStateManager.registerModifiedState(stackId());
    }

    @Override
    default boolean isEmpty() {
        return cowDepths().isEmpty();
    }

    default void drain() {
        int slot;
        while ((slot = cowDepths().pop()) >= 0) restoreSlot(slot);
    }

    @SuppressWarnings("unchecked")
    @Override
    default T push() {
        captureSlot(cowDepths().claimUnconditional());
        return (T) this;
    }

    @SuppressWarnings("unchecked")
    @Override
    default T pop() {
        final int slot = cowDepths().pop();
        if (slot < 0) {
            throw new IllegalStateException("Stack underflow");
        }
        restoreSlot(slot);
        return (T) this;
    }
}
