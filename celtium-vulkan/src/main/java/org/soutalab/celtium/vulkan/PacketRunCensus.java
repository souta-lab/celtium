package org.soutalab.celtium.vulkan;

/**
 * How long a run of consecutive draw packets shares one pipeline and one descriptor set.
 * <p>
 * This is the number that decides whether collapsing packets into one indirect multi-draw is worth building, and how
 * much it could collapse: a multi-draw command is one driver call for a run whose packets agree on the pipeline, the
 * descriptor set and the command buffer, with everything that differs between them - the matrices, the state - fed
 * from an SSBO indexed by {@code gl_DrawID}. Runs of length one would not collapse at all.
 * <p>
 * Counted as identity comparisons against the previous packet, which is exactly the condition the loop would have to
 * test before extending a run.
 */
final class PacketRunCensus {

    private Object lastPipeline;
    private long lastDescriptor;
    private boolean seen;
    private long packets;
    private long runs;
    private long longestRun;
    private long currentRun;

    /**
     * Records one packet and whether it can extend the open run.
     * <p>
     * The descriptor is taken as a {@code long}, not as an {@code Object}: it is a primitive handle, and boxing it to
     * compare by identity made every packet look different from the one before - the first measurement of this census
     * read {@code longest=1} over 745,530 packets, which is the instrument failing, not the scene.
     */
    void observe(Object pipeline, long descriptor) {
        this.packets++;
        if (this.currentRun > 0 && this.seen && pipeline == this.lastPipeline && descriptor == this.lastDescriptor) {
            this.currentRun++;
        } else {
            this.runs++;
            this.currentRun = 1;
        }
        if (this.currentRun > this.longestRun) {
            this.longestRun = this.currentRun;
        }
        this.lastPipeline = pipeline;
        this.lastDescriptor = descriptor;
        this.seen = true;
    }

    long packets() {
        return this.packets;
    }

    long runs() {
        return this.runs;
    }

    long longestRun() {
        return this.longestRun;
    }

    /** {@return the mean packets per run, or -1 before any packet}. */
    long meanRunPermille() {
        return this.runs == 0L ? -1L : this.packets * 1000L / this.runs;
    }

    /** {@return a bounded one-line census for the verification line}. */
    String summary() {
        return "packets=" + this.packets + " runs=" + this.runs + " meanPermille=" + this.meanRunPermille()
                + " longest=" + this.longestRun;
    }
}
