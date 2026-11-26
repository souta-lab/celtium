package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreSignalInfo;
import org.lwjgl.vulkan.VkSemaphoreTypeCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;

import java.nio.LongBuffer;
import java.util.Arrays;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.VK_TIMEOUT;
import static org.lwjgl.vulkan.VK10.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK10.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK12.VK_SEMAPHORE_TYPE_TIMELINE;
import static org.lwjgl.vulkan.VK12.VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO;
import static org.lwjgl.vulkan.VK12.vkGetSemaphoreCounterValue;
import static org.lwjgl.vulkan.VK12.vkSignalSemaphore;
import static org.lwjgl.vulkan.VK12.vkWaitSemaphores;

/**
 * The single timeline semaphore ({@code VK_KHR_timeline_semaphore}, core in Vulkan 1.2) that the new
 * architecture runs all completion tracking on.
 * <p>
 * Frame completion, deferred release of retired resources and upload completion are all unified on this one
 * timeline: every piece of work is handed the next monotonically increasing value, that value is signalled when
 * the work completes, and a reader learns that the work is done by comparing the timeline's current counter
 * against the value it was handed. There is no per-subsystem fence or binary semaphore to keep in step with the
 * others, and a resource that must outlive the frame that used it is retired against a timeline value instead of
 * against a fence owned by whichever subsystem happened to submit it.
 * <p>
 * The only blocking wait this design permits is the single wait at the start of a frame, where the frame loop
 * waits for the slot it is about to reuse to reach the value recorded for that slot's previous submission
 * ({@link #noteSlotSubmitted(int, long)} / {@link #slotValue(int)}). Waiting anywhere else - mid-frame, before a
 * release, before an upload is consumed - is forbidden: code that needs to know whether a value has completed
 * polls {@link #isComplete(long)} or {@link #completedValue()} and defers its work to a later frame when the
 * answer is no, rather than stalling the frame loop on the GPU.
 * <p>
 * Values are allocated on the calling thread with {@link #nextValue()}; slot bookkeeping is a plain array grown
 * to the highest slot the caller names, so the slot count is the caller's policy and is not capped at
 * {@link #FRAMES_IN_FLIGHT}.
 */
public final class FrameTimeline {
    /** Frames in flight under the new architecture's policy (design CELTIUM-NEW-ARCH-2026-11-25, section 2.3). */
    public static final int FRAMES_IN_FLIGHT = 2;

    private final VkDevice device;
    private long semaphore;
    private long lastAllocated;
    private long[] slotValues = new long[FRAMES_IN_FLIGHT];
    private boolean deleted;

    /**
     * Creates the timeline semaphore on {@code device}, starting at value 0 so that the first allocated value
     * (1, from {@link #nextValue()}) is unambiguously "not yet complete".
     *
     * @throws IllegalStateException when {@code vkCreateSemaphore} does not return {@code VK_SUCCESS}
     */
    public FrameTimeline(VkDevice device) {
        this.device = device;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreTypeCreateInfo typeInfo = VkSemaphoreTypeCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_SEMAPHORE_TYPE_CREATE_INFO)
                    .semaphoreType(VK_SEMAPHORE_TYPE_TIMELINE)
                    .initialValue(0L);
            VkSemaphoreCreateInfo createInfo = VkSemaphoreCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(typeInfo);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateSemaphore(device, createInfo, null, handle), "vkCreateSemaphore(FrameTimeline)");
            this.semaphore = handle.get(0);
        }
    }

    /** {@return the timeline semaphore's handle}, or {@code VK_NULL_HANDLE} after {@link #delete()}. */
    public long semaphore() {
        return this.semaphore;
    }

    /**
     * {@return the next timeline value}, allocated in monotonically increasing order starting at 1. The value is
     * only a reservation until something signals it: a queue submission that signals the semaphore with this
     * value, or {@link #signalHost(long)} for an ordering point with no queue work behind it.
     */
    public long nextValue() {
        return ++this.lastAllocated;
    }

    /** {@return the most recently allocated value}, or 0 when no value has been allocated yet. */
    public long lastAllocatedValue() {
        return this.lastAllocated;
    }

    /**
     * Records that {@code slot} was submitted with timeline {@code value}, replacing the slot's previous value.
     * The frame loop reads the record back with {@link #slotValue(int)} before reusing the slot.
     */
    public void noteSlotSubmitted(int slot, long value) {
        checkSlot(slot);
        if (slot >= this.slotValues.length) {
            this.slotValues = Arrays.copyOf(this.slotValues, slot + 1);
        }
        this.slotValues[slot] = value;
    }

    /** {@return the value recorded for {@code slot}'s latest submission}, or 0 when the slot was never submitted. */
    public long slotValue(int slot) {
        checkSlot(slot);
        return slot < this.slotValues.length ? this.slotValues[slot] : 0L;
    }

    /**
     * {@return the timeline's current counter value}: every value less than or equal to this has completed.
     *
     * @throws IllegalStateException when {@code vkGetSemaphoreCounterValue} does not return {@code VK_SUCCESS}
     */
    public long completedValue() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer value = stack.mallocLong(1);
            check(vkGetSemaphoreCounterValue(this.device, this.semaphore, value),
                    "vkGetSemaphoreCounterValue(FrameTimeline)");
            return value.get(0);
        }
    }

    /**
     * Blocks until the timeline reaches {@code value} or {@code timeoutNanos} elapse. This is the frame-start
     * wait, the one blocking wait the design permits; it is not a general synchronisation primitive for callers
     * in the middle of a frame.
     *
     * @return true when the value completed, false when the wait timed out
     * @throws IllegalStateException for any other result (for example {@code VK_ERROR_DEVICE_LOST}), naming the
     *         result code
     */
    public boolean awaitValue(long value, long timeoutNanos) {
        int result = awaitValueResult(value, timeoutNanos);
        if (result == VK_SUCCESS) {
            return true;
        }
        if (result == VK_TIMEOUT) {
            return false;
        }
        throw new IllegalStateException("vkWaitSemaphores(FrameTimeline) returned VkResult " + result);
    }

    /**
     * The same wait as {@link #awaitValue}, returning the raw {@code VkResult} instead of folding it into a
     * boolean-or-throw. Callers whose disposition branches on the code itself - the deferred-submit await,
     * whose matrix separates a lost device from a timeout from any other failure (R-56) - read the code from
     * here rather than parsing it back out of {@link #awaitValue}'s exception text, so the matrix cannot
     * silently break when a message is reworded.
     *
     * @return the {@code vkWaitSemaphores} result: {@code VK_SUCCESS}, {@code VK_TIMEOUT},
     *         {@code VK_ERROR_DEVICE_LOST} or another failure code
     */
    public int awaitValueResult(long value, long timeoutNanos) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo waitInfo = VkSemaphoreWaitInfo.calloc(stack)
                    .sType$Default()
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(this.semaphore))
                    .pValues(stack.longs(value));
            return vkWaitSemaphores(this.device, waitInfo, timeoutNanos);
        }
    }

    /**
     * Signals the timeline at {@code value} from the host, without any queue submission. This creates an ordering
     * point for work that has no GPU work behind it - a frame in which nothing was submitted, an upload that
     * turned out to be empty - so that waiters on the value are released exactly as if a submission had signalled
     * it. The value must still come from {@link #nextValue()} and must be signalled in allocation order.
     *
     * @throws IllegalStateException when {@code vkSignalSemaphore} does not return {@code VK_SUCCESS}
     */
    public void signalHost(long value) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreSignalInfo signalInfo = VkSemaphoreSignalInfo.calloc(stack)
                    .sType$Default()
                    .semaphore(this.semaphore)
                    .value(value);
            check(vkSignalSemaphore(this.device, signalInfo), "vkSignalSemaphore(FrameTimeline)");
        }
    }

    /** {@return whether {@code value} has completed}, without blocking. */
    public boolean isComplete(long value) {
        return completedValue() >= value;
    }

    /** Destroys the semaphore. Idempotent: later calls do nothing. */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        if (this.semaphore != VK_NULL_HANDLE) {
            vkDestroySemaphore(this.device, this.semaphore, null);
            this.semaphore = VK_NULL_HANDLE;
        }
    }

    private static void checkSlot(int slot) {
        if (slot < 0) {
            throw new IllegalArgumentException("FrameTimeline slot must be >= 0, got " + slot);
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned VkResult " + result);
        }
    }
}
