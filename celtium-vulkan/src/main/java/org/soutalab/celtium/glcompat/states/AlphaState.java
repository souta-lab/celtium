package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL11;

public class AlphaState implements ISettableState<AlphaState> {
     protected int function = GL11.GL_ALWAYS;
     protected float reference = 0.0F;

    @Override
    public AlphaState set(AlphaState state) {
        this.function = state.function;
        this.reference = state.reference;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof AlphaState alphaState)) return false;
        return function == alphaState.function && Float.compare(alphaState.reference, reference) == 0;
    }

    @Override
    public AlphaState copy() {
        return new AlphaState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getFunction() { return function; }
    public void setFunction(int function) { this.function = function; }
    public float getReference() { return reference; }
    public void setReference(float reference) { this.reference = reference; }
}
