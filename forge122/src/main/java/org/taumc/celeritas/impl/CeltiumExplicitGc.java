package org.taumc.celeritas.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Counts and suppresses the game's explicit {@code System.gc()} calls on the world-join path.
 * <p>
 * Vanilla calls {@code System.gc()} three times across one world join - in {@code launchIntegratedServer},
 * twice inside {@code freeMemory} and at the end of {@code loadWorld} - and each call is a full stop-the-world
 * collection. On the validation harness those three calls produced the leg's three largest pauses (103, 95 and
 * 88 ms, G1 Full) in a 2 s window at world entry - 286 ms of freeze per join. The calls buy nothing the
 * collector would not do itself: the same work happens at the next young/mixed evacuation without a full
 * collection, which is why MemoryFix-style mods have shipped this suppression for years.
 * <p>
 * Restore them with {@code -Dceltium.explicitGc=true} - the OOM-recovery call inside {@code freeMemory}
 * then behaves exactly as before. Every skipped call is counted and reported once per call site so the
 * behaviour stays observable rather than silently different.
 */
public final class CeltiumExplicitGc {
    public static final boolean ENABLED = !"true".equals(System.getProperty("celtium.explicitGc", "false"));
    private static final Logger LOGGER = LogManager.getLogger("CeltiumExplicitGc");
    private static long skippedCalls;
    private static long reportedMask;

    private CeltiumExplicitGc() {
    }

    /**
     * Called instead of {@code System.gc()}. Does nothing but count and report - the young collections that
     * would have run anyway handle the same allocation wave.
     */
    public static void insteadOfSystemGc(String site) {
        skippedCalls++;
        long bit = 1L << (site.hashCode() & 31);
        if ((reportedMask & bit) == 0L) {
            reportedMask |= bit;
            LOGGER.info("System.gc() call at {} suppressed (celtium.explicitGc=true restores it; {} skipped so far)",
                    site, skippedCalls);
        }
    }

    public static String summary() {
        return "explicitGcSuppressed=" + skippedCalls + " enabled=" + ENABLED;
    }
}
