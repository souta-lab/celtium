package org.soutalab.celtium.vulkan;

/**
 * Owns the output side of the verification diagnostics: when the summary is allowed to print, and in what shape.
 * <p>
 * This is refactoring plan R1c (docs/refactoring-plan.md section 6). The plan's task-65 warning is why this is the
 * first step rather than a snapshot class: reading the backend's fields into a report object would need hundreds of
 * accessors and would replace one god class with two, so the counters stay in the backend until R2 moves them
 * per domain, and what moves now is the printing contract - the once-per-snapshot guard and the exact line.
 * <p>
 * The line's shape is a contract with the fixture and with every reader of a leg: {@code [Celtium] CELTIUM_VERIFY
 * via=<trigger> <summary>}. It is built here and nowhere else, so a future change to the prefix cannot land in one
 * call site and miss another.
 */
final class BackendReport {

    /** Whether a snapshot trigger has already printed; a snapshot never prints twice. */
    private boolean summaryPrinted;

    /**
     * Prints the summary for a trigger.
     *
     * @param trigger  what asked for it, printed verbatim after {@code via=}
     * @param summary  the counter text, built by the backend's own summary method
     * @param snapshot whether this is a bounded snapshot trigger (frame 120, present 120) rather than a real event
     *                 (world teardown, fixture-requested end of run). A snapshot prints at most once; a real event
     *                 prints every time, because a leg that tears down twice must not lose the second line.
     * @return whether a line was printed
     */
    boolean printSummary(String trigger, String summary, boolean snapshot) {
        if (this.summaryPrinted && snapshot) {
            return false;
        }
        this.summaryPrinted = true;
        System.out.println("[Celtium] CELTIUM_VERIFY via=" + trigger + " " + summary);
        return true;
    }

    /** {@return whether a snapshot trigger has printed already}. */
    boolean summaryPrinted() {
        return this.summaryPrinted;
    }
}
