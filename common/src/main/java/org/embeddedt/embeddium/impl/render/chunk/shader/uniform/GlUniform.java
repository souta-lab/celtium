package org.embeddedt.embeddium.impl.render.chunk.shader.uniform;

public abstract class GlUniform<T> {
    protected final int index;

    protected GlUniform(int index) {
        this.index = index;
    }

    public abstract void set(T value);
}
