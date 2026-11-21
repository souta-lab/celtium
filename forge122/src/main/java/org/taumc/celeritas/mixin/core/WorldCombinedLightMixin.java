package org.taumc.celeritas.mixin.core;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.taumc.celeritas.impl.CeltiumLightCache;

/**
 * The light cache's single funnel (item 378, addon connection surface ledger II-7):
 * {@link CeltiumLightCache} is served inside {@code World#getCombinedLight} itself, not at its
 * call sites. Until this change the cache sat in front of the entity, particle and block-entity
 * methods - HEAD-cancelling two of them and wrapping the third's call into this method - which
 * silently disabled addons injecting on those vanilla methods (their injections applied but never
 * ran) and made the block-entity call a contested instruction point. Wrapping the method itself
 * instead keeps the vanilla body as the only computation: the wrapper only decides who answers,
 * the cache or the original body, under the rules in {@link CeltiumLightCache#combinedLightFunnel}.
 * Every caller's own injections - on the calling methods, on this method's call sites, on this
 * method's return - now apply as written.
 */
@Mixin(World.class)
public abstract class WorldCombinedLightMixin {

    @WrapMethod(method = { "func_175626_b", "getCombinedLight" }, require = 0)
    private int celtium$combinedLightFunnel(BlockPos pos, int lightValue, Operation<Integer> original) {
        return CeltiumLightCache.combinedLightFunnel((World) (Object) this, pos, lightValue,
                () -> original.call(pos, lightValue));
    }
}
