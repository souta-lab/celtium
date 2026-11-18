package org.soutalab.celtium.vulkan;

/**
 * The last few submissions the renderer handed to the queue, kept as numbers in the same spirit as
 * {@link CrashBreadcrumbs}: three array writes per submission, formatted only when a stall asks.
 * <p>
 * Item 414's gap: the submission counters ({@code submitSequence} and its completed twin) print at teardown and in
 * the VERIFY line, but the teardown that detected the loss threw before its own print, so the log held no record of
 * what had been submitted last - the two-second window could be bracketed only by reading two successful settles
 * around it. This ring is what {@code noteStalled} prints instead: the newest {@link #KEPT} submissions with their
 * kind and the two numbers each kind defines (a frame submission's scheduler sequence and slot; a deferred
 * submission's fence and upload-slot mask).
 * <p>
 * Static and synchronized-free in the same way the breadcrumb ring is: submissions are recorded on the render
 * thread, and a torn read at stall time costs at most one entry's coherence, never the stall report itself.
 */
public final class SubmitTrail {
    /** A frame slot's submission: first number is the scheduler's submit sequence, second is the slot. */
    public static final int FRAME = 1;
    /** A deferred submission: first number is its fence, second is the upload-slot mask it read. */
    public static final int DEFERRED = 2;

    private static final int KEPT = 8;
    private static final int[] KINDS = new int[KEPT];
    private static final long[] FIRST = new long[KEPT];
    private static final long[] SECOND = new long[KEPT];
    private static int cursor;

    private SubmitTrail() {
    }

    /** Records one submission. No allocation and no formatting: three array writes. */
    public static void note(int kind, long first, long second) {
        int index = cursor % KEPT;
        KINDS[index] = kind;
        FIRST[index] = first;
        SECOND[index] = second;
        cursor++;
    }

    /** {@return the recorded submissions, oldest first, as one bounded log field}; empty before the first submit. */
    public static String describe() {
        int count = Math.min(cursor, KEPT);
        if (count == 0) {
            return "lastSubmits=[none]";
        }
        StringBuilder out = new StringBuilder(160);
        out.append("lastSubmits=[");
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(cursor - count + i, KEPT);
            if (i > 0) {
                out.append(' ');
            }
            switch (KINDS[index]) {
                case FRAME:
                    out.append("frame seq=").append(FIRST[index]).append(" slot=").append(SECOND[index]);
                    break;
                case DEFERRED:
                    out.append("deferred fence=0x").append(Long.toHexString(FIRST[index]))
                            .append(" mask=").append(SECOND[index]);
                    break;
                default:
                    out.append("kind=").append(KINDS[index]).append(" a=").append(FIRST[index])
                            .append(" b=").append(SECOND[index]);
                    break;
            }
        }
        return out.append(']').toString();
    }

    /** Clears the ring; for tests, which share the static state. */
    static void clear() {
        cursor = 0;
    }
}
