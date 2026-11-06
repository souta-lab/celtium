package org.soutalab.celtium.vulkan;

/**
 * The {@code -Dceltium.vulkan.frameStats=true} report: frame intervals, bracket timings and per-family counters,
 * printed as one CELTIUM_FRAME_STATS line every 120 frames. Moved out of {@link VulkanTerrainGpuBackend} unchanged
 * (2026-11-06).
 */
final class FrameStatsReport {
    private final VulkanTerrainGpuBackend b;

    FrameStatsReport(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    /** {@code -Dceltium.vulkan.frameStats=true}'s report: frame intervals and the previous report's counter values. */
    final long[] frameIntervals = new long[120];
    /** How many frame-statistic lines this run has printed, so per-frame means can be reported. */
    long debugFrameReports;
    long lastFrameBoundaryNanos;
    long lastAcceptDecideNanos;
    long lastAcceptPreflightNanos;
    long lastAcceptTailNanos;
    long lastAcceptedBatches;
    long lastAppendNanos;
    long lastArenaBlocked;
    long lastArenaFallbacks;
    long lastArenaPromotions;
    long lastBindFormBinds;
    long lastBlockDrawCount;
    long lastBlockDrawNanos;
    long lastDescriptorBindNanos;
    long lastDrawFormBinds;
    long lastGenericDrawCount;
    long lastGenericDrawNanos;
    long lastImmediateSubmitNanos;
    long lastItemDescriptorNanos;
    long lastItemDrawCount;
    long lastItemDrawNanos;
    long lastItemMeshNanos;
    long lastItemPipelineNanos;
    long lastItemPushNanos;
    long lastItemViewNanos;
    long lastMatrixSplits;
    long lastMeshBindNanos;
    long lastParticleDrawCount;
    long lastParticleDrawNanos;
    long lastPipelineBindNanos;
    long lastPreTransformed;
    long lastPushCallNanos;
    long lastTextureDrawCount;
    long lastTextureDrawNanos;
    long lastViewNanos;

    /**
     * {@return the game loop's own breakdown of the frame}, read reflectively because the counters live in the mod's
     * mixin package and this module does not depend on it. It is what separates tick, display, fps-sync, render and the
     * loop's remainder - the shares a present-to-present interval cannot name.
     */
    String celtium$gameLoopSummary() {
        try {
            Class<?> timing = Class.forName("org.taumc.celeritas.impl.profile.GameLoopTiming");
            Object summary = timing.getMethod("summaryDelta").invoke(null);
            return String.valueOf(summary);
        } catch (ReflectiveOperationException | LinkageError failed) {
            return "unavailable(" + failed.getClass().getSimpleName() + ")";
        }
    }

    void celtium$frameStatsReport(String where) {
        long now = System.nanoTime();
        if (this.lastFrameBoundaryNanos != 0L) {
            this.frameIntervals[b.frameIntervalCount++] = now - this.lastFrameBoundaryNanos;
        }
        this.lastFrameBoundaryNanos = now;
        if (b.frameIntervalCount < this.frameIntervals.length) {
            return;
        }
        // Snapshotted before the report is built, so the deltas describe the frames this report covers.
        long pipelineBindTotal = VulkanImmediateTexturePipeline.PIPELINE_BIND_NANOS;
        long descriptorBindTotal = VulkanImmediateTexturePipeline.DESCRIPTOR_BIND_NANOS;
        long pipelineBindDelta = pipelineBindTotal - this.lastPipelineBindNanos;
        long descriptorBindDelta = descriptorBindTotal - this.lastDescriptorBindNanos;
        long submitDelta = b.immediateSubmitNanos - this.lastImmediateSubmitNanos;
        this.lastImmediateSubmitNanos = b.immediateSubmitNanos;
        long acceptDecideDelta = b.acceptDecideNanos - this.lastAcceptDecideNanos;
        long acceptPreflightDelta = b.acceptPreflightNanos - this.lastAcceptPreflightNanos;
        long acceptTailDelta = b.acceptTailNanos - this.lastAcceptTailNanos;
        long appendDelta = b.immediateStatistics.appendNanos() - this.lastAppendNanos;
        this.lastAcceptDecideNanos = b.acceptDecideNanos;
        this.lastAcceptPreflightNanos = b.acceptPreflightNanos;
        this.lastAcceptTailNanos = b.acceptTailNanos;
        this.lastAppendNanos = b.immediateStatistics.appendNanos();
        this.lastAcceptedBatches = b.immediateStatistics.immediateBatchesOffered();
        long drawFormDelta = VulkanDynamicMesh.DRAW_FORM_BINDS - this.lastDrawFormBinds;
        long bindFormDelta = VulkanDynamicMesh.BIND_FORM_BINDS - this.lastBindFormBinds;
        this.lastDrawFormBinds = VulkanDynamicMesh.DRAW_FORM_BINDS;
        this.lastBindFormBinds = VulkanDynamicMesh.BIND_FORM_BINDS;
        long arenaPromotionsDelta = (b.frameArena == null ? 0 : b.frameArena.promotions()) - this.lastArenaPromotions;
        long arenaBlockedDelta = (b.frameArena == null ? 0 : b.frameArena.blockedOpenings()) - this.lastArenaBlocked;
        long arenaFallbacksDelta = b.submissionStatistics.frameArenaFallbackGroups() - this.lastArenaFallbacks;
        this.lastArenaPromotions = b.frameArena == null ? 0 : b.frameArena.promotions();
        this.lastArenaBlocked = b.frameArena == null ? 0 : b.frameArena.blockedOpenings();
        this.lastArenaFallbacks = b.submissionStatistics.frameArenaFallbackGroups();
        long itemPipelineDelta = VulkanImmediateItemPipeline.ITEM_PIPELINE_NANOS - this.lastItemPipelineNanos;
        long itemDescriptorDelta = VulkanImmediateItemPipeline.ITEM_DESCRIPTOR_NANOS - this.lastItemDescriptorNanos;
        long itemViewDelta = VulkanImmediateItemPipeline.ITEM_VIEW_NANOS - this.lastItemViewNanos;
        long itemPushDelta = VulkanImmediateItemPipeline.ITEM_PUSH_NANOS - this.lastItemPushNanos;
        long itemMeshDelta = VulkanImmediateItemPipeline.ITEM_MESH_NANOS - this.lastItemMeshNanos;
        this.lastItemPipelineNanos = VulkanImmediateItemPipeline.ITEM_PIPELINE_NANOS;
        this.lastItemDescriptorNanos = VulkanImmediateItemPipeline.ITEM_DESCRIPTOR_NANOS;
        this.lastItemViewNanos = VulkanImmediateItemPipeline.ITEM_VIEW_NANOS;
        this.lastItemPushNanos = VulkanImmediateItemPipeline.ITEM_PUSH_NANOS;
        this.lastItemMeshNanos = VulkanImmediateItemPipeline.ITEM_MESH_NANOS;
        long preTransformedDelta = b.immediateStatistics.preTransformedGroups() - this.lastPreTransformed;
        long matrixSplitsDelta = b.immediateStatistics.matrixSplits() - this.lastMatrixSplits;
        this.lastPreTransformed = b.immediateStatistics.preTransformedGroups();
        this.lastMatrixSplits = b.immediateStatistics.matrixSplits();
        long textureCountDelta = b.textureDrawCount - this.lastTextureDrawCount;
        long itemCountDelta = b.itemDrawCount - this.lastItemDrawCount;
        long blockCountDelta = b.blockDrawCount - this.lastBlockDrawCount;
        long particleCountDelta = b.particleDrawCount - this.lastParticleDrawCount;
        long genericCountDelta = b.genericDrawCount - this.lastGenericDrawCount;
        this.lastTextureDrawCount = b.textureDrawCount;
        this.lastItemDrawCount = b.itemDrawCount;
        this.lastBlockDrawCount = b.blockDrawCount;
        this.lastParticleDrawCount = b.particleDrawCount;
        this.lastGenericDrawCount = b.genericDrawCount;
        long textureDrawDelta = b.textureDrawNanos - this.lastTextureDrawNanos;
        long itemDrawDelta = b.itemDrawNanos - this.lastItemDrawNanos;
        long blockDrawDelta = b.blockDrawNanos - this.lastBlockDrawNanos;
        long particleDrawDelta = b.particleDrawNanos - this.lastParticleDrawNanos;
        long genericDrawDelta = b.genericDrawNanos - this.lastGenericDrawNanos;
        this.lastTextureDrawNanos = b.textureDrawNanos;
        this.lastItemDrawNanos = b.itemDrawNanos;
        this.lastBlockDrawNanos = b.blockDrawNanos;
        this.lastParticleDrawNanos = b.particleDrawNanos;
        this.lastGenericDrawNanos = b.genericDrawNanos;
        long viewDelta = VulkanImmediateTexturePipeline.VIEW_NANOS - this.lastViewNanos;
        long pushDelta = VulkanImmediateTexturePipeline.PUSH_CALL_NANOS - this.lastPushCallNanos;
        long meshDelta = VulkanImmediateTexturePipeline.MESH_BIND_NANOS - this.lastMeshBindNanos;
        this.lastViewNanos = VulkanImmediateTexturePipeline.VIEW_NANOS;
        this.lastPushCallNanos = VulkanImmediateTexturePipeline.PUSH_CALL_NANOS;
        this.lastMeshBindNanos = VulkanImmediateTexturePipeline.MESH_BIND_NANOS;
        this.lastPipelineBindNanos = pipelineBindTotal;
        this.lastDescriptorBindNanos = descriptorBindTotal;

        if (VulkanTerrainGpuBackend.BUFFER_CENSUS) {
            System.out.println("[Celtium] CELTIUM_BUFFER_CENSUS groups=" + b.bufferCensusGroups
                    + " vertexBuffers=" + b.bufferCensusVertexBuffers.size()
                    + " indexBuffers=" + b.bufferCensusIndexBuffers.size()
                    + " arenaBacked=" + b.bufferCensusArenaBacked
                    + " owning=" + b.bufferCensusOwning);
            b.bufferCensusGroups = 0L;
            b.bufferCensusArenaBacked = 0L;
            b.bufferCensusOwning = 0L;
            b.bufferCensusVertexBuffers.clear();
            b.bufferCensusIndexBuffers.clear();
        }
        if (VulkanTerrainGpuBackend.GROUP_CENSUS) {
            StringBuilder census = new StringBuilder();
            String[] families = {"texture", "litItem", "unlitItem", "colour", "other"};
            for (int i = 0; i < b.groupCensusCounts.length; i++) {
                if (b.groupCensusCounts[i] == 0) {
                    continue;
                }
                int family = i / 4;
                int state = i % 4;
                census.append(' ').append(families[family]).append((state & 2) != 0 ? "/blend" : "/opaque")
                        .append((state & 1) != 0 ? "+dw" : "-dw").append('=').append(b.groupCensusCounts[i]);
            }
            System.out.println("[Celtium] CELTIUM_GROUP_COMPOSITION groups=" + b.groupCensusMain + census);
            java.util.Arrays.fill(b.groupCensusCounts, 0L);
            b.groupCensusMain = 0;
        }
        if (VulkanTerrainGpuBackend.DRAW_KEY_CENSUS) {
            System.out.println("[Celtium] CELTIUM_DRAW_KEY_CENSUS groups=" + b.drawKeyGroups
                    + " distinctKeys=" + b.drawKeyCounts.size()
                    + " switches=" + b.drawKeySwitches
                    + " largestBucket=" + b.drawKeyLargest
                    + " arrayKeyRuns=" + (b.arrayKeyGroups - b.arrayKeySwitches)
                    + " arrayKeySwitches=" + b.arrayKeySwitches
                    + " arrayKeyDistinct=" + b.arrayKeyCounts.size()
                    + " arrayKeyLargest=" + b.arrayKeyLargest);
            b.drawKeyGroups = 0L;
            b.drawKeySwitches = 0L;
            b.drawKeyPrevious = 0L;
            b.drawKeyLargest = 0;
            b.drawKeyCounts.clear();
            b.arrayKeyGroups = 0L;
            b.arrayKeySwitches = 0L;
            b.arrayKeyPrevious = 0L;
            b.arrayKeyLargest = 0;
            b.arrayKeyCounts.clear();
        }
        if (VulkanTerrainGpuBackend.RETAINED_CENSUS) {
            System.out.println("[Celtium] CELTIUM_RETAINED_CENSUS groups=" + b.retainedCensusGroups
                    + " retainedAtAccept=" + b.retainedAccept
                    + " preTransformEligible=" + b.preTransformEligible
                    + " retained=" + b.retainedCensusRetained
                    + " distinctMeshes=" + b.retainedCensusByMesh.size()
                    + " largestBucket=" + b.retainedCensusLargest);
            b.retainedCensusGroups = 0L;
            b.retainedAccept = 0L;
            b.preTransformEligible = 0L;
            b.retainedCensusRetained = 0L;
            b.retainedCensusLargest = 0;
            b.retainedCensusByMesh.clear();
        }
        long[] sorted = java.util.Arrays.copyOf(this.frameIntervals, b.frameIntervalCount);
        java.util.Arrays.sort(sorted);
        long p50 = sorted[sorted.length / 2];
        System.out.println("[Celtium] CELTIUM_FRAME_STATS site=" + where
                + " frames=" + b.frameIntervalCount
                + " ms[p50=" + String.format(java.util.Locale.ROOT, "%.2f", p50 / 1e6)
                + " p95=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[(int) (sorted.length * 0.95)] / 1e6)
                + " min=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[0] / 1e6)
                + " max=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[sorted.length - 1] / 1e6) + "]"
                + " fps[p50=" + String.format(java.util.Locale.ROOT, "%.1f", 1e9 / Math.max(1L, p50)) + "] "
                + org.soutalab.celtium.vulkan.FrameCoordinator.submitQueueSummary()
                + " " + org.soutalab.celtium.vulkan.VulkanContext.batchedSubmitSummary()
                + (VulkanTerrainGpuBackend.BATCH_TIMINGS ? b.offerStageNanosSummary() + " " + b.acceptSpanSummary() + " "
                        + b.batchDistSummary() : "")
                + " append[ms=" + (b.immediateStatistics.appendNanos() / 1000000L) + "]"
                + " prepare[nanos=" + b.immediateStatistics.prepareImmediateNanos()
                + " calls=" + b.immediateStatistics.prepareImmediateCalls()
                + " perFrameMs=" + String.format(java.util.Locale.ROOT, "%.2f",
                        b.immediateStatistics.prepareImmediateNanos() / 1e6
                                / Math.max(1L, this.debugFrameReports))
                + "]"
                + (VulkanTerrainGpuBackend.FRAME_STATS ? " loop[" + celtium$gameLoopSummary() + "]" : "")
                + " texCopy[calls=" + b.textureCopyCalls + " totalMs=" + (b.textureCopyNanos / 1000000L) + "]"
                + " copyBlit[refusals=" + b.textureCopyService.textureCopyBlitRefusals()
                + " last=" + b.textureCopyService.lastTextureCopyBlitRefusal() + "]"
                + " lazyMirror[supplies=" + b.lazyMirrorSupplies + " rawWriteRefreshes=" + b.rawWriteRefreshes
                + " current=" + b.rawWriteAlreadyCurrent
                + " skips=" + b.lazyMirrorRetrySkips
                + " failures=" + b.lazyMirrorFailures + "(supply=" + b.lazyMirrorSupplyFailures
                + " raw=" + b.rawWriteFailures + " coalesced=" + b.coalescedRawWriteFailures + ")"
                + " deferred=" + b.lazyMirrorDeferrals + " empty=" + b.lazyMirrorEmptyDeferrals
                + " last=[" + b.lastLazyMirrorFailure + "]]"
                // Which textures the supply part of the accept path is actually paying for. The count alone could not
                // say: `rawWriteRefreshes` is 11-12 a world frame on the shipped profile and the supply part is 60% of
                // the accept path, but "a mod re-declaring a texture it did not change" (now suppressed by the stub's
                // whole-image serial rule) and "a small patch landing in a large record" (which this class pays for by
                // re-copying the whole record) are different defects with different repairs. The per-GL-id counts and
                // the wall time in uploadFromGlTexture name which one it is, on the same line as the frame it costs.
                + " mirrorUploads[" + org.soutalab.celtium.vulkan.VulkanGlTexture.fullUploadSummary() + "]"
                // What the terrain pass records per frame. The KPI's other half (terrain 4.5 ms of a 6.8 ms frame) had
                // no per-frame shape: the counters existed as run totals, so "many small indirect draws" and "few big
                // ones" could not be told apart from a log. These are deltas over the frames this report covers.
                + b.celtium$terrainPerFrameSummary(b.frameIntervalCount)
                + " preflush[groupsPerFrame=" + (b.preflushFrames == 0 ? 0
                        : b.preflushGroupsDrained / b.preflushFrames) + "]"
                + " framesNoClear=" + b.framesWithNoClearAtAll
                + " drawn[perFrame=" + b.celtium$drawnGroupsPerFrame() + "]"
                + " modelMerge[draws=" + b.modelRunsDrawn + " merged=" + b.modelRunsMerged + "]"
                + " runFlush" + b.indirectRuns.indirectFlushReasons
                + " refuse" + b.indirectRuns.indirectRefusalCounts
                + " indirect[runs=" + b.indirectRuns.indirectRunsIssued + " singletons=" + b.indirectRuns.indirectSingletonRuns
                + " groups=" + b.indirectRuns.indirectGroupsIssued
                + " runsPerFrameX100=" + b.celtium$indirectRunsPerFrameX100() + "]"
                // How many groups the bake actually opened and how many splits still name the matrix: the bake only pays
                // if the second number falls, and a zero on the first says the predicate never fired.
                + " preTransform[groups=" + (b.immediateStatistics.preTransformedGroups() - this.lastPreTransformed)
                + " matrixSplits=" + (b.immediateStatistics.matrixSplits() - this.lastMatrixSplits) + "]"
                // Windows per frame: each window is a render pass plus a submission, and the A/B that removed the drawing
                // left 0.5 ms a frame in the window machinery, so this count is what says whether coalescing windows is
                // worth anything.
                + " windows[perFrameX100=" + b.celtium$windowsPerFrameX100() + "]"
                // The arena hypothesis for the 6 microseconds a mesh bind costs: a mesh that could not borrow one of the
                // frame arena's slices owns its buffers, and owning buffers means two binds per draw that cannot be
                // deduplicated. Promotion and fallback counts say whether that is happening at all.
                + " arena[promotions=" + arenaPromotionsDelta + " blocked=" + arenaBlockedDelta
                + " fallbacks=" + arenaFallbacksDelta + "]"
                // Which mesh-bind form the frame used: the offset-in-draw form binds the arena's buffers once and is
                // cheap; re-binding a slice offset per group is the expensive one, and only happens when the slice
                // offset is not a multiple of the vertex stride.
                + " forms[draw=" + drawFormDelta + " bind=" + bindFormDelta + "]"
                // The accept path's split per frame, not cumulatively: the JFR puts submitImmediateBatch at the top of
                // the render thread and the counters for its parts existed, but only as run totals.
                + " acceptPerFrame[batches=" + (b.immediateStatistics.immediateBatchesOffered() - this.lastAcceptedBatches)
                + " decideMs=" + (acceptDecideDelta / 1_000_000L)
                + " preflightMs=" + (acceptPreflightDelta / 1_000_000L)
                + " tailMs=" + (acceptTailDelta / 1_000_000L)
                + " appendMs=" + (appendDelta / 1_000_000L)
                // The batch method's whole time against the parts measured inside it. Whatever the gap is, is work in
                // submitImmediateBatchTimed that no bracket named yet - the figure that decides whether the accept side
                // can explain the ~2 ms the JFR attributes to it.
                + " submitMs=" + (submitDelta / 1_000_000L) + "]"
                + " binds[pipelineMs=" + (pipelineBindDelta / 1_000_000L)
                + " descriptorMs=" + (descriptorBindDelta / 1_000_000L) + "]"
                + " variants[textureMs=" + (textureDrawDelta / 1_000_000L)
                + " itemMs=" + (itemDrawDelta / 1_000_000L)
                + " blockMs=" + (blockDrawDelta / 1_000_000L)
                + " particleMs=" + (particleDrawDelta / 1_000_000L)
                + " genericMs=" + (genericDrawDelta / 1_000_000L)
                + " counts[texture=" + textureCountDelta + " item=" + itemCountDelta
                + " block=" + blockCountDelta + " particle=" + particleCountDelta
                + " generic=" + genericCountDelta + "]]"
                + " itemParts[pipelineMs=" + (itemPipelineDelta / 1_000_000L)
                + " descriptorMs=" + (itemDescriptorDelta / 1_000_000L)
                + " viewMs=" + (itemViewDelta / 1_000_000L)
                + " pushMs=" + (itemPushDelta / 1_000_000L)
                + " meshMs=" + (itemMeshDelta / 1_000_000L) + "]"
                + " drawParts[viewMs=" + (viewDelta / 1_000_000L)
                + " pushCallMs=" + (pushDelta / 1_000_000L)
                + " meshBindMs=" + (meshDelta / 1_000_000L) + "]"
                // The frame's own serialisation: the fence wait on a slot, counted where vkWaitForFences is called. The
                // pre-terrain flush reads 2.8 ms with zero groups, so this is the number that says whether that time is
                // waiting for the GPU rather than working.
                + " frameFenceWait[count=" + b.submissionStatistics.frameFenceWaitCount()
                + " totalMs=" + b.submissionStatistics.frameFenceWaitMillis()
                + " worstMs=" + b.submissionStatistics.frameFenceWaitWorstMillis() + "]");
        this.debugFrameReports++;
        b.frameIntervalCount = 0;
    }
}
