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
 */
public final class CeltiumLogLimiter extends PrintStream {
    static final int LIMIT = Integer.getInteger("celtium.debug.logLinesPerTag", 16);
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

    /** {@return whether a line may be printed}; counts it either way. */
    static boolean admit(String line) {
        String tag = tagOf(line);
        if (tag == null) {
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
