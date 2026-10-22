package org.soutalab.celtium.vulkan;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkSubmitInfo;
import static org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
import static org.lwjgl.vulkan.VK10.vkQueueSubmit;
import static org.lwjgl.vulkan.VK10.vkQueueWaitIdle;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.vkResetFences;
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
     * Coalesce accepted recordings into an open command buffer until the real frame boundary (default on).
     * A RECORDED result is not a submission packet and must not be treated as a failed external-GL handoff.
     * Every referenced buffer, image and descriptor generation must remain valid from recording through completion;
     * retained geometry uses draw leases for that contract. Other resource domains require the same lifetime audit.
     */
    /**
     * Whether a frame's command buffer is ended and submitted once, at the frame boundary, instead of per segment.
     * <p>
     * On a discrete card that is the cheaper arrangement and it has been the default since it was measured. On an
     * integrated Intel part it is not: the same build, the same scene and the same three alternating pairs read
     * 3.69 / 3.60 / 3.66 ms with the batched path off and 31.4 / 9.6 / 10.0 ms with it on - a median ratio of 0.374x, and
     * a spread on the batched side that says the frame is waiting for something rather than working. On the discrete
     * AMD card the same flag costs 10% (1.102x), so this is a per-device decision, not a better default.
     * <p>
     * The property still wins when it is set, so a leg can choose either path on either device.
     */
    private static final Boolean BATCH_FRAME_SUBMITS_OVERRIDE = batchFrameSubmitsOverride();

    private static Boolean batchFrameSubmitsOverride() {
        String value = System.getProperty("celtium.vulkan.batchFrameSubmits",
                System.getProperty("celtium.vulkan.batchedSubmits"));
        if (value == null) {
            return null;
        }
        return Boolean.valueOf(!"false".equalsIgnoreCase(value));
    }

    /**
     * {@return whether this device's frames are submitted in one batch}.
     * <p>
     * The property, when set, decides. Otherwise the device does: batching is kept on the discrete parts where it was
     * measured cheaper, and turned off on Intel's integrated parts where it was measured 2.7x slower.
     */
    private boolean batchFrameSubmits() {
        if (BATCH_FRAME_SUBMITS_OVERRIDE != null) {
            return BATCH_FRAME_SUBMITS_OVERRIDE.booleanValue();
        }
        return !this.host.isIntelDevice();
    }

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

        /** {@return whether the device is an Intel one}, where batched frame submits were measured 2.7x slower. */
        boolean isIntelDevice();
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
        int failImmediateSubmits();
        void record(String message);
        void noteImmediateSubmitFailure();
        void check(int result, String operation);

        void setSubmissionPending(boolean pending);
        void setFrameSubmitReachedQueue(boolean reached);
        void setLastFrameReachedQueue(boolean reached);

        /**
         * Queue an ended slot buffer through the batched deferred list instead of vkQueueSubmit; the host records
         * the returned batch fence as the slot's completion proxy. See {@link #submitCommands(boolean)}.
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

    void submitSignalOnly() {
        // With the pair skipped there is no frame synchronization to release: the frame-close flags this path set
        // are still set, because they gate the present's own "a frame reached the queue" question, but no packet
        // goes to the queue for a contract nothing honours.
        if (!this.host.frameTarget().needsFrameSemaphores()) {
            this.host.setFrameSubmitReachedQueue(true);
            this.host.setLastFrameReachedQueue(true);
            return;
        }
        // Counted, so a leg can say whether the frame-close path fired rather than whether the leg was green: the
        // per-image change made consumeDone wait on whichever done semaphore was recorded, and this is the producer
        // whose pairing that could break. Two legs were green without this path firing at all, which is a leg about
        // the other path - the counter is what turns that into evidence either way.
        this.host.submissionStatistics().noteSignalOnlySubmissions();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = stack.mallocLong(1);
            waitSemaphores.put(0, this.host.frameTarget().waitSemaphore());
            IntBuffer waitStages = stack.mallocInt(1);
            waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            LongBuffer signalSemaphores = stack.mallocLong(1);
            signalSemaphores.put(0, this.host.frameTarget().doneSemaphore());
            submit.waitSemaphoreCount(waitSemaphores.remaining());
            submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages).pSignalSemaphores(signalSemaphores);
            // No command buffers: the submission exists only to release the frame's synchronization.
            if (VulkanSubmitFault.shouldFail("signal-only")) {
                // The failure is raised exactly where a driver refusal would be, before the queue has seen anything, so
                // the code's own pre-submit rules apply: what is already recorded stays recorded and nothing in flight is
                // touched. That is the property this switch exists to check.
                this.host.check(org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST, "vkQueueSubmit (injected)");
            }
                        VulkanContext.queueSubmitCalls++;
                        this.host.check(vkQueueSubmit(this.host.context().queue(), submit, VK_NULL_HANDLE), "vkQueueSubmit (signal only)");
            this.host.setFrameSubmitReachedQueue(true);
            this.host.setLastFrameReachedQueue(true);
            if (this.host.frameTarget() instanceof GllessFrameTarget gllessTarget) {
                // The signal-only submission waited on the wait semaphore and signalled the done semaphore: the pairing is
                // complete for this frame, so nothing is outstanding to consume.
                gllessTarget.noteWaitConsumed();
            }
        }
    }

    /** Recording acceptance is not queue submission, and neither means GPU completion. */
    enum Submission {
        SKIPPED, RECORDED, QUEUED;

        boolean canCloseTarget(boolean needsExternalSemaphores) {
            return this == QUEUED || (this == RECORDED && !needsExternalSemaphores);
        }
    }

    Submission submitCommands() {
        return submitCommands(true);
    }

    /** Bounded probe counter for the submit entry (2026-10-15). */
    private static int celtium$submitEntryLogs;
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

    Submission submitCommands(boolean withFrameSemaphores) {
        // Temporary probe: the present skips with "no completed frame waiting" and the frame-queued setter is never
        // called with true, while gpuStalled is false. This line says whether the submit is entered at all, which
        // splits "never called" from "returns early" in one leg.
        if (celtium$submitEntryLogs++ < 12) {
            System.out.println("[Celtium] CELTIUM_SUBMIT_ENTRY call=" + celtium$submitEntryLogs
                    + " withFrameSemaphores=" + withFrameSemaphores + " gpuStalled=" + this.host.gpuStalled());
        }
        if (this.host.gpuStalled()) {
            // Nothing to end: the buffer was never begun. This is the last of the recording entries the steady-state injection
            // reported, after the pass-level ones.
            return Submission.SKIPPED;
        }
        // The owned target's semaphore pair synchronizes the renderer with itself on one queue, which is ordering
        // the queue already has: when the target says the contract does not apply the submission takes no semaphore
        // at all, and every per-stage packet that exists only to carry the pair is skipped with it.
        boolean frameSemaphores = withFrameSemaphores && this.host.frameTarget().needsFrameSemaphores();
        // OPT-07: every copy the frame recorded goes out as one submission before this one, so the vertices, indices
        // and uniform bytes this command buffer reads are in place. It is a no-op when nothing is pending, and it is
        // the frame's own ordering statement: a copy no longer needs a submission and a wait of its own.
        this.host.context().flushUploads();
        if (this.host.frameBufferOpen()) {
            // Unified frame buffer: the segment's commands stay recorded; end, submission and bookkeeping all happen
            // once at the frame boundary via celtium$finishFrameBuffer. A frame that switched to a semaphore-carrying
            // target mid-recording falls through instead and takes the direct path below.
            if (!frameSemaphores && this.batchFrameSubmits()) {
                return Submission.RECORDED;
            }
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
            long[] waitArray = null;
            int[] stageArray = null;
            long[] signalArray = null;
            if (frameSemaphores) {
                // Plain arrays for the deferred entry: the batch flush copies them into its VkSubmitInfo array at
                // submit time, long after this stack is gone, so stack buffers cannot carry them.
                waitArray = new long[] {this.host.frameTarget().waitSemaphore()};
                stageArray = new int[] {VK_PIPELINE_STAGE_ALL_COMMANDS_BIT};
                signalArray = new long[] {this.host.frameTarget().doneSemaphore()};
            }
            VkSubmitInfo submit = null;
            if (!this.batchFrameSubmits()) {
            this.host.check(vkResetFences(this.host.context().device(), this.host.fence()), "vkResetFences");
            submit = VkSubmitInfo.calloc(stack).sType$Default();
            LongBuffer waitSemaphores = null;
            IntBuffer waitStages = null;
            LongBuffer signalSemaphores = null;
            if (frameSemaphores) {
                waitSemaphores = stack.mallocLong(1);
                waitSemaphores.put(0, this.host.frameTarget().waitSemaphore());
                waitStages = stack.mallocInt(1);
                waitStages.put(0, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT);
            }
            if (frameSemaphores) {
                signalSemaphores = stack.mallocLong(1);
                signalSemaphores.put(0, this.host.frameTarget().doneSemaphore());
            }
            org.lwjgl.PointerBuffer commandBuffers = stack.mallocPointer(1);
            commandBuffers.put(0, this.host.commandBuffer().address());
            // LWJGL derives the semaphore counts from the buffers, so only the pointers are set here.
            // waitSemaphoreCount must be set explicitly: LWJGL's pWaitSemaphores(LongBuffer) writes only the
            // pointer, unlike pSignalSemaphores which also sets its count. Leaving it to calloc's zero meant this
            // submission waited on nothing at all - which is what the validation layer was reporting all along.
            if (frameSemaphores) {
                submit.waitSemaphoreCount(waitSemaphores.remaining());
                submit.pWaitSemaphores(waitSemaphores).pWaitDstStageMask(waitStages)
                        .pSignalSemaphores(signalSemaphores);
            }
            submit.pCommandBuffers(commandBuffers);
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
            if (this.batchFrameSubmits()) {
                // One vkQueueSubmit arms ONE fence, so ~9 recordings a frame were ~9 driver calls. The batch keeps
                // every VkSubmitInfo and submits them together at the frame's flush points: same queue order, one
                // call, and the shared batch fence stands in for the slot's own fence via the reclaim callback.
                this.host.celtium$submitSlotDeferred(this.host.commandBuffer(), waitArray, stageArray, signalArray);
            } else {
                        VulkanContext.queueSubmitCalls++;
                        this.host.check(vkQueueSubmit(this.host.context().queue(), submit, this.host.fence()), "vkQueueSubmit");
            }
            submitQueueNanos += System.nanoTime() - submitStartedNanos;
            submitQueueCalls++;

            this.host.celtium$noteDirectSubmit();
            celtium$noteSubmissionBookkeeping(frameSemaphores);
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
     * unified buffer is open ({@code batchFrameSubmits} off, a fallback direct submit already ran, or a semaphore
     * target mid-frame).
     */
    void celtium$finishFrameBuffer(boolean withFrameSemaphores) {
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
        boolean frameSemaphores = withFrameSemaphores && this.host.frameTarget().needsFrameSemaphores();
        this.host.context().flushUploads();
        // The batched path ends the buffer here rather than in submit(), so the frame's GPU-time pair closes here too.
        this.host.recordFrameEndTimestamp();
        long[] waitArray = null;
        int[] stageArray = null;
        long[] signalArray = null;
        if (frameSemaphores) {
            waitArray = new long[] {this.host.frameTarget().waitSemaphore()};
            stageArray = new int[] {VK_PIPELINE_STAGE_ALL_COMMANDS_BIT};
            signalArray = new long[] {this.host.frameTarget().doneSemaphore()};
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
        celtium$noteSubmissionBookkeeping(frameSemaphores);
    }

    /**
     * The bookkeeping one accepted submission makes true - identical for the direct {@link #submitCommands} submit
     * and the deferred {@link #celtium$finishFrameBuffer} one: arena in-flight marking, layout commit, the pending
     * bit, the slot's sequence record, the recorded groups' retirement tags and the reached-queue flags.
     */
    private void celtium$noteSubmissionBookkeeping(boolean frameSemaphores) {
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
        this.host.setSubmissionPending(true);
        // OPT-08: the submission belongs to a slot and carries a sequence. The slot's fence is what makes its
        // buffer reusable, and the sequence watermark is what releases the resources this submission still names -
        // not "some wait succeeded", which is what a single-frame renderer could get away with.
        this.host.scheduler().noteSubmitted(this.host.frameSlot());
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
        if (frameSemaphores && this.host.frameTarget() instanceof GllessFrameTarget gllessTarget) {
            // This submission waited on the frame's wait semaphore, so the target must not consume it again when the
            // frame is abandoned (fog-path's measured VUID-00067 chain: an abandoned frame left the signal
            // outstanding and the next beginFrame signalled the same binary semaphore twice).
            gllessTarget.noteWaitConsumed();
        }
    }
}
