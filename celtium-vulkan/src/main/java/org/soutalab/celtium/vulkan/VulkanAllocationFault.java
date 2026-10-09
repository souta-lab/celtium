package org.soutalab.celtium.vulkan;

/**
 * Test-only injection of an allocation failure, so the paths that must survive one can be exercised.
 * <p>
 * The harness could inject a failed submit and a failed idle wait, but nothing made an allocation fail - and those are
 * the failures that decide whether a half-built resource leaks, whether the caller sees a bounded failure or whether the
 * process dies with a driver error. {@code -Dceltium.vulkan.failAllocationAt=<n>} fails the n-th allocation this class is
 * asked about (counting from one, across every site that asks).
 */
public final class VulkanAllocationFault {

    /** The allocation number to fail, or 0 for none. */
    private static final int FAIL_AT = Integer.getInteger("celtium.vulkan.failAllocationAt", 0).intValue();

    private static int seen;
    private static int injected;
    private static String lastInjection = "none";

    private VulkanAllocationFault() {
    }

    /**
     * {@return whether the allocation about to be made should be made to fail}.
     *
     * @param what a short description of the allocation, for the record
     */
    public static boolean shouldFail(String what) {
        if (FAIL_AT <= 0) {
            return false;
        }
        seen++;
        if (seen != FAIL_AT) {
            return false;
        }
        injected++;
        lastInjection = what + " (#" + seen + ")";
        System.out.println("[Celtium] CELTIUM_ALLOCATION_FAULT injected for " + lastInjection);
        return true;
    }

    /** {@return how many allocations failed because of the injection}. */
    public static int injected() {
        return injected;
    }

    /** {@return a description of the last injected failure}. */
    public static String lastInjection() {
        return lastInjection;
    }

    /** {@return how many allocations were asked about}. */
    public static int seen() {
        return seen;
    }
}
