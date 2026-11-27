package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

import org.lwjgl.opengl.GL20C;


public class GlUniformFloat extends GlUniform<Float> {
    public GlUniformFloat(int index) {
        super(index);
    }

    @Override
    public void set(Float value) {
        this.setFloat(value);
    }

    public void setFloat(float value) {
        GL20C.glUniform1f(this.index, value);
    }
}
