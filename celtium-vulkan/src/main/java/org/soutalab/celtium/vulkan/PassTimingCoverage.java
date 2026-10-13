package org.soutalab.celtium.vulkan;

import java.util.Arrays;

/** Render-thread coverage of named brackets; overlapping inclusive times contribute once to the frame. */
final class PassTimingCoverage {
    private final int[] depth;
    private int active;
    private long openedAt;
    private long covered;

    PassTimingCoverage(int passes) {
        this.depth = new int[passes];
    }

    void reset() {
        Arrays.fill(this.depth, 0);
        this.active = 0;
        this.openedAt = 0L;
        this.covered = 0L;
    }

    /** Returns true only for the outermost occurrence of this pass. */
    boolean begin(int pass, long now) {
        if (this.depth[pass]++ != 0) return false;
        if (this.active++ == 0) this.openedAt = now;
        return true;
    }

    /** An unmatched end does not close somebody else's bracket. */
    boolean end(int pass, long now) {
        if (this.depth[pass] == 0 || --this.depth[pass] != 0) return false;
        if (--this.active == 0) {
            this.covered += Math.max(0L, now - this.openedAt);
        }
        return true;
    }

    /** Clip a still-open bracket to the frame's end instead of leaking it into the next frame. */
    long coveredNanos(long frameEnd) {
        return this.covered + (this.active == 0 ? 0L : Math.max(0L, frameEnd - this.openedAt));
    }
}
