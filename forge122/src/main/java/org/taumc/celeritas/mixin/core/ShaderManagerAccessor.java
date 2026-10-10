package org.taumc.celeritas.mixin.core;

import java.util.List;
import net.minecraft.client.shader.ShaderManager;
import net.minecraft.client.shader.ShaderUniform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the program's declared uniform list and name for the native executor.
 * <p>
 * {@code shaderUniforms} is in the program JSON's declaration order, which is the order the native push block
 * expects; {@code programFilename} is the program id (for example {@code minecraft:shaders/program/invert.json})
 * the pipeline is keyed by.
 */
@Mixin(ShaderManager.class)
public interface ShaderManagerAccessor {

    @Accessor("shaderUniforms")
    List<ShaderUniform> celtium$shaderUniforms();

    @Accessor("programFilename")
    String celtium$programFilename();
}
