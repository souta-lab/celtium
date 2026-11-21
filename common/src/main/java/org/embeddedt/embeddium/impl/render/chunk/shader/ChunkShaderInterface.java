package org.embeddedt.embeddium.impl.render.chunk.shader;

import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.joml.Matrix4fc;

public interface ChunkShaderInterface {
    void setupState(TerrainRenderPass pass);
    default void restoreState() {}
    GlPrimitiveType getPrimitiveType();
    default void setProjectionMatrix(Matrix4fc matrix) {
        setProjectionMatrix(new org.embeddedt.embeddium.impl.shadow.joml.Matrix4f(matrix));
    }

    default void setModelViewMatrix(Matrix4fc matrix) {
        setModelViewMatrix(new org.embeddedt.embeddium.impl.shadow.joml.Matrix4f(matrix));
    }

    /** Demonica Tier C bridge ({@code IrisCeleritasChunkShaderInterface}). */
    default void setProjectionMatrix(org.embeddedt.embeddium.impl.shadow.joml.Matrix4fc matrix) {
        setProjectionMatrix(matrix.toJoml());
    }

    /** Demonica Tier C bridge ({@code IrisCeleritasChunkShaderInterface}). */
    default void setModelViewMatrix(org.embeddedt.embeddium.impl.shadow.joml.Matrix4fc matrix) {
        setModelViewMatrix(matrix.toJoml());
    }
    void setRegionOffset(float x, float y, float z);
    void setTextureSlot(ChunkShaderTextureSlot slot, int val);

    default void setSectionAges(long timestamp, long[] loadTimes) {

    }
}
