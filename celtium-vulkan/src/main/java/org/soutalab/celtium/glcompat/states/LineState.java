package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */



/**
 * Tracks GL line state: width, stipple factor, and stipple pattern.
 * Corresponds to GL_LINE_BIT attributes.
 */
public class LineState implements ISettableState<LineState> {
    private float width = 1.0f;
    private int stippleFactor = 1;
    private short stipplePattern = (short) 0xFFFF;

    @Override
    public LineState set(LineState state) {
        this.width = state.width;
        this.stippleFactor = state.stippleFactor;
        this.stipplePattern = state.stipplePattern;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof LineState lineState)) return false;
        return Float.compare(width, lineState.width) == 0
            && stippleFactor == lineState.stippleFactor
            && stipplePattern == lineState.stipplePattern;
    }

    @Override
    public LineState copy() {
        return new LineState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public float getWidth() { return width; }
    public void setWidth(float width) { this.width = width; }
    public int getStippleFactor() { return stippleFactor; }
    public void setStippleFactor(int stippleFactor) { this.stippleFactor = stippleFactor; }
    public short getStipplePattern() { return stipplePattern; }
    public void setStipplePattern(short stipplePattern) { this.stipplePattern = stipplePattern; }
}
