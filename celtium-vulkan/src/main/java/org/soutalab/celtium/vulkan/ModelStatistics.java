package org.soutalab.celtium.vulkan;

/**
 * The model domain's fragments of the verification line (refactoring plan R2, stage 2).
 *
 * <p>Same decision as stage 1: counters whose owner exists are read from that owner. The model mesh table and its
 * quads/failure reason belong to {@link ModelGeometryCache} since R3a; the draw census is still the backend's because
 * it is static on purpose - it must survive a resource reload and a world switch while a backend instance does not.
 * The display-list census is {@link VulkanModelQuadCapture}'s, read through as it always was.
 *
 * <p>The fragments reproduce the field names and order exactly; the line calls them where those fields used to be
 * concatenated.
 */
public final class ModelStatistics {

    private final ModelGeometryCache modelGeometry;
    /** Nanoseconds spent in the model enqueue path, and how many enqueues it covers. */
    private long enqueueNanos;
    private long enqueueCalls;

    ModelStatistics(ModelGeometryCache modelGeometry) {
        this.modelGeometry = modelGeometry;
    }

    /** Records one model enqueue attempt and the time it took. */
    public void noteEnqueue(long nanos) {
        this.enqueueNanos += nanos;
        this.enqueueCalls++;
    }

    /** {@return milliseconds spent in the model enqueue path}. */
    public long enqueueMillis() {
        return this.enqueueNanos / 1000000L;
    }

    /** {@return how many model enqueues the timing covers}. */
    public long enqueueCalls() {
        return this.enqueueCalls;
    }

    /** {@return microseconds per model enqueue, or -1 when the switch is off}. */
    public long enqueueMicrosPerCall() {
        return this.enqueueCalls == 0L ? -1L : this.enqueueNanos / this.enqueueCalls / 1000L;
    }

    /** {@return run 0 of the line's model fields: modelDraws and where the enqueue path's time goes}. */
    public String run0(long modelDraws) {
        return " modelDraws=" + modelDraws
                // Zero means the switch is off, which the report already treats as a missing measurement - not a
                // measurement of zero cost. The per-call figure is the one that says whether a model replay is
                // expensive enough to be worth attacking, or whether its draws alone are what the frame pays for.
                + " modelEnqueueMillis=" + (this.enqueueNanos / 1000000L)
                + " modelEnqueueCalls=" + this.enqueueCalls
                + " modelEnqueueMicrosPerCall=" + (this.enqueueCalls == 0L ? -1L
                        : this.enqueueNanos / this.enqueueCalls / 1000L);
    }

    /** {@return run 1: the draw census, the mesh table and the display-list census, in the line's order}. */
    public String run1(long modelDrawRefusals, String modelLastRefusal) {
        StringBuilder out = new StringBuilder();
        out.append(" modelDrawRefusals=").append(modelDrawRefusals);
        out.append(" modelLastRefusal=").append(modelLastRefusal);
        out.append(" modelMeshLists=").append(this.modelGeometry.modelMeshes() == null ? -1
                : this.modelGeometry.modelMeshes().size());
        return out.toString();
    }

    /** {@return run 2: the mesh quads and the first failure reason, which the line prints after two interleaved
     * expressions from other owners}. */
    public String run2() {
        StringBuilder out = new StringBuilder();
        out.append(" modelMeshQuads=").append(this.modelGeometry.modelMeshQuads());
        out.append(" modelMeshFailure=").append(this.modelGeometry.modelMeshFailure());
        return out.toString();
    }
}
