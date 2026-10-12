package org.taumc.celeritas.mixin.core;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * Entity light lookups through the per-tick cache ({@link CeltiumLightCache}): vanilla
 * {@code Entity.getBrightnessForRender} and {@code getBrightness} run a column-loaded check plus a
 * light query per entity per frame - and the renderer calls them for every rendered entity. World
 * light changes on the tick, not the frame, so identical results are served from the shared
 * tick-epoch map and the {@code MutableBlockPos} the original allocates only materialises on a
 * cache miss.
 * <p>
 * Injected on {@link Entity} itself: subclasses that override the methods keep their own paths, so
 * the cache applies exactly where the base implementation would have run. Kill switch
 * {@code -Dceltium.entityLightCache=false}.
 */
@Mixin(Entity.class)
public abstract class EntityLightCacheMixin {

    @Shadow public double posX, posY, posZ;
    @Shadow public World world;
    @Shadow public abstract float getEyeHeight();

    private static final boolean CELTIUM_ENTITY_LIGHT =
            !"false".equals(System.getProperty("celtium.entityLightCache", "true"));

    @Inject(method = "func_70070_b", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$cachedBrightnessForRender(CallbackInfoReturnable<Integer> info) {
        if (!CELTIUM_ENTITY_LIGHT) {
            return;
        }
        info.setReturnValue(Integer.valueOf(CeltiumLightCache.entityCombinedLight(
                this.world, this.posX, this.posY + this.getEyeHeight(), this.posZ)));
    }

    @Inject(method = "func_70013_c", at = @At("HEAD"), cancellable = true, require = 0)
    private void celtium$cachedBrightness(CallbackInfoReturnable<Float> info) {
        if (!CELTIUM_ENTITY_LIGHT) {
            return;
        }
        info.setReturnValue(Float.valueOf(CeltiumLightCache.entityBrightness(
                this.world, this.posX, this.posY + this.getEyeHeight(), this.posZ)));
    }
}
