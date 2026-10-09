package org.taumc.celeritas.mixin.core;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.ByteBuffer;

/**
 * The official Mixin accessor for the {@code BufferBuilder} members the immediate path needs.
 * <p>
 * This replaces the reflection the first revision used ({@code getDeclaredField}/{@code setAccessible} per batch).
 * Mixin's accessor mechanism is the supported way to reach private members: the names below are the project's normal
 * mapped names, so Refine/RetroFuturaGradle remaps them through the refmap like any other mixin reference, and there
 * is no reflective lookup in the hot path at all. {@code getVertexCount()} is public on the target and is therefore
 * called directly rather than accessed.
 */
@Mixin(BufferBuilder.class)
public interface BufferBuilderAccessor {
    @Accessor("byteBuffer")
    ByteBuffer celtium$byteBuffer();

    @Accessor("drawMode")
    int celtium$drawMode();

    @Accessor("vertexFormat")
    VertexFormat celtium$vertexFormat();
}
