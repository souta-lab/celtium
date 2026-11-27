package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */



public class IntegerState implements ISettableState<IntegerState> {
      private int value;

    @Override
    public IntegerState set(IntegerState state) {
        this.value = state.value;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof IntegerState integerState)) return false;
        return value == integerState.value;
    }

    @Override
    public IntegerState copy() {
        return new IntegerState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getValue() { return value; }
    public void setValue(int value) { this.value = value; }
}
