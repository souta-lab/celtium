package org.soutalab.celtium.vulkan;

/**
 * Injects a descriptor-set failure at a chosen call site, so the failure path can be exercised on a real GPU.
 * <p>
 * The allocation faults cover buffers and images; descriptors are a separate resource with their own refusal path in
 * the backend, and that path had never been reached before this switch existed. Two forms are supported:
 * <ul>
 *   <li>{@code -Dceltium.vulkan.failDescriptorSetAt=<n>} fails the n-th descriptor-set request (1-based);</li>
 *   <li>{@code -Dceltium.vulkan.failDescriptorSetSite=<substring>} fails the first request whose label contains it.</li>
 * </ul>
 * A failing request returns zero, which is what the callers already treat as "no descriptor available", so the
 * question this answers is whether they then refuse cleanly instead of drawing with a stale or null handle.
 */
public final class VulkanDescriptorFault {
    private static final long FAIL_AT = Long.getLong("celtium.vulkan.failDescriptorSetAt", -1L).longValue();
    private static final String FAIL_SITE = System.getProperty("celtium.vulkan.failDescriptorSetSite", "");
    private static long requests;
    private static int injections;
    private static boolean reported;

    private VulkanDescriptorFault() {
    }

    /**
     * {@return whether this descriptor request should fail}.
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
                System.out.println("[Celtium] CELTIUM_DESCRIPTOR_FAULT site=" + site + " request=" + requests);
            }
        }
        return fail;
    }

    /** {@return how many descriptor requests were seen}. */
    public static synchronized long requests() {
        return requests;
    }

    /** {@return how many were made to fail}. */
    public static synchronized int injections() {
        return injections;
    }
}
