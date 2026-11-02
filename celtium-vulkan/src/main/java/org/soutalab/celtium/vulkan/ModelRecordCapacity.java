package org.soutalab.celtium.vulkan;

/**
 * How many model-instance records one frame's segment holds.
 * <p>
 * The instance path writes one 256-byte record and one 20-byte indirect command per replay into a per-frame segment of
 * a host buffer, and draws a whole run with one {@code vkCmdDrawIndexedIndirect}. A replay that finds the segment full
 * cannot be written - records are indexed by frame and read by the GPU while the frame is in flight - so it declines
 * and the caller takes the per-group path instead: an {@link ImmediateGroup}, a push block, its own binds and its own
 * draw.
 * <p>
 * That fallback is correct but it is the slow path, and the counter that observes it was read in leg
 * {@code r14e-menu-colour-fix} (Supersymmetry, AMD RX 6400, world stage): {@code appended=242944
 * declinedFull=479232 runs=72 pipelines=1}. Two thirds of that leg's model replays were declined by a 4096-record cap
 * that the pack's world exceeds - not by a missing mesh, an absent list or a state change ({@code declinedMissing=0}).
 * The cap is therefore a size, not a policy, and this class owns it: a 32768-record default (48 MiB of records and
 * 3.75 MiB of commands for the six segments), a property for a machine whose world starts out heavier, and
 * {@link #grownFor} for the frame that turns out to need more than either - because the next leg then showed the same
 * defect one size up (leg {@code r15b}, 37380 replays in a late frame against a 32768-record segment, 4612 declined).
 * <p>
 * The value is clamped rather than refused: a typo must not silently return the model path to the slow lane for the
 * whole session, and a too-small value must not make the segment index arithmetic wrong. The record index
 * ({@code segment * records + used}) must also stay inside an {@code int}, because the indirect command carries it as
 * its {@code firstInstance} - {@link #MAX} keeps the six-segment product at 1.5M, far below that.
 */
final class ModelRecordCapacity {

    /**
     * Records per frame, sized from the pack's own heaviest measured frame rather than from the old constant: leg
     * {@code r15-model-records} had the world stage attempt 26880 model replays in one presented frame (the old 4096
     * cap turned 10496 of them away in that same frame), so the default sits above that peak.
     */
    static final int DEFAULT = 32768;
    /** Below this the per-frame cap binds on any real world; the floor keeps a typo from disabling the instance path. */
    static final int MIN = 1024;
    /** Six segments of this stay below 2^31, which is the width of the indirect command's record index. */
    static final int MAX = 262144;
    /**
     * Records the segment grows by, rounded up to a multiple of this. One page of records is 4 records, so a step of
     * 1024 is 256 KiB of records and 20 KiB of commands per segment - small enough that a frame one replay over the
     * size does not double the footprint, large enough that a load's rising demand converges in a few frames.
     */
    static final int STEP = 1024;
    /**
     * Headroom over the frame that asked for the growth. A load's demand rises for as long as new chunks and block
     * entities are compiled, so a segment sized exactly to the frame that overflowed grows again a frame or two later;
     * the measured rise in leg {@code r15b-model-records-32k} was 26733 (frames 47..60) to 37380 (frames 109..120),
     * i.e. a quarter over the plateau. The fraction is the same 25 % the default carries over its own measurement.
     */
    static final int HEADROOM_PERCENT = 25;

    private ModelRecordCapacity() {
    }

    /**
     * {@return the segment size a frame's demand asks for, or 0 when the current size is enough}.
     * <p>
     * The size is a measurement, not a policy: the default is the pack's plateau and the ceiling is the record index's
     * width, so the only thing left to decide is how a frame that needs more than the default is answered. It answers by
     * growing - the frame that overflowed pays the per-group fallback once, and every later frame writes its records -
     * rather than by turning replays away for the rest of the session. That is the shape of the defect this parameter
     * was found by: 4096 records declined two thirds of a leg's replays and nothing in the run said why.
     * <p>
     * The result is a multiple of {@link #STEP}, at least {@link #STEP} above {@code current} and at least
     * {@code demand + demand * }{@link #HEADROOM_PERCENT}{@code / 100}, never above {@link #MAX}. A demand the current
     * size already covers, a capacity at the ceiling and a non-positive demand all answer 0: nothing to do.
     *
     * @param current the records one frame's segment holds now
     * @param demand  the replays the last frame tried to write, declined ones included
     */
    static int grownFor(int current, long demand) {
        if (demand <= (long) current || current >= MAX) {
            return 0;
        }
        long wanted = demand + demand / 100L * HEADROOM_PERCENT;
        long withStep = Math.max(wanted, (long) current + STEP);
        long rounded = ((withStep + STEP - 1L) / STEP) * STEP;
        long target = Math.min((long) MAX, rounded);
        return target > current ? (int) target : 0;
    }

    /**
     * Resolves {@code -Dceltium.vulkan.modelSegmentRecords=N} to a usable record count.
     *
     * @param value the property's raw text, or {@code null} when it is not set
     * @return the requested count clamped to {@link #MIN}..{@link #MAX}, or {@link #DEFAULT} when the value is absent
     *         or not a number
     */
    static int resolve(String value) {
        if (value == null) {
            return DEFAULT;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return DEFAULT;
        }
        int requested;
        try {
            requested = Integer.parseInt(trimmed);
        } catch (NumberFormatException notANumber) {
            return DEFAULT;
        }
        if (requested < MIN) {
            return MIN;
        }
        if (requested > MAX) {
            return MAX;
        }
        return requested;
    }

    /** {@return the bytes a records or commands buffer needs for every segment at this capacity}. */
    static long bytes(int recordCount, int segments, int bytesPerRecord) {
        return (long) recordCount * segments * bytesPerRecord;
    }
}
