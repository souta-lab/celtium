package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import java.nio.IntBuffer;

/**
 * Tracks the {@code GL_SCISSOR_BOX} rectangle so queries and attribute saves read the value GLSM
 * last issued instead of the driver. The box is per-context state, unlike the {@code GL_SCISSOR_TEST}
 * enable bit which lives on {@link org.soutalab.celtium.glcompat.stacks.BooleanStateStack}.
 */
public class ScissorState implements ISettableState<ScissorState> {
    public int x;
    public int y;
    public int width;
    public int height;

    public void setScissor(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void get(IntBuffer params) {
        params.put(0, x);
        params.put(1, y);
        params.put(2, width);
        params.put(3, height);
    }

    public void get(int[] params) {
        params[0] = x;
        params[1] = y;
        params[2] = width;
        params[3] = height;
    }

    @Override
    public ScissorState set(ScissorState state) {
        this.x = state.x;
        this.y = state.y;
        this.width = state.width;
        this.height = state.height;

        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof ScissorState scissorState)) return false;
        return x == scissorState.x && y == scissorState.y && width == scissorState.width && height == scissorState.height;
    }

    @Override
    public ScissorState copy() {
        return new ScissorState().set(this);
    }

}
