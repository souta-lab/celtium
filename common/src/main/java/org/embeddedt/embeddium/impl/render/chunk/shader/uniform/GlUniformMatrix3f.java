package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

import org.lwjgl.opengl.GL20C;

import org.joml.Matrix3f;
import org.joml.Matrix3fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

/**
 * 3x3 matrix uniform wrapper (used by Demonica's {@code IrisCeleritasChunkShaderInterface} for {@code iris_NormalMatrix}).
 */
public class GlUniformMatrix3f extends GlUniform<Matrix3fc> {
    public GlUniformMatrix3f(int index) {
        super(index);
    }

    public void set(Matrix3f value) {
        set((Matrix3fc) value);
    }

    @Override
    public void set(Matrix3fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(9);
            value.get(buf);
            GL20C.glUniformMatrix3fv(this.index, false, buf);
        }
    }
}
