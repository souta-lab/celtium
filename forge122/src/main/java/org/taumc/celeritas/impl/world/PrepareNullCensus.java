package org.taumc.celeritas.impl.world;

/**
 * Prepare-null census (item 701): a reason breakdown for {@link WorldSlice#prepare} returning null.
 *
 * When prepare returns null for a non-sort rebuild, RenderSectionManager.submitRebuildTasks
 * fabricates an EMPTY_DATA success and commits it, so the section becomes "built, with nothing in
 * it" and its pending update is gone - there is no re-queue path if the data arrives afterwards.
 * The present census (item 683) attributed the terrain presentation defect to that terminal-built
 * population, but could not split it into sections that are legitimately empty and sections whose
 * data had simply not arrived when they were submitted. This census makes that split at the exact
 * point of the null return:
 *
 *   BLANK_CHUNK  - the provider handed back its blank EmptyChunk placeholder:
 *                  the client never received this chunk, so an EMPTY commit fabricated from this
 *                  null is a guess, not a measurement. This is the suspicious population.
 *   SLOT_ABSENT  - a real chunk whose storage array slot for this Y section was never allocated.
 *                  In vanilla semantics an unallocated slot is how a section with no non-air
 *                  blocks is represented, so this null is a legitimate empty.
 *   SLOT_EMPTY   - a real chunk with an allocated slot whose storage reports isEmpty() (no block
 *                  or tile-entity references). Also a legitimate empty.
 *   Y_OUT_OF_RANGE - origin.y() outside the chunk's storage array. Defensive: prepare used to
 *                  index the array directly, so this could only surface as an exception; the
 *                  section lookup is now range-guarded so it can be counted instead.
 *
 * Enabled only when -Dceltium.debug.prepareNullCensus is set; the value bounds the session's
 * printed lines (default 24 when set without a usable value), the same shape as the present
 * census. When the property is unset, WorldSlice.prepare never touches the counters, so the
 * production path is unchanged. A summary line is printed on the first null, whenever the null
 * total crosses a multiple of 100, and on the first occurrence of a suspicious reason, so a leg
 * sees both the early composition and how it settles without a line per null (chunk loading
 * produces nulls in bulk).
 */
public final class PrepareNullCensus {
    /** Why prepare returned null; see the class note for what each reason means. */
    public enum Reason {
        BLANK_CHUNK,
        SLOT_ABSENT,
        SLOT_EMPTY,
        Y_OUT_OF_RANGE
    }

    /**
     * Classifies a null return from its raw inputs. The blank-chunk check comes first on purpose:
     * a blank EmptyChunk's storage array is all null slots, so testing the slot first would dress
     * "data never arrived" up as a legitimate absent slot. Returns null when the inputs describe
     * a buildable section (slot present and non-empty) - i.e. prepare would not have returned
     * null for them.
     */
    public static Reason classify(boolean chunkBlank, int storageLength, int y, boolean slotAbsent, boolean slotEmpty) {
        if (chunkBlank) {
            return Reason.BLANK_CHUNK;
        }

        if (y < 0 || y >= storageLength) {
            return Reason.Y_OUT_OF_RANGE;
        }

        if (slotAbsent) {
            return Reason.SLOT_ABSENT;
        }

        if (slotEmpty) {
            return Reason.SLOT_EMPTY;
        }

        return null;
    }

    /**
     * The classification for a call site that has already decided prepare returns null. Falls back
     * to SLOT_EMPTY if the pure classifier reads the (racing) inputs as buildable on a second
     * look, so a concurrent chunk fill cannot lose the count.
     */
    public static Reason classifyForNullBranch(boolean chunkBlank, int storageLength, int y, boolean slotAbsent, boolean slotEmpty) {
        Reason reason = classify(chunkBlank, storageLength, y, slotAbsent, slotEmpty);
        return reason != null ? reason : Reason.SLOT_EMPTY;
    }

    /** One census snapshot, in the printed line's field order. */
    public static final class Snapshot {
        public final long calls;
        public final long contexts;
        public final long blankChunk;
        public final long slotAbsent;
        public final long slotEmpty;
        public final long yOutOfRange;

        Snapshot(long calls, long contexts, long blankChunk, long slotAbsent, long slotEmpty, long yOutOfRange) {
            this.calls = calls;
            this.contexts = contexts;
            this.blankChunk = blankChunk;
            this.slotAbsent = slotAbsent;
            this.slotEmpty = slotEmpty;
            this.yOutOfRange = yOutOfRange;
        }

        public long nulls() {
            return this.blankChunk + this.slotAbsent + this.slotEmpty + this.yOutOfRange;
        }

        /** Nulls that describe a section which really has nothing in it. */
        public long legitimateEmpty() {
            return this.slotAbsent + this.slotEmpty;
        }

        /** Nulls where an EMPTY commit would be fabricated from missing data or a bad origin. */
        public long suspicious() {
            return this.blankChunk + this.yOutOfRange;
        }
    }

    private static int lineLimitFromProperty() {
        if (System.getProperty("celtium.debug.prepareNullCensus") == null) {
            return 0;
        }

        return Math.max(0, Integer.getInteger("celtium.debug.prepareNullCensus", 24));
    }

    /** The production census; inert (and never consulted by prepare) unless the property is set. */
    public static final PrepareNullCensus GLOBAL = new PrepareNullCensus(lineLimitFromProperty());

    private static boolean armedLinePrinted;

    public static boolean enabled() {
        if (GLOBAL.lineLimit <= 0) {
            return false;
        }

        if (!armedLinePrinted) {
            armedLinePrinted = true;
            // Positive control (item 683 follow-up): a leg that sees no CELTIUM_PREPARE_NULL line at
            // all must be able to tell "the census was armed and counted zero events" from "the
            // census never became active". The counters only ever printed on a null, and the
            // section-add population this census exists to split never passes through prepare, so
            // a silent session was ambiguous between the two. Print one all-zero snapshot the
            // first time the census is consulted and found enabled; the format is the census line
            // itself, so no new log marker is introduced.
            GLOBAL.printSnapshot();
        }

        return true;
    }

    private final int lineLimit;
    private int printedLines;
    private long calls;
    private long contexts;
    private long addEmptyCommits;
    private long blankChunk;
    private long slotAbsent;
    private long slotEmpty;
    private long yOutOfRange;

    public PrepareNullCensus(int lineLimit) {
        this.lineLimit = Math.max(0, lineLimit);
    }

    public void recordContext() {
        this.calls++;
        this.contexts++;
    }

    public void recordNull(Reason reason) {
        this.calls++;
        this.bump(reason);
    }

    /**
     * Counts a section that was committed as EMPTY at section-add time (RenderSectionManager
     * .onSectionAdded, via the manager's onSectionAddedAsEmpty hook) rather than after a prepare
     * null at submit time. The reason taxonomy is the same - that population is the bulk of the
     * present census's terminalBuilt count, and it never passes through WorldSlice.prepare, so
     * before this hook existed the census could not see it at all (item 683: a full leg printed
     * zero census lines while terminalBuilt stood at 680). The add-path share of the reason
     * buckets is reported separately in the line's addEmpty field.
     */
    public void recordAddEmpty(Reason reason) {
        this.addEmptyCommits++;
        this.bump(reason);
    }

    private void bump(Reason reason) {
        switch (reason) {
            case BLANK_CHUNK -> this.blankChunk++;
            case SLOT_ABSENT -> this.slotAbsent++;
            case SLOT_EMPTY -> this.slotEmpty++;
            case Y_OUT_OF_RANGE -> this.yOutOfRange++;
        }

        if (this.shouldPrint(reason)) {
            this.printedLines++;
            this.printSnapshot();
        }
    }

    private void printSnapshot() {
        Snapshot s = this.snapshot();
        System.out.println("[Celtium] CELTIUM_PREPARE_NULL calls=" + s.calls
                + " contexts=" + s.contexts
                + " nulls=" + s.nulls()
                + " blankChunk=" + s.blankChunk
                + " slotAbsent=" + s.slotAbsent
                + " slotEmpty=" + s.slotEmpty
                + " yOutOfRange=" + s.yOutOfRange
                + " legitimateEmpty=" + s.legitimateEmpty()
                + " suspicious=" + s.suspicious()
                + " addEmpty=" + this.addEmptyCommits);
    }

    /**
     * The print cadence: the first null, every hundredth null, and the first sighting of a
     * suspicious reason - bounded by the session line limit. Package-private for the unit test.
     */
    boolean shouldPrint(Reason reason) {
        if (this.printedLines >= this.lineLimit) {
            return false;
        }

        long nulls = this.blankChunk + this.slotAbsent + this.slotEmpty + this.yOutOfRange;

        if (nulls == 1 || nulls % 100 == 0) {
            return true;
        }

        return (reason == Reason.BLANK_CHUNK && this.blankChunk == 1)
                || (reason == Reason.Y_OUT_OF_RANGE && this.yOutOfRange == 1);
    }

    /** Lines printed so far this session; package-private for the unit test. */
    int printedLines() {
        return this.printedLines;
    }

    public Snapshot snapshot() {
        return new Snapshot(this.calls, this.contexts, this.blankChunk, this.slotAbsent, this.slotEmpty, this.yOutOfRange);
    }
}
