package org.soutalab.celtium.vulkan;

/**
 * The submission domain's counters (refactoring plan R2, stage 6).
 * <p>
 * These are counters whose owner did not exist, so they moved here: the stall and idle path, the fence wait
 * census, the signal-only submissions and the present-mode rebuilds. Every writer calls a named method and the
 * line reads the getters, so the field names and their order are unchanged.
 * <p>
 * Counters whose owner already exists are read from that owner and are not duplicated: the frame slots, their
 * submit sequences, the reuse waits and the slot polls belong to {@link FrameSubmissionScheduler}, the bootstrap
 * and queue-submit counters to {@link VulkanContext}, and the swapchain's out-of-date counters to the swapchain.
 * {@code gpuStalled} is not here either - it is a flag the draw paths read, not a count.
 */
public final class SubmissionStatistics {

    private int stalledStops;
    private int stalledTeardownSkips;
    private int idleWaits;
    private int stalledGeometryDrops;
    private int stalledGeometryDroppedBatches;
    private int injectedIdleFailures;
    private long frameArenaFallbackGroups;
    private long frameFenceWaitMillis;
    private long frameFenceWaitWorstMillis;
    private long frameFenceWaitCount;
    private long retiredFlushes;
    private int signalOnlySubmissions;
    private int presentModeRebuilds;
    private int idleWaitTimeouts;
    private int idleWaitRetries;
    private long deviceWaitToken;
    private String stallOrigin = "none";

    /** Records one stalledStops. */
    void noteStalledStops() {
        this.stalledStops++;
    }

    /** Sets stalledStops. */
    void setStalledStops(int value) {
        this.stalledStops = value;
    }

    /** Adds to stalledStops. */
    void addStalledStops(int value) {
        this.stalledStops += value;
    }

    /** {@return stalledStops}. */
    int stalledStops() {
        return this.stalledStops;
    }

    /** Records one stalledTeardownSkips. */
    void noteStalledTeardownSkips() {
        this.stalledTeardownSkips++;
    }

    /** Sets stalledTeardownSkips. */
    void setStalledTeardownSkips(int value) {
        this.stalledTeardownSkips = value;
    }

    /** Adds to stalledTeardownSkips. */
    void addStalledTeardownSkips(int value) {
        this.stalledTeardownSkips += value;
    }

    /** {@return stalledTeardownSkips}. */
    int stalledTeardownSkips() {
        return this.stalledTeardownSkips;
    }

    /** Records one idleWaits. */
    void noteIdleWait() {
        this.idleWaits++;
    }

    /** Sets idleWaits. */
    void setIdleWaits(int value) {
        this.idleWaits = value;
    }

    /** Adds to idleWaits. */
    void addIdleWaits(int value) {
        this.idleWaits += value;
    }

    /** {@return idleWaits}. */
    int idleWaits() {
        return this.idleWaits;
    }

    /** Records one stalledGeometryDrops. */
    void noteStalledGeometryDrops() {
        this.stalledGeometryDrops++;
    }

    /** Sets stalledGeometryDrops. */
    void setStalledGeometryDrops(int value) {
        this.stalledGeometryDrops = value;
    }

    /** Adds to stalledGeometryDrops. */
    void addStalledGeometryDrops(int value) {
        this.stalledGeometryDrops += value;
    }

    /** {@return stalledGeometryDrops}. */
    int stalledGeometryDrops() {
        return this.stalledGeometryDrops;
    }

    /** Records one stalledGeometryDroppedBatches. */
    void noteStalledGeometryDroppedBatches() {
        this.stalledGeometryDroppedBatches++;
    }

    /** Sets stalledGeometryDroppedBatches. */
    void setStalledGeometryDroppedBatches(int value) {
        this.stalledGeometryDroppedBatches = value;
    }

    /** Adds to stalledGeometryDroppedBatches. */
    void addStalledGeometryDroppedBatches(int value) {
        this.stalledGeometryDroppedBatches += value;
    }

    /** {@return stalledGeometryDroppedBatches}. */
    int stalledGeometryDroppedBatches() {
        return this.stalledGeometryDroppedBatches;
    }

    /** Records one injectedIdleFailures. */
    void noteInjectedIdleFailures() {
        this.injectedIdleFailures++;
    }

    /** Sets injectedIdleFailures. */
    void setInjectedIdleFailures(int value) {
        this.injectedIdleFailures = value;
    }

    /** Adds to injectedIdleFailures. */
    void addInjectedIdleFailures(int value) {
        this.injectedIdleFailures += value;
    }

    /** {@return injectedIdleFailures}. */
    int injectedIdleFailures() {
        return this.injectedIdleFailures;
    }

    /** Records one frameArenaFallbackGroups. */
    void noteFrameArenaFallbackGroups() {
        this.frameArenaFallbackGroups++;
    }

    /** Sets frameArenaFallbackGroups. */
    void setFrameArenaFallbackGroups(long value) {
        this.frameArenaFallbackGroups = value;
    }

    /** Adds to frameArenaFallbackGroups. */
    void addFrameArenaFallbackGroups(long value) {
        this.frameArenaFallbackGroups += value;
    }

    /** {@return frameArenaFallbackGroups}. */
    long frameArenaFallbackGroups() {
        return this.frameArenaFallbackGroups;
    }

    /** Records one frameFenceWaitMillis. */
    void noteFrameFenceWaitMillis() {
        this.frameFenceWaitMillis++;
    }

    /** Sets frameFenceWaitMillis. */
    void setFrameFenceWaitMillis(long value) {
        this.frameFenceWaitMillis = value;
    }

    /** Adds to frameFenceWaitMillis. */
    void addFrameFenceWaitMillis(long value) {
        this.frameFenceWaitMillis += value;
    }

    /** {@return frameFenceWaitMillis}. */
    long frameFenceWaitMillis() {
        return this.frameFenceWaitMillis;
    }

    /** Records one frameFenceWaitWorstMillis. */
    void noteFrameFenceWaitWorstMillis() {
        this.frameFenceWaitWorstMillis++;
    }

    /** Sets frameFenceWaitWorstMillis. */
    void setFrameFenceWaitWorstMillis(long value) {
        this.frameFenceWaitWorstMillis = value;
    }

    /** Adds to frameFenceWaitWorstMillis. */
    void addFrameFenceWaitWorstMillis(long value) {
        this.frameFenceWaitWorstMillis += value;
    }

    /** {@return frameFenceWaitWorstMillis}. */
    long frameFenceWaitWorstMillis() {
        return this.frameFenceWaitWorstMillis;
    }

    /** Records one frameFenceWaitCount. */
    void noteFrameFenceWaitCount() {
        this.frameFenceWaitCount++;
    }

    /** Sets frameFenceWaitCount. */
    void setFrameFenceWaitCount(long value) {
        this.frameFenceWaitCount = value;
    }

    /** Adds to frameFenceWaitCount. */
    void addFrameFenceWaitCount(long value) {
        this.frameFenceWaitCount += value;
    }

    /** {@return frameFenceWaitCount}. */
    long frameFenceWaitCount() {
        return this.frameFenceWaitCount;
    }

    /** Records one retiredFlushes. */
    void noteRetiredFlushes() {
        this.retiredFlushes++;
    }

    /** Sets retiredFlushes. */
    void setRetiredFlushes(long value) {
        this.retiredFlushes = value;
    }

    /** Adds to retiredFlushes. */
    void addRetiredFlushes(long value) {
        this.retiredFlushes += value;
    }

    /** {@return retiredFlushes}. */
    long retiredFlushes() {
        return this.retiredFlushes;
    }

    /** Records one signalOnlySubmissions. */
    void noteSignalOnlySubmissions() {
        this.signalOnlySubmissions++;
    }

    /** Sets signalOnlySubmissions. */
    void setSignalOnlySubmissions(int value) {
        this.signalOnlySubmissions = value;
    }

    /** Adds to signalOnlySubmissions. */
    void addSignalOnlySubmissions(int value) {
        this.signalOnlySubmissions += value;
    }

    /** {@return signalOnlySubmissions}. */
    int signalOnlySubmissions() {
        return this.signalOnlySubmissions;
    }

    /** Records one presentModeRebuilds. */
    void notePresentModeRebuilds() {
        this.presentModeRebuilds++;
    }

    /** Sets presentModeRebuilds. */
    void setPresentModeRebuilds(int value) {
        this.presentModeRebuilds = value;
    }

    /** Adds to presentModeRebuilds. */
    void addPresentModeRebuilds(int value) {
        this.presentModeRebuilds += value;
    }

    /** {@return presentModeRebuilds}. */
    int presentModeRebuilds() {
        return this.presentModeRebuilds;
    }

    /** Records one idleWaitTimeouts. */
    void noteIdleWaitTimeouts() {
        this.idleWaitTimeouts++;
    }

    /** Sets idleWaitTimeouts. */
    void setIdleWaitTimeouts(int value) {
        this.idleWaitTimeouts = value;
    }

    /** Adds to idleWaitTimeouts. */
    void addIdleWaitTimeouts(int value) {
        this.idleWaitTimeouts += value;
    }

    /** {@return idleWaitTimeouts}. */
    int idleWaitTimeouts() {
        return this.idleWaitTimeouts;
    }

    /** Records one idleWaitRetries. */
    void noteIdleWaitRetries() {
        this.idleWaitRetries++;
    }

    /** Sets idleWaitRetries. */
    void setIdleWaitRetries(int value) {
        this.idleWaitRetries = value;
    }

    /** Adds to idleWaitRetries. */
    void addIdleWaitRetries(int value) {
        this.idleWaitRetries += value;
    }

    /** {@return idleWaitRetries}. */
    int idleWaitRetries() {
        return this.idleWaitRetries;
    }

    /** Records one deviceWaitToken. */
    void noteDeviceWaitToken() {
        this.deviceWaitToken++;
    }

    /** Sets deviceWaitToken. */
    void setDeviceWaitToken(long value) {
        this.deviceWaitToken = value;
    }

    /** Adds to deviceWaitToken. */
    void addDeviceWaitToken(long value) {
        this.deviceWaitToken += value;
    }

    /** {@return deviceWaitToken}. */
    long deviceWaitToken() {
        return this.deviceWaitToken;
    }

    /** Records stallOrigin. */
    void setStallOrigin(String value) {
        this.stallOrigin = value;
    }

    /** {@return stallOrigin}. */
    String stallOrigin() {
        return this.stallOrigin;
    }

}
