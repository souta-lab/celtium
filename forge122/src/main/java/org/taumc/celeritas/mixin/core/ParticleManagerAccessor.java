package org.taumc.celeritas.mixin.core;

import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.ArrayDeque;

/**
 * Reads {@code ParticleManager.fxLayers}, which is private and which {@link ParticleDrawProbeMixin} needs to say
 * whether the particle draw it counts had anything in its layers. The same accessor pattern as
 * {@code BufferBuilderAccessor}: a compile-time reference the refmap remaps, rather than a reflective lookup per
 * frame. The declared type is the field's own ({@code ArrayDeque<Particle>[][]}), so the accessor's descriptor
 * matches the target's.
 */
@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {

    @Accessor("fxLayers")
    ArrayDeque<?>[][] celtium$getFxLayers();
}
