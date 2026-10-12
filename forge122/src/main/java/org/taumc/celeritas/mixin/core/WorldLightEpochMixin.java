package org.taumc.celeritas.mixin.core;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * Bumps the light epoch {@link CeltiumLightCache} reads, at the only place the light map is written.
 * <p>
 * {@code World.setLightFor} (SRG {@code func_175653_a}) is the single funnel through which every
 * light change lands on a chunk's light arrays - block placement relights, the day-cycle skylight
 * sweep, {@code checkLight} propagation and mod calls all end here. Bumping on this entry means the
 * render-side cache flushes at exactly the moments a fresh {@code getCombinedLight}/
 * {@code getLightBrightness} read could return something new, so the cached value is never stale the
 * way a vanilla repeated read would never be. A block state change that writes no light keeps the
 * cache - which is also what vanilla would see, since the map has not moved.
 */
@Mixin(World.class)
public abstract class WorldLightEpochMixin {

    @Inject(method = "func_175653_a", at = @At("HEAD"), require = 0)
    private void celtium$noteLightWrite(net.minecraft.world.EnumSkyBlock type,
            net.minecraft.util.math.BlockPos pos, int lightValue, CallbackInfo info) {
        CeltiumLightCache.noteLightWrite();
    }
}
