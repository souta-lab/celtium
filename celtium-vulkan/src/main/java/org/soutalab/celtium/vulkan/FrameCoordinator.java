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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            this.host.check(vkEndCommandBuffer(this.host.commandBuffer()), "vkEndCommandBuffer");
            this.host.commandState().end();
            this.host.check(vkResetFences(this.host.context().device(), this.host.fence()), "vkResetFences");
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default();
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
                        VulkanContext.queueSubmitCalls++;
                        this.host.check(vkQueueSubmit(this.host.context().queue(), submit, this.host.fence()), "vkQueueSubmit");

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
}
