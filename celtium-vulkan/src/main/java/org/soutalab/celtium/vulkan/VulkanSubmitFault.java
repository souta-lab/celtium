package org.soutalab.celtium.vulkan;

/**
 * Injects a queue-submit failure, so the path around a failed submit can be exercised on a real GPU.
 * <p>
 * The backend already distinguishes a failure before {@code vkQueueSubmit} from one after it - a command buffer that
 * may be in flight must not be reset, re-recorded or destroyed, and a fence that will never be signalled must not be
 * waited on forever. Those rules are written in the code; this switch is what makes them reachable.
 * <ul>
 *   <li>{@code -Dceltium.vulkan.failSubmitAt=<n>} fails the n-th submit (1-based);</li>
 *   <li>{@code -Dceltium.vulkan.failSubmitSite=<substring>} fails the first submit whose label contains it.</li>
 * </ul>
 */
public final class VulkanSubmitFault {
    private static final long FAIL_AT = Long.getLong("celtium.vulkan.failSubmitAt", -1L).longValue();
    private static final String FAIL_SITE = System.getProperty("celtium.vulkan.failSubmitSite", "");
    private static long requests;
    private static int injections;
    private static boolean reported;

    private VulkanSubmitFault() {
    }

    /**
     * {@return whether this submit should be made to fail before it reaches the driver}.
     *
     * @param site a short label identifying the call site
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
                System.out.println("[Celtium] CELTIUM_SUBMIT_FAULT site=" + site + " request=" + requests);
            }
        }
        return fail;
    }

    /** {@return how many submits were requested}. */
    public static synchronized long requests() {
        return requests;
    }

    /** {@return how many were made to fail}. */
    public static synchronized int injections() {
        return injections;
    }
}
