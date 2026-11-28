package org.soutalab.celtium.glcompat.backend;

import java.util.Locale;
import org.soutalab.celtium.glcompat.GlCompatLog;

/** Process-wide Lane 3 refusal accounting for CELTIUM_VERIFY. */
public final class DirectGlRefusalCensus {
    public enum Reason {
        COMPILE_FAILED, UNSUPPORTED_STAGE, NO_FRAME, TARGET_INCOMPATIBLE,
        FEEDBACK, SAMPLER_UNBOUND, DESCRIPTOR_EXHAUSTED, PIPELINE_FAILED;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final Reason[] REASONS = Reason.values();
    private static final long[] COUNTS = new long[REASONS.length];

    private DirectGlRefusalCensus() {}

    public static void note(Reason reason, String detail) {
        long count;
        synchronized (COUNTS) {
            count = ++COUNTS[reason.ordinal()];
        }
        if (count <= 16) {
            GlCompatLog.LOGGER.warn("Direct GL refusal {}: {}", reason.key(), detail);
        }
    }

    public static long count(Reason reason) {
        synchronized (COUNTS) {
            return COUNTS[reason.ordinal()];
        }
    }

    public static String refusalSummary() {
        synchronized (COUNTS) {
            long total = 0;
            StringBuilder reasons = new StringBuilder();
            for (Reason reason : REASONS) {
                long count = COUNTS[reason.ordinal()];
                total += count;
                if (!reasons.isEmpty()) reasons.append(' ');
                reasons.append(reason.key()).append('=').append(count);
            }
            return "modRefusals=" + total + " modRefusalsByReason[" + reasons + ']';
        }
    }
}
