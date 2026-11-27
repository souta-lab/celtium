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

/**
 * Tracks GL stencil buffer state.
 * Corresponds to GL_STENCIL_BUFFER_BIT attributes.
 * Supports separate front/back state (GL 2.0 glStencilFuncSeparate, glStencilOpSeparate, glStencilMaskSeparate).
 */
public class StencilState implements ISettableState<StencilState> {
    // Front face state
    private int funcFront = GL11.GL_ALWAYS;
    private int refFront = 0;
    private int valueMaskFront = 0xFFFFFFFF;
    private int failOpFront = GL11.GL_KEEP;
    private int zFailOpFront = GL11.GL_KEEP;
    private int zPassOpFront = GL11.GL_KEEP;
    private int writeMaskFront = 0xFFFFFFFF;

    // Back face state (GL 2.0)
    private int funcBack = GL11.GL_ALWAYS;
    private int refBack = 0;
    private int valueMaskBack = 0xFFFFFFFF;
    private int failOpBack = GL11.GL_KEEP;
    private int zFailOpBack = GL11.GL_KEEP;
    private int zPassOpBack = GL11.GL_KEEP;
    private int writeMaskBack = 0xFFFFFFFF;

    // Shared between front and back faces
    private int clearValue = 0;

    @Override
    public StencilState set(StencilState state) {
        this.funcFront = state.funcFront;
        this.refFront = state.refFront;
        this.valueMaskFront = state.valueMaskFront;
        this.failOpFront = state.failOpFront;
        this.zFailOpFront = state.zFailOpFront;
        this.zPassOpFront = state.zPassOpFront;
        this.writeMaskFront = state.writeMaskFront;

        this.funcBack = state.funcBack;
        this.refBack = state.refBack;
        this.valueMaskBack = state.valueMaskBack;
        this.failOpBack = state.failOpBack;
        this.zFailOpBack = state.zFailOpBack;
        this.zPassOpBack = state.zPassOpBack;
        this.writeMaskBack = state.writeMaskBack;

        this.clearValue = state.clearValue;
        return this;
    }

    /**
     * Set both front and back state at once (non-separate calls).
     */
    public void setFunc(int func, int ref, int mask) {
        this.funcFront = func;
        this.refFront = ref;
        this.valueMaskFront = mask;
        this.funcBack = func;
        this.refBack = ref;
        this.valueMaskBack = mask;
    }

    /**
     * Set both front and back operations at once (non-separate calls).
     */
    public void setOp(int fail, int zFail, int zPass) {
        this.failOpFront = fail;
        this.zFailOpFront = zFail;
        this.zPassOpFront = zPass;
        this.failOpBack = fail;
        this.zFailOpBack = zFail;
        this.zPassOpBack = zPass;
    }

    /**
     * Set both front and back write mask at once (non-separate calls).
     */
    public void setWriteMask(int mask) {
        this.writeMaskFront = mask;
        this.writeMaskBack = mask;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof StencilState stencilState)) return false;
        return funcFront == stencilState.funcFront
            && refFront == stencilState.refFront
            && valueMaskFront == stencilState.valueMaskFront
            && failOpFront == stencilState.failOpFront
            && zFailOpFront == stencilState.zFailOpFront
            && zPassOpFront == stencilState.zPassOpFront
            && writeMaskFront == stencilState.writeMaskFront
            && funcBack == stencilState.funcBack
            && refBack == stencilState.refBack
            && valueMaskBack == stencilState.valueMaskBack
            && failOpBack == stencilState.failOpBack
            && zFailOpBack == stencilState.zFailOpBack
            && zPassOpBack == stencilState.zPassOpBack
            && writeMaskBack == stencilState.writeMaskBack
            && clearValue == stencilState.clearValue;
    }

    @Override
    public StencilState copy() {
        return new StencilState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getFuncFront() { return funcFront; }
    public void setFuncFront(int funcFront) { this.funcFront = funcFront; }
    public int getRefFront() { return refFront; }
    public void setRefFront(int refFront) { this.refFront = refFront; }
    public int getValueMaskFront() { return valueMaskFront; }
    public void setValueMaskFront(int valueMaskFront) { this.valueMaskFront = valueMaskFront; }
    public int getFailOpFront() { return failOpFront; }
    public void setFailOpFront(int failOpFront) { this.failOpFront = failOpFront; }
    public int getZFailOpFront() { return zFailOpFront; }
    public void setZFailOpFront(int zFailOpFront) { this.zFailOpFront = zFailOpFront; }
    public int getZPassOpFront() { return zPassOpFront; }
    public void setZPassOpFront(int zPassOpFront) { this.zPassOpFront = zPassOpFront; }
    public int getWriteMaskFront() { return writeMaskFront; }
    public void setWriteMaskFront(int writeMaskFront) { this.writeMaskFront = writeMaskFront; }
    public int getFuncBack() { return funcBack; }
    public void setFuncBack(int funcBack) { this.funcBack = funcBack; }
    public int getRefBack() { return refBack; }
    public void setRefBack(int refBack) { this.refBack = refBack; }
    public int getValueMaskBack() { return valueMaskBack; }
    public void setValueMaskBack(int valueMaskBack) { this.valueMaskBack = valueMaskBack; }
    public int getFailOpBack() { return failOpBack; }
    public void setFailOpBack(int failOpBack) { this.failOpBack = failOpBack; }
    public int getZFailOpBack() { return zFailOpBack; }
    public void setZFailOpBack(int zFailOpBack) { this.zFailOpBack = zFailOpBack; }
    public int getZPassOpBack() { return zPassOpBack; }
    public void setZPassOpBack(int zPassOpBack) { this.zPassOpBack = zPassOpBack; }
    public int getWriteMaskBack() { return writeMaskBack; }
    public void setWriteMaskBack(int writeMaskBack) { this.writeMaskBack = writeMaskBack; }
    public int getClearValue() { return clearValue; }
    public void setClearValue(int clearValue) { this.clearValue = clearValue; }
}
