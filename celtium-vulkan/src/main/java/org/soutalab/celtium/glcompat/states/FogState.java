package org.soutalab.celtium.glcompat.states;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class FogState implements ISettableState<FogState> {
    protected int fogMode = GL11.GL_EXP;
    protected final Vector3d fogColor = new Vector3d(0.0F, 0.0F, 0.0F);
    protected float fogAlpha = 1.0F;
    protected final FloatBuffer fogColorBuffer = FloatBuffer.allocate(4);
    protected float density = 1.0F;
    protected float start;
    protected float end = 1.0F;
    public static final int DISTANCE_EYE_RADIAL = 0;
    public static final int DISTANCE_EYE_PLANE = 1;
    public static final int DISTANCE_EYE_PLANE_ABS = 2;

    /** Fog distance computation mode: one of the DISTANCE_* constants. */
    protected int fogDistanceMode = DISTANCE_EYE_PLANE_ABS;

    @Override
    public FogState set(FogState state) {
        this.fogMode = state.fogMode;
        this.fogColor.set(state.fogColor);
        this.fogAlpha = state.fogAlpha;
        this.fogColorBuffer.put(0, state.fogColorBuffer.get(0));
        this.fogColorBuffer.put(1, state.fogColorBuffer.get(1));
        this.fogColorBuffer.put(2, state.fogColorBuffer.get(2));
        this.fogColorBuffer.put(3, state.fogColorBuffer.get(3));
        this.density = state.density;
        this.start = state.start;
        this.end = state.end;
        this.fogDistanceMode = state.fogDistanceMode;
        return this;
    }
    @Override
    public boolean sameAs(Object state) {
        if (this == state) return true;
        if (!(state instanceof FogState fogState)) return false;
        return fogMode == fogState.fogMode && fogDistanceMode == fogState.fogDistanceMode && Float.compare(fogState.fogAlpha, fogAlpha) == 0 && Float.compare(fogState.density, density) == 0 && Float.compare(fogState.start, start) == 0 && Float.compare(fogState.end, end) == 0 && fogColor.equals(fogState.fogColor);
    }

    @Override
    public FogState copy() {
        return new FogState().set(this);
    }

    // -- explicit accessors (delomboked during the S4 native conversion) --
    public int getFogMode() { return fogMode; }
    public void setFogMode(int fogMode) { this.fogMode = fogMode; }
    public Vector3d getFogColor() { return fogColor; }
    public float getFogAlpha() { return fogAlpha; }
    public void setFogAlpha(float fogAlpha) { this.fogAlpha = fogAlpha; }
    public FloatBuffer getFogColorBuffer() { return fogColorBuffer; }
    public float getDensity() { return density; }
    public void setDensity(float density) { this.density = density; }
    public float getStart() { return start; }
    public void setStart(float start) { this.start = start; }
    public float getEnd() { return end; }
    public void setEnd(float end) { this.end = end; }
    public static int getDISTANCE_EYE_RADIAL() { return DISTANCE_EYE_RADIAL; }
    public static int getDISTANCE_EYE_PLANE() { return DISTANCE_EYE_PLANE; }
    public static int getDISTANCE_EYE_PLANE_ABS() { return DISTANCE_EYE_PLANE_ABS; }
    public int getFogDistanceMode() { return fogDistanceMode; }
    public void setFogDistanceMode(int fogDistanceMode) { this.fogDistanceMode = fogDistanceMode; }
}
