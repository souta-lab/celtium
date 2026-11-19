package org.soutalab.celtium.vulkan;

/**
 * Injects a texture-upload failure, so the path a texture takes when it cannot be mirrored can be exercised.
 * <p>
 * The allocation and descriptor faults cover their own resources; a texture that fails to upload is a third case, and
 * the interesting question is the same one: does the renderer refuse the batches that need it, keep rendering the rest,
 * and stay free of validation errors - or does it draw with a stale or empty texture?
 * <ul>
 *   <li>{@code -Dceltium.vulkan.failTextureUploadAt=<n>} fails the n-th upload (1-based);</li>
 *   <li>{@code -Dceltium.vulkan.failTextureUploadSite=<substring>} fails the first upload whose label contains it.</li>
 * </ul>
 */
public final class VulkanTextureFault {
    private static final long FAIL_AT = Long.getLong("celtium.vulkan.failTextureUploadAt", -1L).longValue();
    private static final String FAIL_SITE = System.getProperty("celtium.vulkan.failTextureUploadSite", "");
    private static long requests;
    private static int injections;
    private static boolean reported;

    /**
     * The test override of the two JVM switches, or inactive in every production run.
     * <p>
     * The switches are read once at class load because they are JVM arguments, which left the injector with no
     * seam an offline test could arm - the same gap {@code StageGate.withSwitchedOff} closes for the stage gate.
     * A test arms the override with {@link #armForTest}, which also resets the counters so the test reads its own
     * run and no other, and disarms it afterwards; a leaked override would fail uploads in whatever test ran next
     * in the same JVM, so the tests that arm it do so in a try/finally. Volatile because {@link #shouldFail} reads
     * it on the hot path without the monitor: one volatile read is the whole cost while no test is armed.
     */
    private static volatile boolean testArmed;
    private static long testFailAt = -1L;
    private static String testFailSite = "";

    private VulkanTextureFault() {
    }

    /**
     * Arms the injector for a test, with the same two shapes the JVM switches offer, and resets the counters.
     * Package-private: production arming is the JVM switches alone.
     *
     * @param failAt   fail the n-th upload from now (1-based), or a value {@code <= 0} for no count arm
     * @param failSite fail the first upload whose label contains this, or empty for no site arm
     */
    static synchronized void armForTest(long failAt, String failSite) {
        testFailAt = failAt;
        testFailSite = failSite == null ? "" : failSite;
        requests = 0L;
        injections = 0;
        reported = false;
        testArmed = true;
    }

    /** Disarms the test override and resets the counters, restoring the JVM switches' (unarmed) behaviour. */
    static synchronized void disarmForTest() {
        testArmed = false;
        testFailAt = -1L;
        testFailSite = "";
        requests = 0L;
        injections = 0;
        reported = false;
    }

    /**
     * Whether any injection was configured at all. Resolved once, because the switch is a JVM argument.
     * <p>
     * {@link #shouldFail} is asked on every texture upload, and it was a {@code synchronized} static method doing a
     * counter increment and up to two {@code String} operations - a global lock in the middle of the accept path, for a
     * feature that is off in every shipping configuration. The profile put it at 2.3% of what runs inside
     * {@code submitImmediateBatchBody}. When nothing is armed there is nothing to count either, so the early return is
     * also honest: {@code requests} is the number of uploads the injector looked at, which is zero when it is not armed.
     */
    private static final boolean ARMED = FAIL_AT > 0 || !FAIL_SITE.isEmpty();

    /**
     * {@return whether this texture upload should be skipped}.
     *
     * @param site a short label identifying the call site, for the substring form and for the log line
     */
    public static boolean shouldFail(String site) {
        return (ARMED || testArmed) && shouldFailArmed(site);
    }

    private static synchronized boolean shouldFailArmed(String site) {
        long failAt = testArmed ? testFailAt : FAIL_AT;
        String failSite = testArmed ? testFailSite : FAIL_SITE;
        requests++;
        boolean fail = false;
        if (failAt > 0 && requests == failAt) {
            fail = true;
        } else if (!failSite.isEmpty() && injections == 0 && site != null && site.contains(failSite)) {
            fail = true;
        }
        if (fail) {
            injections++;
            if (!reported) {
                reported = true;
                System.out.println("[Celtium] CELTIUM_TEXTURE_FAULT site=" + site + " request=" + requests);
            }
        }
        return fail;
    }

    /** {@return how many uploads were requested}. */
    public static synchronized long requests() {
        return requests;
    }

    /** {@return how many were made to fail}. */
    public static synchronized int injections() {
        return injections;
    }
}
