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
     * Route frame submissions through the batched deferred queue instead of an immediate vkQueueSubmit: every
     * recording of the frame becomes a VkSubmitInfo inside the batch's single driver call. Order is preserved
     * because the flush submits the entries in the order they were queued. Requires
     * {@code celtium.vulkan.batchedSubmits} (the deferred machinery's own switch); off by default until a leg
     * measures it.
     */
    /**
     * Whether a frame's recordings are coalesced into one open frame buffer and submitted once at the frame
     * boundary. OFF by default: with it the flush wall time falls from 3.82 to 2.35-2.41 ms per frame (AMD RX 6400,
     * 4.55 flushes/frame) and queue submissions per frame from 12.7 to 4.6, but the E2E 1.93a pack produces
     * command-buffer state validation errors under it (legs unife2e1 and uniE1, 8 VUIDs of the
     * vkCmd*-commandBuffer-recording family, then the process ended with no report). The validation text names the
     * mechanism exactly: the open command buffer "is now in an invalid state ... because the following objects bound
     * to the command buffer were invalidated: VkBuffer ... was destroyed, VkDescriptorSet ... was destroyed or
     * updated without UPDATE_AFTER_BIND". A long-lived open recording holds references to buffers and descriptor
     * sets that the E2E pack's world load and mod framebuffer paths grow, replace or rewrite mid-frame, so the
     * resource-lifetime contract has to become "nothing an open recording references is destroyed or rewritten
     * before that recording is submitted" (the OPT-07 ownership contract the upload path already names) before this
     * can be the default. Take the win only then: {@code -Dceltium.vulkan.batchFrameSubmits=true} is the measured
     * A/B (flush 3.82 -> 2.35 ms/frame, submits 12.7 -> 4.6) for that work.
     */
    private static final boolean BATCH_FRAME_SUBMITS =
            Boolean.getBoolean("celtium.vulkan.batchFrameSubmits")
                    && !"false".equals(System.getProperty("celtium.vulkan.batchedSubmits", "true"));

    /** What the coordinator needs from the renderer that owns the frame. */
    public interface Host {
        org.lwjgl.vulkan.VkCommandBuffer commandBuffer();
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

    /** Submits the frame with the GL-Vulkan semaphore pair, which is the ordinary path. */
    void submitCommands() {
        submitCommands(true);
    }

    /** Bounded probe counter for the submit entry (2026-10-15). */
    private static int celtium$submitEntryLogs;

    void submitCommands(boolean withFrameSemaphores) {
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
            return;
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
            if (!frameSemaphores && BATCH_FRAME_SUBMITS) {
                return;
            }
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
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
            if (!BATCH_FRAME_SUBMITS) {
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
            if (BATCH_FRAME_SUBMITS) {
                // One vkQueueSubmit arms ONE fence, so ~9 recordings a frame were ~9 driver calls. The batch keeps
                // every VkSubmitInfo and submits them together at the frame's flush points: same queue order, one
                // call, and the shared batch fence stands in for the slot's own fence via the reclaim callback.
                this.host.celtium$submitSlotDeferred(this.host.commandBuffer(), waitArray, stageArray, signalArray);
            } else {
                        VulkanContext.queueSubmitCalls++;
                        this.host.check(vkQueueSubmit(this.host.context().queue(), submit, this.host.fence()), "vkQueueSubmit");
            }

            celtium$noteSubmissionBookkeeping(frameSemaphores);
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
