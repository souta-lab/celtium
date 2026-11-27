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

public final class CowDepths {
    public int id = -1;
    /** Context that owns the stack; capability writes report their capture through it. */
    public GLContextState owner;
    public final int restoreBit;
    public final int restoreUnit;
    private final int[] slotDepth = new int[GLStateManager.MAX_ATTRIB_STACK_DEPTH];
    private int pointer;

    public CowDepths() {
        this(0, -1);
    }

    public CowDepths(int restoreBit) {
        this(restoreBit, -1);
    }

    public CowDepths(int restoreBit, int restoreUnit) {
        this.restoreBit = restoreBit;
        this.restoreUnit = restoreUnit;
    }

    public int claim(int depth) {
        slotDepth[pointer] = depth;
        return pointer++;
    }

    public boolean claimedAt(int depth) {
        return pointer > 0 && slotDepth[pointer - 1] == depth;
    }

    public int claimUnconditional() {
        slotDepth[pointer] = -1;
        return pointer++;
    }

    public int pop() {
        return pointer > 0 ? --pointer : -1;
    }

    public int discard(int poppedDepth) {
        if (pointer == 0) return -1;
        final int parent = poppedDepth - 1;
        if (parent > 0 && (pointer < 2 || slotDepth[pointer - 2] != parent)) {
            slotDepth[pointer - 1] = parent;
            return pointer - 1;
        }
        pointer--;
        return -1;
    }

    public int top() {
        return pointer - 1;
    }

    public boolean isEmpty() {
        return pointer == 0;
    }
}
