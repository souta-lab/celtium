package org.soutalab.celtium.vulkan;

/**
 * The font domain's fragment of the verification line (refactoring plan R2, stage 1).
 *
 * <p>R2's decision, taken after the seven classes were parked: a counter whose owner already exists is <b>read from
 * that owner</b> rather than moved again or duplicated. The font counters belong to {@link FontBatch} since R3b, so
 * this class reads them there and owns only the fragment - which is the half of the ownership that was missing: the
 * line's text for this domain is built in one place instead of being spelled out in the middle of a 480-line
 * expression.
 *
 * <p>The field names and their order are a contract with the fixture and every leg record
 * (scripts/cpu-contract/verify_line_baseline.txt), so the fragment reproduces them exactly.
 */
public final class FontStatistics {

    private final FontBatch fontBatch;

    FontStatistics(FontBatch fontBatch) {
        this.fontBatch = fontBatch;
    }

    /** {@return run 0 of the line's font fields: fontStripSeparators fontRunsStarted fontQuadsAppended}. */
    public String run0() {
        StringBuilder out = new StringBuilder();
        out.append(" fontStripSeparators=").append(this.fontBatch.fontStripSeparators());
        out.append(" fontRunsStarted=").append(this.fontBatch.fontRunsStarted());
        // Every flush asked for, against the runs actually started: the difference is the per-call cost the
        // profile sees and the classified counters cannot.
        out.append(" fontFlushCalls=").append(this.fontBatch.fontFlushCalls());
        out.append(" fontQuadsAppended=").append(this.fontBatch.fontQuadsAppended());
        return out.toString();
    }
}
