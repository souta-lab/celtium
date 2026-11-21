package org.embeddedt.embeddium.impl.gl.shader;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

public interface ShaderBindingContext {
    default <U extends GlUniform<?>> U bindUniform(String name, IntFunction<U> factory) {
        var uniform = bindUniformIfPresent(name, factory);

        if (uniform == null) {
            throw new NullPointerException("No uniform exists with name: " + name);
        }

        return uniform;
    }

    <U extends GlUniform<?>> @Nullable U bindUniformIfPresent(String name, IntFunction<U> factory);
}
