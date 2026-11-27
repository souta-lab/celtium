package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.lwjgl.opengl.GL20;

/**
 * Tracks GL point state: rasterized size and the {@code glPointParameter} family.
 * Corresponds to GL_POINT_BIT attributes.
 */
public class PointState implements ISettableState<PointState> {
    private float size = 1.0f;
    private float minSize = 0.0f;
    /** Largest accepted point size; stays NaN until the caller sets it so the driver range answers the query. */
    private float maxSize = Float.NaN;
    private float fadeThreshold = 1.0f;
    private float attenuationConstant = 1.0f;
    private float attenuationLinear = 0.0f;
    private float attenuationQuadratic = 0.0f;
    private int spriteCoordOrigin = GL20.GL_UPPER_LEFT;

    @Override
    public PointState set(PointState state) {
        this.size = state.size;
        this.minSize = state.minSize;
        this.maxSize = state.maxSize;
        this.fadeThreshold = state.fadeThreshold;
        this.attenuationConstant = state.attenuationConstant;
        this.attenuationLinear = state.attenuationLinear;
        this.attenuationQuadratic = state.attenuationQuadratic;
        this.spriteCoordOrigin = state.spriteCoordOrigin;
        return this;
    }

    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof PointState pointState)) return false;
        return Float.compare(size, pointState.size) == 0
            && Float.compare(minSize, pointState.minSize) == 0
            && Float.compare(maxSize, pointState.maxSize) == 0
            && Float.compare(fadeThreshold, pointState.fadeThreshold) == 0
            && Float.compare(attenuationConstant, pointState.attenuationConstant) == 0
            && Float.compare(attenuationLinear, pointState.attenuationLinear) == 0
            && Float.compare(attenuationQuadratic, pointState.attenuationQuadratic) == 0
            && spriteCoordOrigin == pointState.spriteCoordOrigin;
    }

    @Override
    public PointState copy() {
        return new PointState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public float getSize() { return size; }
    public void setSize(float size) { this.size = size; }
    public float getMinSize() { return minSize; }
    public void setMinSize(float minSize) { this.minSize = minSize; }
    public float getMaxSize() { return maxSize; }
    public void setMaxSize(float maxSize) { this.maxSize = maxSize; }
    public float getFadeThreshold() { return fadeThreshold; }
    public void setFadeThreshold(float fadeThreshold) { this.fadeThreshold = fadeThreshold; }
    public float getAttenuationConstant() { return attenuationConstant; }
    public void setAttenuationConstant(float attenuationConstant) { this.attenuationConstant = attenuationConstant; }
    public float getAttenuationLinear() { return attenuationLinear; }
    public void setAttenuationLinear(float attenuationLinear) { this.attenuationLinear = attenuationLinear; }
    public float getAttenuationQuadratic() { return attenuationQuadratic; }
    public void setAttenuationQuadratic(float attenuationQuadratic) { this.attenuationQuadratic = attenuationQuadratic; }
    public int getSpriteCoordOrigin() { return spriteCoordOrigin; }
    public void setSpriteCoordOrigin(int spriteCoordOrigin) { this.spriteCoordOrigin = spriteCoordOrigin; }
}
