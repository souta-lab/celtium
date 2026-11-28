package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import org.soutalab.celtium.vulkan.pack.ShaderPackRuntime;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;
import org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceMap;
import it.unimi.dsi.fastutil.objects.Reference2ReferenceOpenHashMap;
import org.soutalab.celtium.harness.HarnessConfig;
import org.soutalab.celtium.vulkan.DynamicLightService;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkClearValue;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.lwjgl.vulkan.VK10.*;

/**
 * The frame's submit sequence (refactoring plan R7).
 * <p>
 * The slots, their sequences and their retirement rules belong to {@link FrameSubmissionScheduler} since R6; what this
 * class owns is the order those rules are asked in and the single place a frame is handed to the queue. The state it
 * reads and writes stays the renderer's, reached through {@link Host} - a narrow contract rather than a copy, because
 * the frame's command buffer, its target and its counters have exactly one owner and it is not this class.
 * <p>
 * This is the first half of R7: the plan's root and facade come next, and the Host is what they will replace with
 * typed phase contexts.
 */
public final class FrameCoordinator {

    final VulkanContext context;
    final BackendDiagnostics reports;
    Lane2ImmediateRecorder funnel;
    PresentSwapchain presentSwapchain;
    VulkanBackendAdapter owner;

    FrameCoordinator(VulkanContext context, BackendDiagnostics reports) {
        this.context = context;
        this.reports = reports;
    }


    /** The failure injection the submit path honours, as the backend declares it (a property, not a constant). */
    private static int FAIL_SUBMITS_REMAINING = Integer.getInteger("celtium.vulkan.failSubmits", 0).intValue();

    public enum VanillaPostOutcome { EXECUTED, REFUSED_CREATION, REFUSED_UNSUPPORTED }

    private VanillaPostExecutor vanillaPost;

    private VulkanContext vanillaPostContext;

    private PublishedFrameTextures vanillaPostPublications;

    private long chainInputFlushFailures;

    private String lastVanillaPostRefusal;


    /** Installs the renderer's existing framebuffer publications. */
    void setVanillaPostPublications(PublishedFrameTextures publications) {
        this.vanillaPostPublications = publications;
    }


    /** Call outside a rendering instance, after the target's pending Lane 2 draws are recorded. */
    public VanillaPostOutcome runVanillaPostChain(java.util.List<VanillaPostPassSpec> specs) {
        // The mixin checks applicability before taking ownership of the group's render call.
        if (!isBundledVanillaPostChain(specs)) {
            this.lastVanillaPostRefusal = "non-vanilla-program";
            return VanillaPostOutcome.REFUSED_UNSUPPORTED;
        }
        this.lastVanillaPostRefusal = null;
        if (this.vanillaPost == null) {
            this.vanillaPost = new VanillaPostExecutor();
        }
        VanillaPostExecutor executor = this.vanillaPost;
        // The frame owner opens the seat before it knows whether a pack will use it.
        if (ShaderPackRuntime.builtInPackActive() && ShaderPackRuntime.nativeFrameOpen()) {
            return executor.refuseUnsupported("pack-frame-open");
        }
        VulkanContext context = this.context;
        VulkanCommandBufferState state = this.commandState;
        if (context == null || context.device() == null || this.gpuStalled
                || this.funnel.frameTarget == null || this.commandBuffer == null
                || state == null || !state.recording() || state.inRenderPass()) {
            return executor.refuseUnsupported("vanilla post requires a frame recording outside a rendering instance");
        }
        if (this.vanillaPostContext == null) {
            this.vanillaPostContext = context;
            context.setVanillaPostTeardown(() -> {
                executor.destroyIdle(context.device());
                this.vanillaPostContext = null;
            });
        }
        return executor.run(context, this.commandBuffer, this.frameSlot, this.funnel.frameTarget,
                this.funnel.targetRegistry, this.vanillaPostPublications, specs);
    }

    public static boolean isBundledVanillaPostChain(java.util.List<VanillaPostPassSpec> specs) {
        if (specs == null || specs.isEmpty()) return false;
        for (VanillaPostPassSpec spec : specs) {
            if (spec == null || !VanillaPostExecutor.isBundled(spec.program())) return false;
        }
        return true;
    }


    /**
     * A category-A refusal raised at the mixin boundary itself (ruling section 5.3): the chain is a
     * native target (the group's main is the game's framebuffer and every program is bundled), but
     * the boundary could not describe it natively - a pass that would not collect, or a chain input
     * with no colour texture to supply. The refusal is counted on the executor's census exactly like
     * a preflight refusal inside {@link #runVanillaPostChain}, and the caller still cancels the GL
     * original: on the GL-less path a silent fallback would draw nothing and say nothing.
     */
    public VanillaPostOutcome refuseVanillaPostChain(String reason) {
        if (this.vanillaPost == null) {
            this.vanillaPost = new VanillaPostExecutor();
        }
        this.lastVanillaPostRefusal = null;
        return this.vanillaPost.refuseUnsupported(reason);
    }

    public String lastVanillaPostRefusalReason() {
        if (this.lastVanillaPostRefusal != null) return this.lastVanillaPostRefusal;
        return this.vanillaPost == null ? null : this.vanillaPost.lastRefusalReason();
    }

    long vanillaPostRuns() { return this.vanillaPost == null ? 0 : this.vanillaPost.runs(); }

    long vanillaPostPasses() { return this.vanillaPost == null ? 0 : this.vanillaPost.passes(); }

    long postChainCreationFailures() { return this.vanillaPost == null ? 0 : this.vanillaPost.creationFailures(); }

    long postChainUnsupportedRefusals() { return this.vanillaPost == null ? 0 : this.vanillaPost.unsupportedRefusals(); }

    long chainInputFlushFailures() { return this.chainInputFlushFailures; }


    /** The caller must report a pending-input flush exception exactly once. */
    public void noteChainInputFlushFailure() {
        this.chainInputFlushFailures++;
    }


    // -- fixed stage template (plan section 2.2, S3-b seating) ------------------

    /**
     * The frame's static execution template. The generic dynamic frame-graph compiler the
     * plan rejects does not exist here: at the frame head the coordinator picks one of two
     * static templates in O(1), and the frame runs it with no dynamic reconfiguration.
     */
    enum FrameTemplate {
        /** Pack OFF: the V2-2 fixed four stages, zero pack tax. */
        FIXED_FOUR_STAGE,
        /** Pack ON: the isolated D+ executor ({@code PackFrameExecutor}) owns the frame. */
        PACK_EXECUTOR
    }


    /** The fixed stages of the pack-OFF template, in their only order. */
    enum Stage {
        UPLOAD, CULL_LIGHTGRID, MAIN_RENDER, HIZ_BUILD
    }


    /**
     * The O(1) template pick of plan section 2.2: a pack frame goes to the isolated
     * executor; everything else runs the fixed four stages. A mod custom-FBO request does
     * not switch the template - inside the fixed template it is absorbed by the main
     * stage (same dynamic-rendering target, or an isolated side FBO), so the frame's
     * skeleton does not break; the flag is accepted here so the frame head states both
     * judgements the plan names, and the main stage can read the recorded value.
     */
    static FrameTemplate selectTemplate(boolean packActive, boolean modFboRequested) {
        return packActive ? FrameTemplate.PACK_EXECUTOR : FrameTemplate.FIXED_FOUR_STAGE;
    }

    private FrameTemplate frameTemplate = FrameTemplate.FIXED_FOUR_STAGE;

    private boolean frameModFboRequested;

    private final boolean[] stageEntered = new boolean[Stage.values().length];

    private Stage lastStage;

    /**
     * Opens one frame: the E7-integrated frame head (the monolith's former beginCommands body,
     * now owned here) - slot pick and wait, the FP-1 borrowed-image acquire, the command buffer
     * begin, the native seat opening and the fixed-template stage recording, in that order.
     */
    void beginFrame(boolean packActive, boolean modFboRequested) {

        if (this.frameBufferOpen) {
            // The frame's buffer is still recording: a later segment (window, terrain pass) continues into it
            // without paying the slot wait, the reset/begin pair, the query reset or the indirect-page rebind -
            // the ~150-250 microseconds each recording was paying in cycle overhead, ~8-9 times a frame.
            this.commandBuffer = this.slotCommandBuffers[this.frameSlot];
            this.fence = this.slotFences[this.frameSlot];
            this.commandState = this.slotCommandStates[this.frameSlot];
            return;
        }
        if (this.commandState.recording()) throw new IllegalStateException("nested begin on backend command buffer");
        // OPT-08's ownership half: a recording takes the next frame slot, and only that slot's submission has to have
        // completed - the other slots may still be executing, which is the overlap the pipelined step needs. With one
        // buffer there was no such choice: the previous submission always had to finish first.
        this.frameSlot = this.nextFrameSlot;
        this.nextFrameSlot = (this.nextFrameSlot + 1) % FRAME_SLOTS;
        this.commandBuffer = this.slotCommandBuffers[this.frameSlot];
        this.fence = this.slotFences[this.frameSlot];
        this.commandState = this.slotCommandStates[this.frameSlot];
        if (this.scheduler.isPending(this.frameSlot)) {
            long waitStart = System.nanoTime();
            // This slot's own previous submission is still in flight: only this one blocks the reset below. Every
            // submission older than it has completed by the queue's own ordering, which is what the sequence watermark
            // records after a successful wait.
            this.scheduler.noteSlotReuseWait(this.frameSlot);
            if (!waitForSlot(this.frameSlot)) {
                this.noteStalled();
                this.submissionStatistics.noteStalledStops();
                this.reports.record("gpu stalled: frame slot " + this.frameSlot + " did not complete; drawing is refused from here");
                throw new IllegalStateException("pending frame slot command buffer did not complete");
            }
            long waitNanos = System.nanoTime() - waitStart;
            this.slotWaitNanos += waitNanos;
            FrameBudgetTimings.addSlotWait(waitNanos);
            if (waitNanos > 50_000_000L) {
                System.out.println("[Celtium] CELTIUM_SLOT_WAIT millis=" + (waitNanos / 1_000_000L)
                        + " slot=" + this.frameSlot + " frame=" + this.frameId);
            }
        }
        if (this.submissionPending) {
            // Confirmation, not a drain: whatever has completed frees what its sequence covers, and whatever is
            // still in flight is paid for by the next user of ITS slot - never by this recording. The queue-wide
            // wait that used to sit here is the stall this branch removes: it stopped the render thread on every
            // begin until the whole device was idle again.
            int confirmed = celtium$pollCompletedSlots();
            if (confirmed > 0 && !this.funnel.celtium$recordingOpen()
                    && this.scheduler.completedSubmitSequence() >= this.scheduler.submitSequence()) {
                this.submissionPending = false;
                // Nothing is in flight: the same "device genuinely idle" statement the drain used to establish, so
                // the retire queue may flush here (audit S12) and any off-target copy recorded for the previous
                // frame has certainly completed.
                int drained = VulkanRetireQueue.flushFor(this.context.device().address());
                if (drained > 0) {
                    this.submissionStatistics.addRetiredFlushes(drained);
                }
            }
            // The confirmed submissions' timestamps are readable now; the availability bit keeps whatever is still
            // in flight from reporting fiction.
            updateTimings();
        }
        if (this.funnel.drawRecords != null) {
            // The records store's frame boundary for this slot: its previous submission has
            // completed (a pending one was waited on above), and this recording has not begun,
            // so a lightmap write deferred while the slot's set was bound into the last frame's
            // recording can be applied here - the one seat where it lands under neither a
            // recording nor an executing command buffer.
            this.funnel.drawRecords.beginFrame(this.frameSlot);
        }
        // FP-1 (plan 8.3 item 8; D7 seat ruling, NEON 2026-11-28): this frame's present-image
        // borrow is decided here, at the frame head. The acquire used to sit in the present
        // body's tail - one frame early, and outside the frame it served. The ensure is
        // idempotent (a live borrow stands) and gated (a bypassed frame keeps its own image
        // and takes the composed present), and it must run before the recording opens below:
        // the ensure refuses while a recording is open. The native pack frame seat opened
        // further down is built from the target this decision leaves active.
        this.presentSwapchain.celtium$ensureBorrowedFrameImage();
        // No VK_COMMAND_BUFFER_RESET_RELEASE_RESOURCES_BIT: the flag tells the driver it may free the buffer's internal
        // allocations, and this buffer is reset and re-begun every frame - so the release is paid every frame and the
        // allocations are re-made every frame. The spec allows either; keeping them is the reuse the frame wants. The
        // abort paths keep the flag (they are rare and a released buffer is the honest state after a throw).
        check(vkResetCommandBuffer(this.commandBuffer, 0), "vkResetCommandBuffer");
        this.commandState.reset();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(this.commandBuffer, begin), "vkBeginCommandBuffer");
            // A begun command buffer starts with undefined push constants, and the cache is keyed by the buffer's
            // handle - which a reused buffer keeps across the reset. Without this the first draw of a re-recorded
            // buffer could elide its push and be drawn with whatever the previous recording left, which is the shape
            // of a draw that is issued, counted and leaves no pixels. The sky plane is exactly that first draw.
            VulkanPushCache.SHARED.invalidate();
            this.commandState.begin();
            // A fresh recording begins: a new recording epoch (INV-2c).
            this.funnel.recordingEpoch++;
            // The flag the retire drain guard reads. It was declared ("set at its begin") and only ever cleared, so
            // celtium$recordingOpen() was blind to the frame's own recording and rc.24's guard never fired - which is
            // why the tester's black screen came back after that fix with the same VUID (objects bound to an open
            // command buffer destroyed under it). Set here, at the one place the frame's unified buffer begins; the
            // coordinator clears it at submit and on every fallback path.
            this.frameBufferOpen = true;
            // S3-b D7: the native pack frame seat opens with the frame's unified buffer -
            // the one point where the context, the begun buffer and the frame id are all
            // live. The terrain driver resolves lazily through the factory (the frontend
            // is created on first terrain use). The swapchain supply is the frame's
            // active colour target, built by celtium$seatSwapchainTargets() from the
            // borrow decision the frame head just made (D7 seat ruling, NEON 2026-11-28:
            // option (b) - a null supply is not a landing form). A frame with no live
            // target, or a minimized 0x0 target (FP-4), opens no seat.
            if (org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.nativeFrameOpen()) {
                // A seat that escaped its own frame's closes must not leak a dead
                // command buffer into this frame: close it before this frame's opens.
                // The all-exit close discipline (finish, the abandon seats, the stall
                // seats) makes this unreachable in the steady state; it stands so a
                // missed close degrades to a lost pack frame, never to a stale seat.
                org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.endNativeFrame();
            }
            org.soutalab.celtium.core.frame.ActiveRenderTargets seatTargets =
                    this.presentSwapchain.celtium$seatSwapchainTargets();
            if (seatTargets != null) {
                org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.beginNativeFrame(
                        this.context, this.commandBuffer, seatTargets,
                        ctx -> this.owner.celtium$lane1().terrainDriver(), this.frameId);
            }
            // The coordinator's frame opens at the same point (plan 2.2 template pick).
            // A frame recorded through this backend runs the fixed four-stage template:
            // the pack executor, when a pack is active, records through the seat above
            // rather than through this frame path, so the pick's inputs are stated as
            // the fixed template's here and the executor's frames are picked on its own
            // entry when the world-pass hook is wired (S3-b D7-c).

        VulkanContext context = this.context;
        if (context != null) context.setDirectGlFrameSlot(this.frameSlot);
        this.frameTemplate = selectTemplate(packActive, modFboRequested);
        this.frameModFboRequested = modFboRequested;
        java.util.Arrays.fill(this.stageEntered, false);
        this.lastStage = null;
            // N05(2): bind every foreign-image transition recorded into this buffer to this recording. It becomes
            // fact only when the submission that carries it is accepted; opening a recording on a handle that still
            // has an uncommitted one abandons that one first, which is exactly the reset/abandon case.
            VulkanGlTexture.beginLayoutRecording(this.commandBuffer);
            // The named targets keep the same discipline on the same recording (audit-vulkan-core E2):
            // their transitions become fact only when this buffer's submission is accepted, and opening
            // this recording drops the previous one's uncommitted notes with it.
            this.funnel.targetRegistry.beginNamedTargetLayoutRecording(this.commandBuffer.address());
            // Queries must be reset before they are written again, and a fresh command buffer is exactly where that
            // belongs; without it validation reports every timestamp write as using an unavailable query.
            if (this.timestampPool != 0) {
                // Only the terrain pairs unless a report collects: the pool grew to TIMESTAMP_SLOTS for the window and
                // mark slots, and resetting all of them at every command buffer was a per-frame cost the shipping path
                // paid for a report it does not run (measured: 1.032x slower against the build before). The range must
                // follow the write path's own predicate - a window write without its reset is precisely
                // VUID-vkCmdWriteTimestamp-00830, which the frame budget's first leg reproduced.
                vkCmdResetQueryPool(this.commandBuffer, this.timestampPool, celtium$timestampPoolBase(),
                        celtium$windowTimestampsActive() ? TIMESTAMP_SLOTS : Lane2ImmediateRecorder.WINDOW_SLOT_BASE * 2);
                this.windowTimestampCount = 0;
                this.gpuMarkCount = 0;
                if (this.timestampsUsable && org.soutalab.celtium.vulkan.GpuPassTimingReport.enabled()) {
                    // Written only while the report is collecting: they are the report's own numbers, and a validation leg
                    // that is not asking for them should not carry extra query writes (measured: ten
                    // VUID-vkCmdWriteTimestamp-00830 errors on the discrete card when the batched end path also wrote one).
                    vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, this.timestampPool,
                            celtium$timestampPoolBase() + (FRAME_TIMESTAMP_SLOT * 2));
                    this.frameEndTimestampWritten = false;
                }
            }
            if (this.statisticsPool != 0) {
                // This slot's fence has completed above, so none of its queries are in flight. Queries the
                // readback never consumed are dropped samples, counted before the reset discards them.
                int written = this.statisticsSlotCounts[this.frameSlot];
                if (written > 0) {
                    int unread = written - Integer.bitCount(this.statisticsSlotConsumed[this.frameSlot]);
                    if (unread > 0) {
                        this.statisticsDroppedSamples += unread;
                    }
                }
                vkCmdResetQueryPool(this.commandBuffer, this.statisticsPool,
                        this.frameSlot * STATS_PER_SLOT, STATS_PER_SLOT);
                this.statisticsSlotCounts[this.frameSlot] = 0;
                this.statisticsSlotConsumed[this.frameSlot] = 0;
            }
        }
        // The indirect packet pages are slot-owned: the slot's prior fence already completed above (or it never
        // submitted), so its pages may be rewritten from the start of this recording. Without this call the field
        // stays -1 and every terrain batch falls back with "no_indirect_recording_slot_owns_the_packets" - measured
        // as offers=7011/fallbacks=7011/draws=0 on leg swap6-amd, the whole indirect path dead letter.
        // The reset above leaves every piece of dynamic state undefined, so the dedup trackers go to their never-
        // matching sentinels: the first group in this buffer emits whatever it needs, whatever it is.
        this.funnel.passVpX = this.funnel.passVpY = this.funnel.passVpW = this.funnel.passVpH = -1;
        this.funnel.passScX = this.funnel.passScY = this.funnel.passScW = this.funnel.passScH = -1;
        this.funnel.passBiasC = this.passBiasS = Float.NaN;
        // The pass area is the window's until a pass says otherwise; see the field's own note.
        this.funnel.passAreaWidth = this.funnel.targetWidth;
        this.funnel.passAreaHeight = this.funnel.targetHeight;
        // Open the unified frame buffer for this frame: recordings coalesce into it unconditionally - no
        // target carries a semaphore contract any more (Stage 2 submit unification).
        this.frameBufferOpen = true;
        // The frame's slot is final here (the slot wait and the ring slot reset are both above): the batch
        // wiring takes it for its staging adapter and opens the frame's first capture segment (S5-2).
        this.funnel.celtium$beginBatcherFrame();
    }


    /**
     * Records a stage's entry for this frame. Marks are a record, not a gate: the
     * segmented recording revisits stages (several windows record inside MAIN_RENDER,
     * uploads flush at several points), and the fixed order is a property of the final
     * single-pass main stage, not of a state machine bolted onto the segments. What the
     * record gives the legs and the instruments is the frame's actual stage structure:
     * which stages ran, and in which first-entry order.
     */
    void markStage(Stage stage) {
        this.stageEntered[stage.ordinal()] = true;
        this.lastStage = stage;
    }

    FrameTemplate frameTemplate() {
        return this.frameTemplate;
    }

    boolean frameModFboRequested() {
        return this.frameModFboRequested;
    }

    boolean stageEntered(Stage stage) {
        return this.stageEntered[stage.ordinal()];
    }


    /** One line naming the template and the stages this frame entered, for diagnostics. */
    String stageSummary() {
        StringBuilder out = new StringBuilder("template=").append(this.frameTemplate);
        for (Stage stage : Stage.values()) {
            if (this.stageEntered[stage.ordinal()]) {
                out.append(' ').append(stage);
            }
        }
        return out.toString();
    }


    /** Recording acceptance is not queue submission, and neither means GPU completion. */
    enum Submission {
        SKIPPED, RECORDED, QUEUED;

        /**
         * Whether the frame's target may close on this outcome. There is no external semaphore packet a
         * close could owe (Stage 2 submit unification removed the wait/done pair), so an accepted recording
         * closes the target exactly as a queued submission does; only a skipped one does not.
         */
        boolean canCloseTarget() {
            return this == QUEUED || this == RECORDED;
        }
    }


    /**
     * Wall time spent inside the queue submission. The steady-state profile put 61% of the client thread's samples inside
     * vkQueueSubmit while the GPU sat at 3% busy, so the question is whether that call blocks and for how long; these
     * counters say it with numbers rather than with a profile's stack attribution.
     */
    static long submitQueueNanos;

    static long submitQueueCalls;


    /** {@return the accumulated queue-submit time and call count as one line}, for the frame statistics. */
    public static String submitQueueSummary() {
        long calls = submitQueueCalls;
        return "submitCalls=" + calls
                + " submitTotalMs=" + (submitQueueNanos / 1000000L)
                + " submitMeanMs=" + String.format(java.util.Locale.ROOT, "%.3f",
                        calls == 0 ? 0.0 : submitQueueNanos / 1e6 / calls);
    }

    Submission submitCommands() {
        if (this.gpuStalled) {
            // Nothing to end: the buffer was never begun. This is the last of the recording entries the steady-state injection
            // reported, after the pass-level ones.
            return Submission.SKIPPED;
        }
        // OPT-07: every copy the frame recorded goes out as one submission before this one, so the vertices, indices
        // and uniform bytes this command buffer reads are in place. It is a no-op when nothing is pending, and it is
        // the frame's own ordering statement: a copy no longer needs a submission and a wait of its own.
        // The frame's own flush attributes to the frame's own budget window (upload budget layer 2):
        // supply the exact frame key before flushing.
        markStage(Stage.UPLOAD);
        this.context.celtium$supplyUploadBudgetFrameKey(this.frameId);
        this.context.flushUploads();
        if (this.frameBufferOpen) {
            // Unified frame buffer: the segment's commands stay recorded; end, submission and bookkeeping all happen
            // once at the frame boundary via celtium$finishFrameBuffer.
            return Submission.RECORDED;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The frame's own GPU-time pair closes here, so it spans both the passes and everything between them.
            this.recordFrameEndTimestamp();
            check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer");
            this.commandState.end();
            if (this.frameBufferOpen) {
                // Falling back to a direct submit mid-frame: the partially recorded frame buffer is ended above and
                // armed by its own slot fence like any ordinary submission, so the flag's story ends here.
                this.frameBufferOpen = (false);
            }
            // Direct present path: when the frame renders into a borrowed swapchain image, the first
            // submission that touches it waits on the borrow's acquire semaphore - the presentation
            // engine's release of the image is the one ordering fact queue order cannot give. The wait/done
            // pair the frame's submissions used to carry is gone (Stage 2 submit unification): on one
            // queue, submission order already gives that ordering, and no signal array is owed either.
            long[] waitArray = celtium$presentAcquireWait();
            int[] stageArray = null;
            long[] signalArray = null;
            if (waitArray != null) {
                stageArray = new int[] {VK_PIPELINE_STAGE_ALL_COMMANDS_BIT};
            }
            if (this.funnel.immediateWindowBusy && !this.recordedImmediateGroups.isEmpty() && this.failImmediateSubmits > 0) {
                this.failImmediateSubmits--;
                throw new IllegalStateException("injected immediate submit failure before queue ownership");
            }
            if (FAIL_SUBMITS_REMAINING > 0) {
                // Test-only injection, so the fail-closed paths can be exercised instead of merely compiled: the
                // submission is treated as refused by the driver, which is the case the audit's E asks to be shown to
                // end in bounded time rather than in a wait for a signal that never comes.
                FAIL_SUBMITS_REMAINING--;
                this.reports.record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
                throw new IllegalStateException("injected submit failure before queue ownership");
            }
            if (VulkanSubmitFault.shouldFail("fenced")) {
                // The failure is raised exactly where a driver refusal would be, before the queue has seen anything, so
                // the code's own pre-submit rules apply: what is already recorded stays recorded and nothing in flight is
                // touched. That is the property this switch exists to check.
                check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
            }
            long submitStartedNanos = System.nanoTime();
            // One vkQueueSubmit arms ONE fence, so ~9 recordings a frame were ~9 driver calls. The batch keeps
            // every VkSubmitInfo and submits them together at the frame's flush points: same queue order, one
            // call, and the shared batch fence stands in for the slot's own fence via the reclaim callback.
            this.celtium$submitSlotDeferred(this.commandBuffer, waitArray, stageArray, signalArray);
            submitQueueNanos += System.nanoTime() - submitStartedNanos;
            submitQueueCalls++;

            this.celtium$noteDirectSubmit();
            celtium$noteSubmissionBookkeeping();
            return Submission.QUEUED;
        }
    }


    /**
     * Ends and queues the frame's single command buffer: every recording this frame made went into it, and this is
     * the one place it hands to the queue - via the batched deferred list, so the frame's own vkQueueSubmit is the
     * boundary flush's single call covering every recording.
     * <p>
     * Called from {@code celtium$flushPendingAtFrameBoundary} after the last window's records land in the buffer,
     * and again defensively at the top of the present path for callers that skip the boundary. No-op when no
     * unified buffer is open (a fallback direct submit already ran).
     */
    void celtium$finishFrameBuffer() {
        if (!this.frameBufferOpen) {
            return;
        }
        this.frameBufferOpen = (false);
        if (this.gpuStalled) {
            // The buffer may have been begun then stalled mid-frame: it was never submitted, so it may be reset by
            // the next beginCommands without a wait. The state object goes back to initial with it, or that begin
            // reports a nested begin on a recording nothing will ever end.
            this.commandState.reset();
            return;
        }
        markStage(Stage.UPLOAD);
        this.context.celtium$supplyUploadBudgetFrameKey(this.frameId);
        this.context.flushUploads();
        // S3 HiZBuild's seat: the fixed template's last stage records here, after the main
        // stage's depth exists and before the buffer ends. The host records the actual
        // MAX-reduction pyramid build into this bracket (D8-a).
        markStage(Stage.HIZ_BUILD);
        this.celtium$recordHiZBuild();
        // The batched path ends the buffer here rather than in submit(), so the frame's GPU-time pair closes here too.
        this.recordFrameEndTimestamp();
        // Direct present path: when the frame renders into a borrowed swapchain image, the first
        // submission that touches it waits on the borrow's acquire semaphore - the presentation
        // engine's release of the image is the one ordering fact queue order cannot give. The wait/done
        // pair the frame's submissions used to carry is gone (Stage 2 submit unification): on one
        // queue, submission order already gives that ordering, and no signal array is owed either.
        long[] waitArray = celtium$presentAcquireWait();
        int[] stageArray = null;
        long[] signalArray = null;
        if (waitArray != null) {
            stageArray = new int[] {VK_PIPELINE_STAGE_ALL_COMMANDS_BIT};
        }
        check(vkEndCommandBuffer(this.commandBuffer), "vkEndCommandBuffer(frame)");
        this.commandState.end();
        if (this.funnel.immediateWindowBusy && !this.recordedImmediateGroups.isEmpty() && this.failImmediateSubmits > 0) {
            this.failImmediateSubmits--;
            throw new IllegalStateException("injected immediate submit failure before queue ownership");
        }
        if (FAIL_SUBMITS_REMAINING > 0) {
            FAIL_SUBMITS_REMAINING--;
            this.reports.record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
            throw new IllegalStateException("injected submit failure before queue ownership");
        }
        if (VulkanSubmitFault.shouldFail("fenced")) {
            check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
        }
        this.celtium$submitSlotDeferred(this.commandBuffer, waitArray, stageArray, signalArray);
        celtium$noteSubmissionBookkeeping();
    }


    /**
     * {@return the one-element wait array carrying the frame target's borrowed-image acquire semaphore},
     * or null when no borrow's acquire is waiting to be consumed. Consume-on-read at the target: only the
     * first submission built after a borrow sees it, which is exactly the submission that must wait.
     */
    private long[] celtium$presentAcquireWait() {
        long acquire = this.funnel.frameTarget.presentAcquireSemaphore();
        return acquire == VK_NULL_HANDLE ? null : new long[] {acquire};
    }


    /**
     * The bookkeeping one accepted submission makes true - identical for the direct {@link #submitCommands} submit
     * and the deferred {@link #celtium$finishFrameBuffer} one: arena in-flight marking, layout commit, the pending
     * bit, the slot's sequence record, the recorded groups' retirement tags and the reached-queue flags.
     */
    private void celtium$noteSubmissionBookkeeping() {
        // OPT-05: the arena slot that holds this frame's immediate geometry is now in flight, and may not be reset
        // until a wait confirms the submission completed. Recorded here rather than at the wait, because a submit
        // that never reaches the queue must not hold a slot, and an abandon after a successful submit must.
        if (this.frameArena != null) {
            // The sequence increments a few lines below, where the slot's own record is armed; the value this
            // submission will take is the current one plus one.
            this.frameArena.submitted(this.frameId, this.scheduler.submitSequence() + 1);
        }
        // The queue accepted this submission, so the layouts the recording declared into it are now the truth
        // every wrapper of those images shares. A refused submit throws before this line, leaving the recording
        // pending for the next beginCommands() to abandon - and the next ensureGeneralLayout/ensureSampledLayout
        // to re-record.
        VulkanGlTexture.commitLayoutRecording(this.commandBuffer);
        // The named targets' recorded transitions become fact at the same point and by the same rule
        // (audit-vulkan-core E2): their registry layout is what the last accepted submission left, never
        // what a recording merely drew into a buffer that may still be abandoned.
        this.funnel.targetRegistry.commitNamedTargetLayoutRecording(this.commandBuffer.address());
        this.submissionPending = (true);
        // OPT-08: the submission belongs to a slot and carries a sequence. The slot's fence is what makes its
        // buffer reusable, and the sequence watermark is what releases the resources this submission still names -
        // not "some wait succeeded", which is what a single-frame renderer could get away with.
        this.scheduler.noteSubmitted(this.frameSlot);
        SubmitTrail.note(SubmitTrail.FRAME, this.scheduler.submitSequence(), this.frameSlot);
        this.commandState.submitted();
        for (ImmediateGroup group : this.recordedImmediateGroups) {
            this.funnel.immediateStatistics.addImmediateSubmittedBatches(group.batches);
            // This submission is the newest one that can still name the group's arena bytes: its mesh may be
            // freed exactly when the watermark covers this sequence and not before.
            group.retireSequence = this.scheduler.submitSequence();
            this.retiredImmediateGroups.add(group);
        }
        this.recordedImmediateGroups.clear();
        this.frameSubmitReachedQueue = (true);
        this.setLastFrameReachedQueue(true);
    }

public void setLastFrameReachedQueue(boolean reached) {
            // Temporary probe (2026-10-15): the present skips every frame with "no completed frame waiting" even though
            // the pass-submission counter shows frames being submitted, so the question is whether this setter is ever
            // called with true at all. Bounded to a dozen lines.
            if (reached && celtium$lastFrameReachedQueueLogs++ < 12) {
                System.out.println("[Celtium] CELTIUM_FRAME_QUEUED reached=true call=" + celtium$lastFrameReachedQueueLogs
                        + " presentHookCalls=" + FrameCoordinator.this.presentHookCalls);
            }
            FrameCoordinator.this.lastFrameReachedQueue = reached;
            if (reached) {
                FrameCoordinator.this.queuedFrameId = FrameCoordinator.this.frameId;
            }
        }


    /**
     * The slot-wait decision point's fallback counters (Stage 2): every {@code waitForSlot} call that
     * actually waits on a pending submission is one attempt, confirmed by the timeline wait (fast) or
     * by the fence wait it falls through to (fallback). Counting only - rendering behaviour is unchanged.
     */
    private static final FallbackPaths.Handle TIMELINE_WAIT_PATH =
            FallbackPaths.register(FallbackPaths.SYNC_TIMELINE);

    private long commandPool;

    VkCommandBuffer commandBuffer;

    private long fence;

    private long frameIndex;

    /** The arena's own frame counter: advanced at every frame boundary, world pass or not. */
    long arenaFrameId;

    /** Whether a world snapshot ran since the last frame boundary, so the menu-only resets know to stay out. */
    boolean frameSnapshotSinceBoundary;

    private long readbackBuffer;

    /** One slot per remembered draw; 32 covers a frame's visible sections at a typical render distance. */
    static final int READBACK_SLOTS = 32;

    /** The diagnostic copy's size: one whole block per remembered draw, and the range the guard must cover. */
    private static final int READBACK_COPY_BYTES = 64;

    /** Whether the copy for each slot was actually issued; a refused slot's block is stale, not sampled data. */
    private final boolean[] readbackValid = new boolean[READBACK_SLOTS];

    /** The region each remembered draw belongs to; the offsets alone cannot say which arena to read. */


    private long readbackMemory;

    private long readbackMapped;

    /**
     * The frame the renderer last finished, for diagnostics that run outside it (the swapchain's flicker sampler).
     * <p>
     * The pair the sampler compares is two presents, and a present is not a frame: the loading screen presents hundreds
     * of frames before the world exists. Without this number a census line cannot be tied to either frame of the pair,
     * which is exactly the correlation the sky band needs.
     */
    static volatile long lastFinishedFrameId;

    boolean submissionPending;

    VulkanCommandBufferState commandState;

    final java.util.List<ImmediateGroup> recordedImmediateGroups = new java.util.ArrayList<ImmediateGroup>();

    private int failImmediateSubmits;


    /** Restricted to the small validation scene, so retry is exercised after a real batch was accepted. */
    public void injectImmediateSubmitFailure(int count) {
        if (!HarnessConfig.bool("commandBufferTargets", false) || count < 0 || count > 1)
            throw new IllegalArgumentException("submit failure injection requires the target acceptance fixture");
        this.failImmediateSubmits = count;
    }


    /**
     * {@return whether this frame's recordings are being coalesced into one open frame buffer}.
     * <p>
     * An immediate window's groups are recorded into the open unified buffer and the submission happens at the
     * frame boundary, so the execution counters cannot advance at a bind the way they do on the direct path. A
     * validation client has to read this to assert the right contract instead of encoding one mode's timing as
     * the rule.
     */
    public boolean celtium$unifiedFrameBuffer() {
        return !this.gpuStalled;
    }


    /**
     * Whether the shared images are currently handed to Vulkan. The round trip is per *frame*, not per pass: the
     * terrain is drawn by three passes in a row and handing the images back between them costs a full colour copy
     */
    /** CPU time spent inside the GL/Vulkan handoff, so the debug overlay can show what the round trip costs. */
    long handoffNanos;

    long handoffSamples;

    long handoffWorstNanos;

    int frameOpens;

    int frameCloses;


    /** Timestamp queries: two per render pass, so the debug overlay can show real terrain times under Vulkan. */
    private static final int TIMESTAMP_SLOTS = 320;

    /**
     * Labelled single timestamps along the last command buffer (queries {@code MARK_BASE..}), written only while the
     * GPU pass report collects: the delta between two marks is the GPU time of the commands recorded between them -
     * barriers and clears included, which no pass bracket owns.
     */
    private static final int MARK_BASE = 64;

    private static final int MARKS = 240;

    static final String[] GROUP_MARK_LABELS = new String[VulkanImmediateFormats.COUNT];

    private int gpuMarkCount;

    private final String[] gpuMarkLabels = new String[MARKS];

    private String gpuMarkTimeline = "";


    /** Writes a labelled GPU mark when the GPU pass report is collecting; otherwise nothing. */
    void gpuMark(String label) {
        if (this.timestampPool == 0L || !this.timestampsUsable || this.gpuMarkCount >= MARKS
                || !org.soutalab.celtium.vulkan.GpuPassTimingReport.enabled() || this.commandBuffer == null) {
            return;
        }
        this.gpuMarkLabels[this.gpuMarkCount] = label;
        vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, this.timestampPool,
                celtium$timestampPoolBase() + MARK_BASE + this.gpuMarkCount);
        this.gpuMarkCount++;
    }

    int windowTimestampCount;

    private long windowGpuMainNanos;

    private long windowGpuOffNanos;

    private long windowGpuMaxNanos;

    private int windowGpuCount;

    /**
     * The slot that measures the whole frame's GPU time, from the start of the command buffer to its end.
     * <p>
     * The terrain passes and the immediate window together account for under two milliseconds on the integrated GPU
     * while the frame takes nine, so the question the per-pass numbers cannot answer is whether the GPU is busy for
     * the rest or idle while the host waits. This one pair answers exactly that.
     */
    private static final int FRAME_TIMESTAMP_SLOT = (TIMESTAMP_SLOTS / 2) - 2;

    long timestampPool;


    /**
     * The pipeline-statistics instrument's queries per frame slot (item 359). Eight is headroom, not an
     * invariant: a frame records three passes by default, four with the cutout split, and the shadow view
     * records through the same loop - so the count is not derived from {@link #MAX_UNIFORM_PASSES}, which
     * bounds uniform blocks, not passes. A pass that finds no free query is counted in
     * {@link #statisticsDroppedPasses}, never silently skipped.
     */
    private static final int STATS_PER_SLOT = 8;

    /** The statistics pool: a query type of its own, so it cannot share the timestamp pool. */
    private long statisticsPool;

    /** Queries written per frame slot in that slot's current recording, and the bitmask of those already read. */
    private final int[] statisticsSlotCounts = new int[FRAME_SLOTS];

    private final int[] statisticsSlotConsumed = new int[FRAME_SLOTS];

    /** Per-slot, per-query snapshots taken at record time: the pass, its logical draws, sections and charge. */
    private final String[][] statisticsSlotPasses = new String[FRAME_SLOTS][STATS_PER_SLOT];

    private final long[][] statisticsSlotDraws = new long[FRAME_SLOTS][STATS_PER_SLOT];

    private final long[][] statisticsSlotSections = new long[FRAME_SLOTS][STATS_PER_SLOT];

    private final long[][] statisticsSlotCharges = new long[FRAME_SLOTS][STATS_PER_SLOT];

    /** The open query's accumulated unsorted budget charge, summed as its batches are charged. */
    private long statisticsChargeAccum;

    /** Window (since the last printed line) and cumulative totals, per pass; see TerrainStatisticsReport. */
    private final java.util.Map<String, long[]> statisticsWindowTotals =
            new Reference2ObjectOpenHashMap<>();

    private long statisticsWindowStartFrame = -1L;

    private long statisticsLastPrintFrame = -1L;

    long statisticsSamples;

    private long statisticsDroppedPasses;

    private long statisticsDroppedSamples;

    private long statisticsReadFailures;

    /** The immediate window pass's last measured GPU time, in nanoseconds. */
    private long immediatePassGpuNanos;

    private boolean immediatePassTimed;

    /** The whole frame's last measured GPU time, in nanoseconds, and whether this frame recorded it. */
    private long frameGpuNanos;

    private boolean frameGpuTimed;

    /** Whether this frame's end timestamp has been written, so a frame that takes both end paths writes it once. */
    private boolean frameEndTimestampWritten;

    /**
     * The part of the frame's GPU time that happens before the first pass starts and after the last one ends.
     * <p>
     * The passes are timed, and so is the whole frame; the difference between those two numbers is what the integrated
     * GPU spends outside any pass, and it is several milliseconds there. Splitting it into "before the first draw" and
     * "after the last draw" says whether it is the handoff and acquire at the head of the frame or the composite and
     * present preparation at its tail - two different problems with different fixes.
     */
    private long frameHeadNanos;

    private long frameTailNanos;

    private float timestampPeriod = 1.0f;


    // The GPU pass timings are reported by GpuPassTimingReport, which owns the format and the interval.
    boolean timestampsUsable;

    /**
     * Forces the no-timestamp path, so the behaviour on a device whose queue cannot record timestamps can be measured
     * on hardware that can: {@code -Dceltium.vulkan.forceNoTimestamps=true}.
     */
    private static final boolean FORCE_NO_TIMESTAMPS = Boolean.getBoolean("celtium.vulkan.forceNoTimestamps");

    private final Reference2ReferenceMap<String, Long> passTimings = new Reference2ReferenceOpenHashMap<>();

    /**
     * The pack route's per-stage timings, under the pack pipeline's own stage names ({@code shadow} /
     * {@code gbuffers}): the same last-measured-value persistence as {@link #passTimings}, keyed by name
     * because one subpass object serves both stages in a frame and cannot name them apart.
     */
    private final java.util.Map<String, Long> packPassTimings = new java.util.LinkedHashMap<>();

    private int readbackSlots;

    private long readbackOffset;

    final Reference2ReferenceMap<Integer, String> timestampPasses = new Reference2ReferenceOpenHashMap<>();

    /** Which pack stage each live timed slot belongs to, for the slots the pack route armed. */
    final java.util.Map<Integer, String> timestampPackPassNames = new java.util.HashMap<>();


    /** The offset of the recording frame slot's query range within the timestamp pool. */
    int celtium$timestampPoolBase() {
        return this.frameSlot * TIMESTAMP_SLOTS;
    }

    /**
     * Per-submission snapshots of the timing state above, and which of them a readback may consume.
     * The live accumulators are moved into a snapshot at submit time and restart empty; the readback
     * consumes a snapshot only once the scheduler confirms its submission. See TerrainTimingReadback
     * for why reading the live state instead filed zero for every frame.
     */
    private final TerrainTimingReadback timingReadback = new TerrainTimingReadback();

    /** The readback's census: snapshots consumed, and timestamp slots a consumed read found unavailable. */
    private long timingSnapshotsRead;

    private long timingSlotsUnavailable;

    private boolean readbackPending;

    private boolean readbackDone;

    /** A completed frame's content is in the shared image and has not been presented yet. */
    boolean lastFrameReachedQueue;

    /** The frame identity whose work was last queued, so a present can say how far behind the game it is. */
    long queuedFrameId = -1L;

    /** Per-frame present calls that arrived, and why the last one did not present. */
    int presentHookCalls;

    // The swap-point accounting (swapPoint*) lives in PresentSwapchain with the present path it
    // measures (item 704, R3 candidate 2 carried out with the R10 extraction).
    /**
     * The last {@link #DISTRIBUTION_SAMPLES} durations of the two frame costs that decide the interval but are invisible
     * in the render brackets: the swap-point image path (frame image to swapchain blit plus the wait for the frame's
     * completion) and the time from a submission reaching the queue to its fence being confirmed. Totals cannot answer
     * "what does a steady-state frame pay" - the load phase dominates them - so the summary reports p50/p95/worst of
     * both.
     */
    static final int DISTRIBUTION_SAMPLES = 512;

    final long[] submitToFenceRing = new long[DISTRIBUTION_SAMPLES];

    private int submitToFenceRingAt;

    int submitToFenceRingCount;

    /** When the current slot's submission reached the queue, for the submit-to-fence measurement. */
    private final long[] slotSubmitNanos = new long[FRAME_SLOTS];

    private void celtium$noteSubmitToFence(int slot, long nanos) {
        if (nanos <= 0L) {
            return;
        }
        this.submitToFenceRing[this.submitToFenceRingAt] = nanos;
        this.submitToFenceRingAt = (this.submitToFenceRingAt + 1) % DISTRIBUTION_SAMPLES;
        if (this.submitToFenceRingCount < DISTRIBUTION_SAMPLES) {
            this.submitToFenceRingCount++;
        }
    }


    /** {@return "p50/p95/worst" in milliseconds over the ring, or "n/a" when nothing was sampled}. */
    static String celtium$distribution(long[] ring, int count) {
        if (count <= 0) {
            return "n/a";
        }
        long[] copy = java.util.Arrays.copyOf(ring, count);
        java.util.Arrays.sort(copy);
        long p50 = copy[(int) (count * 0.50)] / 1000000L;
        long p95 = copy[Math.min(count - 1, (int) (count * 0.95))] / 1000000L;
        long worst = copy[count - 1] / 1000000L;
        return p50 + "/" + p95 + "/" + worst + " n=" + count;
    }

    /** Bounded probe counter for the frame-queued setter. */
    private int celtium$lastFrameReachedQueueLogs;


    /**
     * Token of the device-wide wait in progress, and the token whose wait has returned. Per-call rather than one
     * shared flag: a late watchdog must be able to tell "my wait is still running" from "a later wait is running now"
     * (audit A05).
     */
    private long deviceWaitFinishedToken = -1L;

    private final Object deviceWaitLock = new Object();


    /** The submission domain's counters (refactoring plan R2). */
    final SubmissionStatistics submissionStatistics = new SubmissionStatistics();



    /** The frame slots, their completion epochs and the retirement rules (refactoring plan R6). */
    final FrameSubmissionScheduler scheduler = new FrameSubmissionScheduler();


    /** Set when a wait timed out: no further recording or submission happens until the renderer is rebuilt. */
    boolean gpuStalled;


    /**
     * Marks the device stalled and says where from, once.
     * <p>
     * The stall is permanent - nothing clears it, by design, because the resources it protects may still be owned by a
     * command buffer that never completed. It happened on a mid-run resize and left the present path declining with
     * "no frame target, or stalled" for the rest of the session (presentedFromVulkan frozen at 355), so which of the four
     * places set it is the fact the fix needs.
     */
    void noteStalled() {
        if (!this.gpuStalled) {
            this.gpuStalled = true;
            StackTraceElement[] stack = new Throwable().getStackTrace();
            String where = "unknown";
            // The first frame outside this class says which caller's path reached the latch; the first frame inside
            // it says which of the ten noteStalled() sites did. Recording only the outer one hid the site - measured
            // on the loading screen's font path, where the outer frame was the font submit and the site was not the
            // window-flush catch it was first read as.
            String site = "unknown";
            for (StackTraceElement frame : stack) {
                if (frame.getClassName().startsWith("org.soutalab.celtium.vulkan.VulkanTerrainGpuBackend")) {
                    if (site.equals("unknown") && !"noteStalled".equals(frame.getMethodName())) {
                        site = frame.getMethodName() + ':' + frame.getLineNumber();
                    }
                    continue;
                }
                where = frame.getClassName() + '.' + frame.getMethodName() + ':' + frame.getLineNumber();
                break;
            }
            this.submissionStatistics.setStallOrigin(where + " via " + site);
            // The last submissions ride along (item 414, hole 3): the counters alone print at teardown, and the
            // teardown that detects a loss throws before its own print - the stall line is the record that
            // survives, so it carries the trail and both sequence watermarks with it.
            System.out.println("[Celtium] CELTIUM_STALL site=" + where + " latchSite=" + site
                    + " target=" + this.funnel.targetWidth + "x"
                    + this.funnel.targetHeight + " frameId=" + this.frameId + " frameTargetNull=" + (this.funnel.frameTarget == null)
                    + " presentSkipReason=[" + this.funnel.presentSkipReason + "]"
                    + " submitSequence=" + this.scheduler.submitSequence()
                    + " completedSubmitSequence=" + this.scheduler.completedSubmitSequence()
                    + " " + SubmitTrail.describe());
        }
        this.submissionStatistics.noteStalledStops();
    }

    /** One-shot count for the injected device-lost fence result. */
    private int injectedDeviceLosses;

    /**
     * The immediate path's vertex and index memory for the frame being recorded (OPT-05).
     * <p>
     * One arena, suballocated per group, instead of two {@code VkBuffer}/{@code VkDeviceMemory}/{@code map} pairs per
     * group (BH-28). Created on the first group of the first frame that needs it, so a run that never draws an
     * immediate batch pays nothing. A group that does not fit even at the arena's cap promotes itself to buffers it
     * owns rather than being dropped, and the arena's own counters say how often that happened.
     */
    VulkanFrameArena frameArena;

    /** {@code -Dceltium.vulkan.skipPresent=true}: record and submit, never present (isolation for the Wayland fault). */
    static final boolean SKIP_PRESENT = Boolean.getBoolean("celtium.vulkan.skipPresent");

    private static final boolean SWAPCHAIN_PROBE = Boolean.getBoolean(VulkanContext.SWAPCHAIN_PROPERTY);

    /**
     * A real frame identity (audit S11).
     * <p>
     * The per-frame window budget used to be reset by {@code updateTimings}, which is called on every fence wait - so a
     * frame with three flushes had its budget reset three times and "sixteen windows per frame" could not be demonstrated
     * at all. The identity is incremented at the camera boundary, which fires exactly once per frame, and the counters are
     * reconciled there rather than on a flush.
     */
    long frameId;

    /**
     * How often every mirror is marked stale, in frames. Zero means never.
     * <p>
     * Now zero. The periodic invalidation existed because some textures - the light map above all - were updated through
     * paths the upload hooks could not reach, so the mirror had to be re-read from OpenGL to stay current. Those paths
     * are announced now (the DynamicTexture hook and the int[] TextureLoader hook both hand the pixels over), so the
     * only thing the periodic refresh still did was force a GL readback of the 16x16 light map on the frames where it
     * had not changed: measured 2.30 ms/frame on Intel and 0.31 on AMD, all of it native blocking, because
     * glGetTexImage waits for the GPU rather than for the bytes.
     * <p>
     * Measured with it off: Intel p50 31.18 -> 28.66/28.89 ms, AMD 16.05 -> 16.12 ms, stale 0.00 ms/frame on both, and
     * the image is identical to the default (mean absolute difference 0.0000, 5-7 pixels above one step on both GPUs).
     * The 0.0169 seen between two runs of this setting is the path-dependent batch variation measured separately, not a
     * staleness error.
     */
    private static final int MIRROR_REFRESH_FRAMES =
            Integer.getInteger("celtium.vulkan.mirrorRefreshFrames", 0).intValue();

    private int mirrorRefreshCounter;

    private int mirrorRefreshes;

    /**
     * Whether this frame's submission actually reached the queue.
     * <p>
     * Set by {@code submitCommands} on a successful {@code vkQueueSubmit} and cleared when a frame opens, so reading it
     * before {@code endFrame} answers "did this frame hand the images over" - which is the question the audit's item E
     * needs answered. It is deliberately not {@code submissionPending}: that flag belongs to GPU-idle bookkeeping and is
     * cleared by the idle wait before {@code endFrame} runs, which is why using it there refused to end every healthy
     * frame.
     */
    boolean frameSubmitReachedQueue;

    /**
     * Groups whose draws are already recorded but whose command buffer may still be in flight.
     * <p>
     * Destroying the mesh buffers immediately after recording them is a use-after-free as far as the GPU is
     * concerned: the handoff submits later in the same pass, so the buffers must outlive it. Keeping one frame's
     * worth and releasing it at the next flush is enough, because a pass only starts after the previous one signalled.
     */
    final java.util.List<ImmediateGroup> retiredImmediateGroups = new java.util.ArrayList<ImmediateGroup>();


    /**
     * Whether the stall latch has closed this backend (item 416): once it has, no draw can be recorded, and
     * the native draw entry in the Forge layer reads this to skip a draw instead of failing the frame over a
     * refusal the latch already explains. The latch never clears, by design - the resources it protects may
     * still be owned by a command buffer that never completed.
     */
    public boolean celtium$isGpuStalled() {
        return this.gpuStalled;
    }


    /**
     * Flushes the frame's pending work at the frame boundary, which the pure-native path never reaches otherwise.
     * <p>
     * Measured 2026-10-15 (legs {@code gv-cohort-fix154} and {@code gv-cohort-fix155}): the client loads a world and
     * runs frames - {@code CELTIUM_SWAPPOINT calls=159900} at the game's own swap point - yet
     * {@code FrameCoordinator.submitCommands} is never entered (its bounded entry probe printed nothing) and
     * {@code setLastFrameReachedQueue(true)} is never called, so every present skips with "no completed frame
     * waiting" and nothing is ever shown.
     * <p>
     * The reason is the trigger: the pending work is flushed by {@code flushPendingBeforeGlDraw}, which fires at GL
     * draw and bind boundaries ({@code bindTexture} and friends) and from {@code applyDerivedProjection}. A GL-less
     * frame crosses none of those, so its immediate work stays pending forever and no submission is ever made. The
     * swap point is the boundary that <em>does</em> fire once per frame in both paths, so the flush is offered here
     * for that caller.
     */
    public void celtium$flushPendingAtFrameBoundary() {
        // M-6 wired (FL-1 F-5): the frame-boundary drain records into the single flush census.
        // The body's drain goes through flushPendingBeforeGlDraw directly (not the stamped
        // funnel), so this entry note is the drain's only census record - no double count.
        long flushStartedNanos = System.nanoTime();
        try {
            celtium$flushPendingAtFrameBoundaryBody(flushStartedNanos);
        } finally {
            long boundaryNanos = System.nanoTime() - flushStartedNanos;
            if (boundaryNanos > 50_000_000L) {
                System.out.println("[Celtium] CELTIUM_BOUNDARY_SLOW millis=" + (boundaryNanos / 1_000_000L)
                        + " frame=" + this.frameId);
            }
        }
    }

    private void celtium$flushPendingAtFrameBoundaryBody(long flushStartedNanos) {
        this.funnel.fog.tracePeriodically(this.frameId);
        try {
            // The boundary's own flush is the frame-end trigger for the window census.
            this.funnel.pendingWindowTrigger = FramePassTimings.WINDOW_TRIGGER_FRAME_END;
            this.funnel.flushPendingBeforeGlDraw();
            this.funnel.celtium$applyPendingRawWrites();
            // After the frame's own recording and inside the still-open frame buffer: N bind+draw calls on a real mesh,
            // which is the only way to price the command itself without the rest of the frame in the way.
            this.funnel.celtium$runDrawBench();
            this.funnel.celtium$debugMirrorRequest();
            // The last window's records just landed in the frame buffer; end and queue it before the batch flush
            // sends the frame's work to the driver in logical order.
            // The frame's wall clock split. A sampling profiler cannot see a thread that is waiting, and the gap to the
            // reference is larger than the CPU the samples account for, so the boundary reports the three parts it can
            // name: the recording (already bracketed), this submit, and the slot wait at the next frame's start.
            if (Lane2ImmediateRecorder.FRAME_SIGNATURE) {
                // Close off the frame's signature: a frame that hashes the same as the previous one repeated its batch
                // traffic exactly, which is the condition a cached recording would have to detect. Gated with the hash
                // itself: with the hash off both values stay zero, and counting "0 == 0" as a repeat would report every
                // frame as identical.
                this.comparedFrameSignatures++;
                if (this.frameBatchSignature == this.previousFrameBatchSignature && this.frameBatchSignature != 0L) {
                    this.identicalFrameSignatures++;
                }
                this.previousFrameBatchSignature = this.frameBatchSignature;
                this.frameBatchSignature = 0L;
            }
            this.funnel.celtium$executeNativePasses(NativePassRegistry.PassStage.BEFORE_PRESENT);
            long finishStart = System.nanoTime();
            this.owner.celtium$finishFrameBuffer();
            long finishNanos = System.nanoTime() - finishStart;
            this.boundarySubmitNanos += finishNanos;
            FrameBudgetTimings.addSubmit(finishNanos);
            this.boundaryFrames++;
            if (this.boundaryFrames % 600 == 0) {
                System.out.println("[Celtium] CELTIUM_BOUNDARY_SPLIT frames=" + this.boundaryFrames
                        + " submitMillisPerFrame=" + (this.boundarySubmitNanos / 1_000_000L / 600L)
                        + " slotWaitMillisPerFrame=" + (this.slotWaitNanos / 1_000_000L / 600L));
                this.boundarySubmitNanos = 0L;
                this.slotWaitNanos = 0L;
                this.boundaryFrames = 0;
            }
            // Batched frame submissions reach the queue here at the latest: the frame's recordings are queued in
            // order, and the boundary is the last ordering point before the present's own submit (which also
            // flushes). A no-op when nothing is batched.
            // S5-D1 (change B): while the frame target holds a live present borrow and the present is not
            // skipped, this flush is held instead - the batch stays pending and the direct present's own
            // flush submits the frame's entries and the final transition as one vkQueueSubmit. The two
            // calls are consecutive in the same mixin hook, so nothing observes the batch in between; a
            // present that is then gated off leaves the batch for the next natural flush point, and every
            // observer of a submission flushes first. Swapchain destruction flushes before it drains
            // (VulkanSwapchain.delete), so a held batch never outlives the images it names. The retired
            // cleanups below keep their order: their firing is sequence-gated, not flush-gated.
            boolean holdBatchForPresent = !SKIP_PRESENT
                    && this.funnel.frameTarget instanceof GllessFrameTarget gllessTarget
                    && gllessTarget.hasLivePresentBorrow();
            if (!holdBatchForPresent) {
                this.context.celtium$flushBatchedSubmits();
            }
            // Deferred pipeline/pool teardowns run here rather than in a mid-frame wait: this is the point where the
            // frame's recording has been ended and submitted, so nothing can still name the old pipeline, its pools
            // or its layouts. Running them from waitForIdle measured worse (leg uniE8: 117 VUIDs of VkPipeline
            // destroyed while a recording lived), which is why the drain moved to this boundary.
            if (!this.frameBufferOpen && !this.funnel.immediateWindowBusy && !this.commandState.recording()) {
                // The pending-retirement checkpoint's fallback seat (T3 design section 2.3): a frame
                // whose batch was empty never passes through the post-flush hook, so the ledger would
                // stall on menu screens. When the boundary flush did run, the hook already drained
                // and this is a no-op on an empty ledger; while S5-D1 holds the batch for the direct
                // present the pending batch is observable here and the checkpoint must not fire -
                // the stamp would name a submission the queue has not accepted (INV-1b).
                // The Lane 1 store's deferred frees are collected by the terrain frontend at its
                // own frame seat; nothing else has pending retirements at this boundary.
                this.context.celtium$runRetiredCleanups();
                // The slab retire list's per-frame drain sits at the same seat, under the same guard: its
                // only other exit hangs off waitForIdle, which no frame boundary calls, so a burst's worth
                // of retired slab buffers starved there while steady rendering ran (the drain inside the
                // context gates each entry on the submission sequence, because this boundary is not a
                // queue-idle point).
                this.context.celtium$drainRetiredSlabBuffers();
            }
        } catch (RuntimeException | LinkageError error) {
            // A failed flush must not stop the frame: the queued work keeps its owner and the next boundary retries.
            this.reports.record("frame-boundary flush failed: " + error);
        } finally {
            this.presentSwapchain.swapPointFlushNanos += System.nanoTime() - flushStartedNanos;
        }
    }


    /** Adds one frame fence wait's duration to the totals, so the frame's own serialisation is a number. */
    private void celtium$recordFrameWait(long startedNanos) {
        long waitedNanos = System.nanoTime() - startedNanos;
        long millis = waitedNanos / 1_000_000L;
        FrameBudgetTimings.addFenceWait(waitedNanos);
        this.submissionStatistics.addFrameFenceWaitMillis(millis);
        this.submissionStatistics.noteFrameFenceWaitCount();
        if (millis > this.submissionStatistics.frameFenceWaitWorstMillis()) {
            this.submissionStatistics.setFrameFenceWaitWorstMillis(millis);
        }
    }


    /**
     * Whether the window timestamp queries are being written this run.
     * <p>
     * Two instruments read the same measurement: the GPU pass report ({@code -Dceltium.gpuPassTimings=true}) and the
     * frame budget's {@code gpu} column ({@code -Dceltium.vulkan.frameBudget=true}). The write side and the query reset
     * must ask the same question - the reset range only covers those slots while this is true - because a window write
     * without its reset is VUID-vkCmdWriteTimestamp-00830, which the frame budget's first positive-control leg did
     * reproduce (twenty errors, and a validation verdict of dirty).
     */
    static boolean celtium$windowTimestampsActive() {
        return org.soutalab.celtium.vulkan.GpuPassTimingReport.enabled() || FrameBudgetTimings.enabled();
    }

    void recordFrameEndTimestamp() {
        if (this.timestampPool == 0L || !this.timestampsUsable
                || !org.soutalab.celtium.vulkan.GpuPassTimingReport.enabled()) {
            return;
        }
        // One write per frame: the batched path ends the buffer in the coordinator's finish, the direct path ends it in
        // submit(), and a frame that took both - which is what a validation leg reported as ten
        // VUID-vkCmdWriteTimestamp-00830 errors on the discrete card - would write the same query twice.
        if (this.frameEndTimestampWritten) {
            return;
        }
        this.frameEndTimestampWritten = true;
        gpuMark("end");
        vkCmdWriteTimestamp(this.commandBuffer, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, this.timestampPool,
                celtium$timestampPoolBase() + (FRAME_TIMESTAMP_SLOT * 2) + 1);
        this.frameGpuTimed = true;
    }


    /** The original timing update, called once per frame from the wrapper above. */
    private void updateTimingsBody() {
        if (this.timestampPool == 0 || !this.timestampsUsable) {
            return;
        }
        // The state a readback may consume is a confirmed submission's snapshot, never the live
        // accumulators: at this point in the frame those describe the newest recorded frame, whose
        // submission has usually not completed - which is why this method used to file zero for
        // every frame (see TerrainTimingReadback). Nothing confirmed yet, nothing to read; the
        // snapshot waits, and the terrain column keeps its filed zero for the frame rather than a
        // sum measured against a submission that has not run.
        TerrainTimingReadback.Snapshot snapshot =
                this.timingReadback.peekReadable(this.scheduler.completedSubmitSequence());
        if (snapshot == null) {
            return;
        }
        boolean consumed = false;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int queries = (snapshot.immediateTimed || snapshot.frameTimed)
                    ? TIMESTAMP_SLOTS : snapshot.passSlots * 2;
            LongBuffer data = stack.mallocLong(queries * 2);
            int result = vkGetQueryPoolResults(this.context.device(), this.timestampPool, snapshot.poolBase,
                    queries, data, Long.BYTES * 2, VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
            if (result != VK_SUCCESS && result != VK_NOT_READY) {
                // The snapshot stays queued, as the live state used to stay uncleared: the next
                // begin retries the read, unless a newer confirmation drops it as overwritten.
                FrameBudgetTimings.setTerrainTimingCensus(this.timingSnapshotsRead,
                        this.timingReadback.droppedOverwritten(), this.timingSlotsUnavailable);
                return;
            }
            consumed = true;
            long terrainGpuNanos = 0L;
            java.util.Map<String, Long> framePackNanos = new java.util.LinkedHashMap<>();
            java.util.Map<String, Long> frameNativeNanos = new java.util.LinkedHashMap<>();
            for (int slot = 0; slot < snapshot.passSlots; slot++) {
                long startAvailable = data.get((slot * 2 * 2) + 1);
                long endAvailable = data.get((slot * 2 * 2) + 3);
                if (startAvailable == 0 || endAvailable == 0) {
                    // Counted rather than silent: the submission was confirmed, so its writes should
                    // be in the pool. An unavailable slot here is the signature of a read that lost
                    // its generation, and the census line is where that loss shows.
                    this.timingSlotsUnavailable++;
                    // Keep whatever was measured for this pass last time instead of reporting "?? ms".
                    continue;
                }
                long start = data.get(slot * 2 * 2);
                long end = data.get((slot * 2 * 2) + 2);
                if (end >= start) {
                    long passNanos = (long) ((end - start) * this.timestampPeriod);
                    String packName = snapshot.packPassNames.get(Integer.valueOf(slot));
                    if (packName != null) {
                        // A pack stage's subpasses share one name: the stage's frame total is their sum,
                        // filed under the name once, beside the native passes' own entries.
                        framePackNanos.merge(packName, Long.valueOf(passNanos), Long::sum);
                    } else if (snapshot.passes.get(slot) != null) {
                        // A native stage can record several slots under one name in a frame
                        // (segmented recording revisits a layer): the name's frame total is
                        // their sum, filed into the persistent map once, like the pack branch.
                        frameNativeNanos.merge(snapshot.passes.get(slot), Long.valueOf(passNanos), Long::sum);
                    }
                    // The frame budget's terrain column is this frame's passes summed fresh from the readback:
                    // the maps above keep a pass's last measured value across frames, so summing them would file
                    // stale durations for passes whose readback has not landed yet.
                    terrainGpuNanos += passNanos;
                }
            }
            if (!framePackNanos.isEmpty()) {
                this.packPassTimings.putAll(framePackNanos);
            }
            if (!frameNativeNanos.isEmpty()) {
                this.passTimings.putAll(frameNativeNanos);
            }
            FrameBudgetTimings.setTerrainGpuPass(terrainGpuNanos);
            if (snapshot.frameTimed) {
                long startAvailable = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2 + 1);
                long endAvailable = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2 + 3);
                if (startAvailable != 0 && endAvailable != 0) {
                    long start = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2);
                    long end = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2 + 2);
                    if (end >= start) {
                        this.frameGpuNanos = (long) ((end - start) * this.timestampPeriod);
                    }
                }
            }
            if (this.frameGpuNanos > 0L && (!snapshot.passes.isEmpty() || !snapshot.packPassNames.isEmpty())) {
                // The first and last pass of this frame, by the slot they were recorded in: slot order is recording order.
                int firstSlot = Integer.MAX_VALUE;
                int lastSlot = -1;
                for (Integer slot : snapshot.passes.keySet()) {
                    firstSlot = Math.min(firstSlot, slot.intValue());
                    lastSlot = Math.max(lastSlot, slot.intValue());
                }
                for (Integer slot : snapshot.packPassNames.keySet()) {
                    firstSlot = Math.min(firstSlot, slot.intValue());
                    lastSlot = Math.max(lastSlot, slot.intValue());
                }
                if (lastSlot >= 0) {
                    long frameStart = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2);
                    long frameEnd = data.get(FRAME_TIMESTAMP_SLOT * 2 * 2 + 2);
                    long firstPassStart = data.get(firstSlot * 2 * 2);
                    long lastPassEnd = data.get(lastSlot * 2 * 2 + 2);
                    if (frameEnd >= frameStart && firstPassStart >= frameStart && frameEnd >= lastPassEnd) {
                        this.frameHeadNanos = (long) ((firstPassStart - frameStart) * this.timestampPeriod);
                        this.frameTailNanos = (long) ((frameEnd - lastPassEnd) * this.timestampPeriod);
                    }
                }
            }
            if (snapshot.immediateTimed) {
                // Every window of the frame, summed and split by target: main-target windows against the off-target
                // (mod and post-chain framebuffer) ones, plus the largest single window.
                long main = 0L;
                long off = 0L;
                long max = 0L;
                int counted = 0;
                long mainIdentity = this.funnel.targetRegistry.mainIdentity();
                for (int w = 0; w < snapshot.windowCount; w++) {
                    int slot = Lane2ImmediateRecorder.WINDOW_SLOT_BASE + w;
                    if (data.get(slot * 2 * 2 + 1) == 0 || data.get(slot * 2 * 2 + 3) == 0) {
                        continue;
                    }
                    long start = data.get(slot * 2 * 2);
                    long end = data.get(slot * 2 * 2 + 2);
                    if (end < start) {
                        continue;
                    }
                    long nanos = (long) ((end - start) * this.timestampPeriod);
                    long target = snapshot.windowTargets[w];
                    if (target == mainIdentity || target == 0L) {
                        main += nanos;
                    } else {
                        off += nanos;
                    }
                    max = Math.max(max, nanos);
                    counted++;
                }
                this.immediatePassGpuNanos = main + off;
                this.windowGpuMainNanos = main;
                FrameBudgetTimings.setGpuPass(main + off);
                this.windowGpuOffNanos = off;
                this.windowGpuMaxNanos = max;
                this.windowGpuCount = counted;
            }
        }
        if (consumed && snapshot.markCount > 1) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer marks = stack.mallocLong(snapshot.markCount * 2);
                int result = vkGetQueryPoolResults(this.context.device(), this.timestampPool,
                        snapshot.poolBase + MARK_BASE, snapshot.markCount, marks, Long.BYTES * 2,
                        VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
                if (result == VK_SUCCESS || result == VK_NOT_READY) {
                    // Summed per label: the delta before each mark is charged to that mark's label (the commands
                    // recorded since the previous mark), so "g2=310us/48" reads as 48 format-2 groups costing 310 us.
                    java.util.Map<String, long[]> byLabel = new java.util.LinkedHashMap<String, long[]>();
                    long previous = -1L;
                    for (int m = 0; m < snapshot.markCount; m++) {
                        if (marks.get(m * 2 + 1) == 0) {
                            continue;
                        }
                        long at = marks.get(m * 2);
                        if (previous >= 0L && at >= previous) {
                            long[] sum = byLabel.computeIfAbsent(snapshot.markLabels[m], k -> new long[2]);
                            sum[0] += (long) ((at - previous) * this.timestampPeriod);
                            sum[1]++;
                        }
                        previous = at;
                    }
                    StringBuilder timeline = new StringBuilder();
                    for (java.util.Map.Entry<String, long[]> entry : byLabel.entrySet()) {
                        timeline.append(' ').append(entry.getKey()).append('=')
                                .append(entry.getValue()[0] / 1000L).append("us/").append(entry.getValue()[1]);
                    }
                    this.gpuMarkTimeline = timeline.toString();
                }
            }
        }
        if (consumed) {
            this.timingReadback.consume(snapshot);
            this.timingSnapshotsRead++;
        }
        FrameBudgetTimings.setTerrainTimingCensus(this.timingSnapshotsRead,
                this.timingReadback.droppedOverwritten(), this.timingSlotsUnavailable);
        if (consumed) {
            // The pass report belongs to the frame its timings describe. The boundary call site
            // (celtium$finishFrameBuffer) keys on the live frame id and only runs while the frame
            // buffer is still open, which the swap-point and present paths mostly leave already
            // closed - so the line stayed silent even with the timings in hand (proof leg PTF1:
            // the terrain column filed real values, CELTIUM_PASS_GPU never printed). Printing here,
            // keyed on the snapshot's own frame id, pairs each report with the frame it measures.
            // The marks timeline is not the snapshot's own string, so it is left to the boundary site.
            GpuPassTimingReport.printIfDue(snapshot.frameId, this.passTimings, this.packPassTimings,
                    this.immediatePassGpuNanos,
                    this.frameGpuNanos, this.frameHeadNanos, this.frameTailNanos, this.windowGpuCount,
                    this.windowGpuMainNanos, this.windowGpuOffNanos, this.windowGpuMaxNanos, null);
        }
    }


    /**
     * Opens the statistics query that brackets one pass's section draw loop (item 359).
     *
     * @return the query's index within the frame slot, or -1 when the instrument is not collecting - the
     *         pool is absent or the report switch is off - or when the slot's queries are exhausted, which
     *         is counted rather than silent
     */
    private int beginTerrainStatistics(String pass) {
        if (this.statisticsPool == 0L || !TerrainStatisticsReport.enabled() || this.commandBuffer == null) {
            return -1;
        }
        int index = this.statisticsSlotCounts[this.frameSlot];
        if (index >= STATS_PER_SLOT) {
            this.statisticsDroppedPasses++;
            return -1;
        }
        vkCmdBeginQuery(this.commandBuffer, this.statisticsPool, (this.frameSlot * STATS_PER_SLOT) + index, 0);
        this.statisticsSlotPasses[this.frameSlot][index] = pass;
        this.statisticsChargeAccum = 0L;
        return index;
    }


    /** Adds one charged batch to the open query's unsorted charge - the V4 identity's CPU side. */
    private void noteTerrainStatisticsCharge(int query, long chargeVertices, boolean sortedPass) {
        if (query >= 0 && !sortedPass) {
            this.statisticsChargeAccum += chargeVertices;
        }
    }


    /**
     * Closes the pass's statistics query and snapshots the CPU-side counts the report reads beside the GPU
     * counters: the pass's logical draws and sections (the existing per-pass counters, reset at the head of
     * every pass) and its accumulated unsorted budget charge.
     */
    private void endTerrainStatistics(int query, String pass) {
        if (query < 0) {
            return;
        }
        vkCmdEndQuery(this.commandBuffer, this.statisticsPool, (this.frameSlot * STATS_PER_SLOT) + query);
        this.statisticsSlotDraws[this.frameSlot][query] = this.funnel.terrainStatistics.lastDrawCommands();
        this.statisticsSlotSections[this.frameSlot][query] = this.funnel.terrainStatistics.lastSectionsDrawn();
        this.statisticsSlotCharges[this.frameSlot][query] = pass != null && pass.contains("translucent") ? 0L : this.statisticsChargeAccum;
        this.statisticsSlotCounts[this.frameSlot] = query + 1;
    }


    /**
     * Reads back whatever statistics samples have landed, at the frame boundary. Non-blocking like the
     * timestamp read: a query whose availability long is still 0 stays unread for a later frame, and a slot
     * that is reset while some of its queries were never read counts them as dropped samples - the three
     * counters the summary prints exist so a silent instrument is never read as an empty scene.
     */
    private void readPipelineStatistics() {
        if (this.statisticsPool == 0L) {
            return;
        }
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            int count = this.statisticsSlotCounts[slot];
            if (count == 0) {
                continue;
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer data = stack.mallocLong(count * TerrainStatisticsReport.RESULT_LONGS_PER_QUERY);
                int result = vkGetQueryPoolResults(this.context.device(), this.statisticsPool,
                        slot * STATS_PER_SLOT, count, data, TerrainStatisticsReport.RESULT_STRIDE_BYTES,
                        VK_QUERY_RESULT_64_BIT | VK_QUERY_RESULT_WITH_AVAILABILITY_BIT);
                if (result != VK_SUCCESS && result != VK_NOT_READY) {
                    this.statisticsReadFailures++;
                    continue;
                }
                for (int q = 0; q < count; q++) {
                    if ((this.statisticsSlotConsumed[slot] & (1 << q)) != 0) {
                        continue;
                    }
                    long[] values = TerrainStatisticsReport.decode(data, q);
                    if (values == null) {
                        continue;
                    }
                    this.statisticsSlotConsumed[slot] |= (1 << q);
                    accumulateStatistics(slot, q, values);
                }
            }
        }
        if (this.frameId != this.statisticsLastPrintFrame && !this.statisticsWindowTotals.isEmpty()) {
            long frames = this.statisticsWindowStartFrame < 0L ? 0L : this.frameId - this.statisticsWindowStartFrame;
            if (TerrainStatisticsReport.printWindowIfDue(this.frameId, frames, this.statisticsWindowTotals)) {
                this.statisticsWindowTotals.clear();
                this.statisticsWindowStartFrame = -1L;
                this.statisticsLastPrintFrame = this.frameId;
            }
        }
    }


    /** Folds one landed sample into the window and cumulative totals, beside its record-time snapshots. */
    private void accumulateStatistics(int slot, int query, long[] values) {
        String pass = this.statisticsSlotPasses[slot][query];
        if (pass == null) {
            return;
        }
        long[] window = this.statisticsWindowTotals.computeIfAbsent(pass, k -> new long[TerrainStatisticsReport.VALUES]);
        long[] totals = this.funnel.statisticsTotals.computeIfAbsent(pass, k -> new long[TerrainStatisticsReport.VALUES]);
        for (int i = 0; i < TerrainStatisticsReport.COUNTERS; i++) {
            window[i] += values[i];
            totals[i] += values[i];
        }
        window[TerrainStatisticsReport.DRAWS] += this.statisticsSlotDraws[slot][query];
        totals[TerrainStatisticsReport.DRAWS] += this.statisticsSlotDraws[slot][query];
        window[TerrainStatisticsReport.SECTIONS] += this.statisticsSlotSections[slot][query];
        totals[TerrainStatisticsReport.SECTIONS] += this.statisticsSlotSections[slot][query];
        window[TerrainStatisticsReport.BUDGET_CHARGE] += this.statisticsSlotCharges[slot][query];
        totals[TerrainStatisticsReport.BUDGET_CHARGE] += this.statisticsSlotCharges[slot][query];
        this.statisticsSamples++;
        if (this.statisticsWindowStartFrame < 0L) {
            this.statisticsWindowStartFrame = this.frameId;
        }
    }



    /** The vertex-level diagnostics (layout, drawn sections, GPU readback): one switch, quiet by default. */
    private static final boolean VERTEX_DIAGNOSTICS =
            Boolean.getBoolean("celtium.vulkan.vertexDiagnostics");


    /**
     * The host seat for the fixed template's S3 stage (FrameCoordinator calls this at its
     * HIZ_BUILD mark, buffer still recording, no pass open): brackets the frontend's Hi-Z
     * pyramid build with the stage's G0 timestamp pair. A missing target or buffer leaves the
     * stage unrecorded, the same as a frame the finish path abandons.
     */
    void celtium$recordHiZBuild() {
        if (this.commandBuffer == null || this.funnel.frameTarget == null || this.gpuStalled
                || this.owner.lane1 == null) {
            return;
        }
        int slot = this.funnel.celtium$beginNamedPassTimestamp();
        try {
            this.owner.lane1.recordHiZBuild(this.commandBuffer, this.funnel.frameTarget, this.frameSlot);
        } finally {
            this.funnel.celtium$closeNamedPassTimestamp(slot, "S3_hiz");
        }
    }

    /** Hash of the current frame's batch traffic, and the previous frame's, to count identical frames. */
    long frameBatchSignature;

    private long previousFrameBatchSignature;

    long identicalFrameSignatures;

    long comparedFrameSignatures;







    // The shadow pass's draw tuple, captured in one place: the renderer supplies the geometry (vertex, index, indirect
    // range) from the instant its first batch of the frame was recorded, and this side supplies the pipeline, descriptor
    // set, dynamic offset and push block as they were for that same batch. Reading them separately mixed region A's
    // commands with region B's buffers and state (#397).
    /** Every terrain batch captured this frame, in recording order; the map is drawn from all of them. */


    private long boundarySubmitNanos;

    private long slotWaitNanos;

    private int boundaryFrames;

    /**
     * Forces the slot wait to report a timeout, so the post-submit failure path can be exercised (lab switch).
     * The wait it falsifies is the timeline await on a timeline device and the fence wait on the fallback path
     * (R-56: the exercise moved with the wait).
     */
    private static final boolean FENCE_WAIT_FAULT = Boolean.getBoolean("celtium.vulkan.failFenceWait");

    /**
     * Makes the slot wait report {@code VK_ERROR_DEVICE_LOST} instead of its real result - the case of a submission
     * that was queued and then lost with the device, which the pre-submit fault cannot produce. Lab switch. The
     * wait it falsifies is the timeline await on a timeline device and the fence wait on the fallback path (R-56).
     */
    private static final boolean DEVICE_LOST_FAULT = Boolean.getBoolean("celtium.vulkan.failDeviceLost");


    // ------------------------------------------------------------------ command resources

    /** The frame slots, owned by the scheduler (refactoring plan R6). */
    static final int FRAME_SLOTS = FrameSubmissionScheduler.FRAME_SLOTS;

    private final VkCommandBuffer[] slotCommandBuffers = new VkCommandBuffer[FRAME_SLOTS];

    private final long[] slotFences = new long[FRAME_SLOTS];

    /**
     * When a frame recording's submission goes into the batched deferred queue instead of vkQueueSubmit, the token
     * naming the batch stands in for the slot's own fence (vkQueueSubmit arms one fence per call - it cannot arm each
     * entry's; and on a timeline device the batch carries no fence at all, R-56). 0 means the slot's last submission
     * armed {@link #slotFences} the ordinary way. Cleared at {@link #celtium$noteSlotConfirmed}: the reclaim runs
     * that confirmation, so the map's lifetime is "queued until confirmed", never reset independently.
     */
    private final long[] slotDeferredTokens = new long[FRAME_SLOTS];

    /**
     * The unified frame buffer's open state: when the target carries no frame semaphores, the first
     * {@link #beginCommands()} of a frame begins the slot's buffer and every later recording of the same frame
     * only re-points the aliases below - one vkReset+vkBegin, one end, one submission per frame instead of one
     * per recording. Frame recordings are always coalesced this way (the measured A/B on AMD RX 6400: flush
     * 3.82 -> 2.35-2.41 ms per frame at 4.55 flushes/frame, queue submissions per frame from 12.7 to 4.6).
     * Cleared at the frame's boundary submit and at every mid-frame reset path.
     */
    boolean frameBufferOpen;

    /**
     * Whether the window being recorded moved the frame image into its attachment layout.
     * <p>
     * The acquire is recorded before the pass and the release after it, but only the pass knows which framebuffer it
     * ended up attaching: an off-target window skips both barriers, and the off-target path's own fallback (a refused
     * pair) attaches the frame image instead and records the acquire there. This field is how the release learns what
     * the acquire actually did, so the two can never disagree about the image's layout.
     */
    boolean windowFrameImageAttached = true;

    /**
     * The recording-lifecycle guard per slot. {@link #commandState} aliases the current slot's entry, so every site
     * that guarded "the backend command buffer" now guards "the command buffer this recording owns" - the same
     * statement now that several submissions may be in flight at once.
     */
    private final VulkanCommandBufferState[] slotCommandStates = new VulkanCommandBufferState[FRAME_SLOTS];

    /** Slot this recording belongs to, and the slot the next recording will take. */
    int frameSlot;

    private int nextFrameSlot;

    /** Waits spent on a slot whose previous submission had not completed when it was reused. */

    private void createCommandResources() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                    .queueFamilyIndex(this.context.queueFamilyIndex());
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateCommandPool(this.context.device(), poolInfo, null, handle), "vkCreateCommandPool");
            this.commandPool = handle.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(this.commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(FRAME_SLOTS);
            org.lwjgl.PointerBuffer buffers = stack.mallocPointer(FRAME_SLOTS);
            check(vkAllocateCommandBuffers(this.context.device(), allocate, buffers), "vkAllocateCommandBuffers");
            for (int slot = 0; slot < FRAME_SLOTS; slot++) {
                // One buffer, one fence and one recording-lifecycle guard per frame slot: a slot is only reset or
                // re-recorded once its own submission has completed (see beginCommands), which is what lets several
                // frames be in flight without either one touching a buffer the other is reading.
                this.slotCommandBuffers[slot] = new VkCommandBuffer(buffers.get(slot), this.context.device());
                VkFenceCreateInfo slotFenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default();
                check(vkCreateFence(this.context.device(), slotFenceInfo, null, handle), "vkCreateFence(slot " + slot + ")");
                this.slotFences[slot] = handle.get(0);
                this.slotCommandStates[slot] = new VulkanCommandBufferState(buffers.get(slot));
            }
            this.frameSlot = 0;
            this.nextFrameSlot = 0;
            this.commandBuffer = this.slotCommandBuffers[0];
            this.fence = this.slotFences[0];

            this.commandState = this.slotCommandStates[0];

            org.lwjgl.vulkan.VkPhysicalDeviceProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.context.physicalDevice(), properties);
            // timestampValidBits belongs to the queue family, not to the device limits, so it is read for the
            // family this backend submits on.
            IntBuffer familyCount = stack.mallocInt(1);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, null);
            org.lwjgl.vulkan.VkQueueFamilyProperties.Buffer families =
                    org.lwjgl.vulkan.VkQueueFamilyProperties.calloc((int) familyCount.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(
                    this.context.physicalDevice(), familyCount, families);
            int family = Math.min(this.context.queueFamilyIndex(), families.capacity() - 1);
            long timestampValidBits = Integer.toUnsignedLong(families.get(Math.max(0, family)).timestampValidBits());
            this.timestampPeriod = properties.limits().timestampPeriod();
            // Writing timestamps on a queue that cannot record them, or with a zero period, produces meaningless
            // numbers; the overlay then keeps its "?? ms" instead of reporting fiction.
            this.timestampsUsable = timestampValidBits > 0L && this.timestampPeriod > 0.0f;
            // One query range of TIMESTAMP_SLOTS per frame slot: a frame's results then live in its own
            // slot's range until that slot records again, which is what lets the readback consume
            // snapshots in submit order instead of losing every confirmed snapshot but the newest
            // (see TerrainTimingReadback).
            org.lwjgl.vulkan.VkQueryPoolCreateInfo queryPool = org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack)
                    .sType$Default().queryType(VK_QUERY_TYPE_TIMESTAMP).queryCount(TIMESTAMP_SLOTS * FRAME_SLOTS);
            if (this.timestampsUsable && !FORCE_NO_TIMESTAMPS) {
                check(vkCreateQueryPool(this.context.device(), queryPool, null, handle), "vkCreateQueryPool");
                this.timestampPool = handle.get(0);
            } else {
                // Pass timing is optional; the backend is not. Registering only when a queue can record timestamps
                // meant a device without them lost the backend entirely - the audit's item H, and a direct contradiction
                // of choosing features by capability rather than by assumption.
                this.timestampsUsable = false;
                this.reports.record("the queue cannot record timestamps, so pass timing is disabled; the backend registers anyway");
            }

            // Item 359: the pipeline-statistics pool, judged independently of the timestamps - a queue can
            // lack one and offer the other. Statistics are an instrument, never a precondition: a failed
            // creation is recorded and the backend registers anyway, and the summary says supported=false.
            if (this.context.pipelineStatisticsQuerySupported() && !TerrainStatisticsReport.forceDisabled()) {
                org.lwjgl.vulkan.VkQueryPoolCreateInfo statisticsPoolInfo =
                        org.lwjgl.vulkan.VkQueryPoolCreateInfo.calloc(stack).sType$Default()
                                .queryType(VK_QUERY_TYPE_PIPELINE_STATISTICS)
                                .queryCount(FRAME_SLOTS * STATS_PER_SLOT)
                                .pipelineStatistics(TerrainStatisticsReport.STATISTICS_MASK);
                LongBuffer statisticsHandle = stack.mallocLong(1);
                if (vkCreateQueryPool(this.context.device(), statisticsPoolInfo, null, statisticsHandle) == VK_SUCCESS) {
                    this.statisticsPool = statisticsHandle.get(0);
                } else {
                    this.reports.record("the pipeline statistics query pool could not be created, so terrain statistics are disabled; the backend registers anyway");
                }
            }

            if (Lane2ImmediateRecorder.REFERENCE_RENDER) {
                // A reference render: the mod loads, the bootstrap completes and the fixture can query the device, but the
                // backend never registers - so no pass, no capture and no mixin takes anything, and OpenGL draws the whole
                // frame. Returning here instead of skipping only the registration also skipped the command registration
                // and made the fixture fail with "Vulkan bootstrap was expected but the command is missing".
                this.reports.record("reference render: the Vulkan backend is not installed, so the frame is OpenGL");
            } else {
                org.soutalab.celtium.vulkan.CeltiumBackends.install(this);
            }

            if (SWAPCHAIN_PROBE) {
                this.owner.runSwapchainProbe();
            }
        }
    }

    private void destroyCommandResources() {
        if (this.timestampPool != 0) {
            vkDestroyQueryPool(this.context.device(), this.timestampPool, null);
            this.timestampPool = 0;
        }
        if (this.statisticsPool != 0) {
            vkDestroyQueryPool(this.context.device(), this.statisticsPool, null);
            this.statisticsPool = 0;
        }
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            if (this.slotFences[slot] != 0) {
                vkDestroyFence(this.context.device(), this.slotFences[slot], null);
                this.slotFences[slot] = 0;
                this.scheduler.releaseOnSlotReset(slot);
            }
            this.slotCommandBuffers[slot] = null;
            this.slotCommandStates[slot] = null;
        }
        // this.fence is an alias of one of the slot fences destroyed above, not a fourth handle: clearing it here is
        // what the separate destroy used to do by accident - vkDestroyFence on the same handle twice is invalid.
        this.fence = 0;
        if (this.commandPool != 0) {
            vkDestroyCommandPool(this.context.device(), this.commandPool, null);
            this.commandPool = 0;
            this.commandBuffer = null;
        }
    }






    /**
     * Waits for the last submission of this backend to finish, and refuses to wait forever.
     * <p>
     * The fence exists from creation but is signalled only by a submission the driver accepted, so waiting on it when
     * nothing was submitted is a wait that can never end - which is exactly what a failed {@code vkQueueSubmit} leaves
     * behind: the previous code cleared the pending flag *before* waiting and passed {@code Long.MAX_VALUE}, so a
     * submission that never reached the queue, or a device that stopped making progress, hung the client instead of
     * ending the run. The wait is bounded now, the flag is cleared only once the wait has resolved, and the queue is
     * only drained after the fence actually signalled - draining a queue whose work never completes would hang in the
     * driver instead.
     */
    /**
     * Ends all work the device is doing, so resources can be released without the GPU still reading them.
     * <p>
     * <b>This wait cannot be bounded by the API.</b> {@code vkDeviceWaitIdle} takes no timeout, and it is the honest
     * call here because submissions from several command buffers and from the bridge's handoffs are outstanding at
     * once. The comment used to claim the frame waits' timeout applied to it, which was not true of the call it
     * describes - and a doc that promises a bound the code does not have is worse than no bound, because it stops
     * anyone adding one.
     * <p>
     * What is done instead: a watchdog names the stall. If the call has not returned after the same timeout the frame
     * waits use, one bounded line is recorded and {@code gpuStalled} is set, so a device that stopped making progress
     * leaves evidence and every later path that must not reset, re-record or free in-use resources already refuses on
     * that flag. The call itself still returns when the driver returns; nothing here can interrupt it.
     * <p>
     * Used for resource release/replacement, including a pair framebuffer's render-pass change.
     * Cache hits and initial pair-framebuffer creation do not call this wait.
     */
    /**
     * Whether a {@code vkDeviceWaitIdle} result is the device-loss report {@code settleGpu} absorbs as a
     * false return instead of an exception (item 416). Only {@code VK_ERROR_DEVICE_LOST} qualifies: it is the
     * one result that says the device is gone for good, so no caller's release may proceed and no reload is
     * served by dying on the detection.
     */
    static boolean celtium$settleReportsDeviceLoss(int result) {
        return result == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
    }

    boolean settleGpu(String reason) {
        celtium$watchDeviceWait(reason);
        if (this.gpuStalled) {
            // A stalled GPU means the last command buffer may never have been begun and never submitted, so its recorded
            // references are still live. Destroying the frame target or its framebuffers here is what validation reported as
            // "in use by VkCommandBuffer": on this path the resources are left for process exit instead.
            // It reports rather than throws: the exception reached Minecraft's render loop and killed the client on the
            // crash screen, which the idle-failure injection measured as exit 255 with no report at all.
            this.submissionStatistics.noteStalledTeardownSkips();
            this.reports.record("not releasing resources after a stall: " + reason);
            return false;
        }
        // Close the submission stream before the wait (T3 review M4): a frame
        // buffer still open or a batch still queued has not been submitted, so
        // waiting on the device alone would confirm nothing about it - and a
        // teardown gate composed of "no recording, nothing queued, everything
        // confirmed" could pass over work that was never sent. Finishing and
        // flushing here makes the wait below cover everything recorded so
        // far, which is what the gate's completion half then reads.
        if (this.context.celtium$recordingOpen()) {
            this.owner.celtium$finishFrameBuffer();
        }
        this.context.celtium$flushBatchedSubmits();
        long start = System.nanoTime();
        int result = org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(this.context.device());
        synchronized (this.deviceWaitLock) {
            // This call's wait returned: answer its own token so a late watchdog for it cannot call it a stall.
            this.deviceWaitFinishedToken = this.submissionStatistics.deviceWaitToken();
        }
        if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS) {
            this.noteStalled();
            if (celtium$settleReportsDeviceLoss(result)) {
                // The device is already gone (issue #425, item 416): the wait did not lose it, it detected the
                // loss. Every caller of this wait already treats a false return as "release nothing" - delete()
                // stops, eviction keeps its regions, a recreation keeps the old target, the loading screen keeps
                // its resources - which is the only safe answer when nothing the GPU holds can be waited on.
                // Throwing instead turned a renderer reload whose teardown merely detected the loss into the
                // crash that reported it (the reporter's first report, on a scheduled task thread). Other
                // failures keep the throw: they are not a known-gone device, and absorbing them silently would
                // let a caller free resources under a GPU whose state nobody has established.
                this.reports.record("settleGpu(" + reason + "): vkDeviceWaitIdle returned VK_ERROR_DEVICE_LOST; the device is"
                        + " already lost, so nothing is released and the teardown stops here");
                return false;
            }
            throw new IllegalStateException("settleGpu(" + reason + "): vkDeviceWaitIdle returned " + result);
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (millis > 200L) {
            this.reports.record("settleGpu(" + reason + ") waited " + millis + "ms for the device");
        }
        // A device-wide idle is a confirmation of every submission, whichever fence it carried: mark the slots so
        // the deferred bookkeeping (arena epochs, retired groups, the retire queue) can run before the callers free
        // the resources it covers.
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            if (this.scheduler.isPending(slot)) {
                celtium$noteSlotConfirmed(slot, false);
            }
        }
        return true;
    }


    /**
     * Starts the watchdog that names a {@code vkDeviceWaitIdle} which never returns.
     * <p>
     * A daemon thread rather than an interrupt: the call cannot be interrupted, so the only useful thing a watchdog can
     * do is leave evidence and set the flag the failure paths already key on. It is created per call and exits after
     * firing once, so a normal run costs one short-lived thread per teardown.
     */
    private void celtium$watchDeviceWait(String reason) {
        // Per-call token, not a shared boolean (audit A05). With one flag, a watchdog from an earlier wait that fires
        // late reads the flag a NEWER wait has already cleared and declares a stall for a wait that has only just
        // started - a false stall, and a false stall is expensive here because gpuStalled makes every later path
        // refuse to release resources. Each call takes the next token and only the watchdog holding the current one
        // may report; the wait's completion sets the token to "finished", so a late watchdog finds its own call
        // already answered.
        this.submissionStatistics.noteDeviceWaitToken();
        long token = this.submissionStatistics.deviceWaitToken();
        Thread watchdog = new Thread(() -> {
            try {
                Thread.sleep(IDLE_TIMEOUT_NANOS / 1_000_000L);
            } catch (InterruptedException interrupted) {
                return;
            }
            synchronized (this.deviceWaitLock) {
                if (this.submissionStatistics.deviceWaitToken() != token || this.deviceWaitFinishedToken == token) {
                    // Either a later wait has taken over (this watchdog is stale) or this call's wait already
                    // returned. Neither is a stall, and saying so is the whole point of the token.
                    return;
                }
                this.deviceWaitFinishedToken = token;
                this.gpuStalled = true;
                this.reports.record("settleGpu(" + reason + "): vkDeviceWaitIdle has not returned after "
                        + (IDLE_TIMEOUT_NANOS / 1_000_000_000L) + "s (wait token " + token
                        + "); the device has stopped making progress. No recorded work is reset, re-recorded or"
                        + " freed from here on.");
            }
        }, "celtium-device-wait-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }


    /**
     * Waits for one frame slot's submission to complete.
     * <p>
     * OPT-08's retirement rule in one place: this slot's own fence decides whether the slot may be reused, and because
     * a Vulkan queue completes in submission order, a signalled fence also confirms every earlier submission - which is
     * what the sequence watermark records for the resource lists below.
     */
    boolean waitForSlot(int slot) {
        if (slot < 0 || slot >= FRAME_SLOTS || slotFences[slot] == 0 || !this.scheduler.isPending(slot)) {
            if (slot >= 0 && slot < FRAME_SLOTS) {
                // Nothing of this slot's is in flight. If it ever carried a submission, that submission's
                // confirmation already recycled its linear ring at the confirmation itself
                // (celtium$noteSlotConfirmed), so there is nothing left to reset here.
                celtium$drainBindlessReleases();
            }
            return true;
        }
        // A wait decision is actually being made for a pending submission: count the attempt here so
        // the early return above (nothing in flight, no wait at all) stays invisible, and give the
        // attempt its verdict below - fast when the timeline branch confirms the slot, fallback when
        // the fence wait is what confirms it. A timeline stall ends the wait without a verdict (the
        // fence never runs), so fast + fallback can honestly read below attempts.
        TIMELINE_WAIT_PATH.attempt();
        long deferredToken = this.slotDeferredTokens[slot];
        if (deferredToken != 0) {
            // The slot's submission is still queued in the batched list: the batch is submitted only when that
            // list flushes, so the flush has to happen before the wait or the wait blocks on a batch nothing
            // submitted yet.
            this.context.celtium$flushBatchedSubmits();
            // Stage 2 + R-56: the batch this slot rode in signalled the frame timeline as it submitted, and on
            // a timeline device that counter value is the batch's only confirmation - the batch carries no
            // fence. The wait is a single counter wait on the value the batch was handed - the frame's one
            // permitted blocking wait. Its timeout disposition is the fence wait's, re-expressed on the same
            // await rather than by falling through to a second mechanism: the retries re-await the value, and
            // the stall latch and frame abandon after them are the established answer to a slot that will not
            // confirm. The fault-injection switches exercise this path now: they used to skip it deliberately
            // so the fence wait's failure shapes ran, and with no batch fence left those shapes live here -
            // the injected result is assigned after the real await, exactly as the fence path assigns it
            // after its real wait.
            long timelineValue = this.context.celtium$timelineValueForDeferredToken(deferredToken);
            org.soutalab.celtium.vulkan.hal.FrameTimeline timeline = this.context.frameTimeline();
            if (timeline != null && timelineValue > 0L) {
                long started = System.nanoTime();
                int result = timeline.awaitValueResult(timelineValue, IDLE_TIMEOUT_NANOS);
                this.celtium$recordFrameWait(started);
                if (FENCE_WAIT_FAULT) {
                    // A wait that times out after the submission was accepted: the command buffer may still
                    // be in flight, so nothing it recorded may be reset, re-recorded or destroyed. This is
                    // the second half of the rule the submit fault covers, exercised on the slot wait that
                    // replaced the old queue-wide drain.
                    result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;
                    System.out.println("[Celtium] CELTIUM_FENCE_TIMEOUT injected");
                }
                if (DEVICE_LOST_FAULT && result == org.lwjgl.vulkan.VK10.VK_SUCCESS
                        && this.injectedDeviceLosses == 0) {
                    // One lost device, once: reporting the loss after a real submission is the post-submit
                    // case the pre-submit vkQueueSubmit fault cannot produce - the queue took the command
                    // buffer and the answer is the device is gone.
                    this.injectedDeviceLosses++;
                    result = org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
                    System.out.println("[Celtium] CELTIUM_DEVICE_LOST injected");
                }
                if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                    // A timed-out wait is not by itself a dead device: a swapchain rebuild, a resize or a
                    // driver that is busy with a long submission all produce one, and latching on the first
                    // timeout stops the renderer for the rest of the session. The wait is retried a bounded
                    // number of times (per wait, not per session) before the latch, and both are counted -
                    // the fence wait's disposition, restated on this await (R-56).
                    this.submissionStatistics.noteIdleWaitTimeouts();
                    int retries = 0;
                    while (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT && retries < IDLE_WAIT_RETRIES) {
                        retries++;
                        this.submissionStatistics.noteIdleWaitRetries();
                        this.reports.record("waitForSlot(" + slot + "): the timeline did not reach " + timelineValue
                                + " within " + (IDLE_TIMEOUT_NANOS / 1_000_000_000L) + "s; retry " + retries
                                + " of " + IDLE_WAIT_RETRIES + " before treating the device as stalled"
                                + " (timeouts=" + this.submissionStatistics.idleWaitTimeouts() + ")");
                        result = timeline.awaitValueResult(timelineValue, IDLE_TIMEOUT_NANOS);
                        if (FENCE_WAIT_FAULT) {
                            // The fault re-applies inside the retry loop (R-56 injection redesign): on a
                            // healthy device the real re-await answers SUCCESS at once, so a fault assigned
                            // only before the loop was always overwritten by the first retry and the stall
                            // seat behind the loop could never be exercised. Re-assigning after each
                            // re-await makes every retry read as the same timeout, so the wait runs its
                            // full bounded course (the initial wait plus IDLE_WAIT_RETRIES) and reaches
                            // noteStalled() below.
                            result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;
                            System.out.println("[Celtium] CELTIUM_FENCE_TIMEOUT injected");
                        }
                    }
                }
                if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                    this.submissionStatistics.noteIdleWaitTimeouts();
                    this.noteStalled();
                    // The abandon-path flag clear, as on the fence path below (bughunt LANE-05 #3).
                    this.frameBufferOpen = false;
                    this.funnel.frameTarget.abandonFrame("frame slot timeline timed out");
                    // The stalled frame's pack seat closes with it (D7 seat ruling:
                    // endNativeFrame on every exit path, GPU stall included).
                    org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.endNativeFrame();
                    return false;
                }
                if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS
                        && result != org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                    this.reports.record("waitForSlot(" + slot + "): vkWaitSemaphores returned " + result);
                    this.noteStalled();
                    this.frameBufferOpen = false;
                    this.funnel.frameTarget.abandonFrame("frame slot timeline wait failed: " + result);
                    return false;
                }
                this.scheduler.noteSlotWait(true);
                // The batch is shared: the slot's confirm (and the flag's clear) runs inside the reclaim's
                // callback, once, rather than here - polling also frees every earlier entry the timeline
                // value covered. A device the wait reported lost surfaces from the poll instead: the poll's
                // counter read is where the loss is detected on this path.
                this.context.celtium$pollDeferredSubmits();
                celtium$drainBindlessReleases();
                // The timeline wait confirmed this slot - a success the bounded retries reached is
                // still the timeline's confirmation, so the final result decides the verdict.
                TIMELINE_WAIT_PATH.fast();
                return true;
            }
        }
        // The timeline branch did not confirm this slot (no timeline, no value, or the slot never
        // rode a batch): the fence wait below is the attempt's verdict.
        TIMELINE_WAIT_PATH.fallback();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer fences = stack.mallocLong(1);
            // A batched slot on a device without a timeline waits on its batch's fence, resolved from the
            // token it holds; a slot that submitted directly waits on its own fence.
            fences.put(0, deferredToken != 0
                    ? this.context.celtium$fenceForDeferredToken(deferredToken) : this.slotFences[slot]);
            long started = System.nanoTime();
            int result = vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS);
            this.celtium$recordFrameWait(started);
            if (FENCE_WAIT_FAULT) {
                // A wait that times out after the submission was accepted: the command buffer may still be in flight, so
                // nothing it recorded may be reset, re-recorded or destroyed. This is the second half of the rule the
                // submit fault covers, exercised on the slot wait that replaced the old queue-wide drain.
                result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;
                System.out.println("[Celtium] CELTIUM_FENCE_TIMEOUT injected");
            }
            if (DEVICE_LOST_FAULT && result == org.lwjgl.vulkan.VK10.VK_SUCCESS && this.injectedDeviceLosses == 0) {
                // One lost device, once: reporting the loss after a real submission is the post-submit case the
                // pre-submit vkQueueSubmit fault cannot produce - the queue took the command buffer and the answer
                // is the device is gone.
                this.injectedDeviceLosses++;
                result = org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
                System.out.println("[Celtium] CELTIUM_DEVICE_LOST injected");
            }
            if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                // A timed-out fence wait is not by itself a dead device: a swapchain rebuild, a resize or a driver that
                // is busy with a long submission all produce one, and latching on the first timeout stops the renderer for
                // the rest of the session - measured on the world-resize leg, where `stalledStops=1` after the resize
                // left `presentedImageFramesAtCapture=0` and no picture. The wait is retried a bounded number of times
                // (per wait, not per session) before the latch, and both are counted.
                this.submissionStatistics.noteIdleWaitTimeouts();
                int retries = 0;
                while (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT && retries < IDLE_WAIT_RETRIES) {
                    retries++;
                    this.submissionStatistics.noteIdleWaitRetries();
                    this.reports.record("waitForSlot(" + slot + "): the fence did not signal within "
                            + (IDLE_TIMEOUT_NANOS / 1_000_000_000L) + "s; retry " + retries + " of "
                            + IDLE_WAIT_RETRIES + " before treating the device as stalled (timeouts="
                            + this.submissionStatistics.idleWaitTimeouts() + ")");
                    result = vkWaitForFences(this.context.device(), fences, true, IDLE_TIMEOUT_NANOS);
                    if (FENCE_WAIT_FAULT) {
                        // The fault re-applies inside the retry loop, as on the timeline branch above
                        // (R-56 injection redesign): without the re-assignment the first retry's real
                        // wait overwrote the one pre-loop forgery with its true SUCCESS and the stall
                        // seat behind the loop stayed unreachable for this injection.
                        result = org.lwjgl.vulkan.VK10.VK_TIMEOUT;
                        System.out.println("[Celtium] CELTIUM_FENCE_TIMEOUT injected");
                    }
                }
            }
            if (result == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                this.submissionStatistics.noteIdleWaitTimeouts();
                this.noteStalled();
                // An abandoned frame is not "the frame is still recording": the flag must not route the
                // next beginCommands into the buffer whose recording the abandon just discards. Cleared on the
                // abandon paths only - this clear used to run unconditionally before both checks, so a wait that
                // merely succeeded while a frame was open (the immediate-window waitForIdle path) cleared the
                // flag behind its owner's back and the next beginCommands took the new-begin path into a
                // "nested begin" failure (bughunt LANE-05 #3).
                this.frameBufferOpen = false;
                this.funnel.frameTarget.abandonFrame("frame slot fence timed out");
                // The stalled frame's pack seat closes with it (D7 seat ruling:
                // endNativeFrame on every exit path, GPU stall included).
                org.soutalab.celtium.vulkan.pack.ShaderPackRuntime.endNativeFrame();
                return false;
            }
            if (result != org.lwjgl.vulkan.VK10.VK_SUCCESS
                    && result != org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                this.reports.record("waitForSlot(" + slot + "): vkWaitForFences returned " + result);
                this.noteStalled();
                // The abandon-path flag clear, as in the timeout branch above (bughunt LANE-05 #3).
                this.frameBufferOpen = false;
                this.funnel.frameTarget.abandonFrame("frame slot fence failed: " + result);
                return false;
            }
            this.scheduler.noteSlotWait(true);
            if (deferredToken != 0) {
                // The batch fence is shared: the slot's confirm (and the flag's clear) runs inside the reclaim's
                // callback, once, rather than here - polling also frees every earlier entry the fence covered.
                this.context.celtium$pollDeferredSubmits();
            } else {
                celtium$noteSlotConfirmed(slot, result == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST);
            }
            // The confirmation above - direct, or inside the reclaim callback the poll just ran - has
            // already recycled the slot's linear ring at the confirmation tail.
            celtium$drainBindlessReleases();
            return true;
        }
    }


    /**
     * Advances the bindless binding's deferred releases against the merged completion currency
     * (Stage 5 stage 3, section 3.5). Called only from {@link #waitForSlot(int)}'s success exits -
     * the frame loop's one completion gate -
     * so a retired texture's array slot is freed only once the counter says every submission that
     * could still sample it has completed. The peek form is deliberate: a frame that never created
     * the binding has nothing to drain.
     */
    private void celtium$drainBindlessReleases() {
        org.soutalab.celtium.vulkan.bindless.BindlessTextureBinding binding =
                this.context.celtium$bindlessBindingOrNull();
        if (binding != null) {
            binding.advance(this.context.celtium$slabCompletedValue());
            // Slot dump instrument tick (no-op unless -Dceltium.debug.bindlessSlotDump is set).
            this.owner.celtium$registry().celtium$maybeDumpBindlessSlots();
        }
    }


    /**
     * Confirms every pending slot whose submission is already confirmed - by its fence's signal, or, for a
     * batched slot on a timeline device, by the counter reaching its batch's value - without blocking.
     * <p>
     * This is the shape the per-submission drains were replaced by: a submission's completion is observed where a
     * later step can use it - at the next begin, the next window, the next poll after a flush - rather than being
     * paid for on the spot by the render thread. Returns how many slots were confirmed, which callers use for the
     * "nothing is in flight anymore" statement the retire queue needs.
     */
    int celtium$pollCompletedSlots() {
        this.scheduler.notePoll(false);
        int confirmed = 0;
        for (int slot = 0; slot < FRAME_SLOTS; slot++) {
            if (!this.scheduler.isPending(slot) || this.slotFences[slot] == 0) {
                continue;
            }
            long deferredToken = this.slotDeferredTokens[slot];
            if (deferredToken != 0) {
                // Batched slot: its confirm lives in the reclaim callback, so the poll only triggers the
                // context poll - a confirmed batch reclaims the entry, which runs the slot confirmation
                // exactly once. On a timeline device the confirmation is the counter reaching the batch's
                // value (R-56: the batch has no fence to query; a lost device surfaces from the counter
                // read itself, as the check inside completedValue). On the fallback path the batch's fence,
                // resolved from the token, is queried as before.
                org.soutalab.celtium.vulkan.hal.FrameTimeline timeline = this.context.frameTimeline();
                if (timeline != null) {
                    long timelineValue = this.context.celtium$timelineValueForDeferredToken(deferredToken);
                    if (timelineValue > 0L && timeline.isComplete(timelineValue)) {
                        this.context.celtium$pollDeferredSubmits();
                        confirmed++;
                        this.scheduler.notePollConfirmation();
                    }
                    continue;
                }
                int deferredStatus = vkGetFenceStatus(this.context.device(),
                        this.context.celtium$fenceForDeferredToken(deferredToken));
                if (deferredStatus == org.lwjgl.vulkan.VK10.VK_SUCCESS
                        || deferredStatus == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                    this.context.celtium$pollDeferredSubmits();
                    confirmed++;
                    this.scheduler.notePollConfirmation();
                }
                continue;
            }
            int status = vkGetFenceStatus(this.context.device(), this.slotFences[slot]);
            if (status == org.lwjgl.vulkan.VK10.VK_SUCCESS) {
                confirmed++;
                this.scheduler.notePollConfirmation();
                celtium$noteSlotConfirmed(slot, false);
            } else if (status == org.lwjgl.vulkan.VK10.VK_NOT_READY) {
                // Still in flight: the next user of this slot pays the bounded wait, not this thread.
            } else if (status == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                // Completion-equivalent for pending purposes: nothing will ever run, and the resources the
                // submission named are as free as they will ever get.
                confirmed++;
                this.scheduler.notePollConfirmation();
                celtium$noteSlotConfirmed(slot, true);
            } else {
                this.reports.record("pollCompletedSlots: vkGetFenceStatus returned " + status + " for slot " + slot);
            }
        }
        return confirmed;
    }


    /**
     * The deferred counterpart of the slot's own {@code vkQueueSubmit}: the ended slot buffer joins the batched
     * list, and the token naming the batch is recorded as this slot's completion proxy until the reclaim runs the
     * confirmation. Every piece of bookkeeping the direct submit performs (pending bit, sequence, retirement) is
     * the coordinator's own code - the only change is where the queue sees the work.
     */
    public void celtium$submitSlotDeferred(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, long[] waitSemaphores,
            int[] waitStages, long[] signalSemaphores) {
        final int slot = this.frameSlot;
        this.slotSubmitNanos[slot] = System.nanoTime();
        this.slotDeferredTokens[slot] = this.context.celtium$submitDeferredFrame(
                commandBuffer, waitSemaphores, waitStages, signalSemaphores, slot,
                lost -> celtium$noteSlotConfirmed(slot, lost.booleanValue()));
        celtium$snapshotTimingState();
    }


    /**
     * Moves this frame's accumulated timestamp state into a per-submission snapshot, at the one point
     * where the state is final: the frame's buffer has ended and its submission is queued. The live
     * accumulators restart empty for the next recording, and the readback consumes the snapshot only
     * once the scheduler confirms this submission - the wait the old read-the-live-state shape could
     * not make, because by the next begin the accumulators already described a newer, still
     * unconfirmed frame and were cleared either way. The sequence is the one the coordinator's
     * bookkeeping is about to stamp for this submission: the same {@code submitSequence() + 1} the
     * arena's in-flight marking reads in that bookkeeping. A frame that timed nothing queues no
     * snapshot and files nothing, which is what keeps its terrain column at zero rather than at a
     * stale sum.
     */
    private void celtium$snapshotTimingState() {
        if (this.funnel.timestampSlots == 0 && !this.immediatePassTimed && !this.frameGpuTimed) {
            return;
        }
        this.timingReadback.offer(new TerrainTimingReadback.Snapshot(
                this.scheduler.submitSequence() + 1L, this.frameId, celtium$timestampPoolBase(),
                this.funnel.timestampSlots, new java.util.HashMap<>(this.timestampPasses),
                new java.util.HashMap<>(this.timestampPackPassNames),
                this.windowTimestampCount,
                java.util.Arrays.copyOf(this.funnel.windowTimestampTargets, this.windowTimestampCount),
                this.immediatePassTimed, this.frameGpuTimed,
                this.gpuMarkCount, java.util.Arrays.copyOf(this.gpuMarkLabels, this.gpuMarkCount)));
        this.timestampPasses.clear();
        this.timestampPackPassNames.clear();
        this.funnel.timestampSlots = 0;
        this.windowTimestampCount = 0;
        this.immediatePassTimed = false;
        this.frameGpuTimed = false;
        this.gpuMarkCount = 0;
    }


    /** Stamps the direct (non-deferred) submit path, which the frame coordinator drives itself. */
    public void celtium$noteDirectSubmit() {
        this.slotSubmitNanos[this.frameSlot] = System.nanoTime();
    }


    /**
     * The bookkeeping one confirmed slot completion makes true: the fence was observed signalled (or the device
     * reported lost, which is completion-equivalent for pending purposes), so the slot may be re-recorded and every
     * resource gated on its sequence may be released.
     * <p>
     * Every route that confirms a slot runs through this one tail - the slot wait's success exits, the
     * completion poll, the deferred submits' reclaim callback, a device-wide idle - so the per-frame
     * linear ring's reset hangs off this method rather than off any one route: the confirmation recorded
     * here is exactly the reset gate's precondition (Stage 2), and a route that confirms without passing
     * here does not exist.
     */
    private void celtium$noteSlotConfirmed(int slot, boolean lostDevice) {
        long submitNanos = this.slotSubmitNanos[slot];
        if (submitNanos != 0L) {
            this.slotSubmitNanos[slot] = 0L;
            celtium$noteSubmitToFence(slot, System.nanoTime() - submitNanos);
        }
        this.slotDeferredTokens[slot] = 0;
        this.scheduler.noteCompleted(slot, this.scheduler.slotSubmitSequence(slot));
        // Stage 2's reset gate, seated at the confirmation itself: the slot's linear-ring slices are the
        // GPU's no longer, whichever route observed the completion. The wait-only wiring this replaces
        // left poll-confirmed slots unrecycled - the poll is the dominant confirmation route and never
        // enters the wait - so their ring cursor accumulated across reuses until the slot pinned at
        // capacity and every record of a window was refused (the stage-4 legs' droppedDraws). The reset
        // stays hold-aware: a consumer still holding slices of this slot defers it to its last release.
        this.context.celtium$resetFrameLinearSlot(slot);
        // Queue order: this submission's completion implies every earlier one finished too, and the
        // scheduler's noteCompleted records exactly that.
        if (this.slotCommandStates[slot].state() == VulkanCommandBufferState.State.PENDING) {
            this.slotCommandStates[slot].completed();
        }
        if (this.scheduler.completedSubmitSequence() >= this.scheduler.submitSequence()) {
            this.submissionPending = false;
        }
        VulkanContext.noteCompletionConfirmed(lostDevice);
        celtium$onSubmissionConfirmed();
    }


    /**
     * The shared "a submission was confirmed" tail: the arena's completion epoch advances, and every retired group
     * whose submission sequence is covered by the watermark may be released now.
     */
    private void celtium$onSubmissionConfirmed() {
        if (this.frameArena != null) {
            this.frameArena.completed(VulkanContext.completedEpoch(), this.scheduler.completedSubmitSequence());
        }
        this.funnel.releaseRetiredImmediateGroups();
        if (!this.funnel.celtium$recordingOpen()
                && this.scheduler.completedSubmitSequence() >= this.scheduler.submitSequence()
                && this.funnel.immediateGroups.isEmpty() && this.funnel.currentImmediateMesh == null) {
            // The retire queue is drained per device: this device's entries may only flush when nothing of this
            // device is in flight at all - the same statement the drain used to establish, reached here through
            // the watermark. Another device's entries wait for that device's own drain (L06-01/BH10-09).
            int drained = VulkanRetireQueue.flushFor(this.context.device().address());
            if (drained > 0) {
                this.submissionStatistics.addRetiredFlushes(drained);
            }
        }
    }


    /** How long a wait for the GPU will be tolerated before the run ends rather than hanging. */
    private static final long IDLE_TIMEOUT_NANOS = 5_000_000_000L;

    /**
     * How many times a timed-out fence wait is retried before the renderer latches. A resize or a swapchain rebuild
     * produces transient timeouts; a device that has genuinely stopped produces one on every attempt. Counted as
     * {@code idleWaitTimeouts}/{@code idleWaitRetries} so the report says which of the two happened.
     */
    private static final int IDLE_WAIT_RETRIES = 3;


    /**
     * Debug aid: copies the first vertices of the buffer the first draw uses into host-visible memory and decodes
     * them. It answers the one question the CPU-side logs cannot: what the GPU actually holds.
     */
    private void ensureReadback() {
        if (this.readbackBuffer != 0) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkBufferCreateInfo create = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(READBACK_SLOTS * 64L).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateBuffer(this.context.device(), create, null, handle), "vkCreateBuffer(readback)");
            this.readbackBuffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements = org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.context.device(), this.readbackBuffer, requirements);
            org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties properties =
                    org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(this.context.physicalDevice(), properties);
            int type = -1;
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((requirements.memoryTypeBits() & (1 << i)) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
                    type = i;
                    break;
                }
            }
            if (type < 0) {
                throw new IllegalStateException("No host-visible coherent memory type for the debug readback");
            }
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size()).memoryTypeIndex(type);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanTerrainGpuBackend:5047")) {
                throw new IllegalStateException("injected allocation failure: VulkanTerrainGpuBackend:5047");
            }
            check(VulkanContext.allocateMemory(this.context.device(), allocate, handle, "readbackStaging"), "vkAllocateMemory(readback)");
            this.readbackMemory = handle.get(0);
            check(vkBindBufferMemory(this.context.device(), this.readbackBuffer, this.readbackMemory, 0),
                    "vkBindBufferMemory(readback)");
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            check(vkMapMemory(this.context.device(), this.readbackMemory, 0, READBACK_SLOTS * 64L, 0, pointer), "vkMapMemory(readback)");
            this.readbackMapped = pointer.get(0);
        }
    }

    private void readBackAndLog() {
        this.readbackPending = false;
        this.readbackDone = true;
        if (!this.funnel.waitForIdle()) throw new IllegalStateException("readback fence did not complete");
        ByteBuffer view = MemoryUtil.memByteBuffer(this.readbackMapped,
                Math.max(READBACK_COPY_BYTES, this.readbackSlots * READBACK_COPY_BYTES));
        view.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        StringBuilder line = new StringBuilder("gpu draws:");
        int colourCounts = 0;
        it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap histogram = new it.unimi.dsi.fastutil.ints.Int2IntRBTreeMap();
        for (int slot = 0; slot < this.readbackSlots; slot++) {
            if (!this.readbackValid[slot]) {
                // The copy for this slot was refused, so the block holds whatever was there before it. Decoding it
                // would put a stale colour into the histogram and into the diagnostic this exists to be.
                continue;
            }
            int base = slot * READBACK_COPY_BYTES;
            int colour = view.getInt(base + 12);
            histogram.put(colour, histogram.get(colour) + 1);
            if (slot < 10) {
                line.append(" [").append(slot).append("] colour=0x").append(Integer.toHexString(colour))
                        .append(" light=0x").append(Integer.toHexString(view.getInt(base + 24)))
                        .append(" pos=(").append(Lane2ImmediateRecorder.fmt(view.getFloat(base))).append(',')
                        .append(Lane2ImmediateRecorder.fmt(view.getFloat(base + 4))).append(',')
                        .append(Lane2ImmediateRecorder.fmt(view.getFloat(base + 8))).append(')');
            }
        }
        colourCounts = histogram.size();
        line.append(" distinctColours=").append(colourCounts).append(" histogram=");
        int shown = 0;
        for (it.unimi.dsi.fastutil.ints.Int2IntMap.Entry e : histogram.int2IntEntrySet()) {
            if (shown++ >= 8) break;
            line.append('0').append(Integer.toHexString(e.getIntKey())).append('x').append(e.getIntValue()).append(' ');
        }
        this.reports.record(line.toString());
        // stdout as well as the list: this is the only place that shows what the GPU holds rather than what the CPU
        // wrote, and the diagnostics list is not printed by every run.
        if (VERTEX_DIAGNOSTICS) {
            System.out.println("[Celtium] CELTIUM_GPU_VERTEX " + line);
        }
    }

    static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned " + result);
        }
    }

    public void updateTimings() {
        // The vertex readback is read once per run, after a frame that recorded its copies: this is the frame boundary.
        // Wait until at least two draws were remembered: the first frame of a session draws almost nothing, and reading
        // then would report a single vertex and say nothing about the sections that are actually visible.
        if (VERTEX_DIAGNOSTICS && !this.readbackDone && this.readbackSlots >= READBACK_SLOTS / 2) readBackAndLog();
        // The immediate window count is per frame. The measurement that forced the limit: the first window of a frame
        // left the framebuffer at 150.46 before and after, and the second turned it to 0.0 with the GL state restored
        // unchanged, which is what blackened the captured frame.
        // A flush is not a frame: it increments the flush count and leaves the frame's budget and counters alone (S11).
        this.funnel.immediateStatistics.noteFlushCount();
        this.funnel.framesSeen++;
        // The lightmap is refreshed by Minecraft every frame through DynamicTexture, whose upload path no hook of this
        // project has been able to reach without crashing the client. Dropping the mirrors here makes the next use of
        // each re-read its GL texture; the interval is a property so the cost can be measured rather than assumed.
        if (MIRROR_REFRESH_FRAMES > 0 && (++this.mirrorRefreshCounter % MIRROR_REFRESH_FRAMES) == 0) {
            if (this.funnel.textureRegistry != null) {
                this.funnel.textureRegistry.invalidateAll();
                this.mirrorRefreshes++;
            }
        }
        // Item 359: the statistics read stands outside updateTimingsBody on purpose - that body's early
        // return (no timestamp pool, or no pass timed this frame) must not be able to starve this read, or a
        // timestamp-less device would report a silent instrument, which reads as "nothing was drawn".
        readPipelineStatistics();
        updateTimingsBody();
    }
}
