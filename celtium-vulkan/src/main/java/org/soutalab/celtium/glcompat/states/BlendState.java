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
import org.lwjgl.opengl.GL14;


public class BlendState implements ISettableState<BlendState> {
    protected int srcRgb = GL11.GL_ONE;
    protected int dstRgb = GL11.GL_ZERO;
    protected int srcAlpha = GL11.GL_ONE;
    protected int dstAlpha = GL11.GL_ZERO;
    protected int equationRgb = GL14.GL_FUNC_ADD;
    protected int equationAlpha = GL14.GL_FUNC_ADD;
    protected float blendColorR = 0.0f;
    protected float blendColorG = 0.0f;
    protected float blendColorB = 0.0f;
    protected float blendColorA = 0.0f;

    public BlendState() {}

    public BlendState(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    public void setAll(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
        this.srcAlpha = srcAlpha;
        this.dstAlpha = dstAlpha;
    }

    public void setSrcDstRgb(int srcRgb, int dstRgb) {
        this.srcRgb = srcRgb;
        this.dstRgb = dstRgb;
    }

    /** Restores equations and blend color while retaining the active blend function. */
    public void setExceptFunc(BlendState state) {
        final int savedSrcRgb = srcRgb;
        final int savedDstRgb = dstRgb;
        final int savedSrcAlpha = srcAlpha;
        final int savedDstAlpha = dstAlpha;
        set(state);
        srcRgb = savedSrcRgb;
        dstRgb = savedDstRgb;
        srcAlpha = savedSrcAlpha;
        dstAlpha = savedDstAlpha;
    }

    @Override
    public BlendState set(BlendState state) {
        this.srcRgb = state.srcRgb;
        this.dstRgb = state.dstRgb;
        this.srcAlpha = state.srcAlpha;
        this.dstAlpha = state.dstAlpha;
        this.equationRgb = state.equationRgb;
        this.equationAlpha = state.equationAlpha;
        this.blendColorR = state.blendColorR;
        this.blendColorG = state.blendColorG;
        this.blendColorB = state.blendColorB;
        this.blendColorA = state.blendColorA;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof BlendState blendState)) return false;
        return srcRgb == blendState.srcRgb && dstRgb == blendState.dstRgb
            && srcAlpha == blendState.srcAlpha && dstAlpha == blendState.dstAlpha
            && equationRgb == blendState.equationRgb && equationAlpha == blendState.equationAlpha
            && Float.compare(blendColorR, blendState.blendColorR) == 0
            && Float.compare(blendColorG, blendState.blendColorG) == 0
            && Float.compare(blendColorB, blendState.blendColorB) == 0
            && Float.compare(blendColorA, blendState.blendColorA) == 0;
    }

    @Override
    public BlendState copy() {
        return new BlendState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getSrcRgb() { return srcRgb; }
    public void setSrcRgb(int srcRgb) { this.srcRgb = srcRgb; }
    public int getDstRgb() { return dstRgb; }
    public void setDstRgb(int dstRgb) { this.dstRgb = dstRgb; }
    public int getSrcAlpha() { return srcAlpha; }
    public void setSrcAlpha(int srcAlpha) { this.srcAlpha = srcAlpha; }
    public int getDstAlpha() { return dstAlpha; }
    public void setDstAlpha(int dstAlpha) { this.dstAlpha = dstAlpha; }
    public int getEquationRgb() { return equationRgb; }
    public void setEquationRgb(int equationRgb) { this.equationRgb = equationRgb; }
    public int getEquationAlpha() { return equationAlpha; }
    public void setEquationAlpha(int equationAlpha) { this.equationAlpha = equationAlpha; }
    public float getBlendColorR() { return blendColorR; }
    public void setBlendColorR(float blendColorR) { this.blendColorR = blendColorR; }
    public float getBlendColorG() { return blendColorG; }
    public void setBlendColorG(float blendColorG) { this.blendColorG = blendColorG; }
    public float getBlendColorB() { return blendColorB; }
    public void setBlendColorB(float blendColorB) { this.blendColorB = blendColorB; }
    public float getBlendColorA() { return blendColorA; }
    public void setBlendColorA(float blendColorA) { this.blendColorA = blendColorA; }
}
