package org.soutalab.celtium.vulkan;

/**
 * The target domain's counters (refactoring plan R2, stage 5).
 * <p>
 * These are counters whose owner did not exist, so they moved here: the off-target pair census, the window and
 * boundary flush outcomes, the splash path and the game-clear attribution. Every writer calls a named method and
 * the line reads the getters, so the field names and their order are unchanged.
 * <p>
 * Counters whose owner already exists are read from that owner and are not duplicated: the pair framebuffers, the
 * clear decisions and the identity census belong to {@link RenderTargetRegistry}, and the publication counters to
 * {@link PublishedFrameTextures}.
 */
public final class TargetStatistics {

    private int offTargetTargetsCreated;
    private int offTargetDrawn;
    private int offTargetOffered;
    private int offTargetAccepted;
    private int offTargetCopiedBack;
    private int offTargetRefusals;
    private int drainedOtherTargetPasses;
    private int unflushedTargetGroupsDropped;
    private int unflushedTargetPassFailures;
    private int windowFlushFailures;
    private long windowFlushFailureFrame;
    private int windowFlushDroppedBatches;
    private int windowFlushRetainedBatches;
    private int compositePresentRefusals;
    private int splashCanvasInvalidations;
    private int splashSourcedGroups;
    private int offTargetPassesSkippedEmpty;
    private int otherTargetGroupsAtMainFlush;
    private int pairClearsRefusedOtherIdentity;
    private int windowDrawsToFrameImage;
    private int windowFramesMarkedPresentable;
    private String lastPairClearRefusal = "none";
    private int outsideWorldPassRefusals;
    private int outsideWorldPassAccepted;
    private int gameClearsLatchedForFrame;

    /** Records one offTargetTargetsCreated. */
    void noteOffTargetTargetsCreated() {
        this.offTargetTargetsCreated++;
    }

    /** Sets offTargetTargetsCreated. */
    void setOffTargetTargetsCreated(int value) {
        this.offTargetTargetsCreated = value;
    }

    /** {@return offTargetTargetsCreated}. */
    int offTargetTargetsCreated() {
        return this.offTargetTargetsCreated;
    }

    /** Records one offTargetDrawn. */
    void noteOffTargetDrawn() {
        this.offTargetDrawn++;
    }

    /** Sets offTargetDrawn. */
    void setOffTargetDrawn(int value) {
        this.offTargetDrawn = value;
    }

    /** {@return offTargetDrawn}. */
    int offTargetDrawn() {
        return this.offTargetDrawn;
    }

    /** Records one offTargetOffered. */
    void noteOffTargetOffered() {
        this.offTargetOffered++;
    }

    /** Sets offTargetOffered. */
    void setOffTargetOffered(int value) {
        this.offTargetOffered = value;
    }

    /** {@return offTargetOffered}. */
    int offTargetOffered() {
        return this.offTargetOffered;
    }

    /** Records one offTargetAccepted. */
    void noteOffTargetAccepted() {
        this.offTargetAccepted++;
    }

    /** Sets offTargetAccepted. */
    void setOffTargetAccepted(int value) {
        this.offTargetAccepted = value;
    }

    /** {@return offTargetAccepted}. */
    int offTargetAccepted() {
        return this.offTargetAccepted;
    }

    /** Records one offTargetCopiedBack. */
    void noteOffTargetCopiedBack() {
        this.offTargetCopiedBack++;
    }

    /** Sets offTargetCopiedBack. */
    void setOffTargetCopiedBack(int value) {
        this.offTargetCopiedBack = value;
    }

    /** {@return offTargetCopiedBack}. */
    int offTargetCopiedBack() {
        return this.offTargetCopiedBack;
    }

    /** Records one offTargetRefusals. */
    void noteOffTargetRefusals() {
        this.offTargetRefusals++;
    }

    /** Sets offTargetRefusals. */
    void setOffTargetRefusals(int value) {
        this.offTargetRefusals = value;
    }

    /** {@return offTargetRefusals}. */
    int offTargetRefusals() {
        return this.offTargetRefusals;
    }

    /** Records one drainedOtherTargetPasses. */
    void noteDrainedOtherTargetPasses() {
        this.drainedOtherTargetPasses++;
    }

    /** Sets drainedOtherTargetPasses. */
    void setDrainedOtherTargetPasses(int value) {
        this.drainedOtherTargetPasses = value;
    }

    /** {@return drainedOtherTargetPasses}. */
    int drainedOtherTargetPasses() {
        return this.drainedOtherTargetPasses;
    }

    /** Records one unflushedTargetGroupsDropped. */
    void noteUnflushedTargetGroupsDropped() {
        this.unflushedTargetGroupsDropped++;
    }

    /** Sets unflushedTargetGroupsDropped. */
    void setUnflushedTargetGroupsDropped(int value) {
        this.unflushedTargetGroupsDropped = value;
    }

    /** {@return unflushedTargetGroupsDropped}. */
    int unflushedTargetGroupsDropped() {
        return this.unflushedTargetGroupsDropped;
    }

    /** Records one unflushedTargetPassFailures. */
    void noteUnflushedTargetPassFailures() {
        this.unflushedTargetPassFailures++;
    }

    /** Sets unflushedTargetPassFailures. */
    void setUnflushedTargetPassFailures(int value) {
        this.unflushedTargetPassFailures = value;
    }

    /** {@return unflushedTargetPassFailures}. */
    int unflushedTargetPassFailures() {
        return this.unflushedTargetPassFailures;
    }

    /** Records one windowFlushFailures. */
    void noteWindowFlushFailures() {
        this.windowFlushFailures++;
    }

    /** Sets windowFlushFailures. */
    void setWindowFlushFailures(int value) {
        this.windowFlushFailures = value;
    }

    /** {@return windowFlushFailures}. */
    int windowFlushFailures() {
        return this.windowFlushFailures;
    }

    /** Records one windowFlushFailureFrame. */
    void noteWindowFlushFailureFrame() {
        this.windowFlushFailureFrame++;
    }

    /** Sets windowFlushFailureFrame. */
    void setWindowFlushFailureFrame(long value) {
        this.windowFlushFailureFrame = value;
    }

    /** {@return windowFlushFailureFrame}. */
    long windowFlushFailureFrame() {
        return this.windowFlushFailureFrame;
    }

    /** Records one windowFlushDroppedBatches. */
    void noteWindowFlushDroppedBatches() {
        this.windowFlushDroppedBatches++;
    }

    /** Sets windowFlushDroppedBatches. */
    void setWindowFlushDroppedBatches(int value) {
        this.windowFlushDroppedBatches = value;
    }

    /** {@return windowFlushDroppedBatches}. */
    int windowFlushDroppedBatches() {
        return this.windowFlushDroppedBatches;
    }

    /** Records one windowFlushRetainedBatches. */
    void noteWindowFlushRetainedBatches() {
        this.windowFlushRetainedBatches++;
    }

    /** Sets windowFlushRetainedBatches. */
    void setWindowFlushRetainedBatches(int value) {
        this.windowFlushRetainedBatches = value;
    }

    /** {@return windowFlushRetainedBatches}. */
    int windowFlushRetainedBatches() {
        return this.windowFlushRetainedBatches;
    }

    /** Records one compositePresentRefusals. */
    void noteCompositePresentRefusals() {
        this.compositePresentRefusals++;
    }

    /** Sets compositePresentRefusals. */
    void setCompositePresentRefusals(int value) {
        this.compositePresentRefusals = value;
    }

    /** {@return compositePresentRefusals}. */
    int compositePresentRefusals() {
        return this.compositePresentRefusals;
    }

    /** Records one splashCanvasInvalidations. */
    void noteSplashCanvasInvalidations() {
        this.splashCanvasInvalidations++;
    }

    /** Sets splashCanvasInvalidations. */
    void setSplashCanvasInvalidations(int value) {
        this.splashCanvasInvalidations = value;
    }

    /** {@return splashCanvasInvalidations}. */
    int splashCanvasInvalidations() {
        return this.splashCanvasInvalidations;
    }

    /** Records one splashSourcedGroups. */
    void noteSplashSourcedGroups() {
        this.splashSourcedGroups++;
    }

    /** Sets splashSourcedGroups. */
    void setSplashSourcedGroups(int value) {
        this.splashSourcedGroups = value;
    }

    /** {@return splashSourcedGroups}. */
    int splashSourcedGroups() {
        return this.splashSourcedGroups;
    }

    /** Records one offTargetPassesSkippedEmpty. */
    void noteOffTargetPassesSkippedEmpty() {
        this.offTargetPassesSkippedEmpty++;
    }

    /** Sets offTargetPassesSkippedEmpty. */
    void setOffTargetPassesSkippedEmpty(int value) {
        this.offTargetPassesSkippedEmpty = value;
    }

    /** {@return offTargetPassesSkippedEmpty}. */
    int offTargetPassesSkippedEmpty() {
        return this.offTargetPassesSkippedEmpty;
    }

    /** Records one otherTargetGroupsAtMainFlush. */
    void noteOtherTargetGroupsAtMainFlush() {
        this.otherTargetGroupsAtMainFlush++;
    }

    /** Sets otherTargetGroupsAtMainFlush. */
    void setOtherTargetGroupsAtMainFlush(int value) {
        this.otherTargetGroupsAtMainFlush = value;
    }

    /** {@return otherTargetGroupsAtMainFlush}. */
    int otherTargetGroupsAtMainFlush() {
        return this.otherTargetGroupsAtMainFlush;
    }

    /** Records one pairClearsRefusedOtherIdentity. */
    void notePairClearsRefusedOtherIdentity() {
        this.pairClearsRefusedOtherIdentity++;
    }

    /** Sets pairClearsRefusedOtherIdentity. */
    void setPairClearsRefusedOtherIdentity(int value) {
        this.pairClearsRefusedOtherIdentity = value;
    }

    /** {@return pairClearsRefusedOtherIdentity}. */
    int pairClearsRefusedOtherIdentity() {
        return this.pairClearsRefusedOtherIdentity;
    }

    /** Records one windowDrawsToFrameImage. */
    void noteWindowDrawsToFrameImage() {
        this.windowDrawsToFrameImage++;
    }

    /** Sets windowDrawsToFrameImage. */
    void setWindowDrawsToFrameImage(int value) {
        this.windowDrawsToFrameImage = value;
    }

    /** {@return windowDrawsToFrameImage}. */
    int windowDrawsToFrameImage() {
        return this.windowDrawsToFrameImage;
    }

    /** Records one windowFramesMarkedPresentable. */
    void noteWindowFramesMarkedPresentable() {
        this.windowFramesMarkedPresentable++;
    }

    /** Sets windowFramesMarkedPresentable. */
    void setWindowFramesMarkedPresentable(int value) {
        this.windowFramesMarkedPresentable = value;
    }

    /** {@return windowFramesMarkedPresentable}. */
    int windowFramesMarkedPresentable() {
        return this.windowFramesMarkedPresentable;
    }

    /** Records lastPairClearRefusal. */
    void setLastPairClearRefusal(String value) {
        this.lastPairClearRefusal = value;
    }

    /** {@return lastPairClearRefusal}. */
    String lastPairClearRefusal() {
        return this.lastPairClearRefusal;
    }

    /** Records one outsideWorldPassRefusals. */
    void noteOutsideWorldPassRefusals() {
        this.outsideWorldPassRefusals++;
    }

    /** Sets outsideWorldPassRefusals. */
    void setOutsideWorldPassRefusals(int value) {
        this.outsideWorldPassRefusals = value;
    }

    /** {@return outsideWorldPassRefusals}. */
    int outsideWorldPassRefusals() {
        return this.outsideWorldPassRefusals;
    }

    /** Records one outsideWorldPassAccepted. */
    void noteOutsideWorldPassAccepted() {
        this.outsideWorldPassAccepted++;
    }

    /** Sets outsideWorldPassAccepted. */
    void setOutsideWorldPassAccepted(int value) {
        this.outsideWorldPassAccepted = value;
    }

    /** {@return outsideWorldPassAccepted}. */
    int outsideWorldPassAccepted() {
        return this.outsideWorldPassAccepted;
    }

    /** Records one gameClearsLatchedForFrame. */
    void noteGameClearsLatchedForFrame() {
        this.gameClearsLatchedForFrame++;
    }

    /** Sets gameClearsLatchedForFrame. */
    void setGameClearsLatchedForFrame(int value) {
        this.gameClearsLatchedForFrame = value;
    }

    /** {@return gameClearsLatchedForFrame}. */
    int gameClearsLatchedForFrame() {
        return this.gameClearsLatchedForFrame;
    }

    /** Adds to unflushedTargetGroupsDropped, which the flush accumulates per group. */
    void addUnflushedTargetGroupsDropped(int value) {
        this.unflushedTargetGroupsDropped += value;
    }

    /** Adds to windowFlushRetainedBatches, which the flush accumulates per group. */
    void addWindowFlushRetainedBatches(int value) {
        this.windowFlushRetainedBatches += value;
    }

}
