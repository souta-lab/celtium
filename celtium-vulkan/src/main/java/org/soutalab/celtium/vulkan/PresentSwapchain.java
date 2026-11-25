package org.soutalab.celtium.vulkan;

/**
 * The present and swapchain cluster, extracted from {@code VulkanTerrainGpuBackend} (refactoring plan R10,
 * seam 4 of item 704: the head and tail partial cluster of the frame-target region, about 870 lines).
 * <p>
 * What lives here is the swapchain's life cycle at the head of the region - the rebuild handover, the
 * lost-surface drop, the held presented-image dumps' forwarding, the lazy creation and its escalation
 * verdict, and the one-shot probe - and the present path at its tail: the window's render-pass begin
 * info, the present entry and its body, the frame dumps the present writes, the event poll that replaces
 * the game's own swap, and the presented-image accessors. The region's middle (the target state, the
 * statistics and census summaries, the push census and the debug mirrors) is not a unit and stays on the
 * backend, as do the present counters and the swapchain field itself: their readers are the whole
 * renderer, so this class reads them through the backend. The state that was already local to this
 * cluster (the kept surface, the cached window-begin struct) moved with the code. Every entry the rest
 * of the renderer calls keeps its name and signature on the backend as a delegating door, so no caller
 * changed. The swap-point accounting ({@code swapPoint*}) also lives here: it measures this present path,
 * and its one writer outside it (the boundary flush's share) reaches it through the backend's
 * {@code presentSwapchain} field.
 */
final class PresentSwapchain {

    private final VulkanTerrainGpuBackend backend;

    PresentSwapchain(VulkanTerrainGpuBackend backend) {
        this.backend = backend;
    }

    /**
     * Swap-point wall-clock accounting, in nanos: {@code celtium$presentFrame} runs between updateRenderer's end and
     * the window swap, outside every {@code FramePassTimings} bracket. A frame-interval gap that survives the bracket
     * split needs these to say whether the present (acquire, blit submit, queue present) or the boundary flush owns
     * the time.
     */
    long swapPointNanos;
    long swapPointFlushNanos;
    long swapPointWorstNanos;
    long swapPointCalls;
    /** The three blocks inside a present call that are not the swapchain stages themselves. */
    long swapPointImageNanos;
    /** The image ring of the swap-point accounting; its size is the backend's {@code DISTRIBUTION_SAMPLES}, shared with the submit-to-fence ring that stayed there. */
    final long[] swapPointImageRing = new long[VulkanTerrainGpuBackend.DISTRIBUTION_SAMPLES];
    private int swapPointImageRingAt;
    int swapPointImageRingCount;
    long swapPointDumpNanos;
    long swapPointPollNanos;

    private void celtium$noteSwapPointImage(long nanos) {
        this.swapPointImageRing[this.swapPointImageRingAt] = nanos;
        this.swapPointImageRingAt = (this.swapPointImageRingAt + 1) % VulkanTerrainGpuBackend.DISTRIBUTION_SAMPLES;
        if (this.swapPointImageRingCount < VulkanTerrainGpuBackend.DISTRIBUTION_SAMPLES) {
            this.swapPointImageRingCount++;
        }
    }

    /** A surface kept across a rebuild: it belongs to the window, and recreating it breaks Wayland's explicit sync. */
    private long reusableSurface;
    private long reusableSurfaceWindow;

    /** A request is owned by the window, not the replaceable swapchain wrapper. */
    private void releaseSwapchainForRebuild() {
        backend.pendingPresentedDumps.addAll(backend.swapchain.takePendingPresentedImageDumps());
        long window = CeltiumWindow.sdl() ? CeltiumWindow.sdlWindowPointer() : 0L;
        if (window != 0L && backend.swapchain.nativeWindowHandle() == window) {
            // Same window: hand the surface over instead of destroying it. Destroying and recreating it on the same
            // wl_surface is what produced wp_linux_drm_syncobj_surface_v1 error 4 on the first present after a resize,
            // and sway closes the connection on it.
            this.reusableSurface = backend.swapchain.detachSurface();
            this.reusableSurfaceWindow = window;
        }
        backend.swapchain.delete();
        backend.swapchain = null;
        // Write serials are local to the old wrapper. Retain completed frame bindings,
        // but do not mistake the new wrapper's serial 1 for an already observed write.
        backend.seenDumpWriteSerialByName.clear();
    }

    /**
     * Drops the swapchain WITHOUT handing its surface over to the next one.
     * <p>
     * The counterpart of {@link #releaseSwapchainForRebuild()} for a lost surface: the handover exists because a
     * surface belongs to its window, but a lost surface is dead - adopting it would rebuild every new swapchain on
     * the same dead handle and fail forever (bughunt LANE-05 #5). {@code delete()} destroys the surface with the
     * swapchain, and the creation below builds a fresh one from the window.
     */
    private void dropSwapchainAndSurface() {
        backend.pendingPresentedDumps.addAll(backend.swapchain.takePendingPresentedImageDumps());
        backend.swapchain.delete();
        backend.swapchain = null;
        backend.seenDumpWriteSerialByName.clear();
    }

    private void forwardPendingPresentedDumps() {
        if (backend.swapchain == null) {
            return;
        }
        java.util.Iterator<java.nio.file.Path> requests = backend.pendingPresentedDumps.iterator();
        while (requests.hasNext()) {
            java.nio.file.Path held = requests.next();
            backend.swapchain.requestPresentedImageDump(held);
            backend.presentedDumpPath = held;
            requests.remove();
            System.out.println("[Celtium] CELTIUM_PRESENTED_DUMP_HELD path=" + held
                    + " (forwarded after swapchain creation/rebuild)");
        }
    }

    /**
     * Creates the swapchain if it does not exist yet, and reports why when it cannot.
     * <p>
     * It used to be created as a side effect of the diagnostic probe, which meant turning the probe off silently turned
     * the frame present off with it - a run that looked validation-clean because it presented nothing. Anything that
     * needs to present calls this instead, so the probe is only a probe.
     */
    boolean ensureSwapchain() {
        // The VSync option changed since this swapchain was built (task-56): the present mode is a creation parameter,
        // so the only way to honour it is a new swapchain. Dropping it here routes the rebuild through exactly the
        // path an out-of-date surface takes - the acquire semaphores, the per-image render-finished semaphores and the
        // image ownership all belong to the swapchain object and are recreated with it, which is why this is safe to
        // do between frames and not mid-acquire.
        if (backend.swapchain != null && backend.swapchain.presentModeStale()) {
            backend.record("the VSync option changed: dropping the swapchain so the present mode is rebuilt"
                    + " requested=" + org.soutalab.celtium.vulkan.VulkanSwapchain.vsyncRequested()
                    + " previous=[" + backend.swapchain.presentModeSummary() + "]");
            releaseSwapchainForRebuild();
            backend.submissionStatistics.notePresentModeRebuilds();
        }
        // The swapchain's own verdicts, consumed here because this is the one place that runs before every present
        // and owns the drop: an out-of-date acquire or present, a retired acquisition and a presenter failure all
        // used to set a flag no production code read, so the rebuild they asked for never happened - an out-of-date
        // whose cause was not an extent change presented-failed every frame forever, and retired acquisitions
        // leaked an image each until the acquire blocked for good (bughunt LANE-05 #1). A lost surface is the
        // stronger verdict and is decided first: its surface must not be handed over to the next swapchain.
        if (backend.swapchain != null) {
            String decision = org.soutalab.celtium.vulkan.VulkanSwapchain.rebuildDecision(
                    backend.swapchain.surfaceLost(), backend.swapchain.rebuildNeeded());
            if ("recreate-surface".equals(decision)) {
                backend.record("the surface was lost (VK_ERROR_SURFACE_LOST_KHR): dropping the swapchain and its"
                        + " surface so both are recreated");
                dropSwapchainAndSurface();
                backend.submissionStatistics.noteSurfaceLostRebuilds();
            } else if ("rebuild".equals(decision)) {
                backend.record("the swapchain asked for a rebuild (" + backend.swapchain.rebuildReason()
                        + "): dropping it so it is rebuilt");
                releaseSwapchainForRebuild();
                backend.submissionStatistics.noteOutOfDateRebuilds();
            }
        }
        // Under the GL-less window there is no current context to ask, so the handle comes from Display - the same
        // GLFW window either way. Reflective because celtium-vulkan does not depend on the lwjglxx shim.
        //
        // On Cleanroom 0.7 the window is SDL's and the GLFW class is not on the runtime classpath at all: the call
        // below is a NoClassDefFoundError there, so SDL answers first and the GLFW branch is never reached.
        long window = CeltiumWindow.sdl() ? CeltiumWindow.sdlWindowPointer()
                : org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        if (window == 0L) {
            try {
                Class<?> display = Class.forName("org.lwjgl.opengl.Display");
                window = ((Long) display.getMethod("getWindow").invoke(null)).longValue();
            } catch (ReflectiveOperationException | LinkageError ignored) {
                // Fall through: no window means no swapchain.
            }
        }
        if (window == 0L) {
            backend.record("swapchain unavailable: no current " + (CeltiumWindow.sdl() ? "SDL" : "GLFW") + " window");
            return false;
        }
        // Through the cached read: this runs on every present (and every frame that ensures the swapchain), and
        // glfwGetFramebufferSize on X11 is a server round-trip - JFR put it at 1.4% of the client thread's samples with
        // this as one of its two callers. The cache is dropped when the game's own framebuffer reports a different size,
        // and the surface capabilities below remain the authority on what a fresh swapchain would be given, so a resize
        // is still seen on the first frame after it.
        int[] drawable = backend.cachedWindowFramebufferSize();
        int framebufferWidth = drawable[0];
        int framebufferHeight = drawable[1];
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            // A minimised window has no drawable: the swapchain stays as it is and the frame swaps through GL.
            backend.presentSkipReason = "window has no drawable size";
            return false;
        }
        if (backend.swapchain != null) {
            // The size the surface would give a fresh swapchain is the authority, not the driver's out-of-date report.
            // The check used to be gated on frameId, which only advances at a world frame boundary
            // (captureWorldSnapshot) - so a window that was resized while the menu was up (which is what a real launch
            // does: the window is created small and then sized to the display) kept the swapchain from its first
            // present. The present blit is bottom-anchored and never scaled, so it then showed the frame's lower left
            // clipped by the smaller swapchain: measured on the user's own session, the menu appeared shifted with its
            // right side and logo cut off, and going fullscreen did not change it. A present-time check costs one
            // surface-capabilities query per presented frame and is what makes the swapchain follow the window.
            // The rebuild is back to unconditional: the Wayland rejection was not the new swapchain but SDL's own
            // window swap presenting on the same wl_surface (stubbed in CeltiumGllessSdlSwapTransformer), so a resize
            // can keep following the window at full resolution.
            if (!backend.swapchain.matchesSurfaceExtent(framebufferWidth, framebufferHeight)) {
                backend.record("the surface extent no longer matches the " + backend.swapchain.width() + "x"
                        + backend.swapchain.height() + " swapchain (window framebuffer is " + framebufferWidth
                        + "x" + framebufferHeight + "), rebuilding it");
                releaseSwapchainForRebuild();
            }
            if (backend.swapchain != null) {
                forwardPendingPresentedDumps();
                return true;
            }
        }
        java.util.List<String> reasons = new java.util.ArrayList<String>();
        long adopt = window == this.reusableSurfaceWindow ? this.reusableSurface : 0L;
        this.reusableSurface = 0L;
        this.reusableSurfaceWindow = 0L;
        VulkanSwapchain.CreationOutcome creationOutcome = new VulkanSwapchain.CreationOutcome();
        backend.swapchain = VulkanSwapchain.create(backend.context, window, framebufferWidth, framebufferHeight, reasons, adopt,
                creationOutcome);
        if (backend.swapchain != null) {
            backend.swapchainCreationEscalation.recordSuccess();
            org.soutalab.celtium.vulkan.CeltiumFramePacing.setFifoPacing(backend.swapchain.presentModeIsFifo());
        }
        forwardPendingPresentedDumps();
        if (backend.swapchain == null) {
            if (creationOutcome.awaitingSurfaceSize) {
                // A wait, not a refused creation: the surface pins a 0x0 extent (a minimised window, or one
                // mid-resize), so no caller could have created a swapchain against it and nothing about the
                // surface's fitness was decided. The escalation below counts refused creations only, and
                // feeding it waits is what killed a healthy shipped v0.3.0 session: the surface pinned 0x0
                // while a world loaded, the loading screen's present loop retried unthrottled, and 120 waits
                // in about two seconds reached the limit. The frame is skipped exactly as the pre-attempt
                // returns above skip it, creation is retried on the next present, and the escalation is left
                // untouched - neither fed nor reset, because a wait is neither a refusal nor a success. The
                // first present after the surface reports a size lands the creation and its recordSuccess.
                backend.presentSkipReason = "surface reports no size yet";
                if (!backend.pendingPresentedDumps.isEmpty()) {
                    // Held, as on the refusal path: the next successful creation still serves the request.
                    backend.record("presented-image dump held while swapchain creation waits for a surface size: "
                            + backend.pendingPresentedDumps + " (" + reasons + ")");
                }
                backend.record("swapchain creation waiting: the surface reports no size yet: " + reasons);
                return false;
            }
            // Only this branch is a refused creation: the returns above it (no window handle, no drawable
            // size) attempted nothing, so they never reach the latch. The refusal is latched with its
            // reasons for the escalation the swap point polls; the recording is all that changes here -
            // the skip this returns for is counted and reported exactly as before.
            backend.swapchainCreationEscalation.recordRefusal(reasons.toString());
            if (!backend.pendingPresentedDumps.isEmpty()) {
                // Not dropped: the request stays held and is named, so the next creation attempt can still serve it and
                // a leg can tell "no frame was presented" from "the request was thrown away".
                backend.record("presented-image dump held across a failed swapchain creation: "
                        + backend.pendingPresentedDumps + " (" + reasons + ")");
            }
            backend.record("swapchain unavailable at surface and swapchain creation: " + reasons);
            // BH-19 evidence: this list is what names *why* the swapchain was refused - the present-support contract,
            // a surface usage/alpha/format refusal, or a vkCreateSwapchainKHR result. record()'s line reaches the
            // report but not the process stdout, so the leg that exercises the refusal (the synthetic
            // -Dceltium.vulkan.assumeNoPresentSupport=true contract test, or a real non-presenting family) had no
            // reason string in its artifact - only the present hook's lastSkip=[swapchain unavailable]. Printed once
            // per failed creation, where the run's own log keeps it.
            System.out.println("[Celtium] CELTIUM_SWAPCHAIN_FAILED " + reasons);
            return false;
        }
        backend.record(backend.swapchain.describe());
        return true;
    }

    /**
     * The swapchain creation escalation's verdict, for the swap point to poll: the crash line once
     * {@link SwapchainCreationEscalation#FAILURE_LIMIT} creations in a row have been refused, or
     * {@code null} while creation is merely failing transiently (or landing).
     * <p>
     * Polled rather than thrown from here, for two measured reasons: the swap point's hook wraps the
     * present in a catch that swallows {@code RuntimeException | LinkageError}, so a throw from the
     * backend would never reach a crash report; and this module is not compiled against Minecraft, so the
     * {@code displayCrashReport} the escalation ends in cannot be called from here. The mixin polls this
     * beside the window-renderer escalation, outside that catch, and owns the report and the throw - the
     * same split as the renderer's, whose reasons are likewise produced on this side and reported there.
     *
     * @return the failure line for the crash report, or {@code null} when no escalation is due
     */
    public String celtium$swapchainCreationFailure() {
        if (!backend.swapchainCreationEscalation.escalationDue()) {
            return null;
        }
        return "Celtium could not create the Vulkan swapchain after "
                + backend.swapchainCreationEscalation.consecutiveRefusals()
                + " consecutive attempts. Last failure: " + backend.swapchainCreationEscalation.lastRefusal()
                + ". Check the Vulkan driver and Celtium's bundled LWJGL Vulkan bindings.";
    }

    /**
     * S2 probe: builds the surface and swapchain for the window GLFW currently owns, reports what the surface
     * advertises, and presents one cleared image. GL still presents this window in the hybrid build, so this runs
     * once and only when {@code -Dceltium.vulkan.swapchainProbe=true} is set.
     */
    /**
     * Establishes the frame target's borrow of the next swapchain image, when none is live (direct
     * present path, lanechat #8830 (3)(4)).
     * <p>
     * Called from the render path when the frame target is (re)made ready and from the end of the present
     * body, so a frame's borrow exists before its first pass records - the passes attach the borrowed
     * image through the target's ordinary colour accessors. A borrow abandoned unpresented (its owner
     * died holding it) leaves its image acquired until the generation is retired, so the generation is
     * retired here before borrowing again. Never called while a recording is open: an acquire is a queue
     * operation, not a recording, and the frame being recorded already holds its borrow.
     */
    /** Bounded CELTIUM_BORROW_BYPASS lines per reason; the bypasses themselves are not bounded. */
    private int borrowBypassLines;

    /** Prints why a borrow was bypassed, a bounded number of times per run (see the conditions above). */
    private void celtium$noteBorrowBypass(String reason) {
        if (this.borrowBypassLines < 4) {
            this.borrowBypassLines++;
            System.out.println("[Celtium] CELTIUM_BORROW_BYPASS reason=" + reason
                    + " (the frame keeps its own image and takes the composed present)");
        }
    }

    void celtium$ensureBorrowedFrameImage() {
        if (!VulkanContext.PRESENT_DIRECT) {
            return;
        }
        if (!(backend.frameTarget instanceof GllessFrameTarget)) {
            return;
        }
        GllessFrameTarget target = (GllessFrameTarget) backend.frameTarget;
        if (target.hasLivePresentBorrow()) {
            return;
        }
        if (backend.celtium$recordingOpen()) {
            return;
        }
        // Borrow bypass conditions (designer audit 2026-11-25, section 1 Stage 1): a borrowed image is
        // the swapchain's own - it was created without SAMPLED usage - so a frame whose colour must be
        // sampled keeps its own image and takes the composed present. The conditions are named here, at
        // the one place that decides, rather than left to emerge from the paths that would break. Each
        // refusal is printed a bounded number of times: a bypass that fires silently forever is
        // indistinguishable from the path never existing (the fallback-visibility rule).
        // 1. A shader pack is active. The pack's own passes sample the frame colour and write through
        //    their targets; the frame they compose must be an image those passes can sample.
        if (org.embeddedt.embeddium.impl.render.ShaderModBridge.areShadersEnabled()) {
            celtium$noteBorrowBypass("shader-pack");
            return;
        }
        // 2. A post chain has been adopted on this backend (a custom framebuffer's ShaderGroup - the
        //    entity outline, a mod's bloom chain - has run natively): the chain's main-input passes
        //    sample the frame image, and a chain that ran once is live machinery whose groups recur
        //    while their feature is on. The condition is the chain executor's existence, not the mere
        //    existence of chain pairs: pairs are allocated for framebuffers that may never sample the
        //    frame (the outline framebuffer exists from startup, the leg harness's capture FBOs
        //    likewise), and keying on them disabled the borrow for whole sessions that never ran a
        //    chain (measured: agera leg run-s1bgra-agera, 0 direct presents with postChains=0). The
        //    one frame a session's first chain is adopted on can still hold a borrow taken before the
        //    chain existed; that frame is closed at the chain's own entry instead
        //    (PostChainBridge.celtium$postChainRun refuses a main-reading chain over a live borrow).
        if (backend.postChain != null) {
            celtium$noteBorrowBypass("post-chain");
            return;
        }
        // 3. A presented-image readback is outstanding (a diagnostics dump requested of the backend or
        //    already handed to the swapchain): the dump reads the presented image back, which the borrow
        //    contract does not offer. Costs nothing outside a leg - no fixture, no dump requests.
        if (!backend.pendingPresentedDumps.isEmpty()
                || (backend.swapchain != null && backend.swapchain.hasPendingPresentedImageDumps())) {
            celtium$noteBorrowBypass("readback");
            return;
        }
        if (backend.swapchain != null && backend.context.presentEngine().borrowAbandoned()) {
            releaseSwapchainForRebuild();
        }
        if (!ensureSwapchain()) {
            return;
        }
        target.borrowNextPresentImage(backend.context.presentEngine(), (int) (backend.frameId & 1L));
    }

    void runSwapchainProbe() {
        if (backend.gpuStalled) {
            // A stalled GPU closes every entry that touches the frame target or the bridge: the injected timeout showed a
            // barrier recorded into a buffer that was never begun, and a framebuffer destroyed while a stale command buffer
            // still referenced it, both from paths that assumed the frame had been recorded.
            return;
        }
        if (backend.swapchainProbeDone) {
            return;
        }
        backend.swapchainProbeDone = true;
        String step = "window handle";
        try {
            if (!ensureSwapchain()) {
                return;
            }
            backend.record(backend.swapchain.describe());
            step = "clear and present";
            backend.record(backend.swapchain.clearAndPresent(0.2f, 0.4f, 0.8f, 1.0f));
            // Then present the frame's own content rather than a cleared image. Minecraft draws its final composite
            // through Framebuffer.func_178038_a - a shader over a full-screen quad - and this is the step that replaces
            // it: the shared colour image is where this frame's content lives. It runs after the clear probe so both
            // are visible in one run and neither can hide the other.
            step = "present the frame content";
            // The frame content is only readable while the game's framebuffer is bound: at the probe's moment the
            // default framebuffer is, which is exactly what beginFrame() refuses with
            // "the bound draw framebuffer is the default framebuffer". So this step is skipped and said so, rather
            // than reported as a failure of the mechanism.
            if (org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING) == 0) {
                backend.record("presenting the frame skipped: the default framebuffer is bound, so there is no frame content"
                        + " to present here");
            } else if (backend.ensureFrameTarget()) {
                backend.frameSubmitReachedQueue = false;
                backend.frameTarget.beginFrame();
                celtium$ensureBorrowedFrameImage();
                backend.lastPresentResult = backend.frameTarget.borrowedPresentImage() != null
                        ? backend.swapchain.presentDirect(backend.frameTarget)
                        : backend.swapchain.presentImage(backend.frameTarget.colorImage(),
                                org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                                backend.targetWidth, backend.targetHeight);
                backend.record(backend.lastPresentResult);
                backend.frameTarget.endFrame();
            } else {
                backend.record("presenting the frame skipped: no frame target");
            }
        } catch (RuntimeException | LinkageError error) {
            // The failing step and the deepest frame inside this module are what make an NPE diagnosable here: the
            // probe runs once, so a bare message would leave the next attempt guessing.
            StackTraceElement[] trace = error.getStackTrace();
            StringBuilder where = new StringBuilder();
            for (int i = 0; i < Math.min(4, trace.length); i++) {
                if (i > 0) {
                    where.append(" <- ");
                }
                where.append(trace[i].getClassName().substring(trace[i].getClassName().lastIndexOf('.') + 1))
                        .append(':').append(trace[i].getLineNumber());
            }
            String arguments = backend.swapchain == null ? "" : " args[" + backend.swapchain.argumentSummary() + "]";
            backend.record("swapchain probe failed at " + step + ": " + backend.describe(error) + " [" + where + "]" + arguments);
        }
    }

    /** The window begin struct reused across frames: its contents change only when the pass, framebuffer or extent do. */
    private org.lwjgl.vulkan.VkRenderPassBeginInfo cachedWindowBegin;
    private org.lwjgl.vulkan.VkRect2D cachedWindowBeginArea;
    private long cachedWindowBeginPass;
    private long cachedWindowBeginFramebuffer;
    private int cachedWindowBeginWidth = -1;
    private int cachedWindowBeginHeight = -1;
    private boolean cachedWindowBeginValid;

    /** Frees the cached window-begin struct and its render area; called by the backend's {@code delete()}. */
    void freeCachedWindowBegin() {
        if (this.cachedWindowBegin != null) {
            this.cachedWindowBegin.free();
            this.cachedWindowBegin = null;
        }
        if (this.cachedWindowBeginArea != null) {
            this.cachedWindowBeginArea.free();
            this.cachedWindowBeginArea = null;
        }
    }

    /**
     * {@return the window's render-pass begin info}, reusing the struct and its render area while the pass, the
     * framebuffer and the extent stay the same. Every window used to calloc a begin struct and a rectangle and fill
     * them: thirty windows a frame, each about 3 us of the measured winBeginPass span including the caller's vkCmd.
     */
    org.lwjgl.vulkan.VkRenderPassBeginInfo celtium$windowBeginInfo(long renderPass, long framebuffer,
                                                                          int width, int height) {
        if (this.cachedWindowBegin == null) {
            // calloc, not malloc: the rebuild below writes every field of the begin info except pNext, and malloc
            // memory is not zeroed, so a malloc'd struct handed the driver whatever the reused heap block last held
            // as its pNext chain. A non-zero garbage pNext makes the driver walk a bogus extension chain inside
            // vkCmdBeginRenderPass - the biyu_bu v0.3.2 crash (GTX 1650 Ti / 581.29, first splash flush at frame=0,
            // nvoglv64.dll reading address -1) is that shape. The area rect is calloc'd for the same reason, even
            // though both of its fields are rewritten: the cost is once per process, the failure mode is not worth
            // re-deriving if a field is ever added.
            this.cachedWindowBegin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc();
            this.cachedWindowBeginArea = org.lwjgl.vulkan.VkRect2D.calloc();
            this.cachedWindowBeginValid = false;
        }
        // Compared field by field, not by a hash: two different framebuffers whose hash collided would have recorded a
        // pass against the wrong one, and a device loss on a loaded world is exactly what that costs.
        if (!this.cachedWindowBeginValid || renderPass != this.cachedWindowBeginPass
                || framebuffer != this.cachedWindowBeginFramebuffer
                || width != this.cachedWindowBeginWidth || height != this.cachedWindowBeginHeight) {
            org.lwjgl.vulkan.VkRect2D area = this.cachedWindowBeginArea;
            area.offset().set(0, 0);
            area.extent().set(width, height);
            this.cachedWindowBegin.sType$Default().pNext(0L).renderPass(renderPass).framebuffer(framebuffer)
                    .renderArea(area);
            // The chain-pair pass clears its depth attachment, so it needs a clear value for it: the count must cover
            // the highest attachment index with a CLEAR load op (VUID-VkRenderPassBeginInfo-clearValueCount-00902,
            // which the first leg of this change reported 20 times before this line existed). The frame pass loads
            // both attachments, so it needs none - and passing none is what it has always done.
            int chainColors = backend.chainPairRenderPassColorCount(renderPass);
            if (chainColors > 0) {
                if (this.cachedWindowClearValues == null) {
                    this.cachedWindowClearValues = org.lwjgl.vulkan.VkClearValue.calloc(9);
                }
                // The colour attachments load, so their values are ignored; the depth - at the index the colour
                // count puts it - is cleared to the far plane.
                this.cachedWindowClearValues.get(chainColors).depthStencil().depth(1.0f).stencil(0);
                this.cachedWindowBegin.clearValueCount(chainColors + 1).pClearValues(this.cachedWindowClearValues);
            } else {
                this.cachedWindowBegin.clearValueCount(0).pClearValues(null);
            }
            this.cachedWindowBeginPass = renderPass;
            this.cachedWindowBeginFramebuffer = framebuffer;
            this.cachedWindowBeginWidth = width;
            this.cachedWindowBeginHeight = height;
            this.cachedWindowBeginValid = true;
        }
        return this.cachedWindowBegin;
    }

    /** The clear values the chain-pair pass' begin needs (depth only); allocated on first use. */
    private org.lwjgl.vulkan.VkClearValue.Buffer cachedWindowClearValues;

    /**
     * Presents this frame's content from Vulkan, and says whether it reached the queue.
     * <p>
     * The frame's colour image is the one the game's own rendering and the native passes both wrote into (it is the shared
     * image the GL side draws through), so presenting it puts this frame on the swapchain without a GL swap. The sequence
     * is the one the probe already used: beginFrame, presentImage, endFrame.
     * <p>
     * Called once per frame from {@code Minecraft.updateDisplay}. A false return leaves the caller to swap through GL as
     * before, so a failure here cannot leave the window without a frame.
     */
    public boolean celtium$presentFrame() {
        long presentStartedNanos = System.nanoTime();
        try {
            return celtium$presentFrameImpl();
        } finally {
            // Swap-point accounting: this call and the boundary flush before it are the renderer's work between the
            // end of updateRenderer and the window swap - outside every FramePassTimings bracket, so a leg's
            // wall-vs-CPU gap cannot attribute them without a dedicated counter.
            long spentNanos = System.nanoTime() - presentStartedNanos;
            this.swapPointNanos += spentNanos;
            FrameBudgetTimings.addPresent(spentNanos);
            // The cadence mark: a menu presents frames without ever reaching the frame boundary, so this is the only
            // place the frame period is measured there (-Dceltium.vulkan.frameBudget=true; a no-op otherwise).
            FrameBudgetTimings.markPresent();
            this.swapPointCalls++;
            if (spentNanos > this.swapPointWorstNanos) {
                this.swapPointWorstNanos = spentNanos;
            }
        }
    }

    private void celtium$debugMaybeGoFullscreen() {
        if (VulkanTerrainGpuBackend.DEBUG_FULLSCREEN_AFTER_FRAMES < 0 || backend.debugFullscreenAsked) {
            return;
        }
        if (++backend.debugFramesPresented < VulkanTerrainGpuBackend.DEBUG_FULLSCREEN_AFTER_FRAMES) {
            return;
        }
        backend.debugFullscreenAsked = true;
        long window = CeltiumWindow.sdlWindowPointer();
        if (window != 0L) {
            boolean ok = org.lwjgl.sdl.SDLVideo.SDL_SetWindowFullscreen(window, true);
            System.out.println("[Celtium] CELTIUM_DEBUG_FULLSCREEN frames=" + backend.debugFramesPresented
                    + " window=0x" + Long.toHexString(window) + " ok=" + ok
                    + " (the launcher's fatal transition, asked for deliberately)");
        }
    }

    private boolean celtium$presentFrameImpl() {
        org.soutalab.celtium.vulkan.CeltiumSdlEventTrace.installOnce();
        backend.celtium$noteFrameBoundary("present");
        backend.reports.celtium$reportWindowState();
        backend.celtium$debugMaybeResize();
        celtium$debugMaybeGoFullscreen();
        // Isolation switch for the Wayland fault: with the swapchain out of the picture the frame is recorded and
        // submitted but never presented, which separates "the swapchain's own wl_display use is the fault" from
        // "something else in the present path is". Off by default; a leg that sets it draws nothing to the window.
        if (VulkanTerrainGpuBackend.SKIP_PRESENT) {
            return true;
        }
        if (VulkanTerrainGpuBackend.PUMP_BEFORE_PRESENT) {
            // Serialise the two users of the wayland display: SDL services its queue here, on this thread, immediately
            // before the swapchain's own dispatch runs. The fault measured on Wayland happens inside SDL's dispatch
            // reached from SDL itself, and it disappears when the present is skipped, so the experiment is whether
            // having nothing pending for SDL while the WSI works removes it.
            try {
                // Reflective: this module does not compile against LWJGL's GLFW-era Display, and Cleanroom's window
                // bridge answers it on the SDL line.
                Class<?> display = Class.forName("org.lwjgl.opengl.Display");
                display.getMethod("processMessages").invoke(null);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                // A pump that fails must not stop the frame from being presented.
            }
        }
        long presentStart = System.nanoTime();
        try {
            return celtium$presentFrameBody();
        } finally {
            long presentNanos = System.nanoTime() - presentStart;
            if (presentNanos > 50_000_000L) {
                System.out.println("[Celtium] CELTIUM_PRESENT_SLOW millis=" + (presentNanos / 1_000_000L)
                        + " frame=" + backend.frameId);
            }
        }
    }

    private boolean celtium$presentFrameBody() {
        // The present must not order after work still sitting in the frame buffer: paths that reach here without
        // the swap-point flush (the bridge's world-pass present) still need the frame's submission queued first.
        backend.celtium$finishFrameBuffer();
        backend.celtium$dropSplashCanvasesAtSplashEnd();
        // Fourth trigger for the counter summary, and the one a screen-open leg needs: frameId advances on the world
        // render path, which a GUI screen skips, so a leg that opens a screen never reached the frame-120 print and
        // read every counter as absent. Presents keep happening with a screen up, so counting them gives such a leg a
        // print too. Bounded like the others: once per process, at the 120th present.
        backend.celtium$presentCount++;
        if (backend.celtium$presentCount == 120) {
            backend.celtium$printVerificationSummary("present 120", true);
        }
        // Counted before any condition: the entry counter is what says whether the per-frame call arrives at all, which the
        // attempt counter cannot (it is only reached after the early returns).
        backend.presentHookCalls++;
        // The counters below are otherwise only printed by event-driven reports, which say what happened the first time
        // something changed rather than where the session ended up. This heartbeat is what makes the present path
        // readable at the end of a run: whether the per-frame call arrives, and if it does not present, which gate
        // returned it.
        if (backend.presentHookCalls <= 3 || backend.presentHookCalls % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_PRESENT hookCalls=" + backend.presentHookCalls
                    + " attempts=" + backend.presentAttempts + " presented=" + backend.framePresentedFromVulkan
                    + " failures=" + backend.presentFailures + " swapsSkipped=" + backend.glSwapsSkipped
                    + " drainFailures=" + backend.presentFlushFailures
                    + " waiting=" + backend.lastFrameReachedQueue + " lastSkip=[" + backend.presentSkipReason
                    + "] lastResult=[" + backend.lastPresentResult + "] skipsByReason=[noTarget/stalled="
                    + backend.presentSkipsByReason[0] + " noSwapchain=" + backend.presentSkipsByReason[1]
                    + " noCompletedFrame=" + backend.presentSkipsByReason[2] + " targetUnavailable="
                    + backend.presentSkipsByReason[3] + "]");
        }
        if (backend.frameTarget == null || backend.gpuStalled) {
            backend.presentSkipReason = "no frame target, or stalled";
            backend.presentSkips++;
            backend.presentSkipsByReason[0]++;
            return false;
        }
        if (!ensureSwapchain()) {
            // The swapchain is created here rather than only by the diagnostic probe: "anything that needs to present calls
            // this" is what that method's own contract says, and this is the second caller it was written for.
            backend.presentSkipReason = "swapchain unavailable";
            backend.presentSkips++;
            backend.presentSkipsByReason[1]++;
            return false;
        }
        // GL's updateDisplay presents everything drawn since the last swap, but this renderer defers immediate
        // draws to the next bind or texture boundary - which can be arbitrarily later. Measured at startup: the
        // splash's composite sat in the queue while its own updateDisplay presented a still-white frame image, and
        // the presented loading frames stayed one update behind for the whole phase. Drawing the bound target's
        // pending work here restores the swap-point ordering; groups for other targets stay queued for their own
        // pass, exactly as at a bind boundary, and an empty queue costs one list check.
        if (VulkanTerrainGpuBackend.PRESENT_FLUSH_PENDING && !backend.immediateWindowBusy) {
            try {
                FramePassTimings.begin("flush");
                try {
                    // The swap-point drain is the frame-end trigger for the window census.
                    backend.pendingWindowTrigger = FramePassTimings.WINDOW_TRIGGER_FRAME_END;
                    backend.flushPendingBeforeGlDraw();
                } finally {
                    FramePassTimings.end("flush");
                }
                if (VulkanTerrainGpuBackend.PRESENT_DRAIN_FINISH_GUARD) {
                    // If the present-time drain opened and recorded into the unified frame buffer,
                    // close and queue it before the lastFrameReachedQueue gate and presentDirect().
                    backend.celtium$finishFrameBuffer();
                }
            } catch (RuntimeException | LinkageError error) {
                // A failed drain must not stop the present: the queued work keeps its owner, and whatever
                // completed earlier is still worth showing.
                backend.presentFlushFailures++;
                if (backend.presentFlushFailures <= 3) {
                    backend.record("present-time immediate drain failed; the queue keeps its work: "
                            + backend.describeWithOrigin(error));
                }
            }
        }
        if (!backend.lastFrameReachedQueue) {
            backend.presentSkipReason = "no completed frame waiting";
            backend.presentSkips++;
            backend.presentSkipsByReason[2]++;
            // No completed frame's content is waiting in the shared image. frameSubmitReachedQueue cannot be used here:
            // it is cleared when the next frame begins, and this call happens after that.
            return false;
        }
        // Consumed: the next call presents the frame after this one. What is worth measuring is how far the frame
        // being presented is from the one the game is drawing: a still picture cannot show a trail, but a present that
        // serves a frame two or more behind is exactly what a moving camera looks like when it lags.
        backend.lastFrameReachedQueue = false;
        if (backend.queuedFrameId >= 0L) {
            long lag = backend.frameId - backend.queuedFrameId;
            if (lag > backend.presentLagMax) {
                backend.presentLagMax = lag;
            }
            if (lag <= 1L) {
                backend.presentLagOne++;
            } else {
                backend.presentLagMore++;
            }
        }
        try {
            if (!backend.ensureFrameTarget()) {
                backend.presentSkipReason = "frame target not available";
                backend.presentSkips++;
                backend.presentSkipsByReason[3]++;
                return false;
            }
            // Direct present (lanechat #8830 (3)(4)): the frame rendered into a swapchain image it
            // borrowed at frame start, so there is nothing to copy and no source layout to declare -
            // the target's own bookkeeping names the layout the borrowed image rests in, and the
            // swapchain's presentDirect takes it from there to PRESENT_SRC and presents it through
            // the lender. A frame that holds no borrow (direct present not opted in, which is the
            // default since the opt-in landing; a failed borrow acquire; or a target that is not the
            // GL-less one) takes the composite fallback below and is blitted from the owned image,
            // exactly as before this stage.
            // The image is handed back in GENERAL whatever it was found in, because UNDEFINED is a legal oldLayout for
            // the source transition and an illegal newLayout for the hand-back: passing the found layout through as the
            // restore target produced VUID-VkImageMemoryBarrier-newLayout-01198 on a freshly rebuilt target (the
            // present's restore barrier asked for newLayout=UNDEFINED). Content is not claimed by saying GENERAL here -
            // a fresh image has none either way, and the next frame's acquire barrier is what gives it a layout it can
            // be drawn into.
            // What the present is about to serve, and what the pair held for it: the loading screen's frame image is a
            // flat white field while the pair holds the game's picture, and the question is whether that is already
            // true when the frame is presented or whether the image is whitened after the composite. The patch is the
            // logo's own region, so a flat value is "no picture" and a dark one is "picture".
            if (VulkanTerrainGpuBackend.PRESENT_PATCH_TRACE && backend.presentPatchLogs < VulkanTerrainGpuBackend.PRESENT_PATCH_LINES
                    && backend.framePresentedFromVulkan + 1 >= VulkanTerrainGpuBackend.PRESENT_PATCH_MIN_PRESENT
                    && backend.targetWidth > 512 && backend.targetHeight > 512) {
                backend.presentPatchLogs++;
                String presentLine = "CELTIUM_PRESENT_PATCH present=" + (backend.framePresentedFromVulkan + 1)
                        + " frame=" + backend.frameId
                        + " windows=" + backend.immediateStatistics.immediateWindows()
                        + " windowsThisFrame=" + backend.immediateStatistics.immediateWindowsThisFrame()
                        + " frameCentre=" + backend.postChainBridge.celtium$patchMean(backend.frameTarget.colorImage(), 928, 516, 64)
                        // The hotbar's own band, in the frame image the present is about to blit. A flat band means the
                        // GUI stage's draws are not in that image at all; a textured one means they are and the blit or
                        // the swapchain loses them. Two statements, two different fixes.
                        //
                        // Row 0 of an owned image is the picture's BOTTOM (the readback is in image order; the present
                        // flips once), so the hotbar - the bottom of the picture - is y = 0, not y = targetHeight - 32.
                        // The earlier form read the picture's top: measured on leg r212-world-hud16 at presents 40-47,
                        // "hotbarBand" was (111,145,207) - the sky - while the pair read (32,23,16). Both labels were
                        // read as being about the hotbar in the rounds before that.
                        + " hotbarBand=" + backend.postChainBridge.celtium$patchMean(backend.frameTarget.colorImage(),
                                Math.max(0, backend.targetWidth / 2 - 120), 0, 32)
                        // The same coordinates on the picture's other end, so the two are told apart in one line.
                        + " topBand=" + backend.postChainBridge.celtium$patchMean(backend.frameTarget.colorImage(),
                                Math.max(0, backend.targetWidth / 2 - 120), Math.max(0, backend.targetHeight - 32), 32)
                        + " pairCentre=" + (backend.postChainBridge.celtium$livePairImage() == 0L ? "none"
                                : backend.postChainBridge.celtium$patchMean(backend.postChainBridge.celtium$livePairImage(), 928, 516, 64))
                        // The same hotbar band in the chain pair. The teammate's round-240 census puts the HUD family
                        // on its own target identity, not on the frame image's; if that identity routes to the pair,
                        // the HUD is in the pair and the present serves the frame image - a routing fault, not a lost
                        // draw. Pixels, not bounds: the bounds reader collapses these groups to NDC (0,0), so its
                        // geometry evidence cannot answer this.
                        + " pairHotbarBand=" + (backend.postChainBridge.celtium$livePairImage() == 0L ? "none"
                                : backend.postChainBridge.celtium$patchMean(backend.postChainBridge.celtium$livePairImage(),
                                        Math.max(0, backend.targetWidth / 2 - 120), 0, 32))
                        // And the default pair, which the routing table sends a non-main, non-zero identity's draws to.
                        // The HUD's stage carries an identity of its own in this leg, and that identity's image has to be
                        // looked at directly: the chain pair was checked and holds the world, so the default pair is the
                        // remaining candidate - it is black in the splash legs, and nothing presents it.
                        + " defaultPairHotbarBand=" + (backend.offTargetTarget() == null
                                || backend.offTargetTarget().offTargetColorImage() == 0L ? "none"
                                : backend.postChainBridge.celtium$patchMean(backend.offTargetTarget().offTargetColorImage(),
                                        Math.max(0, backend.targetWidth / 2 - 120), 0, 32));
                System.out.println("[Celtium] " + presentLine);
                backend.record(presentLine);
            }
            long presentImageStartedNanos = System.nanoTime();
            // The frame image as the blit is about to read it, in the present that serves it: the boundary probe reads
            // the same image one frame later, and a difference between the two is the frame's own draws landing after
            // the present rather than before it.
            if (VulkanTerrainGpuBackend.SKY_FRAME_PROBE >= 0L && backend.frameId >= VulkanTerrainGpuBackend.SKY_FRAME_PROBE
                    && backend.skyPresentProbeLines < VulkanTerrainGpuBackend.SKY_FRAME_PROBE_LIMIT
                    && backend.frameTarget.colorImage() != 0L) {
                backend.skyPresentProbeLines++;
                int[] presentFramePixels = backend.celtium$readImageRectangle(backend.frameTarget.colorImage(), 0, 0,
                        backend.targetWidth, backend.targetHeight);
                System.out.println("[Celtium] CELTIUM_SKY_PRESENT_PROBE present=" + (backend.framePresentedFromVulkan + 1)
                        + " frame=" + backend.frameId
                        + " skyDraws=" + backend.skyDrawsThisFrame
                        + " " + PixelSignatures.celtium$skyBandSignature(presentFramePixels, backend.targetWidth, backend.targetHeight));
            }
            if (backend.frameTarget.borrowedPresentImage() != null) {
                backend.lastPresentResult = backend.swapchain.presentDirect(backend.frameTarget);
            } else {
                int presentSourceLayout = backend.frameTarget.colorImageLayout();
                backend.lastPresentResult = backend.swapchain.presentImage(backend.frameTarget.colorImage(),
                        presentSourceLayout, backend.targetWidth, backend.targetHeight,
                        org.lwjgl.vulkan.VK10.VK_NULL_HANDLE, org.lwjgl.vulkan.VK10.VK_NULL_HANDLE,
                        org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
                backend.frameTarget.noteColorImageLayout(org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
            }
            long presentImageNanos = System.nanoTime() - presentImageStartedNanos;
            this.swapPointImageNanos += presentImageNanos;
            celtium$noteSwapPointImage(presentImageNanos);
            backend.presentAttempts++;
            backend.presentSkipReason = "presented";
            boolean reached = backend.swapchain.lastPresentSucceeded();
            if (reached) {
                backend.framePresentedFromVulkan++;
                // Attribute the dumps this present wrote to this presented frame. The fixture can only observe
                // the file once per render tick and several presents can pass in that window (measured: the
                // startup request's file first existed while the counter read 16), which is why the frame is
                // named from the write rather than from the observation. Several requests can be pending at
                // once, so the attribution is kept per file name rather than as one "last write".
                java.util.Map<String, Long> writes = backend.swapchain.dumpWriteSerialByPath();
                if (!writes.isEmpty()) {
                    for (java.util.Map.Entry<String, Long> write : writes.entrySet()) {
                        Long seen = backend.seenDumpWriteSerialByName.get(write.getKey());
                        if (seen == null || seen.longValue() != write.getValue().longValue()) {
                            backend.seenDumpWriteSerialByName.put(write.getKey(), write.getValue());
                            backend.presentedDumpWrittenFrames.put(write.getKey(),
                                    Integer.valueOf(backend.framePresentedFromVulkan));
                        }
                    }
                }
                long dumpStartedNanos = System.nanoTime();
                dumpFrameIfRequested(backend.framePresentedFromVulkan);
                this.swapPointDumpNanos += System.nanoTime() - dumpStartedNanos;
                // The caller skips the game's own Display.update when this returns true, and that call is also what polls
                // the window's events, so they are polled here instead: input keeps working without the GL swap. Done in
                // this module because the GLFW classes are on its classpath and not on the game module's.
                long pollStartedNanos = System.nanoTime();
                celtium$pollEvents();
                this.swapPointPollNanos += System.nanoTime() - pollStartedNanos;
                // The game's own Display.update runs after this and would swap through GL as well. The frame that swap
                // would show is the one just presented, so it is skipped once, at the swap point itself.
                backend.swapPendingFromVulkan = true;
            } else {
                backend.presentFailures++;
            }
            // The present released the frame's borrow on every path through presentDirect: borrow the
            // next frame's image now, so the rendering that starts when this call returns attaches the
            // swapchain image from its first pass.
            celtium$ensureBorrowedFrameImage();
            return reached;
        } catch (RuntimeException | LinkageError error) {
            backend.presentFailures++;
            backend.lastPresentResult = "present threw: " + error;
            return false;
        }
    }

    /**
     * Writes the presented image out every N frames when a directory is named, which is how the window's content is
     * inspected over a run: one image at the fixture's capture cannot say whether what is on screen is a loading
     * screen, the world, or a frame that never got drawn.
     */
    private void dumpFrameIfRequested(int presented) {
        if (VulkanTerrainGpuBackend.DUMP_PAIRS_AT_PRESENT > 0 && presented == VulkanTerrainGpuBackend.DUMP_PAIRS_AT_PRESENT) {
            backend.postChainBridge.dumpChainPairs("present" + presented);
        }
        // Keep the fixture's own dump fresh as well. It asks for it once, and the write happens at the next present;
        // when the run ends before that present - which is what a capture followed by a shutdown does - the file on
        // disk is still the early one, and the picture assertions measure a frame from before the world existed.
        // Re-asking on a cadence bounds the age instead of depending on where the run happens to stop.
        int dumpEvery = VulkanTerrainGpuBackend.PRESENTED_DUMP_EVERY;
        if (backend.presentedDumpPath != null && dumpEvery > 0 && presented % dumpEvery == 0) {
            backend.swapchain.requestPresentedImageDump(backend.presentedDumpPath);
        }
        String directory = VulkanTerrainGpuBackend.FRAME_DUMP_DIR;
        if (directory == null) {
            return;
        }
        if (VulkanTerrainGpuBackend.FRAME_DUMP_EVERY <= 0 || presented % VulkanTerrainGpuBackend.FRAME_DUMP_EVERY != 0) {
            return;
        }
        if (backend.frameDumpsWritten >= VulkanTerrainGpuBackend.FRAME_DUMP_COUNT) {
            return;
        }
        backend.frameDumpsWritten++;
        celtium$dumpPresentedImage(java.nio.file.Paths.get(directory, "frame-" + presented + ".png"));
    }

    /** Polls the window's events. The caller skips Display.update when a frame was presented, and that call polls them. */
    public void celtium$pollEvents() {
        try {
            if (!CeltiumWindow.sdl()) {
                // SDL pumps its own events in the loader's frame loop; calling GLFW there would be a
                // NoClassDefFoundError on 0.7.
                org.lwjgl.glfw.GLFW.glfwPollEvents();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Polling is not worth failing a frame over.
        }
    }

    /**
     * {@return whether a presented frame is still waiting for the game's swap point, consuming that fact}.
     * <p>
     * True at most once per presented frame: the caller uses it to skip the GL swap for a frame Vulkan already showed,
     * and the answer is cleared here so that a frame which was not presented still swaps through GL.
     */
    public boolean celtium$consumeSwapPendingFromVulkan() {
        boolean pending = backend.swapPendingFromVulkan;
        backend.swapPendingFromVulkan = false;
        if (pending) {
            backend.glSwapsSkipped++;
        }
        return pending;
    }

    /**
     * {@return the mean of the last image presented to the swapchain}, or -1 when it was not read back.
     * <p>
     * Exposed so the fixture can record it at the capture: an earlier comparison of this mean against the GL screenshot's
     * was wrong because the two were taken at different moments, and a value recorded at the capture is the same frame.
     * It is only meaningful with {@code -Dceltium.vulkan.presentReadback=true}.
     */
    public double celtium$presentedImageMean() {
        return backend.swapchain == null ? -1.0D : backend.swapchain.lastPresentedMean();
    }

    /**
     * Requests a PNG of the next acquired/presented image, not the last-presented image.
     * <p>
     * Readback and frame attribution belong to the present path. Requests survive lazy creation and wrapper replacement;
     * accepting one does not mean its PNG has been written. Never read a swapchain image after presentation released it.
     */
    public boolean celtium$dumpPresentedImage(java.nio.file.Path path) {
        if (path == null) {
            return false;
        }
        if (backend.swapchain == null) {
            // The loading phase's request: accepted and held, because the swapchain is created on the present path and
            // the splash's own last updateDisplay() is this run's first present. Returning false here made the fixture
            // record "could not be requested" for a frame that was about to be presented - the request was possible,
            // it just had no object to write through yet.
            backend.pendingPresentedDumps.add(path);
            backend.presentedImageDumpRequested = true;
            return true;
        }
        backend.swapchain.requestPresentedImageDump(path);
        backend.presentedDumpPath = path;
        backend.presentedImageDumpRequested = true;
        return true;
    }
}
