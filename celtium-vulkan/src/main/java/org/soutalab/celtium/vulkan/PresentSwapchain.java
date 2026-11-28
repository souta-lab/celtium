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
 * and its one writer outside it (the boundary flush's share) reaches it through the backend's S3-b E6 added the published-frame-texture machinery
 * (publication, the layout transition walk, the stale-name prune, the pair-recreation scan) and the swapchain
 * seat supply to this cluster; the backend keeps delegating doors for the publication entries.
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
     * <p>
     * This is the full form - rebuild consumption, then existence - and its callers are the ones that run before any
     * borrow is taken (the borrow path, the probe), where consuming a generation change is safe because the borrow is
     * acquired after it. The swap point splits the two halves instead ({@link #ensureSwapchainForPresent()} at its
     * head, {@link #consumeSwapchainRebuild()} after its present attempt): the frame it presents already holds its
     * borrow by then.
     */
    boolean ensureSwapchain() {
        consumeSwapchainRebuildVerdicts();
        return ensureSwapchainExists(true);
    }

    /**
     * The swap point's own ensure, at the head of {@code celtium$presentFrameBody}: existence only, no rebuild
     * consumption. The frame that body is about to present holds a borrow of the current generation, so consuming a
     * generation change here would drop that generation under the borrow before the drain, the frame-buffer finish
     * and the present attempt have run - the ordering R-06 removes (VUID-vkQueueSubmit-pCommandBuffers-00070, and
     * the transition frame's VUID-vkCmdDrawIndexed-renderPass-02684). The consumption runs at the end of the same
     * body instead, after the present attempt ({@link #consumeSwapchainRebuild()}).
     */
    private boolean ensureSwapchainForPresent() {
        return ensureSwapchainExists(false);
    }

    /**
     * Consumes the swapchain's own rebuild verdicts: the present-mode staleness and the {@code rebuildDecision}
     * verdicts. The drops are idempotent - a verdict whose swapchain is already gone consumes nothing - so a
     * consumption that follows one in the same frame is a no-op.
     */
    private void consumeSwapchainRebuildVerdicts() {
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
        // The swapchain's own verdicts, consumed by the places that own the drop (the ensure, and the swap point's
        // end step): an out-of-date acquire or present, a retired acquisition and a presenter failure all
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
    }

    /**
     * The swap point's rebuild consumption (R-06): the verdict drops and the extent drop, in the ensure's own
     * order, run by {@code celtium$presentFrameBody} after the present attempt and before the next frame's borrow
     * is taken. A swapchain dropped here is not recreated here - the borrow that follows in the same body creates
     * it through the full ensure, and a swap point that takes no borrow leaves it to the next ensure, exactly as a
     * rebuild consumed inside {@link #ensureSwapchain()} leaves it to that call's own creation step.
     */
    private void consumeSwapchainRebuild() {
        if (backend.swapchain == null) {
            return;
        }
        consumeSwapchainRebuildVerdicts();
        if (backend.swapchain == null) {
            return;
        }
        int[] drawable = backend.cachedWindowFramebufferSize();
        if (drawable[0] > 0 && drawable[1] > 0) {
            dropSwapchainOnExtentMismatch(drawable[0], drawable[1]);
        }
    }

    /** Drops the swapchain when the surface would give a fresh one a different extent. The caller guarantees one exists. */
    private void dropSwapchainOnExtentMismatch(int framebufferWidth, int framebufferHeight) {
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
    }

    private boolean ensureSwapchainExists(boolean consumeExtentMismatch) {
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
            if (consumeExtentMismatch) {
                dropSwapchainOnExtentMismatch(framebufferWidth, framebufferHeight);
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

    /**
     * The extent gate's rule (D1 L4): a borrow is offered only while the swapchain generation's
     * extent equals the frame target's extent. Pure and stateless, and consulted with the target's
     * current extent on every borrow attempt, so a target remade at the generation's extent
     * re-enables the borrow by itself - nothing latches in either direction.
     */
    static boolean celtium$extentAllowsBorrow(int swapchainWidth, int swapchainHeight,
            int targetWidth, int targetHeight) {
        return swapchainWidth == targetWidth && swapchainHeight == targetHeight;
    }

    void celtium$ensureBorrowedFrameImage() {
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
        //    The built-in pack runtime is not this condition (D7 seat ruling, NEON 2026-11-28): its
        //    intermediates all live on its own attachment slab and its final pass writes only the
        //    swapchain image, so a built-in pack frame keeps the direct-present borrow - the probe
        //    below names the external pack path this bypass was written for.
        if (org.soutalab.celtium.vulkan.ShaderPackBridge.areShadersEnabled()
                && !org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.builtInPackActive()) {
            celtium$noteBorrowBypass("shader-pack");
            return;
        }
        // 2. (Retired with VulkanPostChain, ruling step 5.) This condition keyed on the old chain
        //    executor's existence: a chain that had run natively samples the frame image, so its
        //    frames kept their own image. The vanilla chain's seat is now the FrameCoordinator's
        //    VanillaPostExecutor, which is handed the borrowed state of the main image and refuses
        //    a run whose main input is borrowed - the hazard is closed at the chain's own entry,
        //    so no borrow condition remains here.
        // 3. A presented-image readback is outstanding (a diagnostics dump requested of the backend or
        //    already handed to the swapchain): the dump reads the presented image back, which the borrow
        //    contract does not offer. Costs nothing outside a leg - no fixture, no dump requests.
        if (!backend.pendingPresentedDumps.isEmpty()
                || (backend.swapchain != null && backend.swapchain.hasPendingPresentedImageDumps())) {
            celtium$noteBorrowBypass("readback");
            return;
        }
        // 4. The frame's own colour has been sampled through the "frame-image" publication route (a mod
        //    program's main input, or an immediate batch bound to the main colour name). The retired
        //    condition 2 could not see the measured case: GTCEu's bloom chain runs through the immediate
        //    path, not ShaderGroup, so no post chain is ever adopted while the chain samples main every
        //    frame. The sampling side
        //    notes the demand on the backend when the frame image is published (or refused), and while
        //    the latch stands the frame keeps its own SAMPLED image and takes the composed present. The
        //    one frame the demand first arrives on can still hold a borrow taken before it existed; that
        //    frame is closed at the publication entry instead (the backend refuses the one publication,
        //    and ModPrograms refuses that frame's blend-off pass into main).
        if (backend.mainColorSampled) {
            celtium$noteBorrowBypass("main-sampled");
            return;
        }
        if (backend.swapchain != null && backend.context.presentEngine().borrowAbandoned()) {
            releaseSwapchainForRebuild();
        }
        if (!ensureSwapchain()) {
            return;
        }
        // 5. The swapchain generation's extent differs from the frame target's extent (D1 L4). This
        //    condition stands after the ensure because the generation extent exists only once the
        //    swapchain does: the swapchain follows the surface at the end of the present body, and
        //    this seat itself creates the new generation before borrowing, while the frame target
        //    is remade only on the draw path - and a resize during a recording is deferred there -
        //    so a borrow can be offered while the target still declares the old extent. The
        //    framebuffer seats declare the target's extent while attaching its colour view, which
        //    under a live borrow is the borrowed image's view, so borrowing across the mismatch
        //    built framebuffers whose declared extent disagreed with their attachment
        //    (VUID-VkFramebufferCreateInfo-04533/04534, one pair on the D1 L4 leg). The refusal
        //    takes the format gate's shape at the borrow's other end (GllessFrameTarget): the frame
        //    keeps its own image and takes the composed present, and once the target has been
        //    remade at the generation's extent the borrow resumes on its own.
        if (backend.swapchain != null && !celtium$extentAllowsBorrow(
                backend.swapchain.width(), backend.swapchain.height(),
                target.width(), target.height())) {
            celtium$noteBorrowBypass("extent");
            return;
        }
        target.borrowNextPresentImage(backend.context.presentEngine(), (int) (backend.frameId & 1L));
        // The borrow's fatal verdict (DP-16): the call's boolean used to be the only output, so an
        // out-of-date or lost-surface acquire latched nothing here - the rebuild the composed
        // present's own acquire would have asked for was incidental, and a frame whose present was
        // skip-gated after this dropped the verdict outright. The verdict is consumed on the spot and
        // latched on the wrapper it names, through the wrapper's own recording, so the consumption at
        // the end of the present body acts on it exactly as on the composed path's verdicts. A device
        // loss goes through the context's loss funnel, once.
        int borrowVerdict = target.celtium$takeBorrowAcquireVerdict();
        if (borrowVerdict == org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR
                && backend.swapchain != null) {
            backend.swapchain.celtium$noteBorrowOutOfDate();
        } else if (borrowVerdict == org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR
                && backend.swapchain != null) {
            backend.swapchain.celtium$noteBorrowSurfaceLost();
        } else if (borrowVerdict == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST
                && !backend.context.celtium$isDeviceLost()) {
            backend.context.celtium$reportDeviceLost("the borrow acquire", borrowVerdict);
        }
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
        // A device loss an earlier path routed into the context latches the backend's stall before this
        // frame's gates read it (DP-17): the gates below key on the backend's own latch, not the
        // context's fact, and without the sync a loss observed by a submit kept presenting.
        backend.celtium$noteStalledOnDeviceLoss();
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
                    + " drainUnfinished=" + backend.presentDrainUnfinished
                    + " waiting=" + backend.lastFrameReachedQueue + " lastSkip=[" + backend.presentSkipReason
                    + "] lastResult=[" + backend.lastPresentResult + "] skips=" + backend.presentSkips
                    + " skipsByReason=[noTarget/stalled="
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
        if (!ensureSwapchainForPresent()) {
            // Existence only (R-06): this call must not consume a pending rebuild - the frame being presented
            // holds a borrow of the current generation, and dropping that generation here, before the drain and
            // the present below have run, is the ordering the end-of-body consumption exists to prevent. The
            // swapchain is still created here rather than only by the diagnostic probe: "anything that needs to
            // present calls this" is what the ensure contract says, and this is the second caller it was written
            // for.
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
            boolean frameBufferOpenBeforeDrain = backend.frameBufferOpen;
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
            // B4 census: the drain opened the unified frame buffer and it is still open here, so the
            // drained groups were recorded but not queued and miss this present. The pre-drain
            // snapshot keeps the count to windows this drain opened, not a buffer left open by an
            // earlier path. Structurally zero while PRESENT_DRAIN_FINISH_GUARD is on.
            if (!frameBufferOpenBeforeDrain && backend.frameBufferOpen) {
                backend.presentDrainUnfinished++;
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
        boolean reached;
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
                        + " frameCentre=" + backend.celtium$patchMean(backend.frameTarget.colorImage(), 928, 516, 64)
                        // The hotbar's own band, in the frame image the present is about to blit. A flat band means the
                        // GUI stage's draws are not in that image at all; a textured one means they are and the blit or
                        // the swapchain loses them. Two statements, two different fixes.
                        //
                        // Row 0 of an owned image is the picture's BOTTOM (the readback is in image order; the present
                        // flips once), so the hotbar - the bottom of the picture - is y = 0, not y = targetHeight - 32.
                        // The earlier form read the picture's top: measured on leg r212-world-hud16 at presents 40-47,
                        // "hotbarBand" was (111,145,207) - the sky - while the pair read (32,23,16). Both labels were
                        // read as being about the hotbar in the rounds before that.
                        + " hotbarBand=" + backend.celtium$patchMean(backend.frameTarget.colorImage(),
                                Math.max(0, backend.targetWidth / 2 - 120), 0, 32)
                        // The same coordinates on the picture's other end, so the two are told apart in one line.
                        + " topBand=" + backend.celtium$patchMean(backend.frameTarget.colorImage(),
                                Math.max(0, backend.targetWidth / 2 - 120), Math.max(0, backend.targetHeight - 32), 32)
                        + " pairCentre=" + (backend.celtium$livePairImage() == 0L ? "none"
                                : backend.celtium$patchMean(backend.celtium$livePairImage(), 928, 516, 64))
                        // The same hotbar band in the chain pair. The teammate's round-240 census puts the HUD family
                        // on its own target identity, not on the frame image's; if that identity routes to the pair,
                        // the HUD is in the pair and the present serves the frame image - a routing fault, not a lost
                        // draw. Pixels, not bounds: the bounds reader collapses these groups to NDC (0,0), so its
                        // geometry evidence cannot answer this.
                        + " pairHotbarBand=" + (backend.celtium$livePairImage() == 0L ? "none"
                                : backend.celtium$patchMean(backend.celtium$livePairImage(),
                                        Math.max(0, backend.targetWidth / 2 - 120), 0, 32))
                        // And the default pair, which the routing table sends a non-main, non-zero identity's draws to.
                        // The HUD's stage carries an identity of its own in this leg, and that identity's image has to be
                        // looked at directly: the chain pair was checked and holds the world, so the default pair is the
                        // remaining candidate - it is black in the splash legs, and nothing presents it.
                        + " defaultPairHotbarBand=" + (backend.offTargetTarget() == null
                                || backend.offTargetTarget().offTargetColorImage() == 0L ? "none"
                                : backend.celtium$patchMean(backend.offTargetTarget().offTargetColorImage(),
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
            reached = backend.swapchain.lastPresentSucceeded();
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
        } catch (RuntimeException | LinkageError error) {
            backend.presentFailures++;
            backend.lastPresentResult = "present threw: " + error;
            reached = false;
        }
        // R-06: the present attempt is complete - the drain, the frame-buffer finish and the present itself
        // have all been submitted against the generation this frame borrowed - so the generation change the
        // swapchain's verdicts asked for is consumed now, before the next frame's borrow is taken at its
        // head. Consuming it at the head of this body instead dropped the generation under the live borrow,
        // which is the ordering VUID-vkQueueSubmit-pCommandBuffers-00070 and the transition frame's
        // VUID-vkCmdDrawIndexed-renderPass-02684 were measured on.
        // DP-18: this tail used to sit inside the try, so a present that threw skipped it entirely - the
        // rebuild the failed present's own verdicts had asked for was never consumed (the stale
        // generation stayed, and every later frame failed the same way). It runs after the catch now:
        // the consumption is idempotent (a verdict whose swapchain is already gone consumes nothing),
        // so the exception path takes the same shape as the return path.
        consumeSwapchainRebuild();
        // FP-1 (plan 8.3 item 8): the next frame's present image is no longer borrowed here, in
        // the present body's tail. The borrow is decided at the frame head, in the backend's
        // beginCommands before the recording opens, where the native pack frame seat is built
        // from the by-then-active target - the tail borrow sat one frame early and outside the
        // frame it served, so a frame whose present was skipped or abandoned carried a borrow
        // decision made for a different frame.
        // A device loss this attempt itself observed (DP-17, routed into the context by the swapchain)
        // latches the backend's stall before the next frame records anything.
        backend.celtium$noteStalledOnDeviceLoss();
        return reached;
    }

    /**
     * Writes the presented image out every N frames when a directory is named, which is how the window's content is
     * inspected over a run: one image at the fixture's capture cannot say whether what is on screen is a loading
     * screen, the world, or a frame that never got drawn.
     */
    private void dumpFrameIfRequested(int presented) {
        if (VulkanTerrainGpuBackend.DUMP_PAIRS_AT_PRESENT > 0 && presented == VulkanTerrainGpuBackend.DUMP_PAIRS_AT_PRESENT) {
            backend.dumpChainPairs("present" + presented);
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

    // ---- The published-frame-texture machinery and the swapchain seat supply (S3-b E6) ----
    // Moved from VulkanTerrainGpuBackend: the publication census's image half (the wrapper half stays in
    // PublishedFrameTextures), the publish/unpublish entries, the layout transition walk, the stale-name
    // prune, the pair-recreation scan and the seat supply. Backend state the machinery reads (the frame
    // target, the registry, the command buffer, the pass attachments) is reached through the backend,
    // exactly as the present path above reaches the swapchain field.

    /**
     * The published wrapper for the chain's adopted output: the renderer's off-target colour image under the GL name
     * the game's composite binds, plus the name and the image handle it was published for.
     * <p>
     * The pair is published from the chain's adoption, the one place that knows both the identity -&gt; GL name
     * association (reported by the game's own framebuffer bind) and the image the chain actually runs on. Publishing
     * is idempotent for the same handles, so a per-frame call returns the same wrapper and the descriptor keys the
     * immediate path cached stay valid. The image handle is kept so a pair recreated at a new size - whose wrapper
     * would name a destroyed image - is unpublished instead of transitioned.
     */
    VulkanGlTexture publishedChainOutput;
    int publishedChainTexture;
    long publishedChainImage;
    /** The framebuffer identity whose chain pair the published chain output is the image of. */
    long publishedChainIdentity;
    /**
     * Every game colour texture name published over the chain pair's image, as its *image handle*, so a pair
     * recreated at a new size can be unpublished from every name it stood for instead of only from the outline's.
     * <p>
     * The name-to-wrapper half of this census is `PublishedFrameTextures.published` (refactoring plan R1b); the
     * backend's own `Map<Integer, VulkanGlTexture> publishedFrameTextures` stood beside it and was read by nothing -
     * the plan's R8 item "the duplicated publication display". It is removed here, and the image-handle map below
     * stays because it is read at :10453/:10492/:10539.
     */
    final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap publishedFrameImages =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    /**
     * The shared depth store key each depth-published GL name stands for, beside {@link #publishedFrameImages}.
     * <p>
     * A depth publication's layout record lives in the frame target's shared depth store, not in a pair, so the
     * transition walk needs the key to report the wrapper's moves back into the store - the report the owner's
     * next attachment acquire reads its source layout from (SHADOW-NATIVE-DESIGN section 3.4). Colour names are
     * absent here; presence in this map is what makes a published name a depth publication.
     */
    final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap publishedFrameDepthKeys =
            new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();

    /** The published GL names, formatted like the boxed key set it replaced so log readers see the same text. */
    String publishedFrameNames() {
        return java.util.Arrays.toString(this.publishedFrameImages.keySet().toIntArray());
    }

    /**
     * Name-to-image resolution cached for the transition loop, rebuilt only when the publication set or a chain pair
     * changes. The loop runs once per window (about fifty a frame on a world scene), and it used to prune and rebuild
     * its map views on every one of them.
     */
    private long publishedCacheVersion = Long.MIN_VALUE;
    private long publishedCachePairs = Long.MIN_VALUE;
    /**
     * The frame target's colour image as of the last prune and cache rebuild. The prune's liveness rule
     * for a name published over the frame image compares against the live {@code frameTarget.colorImage()},
     * but neither the publication version nor the pair generation moves when that image does - the
     * borrowed swapchain image rotates every frame, and a borrow bypass swaps in the owned image - so
     * without this component the prune never re-ran once the chain pairs settled, and the main name
     * stayed published over a single already-presented swapchain image.
     */
    private long publishedCacheFrameImage = Long.MIN_VALUE;


    private int[] cachedNames = new int[0];
    private VulkanGlTexture[] cachedWrappers = new VulkanGlTexture[0];
    private long[] cachedImages = new long[0];
    /**
     * The chain-pair identity each cached name's image stands for, resolved once per cache rebuild.
     * <p>
     * The transition loop used to ask {@code chainPairIdentityForImage} for every name on every window - a hash lookup
     * per name per window, and the loop runs once per window (35.8 a frame on the measured world scene). The answer
     * only changes when the pair generation changes, which is already part of the cache key, so it is resolved here
     * with the rest of the cache instead of 35.8 times a frame.
     */
    private long[] cachedPairIdentities = new long[0];

    /**
     * The chain-pair colour slot each cached name's image fills, resolved with the rest of the cache.
     * <p>
     * The walk used to re-resolve this per name per pass through
     * {@code chainPairColorSlotForImage} - a pair lookup plus a slot scan - although the answer only
     * changes when the pair generation changes, which is already part of the cache key (the same
     * argument as {@link #cachedPairIdentities}). -1 for a name that fills no slot.
     */
    private int[] cachedPairSlots = new int[0];

    /**
     * The shared depth store key each cached name stands for, 0 for a colour name, resolved with the rest of the
     * cache: the walk reports a depth wrapper's layout moves into the store under this key, and resolving it per
     * window would be the same per-name lookup the pair-identity cache exists to avoid.
     */
    private long[] cachedDepthKeys = new long[0];

    /**
     * The pair generation each identity's stale-name scan last completed for, and the publication version it saw.
     * <p>
     * The scan re-points names whose image died with a recreated pair, so it can only find one when that identity's
     * pair was recreated or the publication set moved. The key used to be the identity and the pair's colour image,
     * which is the same thing only while one identity is bound: the measured world scene binds thirteen of
     * GregTech's post-chain framebuffers a frame, so the key missed on every window and the scan walked all published
     * names (38) with two boxed-key lookups each, thirteen times a frame. That is the whole of {@code winHead}'s
     * 0.1 ms a frame, and it is a scan that could not have found anything - no pair was recreated between those
     * windows. The generation is the counter that moves when one is, and it is per identity, so a window for one
     * framebuffer cannot consume the scan another framebuffer still needs.
     */
    private final it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap syncScanGenerations =
            new it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap();
    private long syncScanVersion = Long.MIN_VALUE;
    /** The frame target's pair-generation total when the memo was last used, so a new target clears it. */
    private long syncScanTotal = Long.MIN_VALUE;

    /**
     * GL name -> the slot of the owner's chain pair its published image fills (0 = the pair's own, absent = 0). A
     * name over a tuple slot must be re-published over that slot's live image, not over slot 0's.
     */
    final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap publishedFrameImageSlots =
            new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    /** GL name -> the chain-pair identity its published image belongs to. MIN_VALUE/absent = not a chain image. */
    final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap publishedFrameImageIdentities =
            createPublishedFrameImageIdentities();

    private static it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap createPublishedFrameImageIdentities() {
        it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap map =
                new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
        map.defaultReturnValue(Long.MIN_VALUE);
        return map;
    }

    // The published-frame-texture machinery the PostChainBridge used to house, seated in the
    // backend with the bridge's retirement (ruling step 5) and moved here in S3-b E6: publication,
    // the layout transition walk, the stale-name prune and the pair-recreation scan all operate
    // on the publication state below; the backend keeps delegating doors under the same names.
    // to FrameCoordinator ownership at E7.
    /**
     * Publishes a game framebuffer's colour texture name over the chain pair's image, and keeps it current.
     * <p>
     * One entry per GL texture name, holding the image that name currently stands for. The same name is published
     * again only when the pair has been recreated (a new size makes a new image); a recreated pair's old image is
     * destroyed, so the wrapper that named it is unpublished first - the rule {@code celtium$syncPublishedChainOutput}
     * follows for the outline's name. Called from the off-target pass that draws the game's own framebuffer into the
     * pair, which is the moment both facts are true: the identity being drawn is known, and the image its draws land in
     * is the pair.
     */
    void celtium$publishFrameTexture(int glTextureName, long image, long view, int width, int height) {
        if (glTextureName == 0 || image == 0L || width <= 0 || height <= 0) {
            return;
        }
        VulkanGlTexture current = backend.publishedTextures.published(glTextureName);
        // One lookup: image is non-zero here (the guard above), so 0 is the absent sentinel.
        if (current != null && this.publishedFrameImages.get(glTextureName) == image) {
            return;
        }
        if (current != null) {
            backend.celtium$registry().unpublishExternalImage(glTextureName);
        }
        VulkanGlTexture published = backend.celtium$registry().publishExternalImage(glTextureName, image, view, width, height);
        if (published == null) {
            return;
        }
        backend.publishedTextures.putPublished(glTextureName, published);
        this.publishedFrameImages.put(glTextureName, image);
        backend.publishedTextures.noteCreated();
        // Which image this name stands for decides the layout a descriptor from it declares: a pair image is attached
        // by a pass that declares GENERAL, and pair images stay in GENERAL, so it must declare GENERAL too.
        GllessFrameTarget offTarget = backend.offTargetTarget();
        // The native main attachment also stays in GENERAL. Treating only auxiliary pairs this way let its
        // published sampler move the main image to SHADER_READ_ONLY behind the native target owner's back;
        // the next world/menu acquisition then disagreed with the wrapper's remembered layout.
        published.notePairImage((backend.frameTarget instanceof GllessFrameTarget && image == backend.frameTarget.colorImage())
                || (offTarget != null && (offTarget.isChainPairColorImage(image)
                || image == offTarget.offTargetColorImage())));
        if (offTarget != null) {
            long owner = offTarget.chainPairIdentityForImage(image);
            if (owner != Long.MIN_VALUE) {
                this.publishedFrameImageIdentities.put(glTextureName, owner);
                int slot = offTarget.chainPairColorSlotForImage(owner, image);
                if (slot > 0) {
                    this.publishedFrameImageSlots.put(glTextureName, slot);
                } else {
                    this.publishedFrameImageSlots.remove(glTextureName);
                }
            } else {
                this.publishedFrameImageIdentities.remove(glTextureName);
                this.publishedFrameImageSlots.remove(glTextureName);
            }
        }
    }

    /**
     * The depth twin of {@link #celtium$publishFrameTexture}: one GL depth texture name over the shared depth
     * image that stands for it (SHADOW-NATIVE-DESIGN section 3.4).
     * <p>
     * The name's store key is recorded beside the publication ({@code publishedFrameDepthKeys}), because the
     * shared depth store - not a pair - holds this image's layout record, and the transition walk reports the
     * wrapper's moves back into it. No pair identity is recorded: the pair owner map names colour images only,
     * and the stale-name prune reads the store for depth names instead.
     */
    void celtium$publishFrameDepthTexture(int glTextureName, long image, long view, int width, int height,
            int declaredLayout, long depthKey) {
        if (glTextureName == 0 || image == 0L || view == 0L || depthKey == 0L || width <= 0 || height <= 0) {
            return;
        }
        VulkanGlTexture current = backend.publishedTextures.published(glTextureName);
        if (current != null && current.isDepthExternal()
                && this.publishedFrameImages.get(glTextureName) == image
                && this.publishedFrameDepthKeys.get(glTextureName) == depthKey) {
            return;
        }
        if (current != null) {
            backend.celtium$registry().unpublishExternalImage(glTextureName);
        }
        VulkanGlTexture published = backend.celtium$registry().publishExternalDepthImage(glTextureName, image, view,
                width, height, declaredLayout);
        if (published == null) {
            return;
        }
        backend.publishedTextures.putPublished(glTextureName, published);
        this.publishedFrameImages.put(glTextureName, image);
        this.publishedFrameDepthKeys.put(glTextureName, depthKey);
        backend.publishedTextures.noteCreated();
    }

    /** Drop wrappers for a deleted target before its owner retires the image and view. */
    void celtium$forgetPublishedTarget(long identity) {
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> it =
                this.publishedFrameImageIdentities.int2LongEntrySet().fastIterator();
        while (it.hasNext()) {
            it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry = it.next();
            if (entry.getLongValue() != identity) continue;
            int name = entry.getIntKey();
            backend.celtium$registry().unpublishExternalImage(name);
            backend.publishedTextures.removePublished(name);
            this.publishedFrameImages.remove(name);
            this.publishedFrameImageSlots.remove(name);
            it.remove();
        }
        if (this.publishedChainOutput != null && this.publishedChainIdentity == identity) {
            backend.celtium$registry().unpublishExternalImage(this.publishedChainTexture);
            this.publishedChainOutput = null;
            this.publishedChainTexture = 0;
            this.publishedChainImage = 0L;
            this.publishedChainIdentity = 0L;
        }
        // Depth publications record no pair identity, so they are matched through the depth record instead -
        // which the caller still holds at this point (it clears the identity's depth binding after this
        // returns). A name whose depth texture belongs to the forgotten identity is unpublished with the
        // colour names, before the shared depth store entry it stands for is retired.
        it.unimi.dsi.fastutil.objects.ObjectIterator<it.unimi.dsi.fastutil.ints.Int2LongMap.Entry> depthIt =
                this.publishedFrameDepthKeys.int2LongEntrySet().fastIterator();
        while (depthIt.hasNext()) {
            it.unimi.dsi.fastutil.ints.Int2LongMap.Entry entry = depthIt.next();
            int name = entry.getIntKey();
            if (backend.publishedTextures.identityForDepthTexture(name) != identity) continue;
            backend.celtium$registry().unpublishExternalImage(name);
            backend.publishedTextures.removePublished(name);
            this.publishedFrameImages.remove(name);
            depthIt.remove();
        }
    }

    /**
     * Whether the published-name walk has to visit one name.
     * <p>
     * The sampling side always does: it is the direction that has to cover every name a draw in the pass might sample,
     * and that set is not known at the walk. The attach side only has to visit the names whose image the pass is about
     * to attach - the hand-back exists to satisfy {@code VUID-vkCmdBeginRenderPass-initialLayout-00900} for that one
     * image - so a name over another image is skipped. {@code attachImage == 0} means the caller did not name an
     * image, and the walk keeps its old, unconditional behaviour.
     * <p>
     * Static and package-private so the rule is testable without a device: the walk itself needs a GPU, but "which
     * names may be skipped" is arithmetic, and a green test here is not evidence that a transition happened.
     *
     * @param general     {@code true} for the attach-side hand-back
     * @param attachImage the image the pass attaches, or 0 when the caller did not narrow
     * @param publishedImage the image the published name stands for, 0 when it has none
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long attachImage, long publishedImage) {
        return !general || attachImage == 0L || publishedImage == attachImage;
    }

    /**
     * The same rule for a pass that attaches several images (a multi-target window's whole tuple): the name is
     * visited when any attached image is the one it stands for. A null array is the unnarrowed walk, as
     * {@code attachImage == 0} is above.
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long[] attachImages, long publishedImage) {
        if (!general || attachImages == null) {
            return true;
        }
        for (long attachImage : attachImages) {
            if (celtium$attachHandBackNeeded(true, attachImage, publishedImage)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Zero-allocation attach hand-back predicate covering a pass's color target(s) AND its borrowed shared depth
     * target (shadow §3.4). When {@code general == false} (sampling side) or all attach arguments are unset
     * (unnarrowed walk), returns {@code true}; otherwise returns {@code true} iff {@code publishedImage} matches
     * {@code attachColor}, {@code attachDepth}, or any element of {@code attachColors}.
     */
    static boolean celtium$attachHandBackNeeded(boolean general, long attachColor, long attachDepth,
            long[] attachColors, long publishedImage) {
        if (!general) {
            return true;
        }
        if (attachColor == 0L && attachDepth == 0L && attachColors == null) {
            return true;
        }
        if (attachColor != 0L && publishedImage == attachColor) {
            return true;
        }
        if (attachDepth != 0L && publishedImage == attachDepth) {
            return true;
        }
        if (attachColors != null) {
            for (long candidate : attachColors) {
                if (candidate != 0L && candidate == publishedImage) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * {@return the layout the published-name walk moves one name's image to, and the layout it reports to the
     * image's owner afterwards}.
     * <p>
     * One decision for both the transition and the report, so the two cannot disagree: the walk records a barrier
     * to this value and then reports this value (read back as the wrapper's recorded destination). The sampling
     * side ({@code general == false}) is {@code SHADER_READ_ONLY_OPTIMAL} for every published image - the layout
     * the sampling descriptors declare. The attach side differs by kind: a colour image's owner attaches it in
     * {@code GENERAL}, the layout its pass declares, but a depth image's owner attaches it in its own layout
     * ({@code ownerLayout}, normally {@code DEPTH_STENCIL_ATTACHMENT_OPTIMAL}) - {@code GENERAL} is a value the
     * depth owner's records never use, and handing a depth image back there left the owner's store record naming
     * a layout the next draw's descriptor did not declare (item 537 wall (c): the two
     * {@code VUID-vkCmdDraw-None-09600} on the published depth image).
     * <p>
     * Static and package-private so the rule is testable without a device, like
     * {@link #celtium$attachHandBackNeeded}: a green test here pins the decision table, not a recorded barrier.
     *
     * @param depth       {@code true} when the published name stands for a depth image (it carries a depth key)
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling side
     * @param ownerLayout the layout the depth image's owner attaches it in; ignored for colour names and on the
     *                    sampling side
     */
    static int celtium$publishedHandBackLayout(boolean depth, boolean general, int ownerLayout) {
        if (!general) {
            return org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;
        }
        return depth ? ownerLayout : org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
    }

    private void celtium$rebuildPublishedCache() {
        int size = backend.publishedTextures.publishedCount();
        GllessFrameTarget pairOwner = backend.offTargetTarget();
        this.cachedNames = new int[size];
        this.cachedWrappers = new VulkanGlTexture[size];
        this.cachedImages = new long[size];
        this.cachedPairIdentities = new long[size];
        this.cachedPairSlots = new int[size];
        this.cachedDepthKeys = new long[size];
        for (int i = 0; i < size; i++) {
            int name = backend.publishedTextures.publishedKeyAt(i);
            VulkanGlTexture wrapper = backend.publishedTextures.publishedValueAt(i);
            long image = this.publishedFrameImages.get(name);
            this.cachedNames[i] = name;
            this.cachedWrappers[i] = wrapper;
            this.cachedImages[i] = image;
            this.cachedPairIdentities[i] = pairOwner == null || image == 0L
                    ? Long.MIN_VALUE : pairOwner.chainPairIdentityForImage(image);
            this.cachedPairSlots[i] = pairOwner == null || this.cachedPairIdentities[i] == Long.MIN_VALUE
                    ? -1 : pairOwner.chainPairColorSlotForImage(this.cachedPairIdentities[i], image);
            this.cachedDepthKeys[i] = this.publishedFrameDepthKeys.get(name);
        }
    }

    /**
     * Moves every published frame texture to the layout its next use needs.
     * <p>
     * Two uses alternate on the same image: the pass that draws the game's framebuffer into the pair attaches it, which
     * declares GENERAL, and the pass that samples it through the published name has a descriptor declaring
     * SHADER_READ_ONLY_OPTIMAL. {@code general} picks which one is next; the sampling side is the transition that was
     * missing, and its absence is the VUID-vkCmdDraw-None-09600 the vsync leg reported at vkQueueSubmit.
     */
    void celtium$transitionPublishedFrameTextures(boolean general) {
        celtium$transitionPublishedFrameTextures(general, 0L);
    }

    /**
     * Records a layout move in the pair that owns the image, at the slot the cache resolved for it:
     * slot 0's record is the pair's own, a tuple slot's record is what that slot's acquire barrier
     * reads as its source layout.
     *
     * @return whether the pair's record changed - a settled name (the record already names this
     *         layout) writes nothing, and its caller skips the layout report for it too
     */
    private boolean notePairColorLayout(GllessFrameTarget pairOwner, long identity, int slot, int layout) {
        return pairOwner.noteChainPairColorLayoutAtSlotIfChanged(identity, slot, layout);
    }

    /**
     * The same walk, narrowed on the attach side to the names that stand for the image the pass is about to attach.
     * <p>
     * The hand-back exists for one reason: the pass declares {@code GENERAL} for attachment 0, so every published name
     * whose image that pass attaches has to be moved back before {@code vkCmdBeginRenderPass}
     * ({@code VUID-vkCmdBeginRenderPass-initialLayout-00900}). A name published over a <em>different</em> image is not
     * attached by this pass and is not moved by it, so handing it back is a transition nothing asked for - and the walk
     * that records it runs once per off-target window, over every published name (38 at the end of the measured run).
     * <p>
     * The sampling side ({@code general == false}) is unchanged: it is the direction that has to cover every name a
     * draw in the pass might sample, and that set is not known here.
     *
     * @param general     {@code true} for the attach-side hand-back, {@code false} for the sampling-side transition
     * @param attachImage the image the pass is about to attach, or 0 to hand back every published name as before
     */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachImage) {
        celtium$transitionPublishedFrameTextures(general, attachImage, 0L, null);
    }

    /** Zero-allocation overload for a single-color pass that may also attach a borrowed shared depth image. */
    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth) {
        celtium$transitionPublishedFrameTextures(general, attachColor, attachDepth, null);
    }

    /**
     * The same walk for a pass that attaches several images at once (a multi-target window's tuple): the
     * hand-back covers every attached slot's names, and the sampling side skips every attached slot's names.
     *
     * @param attachImages the images the pass is about to attach, or null to hand back every published name
     */
    void celtium$transitionPublishedFrameTextures(boolean general, long[] attachImages) {
        celtium$transitionPublishedFrameTextures(general, 0L, 0L, attachImages);
    }

    void celtium$transitionPublishedFrameTextures(boolean general, long attachColor, long attachDepth,
            long[] attachImages) {
        if (backend.publishedTextures.isEmptyPublished() || backend.commandBuffer == null) {
            return;
        }
        long publications = backend.publishedTextures.publishedVersion();
        GllessFrameTarget cachePairsOwner = backend.offTargetTarget();
        long pairs = cachePairsOwner == null ? -1L : cachePairsOwner.chainPairGenerationTotal();
        long frameColorImage = backend.frameTarget == null ? 0L : backend.frameTarget.colorImage();
        if (publications != this.publishedCacheVersion || pairs != this.publishedCachePairs
                || frameColorImage != this.publishedCacheFrameImage) {
            // The prune re-publishes a name whose pair image was recreated; it is the reason a stale resolution cannot
            // simply be cached forever, and the generation is part of the key for exactly that case. The frame
            // image is part of the key for the twin case: a name published over it goes stale when the image
            // rotates or is swapped, which neither of the other two components can see.
            celtium$pruneStalePublishedNames();
            this.publishedCacheVersion = backend.publishedTextures.publishedVersion();
            this.publishedCachePairs = pairs;
            this.publishedCacheFrameImage = frameColorImage;
            celtium$rebuildPublishedCache();
            if (backend.publishedTextures.isEmptyPublished()) {
                return;
            }
        }
        GllessFrameTarget pairOwner = backend.offTargetTarget();
        for (int cachedIndex = 0; cachedIndex < this.cachedNames.length; cachedIndex++) {
            int entryKey = this.cachedNames[cachedIndex];
            VulkanGlTexture entryValue = this.cachedWrappers[cachedIndex];
            long publishedImageHandleValue = this.cachedImages[cachedIndex];
            long publishedImageIdentity = this.cachedPairIdentities[cachedIndex];
            boolean pairImageName = publishedImageIdentity != Long.MIN_VALUE;
            if (!celtium$attachHandBackNeeded(general, attachColor, attachDepth, attachImages,
                    publishedImageHandleValue)) {
                // The hand-back is the attach's own precondition: only the names that stand for the image this pass
                // attaches have to be in GENERAL / DEPTH_STENCIL_ATTACHMENT_OPTIMAL for it. A name over another
                // image is not attached here and is not moved here, so the transition would be one nothing asked for.
                continue;
            }
            // The sampling side must not move an image (color OR depth) that this pass is about to attach: the pass
            // declares GENERAL for color attachments and DEPTH_STENCIL_ATTACHMENT_OPTIMAL for depth attachments, so
            // transitioning it to SHADER_READ_ONLY_OPTIMAL right before vkCmdBeginRenderPass causes
            // VUID-vkCmdBeginRenderPass-initialLayout-00900.
            if (!general && (backend.passAttachedColorImage != 0L || backend.passAttachedDepthImage != 0L)) {
                if (publishedImageHandleValue != 0L
                        && backend.celtium$passAttachesImage(publishedImageHandleValue)) {
                    backend.publishedTextures.noteSkippedForAttach();
                    if (backend.publishedTextures.skippedForAttach() == 1) {
                        backend.record("published name " + entryKey + " stands for the image this pass attaches (0x"
                                + Long.toHexString(backend.passAttachedColorImage) + "); the sampling-side transition"
                                + " would leave it in SHADER_READ_ONLY under a pass that declares GENERAL, so it is"
                                + " skipped and the attach-side hand-back keeps it in GENERAL");
                    }
                    if (backend.pairSkipLogs < 8) {
                        // Whether the refusal leaves a draw without the layout its descriptor declares: the count is
                        // of groups that sampled a published chain-pair name, this frame and cumulatively.
                        backend.pairSkipLogs++;
                        System.out.println("[Celtium] CELTIUM_PAIR_SKIP frame=" + backend.frameId
                                + " name=" + entryKey + " attached=0x"
                                + Long.toHexString(backend.passAttachedColorImage)
                                + " identity=0x" + Long.toHexString(backend.targetRegistry.currentIdentity())
                                + " samplingDrawsThisRun=" + backend.pairNameSamplingDraws
                                + " samplingDrawsLastFrame=" + backend.pairNameSamplingDrawsFrame
                                + " (0 means nothing sampled this name before the skip)");
                    }
                    continue;
                }
            }
            // A depth publication is decided apart from the colour names (item 537 wall (c)): its owner's
            // vocabulary has no GENERAL in it, so the hand-back target comes from the same decision the report
            // below uses, with the owner's own attachment layout as the depth answer.
            long depthKey = this.cachedDepthKeys[cachedIndex];
            int pairSlot = this.cachedPairSlots[cachedIndex];
            // Whether this name's pair record actually moves below: a settled name writes nothing
            // and earns no layout report (the report's summary cannot have changed through it).
            boolean pairRecordChanged = false;
            if (general) {
                if (depthKey != 0L) {
                    entryValue.ensureHandBackLayout(backend.commandBuffer, celtium$publishedHandBackLayout(true, true,
                            GllessFrameTarget.DEPTH_RENDER_LAYOUT));
                } else {
                    entryValue.ensureGeneralLayout(backend.commandBuffer);
                }
            } else if (pairImageName) {
                // The pair is never moved out of the layout its pass declares, so its sampling transition is not
                // served. Counted, so "the transition was skipped" is a measurement; and the pair's own record follows,
                // because the image is still in GENERAL and a record left at SHADER_READ_ONLY would be stale.
                backend.publishedTextures.noteSkippedForAttach();
                if (pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                    pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                            org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL);
                }
            } else {
                entryValue.ensureSampledLayout(backend.commandBuffer);
            }
            // The pair's own record of the image's layout follows the wrapper's barrier, but only for the names that
            // stand for the pair image on the hand-back side: on the sampling side (!general), pairImageName already
            // skipped the transition and recorded GENERAL above, so writing SHADER_READ_ONLY_OPTIMAL here would
            // overwrite the true GENERAL layout with a stale sampling layout.
            if (general && pairOwner != null && publishedImageIdentity != Long.MIN_VALUE) {
                pairRecordChanged = notePairColorLayout(pairOwner, publishedImageIdentity, pairSlot,
                        celtium$publishedHandBackLayout(false, true, 0));
            }
            // A depth publication's layout record is the shared depth store, not a pair, so the wrapper's move is
            // reported into it under the name's key: the owner's next attachment acquire reads the store for its
            // barrier's source layout, and the native depth copy reads it too. Without the report the store would
            // keep naming the attachment layout while the image sits in the read layout the sampling side left it
            // in, and the next acquire would name a source layout the image is not in. This is the report
            // SHADOW-NATIVE-DESIGN section 3.4 requires of the publication.
            // The reported value is the destination the wrapper's recording actually leaves the image in, read
            // back from the wrapper - not a value synthesised from `general`. The two agreed for colour, but for
            // depth the binary synthesis wrote GENERAL on the hand-back side: a layout the depth owner never
            // uses, over an image the hand-back above moved to the owner's attachment layout (item 537 wall (c)).
            if (depthKey != 0L && pairOwner != null) {
                // The destination is the value the transition above was told to reach, on both
                // deterministic routes: the hand-back names DEPTH_RENDER_LAYOUT, the sampling side
                // names the wrapper's sampling layout. Reading it back through foreignLayoutIn -
                // a synchronized lookup over two shared maps - per depth name per pass bought
                // nothing on those routes. The pair-name sampling route is the exception: it
                // records no transition, so the image stays wherever it was, and only the read-back
                // knows that value. The store write itself is skipped when it already names the
                // destination (a settled depth name).
                int depthDestination;
                if (general) {
                    depthDestination = GllessFrameTarget.DEPTH_RENDER_LAYOUT;
                } else if (pairImageName) {
                    depthDestination = entryValue.foreignLayoutIn(backend.commandBuffer);
                } else {
                    depthDestination = entryValue.samplingLayout();
                }
                pairOwner.noteSharedDepthLayoutIfChanged(depthKey, depthDestination);
            }
        }
    }

    /**
     * Drops every published name whose image is no longer the pair's live image.
     * <p>
     * A name is published over the renderer's own image so the game's composite can sample it by the name it binds. That
     * image is destroyed and replaced when the pair is recreated (a size change, the first real frame after the splash),
     * and the wrapper then names freed memory. Recording a barrier on it is not a validation warning: the menu leg
     * measured `vkCmdPipelineBarrier(): pImageMemoryBarriers[0].image Invalid VkImage Object 0x300000000030` and then a
     * <b>SIGSEGV inside libvulkan_radeon</b> — the driver dereferenced the freed handle. The unpublish-on-recreation rule
     * already existed ({@code celtium$syncPublishedChainOutput}), but it only ran on the chain-identity branch, so a
     * frame that samples the name before that branch runs used the stale wrapper. This is the same rule at the point of
     * use, so a recreated pair cannot be sampled through a name that still points at the old image.
     */
    void celtium$pruneStalePublishedNames() {
        if (backend.publishedTextures.isEmptyPublished()) {
            return;
        }
        GllessFrameTarget offTarget = backend.offTargetTarget();
        if (offTarget == null) {
            // Only the owned target carries the chain pairs the published names stand for; without one there is
            // nothing here to judge.
            return;
        }
        for (int idx = backend.publishedTextures.publishedCount() - 1; idx >= 0; idx--) {
            int name = backend.publishedTextures.publishedKeyAt(idx);
            long image = this.publishedFrameImages.get(name);
            long owner = this.publishedFrameImageIdentities.get(name);
            long depthKey = this.publishedFrameDepthKeys.get(name);
            long live;
            if (depthKey != 0L) {
                // A depth publication stands for the shared depth store's image for its key, not a pair's colour
                // image: the store is its liveness record, and a recreated store entry re-points the name the
                // same way a recreated pair re-points a colour name.
                live = offTarget.sharedDepthImage(depthKey);
            } else if (owner != Long.MIN_VALUE) {
                // The name stands for the slot it was published over: a tuple slot's live image is not slot 0's
                // (comparing against slot 0 re-published colortex4/5 and shadowcolor1 over the pair's own image).
                live = offTarget.chainPairColorImageAt(owner, this.publishedFrameImageSlots.get(name));
            } else if (image != 0L && (image == backend.frameTarget.colorImage()
                    || image == offTarget.offTargetColorImage())) {
                live = image;
            } else {
                live = 0L;
            }
            if (image != 0L && live != 0L && image == live) {
                continue;
            }
            if (live != 0L) {
                VulkanGlTexture republished;
                if (depthKey != 0L) {
                    long liveView = offTarget.sharedDepthView(depthKey);
                    int[] liveExtent = offTarget.sharedDepthExtent(depthKey);
                    int liveLayout = offTarget.sharedDepthLayout(depthKey);
                    republished = liveView == 0L || liveExtent == null
                            || liveLayout == org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED ? null
                                    : backend.celtium$registry().publishExternalDepthImage(name, live, liveView,
                                            liveExtent[0], liveExtent[1], liveLayout);
                } else {
                    republished = backend.celtium$registry().publishExternalImage(name, live,
                            offTarget.chainPairColorViewAt(owner, this.publishedFrameImageSlots.get(name)),
                            backend.targetWidth, backend.targetHeight);
                    // A republished wrapper is a fresh wrapper: without the flag it declares SHADER_READ_ONLY over an
                    // image the pair keeps in GENERAL (09600/00344 colour form, 00900 on the next attach).
                    if (republished != null) {
                        republished.notePairImage(true);
                    }
                }
                if (republished != null) {
                    backend.publishedTextures.setPublishedValueAt(idx, republished);
                    this.publishedFrameImages.put(name, live);
                    backend.publishedTextures.noteRepublished();
                    continue;
                }
            }
            backend.celtium$registry().unpublishExternalImage(name);
            this.publishedFrameImages.remove(name);
            this.publishedFrameImageIdentities.remove(name);
            this.publishedFrameImageSlots.remove(name);
            this.publishedFrameDepthKeys.remove(name);
            backend.publishedTextures.noteUnpublished();
            backend.publishedTextures.noteStalePruned();
            if (backend.publishedTextures.stalePruned() == 1) {
                backend.record("published name " + name + " named image 0x"
                        + (image == 0L ? "unknown" : Long.toHexString(image))
                        + " which is no longer the pair's image (0x" + Long.toHexString(live)
                        + " for identity 0x" + Long.toHexString(owner)
                        + "); unpublished before any barrier could be recorded on it");
            }
            backend.publishedTextures.removePublishedAt(idx);
        }
    }

    /**
     * Follows a pair re-creation for every published name whose image died with it.
     * <p>
     * With per-identity pairs the caller names <em>which</em> identity's pair was recreated: only names published over
     * that identity's image can be stale; the others name living images and are left alone. The name's owner is the
     * identity recorded at publish time.
     *
     * @param identity the framebuffer identity whose pair was (re)created
     * @param chainPairColorImage the pair's live colour image now
     */
    void celtium$syncPublishedChainOutput(long identity, long chainPairColorImage) {
        if (!backend.publishedTextures.isEmptyPublished()) {
            // The scan below walks every published name and asks two boxed-key lookups of each, and it runs on every
            // off-target window (13 a frame on the measured world scene) - which is where winHead's 0.1 ms a frame
            // comes from. What it looks for is a name whose image died with a recreated pair, so it can only find one
            // when that identity's pair was recreated or the publication set moved. Both are counters, and both are in
            // the key; when neither has moved the scan would find nothing again and is skipped. The key used to be the
            // identity and the pair's colour image, which misses on every window of a frame that binds thirteen
            // different framebuffers - the scan then ran thirteen times for a question whose answer could not change.
            long publications = backend.publishedTextures.publishedVersion();
            GllessFrameTarget scanTarget = backend.offTargetTarget();
            long generation = scanTarget == null ? -1L : scanTarget.chainPairGeneration(identity);
            long total = scanTarget == null ? -1L : scanTarget.chainPairGenerationTotal();
            if (total < this.syncScanTotal) {
                // A new frame target: its generations start over, so a memo from the previous one would say "already
                // scanned" for a pair that has just been created. The total is monotonic within one target.
                this.syncScanGenerations.clear();
            }
            this.syncScanTotal = total;
            if (this.syncScanGenerations.get(identity) == generation
                    && publications == this.syncScanVersion) {
                if (this.publishedChainOutput == null) {
                    return;
                }
                if (this.publishedChainIdentity == identity && this.publishedChainImage != chainPairColorImage) {
                    backend.celtium$registry().unpublishExternalImage(this.publishedChainTexture);
                    this.publishedChainOutput = null;
                    this.publishedChainImage = 0L;
                    return;
                }
                this.publishedChainOutput.ensureGeneralLayout(backend.commandBuffer);
                return;
            }
            this.syncScanGenerations.put(identity, generation);
            this.syncScanVersion = publications;
            GllessFrameTarget liveTarget = backend.offTargetTarget();
            long liveImage = liveTarget == null ? 0L : liveTarget.chainPairColorImage(identity);
            long liveView = liveTarget == null ? 0L : liveTarget.chainPairColorView(identity);
            for (int idx = backend.publishedTextures.publishedCount() - 1; idx >= 0; idx--) {
                int name = backend.publishedTextures.publishedKeyAt(idx);
                long image = this.publishedFrameImages.get(name);
                long owner = this.publishedFrameImageIdentities.get(name);
                if (owner != identity) {
                    continue;
                }
                int nameSlot = this.publishedFrameImageSlots.get(name);
                long slotImage = nameSlot == 0 || liveTarget == null ? liveImage
                        : liveTarget.chainPairColorImageAt(identity, nameSlot);
                long slotView = nameSlot == 0 || liveTarget == null ? liveView
                        : liveTarget.chainPairColorViewAt(identity, nameSlot);
                if (image != 0L && image == (nameSlot == 0 ? chainPairColorImage : slotImage)) {
                    continue;
                }
                if (slotImage != 0L && slotView != 0L) {
                    VulkanGlTexture republished = backend.celtium$registry().publishExternalImage(name,
                            slotImage, slotView, backend.targetWidth, backend.targetHeight);
                if (republished != null) {
                    republished.notePairImage(true);
                    backend.publishedTextures.setPublishedValueAt(idx, republished);
                    this.publishedFrameImages.put(name, slotImage);
                    backend.publishedTextures.noteRepublished();
                    continue;
                }
                }
                backend.celtium$registry().unpublishExternalImage(name);
                this.publishedFrameImages.remove(name);
                this.publishedFrameImageIdentities.remove(name);
                this.publishedFrameImageSlots.remove(name);
                backend.publishedTextures.noteUnpublished();
                backend.publishedTextures.removePublishedAt(idx);
            }
        }
        if (this.publishedChainOutput == null) {
            return;
        }
        if (this.publishedChainIdentity == identity && this.publishedChainImage != chainPairColorImage) {
            backend.celtium$registry().unpublishExternalImage(this.publishedChainTexture);
            this.publishedChainOutput = null;
            this.publishedChainImage = 0L;
            return;
        }
        this.publishedChainOutput.ensureGeneralLayout(backend.commandBuffer);
    }

    /**
     * The native pack frame seat's swapchain supply for this frame (D7 seat ruling,
     * NEON 2026-11-28, construction spec): a single colour attachment naming the
     * frame's active colour target - the borrowed present image's view while a
     * borrow is live, the target's own view on a bypass frame - with no depth
     * attachment (the executor's depth-format-0 contract) and the target's real
     * extent. The target's accessors are borrow-aware and its format is the
     * borrow's own gate, so the view and format named here always describe the
     * image the frame actually renders into.
     *
     * @return the targets, or null when this frame opens no seat: no live target,
     *         or a minimized 0x0 target (FP-4 - no borrow, no seat)
     */
    org.soutalab.celtium.core.frame.ActiveRenderTargets celtium$seatSwapchainTargets() {
        if (!(backend.frameTarget instanceof GllessFrameTarget target)) {
            return null;
        }
        int width = target.width();
        int height = target.height();
        if (width <= 0 || height <= 0) {
            return null;
        }
        return new org.soutalab.celtium.core.frame.ActiveRenderTargets(
                java.util.List.of(new org.soutalab.celtium.core.frame.ActiveRenderTargets.RenderTargetView(
                        target.colorView(), target.colorFormat(),
                        org.soutalab.celtium.core.frame.ActiveRenderTargets.LoadOp.DONT_CARE,
                        org.soutalab.celtium.core.frame.ActiveRenderTargets.StoreOp.STORE)),
                null, width, height);
    }
}
