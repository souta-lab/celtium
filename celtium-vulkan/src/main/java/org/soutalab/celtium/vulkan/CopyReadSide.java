package org.soutalab.celtium.vulkan;

import java.nio.IntBuffer;

import org.lwjgl.system.MemoryStack;

/**
 * The read side of the copy, extracted from {@code VulkanTerrainGpuBackend} (refactoring plan R11).
 * <p>
 * What lives here is the banner region that began with the {@code glCopyTexImage2D} /
 * {@code glCopyTexSubImage2D} read side: the colour and depth copies and the {@link TextureCopyService}
 * host behind them, the readbacks ({@code glReadPixels} / {@code glGetTexImage}) and the chain-time
 * image dumps, the present counters the diagnostics read, the light-map coordinate capture, the game
 * framebuffer's adoption and the frame target's establishment, the viewport and window-size reads the
 * establishment sizes from, the mirror probes, the texture registry's accessor, and the atlas and
 * light-map mirror refreshes.
 * <p>
 * The class holds the backend and reads its frame state through it; the state that was already local
 * to this region (the copy counters, the viewport and window-size caches, the mirror countdowns) moved
 * with the code. Every entry the rest of the renderer calls keeps its name and signature on the
 * backend as a delegating door, so no caller changed. The model-draw switches and the fence/descriptor
 * fault switches that were declared inside this region stay on the backend: their readers are the
 * model path and the fence/descriptor paths, not the read side.
 */
final class CopyReadSide {

    private final VulkanTerrainGpuBackend backend;

    CopyReadSide(VulkanTerrainGpuBackend backend) {
        this.backend = backend;
    }

    // ---- glCopyTexImage2D / glCopyTexSubImage2D: the read side of the copy --------------------------------------
    //
    // The main menu's panorama is the measured caller (GuiMainMenu.renderSkybox -> drawPanorama -> 7x
    // rotateAndBlurSkybox): 384 quads are drawn into the default framebuffer with a 256x256 viewport and then copied
    // into a 256x256 dynamic texture with glCopyTexSubImage2D(3553, 0, 0, 0, 0, 0, 256, 256), seven times, each copy
    // followed by three blur quads. Under GL-less the stub has no framebuffer to read, so those copies were a named
    // refusal and the skybox texture kept the flat colour the game had uploaded - the menu's background.
    //
    // What the copy needs is the pixels the game's own draws put in the bound read framebuffer, and this renderer owns
    // that image: identity 0 is the GL-less default framebuffer, whose draws the off-target pass puts in the frame
    // target's default pair (see GllessFrameTarget.pairLabelForIdentity), and the game's main identity is drawn into
    // the frame image the present path blits from. Nothing else is served: a read of a framebuffer this renderer does
    // not own (a mod's own FBO) stays a refusal rather than reading whatever happens to be bound.

    /** How many chain-time image dumps have been written (bounded; see {@code celtium$dumpImagesAtChainTime}). */
    int chainFrameDumps;

    /**
     * The texture-copy path, extracted (task-63 / R1a). Every counter the summary reads lives in the service and is
     * read back through its accessors, so the summary's strings are unchanged while the state has one owner.
     * <p>
     * The service's {@code Host} surface is the temporary boundary the plan records: it exposes the backend fields the
     * copy path reads today, and the follow-up (OPT-26/27) narrows it to a {@code ReadSource} - image/view, extent,
     * layout, generation and a content version - plus the explicit ordering operations.
     */
    final TextureCopyService textureCopyService = new TextureCopyService(new CopyHost());

    /** Cumulative wall time of the game's copyTexSubImage calls: three a frame in the menu, and each is a GPU round trip. */
    long textureCopyCalls;
    long textureCopyNanos;

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
    public String celtium$serveTextureCopy(int destName, int level, int xoffset, int yoffset,
            int x, int y, int width, int height, int readFbo) {
        // The depth route first: a copy whose destination is a recorded depth texture, out of a read
        // framebuffer that records a depth attachment, is a GPU depth copy between the two textures' shared
        // depth images - not a colour readback. The colour service has no depth resolution and refuses such
        // a copy by name ("framebuffer N is not an image this renderer owns"); the measured case is Iris's
        // pre-translucent shadow depth copy, refused every leg until this route existed.
        if (level == 0 && destName != 0
                && org.soutalab.celtium.vulkan.gl.GlsmFrontend.celtium$framebufferForDepthTexture(destName) != 0) {
            int sourceDepthTexture = org.soutalab.celtium.vulkan.gl.GlsmFrontend
                    .celtium$framebufferDepthTexture(readFbo);
            if (sourceDepthTexture != 0) {
                return celtium$serveDepthCopy(sourceDepthTexture, destName, x, y, width, height,
                        xoffset, yoffset);
            }
        }
        // Thin delegate: the copy path lives in TextureCopyService, and this signature is what the GL-less stub calls.
        long copyStarted = System.nanoTime();
        String copyResult = this.textureCopyService.serveTextureCopy(destName, level, xoffset, yoffset, x, y, width,
                height, readFbo);
        this.textureCopyNanos += System.nanoTime() - copyStarted;
        this.textureCopyCalls++;
        return copyResult;
    }

    /** Depth copies served on the native route, counted apart from the colour service's served/refused. */
    private int depthCopiesServed;
    /** Bounded CELTIUM_DEPTH_COPY lines. */
    private int depthCopyLogs;

    /**
     * Serves one depth copy between two depth textures' shared depth images, natively: the pending groups
     * flush first (the colour service's ordering contract — the copy must read what the game has already
     * drawn), then a {@code vkCmdCopyImage} over the depth aspect is recorded and submitted ahead of
     * everything the game records from here on. No host readback: the destination is a GPU depth texture
     * the same frame's programs sample, which is the whole shape the colour service cannot serve.
     * <p>
     * Layout discipline is the shared store's: each image is transitioned from its recorded layout to its
     * transfer layout, the source is restored to the layout it came from, and the destination is left in
     * {@code SHADER_READ_ONLY_OPTIMAL} — its next reader is a sampler, and any later attachment or copy
     * transitions from the recorded layout, which the store now names.
     *
     * @return {@code "served"} when the copy was recorded and submitted, otherwise a reason that names itself
     */
    public String celtium$serveDepthCopy(int sourceDepthTexture, int destDepthTexture,
            int x, int y, int width, int height, int xoffset, int yoffset) {
        if (sourceDepthTexture == 0 || destDepthTexture == 0 || width <= 0 || height <= 0) {
            return "invalid depth copy arguments: source=" + sourceDepthTexture + " dest=" + destDepthTexture
                    + " " + width + "x" + height;
        }
        GllessFrameTarget target = this.backend.offTargetTarget();
        if (target == null) {
            return "the depth copy has no frame target to serve from";
        }
        long sourceKey = RenderTargetRegistry.rawColorTextureIdentity(sourceDepthTexture);
        long destKey = RenderTargetRegistry.rawColorTextureIdentity(destDepthTexture);
        long sourceImage = target.sharedDepthImage(sourceKey);
        long destImage = target.sharedDepthImage(destKey);
        if (sourceImage == 0L) {
            // Registration normally lands at bind/attach time. A source whose framebuffer was only ever attached
            // before this backend existed registers here instead — too late for this frame's copy (the pass has
            // already drawn into whatever target it drew into, not into a pair created now), so the refusal
            // stands, but the store is filled for the frames that follow.
            int sourceFbo = org.soutalab.celtium.vulkan.gl.GlsmFrontend
                    .celtium$framebufferForDepthTexture(sourceDepthTexture);
            if (sourceFbo != 0) this.backend.celtium$registerRawFramebuffer(sourceFbo, false);
            return "the depth copy's source texture " + sourceDepthTexture
                    + " is not in the shared depth store (its framebuffer's pair never borrowed it)";
        }
        if (destImage == 0L) {
            // The destination's content is defined by this copy, so registering it now is exact rather than late:
            // its framebuffer's pair borrows the shared depth image and the copy lands in it below.
            int destFbo = org.soutalab.celtium.vulkan.gl.GlsmFrontend
                    .celtium$framebufferForDepthTexture(destDepthTexture);
            if (destFbo != 0) this.backend.celtium$registerRawFramebuffer(destFbo, false);
            destImage = target.sharedDepthImage(destKey);
            if (destImage == 0L) {
                return "the depth copy's destination texture " + destDepthTexture
                        + " is not in the shared depth store (its framebuffer's pair never borrowed it)";
            }
        }
        if (sourceImage == destImage) {
            // A copy whose two names resolve to the one shared image - the same depth texture on both sides, or
            // two names over one borrowed depth - is a feedback loop: the barrier below would carry two
            // conflicting transitions for one image in a single call, vkCmdCopyImage would read and write the
            // same image with possibly overlapping rectangles, and the store would end up with two different
            // layouts recorded for it. Refused by name, like this method's other refusals.
            // Bughunt 2026-11-21 lane 01, finding 4.
            return "the depth copy's source texture " + sourceDepthTexture + " and destination texture "
                    + destDepthTexture + " resolve to the same shared depth image";
        }
        int[] sourceExtent = target.sharedDepthExtent(sourceKey);
        int[] destExtent = target.sharedDepthExtent(destKey);
        if (sourceExtent == null || destExtent == null
                || x < 0 || y < 0 || x + width > sourceExtent[0] || y + height > sourceExtent[1]
                || xoffset < 0 || yoffset < 0 || xoffset + width > destExtent[0]
                || yoffset + height > destExtent[1]) {
            return "the depth copy rectangle " + x + "," + y + " " + width + "x" + height + " -> "
                    + xoffset + "," + yoffset + " is not inside the shared depth images";
        }
        // The copy's ordering flush, not a draw boundary (the window census' stamp).
        this.backend.pendingWindowTrigger = FramePassTimings.WINDOW_TRIGGER_ORDER;
        this.backend.celtium$flushPendingForGlDrawCause(GlDrawWindowCensus.CAUSE_COPY_ORDER);
        int sourceFrom = target.sharedDepthLayout(sourceKey);
        int destFrom = target.sharedDepthLayout(destKey);
        org.lwjgl.vulkan.VkCommandBuffer commands = this.backend.context.beginCommands();
        try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barriers =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(2, stack);
            org.lwjgl.vulkan.VkImageMemoryBarrier sourceBarrier = barriers.get(0);
            sourceBarrier.sType$Default().image(sourceImage)
                    .oldLayout(sourceFrom).newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(depthLayoutAccess(sourceFrom))
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT);
            sourceBarrier.subresourceRange().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            org.lwjgl.vulkan.VkImageMemoryBarrier destBarrier = barriers.get(1);
            destBarrier.sType$Default().image(destImage)
                    .oldLayout(destFrom).newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(depthLayoutAccess(destFrom))
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT);
            destBarrier.subresourceRange().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, barriers);

            org.lwjgl.vulkan.VkImageCopy.Buffer region = org.lwjgl.vulkan.VkImageCopy.calloc(1, stack);
            region.get(0).srcSubresource().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 0, 1);
            region.get(0).srcOffset().set(x, y, 0);
            region.get(0).dstSubresource().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 0, 1);
            region.get(0).dstOffset().set(xoffset, yoffset, 0);
            region.get(0).extent().set(width, height, 1);
            org.lwjgl.vulkan.VK10.vkCmdCopyImage(commands, sourceImage,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, destImage,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region);

            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer back =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(2, stack);
            org.lwjgl.vulkan.VkImageMemoryBarrier sourceBack = back.get(0);
            sourceBack.sType$Default().image(sourceImage)
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .newLayout(sourceFrom == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
                            ? GllessFrameTarget.DEPTH_RENDER_LAYOUT : sourceFrom)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT
                            | org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT);
            sourceBack.subresourceRange().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            org.lwjgl.vulkan.VkImageMemoryBarrier destBack = back.get(1);
            destBack.sType$Default().image(destImage)
                    .oldLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                    .newLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL)
                    .srcQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(org.lwjgl.vulkan.VK10.VK_QUEUE_FAMILY_IGNORED)
                    .srcAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT);
            destBack.subresourceRange().set(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);
            org.lwjgl.vulkan.VK10.vkCmdPipelineBarrier(commands,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_TRANSFER_BIT,
                    org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, back);

            int sourceFinalLayout = sourceFrom == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
                    ? GllessFrameTarget.DEPTH_RENDER_LAYOUT : sourceFrom;
            VulkanGlTexture.noteExternalForeignLayout(commands, sourceImage, sourceFinalLayout);
            VulkanGlTexture.noteExternalForeignLayout(commands, destImage,
                    org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);

            org.lwjgl.vulkan.VkCommandBuffer submitted = commands;
            commands = null;
            this.backend.context.celtium$submitDeferred(submitted, 0L);
            this.backend.context.celtium$flushBatchedSubmits();
        } catch (RuntimeException | LinkageError error) {
            if (commands != null && !this.backend.context.celtium$isDeviceLost()) {
                this.backend.context.celtium$abandonCommands(commands);
            }
            if (this.backend.context.celtium$isDeviceLost()) {
                throw error;
            }
            return "the native depth copy threw: " + error;
        }
        target.noteSharedDepthLayout(sourceKey,
                sourceFrom == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED
                        ? GllessFrameTarget.DEPTH_RENDER_LAYOUT : sourceFrom);
        target.noteSharedDepthLayout(destKey, org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        this.depthCopiesServed++;
        if (this.depthCopyLogs < 8) {
            this.depthCopyLogs++;
            // The source layout is the difference between serving the depth the pass drew and serving an image the
            // barrier treated as undefined (item 332): a copy whose oldLayout is UNDEFINED discards the content it
            // reads, so a SERVED line alone cannot say the shadow pass reached the copy's source. With this field the
            // acceptance leg reads "served, and it was the attachment layout" from one line.
            System.out.println("[Celtium] CELTIUM_DEPTH_COPY source=" + sourceDepthTexture + " dest="
                    + destDepthTexture + " " + width + "x" + height + " servedFrom=native-depth"
                    + " sourceLayout=" + celtium$layoutName(sourceFrom)
                    + " served=" + this.depthCopiesServed);
        }
        return "served";
    }

    /** {@return the name of a Vulkan image layout, for the census lines that have to be read against a claim}. */
    static String celtium$layoutName(int layout) {
        switch (layout) {
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED:
                return "UNDEFINED";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL:
                return "GENERAL";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:
                return "SHADER_READ_ONLY";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL:
                return "TRANSFER_SRC";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL:
                return "TRANSFER_DST";
            case org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL:
                return "DEPTH_RENDER_LAYOUT";
            default:
                return "layout-" + layout;
        }
    }

    /** {@return the access mask a depth image in {@code layout} may have pending}, for the copy's barriers. */
    private static int depthLayoutAccess(int layout) {
        if (layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED) {
            return 0;
        }
        if (layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL) {
            return org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_WRITE_BIT;
        }
        if (layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            return org.lwjgl.vulkan.VK10.VK_ACCESS_TRANSFER_READ_BIT;
        }
        if (layout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL) {
            return org.lwjgl.vulkan.VK10.VK_ACCESS_SHADER_READ_BIT;
        }
        return org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT
                | org.lwjgl.vulkan.VK10.VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
    }

    /**
     * Serves one {@code glReadPixels} from an image this renderer owns.
     * <p>
     * The caller is the GL-less stub, which knows the bound read framebuffer's GL name and the rectangle, format and
     * destination address the game asked for. The pixels come from the same readback the texture-copy path uses
     * (transfer-source copy into a host-visible buffer) after the same ordering contract: pending groups flush, the
     * frame's recordings submit, and the device is confirmed idle - the synchronous answer GL owes, which a noop left
     * as a zeroed buffer for every caller that ever asked (the validation fixture's format-2 readback is the measured
     * case; vanilla's ScreenShotHelper is the mod-facing one).
     *
     * @param readFbo the GL name bound to {@code GL_READ_FRAMEBUFFER} (0 = the window)
     * @param address the caller's destination address
     * @return whether the pixels were written
     */
    public boolean celtium$readPixels(int readFbo, int x, int y, int width, int height,
            int format, int type, long address) {
        return this.textureCopyService.celtium$readPixels(readFbo, x, y, width, height, format, type, address);
    }

    /** The backend side of {@code glGetTexImage}, which is the read the game's own screenshot makes. */
    public boolean celtium$getTexImage(int textureName, int level, int format, int type, long address) {
        return this.textureCopyService.celtium$getTexImage(textureName, level, format, type, address);
    }

    /** As {@link #celtium$getTexImage(int, int, int, int, long)}, bounded to a pack buffer's remaining bytes. */
    public boolean celtium$getTexImage(int textureName, int level, int format, int type, long address, long capacity) {
        return this.textureCopyService.celtium$getTexImage(textureName, level, format, type, address, capacity);
    }

    /**
     * The backend side of {@link TextureCopyService.Host}: one method per fact the copy path reads.
     * <p>
     * Non-static so it reads the backend's own state, and every method is either a direct field read or the same call
     * the copy path made before the extraction - so the surface can be checked by reading it rather than by diffing the
     * moved code. Nothing here writes backend state; the four operations (flush, idle wait, diagnostics, registry) are
     * the copy's own ordering and reporting needs. This is the temporary boundary the refactoring plan records: the
     * follow-up narrows it to a ReadSource (image/view, extent, layout, generation, content version).
     */
    private final class CopyHost implements TextureCopyService.Host {
        @Override
        public VulkanContext context() {
            return CopyReadSide.this.backend.context;
        }

        @Override
        public FrameImageTarget frameTarget() {
            return CopyReadSide.this.backend.frameTarget;
        }

        @Override
        public GllessFrameTarget offTargetTarget() {
            return CopyReadSide.this.backend.offTargetTarget();
        }

        @Override
        public int targetWidth() {
            return CopyReadSide.this.backend.targetWidth;
        }

        @Override
        public int targetHeight() {
            return CopyReadSide.this.backend.targetHeight;
        }

        /** Bumped whenever the frame target is destroyed, so groups of the old target can be told apart. */
        @Override
        public long targetGeneration() {
            return CopyReadSide.this.backend.targetRegistry.generation();
        }

        @Override
        public long frameId() {
            return CopyReadSide.this.backend.frameId;
        }

        /**
         * The present serial, which is the counter the copy reuse set is keyed on: it advances for a screen's frame as
         * well, where {@link #frameId()} does not (see {@link TextureCopyService.Host#presentSerial()}).
         */
        @Override
        public long presentSerial() {
            return CopyReadSide.this.backend.celtium$presentCount;
        }

        /** The identity of the main framebuffer, so an unfiltered flush can mean "main" rather than "everything". */
        @Override
        public long mainTargetIdentity() {
            return CopyReadSide.this.backend.targetRegistry.mainIdentity();
        }

        @Override
        public int framebufferColorTextureName(long identity) {
            return CopyReadSide.this.backend.framebufferColorTextureName(identity);
        }

        @Override
        public long currentRenderTarget() {
            return CopyReadSide.this.backend.targetRegistry.currentIdentity();
        }

        @Override
        public boolean currentRenderTargetIsMain() {
            return CopyReadSide.this.backend.targetRegistry.currentIsMain();
        }

        @Override
        public long lastDrawnOffTargetIdentity() {
            return CopyReadSide.this.backend.lastDrawnOffTargetIdentity;
        }

        @Override
        public long lastDrawnOffTargetFrame() {
            return CopyReadSide.this.backend.lastDrawnOffTargetFrame;
        }

        @Override
        public long lastDrawnOffTargetPresent() {
            return CopyReadSide.this.backend.lastDrawnOffTargetPresent;
        }

        @Override
        public long lastDrawnImage() {
            return CopyReadSide.this.backend.lastDrawnImage;
        }

        @Override
        public long lastDrawnFrame() {
            return CopyReadSide.this.backend.lastDrawnFrame;
        }

        @Override
        public long lastDrawnEpoch() {
            return CopyReadSide.this.backend.lastDrawnEpoch;
        }

        @Override
        public long passAttachedColorImage() {
            return CopyReadSide.this.backend.passAttachedColorImage;
        }

        @Override
        public int passSubmissions() {
            return CopyReadSide.this.backend.terrainStatistics.passSubmissions();
        }

        @Override
        public int immediateWindowsThisFrame() {
            return CopyReadSide.this.backend.immediateStatistics.immediateWindowsThisFrame();
        }

        @Override
        public boolean immediateWindowBusy() {
            return CopyReadSide.this.backend.immediateWindowBusy;
        }

        @Override
        public int immediateGroupCount() {
            return CopyReadSide.this.backend.immediateGroups.size();
        }

        @Override
        public boolean worldPassActive() {
            return CopyReadSide.this.backend.worldPassActive;
        }

        public boolean hasPendingImmediateWork() {
            // The group being filled counts: the panorama copies a rectangle it drew into in the same call sequence,
            // so the draw is in this group and not in a queued one when the copy asks whether anything is pending.
            return CopyReadSide.this.backend.currentGroupUsed
                    || !CopyReadSide.this.backend.immediateGroups.isEmpty();
        }

        @Override
        public int recordedImmediateGroupCount() {
            return CopyReadSide.this.backend.recordedImmediateGroups.size();
        }

        @Override
        public int currentGroupBatches() {
            return CopyReadSide.this.backend.currentGroupBatches;
        }

        @Override
        public VulkanTextureRegistry registry() {
            return CopyReadSide.this.celtium$registry();
        }

        @Override
        public void flushPendingBeforeGlDraw() {
            // The copy host's flush is the copy path's ordering contract (TextureCopyService's own
            // statement of it), not a draw boundary: stamp it so its windows count as order.
            CopyReadSide.this.backend.pendingWindowTrigger = FramePassTimings.WINDOW_TRIGGER_ORDER;
            // The copy service's ordering flushes all arrive through this one delegation, so the E2
            // census stamps them here (item 695): copy-order is the call point, whichever copy asked.
            CopyReadSide.this.backend.celtium$flushPendingForGlDrawCause(
                    GlDrawWindowCensus.CAUSE_COPY_ORDER);
        }

        @Override
        public boolean submitCopySource() {
            if (CopyReadSide.this.backend.immediateWindowBusy
                    || CopyReadSide.this.backend.gpuStalled
                    || CopyReadSide.this.backend.context.celtium$isDeviceLost()
                    || hasPendingImmediateWork()) return false;
            CopyReadSide.this.backend.submitOpenFrameBuffer();
            if (CopyReadSide.this.backend.frameBufferOpen
                    || CopyReadSide.this.backend.commandState.recording()) return false;
            CopyReadSide.this.backend.context.celtium$flushBatchedSubmits();
            return !CopyReadSide.this.backend.gpuStalled
                    && !CopyReadSide.this.backend.context.celtium$isDeviceLost();
        }

        @Override
        public boolean waitForIdle() {
            return CopyReadSide.this.backend.waitForIdle();
        }

        @Override
        public long identityForColorTexture(int glTextureName) {
            return CopyReadSide.this.backend.celtium$framebufferIdentityForColorTexture(glTextureName);
        }

        @Override
        public long identityForFramebufferName(int framebufferObject) {
            return CopyReadSide.this.backend.celtium$identityForFramebufferName(framebufferObject);
        }

        @Override
        public void record(String diagnostic) {
            CopyReadSide.this.backend.record(diagnostic);
        }

        @Override
        public String describe(Throwable error) {
            return VulkanTerrainGpuBackend.describe(error);
        }
    }


    /**
     * Reads one owned image out and writes it, with its mean and opaque fraction on the line that names it.
     * <p>
     * Orientation, measured by proj-channel against the presented dump of the same frame: this readback's rows are
     * bottom-up relative to the presented-image dump, so comparing the two PNGs directly reports a ~98 % difference
     * that is pure orientation - {@code flipud} of one side differs on 0 of 921,600 pixels. Anyone comparing a
     * chain-time dump with a presented dump has to flip one of them first.
     */
    void celtium$dumpOwnedImage(String stage, long image, String base, String identity, int width,
            int height) {
        if (image == 0L || width <= 0 || height <= 0) {
            return;
        }
        int[] pixels = celtium$readImageRectangle(image, 0, 0, width, height);
        if (pixels == null) {
            System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=" + stage + " readback=failed " + identity);
            return;
        }
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
        java.nio.file.Path path = java.nio.file.Paths.get(stage.equals("frame") ? base
                : base.replaceFirst("\\.png$", "") + "-" + stage + ".png");
        boolean written = false;
        try {
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(width, height,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, width, height, pixels, 0, width);
            if (path.getParent() != null) {
                java.nio.file.Files.createDirectories(path.getParent());
            }
            written = javax.imageio.ImageIO.write(out, "png", path.toFile());
        } catch (java.io.IOException | RuntimeException | LinkageError error) {
            this.backend.record("chain frame dump write failed: " + VulkanTerrainGpuBackend.describe(error));
        }
        System.out.println("[Celtium] CELTIUM_CHAIN_FRAME_DUMP stage=" + stage + " path=" + path
                + " written=" + written + " " + width + "x" + height
                + " mean=" + (red / count) + "," + (green / count) + "," + (blue / count)
                + " opaque=" + opaque + "/" + pixels.length + " " + identity);
    }

    /** {@return the image a copy's read framebuffer names, or 0 when this renderer does not own it}. */




    /** Reverses a {@code width x height} rectangle's row order in place; the copy's GL-to-Vulkan row mapping. */


    /** {@return how many of the read pixels are not pure white}; a mean cannot tell that apart. */


    /**
     * {@return rectangle {@code width x height} at {@code x,y} of an owned colour image as ARGB texels}, or null.
     * <p>
     * The image is a colour attachment in {@code VK_IMAGE_LAYOUT_GENERAL} (both the off-target pairs and the frame image
     * are), and GENERAL is a legal transfer source - but the next frame attaches the image again, so the barrier pair
     * below is GENERAL to TRANSFER_SRC and back to GENERAL and the target's own layout bookkeeping stays true. The copy
     * goes through {@code VulkanContext.beginCommands}/{@code endCommands}, which waits for the queue before allocating
     * and after submitting: that is the same route the swapchain's own presented-image readback takes.
     */
    int[] celtium$readImageRectangle(long image, int x, int y, int width, int height) {
        // Thin delegate, kept because the chain-time dump (celtium$dumpOwnedImage) reads through this name; the
        // implementation moved to TextureCopyService with the rest of the copy path.
        return this.textureCopyService.readImageRectangle(image, x, y, width, height);
    }

    /** {@return whether a requested presented-image dump has been written}. */
    public boolean celtium$presentedImageDumpWritten() {
        return this.backend.presentedImageDumpRequested && this.backend.swapchain != null && this.backend.swapchain.lastDumpWritten();
    }

    /** {@return how many frames have been presented from Vulkan so far}. */
    public int celtium$presentedFrameCount() {
        return this.backend.framePresentedFromVulkan;
    }

    /**
     * {@return how many present calls the entry point declined before reaching the swapchain}.
     * <p>
     * Read beside {@link #celtium$presentAttempts()} and {@link #celtium$presentedFrameCount()} it turns a deficit
     * between the fixture's world frames and the presented counter into an attribution: a declined call is a frame that
     * was drawn and never offered to the swapchain, a failed attempt is one the swapchain refused.
     */
    public int celtium$presentSkips() {
        return this.backend.presentSkips;
    }

    /** {@return how many present calls reached the swapchain present}. */
    public int celtium$presentAttempts() {
        return this.backend.presentAttempts;
    }

    /**
     * {@return how many per-frame present calls arrived at all}.
     * <p>
     * The third term of the same arithmetic: a frame that never reaches the entry point is neither declined nor
     * failed, and only the entry counter can tell that apart from a present the renderer refused. It is not reset when
     * a world adopts the window-time backend, so it spans the whole process while
     * {@link #celtium$presentedFrameCount()} restarts with the world.
     */
    public int celtium$presentHookCalls() {
        return this.backend.presentHookCalls;
    }

    /**
     * {@return the presented-frame count of the present that wrote the named presented-image dump}, or -1 when no
     * write of that name has been recorded.
     * <p>
     * The fixture's file observation cannot name the frame on its own: it runs once per render tick, and several
     * presents fit inside that window at startup, so "the file appeared while the counter read N" is N frames
     * late. The write happens inside the present, which is what makes this the authoritative binding; the file
     * name is the key because several requests can be pending at once.
     */
    public int celtium$presentedDumpWrittenFrameFor(String fileName) {
        if (fileName == null) {
            return -1;
        }
        Integer frame = this.backend.presentedDumpWrittenFrames.get(fileName);
        return frame == null ? -1 : frame.intValue();
    }

    /**
     * Tells the renderer which texture unit Minecraft's light map is bound on.
     * <p>
     * The value lives in a Minecraft class, which this module deliberately does not depend on, so the mod side passes the
     * number in. It is needed because the reallocation path zeroes the light map's GL id, and a batch that arrives inside
     * that window is otherwise refused with no way to take the mirror again: with the unit known, the binding is asked of
     * GL exactly as the model draw asks for its texture.
     */
    public void celtium$setLightmapUnit(int unit) {
        this.backend.lightmapUnit = unit;
    }

    /**
     * The light-map coordinate pair of the draw that is about to happen, captured from
     * {@code OpenGlHelper.setLightmapTextureCoords} (SRG {@code func_77475_a}) by {@code LightmapCoordsMixin}.
     * <p>
     * This is the world light level, and it exists nowhere else for the model batch: the ITEM layout's vertices carry
     * position, texture, colour and normal, the trailing normal carries the fixed-function lighting, and the light
     * level only ever travelled as a texture coordinate on unit 1. Under the GL-less window that call is a no-op, so
     * without this capture a light-map sampler would read one wrong texel for every batch - which is why the term
     * could not be added to the shader alone.
     */
    public void setLightmapCoords(float x, float y) {
        this.backend.lightmapCoordX = x;
        this.backend.lightmapCoordY = y;
        this.backend.immediateStatistics.noteLightmapCoordCalls();
        this.backend.immediateStatistics.noteImmediateStateCalls();
    }

    /** {@return whether a light-map coordinate has been captured since the renderer came up}. */
    private boolean lightmapCoordKnown() {
        return this.backend.immediateStatistics.lightmapCoordCalls() > 0;
    }

    /** The light-map coordinate pair the last capture stored, as {x, y}. */
    float[] lightmapCoords() {
        // Held by queued groups and never written after: equal pairs share one array (2% of the client thread's
        // allocation was these two floats, one pair per model instance).
        float[] last = this.lastLightmapCoords;
        if (last != null && last[0] == this.backend.lightmapCoordX && last[1] == this.backend.lightmapCoordY) {
            return last;
        }
        last = new float[] {this.backend.lightmapCoordX, this.backend.lightmapCoordY};
        this.lastLightmapCoords = last;
        return last;
    }

    private float[] lastLightmapCoords;

    /**
     * The full-bright light-map coordinate, the pair {@code GuiContainer.java:107} sets before it draws its items:
     * {@code OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0F, 240.0F)}. The light map's
     * texel there is its brightest, which is what "GUI items are drawn full-bright" is in a renderer whose lit layouts
     * always sample the light map.
     */
    private static final float[] FULL_BRIGHT_LIGHTMAP = {240.0F, 240.0F};

    /**
     * {@code -Dceltium.vulkan.lightmapFullBrightGui=false} turns the rule off, for measurements: the same jar, the same
     * scene and the same tick then draw a GUI item with the world's light, which is the "before" half of the pair
     * without rebuilding a jar that differs in anything else.
     */
    private static final boolean LIGHTMAP_FULL_BRIGHT_GUI = !"false".equalsIgnoreCase(
            System.getProperty("celtium.vulkan.lightmapFullBrightGui", "true"));

    /**
     * The light-map coordinate a group offered now must be drawn with: the captured world light where the light-map
     * texture unit was **enabled**, the full-bright pair where it was disabled.
     * <p>
     * The key is the unit's own enable state, not the pass, because that is what real OpenGL modulates by. The unit is
     * enabled in exactly three brackets - {@code RenderGlobal.renderEntities} ({@code RenderGlobal.java:1166}/{@code :1205}),
     * the first-person hand ({@code EntityRenderer.java:825}/{@code :827}, inside {@code renderWorldPass}) and the
     * block-entity section ({@code EntityRenderer.java:1434}/{@code :1403}) - and disabled everywhere else, including
     * the whole GUI stage: {@code GuiIngame.renderHotbar} never sets the coordinate while
     * {@code GuiContainer.java:107} sets {@code (240,240)} for its own items. This renderer's lit layouts always sample
     * the light map, so a draw made with the unit disabled has to be given the full-bright texel, or the coordinate the
     * world last set (the player's own light, {@code ItemRenderer.setLightmap()}) reaches the hotbar and a GUI item in a
     * dark place is drawn with the cave's light.
     * <p>
     * The state is a hook on {@code EntityRenderer.enableLightmap}/{@code disableLightmap} (SRG
     * {@code func_180436_i}/{@code func_175072_h}, resolved from the SRG table rather than guessed) and it defaults to
     * <em>enabled</em>: a hook that never fires leaves every group on the captured coordinate, which is the behaviour
     * this renderer had before the rule, so a silently dropped injection degrades to the status quo rather than to a
     * frame of full-bright entities. {@code lightmapUnitEnables}/{@code lightmapUnitDisables} in the census say whether
     * the hook ran at all.
     * <p>
     * The model path uses this too. Its GUI-stage content is the inventory's player model and any TESR item - a chest,
     * a shulker box, a banner, which {@code RenderItem.renderItem} sends through
     * {@code getTileEntityItemStackRenderer().renderByItem} ({@code RenderItem.java:144-148}) - and both are drawn with
     * the unit disabled, so both are full-bright under real GL. The inventory's player model is the reason this is
     * checked rather than assumed: {@code GuiInventory.java:154-165} only <em>disables</em> the unit at the end of its
     * draw, it never enables it, so nothing lights that model by the world either.
     */
    float[] celtium$lightmapForGroup() {
        if (!this.backend.lightmapUnitEnabled && LIGHTMAP_FULL_BRIGHT_GUI) {
            this.backend.immediateStatistics.noteLightmapFullBrightGroups();
            return FULL_BRIGHT_LIGHTMAP;
        }
        return lightmapCoordKnown() ? lightmapCoords() : null;
    }

    /**
     * Points the game's main framebuffer at the shared images so the frame is produced in Vulkan-owned memory.
     * <p>
     * On by default since both GPUs accepted it with the same picture: AMD 0.001 mean absolute difference and 0.00 % of
     * pixels over 8 against the GL path in the same scene, Intel 0.026 and 0.02 %, both PASSED with no GL error and
     * nothing left refused for a reason of its own. Set {@code -Dceltium.vulkan.adoptGameFramebuffer=false} to go back to
     * the per-frame copy into OpenGL's framebuffer, which is what the migration still supports but no longer uses.
     */
    private static final boolean ADOPT_GAME_FRAMEBUFFER =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.adoptGameFramebuffer", "true"));

    /**
     * Prepares the frame target for whatever framebuffer is bound right now and answers whether it is ready.
     * <p>
     * The adoption runs after the game has bound its framebuffer, not before: {@code ensureFrameTarget} reads the size of
     * the bound colour texture, and before the bind that is still the previous framebuffer. Doing it in that order made a
     * resized window keep the old shared images - measured: after a mid-run resize the game bound a 1024x640 framebuffer
     * while the shared images were 1280x720, {@code adoptableFramebuffer} answered 0, and no present came from Vulkan
     * again (265 presents against 785 in the same duration without a resize).
     */
    public boolean celtium$ensureTargetForBoundFramebuffer() {
        boolean ready = ensureFrameTarget();
        if (ready) {
            // The shared images match the framebuffer that is bound now, so the replacement window is over and ordering
            // flushes are meaningful again.
            this.backend.targetRegistry.noteReplacementSettled();
        }
        return ready;
    }

    /**
     * Ensures the frame target, falling back to the framebuffer object's own size when the bound attachment cannot be
     * measured.
     * <p>
     * The bind-returns hook has the framebuffer object, whose {@code framebufferWidth}/{@code framebufferHeight} stay
     * correct even when the colour attachment is a shared image that was released with a previous backend instance -
     * reading that attachment gives 0x0, and without a size no target can be built, which is the deadlock the Nether
     * dimension transfer left behind (every pass skipped on "no frame target"). The size hint is only used for the
     * game's main framebuffer, whose logical size is the same size the target must cover anyway.
     */
    public boolean celtium$ensureTargetForBoundFramebuffer(int widthHint, int heightHint) {
        boolean ready = ensureFrameTarget();
        if (!ready && widthHint > 0 && heightHint > 0) {
            ready = ensureFrameTarget(widthHint, heightHint);
        }
        if (ready) {
            this.backend.targetRegistry.noteReplacementSettled();
        }
        return ready;
    }

    public int celtium$adoptableFramebuffer(int width, int height) {
        if (!ADOPT_GAME_FRAMEBUFFER || this.backend.frameTarget == null) {
            return 0;
        }
        return this.backend.frameTarget.adoptableFramebuffer(width, height);
    }

    public int celtium$adoptedColorTexture() {
        return this.backend.frameTarget == null ? 0 : this.backend.frameTarget.sharedColorTexture();
    }

    public int celtium$adoptedDepthTexture() {
        return this.backend.frameTarget == null ? 0 : this.backend.frameTarget.sharedDepthTexture();
    }

    public void celtium$noteFramebufferAdopted() {
        this.backend.gameFramebufferAdopted++;
        this.backend.gameFramebufferIsShared = true;
        if (this.backend.frameTarget != null) {
            this.backend.frameTarget.noteGameFramebufferAdopted();
        }
    }

    public boolean celtium$isSharedFramebuffer(int framebufferObject) {
        return ADOPT_GAME_FRAMEBUFFER && this.backend.frameTarget != null
                && this.backend.frameTarget.isSharedFramebuffer(framebufferObject);
    }

    /**
     * Records the first GL error seen at a named point after the swap, and where it was seen.
     * <p>
     * Reading a GL error clears it, so only the first probe that finds one can speak: it names the window the error was
     * produced in. Probes after it will read zero whatever happens, which is why the point is recorded with the code.
     */
    public void celtium$noteGlErrorAt(String where, int error) {
        if (error != 0 && this.backend.firstGlErrorWhere == null) {
            this.backend.firstGlErrorWhere = where;
            this.backend.firstGlErrorCode = error;
        }
    }


    /** Static probe for the font-glyph diagnostic: the mirror dims for a bound GL name, or "none"/"nobackend". */
    public static String celtium$mirrorDims(int glTextureId) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (backend instanceof VulkanTerrainGpuBackend vulkan) {
            return vulkan.celtium$registry().celtium$entryDims(glTextureId);
        }
        return "nobackend";
    }

    /** The probe spec naming which mirrors {@link #celtium$mirrorProbe} dumps: {@code -Dceltium.mirrorDump}. */
    private static final String MIRROR_DUMP_SPEC = System.getProperty("celtium.mirrorDump");

    /** The box-glyph hunt: the mirror's own pixels, read back, so "the GPU image holds the rasterised sheet" is
     *  measured and not assumed. {@code -Dceltium.mirrorDump=<name>:<path>} dumps the whole level-0 rect once. */
    public static String celtium$mirrorProbe(int glTextureId) {
        org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend backend =
                org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends.current();
        if (!(backend instanceof VulkanTerrainGpuBackend vulkan)) {
            return "nobackend";
        }
        String spec = MIRROR_DUMP_SPEC;
        String dumpPath = null;
        if (spec != null) {
            // Comma-separated <name>:<path> pairs so one leg can dump several mirrors; a path prefix without a
            // name ("all=/tmp/m-") dumps every texture the probe touches, named by its GL id.
            for (String pair : spec.split(",")) {
                int colon = pair.indexOf(':');
                if (colon > 0) {
                    if ("all".equals(pair.substring(0, colon))) {
                        dumpPath = pair.substring(colon + 1) + glTextureId + ".png";
                        break;
                    }
                    if (pair.substring(0, colon).equals(Integer.toString(glTextureId))) {
                        dumpPath = pair.substring(colon + 1);
                        break;
                    }
                }
            }
        }
        if (dumpPath == null) {
            return "skip";
        }
        org.soutalab.celtium.vulkan.VulkanGlTexture texture = vulkan.celtium$registry().get(glTextureId);
        if (texture == null) {
            return "nomirror";
        }
        int w = texture.width(), h = texture.height();
        int[] pixels = vulkan.textureCopyService.readImageRectangle(texture.image(), 0, 0, Math.min(w, 1024), h);
        try {
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(Math.min(w, 1024), h,
                    java.awt.image.BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, Math.min(w, 1024), h, pixels, 0, Math.min(w, 1024));
            javax.imageio.ImageIO.write(out, "png", new java.io.File(dumpPath));
            return "dumped";
        } catch (Throwable t) {
            return "fail:" + t;
        }
    }

    /**
     * The one texture registry.
     * <p>
     * It was created inline at five call sites, which is five chances for two registries to exist and for the image the
     * backend samples to differ from the image the announcements patch - the split this change removes. Creation stays lazy
     * because the first texture registration happens before any world draw.
     */
    VulkanTextureRegistry celtium$registry() {
        if (this.backend.textureRegistry == null) {
            this.backend.textureRegistry = new VulkanTextureRegistry(this.backend.context);
            // Stage 5 stage 3 (section 3.5): the registry's lifecycle hooks feed the context's one
            // bindless binding from here on. Injection happens at creation, before any texture
            // exists in the registry, so no registration can slip past the hook; a null binding
            // (device without array indexing, or the kill switch) leaves the hooks inert.
            this.backend.textureRegistry.setBindlessBinding(this.backend.context.bindlessBinding());
        }
        return this.backend.textureRegistry;
    }

    /**
     * {@return one line's worth of what this renderer holds for a texture name, for the legacy-run census}.
     * <p>
     * The menu's missing background has two candidate homes - the quad was never written, or it was written sampling a
     * mirror with no content - and the capture alone cannot tell them apart. The size and completeness of the mirror
     * the run's texture resolves to is the fact that separates them; the source label says which route filled it.
     */
    public String celtium$textureCensus(int glTextureId) {
        if (glTextureId <= 0) {
            return "none";
        }
        try {
            VulkanGlTexture texture = celtium$registry().get(glTextureId);
            if (texture == null) {
                return "absent source=" + celtium$registry().celtium$mirrorSourceFor(glTextureId);
            }
            return texture.width() + "x" + texture.height() + " full=" + texture.hasFullContent()
                    + " source=" + celtium$registry().celtium$mirrorSourceFor(glTextureId);
        } catch (RuntimeException | LinkageError error) {
            return "unreadable:" + error.getClass().getSimpleName();
        }
    }

    /**
     * {@return the mirrored matrices an immediate group is about to be transformed by}.
     * <p>
     * Printed beside a legacy run because the placement of a quad is decided here: a full-screen background that
     * arrives with an on-screen GL rectangle and is still absent from the frame cannot be explained by the vertices,
     * and the projection's two diagonal terms plus the modelview's translation are what say whether the group landed
     * where the game asked for.
     */
    public String celtium$immediateMatrixState() {
        // The modelview's z translation is printed with the x and y: the GUI's ortho maps z=0 to the near plane and
        // the overlay rendering puts the eye at z=-2000, so a group recorded without that translation lands outside
        // the clip volume and vanishes even though its x/y rectangle is on screen.
        return "proj=" + VulkanTerrainGpuBackend.round3(this.backend.glProjection[0]) + "," + VulkanTerrainGpuBackend.round3(this.backend.glProjection[5])
                + " mv=" + VulkanTerrainGpuBackend.round3(this.backend.glModelView[0]) + "," + VulkanTerrainGpuBackend.round3(this.backend.glModelView[5])
                + " mvT=" + VulkanTerrainGpuBackend.round3(this.backend.glModelView[12]) + "," + VulkanTerrainGpuBackend.round3(this.backend.glModelView[13]) + ","
                + VulkanTerrainGpuBackend.round3(this.backend.glModelView[14]);
    }

    boolean ensureFrameTarget() {
        // The window's own framebuffer size is what the frame target must match: it is the drawable the swapchain
        // presents, and it does not move when the game binds one of its own framebuffers. Sizing from the GL viewport
        // instead adopted whatever framebuffer was bound last - the splash's own FBO during startup - so the target
        // (and with it the pairs and every name published over them) was created at the wrong size and then rebuilt
        // when the main framebuffer arrived, which threw the splash's pixels away and left the composite sampling an
        // empty image: the white loading screen. The main framebuffer's reported size is the second choice, the
        // viewport the last.
        int[] window = cachedWindowFramebufferSize();
        if (window[0] > 0 && window[1] > 0) {
            return ensureFrameTarget(window[0], window[1]);
        }
        int[] drawable = currentViewport();
        int width = this.backend.mainFramebufferWidth > 0 ? this.backend.mainFramebufferWidth : drawable[2];
        int height = this.backend.mainFramebufferHeight > 0 ? this.backend.mainFramebufferHeight : drawable[3];
        return ensureFrameTarget(width, height);
    }

    /**
     * Ensures the frame target exists for the given size, without asking the bound framebuffer how big it is.
     * <p>
     * The adoption path passes the framebuffer object's own width and height, which are the authoritative size there.
     * A world change recreates the renderer and this backend but not the framebuffer, and a size cached from the old
     * target would otherwise leave the target, and with it every draw, unrecoverable.
     */
    boolean ensureFrameTarget(int width, int height) {
        if (this.backend.frameTarget != null && this.backend.frameTarget.matches(width, height)) {
            // Direct present path: the frame's swapchain borrow is established here, on the render path,
            // so it exists before the frame's first pass records against the target (a no-op while a
            // borrow is live or a recording is open).
            this.backend.presentSwapchain.celtium$ensureBorrowedFrameImage();
            return true;
        }
        if (this.backend.frameTarget != null && this.backend.celtium$recordingOpen()) {
            // A size change can arrive while the frame's unified command buffer is still recording - the real launch
            // does exactly this once, when the window it opened at 854x480 goes fullscreen at the monitor size, and the
            // first attempt released the old pair's images and its framebuffer under the open recording. The validation
            // layer named it (VUID-vkEndCommandBuffer-commandBuffer-00059: two images and a framebuffer destroyed while
            // bound) and the next submission took the device down, which was the black screen. A target whose size has
            // to change is therefore refused for this recording and rebuilt on the next one, when nothing is open.
            this.backend.frameTargetRecreationDeferred++;
            if (this.backend.frameTargetRecreationDeferred <= 8) {
                System.out.println("[Celtium] CELTIUM_FRAME_TARGET_DEFERRED from=" + this.backend.targetWidth + "x"
                        + this.backend.targetHeight + " to=" + width + "x" + height
                        + " count=" + this.backend.frameTargetRecreationDeferred + " (a recording is open)");
            }
            return false;
        }
        // The GPU must be finished with the old target before any of it is released: a resize recreates the game's
        // framebuffer, and the validation layer caught the first attempt doing exactly this -
        // "vkDestroyFramebuffer(): can't be called on VkFramebuffer ... that is currently in use by VkCommandBuffer".
        // The same rule as delete() and region eviction, applied to the path a resize takes.
        if (!this.backend.celtium$settleGpuForReadSide("frame target recreation")) {
            // Fail closed: without a settled GPU the old target cannot be released, so no new one is built either.
            return false;
        }
        if (!VulkanTerrainGpuBackend.celtium$frameTargetReplaceable(this.backend.destroyFramebuffer())) {
            // The framebuffers could not be freed, so they still name the old target's views: deleting them here is
            // what left a later window's begin pointing at a dead view (issue #423, item 372 hole 1). Same direction
            // as the failed settle above - the old target keeps drawing, the recreation is retried on a later frame.
            this.backend.record("frame target " + width + "x" + height + " recreation refused: its framebuffers could not be freed");
            return false;
        }
        if (this.backend.frameTarget != null) {
            this.backend.frameTarget.delete();
            this.backend.frameTarget = null;
            // Groups recorded against the old target may still be queued; the generation is what lets the draw refuse them
            // instead of binding a render pass that no longer exists. Both the recreate path and the teardown path bump it.
            this.backend.targetRegistry.noteFrameTargetReplaced();
        }
        // Stage 1 BGRA ignition: the new target's colour format follows the swapchain's, so the present
        // path gets its chance to create a generation before the target reads the decision - otherwise
        // the first target of a session would fix the frame at the classic format from an uninformed
        // state and no BGRA surface could ever be borrowed. A failed creation is not fatal here: the
        // target then takes the classic format and the session presents composed (the context records
        // that degradation at the note).
        this.backend.presentSwapchain.ensureSwapchain();
        try {
            // There is no GL side to share images with: the frame lands in an owned target whose semaphores are
            // signalled and consumed by Vulkan submissions alone.
            this.backend.frameTarget = GllessFrameTarget.create(this.backend.context, width, height);
            // GLSM stage 2: the new target latches its frame clears into the backend's shared
            // frame state on the stage-2 route (a no-op shape off it - the attach is cheap
            // and the target only reads the state when the route is on).
            this.backend.celtium$attachFrameState();
            if (this.backend.gameFramebufferIsShared) {
                this.backend.frameTarget.noteGameFramebufferAdopted();
            }
            this.backend.targetWidth = width;
            this.backend.targetHeight = height;
            // B10 of the maximum audit: the post chain's executor holds the frame target every `main` pass input/output
            // resolves against, and it used to keep the one it was created with. The old target's images are destroyed
            // just above, so the executor has to move with the replacement before any chain is recorded against it;
            // otherwise a chain recorded after this point would name freed memory. The generation is the target
            // replacement count, and the executor refuses a recording whose generation moved underneath it.
            if (this.backend.postChain != null) {
                this.backend.postChain.adoptFrameTarget(this.backend.frameTarget, this.backend.targetRegistry.generation());
            }
            // The fresh target's first frame borrows its swapchain image from here (see the match path).
            this.backend.presentSwapchain.celtium$ensureBorrowedFrameImage();
            return true;
        } catch (RuntimeException | LinkageError error) {
            this.backend.record("frame target " + width + "x" + height + " failed: " + VulkanTerrainGpuBackend.describe(error));
            return false;
        }
    }





    /** Reused by the GL fallback below: the immediate path asks for the viewport once per group, and a fresh direct
     * buffer per call was ~1.4% of the client thread's samples on the dense world scene (JFR line census). */
    private static final IntBuffer VIEWPORT_SCRATCH = org.lwjgl.BufferUtils.createIntBuffer(4);

    private static long lastViewportPacked = Long.MIN_VALUE;
    private static int[] lastViewport;

    static int[] currentViewport() {
        // Under the GL-less window the answer is the rectangle the game last set, kept by the stub: asking GL through
        // the direct buffer below costs an allocation and a query for every group.
        long packed = org.soutalab.celtium.vulkan.gl.GlsmFrontend.celtium$viewportPacked();
        if (packed >= 0L) {
            // Held by queued groups and never written: the same rectangle shares one array (a new one per group was
            // 1.7% of the client thread's allocation). Render thread only.
            if (packed == lastViewportPacked && lastViewport != null) {
                return lastViewport;
            }
            lastViewportPacked = packed;
            return lastViewport = new int[] { (int) ((packed >> 48) & 0xFFFF) - (((packed >> 48) & 0x8000) != 0 ? 0x10000 : 0),
                    (int) ((packed >> 32) & 0xFFFF) - (((packed >> 32) & 0x8000) != 0 ? 0x10000 : 0),
                    (int) ((packed >> 16) & 0xFFFF), (int) (packed & 0xFFFF) };
        }
        IntBuffer viewport = VIEWPORT_SCRATCH;
        org.lwjgl.opengl.GL11C.glGetIntegerv(org.lwjgl.opengl.GL11C.GL_VIEWPORT, viewport);

        int width = viewport.get(2);
        int height = viewport.get(3);

        // GL's default viewport is the whole drawable, and under the GL-less window the stub answers this query with
        // zeros, so a zero here means "never set" rather than "no area". Measured on leg gv-cohort-mesh1: the sections
        // were built (sectionsWithGeometry=67) and the render lists were full, but CELTIUM_REFUSALS_BY_FORMAT read
        // "target: the viewport 0,0 0x0 has no area" for 140+ draws per frame, so no draw reached the backend and the
        // present had nothing to take. windowFramebufferSize() is the drawable the default would have described.
        if (width <= 0 || height <= 0) {
            int[] drawable = cachedWindowFramebufferSize();
            if (drawable[0] > 0 && drawable[1] > 0) {
                return new int[] { 0, 0, drawable[0], drawable[1] };
            }
        }

        return new int[] { viewport.get(0), viewport.get(1), width, height };
    }

    /**
     * Last window drawable size, and when it was read. {@code glfwGetFramebufferSize} on X11 is a server round-trip
     * and the viewport fallback asks for it on paths that run many times per frame: JFR native samples measured
     * {@code GLFW.nglfwGetFramebufferSize} as the top native frame on the client thread (502 of 1,348 samples, ~4% of
     * the thread's own CPU) with this fallback as the only per-frame caller.
     */
    static int[] windowSizeCache;
    private static long windowSizeCacheNanos;
    /**
     * How long a cached drawable size is trusted. The cache is also dropped the moment the game's own framebuffer
     * reports a different size ({@link #celtium$noteBindSize}), so a resize is picked up on the bind that follows it,
     * not after this interval; the interval only bounds the case where nothing binds (early startup).
     */
    private static final long WINDOW_SIZE_CACHE_NANOS = 200_000_000L;

    static int[] cachedWindowFramebufferSize() {
        int[] cached = windowSizeCache;
        long now = System.nanoTime();
        if (cached != null && now - windowSizeCacheNanos < WINDOW_SIZE_CACHE_NANOS) {
            return cached;
        }
        int[] fresh = windowFramebufferSize();
        if (fresh[0] > 0 && fresh[1] > 0) {
            windowSizeCache = fresh;
            windowSizeCacheNanos = now;
        }
        return fresh;
    }

    /**
     * {@return the GLFW window's framebuffer size}, or {@code {0, 0}} when the window is unknown or has no drawable.
     * <p>
     * The window handle is the current context under a real GL configuration and comes from {@code Display} under the
     * GL-less window, where there is no context to ask (reflective, because celtium-vulkan does not depend on the
     * lwjglxx shim). This is the size the frame target must have: it is the drawable the swapchain presents.
     */
    private static int[] windowFramebufferSize() {
        // SDL first: on Cleanroom 0.7 the GLFW classes are not on the runtime classpath, so this method used to throw
        // NoClassDefFoundError the first time the swap chain asked how large the drawable is.
        if (CeltiumWindow.sdl()) {
            int[] pixels = new int[] {0, 0};
            if (CeltiumWindow.sdlPixelSize(pixels)) {
                return pixels;
            }
        }
        long window = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            try {
                Class<?> display = Class.forName("org.lwjgl.opengl.Display");
                window = ((Long) display.getMethod("getWindow").invoke(null)).longValue();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // No window handle from here; the caller falls back to the framebuffer or the viewport.
            }
        }
        if (window == 0L) {
            return new int[] {0, 0};
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer width = stack.mallocInt(1);
            IntBuffer height = stack.mallocInt(1);
            org.lwjgl.glfw.GLFW.glfwGetFramebufferSize(window, width, height);
            return new int[] {width.get(0), height.get(0)};
        }
    }

    int groupDepthRowChecks;

    /**
     * Takes both directly sampled textures' mirrors: the block atlas and the light map.
     * <p>
     * Two methods rather than one, because the atlas's "no storage yet" answer used to be a {@code return} from this
     * method - and the atlas block sits above the light map's, so a frame in which the atlas had no extent also skipped
     * the light map's establishment. Each texture's answer now ends its own method.
     */
    void refreshTextures() {
        refreshAtlasMirror();
        refreshLightmapMirror();
    }

    /** Takes the block atlas's mirror when its name is known, on the cadence the terrain sampler needs. */
    private void refreshAtlasMirror() {
        // A terrain material names the atlas and lightmap resources. Whichever GUI/entity texture happened to be
        // bound last is not the terrain material, and querying that binding must not mutate the active unit.
        //
        // The fallback that used to sit here asked the same source a second time when the first answer was <= 0, so it
        // could never change the answer: the GL binding is not available without a GL context, and 1.12.2 binds the
        // atlas through paths that skip the call (leg gv-cohort-dev10: atlasGlId stayed -1, the pass was skipped and
        // draws stayed 0). The atlas object owns its name, and that is the one source this reads.
        int atlasId = LightmapIdSource.atlasTextureId();
        // CELTIUM_TEXTURE_DIAG: off by default. The atlas is the texture whose mip state matters, and this is the
        // only method that ever mirrors it, so the question is asked here - with the binding read rather than set, so
        // nothing the game is drawing can be disturbed.
        if (VulkanTerrainGpuBackend.TEXTURE_DIAGNOSTICS && atlasId != 0 && this.backend.diagnosedTextures.add(atlasId)) {
            int levels = 0;
            while (levels < 16 && org.lwjgl.opengl.GL11.glGetTexLevelParameteri(
                    org.lwjgl.opengl.GL11.GL_TEXTURE_2D, levels, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH) > 0) {
                levels++;
            }
            System.out.println("[Celtium] CELTIUM_TEXTURE_DIAG atlas=" + atlasId + " levels=" + levels
                    + " maxLevel=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813D)
                    + " minLod=" + org.lwjgl.opengl.GL11.glGetTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813A)
                    + " maxLod=" + org.lwjgl.opengl.GL11.glGetTexParameterf(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x813B)
                    + " minFilter=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x2801)
                    + " magFilter=" + org.lwjgl.opengl.GL11.glGetTexParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0x2800)
                    + " atlasSize=" + org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_WIDTH)
                    + "x" + org.lwjgl.opengl.GL11.glGetTexLevelParameteri(org.lwjgl.opengl.GL11.GL_TEXTURE_2D, 0, org.lwjgl.opengl.GL11.GL_TEXTURE_HEIGHT));
        }


        if (atlasId != 0 && (this.backend.atlasTexture == null || atlasId != this.backend.atlasGlId || this.backend.atlasRefreshCountdown <= 0)) {
            try {
                // The image this call supplies, decided before anything is handed to the field. A name change is a
                // reallocation: the registry entry for the old name was retired with it, so binding the kept object
                // samples a dead image - measured as a fully black terrain after the reload re-stitched the atlas
                // under a new GL name while this field still held the old mirror.
                VulkanGlTexture image = this.backend.atlasTexture != null && atlasId == this.backend.atlasGlId
                        ? this.backend.atlasTexture : null;
                if (image == null) {
                    int[] size = textureSize(atlasId);
                    if (size[0] <= 0 || size[1] <= 0) {
                        // No storage to mirror yet. Creating an image of an unknown size is what produced a 1x1
                        // mirror that the upload then had to replace, so the frame is skipped and the question is
                        // asked again when the atlas exists.
                        this.backend.atlasRefreshCountdown = 0;
                        return;
                    }
                    // The registry owns the image; the backend samples that same object, so an announced patch and the
                    // descriptor look at one image instead of two.
                    image = celtium$registry().imageFor(atlasId, size[0], size[1], true);
                }
                this.backend.celtium$establishAtlasImage(atlasId, image, "atlas");
                // The field names the mirror only once its supply has run. The terrain pass's skip guard asks this
                // field for null, so assigning before the supply - which is what this method did - is how a supply that
                // threw left an image no upload ever filled in the field: the pass then drew it as the atlas instead of
                // skipping with the reason recorded. A mirror this id already had stays in the field across a failed
                // re-supply, because that image was established by an earlier one.
                this.backend.atlasTexture = image;
                // The terrain sampler asks the atlas for its GL name to read the game's GL_TEXTURE_MAX_LEVEL.
                image.noteGlTextureName(atlasId);
                this.backend.atlasSourceGlId = atlasId;
                int[] atlasSize = textureSize(atlasId);
                this.backend.record("block atlas: gl texture " + atlasId + " " + atlasSize[0] + "x" + atlasSize[1]);
                this.backend.atlasGlId = atlasId;
                this.backend.atlasRefreshCountdown = 400;
            } catch (RuntimeException | LinkageError error) {
                this.backend.record("block atlas upload failed: " + VulkanTerrainGpuBackend.describe(error));
                this.backend.atlasRefreshCountdown = 400;
            }
        }
        this.backend.atlasRefreshCountdown--;
    }

    /**
     * Takes the light map mirror: the one route that can establish it, plus the per-frame refresh it needs.
     * <p>
     * The light map changes every frame (torch flicker, sun angle, weather), and it is small, so it is refreshed every
     * frame instead of on a cadence: a stale copy shows up as a small uniform colour shift against the OpenGL render.
     */
    private void refreshLightmapMirror() {
        int lightmapId = LightmapIdSource.lightmapTextureId();
        if (lightmapId <= 0) {
            // No name to mirror: the source is not installed yet, or it answered its "cannot answer" -1. A negative id
            // used to reach imageFor() as an extent of 0x0, which threw IllegalArgumentException into the catch below on
            // every frame of the window before the game side installed the supplier.
            this.backend.lightmapRefreshCountdown--;
            return;
        }
        if (!(this.backend.lightmapTexture == null || lightmapId != this.backend.lightmapGlId
                || this.backend.lightmapRefreshCountdown <= 0)) {
            this.backend.lightmapRefreshCountdown--;
            return;
        }
        try {
            if (this.backend.lightmapTexture == null || lightmapId != this.backend.lightmapGlId) {
                // A different name owns a different mirror. The old image stays in the registry under the name it was
                // created for; what must not happen is this field naming it for the new id.
                this.backend.lightmapTexture = null;
                this.backend.lightmapGlId = lightmapId;
                // The one route that can establish the light map: the DynamicTexture's own pixels answer both its
                // content and, when the stub recorded no extent, its size. Creating the image here and supplying it
                // below was the route that could not succeed - its readback always misses the light map's NULL-data
                // level - and the content-less field it left behind switched the working route off for the rest of the
                // run (see ensureLightmapMirror).
                this.backend.ensureLightmapMirror();
            }
            if (this.backend.lightmapTexture != null) {
                if (this.backend.lightmapIdentityLogs < 4) {
                    this.backend.lightmapIdentityLogs++;
                    org.soutalab.celtium.vulkan.VulkanGlTexture registryEntry =
                            celtium$registry().get(lightmapId);
                    this.backend.record("lightmap identity: glId=" + lightmapId
                            + " boundView=" + Long.toHexString(this.backend.lightmapTexture.view())
                            + " boundFull=" + this.backend.lightmapTexture.hasFullContent()
                            + " registryView=" + (registryEntry == null ? "none"
                                    : Long.toHexString(registryEntry.view()))
                            + " registryFull=" + (registryEntry == null ? "none"
                                    : Boolean.toString(registryEntry.hasFullContent()))
                            + " same=" + (registryEntry == this.backend.lightmapTexture));
                }
                // Back on the announcement path, which is the light map's real source: the black it produced was the
                // announced array's shape (one int[] per row where the consumer reads one int[] for the whole of level
                // 0), not this call. With the producer fixed, an announcement-only run measures the same band as the
                // readback did - 102.4/63.9/58.5 - so the readback detour is gone and the image has one owner again.
                this.backend.celtium$establishAtlasImage(lightmapId, this.backend.lightmapTexture, "lightmap");
                this.backend.lightmapSourceGlId = lightmapId;
                int[] lightmapSize = textureSize(lightmapId);
                this.backend.record("lightmap: gl texture " + lightmapId + " " + lightmapSize[0] + "x" + lightmapSize[1]);
                this.backend.lightmapGlId = lightmapId;
                this.backend.lightmapRefreshCountdown = 1;
            } else {
                // Nothing could supply it this pass. The field stays null so the pass-skip guard names the light map as
                // the mirror that is missing, instead of drawing with an image no upload ever filled; the next
                // attempt is the one ensureLightmapMirror bounds by the level's content serial.
                this.backend.lightmapRefreshCountdown = 1;
            }
        } catch (RuntimeException | LinkageError error) {
            this.backend.record("lightmap upload failed: " + VulkanTerrainGpuBackend.describe(error));
            this.backend.lightmapRefreshCountdown = 1;
        }
        this.backend.lightmapRefreshCountdown--;
    }

    /**
     * The size of a GL texture's level 0, asked through the binding rather than through direct state access.
     * <p>
     * The extent comes from the allocation the stub recorded, not from a bound GL query: {@code glGetTextureLevelParameteri}
     * needs GL 4.5 or {@code ARB_direct_state_access}, and there is no context to ask anyway.
     */
    static int[] textureSize(int texture) {
        if (texture == 0) {
            return new int[] { 0, 0 };
        }
        // Native GL bind/query entry points may be replaced; the named allocation owns this texture's extent.
        return new int[] {
                org.soutalab.celtium.vulkan.gl.GlsmFrontend.textureLevelParameter(texture, 0, 0x1000),
                org.soutalab.celtium.vulkan.gl.GlsmFrontend.textureLevelParameter(texture, 0, 0x1001) };
    }
}
