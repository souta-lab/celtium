package org.soutalab.celtium.vulkan;

import java.util.List;
import java.util.ArrayList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The backend's diagnostic reports and bounded diagnostic lines, moved out of {@link VulkanBackendAdapter}
 * unchanged: what they read is the backend's state, through {@link #b}; nothing here changes what is drawn.
 */
final class BackendDiagnostics {
    private FrameCoordinator frame;
    private Lane2ImmediateRecorder funnel;
    private PresentSwapchain present;
    private VulkanBackendAdapter seatOwner;

    BackendDiagnostics() {
    }

    void bind(FrameCoordinator frame, Lane2ImmediateRecorder funnel, PresentSwapchain present, VulkanBackendAdapter owner) {
        this.frame = frame;
        this.funnel = funnel;
        this.present = present;
        this.seatOwner = owner;
    }

    /** The snapshot lines {@link #diagnostics()} builds and {@code record} appends to. */
    private final List<String> lines = new ArrayList<>();

    /** The distinct {@link #record} messages already printed; bounded so a message carrying a counter cannot grow it. */
    final java.util.Set<String> printedRecords = new java.util.HashSet<String>();

    void record(String diagnostic) {
        this.addLine(diagnostic);
        // Also printed: diagnostics() rebuilds the list on every call, so a recorded failure (an ordering flush that
        // dropped its batches, a stalled device, a refused window) was discarded unprinted the next time the report or
        // F3 asked - RLCraft's 741 ordering-flush failures were visible only as a counter. One tag, so the log
        // limiter bounds it (-Dceltium.debug.logLinesPerTag lifts the bound for a verification run).
        // Each distinct message once: some records are informational and repeat every frame (the light map's name),
        // and thousands of copies would bury the failures this line exists for.
        if (this.printedRecords.size() < 4096 && this.printedRecords.add(diagnostic)) {
            System.out.println("[Celtium] CELTIUM_RECORD " + diagnostic);
        }
    }

    /** {@return the current snapshot lines (what the backend's describe() appends).} */
    List<String> lines() {
        return this.lines;
    }

    /** Appends one recorded line (the backend's record() seat). */
    void addLine(String diagnostic) {
        this.lines.add(diagnostic);
    }


    /**
     * The bindless binding's state for the summary line: how many textures the array holds and how
     * many releases are still waiting on the completion currency. "absent" when the binding was
     * never created (or the device/kill switch refused it) - the successor of the old textureArray
     * segment, in the same place on the line.
     */
    private String bindlessSummary() {
        org.soutalab.celtium.vulkan.bindless.BindlessTextureBinding binding =
                frame.context == null ? null : frame.context.celtium$bindlessBindingOrNull();
        return binding == null ? "absent"
                : "registered" + binding.registeredCount() + "/pending" + binding.pendingReleases();
    }

    /**
     * Registrations the 4096-entry bindless table refused because it was full. A refused texture's
     * draws stay on the per-texture path, so this counts the textures whose draws fell back - the
     * registration-side reading of the slicing design's bindlessOverflowDraws counter (the draw-side
     * count belongs to the stage 4 execution loop, which is the path that would observe the draws
     * themselves). Printed unconditionally: the gate is that the counter appears, value 0 included.
     */
    private long bindlessOverflowDraws() {
        org.soutalab.celtium.vulkan.bindless.BindlessTextureBinding binding =
                frame.context == null ? null : frame.context.celtium$bindlessBindingOrNull();
        return binding == null ? 0L : binding.overflowRegistrations();
    }

    /**
     * The shared texture upload service's counters (S3R-3 landing A): batches submitted, pixel
     * bytes submitted, and bytes the flushes left queued by deferral. Zero when no upload has
     * created the service yet - printed unconditionally, like the bindless counters above, so a
     * leg reads the service's activity (or its absence) from the summary line itself.
     */
    private String textureUploadSummary() {
        TextureUploadService service =
                frame.context == null ? null : frame.context.celtium$textureUploadServiceOrNull();
        return "textureUploadSubmits=" + (service == null ? 0L : service.submits())
                + " textureUploadBytes=" + (service == null ? 0L : service.uploadedBytes())
                + " textureUploadDeferredBytes=" + (service == null ? 0L : service.deferredBytesTotal());
    }

    /**
     * The mesh producer's own budget account counters (budget layer 2, option C): the resolved
     * mesh budget and what the mesh side deferred against it. These are the mesh side's own
     * numbers - before the account existed, a leg had to attribute mesh deferrals by subtracting
     * the texture counter from the shared ledger's total, an identity that held per run by
     * measurement, not by construction. Zero counts when no burst arrival has resolved the
     * account yet - printed unconditionally, like the texture counters above; the budget value
     * still resolves in that case, so the line names the budget a burst would meet.
     */
    private String meshUploadBudgetSummary() {
        // The mesh upload budget retired with the old terrain path (S2-b); the counters it
        // reported no longer exist, so the summary names the retirement instead.
        return "meshUploadBudgetBytes=0 meshUploadDeferredBytes=0 meshUploadDeferredCount=0"
                + " meshUploadDeferredOutstanding=0 meshUploadDeferredCapForce=0";
    }

    /**
     * The shared frame ring's per-slot state (ring records separation, observation point 1):
     * bytes handed out, the high-water mark between resets, the holds still standing and whether
     * a confirmed reset is deferred behind them. This is the reading that separates the
     * separation record's seat 1 (a reset that can never apply because a hold never releases)
     * from seat 2 (plain consumption pressure): holds standing at rest name the first, a cursor
     * pinned at capacity with no holds names the second. Read through the non-creating peek - a
     * summary must not create the ring to report its absence. Printed unconditionally.
     */
    private String frameRingSummary() {
        org.soutalab.celtium.vulkan.hal.FrameLinearRing ring =
                frame.context == null ? null : frame.context.celtium$frameLinearRingOrNull();
        if (ring == null) {
            return "absent";
        }
        StringBuilder out = new StringBuilder();
        for (int slot = 0; slot < ring.slots(); slot++) {
            if (slot > 0) {
                out.append(';');
            }
            out.append('s').append(slot)
                    .append(":used=").append(ring.used(slot))
                    .append(",high=").append(ring.highWater(slot))
                    .append(",holds=").append(ring.holds(slot))
                    .append(",deferred=").append(ring.resetDeferred(slot));
        }
        return out.toString();
    }

    /**
     * The records store's state (ring records separation, F-4 instruments, merged with the
     * bindless leg diagnosis section 2.7's observation points): which home the records live in,
     * per slot the remembered demand, the bytes the home has handed out against its capacity and
     * the selections that found no room, and the refusal split - no slice, growth, bad block -
     * kept apart from the batch route's own drop counts, so a leg can tell a records refusal
     * (drawing survives on the direct path) from a draw that was actually lost. The end-of-run
     * reading is also the residual-holds evidence's other half: {@code selectFail} climbing while
     * {@code used} sits at capacity is the exhaustion signature, whatever home the records use.
     * Printed unconditionally.
     */
    private String recordsStoreSummary() {
        InstanceDataStore store = funnel.drawRecords;
        if (store == null) {
            return "absent";
        }
        StringBuilder out = new StringBuilder("pool=").append(store.sourceKind());
        for (int slot = 0; slot < store.slots(); slot++) {
            out.append(";s").append(slot)
                    .append(":demand=").append(store.demandBytes(slot))
                    .append(",used=").append(store.sourceUsedBytes(slot))
                    .append(",cap=").append(store.sourceCapacityBytes(slot))
                    .append(",selectFail=").append(store.selectFailures(slot));
        }
        out.append(";refused=noSlice").append(store.refusalsNoSlice())
                .append("/grow").append(store.refusalsGrow())
                .append("/block").append(store.refusalsBlock())
                .append(",written=").append(store.recordsWritten());
        return out.toString();
    }

    /**
     * The non-quad emitter census (R-23 strip/fan occurrence census): how many non-quad groups the
     * drain offered the emitter, by GL mode, and what became of them - drawn by the emitter itself
     * (emitted), delegated to the direct draw and drawn there (delegated), or drawn by neither
     * route (dropped). The four columns close the per-mode identity structurally
     * (offered = emitted + delegated + dropped); the delegation used to be an unnamed difference
     * between the first two (drawpath audit DP-12). Zero modes are omitted; the field prints
     * unconditionally, so a scene with no non-quad groups reads as empty lists rather than a
     * missing field.
     */
    private String nonQuadByModeSummary() {
        return "offered=" + nonQuadModeCounts(funnel.nonQuadOfferedByMode)
                + " emitted=" + nonQuadModeCounts(funnel.nonQuadEmittedByMode)
                + " delegated=" + nonQuadModeCounts(funnel.nonQuadDelegatedByMode)
                + " dropped=" + nonQuadModeCounts(funnel.nonQuadDroppedByMode);
    }

    private static String nonQuadModeCounts(long[] counts) {
        StringBuilder out = new StringBuilder();
        for (int mode = 0; mode < counts.length; mode++) {
            if (counts[mode] == 0L) {
                continue;
            }
            if (out.length() > 0) {
                out.append(',');
            }
            out.append(glPrimitiveModeName(mode)).append('=').append(counts[mode]);
        }
        return out.toString();
    }

    /** The GL primitive modes 0-7 by name: the census arrays' index space. */
    private static String glPrimitiveModeName(int mode) {
        switch (mode) {
            case 0: return "GL_POINTS";
            case 1: return "GL_LINES";
            case 2: return "GL_LINE_LOOP";
            case 3: return "GL_LINE_STRIP";
            case 4: return "GL_TRIANGLES";
            case 5: return "GL_TRIANGLE_STRIP";
            case 6: return "GL_TRIANGLE_FAN";
            case 7: return "GL_QUADS";
            default: return "mode" + mode;
        }
    }

    String verificationSummary() {
        return "celtium/device[dynamicRendering="
                + (frame.context != null && frame.context.dynamicRenderingSupported()) + "] "
                // DP-01/DP-02/DP-03/DP-29: the silent-loss censuses, printed unconditionally like the bracket
                // readings above - a loss counter that only prints when diagnostics are on is the invisibility
                // these audits closed. offerDeaths is the pre-backend population offerRefusals cannot see;
                // nativeDrawSkips is the submission-side population stalledGeometryDrops cannot see.
                + "celtium/offerDeaths[" + OfferFunnelCensus.summary() + "] "
                + "celtium/nativeDrawSkips[" + NativeDrawSkipCensus.summary() + "] "
                + "celtium/noCameraSkips[" + NoCameraSkipCensus.summary() + "] "
                + "celtium/vulkan-check passes=" + funnel.terrainStatistics.passSubmissions() + " skipped=" + funnel.terrainStatistics.passSkips() + " errors="
                + funnel.terrainStatistics.passErrors() + " lastDraw=" + funnel.terrainStatistics.lastDrawCommands() + " lastSections=" + funnel.terrainStatistics.lastSectionsDrawn()
                + " skipReason=" + (funnel.terrainStatistics.lastSkipReason() == null ? "none" : funnel.terrainStatistics.lastSkipReason().replace(' ', '_'))
                + " immediateState=" + funnel.immediateStatistics.immediateStateCalls() + " immediateOffered=" + funnel.immediateStatistics.immediateBatchesOffered()
                + " frameId=" + frame.frameId + " flushCount=" + funnel.immediateStatistics.flushCount()
                + " bindless=" + bindlessSummary()
                + " bindlessOverflowDraws=" + bindlessOverflowDraws()
                + " " + textureUploadSummary()
                + " " + (funnel.modelInstances == null ? "modelInstances[absent failed=" + funnel.modelInstancesFailed + "]"
                        : funnel.modelInstances.describe())
                + " textureIndex=hit" + funnel.textureIndexHits + "/miss" + funnel.textureIndexMisses
                // The offer state-key cache's hit rate. It was collected and never printed, so "the key is cached"
                // could not be told from "the cache misses on every offer" - and the cache was a single entry keyed on
                // the layout id, which is exactly the shape that misses on a format alternation. Printed here so the
                // next leg answers it with a number instead of a reading of the code.
                + " stateKeyCache=hit" + funnel.celtium$stateKeyCacheSummary()
                + " orderPreservingFlushes=" + funnel.immediateStatistics.orderPreservingFlushes()
                + " actualFlushes=" + funnel.immediateStatistics.actualFlushes()
                + " actualFlushMillis=" + (funnel.immediateStatistics.actualFlushNanos() / 1000000L)
                + " actualFlushWorstMillis=" + (funnel.immediateStatistics.actualFlushWorstNanos() / 1000000L)
                + " actualFlushCpuMillis=" + (funnel.immediateStatistics.actualFlushCpuNanos() / 1000000L)
                // Where a batch's time goes, per batch, with -Dceltium.vulkan.batchTimings=true. The counters existed but
                // were never printed, so the question "is the per-batch cost the state capture, the key, the program query or
                // the preparation" had no answer in a run.
                + (funnel.BATCH_TIMINGS ? funnel.immediateStatistics.batchTimingSummary() + funnel.offerStageNanosSummary() : "")
                // "off" rather than "0/0": the per-frame hash is opt-in, and a counter that can only read zero would
                // invite "no two frames were identical" from a number that was never measured.
                + " identicalFrameSignatures=" + (funnel.FRAME_SIGNATURE
                        ? frame.identicalFrameSignatures + "/" + frame.comparedFrameSignatures : "off")
                // Task-60: the wait facts, so the JSON report names the wait instead of a reader guessing. The
                // bootstrap path's waits have no API bound at all; the frame path's fence wait has a 5 s one and is
                // the only place that sets gpuStalled. Worst is the number that says whether either is a hazard.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " bootstrapQueueWaitMillis=" + VulkanContext.bootstrapQueueWaitMillis
                + " bootstrapQueueWaitWorstMillis=" + VulkanContext.bootstrapQueueWaitWorstMillis
                + " bootstrapSubmitWaits=" + VulkanContext.bootstrapSubmitWaits
                + " submitReasons=" + frame.context.submitReasons()
                + " bootstrapSubmitWaitMillis=" + VulkanContext.bootstrapSubmitWaitMillis
                + " bootstrapSubmitWaitWorstMillis=" + VulkanContext.bootstrapSubmitWaitWorstMillis
                + " " + frame.context.uploadBatchSummary()
                + " " + (frame.frameArena == null ? "frameArena=not-created" : frame.frameArena.describe())
                + " frameRing=[" + frameRingSummary() + "]"
                + " recordsStore=[" + recordsStoreSummary() + "]"
                + " celtium/nonQuadByMode[" + nonQuadByModeSummary() + "]"
                // The batch route's two loss counters, promoted from the GROUP_CENSUS-gated route line
                // (drawpath audit DP-12): the X-1 reader table showed no leg script reads the route line,
                // so on a default leg a dropped draw was counted nowhere a reader looks. Beside them, the
                // offer funnel's aggregate refusal count (DP-01) - the per-format refusal detail lives in
                // the Forge layer and prints only under diagnostics, so this total is the always-readable
                // half of that pair.
                + " batchDroppedDraws=" + funnel.batchDroppedDraws
                + " batchDiscardedSegments=" + funnel.batchDiscardedSegments
                + " offerRefusals=" + funnel.offerRefusals
                + " frameArenaFallbackGroups=" + frame.submissionStatistics.frameArenaFallbackGroups()
                + " frameSlots=" + frame.FRAME_SLOTS + " frameSlotReuseWaits=" + frame.scheduler.slotReuseWaits()
                + " slotPolls=" + frame.scheduler.slotPolls() + " slotPollConfirms=" + frame.scheduler.slotPollConfirms()
                + " slotWaitConfirms=" + frame.scheduler.slotWaitConfirms()
                + " submitSequence=" + frame.scheduler.submitSequence()
                + " completedSubmitSequence=" + frame.scheduler.completedSubmitSequence()
                + " frameFenceWaitMillis=" + frame.submissionStatistics.frameFenceWaitMillis()
                + " frameFenceWaitCount=" + frame.submissionStatistics.frameFenceWaitCount()
                + " frameFenceWaitWorstMillis=" + frame.submissionStatistics.frameFenceWaitWorstMillis()
                + " " + VulkanHostBuffer.allocationSummary()
                + " uniform" + "retired"
                + " readResolvedFromLastDrawnTarget=" + seatOwner.textureCopyService.readResolvedFromLastDrawnTarget()
                + " offerStages=[" + offerStageSummary() + "]"
                + " windowOpenSites=[" + funnel.windowOpenSiteSummary() + "]"
                + " windowRecordMillis=" + (funnel.windowRecordNanos / 1000000L)
                + " windowRecords=" + funnel.windowRecords
                + " windowRecordPerFrameMillis=" + (frame.frameId == 0L ? -1.0
                        : Math.round((double) funnel.windowRecordNanos / 1000000.0 / frame.frameId * 100.0) / 100.0)
                + " drawSites=[" + funnel.drawSiteSummary() + "]"
                + " abParity=[" + funnel.abParitySummary() + "]"
                + " pushComponents=[" + funnel.pushComponentSummary() + "]"
                + " pushBlockHits=" + funnel.pushBlockHits
                + " pushBlockMisses=" + funnel.pushBlockMisses
                + " pushBlockEntries=" + funnel.pushBlocks.size()
                + " pushBlockClears=" + funnel.pushBlockClears
                + " retainedGeometry=[" + (funnel.retainedGeometry == null ? "unused" : funnel.retainedGeometry.summary()) + "]"
                + " batchReuseHits=" + funnel.batchReuseHits
                + " batchReuseMisses=" + funnel.batchReuseMisses
                + " batchReuseLastFrameSize=" + funnel.batchReuseLastFrame.size()
                + " pushElided=" + VulkanPushCache.SHARED.skipped()
                + " pushIssued=" + VulkanPushCache.SHARED.issued()
                + " splashSourcedGroups=" + funnel.targetStatistics.splashSourcedGroups()
                + " splashCanvasInvalidations=" + funnel.targetStatistics.splashCanvasInvalidations()
                + " immediateDrawSiteLogged=" + funnel.immediateStatistics.immediateDrawSiteLogged()
                + " unflushedTargetGroupsDropped=" + funnel.targetStatistics.unflushedTargetGroupsDropped()
                + " unflushedTargetDropTargets=[" + funnel.unflushedTargetDropSummary() + "]"
                + " drainedOtherTargetPasses=" + funnel.targetStatistics.drainedOtherTargetPasses()
                + " flushEntries=[" + flushEntrySummary() + "]"
                + " submitTimeouts=" + VulkanContext.submitTimeouts
                + " gpuStalled=" + frame.gpuStalled
                // Drawpath audit DP-04/DP-05/DP-06/DP-36: the terrain executors' and pack
                // path's silent-failure quantities, previously visible only as one printed
                // line at the moment of failure (or, for region skips, only inside the
                // first 16 CELTIUM_PACK_TERRAIN_DRAWN lines). All are zero in a healthy
                // run, which is the shape the leg gates read: a disabled executor, a
                // latched pipeline failure, an atlas backoff, a pack refusal, a skipped
                // region or a broken Demonica bridge can no longer hide behind a
                // summary that never mentions them.
                + " terrainEngines=[" + "retired" + "]"
                + " failedPipelines=" + 0L
                + " atlasRefreshFailures=" + funnel.atlasRefreshFailures
                + " packTerrainRefused=" + funnel.packTerrainRefused
                + " packTerrainSkippedRegions=" + funnel.packTerrainSkippedRegions
                + " demonicaBridge=[" + "retired" + "]"
                + " deviceWaitToken=" + frame.submissionStatistics.deviceWaitToken()
                + " stalledGeometryDrops=" + frame.submissionStatistics.stalledGeometryDrops()
                + " stalledGeometryDroppedBatches=" + frame.submissionStatistics.stalledGeometryDroppedBatches()
                + " stallOrigin=" + (frame.submissionStatistics.stallOrigin() == null ? "none" : frame.submissionStatistics.stallOrigin().replace(' ', '_'))
                + " windowFlushFailures=" + funnel.targetStatistics.windowFlushFailures()
                + " windowFlushFailureFrame=" + funnel.targetStatistics.windowFlushFailureFrame()
                + " groupViewportSets=" + funnel.immediateStatistics.groupViewportSets()
                + " stateSkipsViewport=" + funnel.immediateStatistics.stateSkipsViewport()
                + " stateSkipsScissor=" + funnel.immediateStatistics.stateSkipsScissor()
                + " stateSkipsDepthBias=" + funnel.immediateStatistics.stateSkipsDepthBias()
                + " cullEnabledGroups=" + funnel.immediateStatistics.cullEnabledGroups()
                + " cullDisabledGroups=" + funnel.immediateStatistics.cullDisabledGroups()
                // The signature cache's hit rate: the capture it replaces is twenty-one inputs and two hash lookups on
                // every offer, so "how often the versions answered for it" is the number that says whether the cache
                // is doing anything on this scene. hits/misses, printed rather than inferred.
                + " stateSignatureCache=" + funnel.celtium$signatureCacheSummary()
                // The offer key cache's hit rate, beside the signature cache's: a hit skips the sixteen-int mapping,
                // the hash and the per-offer census, and leaves the scratch array for the slow path to materialise.
                // The two rates differ because this key also carries the layout id, which changes between offers the
                // signature does not.
                + " stateKeyCache=" + funnel.celtium$stateKeyCacheSummary()
                // BH-11 and BH-12's counters, at the same place as BH-08's. acceptance found
                // immediateDepthBiasedGroups declared and incremented with zero print sites, so "the bias was
                // applied" had no evidence in any artifact; the narrowed-line total had the same gap (only the
                // first four events printed). Both are now in the verification summary.
                + " immediateDepthBiasedGroups=" + funnel.immediateStatistics.immediateDepthBiasedGroups()
                // Every bracket's open count, enabled or not: an empty framePassSplit cannot say whether a bracket
                // ran, and the missing HUD turns on exactly that (GuiIngameMixin's `gui` bracket against a HUD that may
                // never be drawn).
                + " passBegins=[" + FramePassTimings.passBeginCallsSummary() + "]"
                // Which textures and formats each identity carried: the stage census the HUD question needs, because an
                // identity's first group says nothing about the families that arrive later in the frame.
                + " targetStages=[" + funnel.celtium$targetStagesSummary() + "]"
                + " framePassSplit=[" + FramePassTimings.summary() + "]"
                + " terrainDrawCallMillis=" + (0L / 1_000_000L)
                + " terrainBindMillis=" + (funnel.terrainBindNanos / 1_000_000L)
                + " terrainPacketMillis=" + (0L / 1_000_000L)
                + " terrainBatchMillis=" + (0L / 1_000_000L)
                + " terrainIndirectSetupMillis=" + (0L / 1_000_000L)
                + " terrainIndirectBindMillis=" + (0L / 1_000_000L)
                + " terrainPacketCacheHits=" + 0L
                + " terrainBindSkips=" + 0L
                + " terrainPassBeginMillis=" + (funnel.terrainPassBeginNanos / 1_000_000L)
                + " terrainUniformMillis=" + (funnel.terrainUniformNanos / 1_000_000L)
                + " polygonOffsetFillEnabled=" + funnel.rasterState.polygonOffsetFillEnabled()
                + " polygonOffsetCalled=" + funnel.rasterState.polygonOffsetCalled()
                // BH-16: a clear that names one of the game's own framebuffers is dropped instead of being applied
                // to the shared chain pair, so the counter is what says the fix fired and the owner is what makes it
                // attributable - the panorama's faces were wiped by exactly such a clear before this existed.
                + " gameClearsDroppedForeignTarget=" + (funnel.offTargetTarget() == null ? -1
                        : funnel.offTargetTarget().gameClearsDroppedForeignTargetCount())
                + " lastDroppedClearOwner=" + (funnel.offTargetTarget() == null ? -1
                        : funnel.offTargetTarget().lastDroppedClearOwnerName())
                + " lineBatchesNarrowed=" + funnel.immediateStatistics.lineBatchesNarrowed()
                // BH-07/BH-10/BH-03's counters, at the same place as BH-08's: captured per batch, so a leg says how
                // many batches carried an alpha pair the old pipelines could not express, how many carried a write
                // mask that was not all four channels, and which alpha comparison reached the shader. Before the fix
                // the first two were zero by construction (the capture never read them) and the third did not exist.
                + " blendGroups=" + funnel.immediateStatistics.blendGroups()
                + " blendAlphaNonDefaultGroups=" + funnel.immediateStatistics.blendAlphaNonDefaultGroups()
                + " partialColorMaskGroups=" + funnel.immediateStatistics.partialColorMaskGroups()
                + " lastPartialColorMask=0x" + Integer.toHexString(funnel.immediateStatistics.lastPartialColorMask())
                + " colorMaskHistogram=" + funnel.colorMaskHistogramText()
                + " nonAddBlendEquationGroups=" + funnel.immediateStatistics.nonAddBlendEquationGroups()
                + " scissorBoundarySplits=" + funnel.immediateStatistics.scissorBoundarySplits()
                + " lightParamBoundarySplits=" + funnel.immediateStatistics.lightParamBoundarySplits()
                + " scissorCapturedGroups=" + funnel.immediateStatistics.scissorCapturedGroups()
                + " scissorLateCapturedGroups=" + funnel.immediateStatistics.scissorLateCapturedGroups()
                + " alphaFuncGroups=" + funnel.immediateStatistics.alphaFuncGroups()
                + " nonGreaterAlphaFuncGroups=" + funnel.immediateStatistics.nonGreaterAlphaFuncGroups()
                + " alphaTestDisabledGroups=" + funnel.immediateStatistics.alphaTestDisabledGroups()
                + " lastAlphaFunc=" + funnel.immediateStatistics.lastAlphaFunc()
                + " alphaSelectorCounts=" + funnel.alphaSelectorSummary()
                + " samplerFilters=" + VulkanGlTexture.samplerFilterCensus()
                + " lastAlphaPushRef=" + funnel.round3(funnel.immediateStatistics.lastAlphaPushReference())
                + " alphaReferenceValues=" + funnel.alphaReferenceValueSummary()
                + " windowFlushRetainedBatches=" + funnel.targetStatistics.windowFlushRetainedBatches()
                + " lastWindowFlushFailure=" + (funnel.lastWindowFlushFailure == null ? "none"
                        : funnel.lastWindowFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, funnel.lastWindowFlushFailure.length())))
                // The ordering flush's own bounded catch: the site the user's loading screen latched at before it was
                // bounded, so the next leg reads whether it still happens and what it throws.
                + " orderingFlushFailures=" + funnel.immediateStatistics.orderingFlushFailures()
                + " orderingFlushDroppedBatches=" + funnel.immediateStatistics.orderingFlushDroppedBatches()
                + " lastOrderingFlushFailure=" + (funnel.lastOrderingFlushFailure == null ? "none"
                        : funnel.lastOrderingFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, funnel.lastOrderingFlushFailure.length())))
                + " batchCallCount=" + funnel.batchCallCount
                + " batchCallMillis=" + (funnel.batchCallNanos / 1000000L)
                + " batchCallWorstMillis=" + (funnel.batchCallWorstNanos / 1000000L)
                + " batchCallOpeningMillis=" + (funnel.batchCallOpeningNanos / 1000000L)
                + " batchCallOpeningCount=" + funnel.batchCallOpeningCount
                + " batchCallCpuMillis=" + (funnel.batchCallCpuNanos / 1000000L)
                + " threadCpuSupported=" + org.soutalab.celtium.vulkan.Lane2ImmediateRecorder.THREAD_CPU_SUPPORTED
                + " threadWaitedMillis=" + funnel.threadWaitedMillis()
                + " threadBlockedMillis=" + funnel.threadBlockedMillis()
                + " threadCpuTotalMillis=" + (funnel.threadCpuNanos() / 1000000L)
                + " uniformPreparationFailures=" + funnel.immediateStatistics.uniformPreparationFailures()
                + " lastUniformPreparationFailure=" + funnel.immediateStatistics.lastUniformPreparationFailure()
                + " regionUploadFailures=" + funnel.regionUploadFailures
                + " sectionsLeftUnbuilt=" + 0L
                + " deferredUploads=[batches=" + 0L
                + " outputs=" + 0L
                + " drains=" + 0L
                + " drained=" + 0L
                + " maxQueued=" + 0L
                + " maxDrain=" + 0L + "]"
                + " " + meshUploadBudgetSummary()
                + " terrainIndirect=[offers=" + 0L
                + " calls=" + 0L
                + " draws=" + 0L
                + " clamps=" + 0L
                + " growths=" + 0L
                + " fallbacks=" + 0L
                + " lastFallback=" + "retired" + "]"
                + " terrainIndirectStream=[indirectDraws=" + 0L
                + " indirectChecksum=" + Long.toHexString(0L)
                + " directDraws=" + 0L
                + " directChecksum=" + Long.toHexString(0L)
                + " match=" + (0L == 0L
                        && 0L == 0L)
                + "]"
                + " " + org.soutalab.celtium.vulkan.VulkanArena.alignmentSummary()
                + " lastRegionUploadFailure=" + funnel.lastRegionUploadFailure
                + " innerWallCpu=[preflight " + (funnel.immediateStatistics.prepareImmediateNanos() / 1000000L) + "/"
                + (funnel.preflightCpuNanos / 1000000L) + ", append " + (funnel.immediateStatistics.appendNanos() / 1000000L) + "/"
                + (funnel.appendCpuNanos / 1000000L) + ", groupCreate " + (funnel.immediateStatistics.groupCreateNanos() / 1000000L) + "/"
                + (funnel.groupCreateCpuNanos / 1000000L) + "]"
                + " prepareImmediateCalls=" + funnel.immediateStatistics.prepareImmediateCalls()
                + " prepareImmediateMillis=" + (funnel.immediateStatistics.prepareImmediateNanos() / 1000000L)
                + " groupCreateCount=" + funnel.immediateStatistics.groupCreateCount() + " animatedUploadFlushes=" + funnel.animatedUploadFlushes
                + " inWorldAnimMirror=" + funnel.inWorldAnimMirrorCalls + "/" + (funnel.inWorldAnimMirrorNanos / 1000L) + "us/worst"
                + (funnel.inWorldAnimMirrorWorstNanos / 1000L) + "us" + " sharedIndexRegenerations=" + funnel.sharedIndexRegenerations
                + " groupCreateMillis=" + (funnel.immediateStatistics.groupCreateNanos() / 1000000L)
                + " appendCount=" + funnel.immediateStatistics.appendCount()
                + " appendMillis=" + (funnel.immediateStatistics.appendNanos() / 1000000L)
                + " programQueryCount=" + funnel.immediateStatistics.programQueryCount()
                + " programQueryMillis=" + (funnel.immediateStatistics.programQueryNanos() / 1000000L)
                + " matrixCopyMillis=" + (funnel.immediateStatistics.matrixCopyNanos() / 1000000L)
                + " captureStateMillis=" + (funnel.immediateStatistics.captureStateNanos() / 1000000L)
                + " stateKeyMillis=" + (funnel.immediateStatistics.stateKeyNanos() / 1000000L)
                + " prepareCache=" + funnel.prepareCacheSummary()
                + " groupDrawCount=" + funnel.immediateStatistics.groupDrawCount()
                + " groupDrawMillis=" + (funnel.immediateStatistics.groupDrawNanos() / 1000000L)
                + " groupCloneCount=" + funnel.immediateStatistics.groupCloneCount()
                + " groupCloneMillis=" + (funnel.immediateStatistics.groupCloneNanos() / 1000000L)
                + " preTransformedGroups=" + funnel.immediateStatistics.preTransformedGroups()
                + " preTransformedBatches=" + funnel.immediateStatistics.preTransformedBatches()
                + " preTransformedVertices=" + funnel.immediateStatistics.preTransformedVertices()
                + " selfCompositeSkips=" + funnel.immediateStatistics.selfCompositeSkips()
                + " framebufferHintFailures=" + funnel.framebufferHintFailures
                + " pairNameSamplingDraws=" + funnel.pairNameSamplingDraws
                + " presentedDumpFailure=" + (funnel.swapchain == null ? "none" : funnel.swapchain.lastDumpFailure())
                + " matrixSplits=" + funnel.immediateStatistics.matrixSplits()
                + " groupSplits[" + funnel.groupSplitCensus.summary() + "]"
                + " groupCloses=" + funnel.groupClosesForcingStart
                + " flushBeforeGlDraw=" + funnel.flushBeforeGlDrawCalls
                + " flushBeforeGlDrawWithGroups=" + funnel.flushBeforeGlDrawWithGroups
                + " mergeRefusals=" + funnel.mergeRefusalSummary()
                // One packet's time, split at the one boundary every immediate family shares. groupDrawMillis is the
                // caller's total; pushing is what remains after the bind and draw halves below are taken out.
                + " packetPipelineMillis=" + PacketCost.pipelineBindMillis()
                + " packetPipelineBinds=" + PacketCost.pipelineBinds()
                + " packetPipelineMicros=" + PacketCost.pipelineBindMicros()
                + " packetPushMillis=" + PacketCost.pushMillis()
                + " packetPushMicros=" + PacketCost.pushMicros()
                + " packetBindMillis=" + PacketCost.bindMillis()
                + " packetDrawMillis=" + PacketCost.drawMillis()
                + " packetCalls=" + PacketCost.packets()
                + " packetRuns[" + funnel.packetRunCensus.summary() + "]"
                + " packetBindMicros=" + PacketCost.bindMicros()
                + " packetDrawMicros=" + PacketCost.drawMicros()
                + " packetBindSkips=" + PacketCost.bindSkips()
                + " " + funnel.groupSplitCensus.affineSummary()
                + " fastMergedBatches=" + funnel.immediateStatistics.fastMergedBatches()
                + " prepLookupMillis=" + (funnel.immediateStatistics.prepLookupNanos() / 1000000L)
                + " prepTerrainPipelineMillis=" + (funnel.immediateStatistics.prepTerrainPipelineNanos() / 1000000L)
                + " prepRegistryMillis=" + (funnel.immediateStatistics.prepRegistryNanos() / 1000000L)
                + " regAnnouncedCalls=" + VulkanTextureRegistry.ANNOUNCED_CALLS
                + " regAnnouncedMillis=" + (VulkanTextureRegistry.ANNOUNCED_NANOS / 1000000L)
                + " regStaleCalls=" + VulkanTextureRegistry.STALE_CALLS
                + " regStaleMillis=" + (VulkanTextureRegistry.STALE_NANOS / 1000000L)
                + " regHitCalls=" + VulkanTextureRegistry.HIT_CALLS
                + " regAnnouncedSkips=" + VulkanTextureRegistry.ANNOUNCED_SKIPS
                + " prepBranchMillis=" + (funnel.immediateStatistics.prepBranchNanos() / 1000000L)
                + " descriptorHits=" + VulkanImmediateTextureRecordsPipeline.DESCRIPTOR_HITS
                + " descriptorMisses=" + VulkanImmediateTextureRecordsPipeline.DESCRIPTOR_MISSES + " clearOrderFlushes=" + funnel.immediateStatistics.clearOrderFlushes()
                + " clearOrderZeroMask=" + funnel.clearOrderFlushesZeroMask
                + " clearOrderSkipped=" + funnel.clearOrderSkipped + " clearOrderNoTargetMask=" + funnel.clearOrderNoTargetMask
                + " mainClearsQueued=" + funnel.mainClearsQueuedCount()
                + " mainClearsRescued=" + funnel.mainClearsRescuedCount()
                + " mainClearRefusals=[" + funnel.mainClearRefusalSummary() + "]"
                + " flushReasons=[" + funnel.flushReasonSummary() + " flushSiteCounts=" + funnel.flushSiteSummary() + "]"
                + " offTargetByTarget=[" + funnel.offTargetSummary() + "] offTargetTargets=" + funnel.targetStatistics.offTargetTargetsCreated()
                + " refusedBeforeAppend=" + funnel.immediateStatistics.refusedBeforeAppend()
                + " partialMirrorRefusals=" + funnel.immediateStatistics.partialMirrorRefusals()
                + " partialMirrorsCompleted=" + funnel.immediateStatistics.partialMirrorsCompleted()
                + " offTargetPassesSkippedEmpty=" + funnel.targetStatistics.offTargetPassesSkippedEmpty()
                + " offTargetPerTarget=[" + funnel.offTargetPerTarget() + "]"
                + " offTargetPairs=[" + funnel.frameTargetPairSummary() + "]"
                + " offTargetPairClears=[default-pair=" + funnel.defaultPairClears
                + ",chain-pair=" + funnel.chainPairClears + "]"
                + " gameClearAttribution=[latchedForFrame=" + funnel.targetStatistics.gameClearsLatchedForFrame()
                + ",routedToPairs=" + funnel.gameClearsRoutedToPairs
                + ",queuedOnGroups=" + funnel.gameClearsQueued
                + ",inPassArm=" + funnel.gameClearsInPassArm
                + ",appliedInPass=" + funnel.gameClearsAppliedInPass
                + ",standaloneSubmits=" + funnel.gameClearsSubmittedStandalone
                + ",latchedForPairs=" + funnel.gameClearsLatchedForPairs + "]"
                + " offTargetPairFramebuffers=[default-pair=" + funnel.targetRegistry.pairFramebufferCreations(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR)
                + ",chain-pair=" + funnel.targetRegistry.chainPairFramebufferCreationsTotal() + "]"
                + " otherTargetGroups=[atMainFlush=" + funnel.targetStatistics.otherTargetGroupsAtMainFlush()
                + ",discarded=" + seatOwner.otherTargetGroupsDiscarded
                + ",windowFlush=" + funnel.otherTargetGroupsFlushedByWindow
                + ",enqueuedForOtherTargets=" + funnel.groupsEnqueuedForOtherTargets
                + ",acceptedForOtherTargets=" + funnel.acceptedForOtherTargets + "]"
                + " immediateRecordedBatches=" + funnel.immediateStatistics.immediateRecordedBatches()
                + " immediateSubmittedBatches=" + funnel.immediateStatistics.immediateSubmittedBatches()
                + " immediateCompletedBatches=" + funnel.immediateStatistics.immediateCompletedBatches()
                + " immediateRetries=" + funnel.immediateStatistics.immediateRetryCount()
                + " invalidImmediateRecords=" + funnel.immediateStatistics.invalidImmediateRecords()
                + " backendCommandBuffer=" + Long.toHexString(frame.commandBuffer.address())
                + " backendCommandState=" + frame.commandState.state()
                + " offTargetRefusals=" + (funnel.offTargetRefusalsByReason.isEmpty()
                        ? "none" : funnel.offTargetRefusalsByReason.toString())
                // Off-target passes whose routing failed and were redirected into the frame image
                // instead (drawpath audit DP-33): the pass still draws, into a different image, so
                // without this count the redirect was visible only in the last failure's text.
                + " offTargetRedirects=" + funnel.offTargetRedirects
                + " offTargetOffered=" + funnel.targetStatistics.offTargetOffered() + " offTargetAccepted=" + funnel.targetStatistics.offTargetAccepted()
                + " offTargetDrawn=" + funnel.targetStatistics.offTargetDrawn() + " offTargetCopiedBack=" + funnel.targetStatistics.offTargetCopiedBack()
                + " outsideWorldPassAccepted=" + funnel.targetStatistics.outsideWorldPassAccepted()
                + " descriptorSets=" + funnel.descriptorSetCensus()
                // BH-20's census, beside the descriptor sets it shares a lifetime with: how many pipeline builds failed
                // after a stage had created handles and were rolled back rather than leaked. Zero on a normal leg is
                // expected; the injected-failure leg is what distinguishes "nothing failed" from "not wired".
                + " pipelineBuildRollbacks=" + VulkanImmediateTextureRecordsPipeline.BUILD_ROLLBACKS.get()
                + " inPlaceRefreshes=" + (funnel.textureRegistry != null ? funnel.textureRegistry.inPlaceRefreshes() : 0)
                + " mirroredTextures=" + (funnel.textureRegistry != null ? funnel.textureRegistry.mirrorCount() : 0)
                + " externalImages=[" + (funnel.textureRegistry != null
                        ? funnel.textureRegistry.externalSummary() : "none") + "]"
                // Condition (c) evidence: what the swapchain last presented, and whether the present was accepted. With
                // presentMarker the image is cleared to magenta (1,0,1), whose 8-bit channel mean is ~170, so a
                // presented mean near 170 says the swapchain image held this renderer's frame rather than nothing.
                + " presentSucceeded=" + (funnel.swapchain != null && funnel.swapchain.lastPresentSucceeded())
                + " presentSubmitDone=" + (funnel.swapchain != null && funnel.swapchain.lastPresentSubmitDone())
                + " depthFormat=" + org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName()
                + " depthFormatDecision=[" + org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary() + "]"
                // Which leg of the clear-attribution A/B this run is. On by default; the control leg sets
                // -Dceltium.vulkan.gameClearLatch=false, and a reader of one report must be able to tell which is
                // which without a log. Surfaced here and not in b.diagnostics(): that list is not part of the report.
                + " gameClearLatch=" + (funnel.celtium$gameClearLatchEnabled() ? "on" : "off")
                + " presentedMean=" + (funnel.swapchain != null ? funnel.swapchain.lastPresentedMean() : -1.0f)
                + " swapPointCalls=" + present.swapPointCalls
                + " swapPointMillis=" + (present.swapPointNanos / 1000000L)
                + " swapPointFlushMillis=" + (present.swapPointFlushNanos / 1000000L)
                + " swapPointWorstMs=" + (present.swapPointWorstNanos / 1000000L)
                + " presentStages[" + (funnel.swapchain != null
                        ? org.soutalab.celtium.vulkan.VulkanSwapchain.presentStageSummary() : "none") + "]"
                + " swapPointImageMillis=" + (present.swapPointImageNanos / 1000000L)
                + " swapPointImageMs[p50/p95/worst=" + frame.celtium$distribution(present.swapPointImageRing, present.swapPointImageRingCount) + "]"
                + " submitToFenceMs[p50/p95/worst=" + frame.celtium$distribution(frame.submitToFenceRing, frame.submitToFenceRingCount) + "]"
                + " swapPointDumpMillis=" + (present.swapPointDumpNanos / 1000000L)
                + " swapPointPollMillis=" + (present.swapPointPollNanos / 1000000L)
                // The swap point's swallowed exceptions (DP-14): a leg's completion gate reads this
                // == 0, so a present path that threw its frames away cannot pass as a quiet run.
                + " swapPointExceptions=" + VulkanBackendAdapter.swapPointExceptions
                + " presentMarkerExpected=170.0_magenta_1_0_1"
                + " fogModeSource=native-owner"
                // The lit ITEM family's fog: 0 lit-fog groups with a 256-byte block means the fix is compiled but never
                // written, and the skip counter names the remaining unfogged shape (a non-perspective projection).
                + " litFogGroups=" + funnel.litFogGroupCount()
                + " litFogSkippedNonPerspective=" + funnel.litFogSkipCount()
                + " boundTextureSamples=" + funnel.textureStats.boundTextureComparisons()
                + " boundTextureMismatches=" + funnel.textureStats.boundTextureMismatches()
                + " announcedDynamicTextures=" + funnel.textureStats.announcedDynamicTextures()
                + " colorMirrorUses=" + funnel.textureStats.colorMirrorUses()
                // The refusal counters belong in this summary, not in b.diagnostics(): b.diagnostics() is not surfaced in
                // the report (it is a separate list, and the report keeps ten items), while this string is the one the
                // fixture reads. shaderProgramRefusals is what the custom-program shutter took out of Vulkan.
                + " modelDraws=" + funnel.modelDraws
                + " modelEnqueueMillis=" + funnel.modelStats.enqueueMillis()
                + " modelEnqueueCalls=" + funnel.modelStats.enqueueCalls()
                + " modelEnqueueMicrosPerCall=" + funnel.modelStats.enqueueMicrosPerCall()
                + " modelRepeatsPrev=" + funnel.modelStats.repeatReplays()
                + " modelRepeatsPercent=" + funnel.modelStats.repeatPercent()
                // What the replay input cache skipped: a hit is a 3x3 inverse + two square roots (light) or a 4x4
                // multiply (projection) that produced bytes already in hand. The miss side is what a scene that
                // repeats nothing would pay, so the pair is the measurement of this optimisation on that scene.
                + " modelReplayCache[" + funnel.modelReplayInputCache.describe() + "]"
                // The whole-replay fast path: hits are replays whose record this path wrote without resolving the
                // state, the texture, the affine, the light map or the lit payload again; the miss reasons say which
                // half of the guard a scene fails, so a scene that repeats nothing reports that instead of a saving.
                + " modelFastPath[" + funnel.modelReplayFastPath.describe() + "]"
                + " texMatrixGroups=" + funnel.immediateStatistics.texMatrixGroups()
                + " modelDrawRefusals=" + funnel.modelDrawRefusals
                + " modelRunDropped=" + funnel.modelRunDropped
                + " modelInstancesFailed=" + funnel.modelInstancesFailed
                + " modelMissingMeshes=" + seatOwner.modelGeometry.modelMissingMeshes()
                + " modelLastRefusal=" + funnel.modelLastRefusal
                + " modelMeshLists=" + (seatOwner.modelGeometry.modelMeshes() == null ? -1 : seatOwner.modelGeometry.modelMeshes().size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + seatOwner.modelGeometry.modelMeshQuads()
                + " modelMeshFailure=" + seatOwner.modelGeometry.modelMeshFailure()
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " shaderProgramRefusals=" + funnel.immediateStatistics.shaderProgramRefusals()
                // Lane 3 reports its refusal total and the fixed categories consumed by the loss gate.
                + " " + org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus.refusalSummary()
                // The native-pass refusals (DP-37): until now they existed only in the
                // CELTIUM_NATIVE_PASS census line at snapshot time, outside this verify body.
                + " nativePassExecRefusals=" + funnel.nativePassRegistry.executionRefusalCount()
                + " nativePassCompileRefusals=" + funnel.nativePassRegistry.compileRefusalCount()
                + " acceptedBatches=" + funnel.immediateStatistics.frameAccepted()
                + " imageFilledMirrors=" + (funnel.textureRegistry != null ? funnel.textureRegistry.imageUploads() : 0)
                + " imageUploadsSkipped="
                + (funnel.textureRegistry != null ? funnel.textureRegistry.imageUploadsSkipped() : 0)
                + " announcedUploads=" + funnel.textureStats.announcedUploads() + " animatedImageFills=" + funnel.textureStats.animatedImageFills()
                // The animation announcement's one-shot skip: a patch whose capture the announcement made
                // unnecessary. It is a saving, not a loss, so it is counted beside the fills it replaces.
                + " announcedCaptureSkips="
                + org.soutalab.celtium.vulkan.TextureCaptureStore.celtium$announcedCaptureSkips()
                + " textureCopiesServed=" + seatOwner.textureCopyService.textureCopiesServed()
                + " textureCopiesRefused=" + seatOwner.textureCopyService.textureCopiesRefused()
                + " textureCopiesReusedInFrame=" + seatOwner.textureCopyService.textureCopiesReusedInFrame()
                + " textureCopyReadbacks=" + seatOwner.textureCopyService.textureCopyReadbacks()
                + " textureCopyReadbackMillis=" + (seatOwner.textureCopyService.textureCopyReadbackNanos() / 1000000L)
                // The native route's own census: a copy the GPU wrote into the mirror directly, against one the blit
                // refused (which then took the host route above). The two lines together are what says which path a
                // leg's copies took, rather than the absence of a readback implying the blit ran.
                + " textureCopiesServedPeakPerPresent=" + seatOwner.textureCopyService.textureCopiesServedPeakPerPresent()
                + " textureCopiesReServedAfterContentChange="
                + seatOwner.textureCopyService.textureCopiesReServedAfterContentChange()
                + " textureCopyBlits=" + seatOwner.textureCopyService.textureCopyBlits()
                + " textureCopyBlitRefusals=" + seatOwner.textureCopyService.textureCopyBlitRefusals()
                + " lastTextureCopyBlitRefusal=[" + seatOwner.textureCopyService.lastTextureCopyBlitRefusal() + "]"
                + " lastTextureCopyRefusal=[" + seatOwner.textureCopyService.lastTextureCopyRefusal() + "]"
                // The pair clear-once contract: applied clears against the clears a second identity's pass asked for
                // and did not get. Without the second number the rule's effect is inferred from a picture.
                + " pairClearsRefusedOtherIdentity=" + funnel.targetStatistics.pairClearsRefusedOtherIdentity()
                + " windowDrawsToFrameImage=" + funnel.targetStatistics.windowDrawsToFrameImage()
                + " windowFramesMarkedPresentable=" + funnel.targetStatistics.windowFramesMarkedPresentable()
                + " lastPairClearRefusal=[" + funnel.targetStatistics.lastPairClearRefusal() + "]"
                + " vanillaPostRuns=" + frame.vanillaPostRuns()
                + " vanillaPostPasses=" + frame.vanillaPostPasses()
                + " postChainCreationFailures=" + frame.postChainCreationFailures()
                + " postChainUnsupportedRefusals=" + frame.postChainUnsupportedRefusals()
                + " chainInputFlushFailures=" + frame.chainInputFlushFailures()
                + " " + StageGate.system().summary()
                + " " + VulkanContext.pipelineCreationSummary()
                // The prewarm's fallback census, beside the creation summary it qualifies: a warm
                // window with no synchronous creations reads very differently when the number of
                // draws it substituted is zero than when it is not.
                + " " + funnel.celtium$prewarmSummary()
                + " " + VulkanContext.memoryAllocationSummary()
                + " " + FallbackPaths.summary()
                + " " + frame.context.celtium$slabPoolSummary()
                + " " + frame.context.celtium$slabDrainSummary()
                + " " + funnel.framebufferCompositor.describe()
                + " directMirrorFromAnnouncement=" + funnel.textureStats.directMirrorFromAnnouncement()
                + " dynamicTextureFills=" + funnel.textureStats.dynamicTextureFills()
                + " readbackByOrigin=" + (funnel.textureRegistry != null ? funnel.textureRegistry.readbackByOrigin() : "none")
                + " mirrorsCreatedByAnnouncement="
                + (funnel.textureRegistry != null ? funnel.textureRegistry.createdByAnnouncement() : 0)
                + " swapchainOutOfDate=" + (funnel.swapchain != null ? (funnel.swapchain.outOfDate() ? 1 : 0) : -1)
                + " acquireOutOfDate=" + (funnel.swapchain != null ? funnel.swapchain.acquireOutOfDate() : -1)
                + " retiredFlushes=" + frame.submissionStatistics.retiredFlushes() + " retire=" + VulkanRetireQueue.describe()
                + " lastFrameAccepted=" + funnel.immediateStatistics.lastFrameAccepted() + " lastFrameDrawn=" + funnel.immediateStatistics.lastFrameDrawn()
                + " immediateTaken=" + funnel.immediateStatistics.immediateTaken() + " immediateGroupsFlushed=" + funnel.immediateStatistics.immediateGroupsFlushed()
                + " rawWriteGate=[skips=" + funnel.rawWriteGateSkips + " flushes=" + funnel.rawWriteGateFlushes + "]"
                + " immediateGroupsClosed=" + funnel.immediateStatistics.immediateGroupsClosed()
                + " immediateMaxGroupVertices=" + funnel.immediateStatistics.immediateMaxGroupVertices()
                + " immediateRefusal=" + funnel.celtium$refusalText().replace(' ', '_')
                + " snapshotCaptures=" + funnel.immediateStatistics.snapshotCaptures() + " snapshotServed=" + funnel.immediateStatistics.snapshotServedCaptures()
                + " textureRefusalsBeforeAccept=" + funnel.textureStats.textureRefusalsBeforeAccept()
                + " postAcceptDroppedGroups=" + funnel.immediateStatistics.postAcceptDroppedGroups()
                // The post-accept loss aggregate (drawpath audit DP-10): the loss counters are scattered
                // across three statistics owners, so no single key let a reader gate on "nothing accepted
                // was lost". The members: postAcceptDroppedGroups (ordering-flush replacement drops),
                // orderingFlushDroppedBatches, stalledGeometryDroppedBatches, unflushedTargetGroupsDropped,
                // staleGroupRefusals and immediateGroupsDiscarded. Each member counts in its own unit
                // (groups or batches, as its seat defines them); the aggregate is a zero gate - it is read
                // as "== 0", where the units cannot mislead.
                + " totalPostAcceptDrops=" + (funnel.immediateStatistics.postAcceptDroppedGroups()
                        + funnel.immediateStatistics.orderingFlushDroppedBatches()
                        + frame.submissionStatistics.stalledGeometryDroppedBatches()
                        + funnel.targetStatistics.unflushedTargetGroupsDropped()
                        + funnel.immediateStatistics.staleGroupRefusals()
                        + funnel.immediateStatistics.immediateGroupsDiscarded())
                + " particleDescriptorRefusals=" + funnel.immediateStatistics.particleDescriptorRefusals()
                + " gpuStalled=" + frame.gpuStalled + " stalledStops=" + frame.submissionStatistics.stalledStops()
                + " publishedFrameTexturesCreated=" + funnel.publishedTextures.created()
                + " publishedFrameTexturesUnpublished=" + funnel.publishedTextures.unpublished()
                // The attach-side rule's counter: a published name that stands for the image the pass attaches is kept
                // out of the sampling-side transition, which is the VUID-00900 fix. It must be visible, because a rule
                // that silently stops transitioning an image is indistinguishable from one that forgot to.
                + " publishedFrameTexturesSkippedForAttach=" + funnel.publishedTextures.skippedForAttach()
                // A name whose image was recreated under it is unpublished at the point of use: a barrier on the freed
                // handle was measured as a validation error followed by a SIGSEGV inside libvulkan_radeon.
                + " publishedFrameTexturesStalePruned=" + funnel.publishedTextures.stalePruned()
                + " publishedFrameTexturesRepublished=" + funnel.publishedTextures.republished()
                // A group that outlived its texture wrapper: the descriptor is rebuilt from the current wrapper, or the
                // group is refused. Both are counted because "the picture lost a batch" and "the device was lost" are
                // different outcomes and only the counter says which happened.
                + " staleGroupDescriptorsRebuilt=" + funnel.immediateStatistics.staleGroupDescriptorsRebuilt()
                + " evictedSetDescriptorsRebuilt=" + funnel.immediateStatistics.evictedSetDescriptorsRebuilt()
                + " staleGroupRefusals=" + funnel.immediateStatistics.staleGroupRefusals()
                + " stalledTeardownSkips=" + frame.submissionStatistics.stalledTeardownSkips()
                + " idleWaits=" + frame.submissionStatistics.idleWaits() + " injectedIdleFailures=" + frame.submissionStatistics.injectedIdleFailures()
                // A timed-out fence wait is retried before the latch: a resize or a swapchain rebuild produces transient
                // timeouts, and latching on the first one stopped the renderer for the session (measured on the
                // world-resize leg). Both the retries and the timeouts are reported so the two are told apart.
                + " idleWaitTimeouts=" + frame.submissionStatistics.idleWaitTimeouts() + " idleWaitRetries=" + frame.submissionStatistics.idleWaitRetries()
                + " terrainPasses=" + funnel.terrainStatistics.terrainPasses() + " immediateOnlyPasses=" + funnel.terrainStatistics.immediateOnlyPasses()
                // Item 384: terrain passes drawn into the bound mod framebuffer's chain pair
                // (terrainToBoundTarget) - the routing half of the bloom-halo split belongs in
                // this summary, the one the fixture reads, not only in b.diagnostics().
                + " terrainPassesToBoundTarget=" + funnel.terrainPassesToBoundTarget()
                // Item 644 follow-up (bdisc2): the bound-target refusal census was collected and printed
                // only on the diagnostics() line, never on this VERIFY summary, so a leg reading the
                // summary the fixture surfaces could not see attempts or refusals at all. Same summary
                // string the diagnostics line uses, so the two cannot drift apart.
                + funnel.boundTargetCensusSummary()
                + " worldPassBoundaryFlushes=" + funnel.terrainStatistics.worldPassBoundaryFlushes()
                // Item 328 (V-03): the terrain frame budget's cumulative account - what the per-frame caps
                // dropped and what the rebuild discipline deferred or reused - so a leg that hit either says
                // so in the summary the fixture reads, not only in the bounded stdout line.
                + " terrainBudgetDroppedBatches=" + 0L
                + " terrainBudgetDroppedDraws=" + 0L
                + " terrainBudgetDroppedVertices=" + 0L
                + " terrainBudgetRebuilds=" + 0L
                + " terrainBudgetDeferred=" + 0L
                + " terrainBudgetReused=" + 0L
                + " terrainBudgetForced=" + 0L
                // Item 359 (C4): the budget's drawn totals - the identity check's CPU side - were accumulated
                // and never printed, so a healthy run (no drops, no budget line) had no V4 material at all.
                + " terrainBudgetDrawnVertices=" + 0L
                + " terrainBudgetDrawnDraws=" + 0L
                + " " + funnel.terrainStatisticsSummary()
                // task-51's prerequisite: the upload path's queue drain and volume, on the line the fixture actually
                // reports. VulkanContext.bootstrapQueueWaits is incremented by b.beginCommands()'s vkQueueWaitIdle and
                // was printed only by CELTIUM_WAIT_CENSUS from VulkanContext.delete(), which appears in no run at all -
                // a counter that cannot be read is not a measurement. The arena totals beside it are what make the
                // cost per upload readable instead of the wait count alone.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " arenaUploads=" + 0L + " arenaUploadedBytes="
                + 0L
                + " arenaCreateCalls=" + 0L
                + " arenaCreateMillis=" + (0L / 1000000L)
                + " arenaGrowthCopies=" + 0L
                + " arenaPages=" + 0L
                + " textureDiagnostics=" + funnel.TEXTURE_DIAGNOSTICS + " diagnosed=" + funnel.diagnosedTextures.size()
                + " blendObserved=" + funnel.immediateStatistics.blendObserved() + "(" + funnel.observedBlendSrc + "," + funnel.observedBlendDst + ")"
                + " blendPairs=" + funnel.blendPairs + " blendFuncIgnored=" + funnel.immediateStatistics.blendFuncIgnored()
                + " blendOutlier=\"" + (funnel.blendOutlier == null ? "none" : funnel.blendOutlier) + "\""
                // The 2-argument glBlendFunc calls, which used to reach the counters but not the mirror. A run whose
                // value here is 0 and whose picture has an additive layer is a run where the call never arrived.
                + " blendFuncTwoArg=" + funnel.immediateStatistics.blendFuncTwoArgCalls()
                + " lastTwoArgBlendPair=\"" + funnel.immediateStatistics.lastTwoArgBlendPair() + "\""
                + " particleDescriptorFailure=\"" + funnel.immediateStatistics.particleDescriptorFailureReason() + "\""
                + " acceptedByFormat=[" + funnel.celtium$perFormat(funnel.acceptedByFormat) + "]"
                + " drawnByFormat=[" + funnel.celtium$perFormat(funnel.drawnByFormat) + "]"
                + " droppedByFormat=[" + funnel.celtium$perFormat(funnel.postAcceptDroppedByFormat) + "]"
                + " passBoundaryFlushes=" + funnel.immediateStatistics.passBoundaryFlushes() + " matrixDiagnostics=" + funnel.MATRIX_DIAGNOSTICS
                // Per-format arrival and acceptance over every id the layouts class declares, built from COUNT rather
                // than from a hand-written list: the list stopped at 9 and hid POSITION_NORMAL's own entry, which is
                // the same shape as the counters that hid ids 6..10 before. droppedByFormat is incremented at the
                // one post-accept drop boundary (framebuffer replacement in the ordering flush), per group and
                // format; printing every id is what says which formats lost groups rather than a list that
                // stops at 5.
                // The group lifecycle at the end of the run: what the queue did with what it accepted.
                // immediateGroupsFlushed is incremented beside immediateGroupsDrawn, so both count groups drawn -
                // the key was called immediateFlushes until round 49, which read as a window count and was not one:
                // the real flush count is actualFlushes (22.1 a frame on the world scene against 113.6 groups). The
                // rename is the whole change; closed minus drawn is the number that reached no pipeline, and the
                // pending/recorded/retired sizes are what
                // is still held. immediateGroupsKeptForOtherTargets is printed because it names a way a group
                // can be held back; a counter with no increment site anywhere is not printed at all - the
                // reader-wiring pass removed those rather than leave a permanent zero on the line.
                + " immediateGroupsDrawn=" + funnel.immediateStatistics.immediateGroupsDrawn()
                + " groupPool=[created=" + ImmediateGroup.poolCreated + " reused=" + ImmediateGroup.poolReused
                + " recycled=" + ImmediateGroup.poolRecycled + "]"
                // Every vkQueueSubmit the process issued, all paths together: the owned target's per-stage
                // semaphore packets show up here as part of the same total the work submissions do, so "submissions
                // per frame" is readable as queueSubmitCalls/worldFrames rather than assembled from call sites.
                + " queueSubmitCalls=" + VulkanContext.queueSubmitCalls
                // The light-map census, printed here because the two counters it is read with had no reporting site at
                // all: lightmapCoordGroups counts the groups whose captured coordinate reached the push block and
                // lightmapFullBrightGroups the GUI-stage groups that were given the full-bright pair instead. A
                // hotbar leg that reports the capture without the second number cannot say which coordinate was drawn.
                + " lightmapCoordCalls=" + funnel.immediateStatistics.lightmapCoordCalls()
                + " lightmapCoordGroups=" + funnel.immediateStatistics.lightmapCoordGroups()
                + " lightmapFullBrightGroups=" + funnel.immediateStatistics.lightmapFullBrightGroups()
                // The hook's own census: a zero here means the enableLightmap/disableLightmap injection never ran, and
                // the full-bright rule then degrades to the captured coordinate rather than to full-bright entities.
                + " lightmapUnitEnables=" + funnel.immediateStatistics.lightmapUnitEnables()
                + " lightmapUnitDisables=" + funnel.immediateStatistics.lightmapUnitDisables()
                // The ITEM variant split, so "the GUI item is bright" can be read as "it was lit and the coordinate was
                // full-bright" or as "it was never lit at all" rather than assumed from the picture.
                + " itemLitGroups=" + funnel.immediateStatistics.itemLitGroups()
                + " itemLitGuiGroups=" + funnel.immediateStatistics.itemLitGuiGroups()
                + " itemUnlitGroups=" + funnel.immediateStatistics.itemUnlitGroups()
                + " itemUnlitGuiGroups=" + funnel.immediateStatistics.itemUnlitGuiGroups()
                // The strip separators, in the census as well as in the font summary. The summary is printed by a
                // shutdown hook, and a leg whose fixture exits early loses it - measured: the font-strip2 leg has no
                // CELTIUM_FONT_GLYPH_SUMMARY line at all, so the counter that says the degenerate vertices were
                // inserted was unreadable exactly in the leg that needed it. This line is written during the run.
                + funnel.fontStats.run0()
                + " " + funnel.fontStats.retentionSummary()
                // The bind chain, in the summary rather than only in the bounded CELTIUM_UNIT_BIND lines: the arrivals
                // (bindTextureCalls = forwards, bindShortCircuits = the ones the hook skipped) against every mirror call
                // (immediateStateCalls). Reading the bounded print lines as arrivals is the mistake this line exists to
                // make impossible - 248 lines against 1,628,711 arrivals is what they actually said.
                + " bindTextureCalls=" + funnel.textureStats.bindTextureCalls()
                + " bindShortCircuits=" + funnel.textureStats.bindShortCircuits()
                + " immediateStateCalls=" + funnel.immediateStatistics.immediateStateCalls()
                + " immediateGroupsDiscarded=" + funnel.immediateStatistics.immediateGroupsDiscarded()
                + " immediateGroupsKeptForOtherTargets=" + funnel.immediateStatistics.immediateGroupsKeptForOtherTargets()
                + " groupsStaleTargetGeneration=" + funnel.immediateStatistics.groupsStaleTargetGeneration()
                + " groupsStaleWorldEpoch=" + funnel.immediateStatistics.groupsStaleWorldEpoch()
                + " worldEpoch=" + funnel.worldEpoch
                + " groupsDiscardedAtWorldTeardown=" + funnel.immediateStatistics.groupsDiscardedAtWorldTeardown()
                + " groupsDiscardedAtFullTeardown=" + funnel.immediateStatistics.groupsDiscardedAtFullTeardown()
                + " immediateWindows=" + funnel.immediateStatistics.immediateWindows()
                + " immediateWindowsThisFrame=" + funnel.immediateStatistics.immediateWindowsThisFrame()
                + " pendingImmediateIndices=" + funnel.pendingImmediateIndices
                + " immediateGroupsPending=" + funnel.immediateGroups.size()
                + " recordedGroupsHeld=" + frame.recordedImmediateGroups.size()
                + " retiredGroupsHeld=" + frame.retiredImmediateGroups.size()
                + " lastTerrainPassFrame=" + funnel.terrainStatistics.lastTerrainPassFrame() + " frameId=" + frame.frameId
                + " matrixMirrorDelta=proj:" + funnel.immediateStatistics.mirroredProjectionDelta() + ",view:" + funnel.mirroredModelViewDelta
                // The application side of the stub's raw glMultMatrixf arrival census. Without this print the field was
                // an instrument with no output (proj-channel found it incremented at :3102 and printed nowhere), and
                // "arrivals without a mirrored multiply" is the pair that says a raw call reached GL with no owner.
                + ",mirroredMultiplies:" + funnel.mirroredMultiplies
                + ",comparisons:" + funnel.matrixComparisons + ",exact:" + funnel.matrixExactComparisons
                + ",translateCalls:" + funnel.translateCalls + ",zeroTranslates:" + funnel.translateZeroCalls
                + ",passSplits:" + funnel.passSplits + ",target:" + funnel.targetRegistry.currentIdentity()
                + ",worstView[" + (funnel.worstModelViewSample == null ? "none" : funnel.worstModelViewSample) + "]"
                + " trace[" + String.join(" ", funnel.trace) + "]"
                + ",steadyComparisons:" + funnel.steadyComparisons + ",crossPassSkipped:" + funnel.crossPassComparisonsSkipped + ",midSetupSkipped:" + funnel.midSetupComparisonsSkipped
                + ",steadyDelta=proj:" + funnel.steadyProjectionDelta + ",view:" + funnel.steadyModelViewDelta
                + ",derivedProj:" + funnel.derivedProjections + ",refused:" + funnel.derivedProjectionRefusals
                + ",cameraSeeds:" + funnel.cameraSetupSeeds
                + ",derivedCompared:" + funnel.derivedProjectionComparisons
                + ",writeId:" + funnel.derivedWriteIdentity + ",compareId:" + funnel.derivedCompareIdentity
                + ",loadIdentityWhilePending:" + funnel.loadIdentityWhilePending
                + ",loadIdentityByMode=proj:" + funnel.loadIdentityByMode[0] + ",view:" + funnel.loadIdentityByMode[1]
                + ",matrixModeByMode=proj:" + funnel.matrixModeCallsByMode[0] + ",view:" + funnel.matrixModeCallsByMode[1]
                + ",derivedDelta:" + funnel.derivedProjectionDelta
                + " derivedWorst[" + (funnel.derivedProjectionWorst == null ? "none" : funnel.derivedProjectionWorst) + "]"
                + " matrixDump[" + (funnel.matrixDump == null ? "none" : funnel.matrixDump) + "]"
                + " immediateSubmitAvgNs=" + (funnel.immediateStatistics.immediateBatchesOffered() == 0 ? 0
                : funnel.immediateSubmitNanos / funnel.immediateStatistics.immediateBatchesOffered())
                + " immediateSubmitWorldAvgNs=" + (funnel.immediateSubmitBaselineNanos < 0
                        || funnel.immediateStatistics.immediateBatchesOffered() <= funnel.immediateSubmitBaselineOffered ? 0
                : (funnel.immediateSubmitNanos - funnel.immediateSubmitBaselineNanos)
                        / (funnel.immediateStatistics.immediateBatchesOffered() - funnel.immediateSubmitBaselineOffered))
                + " submitBuckets=[<5us:" + funnel.immediateSubmitUnder5 + " 5-50:" + funnel.immediateSubmit5to50
                + " 50-200:" + funnel.immediateSubmit50to200 + " >200us:" + funnel.immediateSubmitTailCalls
                + " tailMillis=" + (funnel.immediateStatistics.immediateSubmitTailNanos() / 1000000L) + "]"
                + " tailCarried=[flush:" + funnel.tailCarriedFlush + " supply:" + funnel.tailCarriedSupply
                + " both:" + funnel.tailCarriedBoth + " neither:" + funnel.tailCarriedNeither + "]"
                // Audit B03/B04: what the descriptor pools did (pools, growths, sets, cached texture keys,
                // refusals) and the wait-outcome matrix (completions, timeouts, lost devices, unconfirmed waits),
                // in the run's own report rather than only on stdout at teardown - the teardown path is not reached
                // in every run, and "the counter is in a log line nobody read" is how a bound stays invisible.
                + " immediateDescriptors=[" + VulkanImmediateTextureRecordsPipeline.descriptorPoolSummary() + "]"
                // Task-46: the authoritative "what renders now" fields - the real driver, from the device that
                // renders. The old GL bootstrap probe also has a glRenderer/vulkanDevice pair and those describe a
                // different, earlier question (the GL identity and the interop contract); this is the live one.
                + " driverInfo=[" + VulkanContext.driverSummary() + "]"
                + " waitOutcomes=[" + VulkanContext.waitOutcomeSummary() + "]"
                + " identityPairs=[" + org.soutalab.celtium.vulkan.GllessFrameTarget.identityPairCensus() + "]"
                // QUEUE 405 (S5/S6): what the chain pairs were asked for against what they were allocated
                // in - exact/widened/fallback with the fallback reasons kept apart, the stub-side request
                // distribution, and the bit-count answers that no longer describe the current target.
                // Counted at the junction and in the stub; printed here so a leg answers with numbers.
                + " targetFormats=[" + TargetFormatCensus.summary() + "]"
                + " presentMode=[" + VulkanSwapchain.presentModeSummary()
                + " rebuilds=" + frame.submissionStatistics.presentModeRebuilds()
                + " outOfDateRebuilds=" + frame.submissionStatistics.outOfDateRebuilds()
                + " surfaceLostRebuilds=" + frame.submissionStatistics.surfaceLostRebuilds() + "]"
                + " quarantined=[buffers=" + VulkanContext.quarantinedResourceCounts()[0]
                + " fences=" + VulkanContext.quarantinedResourceCounts()[1] + "]"
                + " matrixChannels=persp:" + funnel.immediateStatistics.perspectiveCalls() + ",frustum:" + funnel.frustumCalls
                + ",gluPerspective:" + funnel.mirroredGluPerspectives + ",gluSkipped:"
                + funnel.skippedGluPerspectives + ",gluSites:" + funnel.gluPerspectiveShapes.size()
                + ",gluSkipSites:" + funnel.gluPerspectiveSkips.size()
                + ",mode:" + funnel.matrixModeCalls + ",identity:" + funnel.loadIdentityCalls
                + ",texture:" + funnel.textureStats.bindTextureCalls()
                + " " + lossAggregateSummary()
                + " " + CeltiumLogLimiter.suppressionSummary();
    }

    /**
     * The loss aggregate the leg gates read (drawpath audit X-1): every counted-but-unread loss and
     * failure counter that is zero on a healthy run, summed into one total beside its breakdown. The
     * members are the counters whose individual keys elsewhere on this line had no automated reader -
     * post-accept group drops, ordering-flush failures and the batches they dropped, geometry dropped
     * at a stalled submission, target groups dropped unflushed, failed window flushes (whose batches
     * are retained, so the member is the failure count) and stale-group refusals. Counters that are
     * nonzero by design (terrain budget drops, pre-accept format negotiation) and counters another
     * bundle promotes on their own keys (the batch route's dropped draws) are deliberately not members.
     */
    private String lossAggregateSummary() {
        long postAcceptDroppedGroups = funnel.immediateStatistics.postAcceptDroppedGroups();
        long orderingFlushFailures = funnel.immediateStatistics.orderingFlushFailures();
        long orderingFlushDroppedBatches = funnel.immediateStatistics.orderingFlushDroppedBatches();
        long stalledGeometryDrops = frame.submissionStatistics.stalledGeometryDrops();
        long stalledGeometryDroppedBatches = frame.submissionStatistics.stalledGeometryDroppedBatches();
        long unflushedTargetGroupsDropped = funnel.targetStatistics.unflushedTargetGroupsDropped();
        long windowFlushFailures = funnel.targetStatistics.windowFlushFailures();
        long staleGroupRefusals = funnel.immediateStatistics.staleGroupRefusals();
        long total = postAcceptDroppedGroups + orderingFlushFailures + orderingFlushDroppedBatches
                + stalledGeometryDrops + stalledGeometryDroppedBatches + unflushedTargetGroupsDropped
                + windowFlushFailures + staleGroupRefusals;
        return "lossAggregate=[postAcceptDroppedGroups=" + postAcceptDroppedGroups
                + ",orderingFlushFailures=" + orderingFlushFailures
                + ",orderingFlushDroppedBatches=" + orderingFlushDroppedBatches
                + ",stalledGeometryDrops=" + stalledGeometryDrops
                + ",stalledGeometryDroppedBatches=" + stalledGeometryDroppedBatches
                + ",unflushedTargetGroupsDropped=" + unflushedTargetGroupsDropped
                + ",windowFlushFailures=" + windowFlushFailures
                + ",staleGroupRefusals=" + staleGroupRefusals
                + ",total=" + total + "]";
    }

    /** The bounded window diagnostics {@link #b.drawPendingImmediate(Long)} used to run inline; see the call site. */
    void celtium$windowDiagnostics(long target) {
        if (funnel.DUMP_TEXTURE_IDS != null && funnel.dumpedTextureImages < 8 && frame.presentHookCalls >= 120) {
            for (int di = 0; di < funnel.immediateGroups.size(); di++) {
                ImmediateGroup dumpGroup = funnel.immediateGroups.get(di);
                if (!funnel.DUMP_TEXTURE_IDS.contains(dumpGroup.texture)
                        || !funnel.dumpedTextureIds.add(dumpGroup.texture)) {
                    // Once per texture: the same id drawn every frame used to spend the whole budget on itself.
                    continue;
                }
                VulkanGlTexture dumpImage = dumpGroup.textureImage;
                if (dumpImage == null || dumpImage.image() == 0L) {
                    continue;
                }
                funnel.dumpedTextureImages++;
                java.nio.file.Path path = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                        "celtium-tex-" + dumpGroup.texture + "-" + dumpImage.width() + "x" + dumpImage.height()
                                + "-n" + funnel.dumpedTextureImages + ".png");
                boolean wrote = seatOwner.textureCopyService.celtium$dumpImage(dumpImage.image(), dumpImage.width(),
                        dumpImage.height(), path);
                System.out.println("[Celtium] CELTIUM_TEXTURE_IMAGE id=" + dumpGroup.texture + " path=" + path
                        + " " + dumpImage.width() + "x" + dumpImage.height() + " wrote=" + wrote
                        + " groupTarget=0x" + Long.toHexString(dumpGroup.target) + " format=" + dumpGroup.formatId
                        + " blend=" + dumpGroup.blendSrc + "/" + dumpGroup.blendDst
                        + " color=" + java.util.Arrays.toString(dumpGroup.color)
                        + " verts=" + (dumpGroup.mesh == null ? -1 : dumpGroup.mesh.vertexCount())
                        + " viewport=" + java.util.Arrays.toString(dumpGroup.viewportValues));
                break;
            }
        }
        if (funnel.VERTEX_DUMP_TEXTURE >= 0 && funnel.textureVertexDumps < 6 && !funnel.immediateGroups.isEmpty()) {
            ImmediateGroup probeGroup = funnel.immediateGroups.get(0);
            if (probeGroup.texture == funnel.VERTEX_DUMP_TEXTURE && probeGroup.mesh != null
                    && probeGroup.mesh.vertexAddress() != 0L) {
                funnel.textureVertexDumps++;
                long probeBase = probeGroup.mesh.vertexAddress();
                int probeStride = 24;
                StringBuilder probeVertices = new StringBuilder();
                for (int pv = 0; pv < Math.min(4, probeGroup.mesh.vertexCount()); pv++) {
                    long at = probeBase + (long) pv * probeStride;
                    probeVertices.append(String.format(java.util.Locale.ROOT, " (%.2f,%.2f,%.2f|uv %.3f,%.3f)",
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 4L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 8L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 12L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 16L))));
                }
                String matrix = probeGroup.texMatrix == null ? "none"
                        : String.format(java.util.Locale.ROOT, "[%.3f,%.3f,%.3f,%.3f,%.3f,%.3f]",
                                Float.valueOf(probeGroup.texMatrix[0]), Float.valueOf(probeGroup.texMatrix[1]),
                                Float.valueOf(probeGroup.texMatrix[2]), Float.valueOf(probeGroup.texMatrix[3]),
                                Float.valueOf(probeGroup.texMatrix[4]), Float.valueOf(probeGroup.texMatrix[5]));
                long probeImage = probeGroup.textureImage == null ? 0L : probeGroup.textureImage.image();
                long frameImage = funnel.frameTarget == null ? 0L : funnel.frameTarget.colorImage();
                System.out.println("[Celtium] CELTIUM_TEX_VERTICES id=" + funnel.VERTEX_DUMP_TEXTURE + " groups="
                        + funnel.immediateGroups.size() + " texMat=" + matrix
                        + " image=0x" + Long.toHexString(probeImage)
                        + " frameImage=0x" + Long.toHexString(frameImage)
                        + " selfSampling=" + (probeImage != 0L && probeImage == frameImage)
                        + " attached=0x" + Long.toHexString(funnel.passAttachedColorImage)
                        + " feedback=" + (probeImage != 0L && probeImage == funnel.passAttachedColorImage)
                        + probeVertices);
            }
        }
        if (funnel.WINDOW_CENSUS && funnel.immediateGroups.size() >= 10 && funnel.windowCensusLines < 80
                && funnel.groupDetailDumps < 3) {
            funnel.groupDetailDumps++;
            for (int gi = 0; gi < funnel.immediateGroups.size(); gi++) {
                ImmediateGroup detail = funnel.immediateGroups.get(gi);
                if (detail.mesh == null || detail.mesh.vertexAddress() == 0L) {
                    continue;
                }
                long at = detail.mesh.vertexAddress();
                System.out.println("[Celtium] CELTIUM_GROUP_DETAIL i=" + gi + " tex=" + detail.texture
                        + " format=" + detail.formatId + " verts=" + detail.mesh.vertexCount()
                        + String.format(java.util.Locale.ROOT, " v0=(%.1f,%.1f,%.1f|uv %.3f,%.3f)",
                                Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at)),
                                Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 4L)),
                                Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 8L)),
                                Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 12L)),
                                Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 16L))));
            }
        }
        if (funnel.WINDOW_CENSUS && funnel.windowCensusLines < 80) {
            funnel.windowCensusLines++;
            java.util.LinkedHashSet<Integer> censusTextures = new java.util.LinkedHashSet<Integer>();
            for (int ci = 0; ci < funnel.immediateGroups.size() && censusTextures.size() < 6; ci++) {
                censusTextures.add(Integer.valueOf(funnel.immediateGroups.get(ci).texture));
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_CENSUS target=0x" + Long.toHexString(target)
                    + " groups=" + funnel.immediateGroups.size() + " textures=" + censusTextures
                    + " projection=" + funnel.projectionSummary(funnel.immediateGroups.isEmpty() ? null
                            : funnel.immediateGroups.get(0).projection)
                    + " viewport=" + funnel.liveViewportWidth + "x" + funnel.liveViewportHeight
                    + " frame=" + frame.frameId);
        }
        if (funnel.immediateGroups.size() >= 100) {
            // Counted every time, not only while the diagnostic line is allowed: the question this answers is how many
            // large windows a menu frame draws, and a log bounded to twelve cannot say that.
            funnel.largeWindowGroups += funnel.immediateGroups.size();
            funnel.largeWindows++;
            if (funnel.largeWindows <= 12) {
                System.out.println("[Celtium] CELTIUM_WINDOW_DRAW presents=" + frame.presentHookCalls
                        + " groups=" + funnel.immediateGroups.size() + " viewport=" + funnel.liveViewportWidth
                        + "x" + funnel.liveViewportHeight + " target=0x" + Long.toHexString(target));
            }
            if (PeriodicLog.ENABLED && funnel.largeWindows % 60 == 0) {
                System.out.println("[Celtium] CELTIUM_LARGE_WINDOWS windows=" + funnel.largeWindows
                        + " groups=" + funnel.largeWindowGroups + " frame=" + frame.frameId
                        + " presents=" + frame.presentHookCalls);
            }
        }
        if (funnel.windowRouteLogs < 12 && funnel.immediateGroups.size() >= 100) {
            funnel.windowRouteLogs++;
            boolean isMainTarget = target == funnel.targetRegistry.mainIdentity();
            int exactDistinct = 0;
            int identityMatrices = 0;
            java.util.List<float[]> seen = new java.util.ArrayList<float[]>();
            java.util.Map<Long, java.util.List<float[]>> byPlacement =
                    new java.util.LinkedHashMap<Long, java.util.List<float[]>>();
            java.util.LinkedHashSet<Integer> textures = new java.util.LinkedHashSet<Integer>();
            for (int i = 0; i < funnel.immediateGroups.size(); i++) {
                float[] mv = funnel.immediateGroups.get(i).modelView;
                textures.add(Integer.valueOf(funnel.immediateGroups.get(i).texture));
                if (mv == null || mv.length < 16) {
                    continue;
                }
                boolean duplicate = false;
                for (int s = 0; s < seen.size() && !duplicate; s++) {
                    float[] other = seen.get(s);
                    boolean same = true;
                    for (int e = 0; e < 16; e++) {
                        if (Float.floatToIntBits(other[e]) != Float.floatToIntBits(mv[e])) {
                            same = false;
                            break;
                        }
                    }
                    duplicate = same;
                }
                if (!duplicate) {
                    seen.add(mv);
                    exactDistinct++;
                }
                if (Math.abs(mv[0] - 1.0f) < 1.0e-5f && Math.abs(mv[5] - 1.0f) < 1.0e-5f
                        && Math.abs(mv[10] - 1.0f) < 1.0e-5f && Math.abs(mv[12]) < 1.0e-5f
                        && Math.abs(mv[13]) < 1.0e-5f && Math.abs(mv[14]) < 1.0e-5f) {
                    identityMatrices++;
                }
                long placement = (((long) Float.floatToIntBits(mv[12])) << 32)
                        ^ (Float.floatToIntBits(mv[13]) & 0xFFFFFFFFL);
                byPlacement.computeIfAbsent(Long.valueOf(placement),
                        ignored -> new java.util.ArrayList<float[]>()).add(mv);
            }
            int[] histogram = new int[7];
            for (java.util.List<float[]> bucket : byPlacement.values()) {
                java.util.List<float[]> rotations = new java.util.ArrayList<float[]>();
                for (float[] mv : bucket) {
                    boolean duplicate = false;
                    for (int s = 0; s < rotations.size() && !duplicate; s++) {
                        float[] other = rotations.get(s);
                        boolean same = true;
                        for (int e : new int[] {0, 1, 2, 4, 5, 6, 8, 9, 10}) {
                            if (Float.floatToIntBits(other[e]) != Float.floatToIntBits(mv[e])) {
                                same = false;
                                break;
                            }
                        }
                        duplicate = same;
                    }
                    if (!duplicate) {
                        rotations.add(mv);
                    }
                }
                histogram[Math.min(6, rotations.size())]++;
            }
            StringBuilder perTile = new StringBuilder();
            for (int i = 0; i < histogram.length; i++) {
                if (histogram[i] == 0) {
                    continue;
                }
                if (perTile.length() > 0) {
                    perTile.append(' ');
                }
                perTile.append(i).append(':').append(histogram[i]);
            }
            StringBuilder textureList = new StringBuilder();
            for (Integer texture : textures) {
                if (textureList.length() > 0) {
                    textureList.append(',');
                }
                textureList.append(texture.intValue());
            }
            java.util.LinkedHashSet<Long> descriptors = new java.util.LinkedHashSet<Long>();
            java.util.LinkedHashSet<Object> textureImages = new java.util.LinkedHashSet<Object>();
            for (int i = 0; i < funnel.immediateGroups.size(); i++) {
                ImmediateGroup group = funnel.immediateGroups.get(i);
                if (group.prepared != null) {
                    descriptors.add(Long.valueOf(group.prepared.descriptor));
                }
                textureImages.add(group.textureImage);
            }
            StringBuilder descriptorList = new StringBuilder();
            int descriptorLines = 0;
            for (Long descriptor : descriptors) {
                if (descriptorLines++ >= 6) {
                    break;
                }
                if (descriptorList.length() > 0) {
                    descriptorList.append(',');
                }
                descriptorList.append("0x").append(Long.toHexString(descriptor.longValue()));
            }
            StringBuilder imageDetail = new StringBuilder();
            int imageLines = 0;
            for (Object image : textureImages) {
                if (imageLines++ >= 6) {
                    break;
                }
                if (imageDetail.length() > 0) {
                    imageDetail.append(' ');
                }
                if (image instanceof VulkanGlTexture texture) {
                    imageDetail.append(texture.width()).append('x').append(texture.height())
                            .append(texture.hasFullContent() ? "/full" : "/EMPTY")
                            .append("@0x").append(Long.toHexString(texture.image()));
                } else {
                    imageDetail.append(image == null ? "null" : image.getClass().getSimpleName());
                }
            }
            java.util.LinkedHashSet<Integer> alphas = new java.util.LinkedHashSet<Integer>();
            StringBuilder firstQuad = new StringBuilder();
            for (int i = 0; i < funnel.immediateGroups.size() && i < 64; i++) {
                ImmediateGroup group = funnel.immediateGroups.get(i);
                if (group.mesh == null || group.formatId != 2) {
                    continue;
                }
                long base = group.mesh.vertexAddress();
                if (base == 0L) {
                    continue;
                }
                for (int v = 0; v < 4; v++) {
                    int alpha = org.lwjgl.system.MemoryUtil.memGetByte(base + (long) v * 24L + 23L) & 0xFF;
                    alphas.add(Integer.valueOf(alpha));
                    if (i == 0) {
                        firstQuad.append(alpha).append(' ');
                    }
                }
            }
            for (int i = 0; i < funnel.immediateGroups.size(); i++) {
                ImmediateGroup group = funnel.immediateGroups.get(i);
                if (group.mesh == null || group.formatId != 2 || group.mesh.vertexAddress() == 0L) {
                    continue;
                }
                if (funnel.VERTEX_DUMP_TEXTURE >= 0 && group.texture != funnel.VERTEX_DUMP_TEXTURE) {
                    continue;
                }
                long base = group.mesh.vertexAddress();
                StringBuilder vertices = new StringBuilder();
                for (int v = 0; v < Math.min(4, group.mesh.vertexCount()); v++) {
                    long at = base + (long) v * 24L;
                    vertices.append(String.format(java.util.Locale.ROOT,
                            " (%.3f,%.3f,%.3f|%.3f,%.3f|%d,%d,%d,%d)",
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 4L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 8L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 12L)),
                            Float.valueOf(org.lwjgl.system.MemoryUtil.memGetFloat(at + 16L)),
                            Integer.valueOf(org.lwjgl.system.MemoryUtil.memGetByte(at + 20L) & 0xFF),
                            Integer.valueOf(org.lwjgl.system.MemoryUtil.memGetByte(at + 21L) & 0xFF),
                            Integer.valueOf(org.lwjgl.system.MemoryUtil.memGetByte(at + 22L) & 0xFF),
                            Integer.valueOf(org.lwjgl.system.MemoryUtil.memGetByte(at + 23L) & 0xFF)));
                }
                // The tile offsets, which are what makes 384 stacked quads a blur rather than one picture: vanilla moves each
            // by a fraction of a block (about 0.05 in the unit-square model space), so their magnitudes matter.
            StringBuilder offsets = new StringBuilder();
            for (int gi = 0; gi < funnel.immediateGroups.size() && gi < 8; gi++) {
                float[] offsetMv = funnel.immediateGroups.get(gi).modelView;
                if (offsetMv == null || offsetMv.length < 16) {
                    continue;
                }
                if (offsets.length() > 0) {
                    offsets.append(' ');
                }
                offsets.append(String.format(java.util.Locale.ROOT, "(%.4f,%.4f)", Float.valueOf(offsetMv[12]),
                        Float.valueOf(offsetMv[13])));
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_TILE_OFFSETS first=[" + offsets + "]");
            System.out.println("[Celtium] CELTIUM_WINDOW_VERTICES count=" + group.mesh.vertexCount()
                        + vertices);
                break;
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_VERTEX_ALPHA distinct=" + alphas.size()
                    + " values=" + alphas + " firstQuad=[" + firstQuad.toString().trim() + "]");
            int textured = 0;
            String firstTextureMatrix = "none";
            for (int i = 0; i < funnel.immediateGroups.size(); i++) {
                float[] texMatrix = funnel.immediateGroups.get(i).texMatrix;
                if (texMatrix != null) {
                    textured++;
                    if ("none".equals(firstTextureMatrix)) {
                        firstTextureMatrix = String.format(java.util.Locale.ROOT, "[%.3f,%.3f,%.3f,%.3f,%.3f,%.3f]",
                                Float.valueOf(texMatrix[0]), Float.valueOf(texMatrix[1]), Float.valueOf(texMatrix[2]),
                                Float.valueOf(texMatrix[3]), Float.valueOf(texMatrix[4]), Float.valueOf(texMatrix[5]));
                    }
                }
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_TEXMATRIX withMatrix=" + textured
                    + " groups=" + funnel.immediateGroups.size() + " first=" + firstTextureMatrix);
            if (funnel.SAMPLE_WINDOW_IMAGES) {
                StringBuilder means = new StringBuilder();
                int sampled = 0;
                for (Object image : textureImages) {
                    if (sampled++ >= 6) {
                        break;
                    }
                    if (image instanceof VulkanGlTexture texture) {
                        float mean = seatOwner.textureCopyService.celtium$sampleImageMean(texture.image(),
                                texture.width(), texture.height());
                        if (means.length() > 0) {
                            means.append(' ');
                        }
                        means.append(String.format(java.util.Locale.ROOT, "%.1f", Float.valueOf(mean)));
                    }
                }
                System.out.println("[Celtium] CELTIUM_WINDOW_IMAGE_MEANS means=[" + means + "]");
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_IMAGES distinct=" + textureImages.size()
                    + " images=[" + imageDetail + "]");
            System.out.println("[Celtium] CELTIUM_WINDOW_DESCRIPTORS distinct=" + descriptors.size()
                    + " groups=" + funnel.immediateGroups.size() + " textureImages=" + textureImages.size()
                    + " first=[" + descriptorList + "]");
            java.util.LinkedHashSet<Object> pipelines = new java.util.LinkedHashSet<Object>();
            for (int i = 0; i < funnel.immediateGroups.size(); i++) {
                ImmediateGroup group = funnel.immediateGroups.get(i);
                if (group.prepared != null) {
                    pipelines.add(group.prepared.pipeline);
                }
            }
            StringBuilder pipelineSummary = new StringBuilder();
            int pipelineLines = 0;
            for (Object pipeline : pipelines) {
                if (pipelineLines++ >= 4) {
                    break;
                }
                if (pipelineSummary.length() > 0) {
                    pipelineSummary.append(" | ");
                }
                if (pipeline instanceof VulkanImmediateTextureRecordsPipeline texture) {
                    pipelineSummary.append("texture blends=").append(texture.celtium$blends())
                            .append(" depthWrite=").append(texture.celtium$depthWrites())
                            .append(" depthTest=").append(texture.celtium$depthTests())
                            .append(" blend=").append(texture.celtium$colorBlend().describe());
                } else if (pipeline instanceof VulkanImmediateItemRecordsPipeline item) {
                    pipelineSummary.append("item blends=").append(item.celtium$blends())
                            .append(" depthWrite=").append(item.celtium$depthWrites())
                            .append(" depthTest=").append(item.celtium$depthTests());
                } else if (pipeline instanceof VulkanImmediateColourRecordsPipeline colour) {
                    pipelineSummary.append("colour blends=").append(colour.celtium$blends())
                            .append(" depthWrite=").append(colour.celtium$depthWrites())
                            .append(" depthTest=").append(colour.celtium$depthTests());
                } else {
                    pipelineSummary.append(pipeline == null ? "null" : pipeline.getClass().getSimpleName());
                }
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_PIPELINES distinct=" + pipelines.size()
                    + " " + pipelineSummary);
            System.out.println("[Celtium] CELTIUM_MODELVIEW_EXACT distinct=" + exactDistinct
                    + " groups=" + funnel.immediateGroups.size() + " identity=" + identityMatrices
                    + " placements=" + byPlacement.size() + " perTileRotations=[" + perTile + "]"
                    + " textures=[" + textureList + "]"
                    + " route=" + GllessFrameTarget.routeLabelForBinding(target, isMainTarget)
                    + " projection=" + funnel.projectionSummary(funnel.immediateGroups.get(0).projection));
        }
    }

    void celtium$reportWindowState() {
        if (!funnel.WINDOW_DIAG) {
            return;
        }
        ++funnel.windowDiagFrames;
        long window = CeltiumWindow.sdlWindowPointer();
        int[] pixels = new int[2];
        boolean havePixels = CeltiumWindow.sdlPixelSize(pixels);
        long flags = window == 0L ? -1L : org.lwjgl.sdl.SDLVideo.SDL_GetWindowFlags(window);
        String display = "unavailable";
        try {
            Class<?> cls = Class.forName("org.lwjgl.opengl.Display");
            display = "close=" + cls.getMethod("isCloseRequested").invoke(null)
                    + " created=" + cls.getMethod("isCreated").invoke(null)
                    + " size=" + cls.getMethod("getWidth").invoke(null) + "x"
                    + cls.getMethod("getHeight").invoke(null)
                    + " active=" + cls.getMethod("isActive").invoke(null)
                    + " fullscreen=" + cls.getMethod("isFullscreen").invoke(null);
        } catch (ReflectiveOperationException | LinkageError failed) {
            display = "unavailable(" + failed.getClass().getSimpleName() + ")";
        }
        System.out.println("[Celtium] CELTIUM_WINDOW_STATE frame=" + funnel.windowDiagFrames
                + " sdl=" + (window == 0L ? "none" : "0x" + Long.toHexString(window))
                + " flags=0x" + Long.toHexString(flags)
                + " pixelSize=" + (havePixels ? pixels[0] + "x" + pixels[1] : "unknown")
                + " display[" + display + "]");
    }

    /** {@return one line per target the drain visited}, with its entry counts. */
    String flushEntrySummary() {
        if (funnel.flushEntryPassesByTarget.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (it.unimi.dsi.fastutil.longs.Long2IntMap.Entry entry : funnel.flushEntryPassesByTarget.long2IntEntrySet()) {
            text.append(Long.toHexString(entry.getLongKey())).append(":entries=").append(entry.getIntValue())
                    .append(",groupsAtEntry=")
                    .append(funnel.flushEntryGroupsByTarget.get(entry.getLongKey()))
                    .append(' ');
        }
        return text.toString().trim();
    }

    /** {@return the offer-stage histogram}, so a stopped offer names its branch. */
    String offerStageSummary() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < funnel.offerStageReached.length; i++) {
            if (funnel.offerStageReached[i] != 0) {
                text.append(i).append(':').append(funnel.offerStageReached[i]).append(' ');
            }
        }
        return text.length() == 0 ? "none" : text.toString().trim();
    }

    /**
     * Reports the projected NDC bounds of a group and the colour of its first vertex.
     * <p>
     * This is the narrower diagnostic the previous round called for: vertex 0 alone cannot tell "projects on-screen
     * but is rejected by the depth test" from "never enters an accepted group", while the bounds over the whole group
     * can. The packed colour identifies the test geometry (the fixture draws with 255, 64, 64, 200).
     */
    void reportGroupBounds(ImmediateGroup group) {
        if (!funnel.wantedGroupBoundsFormat(group.formatId)) {
            // The format filter is checked before the cap so that a run narrowed to one layout is not spent on the
            // first six groups of every other layout.
            return;
        }
        if (frame.frameId < funnel.GROUP_BOUNDS_MIN_FRAME) {
            // Same reason, one level up: the loading screen and the menu own the first frames' groups, so a bounded
            // budget has to skip them to reach the frame the question is about.
            return;
        }
        if (funnel.GROUP_BOUNDS_GUI_ONLY && !FramePassTimings.guiSectionActive()) {
            // {@code -Dceltium.vulkan.groupBoundsGuiOnly=true}: only the groups created inside the HUD's own function.
            // Measured need (leg r212-world-hud19): the frame under test holds 39 immediate groups of which 23 are the
            // HUD's, so a bounded budget spent on the frame's first groups answers about the world and never about the
            // overlay - the same shape of mistake as the loading screen owning every early line.
            return;
        }
        int limit = funnel.GROUP_BOUNDS_DIAGNOSTICS ? funnel.GROUP_BOUNDS_LIMIT : 6;
        if (funnel.immediateDiagnostics >= limit) {
            return;
        }
        funnel.immediateDiagnostics++;
        try {
            VulkanDynamicMesh mesh = group.mesh;
            float[] projection = group.projection;
            float[] modelView = group.modelView;
            long base = mesh.vertexAddress();
            int vertices = mesh.vertexCount();
            // The stride the mesh was built with, not a pipeline constant: an immediate group copies the caller's
            // vertices unchanged and records their own stride in the layout, and for POSITION_TEX that is 20 bytes
            // (position 12 + texture 8), not the 16 of the base immediate layout. Reading the wrong stride put every
            // vertex after the first at an offset inside another vertex's texture data: measured on the sun and moon
            // groups, it reported y ranges of 100+ NDC units for quads whose real extent is 0.9, which is how a
            // correct quad came to be described as a frame-covering wedge. The vertices, the per-vertex clip
            // positions and the model-view are all printed now, so the reading can be checked rather than believed.
            int stride = mesh.layout() != null ? mesh.layout().stride : VulkanImmediateColourRecordsPipeline.VERTEX_BYTES;
            float[] mvp = new float[16];
            for (int column = 0; column < 4; column++) {
                for (int row = 0; row < 4; row++) {
                    float sum = 0.0f;
                    for (int k = 0; k < 4; k++) {
                        sum += projection[k * 4 + row] * modelView[column * 4 + k];
                    }
                    mvp[column * 4 + row] = sum;
                }
            }
            float minX = Float.MAX_VALUE;
            float maxX = -Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxY = -Float.MAX_VALUE;
            float minZ = Float.MAX_VALUE;
            float maxZ = -Float.MAX_VALUE;
            int behind = 0;
            StringBuilder perVertex = new StringBuilder();
            StringBuilder positions = new StringBuilder();
            StringBuilder uvs = new StringBuilder();
            for (int vertex = 0; vertex < vertices; vertex++) {
                long offset = base + ((long) vertex * stride);
                float x = MemoryUtil.memGetFloat(offset);
                float y = MemoryUtil.memGetFloat(offset + 4);
                float z = MemoryUtil.memGetFloat(offset + 8);
                float cx = (mvp[0] * x) + (mvp[4] * y) + (mvp[8] * z) + mvp[12];
                float cy = (mvp[1] * x) + (mvp[5] * y) + (mvp[9] * z) + mvp[13];
                float cz = (mvp[2] * x) + (mvp[6] * y) + (mvp[10] * z) + mvp[14];
                float cw = (mvp[3] * x) + (mvp[7] * y) + (mvp[11] * z) + mvp[15];
                if (vertex < 8) {
                    // The vertices themselves, beside the bounds: the bounds say where the group lands, and these say
                    // whether the geometry the group was built from is the quad the caller drew. A defect in either
                    // would move the picture, and the two are indistinguishable from the bounds alone. The same
                    // stride applies to the texture coordinates, which is what names the quad (the sun's is u=0,
                    // the moon's first vertex u=0.25 of the phase atlas).
                    positions.append(vertex > 0 ? ";" : "").append(funnel.round3(x)).append(',').append(funnel.round3(y))
                            .append(',').append(funnel.round3(z));
                    if (stride >= 20) {
                        uvs.append(vertex > 0 ? ";" : "")
                                .append(funnel.round3(MemoryUtil.memGetFloat(offset + 12))).append(',')
                                .append(funnel.round3(MemoryUtil.memGetFloat(offset + 16)));
                    }
                    perVertex.append(vertex > 0 ? ";" : "").append(funnel.round3(cx / cw)).append(',')
                            .append(funnel.round3(cy / cw)).append(',').append(funnel.round3(cz / cw)).append(',')
                            .append(funnel.round3(cw));
                }
                if (cw <= 0.0f) {
                    behind++;
                    continue;
                }
                minX = Math.min(minX, cx / cw);
                maxX = Math.max(maxX, cx / cw);
                minY = Math.min(minY, cy / cw);
                maxY = Math.max(maxY, cy / cw);
                minZ = Math.min(minZ, cz / cw);
                maxZ = Math.max(maxZ, cz / cw);
            }
            String bounds = behind == vertices ? "all behind the camera"
                    : String.format("x[%.2f..%.2f] y[%.2f..%.2f] z[%.2f..%.2f] behind=%d",
                            minX, maxX, minY, maxY, minZ, maxZ, behind);
            String colour = "0x" + Integer.toHexString(MemoryUtil.memGetInt(base + 12));
            // The world-pass POSITION_TEX family is the sky - the sun, the moon, the water surface - and it is the one
            // family whose model-view is the camera times two rotations the pass itself makes. For those groups the
            // line carries the whole chain: the group's own model-view, the vertices it was built from, each vertex's
            // clip-space position, and the rotations that reached the mirror since the camera boundary. Nothing else is
            // added, and with the switch off none of it is even computed.
            String chain = "";
            // The panorama's faces get the same per-vertex clip-space numbers the sky family gets, because the question
            // left after round 225 is exactly "with these matrices, where do the four corners actually land": the six
            // faces' model-views are six distinct orthonormal matrices with a translation of 0.008, their union has to
            // cover the viewport for a camera inside the cube, and the box they paint is 0.9% of its pixels per frame.
            // The clip values are the renderer's own arithmetic on the group's own matrices, so they separate "the
            // matrices are not what the draw uses" from "the matrices are wrong".
            boolean panoramaChain = group.formatId == 2 && group.texture >= 46 && group.texture <= 51
                    && !funnel.worldPassActive;
            if (((group.formatId == 6 || group.formatId == 7) && funnel.worldPassActive) || panoramaChain
                    || (funnel.GROUP_BOUNDS_GUI_ONLY && FramePassTimings.guiSectionActive())) {
                // The GUI-only filter is added to this condition as well as to the entry: the HUD's groups are
                // format 6/7/2 with worldPass=false, so the world-pass branch never printed their model-view or their
                // vertices - and those are the two fields that say whether a quad measured at NDC (0,0) collapsed
                // because its vertices are one point or because its model-view is degenerate. Measured on leg
                // r212-world-hud20: all 23 of the frame's HUD groups report ndc x[-0.00..0.00] y[-0.00..0.00] with a
                // correct ortho (m00=0.005 m11=-0.008), and nothing in that line said which of the two it was.
                chain = " mv=" + funnel.matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + funnel.currentGroupRotations + "]";
            } else if (group.formatId == VulkanImmediateFormats.ITEM && !funnel.worldPassActive) {
                // The GUI item family, added 2026-10-11: RenderItem draws a GUI item through
                // BufferBuilder.begin(7, DefaultVertexFormats.ITEM) - id 4 - so a sprite that comes out at the wrong
                // size is either this group's model-view (its scale is exactly diag(16,-16,16) with a translation of
                // (x+8, y+8, 100+zLevel), from RenderItem.setupGuiTransform) or the ITEM layout being read at the
                // wrong stride. The model-view is printed here so the two are told apart from one line: a wrong scale
                // shows in mv[0]/mv[5], a wrong stride shows in verts=[...] while mv stays 16/-16.
                chain = " mv=" + funnel.matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + funnel.currentGroupRotations + "]";
            }
            // On stdout as well as in the report: the report keeps only the last recorded line, and the question this
            // answers - which groups an artefact frame is made of - is a sequence, not a sample. frame/group tie the
            // line to the frame dump the PNG is of, and the projection's own columns say whether the group was
            // captured with the world perspective, an ortho, or a stale stack.
            System.out.println("[Celtium] CELTIUM_GROUP_BOUNDS frame=" + frame.frameId
                    + " group=" + funnel.immediateStatistics.immediateGroupsClosed() + " fmt=" + group.formatId
                    + " worldPass=" + funnel.worldPassActive + " target=" + group.target
                    + " texture=" + group.texture + " batches=" + group.batches
                    // The pipeline class this group was prepared for, beside the layout it carries. A group can be
                    // accepted, closed and drawn and still leave no pixels when the pipeline it drew with does not
                    // read the mesh it was given - the position/colour pipeline at a stride the mesh does not have,
                    // or a texture pipeline for an untextured group. Measured need: the creative inventory's
                    // hovered-slot highlight (fmt=1, 4 vertices, colour 0x80ffffff, ndc bounds exactly the slot) is
                    // counted by the draw site and leaves none of its pixels in the frame, and nothing on this line
                    // named the pipeline that draw used.
                    + " prepared=" + (group.prepared == null ? "none"
                            : group.prepared.pipeline.getClass().getSimpleName())
                    // The pipeline *instance*, not only its class, beside the render pass it was built for: two groups
                    // of one class can be drawn by different pipelines (one per state key) and only one of them can be
                    // the reason a group leaves no pixels. A group whose pipeline instance is shared with a group that
                    // does reach the image is evidence the pipeline is not the difference.
                    + " pipeline=0x" + (group.prepared == null || group.prepared.pipeline == null ? "0"
                            : Integer.toHexString(System.identityHashCode(group.prepared.pipeline)))
                    + " renderPass=0x" + (group.prepared != null
                            && group.prepared.pipeline instanceof VulkanImmediateColourRecordsPipeline immediate
                                    ? Long.toHexString(immediate.celtium$renderPass()) : "?")
                    // The rectangle the group carries for its viewport and its clip. The capture is what the draw
                    // applies (BH-09/OPT-10), so a group whose captured clip is empty or narrower than its quad is
                    // clipped before any fragment exists - which is indistinguishable from "not drawn" in a counter.
                    // Measured need: the hovered-slot highlight is the only fmt-1 group of its frame with a write mask
                    // that excludes alpha, and the clip was the last captured value that had never been printed.
                    + " vp=" + funnel.boxText(group.viewportValues)
                    + " clip=" + funnel.boxText(group.scissorBox) + " clipOn=" + group.scissorEnabled
                    + " vertices=" + vertices + " indices=" + mesh.indexCount()
                    + " colour0=" + colour
                    // The attribute itself, read from the group's own vertex memory, beside the value the state
                    // capture produced: "the attribute is zero" and "the attribute is fine and something else ate
                    // the fragment" are different findings, and colour0 alone cannot tell them apart.
                    + " vertexColour0=" + funnel.celtium$firstVertexColour(group, mesh)
                    // round3, not fmt3: the GUI's ortho has m00 = 2/ScaledWidth, which at the pinned pose is
                    // 0.00469 - a *correct* GUI projection and a collapsed one both print "0.00" under fmt3's two
                    // decimals, so the field could not answer the one question it exists for. The trap is the same
                    // shape as the 16-byte-stride and pointerStride=1 readings: the instrument did not read what it
                    // looked like it read. m11 = -2/ScaledHeight = -0.00833 is the second tell.
                    + " proj=[m00=" + funnel.round3(projection[0]) + " m11=" + funnel.round3(projection[5])
                    + " m22=" + funnel.round3(projection[10]) + " m32=" + funnel.round3(projection[14]) + "]"
                    // The drawing state the group was captured with, which is what decides whether a group that
                    // measures on-screen can still be rejected: a depth-tested quad drawn after the world pass is
                    // tested against the world's depth unless something cleared the depth attachment in between.
                    + " state=" + funnel.stateText(group.state)
                    // The texture matrix the group was captured with, because it is the one input that moves a
                    // correct-looking quad's UVs somewhere else: a stale affine left on unit 0 by an earlier draw
                    // transforms this group's texture coordinates, and the sample then lands outside the sprite -
                    // which the alpha test turns into "drawn, accepted, no pixels". The block-breaking overlay is the
                    // measured case (its fragments are alpha-discarded with the real atlas bound), and this field is
                    // what tells "the group carries a transform" from "it does not".
                    + " texMat=" + (group.texMatrix == null ? "none"
                            : funnel.round3(group.texMatrix[0]) + "," + funnel.round3(group.texMatrix[1]) + ","
                                    + funnel.round3(group.texMatrix[2]) + "," + funnel.round3(group.texMatrix[3]) + ","
                                    + funnel.round3(group.texMatrix[4]) + "," + funnel.round3(group.texMatrix[5]))
                    + " ndc " + bounds + chain);
            this.record("group " + funnel.immediateDiagnostics + ": vertices=" + vertices
                    + " indices=" + mesh.indexCount() + " colour0=" + colour
                    + " proj=[m00=" + projection[0] + " m11=" + projection[5] + "] ndc " + bounds);
        } catch (RuntimeException | LinkageError error) {
            this.record("group diagnostic failed: " + funnel.describe(error));
        }
    }
    List<String> diagnostics() {
        // Each call builds the current snapshot rather than appending to the last one. The audit (C13.5) measured that
        // the list grew by a fixed block on every call, so a repeatedly queried renderer (F3, the report, a watchdog)
        // accumulated its own history and the strings described older states as well as the current one. The stable
        // configuration and the current counters belong in the snapshot; a history, if one is ever wanted, is a
        // separate bounded structure and not this list.
        this.lines.clear();
        // takeTimingSummary() was written but never called, so every post-process investigation ran without the numbers
        // that say where the handoff time goes. Gated, because it is a per-report string rather than a per-frame cost.
        // Which depth format this device ended up with, and how: the choice is made once, from the device's own
        // answers, and a run that does not say which format it used cannot be compared with one on another GPU.
        this.lines.add("depthFormat: " + org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName()
                + " (" + org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary() + ")");
        // Unconditional for the same reason: these counters were kept but read by nothing outside tests, so a
        // run could hold shaderc timeouts, uniform writes that matched no uniform, location collisions and
        // descriptor-set collisions that no log ever showed.
        this.lines.add("modPrograms: " + org.soutalab.celtium.vulkan.shader.ModShaderCompiler.compileRefusalSummary());
        if (Lane2ImmediateRecorder.HANDOFF_TIMINGS) {
            // The refusal counters belong beside the timings: the shutter that keeps a shader-drawn batch out of Vulkan
            // changes which batches are taken, so a run has to say how many it refused and why.
            this.lines.add("acceptance: refusedBeforeAppend=" + funnel.immediateStatistics.refusedBeforeAppend()
                    + " fullGlUploads=" + VulkanGlTexture.fullUploadSummary()
                + " submitFaultInjections=" + VulkanSubmitFault.injections()
                + " submitRequests=" + VulkanSubmitFault.requests()
                + " textureFaultInjections=" + VulkanTextureFault.injections()
                + " textureUploadFailures=" + funnel.textureUploadFailures
                + " lastTextureUploadFailure=" + funnel.lastTextureUploadFailure
                + " descriptorFaultInjections=" + VulkanDescriptorFault.injections()
                + " descriptorRequests=" + VulkanDescriptorFault.requests()
                + funnel.modelStats.run0(funnel.modelDraws)
                + " texMatrixGroups=" + funnel.immediateStatistics.texMatrixGroups()
                + " modelDrawRefusals=" + funnel.modelDrawRefusals
                + " modelRecordedReplays=" + funnel.modelRecordedReplayCalls
                + " modelFastPath[" + funnel.modelReplayFastPath.describe() + "]"
                + " modelMissingMeshes=" + seatOwner.modelGeometry.modelMissingMeshes()
                + " modelLastRefusal=" + funnel.modelLastRefusal
                + " modelMeshLists=" + (seatOwner.modelGeometry.modelMeshes() == null ? -1 : seatOwner.modelGeometry.modelMeshes().size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + funnel.modelStats.run2()
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " textureDeletions=" + funnel.glTextureDeletions
                + " atlasReestablished=" + funnel.atlasReestablished
                + " lightmapReestablished=" + funnel.lightmapReestablished
                + " texturePatchCost=" + (funnel.textureRegistry == null ? "-" : funnel.textureRegistry.patchCostSummary())
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " prepareDescriptorFailures=" + funnel.prepareDescriptorFailures
                + " lastPrepareFailure=" + funnel.lastPrepareFailure
                + " groupsQueued=" + funnel.immediateGroups.size()
                + " groupsRecorded=" + frame.recordedImmediateGroups.size()
                + " groupsRetired=" + frame.retiredImmediateGroups.size()
                + " groupsStaleTargetGeneration=" + funnel.immediateStatistics.groupsStaleTargetGeneration()
                + " targetGeneration=" + funnel.targetRegistry.generation()
                + " groupsStaleWorldEpoch=" + funnel.immediateStatistics.groupsStaleWorldEpoch()
                + " worldEpoch=" + funnel.worldEpoch
                + " groupsDiscardedAtWorldTeardown=" + funnel.immediateStatistics.groupsDiscardedAtWorldTeardown()
                + " groupsDiscardedAtFullTeardown=" + funnel.immediateStatistics.groupsDiscardedAtFullTeardown()
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " modelDraws=" + funnel.modelDraws
                + funnel.modelStats.run1(funnel.modelDrawRefusals, funnel.modelLastRefusal)
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + seatOwner.modelGeometry.modelMeshQuads()
                + " modelMeshFailure=" + seatOwner.modelGeometry.modelMeshFailure()
                + " modelStoredFloats=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.storedFloats()
                + " modelFirstVertex=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.firstStoredVertex()
                + " modelMatrixPair=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.matrixComparison()
                + " modelLists=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lists()
                + " modelListQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.listSummary()
                + " modelQuads=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.quads()
                + " modelQuadVertices=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.vertices()
                + " modelQuadExtent=" + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.summary()
                + " displayListOrigins=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.origins()
                + " displayListReplaysPassOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassOpen()
                + " displayListReplaysPassClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithPassClosed()
                + " displayListOriginCancels=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.originCancels()
                + " displayListCancelled=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.cancelled()
                + " displayListReplaysOpen=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameOpen()
                + " displayListReplaysClosed=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.replaysWithFrameClosed()
                + " displayListReplays=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.total()
                + " displayListDistinct=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.distinctLists()
                + " displayListTop=" + org.soutalab.celtium.vulkan.VulkanDisplayListCensus.summary()
                + " shaderProgramRefusals=" + funnel.immediateStatistics.shaderProgramRefusals()
                + " shaderProgramRefusedIds=" + funnel.shaderProgramRefusedIds
                + " shaderProgramRefusedPrograms=" + funnel.programRefusalsText()
                + " terrainPassesToBoundTarget=" + funnel.terrainPassesToBoundTarget
                + funnel.boundTargetCensusSummary()
                    + " textureRefusals=" + funnel.textureStats.textureRefusalsBeforeAccept()
                    + " accepted=" + funnel.immediateStatistics.frameAccepted());
        }
        if (Lane2ImmediateRecorder.HANDOFF_TIMINGS && funnel.frameTarget != null) {
            String timings = funnel.frameTarget.takeTimingSummary();
            if (timings != null) {
                this.lines.add(timings);
            }
        }
        return this.lines;
    }
}
