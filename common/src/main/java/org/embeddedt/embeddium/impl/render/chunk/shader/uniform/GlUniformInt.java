package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

import org.lwjgl.opengl.GL20C;


public class GlUniformInt extends GlUniform<Integer> {
    public GlUniformInt(int index) {
        super(index);
    }

    @Override
    public void set(Integer value) {
        this.setInt(value);
    }

    public void setInt(int value) {
        GL20C.glUniform1i(this.index, value);
    }
}
