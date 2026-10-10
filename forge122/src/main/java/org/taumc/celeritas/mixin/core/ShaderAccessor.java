package org.taumc.celeritas.mixin.core;

import java.util.List;
import net.minecraft.client.shader.Shader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes a pass's auxiliary sampler framebuffers and their declared names.
 * <p>
 * The spider program is the only reachable consumer: its {@code BlurSampler} is bound here to another target of the
 * chain, and the native executor resolves the same target as its second descriptor.
 */
@Mixin(Shader.class)
public interface ShaderAccessor {

    @Accessor("listAuxFramebuffers")
    List<Object> celtium$auxFramebuffers();

    @Accessor("listAuxNames")
    List<String> celtium$auxNames();

    /**
     * {@return the ortho matrix installed by the group's createBindFramebuffers}, the ProjMat the pass's render
     * would have set - needed because the native run replaces that method.
     */
    @Accessor("projectionMatrix")
    org.lwjgl.util.vector.Matrix4f celtium$projectionMatrix();
}
