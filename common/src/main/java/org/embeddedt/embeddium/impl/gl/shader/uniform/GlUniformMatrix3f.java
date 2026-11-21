package org.embeddedt.embeddium.impl.gl.shader.uniform;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

import org.joml.Matrix3fc;
import org.taumc.celeritas.lwjgl.MemoryStack;

import java.nio.FloatBuffer;

/**
 * 3x3 matrix uniform wrapper (used by Demonica's {@code IrisCeleritasChunkShaderInterface} for {@code iris_NormalMatrix}).
 */
public class GlUniformMatrix3f extends GlUniform<Matrix3fc> {
    public GlUniformMatrix3f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix3fc value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer buf = stack.callocFloat(9);
            value.get(buf);
            LWJGL.glUniformMatrix3fv(this.index, false, buf);
        }
    }
}
