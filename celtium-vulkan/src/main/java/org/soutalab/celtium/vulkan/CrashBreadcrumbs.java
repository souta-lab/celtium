package org.soutalab.celtium.vulkan;

/**
 * The last few renderer operations before a device loss, kept as numbers so the ring costs almost nothing and can stay
 * on for every session.
 * <p>
 * A RADV GPUVM fault reports an address and nothing else; the log around it carries whatever diagnostic lines happened
 * to print, which in the measured case were a texture dump and a loading-bar quad rather than the faulting call. This
 * ring records the operations themselves - code, and up to three numbers that mean whatever the code says (a window's target
 * and group count, an upload's GL name and extent, a chain pair's identity, a window's render pass and framebuffer
 * handles) - and formats them only when the device is actually lost. That is why there is no switch to remember in the
 * launcher: three array writes per operation, four for the {@link #note3} form.
 */
public final class CrashBreadcrumbs {
    /** {@code -Dceltium.vulkan.crashBreadcrumbs=false} turns the ring off; it is on by default. */
    private static final boolean ENABLED =
            !"false".equals(System.getProperty("celtium.vulkan.crashBreadcrumbs", "true"));
    private static final int CAPACITY = 128;
    private static final int[] CODES = new int[CAPACITY];
    private static final long[] FIRST = new long[CAPACITY];
    private static final long[] SECOND = new long[CAPACITY];
    /**
     * The third number, for the codes that need one (a window's pass, the frame cache's view tuple). Only
     * {@link #note3} writes it, so a slot held by a two-number note keeps whatever the previous occupant of that
     * index left there; {@link #describe} reads it only for the codes that record one.
     */
    private static final long[] THIRD = new long[CAPACITY];
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
    /** A window's begin: the render pass, the framebuffer, and the extent packed as {@code hi=width, lo=height}. */
    public static final int WINDOW_PASS = 9;
    /** The frame-target cache answered with an entry built over the two views in the second and third numbers. */
    public static final int FRAME_CACHE_HIT = 10;
    /** The frame-target cache had no entry for these views and built one (its handle is the first number). */
    public static final int FRAME_CACHE_MISS = 11;
    /** A graphics pipeline was created: its generation number, its handle, and its render pass. */
    public static final int PIPELINE_CREATE = 12;
    /** A model display list was replayed natively: the list, the texture it samples, and its state key. */
    public static final int MODEL_REPLAY = 13;
    /** An immediate group was queued for the window: its vertex format, its pipeline, and its target. */
    public static final int IMMEDIATE_GROUP = 14;
    /** A raw (mod-created) framebuffer was registered: its identity, its colour texture, and its depth texture. */
    public static final int RAW_FBO = 15;
    /** A texture's deferred upload was submitted: the texture's GL name and the fence that stands for the submit. */
    public static final int DEFERRED_UPLOAD = 16;

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

    /**
     * Records one operation that needs three numbers. No allocation and no formatting: four array writes.
     * <p>
     * A second entry point rather than a third field on every note: the two-number notes are the ones on the hottest
     * paths (one per texture upload, one per coalesced copy) and they are most of what the ring holds.
     */
    public static void note3(int code, long first, long second, long third) {
        if (!ENABLED) {
            return;
        }
        int index = cursor % CAPACITY;
        CODES[index] = code;
        FIRST[index] = first;
        SECOND[index] = second;
        THIRD[index] = third;
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
        StringBuilder report = new StringBuilder(2048);
        report.append("Celtium device loss\n");
        report.append("trigger: ").append(trigger).append('\n');
        report.append("A device loss cannot be recovered in this process: the renderer stops issuing work and the\n");
        report.append("window keeps showing whatever was last presented (the tester's black screen). These are the\n");
        report.append("renderer operations recorded before the loss, oldest first.\n\n");
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(cursor - count + i, CAPACITY);
            String line = describe(CODES[index], FIRST[index], SECOND[index], THIRD[index]);
            System.out.println("[Celtium] CELTIUM_CRUMB #" + i + " " + line);
            report.append('#').append(i).append(' ').append(line).append('\n');
        }
        // Written beside the game (the working directory is the instance's minecraft/) so a tester's black screen comes
        // with a file to attach, instead of a log they have to search for the crumbs in.
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("celtium-device-lost.log");
            java.nio.file.Files.write(path, report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            System.out.println("[Celtium] CELTIUM_CRASH_BREADCRUMBS wrote " + path.toAbsolutePath());
        } catch (RuntimeException | java.io.IOException ignored) {
            // A report that cannot be written must not change how the loss is handled.
        }
    }

    /** Package-private for the format pin in {@code CrashBreadcrumbsTest}, like the limiter's test hooks. */
    static String describe(int code, long first, long second, long third) {
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
            case WINDOW_PASS:
                // The handles a begin is made of, so a loss inside vkCmdBeginRenderPass can say which one may have
                // been dead: the pass, the framebuffer, and the extent the render area was declared at.
                return "window-pass renderPass=0x" + Long.toHexString(first)
                        + " framebuffer=0x" + Long.toHexString(second)
                        + " extent=" + (third >>> 32) + "x" + (third & 0xffffffffL);
            case PIPELINE_CREATE:
                return "pipeline-create generation=" + first + " pipeline=0x" + Long.toHexString(second)
                        + " renderPass=0x" + Long.toHexString(third);
            case MODEL_REPLAY:
                return "model-replay list=" + first + " texture=" + second + " key=0x" + Long.toHexString(third);
            case IMMEDIATE_GROUP:
                return "immediate-group format=" + first + " pipeline=0x" + Long.toHexString(second)
                        + " target=0x" + Long.toHexString(third);
            case RAW_FBO:
                return "raw-fbo identity=0x" + Long.toHexString(first) + " color=" + second + " depth=" + third;
            case DEFERRED_UPLOAD:
                return "deferred-upload texture=" + first + " fence=0x" + Long.toHexString(second);
            case FRAME_CACHE_HIT:
            case FRAME_CACHE_MISS:
                return (code == FRAME_CACHE_HIT ? "frame-cache hit framebuffer=0x" : "frame-cache miss framebuffer=0x")
                        + Long.toHexString(first) + " over color=0x" + Long.toHexString(second)
                        + " depth=0x" + Long.toHexString(third);
            default:
                return "op=" + code + " a=" + first + " b=" + second;
        }
    }
}
