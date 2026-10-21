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

    private VulkanTextureFault() {
    }

    /**
     * {@return whether this texture upload should be skipped}.
     *
     * @param site a short label identifying the call site, for the substring form and for the log line
     */
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

    public static boolean shouldFail(String site) {
        return ARMED && shouldFailArmed(site);
    }

    private static synchronized boolean shouldFailArmed(String site) {
        requests++;
        boolean fail = false;
        if (FAIL_AT > 0 && requests == FAIL_AT) {
            fail = true;
        } else if (!FAIL_SITE.isEmpty() && injections == 0 && site != null && site.contains(FAIL_SITE)) {
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
