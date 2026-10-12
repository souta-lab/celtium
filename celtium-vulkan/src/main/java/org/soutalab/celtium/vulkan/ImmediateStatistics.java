package org.soutalab.celtium.vulkan;

/**
 * The immediate domain's counters, first group: those whose only writer is an increment (refactoring plan R2,
 * stage 7).
 * <p>
 * The immediate domain is the most interlocked one, so it moves in shape groups rather than in one pass: this group
 * is the simple counts, the next is the compound-assignment accumulators, then the value-shaped fields, then the
 * handful passed as arguments. Every writer calls a named method and the line reads the getters, so the field names
 * and their order are unchanged.
 */
public final class ImmediateStatistics {

    private int actualFlushes;
    private long alphaFuncGroups;
    private long alphaTestDisabledGroups;
    private int appendCount;
    private long blendAlphaNonDefaultGroups;
    private int blendFuncIgnored;
    private int blendFuncTwoArgCalls;
    private long blendGroups;
    private int clearOrderFlushes;
    private long cullDisabledGroups;
    private long cullEnabledGroups;
    private int fastMergedBatches;
    private long flushCount;
    private int groupCloneCount;
    private int groupCreateCount;
    private int groupDrawCount;
    private long groupViewportSets;
    private int groupsStaleTargetGeneration;
    private int groupsStaleWorldEpoch;
    private long immediateDepthBiasedGroups;
    private int immediateDrawSiteLogged;
    private int immediateFlushes;
    private int immediateGroupsClosed;
    private int immediateGroupsDiscarded;
    private int immediateGroupsDrawn;
    private int immediateGroupsKeptForOtherTargets;
    private long immediateRetryCount;
    private int immediateStateCalls;
    private int immediateTaken;
    private int immediateWindows;
    private long invalidImmediateRecords;
    private int itemLitGroups;
    private int itemLitGuiGroups;
    private int itemUnlitGroups;
    private int itemUnlitGuiGroups;
    private long lightParamBoundarySplits;
    private int lightmapCoordCalls;
    private int lightmapCoordGroups;
    private int lightmapFullBrightGroups;
    private int lightmapUnitDisables;
    private int lightmapUnitEnables;
    private int lineBatchesNarrowed;
    private int matrixSplits;
    private long nonAddBlendEquationGroups;
    private long nonGreaterAlphaFuncGroups;
    private int orderPreservingFlushes;
    private int orderingFlushFailures;
    private long partialColorMaskGroups;
    private int partialMirrorRefusals;
    private int partialMirrorsCompleted;
    private int particleDescriptorRefusals;
    private int passBoundaryFlushes;
    private int preTransformedBatches;
    private int preTransformedGroups;
    private int prepareImmediateCalls;
    private int programQueryCount;
    private int refusedBeforeAppend;
    private long scissorBoundarySplits;
    private long scissorCapturedGroups;
    private long scissorLateCapturedGroups;
    private int shaderProgramRefusals;
    private int snapshotCaptures;
    private int snapshotServedCaptures;
    private int staleGroupDescriptorsRebuilt;
    private int staleGroupRefusals;
    private long stateSkipsDepthBias;
    private int stateSkipsViewport;
    private int stateSkipsScissor;
    private int texMatrixGroups;
    private int uniformPreparationFailures;

    /** Records one actualFlushes. */
    void noteActualFlushes() {
        this.actualFlushes++;
    }

    /** {@return actualFlushes}. */
    int actualFlushes() {
        return this.actualFlushes;
    }

    /** Records one alphaFuncGroups. */
    void noteAlphaFuncGroups() {
        this.alphaFuncGroups++;
    }

    /** {@return alphaFuncGroups}. */
    long alphaFuncGroups() {
        return this.alphaFuncGroups;
    }

    /** Records one alphaTestDisabledGroups. */
    void noteAlphaTestDisabledGroups() {
        this.alphaTestDisabledGroups++;
    }

    /** {@return alphaTestDisabledGroups}. */
    long alphaTestDisabledGroups() {
        return this.alphaTestDisabledGroups;
    }

    /** Records one appendCount. */
    void noteAppendCount() {
        this.appendCount++;
    }

    /** {@return appendCount}. */
    int appendCount() {
        return this.appendCount;
    }

    /** Records one blendAlphaNonDefaultGroups. */
    void noteBlendAlphaNonDefaultGroups() {
        this.blendAlphaNonDefaultGroups++;
    }

    /** {@return blendAlphaNonDefaultGroups}. */
    long blendAlphaNonDefaultGroups() {
        return this.blendAlphaNonDefaultGroups;
    }

    /** Records one blendFuncIgnored. */
    void noteBlendFuncIgnored() {
        this.blendFuncIgnored++;
    }

    /** {@return blendFuncIgnored}. */
    int blendFuncIgnored() {
        return this.blendFuncIgnored;
    }

    /** Records one blendFuncTwoArgCalls. */
    void noteBlendFuncTwoArgCalls() {
        this.blendFuncTwoArgCalls++;
    }

    /** {@return blendFuncTwoArgCalls}. */
    int blendFuncTwoArgCalls() {
        return this.blendFuncTwoArgCalls;
    }

    /** Records one blendGroups. */
    void noteBlendGroups() {
        this.blendGroups++;
    }

    /** {@return blendGroups}. */
    long blendGroups() {
        return this.blendGroups;
    }

    /** Records one clearOrderFlushes. */
    void noteClearOrderFlushes() {
        this.clearOrderFlushes++;
    }

    /** {@return clearOrderFlushes}. */
    int clearOrderFlushes() {
        return this.clearOrderFlushes;
    }

    /** Records one cullDisabledGroups. */
    void noteCullDisabledGroups() {
        this.cullDisabledGroups++;
    }

    /** {@return cullDisabledGroups}. */
    long cullDisabledGroups() {
        return this.cullDisabledGroups;
    }

    /** Records one cullEnabledGroups. */
    void noteCullEnabledGroups() {
        this.cullEnabledGroups++;
    }

    /** {@return cullEnabledGroups}. */
    long cullEnabledGroups() {
        return this.cullEnabledGroups;
    }

    /** Records one fastMergedBatches. */
    void noteFastMergedBatches() {
        this.fastMergedBatches++;
    }

    /** {@return fastMergedBatches}. */
    int fastMergedBatches() {
        return this.fastMergedBatches;
    }

    /** Records one flushCount. */
    void noteFlushCount() {
        this.flushCount++;
    }

    /** {@return flushCount}. */
    long flushCount() {
        return this.flushCount;
    }

    /** Records one groupCloneCount. */
    void noteGroupCloneCount() {
        this.groupCloneCount++;
    }

    /** {@return groupCloneCount}. */
    int groupCloneCount() {
        return this.groupCloneCount;
    }

    /** Records one groupCreateCount. */
    void noteGroupCreateCount() {
        this.groupCreateCount++;
    }

    /** {@return groupCreateCount}. */
    int groupCreateCount() {
        return this.groupCreateCount;
    }

    /** Records one groupDrawCount. */
    void noteGroupDrawCount() {
        this.groupDrawCount++;
    }

    /** {@return groupDrawCount}. */
    int groupDrawCount() {
        return this.groupDrawCount;
    }

    /** Records one groupViewportSets. */
    void noteGroupViewportSets() {
        this.groupViewportSets++;
    }

    /** {@return groupViewportSets}. */
    long groupViewportSets() {
        return this.groupViewportSets;
    }

    /** Records one groupsStaleTargetGeneration. */
    void noteGroupsStaleTargetGeneration() {
        this.groupsStaleTargetGeneration++;
    }

    /** {@return groupsStaleTargetGeneration}. */
    int groupsStaleTargetGeneration() {
        return this.groupsStaleTargetGeneration;
    }

    /** Records one groupsStaleWorldEpoch. */
    void noteGroupsStaleWorldEpoch() {
        this.groupsStaleWorldEpoch++;
    }

    /** {@return groupsStaleWorldEpoch}. */
    int groupsStaleWorldEpoch() {
        return this.groupsStaleWorldEpoch;
    }

    /** Records one immediateDepthBiasedGroups. */
    void noteImmediateDepthBiasedGroups() {
        this.immediateDepthBiasedGroups++;
    }

    /** {@return immediateDepthBiasedGroups}. */
    long immediateDepthBiasedGroups() {
        return this.immediateDepthBiasedGroups;
    }

    /** Records one immediateDrawSiteLogged. */
    void noteImmediateDrawSiteLogged() {
        this.immediateDrawSiteLogged++;
    }

    /** {@return immediateDrawSiteLogged}. */
    int immediateDrawSiteLogged() {
        return this.immediateDrawSiteLogged;
    }

    /** Records one immediateFlushes. */
    void noteImmediateFlushes() {
        this.immediateFlushes++;
    }

    /** {@return immediateFlushes}. */
    int immediateFlushes() {
        return this.immediateFlushes;
    }

    /** Records one immediateGroupsClosed. */
    void noteImmediateGroupsClosed() {
        this.immediateGroupsClosed++;
    }

    /** {@return immediateGroupsClosed}. */
    int immediateGroupsClosed() {
        return this.immediateGroupsClosed;
    }

    /** Records one immediateGroupsDiscarded. */
    void noteImmediateGroupsDiscarded() {
        this.immediateGroupsDiscarded++;
    }

    /** {@return immediateGroupsDiscarded}. */
    int immediateGroupsDiscarded() {
        return this.immediateGroupsDiscarded;
    }

    /** Records one immediateGroupsDrawn. */
    void noteImmediateGroupsDrawn() {
        this.immediateGroupsDrawn++;
    }

    /** {@return immediateGroupsDrawn}. */
    int immediateGroupsDrawn() {
        return this.immediateGroupsDrawn;
    }

    /** Records one immediateGroupsKeptForOtherTargets. */
    void noteImmediateGroupsKeptForOtherTargets() {
        this.immediateGroupsKeptForOtherTargets++;
    }

    /** {@return immediateGroupsKeptForOtherTargets}. */
    int immediateGroupsKeptForOtherTargets() {
        return this.immediateGroupsKeptForOtherTargets;
    }

    /** Records one immediateRetryCount. */
    void noteImmediateRetryCount() {
        this.immediateRetryCount++;
    }

    /** {@return immediateRetryCount}. */
    long immediateRetryCount() {
        return this.immediateRetryCount;
    }

    /** Records one immediateStateCalls. */
    void noteImmediateStateCalls() {
        this.immediateStateCalls++;
    }

    /** {@return immediateStateCalls}. */
    int immediateStateCalls() {
        return this.immediateStateCalls;
    }

    /** Records one immediateTaken. */
    void noteImmediateTaken() {
        this.immediateTaken++;
    }

    /** {@return immediateTaken}. */
    int immediateTaken() {
        return this.immediateTaken;
    }

    /** Records one immediateWindows. */
    void noteImmediateWindows() {
        this.immediateWindows++;
    }

    /** {@return immediateWindows}. */
    int immediateWindows() {
        return this.immediateWindows;
    }

    /** Records one invalidImmediateRecords. */
    void noteInvalidImmediateRecords() {
        this.invalidImmediateRecords++;
    }

    /** {@return invalidImmediateRecords}. */
    long invalidImmediateRecords() {
        return this.invalidImmediateRecords;
    }

    /** Records one itemLitGroups. */
    void noteItemLitGroups() {
        this.itemLitGroups++;
    }

    /** {@return itemLitGroups}. */
    int itemLitGroups() {
        return this.itemLitGroups;
    }

    /** Records one itemLitGuiGroups. */
    void noteItemLitGuiGroups() {
        this.itemLitGuiGroups++;
    }

    /** {@return itemLitGuiGroups}. */
    int itemLitGuiGroups() {
        return this.itemLitGuiGroups;
    }

    /** Records one itemUnlitGroups. */
    void noteItemUnlitGroups() {
        this.itemUnlitGroups++;
    }

    /** {@return itemUnlitGroups}. */
    int itemUnlitGroups() {
        return this.itemUnlitGroups;
    }

    /** Records one itemUnlitGuiGroups. */
    void noteItemUnlitGuiGroups() {
        this.itemUnlitGuiGroups++;
    }

    /** {@return itemUnlitGuiGroups}. */
    int itemUnlitGuiGroups() {
        return this.itemUnlitGuiGroups;
    }

    /** Records one lightParamBoundarySplits. */
    void noteLightParamBoundarySplits() {
        this.lightParamBoundarySplits++;
    }

    /** {@return lightParamBoundarySplits}. */
    long lightParamBoundarySplits() {
        return this.lightParamBoundarySplits;
    }

    /** Records one lightmapCoordCalls. */
    void noteLightmapCoordCalls() {
        this.lightmapCoordCalls++;
    }

    /** {@return lightmapCoordCalls}. */
    int lightmapCoordCalls() {
        return this.lightmapCoordCalls;
    }

    /** Records one lightmapCoordGroups. */
    void noteLightmapCoordGroups() {
        this.lightmapCoordGroups++;
    }

    /** {@return lightmapCoordGroups}. */
    int lightmapCoordGroups() {
        return this.lightmapCoordGroups;
    }

    /** Records one lightmapFullBrightGroups. */
    void noteLightmapFullBrightGroups() {
        this.lightmapFullBrightGroups++;
    }

    /** {@return lightmapFullBrightGroups}. */
    int lightmapFullBrightGroups() {
        return this.lightmapFullBrightGroups;
    }

    /** Records one lightmapUnitDisables. */
    void noteLightmapUnitDisables() {
        this.lightmapUnitDisables++;
    }

    /** {@return lightmapUnitDisables}. */
    int lightmapUnitDisables() {
        return this.lightmapUnitDisables;
    }

    /** Records one lightmapUnitEnables. */
    void noteLightmapUnitEnables() {
        this.lightmapUnitEnables++;
    }

    /** {@return lightmapUnitEnables}. */
    int lightmapUnitEnables() {
        return this.lightmapUnitEnables;
    }

    /** Records one lineBatchesNarrowed. */
    void noteLineBatchesNarrowed() {
        this.lineBatchesNarrowed++;
    }

    /** {@return lineBatchesNarrowed}. */
    int lineBatchesNarrowed() {
        return this.lineBatchesNarrowed;
    }

    /** Records one matrixSplits. */
    void noteMatrixSplits() {
        this.matrixSplits++;
    }

    /** {@return matrixSplits}. */
    int matrixSplits() {
        return this.matrixSplits;
    }

    /** Records one nonAddBlendEquationGroups. */
    void noteNonAddBlendEquationGroups() {
        this.nonAddBlendEquationGroups++;
    }

    /** {@return nonAddBlendEquationGroups}. */
    long nonAddBlendEquationGroups() {
        return this.nonAddBlendEquationGroups;
    }

    /** Records one nonGreaterAlphaFuncGroups. */
    void noteNonGreaterAlphaFuncGroups() {
        this.nonGreaterAlphaFuncGroups++;
    }

    /** {@return nonGreaterAlphaFuncGroups}. */
    long nonGreaterAlphaFuncGroups() {
        return this.nonGreaterAlphaFuncGroups;
    }

    /** Records one orderPreservingFlushes. */
    void noteOrderPreservingFlushes() {
        this.orderPreservingFlushes++;
    }

    /** {@return orderPreservingFlushes}. */
    int orderPreservingFlushes() {
        return this.orderPreservingFlushes;
    }

    /** Records one orderingFlushFailures. */
    void noteOrderingFlushFailures() {
        this.orderingFlushFailures++;
    }

    /** {@return orderingFlushFailures}. */
    int orderingFlushFailures() {
        return this.orderingFlushFailures;
    }

    /** Records one partialColorMaskGroups. */
    void notePartialColorMaskGroups() {
        this.partialColorMaskGroups++;
    }

    /** {@return partialColorMaskGroups}. */
    long partialColorMaskGroups() {
        return this.partialColorMaskGroups;
    }

    /** Records one partialMirrorRefusals. */
    void notePartialMirrorRefusals() {
        this.partialMirrorRefusals++;
    }

    /** {@return partialMirrorRefusals}. */
    int partialMirrorRefusals() {
        return this.partialMirrorRefusals;
    }

    /** Records one partialMirrorsCompleted. */
    void notePartialMirrorsCompleted() {
        this.partialMirrorsCompleted++;
    }

    /** {@return partialMirrorsCompleted}. */
    int partialMirrorsCompleted() {
        return this.partialMirrorsCompleted;
    }

    /** Records one particleDescriptorRefusals. */
    void noteParticleDescriptorRefusals() {
        this.particleDescriptorRefusals++;
    }

    /** {@return particleDescriptorRefusals}. */
    int particleDescriptorRefusals() {
        return this.particleDescriptorRefusals;
    }

    /** Records one passBoundaryFlushes. */
    void notePassBoundaryFlushes() {
        this.passBoundaryFlushes++;
    }

    /** {@return passBoundaryFlushes}. */
    int passBoundaryFlushes() {
        return this.passBoundaryFlushes;
    }

    /** Records one preTransformedBatches. */
    void notePreTransformedBatches() {
        this.preTransformedBatches++;
    }

    /** {@return preTransformedBatches}. */
    int preTransformedBatches() {
        return this.preTransformedBatches;
    }

    /** Records one preTransformedGroups. */
    void notePreTransformedGroups() {
        this.preTransformedGroups++;
    }

    /** {@return preTransformedGroups}. */
    int preTransformedGroups() {
        return this.preTransformedGroups;
    }

    /** Records one prepareImmediateCalls. */
    void notePrepareImmediateCalls() {
        this.prepareImmediateCalls++;
    }

    /** {@return prepareImmediateCalls}. */
    int prepareImmediateCalls() {
        return this.prepareImmediateCalls;
    }

    /** Records one programQueryCount. */
    void noteProgramQueryCount() {
        this.programQueryCount++;
    }

    /** {@return programQueryCount}. */
    int programQueryCount() {
        return this.programQueryCount;
    }

    /** Records one refusedBeforeAppend. */
    void noteRefusedBeforeAppend() {
        this.refusedBeforeAppend++;
    }

    /** {@return refusedBeforeAppend}. */
    int refusedBeforeAppend() {
        return this.refusedBeforeAppend;
    }

    /** Records one scissorBoundarySplits. */
    void noteScissorBoundarySplits() {
        this.scissorBoundarySplits++;
    }

    /** {@return scissorBoundarySplits}. */
    long scissorBoundarySplits() {
        return this.scissorBoundarySplits;
    }

    /** Records one scissorCapturedGroups. */
    void noteScissorCapturedGroups() {
        this.scissorCapturedGroups++;
    }

    /** {@return scissorCapturedGroups}. */
    long scissorCapturedGroups() {
        return this.scissorCapturedGroups;
    }

    /** Records one scissorLateCapturedGroups. */
    void noteScissorLateCapturedGroups() {
        this.scissorLateCapturedGroups++;
    }

    /** {@return scissorLateCapturedGroups}. */
    long scissorLateCapturedGroups() {
        return this.scissorLateCapturedGroups;
    }

    /** Records one shaderProgramRefusals. */
    void noteShaderProgramRefusals() {
        this.shaderProgramRefusals++;
    }

    /** {@return shaderProgramRefusals}. */
    int shaderProgramRefusals() {
        return this.shaderProgramRefusals;
    }

    /** Records one snapshotCaptures. */
    void noteSnapshotCaptures() {
        this.snapshotCaptures++;
    }

    /** {@return snapshotCaptures}. */
    int snapshotCaptures() {
        return this.snapshotCaptures;
    }

    /** Records one snapshotServedCaptures. */
    void noteSnapshotServedCaptures() {
        this.snapshotServedCaptures++;
    }

    /** {@return snapshotServedCaptures}. */
    int snapshotServedCaptures() {
        return this.snapshotServedCaptures;
    }

    /** Records one staleGroupDescriptorsRebuilt. */
    void noteStaleGroupDescriptorsRebuilt() {
        this.staleGroupDescriptorsRebuilt++;
    }

    /** {@return staleGroupDescriptorsRebuilt}. */
    int staleGroupDescriptorsRebuilt() {
        return this.staleGroupDescriptorsRebuilt;
    }

    /** Records one staleGroupRefusals. */
    void noteStaleGroupRefusals() {
        this.staleGroupRefusals++;
    }

    /** {@return staleGroupRefusals}. */
    int staleGroupRefusals() {
        return this.staleGroupRefusals;
    }

    /** Records one stateSkipsViewport. */
    void noteStateSkipsViewport() {
        this.stateSkipsViewport++;
    }

    /** {@return stateSkipsViewport}. */
    int stateSkipsViewport() {
        return this.stateSkipsViewport;
    }

    /** Records one stateSkipsScissor. */
    void noteStateSkipsScissor() {
        this.stateSkipsScissor++;
    }

    /** {@return stateSkipsScissor}. */
    int stateSkipsScissor() {
        return this.stateSkipsScissor;
    }

    /** Records one stateSkipsDepthBias. */
    void noteStateSkipsDepthBias() {
        this.stateSkipsDepthBias++;
    }

    /** {@return stateSkipsDepthBias}. */
    long stateSkipsDepthBias() {
        return this.stateSkipsDepthBias;
    }

    /** Records one texMatrixGroups. */
    void noteTexMatrixGroups() {
        this.texMatrixGroups++;
    }

    /** {@return texMatrixGroups}. */
    int texMatrixGroups() {
        return this.texMatrixGroups;
    }

    /** Records one uniformPreparationFailures. */
    void noteUniformPreparationFailures() {
        this.uniformPreparationFailures++;
    }

    /** {@return uniformPreparationFailures}. */
    int uniformPreparationFailures() {
        return this.uniformPreparationFailures;
    }

}
