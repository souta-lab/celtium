package org.soutalab.celtium.vulkan;

/**
 * The frame-stats report (the backend's {@code FRAME_STATS} switch, resolved through the harness):
 * frame intervals, bracket timings and per-family counters, printed as one CELTIUM_FRAME_STATS line
 * every 120 frames. Moved out of {@link VulkanBackendAdapter} unchanged (2026-11-06).
 */
final class FrameStatsReport {
    private final FrameCoordinator frame;
    private final Lane2ImmediateRecorder funnel;
    VulkanBackendAdapter owner;

    FrameStatsReport(FrameCoordinator frame, Lane2ImmediateRecorder funnel) {
        this.frame = frame;
        this.funnel = funnel;
    }

    /** The frame-stats report's frame intervals and the previous report's counter values. */
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
            this.frameIntervals[funnel.frameIntervalCount++] = now - this.lastFrameBoundaryNanos;
        }
        this.lastFrameBoundaryNanos = now;
        if (funnel.frameIntervalCount < this.frameIntervals.length) {
            return;
        }
        // Snapshotted before the report is built, so the deltas describe the frames this report covers.
        long pipelineBindTotal = VulkanImmediateTextureRecordsPipeline.PIPELINE_BIND_NANOS;
        long descriptorBindTotal = VulkanImmediateTextureRecordsPipeline.DESCRIPTOR_BIND_NANOS;
        long pipelineBindDelta = pipelineBindTotal - this.lastPipelineBindNanos;
        long descriptorBindDelta = descriptorBindTotal - this.lastDescriptorBindNanos;
        long submitDelta = funnel.immediateSubmitNanos - this.lastImmediateSubmitNanos;
        this.lastImmediateSubmitNanos = funnel.immediateSubmitNanos;
        long acceptDecideDelta = funnel.acceptDecideNanos - this.lastAcceptDecideNanos;
        long acceptPreflightDelta = funnel.acceptPreflightNanos - this.lastAcceptPreflightNanos;
        long acceptTailDelta = funnel.acceptTailNanos - this.lastAcceptTailNanos;
        long appendDelta = funnel.immediateStatistics.appendNanos() - this.lastAppendNanos;
        this.lastAcceptDecideNanos = funnel.acceptDecideNanos;
        this.lastAcceptPreflightNanos = funnel.acceptPreflightNanos;
        this.lastAcceptTailNanos = funnel.acceptTailNanos;
        this.lastAppendNanos = funnel.immediateStatistics.appendNanos();
        this.lastAcceptedBatches = funnel.immediateStatistics.immediateBatchesOffered();
        long drawFormDelta = VulkanDynamicMesh.DRAW_FORM_BINDS - this.lastDrawFormBinds;
        long bindFormDelta = VulkanDynamicMesh.BIND_FORM_BINDS - this.lastBindFormBinds;
        this.lastDrawFormBinds = VulkanDynamicMesh.DRAW_FORM_BINDS;
        this.lastBindFormBinds = VulkanDynamicMesh.BIND_FORM_BINDS;
        long arenaPromotionsDelta = (frame.frameArena == null ? 0 : frame.frameArena.promotions()) - this.lastArenaPromotions;
        long arenaBlockedDelta = (frame.frameArena == null ? 0 : frame.frameArena.blockedOpenings()) - this.lastArenaBlocked;
        long arenaFallbacksDelta = frame.submissionStatistics.frameArenaFallbackGroups() - this.lastArenaFallbacks;
        this.lastArenaPromotions = frame.frameArena == null ? 0 : frame.frameArena.promotions();
        this.lastArenaBlocked = frame.frameArena == null ? 0 : frame.frameArena.blockedOpenings();
        this.lastArenaFallbacks = frame.submissionStatistics.frameArenaFallbackGroups();
        long itemPipelineDelta = VulkanImmediateItemRecordsPipeline.ITEM_PIPELINE_NANOS - this.lastItemPipelineNanos;
        long itemDescriptorDelta = VulkanImmediateItemRecordsPipeline.ITEM_DESCRIPTOR_NANOS - this.lastItemDescriptorNanos;
        long itemViewDelta = VulkanImmediateItemRecordsPipeline.ITEM_VIEW_NANOS - this.lastItemViewNanos;
        long itemPushDelta = VulkanImmediateItemRecordsPipeline.ITEM_PUSH_NANOS - this.lastItemPushNanos;
        long itemMeshDelta = VulkanImmediateItemRecordsPipeline.ITEM_MESH_NANOS - this.lastItemMeshNanos;
        this.lastItemPipelineNanos = VulkanImmediateItemRecordsPipeline.ITEM_PIPELINE_NANOS;
        this.lastItemDescriptorNanos = VulkanImmediateItemRecordsPipeline.ITEM_DESCRIPTOR_NANOS;
        this.lastItemViewNanos = VulkanImmediateItemRecordsPipeline.ITEM_VIEW_NANOS;
        this.lastItemPushNanos = VulkanImmediateItemRecordsPipeline.ITEM_PUSH_NANOS;
        this.lastItemMeshNanos = VulkanImmediateItemRecordsPipeline.ITEM_MESH_NANOS;
        long preTransformedDelta = funnel.immediateStatistics.preTransformedGroups() - this.lastPreTransformed;
        long matrixSplitsDelta = funnel.immediateStatistics.matrixSplits() - this.lastMatrixSplits;
        this.lastPreTransformed = funnel.immediateStatistics.preTransformedGroups();
        this.lastMatrixSplits = funnel.immediateStatistics.matrixSplits();
        long textureCountDelta = funnel.textureDrawCount - this.lastTextureDrawCount;
        long itemCountDelta = funnel.itemDrawCount - this.lastItemDrawCount;
        long blockCountDelta = funnel.blockDrawCount - this.lastBlockDrawCount;
        long particleCountDelta = funnel.particleDrawCount - this.lastParticleDrawCount;
        long genericCountDelta = funnel.genericDrawCount - this.lastGenericDrawCount;
        this.lastTextureDrawCount = funnel.textureDrawCount;
        this.lastItemDrawCount = funnel.itemDrawCount;
        this.lastBlockDrawCount = funnel.blockDrawCount;
        this.lastParticleDrawCount = funnel.particleDrawCount;
        this.lastGenericDrawCount = funnel.genericDrawCount;
        long textureDrawDelta = funnel.textureDrawNanos - this.lastTextureDrawNanos;
        long itemDrawDelta = funnel.itemDrawNanos - this.lastItemDrawNanos;
        long blockDrawDelta = funnel.blockDrawNanos - this.lastBlockDrawNanos;
        long particleDrawDelta = funnel.particleDrawNanos - this.lastParticleDrawNanos;
        long genericDrawDelta = funnel.genericDrawNanos - this.lastGenericDrawNanos;
        this.lastTextureDrawNanos = funnel.textureDrawNanos;
        this.lastItemDrawNanos = funnel.itemDrawNanos;
        this.lastBlockDrawNanos = funnel.blockDrawNanos;
        this.lastParticleDrawNanos = funnel.particleDrawNanos;
        this.lastGenericDrawNanos = funnel.genericDrawNanos;
        long viewDelta = VulkanImmediateTextureRecordsPipeline.VIEW_NANOS - this.lastViewNanos;
        long pushDelta = VulkanImmediateTextureRecordsPipeline.PUSH_CALL_NANOS - this.lastPushCallNanos;
        long meshDelta = VulkanImmediateTextureRecordsPipeline.MESH_BIND_NANOS - this.lastMeshBindNanos;
        this.lastViewNanos = VulkanImmediateTextureRecordsPipeline.VIEW_NANOS;
        this.lastPushCallNanos = VulkanImmediateTextureRecordsPipeline.PUSH_CALL_NANOS;
        this.lastMeshBindNanos = VulkanImmediateTextureRecordsPipeline.MESH_BIND_NANOS;
        this.lastPipelineBindNanos = pipelineBindTotal;
        this.lastDescriptorBindNanos = descriptorBindTotal;

        if (Lane2ImmediateRecorder.BUFFER_CENSUS) {
            System.out.println("[Celtium] CELTIUM_BUFFER_CENSUS groups=" + funnel.bufferCensusGroups
                    + " vertexBuffers=" + funnel.bufferCensusVertexBuffers.size()
                    + " indexBuffers=" + funnel.bufferCensusIndexBuffers.size()
                    + " arenaBacked=" + funnel.bufferCensusArenaBacked
                    + " owning=" + funnel.bufferCensusOwning);
            funnel.bufferCensusGroups = 0L;
            funnel.bufferCensusArenaBacked = 0L;
            funnel.bufferCensusOwning = 0L;
            funnel.bufferCensusVertexBuffers.clear();
            funnel.bufferCensusIndexBuffers.clear();
        }
        if (Lane2ImmediateRecorder.GROUP_CENSUS) {
            StringBuilder census = new StringBuilder();
            String[] families = {"texture", "litItem", "unlitItem", "colour", "other"};
            for (int i = 0; i < funnel.groupCensusCounts.length; i++) {
                if (funnel.groupCensusCounts[i] == 0) {
                    continue;
                }
                int family = i / 4;
                int state = i % 4;
                census.append(' ').append(families[family]).append((state & 2) != 0 ? "/blend" : "/opaque")
                        .append((state & 1) != 0 ? "+dw" : "-dw").append('=').append(funnel.groupCensusCounts[i]);
            }
            System.out.println("[Celtium] CELTIUM_GROUP_COMPOSITION groups=" + funnel.groupCensusMain + census);
            java.util.Arrays.fill(funnel.groupCensusCounts, 0L);
            funnel.groupCensusMain = 0;
        }
        if (Lane2ImmediateRecorder.DRAW_KEY_CENSUS) {
            System.out.println("[Celtium] CELTIUM_DRAW_KEY_CENSUS groups=" + funnel.drawKeyGroups
                    + " distinctKeys=" + funnel.drawKeyCounts.size()
                    + " switches=" + funnel.drawKeySwitches
                    + " largestBucket=" + funnel.drawKeyLargest
                    + " arrayKeyRuns=" + (funnel.arrayKeyGroups - funnel.arrayKeySwitches)
                    + " arrayKeySwitches=" + funnel.arrayKeySwitches
                    + " arrayKeyDistinct=" + funnel.arrayKeyCounts.size()
                    + " arrayKeyLargest=" + funnel.arrayKeyLargest);
            funnel.drawKeyGroups = 0L;
            funnel.drawKeySwitches = 0L;
            funnel.drawKeyPrevious = 0L;
            funnel.drawKeyLargest = 0;
            funnel.drawKeyCounts.clear();
            funnel.arrayKeyGroups = 0L;
            funnel.arrayKeySwitches = 0L;
            funnel.arrayKeyPrevious = 0L;
            funnel.arrayKeyLargest = 0;
            funnel.arrayKeyCounts.clear();
        }
        if (Lane2ImmediateRecorder.RETAINED_CENSUS) {
            System.out.println("[Celtium] CELTIUM_RETAINED_CENSUS groups=" + funnel.retainedCensusGroups
                    + " retainedAtAccept=" + funnel.retainedAccept
                    + " preTransformEligible=" + funnel.preTransformEligible
                    + " retained=" + funnel.retainedCensusRetained
                    + " distinctMeshes=" + funnel.retainedCensusByMesh.size()
                    + " largestBucket=" + funnel.retainedCensusLargest);
            funnel.retainedCensusGroups = 0L;
            funnel.retainedAccept = 0L;
            funnel.preTransformEligible = 0L;
            funnel.retainedCensusRetained = 0L;
            funnel.retainedCensusLargest = 0;
            funnel.retainedCensusByMesh.clear();
        }
        long[] sorted = java.util.Arrays.copyOf(this.frameIntervals, funnel.frameIntervalCount);
        java.util.Arrays.sort(sorted);
        long p50 = sorted[sorted.length / 2];
        System.out.println("[Celtium] CELTIUM_FRAME_STATS site=" + where
                + " frames=" + funnel.frameIntervalCount
                + " ms[p50=" + String.format(java.util.Locale.ROOT, "%.2f", p50 / 1e6)
                + " p95=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[(int) (sorted.length * 0.95)] / 1e6)
                + " min=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[0] / 1e6)
                + " max=" + String.format(java.util.Locale.ROOT, "%.2f", sorted[sorted.length - 1] / 1e6) + "]"
                + " fps[p50=" + String.format(java.util.Locale.ROOT, "%.1f", 1e9 / Math.max(1L, p50)) + "] "
                + org.soutalab.celtium.vulkan.FrameCoordinator.submitQueueSummary()
                + " " + org.soutalab.celtium.vulkan.VulkanContext.batchedSubmitSummary()
                + " " + funnel.celtium$prewarmSummary()
                + (Lane2ImmediateRecorder.BATCH_TIMINGS ? funnel.offerStageNanosSummary() + " " + funnel.acceptSpanSummary() + " "
                        + funnel.batchDistSummary() : "")
                + " append[ms=" + (funnel.immediateStatistics.appendNanos() / 1000000L) + "]"
                + " prepare[nanos=" + funnel.immediateStatistics.prepareImmediateNanos()
                + " calls=" + funnel.immediateStatistics.prepareImmediateCalls()
                + " perFrameMs=" + String.format(java.util.Locale.ROOT, "%.2f",
                        funnel.immediateStatistics.prepareImmediateNanos() / 1e6
                                / Math.max(1L, this.debugFrameReports))
                + "]"
                + (Lane2ImmediateRecorder.FRAME_STATS ? " loop[" + celtium$gameLoopSummary() + "]" : "")
                + " texCopy[calls=" + owner.copyReadSide.textureCopyCalls + " totalMs=" + (owner.copyReadSide.textureCopyNanos / 1000000L) + "]"
                + " copyBlit[refusals=" + owner.textureCopyService.textureCopyBlitRefusals()
                + " last=" + owner.textureCopyService.lastTextureCopyBlitRefusal() + "]"
                + " lazyMirror[supplies=" + funnel.lazyMirrorSupplies + " rawWriteRefreshes=" + funnel.rawWriteRefreshes
                + " current=" + funnel.rawWriteAlreadyCurrent
                + " skips=" + funnel.lazyMirrorRetrySkips
                + " failures=" + funnel.lazyMirrorFailures + "(supply=" + funnel.lazyMirrorSupplyFailures
                + " raw=" + funnel.rawWriteFailures + " coalesced=" + funnel.coalescedRawWriteFailures + ")"
                + " deferred=" + funnel.lazyMirrorDeferrals + " empty=" + funnel.lazyMirrorEmptyDeferrals
                + " last=[" + funnel.lastLazyMirrorFailure + "]]"
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
                + funnel.celtium$terrainPerFrameSummary(funnel.frameIntervalCount)
                + " preflush[groupsPerFrame=" + (funnel.preflushFrames == 0 ? 0
                        : funnel.preflushGroupsDrained / funnel.preflushFrames) + "]"
                + " framesNoClear=" + funnel.framesWithNoClearAtAll
                + " drawn[perFrame=" + funnel.celtium$drawnGroupsPerFrame() + "]"
                + " modelMerge[draws=" + funnel.modelRunsDrawn + " merged=" + funnel.modelRunsMerged + "]"
                // How many groups the bake actually opened and how many splits still name the matrix: the bake only pays
                // if the second number falls, and a zero on the first says the predicate never fired.
                + " preTransform[groups=" + (funnel.immediateStatistics.preTransformedGroups() - this.lastPreTransformed)
                + " matrixSplits=" + (funnel.immediateStatistics.matrixSplits() - this.lastMatrixSplits) + "]"
                // Windows per frame: each window is a render pass plus a submission, and the A/B that removed the drawing
                // left 0.5 ms a frame in the window machinery, so this count is what says whether coalescing windows is
                // worth anything.
                + " windows[perFrameX100=" + funnel.celtium$windowsPerFrameX100() + "]"
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
                + " acceptPerFrame[batches=" + (funnel.immediateStatistics.immediateBatchesOffered() - this.lastAcceptedBatches)
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
                + " frameFenceWait[count=" + frame.submissionStatistics.frameFenceWaitCount()
                + " totalMs=" + frame.submissionStatistics.frameFenceWaitMillis()
                + " worstMs=" + frame.submissionStatistics.frameFenceWaitWorstMillis() + "]");
        this.debugFrameReports++;
        funnel.frameIntervalCount = 0;
    }
}
