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
 * Two things changed here. The targets are the runtime (SRG) names, {@code func_73158_c} for {@code loadChunk} and
 * {@code func_186025_d} for {@code provideChunk}: this environment runs SRG-named classes - {@code javap} on
 * {@code srg_merged_minecraft.jar} shows {@code func_71411_J} and friends - and the SRG spelling is the one that has
 * applied every time in this session, including for the guards that had to be verified by their effect. And
 * {@code provideChunk} is hooked as well as {@code loadChunk}, because the client can reach a chunk through either -
 * {@code loadChunk} on the packet path and {@code provideChunk} whenever a chunk is asked for - and a chunk that
 * arrives through the unhooked one is a chunk the tracker never learns about.
 * <p>
 * Each add is counted, bounded to a dozen lines, so the next leg says which path fires and whether the tracker fills.
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

    @Inject(method = "func_186025_d", at = @At("RETURN"), require = 0)
    private void afterProvideChunk(int x, int z, CallbackInfoReturnable<Chunk> cir) {
        celtium$noteChunkAdded(x, z, "provideChunk");
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
