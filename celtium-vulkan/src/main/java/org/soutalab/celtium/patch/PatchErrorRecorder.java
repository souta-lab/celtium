package org.soutalab.celtium.patch;

/**
 * The recording half of the item 356 quarantine design (step 356-C): given one failed mixin, decide what -
 * if anything - is written into the {@link PatchLedger} so the next launch differs from this one, and produce
 * the single diagnostic line that says what happened.
 * <p>
 * The caller is {@code CeltiumMixinErrorHandler} in {@code forge122}, which implements Mixin's
 * {@code IMixinErrorHandler}. This class is the pure half of that handler, kept beside the ledger for the
 * same reason {@link PatchGate} is: {@code forge122} has no test source set, and the decisions here - whose
 * failure is ours to record, which groups may never be quarantined, what the line says - are exactly the
 * ones that need pinning. Like the ledger it is {@code java.util} only and loads no mixin class: the names
 * it works with are strings.
 * <p>
 * Three rules, all from the design:
 * <ul>
 *   <li><b>Only celtium's own mixins are recorded.</b> A failure under any other package - and a null mixin -
 *   is {@link Outcome#IGNORED}: another mod's failure is not ours to file, and the handler returns Mixin's
 *   action untouched in every case, so recording can never change another mod's outcome either.</li>
 *   <li><b>An essential group is never quarantined.</b> Its failure stays fatal with no repair recorded; the
 *   line says the group cannot be switched off and asks for the whole log, because there is no configuration
 *   without that group in which celtium still draws (see {@link PatchGroups}). An unlisted own mixin lands
 *   here too, through {@link PatchGroups#of}'s fall-back to {@code mechanism}.</li>
 *   <li><b>A failed write is said out loud.</b> When the ledger cannot be written (a read-only install, a
 *   full disk), the next launch will fail in exactly the same place, and the line says so instead of letting
 *   the silence look like a repair.</li>
 * </ul>
 * Recording never throws: {@link PatchLedger#recordQuarantine} already reports failure as {@code false}, and
 * everything else here is string handling over values the caller read from the failing mixin's metadata.
 */
public final class PatchErrorRecorder {

    /** What recording one failure did. */
    public enum Outcome {
        /** Not one of celtium's own mixins (or no mixin at all): nothing recorded, no line. */
        IGNORED,
        /** The group may never be quarantined: nothing recorded; the line says it cannot be switched off. */
        ESSENTIAL,
        /** The group was written into the ledger; the next launch skips it. */
        QUARANTINED,
        /** The group could have been quarantined but the ledger could not be written. */
        RECORD_FAILED
    }

    /** The outcome of one recording attempt: what happened, for which group, and the line to log. */
    public static final class Result {
        private final Outcome outcome;
        private final String group;
        private final String line;

        private Result(Outcome outcome, String group, String line) {
            this.outcome = outcome;
            this.group = group;
            this.line = line;
        }

        /** {@return what recording did}. */
        public Outcome outcome() {
            return this.outcome;
        }

        /** {@return the group the failure belongs to, or an empty string when it was ignored}. */
        public String group() {
            return this.group;
        }

        /** {@return the one diagnostic line for this failure, or an empty string when it was ignored}. */
        public String line() {
            return this.line;
        }
    }

    private PatchErrorRecorder() {
    }

    /** {@return true when the class name is one of celtium's own mixins}. */
    public static boolean isOwnMixin(String mixinClassName) {
        return mixinClassName != null && mixinClassName.startsWith(PatchGroups.MIXIN_PACKAGE);
    }

    /**
     * Records one failed mixin into {@code ledger} and returns what happened.
     *
     * @param ledger the launch's ledger, or null when it could not be loaded (recorded as a failed write)
     * @param phase  the Mixin phase word the ledger reason carries: {@code "prepare"} or {@code "apply"}
     * @param mixinClassName the failing mixin's fully qualified class name, or null
     * @param error  the failure, or null
     */
    public static Result record(PatchLedger ledger, String phase, String mixinClassName, Throwable error) {
        if (!isOwnMixin(mixinClassName)) {
            return new Result(Outcome.IGNORED, "", "");
        }
        String group = PatchGroups.of(mixinClassName);
        String simple = simpleName(mixinClassName);
        String detail = detailOf(error);
        if (PatchGroups.isEssential(group)) {
            return new Result(Outcome.ESSENTIAL, group,
                    "CELTIUM_PATCH_ESSENTIAL group=" + group + " mixin=" + simple + " error=" + detail
                            + " remedy=none (this group cannot be switched off; report the failure"
                            + " with the whole log)");
        }
        if (ledger == null) {
            return new Result(Outcome.RECORD_FAILED, group, recordFailedLine(group, simple, detail));
        }
        String reason = PatchLedger.quarantineReason(phase, simple, error);
        if (!ledger.recordQuarantine(group, reason)) {
            return new Result(Outcome.RECORD_FAILED, group, recordFailedLine(group, simple, detail));
        }
        return new Result(Outcome.QUARANTINED, group,
                "CELTIUM_PATCH_QUARANTINED group=" + group + " mixin=" + simple + " error=" + detail);
    }

    private static String recordFailedLine(String group, String simple, String detail) {
        return "CELTIUM_PATCH_RECORD_FAILED group=" + group + " mixin=" + simple + " error=" + detail
                + " remedy=none (the ledger could not be written, so the next launch will fail"
                + " the same way)";
    }

    /** {@return the mixin's simple class name: the part after the last dot}. */
    static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }

    /** {@return the failure as {@code <ExceptionSimpleName>: <first line of its message>}, one line}. */
    private static String detailOf(Throwable error) {
        if (error == null) {
            return "none";
        }
        String message = error.getMessage();
        return error.getClass().getSimpleName() + ": " + oneLine(message == null ? "" : message);
    }

    private static String oneLine(String text) {
        return text.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
