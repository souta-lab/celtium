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
import org.joml.Vector4f;

public final class Vec4fStack implements CowStateStack<Vec4fStack> {
    private final Vector4f value;
    private final float[][] stack;
    private final CowDepths cow = new CowDepths();

    public Vec4fStack(Vector4f value, int id) {
        this.value = value;
        cow.id = id;
        stack = new float[GLStateManager.STATE_SLOTS][4];
    }

    @Override
    public CowDepths cowDepths() {
        return cow;
    }

    @Override
    public boolean slotChanged(int slot) {
        return value.x != stack[slot][0] || value.y != stack[slot][1] || value.z != stack[slot][2] || value.w != stack[slot][3];
    }

    @Override
    public void captureSlot(int slot) {
        stack[slot][0] = value.x;
        stack[slot][1] = value.y;
        stack[slot][2] = value.z;
        stack[slot][3] = value.w;
    }

    @Override
    public void restoreSlot(int slot) {
        value.set(stack[slot][0], stack[slot][1], stack[slot][2], stack[slot][3]);
    }

    @Override
    public int stackId() {
        return cow.id;
    }
}
