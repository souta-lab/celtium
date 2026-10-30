package org.soutalab.celtium.vulkan;

/**
 * The last few renderer operations before a device loss, kept as numbers so the ring costs almost nothing and can stay
 * on for every session.
 * <p>
 * A RADV GPUVM fault reports an address and nothing else; the log around it carries whatever diagnostic lines happened
 * to print, which in the measured case were a texture dump and a loading-bar quad rather than the faulting call. This
 * ring records the operations themselves - code, and two numbers that mean whatever the code says (a window's target and
 * group count, an upload's GL name and extent, a chain pair's identity) - and formats them only when the device is
 * actually lost. That is why there is no switch to remember in the launcher: three array writes per operation.
 */
public final class CrashBreadcrumbs {
    /** {@code -Dceltium.vulkan.crashBreadcrumbs=false} turns the ring off; it is on by default. */
    private static final boolean ENABLED =
            !"false".equals(System.getProperty("celtium.vulkan.crashBreadcrumbs", "true"));
    private static final int CAPACITY = 128;
    private static final int[] CODES = new int[CAPACITY];
    private static final long[] FIRST = new long[CAPACITY];
    private static final long[] SECOND = new long[CAPACITY];
    private static int cursor;
    private static boolean dumped;

    /** Operation codes; the dump names them, so the numbers stay readable in a log. */
    public static final int WINDOW = 1;
    public static final int TERRAIN_PASS = 2;
    public static final int CHAIN_PAIR = 3;
    public static final int TEXTURE_UPLOAD = 4;
    public static final int RAW_WRITE = 5;
    public static final int PRESENT = 6;
    public static final int COALESCED_COPY = 7;
    public static final int FRAMEBUFFER_BIND = 8;

    private CrashBreadcrumbs() {
    }

    /** {@return whether the ring is recording}, so a caller can skip reading fields it would only pass here. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** Records one operation. No allocation and no formatting: three array writes. */
    public static void note(int code, long first, long second) {
        if (!ENABLED) {
            return;
        }
        int index = cursor % CAPACITY;
        CODES[index] = code;
        FIRST[index] = first;
        SECOND[index] = second;
        cursor++;
    }

    /** Prints the ring once, oldest first, naming each operation; the first device loss is the one that matters. */
    public static void dump(String trigger) {
        if (!ENABLED || dumped) {
            return;
        }
        dumped = true;
        int count = Math.min(cursor, CAPACITY);
        System.out.println("[Celtium] CELTIUM_CRASH_BREADCRUMBS trigger=" + trigger + " count=" + count
                + " (renderer operations before the device loss, oldest first)");
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(cursor - count + i, CAPACITY);
            System.out.println("[Celtium] CELTIUM_CRUMB #" + i + " " + describe(CODES[index], FIRST[index], SECOND[index]));
        }
    }

    private static String describe(int code, long first, long second) {
        switch (code) {
            case WINDOW:
                return "window target=0x" + Long.toHexString(first) + " groups=" + second;
            case TERRAIN_PASS:
                return "terrain-pass frame=" + first;
            case CHAIN_PAIR:
                return "chain-pair identity=0x" + Long.toHexString(first);
            case TEXTURE_UPLOAD:
                return "texture-upload gl=" + first + " extent=" + (second >>> 32) + "x" + (second & 0xffffffffL);
            case RAW_WRITE:
                return "raw-write texture=" + first + " serial=" + second;
            case PRESENT:
                return "present frame=" + first + " presented=" + second;
            case COALESCED_COPY:
                return "coalesced-copy texture=" + first + " mirrors=" + second;
            case FRAMEBUFFER_BIND:
                return "framebuffer-bind identity=0x" + Long.toHexString(first) + " main=" + (second != 0L);
            default:
                return "op=" + code + " a=" + first + " b=" + second;
        }
    }
}
