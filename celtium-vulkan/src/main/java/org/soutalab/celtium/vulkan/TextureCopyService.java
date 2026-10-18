package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.nio.LongBuffer;

/**
 * The texture-copy path: {@code glCopyTexImage2D}/{@code glCopyTexSubImage2D} served from an image this renderer owns.
 * <p>
 * Extracted from {@code VulkanTerrainGpuBackend} (task-63) so this file can be owned on its own. The backend keeps
 * thin delegates with the same signatures, which is what lets every caller - the GL-less stub above all - stay
 * unchanged, and everything this service needs from the backend arrives through {@link Host}. The host surface is
 * deliberately read-only plus four operations: the pending-group flush, the idle wait, the diagnostics sink and the
 * registry. Nothing here reaches into the backend's own state.
 *
 * <p>Threading: render thread only, like the backend it serves.
 */
public final class TextureCopyService {

    /**
     * The backend surface this service is allowed to touch.
     * <p>
     * One method per fact the copy path reads, named after the backend field it exposes, so the extraction can be
     * checked by reading this interface rather than by diffing two thousand lines.
     * <p>
     * <b>This is the temporary boundary the refactoring plan records (R1a / §5.5), and the fields it exposes are the
     * list of what has to be narrowed.</b> The follow-up replaces them with a {@code ReadSource} - an image/view, its
     * extent and layout, a generation and a content version - plus the explicit ordering operations:
     * <ul>
     *   <li>{@code context} stays (the device and the command buffer are the service's own execution surface).</li>
     *   <li>{@code frameTarget} / {@code offTargetTarget} / {@code targetWidth} / {@code targetHeight} become the
     *       {@code ReadSource}'s image and extent, resolved by the host rather than by four separate reads.</li>
     *   <li>{@code mainTargetIdentity} / {@code framebufferColorTextureName} / {@code currentRenderTarget} /
     *       {@code currentRenderTargetIsMain} / {@code lastDrawnOffTargetIdentity} / {@code lastDrawnOffTargetFrame}
     *       are the routing facts behind {@code ownedReadImage}'s resolution; they belong to the read-source
     *       resolution and disappear from this surface when that moves.</li>
     *   <li>{@code frameId} / {@code lastDrawnImage} / {@code lastDrawnFrame} / {@code lastDrawnEpoch} /
     *       {@code passAttachedColorImage} / {@code passSubmissions} / {@code immediateWindowsThisFrame} /
     *       {@code immediateWindowBusy} / {@code immediateGroupCount} / {@code recordedImmediateGroupCount} /
     *       {@code currentGroupBatches} are diagnostics and ordering facts. The plan's own correction applies here:
     *       the same-copy reuse is decided by frame id and rectangle today and must move to the producer's content
     *       version (OPT-26/27), at which point the frame-id reads go away.</li>
     *   <li>{@code registry} stays (the destination mirror's patch path is this service's sink).</li>
     *   <li>{@code flushPendingBeforeGlDraw} / {@code waitForIdle} are the ordering contract and stay explicit.</li>
     *   <li>{@code record} / {@code describe} are the diagnostics sink.</li>
     * </ul>
     */
    public interface Host {
        VulkanContext context();

        FrameImageTarget frameTarget();

        /** {@return the GL-less frame target, or {@code null} when the target is not one}. */
        GllessFrameTarget offTargetTarget();

        int targetWidth();

        int targetHeight();

        long frameId();

        /**
         * {@return the serial of the presented frame}, which is what "this frame" means for the copy reuse set below.
         * <p>
         * Deliberately not {@link #frameId()}: that counter advances on the world render path, and a GUI screen skips
         * it, so during the main menu it stands still. The reuse set is "one readback per frame per destination
         * rectangle", and keying it on a counter that does not move for a screen turns that into "one readback per
         * process". Measured (round 166, the menu): the game's per-frame {@code glCopyTexSubImage2D} of the 256x256
         * panorama viewport was answered from the frame-0 copy, so the panorama never moved even though
         * {@code panoramaTimer} advanced and {@code GuiMainMenu.java:417} rotated it every frame. A screen presents a
         * frame too, so the present serial is the counter that means "this frame" for both.
         */
        long presentSerial();

        long mainTargetIdentity();

        /** {@return the GL colour-texture name recorded for one framebuffer identity}, 0 when none. */
        int framebufferColorTextureName(long identity);

        long currentRenderTarget();

        boolean currentRenderTargetIsMain();

        long lastDrawnOffTargetIdentity();

        long lastDrawnOffTargetFrame();

        /**
         * {@return the present that last accepted a batch for {@link #lastDrawnOffTargetIdentity}}.
         * <p>
         * The read resolution's last resort asks "was this target drawn in the frame the copy is about", and for a
         * screen that is the present, not {@link #frameId()} (which a screen does not advance - see
         * {@link #presentSerial()}). Comparing a present against a frame id would never match, so the backend records
         * both and this is the one the decision uses.
         */
        long lastDrawnOffTargetPresent();

        long lastDrawnImage();

        long lastDrawnFrame();

        long lastDrawnEpoch();

        long passAttachedColorImage();

        int passSubmissions();

        int immediateWindowsThisFrame();

        boolean immediateWindowBusy();

        int immediateGroupCount();

        /**
         * {@return whether any immediate work is pending, including the group still being filled}.
         * <p>
         * {@link #immediateGroupCount()} counts only the groups already queued for a window. The main menu's panorama
         * offers its cube quads and then issues the copy that reads them back in the same breath
         * ({@code GuiMainMenu.renderSkybox -> drawPanorama -> rotateAndBlurSkybox}), so at the copy those quads are
         * still in the group being filled - a guard that asked only the queued count skipped the flush entirely and the
         * copy read the image *before* the draws it is a copy of. Measured (round 215, run r212-panhost-steady2): the
         * copy's rectangle was the menu's grey gradient at every present (bands 226..253, opaque=0) and never the cube.
         */
        boolean hasPendingImmediateWork();

        int recordedImmediateGroupCount();

        int currentGroupBatches();

        VulkanTextureRegistry registry();

        /** Submits the frame's queued groups: the copy must read what the game has already drawn. */
        void flushPendingBeforeGlDraw();

        /** {@return whether the GPU confirmed every submission}; a false is a refusal, never a fallback. */
        boolean waitForIdle();

        /**
         * {@return the framebuffer identity whose bind reported this colour texture}, or {@code Long#MIN_VALUE}.
         * The {@code glReadPixels} path resolves a bound read framebuffer by its GL name: the stub records which
         * colour texture that name carries (raw GL and vanilla binds both), and the identity that texture was
         * reported under is the one whose pair the read's pixels live in.
         */
        long identityForColorTexture(int glTextureName);

        /**
         * {@return the framebuffer identity reported under this GL framebuffer-object name}, or
         * {@code Long#MIN_VALUE}. The mixin sees both at bind time; the stub only has the name, so a readback bound to
         * a vanilla framebuffer - which may never have called {@code glFramebufferTexture2D} through the stub, the
         * main framebuffer being the measured case - resolves through this map instead of the colour-texture chain.
         */
        long identityForFramebufferName(int framebufferObject);

        void record(String diagnostic);

        /** {@return one line describing a throwable}, the same text the backend's own diagnostics use. */
        String describe(Throwable error);
    }

    /**
     * {@code -Dceltium.vulkan.nativeTextureCopy=false} forces the host route (flush, device wait, readback, upload),
     * which is the measurement control for this unit: the same jar then has both paths and the counters say which one
     * ran. Default on - the native route is what the menu's frame should pay.
     */
    private static final boolean NATIVE_TEXTURE_COPY = !"false".equalsIgnoreCase(
        System.getProperty("celtium.vulkan.nativeTextureCopy", "true"));
    /**
     * Whether a copy whose source is the window may take the blit route, which cannot force the alpha GL reads back.
     *
     * <p>Measured on the title screen (leg gv-ab-menu-new, jar fba1c9f2): the panorama's copies read the window, every
     * one of them declined the blit for the alpha reason, and the host route cost
     * {@code textureCopyReadbacks=324} and {@code textureCopyReadbackMillis=7440} in one 200-tick menu leg - about
     * 23 ms per copy, which is the whole frame, because the arm flushes and waits for the device. That is the
     * user-reported "the panorama is very laggy", and it is a stall rather than work: the same pixels are already on
     * the GPU.
     *
     * <p>On by default: a window-source copy used to need the host route because a blit carries alpha verbatim while
     * the window's GL alpha must read 1. The capture now masks alpha writes on the delivered frame (the GL default
     * framebuffer has no alpha bits), so the image's alpha is the acquire's clear - 1 - and the blit's verbatim copy
     * is the answer GL owes. {@code -Dceltium.vulkan.windowCopyBlit=false} keeps the host route for comparison.
     */
    private static final boolean WINDOW_COPY_BLIT = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.windowCopyBlit", "true"));

    /** Bounded lines for the copy's read resolution (see {@link #ownedReadImage}). */
    private static final int READ_RESOLVE_LOG_LIMIT =
            Integer.getInteger("celtium.vulkan.readResolveLog", 6).intValue();

    private final Host host;

    /** Copy requests served from an owned image, and those refused, with the reason of the last refusal. */
    private int textureCopiesServed;
    private int textureCopiesRefused;
    /** How many served copies have printed their rectangle's statistics (bounded, for the picture question). */
    private int textureCopySamples;
    /**
     * The present the served-copy set belongs to, and the destination rectangles already served in it.
     * <p>
     * The menu asks for the same rectangle seven times per frame; one readback answers all seven, and this set is what
     * makes that a decision rather than a repeated cost. Cleared whenever the present changes - deliberately not
     * {@code frameId}, which advances on the world render path and therefore does not move while a screen is up: keyed
     * on it, this set was never cleared during the main menu, and the game's per-frame 256x256 panorama copy was
     * answered from the first frame's content, so the panorama did not move while {@code panoramaTimer} advanced.
     */
    private long textureCopyPresent = -1L;
    /** The copies served this present, keyed by the exact request, against the content marker they were served at. */
    private final java.util.Map<String, Long> textureCopiesServedThisFrame = new java.util.HashMap<String, Long>();
    /** Copies served for one present, and the most any single present has served (the panorama's seven passes). */
    private int textureCopiesServedThisPresent;
    private int textureCopiesServedLastPresent;
    private int textureCopiesServedPeakPerPresent;
    private int textureCopiesReServedAfterContentChange;
    private int textureCopiesReusedInFrame;
    /** Readback submissions the copy path actually made, and the wall time they took. */
    private int textureCopyReadbacks;
    private long textureCopyReadbackNanos;
    /**
     * How many pixels of the copies that read the window got their alpha forced to opaque, and how many such copies
     * there were. The GL rule and the measurement that names it are at the forcing site in {@code copyTexture}: the
     * default framebuffer has no alpha bits, so the emulation owes the game an alpha read of 1. Counted rather than
     * assumed - a leg whose menu picture is still flat and whose count is 0 has a different fault.
     */
    private long textureCopyWindowAlphaForced;
    private int textureCopyWindowAlphaCopies;
    /** Copies served in the present the copy is in, so a probe can name "the last one of a frame" and "the first". */
    private int copiesThisPresent;
    /**
     * Every call into the copy in this present, including the ones the cache answers without a readback.
     * <p>
     * It exists because the question "what does the cube's face test its depth against" cannot be asked at the
     * readback: the readback happens once per frame, right after the cube, and the blur's own depth write lands after
     * it. The last call of a frame is the moment the next frame's cube will inherit, so the two ends of one frame
     * have to be read separately - a single sample at the readback cannot tell "the depth is 1.0 when the cube draws"
     * from "the depth is 1.0 after the cube and 0.75 before the next one".
     */
    private int copyCallsThisPresent;

    /** How many native blits were declined because the window's alpha rule cannot be expressed by a blit. */
    private int textureCopyWindowAlphaBlitsDeclined;
    private String lastTextureCopyRefusal = "none";
    private int readResolveLogs;
    /** Copies that took the native GPU blit route, and those the blit refused, with the last reason. */
    private int textureCopyBlits;
    private int textureCopyBlitRefusals;
    private String lastTextureCopyBlitRefusal = "none";
    /** Reads that resolved through the frame's last non-main draw target rather than the bound target (panorama). */
    private int readResolvedFromLastDrawnTarget;
    private int copyReadLogs;
    private int copyCallerLogs;

    public TextureCopyService(Host host) {
        if (host == null) {
            throw new IllegalArgumentException("host must be set");
        }
        this.host = host;
    }

    /** {@return copies served}, for the backend's summary line (the field itself stays here). */
    public int textureCopiesServed() {
        return this.textureCopiesServed;
    }

    public int textureCopiesRefused() {
        return this.textureCopiesRefused;
    }

    public int textureCopiesReusedInFrame() {
        return this.textureCopiesReusedInFrame;
    }

    public int textureCopyReadbacks() {
        return this.textureCopyReadbacks;
    }

    public long textureCopyReadbackNanos() {
        return this.textureCopyReadbackNanos;
    }

    public String lastTextureCopyRefusal() {
        return this.lastTextureCopyRefusal;
    }

    public int readResolvedFromLastDrawnTarget() {
        return this.readResolvedFromLastDrawnTarget;
    }

    /**
     * {@return the most copies any one present has served}, which is the number the panorama's seven
     * {@code rotateAndBlurSkybox} passes make: one per pass, not one per frame. A cache that answers them all from the
     * first copy shows 1 here, which is what the menu's flat picture was.
     */
    public int textureCopiesServedPeakPerPresent() {
        return Math.max(this.textureCopiesServedPeakPerPresent, this.textureCopiesServedLastPresent);
    }

    /** {@return copies re-served because the same request's source content had changed since the cached one}. */
    public int textureCopiesReServedAfterContentChange() {
        return this.textureCopiesReServedAfterContentChange;
    }

    /** {@return copies the GPU wrote into the mirror directly}. */
    public int textureCopyBlits() {
        return this.textureCopyBlits;
    }

    /** {@return copies the native route refused, which then took the host route}. */
    public int textureCopyBlitRefusals() {
        return this.textureCopyBlitRefusals;
    }

    public String lastTextureCopyBlitRefusal() {
        return this.lastTextureCopyBlitRefusal;
    }

    /**
     * Serves one {@code glCopyTexImage2D}/{@code glCopyTexSubImage2D} from an image this renderer owns.
     * <p>
     * The caller is the GL-less stub, which knows the destination texture name, the level and offset the copy names,
     * and the source rectangle. The pixels come from the same route the presented-image dump uses (a transfer-source
     * copy into a host-visible buffer), but of a rectangle of the owned image rather than of the whole swapchain image
     * at the swap point.
     * <p>
     * The copy is synchronous on purpose. The game draws the faces, copies them and then draws the blur from the copy
     * in the same frame: a read that returned the previous frame's content would put the blur on the wrong pixels, and
     * the ordering is the game's, not the renderer's to defer. So the pending groups are flushed (which submits and
     * waits - the same immediate-window path an ordering flush uses) before the rectangle is read.
     * <p>
     * One readback per frame per destination rectangle. The menu asks for the same 256x256 region seven times a frame
     * (GuiMainMenu.rotateAndBlurSkybox, each pass followed by three blur quads), and seven queue waits per frame is a
     * menu that stutters: measured as the cost this method reports. The later requests in the same frame are answered
     * from the content the first one read, which is a stated simplification - vanilla's passes each see the blur the
     * pass before them drew - and the counter {@code textureCopiesReusedInFrame} says how many were answered that way,
     * so the difference is visible rather than implied.
     *
     * @param destName the GL texture name the copy writes into
     * @param level    the destination level; only level 0 is served, because the mirror's patch path is level 0
     * @param xoffset  the destination offset inside that level
     * @param yoffset  the destination offset inside that level
     * @param x        the source rectangle's left edge in the read framebuffer
     * @param y        the source rectangle's bottom edge in the read framebuffer (GL's origin)
     * @param width    the source rectangle's width, also the copied width
     * @param height   the source rectangle's height, also the copied height
     * @param readFbo  the framebuffer the game bound for reading
     * @return {@code "served"} when the pixels reached the destination mirror, otherwise a reason that names itself
     */
    public String serveTextureCopy(int destName, int level, int xoffset, int yoffset,
            int x, int y, int width, int height, int readFbo) {
        if (destName == 0 || width <= 0 || height <= 0) {
            return refuseTextureCopy("invalid arguments: dest=" + destName + " " + width + "x" + height);
        }
        // Which caller issues the copy: the panorama's is GuiMainMenu.renderSkybox (after its 64 cubes are drawn), and
        // a splash/loading copy would come before every draw - measured, the copy served at frame 0 runs with
        // lastDrawnFrame=-1, so the two cannot be told apart by the counters alone. Bounded, the BH-06 pattern.
        if (this.copyCallerLogs < 4) {
            this.copyCallerLogs++;
            StackTraceElement[] frames = Thread.currentThread().getStackTrace();
            StringBuilder caller = new StringBuilder();
            for (int i = 2; i < Math.min(frames.length, 16); i++) {
                caller.append(frames[i].getClassName().replaceAll(".*\\.", "")).append('.')
                        .append(frames[i].getMethodName()).append(':').append(frames[i].getLineNumber()).append(" <- ");
            }
            System.out.println("[Celtium] CELTIUM_COPY_CALLER dest=" + destName + " " + width + "x" + height
                    + " frame=" + this.host.frameId()
                    // What the flush would see, so which of its two early returns fired is a fact: an empty queue
                    // means the cubes' offers arrive after this copy, a busy window means it must wait instead.
                    + " windowBusy=" + this.host.immediateWindowBusy()
                    + " groups=" + this.host.immediateGroupCount()
                    + " recorded=" + this.host.recordedImmediateGroupCount()
                    + " groupBatches=" + this.host.currentGroupBatches()
                    + " callers=[" + caller + "]");
        }
        if (level != 0) {
            // The mirror's patch path carries level 0 only (announceFullPixels walks data[level] from 0 and stops at the
            // first absent level), so a copy into a higher level is named rather than written to level 0 as if it were
            // the same image. No measured caller does this: the panorama copies into level 0.
            return refuseTextureCopy("level " + level + " (the mirror's patch path serves level 0)");
        }
        long image = ownedReadImage(readFbo);
        if (image == 0L) {
            return refuseTextureCopy("read framebuffer " + readFbo + " is not an image this renderer owns");
        }
        // The source rectangle is checked against the image it will be read from, because the mapping below depends on
        // the image's height: GL's copy y counts from the bottom of the framebuffer while a Vulkan image's rows count
        // from the top. An out-of-range rectangle is refused by name rather than clamped, since a clamped read is a
        // silently different picture. Every image `celtium$ownedReadImage` returns - the frame image, the chain pair
        // and the default pair - is the target's size, which is what `targetWidth`/`targetHeight` carry.
        int sourceImageHeight = this.host.targetHeight();
        if (!rectangleFits(x, y, width, height, this.host.targetWidth(), sourceImageHeight)) {
            return refuseTextureCopy("the source rectangle " + x + "," + y + " " + width + "x" + height
                    + " is not inside the " + this.host.targetWidth() + "x" + sourceImageHeight + " image");
        }
        // The frame's first request for this destination rectangle pays for the readback; the rest are answered from it.
        // B08: the key is the exact tuple, as a string, because the bit-mix this used to be collided across different
        // rectangles (each field was shifted into a range narrower than the value it carries, so two distinct copies
        // could share one key and one of them was silently served from the other's pixels). It includes the source
        // image, which the mix did not name at all.
        String copyKey = destName + ":" + level + ":" + xoffset + ":" + yoffset + ":" + x + ":" + y + ":" + width + ":"
                + height + ":0x" + Long.toHexString(image);
        // ...and the entry remembers the content marker it was served at, because "the same request" is not "the same
        // pixels": the panorama asks for the same 256x256 seven times per frame and draws a blurred quad between them
        // (GuiMainMenu.rotateAndBlurSkybox), so each pass's source content differs from the last one's. A copy whose
        // source has changed since the cached one is never elided - the marker is the last draw's epoch, which moves
        // exactly when a draw has landed in the image since.
        // The marker carries the *window* count as well as the draw epoch, and that is the difference between one
        // copy per frame and vanilla's seven. `lastDrawnEpoch` moves when a draw lands in an image the terrain path
        // owns; the panorama's blur quads are immediate-window draws, so between two iterations of
        // `GuiMainMenu.rotateAndBlurSkybox` the epoch did not move and six of the seven requests were answered from
        // the cache with the *first* iteration's pixels - measured on the main menu: `textureCopyReadbacks=216` over
        // 216 presents with `textureCopiesServed=1512`, i.e. one readback per frame where vanilla re-copies after
        // every blur pass and so accumulates seven. Vanilla's background is the accumulation; a single pass leaves it
        // sharp. The window counter is a superset of "the content may have changed", so it can only cause extra
        // copies, never a stale one - the cost is six more readbacks per menu frame on the host path, and the
        // native-copy path is where that cost belongs.
        long copyMarker = (this.host.lastDrawnEpoch() * 1000003L + this.host.lastDrawnFrame()) * 131071L
                + this.host.immediateWindowsThisFrame();
        // The serial is the present's, not frameId's: a screen presents without advancing frameId, and keying on frameId
        // made this set never clear during the menu, so the panorama's per-frame copy was served from the first frame's
        // content (round 166 - the reason the main menu's panorama did not move).
        if (this.textureCopyPresent != this.host.presentSerial()) {
            this.textureCopyPresent = this.host.presentSerial();
            this.copiesThisPresent = 0;
            this.copyCallsThisPresent = 0;
            this.textureCopiesServedThisFrame.clear();
            this.textureCopiesServedThisPresent = 0;
            if (this.textureCopiesServedLastPresent > this.textureCopiesServedPeakPerPresent) {
                this.textureCopiesServedPeakPerPresent = this.textureCopiesServedLastPresent;
            }
            this.textureCopiesServedLastPresent = 0;
        }
        Long servedAt = this.textureCopiesServedThisFrame.get(copyKey);
        if (servedAt != null && servedAt.longValue() == copyMarker) {
            this.textureCopiesReusedInFrame++;
            this.textureCopiesServed++;
            this.textureCopiesServedLastPresent++;
            return "served";
        }
        if (servedAt != null) {
            // The request is the same and the content is not: counted, so "the cache elided a copy that had to happen"
            // is a number rather than an inference from a picture.
            this.textureCopiesReServedAfterContentChange++;
        }
        // ---- the native route: the GPU writes the mirror, so the render thread neither waits for the source's draws
        // nor round-trips the pixels through host memory. Everything checkable was checked before this point, and the
        // blit's own refusal names itself; the host route below stays reachable and the counters say which ran.
        if (NATIVE_TEXTURE_COPY) {
            String blitRefusal = this.host.registry().blitRefusal(destName, image, this.host.targetWidth(),
                    this.host.targetHeight(), x, y, width, height, xoffset, yoffset);
            if (blitRefusal == null && !WINDOW_COPY_BLIT && windowAlphaSource(readFbo, image)) {
                // The host route is the comparison arm only: with alpha writes masked on the delivered frame the
                // blit's verbatim copy already carries the alpha-1 the window's GL read owes. The switch stays so a
                // leg can still measure the two routes against each other.
                this.textureCopyWindowAlphaBlitsDeclined++;
                blitRefusal = "the source is the window and windowCopyBlit is off (A/B arm)";
            }
            if (blitRefusal == null) {
                // The faces' window first, and no wait: the queue runs the blit after the draws that filled the
                // source and before the draws that sample the mirror, which is the ordering the host path got from
                // waiting for the device to go idle.
                if (this.host.hasPendingImmediateWork()) {
                    this.host.flushPendingBeforeGlDraw();
                }
                VkCommandBuffer commands = this.host.context().beginCommands();
                try {
                    String recorded = this.host.registry().blitInto(destName, commands, image,
                            this.host.targetWidth(), this.host.targetHeight(), x, y, width, height, xoffset, yoffset);
                    if (recorded == null) {
                        this.host.context().celtium$submitDeferred(commands, 0L);
                        commands = null; // submitted: the context owns it and frees it at its fence
                        this.textureCopyBlits++;
                        this.textureCopiesServedThisFrame.put(copyKey, Long.valueOf(copyMarker));
                        this.textureCopiesServed++;
                        this.textureCopiesServedLastPresent++;
                        return "served";
                    }
                    blitRefusal = recorded;
                } catch (RuntimeException | LinkageError error) {
                    blitRefusal = "the native copy threw: " + error;
                } finally {
                    if (commands != null) {
                        this.host.context().celtium$abandonCommands(commands);
                    }
                }
            }
            this.textureCopyBlitRefusals++;
            this.lastTextureCopyBlitRefusal = blitRefusal;
        }
        // ---- the host route: flush, wait for the device, read the rectangle back and upload it. Kept reachable (a
        // device without the blit features, a mirror that does not exist yet, the control flag) and counted.
        // What the game drew into that image is still queued as groups: submitting them is what makes the read the
        // frame the copy is about. flushPendingBeforeGlDraw draws into an open pass if there is one, otherwise it opens
        // and completes an immediate window, which is the same ordering boundary the game's own draw calls use.
        if (this.host.hasPendingImmediateWork()) {
            this.host.flushPendingBeforeGlDraw();
        }
        if (!this.host.waitForIdle()) {
            return refuseTextureCopy("the GPU did not confirm the draws before the copy completed");
        }
        this.copiesThisPresent++;
        this.copyCallsThisPresent++;
        // The depth the *next* frame's cube will test against, read at the last copy call of this present (after the
        // seven blurs), beside the value at the first call (right after this frame's cube). Both ends of one frame in
        // one line: "the cube is depth-rejected by the previous frame's blur depth" and "the depth is 1.0 when the
        // cube draws" are the two explanations round 224 could not separate with a single sample.
        if (VulkanGlTexture.mirrorDumpConfigured() && this.host.frameTarget() != null
                && this.host.frameTarget().depthImage() != 0L
                && this.host.presentSerial() >= 100 && this.host.presentSerial() <= 102
                && (this.copyCallsThisPresent == 1 || this.copyCallsThisPresent == 7)) {
            int depthFormat = VulkanDepthFormat.chosen();
            float[] box = readDepthRectangle(this.host.frameTarget().depthImage(), 0, 0, 256, 256, depthFormat);
            if (box != null) {
                float min = Float.MAX_VALUE;
                int atOne = 0;
                for (float value : box) {
                    min = Math.min(min, value);
                    if (value >= 0.999F) {
                        atOne++;
                    }
                }
                System.out.println("[Celtium] CELTIUM_DEPTH_END present=" + this.host.presentSerial()
                        + " call=" + this.copyCallsThisPresent + " min=" + depth3(min)
                        + " atOne=" + atOne + "/" + box.length);
                // The colour box at the same two ends, so the cube's own contribution is a mask rather than an
                // inference: the box after the blurs (call 7) is the state the next frame's cube inherits, and the box
                // at call 1 of the next frame is the same box after that cube. Their difference *is* the cube's
                // coverage - and whether the region the probe showed unpainted is unpainted by the cube or painted by
                // something else is exactly what a two-end diff decides.
                for (int band : new int[] {0, this.host.targetHeight() - 256}) {
                    int[] colorBox = readImageRectangle(this.host.frameTarget().colorImage(), 0, band, 256, 256);
                    if (colorBox == null) {
                        continue;
                    }
                    java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(256 * 256 * 4);
                    try {
                        for (int index = 0; index < colorBox.length; index++) {
                            int value = colorBox[index];
                            rgba.put(index * 4, (byte) ((value >> 16) & 0xFF));
                            rgba.put(index * 4 + 1, (byte) ((value >> 8) & 0xFF));
                            rgba.put(index * 4 + 2, (byte) (value & 0xFF));
                            rgba.put(index * 4 + 3, (byte) ((value >>> 24) & 0xFF));
                        }
                        VulkanGlTexture.dumpStageImage("boxend-" + band + "-p" + this.host.presentSerial() + "-c"
                                + this.copyCallsThisPresent, rgba, 256, 256);
                    } finally {
                        org.lwjgl.system.MemoryUtil.memFree(rgba);
                    }
                }
            }
        }
        long readbackStartedNanos = System.nanoTime();
        int[] pixels = readImageRectangle(image, x, y, width, height);
        this.textureCopyReadbackNanos += System.nanoTime() - readbackStartedNanos;
        this.textureCopyReadbacks++;
        if (pixels == null) {
            return refuseTextureCopy("the rectangle read of the owned image failed");
        }
        // The GL rule this path owes the game, and the reason the main menu was flat grey (round 223).
        // `glCopyTexSubImage2D` reads the *bound read framebuffer*. When that is the default framebuffer - GL name 0,
        // the window - there are no alpha bits, so every pixel it yields is opaque whatever the colour attachment
        // held; the hardware emulation of that is an alpha read of 1 (the same value ANGLE's Vulkan backend pins for
        // its window image, `kEmulatedAlphaValue`). A Vulkan image always has the channel, and this renderer's frame
        // image carries the alpha the game's own draws wrote: `GuiMainMenu.drawPanorama` blends with
        // `tryBlendFuncSeparate(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)`, whose *alpha* equation is dst = src, so
        // the panorama's quads left 0x80 and 0x00 in the very 256x256 the menu's blur reads back. Measured
        // (`r212-panhost-diagb`, present=2): the copy's first four pixels were 0x80313b58 0x80313a5a 0x8031395c
        // 0x8030395e and only 24899 of 65536 pixels were opaque, so the *opaque* background texture vanilla relies on
        // was 38% transparent. `rotateAndBlurSkybox` then draws its full-screen quads with `SRC_ALPHA` blending, and
        // `renderSkybox`'s final composite quad likewise: at 38% coverage the blurred panorama cannot cover the frame
        // image, so the presented menu kept its white background under `drawGradientRect`'s wash and only the sharp
        // bottom-left 256x256 (drawn directly, unblended by the copy) showed the panorama. The rule is applied only
        // where the GL source really is the default framebuffer: a copy out of an offscreen FBO keeps its alpha,
        // because that framebuffer has the channel the game asked for.
        if (windowAlphaSource(readFbo, image)) {
            int forced = 0;
            for (int index = 0; index < pixels.length; index++) {
                if ((pixels[index] >>> 24) != 0xFF) {
                    pixels[index] |= 0xFF000000;
                    forced++;
                }
            }
            this.textureCopyWindowAlphaForced += forced;
            this.textureCopyWindowAlphaCopies++;
        }
        VulkanTextureRegistry registry = this.host.registry();
        int[][] levels = new int[1][];
        levels[0] = pixels;
        if (this.textureCopySamples < 3 || this.textureCopyReadbacks % 40 == 0) {
            // What the copy actually served, bounded to three lines plus one every fortieth readback: the mean and the
            // opaque fraction of the rectangle are what tell "the source image holds the accumulated panorama" from
            // "it holds a cleared surface", which the destination mirror's own census cannot (it reports the mirror's
            // state at creation, not per copy). The periodic line is the one that matters for a steady-state defect -
            // the first three samples of a run are the loading screen, and a leg that read only those described the
            // splash's rectangle as if it were the panorama's (round 213).
            this.textureCopySamples++;
            long red = 0L;
            long green = 0L;
            long blue = 0L;
            int opaque = 0;
            for (int pixel : pixels) {
                red += (pixel >> 16) & 0xFF;
                green += (pixel >> 8) & 0xFF;
                blue += pixel & 0xFF;
                if (((pixel >>> 24) & 0xFF) >= 0x80) {
                    opaque++;
                }
            }
            int count = Math.max(1, pixels.length);
            // Where in the rectangle the content is, not only whether there is any: the panorama's faces cover the
            // whole 256x256 viewport in vanilla, so a rectangle that is dark in one band and bright in another says
            // the draw landed somewhere else than the read, or that the image was cleared after the draw. Eight bands
            // of 32 rows each, in the read's own row order, so it compares with CELTIUM_GROUP_VIEWPORT's clipped
            // rectangle (`clipped=0,824 256x256`) without a second run.
            StringBuilder bands = new StringBuilder();
            int bandRows = Math.max(1, height / 8);
            for (int band = 0; band < 8; band++) {
                long br = 0L, bg = 0L, bb = 0L;
                int n = 0;
                for (int row = band * bandRows; row < Math.min((band + 1) * bandRows, height); row++) {
                    for (int column = 0; column < width; column++) {
                        int index = row * width + column;
                        if (index >= pixels.length) {
                            break;
                        }
                        int pixel = pixels[index];
                        br += (pixel >> 16) & 0xFF;
                        bg += (pixel >> 8) & 0xFF;
                        bb += pixel & 0xFF;
                        n++;
                    }
                }
                if (n > 0) {
                    bands.append(band == 0 ? "" : " ").append(br / n).append("/").append(bg / n).append("/").append(bb / n);
                }
            }
            System.out.println("[Celtium] CELTIUM_TEX_COPY_SERVED dest=" + destName + " level=" + level
                    + " destOffset=" + xoffset + "," + yoffset + " source=" + x + "," + y + " " + width + "x" + height
                    + " readFbo=" + readFbo + " mean=" + (red / count) + "," + (green / count) + "," + (blue / count)
                    + " opaque=" + opaque + "/" + pixels.length + " served=" + (this.textureCopiesServed + 1)
                    + " bands=" + bands
                    // The frame and the image this copy read, so it can be put against CELTIUM_DRAW_SITE in the same
                    // frame: the faces draw (DRAW_SITE #1, target=1941007851) and this line reads white, and neither
                    // carried a frame or a handle before, which left ordering unanswerable.
                    + " frame=" + this.host.frameId() + " present=" + this.host.presentSerial()
                    + " readImage=0x" + Long.toHexString(image)
                    // The completion fact: whether the image the copy reads is the one the last recorded group went
                    // into, and whether any submission has been confirmed complete since that group was recorded. If
                    // the handles and the frame match and no completion has happened since, the copy is reading an
                    // image whose producing pass is still in flight - a commit-contract question, not a clear one.
                    + " lastDrawnImage=0x" + Long.toHexString(this.host.lastDrawnImage())
                    + " lastDrawnFrame=" + this.host.lastDrawnFrame()
                    + " drawnThisFrame=" + (this.host.lastDrawnFrame() == this.host.frameId())
                    + " lastDrawnEpoch=" + this.host.lastDrawnEpoch()
                    + " completedEpochNow=" + VulkanContext.completedEpoch()
                    + " completionsSinceDraw=" + (VulkanContext.completedEpoch() - this.host.lastDrawnEpoch())
                    + " passAttached=0x" + Long.toHexString(this.host.passAttachedColorImage())
                    + " passSubmissions=" + this.host.passSubmissions()
                    + " windowsThisFrame=" + this.host.immediateWindowsThisFrame()
                    // A mean cannot tell "all white" from "a few white pixels": the first four pixels and the count of
                    // non-white ones say which, and that separates "the pass never reached this image" from "the
                    // readback is reading a different target".
                    + " firstPixels=" + firstPixels(pixels)
                    + " nonWhite=" + countNonWhite(pixels)
                    // Why the rectangle is a frame old: whether anything was pending when the copy ran, how many
                    // groups and how many batches were in the open one, and whether a window was mid-flight (the
                    // flush returns immediately in that case, line 6730 of the backend, so a copy inside an open
                    // window would read the image as it stands and never see the draws it is a copy of).
                    + " pendingWork=" + this.host.hasPendingImmediateWork()
                    + " groups=" + this.host.immediateGroupCount()
                    + " openBatches=" + this.host.currentGroupBatches()
                    + " windowBusy=" + this.host.immediateWindowBusy()
                    + " windowsThisFrame=" + this.host.immediateWindowsThisFrame()
                    + " windowAlphaForced=" + this.textureCopyWindowAlphaForced
                    + " windowAlphaCopies=" + this.textureCopyWindowAlphaCopies
                    + " windowAlphaBlitsDeclined=" + this.textureCopyWindowAlphaBlitsDeclined);
        }
        boolean applied = registry.announceFullPixels(destName, levels, width, height, xoffset, yoffset);
        if (!applied) {
            return refuseTextureCopy("the destination mirror refused the pixels: "
                    + registry.lastFailure());
        }
        // The frame image itself, once per run, when the mirror dump is on: the copy's pixels say what the rectangle
        // held, and this says what the *whole* image the present reads held at the same instant. The question it exists
        // for is "is the picture that reaches the window the game's own frame": the menu's background is the game's
        // framebuffer composited by `GuiMainMenu.renderSkybox`, and a frame image that already carries the panorama in
        // its bottom-left 256x256 while the rest is the cleared white is a different fault from one that carries the
        // composite everywhere. Bounded to one dump (the third readback, the first menu frame) because it is a
        // full-screen readback plus a PNG.
        if (VulkanGlTexture.mirrorDumpConfigured() && this.textureCopyReadbacks == 3 && this.host.frameTarget() != null) {
            int stageWidth = this.host.targetWidth();
            int stageHeight = this.host.targetHeight();
            int[] stage = readImageRectangle(this.host.frameTarget().colorImage(), 0, 0, stageWidth, stageHeight);
            if (stage != null) {
                java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(stageWidth * stageHeight * 4);
                try {
                    for (int index = 0; index < stage.length; index++) {
                        int value = stage[index];
                        rgba.put(index * 4, (byte) ((value >> 16) & 0xFF));
                        rgba.put(index * 4 + 1, (byte) ((value >> 8) & 0xFF));
                        rgba.put(index * 4 + 2, (byte) (value & 0xFF));
                        rgba.put(index * 4 + 3, (byte) ((value >>> 24) & 0xFF));
                    }
                    VulkanGlTexture.dumpStageImage("frame-image", rgba, stageWidth, stageHeight);
                    System.out.println("[Celtium] CELTIUM_STAGE_DUMP stage=frame-image size=" + stageWidth + "x"
                            + stageHeight + " frame=" + this.host.frameId() + " present=" + this.host.presentSerial()
                            + " nonWhite=" + countNonWhite(stage));
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(rgba);
                }
            }
        }
        // The depth the cube's faces were tested against, in the same bounded slot as the colour dump. A colour read
        // cannot answer this: the box is 89% non-white whether it holds a fresh face or the previous frame's menu.
        if (VulkanGlTexture.mirrorDumpConfigured() && this.textureCopyReadbacks == 3 && this.host.frameTarget() != null
                && this.host.frameTarget().depthImage() != 0L) {
            int depthFormat = VulkanDepthFormat.chosen();
            float[] box = readDepthRectangle(this.host.frameTarget().depthImage(), 0, 0, 256, 256, depthFormat);
            if (box != null) {
                float min = Float.MAX_VALUE;
                float max = -Float.MAX_VALUE;
                double sum = 0.0D;
                int atOne = 0;
                int below = 0;
                for (float value : box) {
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                    sum += value;
                    if (value >= 0.999F) {
                        atOne++;
                    } else {
                        below++;
                    }
                }
                // The four numbers that decide the argument, plus the read's own extent so a partial read cannot be
                // mistaken for a cleared one: min says whether *anything* wrote a nearer depth into the box, atOne
                // says how much of it is the cleared value, and the two sample rows are the blur's own quad depth
                // (GuiMainMenu.rotateAndBlurSkybox draws at zLevel with depthMask on, which NDC z=0.5 puts at 0.75).
                System.out.println("[Celtium] CELTIUM_DEPTH_STAGE box=0,0 256x256 frame=" + this.host.frameId()
                        + " present=" + this.host.presentSerial() + " depthFormat=0x" + Integer.toHexString(depthFormat)
                        + " min=" + depth3(min) + " max=" + depth3(max) + " mean=" + depth3((float) (sum / box.length))
                        + " atOne=" + atOne + "/" + box.length + " belowOne=" + below
                        + " row0=" + depth3(box[0]) + " row128=" + depth3(box[128 * 256])
                        + " clearDepth=" + depth3((float) org.soutalab.celtium.vulkan.gl.CeltiumGlStub.clearDepth()));
            }
        }
        // The cube's own contribution to the panorama's box, measured inside one run instead of argued from bounds: the
        // box at the LAST copy of one frame (all seven blur iterations have run) and at the FIRST copy of the next
        // frame, which is the only moment between the cube's window having been submitted and the next blur. The two
        // reads bracket exactly the cube's draws, so a diff says how much of the box the 384 quads repaint - and a
        // face's NDC bounds cannot say that, because a cube face is large and mostly outside the frustum, so
        // "off-centre bounds" is its normal shape rather than a coverage hole.
        if (VulkanGlTexture.mirrorDumpConfigured() && this.host.frameTarget() != null && pixels != null
                && (this.host.presentSerial() == 100 || this.host.presentSerial() == 101)
                && (this.copiesThisPresent == 1 || this.copiesThisPresent == 7)) {
            int probeWidth = Math.min(256, this.host.targetWidth());
            int probeHeight = Math.min(256, this.host.targetHeight());
            int[] probe = readImageRectangle(this.host.frameTarget().colorImage(), 0, 0, probeWidth, probeHeight);
            if (probe != null) {
                java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(probeWidth * probeHeight * 4);
                try {
                    for (int index = 0; index < probe.length; index++) {
                        int value = probe[index];
                        rgba.put(index * 4, (byte) ((value >> 16) & 0xFF));
                        rgba.put(index * 4 + 1, (byte) ((value >> 8) & 0xFF));
                        rgba.put(index * 4 + 2, (byte) (value & 0xFF));
                        rgba.put(index * 4 + 3, (byte) ((value >>> 24) & 0xFF));
                    }
                    VulkanGlTexture.dumpStageImage("box-p" + this.host.presentSerial() + "-c" + this.copiesThisPresent,
                            rgba, probeWidth, probeHeight);
                    System.out.println("[Celtium] CELTIUM_BOX_PROBE present=" + this.host.presentSerial()
                            + " copy=" + this.copiesThisPresent + " nonWhite=" + countNonWhite(probe)
                            + "/" + probe.length);
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(rgba);
                }
            }
        }
        // What the copy just put into the mirror, as an image, when the bounded mirror dump is enabled: the mirror's own
        // dump sites sit on the announce/upload paths and never see a copy-filled mirror (measured: 411 PNGs in one
        // leg, none of them the panorama's gl-21), so the one rectangle that matters could not be looked at.
        // Unit: the copy's own RGBA8 pixels (ARGB ints unpacked as r=(p>>16)&0xFF, g=(p>>8)&0xFF, b=p&0xFF,
        // a=(p>>>24)&0xFF - the packing readImageRectangle uses, so this is the inverse of that read). Limit: the
        // helper's own MIRROR_DUMP_MAX_PIXELS and one file per call, named mirror-gl<dest>-<w>x<h>-n<k>.png; the
        // pixels are the *source* rectangle, which for the panorama is the whole 256x256 level 0. Off by default.
        if (VulkanGlTexture.mirrorDumpConfigured()) {
            java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
            try {
                int count = Math.min(pixels.length, width * height);
                for (int pixel = 0; pixel < count; pixel++) {
                    int value = pixels[pixel];
                    rgba.put(pixel * 4, (byte) ((value >> 16) & 0xFF));
                    rgba.put(pixel * 4 + 1, (byte) ((value >> 8) & 0xFF));
                    rgba.put(pixel * 4 + 2, (byte) (value & 0xFF));
                    rgba.put(pixel * 4 + 3, (byte) ((value >>> 24) & 0xFF));
                }
                VulkanGlTexture.dumpMirrorPixels(destName, rgba, width, height);
            } finally {
                org.lwjgl.system.MemoryUtil.memFree(rgba);
            }
        }
        this.textureCopiesServedThisFrame.put(copyKey, Long.valueOf(copyMarker));
        this.textureCopiesServed++;
        this.textureCopiesServedLastPresent++;
        return "served";
    }

    private long ownedReadImage(int readFbo) {
        GllessFrameTarget glless = this.host.offTargetTarget();
        if (readFbo != 0) {
            // The name-to-identity map covers every framebuffer the mixin saw at bind time, including the main one -
            // whose creation never routed a glFramebufferTexture2D through the stub, which the texture chain below
            // cannot see (measured: readFbo=1 refused). Raw-GL framebuffers have no object identity, so they fall back
            // to resolving the colour texture their attach call did record, then the identity it was reported under.
            // The pair the draws went into is the identity's own chain pair - not "the" shared one, which is what the
            // single-pair design got wrong.
            long identity = this.host.identityForFramebufferName(readFbo);
            if (identity == Long.MIN_VALUE) {
                int colorTexture = org.soutalab.celtium.vulkan.gl.CeltiumGlStub
                        .celtium$framebufferColorTexture(readFbo);
                identity = colorTexture == 0 ? Long.MIN_VALUE
                        : this.host.identityForColorTexture(colorTexture);
            }
            if (glless == null || identity == Long.MIN_VALUE) {
                return 0L;
            }
            // The routing rule the draws followed, read back: the window and the main framebuffer's picture live in
            // the frame image; every other non-main identity's draws went into that identity's own chain pair.
            if (identity == 0L || identity == this.host.mainTargetIdentity()) {
                return this.host.frameTarget() == null ? 0L : this.host.frameTarget().colorImage();
            }
            return glless.chainPairColorImage(identity);
        }
        // The routing decision, printed bounded: which facts the read resolved from and which image it chose. The
        // panorama copy reads GL name 0 while the cube faces were offered for a non-zero identity, and uniform white
        // is what a read of the wrong image looks like, so the inputs have to be in the log rather than inferred.
        if (this.readResolveLogs < READ_RESOLVE_LOG_LIMIT) {
            this.readResolveLogs++;
            System.out.println("[Celtium] CELTIUM_READ_RESOLVE readFbo=" + readFbo
                    + " mainIdentity=" + this.host.mainTargetIdentity()
                    + " mainColorName=" + this.host.framebufferColorTextureName(this.host.mainTargetIdentity())
                    + " currentTarget=" + this.host.currentRenderTarget()
                    + " currentIsMain=" + this.host.currentRenderTargetIsMain()
                    + " frameImage=" + (this.host.frameTarget() == null ? 0L : this.host.frameTarget().colorImage())
                    + " defaultPair=" + (glless == null ? 0L : glless.offTargetColorImage())
                    + " chainPair=" + (glless == null ? 0L
                            : glless.chainPairColorImage(this.host.currentRenderTarget())));
        }
        // The read framebuffer's GL name against the main framebuffer's own recorded GL name - not against the object
        // identity, which is a different number space and made this branch unreachable. Measured: Minecraft's main
        // framebuffer is the default one in this configuration (its bind reports GL name 0), its draws are routed to the
        // frame image, and the panorama copy reads from GL name 0 - so resolving name 0 to the default pair read an image
        // the cube faces never wrote and returned uniform white (`CELTIUM_TEX_COPY_SERVED … mean=255,255,255`).
        if (this.host.frameTarget() != null && this.host.mainTargetIdentity() != Long.MIN_VALUE
                && this.host.framebufferColorTextureName(this.host.mainTargetIdentity()) == readFbo) {
            return this.host.frameTarget().colorImage();
        }
        // The name comparison above can only fire when the game's main framebuffer is the one whose colour texture the
        // bind census recorded, and the copy's read name is an FBO name while that census carries a texture name. The
        // routing is the fact that does not depend on either: when the main framebuffer is what is bound, its draws went
        // to the frame image, so a read from GL name 0 is a read of the frame image.
        if (readFbo == 0 && this.host.currentRenderTargetIsMain() && this.host.frameTarget() != null) {
            return this.host.frameTarget().colorImage();
        }
        if (readFbo == 0 && "frame-image".equals(GllessFrameTarget.routeLabelForBinding(
                this.host.currentRenderTarget(), this.host.currentRenderTargetIsMain())) && this.host.frameTarget() != null) {
            // The window's own image, which is where a name-0 bind's draws went (identity 0 is the window in every
            // phase). The routing rule is asked rather than the name compared, so the read and the draws answer the
            // same question: the menu's skybox draws its faces with name 0 bound and copies from name 0, and reading a
            // different image than the faces were drawn into is what made the presented menu flat and washed out.
            return this.host.frameTarget().colorImage();
        }
        if (readFbo == 0 && glless != null) {
            // GL name 0 with a target bound that is not the main framebuffer: the name is not the fact that decides
            // where the draws went - the routing is. The game's own framebuffer carries a non-zero identity and a GL
            // name the stub records as 0, and the panorama's cube faces are offered for exactly that identity, so a
            // read that resolved "name 0 and not main" to the default pair read an image the faces never wrote and
            // returned uniform white. routeLabelForBinding is the one place the routing rule lives (main -> frame
            // image, identity 0 -> default pair, every other non-main identity -> chain pair), so the read follows
            // the same rule the draws did.
            String route = GllessFrameTarget.routeLabelForBinding(this.host.currentRenderTarget(), this.host.currentRenderTargetIsMain());
            if ("chain-pair".equals(route)) {
                glless.ensureChainPair(this.host.currentRenderTarget(), this.host.targetWidth(), this.host.targetHeight());
                return glless.chainPairColorImage(this.host.currentRenderTarget());
            }
            if ("default-pair".equals(route)) {
                // The symmetric half of the rule above, and its absence is what the panorama measured. The binding is
                // the default framebuffer (identity 0), whose draws the pass below sends to the default pair - so a
                // read of GL name 0 is a read of the default pair, and the label the binding already carries says so.
                // Without this branch the resolution fell through to the accept-time identity, which is a different
                // fact: `lastDrawnOffTargetIdentity` records which target a batch was *accepted* for (`:5744`), while
                // `passAttachedColorImage` records which image the pass *attached* (`:7539`/`:7563`). Vanilla's panorama
                // is the case that separates them - `GuiMainMenu.renderSkybox` unbinds the main framebuffer, draws the
                // cube faces into it at a 256x256 viewport, and `rotateAndBlurSkybox` then copies 256x256 of it back
                // into the panorama texture (`GuiMainMenu.java:483`) - and it measured `readFbo=0` with
                // `passAttached=defaultPair` while `readImage` came back as the chain pair:
                // `CELTIUM_TEX_COPY_SERVED dest=21 256x256 readFbo=0 mean=255,255,255 opaque=65536/65536 nonWhite=0`.
                // The gate is therefore the binding's own route, and the last-drawn identity is left as the last
                // resort below rather than the second guess.
                this.readResolvedFromLastDrawnTarget++;
                glless.ensureOffTarget(this.host.targetWidth(), this.host.targetHeight());
                return glless.offTargetColorImage();
            }
            if (this.host.lastDrawnOffTargetIdentity() != 0L
                    && this.host.lastDrawnOffTargetPresent() == this.host.presentSerial()) {
                // The copy reads name 0 and the default pair is bound, but this frame's non-main content was drawn
                // into this identity's pair: that image is what the copy is of. Measured on the menu, where the
                // panorama's faces go to the chain pair and the read otherwise found uniform white.
                this.readResolvedFromLastDrawnTarget++;
                glless.ensureOffTarget(this.host.targetWidth(), this.host.targetHeight());
                // The identity decides which pair, exactly as it does for the draws: routeLabelForBinding sends
                // identity 0 to the default pair and every other non-main identity to the chain pair. Returning the
                // chain pair unconditionally read an image the last-drawn identity never wrote - measured on the
                // menu, where the panorama's faces go to the default pair and this returned the chain pair's
                // uniform white (`CELTIUM_TEX_COPY_SERVED … mean=255,255,255`, firstPixels all 0xffffffff).
                if ("chain-pair".equals(GllessFrameTarget.routeLabelForBinding(this.host.lastDrawnOffTargetIdentity(),
                        false))) {
                    glless.ensureChainPair(this.host.lastDrawnOffTargetIdentity(),
                            this.host.targetWidth(), this.host.targetHeight());
                    return glless.chainPairColorImage(this.host.lastDrawnOffTargetIdentity());
                }
                return glless.offTargetColorImage();
            }
            glless.ensureOffTarget(this.host.targetWidth(), this.host.targetHeight());
            return glless.offTargetColorImage();
        }
        return 0L;
    }

    /**
     * A {@code glReadPixels}: reads the bound read framebuffer's image rectangle into the caller's buffer.
     * <p>
     * The contract is GL's synchronous one: the reads see every draw the game has issued so far, which the deferred
     * submission model makes an explicit ordering point - pending groups flush, the frame's recordings submit, and the
     * device is waited before the rectangle is read (the wait itself now does that submission under
     * {@code batchFrameSubmits}). Only {@code GL_RGBA}/{@code GL_BGRA} + {@code GL_UNSIGNED_BYTE} into a client
     * address are served; everything else is a refusal a mod sees as untouched buffer, the same answer a noop gives
     * but named in the log instead of silent.
     *
     * @param readFbo the GL name bound to {@code GL_READ_FRAMEBUFFER} (0 = the window)
     * @param address the caller's destination address (a PBO binding is an offset - refused)
     * @return whether the pixels were written
     */
    public boolean celtium$readPixels(int readFbo, int x, int y, int width, int height,
            int format, int type, long address) {
        if (address == 0L || width <= 0 || height <= 0) {
            return false;
        }
        // GL_UNSIGNED_BYTE (0x1401) is the only type the callers a readback can serve need; GL_RGBA (0x1908) and
        // GL_BGRA (0x80E1) are the two orders ScreenShotHelper and screenshot mods use.
        if (type != 0x1401 || (format != 0x1908 && format != 0x80E1)) {
            return false;
        }
        long image = ownedReadImage(readFbo);
        if (image == 0L) {
            if (this.readPixelsRefusals < 8) {
                this.readPixelsRefusals++;
                System.out.println("[Celtium] CELTIUM_READ_PIXELS refused: no image for readFbo=" + readFbo
                        + " rect=" + x + "," + y + " " + width + "x" + height);
            }
            return false;
        }
        // The draws the read must see, then the submission the wait must cover. Both were already the copy path's
        // contract; under batchFrameSubmits the wait's own submitOpenFrameBuffer is what sends them to the queue.
        if (this.host.hasPendingImmediateWork()) {
            this.host.flushPendingBeforeGlDraw();
        }
        if (!this.host.waitForIdle()) {
            return false;
        }
        int[] pixels = readImageRectangle(image, x, y, width, height);
        if (pixels == null) {
            return false;
        }
        // ARGB ints -> RGBA or BGRA bytes, in image row order - row 0 is the picture's bottom, which is also the row
        // order GL's readback contract returns.
        boolean bgra = format == 0x80E1;
        long dst = address;
        int count = Math.min(pixels.length, width * height);
        for (int i = 0; i < count; i++) {
            int p = pixels[i];
            if (bgra) {
                org.lwjgl.system.MemoryUtil.memPutByte(dst, (byte) p);
                org.lwjgl.system.MemoryUtil.memPutByte(dst + 1, (byte) (p >>> 8));
                org.lwjgl.system.MemoryUtil.memPutByte(dst + 2, (byte) (p >>> 16));
            } else {
                org.lwjgl.system.MemoryUtil.memPutByte(dst, (byte) (p >>> 16));
                org.lwjgl.system.MemoryUtil.memPutByte(dst + 1, (byte) (p >>> 8));
                org.lwjgl.system.MemoryUtil.memPutByte(dst + 2, (byte) p);
            }
            org.lwjgl.system.MemoryUtil.memPutByte(dst + 3, (byte) (p >>> 24));
            dst += 4L;
        }
        this.readPixelsServed++;
        return true;
    }

    /** Bounded refusals for {@link #celtium$readPixels} and the serve count for the run report. */
    private int readPixelsRefusals;
    private long readPixelsServed;

    /** {@return the glReadPixels calls served this run}, for the texture-copy section of the run report. */
    public long readPixelsServed() {
        return this.readPixelsServed;
    }

    private String refuseTextureCopy(String reason) {
        this.textureCopiesRefused++;
        this.lastTextureCopyRefusal = reason;
        return "refused-" + reason;
    }

    /** Copy bounds without overflowing an int sum; all callers check this before readback or Vulkan recording. */
    static boolean rectangleFits(int x, int y, int width, int height, int imageWidth, int imageHeight) {
        return x >= 0 && y >= 0 && width > 0 && height > 0
                && imageWidth > 0 && imageHeight > 0
                && (long) x + width <= imageWidth && (long) y + height <= imageHeight;
    }

    /** A valid 1x1 copy must not fail because a diagnostic assumes it has four pixels. */
    static String firstPixels(int[] pixels) {
        StringBuilder preview = new StringBuilder("[");
        for (int i = 0; i < Math.min(4, pixels.length); i++) {
            if (i > 0) preview.append(',');
            preview.append(Integer.toHexString(pixels[i]));
        }
        return preview.append(']').toString();
    }

    private static int countNonWhite(int[] pixels) {
        int count = 0;
        for (int pixel : pixels) {
            if ((pixel & 0xFFFFFF) != 0xFFFFFF) {
                count++;
            }
        }
        return count;
    }

    /**
     * {@return whether this copy's GL source is the window} - the default framebuffer, whose alpha GL reports as 1
     * whatever the colour attachment holds (GL name 0, no alpha bits; the emulation pins 1, the value ANGLE's Vulkan
     * backend uses for its window surface). The rule lives here, once, because the two routes owe it differently: the
     * host route applies it to the pixels it read, and the native route gets it for free: alpha writes are masked on
     * the delivered frame, so its image's alpha channel holds the acquire's alpha-1 clear and the blit's verbatim
     * copy already yields 1.
     */
    private boolean windowAlphaSource(int readFbo, long image) {
        return readFbo == 0 && this.host.currentRenderTarget() == 0L && this.host.frameTarget() != null
                && image == this.host.frameTarget().colorImage();
    }

    /** Three decimals of a depth, so 0.750 and 1.000 are readable in the one line this instrument prints. */
    private static String depth3(float value) {
        return String.format(java.util.Locale.ROOT, "%.3f", Float.valueOf(value));
    }

    /**
     * Reads a rectangle of a depth image into floats, the depth-side twin of {@link #readImageRectangle}.
     * <p>
     * It exists for one question round 223 narrowed the menu's grey background to and could not answer from pixels:
     * the frame image's depth in the panorama's 256x256 box is either 1.0 (the game's per-frame
     * {@code glClear(GL_DEPTH_BUFFER_BIT)} reached this image) or the previous frame's blur quad depth
     * ({@code rotateAndBlurSkybox} draws at {@code zLevel} with {@code depthMask} left on at the end of
     * {@code drawPanorama}, so it writes roughly 0.75 into exactly that box). The cube does not write depth at all
     * ({@code drawPanorama} sets {@code depthMask(false)}), so the value read here is the value its faces were tested
     * against - and a stale 0.75 rejects every face, because a face's 64 jitter copies do not change its depth. That
     * is the difference between "the cube repaints the box and something else eats it" and "the cube is never drawn
     * into the box at all", and no colour read can tell them apart.
     * <p>
     * Unit: the depth image's own format ({@code VulkanDepthFormat.chosen}), read as floats either way - a 16-bit
     * unorm depth is scaled by 1/65535 so both devices report the same 0..1 quantity. Limit: one rectangle per call,
     * and the caller bounds the calls, because this is a full command buffer plus a fence wait.
     */
    public float[] readDepthRectangle(long depthImage, int x, int y, int width, int height, int depthFormat) {
        long buffer = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        long memory = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        boolean sixteenBit = depthFormat == org.lwjgl.vulkan.VK10.VK_FORMAT_D16_UNORM;
        int bytesPerPixel = sixteenBit ? 2 : 4;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bytes = (long) width * (long) height * (long) bytesPerPixel;
            org.lwjgl.vulkan.VkBufferCreateInfo bufferInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes)
                    .usage(org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateBuffer(this.host.context().device(), bufferInfo, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            buffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements =
                    org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements(this.host.context().device(), buffer, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.host.context().memoryTypeIndex(requirements.memoryTypeBits(),
                            org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                    | org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "the depth-stage readback buffer"));
            if (org.lwjgl.vulkan.VK10.vkAllocateMemory(this.host.context().device(), allocate, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            memory = handle.get(0);
            org.lwjgl.vulkan.VK10.vkBindBufferMemory(this.host.context().device(), buffer, memory, 0);
            VkCommandBuffer commands = this.host.context().beginCommands();
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy =
                    org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            copy.get(0).imageOffset().set(x, y, 0);
            copy.get(0).imageExtent().set(width, height, 1);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer toTransfer =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(depthImage)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT);
            toTransfer.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);
            org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer(commands, depthImage,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer backToGeneral =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            backToGeneral.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(depthImage)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT);
            backToGeneral.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, backToGeneral);
            this.host.context().endCommands(commands, "texture-copy");
            org.lwjgl.PointerBuffer mapped = stack.mallocPointer(1);
            if (org.lwjgl.vulkan.VK10.vkMapMemory(this.host.context().device(), memory, 0, bytes, 0, mapped)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            float[] depths = new float[width * height];
            for (int pixel = 0; pixel < depths.length; pixel++) {
                long at = mapped.get(0) + (long) pixel * bytesPerPixel;
                depths[pixel] = sixteenBit
                        ? (MemoryUtil.memGetShort(at) & 0xFFFF) / 65535.0F
                        : MemoryUtil.memGetFloat(at);
            }
            org.lwjgl.vulkan.VK10.vkUnmapMemory(this.host.context().device(), memory);
            return depths;
        } catch (RuntimeException | LinkageError error) {
            this.host.record("depth stage read failed: " + this.host.describe(error));
            return null;
        } finally {
            if (buffer != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyBuffer(this.host.context().device(), buffer, null);
            }
            if (memory != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkFreeMemory(this.host.context().device(), memory, null);
            }
        }
    }

    public int[] readImageRectangle(long image, int x, int y, int width, int height) {
        // The copy's own facts, so the white is decided independently of the hold leg's VUID: which image is read,
        // its extent, the frame, and whether it is the image the current pass attached.
        if (this.copyReadLogs < 6) {
            this.copyReadLogs++;
            System.out.println("[Celtium] CELTIUM_COPY_READ image=0x" + Long.toHexString(image)
                    + " rect=" + x + "," + y + " " + width + "x" + height
                    + " frame=" + this.host.frameId()
                    + " passAttached=0x" + Long.toHexString(this.host.passAttachedColorImage())
                    + " chainPair=0x" + Long.toHexString(this.host.offTargetTarget() == null ? 0L
                            : this.host.offTargetTarget().chainPairColorImage(this.host.currentRenderTarget()))
                    + " frameImage=0x" + Long.toHexString(this.host.frameTarget() == null ? 0L
                            : this.host.frameTarget().colorImage()));
        }
        long buffer = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        long memory = org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            long bytes = (long) width * (long) height * 4L;
            org.lwjgl.vulkan.VkBufferCreateInfo bufferInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes)
                    .usage(org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(org.lwjgl.vulkan.VK10.VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            if (org.lwjgl.vulkan.VK10.vkCreateBuffer(this.host.context().device(), bufferInfo, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            buffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements =
                    org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            org.lwjgl.vulkan.VK10.vkGetBufferMemoryRequirements(this.host.context().device(), buffer, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(this.host.context().memoryTypeIndex(requirements.memoryTypeBits(),
                            org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT
                                    | org.lwjgl.vulkan.VK10.VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "the texture-copy readback buffer"));
            if (org.lwjgl.vulkan.VK10.vkAllocateMemory(this.host.context().device(), allocate, null, handle)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            memory = handle.get(0);
            org.lwjgl.vulkan.VK10.vkBindBufferMemory(this.host.context().device(), buffer, memory, 0);

            VkCommandBuffer commands = this.host.context().beginCommands();
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy =
                    org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            // The rows GL asks for, straight through. GL's copy `y` counts from the bottom of the read framebuffer and
            // this image is in that same order - memory row 0 is the picture's bottom (VulkanSwapchain flips once at
            // present) - so GL's y is the image row. The conversion that used to sit here
            // (`targetHeight - y - height`) is the one a top-down image would need; with the viewport fix
            // (6e75884c1) the panorama's cube is drawn into rows 0..256 and this read the picture's *top* rows
            // instead, which is why the copy's sample was the menu's own background gradient at every present
            // (bands 226..253, opaque=0, run r212-panhost-steady2) while the cube was visibly in the picture.
            copy.get(0).imageOffset().set(x, y, 0);
            copy.get(0).imageExtent().set(width, height, 1);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer toTransfer =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT);
            toTransfer.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, toTransfer);
            org.lwjgl.vulkan.VK10.vkCmdCopyImageToBuffer(commands, image,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer backToGeneral =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            backToGeneral.get(0).sType$Default()
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .image(image)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_MEMORY_READ_BIT);
            backToGeneral.get(0).subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_COLOR_BIT)
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, backToGeneral);
            this.host.context().endCommands(commands, "texture-copy");

            org.lwjgl.PointerBuffer mapped = stack.mallocPointer(1);
            if (org.lwjgl.vulkan.VK10.vkMapMemory(this.host.context().device(), memory, 0, bytes, 0, mapped)
                    != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                return null;
            }
            // The owned colour image is VK_FORMAT_R8G8B8A8_UNORM, so the bytes are R, G, B, A in that order - the ARGB
            // int the mirror's patch path expects packs R in bits 16..23.
            int[] pixels = new int[width * height];
            for (int pixel = 0; pixel < pixels.length; pixel++) {
                long at = mapped.get(0) + (long) pixel * 4L;
                int r = MemoryUtil.memGetByte(at) & 0xFF;
                int g = MemoryUtil.memGetByte(at + 1) & 0xFF;
                int b = MemoryUtil.memGetByte(at + 2) & 0xFF;
                int a = MemoryUtil.memGetByte(at + 3) & 0xFF;
                pixels[pixel] = (a << 24) | (r << 16) | (g << 8) | b;
            }
            org.lwjgl.vulkan.VK10.vkUnmapMemory(this.host.context().device(), memory);
            // No row reversal, and the earlier comment here argued for one. The owned image is in GL texture order -
            // row 0 is the picture's bottom (VulkanSwapchain flips once at present) - and the copy above read memory
            // rows y..y+h, which *are* the GL order rows for that rectangle. GL writes framebuffer row (y+i) into
            // destination row (yoffset+i) and a mirror's row 0 is its texture's first row, so buffer row i already
            // belongs at destination row i: reversing put the destination upside down, which a mean cannot see and a
            // crop can.
            return pixels;
        } catch (RuntimeException | LinkageError error) {
            this.host.record("texture copy read failed: " + this.host.describe(error));
            return null;
        } finally {
            if (buffer != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkDestroyBuffer(this.host.context().device(), buffer, null);
            }
            if (memory != org.lwjgl.vulkan.VK10.VK_NULL_HANDLE) {
                org.lwjgl.vulkan.VK10.vkFreeMemory(this.host.context().device(), memory, null);
            }
        }
    }
}
