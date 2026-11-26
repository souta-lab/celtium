package org.soutalab.celtium.vulkan;

import java.io.PrintStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Bounds the renderer's own diagnostic lines in the game log, by tag.
 * <p>
 * Measured need: the user's sessions logged 46 MB of one tag ({@code CELTIUM_UNIT_BIND}) and, after that was fixed,
 * still kept dozens of periodic tags going for the whole session (a panorama copy line every 40 copies, a present line
 * every 300 presents, a model-mesh line per compiled list...). Fixing them one at a time kept missing the next one, so
 * the bound is applied where every one of them passes: a line that starts with {@code "[Celtium] "} or
 * {@code "celtium "} is counted under its first word, the first {@link #LIMIT} lines of each tag go through, and the
 * rest are counted and summarised once at shutdown. {@code -Dceltium.debug.periodicLogs=true} turns the bound off.
 * Lines that are not the renderer's pass through untouched.
 * <p>
 * Installed in front of whatever {@code System.out} is (Forge's tracing stream), so a dropped line never reaches the
 * stream's per-line caller lookup either.
 * <p>
 * Three tags are exempt ({@link #isUnlimited(String)}): the crash-breadcrumb dump, the pipeline-creation identity
 * line and the present heartbeat. Each is bounded by something other than this limit - the crumb ring itself
 * (128 crumbs plus a header and a footer), the number of distinct pipeline states a session reaches, the
 * heartbeat's own thinning (the first three calls, then one line per 300 present calls) - and each is a line a
 * diagnosis reads at exactly the point the bound would cut it. The bound above measured exactly the failure
 * item 414 diagnosed for the dump: of 128 crumbs, the 16 the tag limit let through were all the loading screen's
 * repeated window operations, so the dump reached the log with its evidence already cut away.
 * The exemption is a named set, not a prefix or a flag on the line, so no other tag can drift into it.
 */
public final class CeltiumLogLimiter extends PrintStream {
    static final int LIMIT = Integer.getInteger("celtium.debug.logLinesPerTag", 16);
    /**
     * Tags that always print, however many lines they reach: the breadcrumb dump (see the class comment), the
     * pipeline-creation identity line and the present heartbeat. Creations are one per distinct pipeline state, not
     * per frame, so that tag is bounded by the number of states a session reaches - and a creation the limit cut is
     * exactly the generation a device-loss diagnosis can no longer name (item 414: creations 39-45 sat behind
     * earlier lines of other tags' summaries, with no identity anywhere). The heartbeat is bounded by its own
     * thinning - the first three present calls, then one line per 300 - and the per-tag bound cut it at the same
     * call in every run: three early lines plus thirteen thinned ones exhaust the 16 at hookCalls=3900, after which
     * the present path's end state went unread for the rest of the session, which is the observation a
     * present-freeze diagnosis reads (B2 freeze desk analysis, 2026-11-26).
     */
    private static final java.util.Set<String> UNLIMITED_TAGS = java.util.Set.of(
            "CELTIUM_CRUMB", "CELTIUM_CRASH_BREADCRUMBS", "CELTIUM_PIPELINE_CREATE", "CELTIUM_PRESENT",
            // DIAGNOSTIC ONLY (devlocalimage-plateau-diag branch; never lands): the pair census prints
            // one line per allocation and would be cut at 16 by the per-tag bound.
            "CELTIUM_PAIR_CENSUS");
    private static final Map<String, AtomicLong> COUNTS = new ConcurrentHashMap<String, AtomicLong>();
    private static volatile boolean summaryHookInstalled;

    private final PrintStream delegate;

    private CeltiumLogLimiter(PrintStream delegate) {
        super(delegate, true);
        this.delegate = delegate;
    }

    /** Installs the limiter in front of the current {@code System.out}; idempotent, and a no-op when disabled. */
    public static void install() {
        if (PeriodicLog.ENABLED) {
            return;
        }
        PrintStream current = System.out;
        if (current instanceof CeltiumLogLimiter) {
            return;
        }
        System.setOut(new CeltiumLogLimiter(current));
        if (!summaryHookInstalled) {
            summaryHookInstalled = true;
            Runtime.getRuntime().addShutdownHook(new Thread(CeltiumLogLimiter::printSummary, "celtium-log-summary"));
        }
    }

    /** {@return the tag a line is counted under}, or null for a line that is not the renderer's. */
    static String tagOf(String line) {
        if (line == null) {
            return null;
        }
        int start;
        if (line.startsWith("[Celtium] ")) {
            start = 10;
        } else if (line.startsWith("celtium ")) {
            start = 8;
        } else {
            return null;
        }
        int end = start;
        while (end < line.length() && line.charAt(end) != ' ' && line.charAt(end) != '=' && line.charAt(end) != ':') {
            end++;
        }
        return end > start ? line.substring(start, end) : "untagged";
    }

    /** {@return whether the tag prints without the per-tag bound}; see {@link #UNLIMITED_TAGS}. */
    static boolean isUnlimited(String tag) {
        return tag != null && UNLIMITED_TAGS.contains(tag);
    }

    /** {@return whether a line may be printed}; counts it either way, unless its tag is {@link #isUnlimited}. */
    static boolean admit(String line) {
        String tag = tagOf(line);
        if (tag == null || isUnlimited(tag)) {
            return true;
        }
        return COUNTS.computeIfAbsent(tag, ignored -> new AtomicLong()).incrementAndGet() <= LIMIT;
    }

    @Override
    public void println(String line) {
        if (admit(line)) {
            this.delegate.println(line);
        }
    }

    @Override
    public void println(Object value) {
        String line = String.valueOf(value);
        if (admit(line)) {
            this.delegate.println(line);
        }
    }

    private static void printSummary() {
        StringBuilder summary = new StringBuilder();
        int tags = 0;
        long suppressed = 0;
        for (Map.Entry<String, AtomicLong> entry : COUNTS.entrySet()) {
            long over = entry.getValue().get() - LIMIT;
            if (over > 0) {
                tags++;
                suppressed += over;
                if (summary.length() < 2000) {
                    summary.append(' ').append(entry.getKey()).append('=').append(over);
                }
            }
        }
        if (suppressed > 0) {
            System.err.println("[Celtium] CELTIUM_LOG_LIMIT suppressed " + suppressed + " lines in " + tags
                    + " tags after " + LIMIT + " each (-Dceltium.debug.periodicLogs=true prints all):" + summary);
        }
    }
}
