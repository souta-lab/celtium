package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

import org.lwjgl.opengl.GL20C;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;

public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    public GlUniformMatrix4f(int index) {
        super(index);
    }

    public void set(Matrix4f value) {
        set((Matrix4fc) value);
    }

    @Override
    public void set(Matrix4fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(16);
            value.get(buf);

            GL20C.glUniformMatrix4fv(this.index, false, buf);
        }
    }
}
