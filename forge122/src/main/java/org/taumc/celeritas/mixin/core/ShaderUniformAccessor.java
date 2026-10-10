package org.taumc.celeritas.mixin.core;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import net.minecraft.client.shader.ShaderUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads a post-chain uniform's live values for the native executor.
 * <p>
 * {@code ShaderUniform} keeps its value in a private typed buffer and only ever writes it to GL through
 * {@code upload()}; the native path needs the same numbers for a push-constant block, which is exactly the buffer's
 * contents read in declaration order.
 */
@Mixin(ShaderUniform.class)
public interface ShaderUniformAccessor {

    @Accessor("uniformFloatBuffer")
    FloatBuffer celtium$uniformFloatBuffer();

    @Accessor("uniformIntBuffer")
    IntBuffer celtium$uniformIntBuffer();

    @Accessor("uniformType")
    int celtium$uniformType();

    @Accessor("uniformCount")
    int celtium$uniformCount();
}
