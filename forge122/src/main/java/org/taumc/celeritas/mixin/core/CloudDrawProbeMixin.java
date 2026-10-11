package org.taumc.celeritas.mixin.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostic for task-9 (weather, water, clouds, particles): <b>is the game calling the cloud draw at all?</b>
 * <p>
 * The immediate path's own counters answer the other half of the question - how many format-8 batches arrived and
 * how many were accepted - but they cannot separate "the game stopped calling {@code renderClouds}" from "the draw
 * ran and its batch was dropped on the way". The measured fact this exists for: at the cloud pose the bridge leg
 * shows fancy clouds in the captured frame while a GL-less leg on the same jar accepts exactly 128 format-8 batches
 * and every one of them is in {@code frame=1} of {@code CELTIUM_IMMEDIATE_ENQ}. A frame's worth of clouds is 128
 * batches (2 colour-mask passes x 64 cells), so either the later frames never call this method or the batches stop
 * before the uploader. This prints the call count and, beside it, every input the caller's gates read, so the answer
 * is a number and not an inference.
 * <p>
 * Off unless {@code -Dceltium.probe.draws=true}, bounded to the first three calls and every fiftieth after that, so
 * a run carries a handful of lines. Named in the report it is quoted in; remove it once the defect it measures is
 * closed.
 */
@Mixin(RenderGlobal.class)
public abstract class CloudDrawProbeMixin {

    /** Calls seen so far. Static because the handler merges into the target class. */
    private static final java.util.concurrent.atomic.AtomicLong celtium$probeCloudCalls =
            new java.util.concurrent.atomic.AtomicLong();
    /**
     * The uploader-call count each of the first few {@code renderClouds} calls produced.
     * <p>
     * This is the half the call count cannot answer: {@code renderCloudsFancy} builds 128 cells per call (2
     * colour-mask passes x 8x8 cells) and each cell is one {@code Tessellator.draw()}, so a call that reached the
     * uploader must move this counter by about 128. A delta of 0 next to a call that happened says the batches stop
     * inside the draw rather than in the caller, which is the difference between the two candidates the measurement
     * left open ("the game stopped calling" against "the call ran and its batch was lost").
     */
    private static final java.util.concurrent.atomic.AtomicLong celtium$probeCloudUploaderDeltas =
            new java.util.concurrent.atomic.AtomicLong();
    private static long celtium$probeCloudUploaderAtHead;
    private static final java.util.concurrent.atomic.AtomicLong celtium$probeFancyCalls =
            new java.util.concurrent.atomic.AtomicLong();
    private static long celtium$probeFancyUploaderAtHead;
    private static final boolean celtium$PROBE_DRAW_CALLS =
            Boolean.getBoolean("celtium.probe.draws");
    private static final int celtium$PROBE_EVERY =
            Integer.getInteger("celtium.probe.every", 50).intValue();

    @Inject(method = "func_180447_b", at = @At("HEAD"), require = 0)
    private void celtium$probeRenderClouds(float partialTicks, int pass, double x, double y, double z,
                                            CallbackInfo info) {
        if (!celtium$PROBE_DRAW_CALLS) {
            return;
        }
        long calls = celtium$probeCloudCalls.incrementAndGet();
        celtium$probeCloudUploaderAtHead =
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallCount;
        if (calls > 3 && (celtium$PROBE_EVERY <= 0 || calls % celtium$PROBE_EVERY != 0)) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        double eye = y + (mc.getRenderViewEntity() == null ? 0.0D : mc.getRenderViewEntity().getEyeHeight());
        System.out.println("[Celtium] CELTIUM_CLOUD_CALL calls=" + calls
                + " pass=" + pass
                + " tick=" + (mc.world == null ? -1L : mc.world.getTotalWorldTime())
                + " camY=" + Math.round(y * 100.0D) / 100.0D
                + " camYPlusEye=" + Math.round(eye * 100.0D) / 100.0D
                + " belowCloudHeight=" + (eye < 128.0D)
                + " surfaceWorld=" + (mc.world != null && mc.world.provider.isSurfaceWorld())
                + " clouds=" + mc.gameSettings.clouds
                + " shouldRenderClouds=" + mc.gameSettings.shouldRenderClouds()
                + " renderDistance=" + mc.gameSettings.renderDistanceChunks
                + " cloudHeight=" + (mc.world == null ? -1.0F : mc.world.provider.getCloudHeight())
                + " rainStrength=" + (mc.world == null ? -1.0F : mc.world.getRainStrength(partialTicks))
                + " pitchedUp=" + (mc.getRenderViewEntity() == null ? 0.0F
                        : mc.getRenderViewEntity().rotationPitch)
                // The hook vanilla asks first, whose true answer means "a mod took the clouds over" and stops the
                // method before either branch: the measurement that separates "the game never ran the cloud code"
                // from "the code ran and the batch was dropped on the way to the immediate path", which no counter
                // in the renderer can tell apart on its own.
                + " forgeHookTakesClouds="
                + net.minecraftforge.fml.client.FMLClientHandler.instance()
                        .renderClouds(0, partialTicks));
    }

    /** The uploader delta for the call whose head was just seen; bounded prints, same switch. */
    @Inject(method = "func_180447_b", at = @At("RETURN"), require = 0)
    private void celtium$probeRenderCloudsReturn(float partialTicks, int pass, double x, double y, double z,
                                                 CallbackInfo info) {
        if (!celtium$PROBE_DRAW_CALLS) {
            return;
        }
        long calls = celtium$probeCloudCalls.get();
        if (calls > 3 && (celtium$PROBE_EVERY <= 0 || calls % celtium$PROBE_EVERY != 0)) {
            return;
        }
        long delta = org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallCount
                - celtium$probeCloudUploaderAtHead;
        celtium$probeCloudUploaderDeltas.addAndGet(delta);
        System.out.println("[Celtium] CELTIUM_CLOUD_UPLOADER calls=" + calls
                + " uploaderCallsInRenderClouds=" + delta
                + " fancyCalls=" + celtium$probeFancyCalls.get());
    }

    /** How many times the inner builder that owns the 128 cell draws was entered. */
    @Inject(method = "func_180445_c", at = @At("HEAD"), require = 0)
    private void celtium$probeRenderCloudsFancy(float partialTicks, int pass, double x, double y, double z,
                                                CallbackInfo info) {
        if (!celtium$PROBE_DRAW_CALLS) {
            return;
        }
        long calls = celtium$probeFancyCalls.incrementAndGet();
        celtium$probeFancyUploaderAtHead =
                org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.batchCallCount;
        if (calls > 3 && (celtium$PROBE_EVERY <= 0 || calls % celtium$PROBE_EVERY != 0)) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_CLOUD_FANCY calls=" + calls
                + " uploaderCallsBefore=" + celtium$probeFancyUploaderAtHead);
    }
}
