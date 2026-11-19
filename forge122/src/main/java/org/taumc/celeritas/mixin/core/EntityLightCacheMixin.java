package org.taumc.celeritas.mixin.core;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * Entity light lookups through the per-tick cache ({@link CeltiumLightCache}).
 * <p>
 * {@code Entity.getBrightness} runs a column-loaded check plus a {@code getLightBrightness} query
 * per entity per frame; world light changes on the tick, not the frame, so the result is served
 * from the shared tick-epoch map. This entry stays a caller-side injection because
 * {@code getLightBrightness} does not pass through {@code World#getCombinedLight}.
 * <p>
 * {@code Entity.getBrightnessForRender} is deliberately no longer intercepted (item 378): its
 * vanilla body runs as written and its {@code getCombinedLight} call is answered by the cache
 * inside that method (the funnel, {@code WorldCombinedLightMixin}). The HEAD cancel that used to
 * sit here silently disabled addons injecting on the vanilla method - their injections applied
 * but never ran - for no remaining benefit, since the funnel serves the same cache.
 * <p>
 * Injected on {@link Entity} itself: subclasses that override the method keep their own paths, so
 * the cache applies exactly where the base implementation would have run. Kill switch
 * {@code -Dceltium.entityLightCache=false} (governed inside {@link CeltiumLightCache}).
 */
@Mixin(Entity.class)
public abstract class EntityLightCacheMixin {

    @Shadow public double posX, posY, posZ;
    @Shadow public World world;
    @Shadow public abstract float getEyeHeight();

    @Inject(method = "func_70013_c", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$cachedBrightness(CallbackInfoReturnable<Float> info) {
        info.setReturnValue(Float.valueOf(CeltiumLightCache.entityBrightness(
                this.world, this.posX, this.posY + this.getEyeHeight(), this.posZ)));
    }
}
