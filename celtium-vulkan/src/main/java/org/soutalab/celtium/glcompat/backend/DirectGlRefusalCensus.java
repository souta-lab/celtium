package org.soutalab.celtium.glcompat.backend;

import java.util.Locale;
import org.soutalab.celtium.glcompat.GlCompatLog;

/**
 * Process-wide Lane 3 refusal accounting for CELTIUM_VERIFY. The main-target remap count
 * rides in the same summary: it is the positive counterpart of the target_incompatible
 * refusals, and a leg reads both from the one line.
 */
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
    private static long mainTargetRemaps;

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

    /**
     * Notes one draw submitted against the sink's main target because the framebuffer bound
     * for drawing is the game's main one (the runtime maps that binding to target id 0
     * instead of realizing the framebuffer as a side target).
     */
    public static void noteMainTargetRemap() {
        synchronized (COUNTS) {
            mainTargetRemaps++;
        }
    }

    public static long mainTargetRemaps() {
        synchronized (COUNTS) {
            return mainTargetRemaps;
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
            return "modRefusals=" + total + " modRefusalsByReason[" + reasons + ']'
                    + " mainTargetRemapped=" + mainTargetRemaps;
        }
    }
}
