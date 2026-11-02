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
 * The cap is therefore a size, not a policy, and this class owns it: a 16384-record default (4x, 25 MiB of records and
 * 2 MiB of commands for the six segments) and a property for a machine whose world needs more.
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

    private ModelRecordCapacity() {
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
