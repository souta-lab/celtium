package org.soutalab.celtium.vulkan;

/**
 * The switch for diagnostic lines that repeat for the whole session ("every Nth frame / group / draw").
 * <p>
 * Measured need (leg {@code tickjfr-celtium-2}, AMD, world, shipping flags): the steady state wrote about 600 lines a
 * second, every one through Forge's {@code TracingPrintStream}, which walks the caller's stack per line - the
 * group-viewport line alone was 0.7% of the client thread's samples, and the log grew without bound in normal play.
 * The bounded first-N lines stay as they are; only the periodic repeats need {@code -Dceltium.debug.periodicLogs=true}.
 */
public final class PeriodicLog {
    public static final boolean ENABLED = Boolean.getBoolean("celtium.debug.periodicLogs");

    private PeriodicLog() {
    }
}
