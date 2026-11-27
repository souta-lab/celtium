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
 * Tracks GL polygon state: polygon mode (front/back) and polygon offset.
 * Corresponds to GL_POLYGON_BIT attributes (excluding enable bits which are BooleanStateStack).
 */
public class PolygonState implements ISettableState<PolygonState> {
    private int frontMode = GL11.GL_FILL;
    private int backMode = GL11.GL_FILL;
    private float offsetFactor = 0.0f;
    private float offsetUnits = 0.0f;
    private float offsetClamp = 0.0f;
    private int cullFaceMode = GL11.GL_BACK;
    private int frontFace = GL11.GL_CCW;

    @Override
    public PolygonState set(PolygonState state) {
        this.frontMode = state.frontMode;
        this.backMode = state.backMode;
        this.offsetFactor = state.offsetFactor;
        this.offsetUnits = state.offsetUnits;
        this.offsetClamp = state.offsetClamp;
        this.cullFaceMode = state.cullFaceMode;
        this.frontFace = state.frontFace;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof PolygonState polygonState)) return false;
        return frontMode == polygonState.frontMode
            && backMode == polygonState.backMode
            && Float.compare(offsetFactor, polygonState.offsetFactor) == 0
            && Float.compare(offsetUnits, polygonState.offsetUnits) == 0
            && Float.compare(offsetClamp, polygonState.offsetClamp) == 0
            && cullFaceMode == polygonState.cullFaceMode
            && frontFace == polygonState.frontFace;
    }

    @Override
    public PolygonState copy() {
        return new PolygonState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getFrontMode() { return frontMode; }
    public void setFrontMode(int frontMode) { this.frontMode = frontMode; }
    public int getBackMode() { return backMode; }
    public void setBackMode(int backMode) { this.backMode = backMode; }
    public float getOffsetFactor() { return offsetFactor; }
    public void setOffsetFactor(float offsetFactor) { this.offsetFactor = offsetFactor; }
    public float getOffsetUnits() { return offsetUnits; }
    public void setOffsetUnits(float offsetUnits) { this.offsetUnits = offsetUnits; }
    public float getOffsetClamp() { return offsetClamp; }
    public void setOffsetClamp(float offsetClamp) { this.offsetClamp = offsetClamp; }
    public int getCullFaceMode() { return cullFaceMode; }
    public void setCullFaceMode(int cullFaceMode) { this.cullFaceMode = cullFaceMode; }
    public int getFrontFace() { return frontFace; }
    public void setFrontFace(int frontFace) { this.frontFace = frontFace; }
}
