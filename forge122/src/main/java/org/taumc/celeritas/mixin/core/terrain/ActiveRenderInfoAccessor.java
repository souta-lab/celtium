package org.taumc.celeritas.mixin.core.terrain;

import net.minecraft.client.renderer.ActiveRenderInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.nio.FloatBuffer;

@Mixin(ActiveRenderInfo.class)
public interface ActiveRenderInfoAccessor {
    @Accessor("PROJECTION")
    static FloatBuffer getProjectionMatrix() {
        throw new AssertionError();
    }

    @Accessor("rotationX")
    static float celtium$getRotationX() {
        throw new AssertionError();
    }

    @Accessor("rotationZ")
    static float celtium$getRotationZ() {
        throw new AssertionError();
    }

    @Accessor("rotationXZ")
    static float celtium$getRotationXZ() {
        throw new AssertionError();
    }

    @Accessor("rotationXY")
    static float celtium$getRotationXY() {
        throw new AssertionError();
    }
    @Accessor("MODELVIEW")
    static FloatBuffer getModelViewMatrix() {
        throw new AssertionError();
    }
}
