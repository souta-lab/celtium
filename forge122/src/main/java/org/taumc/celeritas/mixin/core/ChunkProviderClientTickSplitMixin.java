package org.taumc.celeritas.mixin.core;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.soutalab.celtium.vulkan.TickSplit2Index;
import org.soutalab.celtium.vulkan.TickTimings;

/**
 * TickSplit2's {@code tickChunks} child (item 407): the client chunk provider's per-tick unload drain, called
 * from inside {@code WorldClient.tick} (the profiler's {@code chunkCache} section). Bracketed at the method's
 * own edges, once per tick - the per-chunk work inside is not hooked.
 * <p>
 * {@code require = 0}, the tick instruments' rule: a differently shaped tree loses the split, not the game,
 * and the bracket's zero call count in the report says so.
 */
@Mixin(ChunkProviderClient.class)
public abstract class ChunkProviderClientTickSplitMixin {

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void celtium$tickChunksBegin(CallbackInfoReturnable<Boolean> ci) {
        TickTimings.begin(TickSplit2Index.TICK_CHUNKS);
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void celtium$tickChunksEnd(CallbackInfoReturnable<Boolean> ci) {
        TickTimings.end(TickSplit2Index.TICK_CHUNKS);
    }
}
