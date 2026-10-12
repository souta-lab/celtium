package org.soutalab.celtium.vulkan;

/**
 * The terrain pass's counters and its skip reasons (refactoring plan R2, stage 4).
 * <p>
 * These are counters whose owner did not exist, so they moved here: the pass entry points, the
 * last-draw census the acceptance reads, and the boundary flush that closes a world pass. Every
 * writer calls a named method and the verification line reads the getters, so the field names and
 * their order in the line are unchanged while the counter's owner is this class.
 * <p>
 * Counters whose owner already exists are read from that owner and are not duplicated: the region
 * arenas and the failed-upload ledger belong to {@link TerrainRenderer}, the arena upload counters
 * to the backend's upload path, and the indirect fallback reason to the draw path that records it.
 */
public final class TerrainStatistics {

    private int passSubmissions;
    private int passSkips;
    private int passErrors;
    private String lastSkipReason;
    private long lastDrawCommands;
    private long lastSectionsDrawn;
    private int terrainPasses;
    private int immediateOnlyPasses;
    private int worldPassBoundaryFlushes;
    private long lastTerrainPassFrame;

    /** Records a submitted pass. */
    void notePassSubmissions() {
        this.passSubmissions++;
    }

    /** {@return how many passes were submitted}. */
    int passSubmissions() {
        return this.passSubmissions;
    }

    /** Records a skipped pass. */
    void notePassSkips() {
        this.passSkips++;
    }

    /** {@return how many passes were skipped}. */
    int passSkips() {
        return this.passSkips;
    }

    /** Records a pass that failed. */
    void notePassErrors() {
        this.passErrors++;
    }

    /** {@return how many passes failed}. */
    int passErrors() {
        return this.passErrors;
    }

    /** Records why the last pass was skipped. */
    void setLastSkipReason(String reason) {
        this.lastSkipReason = reason;
    }

    /** {@return why the last pass was skipped, or null}. */
    String lastSkipReason() {
        return this.lastSkipReason;
    }

    /** Records one draw in the last pass. */
    void noteLastDrawCommands() {
        this.lastDrawCommands++;
    }

    /** Sets the draw count the pass resets before it counts its draws. */
    void setLastDrawCommands(long value) {
        this.lastDrawCommands = value;
    }

    /** Adds to the draw count the pass accumulates per render list. */
    void addLastDrawCommands(long value) {
        this.lastDrawCommands += value;
    }

    /** {@return the last pass's draw count}. */
    long lastDrawCommands() {
        return this.lastDrawCommands;
    }

    /** Sets the section count the pass resets before it counts its draws. */
    void setLastSectionsDrawn(long value) {
        this.lastSectionsDrawn = value;
    }

    /** Adds to the section count the pass accumulates per render list. */
    void addLastSectionsDrawn(long value) {
        this.lastSectionsDrawn += value;
    }

    /** {@return the last pass's section count}. */
    long lastSectionsDrawn() {
        return this.lastSectionsDrawn;
    }

    /** Records a terrain pass. */
    void noteTerrainPasses() {
        this.terrainPasses++;
    }

    /** {@return how many terrain passes ran}. */
    int terrainPasses() {
        return this.terrainPasses;
    }

    /** Records an immediate-only pass. */
    void noteImmediateOnlyPasses() {
        this.immediateOnlyPasses++;
    }

    /** {@return how many immediate-only passes ran}. */
    int immediateOnlyPasses() {
        return this.immediateOnlyPasses;
    }

    /** Records a flush at a world pass boundary. */
    void noteWorldPassBoundaryFlushes() {
        this.worldPassBoundaryFlushes++;
    }

    /** {@return how many flushes happened at a world pass boundary}. */
    int worldPassBoundaryFlushes() {
        return this.worldPassBoundaryFlushes;
    }

    /** Records the frame the last terrain pass ran in. */
    void setLastTerrainPassFrame(long frameId) {
        this.lastTerrainPassFrame = frameId;
    }

    /** {@return the frame the last terrain pass ran in}. */
    long lastTerrainPassFrame() {
        return this.lastTerrainPassFrame;
    }
}
