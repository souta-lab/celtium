package org.soutalab.celtium.vulkan;

/**
 * SubmissionStatistics - the submission domain's counters and its fragments of the verification line.
 *
 * <p>R2 (docs/history/refactoring-plan.md 6.1). The line's field names and order are a contract with the fixture and
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
 * <li>run 0 (backend L8320-L8336): bootstrapQueueWaits bootstrapQueueWaitMillis bootstrapQueueWaitWorstMillis bootstrapSubmitWaits bootstrapSubmitWaitMillis bootstrapSubmitWaitWorstMillis frameArenaFallbackGroups frameSlots frameSlotReuseWaits slotPolls slotPollConfirms slotWaitConfirms submitSequence completedSubmitSequence frameFenceWaitMillis frameFenceWaitCount frameFenceWaitWorstMillis</li>
 * <li>run 1 (backend L8352-L8357): submitTimeouts gpuStalled deviceWaitToken stalledGeometryDrops stalledGeometryDroppedBatches stallOrigin</li>
 * <li>run 2 (backend L8362-L8362): signalOnlySubmissions</li>
 * <li>run 3 (backend L8380-L8380): framePassSplit</li>
 * <li>run 4 (backend L8536-L8537): presentSucceeded presentSubmitDone</li>
 * <li>run 5 (backend L8544-L8545): presentedMean presentMarkerExpected</li>
 * <li>run 6 (backend L8614-L8616): swapchainOutOfDate acquireOutOfDate retiredFlushes retire</li>
 * <li>run 7 (backend L8627-L8627): gpuStalled stalledStops</li>
 * <li>run 8 (backend L8643-L8648): stalledTeardownSkips idleWaits injectedIdleFailures idleWaitTimeouts idleWaitRetries</li>
 * <li>run 9 (backend L8656-L8656): bootstrapQueueWaits</li>
 * <li>run 10 (backend L8687-L8687): queueSubmitCalls</li>
 * <li>run 11 (backend L8763-L8763): submitBuckets</li>
 * <li>run 12 (backend L8776-L8777): driverInfo waitOutcomes</li>
 * <li>run 13 (backend L8779-L8782): presentMode rebuilds quarantined fences</li>
 */
public final class SubmissionStatistics {

    /** backend field slotReuseWaits (declared L?). */
    long slotReuseWaits;
    /** backend field slotPolls (declared L?). */
    long slotPolls;
    /** backend field slotPollConfirms (declared L?). */
    long slotPollConfirms;
    /** backend field slotWaitConfirms (declared L?). */
    long slotWaitConfirms;
    /** backend field submitSequence (declared L?). */
    long submitSequence;
    /** backend field completedSubmitSequence (declared L?). */
    long completedSubmitSequence;
    /** backend field idleWaitTimeouts (declared L?). */
    long idleWaitTimeouts;
    /** backend field idleWaitRetries (declared L?). */
    long idleWaitRetries;
    /** backend field immediateSubmitUnder5 (declared L?). */
    long immediateSubmitUnder5;
    /** backend field presentModeRebuilds (declared L?). */
    long presentModeRebuilds;
    /** backend field deviceWaitToken (declared L1154). */
    long deviceWaitToken;
    /** backend field gpuStalled (declared L1404). */
    boolean gpuStalled;
    /** backend field stalledStops (declared L1589). */
    int stalledStops;
    /** backend field stalledTeardownSkips (declared L1590). */
    int stalledTeardownSkips;
    /** backend field idleWaits (declared L1593). */
    int idleWaits;
    /** backend field stalledGeometryDrops (declared L1595). */
    int stalledGeometryDrops;
    /** backend field stalledGeometryDroppedBatches (declared L1596). */
    int stalledGeometryDroppedBatches;
    /** backend field injectedIdleFailures (declared L1597). */
    int injectedIdleFailures;
    /** backend field frameArenaFallbackGroups (declared L1887). */
    long frameArenaFallbackGroups;
    /** backend field frameFenceWaitMillis (declared L1889). */
    long frameFenceWaitMillis;
    /** backend field frameFenceWaitWorstMillis (declared L1890). */
    long frameFenceWaitWorstMillis;
    /** backend field frameFenceWaitCount (declared L1891). */
    long frameFenceWaitCount;
    /** backend field retiredFlushes (declared L2217). */
    long retiredFlushes;
    /** backend field signalOnlySubmissions (declared L2383). */
    int signalOnlySubmissions;

    /** Run 0, reproducing backend L8320-L8336: bootstrapQueueWaits bootstrapQueueWaitMillis bootstrapQueueWaitWorstMillis bootstrapSubmitWaits bootstrapSubmitWaitMillis bootstrapSubmitWaitWorstMillis frameArenaFallbackGroups frameSlots frameSlotReuseWaits slotPolls slotPollConfirms slotWaitConfirms submitSequence completedSubmitSequence frameFenceWaitMillis frameFenceWaitCount frameFenceWaitWorstMillis. */
    public void appendRun0(StringBuilder out) {
        out.append(" bootstrapQueueWaits=");
        out.append(VulkanContext.bootstrapQueueWaits);
        out.append(" bootstrapQueueWaitMillis=");
        out.append(VulkanContext.bootstrapQueueWaitMillis);
        out.append(" bootstrapQueueWaitWorstMillis=");
        out.append(VulkanContext.bootstrapQueueWaitWorstMillis);
        out.append(" bootstrapSubmitWaits=");
        out.append(VulkanContext.bootstrapSubmitWaits);
        out.append(" bootstrapSubmitWaitMillis=");
        out.append(VulkanContext.bootstrapSubmitWaitMillis);
        out.append(" bootstrapSubmitWaitWorstMillis=");
        out.append(VulkanContext.bootstrapSubmitWaitWorstMillis);
        out.append(" frameArenaFallbackGroups=");
        out.append(this.frameArenaFallbackGroups);
        out.append(" frameSlots=");
        out.append(FRAME_SLOTS);
        out.append(" frameSlotReuseWaits=");
        out.append(this.slotReuseWaits);
        out.append(" slotPolls=");
        out.append(this.slotPolls);
        out.append(" slotPollConfirms=");
        out.append(this.slotPollConfirms);
        out.append(" slotWaitConfirms=");
        out.append(this.slotWaitConfirms);
        out.append(" submitSequence=");
        out.append(this.submitSequence);
        out.append(" completedSubmitSequence=");
        out.append(this.completedSubmitSequence);
        out.append(" frameFenceWaitMillis=");
        out.append(this.frameFenceWaitMillis);
        out.append(" frameFenceWaitCount=");
        out.append(this.frameFenceWaitCount);
        out.append(" frameFenceWaitWorstMillis=");
        out.append(this.frameFenceWaitWorstMillis);
    }

    /** Run 1, reproducing backend L8352-L8357: submitTimeouts gpuStalled deviceWaitToken stalledGeometryDrops stalledGeometryDroppedBatches stallOrigin. */
    public void appendRun1(StringBuilder out) {
        out.append(" submitTimeouts=");
        out.append(VulkanContext.submitTimeouts);
        out.append(" gpuStalled=");
        out.append(this.gpuStalled);
        out.append(" deviceWaitToken=");
        out.append(this.deviceWaitToken);
        out.append(" stalledGeometryDrops=");
        out.append(this.stalledGeometryDrops);
        out.append(" stalledGeometryDroppedBatches=");
        out.append(this.stalledGeometryDroppedBatches);
        out.append(" stallOrigin=");
        out.append((this.stallOrigin == null ? "none" : this.stallOrigin.replace(' ', '_')));
    }

    /** Run 2, reproducing backend L8362-L8362: signalOnlySubmissions. */
    public void appendRun2(StringBuilder out) {
        out.append(" signalOnlySubmissions=");
        out.append(this.signalOnlySubmissions);
    }

    /** Run 3, reproducing backend L8380-L8380: framePassSplit. */
    public void appendRun3(StringBuilder out) {
        out.append(" framePassSplit=[");
        out.append(FramePassTimings.summary());
    }

    /** Run 4, reproducing backend L8536-L8537: presentSucceeded presentSubmitDone. */
    public void appendRun4(StringBuilder out) {
        out.append(" presentSucceeded=");
        out.append((this.swapchain != null && this.swapchain.lastPresentSucceeded()));
        out.append(" presentSubmitDone=");
        out.append((this.swapchain != null && this.swapchain.lastPresentSubmitDone()));
    }

    /** Run 5, reproducing backend L8544-L8545: presentedMean presentMarkerExpected. */
    public void appendRun5(StringBuilder out) {
        out.append(" presentedMean=");
        out.append((this.swapchain != null ? this.swapchain.lastPresentedMean() : -1.0f));
        out.append(" presentMarkerExpected=170.0_magenta_1_0_1");
    }

    /** Run 6, reproducing backend L8614-L8616: swapchainOutOfDate acquireOutOfDate retiredFlushes retire. */
    public void appendRun6(StringBuilder out) {
        out.append(" swapchainOutOfDate=");
        out.append((this.swapchain != null ? (this.swapchain.outOfDate() ? 1 : 0) : -1));
        out.append(" acquireOutOfDate=");
        out.append((this.swapchain != null ? this.swapchain.acquireOutOfDate() : -1));
        out.append(" retiredFlushes=");
        out.append(this.retiredFlushes);
        out.append(" retire=");
        out.append(VulkanRetireQueue.describe());
    }

    /** Run 7, reproducing backend L8627-L8627: gpuStalled stalledStops. */
    public void appendRun7(StringBuilder out) {
        out.append(" gpuStalled=");
        out.append(this.gpuStalled);
        out.append(" stalledStops=");
        out.append(this.stalledStops);
    }

    /** Run 8, reproducing backend L8643-L8648: stalledTeardownSkips idleWaits injectedIdleFailures idleWaitTimeouts idleWaitRetries. */
    public void appendRun8(StringBuilder out) {
        out.append(" stalledTeardownSkips=");
        out.append(this.stalledTeardownSkips);
        out.append(" idleWaits=");
        out.append(this.idleWaits);
        out.append(" injectedIdleFailures=");
        out.append(this.injectedIdleFailures);
        out.append(" idleWaitTimeouts=");
        out.append(this.idleWaitTimeouts);
        out.append(" idleWaitRetries=");
        out.append(this.idleWaitRetries);
    }

    /** Run 9, reproducing backend L8656-L8656: bootstrapQueueWaits. */
    public void appendRun9(StringBuilder out) {
        out.append(" bootstrapQueueWaits=");
        out.append(VulkanContext.bootstrapQueueWaits);
    }

    /** Run 10, reproducing backend L8687-L8687: queueSubmitCalls. */
    public void appendRun10(StringBuilder out) {
        out.append(" queueSubmitCalls=");
        out.append(VulkanContext.queueSubmitCalls);
    }

    /** Run 11, reproducing backend L8763-L8763: submitBuckets. */
    public void appendRun11(StringBuilder out) {
        out.append(" submitBuckets=[<5us:");
        out.append(this.immediateSubmitUnder5);
    }

    /** Run 12, reproducing backend L8776-L8777: driverInfo waitOutcomes. */
    public void appendRun12(StringBuilder out) {
        out.append(" driverInfo=[");
        out.append(VulkanContext.driverSummary());
        out.append(" waitOutcomes=[");
        out.append(VulkanContext.waitOutcomeSummary());
    }

    /** Run 13, reproducing backend L8779-L8782: presentMode rebuilds quarantined fences. */
    public void appendRun13(StringBuilder out) {
        out.append(" presentMode=[");
        out.append(VulkanSwapchain.presentModeSummary());
        out.append(" rebuilds=");
        out.append(this.presentModeRebuilds);
        out.append(" quarantined=[buffers=");
        out.append(VulkanContext.quarantinedResourceCounts()[0]);
        out.append(" fences=");
        out.append(VulkanContext.quarantinedResourceCounts()[1]);
    }

}
