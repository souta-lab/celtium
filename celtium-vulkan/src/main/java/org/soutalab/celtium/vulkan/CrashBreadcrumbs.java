package org.soutalab.celtium.vulkan;

/**
 * The last few renderer operations before a device loss, for a crash that only reproduces on someone else's machine.
 * <p>
 * A RADV GPUVM fault reports an address and nothing else, and the log around it carries whatever diagnostic lines
 * happened to be printed - in the measured case a texture-dump line and a loading-bar quad, neither of which is the
 * faulting call. This is a ring of the operations themselves (which window, which submit, which upload, which pair),
 * dumped only when the device is lost, so the next report names the operation instead of leaving it inferred.
 * <p>
 * Off unless {@code -Dceltium.vulkan.crashBreadcrumbs=true}: the ring writes one string reference per operation, which
 * is not free in a 400-fps frame, and a working session never reads it.
 */
public final class CrashBreadcrumbs {
    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.crashBreadcrumbs");
    private static final int CAPACITY = 96;
    private static final String[] RING = new String[CAPACITY];
    private static int cursor;
    private static boolean dumped;

    private CrashBreadcrumbs() {
    }

    /** {@return whether the ring is enabled}, so a hot site can skip formatting its tag. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Records one operation, bounded to the ring. */
    public static void note(String tag) {
        if (!ENABLED) {
            return;
        }
        RING[cursor % CAPACITY] = tag;
        cursor++;
    }

    /** Records one operation with a cheap integer, so a hot site does not format a string unless the ring wraps. */
    public static void note(String tag, long a, long b) {
        if (!ENABLED) {
            return;
        }
        note(tag + " a=" + a + " b=" + b);
    }

    /** Prints the ring, newest last, once; the first device loss is the one whose context matters. */
    public static void dump(String trigger) {
        if (!ENABLED || dumped) {
            return;
        }
        dumped = true;
        int count = Math.min(cursor, CAPACITY);
        System.out.println("[Celtium] CELTIUM_CRASH_BREADCRUMBS trigger=" + trigger + " count=" + count
                + " (the operations before the device loss, oldest first)");
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(cursor - count + i, CAPACITY);
            System.out.println("[Celtium] CELTIUM_CRUMB #" + i + " " + RING[index]);
        }
    }
}
