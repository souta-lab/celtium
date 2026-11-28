package org.soutalab.celtium.vulkan;

import org.lwjgl.system.MemoryStack;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.vkEndCommandBuffer;

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

    /** The failure injection the submit path honours, as the backend declares it (a property, not a constant). */
    private static int FAIL_SUBMITS_REMAINING = Integer.getInteger("celtium.vulkan.failSubmits", 0).intValue();

    /**
     * A frame's command buffer is always ended and submitted once, at the frame boundary, instead of per segment:
     * every device batches, unconditionally.
     * <p>
     * Measured history: an Intel exception was measured early - the same build, the same scene and the same three
     * alternating pairs read 3.69 / 3.60 / 3.66 ms with the batched path off and 31.4 / 9.6 / 10.0 ms with it on -
     * but that predated the frame-slot and upload-ordering fixes. Re-measured 2026-10-25 on UHD 630
     * (jar 276f85c17c60d579, KPI flags, alternated): batched 2.23 / 2.23 ms p50, unbatched 3.56 / 4.04 ms, with 10k
     * against 53-64k queue submits. Intel's driver pays per vkQueueSubmit, and unbatched frames were what made
     * Intel 1.68x slower than Celeritas. On the discrete AMD card the per-segment path costs 10% (1.102x).
     */

    /** What the coordinator needs from the renderer that owns the frame. */
    public interface Host {
        org.lwjgl.vulkan.VkCommandBuffer commandBuffer();

        /**
         * Writes the frame's end-of-frame GPU timestamp, the pair to the start written when its command buffer opened.
         * <p>
         * The frame coordinator owns where the buffer ends, which is why this lives on the host interface rather than in
         * the recorder: only the pair together answers whether the GPU is busy for the whole frame or idle while the host
         * waits for it.
         */
        void recordFrameEndTimestamp();

        /**
         * Records the frame's S3 Hi-Z build (the fixed template's last stage) into the still-open
         * command buffer, at the seat {@link #celtium$finishFrameBuffer} marks HIZ_BUILD: after the
         * main stage's depth exists, before the frame-end timestamp.
         */
        void recordHiZBuild();

        /** Stamps the direct submit path for the submit-to-fence distribution the summary reports. */
        void celtium$noteDirectSubmit();
        VulkanCommandBufferState commandState();
        VulkanContext context();
        long fence();
        VulkanFrameArena frameArena();
        long frameId();
        int frameSlot();
        boolean frameSubmitReachedQueue();
        FrameImageTarget frameTarget();
        boolean gpuStalled();
        ImmediateStatistics immediateStatistics();
        boolean immediateWindowBusy();
        boolean lastFrameReachedQueue();
        java.util.List<ImmediateGroup> recordedImmediateGroups();
        java.util.List<ImmediateGroup> retiredImmediateGroups();
        FrameSubmissionScheduler scheduler();
        boolean submissionPending();
        SubmissionStatistics submissionStatistics();
        RenderTargetRegistry targetRegistry();
        int failImmediateSubmits();
        void record(String message);
        void noteImmediateSubmitFailure();
        void check(int result, String operation);

        void setSubmissionPending(boolean pending);
        void setFrameSubmitReachedQueue(boolean reached);
        void setLastFrameReachedQueue(boolean reached);

        /**
         * Queue an ended slot buffer through the batched deferred list instead of vkQueueSubmit; the host records
         * the returned batch fence as the slot's completion proxy. See {@link #submitCommands()}.
         */
        void celtium$submitSlotDeferred(org.lwjgl.vulkan.VkCommandBuffer commandBuffer, long[] waitSemaphores,
                int[] waitStages, long[] signalSemaphores);

        /** Whether the frame's unified command buffer is still recording; set at its begin, cleared at its submit. */
        boolean frameBufferOpen();
        void setFrameBufferOpen(boolean open);
    }

    private final Host host;

    FrameCoordinator(Host host) {
        this.host = host;
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
     * Opens the coordinator's frame: records the template pick and clears the stage
     * record. Called by the host at the frame's begin, the same point the native pack
     * seat opens. A frame the pack executor records (through the seat) does not run the
     * host's stage marks; the pick is recorded so the frame's shape is stated either way.
     */
    void beginFrame(boolean packActive, boolean modFboRequested) {
        this.frameTemplate = selectTemplate(packActive, modFboRequested);
        this.frameModFboRequested = modFboRequested;
        java.util.Arrays.fill(this.stageEntered, false);
        this.lastStage = null;
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
        if (this.host.gpuStalled()) {
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
        this.host.context().celtium$supplyUploadBudgetFrameKey(this.host.frameId());
        this.host.context().flushUploads();
        if (this.host.frameBufferOpen()) {
            // Unified frame buffer: the segment's commands stay recorded; end, submission and bookkeeping all happen
            // once at the frame boundary via celtium$finishFrameBuffer.
            return Submission.RECORDED;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The frame's own GPU-time pair closes here, so it spans both the passes and everything between them.
            this.host.recordFrameEndTimestamp();
            this.host.check(vkEndCommandBuffer(this.host.commandBuffer()), "vkEndCommandBuffer");
            this.host.commandState().end();
            if (this.host.frameBufferOpen()) {
                // Falling back to a direct submit mid-frame: the partially recorded frame buffer is ended above and
                // armed by its own slot fence like any ordinary submission, so the flag's story ends here.
                this.host.setFrameBufferOpen(false);
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
            if (this.host.immediateWindowBusy() && !this.host.recordedImmediateGroups().isEmpty() && this.host.failImmediateSubmits() > 0) {
                this.host.noteImmediateSubmitFailure();
                throw new IllegalStateException("injected immediate submit failure before queue ownership");
            }
            if (FAIL_SUBMITS_REMAINING > 0) {
                // Test-only injection, so the fail-closed paths can be exercised instead of merely compiled: the
                // submission is treated as refused by the driver, which is the case the audit's E asks to be shown to
                // end in bounded time rather than in a wait for a signal that never comes.
                FAIL_SUBMITS_REMAINING--;
                this.host.record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
                throw new IllegalStateException("injected submit failure before queue ownership");
            }
            if (VulkanSubmitFault.shouldFail("fenced")) {
                // The failure is raised exactly where a driver refusal would be, before the queue has seen anything, so
                // the code's own pre-submit rules apply: what is already recorded stays recorded and nothing in flight is
                // touched. That is the property this switch exists to check.
                this.host.check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
            }
            long submitStartedNanos = System.nanoTime();
            // One vkQueueSubmit arms ONE fence, so ~9 recordings a frame were ~9 driver calls. The batch keeps
            // every VkSubmitInfo and submits them together at the frame's flush points: same queue order, one
            // call, and the shared batch fence stands in for the slot's own fence via the reclaim callback.
            this.host.celtium$submitSlotDeferred(this.host.commandBuffer(), waitArray, stageArray, signalArray);
            submitQueueNanos += System.nanoTime() - submitStartedNanos;
            submitQueueCalls++;

            this.host.celtium$noteDirectSubmit();
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
        if (!this.host.frameBufferOpen()) {
            return;
        }
        this.host.setFrameBufferOpen(false);
        if (this.host.gpuStalled()) {
            // The buffer may have been begun then stalled mid-frame: it was never submitted, so it may be reset by
            // the next beginCommands without a wait. The state object goes back to initial with it, or that begin
            // reports a nested begin on a recording nothing will ever end.
            this.host.commandState().reset();
            return;
        }
        markStage(Stage.UPLOAD);
        this.host.context().celtium$supplyUploadBudgetFrameKey(this.host.frameId());
        this.host.context().flushUploads();
        // S3 HiZBuild's seat: the fixed template's last stage records here, after the main
        // stage's depth exists and before the buffer ends. The host records the actual
        // MAX-reduction pyramid build into this bracket (D8-a).
        markStage(Stage.HIZ_BUILD);
        this.host.recordHiZBuild();
        // The batched path ends the buffer here rather than in submit(), so the frame's GPU-time pair closes here too.
        this.host.recordFrameEndTimestamp();
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
        this.host.check(vkEndCommandBuffer(this.host.commandBuffer()), "vkEndCommandBuffer(frame)");
        this.host.commandState().end();
        if (this.host.immediateWindowBusy() && !this.host.recordedImmediateGroups().isEmpty() && this.host.failImmediateSubmits() > 0) {
            this.host.noteImmediateSubmitFailure();
            throw new IllegalStateException("injected immediate submit failure before queue ownership");
        }
        if (FAIL_SUBMITS_REMAINING > 0) {
            FAIL_SUBMITS_REMAINING--;
            this.host.record("injected submit failure (" + FAIL_SUBMITS_REMAINING + " left): this submission is not sent");
            throw new IllegalStateException("injected submit failure before queue ownership");
        }
        if (VulkanSubmitFault.shouldFail("fenced")) {
            this.host.check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
        }
        this.host.celtium$submitSlotDeferred(this.host.commandBuffer(), waitArray, stageArray, signalArray);
        celtium$noteSubmissionBookkeeping();
    }

    /**
     * {@return the one-element wait array carrying the frame target's borrowed-image acquire semaphore},
     * or null when no borrow's acquire is waiting to be consumed. Consume-on-read at the target: only the
     * first submission built after a borrow sees it, which is exactly the submission that must wait.
     */
    private long[] celtium$presentAcquireWait() {
        long acquire = this.host.frameTarget().presentAcquireSemaphore();
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
        if (this.host.frameArena() != null) {
            // The sequence increments a few lines below, where the slot's own record is armed; the value this
            // submission will take is the current one plus one.
            this.host.frameArena().submitted(this.host.frameId(), this.host.scheduler().submitSequence() + 1);
        }
        // The queue accepted this submission, so the layouts the recording declared into it are now the truth
        // every wrapper of those images shares. A refused submit throws before this line, leaving the recording
        // pending for the next beginCommands() to abandon - and the next ensureGeneralLayout/ensureSampledLayout
        // to re-record.
        VulkanGlTexture.commitLayoutRecording(this.host.commandBuffer());
        // The named targets' recorded transitions become fact at the same point and by the same rule
        // (audit-vulkan-core E2): their registry layout is what the last accepted submission left, never
        // what a recording merely drew into a buffer that may still be abandoned.
        this.host.targetRegistry().commitNamedTargetLayoutRecording(this.host.commandBuffer().address());
        this.host.setSubmissionPending(true);
        // OPT-08: the submission belongs to a slot and carries a sequence. The slot's fence is what makes its
        // buffer reusable, and the sequence watermark is what releases the resources this submission still names -
        // not "some wait succeeded", which is what a single-frame renderer could get away with.
        this.host.scheduler().noteSubmitted(this.host.frameSlot());
        SubmitTrail.note(SubmitTrail.FRAME, this.host.scheduler().submitSequence(), this.host.frameSlot());
        this.host.commandState().submitted();
        for (ImmediateGroup group : this.host.recordedImmediateGroups()) {
            this.host.immediateStatistics().addImmediateSubmittedBatches(group.batches);
            // This submission is the newest one that can still name the group's arena bytes: its mesh may be
            // freed exactly when the watermark covers this sequence and not before.
            group.retireSequence = this.host.scheduler().submitSequence();
            this.host.retiredImmediateGroups().add(group);
        }
        this.host.recordedImmediateGroups().clear();
        this.host.setFrameSubmitReachedQueue(true);
        this.host.setLastFrameReachedQueue(true);
    }
}
