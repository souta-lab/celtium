package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

import org.lwjgl.opengl.GL20C;


public class GlUniformFloat4v extends GlUniform<float[]> {
    public GlUniformFloat4v(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        if (value.length != 4) {
            throw new IllegalArgumentException("value.length != 4");
        }

        GL20C.glUniform4fv(this.index, value);
    }
}
