package org.taumc.celeritas.mixin.core;

import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.soutalab.celtium.vulkan.TickSplit2Index;
import org.soutalab.celtium.vulkan.TickTimings;

/**
 * TickSplit2's entity-side children (item 407): {@code tickEnt} over {@code World.updateEntities} as a whole,
 * and {@code tickTileEnt} over just its block-entity section, so the entity share reads as
 * {@code tickEnt - tickTileEnt} in the report.
 * <p>
 * The two live in one method in 1.12.2 - there is no separate tile-entity method to bracket - so the child's
 * edges are the profiler's own section calls inside {@code updateEntities}: the block-entity loop sits between
 * the {@code endStartSection} calls at ordinal 2 ({@code "blockEntities"}) and ordinal 3
 * ({@code "pendingBlockEntities"}), both verified against the SRG class before this was written. The bracket
 * opens and closes once per tick, at the section edges; nothing per entity or per block entity is hooked.
 * <p>
 * {@code require = 0} throughout, the tick instruments' rule: if a patched tree (Cleanroom) reshapes
 * {@code updateEntities}, the split is lost, not the game, and the child's zero call count in the
 * {@code CELTIUM_TICK_SPLIT2} line says so.
 */
@Mixin(World.class)
public abstract class WorldTickSplitMixin {

    @Inject(method = "updateEntities", at = @At("HEAD"), require = 0)
    private void celtium$tickEntBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_ENT);
    }

    @Inject(method = "updateEntities", at = @At("RETURN"), require = 0)
    private void celtium$tickEntEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_ENT);
    }

    @Inject(method = "updateEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    ordinal = 2, shift = At.Shift.AFTER), require = 0)
    private void celtium$tickTileEntBegin(CallbackInfo ci) {
        TickTimings.begin(TickSplit2Index.TICK_TILE_ENT);
    }

    @Inject(method = "updateEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/profiler/Profiler;endStartSection(Ljava/lang/String;)V",
                    ordinal = 3, shift = At.Shift.BEFORE), require = 0)
    private void celtium$tickTileEntEnd(CallbackInfo ci) {
        TickTimings.end(TickSplit2Index.TICK_TILE_ENT);
    }
}
