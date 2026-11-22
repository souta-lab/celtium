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
    /**
     * The fourth number, for the one code that needs it ({@link #WINDOW_PASS}'s framebuffer generation). Only
     * {@link #note4} writes it, on the same terms as {@link #THIRD}: a slot held by a note that records fewer
     * numbers keeps a stale value here, and {@link #describe} reads it only for {@link #WINDOW_PASS}.
     */
    private static final long[] FOURTH = new long[CAPACITY];
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
    /**
     * A texture's patched-levels upload was submitted: the texture's GL name, the fence, and the texture's
     * instance serial. One code per fence-assignment site (item 529, instrument I2): every staging wait is
     * charged as "texture staging reuse", so without the site the record cannot say which submit the waited-on
     * fence actually came from.
     */
    public static final int DEFERRED_UPLOAD_PATCHED_LEVELS = 17;
    /** A texture's layout transition was submitted - it reads no staging: GL name, fence, and instance serial. */
    public static final int DEFERRED_TRANSITION = 18;
    /** A texture's single-level upload was submitted: GL name, fence, and instance serial. */
    public static final int DEFERRED_UPLOAD_LEVEL = 19;
    /** A texture's level copy with its mip chain was submitted: GL name, fence, and instance serial. */
    public static final int DEFERRED_UPLOAD_IMAGE = 20;
    /**
     * A deferred batch was submitted to the queue: the context generation and the batch serial packed into the
     * first number, the batch's shared fence, and the composition packed into the third by
     * {@link #packBatchComposition} (item 529, instrument I3).
     */
    public static final int BATCH_SUBMIT = 21;
    /**
     * A descriptor set was about to be bound by the immediate texture pipeline's draw: the set handle, the
     * set's generation (the allocation serial the pipeline stamps each allocation with), and the texture key
     * the set was handed out for - the key itself is view ^ texture generation, so the record names both the
     * set's lifetime and the texture generation it was written for (hs_err triage, pid14920: the faulting call
     * was vkCmdBindDescriptorSets, and the draw's only guards were "set is not 0" and "pipeline is not null" -
     * nothing recorded which generation of the set was bound).
     */
    public static final int DESCRIPTOR_BIND = 22;
    /**
     * A flush of pending immediate groups actually ran: which entrance opened it, how many groups it held,
     * and the target identity current at the flush. The entrance kinds are {@link #FLUSH_ENTRY_OTHER},
     * {@link #FLUSH_ENTRY_VANILLA_BIND} and {@link #FLUSH_ENTRY_RAW_REGISTER} - the hs_err cluster's two
     * measured entrances are a vanilla Framebuffer bind (TheBetweenLands' GeometryBuffer creating its FBO
     * mid-render) and a raw framebuffer registration (Xaero's ImprovedFramebuffer), and the per-site census
     * the backend already keeps is a run total, not a record next to the window that faulted.
     */
    public static final int FLUSH_ENTRY = 23;

    /** Flush entrance: a caller that is neither of the two named bind entrances. */
    public static final int FLUSH_ENTRY_OTHER = 0;
    /** Flush entrance: {@code bindFramebuffer} on the ordinary (vanilla / game) bind route. */
    public static final int FLUSH_ENTRY_VANILLA_BIND = 1;
    /** Flush entrance: a bind that arrived through {@code celtium$registerRawFramebuffer}. */
    public static final int FLUSH_ENTRY_RAW_REGISTER = 2;

    /**
     * Packs a batch's composition for a {@link #BATCH_SUBMIT} note: the entry total, the frame-slot entries and
     * the semaphore-carrying entries, 21 bits each - a batch is bounded by what one flush gathers, far below
     * the field width.
     */
    public static long packBatchComposition(int total, int frameEntries, int semaphoreEntries) {
        return (total & 0x1fffffL) | ((frameEntries & 0x1fffffL) << 21) | ((semaphoreEntries & 0x1fffffL) << 42);
    }

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
        if (code == WINDOW_PASS) {
            celtium$writeThroughWindowPass(first, second, third, 0L);
        }
    }

    /**
     * Records one operation that needs four numbers. Only {@link #WINDOW_PASS} uses it: the begin's three
     * numbers plus the generation of the framebuffer being begun - the serial {@code FramebufferCache}
     * stamped the handle with at creation (0 when the handle did not come from that cache). A handle value
     * alone cannot say whether the begin named the live framebuffer or a destroyed one whose value the
     * driver had already recycled; the generation can, which is the hs_err triage's instrument (i).
     */
    public static void note4(int code, long first, long second, long third, long fourth) {
        if (!ENABLED) {
            return;
        }
        int index = cursor % CAPACITY;
        CODES[index] = code;
        FIRST[index] = first;
        SECOND[index] = second;
        THIRD[index] = third;
        FOURTH[index] = fourth;
        cursor++;
        if (code == WINDOW_PASS) {
            celtium$writeThroughWindowPass(first, second, third, fourth);
        }
    }

    // ---- the window-pass write-through (item 536) ----------------------------------------------------------
    //
    // The ring above is only ever read at a device loss, because dump() has no other caller. A native access
    // violation inside vkCmdBeginRenderPass - the item 536 signature, a guard-page fault in nvoglv64 - kills the
    // process before any loss is declared, so the ring's record of the fatal window's begin arguments (the pass,
    // the framebuffer, the extent) dies with it and the crash cannot be told apart from a driver-side fault from
    // the reporter's files. The latest window-pass tuple is therefore also written through to a one-line file
    // beside the game, before the begin it describes is recorded: after an AV the file holds the arguments of the
    // window that was being begun, to be read next to the hs_err.
    //
    // A window is begun dozens of times a frame, so the file is not rewritten for every one: it is rewritten when
    // the tuple changes, and otherwise at most once per interval as a heartbeat carrying the current sequence
    // number. A crash inside a window whose tuple repeats an earlier one still finds that tuple in the file -
    // the values are the fatal window's own - and only the sequence number can lag, by less than the interval.

    /** How long an unchanged window-pass tuple may go unrewritten before the heartbeat refresh (nanoseconds). */
    private static final long WINDOW_PASS_WRITE_INTERVAL_NANOS = 250L * 1000L * 1000L;
    private static long windowPassSeq;
    private static boolean windowPassLineWritten;
    private static long windowPassWrittenFirst;
    private static long windowPassWrittenSecond;
    private static long windowPassWrittenThird;
    private static long windowPassWrittenFourth;
    private static long windowPassLastWriteNanos;
    /** Test hook: where the write-through line goes instead of beside the game. */
    static volatile java.nio.file.Path windowPassFileForTest;

    private static void celtium$writeThroughWindowPass(long renderPass, long framebuffer, long extentPacked,
            long framebufferGeneration) {
        windowPassSeq++;
        // The generation is part of the tuple: a framebuffer destroyed and recreated at the same handle value
        // and extent is a different window for this file's purpose - it is exactly the stale-handle case the
        // generation was added to identify - so it must rewrite the line, not ride the heartbeat interval.
        boolean changed = !windowPassLineWritten
                || renderPass != windowPassWrittenFirst
                || framebuffer != windowPassWrittenSecond
                || extentPacked != windowPassWrittenThird
                || framebufferGeneration != windowPassWrittenFourth;
        long now = System.nanoTime();
        if (!changed && now - windowPassLastWriteNanos < WINDOW_PASS_WRITE_INTERVAL_NANOS) {
            return;
        }
        windowPassLineWritten = true;
        windowPassWrittenFirst = renderPass;
        windowPassWrittenSecond = framebuffer;
        windowPassWrittenThird = extentPacked;
        windowPassWrittenFourth = framebufferGeneration;
        windowPassLastWriteNanos = now;
        try {
            java.nio.file.Path path = windowPassFileForTest != null
                    ? windowPassFileForTest
                    : java.nio.file.Paths.get("celtium-last-window-pass.log");
            java.nio.file.Files.write(path, windowPassLine(windowPassSeq, renderPass, framebuffer, extentPacked,
                    framebufferGeneration).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (RuntimeException | java.io.IOException ignored) {
            // A diagnostic that cannot be written must not change how the pass is recorded.
        }
    }

    /** Package-private for the format pin in {@code CrashBreadcrumbsTest}, like {@link #describe}. */
    static String windowPassLine(long seq, long renderPass, long framebuffer, long extentPacked) {
        return "celtium last window-pass seq=" + seq
                + " renderPass=0x" + Long.toHexString(renderPass)
                + " framebuffer=0x" + Long.toHexString(framebuffer)
                + " extent=" + (extentPacked >>> 32) + "x" + (extentPacked & 0xffffffffL) + "\n";
    }

    /**
     * The write-through line with the framebuffer's generation appended (hs_err triage instrument (i)).
     * A separate overload rather than a changed format: the four-number line above is pinned by its test
     * and may already sit in reporters' folders; a line carrying a generation says so by having the field.
     */
    static String windowPassLine(long seq, long renderPass, long framebuffer, long extentPacked,
            long framebufferGeneration) {
        return "celtium last window-pass seq=" + seq
                + " renderPass=0x" + Long.toHexString(renderPass)
                + " framebuffer=0x" + Long.toHexString(framebuffer)
                + " extent=" + (extentPacked >>> 32) + "x" + (extentPacked & 0xffffffffL)
                + " fbGen=" + framebufferGeneration + "\n";
    }

    /** Prints the ring once, oldest first, naming each operation; the first device loss is the one that matters. */
    public static void dump(String trigger) {
        dump(trigger, null);
    }

    /**
     * The same dump with a context section the caller composes (item 529, instruments I6/I7): the losing
     * context's generation and whatever state lines it hands over, printed ahead of the operations so the ring
     * is read against them. This class still knows no context - it prints the lines it is given, verbatim.
     */
    public static void dump(String trigger, String contextReport) {
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
        if (contextReport != null) {
            for (String line : contextReport.split("\n")) {
                System.out.println("[Celtium] CELTIUM_CRASH_CONTEXT " + line);
            }
            report.append("Context at the loss:\n").append(contextReport).append("\n\n");
        }
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(cursor - count + i, CAPACITY);
            String line = describe(CODES[index], FIRST[index], SECOND[index], THIRD[index], FOURTH[index]);
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
        return describe(code, first, second, third, 0L);
    }

    /** The five-number form: only {@link #WINDOW_PASS} reads the fourth number (its framebuffer generation). */
    static String describe(int code, long first, long second, long third, long fourth) {
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
                // been dead: the pass, the framebuffer, and the extent the render area was declared at. The
                // generation says which creation of that framebuffer handle this was (0 = not a handle the
                // FramebufferCache stamped), so a recycled handle value is not mistaken for the live one.
                return "window-pass renderPass=0x" + Long.toHexString(first)
                        + " framebuffer=0x" + Long.toHexString(second)
                        + " extent=" + (third >>> 32) + "x" + (third & 0xffffffffL)
                        + " fbGen=" + fourth;
            case DESCRIPTOR_BIND:
                return "descriptor-bind set=0x" + Long.toHexString(first)
                        + " setGen=" + second + " textureKey=0x" + Long.toHexString(third);
            case FLUSH_ENTRY:
                return "flush-entry kind=" + flushEntryKindName(first)
                        + " groups=" + second + " target=0x" + Long.toHexString(third);
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
                return "deferred-upload texture=" + first + " fence=0x" + Long.toHexString(second)
                        + " serial=" + third;
            case DEFERRED_UPLOAD_PATCHED_LEVELS:
                return "deferred-upload-patched-levels texture=" + first + " fence=0x" + Long.toHexString(second)
                        + " serial=" + third;
            case DEFERRED_TRANSITION:
                return "deferred-transition texture=" + first + " fence=0x" + Long.toHexString(second)
                        + " serial=" + third;
            case DEFERRED_UPLOAD_LEVEL:
                return "deferred-upload-level texture=" + first + " fence=0x" + Long.toHexString(second)
                        + " serial=" + third;
            case DEFERRED_UPLOAD_IMAGE:
                return "deferred-upload-image texture=" + first + " fence=0x" + Long.toHexString(second)
                        + " serial=" + third;
            case BATCH_SUBMIT: {
                long total = third & 0x1fffffL;
                long frameEntries = (third >>> 21) & 0x1fffffL;
                long semaphoreEntries = (third >>> 42) & 0x1fffffL;
                return "batch-submit gen=" + (first >>> 32) + " batch=" + (first & 0xffffffffL)
                        + " fence=0x" + Long.toHexString(second)
                        + " entries=" + total + " frame=" + frameEntries + " deferred=" + (total - frameEntries)
                        + " semaphoreSets=" + semaphoreEntries;
            }
            case FRAME_CACHE_HIT:
            case FRAME_CACHE_MISS:
                return (code == FRAME_CACHE_HIT ? "frame-cache hit framebuffer=0x" : "frame-cache miss framebuffer=0x")
                        + Long.toHexString(first) + " over color=0x" + Long.toHexString(second)
                        + " depth=0x" + Long.toHexString(third);
            default:
                return "op=" + code + " a=" + first + " b=" + second;
        }
    }

    /** Names a {@link #FLUSH_ENTRY} kind for the dump; an unknown kind prints as its number. */
    private static String flushEntryKindName(long kind) {
        if (kind == FLUSH_ENTRY_VANILLA_BIND) {
            return "vanilla-bind";
        }
        if (kind == FLUSH_ENTRY_RAW_REGISTER) {
            return "raw-register";
        }
        if (kind == FLUSH_ENTRY_OTHER) {
            return "other";
        }
        return "kind-" + kind;
    }
}
