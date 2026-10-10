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
    public static synchronized boolean shouldFail(String site) {
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
