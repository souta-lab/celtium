package org.soutalab.celtium.vulkan;

/**
 * ImmediateStatistics - the immediate domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
 * every leg record (scripts/cpu-contract/verify_line_baseline.txt), so this class does not choose an order: it
 * exposes one appendRunN per contiguous run of its fields and the backend calls them in the existing order. The
 * domains are interleaved in the line, so a single fragment() per class would reorder it.
 *
 * <p>Fields are package-private only for the wiring pass; the writers move in with the domain (R3a/R3b/R4/R5/R6)
 * and the visibility narrows then. Expressions that read another class or a helper (VulkanContext, CeltiumGlStub,
 * TextureCopyService, VulkanModelQuadCapture, the celtium\$* accessors, the *Summary helpers) are left as calls;
 * they are the wiring's decisions, not this class's fields.
 *
 * <p>Runs, in the line's order:
 * <li>run 0 (backend L8310-L8316): immediateState immediateOffered frameId flushCount orderPreservingFlushes actualFlushes actualFlushMillis actualFlushWorstMillis actualFlushCpuMillis</li>
 * <li>run 1 (backend L8343-L8343): immediateDrawSiteLogged</li>
 * <li>run 2 (backend L8365-L8379): groupViewportSets stateSkipsViewport stateSkipsScissor stateSkipsDepthBias cullEnabledGroups cullDisabledGroups immediateDepthBiasedGroups polygonOffsetCalls</li>
 * <li>run 3 (backend L8381-L8411): polygonOffsetFillEnabled polygonOffsetCalled gameClearsDroppedForeignTarget lastDroppedClearOwner lineBatchesNarrowed blendGroups blendAlphaNonDefaultGroups partialColorMaskGroups lastPartialColorMask colorMaskHistogram nonAddBlendEquationGroups scissorBoundarySplits lightParamBoundarySplits scissorCalls scissorToggles scissorCapturedGroups scissorLateCapturedGroups alphaFuncGroups nonGreaterAlphaFuncGroups alphaTestDisabledGroups lastAlphaFunc alphaSelectorCounts</li>
 * <li>run 4 (backend L8413-L8414): lastAlphaPushRef alphaReferenceValues</li>
 * <li>run 5 (backend L8421-L8437): orderingFlushFailures orderingFlushDroppedBatches lastOrderingFlushFailure batchCallCount batchCallMillis batchCallWorstMillis batchCallOpeningMillis batchCallOpeningCount batchCallCpuMillis threadCpuSupported threadWaitedMillis threadBlockedMillis threadCpuTotalMillis uniformPreparationFailures lastUniformPreparationFailure</li>
 * <li>run 6 (backend L8456-L8490): innerWallCpu prepareImmediateCalls prepareImmediateMillis groupCreateCount groupCreateMillis appendCount appendMillis programQueryCount programQueryMillis matrixCopyMillis captureStateMillis stateKeyMillis groupDrawCount groupDrawMillis groupCloneCount groupCloneMillis preTransformedGroups preTransformedBatches preTransformedVertices matrixSplits fastMergedBatches prepLookupMillis prepTerrainPipelineMillis prepRegistryMillis regAnnouncedCalls regAnnouncedMillis regStaleCalls regStaleMillis regHitCalls regAnnouncedSkips prepBranchMillis</li>
 * <li>run 7 (backend L8492-L8492): clearOrderFlushes</li>
 * <li>run 8 (backend L8494-L8496): refusedBeforeAppend partialMirrorRefusals partialMirrorsCompleted</li>
 * <li>run 9 (backend L8511-L8518): immediateRecordedBatches immediateSubmittedBatches immediateCompletedBatches immediateRetries invalidImmediateRecords backendCommandBuffer backendCommandState drawsWithoutCommandBuffer</li>
 * <li>run 10 (backend L8546-L8546): fogModeSource</li>
 * <li>run 11 (backend L8555-L8555): texMatrixGroups</li>
 * <li>run 12 (backend L8581-L8582): shaderProgramRefusals acceptedBatches</li>
 * <li>run 13 (backend L8617-L8622): lastFrameAccepted lastFrameDrawn immediateTaken immediateFlushes immediateGroupsClosed immediateMaxGroupVertices immediateRefusal snapshotCaptures snapshotServed</li>
 * <li>run 14 (backend L8625-L8626): postAcceptDroppedGroups particleDescriptorRefusals</li>
 * <li>run 15 (backend L8641-L8642): staleGroupDescriptorsRebuilt staleGroupRefusals</li>
 * <li>run 16 (backend L8660-L8671): blendObserved blendPairs blendFuncIgnored blendOutlier blendFuncTwoArg lastTwoArgBlendPair particleDescriptorFailure acceptedByFormat drawnByFormat droppedByFormat passBoundaryFlushes matrixDiagnostics</li>
 * <li>run 17 (backend L8683-L8683): immediateGroupsDrawn</li>
 * <li>run 18 (backend L8692-L8704): lightmapCoordCalls lightmapCoordGroups lightmapFullBrightGroups lightmapUnitEnables lightmapUnitDisables itemLitGroups itemLitGuiGroups itemUnlitGroups itemUnlitGuiGroups</li>
 * <li>run 19 (backend L8718-L8733): immediateStateCalls immediateGroupsDiscarded immediateGroupsKeptForOtherTargets immediateDeferredNextFrame immediateRefusedWindowBudget groupsStaleTargetGeneration groupsStaleWorldEpoch worldEpoch groupsDiscardedAtWorldTeardown groupsDiscardedAtFullTeardown immediateWindows immediateWindowsThisFrame pendingImmediateIndices immediateGroupsPending recordedGroupsHeld retiredGroupsHeld</li>
 * <li>run 20 (backend L8734-L8735): frameId matrixMirrorDelta</li>
 * <li>run 21 (backend L8757-L8759): immediateSubmitAvgNs immediateSubmitWorldAvgNs</li>
 * <li>run 22 (backend L8765-L8772): tailMillis tailCarried immediateDescriptors</li>
 * <li>run 23 (backend L8778-L8778): identityPairs</li>
 * <li>run 24 (backend L8783-L8783): matrixChannels</li>
 */
public final class ImmediateStatistics {

    /** backend field stateSkipsViewport (declared L?). */
    long stateSkipsViewport;
    /** backend field stateSkipsScissor (declared L?). */
    long stateSkipsScissor;
    /** backend field stateSkipsDepthBias (declared L?). */
    long stateSkipsDepthBias;
    /** backend field immediateDepthBiasedGroups (declared L?). */
    long immediateDepthBiasedGroups;
    /** backend field uniformPreparationFailures (declared L?). */
    long uniformPreparationFailures;
    /** backend field lastUniformPreparationFailure (declared L?). */
    long lastUniformPreparationFailure;
    /** backend field immediateRecordedBatches (declared L?). */
    long immediateRecordedBatches;
    /** backend field immediateSubmittedBatches (declared L?). */
    long immediateSubmittedBatches;
    /** backend field immediateCompletedBatches (declared L?). */
    long immediateCompletedBatches;
    /** backend field blendPairs (declared L?). */
    long blendPairs;
    /** backend field tailCarriedFlush (declared L?). */
    long tailCarriedFlush;
    /** backend field staleGroupDescriptorsRebuilt (declared L168). */
    int staleGroupDescriptorsRebuilt;
    /** backend field staleGroupRefusals (declared L170). */
    int staleGroupRefusals;
    /** backend field groupsStaleTargetGeneration (declared L298). */
    int groupsStaleTargetGeneration;
    /** backend field worldEpoch (declared L308). */
    int worldEpoch;
    /** backend field groupsStaleWorldEpoch (declared L309). */
    int groupsStaleWorldEpoch;
    /** backend field groupsDiscardedAtWorldTeardown (declared L310). */
    int groupsDiscardedAtWorldTeardown;
    /** backend field groupsDiscardedAtFullTeardown (declared L312). */
    int groupsDiscardedAtFullTeardown;
    /** backend field invalidImmediateRecords (declared L315). */
    long invalidImmediateRecords;
    /** backend field immediateRetryCount (declared L316). */
    long immediateRetryCount;
    /** backend field scissorBoundarySplits (declared L336). */
    long scissorBoundarySplits;
    /** backend field lightParamBoundarySplits (declared L338). */
    long lightParamBoundarySplits;
    /** backend field scissorCapturedGroups (declared L349). */
    long scissorCapturedGroups;
    /** backend field scissorLateCapturedGroups (declared L357). */
    long scissorLateCapturedGroups;
    /** backend field immediateStateCalls (declared L884). */
    int immediateStateCalls;
    /** backend field immediateBatchesOffered (declared L885). */
    int immediateBatchesOffered;
    /** backend field immediateTaken (declared L911). */
    int immediateTaken;
    /** backend field immediateSubmitTailNanos (declared L924). */
    long immediateSubmitTailNanos;
    /** backend field prepareImmediateNanos (declared L934). */
    long prepareImmediateNanos;
    /** backend field prepareImmediateCalls (declared L935). */
    int prepareImmediateCalls;
    /** backend field groupCreateNanos (declared L937). */
    long groupCreateNanos;
    /** backend field groupCreateCount (declared L942). */
    int groupCreateCount;
    /** backend field appendNanos (declared L943). */
    long appendNanos;
    /** backend field groupDrawNanos (declared L944). */
    long groupDrawNanos;
    /** backend field groupDrawCount (declared L945). */
    int groupDrawCount;
    /** backend field appendCount (declared L946). */
    int appendCount;
    /** backend field programQueryNanos (declared L948). */
    long programQueryNanos;
    /** backend field programQueryCount (declared L949). */
    int programQueryCount;
    /** backend field matrixCopyNanos (declared L951). */
    long matrixCopyNanos;
    /** backend field captureStateNanos (declared L952). */
    long captureStateNanos;
    /** backend field stateKeyNanos (declared L953). */
    long stateKeyNanos;
    /** backend field groupCloneNanos (declared L954). */
    long groupCloneNanos;
    /** backend field groupCloneCount (declared L955). */
    int groupCloneCount;
    /** backend field prepLookupNanos (declared L957). */
    long prepLookupNanos;
    /** backend field prepTerrainPipelineNanos (declared L959). */
    long prepTerrainPipelineNanos;
    /** backend field prepRegistryNanos (declared L960). */
    long prepRegistryNanos;
    /** backend field prepBranchNanos (declared L961). */
    long prepBranchNanos;
    /** backend field mirroredProjectionDelta (declared L987). */
    float mirroredProjectionDelta;
    /** backend field snapshotCaptures (declared L1020). */
    int snapshotCaptures;
    /** backend field snapshotServedCaptures (declared L1021). */
    int snapshotServedCaptures;
    /** backend field acceptedByFormat (declared L1025). */
    long acceptedByFormat;
    /** backend field partialMirrorRefusals (declared L1039). */
    int partialMirrorRefusals;
    /** backend field partialMirrorsCompleted (declared L1043). */
    int partialMirrorsCompleted;
    /** backend field refusedBeforeAppend (declared L1045). */
    int refusedBeforeAppend;
    /** backend field lineBatchesNarrowed (declared L1090). */
    int lineBatchesNarrowed;
    /** backend field lightmapCoordCalls (declared L1246). */
    int lightmapCoordCalls;
    /** backend field lightmapCoordGroups (declared L1247). */
    int lightmapCoordGroups;
    /** backend field lightmapFullBrightGroups (declared L1253). */
    int lightmapFullBrightGroups;
    /** backend field itemLitGroups (declared L1260). */
    int itemLitGroups;
    /** backend field itemLitGuiGroups (declared L1261). */
    int itemLitGuiGroups;
    /** backend field itemUnlitGroups (declared L1262). */
    int itemUnlitGroups;
    /** backend field itemUnlitGuiGroups (declared L1263). */
    int itemUnlitGuiGroups;
    /** backend field lightmapUnitEnables (declared L1271). */
    int lightmapUnitEnables;
    /** backend field lightmapUnitDisables (declared L1272). */
    int lightmapUnitDisables;
    /** backend field shaderProgramRefusals (declared L1332). */
    int shaderProgramRefusals;
    /** backend field postAcceptDroppedGroups (declared L1339). */
    int postAcceptDroppedGroups;
    /** backend field groupViewportSets (declared L1461). */
    long groupViewportSets;
    /** backend field cullEnabledGroups (declared L1470). */
    long cullEnabledGroups;
    /** backend field cullDisabledGroups (declared L1471). */
    long cullDisabledGroups;
    /** backend field blendGroups (declared L1480). */
    long blendGroups;
    /** backend field blendAlphaNonDefaultGroups (declared L1481). */
    long blendAlphaNonDefaultGroups;
    /** backend field partialColorMaskGroups (declared L1483). */
    long partialColorMaskGroups;
    /** backend field lastPartialColorMask (declared L1484). */
    int lastPartialColorMask;
    /** backend field nonAddBlendEquationGroups (declared L1497). */
    long nonAddBlendEquationGroups;
    /** backend field alphaFuncGroups (declared L1499). */
    long alphaFuncGroups;
    /** backend field nonGreaterAlphaFuncGroups (declared L1500). */
    long nonGreaterAlphaFuncGroups;
    /** backend field alphaTestDisabledGroups (declared L1501). */
    long alphaTestDisabledGroups;
    /** backend field lastAlphaFunc (declared L1503). */
    int lastAlphaFunc;
    /** backend field lastAlphaPushReference (declared L1515). */
    float lastAlphaPushReference;
    /** backend field orderingFlushFailures (declared L1534). */
    int orderingFlushFailures;
    /** backend field orderingFlushDroppedBatches (declared L1535). */
    long orderingFlushDroppedBatches;
    /** backend field texMatrixGroups (declared L1605). */
    int texMatrixGroups;
    /** backend field blendObserved (declared L1606). */
    boolean blendObserved;
    /** backend field blendFuncIgnored (declared L1611). */
    int blendFuncIgnored;
    /** backend field blendFuncTwoArgCalls (declared L1631). */
    int blendFuncTwoArgCalls;
    /** backend field lastTwoArgBlendPair (declared L1632). */
    String lastTwoArgBlendPair;
    /** backend field particleDescriptorRefusals (declared L1636). */
    int particleDescriptorRefusals;
    /** backend field particleDescriptorFailureReason (declared L1637). */
    String particleDescriptorFailureReason;
    /** backend field postAcceptDroppedByFormat (declared L1647). */
    long postAcceptDroppedByFormat;
    /** backend field passBoundaryFlushes (declared L1648). */
    int passBoundaryFlushes;
    /** backend field perspectiveCalls (declared L1839). */
    int perspectiveCalls;
    /** backend field immediateFlushes (declared L1864). */
    int immediateFlushes;
    /** backend field pendingImmediateIndices (declared L1865). */
    int pendingImmediateIndices;
    /** backend field preTransformedGroups (declared L1905). */
    int preTransformedGroups;
    /** backend field preTransformedBatches (declared L1906). */
    int preTransformedBatches;
    /** backend field preTransformedVertices (declared L1907). */
    long preTransformedVertices;
    /** backend field matrixSplits (declared L1908). */
    int matrixSplits;
    /** backend field fastMergedBatches (declared L1910). */
    int fastMergedBatches;
    /** backend field immediateWindows (declared L2202). */
    int immediateWindows;
    /** backend field immediateWindowsThisFrame (declared L2205). */
    int immediateWindowsThisFrame;
    /** backend field frameId (declared L2214). */
    long frameId;
    /** backend field flushCount (declared L2216). */
    long flushCount;
    /** backend field orderPreservingFlushes (declared L2221). */
    int orderPreservingFlushes;
    /** backend field actualFlushes (declared L2308). */
    int actualFlushes;
    /** backend field actualFlushNanos (declared L2309). */
    long actualFlushNanos;
    /** backend field actualFlushWorstNanos (declared L2310). */
    long actualFlushWorstNanos;
    /** backend field actualFlushCpuNanos (declared L2312). */
    long actualFlushCpuNanos;
    /** backend field clearOrderFlushes (declared L2313). */
    int clearOrderFlushes;
    /** backend field frameAccepted (declared L2315). */
    int frameAccepted;
    /** backend field lastFrameAccepted (declared L2317). */
    int lastFrameAccepted;
    /** backend field lastFrameDrawn (declared L2318). */
    int lastFrameDrawn;
    /** backend field immediateDeferredNextFrame (declared L2319). */
    int immediateDeferredNextFrame;
    /** backend field immediateRefusedWindowBudget (declared L2334). */
    int immediateRefusedWindowBudget;
    /** backend field immediateGroupsClosed (declared L2335). */
    int immediateGroupsClosed;
    /** backend field immediateMaxGroupVertices (declared L2336). */
    int immediateMaxGroupVertices;
    /** backend field immediateGroupsDrawn (declared L2400). */
    int immediateGroupsDrawn;
    /** backend field immediateGroupsDiscarded (declared L2401). */
    int immediateGroupsDiscarded;
    /** backend field drawnByFormat (declared L2410). */
    long drawnByFormat;
    /** backend field immediateDrawSiteLogged (declared L2530). */
    int immediateDrawSiteLogged;
    /** backend field immediateGroupsKeptForOtherTargets (declared L2532). */
    int immediateGroupsKeptForOtherTargets;
    /** backend field drawsWithoutCommandBuffer (declared L2541). */
    int drawsWithoutCommandBuffer;

    /** Run 0, reproducing backend L8310-L8316: immediateState immediateOffered frameId flushCount orderPreservingFlushes actualFlushes actualFlushMillis actualFlushWorstMillis actualFlushCpuMillis. */
    public void appendRun0(StringBuilder out) {
        out.append(" immediateState=");
        out.append(this.immediateStateCalls);
        out.append(" immediateOffered=");
        out.append(this.immediateBatchesOffered);
        out.append(" frameId=");
        out.append(this.frameId);
        out.append(" flushCount=");
        out.append(this.flushCount);
        out.append(" orderPreservingFlushes=");
        out.append(this.orderPreservingFlushes);
        out.append(" actualFlushes=");
        out.append(this.actualFlushes);
        out.append(" actualFlushMillis=");
        out.append((this.actualFlushNanos / 1000000L));
        out.append(" actualFlushWorstMillis=");
        out.append((this.actualFlushWorstNanos / 1000000L));
        out.append(" actualFlushCpuMillis=");
        out.append((this.actualFlushCpuNanos / 1000000L));
    }

    /** Run 1, reproducing backend L8343-L8343: immediateDrawSiteLogged. */
    public void appendRun1(StringBuilder out) {
        out.append(" immediateDrawSiteLogged=");
        out.append(this.immediateDrawSiteLogged);
    }

    /** Run 2, reproducing backend L8365-L8379: groupViewportSets stateSkipsViewport stateSkipsScissor stateSkipsDepthBias cullEnabledGroups cullDisabledGroups immediateDepthBiasedGroups polygonOffsetCalls. */
    public void appendRun2(StringBuilder out) {
        out.append(" groupViewportSets=");
        out.append(this.groupViewportSets);
        out.append(" stateSkipsViewport=");
        out.append(this.stateSkipsViewport);
        out.append(" stateSkipsScissor=");
        out.append(this.stateSkipsScissor);
        out.append(" stateSkipsDepthBias=");
        out.append(this.stateSkipsDepthBias);
        out.append(" cullEnabledGroups=");
        out.append(this.cullEnabledGroups);
        out.append(" cullDisabledGroups=");
        out.append(this.cullDisabledGroups);
        out.append(" immediateDepthBiasedGroups=");
        out.append(this.immediateDepthBiasedGroups);
        out.append(" polygonOffsetCalls=");
        out.append(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCallCount());
    }

    /** Run 3, reproducing backend L8381-L8411: polygonOffsetFillEnabled polygonOffsetCalled gameClearsDroppedForeignTarget lastDroppedClearOwner lineBatchesNarrowed blendGroups blendAlphaNonDefaultGroups partialColorMaskGroups lastPartialColorMask colorMaskHistogram nonAddBlendEquationGroups scissorBoundarySplits lightParamBoundarySplits scissorCalls scissorToggles scissorCapturedGroups scissorLateCapturedGroups alphaFuncGroups nonGreaterAlphaFuncGroups alphaTestDisabledGroups lastAlphaFunc alphaSelectorCounts. */
    public void appendRun3(StringBuilder out) {
        out.append(" polygonOffsetFillEnabled=");
        out.append(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetFillEnabled());
        out.append(" polygonOffsetCalled=");
        out.append(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.polygonOffsetCalled());
        out.append(" gameClearsDroppedForeignTarget=");
        out.append((offTargetTarget() == null ? -1 : offTargetTarget().gameClearsDroppedForeignTargetCount()));
        out.append(" lastDroppedClearOwner=");
        out.append((offTargetTarget() == null ? -1 : offTargetTarget().lastDroppedClearOwnerName()));
        out.append(" lineBatchesNarrowed=");
        out.append(this.lineBatchesNarrowed);
        out.append(" blendGroups=");
        out.append(this.blendGroups);
        out.append(" blendAlphaNonDefaultGroups=");
        out.append(this.blendAlphaNonDefaultGroups);
        out.append(" partialColorMaskGroups=");
        out.append(this.partialColorMaskGroups);
        out.append(" lastPartialColorMask=0x");
        out.append(Integer.toHexString(this.lastPartialColorMask));
        out.append(" colorMaskHistogram=");
        out.append(colorMaskHistogramText());
        out.append(" nonAddBlendEquationGroups=");
        out.append(this.nonAddBlendEquationGroups);
        out.append(" scissorBoundarySplits=");
        out.append(this.scissorBoundarySplits);
        out.append(" lightParamBoundarySplits=");
        out.append(this.lightParamBoundarySplits);
        out.append(" scissorCalls=");
        out.append(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorCallCount());
        out.append(" scissorToggles=");
        out.append(org.soutalab.celtium.vulkan.gl.CeltiumGlStub.scissorToggleCount());
        out.append(" scissorCapturedGroups=");
        out.append(this.scissorCapturedGroups);
        out.append(" scissorLateCapturedGroups=");
        out.append(this.scissorLateCapturedGroups);
        out.append(" alphaFuncGroups=");
        out.append(this.alphaFuncGroups);
        out.append(" nonGreaterAlphaFuncGroups=");
        out.append(this.nonGreaterAlphaFuncGroups);
        out.append(" alphaTestDisabledGroups=");
        out.append(this.alphaTestDisabledGroups);
        out.append(" lastAlphaFunc=");
        out.append(this.lastAlphaFunc);
        out.append(" alphaSelectorCounts=");
        out.append(alphaSelectorSummary());
    }

    /** Run 4, reproducing backend L8413-L8414: lastAlphaPushRef alphaReferenceValues. */
    public void appendRun4(StringBuilder out) {
        out.append(" lastAlphaPushRef=");
        out.append(round3(this.lastAlphaPushReference));
        out.append(" alphaReferenceValues=");
        out.append(alphaReferenceValueSummary());
    }

    /** Run 5, reproducing backend L8421-L8437: orderingFlushFailures orderingFlushDroppedBatches lastOrderingFlushFailure batchCallCount batchCallMillis batchCallWorstMillis batchCallOpeningMillis batchCallOpeningCount batchCallCpuMillis threadCpuSupported threadWaitedMillis threadBlockedMillis threadCpuTotalMillis uniformPreparationFailures lastUniformPreparationFailure. */
    public void appendRun5(StringBuilder out) {
        out.append(" orderingFlushFailures=");
        out.append(this.orderingFlushFailures);
        out.append(" orderingFlushDroppedBatches=");
        out.append(this.orderingFlushDroppedBatches);
        out.append(" lastOrderingFlushFailure=");
        out.append((this.lastOrderingFlushFailure == null ? "none" : this.lastOrderingFlushFailure.replace(' ', '_').substring(0, Math.min(200, this.lastOrderingFlushFailure.length()))));
        out.append(" batchCallCount=");
        out.append(batchCallCount);
        out.append(" batchCallMillis=");
        out.append((batchCallNanos / 1000000L));
        out.append(" batchCallWorstMillis=");
        out.append((batchCallWorstNanos / 1000000L));
        out.append(" batchCallOpeningMillis=");
        out.append((batchCallOpeningNanos / 1000000L));
        out.append(" batchCallOpeningCount=");
        out.append(batchCallOpeningCount);
        out.append(" batchCallCpuMillis=");
        out.append((batchCallCpuNanos / 1000000L));
        out.append(" threadCpuSupported=");
        out.append(org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend.THREAD_CPU_SUPPORTED);
        out.append(" threadWaitedMillis=");
        out.append(threadWaitedMillis());
        out.append(" threadBlockedMillis=");
        out.append(threadBlockedMillis());
        out.append(" threadCpuTotalMillis=");
        out.append((threadCpuNanos() / 1000000L));
        out.append(" uniformPreparationFailures=");
        out.append(this.uniformPreparationFailures);
        out.append(" lastUniformPreparationFailure=");
        out.append(this.lastUniformPreparationFailure);
    }

    /** Run 6, reproducing backend L8456-L8490: innerWallCpu prepareImmediateCalls prepareImmediateMillis groupCreateCount groupCreateMillis appendCount appendMillis programQueryCount programQueryMillis matrixCopyMillis captureStateMillis stateKeyMillis groupDrawCount groupDrawMillis groupCloneCount groupCloneMillis preTransformedGroups preTransformedBatches preTransformedVertices matrixSplits fastMergedBatches prepLookupMillis prepTerrainPipelineMillis prepRegistryMillis regAnnouncedCalls regAnnouncedMillis regStaleCalls regStaleMillis regHitCalls regAnnouncedSkips prepBranchMillis. */
    public void appendRun6(StringBuilder out) {
        out.append(" innerWallCpu=[preflight ");
        out.append((this.prepareImmediateNanos / 1000000L));
        out.append(" prepareImmediateCalls=");
        out.append(this.prepareImmediateCalls);
        out.append(" prepareImmediateMillis=");
        out.append((this.prepareImmediateNanos / 1000000L));
        out.append(" groupCreateCount=");
        out.append(this.groupCreateCount);
        out.append(" groupCreateMillis=");
        out.append((this.groupCreateNanos / 1000000L));
        out.append(" appendCount=");
        out.append(this.appendCount);
        out.append(" appendMillis=");
        out.append((this.appendNanos / 1000000L));
        out.append(" programQueryCount=");
        out.append(this.programQueryCount);
        out.append(" programQueryMillis=");
        out.append((this.programQueryNanos / 1000000L));
        out.append(" matrixCopyMillis=");
        out.append((this.matrixCopyNanos / 1000000L));
        out.append(" captureStateMillis=");
        out.append((this.captureStateNanos / 1000000L));
        out.append(" stateKeyMillis=");
        out.append((this.stateKeyNanos / 1000000L));
        out.append(" groupDrawCount=");
        out.append(this.groupDrawCount);
        out.append(" groupDrawMillis=");
        out.append((this.groupDrawNanos / 1000000L));
        out.append(" groupCloneCount=");
        out.append(this.groupCloneCount);
        out.append(" groupCloneMillis=");
        out.append((this.groupCloneNanos / 1000000L));
        out.append(" preTransformedGroups=");
        out.append(this.preTransformedGroups);
        out.append(" preTransformedBatches=");
        out.append(this.preTransformedBatches);
        out.append(" preTransformedVertices=");
        out.append(this.preTransformedVertices);
        out.append(" matrixSplits=");
        out.append(this.matrixSplits);
        out.append(" fastMergedBatches=");
        out.append(this.fastMergedBatches);
        out.append(" prepLookupMillis=");
        out.append((this.prepLookupNanos / 1000000L));
        out.append(" prepTerrainPipelineMillis=");
        out.append((this.prepTerrainPipelineNanos / 1000000L));
        out.append(" prepRegistryMillis=");
        out.append((this.prepRegistryNanos / 1000000L));
        out.append(" regAnnouncedCalls=");
        out.append(VulkanTextureRegistry.ANNOUNCED_CALLS);
        out.append(" regAnnouncedMillis=");
        out.append((VulkanTextureRegistry.ANNOUNCED_NANOS / 1000000L));
        out.append(" regStaleCalls=");
        out.append(VulkanTextureRegistry.STALE_CALLS);
        out.append(" regStaleMillis=");
        out.append((VulkanTextureRegistry.STALE_NANOS / 1000000L));
        out.append(" regHitCalls=");
        out.append(VulkanTextureRegistry.HIT_CALLS);
        out.append(" regAnnouncedSkips=");
        out.append(VulkanTextureRegistry.ANNOUNCED_SKIPS);
        out.append(" prepBranchMillis=");
        out.append((this.prepBranchNanos / 1000000L));
    }

    /** Run 7, reproducing backend L8492-L8492: clearOrderFlushes. */
    public void appendRun7(StringBuilder out) {
        out.append(" clearOrderFlushes=");
        out.append(this.clearOrderFlushes);
    }

    /** Run 8, reproducing backend L8494-L8496: refusedBeforeAppend partialMirrorRefusals partialMirrorsCompleted. */
    public void appendRun8(StringBuilder out) {
        out.append(" refusedBeforeAppend=");
        out.append(this.refusedBeforeAppend);
        out.append(" partialMirrorRefusals=");
        out.append(this.partialMirrorRefusals);
        out.append(" partialMirrorsCompleted=");
        out.append(this.partialMirrorsCompleted);
    }

    /** Run 9, reproducing backend L8511-L8518: immediateRecordedBatches immediateSubmittedBatches immediateCompletedBatches immediateRetries invalidImmediateRecords backendCommandBuffer backendCommandState drawsWithoutCommandBuffer. */
    public void appendRun9(StringBuilder out) {
        out.append(" immediateRecordedBatches=");
        out.append(this.immediateRecordedBatches);
        out.append(" immediateSubmittedBatches=");
        out.append(this.immediateSubmittedBatches);
        out.append(" immediateCompletedBatches=");
        out.append(this.immediateCompletedBatches);
        out.append(" immediateRetries=");
        out.append(this.immediateRetryCount);
        out.append(" invalidImmediateRecords=");
        out.append(this.invalidImmediateRecords);
        out.append(" backendCommandBuffer=");
        out.append(Long.toHexString(this.commandBuffer.address()));
        out.append(" backendCommandState=");
        out.append(this.commandState.state());
        out.append(" drawsWithoutCommandBuffer=");
        out.append(this.drawsWithoutCommandBuffer);
    }

    /** Run 10, reproducing backend L8546-L8546: fogModeSource. */
    public void appendRun10(StringBuilder out) {
        out.append(" fogModeSource=");
        out.append((VulkanFogMirror.hasValue() ? "mirror" : "gl_query"));
    }

    /** Run 11, reproducing backend L8555-L8555: texMatrixGroups. */
    public void appendRun11(StringBuilder out) {
        out.append(" texMatrixGroups=");
        out.append(this.texMatrixGroups);
    }

    /** Run 12, reproducing backend L8581-L8582: shaderProgramRefusals acceptedBatches. */
    public void appendRun12(StringBuilder out) {
        out.append(" shaderProgramRefusals=");
        out.append(this.shaderProgramRefusals);
        out.append(" acceptedBatches=");
        out.append(this.frameAccepted);
    }

    /** Run 13, reproducing backend L8617-L8622: lastFrameAccepted lastFrameDrawn immediateTaken immediateFlushes immediateGroupsClosed immediateMaxGroupVertices immediateRefusal snapshotCaptures snapshotServed. */
    public void appendRun13(StringBuilder out) {
        out.append(" lastFrameAccepted=");
        out.append(this.lastFrameAccepted);
        out.append(" lastFrameDrawn=");
        out.append(this.lastFrameDrawn);
        out.append(" immediateTaken=");
        out.append(this.immediateTaken);
        out.append(" immediateFlushes=");
        out.append(this.immediateFlushes);
        out.append(" immediateGroupsClosed=");
        out.append(this.immediateGroupsClosed);
        out.append(" immediateMaxGroupVertices=");
        out.append(this.immediateMaxGroupVertices);
        out.append(" immediateRefusal=");
        out.append(celtium$refusalText().replace(' ', '_'));
        out.append(" snapshotCaptures=");
        out.append(this.snapshotCaptures);
        out.append(" snapshotServed=");
        out.append(this.snapshotServedCaptures);
    }

    /** Run 14, reproducing backend L8625-L8626: postAcceptDroppedGroups particleDescriptorRefusals. */
    public void appendRun14(StringBuilder out) {
        out.append(" postAcceptDroppedGroups=");
        out.append(this.postAcceptDroppedGroups);
        out.append(" particleDescriptorRefusals=");
        out.append(this.particleDescriptorRefusals);
    }

    /** Run 15, reproducing backend L8641-L8642: staleGroupDescriptorsRebuilt staleGroupRefusals. */
    public void appendRun15(StringBuilder out) {
        out.append(" staleGroupDescriptorsRebuilt=");
        out.append(this.staleGroupDescriptorsRebuilt);
        out.append(" staleGroupRefusals=");
        out.append(this.staleGroupRefusals);
    }

    /** Run 16, reproducing backend L8660-L8671: blendObserved blendPairs blendFuncIgnored blendOutlier blendFuncTwoArg lastTwoArgBlendPair particleDescriptorFailure acceptedByFormat drawnByFormat droppedByFormat passBoundaryFlushes matrixDiagnostics. */
    public void appendRun16(StringBuilder out) {
        out.append(" blendObserved=");
        out.append(this.blendObserved);
        out.append(" blendPairs=");
        out.append(this.blendPairs);
        out.append(" blendFuncIgnored=");
        out.append(this.blendFuncIgnored);
        out.append(" blendOutlier=\"");
        out.append((this.blendOutlier == null ? "none" : this.blendOutlier));
        out.append(" blendFuncTwoArg=");
        out.append(this.blendFuncTwoArgCalls);
        out.append(" lastTwoArgBlendPair=\"");
        out.append(this.lastTwoArgBlendPair);
        out.append(" particleDescriptorFailure=\"");
        out.append(this.particleDescriptorFailureReason);
        out.append(" acceptedByFormat=[");
        out.append(celtium$perFormat(this.acceptedByFormat));
        out.append(" drawnByFormat=[");
        out.append(celtium$perFormat(this.drawnByFormat));
        out.append(" droppedByFormat=[");
        out.append(celtium$perFormat(this.postAcceptDroppedByFormat));
        out.append(" passBoundaryFlushes=");
        out.append(this.passBoundaryFlushes);
        out.append(" matrixDiagnostics=");
        out.append(MATRIX_DIAGNOSTICS);
    }

    /** Run 17, reproducing backend L8683-L8683: immediateGroupsDrawn. */
    public void appendRun17(StringBuilder out) {
        out.append(" immediateGroupsDrawn=");
        out.append(this.immediateGroupsDrawn);
    }

    /** Run 18, reproducing backend L8692-L8704: lightmapCoordCalls lightmapCoordGroups lightmapFullBrightGroups lightmapUnitEnables lightmapUnitDisables itemLitGroups itemLitGuiGroups itemUnlitGroups itemUnlitGuiGroups. */
    public void appendRun18(StringBuilder out) {
        out.append(" lightmapCoordCalls=");
        out.append(this.lightmapCoordCalls);
        out.append(" lightmapCoordGroups=");
        out.append(this.lightmapCoordGroups);
        out.append(" lightmapFullBrightGroups=");
        out.append(this.lightmapFullBrightGroups);
        out.append(" lightmapUnitEnables=");
        out.append(this.lightmapUnitEnables);
        out.append(" lightmapUnitDisables=");
        out.append(this.lightmapUnitDisables);
        out.append(" itemLitGroups=");
        out.append(this.itemLitGroups);
        out.append(" itemLitGuiGroups=");
        out.append(this.itemLitGuiGroups);
        out.append(" itemUnlitGroups=");
        out.append(this.itemUnlitGroups);
        out.append(" itemUnlitGuiGroups=");
        out.append(this.itemUnlitGuiGroups);
    }

    /** Run 19, reproducing backend L8718-L8733: immediateStateCalls immediateGroupsDiscarded immediateGroupsKeptForOtherTargets immediateDeferredNextFrame immediateRefusedWindowBudget groupsStaleTargetGeneration groupsStaleWorldEpoch worldEpoch groupsDiscardedAtWorldTeardown groupsDiscardedAtFullTeardown immediateWindows immediateWindowsThisFrame pendingImmediateIndices immediateGroupsPending recordedGroupsHeld retiredGroupsHeld. */
    public void appendRun19(StringBuilder out) {
        out.append(" immediateStateCalls=");
        out.append(this.immediateStateCalls);
        out.append(" immediateGroupsDiscarded=");
        out.append(this.immediateGroupsDiscarded);
        out.append(" immediateGroupsKeptForOtherTargets=");
        out.append(this.immediateGroupsKeptForOtherTargets);
        out.append(" immediateDeferredNextFrame=");
        out.append(this.immediateDeferredNextFrame);
        out.append(" immediateRefusedWindowBudget=");
        out.append(this.immediateRefusedWindowBudget);
        out.append(" groupsStaleTargetGeneration=");
        out.append(this.groupsStaleTargetGeneration);
        out.append(" groupsStaleWorldEpoch=");
        out.append(this.groupsStaleWorldEpoch);
        out.append(" worldEpoch=");
        out.append(this.worldEpoch);
        out.append(" groupsDiscardedAtWorldTeardown=");
        out.append(this.groupsDiscardedAtWorldTeardown);
        out.append(" groupsDiscardedAtFullTeardown=");
        out.append(this.groupsDiscardedAtFullTeardown);
        out.append(" immediateWindows=");
        out.append(this.immediateWindows);
        out.append(" immediateWindowsThisFrame=");
        out.append(this.immediateWindowsThisFrame);
        out.append(" pendingImmediateIndices=");
        out.append(this.pendingImmediateIndices);
        out.append(" immediateGroupsPending=");
        out.append(this.immediateGroups.size());
        out.append(" recordedGroupsHeld=");
        out.append(this.recordedImmediateGroups.size());
        out.append(" retiredGroupsHeld=");
        out.append(this.retiredImmediateGroups.size());
    }

    /** Run 20, reproducing backend L8734-L8735: frameId matrixMirrorDelta. */
    public void appendRun20(StringBuilder out) {
        out.append(" frameId=");
        out.append(this.frameId);
        out.append(" matrixMirrorDelta=proj:");
        out.append(this.mirroredProjectionDelta);
    }

    /** Run 21, reproducing backend L8757-L8759: immediateSubmitAvgNs immediateSubmitWorldAvgNs. */
    public void appendRun21(StringBuilder out) {
        out.append(" immediateSubmitAvgNs=");
        out.append((this.immediateBatchesOffered == 0 ? 0 : this.immediateSubmitNanos / this.immediateBatchesOffered));
        out.append(" immediateSubmitWorldAvgNs=");
        out.append((this.immediateSubmitBaselineNanos < 0 || this.immediateBatchesOffered <= this.immediateSubmitBaselineOffered ? 0 : (this.immediateSubmitNanos - this.immediateSubmitBaselineNanos) / (this.immediateBatchesOffered - this.immediateSubmitBaselineOffered)));
    }

    /** Run 22, reproducing backend L8765-L8772: tailMillis tailCarried immediateDescriptors. */
    public void appendRun22(StringBuilder out) {
        out.append(" tailMillis=");
        out.append((this.immediateSubmitTailNanos / 1000000L));
        out.append(" tailCarried=[flush:");
        out.append(this.tailCarriedFlush);
        out.append(" immediateDescriptors=[");
        out.append(VulkanImmediateTexturePipeline.descriptorPoolSummary());
    }

    /** Run 23, reproducing backend L8778-L8778: identityPairs. */
    public void appendRun23(StringBuilder out) {
        out.append(" identityPairs=[");
        out.append(org.soutalab.celtium.vulkan.GllessFrameTarget.identityPairCensus());
    }

    /** Run 24, reproducing backend L8783-L8783: matrixChannels. */
    public void appendRun24(StringBuilder out) {
        out.append(" matrixChannels=persp:");
        out.append(this.perspectiveCalls);
    }

}
