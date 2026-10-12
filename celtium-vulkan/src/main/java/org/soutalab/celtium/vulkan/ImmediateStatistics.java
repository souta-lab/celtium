package org.soutalab.celtium.vulkan;

/**
 * The immediate domain's counters (refactoring plan R2, stage 7).
 * <p>
 * This domain is the most interlocked one, so it moved in shape groups: the simple counts, the accumulators
 * (compound assignment), the value-shaped fields and the handful written in other forms. Every writer calls a named
 * method and the line reads the getters, so the field names and their order are unchanged.
 */
public final class ImmediateStatistics {

    private long immediateSubmittedBatches;

    private long immediateRecordedBatches;

    private long actualFlushes;
    private long alphaFuncGroups;
    private long alphaTestDisabledGroups;
    private long appendCount;
    private long blendAlphaNonDefaultGroups;
    private long blendFuncIgnored;
    private long blendFuncTwoArgCalls;
    private long blendGroups;
    private long clearOrderFlushes;
    private long cullDisabledGroups;
    private long cullEnabledGroups;
    private long fastMergedBatches;
    private long flushCount;
    private long groupCloneCount;
    private long groupCreateCount;
    private long groupDrawCount;
    private long groupViewportSets;
    private long groupsStaleTargetGeneration;
    private long groupsStaleWorldEpoch;
    private long immediateDepthBiasedGroups;
    private long immediateDrawSiteLogged;
    private int immediateFlushes;
    private long immediateGroupsClosed;
    private long immediateGroupsDiscarded;
    private long immediateGroupsDrawn;
    private long immediateGroupsKeptForOtherTargets;
    private long immediateRetryCount;
    private long immediateStateCalls;
    private long immediateTaken;
    private long immediateWindows;
    private long invalidImmediateRecords;
    private long itemLitGroups;
    private long itemLitGuiGroups;
    private long itemUnlitGroups;
    private long itemUnlitGuiGroups;
    private long lightParamBoundarySplits;
    private long lightmapCoordCalls;
    private long lightmapCoordGroups;
    private long lightmapFullBrightGroups;
    private long lightmapUnitDisables;
    private long lightmapUnitEnables;
    private long lineBatchesNarrowed;
    private long matrixSplits;
    private long nonAddBlendEquationGroups;
    private long nonGreaterAlphaFuncGroups;
    private long orderPreservingFlushes;
    private long orderingFlushFailures;
    private long partialColorMaskGroups;
    private long partialMirrorRefusals;
    private long partialMirrorsCompleted;
    private long particleDescriptorRefusals;
    private long passBoundaryFlushes;
    private long preTransformedBatches;
    private long preTransformedGroups;
    private long prepareImmediateCalls;
    private long programQueryCount;
    private long refusedBeforeAppend;
    private long scissorBoundarySplits;
    private long scissorCapturedGroups;
    private long scissorLateCapturedGroups;
    private long shaderProgramRefusals;
    private long snapshotCaptures;
    private long snapshotServedCaptures;
    private long staleGroupDescriptorsRebuilt;
    private long staleGroupRefusals;
    private long stateSkipsDepthBias;
    private long texMatrixGroups;
    private long uniformPreparationFailures;
    private long stateSkipsViewport;
    private long stateSkipsScissor;
    private long actualFlushCpuNanos;
    private long actualFlushNanos;
    private long appendNanos;
    private long captureStateNanos;
    private long groupCloneNanos;
    private long groupCreateNanos;
    private long groupDrawNanos;
    private int groupsDiscardedAtFullTeardown;
    private int groupsDiscardedAtWorldTeardown;
    private long immediateCompletedBatches;
    private long immediateSubmitTailNanos;
    private long matrixCopyNanos;
    private long orderingFlushDroppedBatches;
    private int postAcceptDroppedGroups;
    private long preTransformedVertices;
    private long prepBranchNanos;
    private long prepLookupNanos;
    private long prepRegistryNanos;
    private long prepTerrainPipelineNanos;
    private long prepareImmediateNanos;
    private long programQueryNanos;
    private long stateKeyNanos;
    private boolean blendObserved;
    private int lastAlphaFunc;
    private int lastFrameAccepted;
    private int lastFrameDrawn;
    private String lastTwoArgBlendPair = "none";
    private String lastUniformPreparationFailure = "none";
    private float mirroredProjectionDelta = -1.0f;
    private String particleDescriptorFailureReason;    private long actualFlushWorstNanos;
    private long blendPairs;    private int drawsWithoutCommandBuffer;
    private int frameAccepted;
    private int immediateBatchesOffered;
    private int immediateDeferredNextFrame;
    private int immediateMaxGroupVertices;
    private int immediateRefusedWindowBudget;
    private int immediateWindowsThisFrame;
    private float lastAlphaPushReference = Float.NaN;
    private int lastPartialColorMask = -1;
    private int perspectiveCalls;
    /** Records one actualFlushes. */
    void noteActualFlushes() {
        this.actualFlushes++;
    }

    /** Sets actualFlushes. */
    void setActualFlushes(long value) {
        this.actualFlushes = value;
    }

    /** {@return actualFlushes}. */
    long actualFlushes() {
        return this.actualFlushes;
    }

    /** Records one alphaFuncGroups. */
    void noteAlphaFuncGroups() {
        this.alphaFuncGroups++;
    }

    /** Sets alphaFuncGroups. */
    void setAlphaFuncGroups(long value) {
        this.alphaFuncGroups = value;
    }

    /** {@return alphaFuncGroups}. */
    long alphaFuncGroups() {
        return this.alphaFuncGroups;
    }

    /** Records one alphaTestDisabledGroups. */
    void noteAlphaTestDisabledGroups() {
        this.alphaTestDisabledGroups++;
    }

    /** Sets alphaTestDisabledGroups. */
    void setAlphaTestDisabledGroups(long value) {
        this.alphaTestDisabledGroups = value;
    }

    /** {@return alphaTestDisabledGroups}. */
    long alphaTestDisabledGroups() {
        return this.alphaTestDisabledGroups;
    }

    /** Records one appendCount. */
    void noteAppendCount() {
        this.appendCount++;
    }

    /** Sets appendCount. */
    void setAppendCount(long value) {
        this.appendCount = value;
    }

    /** {@return appendCount}. */
    long appendCount() {
        return this.appendCount;
    }

    /** Records one blendAlphaNonDefaultGroups. */
    void noteBlendAlphaNonDefaultGroups() {
        this.blendAlphaNonDefaultGroups++;
    }

    /** Sets blendAlphaNonDefaultGroups. */
    void setBlendAlphaNonDefaultGroups(long value) {
        this.blendAlphaNonDefaultGroups = value;
    }

    /** {@return blendAlphaNonDefaultGroups}. */
    long blendAlphaNonDefaultGroups() {
        return this.blendAlphaNonDefaultGroups;
    }

    /** Records one blendFuncIgnored. */
    void noteBlendFuncIgnored() {
        this.blendFuncIgnored++;
    }

    /** Sets blendFuncIgnored. */
    void setBlendFuncIgnored(long value) {
        this.blendFuncIgnored = value;
    }

    /** {@return blendFuncIgnored}. */
    long blendFuncIgnored() {
        return this.blendFuncIgnored;
    }

    /** Records one blendFuncTwoArgCalls. */
    void noteBlendFuncTwoArgCalls() {
        this.blendFuncTwoArgCalls++;
    }

    /** Sets blendFuncTwoArgCalls. */
    void setBlendFuncTwoArgCalls(long value) {
        this.blendFuncTwoArgCalls = value;
    }

    /** {@return blendFuncTwoArgCalls}. */
    long blendFuncTwoArgCalls() {
        return this.blendFuncTwoArgCalls;
    }

    /** Records one blendGroups. */
    void noteBlendGroups() {
        this.blendGroups++;
    }

    /** Sets blendGroups. */
    void setBlendGroups(long value) {
        this.blendGroups = value;
    }

    /** {@return blendGroups}. */
    long blendGroups() {
        return this.blendGroups;
    }

    /** Records one clearOrderFlushes. */
    void noteClearOrderFlushes() {
        this.clearOrderFlushes++;
    }

    /** Sets clearOrderFlushes. */
    void setClearOrderFlushes(long value) {
        this.clearOrderFlushes = value;
    }

    /** {@return clearOrderFlushes}. */
    long clearOrderFlushes() {
        return this.clearOrderFlushes;
    }

    /** Records one cullDisabledGroups. */
    void noteCullDisabledGroups() {
        this.cullDisabledGroups++;
    }

    /** Sets cullDisabledGroups. */
    void setCullDisabledGroups(long value) {
        this.cullDisabledGroups = value;
    }

    /** {@return cullDisabledGroups}. */
    long cullDisabledGroups() {
        return this.cullDisabledGroups;
    }

    /** Records one cullEnabledGroups. */
    void noteCullEnabledGroups() {
        this.cullEnabledGroups++;
    }

    /** Sets cullEnabledGroups. */
    void setCullEnabledGroups(long value) {
        this.cullEnabledGroups = value;
    }

    /** {@return cullEnabledGroups}. */
    long cullEnabledGroups() {
        return this.cullEnabledGroups;
    }

    /** Records one fastMergedBatches. */
    void noteFastMergedBatches() {
        this.fastMergedBatches++;
    }

    /** Sets fastMergedBatches. */
    void setFastMergedBatches(long value) {
        this.fastMergedBatches = value;
    }

    /** {@return fastMergedBatches}. */
    long fastMergedBatches() {
        return this.fastMergedBatches;
    }

    /** Records one flushCount. */
    void noteFlushCount() {
        this.flushCount++;
    }

    /** Sets flushCount. */
    void setFlushCount(long value) {
        this.flushCount = value;
    }

    /** {@return flushCount}. */
    long flushCount() {
        return this.flushCount;
    }

    /** Records one groupCloneCount. */
    void noteGroupCloneCount() {
        this.groupCloneCount++;
    }

    /** Sets groupCloneCount. */
    void setGroupCloneCount(long value) {
        this.groupCloneCount = value;
    }

    /** {@return groupCloneCount}. */
    long groupCloneCount() {
        return this.groupCloneCount;
    }

    /** Records one groupCreateCount. */
    void noteGroupCreateCount() {
        this.groupCreateCount++;
    }

    /** Sets groupCreateCount. */
    void setGroupCreateCount(long value) {
        this.groupCreateCount = value;
    }

    /** {@return groupCreateCount}. */
    long groupCreateCount() {
        return this.groupCreateCount;
    }

    /** Records one groupDrawCount. */
    void noteGroupDrawCount() {
        this.groupDrawCount++;
    }

    /** Sets groupDrawCount. */
    void setGroupDrawCount(long value) {
        this.groupDrawCount = value;
    }

    /** {@return groupDrawCount}. */
    long groupDrawCount() {
        return this.groupDrawCount;
    }

    /** Records one groupViewportSets. */
    void noteGroupViewportSets() {
        this.groupViewportSets++;
    }

    /** Sets groupViewportSets. */
    void setGroupViewportSets(long value) {
        this.groupViewportSets = value;
    }

    /** {@return groupViewportSets}. */
    long groupViewportSets() {
        return this.groupViewportSets;
    }

    /** Records one groupsStaleTargetGeneration. */
    void noteGroupsStaleTargetGeneration() {
        this.groupsStaleTargetGeneration++;
    }

    /** Sets groupsStaleTargetGeneration. */
    void setGroupsStaleTargetGeneration(long value) {
        this.groupsStaleTargetGeneration = value;
    }

    /** {@return groupsStaleTargetGeneration}. */
    long groupsStaleTargetGeneration() {
        return this.groupsStaleTargetGeneration;
    }

    /** Records one groupsStaleWorldEpoch. */
    void noteGroupsStaleWorldEpoch() {
        this.groupsStaleWorldEpoch++;
    }

    /** Sets groupsStaleWorldEpoch. */
    void setGroupsStaleWorldEpoch(long value) {
        this.groupsStaleWorldEpoch = value;
    }

    /** {@return groupsStaleWorldEpoch}. */
    long groupsStaleWorldEpoch() {
        return this.groupsStaleWorldEpoch;
    }

    /** Records one immediateDepthBiasedGroups. */
    void noteImmediateDepthBiasedGroups() {
        this.immediateDepthBiasedGroups++;
    }

    /** Sets immediateDepthBiasedGroups. */
    void setImmediateDepthBiasedGroups(long value) {
        this.immediateDepthBiasedGroups = value;
    }

    /** {@return immediateDepthBiasedGroups}. */
    long immediateDepthBiasedGroups() {
        return this.immediateDepthBiasedGroups;
    }

    /** Records one immediateDrawSiteLogged. */
    void noteImmediateDrawSiteLogged() {
        this.immediateDrawSiteLogged++;
    }

    /** Sets immediateDrawSiteLogged. */
    void setImmediateDrawSiteLogged(long value) {
        this.immediateDrawSiteLogged = value;
    }

    /** {@return immediateDrawSiteLogged}. */
    long immediateDrawSiteLogged() {
        return this.immediateDrawSiteLogged;
    }

    /** Records one immediateFlushes. */
    void noteImmediateFlushes() {
        this.immediateFlushes++;
    }

    /** Sets immediateFlushes. */
    void setImmediateFlushes(int value) {
        this.immediateFlushes = value;
    }

    /** {@return immediateFlushes}. */
    int immediateFlushes() {
        return this.immediateFlushes;
    }

    /** Records one immediateGroupsClosed. */
    void noteImmediateGroupsClosed() {
        this.immediateGroupsClosed++;
    }

    /** Sets immediateGroupsClosed. */
    void setImmediateGroupsClosed(long value) {
        this.immediateGroupsClosed = value;
    }

    /** {@return immediateGroupsClosed}. */
    long immediateGroupsClosed() {
        return this.immediateGroupsClosed;
    }

    /** Records one immediateGroupsDiscarded. */
    void noteImmediateGroupsDiscarded() {
        this.immediateGroupsDiscarded++;
    }

    /** Sets immediateGroupsDiscarded. */
    void setImmediateGroupsDiscarded(long value) {
        this.immediateGroupsDiscarded = value;
    }

    /** {@return immediateGroupsDiscarded}. */
    long immediateGroupsDiscarded() {
        return this.immediateGroupsDiscarded;
    }

    /** Records one immediateGroupsDrawn. */
    void noteImmediateGroupsDrawn() {
        this.immediateGroupsDrawn++;
    }

    /** Sets immediateGroupsDrawn. */
    void setImmediateGroupsDrawn(long value) {
        this.immediateGroupsDrawn = value;
    }

    /** {@return immediateGroupsDrawn}. */
    long immediateGroupsDrawn() {
        return this.immediateGroupsDrawn;
    }

    /** Records one immediateGroupsKeptForOtherTargets. */
    void noteImmediateGroupsKeptForOtherTargets() {
        this.immediateGroupsKeptForOtherTargets++;
    }

    /** Sets immediateGroupsKeptForOtherTargets. */
    void setImmediateGroupsKeptForOtherTargets(long value) {
        this.immediateGroupsKeptForOtherTargets = value;
    }

    /** {@return immediateGroupsKeptForOtherTargets}. */
    long immediateGroupsKeptForOtherTargets() {
        return this.immediateGroupsKeptForOtherTargets;
    }

    /** Records one immediateRetryCount. */
    void noteImmediateRetryCount() {
        this.immediateRetryCount++;
    }

    /** Sets immediateRetryCount. */
    void setImmediateRetryCount(long value) {
        this.immediateRetryCount = value;
    }

    /** {@return immediateRetryCount}. */
    long immediateRetryCount() {
        return this.immediateRetryCount;
    }

    /** Records one immediateStateCalls. */
    void noteImmediateStateCalls() {
        this.immediateStateCalls++;
    }

    /** Sets immediateStateCalls. */
    void setImmediateStateCalls(long value) {
        this.immediateStateCalls = value;
    }

    /** {@return immediateStateCalls}. */
    long immediateStateCalls() {
        return this.immediateStateCalls;
    }

    /** Records one immediateTaken. */
    void noteImmediateTaken() {
        this.immediateTaken++;
    }

    /** Sets immediateTaken. */
    void setImmediateTaken(long value) {
        this.immediateTaken = value;
    }

    /** {@return immediateTaken}. */
    long immediateTaken() {
        return this.immediateTaken;
    }

    /** Records one immediateWindows. */
    void noteImmediateWindows() {
        this.immediateWindows++;
    }

    /** Sets immediateWindows. */
    void setImmediateWindows(long value) {
        this.immediateWindows = value;
    }

    /** {@return immediateWindows}. */
    long immediateWindows() {
        return this.immediateWindows;
    }

    /** Records one invalidImmediateRecords. */
    void noteInvalidImmediateRecords() {
        this.invalidImmediateRecords++;
    }

    /** Sets invalidImmediateRecords. */
    void setInvalidImmediateRecords(long value) {
        this.invalidImmediateRecords = value;
    }

    /** {@return invalidImmediateRecords}. */
    long invalidImmediateRecords() {
        return this.invalidImmediateRecords;
    }

    /** Records one itemLitGroups. */
    void noteItemLitGroups() {
        this.itemLitGroups++;
    }

    /** Sets itemLitGroups. */
    void setItemLitGroups(long value) {
        this.itemLitGroups = value;
    }

    /** {@return itemLitGroups}. */
    long itemLitGroups() {
        return this.itemLitGroups;
    }

    /** Records one itemLitGuiGroups. */
    void noteItemLitGuiGroups() {
        this.itemLitGuiGroups++;
    }

    /** Sets itemLitGuiGroups. */
    void setItemLitGuiGroups(long value) {
        this.itemLitGuiGroups = value;
    }

    /** {@return itemLitGuiGroups}. */
    long itemLitGuiGroups() {
        return this.itemLitGuiGroups;
    }

    /** Records one itemUnlitGroups. */
    void noteItemUnlitGroups() {
        this.itemUnlitGroups++;
    }

    /** Sets itemUnlitGroups. */
    void setItemUnlitGroups(long value) {
        this.itemUnlitGroups = value;
    }

    /** {@return itemUnlitGroups}. */
    long itemUnlitGroups() {
        return this.itemUnlitGroups;
    }

    /** Records one itemUnlitGuiGroups. */
    void noteItemUnlitGuiGroups() {
        this.itemUnlitGuiGroups++;
    }

    /** Sets itemUnlitGuiGroups. */
    void setItemUnlitGuiGroups(long value) {
        this.itemUnlitGuiGroups = value;
    }

    /** {@return itemUnlitGuiGroups}. */
    long itemUnlitGuiGroups() {
        return this.itemUnlitGuiGroups;
    }

    /** Records one lightParamBoundarySplits. */
    void noteLightParamBoundarySplits() {
        this.lightParamBoundarySplits++;
    }

    /** Sets lightParamBoundarySplits. */
    void setLightParamBoundarySplits(long value) {
        this.lightParamBoundarySplits = value;
    }

    /** {@return lightParamBoundarySplits}. */
    long lightParamBoundarySplits() {
        return this.lightParamBoundarySplits;
    }

    /** Records one lightmapCoordCalls. */
    void noteLightmapCoordCalls() {
        this.lightmapCoordCalls++;
    }

    /** Sets lightmapCoordCalls. */
    void setLightmapCoordCalls(long value) {
        this.lightmapCoordCalls = value;
    }

    /** {@return lightmapCoordCalls}. */
    long lightmapCoordCalls() {
        return this.lightmapCoordCalls;
    }

    /** Records one lightmapCoordGroups. */
    void noteLightmapCoordGroups() {
        this.lightmapCoordGroups++;
    }

    /** Sets lightmapCoordGroups. */
    void setLightmapCoordGroups(long value) {
        this.lightmapCoordGroups = value;
    }

    /** {@return lightmapCoordGroups}. */
    long lightmapCoordGroups() {
        return this.lightmapCoordGroups;
    }

    /** Records one lightmapFullBrightGroups. */
    void noteLightmapFullBrightGroups() {
        this.lightmapFullBrightGroups++;
    }

    /** Sets lightmapFullBrightGroups. */
    void setLightmapFullBrightGroups(long value) {
        this.lightmapFullBrightGroups = value;
    }

    /** {@return lightmapFullBrightGroups}. */
    long lightmapFullBrightGroups() {
        return this.lightmapFullBrightGroups;
    }

    /** Records one lightmapUnitDisables. */
    void noteLightmapUnitDisables() {
        this.lightmapUnitDisables++;
    }

    /** Sets lightmapUnitDisables. */
    void setLightmapUnitDisables(long value) {
        this.lightmapUnitDisables = value;
    }

    /** {@return lightmapUnitDisables}. */
    long lightmapUnitDisables() {
        return this.lightmapUnitDisables;
    }

    /** Records one lightmapUnitEnables. */
    void noteLightmapUnitEnables() {
        this.lightmapUnitEnables++;
    }

    /** Sets lightmapUnitEnables. */
    void setLightmapUnitEnables(long value) {
        this.lightmapUnitEnables = value;
    }

    /** {@return lightmapUnitEnables}. */
    long lightmapUnitEnables() {
        return this.lightmapUnitEnables;
    }

    /** Records one lineBatchesNarrowed. */
    void noteLineBatchesNarrowed() {
        this.lineBatchesNarrowed++;
    }

    /** Sets lineBatchesNarrowed. */
    void setLineBatchesNarrowed(long value) {
        this.lineBatchesNarrowed = value;
    }

    /** {@return lineBatchesNarrowed}. */
    long lineBatchesNarrowed() {
        return this.lineBatchesNarrowed;
    }

    /** Records one matrixSplits. */
    void noteMatrixSplits() {
        this.matrixSplits++;
    }

    /** Sets matrixSplits. */
    void setMatrixSplits(long value) {
        this.matrixSplits = value;
    }

    /** {@return matrixSplits}. */
    long matrixSplits() {
        return this.matrixSplits;
    }

    /** Records one nonAddBlendEquationGroups. */
    void noteNonAddBlendEquationGroups() {
        this.nonAddBlendEquationGroups++;
    }

    /** Sets nonAddBlendEquationGroups. */
    void setNonAddBlendEquationGroups(long value) {
        this.nonAddBlendEquationGroups = value;
    }

    /** {@return nonAddBlendEquationGroups}. */
    long nonAddBlendEquationGroups() {
        return this.nonAddBlendEquationGroups;
    }

    /** Records one nonGreaterAlphaFuncGroups. */
    void noteNonGreaterAlphaFuncGroups() {
        this.nonGreaterAlphaFuncGroups++;
    }

    /** Sets nonGreaterAlphaFuncGroups. */
    void setNonGreaterAlphaFuncGroups(long value) {
        this.nonGreaterAlphaFuncGroups = value;
    }

    /** {@return nonGreaterAlphaFuncGroups}. */
    long nonGreaterAlphaFuncGroups() {
        return this.nonGreaterAlphaFuncGroups;
    }

    /** Records one orderPreservingFlushes. */
    void noteOrderPreservingFlushes() {
        this.orderPreservingFlushes++;
    }

    /** Sets orderPreservingFlushes. */
    void setOrderPreservingFlushes(long value) {
        this.orderPreservingFlushes = value;
    }

    /** {@return orderPreservingFlushes}. */
    long orderPreservingFlushes() {
        return this.orderPreservingFlushes;
    }

    /** Records one orderingFlushFailures. */
    void noteOrderingFlushFailures() {
        this.orderingFlushFailures++;
    }

    /** Sets orderingFlushFailures. */
    void setOrderingFlushFailures(long value) {
        this.orderingFlushFailures = value;
    }

    /** {@return orderingFlushFailures}. */
    long orderingFlushFailures() {
        return this.orderingFlushFailures;
    }

    /** Records one partialColorMaskGroups. */
    void notePartialColorMaskGroups() {
        this.partialColorMaskGroups++;
    }

    /** Sets partialColorMaskGroups. */
    void setPartialColorMaskGroups(long value) {
        this.partialColorMaskGroups = value;
    }

    /** {@return partialColorMaskGroups}. */
    long partialColorMaskGroups() {
        return this.partialColorMaskGroups;
    }

    /** Records one partialMirrorRefusals. */
    void notePartialMirrorRefusals() {
        this.partialMirrorRefusals++;
    }

    /** Sets partialMirrorRefusals. */
    void setPartialMirrorRefusals(long value) {
        this.partialMirrorRefusals = value;
    }

    /** {@return partialMirrorRefusals}. */
    long partialMirrorRefusals() {
        return this.partialMirrorRefusals;
    }

    /** Records one partialMirrorsCompleted. */
    void notePartialMirrorsCompleted() {
        this.partialMirrorsCompleted++;
    }

    /** Sets partialMirrorsCompleted. */
    void setPartialMirrorsCompleted(long value) {
        this.partialMirrorsCompleted = value;
    }

    /** {@return partialMirrorsCompleted}. */
    long partialMirrorsCompleted() {
        return this.partialMirrorsCompleted;
    }

    /** Records one particleDescriptorRefusals. */
    void noteParticleDescriptorRefusals() {
        this.particleDescriptorRefusals++;
    }

    /** Sets particleDescriptorRefusals. */
    void setParticleDescriptorRefusals(long value) {
        this.particleDescriptorRefusals = value;
    }

    /** {@return particleDescriptorRefusals}. */
    long particleDescriptorRefusals() {
        return this.particleDescriptorRefusals;
    }

    /** Records one passBoundaryFlushes. */
    void notePassBoundaryFlushes() {
        this.passBoundaryFlushes++;
    }

    /** Sets passBoundaryFlushes. */
    void setPassBoundaryFlushes(long value) {
        this.passBoundaryFlushes = value;
    }

    /** {@return passBoundaryFlushes}. */
    long passBoundaryFlushes() {
        return this.passBoundaryFlushes;
    }

    /** Records one preTransformedBatches. */
    void notePreTransformedBatches() {
        this.preTransformedBatches++;
    }

    /** Sets preTransformedBatches. */
    void setPreTransformedBatches(long value) {
        this.preTransformedBatches = value;
    }

    /** {@return preTransformedBatches}. */
    long preTransformedBatches() {
        return this.preTransformedBatches;
    }

    /** Records one preTransformedGroups. */
    void notePreTransformedGroups() {
        this.preTransformedGroups++;
    }

    /** Sets preTransformedGroups. */
    void setPreTransformedGroups(long value) {
        this.preTransformedGroups = value;
    }

    /** {@return preTransformedGroups}. */
    long preTransformedGroups() {
        return this.preTransformedGroups;
    }

    /** Records one prepareImmediateCalls. */
    void notePrepareImmediateCalls() {
        this.prepareImmediateCalls++;
    }

    /** Sets prepareImmediateCalls. */
    void setPrepareImmediateCalls(long value) {
        this.prepareImmediateCalls = value;
    }

    /** {@return prepareImmediateCalls}. */
    long prepareImmediateCalls() {
        return this.prepareImmediateCalls;
    }

    /** Records one programQueryCount. */
    void noteProgramQueryCount() {
        this.programQueryCount++;
    }

    /** Sets programQueryCount. */
    void setProgramQueryCount(long value) {
        this.programQueryCount = value;
    }

    /** {@return programQueryCount}. */
    long programQueryCount() {
        return this.programQueryCount;
    }

    /** Records one refusedBeforeAppend. */
    void noteRefusedBeforeAppend() {
        this.refusedBeforeAppend++;
    }

    /** Sets refusedBeforeAppend. */
    void setRefusedBeforeAppend(long value) {
        this.refusedBeforeAppend = value;
    }

    /** {@return refusedBeforeAppend}. */
    long refusedBeforeAppend() {
        return this.refusedBeforeAppend;
    }

    /** Records one scissorBoundarySplits. */
    void noteScissorBoundarySplits() {
        this.scissorBoundarySplits++;
    }

    /** Sets scissorBoundarySplits. */
    void setScissorBoundarySplits(long value) {
        this.scissorBoundarySplits = value;
    }

    /** {@return scissorBoundarySplits}. */
    long scissorBoundarySplits() {
        return this.scissorBoundarySplits;
    }

    /** Records one scissorCapturedGroups. */
    void noteScissorCapturedGroups() {
        this.scissorCapturedGroups++;
    }

    /** Sets scissorCapturedGroups. */
    void setScissorCapturedGroups(long value) {
        this.scissorCapturedGroups = value;
    }

    /** {@return scissorCapturedGroups}. */
    long scissorCapturedGroups() {
        return this.scissorCapturedGroups;
    }

    /** Records one scissorLateCapturedGroups. */
    void noteScissorLateCapturedGroups() {
        this.scissorLateCapturedGroups++;
    }

    /** Sets scissorLateCapturedGroups. */
    void setScissorLateCapturedGroups(long value) {
        this.scissorLateCapturedGroups = value;
    }

    /** {@return scissorLateCapturedGroups}. */
    long scissorLateCapturedGroups() {
        return this.scissorLateCapturedGroups;
    }

    /** Records one shaderProgramRefusals. */
    void noteShaderProgramRefusals() {
        this.shaderProgramRefusals++;
    }

    /** Sets shaderProgramRefusals. */
    void setShaderProgramRefusals(long value) {
        this.shaderProgramRefusals = value;
    }

    /** {@return shaderProgramRefusals}. */
    long shaderProgramRefusals() {
        return this.shaderProgramRefusals;
    }

    /** Records one snapshotCaptures. */
    void noteSnapshotCaptures() {
        this.snapshotCaptures++;
    }

    /** Sets snapshotCaptures. */
    void setSnapshotCaptures(long value) {
        this.snapshotCaptures = value;
    }

    /** {@return snapshotCaptures}. */
    long snapshotCaptures() {
        return this.snapshotCaptures;
    }

    /** Records one snapshotServedCaptures. */
    void noteSnapshotServedCaptures() {
        this.snapshotServedCaptures++;
    }

    /** Sets snapshotServedCaptures. */
    void setSnapshotServedCaptures(long value) {
        this.snapshotServedCaptures = value;
    }

    /** {@return snapshotServedCaptures}. */
    long snapshotServedCaptures() {
        return this.snapshotServedCaptures;
    }

    /** Records one staleGroupDescriptorsRebuilt. */
    void noteStaleGroupDescriptorsRebuilt() {
        this.staleGroupDescriptorsRebuilt++;
    }

    /** Sets staleGroupDescriptorsRebuilt. */
    void setStaleGroupDescriptorsRebuilt(long value) {
        this.staleGroupDescriptorsRebuilt = value;
    }

    /** {@return staleGroupDescriptorsRebuilt}. */
    long staleGroupDescriptorsRebuilt() {
        return this.staleGroupDescriptorsRebuilt;
    }

    /** Records one staleGroupRefusals. */
    void noteStaleGroupRefusals() {
        this.staleGroupRefusals++;
    }

    /** Sets staleGroupRefusals. */
    void setStaleGroupRefusals(long value) {
        this.staleGroupRefusals = value;
    }

    /** {@return staleGroupRefusals}. */
    long staleGroupRefusals() {
        return this.staleGroupRefusals;
    }

    /** Records one stateSkipsDepthBias. */
    void noteStateSkipsDepthBias() {
        this.stateSkipsDepthBias++;
    }

    /** Sets stateSkipsDepthBias. */
    void setStateSkipsDepthBias(long value) {
        this.stateSkipsDepthBias = value;
    }

    /** {@return stateSkipsDepthBias}. */
    long stateSkipsDepthBias() {
        return this.stateSkipsDepthBias;
    }

    /** Records one texMatrixGroups. */
    void noteTexMatrixGroups() {
        this.texMatrixGroups++;
    }

    /** Sets texMatrixGroups. */
    void setTexMatrixGroups(long value) {
        this.texMatrixGroups = value;
    }

    /** {@return texMatrixGroups}. */
    long texMatrixGroups() {
        return this.texMatrixGroups;
    }

    /** Records one uniformPreparationFailures. */
    void noteUniformPreparationFailures() {
        this.uniformPreparationFailures++;
    }

    /** Sets uniformPreparationFailures. */
    void setUniformPreparationFailures(long value) {
        this.uniformPreparationFailures = value;
    }

    /** {@return uniformPreparationFailures}. */
    long uniformPreparationFailures() {
        return this.uniformPreparationFailures;
    }

    /** Records one stateSkipsViewport. */
    void noteStateSkipsViewport() {
        this.stateSkipsViewport++;
    }

    /** Sets stateSkipsViewport. */
    void setStateSkipsViewport(long value) {
        this.stateSkipsViewport = value;
    }

    /** {@return stateSkipsViewport}. */
    long stateSkipsViewport() {
        return this.stateSkipsViewport;
    }

    /** Records one stateSkipsScissor. */
    void noteStateSkipsScissor() {
        this.stateSkipsScissor++;
    }

    /** Sets stateSkipsScissor. */
    void setStateSkipsScissor(long value) {
        this.stateSkipsScissor = value;
    }

    /** {@return stateSkipsScissor}. */
    long stateSkipsScissor() {
        return this.stateSkipsScissor;
    }

    /** Records one actualFlushCpuNanos. */
    void noteActualFlushCpuNanos() {
        this.actualFlushCpuNanos++;
    }

    /** Adds to actualFlushCpuNanos. */
    void addActualFlushCpuNanos(long value) {
        this.actualFlushCpuNanos += value;
    }

    /** Sets actualFlushCpuNanos. */
    void setActualFlushCpuNanos(long value) {
        this.actualFlushCpuNanos = value;
    }

    /** {@return actualFlushCpuNanos}. */
    long actualFlushCpuNanos() {
        return this.actualFlushCpuNanos;
    }

    /** Records one actualFlushNanos. */
    void noteActualFlushNanos() {
        this.actualFlushNanos++;
    }

    /** Adds to actualFlushNanos. */
    void addActualFlushNanos(long value) {
        this.actualFlushNanos += value;
    }

    /** Sets actualFlushNanos. */
    void setActualFlushNanos(long value) {
        this.actualFlushNanos = value;
    }

    /** {@return actualFlushNanos}. */
    long actualFlushNanos() {
        return this.actualFlushNanos;
    }

    /** Records one appendNanos. */
    void noteAppendNanos() {
        this.appendNanos++;
    }

    /** Adds to appendNanos. */
    void addAppendNanos(long value) {
        this.appendNanos += value;
    }

    /** Sets appendNanos. */
    void setAppendNanos(long value) {
        this.appendNanos = value;
    }

    /** {@return appendNanos}. */
    long appendNanos() {
        return this.appendNanos;
    }

    /** Records one captureStateNanos. */
    void noteCaptureStateNanos() {
        this.captureStateNanos++;
    }

    /** Adds to captureStateNanos. */
    void addCaptureStateNanos(long value) {
        this.captureStateNanos += value;
    }

    /** Sets captureStateNanos. */
    void setCaptureStateNanos(long value) {
        this.captureStateNanos = value;
    }

    /** {@return captureStateNanos}. */
    long captureStateNanos() {
        return this.captureStateNanos;
    }

    /** Records one groupCloneNanos. */
    void noteGroupCloneNanos() {
        this.groupCloneNanos++;
    }

    /** Adds to groupCloneNanos. */
    void addGroupCloneNanos(long value) {
        this.groupCloneNanos += value;
    }

    /** Sets groupCloneNanos. */
    void setGroupCloneNanos(long value) {
        this.groupCloneNanos = value;
    }

    /** {@return groupCloneNanos}. */
    long groupCloneNanos() {
        return this.groupCloneNanos;
    }

    /** Records one groupCreateNanos. */
    void noteGroupCreateNanos() {
        this.groupCreateNanos++;
    }

    /** Adds to groupCreateNanos. */
    void addGroupCreateNanos(long value) {
        this.groupCreateNanos += value;
    }

    /** Sets groupCreateNanos. */
    void setGroupCreateNanos(long value) {
        this.groupCreateNanos = value;
    }

    /** {@return groupCreateNanos}. */
    long groupCreateNanos() {
        return this.groupCreateNanos;
    }

    /** Records one groupDrawNanos. */
    void noteGroupDrawNanos() {
        this.groupDrawNanos++;
    }

    /** Adds to groupDrawNanos. */
    void addGroupDrawNanos(long value) {
        this.groupDrawNanos += value;
    }

    /** Sets groupDrawNanos. */
    void setGroupDrawNanos(long value) {
        this.groupDrawNanos = value;
    }

    /** {@return groupDrawNanos}. */
    long groupDrawNanos() {
        return this.groupDrawNanos;
    }

    /** Records one groupsDiscardedAtFullTeardown. */
    void noteGroupsDiscardedAtFullTeardown() {
        this.groupsDiscardedAtFullTeardown++;
    }

    /** Adds to groupsDiscardedAtFullTeardown. */
    void addGroupsDiscardedAtFullTeardown(int value) {
        this.groupsDiscardedAtFullTeardown += value;
    }

    /** Sets groupsDiscardedAtFullTeardown. */
    void setGroupsDiscardedAtFullTeardown(int value) {
        this.groupsDiscardedAtFullTeardown = value;
    }

    /** {@return groupsDiscardedAtFullTeardown}. */
    int groupsDiscardedAtFullTeardown() {
        return this.groupsDiscardedAtFullTeardown;
    }

    /** Records one groupsDiscardedAtWorldTeardown. */
    void noteGroupsDiscardedAtWorldTeardown() {
        this.groupsDiscardedAtWorldTeardown++;
    }

    /** Adds to groupsDiscardedAtWorldTeardown. */
    void addGroupsDiscardedAtWorldTeardown(int value) {
        this.groupsDiscardedAtWorldTeardown += value;
    }

    /** Sets groupsDiscardedAtWorldTeardown. */
    void setGroupsDiscardedAtWorldTeardown(int value) {
        this.groupsDiscardedAtWorldTeardown = value;
    }

    /** {@return groupsDiscardedAtWorldTeardown}. */
    int groupsDiscardedAtWorldTeardown() {
        return this.groupsDiscardedAtWorldTeardown;
    }

    /** Records one immediateCompletedBatches. */
    void noteImmediateCompletedBatches() {
        this.immediateCompletedBatches++;
    }

    /** Adds to immediateCompletedBatches. */
    void addImmediateCompletedBatches(long value) {
        this.immediateCompletedBatches += value;
    }

    /** Sets immediateCompletedBatches. */
    void setImmediateCompletedBatches(long value) {
        this.immediateCompletedBatches = value;
    }

    /** {@return immediateCompletedBatches}. */
    long immediateCompletedBatches() {
        return this.immediateCompletedBatches;
    }

    /** Records one immediateSubmitTailNanos. */
    void noteImmediateSubmitTailNanos() {
        this.immediateSubmitTailNanos++;
    }

    /** Adds to immediateSubmitTailNanos. */
    void addImmediateSubmitTailNanos(long value) {
        this.immediateSubmitTailNanos += value;
    }

    /** Sets immediateSubmitTailNanos. */
    void setImmediateSubmitTailNanos(long value) {
        this.immediateSubmitTailNanos = value;
    }

    /** {@return immediateSubmitTailNanos}. */
    long immediateSubmitTailNanos() {
        return this.immediateSubmitTailNanos;
    }

    /** Records one matrixCopyNanos. */
    void noteMatrixCopyNanos() {
        this.matrixCopyNanos++;
    }

    /** Adds to matrixCopyNanos. */
    void addMatrixCopyNanos(long value) {
        this.matrixCopyNanos += value;
    }

    /** Sets matrixCopyNanos. */
    void setMatrixCopyNanos(long value) {
        this.matrixCopyNanos = value;
    }

    /** {@return matrixCopyNanos}. */
    long matrixCopyNanos() {
        return this.matrixCopyNanos;
    }

    /** Records one orderingFlushDroppedBatches. */
    void noteOrderingFlushDroppedBatches() {
        this.orderingFlushDroppedBatches++;
    }

    /** Adds to orderingFlushDroppedBatches. */
    void addOrderingFlushDroppedBatches(long value) {
        this.orderingFlushDroppedBatches += value;
    }

    /** Sets orderingFlushDroppedBatches. */
    void setOrderingFlushDroppedBatches(long value) {
        this.orderingFlushDroppedBatches = value;
    }

    /** {@return orderingFlushDroppedBatches}. */
    long orderingFlushDroppedBatches() {
        return this.orderingFlushDroppedBatches;
    }

    /** Records one postAcceptDroppedGroups. */
    void notePostAcceptDroppedGroups() {
        this.postAcceptDroppedGroups++;
    }

    /** Adds to postAcceptDroppedGroups. */
    void addPostAcceptDroppedGroups(int value) {
        this.postAcceptDroppedGroups += value;
    }

    /** Sets postAcceptDroppedGroups. */
    void setPostAcceptDroppedGroups(int value) {
        this.postAcceptDroppedGroups = value;
    }

    /** {@return postAcceptDroppedGroups}. */
    int postAcceptDroppedGroups() {
        return this.postAcceptDroppedGroups;
    }

    /** Records one preTransformedVertices. */
    void notePreTransformedVertices() {
        this.preTransformedVertices++;
    }

    /** Adds to preTransformedVertices. */
    void addPreTransformedVertices(long value) {
        this.preTransformedVertices += value;
    }

    /** Sets preTransformedVertices. */
    void setPreTransformedVertices(long value) {
        this.preTransformedVertices = value;
    }

    /** {@return preTransformedVertices}. */
    long preTransformedVertices() {
        return this.preTransformedVertices;
    }

    /** Records one prepBranchNanos. */
    void notePrepBranchNanos() {
        this.prepBranchNanos++;
    }

    /** Adds to prepBranchNanos. */
    void addPrepBranchNanos(long value) {
        this.prepBranchNanos += value;
    }

    /** Sets prepBranchNanos. */
    void setPrepBranchNanos(long value) {
        this.prepBranchNanos = value;
    }

    /** {@return prepBranchNanos}. */
    long prepBranchNanos() {
        return this.prepBranchNanos;
    }

    /** Records one prepLookupNanos. */
    void notePrepLookupNanos() {
        this.prepLookupNanos++;
    }

    /** Adds to prepLookupNanos. */
    void addPrepLookupNanos(long value) {
        this.prepLookupNanos += value;
    }

    /** Sets prepLookupNanos. */
    void setPrepLookupNanos(long value) {
        this.prepLookupNanos = value;
    }

    /** {@return prepLookupNanos}. */
    long prepLookupNanos() {
        return this.prepLookupNanos;
    }

    /** Records one prepRegistryNanos. */
    void notePrepRegistryNanos() {
        this.prepRegistryNanos++;
    }

    /** Adds to prepRegistryNanos. */
    void addPrepRegistryNanos(long value) {
        this.prepRegistryNanos += value;
    }

    /** Sets prepRegistryNanos. */
    void setPrepRegistryNanos(long value) {
        this.prepRegistryNanos = value;
    }

    /** {@return prepRegistryNanos}. */
    long prepRegistryNanos() {
        return this.prepRegistryNanos;
    }

    /** Records one prepTerrainPipelineNanos. */
    void notePrepTerrainPipelineNanos() {
        this.prepTerrainPipelineNanos++;
    }

    /** Adds to prepTerrainPipelineNanos. */
    void addPrepTerrainPipelineNanos(long value) {
        this.prepTerrainPipelineNanos += value;
    }

    /** Sets prepTerrainPipelineNanos. */
    void setPrepTerrainPipelineNanos(long value) {
        this.prepTerrainPipelineNanos = value;
    }

    /** {@return prepTerrainPipelineNanos}. */
    long prepTerrainPipelineNanos() {
        return this.prepTerrainPipelineNanos;
    }

    /** Records one prepareImmediateNanos. */
    void notePrepareImmediateNanos() {
        this.prepareImmediateNanos++;
    }

    /** Adds to prepareImmediateNanos. */
    void addPrepareImmediateNanos(long value) {
        this.prepareImmediateNanos += value;
    }

    /** Sets prepareImmediateNanos. */
    void setPrepareImmediateNanos(long value) {
        this.prepareImmediateNanos = value;
    }

    /** {@return prepareImmediateNanos}. */
    long prepareImmediateNanos() {
        return this.prepareImmediateNanos;
    }

    /** Records one programQueryNanos. */
    void noteProgramQueryNanos() {
        this.programQueryNanos++;
    }

    /** Adds to programQueryNanos. */
    void addProgramQueryNanos(long value) {
        this.programQueryNanos += value;
    }

    /** Sets programQueryNanos. */
    void setProgramQueryNanos(long value) {
        this.programQueryNanos = value;
    }

    /** {@return programQueryNanos}. */
    long programQueryNanos() {
        return this.programQueryNanos;
    }

    /** Records one stateKeyNanos. */
    void noteStateKeyNanos() {
        this.stateKeyNanos++;
    }

    /** Adds to stateKeyNanos. */
    void addStateKeyNanos(long value) {
        this.stateKeyNanos += value;
    }

    /** Sets stateKeyNanos. */
    void setStateKeyNanos(long value) {
        this.stateKeyNanos = value;
    }

    /** {@return stateKeyNanos}. */
    long stateKeyNanos() {
        return this.stateKeyNanos;
    }

    /** Records blendObserved. */
    void setBlendObserved(boolean value) {
        this.blendObserved = value;
    }

    /** {@return blendObserved}. */
    boolean blendObserved() {
        return this.blendObserved;
    }

    /** Records lastAlphaFunc. */
    void setLastAlphaFunc(int value) {
        this.lastAlphaFunc = value;
    }

    /** {@return lastAlphaFunc}. */
    int lastAlphaFunc() {
        return this.lastAlphaFunc;
    }

    /** Records lastFrameAccepted. */
    void setLastFrameAccepted(int value) {
        this.lastFrameAccepted = value;
    }

    /** {@return lastFrameAccepted}. */
    int lastFrameAccepted() {
        return this.lastFrameAccepted;
    }

    /** Records lastFrameDrawn. */
    void setLastFrameDrawn(int value) {
        this.lastFrameDrawn = value;
    }

    /** {@return lastFrameDrawn}. */
    int lastFrameDrawn() {
        return this.lastFrameDrawn;
    }

    /** Records lastTwoArgBlendPair. */
    void setLastTwoArgBlendPair(String value) {
        this.lastTwoArgBlendPair = value;
    }

    /** {@return lastTwoArgBlendPair}. */
    String lastTwoArgBlendPair() {
        return this.lastTwoArgBlendPair;
    }

    /** Records lastUniformPreparationFailure. */
    void setLastUniformPreparationFailure(String value) {
        this.lastUniformPreparationFailure = value;
    }

    /** {@return lastUniformPreparationFailure}. */
    String lastUniformPreparationFailure() {
        return this.lastUniformPreparationFailure;
    }

    /** Records mirroredProjectionDelta. */
    void setMirroredProjectionDelta(float value) {
        this.mirroredProjectionDelta = value;
    }

    /** {@return mirroredProjectionDelta}. */
    float mirroredProjectionDelta() {
        return this.mirroredProjectionDelta;
    }

    /** Records particleDescriptorFailureReason. */
    void setParticleDescriptorFailureReason(String value) {
        this.particleDescriptorFailureReason = value;
    }

    /** {@return particleDescriptorFailureReason}. */
    String particleDescriptorFailureReason() {
        return this.particleDescriptorFailureReason;
    }

    /** Records one actualFlushWorstNanos. */
    void noteActualFlushWorstNanos() {
        this.actualFlushWorstNanos++;
    }

    /** Adds to actualFlushWorstNanos. */
    void addActualFlushWorstNanos(long value) {
        this.actualFlushWorstNanos += value;
    }

    /** Sets actualFlushWorstNanos. */
    void setActualFlushWorstNanos(long value) {
        this.actualFlushWorstNanos = value;
    }

    /** {@return actualFlushWorstNanos}. */
    long actualFlushWorstNanos() {
        return this.actualFlushWorstNanos;
    }

    /** Records one drawsWithoutCommandBuffer. */
    void noteDrawsWithoutCommandBuffer() {
        this.drawsWithoutCommandBuffer++;
    }

    /** Adds to drawsWithoutCommandBuffer. */
    void addDrawsWithoutCommandBuffer(int value) {
        this.drawsWithoutCommandBuffer += value;
    }

    /** Sets drawsWithoutCommandBuffer. */
    void setDrawsWithoutCommandBuffer(int value) {
        this.drawsWithoutCommandBuffer = value;
    }

    /** {@return drawsWithoutCommandBuffer}. */
    int drawsWithoutCommandBuffer() {
        return this.drawsWithoutCommandBuffer;
    }

    /** Records one frameAccepted. */
    void noteFrameAccepted() {
        this.frameAccepted++;
    }

    /** Adds to frameAccepted. */
    void addFrameAccepted(int value) {
        this.frameAccepted += value;
    }

    /** Sets frameAccepted. */
    void setFrameAccepted(int value) {
        this.frameAccepted = value;
    }

    /** {@return frameAccepted}. */
    int frameAccepted() {
        return this.frameAccepted;
    }

    /** Records one immediateBatchesOffered. */
    void noteImmediateBatchesOffered() {
        this.immediateBatchesOffered++;
    }

    /** Adds to immediateBatchesOffered. */
    void addImmediateBatchesOffered(int value) {
        this.immediateBatchesOffered += value;
    }

    /** Sets immediateBatchesOffered. */
    void setImmediateBatchesOffered(int value) {
        this.immediateBatchesOffered = value;
    }

    /** {@return immediateBatchesOffered}. */
    int immediateBatchesOffered() {
        return this.immediateBatchesOffered;
    }

    /** Records one immediateDeferredNextFrame. */
    void noteImmediateDeferredNextFrame() {
        this.immediateDeferredNextFrame++;
    }

    /** Adds to immediateDeferredNextFrame. */
    void addImmediateDeferredNextFrame(int value) {
        this.immediateDeferredNextFrame += value;
    }

    /** Sets immediateDeferredNextFrame. */
    void setImmediateDeferredNextFrame(int value) {
        this.immediateDeferredNextFrame = value;
    }

    /** {@return immediateDeferredNextFrame}. */
    int immediateDeferredNextFrame() {
        return this.immediateDeferredNextFrame;
    }

    /** Records one immediateMaxGroupVertices. */
    void noteImmediateMaxGroupVertices() {
        this.immediateMaxGroupVertices++;
    }

    /** Adds to immediateMaxGroupVertices. */
    void addImmediateMaxGroupVertices(int value) {
        this.immediateMaxGroupVertices += value;
    }

    /** Sets immediateMaxGroupVertices. */
    void setImmediateMaxGroupVertices(int value) {
        this.immediateMaxGroupVertices = value;
    }

    /** {@return immediateMaxGroupVertices}. */
    int immediateMaxGroupVertices() {
        return this.immediateMaxGroupVertices;
    }

    /** Records one immediateRefusedWindowBudget. */
    void noteImmediateRefusedWindowBudget() {
        this.immediateRefusedWindowBudget++;
    }

    /** Adds to immediateRefusedWindowBudget. */
    void addImmediateRefusedWindowBudget(int value) {
        this.immediateRefusedWindowBudget += value;
    }

    /** Sets immediateRefusedWindowBudget. */
    void setImmediateRefusedWindowBudget(int value) {
        this.immediateRefusedWindowBudget = value;
    }

    /** {@return immediateRefusedWindowBudget}. */
    int immediateRefusedWindowBudget() {
        return this.immediateRefusedWindowBudget;
    }

    /** Records one immediateWindowsThisFrame. */
    void noteImmediateWindowsThisFrame() {
        this.immediateWindowsThisFrame++;
    }

    /** Adds to immediateWindowsThisFrame. */
    void addImmediateWindowsThisFrame(int value) {
        this.immediateWindowsThisFrame += value;
    }

    /** Sets immediateWindowsThisFrame. */
    void setImmediateWindowsThisFrame(int value) {
        this.immediateWindowsThisFrame = value;
    }

    /** {@return immediateWindowsThisFrame}. */
    int immediateWindowsThisFrame() {
        return this.immediateWindowsThisFrame;
    }

    /** Records one lastAlphaPushReference. */
    void noteLastAlphaPushReference() {
        this.lastAlphaPushReference++;
    }

    /** Adds to lastAlphaPushReference. */
    void addLastAlphaPushReference(float value) {
        this.lastAlphaPushReference += value;
    }

    /** Sets lastAlphaPushReference. */
    void setLastAlphaPushReference(float value) {
        this.lastAlphaPushReference = value;
    }

    /** {@return lastAlphaPushReference}. */
    float lastAlphaPushReference() {
        return this.lastAlphaPushReference;
    }

    /** Records one lastPartialColorMask. */
    void noteLastPartialColorMask() {
        this.lastPartialColorMask++;
    }

    /** Adds to lastPartialColorMask. */
    void addLastPartialColorMask(int value) {
        this.lastPartialColorMask += value;
    }

    /** Sets lastPartialColorMask. */
    void setLastPartialColorMask(int value) {
        this.lastPartialColorMask = value;
    }

    /** {@return lastPartialColorMask}. */
    int lastPartialColorMask() {
        return this.lastPartialColorMask;
    }

    /** Records one perspectiveCalls. */
    void notePerspectiveCalls() {
        this.perspectiveCalls++;
    }

    /** Adds to perspectiveCalls. */
    void addPerspectiveCalls(int value) {
        this.perspectiveCalls += value;
    }

    /** Sets perspectiveCalls. */
    void setPerspectiveCalls(int value) {
        this.perspectiveCalls = value;
    }

    /** {@return perspectiveCalls}. */
    int perspectiveCalls() {
        return this.perspectiveCalls;
    }

    /** Records one immediateRecordedBatches. */
    void noteImmediateRecordedBatches() {
        this.immediateRecordedBatches++;
    }

    /** Sets immediateRecordedBatches. */
    void setImmediateRecordedBatches(long value) {
        this.immediateRecordedBatches = value;
    }

    /** {@return immediateRecordedBatches}. */
    long immediateRecordedBatches() {
        return this.immediateRecordedBatches;
    }


    /** Records one immediateSubmittedBatches. */
    void noteImmediateSubmittedBatches() {
        this.immediateSubmittedBatches++;
    }

    /** Sets immediateSubmittedBatches. */
    void setImmediateSubmittedBatches(long value) {
        this.immediateSubmittedBatches = value;
    }

    /** {@return immediateSubmittedBatches}. */
    long immediateSubmittedBatches() {
        return this.immediateSubmittedBatches;
    }


    /** Adds to immediateRecordedBatches. */
    void addImmediateRecordedBatches(long value) {
        this.immediateRecordedBatches += value;
    }


    /** Adds to immediateSubmittedBatches. */
    void addImmediateSubmittedBatches(long value) {
        this.immediateSubmittedBatches += value;
    }


}
