package org.soutalab.celtium.vulkan;

import java.util.List;
import java.util.ArrayList;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/**
 * The backend's diagnostic reports and bounded diagnostic lines, moved out of {@link VulkanTerrainGpuBackend}
 * unchanged: what they read is the backend's state, through {@link #b}; nothing here changes what is drawn.
 */
final class BackendDiagnostics {
    private final VulkanTerrainGpuBackend b;

    BackendDiagnostics(VulkanTerrainGpuBackend backend) {
        this.b = backend;
    }

    String verificationSummary() {
        return "celtium/device[dynamicRendering="
                + (b.context != null && b.context.dynamicRenderingSupported()) + "] "
                + "celtium/clears[" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearCensus() + "] "
                + "celtium/lists[" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.listCensus() + "] "
                + "celtium/vulkan-check passes=" + b.terrainStatistics.passSubmissions() + " skipped=" + b.terrainStatistics.passSkips() + " errors="
                + b.terrainStatistics.passErrors() + " lastDraw=" + b.terrainStatistics.lastDrawCommands() + " lastSections=" + b.terrainStatistics.lastSectionsDrawn()
                + " skipReason=" + (b.terrainStatistics.lastSkipReason() == null ? "none" : b.terrainStatistics.lastSkipReason().replace(' ', '_'))
                + " immediateState=" + b.immediateStatistics.immediateStateCalls() + " immediateOffered=" + b.immediateStatistics.immediateBatchesOffered()
                + " frameId=" + b.frameId + " flushCount=" + b.immediateStatistics.flushCount()
                + " textureArray=" + (b.textureArray == null ? "absent" : b.textureArray.report())
                + " " + (b.modelInstances == null ? "modelInstances[absent failed=" + b.modelInstancesFailed + "]"
                        : b.modelInstances.describe())
                + " textureIndex=hit" + b.textureIndexHits + "/miss" + b.textureIndexMisses
                + " orderPreservingFlushes=" + b.immediateStatistics.orderPreservingFlushes()
                + " actualFlushes=" + b.immediateStatistics.actualFlushes()
                + " actualFlushMillis=" + (b.immediateStatistics.actualFlushNanos() / 1000000L)
                + " actualFlushWorstMillis=" + (b.immediateStatistics.actualFlushWorstNanos() / 1000000L)
                + " actualFlushCpuMillis=" + (b.immediateStatistics.actualFlushCpuNanos() / 1000000L)
                // Where a batch's time goes, per batch, with -Dceltium.vulkan.batchTimings=true. The counters existed but
                // were never printed, so the question "is the per-batch cost the state capture, the key, the program query or
                // the preparation" had no answer in a run.
                + (b.BATCH_TIMINGS ? b.immediateStatistics.batchTimingSummary() + b.offerStageNanosSummary() : "")
                + " identicalFrameSignatures=" + b.identicalFrameSignatures + "/" + b.comparedFrameSignatures
                // Task-60: the wait facts, so the JSON report names the wait instead of a reader guessing. The
                // bootstrap path's waits have no API bound at all; the frame path's fence wait has a 5 s one and is
                // the only place that sets gpuStalled. Worst is the number that says whether either is a hazard.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " bootstrapQueueWaitMillis=" + VulkanContext.bootstrapQueueWaitMillis
                + " bootstrapQueueWaitWorstMillis=" + VulkanContext.bootstrapQueueWaitWorstMillis
                + " bootstrapSubmitWaits=" + VulkanContext.bootstrapSubmitWaits
                + " submitReasons=" + b.context.submitReasons()
                + " bootstrapSubmitWaitMillis=" + VulkanContext.bootstrapSubmitWaitMillis
                + " bootstrapSubmitWaitWorstMillis=" + VulkanContext.bootstrapSubmitWaitWorstMillis
                + " " + b.context.uploadBatchSummary()
                + " " + (b.frameArena == null ? "frameArena=not-created" : b.frameArena.describe())
                + " frameArenaFallbackGroups=" + b.submissionStatistics.frameArenaFallbackGroups()
                + " frameSlots=" + b.FRAME_SLOTS + " frameSlotReuseWaits=" + b.scheduler.slotReuseWaits()
                + " slotPolls=" + b.scheduler.slotPolls() + " slotPollConfirms=" + b.scheduler.slotPollConfirms()
                + " slotWaitConfirms=" + b.scheduler.slotWaitConfirms()
                + " submitSequence=" + b.scheduler.submitSequence()
                + " completedSubmitSequence=" + b.scheduler.completedSubmitSequence()
                + " frameFenceWaitMillis=" + b.submissionStatistics.frameFenceWaitMillis()
                + " frameFenceWaitCount=" + b.submissionStatistics.frameFenceWaitCount()
                + " frameFenceWaitWorstMillis=" + b.submissionStatistics.frameFenceWaitWorstMillis()
                + " " + VulkanHostBuffer.allocationSummary()
                + " uniform" + (b.terrain.uniforms() == null ? "=none" : b.terrain.uniforms().growthSummary())
                + " readResolvedFromLastDrawnTarget=" + b.textureCopyService.readResolvedFromLastDrawnTarget()
                + " offerStages=[" + offerStageSummary() + "]"
                + " shaderPack=[" + b.shaderPackSummary + "]"
                + " windowOpenSites=[" + b.windowOpenSiteSummary() + "]"
                + " windowRecordMillis=" + (b.windowRecordNanos / 1000000L)
                + " windowRecords=" + b.windowRecords
                + " windowRecordPerFrameMillis=" + (b.frameId == 0L ? -1.0
                        : Math.round((double) b.windowRecordNanos / 1000000.0 / b.frameId * 100.0) / 100.0)
                + " drawSites=[" + b.drawSiteSummary() + "]"
                + " abParity=[" + b.abParitySummary() + "]"
                + " pushComponents=[" + b.pushComponentSummary() + "]"
                + " pushBlockHits=" + b.pushBlockHits
                + " pushBlockMisses=" + b.pushBlockMisses
                + " pushBlockEntries=" + b.pushBlocks.size()
                + " pushBlockClears=" + b.pushBlockClears
                + " retainedGeometry=[" + (b.retainedGeometry == null ? "unused" : b.retainedGeometry.summary()) + "]"
                + " batchReuseHits=" + b.batchReuseHits
                + " batchReuseMisses=" + b.batchReuseMisses
                + " batchReuseLastFrameSize=" + b.batchReuseLastFrame.size()
                + " pushElided=" + VulkanPushCache.SHARED.skipped()
                + " pushIssued=" + VulkanPushCache.SHARED.issued()
                + " splashSourcedGroups=" + b.targetStatistics.splashSourcedGroups()
                + " splashCanvasInvalidations=" + b.targetStatistics.splashCanvasInvalidations()
                + " immediateDrawSiteLogged=" + b.immediateStatistics.immediateDrawSiteLogged()
                + " unflushedTargetGroupsDropped=" + b.targetStatistics.unflushedTargetGroupsDropped()
                + " unflushedTargetDropTargets=[" + b.unflushedTargetDropSummary() + "]"
                + " drainedOtherTargetPasses=" + b.targetStatistics.drainedOtherTargetPasses()
                + " flushEntries=[" + flushEntrySummary() + "]"
                + " unflushedTargetPassFailures=" + b.targetStatistics.unflushedTargetPassFailures()
                + " lastUnflushedTargetFailure=" + (b.lastUnflushedTargetFailure == null ? "none"
                        : b.lastUnflushedTargetFailure.replace(' ', '_').substring(0,
                                Math.min(120, b.lastUnflushedTargetFailure.length())))
                + " submitTimeouts=" + VulkanContext.submitTimeouts
                + " gpuStalled=" + b.gpuStalled
                + " deviceWaitToken=" + b.submissionStatistics.deviceWaitToken()
                + " stalledGeometryDrops=" + b.submissionStatistics.stalledGeometryDrops()
                + " stalledGeometryDroppedBatches=" + b.submissionStatistics.stalledGeometryDroppedBatches()
                + " stallOrigin=" + (b.submissionStatistics.stallOrigin() == null ? "none" : b.submissionStatistics.stallOrigin().replace(' ', '_'))
                + " windowFlushFailures=" + b.targetStatistics.windowFlushFailures()
                // Printed, not just counted: a zero that never reaches the artifact is an absence rather than a
                // measurement, and this number is the one that says whether the frame-close producer (the second
                // producer of the done semaphore) ran at all - the path the per-image change could have broken.
                + " signalOnlySubmissions=" + b.submissionStatistics.signalOnlySubmissions()
                + " windowFlushFailureFrame=" + b.targetStatistics.windowFlushFailureFrame()
                + " windowFlushDroppedBatches=" + b.targetStatistics.windowFlushDroppedBatches()
                + " groupViewportSets=" + b.immediateStatistics.groupViewportSets()
                + " stateSkipsViewport=" + b.immediateStatistics.stateSkipsViewport()
                + " stateSkipsScissor=" + b.immediateStatistics.stateSkipsScissor()
                + " stateSkipsDepthBias=" + b.immediateStatistics.stateSkipsDepthBias()
                + " cullEnabledGroups=" + b.immediateStatistics.cullEnabledGroups()
                + " cullDisabledGroups=" + b.immediateStatistics.cullDisabledGroups()
                // BH-11 and BH-12's counters, at the same place as BH-08's. acceptance found
                // immediateDepthBiasedGroups declared and incremented with zero print sites, so "the bias was
                // applied" had no evidence in any artifact; the narrowed-line total had the same gap (only the
                // first four events printed). Both are now in the verification summary.
                + " immediateDepthBiasedGroups=" + b.immediateStatistics.immediateDepthBiasedGroups()
                // The two halves that decide whether the bias can fire at all: the call reaching the stub, and the
                // enable bit. acceptance measured 0 groups with the break overlay present, so one of these is false
                // and the pair says which without another round of guessing.
                + " polygonOffsetCalls=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCallCount()
                // Every bracket's open count, enabled or not: an empty framePassSplit cannot say whether a bracket
                // ran, and the missing HUD turns on exactly that (GuiIngameMixin's `gui` bracket against a HUD that may
                // never be drawn).
                + " passBegins=[" + FramePassTimings.passBeginCallsSummary() + "]"
                // Which textures and formats each identity carried: the stage census the HUD question needs, because an
                // identity's first group says nothing about the families that arrive later in the frame.
                + " targetStages=[" + b.celtium$targetStagesSummary() + "]"
                + " framePassSplit=[" + FramePassTimings.summary() + "]"
                + " terrainDrawCallMillis=" + (b.terrain.indirectDrawNanos() / 1_000_000L)
                + " terrainBindMillis=" + (b.terrainBindNanos / 1_000_000L)
                + " terrainPacketMillis=" + (b.terrain.indirectPacketNanos() / 1_000_000L)
                + " terrainBatchMillis=" + (b.terrain.indirectBatchNanos() / 1_000_000L)
                + " terrainPassBeginMillis=" + (b.terrainPassBeginNanos / 1_000_000L)
                + " terrainUniformMillis=" + (b.terrainUniformNanos / 1_000_000L)
                + " polygonOffsetFillEnabled=" + b.rasterState.polygonOffsetFillEnabled()
                + " polygonOffsetCalled=" + b.rasterState.polygonOffsetCalled()
                // BH-16: a clear that names one of the game's own framebuffers is dropped instead of being applied
                // to the shared chain pair, so the counter is what says the fix fired and the owner is what makes it
                // attributable - the panorama's faces were wiped by exactly such a clear before this existed.
                + " gameClearsDroppedForeignTarget=" + (b.offTargetTarget() == null ? -1
                        : b.offTargetTarget().gameClearsDroppedForeignTargetCount())
                + " lastDroppedClearOwner=" + (b.offTargetTarget() == null ? -1
                        : b.offTargetTarget().lastDroppedClearOwnerName())
                + " lineBatchesNarrowed=" + b.immediateStatistics.lineBatchesNarrowed()
                // BH-07/BH-10/BH-03's counters, at the same place as BH-08's: captured per batch, so a leg says how
                // many batches carried an alpha pair the old pipelines could not express, how many carried a write
                // mask that was not all four channels, and which alpha comparison reached the shader. Before the fix
                // the first two were zero by construction (the capture never read them) and the third did not exist.
                + " blendGroups=" + b.immediateStatistics.blendGroups()
                + " blendAlphaNonDefaultGroups=" + b.immediateStatistics.blendAlphaNonDefaultGroups()
                + " partialColorMaskGroups=" + b.immediateStatistics.partialColorMaskGroups()
                + " lastPartialColorMask=0x" + Integer.toHexString(b.immediateStatistics.lastPartialColorMask())
                + " colorMaskHistogram=" + b.colorMaskHistogramText()
                + " nonAddBlendEquationGroups=" + b.immediateStatistics.nonAddBlendEquationGroups()
                + " scissorBoundarySplits=" + b.immediateStatistics.scissorBoundarySplits()
                + " lightParamBoundarySplits=" + b.immediateStatistics.lightParamBoundarySplits()
                + " scissorCalls=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorCallCount()
                + " scissorToggles=" + org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorToggleCount()
                + " scissorCapturedGroups=" + b.immediateStatistics.scissorCapturedGroups()
                + " scissorLateCapturedGroups=" + b.immediateStatistics.scissorLateCapturedGroups()
                + " alphaFuncGroups=" + b.immediateStatistics.alphaFuncGroups()
                + " nonGreaterAlphaFuncGroups=" + b.immediateStatistics.nonGreaterAlphaFuncGroups()
                + " alphaTestDisabledGroups=" + b.immediateStatistics.alphaTestDisabledGroups()
                + " lastAlphaFunc=" + b.immediateStatistics.lastAlphaFunc()
                + " alphaSelectorCounts=" + b.alphaSelectorSummary()
                + " samplerFilters=" + VulkanGlTexture.samplerFilterCensus()
                + " lastAlphaPushRef=" + b.round3(b.immediateStatistics.lastAlphaPushReference())
                + " alphaReferenceValues=" + b.alphaReferenceValueSummary()
                + " windowFlushRetainedBatches=" + b.targetStatistics.windowFlushRetainedBatches()
                + " lastWindowFlushFailure=" + (b.lastWindowFlushFailure == null ? "none"
                        : b.lastWindowFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, b.lastWindowFlushFailure.length())))
                // The ordering flush's own bounded catch: the site the user's loading screen latched at before it was
                // bounded, so the next leg reads whether it still happens and what it throws.
                + " orderingFlushFailures=" + b.immediateStatistics.orderingFlushFailures()
                + " orderingFlushDroppedBatches=" + b.immediateStatistics.orderingFlushDroppedBatches()
                + " lastOrderingFlushFailure=" + (b.lastOrderingFlushFailure == null ? "none"
                        : b.lastOrderingFlushFailure.replace(' ', '_').substring(0,
                                Math.min(200, b.lastOrderingFlushFailure.length())))
                + " batchCallCount=" + b.batchCallCount
                + " batchCallMillis=" + (b.batchCallNanos / 1000000L)
                + " batchCallWorstMillis=" + (b.batchCallWorstNanos / 1000000L)
                + " batchCallOpeningMillis=" + (b.batchCallOpeningNanos / 1000000L)
                + " batchCallOpeningCount=" + b.batchCallOpeningCount
                + " batchCallCpuMillis=" + (b.batchCallCpuNanos / 1000000L)
                + " threadCpuSupported=" + org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.THREAD_CPU_SUPPORTED
                + " threadWaitedMillis=" + b.threadWaitedMillis()
                + " threadBlockedMillis=" + b.threadBlockedMillis()
                + " threadCpuTotalMillis=" + (b.threadCpuNanos() / 1000000L)
                + " uniformPreparationFailures=" + b.immediateStatistics.uniformPreparationFailures()
                + " lastUniformPreparationFailure=" + b.immediateStatistics.lastUniformPreparationFailure()
                + " regionUploadFailures=" + b.regionUploadFailures
                + " sectionsLeftUnbuilt=" + b.terrain.sectionsLeftUnbuilt()
                + " terrainIndirect=[offers=" + b.terrain.terrainIndirectOffers()
                + " calls=" + b.terrain.terrainIndirectCalls()
                + " draws=" + b.terrain.terrainIndirectDraws()
                + " clamps=" + b.terrain.terrainIndirectClampEvents()
                + " growths=" + b.terrain.terrainIndirectGrowths()
                + " fallbacks=" + b.terrain.terrainIndirectFallbacks()
                + " lastFallback=" + b.terrain.lastTerrainIndirectFallback().replace(' ', '_') + "]"
                + " terrainIndirectStream=[indirectDraws=" + b.terrain.terrainIndirectCommandDraws()
                + " indirectChecksum=" + Long.toHexString(b.terrain.terrainIndirectCommandChecksum())
                + " directDraws=" + b.terrain.terrainDirectEquivalentDraws()
                + " directChecksum=" + Long.toHexString(b.terrain.terrainDirectEquivalentChecksum())
                + " match=" + (b.terrain.terrainIndirectCommandDraws() == b.terrain.terrainDirectEquivalentDraws()
                        && b.terrain.terrainIndirectCommandChecksum() == b.terrain.terrainDirectEquivalentChecksum())
                + "]"
                + " " + org.soutalab.celtium.vulkan.VulkanArena.alignmentSummary()
                + " lastRegionUploadFailure=" + b.lastRegionUploadFailure
                + " innerWallCpu=[preflight " + (b.immediateStatistics.prepareImmediateNanos() / 1000000L) + "/"
                + (b.preflightCpuNanos / 1000000L) + ", append " + (b.immediateStatistics.appendNanos() / 1000000L) + "/"
                + (b.appendCpuNanos / 1000000L) + ", groupCreate " + (b.immediateStatistics.groupCreateNanos() / 1000000L) + "/"
                + (b.groupCreateCpuNanos / 1000000L) + "]"
                + " prepareImmediateCalls=" + b.immediateStatistics.prepareImmediateCalls()
                + " prepareImmediateMillis=" + (b.immediateStatistics.prepareImmediateNanos() / 1000000L)
                + " groupCreateCount=" + b.immediateStatistics.groupCreateCount()
                + " groupCreateMillis=" + (b.immediateStatistics.groupCreateNanos() / 1000000L)
                + " appendCount=" + b.immediateStatistics.appendCount()
                + " appendMillis=" + (b.immediateStatistics.appendNanos() / 1000000L)
                + " programQueryCount=" + b.immediateStatistics.programQueryCount()
                + " programQueryMillis=" + (b.immediateStatistics.programQueryNanos() / 1000000L)
                + " matrixCopyMillis=" + (b.immediateStatistics.matrixCopyNanos() / 1000000L)
                + " captureStateMillis=" + (b.immediateStatistics.captureStateNanos() / 1000000L)
                + " stateKeyMillis=" + (b.immediateStatistics.stateKeyNanos() / 1000000L)
                + " " + VulkanImmediateDrawer.indexCacheStats()
                + " prepareCache=" + b.prepareCacheSummary()
                + " groupDrawCount=" + b.immediateStatistics.groupDrawCount()
                + " groupDrawMillis=" + (b.immediateStatistics.groupDrawNanos() / 1000000L)
                + " groupCloneCount=" + b.immediateStatistics.groupCloneCount()
                + " groupCloneMillis=" + (b.immediateStatistics.groupCloneNanos() / 1000000L)
                + " preTransformedGroups=" + b.immediateStatistics.preTransformedGroups()
                + " preTransformedBatches=" + b.immediateStatistics.preTransformedBatches()
                + " preTransformedVertices=" + b.immediateStatistics.preTransformedVertices()
                + " selfCompositeSkips=" + b.immediateStatistics.selfCompositeSkips()
                + " pairNameSamplingDraws=" + b.pairNameSamplingDraws
                + " presentedDumpFailure=" + (b.swapchain == null ? "none" : b.swapchain.lastDumpFailure())
                + " " + b.postChainBridge.celtium$pairLayoutSummary()
                + " matrixSplits=" + b.immediateStatistics.matrixSplits()
                + " groupSplits[" + b.groupSplitCensus.summary() + "]"
                + " groupCloses=" + b.groupClosesForcingStart
                + " flushBeforeGlDraw=" + b.flushBeforeGlDrawCalls
                + " flushBeforeGlDrawWithGroups=" + b.flushBeforeGlDrawWithGroups
                + " mergeRefusals=" + b.mergeRefusalSummary()
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
                + " packetRuns[" + b.packetRunCensus.summary() + "]"
                + " packetBindMicros=" + PacketCost.bindMicros()
                + " packetDrawMicros=" + PacketCost.drawMicros()
                + " packetBindSkips=" + PacketCost.bindSkips()
                + " " + b.groupSplitCensus.affineSummary()
                + " fastMergedBatches=" + b.immediateStatistics.fastMergedBatches()
                + " prepLookupMillis=" + (b.immediateStatistics.prepLookupNanos() / 1000000L)
                + " prepTerrainPipelineMillis=" + (b.immediateStatistics.prepTerrainPipelineNanos() / 1000000L)
                + " prepRegistryMillis=" + (b.immediateStatistics.prepRegistryNanos() / 1000000L)
                + " regAnnouncedCalls=" + VulkanTextureRegistry.ANNOUNCED_CALLS
                + " regAnnouncedMillis=" + (VulkanTextureRegistry.ANNOUNCED_NANOS / 1000000L)
                + " regStaleCalls=" + VulkanTextureRegistry.STALE_CALLS
                + " regStaleMillis=" + (VulkanTextureRegistry.STALE_NANOS / 1000000L)
                + " regHitCalls=" + VulkanTextureRegistry.HIT_CALLS
                + " regAnnouncedSkips=" + VulkanTextureRegistry.ANNOUNCED_SKIPS
                + " prepBranchMillis=" + (b.immediateStatistics.prepBranchNanos() / 1000000L)
                + " descriptorHits=" + VulkanImmediateTexturePipeline.DESCRIPTOR_HITS
                + " descriptorMisses=" + VulkanImmediateTexturePipeline.DESCRIPTOR_MISSES + " clearOrderFlushes=" + b.immediateStatistics.clearOrderFlushes()
                + " clearOrderZeroMask=" + b.clearOrderFlushesZeroMask
                + " offTargetByTarget=[" + b.offTargetSummary() + "] offTargetTargets=" + b.targetStatistics.offTargetTargetsCreated()
                + " refusedBeforeAppend=" + b.immediateStatistics.refusedBeforeAppend()
                + " partialMirrorRefusals=" + b.immediateStatistics.partialMirrorRefusals()
                + " partialMirrorsCompleted=" + b.immediateStatistics.partialMirrorsCompleted()
                + " offTargetPassesSkippedEmpty=" + b.targetStatistics.offTargetPassesSkippedEmpty()
                + " offTargetPerTarget=[" + b.offTargetPerTarget() + "]"
                + " offTargetPairs=[" + b.frameTargetPairSummary() + "]"
                + " offTargetPairClears=[default-pair=" + b.defaultPairClears
                + ",chain-pair=" + b.chainPairClears + "]"
                + " gameClearAttribution=[latchedForFrame=" + b.targetStatistics.gameClearsLatchedForFrame()
                + ",routedToPairs=" + b.gameClearsRoutedToPairs
                + ",queuedOnGroups=" + b.gameClearsQueued
                + ",inPassArm=" + b.gameClearsInPassArm
                + ",appliedInPass=" + b.gameClearsAppliedInPass
                + ",standaloneSubmits=" + b.gameClearsSubmittedStandalone
                + ",latchedForPairs=" + b.gameClearsLatchedForPairs + "]"
                + " offTargetPairFramebuffers=[default-pair=" + b.targetRegistry.pairFramebufferCreations(RenderTargetRegistry.ImageSlot.DEFAULT_PAIR)
                + ",chain-pair=" + b.targetRegistry.chainPairFramebufferCreationsTotal() + "]"
                + " otherTargetGroups=[atMainFlush=" + b.targetStatistics.otherTargetGroupsAtMainFlush()
                + ",discarded=" + b.otherTargetGroupsDiscarded
                + ",windowFlush=" + b.otherTargetGroupsFlushedByWindow
                + ",enqueuedForOtherTargets=" + b.groupsEnqueuedForOtherTargets
                + ",acceptedForOtherTargets=" + b.acceptedForOtherTargets + "]"
                + " immediateRecordedBatches=" + b.immediateStatistics.immediateRecordedBatches()
                + " immediateSubmittedBatches=" + b.immediateStatistics.immediateSubmittedBatches()
                + " immediateCompletedBatches=" + b.immediateStatistics.immediateCompletedBatches()
                + " immediateRetries=" + b.immediateStatistics.immediateRetryCount()
                + " invalidImmediateRecords=" + b.immediateStatistics.invalidImmediateRecords()
                + " backendCommandBuffer=" + Long.toHexString(b.commandBuffer.address())
                + " backendCommandState=" + b.commandState.state()
                + " drawsWithoutCommandBuffer=" + b.immediateStatistics.drawsWithoutCommandBuffer()
                + " offTargetRefusals=" + (b.offTargetRefusalsByReason.isEmpty()
                        ? "none" : b.offTargetRefusalsByReason.toString())
                + " offTargetOffered=" + b.targetStatistics.offTargetOffered() + " offTargetAccepted=" + b.targetStatistics.offTargetAccepted()
                + " offTargetDrawn=" + b.targetStatistics.offTargetDrawn() + " offTargetCopiedBack=" + b.targetStatistics.offTargetCopiedBack()
                + " outsideWorldPassAccepted=" + b.targetStatistics.outsideWorldPassAccepted() + " outsideWorldPassRefusals=" + b.targetStatistics.outsideWorldPassRefusals() + " compositePresentRefusals=" + b.targetStatistics.compositePresentRefusals()
                + " descriptorSets=" + b.descriptorSetCensus()
                // BH-20's census, beside the descriptor sets it shares a lifetime with: how many pipeline builds failed
                // after a stage had created handles and were rolled back rather than leaked. Zero on a normal leg is
                // expected; the injected-failure leg is what distinguishes "nothing failed" from "not wired".
                + " pipelineBuildRollbacks=" + VulkanImmediateTexturePipeline.BUILD_ROLLBACKS.get()
                + " inPlaceRefreshes=" + (b.textureRegistry != null ? b.textureRegistry.inPlaceRefreshes() : 0)
                + " mirroredTextures=" + (b.textureRegistry != null ? b.textureRegistry.mirrorCount() : 0)
                + " externalImages=[" + (b.textureRegistry != null
                        ? b.textureRegistry.externalSummary() : "none") + "]"
                // Condition (c) evidence: what the swapchain last presented, and whether the present was accepted. With
                // presentMarker the image is cleared to magenta (1,0,1), whose 8-bit channel mean is ~170, so a
                // presented mean near 170 says the swapchain image held this renderer's frame rather than nothing.
                + " presentSucceeded=" + (b.swapchain != null && b.swapchain.lastPresentSucceeded())
                + " presentSubmitDone=" + (b.swapchain != null && b.swapchain.lastPresentSubmitDone())
                + " depthFormat=" + org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName()
                + " depthFormatDecision=[" + org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary() + "]"
                // Which leg of the clear-attribution A/B this run is. On by default; the control leg sets
                // -Dceltium.vulkan.gameClearLatch=false, and a reader of one report must be able to tell which is
                // which without a log. Surfaced here and not in b.diagnostics(): that list is not part of the report.
                + " gameClearLatch=" + (b.celtium$gameClearLatchEnabled() ? "on" : "off")
                + " presentedMean=" + (b.swapchain != null ? b.swapchain.lastPresentedMean() : -1.0f)
                + " swapPointCalls=" + b.swapPointCalls
                + " swapPointMillis=" + (b.swapPointNanos / 1000000L)
                + " swapPointFlushMillis=" + (b.swapPointFlushNanos / 1000000L)
                + " swapPointWorstMs=" + (b.swapPointWorstNanos / 1000000L)
                + " presentStages[" + (b.swapchain != null
                        ? org.soutalab.celtium.vulkan.VulkanSwapchain.presentStageSummary() : "none") + "]"
                + " swapPointImageMillis=" + (b.swapPointImageNanos / 1000000L)
                + " swapPointImageMs[p50/p95/worst=" + b.celtium$distribution(b.swapPointImageRing, b.swapPointImageRingCount) + "]"
                + " submitToFenceMs[p50/p95/worst=" + b.celtium$distribution(b.submitToFenceRing, b.submitToFenceRingCount) + "]"
                + " swapPointDumpMillis=" + (b.swapPointDumpNanos / 1000000L)
                + " swapPointPollMillis=" + (b.swapPointPollNanos / 1000000L)
                + " presentMarkerExpected=170.0_magenta_1_0_1"
                + " fogModeSource=" + (VulkanFogMirror.hasValue() ? "mirror" : "gl_query")
                + " boundTextureSamples=" + b.textureStats.boundTextureComparisons()
                + " boundTextureMismatches=" + b.textureStats.boundTextureMismatches()
                + " announcedDynamicTextures=" + b.textureStats.announcedDynamicTextures()
                + " colorMirrorUses=" + b.textureStats.colorMirrorUses()
                // The refusal counters belong in this summary, not in b.diagnostics(): b.diagnostics() is not surfaced in
                // the report (it is a separate list, and the report keeps ten items), while this string is the one the
                // fixture reads. shaderProgramRefusals is what the custom-program shutter took out of Vulkan.
                + " modelDraws=" + b.modelDraws
                + " modelEnqueueMillis=" + b.modelStats.enqueueMillis()
                + " modelEnqueueCalls=" + b.modelStats.enqueueCalls()
                + " modelEnqueueMicrosPerCall=" + b.modelStats.enqueueMicrosPerCall()
                + " modelRepeatsPrev=" + b.modelStats.repeatReplays()
                + " modelRepeatsPercent=" + b.modelStats.repeatPercent()
                + " texMatrixGroups=" + b.immediateStatistics.texMatrixGroups()
                + " modelDrawRefusals=" + b.modelDrawRefusals
                + " modelLastRefusal=" + b.modelLastRefusal
                + " modelMeshLists=" + (b.modelGeometry.modelMeshes() == null ? -1 : b.modelGeometry.modelMeshes().size())
                + " " + VulkanDynamicMesh.describeModel()
                + " " + org.soutalab.celtium.vulkan.VulkanModelQuadCapture.lifecycle()
                + " modelMeshQuads=" + b.modelGeometry.modelMeshQuads()
                + " modelMeshFailure=" + b.modelGeometry.modelMeshFailure()
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
                + " shaderProgramRefusals=" + b.immediateStatistics.shaderProgramRefusals()
                + " acceptedBatches=" + b.immediateStatistics.frameAccepted()
                + " imageFilledMirrors=" + (b.textureRegistry != null ? b.textureRegistry.imageUploads() : 0)
                + " imageUploadsSkipped="
                + (b.textureRegistry != null ? b.textureRegistry.imageUploadsSkipped() : 0)
                + " announcedUploads=" + b.textureStats.announcedUploads() + " animatedImageFills=" + b.textureStats.animatedImageFills()
                + " textureCopiesServed=" + b.textureCopyService.textureCopiesServed()
                + " textureCopiesRefused=" + b.textureCopyService.textureCopiesRefused()
                + " textureCopiesReusedInFrame=" + b.textureCopyService.textureCopiesReusedInFrame()
                + " textureCopyReadbacks=" + b.textureCopyService.textureCopyReadbacks()
                + " textureCopyReadbackMillis=" + (b.textureCopyService.textureCopyReadbackNanos() / 1000000L)
                // The native route's own census: a copy the GPU wrote into the mirror directly, against one the blit
                // refused (which then took the host route above). The two lines together are what says which path a
                // leg's copies took, rather than the absence of a readback implying the blit ran.
                + " textureCopiesServedPeakPerPresent=" + b.textureCopyService.textureCopiesServedPeakPerPresent()
                + " textureCopiesReServedAfterContentChange="
                + b.textureCopyService.textureCopiesReServedAfterContentChange()
                + " textureCopyBlits=" + b.textureCopyService.textureCopyBlits()
                + " textureCopyBlitRefusals=" + b.textureCopyService.textureCopyBlitRefusals()
                + " lastTextureCopyBlitRefusal=[" + b.textureCopyService.lastTextureCopyBlitRefusal() + "]"
                + " lastTextureCopyRefusal=[" + b.textureCopyService.lastTextureCopyRefusal() + "]"
                // The pair clear-once contract: applied clears against the clears a second identity's pass asked for
                // and did not get. Without the second number the rule's effect is inferred from a picture.
                + " pairClearsRefusedOtherIdentity=" + b.targetStatistics.pairClearsRefusedOtherIdentity()
                + " windowDrawsToFrameImage=" + b.targetStatistics.windowDrawsToFrameImage()
                + " windowFramesMarkedPresentable=" + b.targetStatistics.windowFramesMarkedPresentable()
                + " lastPairClearRefusal=[" + b.targetStatistics.lastPairClearRefusal() + "]"
                + " " + (b.postChain != null ? b.postChain.status() : "postChains=0 postPasses=0")
                + " " + VulkanContext.pipelineCreationSummary()
                + " " + VulkanContext.memoryAllocationSummary()
                + " " + b.framebufferCompositor.describe()
                + " directMirrorFromAnnouncement=" + b.textureStats.directMirrorFromAnnouncement()
                + " dynamicTextureFills=" + b.textureStats.dynamicTextureFills()
                + " readbackByOrigin=" + (b.textureRegistry != null ? b.textureRegistry.readbackByOrigin() : "none")
                + " mirrorsCreatedByAnnouncement="
                + (b.textureRegistry != null ? b.textureRegistry.createdByAnnouncement() : 0)
                + " swapchainOutOfDate=" + (b.swapchain != null ? (b.swapchain.outOfDate() ? 1 : 0) : -1)
                + " acquireOutOfDate=" + (b.swapchain != null ? b.swapchain.acquireOutOfDate() : -1)
                + " retiredFlushes=" + b.submissionStatistics.retiredFlushes() + " retire=" + VulkanRetireQueue.describe()
                + " lastFrameAccepted=" + b.immediateStatistics.lastFrameAccepted() + " lastFrameDrawn=" + b.immediateStatistics.lastFrameDrawn()
                + " immediateTaken=" + b.immediateStatistics.immediateTaken() + " immediateFlushes=" + b.immediateStatistics.immediateFlushes()
                + " immediateGroupsClosed=" + b.immediateStatistics.immediateGroupsClosed()
                + " immediateMaxGroupVertices=" + b.immediateStatistics.immediateMaxGroupVertices()
                + " immediateRefusal=" + b.celtium$refusalText().replace(' ', '_')
                + " snapshotCaptures=" + b.immediateStatistics.snapshotCaptures() + " snapshotServed=" + b.immediateStatistics.snapshotServedCaptures() + " outsideWorldPassRefusals=" + b.targetStatistics.outsideWorldPassRefusals()
                + " offTargetRefusals=" + b.targetStatistics.offTargetRefusals()
                + " textureRefusalsBeforeAccept=" + b.textureStats.textureRefusalsBeforeAccept()
                + " postAcceptDroppedGroups=" + b.immediateStatistics.postAcceptDroppedGroups()
                + " particleDescriptorRefusals=" + b.immediateStatistics.particleDescriptorRefusals()
                + " gpuStalled=" + b.gpuStalled + " stalledStops=" + b.submissionStatistics.stalledStops()
                + " publishedFrameTexturesCreated=" + b.publishedTextures.created()
                + " publishedFrameTexturesUnpublished=" + b.publishedTextures.unpublished()
                // The attach-side rule's counter: a published name that stands for the image the pass attaches is kept
                // out of the sampling-side transition, which is the VUID-00900 fix. It must be visible, because a rule
                // that silently stops transitioning an image is indistinguishable from one that forgot to.
                + " publishedFrameTexturesSkippedForAttach=" + b.publishedTextures.skippedForAttach()
                // A name whose image was recreated under it is unpublished at the point of use: a barrier on the freed
                // handle was measured as a validation error followed by a SIGSEGV inside libvulkan_radeon.
                + " publishedFrameTexturesStalePruned=" + b.publishedTextures.stalePruned()
                + " publishedFrameTexturesRepublished=" + b.publishedTextures.republished()
                // A group that outlived its texture wrapper: the descriptor is rebuilt from the current wrapper, or the
                // group is refused. Both are counted because "the picture lost a batch" and "the device was lost" are
                // different outcomes and only the counter says which happened.
                + " staleGroupDescriptorsRebuilt=" + b.immediateStatistics.staleGroupDescriptorsRebuilt()
                + " staleGroupRefusals=" + b.immediateStatistics.staleGroupRefusals()
                + " stalledTeardownSkips=" + b.submissionStatistics.stalledTeardownSkips()
                + " idleWaits=" + b.submissionStatistics.idleWaits() + " injectedIdleFailures=" + b.submissionStatistics.injectedIdleFailures()
                // A timed-out fence wait is retried before the latch: a resize or a swapchain rebuild produces transient
                // timeouts, and latching on the first one stopped the renderer for the session (measured on the
                // world-resize leg). Both the retries and the timeouts are reported so the two are told apart.
                + " idleWaitTimeouts=" + b.submissionStatistics.idleWaitTimeouts() + " idleWaitRetries=" + b.submissionStatistics.idleWaitRetries()
                + " terrainPasses=" + b.terrainStatistics.terrainPasses() + " immediateOnlyPasses=" + b.terrainStatistics.immediateOnlyPasses()
                + " worldPassBoundaryFlushes=" + b.terrainStatistics.worldPassBoundaryFlushes()
                // task-51's prerequisite: the upload path's queue drain and volume, on the line the fixture actually
                // reports. VulkanContext.bootstrapQueueWaits is incremented by b.beginCommands()'s vkQueueWaitIdle and
                // was printed only by CELTIUM_WAIT_CENSUS from VulkanContext.delete(), which appears in no run at all -
                // a counter that cannot be read is not a measurement. The arena totals beside it are what make the
                // cost per upload readable instead of the wait count alone.
                + " bootstrapQueueWaits=" + VulkanContext.bootstrapQueueWaits
                + " arenaUploads=" + b.celtium$arenaUploadCount() + " arenaUploadedBytes="
                + b.celtium$arenaUploadedBytes()
                + " arenaCreateCalls=" + org.soutalab.celtium.vulkan.VulkanArena.arenaCreateCalls
                + " arenaCreateMillis=" + (org.soutalab.celtium.vulkan.VulkanArena.arenaCreateNanos / 1000000L)
                + " textureDiagnostics=" + b.TEXTURE_DIAGNOSTICS + " diagnosed=" + b.diagnosedTextures.size()
                + " blendObserved=" + b.immediateStatistics.blendObserved() + "(" + b.observedBlendSrc + "," + b.observedBlendDst + ")"
                + " blendPairs=" + b.blendPairs + " blendFuncIgnored=" + b.immediateStatistics.blendFuncIgnored()
                + " blendOutlier=\"" + (b.blendOutlier == null ? "none" : b.blendOutlier) + "\""
                // The 2-argument glBlendFunc calls, which used to reach the counters but not the mirror. A run whose
                // value here is 0 and whose picture has an additive layer is a run where the call never arrived.
                + " blendFuncTwoArg=" + b.immediateStatistics.blendFuncTwoArgCalls()
                + " lastTwoArgBlendPair=\"" + b.immediateStatistics.lastTwoArgBlendPair() + "\""
                + " particleDescriptorFailure=\"" + b.immediateStatistics.particleDescriptorFailureReason() + "\""
                + " acceptedByFormat=[" + b.celtium$perFormat(b.acceptedByFormat) + "]"
                + " drawnByFormat=[" + b.celtium$perFormat(b.drawnByFormat) + "]"
                + " droppedByFormat=[" + b.celtium$perFormat(b.postAcceptDroppedByFormat) + "]"
                + " passBoundaryFlushes=" + b.immediateStatistics.passBoundaryFlushes() + " matrixDiagnostics=" + b.MATRIX_DIAGNOSTICS
                // Per-format arrival and acceptance over every id the layouts class declares, built from COUNT rather
                // than from a hand-written list: the list stopped at 9 and hid POSITION_NORMAL's own entry, which is
                // the same shape as the counters that hid ids 6..10 before. droppedByFormat stays for whichever sites
                // increment it; it is empty today, and printing every id is what says so rather than a list that
                // stops at 5.
                // The group lifecycle at the end of the run: what the queue did with what it accepted.
                // immediateFlushes is incremented beside immediateGroupsDrawn, so both count groups drawn - closed
                // minus drawn is the number that reached no pipeline, and the pending/recorded/retired sizes are what
                // is still held. immediateGroupsKeptForOtherTargets, immediateDeferredNextFrame and
                // immediateRefusedWindowBudget are printed because they name the three ways a group can be held back;
                // reading them here is what makes a zero that cannot move (no increment site) visible as such.
                + " immediateGroupsDrawn=" + b.immediateStatistics.immediateGroupsDrawn()
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
                + " lightmapCoordCalls=" + b.immediateStatistics.lightmapCoordCalls()
                + " lightmapCoordGroups=" + b.immediateStatistics.lightmapCoordGroups()
                + " lightmapFullBrightGroups=" + b.immediateStatistics.lightmapFullBrightGroups()
                // The hook's own census: a zero here means the enableLightmap/disableLightmap injection never ran, and
                // the full-bright rule then degrades to the captured coordinate rather than to full-bright entities.
                + " lightmapUnitEnables=" + b.immediateStatistics.lightmapUnitEnables()
                + " lightmapUnitDisables=" + b.immediateStatistics.lightmapUnitDisables()
                // The ITEM variant split, so "the GUI item is bright" can be read as "it was lit and the coordinate was
                // full-bright" or as "it was never lit at all" rather than assumed from the picture.
                + " itemLitGroups=" + b.immediateStatistics.itemLitGroups()
                + " itemLitGuiGroups=" + b.immediateStatistics.itemLitGuiGroups()
                + " itemUnlitGroups=" + b.immediateStatistics.itemUnlitGroups()
                + " itemUnlitGuiGroups=" + b.immediateStatistics.itemUnlitGuiGroups()
                // The strip separators, in the census as well as in the font summary. The summary is printed by a
                // shutdown hook, and a leg whose fixture exits early loses it - measured: the font-strip2 leg has no
                // CELTIUM_FONT_GLYPH_SUMMARY line at all, so the counter that says the degenerate vertices were
                // inserted was unreadable exactly in the leg that needed it. This line is written during the run.
                + b.fontStats.run0()
                // The bind chain, in the summary rather than only in the bounded CELTIUM_UNIT_BIND lines: the arrivals
                // (bindTextureCalls = forwards, bindShortCircuits = the ones the hook skipped) against every mirror call
                // (immediateStateCalls). Reading the bounded print lines as arrivals is the mistake this line exists to
                // make impossible - 248 lines against 1,628,711 arrivals is what they actually said.
                + " bindTextureCalls=" + b.textureStats.bindTextureCalls()
                + " bindShortCircuits=" + b.textureStats.bindShortCircuits()
                + " immediateStateCalls=" + b.immediateStatistics.immediateStateCalls()
                + " immediateGroupsDiscarded=" + b.immediateStatistics.immediateGroupsDiscarded()
                + " immediateGroupsKeptForOtherTargets=" + b.immediateStatistics.immediateGroupsKeptForOtherTargets()
                + " immediateDeferredNextFrame=" + b.immediateStatistics.immediateDeferredNextFrame()
                + " immediateRefusedWindowBudget=" + b.immediateStatistics.immediateRefusedWindowBudget()
                + " groupsStaleTargetGeneration=" + b.immediateStatistics.groupsStaleTargetGeneration()
                + " groupsStaleWorldEpoch=" + b.immediateStatistics.groupsStaleWorldEpoch()
                + " worldEpoch=" + b.worldEpoch
                + " groupsDiscardedAtWorldTeardown=" + b.immediateStatistics.groupsDiscardedAtWorldTeardown()
                + " groupsDiscardedAtFullTeardown=" + b.immediateStatistics.groupsDiscardedAtFullTeardown()
                + " immediateWindows=" + b.immediateStatistics.immediateWindows()
                + " immediateWindowsThisFrame=" + b.immediateStatistics.immediateWindowsThisFrame()
                + " pendingImmediateIndices=" + b.pendingImmediateIndices
                + " immediateGroupsPending=" + b.immediateGroups.size()
                + " recordedGroupsHeld=" + b.recordedImmediateGroups.size()
                + " retiredGroupsHeld=" + b.retiredImmediateGroups.size()
                + " lastTerrainPassFrame=" + b.terrainStatistics.lastTerrainPassFrame() + " frameId=" + b.frameId
                + " matrixMirrorDelta=proj:" + b.immediateStatistics.mirroredProjectionDelta() + ",view:" + b.mirroredModelViewDelta
                // The application side of the stub's raw glMultMatrixf arrival census. Without this print the field was
                // an instrument with no output (proj-channel found it incremented at :3102 and printed nowhere), and
                // "arrivals without a mirrored multiply" is the pair that says a raw call reached GL with no owner.
                + ",mirroredMultiplies:" + b.mirroredMultiplies
                + ",comparisons:" + b.matrixComparisons + ",exact:" + b.matrixExactComparisons
                + ",translateCalls:" + b.translateCalls + ",zeroTranslates:" + b.translateZeroCalls
                + ",passSplits:" + b.passSplits + ",target:" + b.targetRegistry.currentIdentity()
                + ",worstView[" + (b.worstModelViewSample == null ? "none" : b.worstModelViewSample) + "]"
                + " trace[" + String.join(" ", b.trace) + "]"
                + ",steadyComparisons:" + b.steadyComparisons + ",crossPassSkipped:" + b.crossPassComparisonsSkipped + ",midSetupSkipped:" + b.midSetupComparisonsSkipped
                + ",steadyDelta=proj:" + b.steadyProjectionDelta + ",view:" + b.steadyModelViewDelta
                + ",derivedProj:" + b.derivedProjections + ",refused:" + b.derivedProjectionRefusals
                + ",cameraSeeds:" + b.cameraSetupSeeds
                + ",derivedCompared:" + b.derivedProjectionComparisons
                + ",writeId:" + b.derivedWriteIdentity + ",compareId:" + b.derivedCompareIdentity
                + ",loadIdentityWhilePending:" + b.loadIdentityWhilePending
                + ",loadIdentityByMode=proj:" + b.loadIdentityByMode[0] + ",view:" + b.loadIdentityByMode[1]
                + ",matrixModeByMode=proj:" + b.matrixModeCallsByMode[0] + ",view:" + b.matrixModeCallsByMode[1]
                + ",derivedDelta:" + b.derivedProjectionDelta
                + " derivedWorst[" + (b.derivedProjectionWorst == null ? "none" : b.derivedProjectionWorst) + "]"
                + " matrixDump[" + (b.matrixDump == null ? "none" : b.matrixDump) + "]"
                + " immediateSubmitAvgNs=" + (b.immediateStatistics.immediateBatchesOffered() == 0 ? 0
                : b.immediateSubmitNanos / b.immediateStatistics.immediateBatchesOffered())
                + " immediateSubmitWorldAvgNs=" + (b.immediateSubmitBaselineNanos < 0
                        || b.immediateStatistics.immediateBatchesOffered() <= b.immediateSubmitBaselineOffered ? 0
                : (b.immediateSubmitNanos - b.immediateSubmitBaselineNanos)
                        / (b.immediateStatistics.immediateBatchesOffered() - b.immediateSubmitBaselineOffered))
                + " submitBuckets=[<5us:" + b.immediateSubmitUnder5 + " 5-50:" + b.immediateSubmit5to50
                + " 50-200:" + b.immediateSubmit50to200 + " >200us:" + b.immediateSubmitTailCalls
                + " tailMillis=" + (b.immediateStatistics.immediateSubmitTailNanos() / 1000000L) + "]"
                + " tailCarried=[flush:" + b.tailCarriedFlush + " supply:" + b.tailCarriedSupply
                + " both:" + b.tailCarriedBoth + " neither:" + b.tailCarriedNeither + "]"
                // Audit B03/B04: what the descriptor pools did (pools, growths, sets, cached texture keys,
                // refusals) and the wait-outcome matrix (completions, timeouts, lost devices, unconfirmed waits),
                // in the run's own report rather than only on stdout at teardown - the teardown path is not reached
                // in every run, and "the counter is in a log line nobody read" is how a bound stays invisible.
                + " immediateDescriptors=[" + VulkanImmediateTexturePipeline.descriptorPoolSummary() + "]"
                // Task-46: the authoritative "what renders now" fields - the real driver, from the device that
                // renders. The old GL bootstrap probe also has a glRenderer/vulkanDevice pair and those describe a
                // different, earlier question (the GL identity and the interop contract); this is the live one.
                + " driverInfo=[" + VulkanContext.driverSummary() + "]"
                + " waitOutcomes=[" + VulkanContext.waitOutcomeSummary() + "]"
                + " identityPairs=[" + org.soutalab.celtium.vulkan.GllessFrameTarget.identityPairCensus() + "]"
                + " presentMode=[" + VulkanSwapchain.presentModeSummary()
                + " rebuilds=" + b.submissionStatistics.presentModeRebuilds() + "]"
                + " quarantined=[buffers=" + VulkanContext.quarantinedResourceCounts()[0]
                + " fences=" + VulkanContext.quarantinedResourceCounts()[1] + "]"
                + " matrixChannels=persp:" + b.immediateStatistics.perspectiveCalls() + ",frustum:" + b.frustumCalls
                + ",gluPerspective:" + b.mirroredGluPerspectives + ",gluSkipped:"
                + b.skippedGluPerspectives + ",gluSites:" + b.gluPerspectiveShapes.size()
                + ",gluSkipSites:" + b.gluPerspectiveSkips.size()
                + ",mode:" + b.matrixModeCalls + ",identity:" + b.loadIdentityCalls
                + ",texture:" + b.textureStats.bindTextureCalls();
    }

    /** The bounded window diagnostics {@link #b.drawPendingImmediate(Long)} used to run inline; see the call site. */
    void celtium$windowDiagnostics(long target) {
        if (b.DUMP_TEXTURE_IDS != null && b.dumpedTextureImages < 8 && b.presentHookCalls >= 120) {
            for (int di = 0; di < b.immediateGroups.size(); di++) {
                ImmediateGroup dumpGroup = b.immediateGroups.get(di);
                if (!b.DUMP_TEXTURE_IDS.contains(Integer.valueOf(dumpGroup.texture))) {
                    continue;
                }
                VulkanGlTexture dumpImage = dumpGroup.textureImage;
                if (dumpImage == null || dumpImage.image() == 0L) {
                    continue;
                }
                b.dumpedTextureImages++;
                java.nio.file.Path path = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                        "celtium-tex-" + dumpGroup.texture + "-" + dumpImage.width() + "x" + dumpImage.height()
                                + "-n" + b.dumpedTextureImages + ".png");
                boolean wrote = b.textureCopyService.celtium$dumpImage(dumpImage.image(), dumpImage.width(),
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
        if (b.VERTEX_DUMP_TEXTURE >= 0 && b.textureVertexDumps < 6 && !b.immediateGroups.isEmpty()) {
            ImmediateGroup probeGroup = b.immediateGroups.get(0);
            if (probeGroup.texture == b.VERTEX_DUMP_TEXTURE && probeGroup.mesh != null
                    && probeGroup.mesh.vertexAddress() != 0L) {
                b.textureVertexDumps++;
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
                long frameImage = b.frameTarget == null ? 0L : b.frameTarget.colorImage();
                System.out.println("[Celtium] CELTIUM_TEX_VERTICES id=" + b.VERTEX_DUMP_TEXTURE + " groups="
                        + b.immediateGroups.size() + " texMat=" + matrix
                        + " image=0x" + Long.toHexString(probeImage)
                        + " frameImage=0x" + Long.toHexString(frameImage)
                        + " selfSampling=" + (probeImage != 0L && probeImage == frameImage)
                        + " attached=0x" + Long.toHexString(b.passAttachedColorImage)
                        + " feedback=" + (probeImage != 0L && probeImage == b.passAttachedColorImage)
                        + probeVertices);
            }
        }
        if (b.WINDOW_CENSUS && b.immediateGroups.size() >= 10 && b.windowCensusLines < 80
                && b.groupDetailDumps < 3) {
            b.groupDetailDumps++;
            for (int gi = 0; gi < b.immediateGroups.size(); gi++) {
                ImmediateGroup detail = b.immediateGroups.get(gi);
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
        if (b.WINDOW_CENSUS && b.windowCensusLines < 80) {
            b.windowCensusLines++;
            java.util.LinkedHashSet<Integer> censusTextures = new java.util.LinkedHashSet<Integer>();
            for (int ci = 0; ci < b.immediateGroups.size() && censusTextures.size() < 6; ci++) {
                censusTextures.add(Integer.valueOf(b.immediateGroups.get(ci).texture));
            }
            System.out.println("[Celtium] CELTIUM_WINDOW_CENSUS target=0x" + Long.toHexString(target)
                    + " groups=" + b.immediateGroups.size() + " textures=" + censusTextures
                    + " projection=" + b.projectionSummary(b.immediateGroups.isEmpty() ? null
                            : b.immediateGroups.get(0).projection)
                    + " viewport=" + b.liveViewportWidth + "x" + b.liveViewportHeight
                    + " frame=" + b.frameId);
        }
        if (b.immediateGroups.size() >= 100) {
            // Counted every time, not only while the diagnostic line is allowed: the question this answers is how many
            // large windows a menu frame draws, and a log bounded to twelve cannot say that.
            b.largeWindowGroups += b.immediateGroups.size();
            b.largeWindows++;
            if (b.largeWindows <= 12) {
                System.out.println("[Celtium] CELTIUM_WINDOW_DRAW presents=" + b.presentHookCalls
                        + " groups=" + b.immediateGroups.size() + " viewport=" + b.liveViewportWidth
                        + "x" + b.liveViewportHeight + " target=0x" + Long.toHexString(target));
            }
            if (PeriodicLog.ENABLED && b.largeWindows % 60 == 0) {
                System.out.println("[Celtium] CELTIUM_LARGE_WINDOWS windows=" + b.largeWindows
                        + " groups=" + b.largeWindowGroups + " frame=" + b.frameId
                        + " presents=" + b.presentHookCalls);
            }
        }
        if (b.windowRouteLogs < 12 && b.immediateGroups.size() >= 100) {
            b.windowRouteLogs++;
            boolean isMainTarget = target == b.targetRegistry.mainIdentity();
            int exactDistinct = 0;
            int identityMatrices = 0;
            java.util.List<float[]> seen = new java.util.ArrayList<float[]>();
            java.util.Map<Long, java.util.List<float[]>> byPlacement =
                    new java.util.LinkedHashMap<Long, java.util.List<float[]>>();
            java.util.LinkedHashSet<Integer> textures = new java.util.LinkedHashSet<Integer>();
            for (int i = 0; i < b.immediateGroups.size(); i++) {
                float[] mv = b.immediateGroups.get(i).modelView;
                textures.add(Integer.valueOf(b.immediateGroups.get(i).texture));
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
            for (int i = 0; i < b.immediateGroups.size(); i++) {
                ImmediateGroup group = b.immediateGroups.get(i);
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
            for (int i = 0; i < b.immediateGroups.size() && i < 64; i++) {
                ImmediateGroup group = b.immediateGroups.get(i);
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
            for (int i = 0; i < b.immediateGroups.size(); i++) {
                ImmediateGroup group = b.immediateGroups.get(i);
                if (group.mesh == null || group.formatId != 2 || group.mesh.vertexAddress() == 0L) {
                    continue;
                }
                if (b.VERTEX_DUMP_TEXTURE >= 0 && group.texture != b.VERTEX_DUMP_TEXTURE) {
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
            for (int gi = 0; gi < b.immediateGroups.size() && gi < 8; gi++) {
                float[] offsetMv = b.immediateGroups.get(gi).modelView;
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
            for (int i = 0; i < b.immediateGroups.size(); i++) {
                float[] texMatrix = b.immediateGroups.get(i).texMatrix;
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
                    + " groups=" + b.immediateGroups.size() + " first=" + firstTextureMatrix);
            if (b.SAMPLE_WINDOW_IMAGES) {
                StringBuilder means = new StringBuilder();
                int sampled = 0;
                for (Object image : textureImages) {
                    if (sampled++ >= 6) {
                        break;
                    }
                    if (image instanceof VulkanGlTexture texture) {
                        float mean = b.textureCopyService.celtium$sampleImageMean(texture.image(),
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
                    + " groups=" + b.immediateGroups.size() + " textureImages=" + textureImages.size()
                    + " first=[" + descriptorList + "]");
            java.util.LinkedHashSet<Object> pipelines = new java.util.LinkedHashSet<Object>();
            for (int i = 0; i < b.immediateGroups.size(); i++) {
                ImmediateGroup group = b.immediateGroups.get(i);
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
                if (pipeline instanceof VulkanImmediateTexturePipeline texture) {
                    pipelineSummary.append("texture blends=").append(texture.celtium$blends())
                            .append(" depthWrite=").append(texture.celtium$depthWrites())
                            .append(" depthTest=").append(texture.celtium$depthTests())
                            .append(" blend=").append(texture.celtium$colorBlend().describe());
                } else if (pipeline instanceof VulkanImmediateItemPipeline item) {
                    pipelineSummary.append("item blends=").append(item.celtium$blends())
                            .append(" depthWrite=").append(item.celtium$depthWrites())
                            .append(" depthTest=").append(item.celtium$depthTests());
                } else if (pipeline instanceof VulkanImmediatePipeline colour) {
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
                    + " groups=" + b.immediateGroups.size() + " identity=" + identityMatrices
                    + " placements=" + byPlacement.size() + " perTileRotations=[" + perTile + "]"
                    + " textures=[" + textureList + "]"
                    + " route=" + GllessFrameTarget.routeLabelForBinding(target, isMainTarget)
                    + " projection=" + b.projectionSummary(b.immediateGroups.get(0).projection));
        }
    }

    void celtium$reportWindowState() {
        if (!b.WINDOW_DIAG) {
            return;
        }
        ++b.windowDiagFrames;
        if (false) {
            return;
        }
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
        System.out.println("[Celtium] CELTIUM_WINDOW_STATE frame=" + b.windowDiagFrames
                + " sdl=" + (window == 0L ? "none" : "0x" + Long.toHexString(window))
                + " flags=0x" + Long.toHexString(flags)
                + " pixelSize=" + (havePixels ? pixels[0] + "x" + pixels[1] : "unknown")
                + " display[" + display + "]");
    }

    /** {@return one line per target the drain visited}, with its entry counts. */
    String flushEntrySummary() {
        if (b.flushEntryPassesByTarget.isEmpty()) {
            return "none";
        }
        StringBuilder text = new StringBuilder();
        for (java.util.Map.Entry<Long, Integer> entry : b.flushEntryPassesByTarget.entrySet()) {
            text.append(Long.toHexString(entry.getKey().longValue())).append(":entries=").append(entry.getValue())
                    .append(",groupsAtEntry=")
                    .append(b.flushEntryGroupsByTarget.getOrDefault(entry.getKey(), Integer.valueOf(0)))
                    .append(' ');
        }
        return text.toString().trim();
    }

    /** {@return the offer-stage histogram}, so a stopped offer names its branch. */
    String offerStageSummary() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < b.offerStageReached.length; i++) {
            if (b.offerStageReached[i] != 0) {
                text.append(i).append(':').append(b.offerStageReached[i]).append(' ');
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
        if (!b.wantedGroupBoundsFormat(group.formatId)) {
            // The format filter is checked before the cap so that a run narrowed to one layout is not spent on the
            // first six groups of every other layout.
            return;
        }
        if (b.frameId < b.GROUP_BOUNDS_MIN_FRAME) {
            // Same reason, one level up: the loading screen and the menu own the first frames' groups, so a bounded
            // budget has to skip them to reach the frame the question is about.
            return;
        }
        if (b.GROUP_BOUNDS_GUI_ONLY && !FramePassTimings.guiSectionActive()) {
            // {@code -Dceltium.vulkan.groupBoundsGuiOnly=true}: only the groups created inside the HUD's own function.
            // Measured need (leg r212-world-hud19): the frame under test holds 39 immediate groups of which 23 are the
            // HUD's, so a bounded budget spent on the frame's first groups answers about the world and never about the
            // overlay - the same shape of mistake as the loading screen owning every early line.
            return;
        }
        int limit = b.GROUP_BOUNDS_DIAGNOSTICS ? b.GROUP_BOUNDS_LIMIT : 6;
        if (b.immediateDiagnostics >= limit) {
            return;
        }
        b.immediateDiagnostics++;
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
            int stride = mesh.layout() != null ? mesh.layout().stride : VulkanImmediatePipeline.VERTEX_BYTES;
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
                    positions.append(vertex > 0 ? ";" : "").append(b.round3(x)).append(',').append(b.round3(y))
                            .append(',').append(b.round3(z));
                    if (stride >= 20) {
                        uvs.append(vertex > 0 ? ";" : "")
                                .append(b.round3(MemoryUtil.memGetFloat(offset + 12))).append(',')
                                .append(b.round3(MemoryUtil.memGetFloat(offset + 16)));
                    }
                    perVertex.append(vertex > 0 ? ";" : "").append(b.round3(cx / cw)).append(',')
                            .append(b.round3(cy / cw)).append(',').append(b.round3(cz / cw)).append(',')
                            .append(b.round3(cw));
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
                    && !b.worldPassActive;
            if (((group.formatId == 6 || group.formatId == 7) && b.worldPassActive) || panoramaChain
                    || (b.GROUP_BOUNDS_GUI_ONLY && FramePassTimings.guiSectionActive())) {
                // The GUI-only filter is added to this condition as well as to the entry: the HUD's groups are
                // format 6/7/2 with worldPass=false, so the world-pass branch never printed their model-view or their
                // vertices - and those are the two fields that say whether a quad measured at NDC (0,0) collapsed
                // because its vertices are one point or because its model-view is degenerate. Measured on leg
                // r212-world-hud20: all 23 of the frame's HUD groups report ndc x[-0.00..0.00] y[-0.00..0.00] with a
                // correct ortho (m00=0.005 m11=-0.008), and nothing in that line said which of the two it was.
                chain = " mv=" + b.matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + b.currentGroupRotations + "]";
            } else if (group.formatId == VulkanImmediateFormats.ITEM && !b.worldPassActive) {
                // The GUI item family, added 2026-10-11: RenderItem draws a GUI item through
                // BufferBuilder.begin(7, DefaultVertexFormats.ITEM) - id 4 - so a sprite that comes out at the wrong
                // size is either this group's model-view (its scale is exactly diag(16,-16,16) with a translation of
                // (x+8, y+8, 100+zLevel), from RenderItem.setupGuiTransform) or the ITEM layout being read at the
                // wrong stride. The model-view is printed here so the two are told apart from one line: a wrong scale
                // shows in mv[0]/mv[5], a wrong stride shows in verts=[...] while mv stays 16/-16.
                chain = " mv=" + b.matrixText(modelView)
                        + " verts=[" + positions + "]"
                        + " uv=[" + uvs + "]"
                        + " clip=[" + perVertex + "]"
                        + " rot=[" + b.currentGroupRotations + "]";
            }
            // On stdout as well as in the report: the report keeps only the last recorded line, and the question this
            // answers - which groups an artefact frame is made of - is a sequence, not a sample. frame/group tie the
            // line to the frame dump the PNG is of, and the projection's own columns say whether the group was
            // captured with the world perspective, an ortho, or a stale stack.
            System.out.println("[Celtium] CELTIUM_GROUP_BOUNDS frame=" + b.frameId
                    + " group=" + b.immediateStatistics.immediateGroupsClosed() + " fmt=" + group.formatId
                    + " worldPass=" + b.worldPassActive + " target=" + group.target
                    + " texture=" + group.texture + " batches=" + group.batches
                    + " vertices=" + vertices + " indices=" + mesh.indexCount()
                    + " colour0=" + colour
                    // The attribute itself, read from the group's own vertex memory, beside the value the state
                    // capture produced: "the attribute is zero" and "the attribute is fine and something else ate
                    // the fragment" are different findings, and colour0 alone cannot tell them apart.
                    + " vertexColour0=" + b.celtium$firstVertexColour(group, mesh)
                    // round3, not fmt3: the GUI's ortho has m00 = 2/ScaledWidth, which at the pinned pose is
                    // 0.00469 - a *correct* GUI projection and a collapsed one both print "0.00" under fmt3's two
                    // decimals, so the field could not answer the one question it exists for. The trap is the same
                    // shape as the 16-byte-stride and pointerStride=1 readings: the instrument did not read what it
                    // looked like it read. m11 = -2/ScaledHeight = -0.00833 is the second tell.
                    + " proj=[m00=" + b.round3(projection[0]) + " m11=" + b.round3(projection[5])
                    + " m22=" + b.round3(projection[10]) + " m32=" + b.round3(projection[14]) + "]"
                    // The drawing state the group was captured with, which is what decides whether a group that
                    // measures on-screen can still be rejected: a depth-tested quad drawn after the world pass is
                    // tested against the world's depth unless something cleared the depth attachment in between.
                    + " state=" + b.stateText(group.state)
                    // The texture matrix the group was captured with, because it is the one input that moves a
                    // correct-looking quad's UVs somewhere else: a stale affine left on unit 0 by an earlier draw
                    // transforms this group's texture coordinates, and the sample then lands outside the sprite -
                    // which the alpha test turns into "drawn, accepted, no pixels". The block-breaking overlay is the
                    // measured case (its fragments are alpha-discarded with the real atlas bound), and this field is
                    // what tells "the group carries a transform" from "it does not".
                    + " texMat=" + (group.texMatrix == null ? "none"
                            : b.round3(group.texMatrix[0]) + "," + b.round3(group.texMatrix[1]) + ","
                                    + b.round3(group.texMatrix[2]) + "," + b.round3(group.texMatrix[3]) + ","
                                    + b.round3(group.texMatrix[4]) + "," + b.round3(group.texMatrix[5]))
                    + " ndc " + bounds + chain);
            b.record("group " + b.immediateDiagnostics + ": vertices=" + vertices
                    + " indices=" + mesh.indexCount() + " colour0=" + colour
                    + " proj=[m00=" + projection[0] + " m11=" + projection[5] + "] ndc " + bounds);
        } catch (RuntimeException | LinkageError error) {
            b.record("group diagnostic failed: " + b.describe(error));
        }
    }
}
