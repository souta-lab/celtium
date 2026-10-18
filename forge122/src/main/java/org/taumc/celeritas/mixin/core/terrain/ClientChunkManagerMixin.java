package org.taumc.celeritas.mixin.core.terrain;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkStatus;
import org.embeddedt.embeddium.impl.render.chunk.map.ChunkTrackerHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Feeds loaded and unloaded client chunks into the world's {@link org.embeddedt.embeddium.impl.render.chunk.map.ChunkTracker}.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-sect1}, on merged source): the client reaches the world, the renderer
 * initialises, and it reports {@code tracker known=0 ready=0} followed by {@code sections=0}. Everything downstream
 * follows from that - the terrain pass has nothing to draw, nothing is submitted, and the present has no picture - so
 * the tracker is the root cause and this is the hook that fills it.
 * <p>
 * The target is the runtime (SRG) name {@code func_73158_c} for {@code loadChunk}: this environment runs SRG-named
 * classes - {@code javap} on {@code srg_merged_minecraft.jar} shows {@code func_71411_J} and friends - and the SRG
 * spelling is the one that has applied every time in this session, including for the guards that had to be verified by
 * their effect.
 * <p>
 * The {@code provideChunk} hook that used to accompany this one is gone, and the source is why: in 1.12.2
 * {@code ChunkProviderClient.provideChunk(x, z)} is "return getLoadedChunk(x, z) or blankChunk" - a pure query that
 * never inserts into {@code chunkMapping}, while {@code loadChunk} is the only writer (the packet path) and
 * {@code func_73234_b} the only remover. A chunk therefore cannot arrive through {@code provideChunk}, so the hook
 * bought nothing and cost a mixin callback (a {@code CallbackInfoReturnable} allocation) plus a tracker map query on
 * every query the game makes - the JFR execution samples measured it at 6.8% of the client thread's world-phase
 * samples (leg imm1, 2,981 samples, celtium$noteChunkAdded 202 self samples plus the Long2IntOpenHashMap traffic it
 * drove). The first version needed it because {@code getLoadedChunk} could return an EmptyChunk; the guard that fixed
 * that stays here in spirit - only {@code loadChunk} now reports, and it reports what it stored.
 * <p>
 * Each add is counted, bounded to a dozen lines, so the next leg says whether the tracker fills.
 */
@Mixin(ChunkProviderClient.class)
public abstract class ClientChunkManagerMixin {
    @Shadow
    @Final
    private World world;

    /** Bounded report counter, so a leg can see the tracker filling. */
    private static int celtium$chunkAddLogs;
    /** The last reported pair, so lines are written on change. */
    private static int celtium$lastKnown = -1;
    private static int celtium$lastReady = -1;

    @Inject(method = "func_73158_c", at = @At("RETURN"), require = 0)
    private void afterLoadChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        celtium$noteChunkAdded(x, z, "loadChunk");
    }

    @Inject(method = "func_73234_b", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/Chunk;onUnload()V", shift = At.Shift.AFTER), require = 0)
    private void afterUnloadChunk(int x, int z, CallbackInfo ci) {
        ChunkTrackerHolder.get(this.world).onChunkStatusRemoved(x, z, ChunkStatus.FLAG_ALL);
    }

    private void celtium$noteChunkAdded(int x, int z, String path) {
        var tracker = ChunkTrackerHolder.get(this.world);
        tracker.onChunkStatusAdded(x, z, ChunkStatus.FLAG_ALL);
        int known = tracker.knownChunkCount();
        int ready = tracker.readyChunkCount();
        // Reported on change rather than for the first N adds: the first version spent its cap on twelve adds of the
        // same chunk (known=1, ready=0) and could not show whether either count ever moved. A line whenever the pair
        // changes says when chunks accumulate and when readiness follows, which is what sections are built from.
        // Readiness is the count that matters, because sections are built from ready chunks and a centre needs all nine
        // of its 3x3 neighbours: with eight known chunks ready is correctly zero, so a cap that stops at eight known
        // cannot show whether the ninth ever arrives. Readiness changes are always reported; the known count is
        // reported every sixty-four chunks as context.
        boolean readyChanged = ready != celtium$lastReady;
        boolean knownMilestone = known != celtium$lastKnown && known % 64 == 0;
        if (readyChanged || knownMilestone) {
            celtium$lastKnown = known;
            celtium$lastReady = ready;
            if (celtium$chunkAddLogs++ < 40) {
                System.out.println("[Celtium] CELTIUM_CHUNK_ADD path=" + path + " known=" + known + " ready=" + ready);
            }
        }
    }
}
