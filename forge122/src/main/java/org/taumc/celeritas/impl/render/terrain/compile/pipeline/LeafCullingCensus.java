package org.taumc.celeritas.impl.render.terrain.compile.pipeline;

/**
 * Leaf-culling census (III-1): counts the two events the native implementation exists to
 * produce - leaf faces suppressed by the {@code BlockLeaves} mixin, and leaf blocks solidified
 * by the fast renderer's material swap - so a leg can show the mechanism fired (both counts
 * positive under SOLID, both zero under NONE) instead of inferring it from the picture alone.
 * The counts are evidence for the leg's verdict, never a performance claim.
 *
 * <p>The shape is {@code PrepareNullCensus}'s: enabled only when
 * {@code -Dceltium.debug.leafCullingCensus} is set (the value bounds the session's printed
 * lines, default 24 when set without a usable value), inert otherwise - when the property is
 * unset the record methods return without touching the counters, so the production path is
 * unchanged. A summary line is printed on the first event and whenever the event total crosses
 * a multiple of 1000, bounded by the line limit, so a session under the per-tag line cap still
 * shows cumulative totals rather than losing the tail silently. The first consultation that
 * finds the census enabled prints one all-zero snapshot, so a leg can tell "armed and counted
 * zero" apart from "never armed".
 */
public final class LeafCullingCensus {
    private static int lineLimitFromProperty() {
        if (System.getProperty("celtium.debug.leafCullingCensus") == null) {
            return 0;
        }

        return Math.max(0, Integer.getInteger("celtium.debug.leafCullingCensus", 24));
    }

    /** The production census; inert unless the property is set. */
    public static final LeafCullingCensus GLOBAL = new LeafCullingCensus(lineLimitFromProperty());

    private static boolean armedLinePrinted;

    public static boolean enabled() {
        if (GLOBAL.lineLimit <= 0) {
            return false;
        }

        if (!armedLinePrinted) {
            armedLinePrinted = true;
            GLOBAL.printSnapshot();
        }

        return true;
    }

    public static void recordSuppressedFace() {
        if (enabled()) {
            GLOBAL.recordEvent(true);
        }
    }

    public static void recordSolidifiedBlock() {
        if (enabled()) {
            GLOBAL.recordEvent(false);
        }
    }

    private final int lineLimit;
    private int printedLines;
    private long suppressedFaces;
    private long solidifiedBlocks;

    public LeafCullingCensus(int lineLimit) {
        this.lineLimit = Math.max(0, lineLimit);
    }

    /** Counts one event and prints a summary on the cadence. Package-private for the unit test. */
    synchronized void recordEvent(boolean suppressedFace) {
        if (suppressedFace) {
            this.suppressedFaces++;
        } else {
            this.solidifiedBlocks++;
        }

        long total = this.suppressedFaces + this.solidifiedBlocks;
        if ((total == 1 || total % 1000 == 0) && this.printedLines < this.lineLimit) {
            this.printedLines++;
            this.printSnapshot();
        }
    }

    private void printSnapshot() {
        System.out.println("[Celtium] CELTIUM_LEAF_CULLING suppressedFaces=" + this.suppressedFaces
                + " solidifiedBlocks=" + this.solidifiedBlocks);
    }

    /** Test-facing snapshot of the counters, in the printed line's field order. */
    public synchronized long[] snapshot() {
        return new long[]{this.suppressedFaces, this.solidifiedBlocks};
    }
}
