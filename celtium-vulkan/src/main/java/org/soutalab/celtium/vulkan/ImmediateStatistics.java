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

    /** Handoffs that actually carried geometry (the counters above count calls, many of which return early). */
    private long actualFlushes;
    /** BH-03: batches captured with the alpha test on, with a comparison other than GREATER, and with it off. */
    private long alphaFuncGroups;
    private long alphaTestDisabledGroups;
    private long appendCount;
    private long blendAlphaNonDefaultGroups;
    private long blendFuncIgnored;
    /**
     * How many 2-argument {@code glBlendFunc} calls reached the mirror, and the last pair they carried.
     * <p>
     * Before this counter existed a 2-argument call was visible only through {@link #blendOutlier}, which is set for
     * anything that is not the ordinary alpha pair - so a run in which the game called {@code blendFunc(ONE, ONE)}
     * and nothing else unusual happened reported no evidence at all, and the fact that the call never reached the
     * mirror was invisible.
     */
    private long blendFuncTwoArgCalls;
    /**
     * BH-07's per-batch counts, taken at capture with the group: batches with blending on, and of those the ones whose
     * alpha factor pair is not the {@code ONE / ONE_MINUS_SRC_ALPHA} pair every pipeline baked before the fix.
     * <p>
     * The second number is the one that matters. Before BH-07 it was zero <em>by construction</em> - the capture never
     * read the alpha pair - so a non-zero value in a leg is the proof that the pair now travels, and the pipeline
     * caches' size next to it is the proof that it also closes a pipeline rather than being captured and dropped.
     */
    private long blendGroups;
    private long clearOrderFlushes;
    private long cullDisabledGroups;
    /**
     * Immediate groups whose capture saw {@code GL_CULL_FACE} enabled, and the ones that saw it disabled.
     * <p>
     * BH-08's per-batch proof: before the fix every group fell into the disabled column, because nothing ever wrote
     * the mirror's cull flag. A leg that shows both columns non-empty (the HUD and the world differ) is the evidence
     * that the capability and the face now reach the pipeline key, which is stronger than "the state flows" read off
     * the source. Counted at capture, where the group's own state is taken.
     */
    private long cullEnabledGroups;
    /** Batches that joined the open group with the whole preflight skipped (OPT-18b's other half). */
    private long fastMergedBatches;
    private long flushCount;
    private long groupCloneCount;
    private long groupCreateCount;
    private long groupDrawCount;
    /** Viewports issued per group; BH-13's counter, so "the rectangle was restored" is readable, not assumed. */
    private long groupViewportSets;
    private long groupsStaleTargetGeneration;
    private long groupsStaleWorldEpoch;
    /** Groups drawn with GL's polygon offset applied as a Vulkan depth bias (BH-11). */
    private long immediateDepthBiasedGroups;
    /** Lines of {@code CELTIUM_DRAW_SITE} printed; bounded by {@code celtium.vulkan.enqAll} + {@code enqFormat}. */
    private long immediateDrawSiteLogged;
    private int immediateGroupsFlushed;
    private long immediateGroupsClosed;
    private long immediateGroupsDiscarded;
    private long immediateGroupsDrawn;
    /** Groups left in the queue because they belong to another render target (audit A32). */
    private long immediateGroupsKeptForOtherTargets;
    private long immediateRetryCount;
    private long immediateStateCalls;
    private long immediateTaken;
    private long immediateWindows;
    private long invalidImmediateRecords;
    /**
     * The ITEM family's batches split by which variant they got and by the stage they were offered in. This is the
     * instrument the hotbar measurement needed and did not have: a GUI item that arrives unlit never samples the light
     * map, so no light-map coordinate - captured or full-bright - can change its brightness, and a leg that reports
     * only the coordinate counters cannot tell that apart from "the rule did nothing".
     */
    private long itemLitGroups;
    private long itemLitGuiGroups;
    private long itemUnlitGroups;
    private long itemUnlitGuiGroups;
    /** Groups closed because a light's direction or colour changed without the lighting enable moving. */
    private long lightParamBoundarySplits;
    /** How many captures arrived and how many groups carried one into the push block, for the night measurement. */
    private long lightmapCoordCalls;
    private long lightmapCoordGroups;
    /**
     * Groups offered outside the world pass whose light-map coordinate is the full-bright pair instead of the captured
     * one. Named so the fix is visible in the same census as the capture it replaces: at zero the rule did not fire and
     * a hotbar item is still drawn with the world's light, whatever the picture looks like.
     */
    private long lightmapFullBrightGroups;
    private long lightmapUnitDisables;
    private long lightmapUnitEnables;
    /**
     * Line batches drawn at the 1.0 width this renderer implements when OpenGL asked for more (BH-12): the block
     * selection outline asks for 2.0 and the fishing rod's line is a line batch too, and under the GL-less window a
     * refusal draws nothing. Counted so the difference is reported rather than silent.
     */
    private long lineBatchesNarrowed;
    private long matrixSplits;
    /** BH-07: batches whose captured blend equation was not ADD. */
    private long nonAddBlendEquationGroups;
    private long nonGreaterAlphaFuncGroups;
    private long orderPreservingFlushes;
    private long orderingFlushFailures;
    /** BH-10: batches whose captured write mask was not all four channels, and the last such mask. */
    private long partialColorMaskGroups;
    /** Batches refused because the mirror they need has only ever received a partial write. */
    private long partialMirrorRefusals;
    /** Mirrors that had only a partial write and were supplied whole by the offer path (audit A04). */
    private long partialMirrorsCompleted;
    private long particleDescriptorRefusals;
    private long passBoundaryFlushes;
    private long preTransformedBatches;
    /** Groups opened on the pre-transformed path, batches appended to them, and splits a matrix still caused. */
    private long preTransformedGroups;
    private long prepareImmediateCalls;
    private long programQueryCount;
    /** Batches refused by a preflight that now runs before anything is appended (audit A08). */
    private long refusedBeforeAppend;
    private long scissorBoundarySplits;
    /** Groups whose {@code vkCmdSetScissor} was the game's captured clip intersected with the target rectangle. */
    private long scissorCapturedGroups;
    private long scissorLateCapturedGroups;
    /** Batches refused because a custom shader program was bound (the game's post-process chain and mod shaders). */
    private long shaderProgramRefusals;
    private long snapshotCaptures;
    private long snapshotServedCaptures;
    /** Groups whose sampled texture wrapper had been replaced between prepare and draw, and were re-prepared. */
    private long staleGroupDescriptorsRebuilt;
    /** Groups refused because their texture wrapper had moved and no rebuild was available for their pipeline. */
    private long staleGroupRefusals;
    private long stateSkipsDepthBias;
    private long texMatrixGroups;
    /** Passes abandoned because the uniform buffer could not be prepared. */
    private long uniformPreparationFailures;
    private long stateSkipsViewport;
    private long stateSkipsScissor;
    private long actualFlushCpuNanos;
    private long actualFlushNanos;
    private long appendNanos;
    private long captureStateNanos;
    private long groupCloneNanos;
    /** Split of the batch call outside the preflight: group (mesh) creation against the vertex append. */
    private long groupCreateNanos;
    private long groupDrawNanos;
    private int groupsDiscardedAtFullTeardown;
    private int groupsDiscardedAtWorldTeardown;
    private long immediateCompletedBatches;
    private long immediateSubmitTailNanos;
    /** Split of the batch call's remainder: matrices, state capture, state key, and the group's state clone. */
    private long matrixCopyNanos;
    private long orderingFlushDroppedBatches;
    private int postAcceptDroppedGroups;
    private long preTransformedVertices;
    /**
     * Groups dropped because they are the game's finished-frame composite drawn into the image they sample.
     * <p>
     * The identity-0 composite samples the main framebuffer's colour texture, and under the adoption path that texture
     * is published over the frame image - the same image the identity-0 pass attaches. Sampling an image while writing
     * it as a colour attachment is invalid usage without a feedback loop, and RADV returns black: measured on leg
     * r212-world-hud25, the presented frame became a single black colour the moment the GUI ortho was repaired and the
     * quad actually drew. This counter is what makes the drop visible instead of silent.
     */
    private long selfCompositeSkips;
    private long prepBranchNanos;
    /** Split of prepareImmediate: pipeline+texture lookup against the pipeline/descriptor branch. */
    private long prepLookupNanos;
    private long prepRegistryNanos;
    /** Which half of the lookup dominates: the terrain pipeline lookup or the texture registry lookup. */
    private long prepTerrainPipelineNanos;
    /** Split of the per-batch cost: time inside prepareImmediate (pipeline and descriptor preparation). */
    private long prepareImmediateNanos;
    /** The GL query the custom-program shutter makes per batch, timed: it is a synchronising call on some drivers. */
    private long programQueryNanos;
    private long stateKeyNanos;
    private boolean blendObserved;
    /** The last comparison function captured, as the raw GL enum; 0 means the test was disabled for that batch. */
    private int lastAlphaFunc;
    private int lastFrameAccepted;
    private int lastFrameDrawn;
    private String lastTwoArgBlendPair = "none";
    private String lastUniformPreparationFailure = "none";
    private float mirroredProjectionDelta = -1.0f;
    private String particleDescriptorFailureReason;    private long actualFlushWorstNanos;
    /** Flushes refused because no command buffer was recording (A11 exposes this path). */
    private int drawsWithoutCommandBuffer;
    private int frameAccepted;
    private int immediateBatchesOffered;
    private int immediateDeferredNextFrame;
    private int immediateMaxGroupVertices;
    /**
     * How often a batch was left to GL because the frame's window budget was already spent.
     * <p>
     * This is the alternative to deferring it to a later frame: the batch is drawn by GL in the frame that produced it,
     * which keeps ordering and latency right at the cost of GL doing that draw. Named so the configuration is honest.
     */
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

    /** Records one immediateGroupsFlushed. */
    void noteImmediateGroupsFlushed() {
        this.immediateGroupsFlushed++;
    }

    /** Sets immediateGroupsFlushed. */
    void setImmediateGroupsFlushed(int value) {
        this.immediateGroupsFlushed = value;
    }

    /** {@return immediateGroupsFlushed}. */
    int immediateGroupsFlushed() {
        return this.immediateGroupsFlushed;
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

    /** Group-loop iterations across the run, so the per-window scan's size is a number rather than an assumption. */
    private long groupLoopIterations;

    /** How many times the group loop has been entered, so an empty pass cannot be read as "never ran". */
    private long groupLoopCalls;

    /** Counts one pass of the group loop over {@code steps} groups. */
    void noteGroupLoopIterations(int steps) {
        this.groupLoopIterations += steps;
        this.groupLoopCalls++;
    }

    /** {@return how many times the group loop has been entered across the run}. */
    public long groupLoopCalls() {
        return this.groupLoopCalls;
    }

    /** {@return how many groups the immediate group loop has iterated over across the run}. */
    public long groupLoopIterations() {
        return this.groupLoopIterations;
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

    /**
     * Whether the per-state-setter call count is worth its increments. ~113,000 setters fire a frame on a world leg;
     * one field increment each is small per call but not free at that rate, and the number only matters to a leg
     * looking for call-volume regressions. Default off; {@code -Dceltium.vulkan.immediateStateCalls=true} or
     * diagnostics restores it. A settable counter ({@link #setImmediateStateCalls}) bypasses the gate on purpose.
     */
    private static final boolean COUNT_IMMEDIATE_STATE_CALLS =
            "true".equalsIgnoreCase(System.getProperty("celtium.vulkan.immediateStateCalls", "false"))
                    || VulkanDiagnostics.enabled();

    /** Records one immediateStateCalls. */
    void noteImmediateStateCalls() {
        if (COUNT_IMMEDIATE_STATE_CALLS) {
            this.immediateStateCalls++;
        }
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

    /** Records one group dropped as the game's finished-frame composite drawn into the image it samples. */
    void noteSelfCompositeSkips() {
        this.selfCompositeSkips++;
    }

    /** {@return how many groups were dropped as the game's finished-frame composite}. */
    long selfCompositeSkips() {
        return this.selfCompositeSkips;
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



    /**
     * {@return a one-line per-batch timing breakdown for the summary}, empty when no batch timing was recorded.
     * <p>
     * The five counters behind it are written only with {@code -Dceltium.vulkan.batchTimings=true}; the totals divided by the
     * offer count are what says whether a batch costs its state capture, its key, its program query or its preparation.
     */
    String batchTimingSummary() {
        long offers = this.batchTimedOffers;
        if (offers <= 0L) {
            return "";
        }
        return " batchTimings[nanosPerOffer total=" + (this.submitBatchNanos / offers)
                + " checks=" + (this.batchChecksNanos / offers)
                + " captureState=" + (this.captureStateNanos / offers)
                + " stateKey=" + (this.stateKeyNanos / offers)
                + " programQuery=" + (this.programQueryNanos / offers)
                + " matrixCopy=" + (this.matrixCopyNanos / offers)
                + " prepare=" + (this.prepareImmediateNanos / offers) + "]";
    }

    /** Batches whose five timers ran; the denominator of {@link #batchTimingSummary()}. */
    private long batchTimedOffers;

    /** Time spent inside one batch's submit path, end to end. */
    private long submitBatchNanos;

    /** Of that, the part spent in the pre-draw checks. */
    private long batchChecksNanos;

    /** Adds one batch's pre-draw check time. */
    void addBatchChecksNanos(long value) {
        this.batchChecksNanos += value;
    }

    /** Adds one batch's end-to-end submit time. */
    void addSubmitBatchNanos(long value) {
        this.submitBatchNanos += value;
    }

    /** Counts one batch whose timers ran. */
    void noteBatchTimed() {
        this.batchTimedOffers++;
    }
}
