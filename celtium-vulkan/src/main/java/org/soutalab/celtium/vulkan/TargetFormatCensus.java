package org.soutalab.celtium.vulkan;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;

/**
 * The format census of D18 (QUEUE 405, the S5/S6 half left by items 394 and 404): what GL internal formats
 * the chain pairs were asked for, what class they were actually allocated in, and every place the two
 * disagree - counted, never silently dropped (QUEUE 394's standing rule for this work).
 * <p>
 * Item 404 wired the resolution itself ({@link VulkanColorFormat}, the junction in
 * {@code VulkanTerrainGpuBackend.celtium$registerRawFramebuffer}) and deliberately left the counting here:
 * its {@code forRequest} collapses every refusal - an unrecorded texture, an unmapped format, a depth
 * format - onto {@code RGBA8_UNORM}, which is the right allocation and the wrong record. This class is the
 * record. It is observation only: nothing here feeds back into the chosen class, the pair, the pass or the
 * picture, and with the census switch off the only cost is the counters, which count either way (a counter
 * that can only read zero under its own switch would invite "no fallbacks happened" from a number that was
 * never measured - the same reason {@code BackendDiagnostics} prints "off" rather than "0/0" for the
 * opt-in frame signature).
 * <p>
 * Three instruments live here:
 * <ul>
 * <li><b>S5, the decision census.</b> One {@link Resolution} per pair-class resolution that takes effect
 * (the junction's tuple actually changed and a pair was ensured), classified by {@link #decide} into
 * {@link Verdict#EXACT} / {@link Verdict#WIDENED} / {@link Verdict#FALLBACK} and counted, with the
 * fallback's reason kept apart. Under {@code -Dceltium.vulkan.targetFormatCensus=true} the first
 * {@value #MAX_LINES} decisions also print as {@code CELTIUM_TARGET_FORMAT} lines - the bound caps the
 * printing only, never the counting.</li>
 * <li><b>S5, the stub-side distribution.</b> {@code GlsmFrontend} counts every colour internal format
 * recorded into its extent table (the same switch), and {@link #summary()} carries that distribution
 * beside the decision counters. The pair of numbers is what answers "did this fixture's mods actually
 * ask for 16F": the distribution says what was requested at the GL surface, the decisions say what the
 * pairs did with it.</li>
 * <li><b>S6, the stale bit-count answer.</b> The stub answers {@code GL_RED_BITS} and its siblings with
 * the delivered frame's 8 bits whatever the current target is (see the answer's own comment in
 * {@code GlsmFrontend}); while a 16F pair is the current target that answer is stale for the target a
 * mod is actually drawing into. QUEUE 405 allows either fixing the answer or counting the mismatch;
 * this change counts it ({@link #noteBitsAnswer}), because the answer a mod probes to pick a format is
 * behaviour, and an observation item does not change behaviour. The count is
 * {@code targetFormatBitsAnsweredStale} in the summary.</li>
 * </ul>
 * The verdict vocabulary is item 394 plan's three-way split, kept because two ways would mix unlike
 * events into one counter: a request widened into a class that represents every value it asked for
 * ({@link Verdict#WIDENED} - {@code GL_RGB8} served by {@code RGBA8_UNORM}, a tuple slot widened to the
 * tuple's widest class, a forced class wider than the request) loses nothing, while a request the chosen
 * class cannot represent ({@link Verdict#FALLBACK}) is the loss the queue item exists to name.
 */
public final class TargetFormatCensus {

    /**
     * {@code -Dceltium.vulkan.targetFormatCensus=true} prints the bounded {@code CELTIUM_TARGET_FORMAT}
     * lines and turns on the stub-side request distribution. The decision and bit-answer counters count
     * regardless; only the printing and the distribution are gated, as item 394's plan fixed for the stub
     * instrument (the switch is shared, so one leg flag turns the whole census on).
     */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.targetFormatCensus");

    /** Printed {@code CELTIUM_TARGET_FORMAT} lines per process; the counters are not capped with them. */
    static final int MAX_LINES = 16;

    /** How one resolution stands against the request it served. */
    public enum Verdict {
        /** The chosen class is the class the request asked for. */
        EXACT,
        /** The chosen class represents every value the request asked for, and more (no precision lost). */
        WIDENED,
        /** The chosen class cannot represent what the request asked for; the reason says which refusal. */
        FALLBACK
    }

    /** The verdict and its reason token, as {@link #decide} returns them and the census line prints them. */
    public record Outcome(Verdict verdict, String reason) {
    }

    /**
     * One pair-class resolution, as the junction computed it (QUEUE 404's rules, unchanged): the class the
     * pair is allocated in, and the request facts the census classifies. {@code slot0Gl} is slot 0's
     * recorded GL internal format - 0 when its texture was never recorded, -1 when the forced-class path
     * took the decision without reading a request. {@code widestRequested} is the widest class any slot's
     * mapped request asked for (the default when none did); unmapped and widenable requests do not raise
     * it - they are carried by their own flags, which is what lets the census tell "asked for a class and
     * got it" apart from "asked for something no class names".
     */
    public record Resolution(
            TargetFormatClass chosen,
            int slot0Texture,
            int slot0Gl,
            TargetFormatClass widestRequested,
            boolean slotsDiffer,
            boolean anyUnmapped,
            boolean anyWidenable,
            boolean pinned,
            boolean forcedApplied,
            boolean deviceRefused) {
    }

    // The GL internal formats whose every value RGBA8_UNORM represents (item 394 plan's WIDENED rows):
    // GL_RGB/GL_RGB8 gain an alpha channel, the packed and single-channel forms are strictly narrower.
    // Values pinned against lwjgl-opengl 3.4.3 in TargetFormatMappingTest's remainder record.
    private static final int GL_RGB = 0x1907;
    private static final int GL_LUMINANCE8 = 0x8040;
    private static final int GL_ALPHA8 = 0x803C;
    private static final int GL_RGB8 = 0x8051;
    private static final int GL_RGBA4 = 0x8056;
    private static final int GL_RGB5_A1 = 0x8057;
    private static final int GL_RGB565 = 0x8D62;

    private static final AtomicLong EXACT = new AtomicLong();
    private static final AtomicLong WIDENED = new AtomicLong();
    private static final AtomicLong FALLBACKS = new AtomicLong();
    private static final AtomicLong FALLBACKS_UNMAPPED = new AtomicLong();
    private static final AtomicLong FALLBACKS_MAIN_PINNED = new AtomicLong();
    private static final AtomicLong FALLBACKS_DEVICE = new AtomicLong();
    private static final AtomicLong FALLBACKS_UNRECORDED = new AtomicLong();
    private static final AtomicLong FALLBACKS_FORCED = new AtomicLong();
    private static final AtomicLong UNKNOWN_REQUESTS = new AtomicLong();
    private static final AtomicLong TUPLE_WIDENED = new AtomicLong();
    private static final AtomicLong MAIN_PINNED = new AtomicLong();
    private static final AtomicLong DEVICE_REFUSED = new AtomicLong();
    private static final AtomicLong FORCED_DECISIONS = new AtomicLong();
    private static final AtomicLong BITS_ANSWERED_STALE = new AtomicLong();
    private static final AtomicInteger LINES = new AtomicInteger();

    private TargetFormatCensus() {
    }

    /** {@return whether {@code glInternal} is a colour format every value of which RGBA8 represents}. */
    public static boolean isWidenableToRgba8(int glInternal) {
        return switch (glInternal) {
            case GL_RGB, GL_RGB8, GL_RGB565, GL_RGBA4, GL_RGB5_A1, GL_LUMINANCE8, GL_ALPHA8 -> true;
            default -> false;
        };
    }

    /**
     * {@return whether {@code glInternal} is one of the depth internal formats} - the stub's own
     * {@code isDepthInternalFormat} set, mirrored because a depth request is not a colour request: it is
     * owned by {@code VulkanDepthFormat}'s device answer, and counting it as a colour fallback (or as an
     * unmapped colour format) would put another item's refusals in this census.
     */
    public static boolean isDepthInternalFormat(int glInternal) {
        return switch (glInternal) {
            case 0x1902, 0x81A5, 0x81A6, 0x81A7, 0x8CAC, 0x88F0, 0x8CAD, 0x84F9 -> true;
            default -> false;
        };
    }

    /**
     * {@return the bits one channel of {@code formatClass} carries} for channel 0/1/2/3 (red/green/blue/
     * alpha), or -1 for a depth class, whose channels a colour bit-count query does not describe. The
     * packed class is the honest exception: {@code R11G11B10_FLOAT} has no alpha channel at all, so its
     * alpha answer is 0, not the class's storage width.
     */
    public static int channelBits(TargetFormatClass formatClass, int channel) {
        if (formatClass == null || formatClass.isDepth()) {
            return -1;
        }
        return switch (formatClass) {
            case RGBA8_UNORM, BGRA8_UNORM -> 8;
            case RGBA16_FLOAT -> 16;
            case RGBA32_FLOAT -> 32;
            case R11G11B10_FLOAT -> channel == 3 ? 0 : (channel == 2 ? 10 : 11);
            default -> -1;
        };
    }

    /**
     * Classifies one resolution against its request. Pure, so the whole matrix is pinned without a
     * device. The order is the reading order of a refusal: an unmapped request is unmeetable by any
     * class, so it outranks the pin; the pin outranks the device check, because a pinned target never
     * reaches it with a non-default class; a forced decision is judged against the request it overrode.
     */
    public static Outcome decide(Resolution resolution) {
        TargetFormatClass requested = resolution.widestRequested();
        TargetFormatClass chosen = resolution.chosen();
        if (resolution.anyUnmapped()) {
            return new Outcome(Verdict.FALLBACK, "unmapped");
        }
        if (resolution.pinned() && requested != chosen) {
            // The pin serves the frame's own class, whatever the request asked for: under the classic
            // RGBA8 frame that class is RGBA8_UNORM (the comparison this replaces), and under a BGRA
            // frame (Stage 1 R1 revised) it is BGRA8_UNORM, which no GL request names - so on a BGRA
            // frame every pinned resolution is counted here, with the census line's chosen= naming the
            // class the pin actually served.
            return new Outcome(Verdict.FALLBACK, "main-identity-pinned");
        }
        if (resolution.deviceRefused()) {
            return new Outcome(Verdict.FALLBACK, "device-lacks-feature");
        }
        if (resolution.forcedApplied() && chosen != requested) {
            return chosen.bytesPerPixel() > requested.bytesPerPixel()
                    ? new Outcome(Verdict.WIDENED, "forced")
                    : new Outcome(Verdict.FALLBACK, "forced");
        }
        if (resolution.slot0Gl() == 0 && requested == TargetFormatClass.RGBA8_UNORM
                && !resolution.anyWidenable()) {
            // Nothing was recorded for the pair's own texture, so there is no request to have met: the
            // plan counts this with the fallbacks but under its own reason, never as an exact service.
            return new Outcome(Verdict.FALLBACK, "unrecorded");
        }
        if (resolution.anyWidenable() && requested == TargetFormatClass.RGBA8_UNORM
                && chosen == TargetFormatClass.RGBA8_UNORM) {
            return new Outcome(Verdict.WIDENED, "widened-to-rgba8");
        }
        return new Outcome(Verdict.EXACT, "none");
    }

    /** {@return whether one more census line may print}: the switch is on and the bound is not spent. */
    public static boolean wantsLine() {
        return ENABLED && LINES.get() < MAX_LINES;
    }

    /**
     * Counts one effective resolution and returns its {@code CELTIUM_TARGET_FORMAT} line when one is due,
     * or {@code null}. {@code features} is the chosen class's device feature triple
     * ({@code attach, blend, sampled} from {@link VulkanColorFormat#attachmentFeatures}), asked by the
     * caller only when a line is due; {@code null} prints {@code unknown} for the three, which is the
     * honest value for the default class - the resolution never checks it, so the census does not invent
     * a check result for it.
     */
    public static String noteResolution(long identity, Resolution resolution, boolean[] features) {
        Outcome outcome = decide(resolution);
        switch (outcome.verdict()) {
            case EXACT -> EXACT.incrementAndGet();
            case WIDENED -> WIDENED.incrementAndGet();
            case FALLBACK -> {
                FALLBACKS.incrementAndGet();
                switch (outcome.reason()) {
                    case "unmapped" -> FALLBACKS_UNMAPPED.incrementAndGet();
                    case "main-identity-pinned" -> FALLBACKS_MAIN_PINNED.incrementAndGet();
                    case "device-lacks-feature" -> FALLBACKS_DEVICE.incrementAndGet();
                    case "unrecorded" -> FALLBACKS_UNRECORDED.incrementAndGet();
                    case "forced" -> FALLBACKS_FORCED.incrementAndGet();
                    default -> {
                    }
                }
            }
        }
        if (resolution.slot0Gl() == 0) {
            UNKNOWN_REQUESTS.incrementAndGet();
        }
        if (resolution.slotsDiffer()) {
            TUPLE_WIDENED.incrementAndGet();
        }
        if (resolution.pinned() && resolution.widestRequested() != resolution.chosen()) {
            MAIN_PINNED.incrementAndGet();
        }
        if (resolution.deviceRefused()) {
            DEVICE_REFUSED.incrementAndGet();
        }
        if (resolution.forcedApplied()) {
            FORCED_DECISIONS.incrementAndGet();
        }
        if (!wantsLine()) {
            return null;
        }
        LINES.incrementAndGet();
        return line(identity, resolution, outcome, features);
    }

    /** The census line itself, separated from the counting so its shape is pinned without a device. */
    static String line(long identity, Resolution resolution, Outcome outcome, boolean[] features) {
        TargetFormatClass chosen = resolution.chosen();
        String requested = resolution.slot0Gl() < 0 ? "forced"
                : "0x" + Integer.toHexString(resolution.slot0Gl());
        String featureText = features == null ? "attach=unknown blend=unknown sample=unknown"
                : "attach=" + features[0] + " blend=" + features[1] + " sample=" + features[2];
        return "[Celtium] CELTIUM_TARGET_FORMAT identity=0x" + Long.toHexString(identity)
                + " slot=chain-pair texture=" + resolution.slot0Texture()
                + " requested=" + requested
                + " verdict=" + outcome.verdict()
                + " chosen=" + chosen
                + " vk=" + chosen.vkFormat() + "(0x" + Integer.toHexString(chosen.vkFormat()) + ")"
                + " bytesPerPixel=" + chosen.bytesPerPixel()
                + " " + featureText
                + " fallback=" + (outcome.verdict() == Verdict.FALLBACK ? outcome.reason() : "none")
                + (resolution.slotsDiffer() ? " tupleWidened=true" : "")
                + (resolution.pinned() ? " pinned=true" : "")
                + (resolution.forcedApplied() ? " forced=true" : "");
    }

    /**
     * Counts one framebuffer bit-count answer (S6): the stub answered {@code answeredBits} for
     * {@code pname} (one of {@code GL_RED_BITS} 0x0D52 .. {@code GL_ALPHA_BITS} 0x0D55) while the current
     * target's pair class is {@code currentClass}. {@return whether the answer disagrees with the class}
     * - the disagreement is counted either way the caller uses the return, and the answer itself is not
     * changed: the delivered frame's class always agrees with its 8, so the main identity never counts.
     */
    public static boolean noteBitsAnswer(int pname, int answeredBits, TargetFormatClass currentClass) {
        int channel = pname - 0x0D52;
        if (channel < 0 || channel > 3) {
            return false;
        }
        int actual = channelBits(currentClass, channel);
        if (actual < 0 || actual == answeredBits) {
            return false;
        }
        BITS_ANSWERED_STALE.incrementAndGet();
        return true;
    }

    /**
     * {@return the census as one summary segment} for {@code BackendDiagnostics}: the decision counters
     * under the names item 394's plan fixed, the S6 stale-answer count, and the stub-side request
     * distribution (the formats the GL surface was actually asked for, under the shared switch).
     */
    public static String summary() {
        return "targetFormatExact=" + EXACT.get()
                + " targetFormatWidened=" + WIDENED.get()
                + " targetFormatFallbacks=" + FALLBACKS.get()
                + "[unmapped=" + FALLBACKS_UNMAPPED.get()
                + " mainPinned=" + FALLBACKS_MAIN_PINNED.get()
                + " deviceRefused=" + FALLBACKS_DEVICE.get()
                + " unrecorded=" + FALLBACKS_UNRECORDED.get()
                + " forced=" + FALLBACKS_FORCED.get() + "]"
                + " targetFormatUnknownRequest=" + UNKNOWN_REQUESTS.get()
                + " targetFormatTupleWidened=" + TUPLE_WIDENED.get()
                + " targetFormatMainPinned=" + MAIN_PINNED.get()
                + " targetFormatDeviceRefused=" + DEVICE_REFUSED.get()
                + " targetFormatForced=" + FORCED_DECISIONS.get()
                + " targetFormatBitsAnsweredStale=" + BITS_ANSWERED_STALE.get()
                + " targetFormatRequests[" + org.soutalab.celtium.vulkan.gl.GlsmFrontend.targetFormatDistribution() + "]";
    }

    // The counters, read by the pinning tests as deltas (the census is process-wide, so a test asserts
    // what its own calls moved, never an absolute total another test could have raised).
    static long exact() {
        return EXACT.get();
    }

    static long widened() {
        return WIDENED.get();
    }

    static long fallbacks() {
        return FALLBACKS.get();
    }

    static long unknownRequests() {
        return UNKNOWN_REQUESTS.get();
    }

    static long bitsAnsweredStale() {
        return BITS_ANSWERED_STALE.get();
    }
}
