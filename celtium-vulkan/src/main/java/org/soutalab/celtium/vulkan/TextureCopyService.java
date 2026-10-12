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

        long mainTargetIdentity();

        /** {@return the GL colour-texture name recorded for one framebuffer identity}, 0 when none. */
        int framebufferColorTextureName(long identity);

        long currentRenderTarget();

        boolean currentRenderTargetIsMain();

        long lastDrawnOffTargetIdentity();

        long lastDrawnOffTargetFrame();

        long lastDrawnImage();

        long lastDrawnFrame();

        long lastDrawnEpoch();

        long passAttachedColorImage();

        int passSubmissions();

        int immediateWindowsThisFrame();

        boolean immediateWindowBusy();

        int immediateGroupCount();

        int recordedImmediateGroupCount();

        int currentGroupBatches();

        VulkanTextureRegistry registry();

        /** Submits the frame's queued groups: the copy must read what the game has already drawn. */
        void flushPendingBeforeGlDraw();

        /** {@return whether the GPU confirmed every submission}; a false is a refusal, never a fallback. */
        boolean waitForIdle();

        void record(String diagnostic);

        /** {@return one line describing a throwable}, the same text the backend's own diagnostics use. */
        String describe(Throwable error);
    }

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
     * The frame the served-copy set belongs to, and the destination rectangles already served in it.
     * <p>
     * The menu asks for the same rectangle seven times per frame; one readback answers all seven, and this set is what
     * makes that a decision rather than a repeated cost. Cleared whenever the frame changes.
     */
    private long textureCopyFrame = -1L;
    private final java.util.Set<Long> textureCopiesServedThisFrame = new java.util.HashSet<Long>();
    private int textureCopiesReusedInFrame;
    /** Readback submissions the copy path actually made, and the wall time they took. */
    private int textureCopyReadbacks;
    private long textureCopyReadbackNanos;
    private String lastTextureCopyRefusal = "none";
    private int readResolveLogs;
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
        if (x < 0 || y < 0 || x + width > this.host.targetWidth() || y + height > sourceImageHeight) {
            return refuseTextureCopy("the source rectangle " + x + "," + y + " " + width + "x" + height
                    + " is not inside the " + this.host.targetWidth() + "x" + sourceImageHeight + " image");
        }
        // The frame's first request for this destination rectangle pays for the readback; the rest are answered from it.
        long copyKey = (((long) destName) << 32) ^ (((long) level) << 28) ^ (((long) xoffset) << 20)
                ^ (((long) yoffset) << 12) ^ (((long) x) << 8) ^ (((long) y) << 4) ^ (((long) width) << 2)
                ^ ((long) height);
        if (this.textureCopyFrame != this.host.frameId()) {
            this.textureCopyFrame = this.host.frameId();
            this.textureCopiesServedThisFrame.clear();
        }
        if (this.textureCopyFrame == this.host.frameId() && this.textureCopiesServedThisFrame.contains(Long.valueOf(copyKey))) {
            this.textureCopiesReusedInFrame++;
            this.textureCopiesServed++;
            return "served";
        }
        // What the game drew into that image is still queued as groups: submitting them is what makes the read the
        // frame the copy is about. flushPendingBeforeGlDraw draws into an open pass if there is one, otherwise it opens
        // and completes an immediate window, which is the same ordering boundary the game's own draw calls use.
        if (this.host.immediateGroupCount() > 0) {
            this.host.flushPendingBeforeGlDraw();
        }
        if (!this.host.waitForIdle()) {
            return refuseTextureCopy("the GPU did not confirm the draws before the copy completed");
        }
        long readbackStartedNanos = System.nanoTime();
        int[] pixels = readImageRectangle(image, x, y, width, height);
        this.textureCopyReadbackNanos += System.nanoTime() - readbackStartedNanos;
        this.textureCopyReadbacks++;
        if (pixels == null) {
            return refuseTextureCopy("the rectangle read of the owned image failed");
        }
        VulkanTextureRegistry registry = this.host.registry();
        int[][] levels = new int[1][];
        levels[0] = pixels;
        if (this.textureCopySamples < 3) {
            // What the copy actually served, bounded to three lines: the mean and the opaque fraction of the rectangle
            // are what tell "the source image holds the accumulated panorama" from "it holds a cleared surface", which
            // the destination mirror's own census cannot (it reports the mirror's state at creation, not per copy).
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
            System.out.println("[Celtium] CELTIUM_TEX_COPY_SERVED dest=" + destName + " level=" + level
                    + " destOffset=" + xoffset + "," + yoffset + " source=" + x + "," + y + " " + width + "x" + height
                    + " readFbo=" + readFbo + " mean=" + (red / count) + "," + (green / count) + "," + (blue / count)
                    + " opaque=" + opaque + "/" + pixels.length + " served=" + (this.textureCopiesServed + 1)
                    // The frame and the image this copy read, so it can be put against CELTIUM_DRAW_SITE in the same
                    // frame: the faces draw (DRAW_SITE #1, target=1941007851) and this line reads white, and neither
                    // carried a frame or a handle before, which left ordering unanswerable.
                    + " frame=" + this.host.frameId() + " readImage=0x" + Long.toHexString(image)
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
                    + " firstPixels=[" + Integer.toHexString(pixels[0]) + "," + Integer.toHexString(pixels[1])
                    + "," + Integer.toHexString(pixels[2]) + "," + Integer.toHexString(pixels[3]) + "]"
                    + " nonWhite=" + countNonWhite(pixels));
        }
        boolean applied = registry.announceFullPixels(destName, levels, width, height, xoffset, yoffset);
        if (!applied) {
            return refuseTextureCopy("the destination mirror refused the pixels: "
                    + registry.lastFailure());
        }
        this.textureCopiesServedThisFrame.add(Long.valueOf(copyKey));
        this.textureCopiesServed++;
        return "served";
    }

    private long ownedReadImage(int readFbo) {
        GllessFrameTarget glless = this.host.offTargetTarget();
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
                    + " chainPair=" + (glless == null ? 0L : glless.chainPairColorImage()));
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
                glless.ensureOffTarget(this.host.targetWidth(), this.host.targetHeight());
                return glless.chainPairColorImage();
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
            if (this.host.lastDrawnOffTargetIdentity() != 0L && this.host.lastDrawnOffTargetFrame() == this.host.frameId()) {
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
                    return glless.chainPairColorImage();
                }
                return glless.offTargetColorImage();
            }
            glless.ensureOffTarget(this.host.targetWidth(), this.host.targetHeight());
            return glless.offTargetColorImage();
        }
        return 0L;
    }

    private String refuseTextureCopy(String reason) {
        this.textureCopiesRefused++;
        this.lastTextureCopyRefusal = reason;
        return "refused-" + reason;
    }

    private static void reverseRows(int[] pixels, int width, int height) {
        for (int top = 0, bottom = height - 1; top < bottom; top++, bottom--) {
            int first = top * width;
            int last = bottom * width;
            for (int column = 0; column < width; column++) {
                int swap = pixels[first + column];
                pixels[first + column] = pixels[last + column];
                pixels[last + column] = swap;
            }
        }
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
                            : this.host.offTargetTarget().chainPairColorImage())
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
            // GL's copy `y` counts from the *bottom* of the read framebuffer; a Vulkan image's rows count from the top.
            // Passing `y` straight through read the top rows - the cleared ones - which is measured as
            // `CELTIUM_TEX_COPY_SERVED dest=21 256x256 readFbo=0 mean=255,255,255 opaque=0/65536 nonWhite=0` while the
            // panorama's faces were in the bottom 256 rows of the same image (GuiMainMenu.renderSkybox draws them under
            // `GlStateManager.viewport(0, 0, 256, 256)`). The rows GL asks for start `height - y - height` from the top.
            copy.get(0).imageOffset().set(x, this.host.targetHeight() - y - height, 0);
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
            this.host.context().endCommands(commands);

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
            // GL writes framebuffer row (counted from the bottom) `y + i` into destination row `yoffset + i`, and a
            // mirror's row 0 is its texture's first row - the same order every `glTexImage2D` int[] upload arrives in.
            // The mirror's rows are therefore the image's rows read *upwards* from `y`, which is the reverse of the
            // buffer the copy above produced. Without this the destination gets the source upside down: the right rows
            // in the wrong order, which a mean cannot see and a crop can.
            reverseRows(pixels, width, height);
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
