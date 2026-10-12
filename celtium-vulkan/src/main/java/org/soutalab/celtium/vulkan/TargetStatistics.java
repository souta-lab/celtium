package org.soutalab.celtium.vulkan;

/**
 * TargetStatistics - the target domain's counters and its fragments of the verification line.
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
 * <li>run 0 (backend L8340-L8342): offerStages splashSourcedGroups splashCanvasInvalidations</li>
 * <li>run 1 (backend L8344-L8349): unflushedTargetGroupsDropped unflushedTargetDropTargets drainedOtherTargetPasses flushEntries unflushedTargetPassFailures lastUnflushedTargetFailure</li>
 * <li>run 2 (backend L8358-L8358): windowFlushFailures</li>
 * <li>run 3 (backend L8363-L8364): windowFlushFailureFrame windowFlushDroppedBatches</li>
 * <li>run 4 (backend L8415-L8416): windowFlushRetainedBatches lastWindowFlushFailure</li>
 * <li>run 5 (backend L8493-L8493): offTargetByTarget offTargetTargets</li>
 * <li>run 6 (backend L8497-L8506): offTargetPassesSkippedEmpty offTargetPerTarget offTargetPairs offTargetPairClears gameClearAttribution offTargetPairFramebuffers otherTargetGroups</li>
 * <li>run 7 (backend L8519-L8523): offTargetRefusals offTargetOffered offTargetAccepted offTargetDrawn offTargetCopiedBack outsideWorldPassAccepted outsideWorldPassRefusals compositePresentRefusals</li>
 * <li>run 8 (backend L8538-L8543): depthFormat depthFormatDecision gameClearLatch</li>
 * <li>run 9 (backend L8604-L8607): pairClearsRefusedOtherIdentity windowDrawsToFrameImage windowFramesMarkedPresentable lastPairClearRefusal</li>
 * <li>run 10 (backend L8622-L8623): outsideWorldPassRefusals offTargetRefusals</li>
 */
public final class TargetStatistics {

    /** backend field offTargetFramebufferCreations (declared L119). */
    int offTargetFramebufferCreations;
    /** backend field pairClearsRefusedOtherIdentity (declared L212). */
    int pairClearsRefusedOtherIdentity;
    /** backend field windowDrawsToFrameImage (declared L214). */
    int windowDrawsToFrameImage;
    /** backend field windowFramesMarkedPresentable (declared L217). */
    int windowFramesMarkedPresentable;
    /** backend field lastPairClearRefusal (declared L219). */
    String lastPairClearRefusal;
    /** backend field defaultPairClears (declared L221). */
    int defaultPairClears;
    /** backend field gameClearsLatchedForFrame (declared L230). */
    int gameClearsLatchedForFrame;
    /** backend field outsideWorldPassRefusals (declared L1022). */
    int outsideWorldPassRefusals;
    /** backend field outsideWorldPassAccepted (declared L1023). */
    int outsideWorldPassAccepted;
    /** backend field offTargetTargetsCreated (declared L1031). */
    int offTargetTargetsCreated;
    /** backend field offTargetDrawn (declared L1033). */
    int offTargetDrawn;
    /** backend field offTargetOffered (declared L1035). */
    int offTargetOffered;
    /** backend field offTargetAccepted (declared L1037). */
    int offTargetAccepted;
    /** backend field offTargetCopiedBack (declared L1286). */
    int offTargetCopiedBack;
    /** backend field offTargetRefusals (declared L1291). */
    int offTargetRefusals;
    /** backend field drainedOtherTargetPasses (declared L1348). */
    int drainedOtherTargetPasses;
    /** backend field unflushedTargetGroupsDropped (declared L1379). */
    int unflushedTargetGroupsDropped;
    /** backend field unflushedTargetPassFailures (declared L1381). */
    int unflushedTargetPassFailures;
    /** backend field windowFlushFailures (declared L1409). */
    int windowFlushFailures;
    /** backend field windowFlushFailureFrame (declared L1410). */
    long windowFlushFailureFrame;
    /** backend field windowFlushDroppedBatches (declared L1411). */
    long windowFlushDroppedBatches;
    /** backend field windowFlushRetainedBatches (declared L1527). */
    long windowFlushRetainedBatches;
    /** backend field compositePresentRefusals (declared L2314). */
    int compositePresentRefusals;
    /** backend field splashCanvasInvalidations (declared L2480). */
    int splashCanvasInvalidations;
    /** backend field splashSourcedGroups (declared L2494). */
    long splashSourcedGroups;
    /** backend field offTargetPassesSkippedEmpty (declared L2535). */
    int offTargetPassesSkippedEmpty;
    /** backend field otherTargetGroupsAtMainFlush (declared L2547). */
    int otherTargetGroupsAtMainFlush;

    /** Run 0, reproducing backend L8340-L8342: offerStages splashSourcedGroups splashCanvasInvalidations. */
    public void appendRun0(StringBuilder out) {
        out.append(" offerStages=[");
        out.append(offerStageSummary());
        out.append(" splashSourcedGroups=");
        out.append(this.splashSourcedGroups);
        out.append(" splashCanvasInvalidations=");
        out.append(this.splashCanvasInvalidations);
    }

    /** Run 1, reproducing backend L8344-L8349: unflushedTargetGroupsDropped unflushedTargetDropTargets drainedOtherTargetPasses flushEntries unflushedTargetPassFailures lastUnflushedTargetFailure. */
    public void appendRun1(StringBuilder out) {
        out.append(" unflushedTargetGroupsDropped=");
        out.append(this.unflushedTargetGroupsDropped);
        out.append(" unflushedTargetDropTargets=[");
        out.append(unflushedTargetDropSummary());
        out.append(" drainedOtherTargetPasses=");
        out.append(this.drainedOtherTargetPasses);
        out.append(" flushEntries=[");
        out.append(flushEntrySummary());
        out.append(" unflushedTargetPassFailures=");
        out.append(this.unflushedTargetPassFailures);
        out.append(" lastUnflushedTargetFailure=");
        out.append((this.lastUnflushedTargetFailure == null ? "none" : this.lastUnflushedTargetFailure.replace(' ', '_').substring(0, Math.min(120, this.lastUnflushedTargetFailure.length()))));
    }

    /** Run 2, reproducing backend L8358-L8358: windowFlushFailures. */
    public void appendRun2(StringBuilder out) {
        out.append(" windowFlushFailures=");
        out.append(this.windowFlushFailures);
    }

    /** Run 3, reproducing backend L8363-L8364: windowFlushFailureFrame windowFlushDroppedBatches. */
    public void appendRun3(StringBuilder out) {
        out.append(" windowFlushFailureFrame=");
        out.append(this.windowFlushFailureFrame);
        out.append(" windowFlushDroppedBatches=");
        out.append(this.windowFlushDroppedBatches);
    }

    /** Run 4, reproducing backend L8415-L8416: windowFlushRetainedBatches lastWindowFlushFailure. */
    public void appendRun4(StringBuilder out) {
        out.append(" windowFlushRetainedBatches=");
        out.append(this.windowFlushRetainedBatches);
        out.append(" lastWindowFlushFailure=");
        out.append((this.lastWindowFlushFailure == null ? "none" : this.lastWindowFlushFailure.replace(' ', '_').substring(0, Math.min(200, this.lastWindowFlushFailure.length()))));
    }

    /** Run 5, reproducing backend L8493-L8493: offTargetByTarget offTargetTargets. */
    public void appendRun5(StringBuilder out) {
        out.append(" offTargetByTarget=[");
        out.append(this.offTargetSummary());
        out.append("] offTargetTargets=");
        out.append(this.offTargetTargetsCreated);
    }

    /** Run 6, reproducing backend L8497-L8506: offTargetPassesSkippedEmpty offTargetPerTarget offTargetPairs offTargetPairClears gameClearAttribution offTargetPairFramebuffers otherTargetGroups. */
    public void appendRun6(StringBuilder out) {
        out.append(" offTargetPassesSkippedEmpty=");
        out.append(this.offTargetPassesSkippedEmpty);
        out.append(" offTargetPerTarget=[");
        out.append(offTargetPerTarget());
        out.append(" offTargetPairs=[");
        out.append(frameTargetPairSummary());
        out.append(" offTargetPairClears=[default-pair=");
        out.append(this.defaultPairClears);
        out.append(" gameClearAttribution=[latchedForFrame=");
        out.append(this.gameClearsLatchedForFrame);
        out.append(" offTargetPairFramebuffers=[default-pair=");
        out.append(this.offTargetFramebufferCreations);
        out.append(" otherTargetGroups=[atMainFlush=");
        out.append(this.otherTargetGroupsAtMainFlush);
    }

    /** Run 7, reproducing backend L8519-L8523: offTargetRefusals offTargetOffered offTargetAccepted offTargetDrawn offTargetCopiedBack outsideWorldPassAccepted outsideWorldPassRefusals compositePresentRefusals. */
    public void appendRun7(StringBuilder out) {
        out.append(" offTargetRefusals=");
        out.append((this.offTargetRefusalsByReason.isEmpty() ? "none" : this.offTargetRefusalsByReason.toString()));
        out.append(" offTargetOffered=");
        out.append(this.offTargetOffered);
        out.append(" offTargetAccepted=");
        out.append(this.offTargetAccepted);
        out.append(" offTargetDrawn=");
        out.append(this.offTargetDrawn);
        out.append(" offTargetCopiedBack=");
        out.append(this.offTargetCopiedBack);
        out.append(" outsideWorldPassAccepted=");
        out.append(this.outsideWorldPassAccepted);
        out.append(" outsideWorldPassRefusals=");
        out.append(this.outsideWorldPassRefusals);
        out.append(" compositePresentRefusals=");
        out.append(this.compositePresentRefusals);
    }

    /** Run 8, reproducing backend L8538-L8543: depthFormat depthFormatDecision gameClearLatch. */
    public void appendRun8(StringBuilder out) {
        out.append(" depthFormat=");
        out.append(org.soutalab.celtium.vulkan.VulkanDepthFormat.chosenName());
        out.append(" depthFormatDecision=[");
        out.append(org.soutalab.celtium.vulkan.VulkanDepthFormat.decisionSummary());
        out.append(" gameClearLatch=");
        out.append((celtium$gameClearLatchEnabled() ? "on" : "off"));
    }

    /** Run 9, reproducing backend L8604-L8607: pairClearsRefusedOtherIdentity windowDrawsToFrameImage windowFramesMarkedPresentable lastPairClearRefusal. */
    public void appendRun9(StringBuilder out) {
        out.append(" pairClearsRefusedOtherIdentity=");
        out.append(this.pairClearsRefusedOtherIdentity);
        out.append(" windowDrawsToFrameImage=");
        out.append(this.windowDrawsToFrameImage);
        out.append(" windowFramesMarkedPresentable=");
        out.append(this.windowFramesMarkedPresentable);
        out.append(" lastPairClearRefusal=[");
        out.append(this.lastPairClearRefusal);
    }

    /** Run 10, reproducing backend L8622-L8623: outsideWorldPassRefusals offTargetRefusals. */
    public void appendRun10(StringBuilder out) {
        out.append(" outsideWorldPassRefusals=");
        out.append(this.outsideWorldPassRefusals);
        out.append(" offTargetRefusals=");
        out.append(this.offTargetRefusals);
    }

}
