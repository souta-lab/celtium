package org.soutalab.celtium.vulkan;

import org.lwjgl.PointerBuffer;
import org.soutalab.celtium.harness.HarnessConfig;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.EXTDebugUtils;
import org.lwjgl.vulkan.VkApplicationInfo;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkBufferCreateInfo;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkLayerProperties;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceIDProperties;
import org.lwjgl.vulkan.KHRDriverProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceDriverProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VK;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkSemaphoreWaitInfo;
import org.lwjgl.vulkan.VkTimelineSemaphoreSubmitInfo;
import org.soutalab.celtium.vulkan.hal.FrameTimeline;
import org.soutalab.celtium.vulkan.hal.PresentEngine;
import org.soutalab.celtium.vulkan.hal.SlabAllocator;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT;
import static org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT;
import static org.lwjgl.vulkan.KHRExternalMemoryFd.VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRSwapchain.VK_KHR_SWAPCHAIN_EXTENSION_NAME;
import static org.lwjgl.vulkan.KHRExternalSemaphoreFd.VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2;

/**
 * A persistent Vulkan device created for exactly the GPU the current OpenGL context runs on.
 * <p>
 * The instance is created without layers unless {@code -Dceltium.vulkan.validation=true} asks for the Khronos
 * validation layer, the physical device is matched by the GL device UUID (the same fail-closed rule
 * {@link VulkanBootstrap} proves: a CPU device and a device below Vulkan 1.2 are rejected, and nothing ever falls
 * back to another GPU), the external memory/semaphore FD extensions are required, and one logical device with a
 * single graphics+transfer queue is created.
 * <p>
 * <b>Threading:</b> every method except {@link #isAvailable()} and {@link #create(String)} touches live Vulkan
 * objects and must be called on the render thread that owns the OpenGL context. {@link #create(String)} also needs
 * a current GL context, because the device UUIDs it matches against are read from GL.
 * <p>
 * <b>Quality:</b> the command and upload helpers are bootstrap-quality. They allocate one command buffer per call,
 * submit it, wait for the whole queue to go idle and free it again. That is correct and simple, but far too coarse
 * for a frame loop; a later increment replaces it with batched submissions and fences.
 */
public final class VulkanContext {
    /**
     * Enables the S2 swapchain probe: a surface and swapchain are created for the game window and one image is
     * cleared and presented, which is the evidence that the presentation path works. Off by default because GL still
     * presents the window in the hybrid build.
     */
    public static final String SWAPCHAIN_PROPERTY = "celtium.vulkan.swapchainProbe";
    /** Startup constants read once: every use site then pays no lookup. */
    private static final boolean VALIDATION_REQUESTED = HarnessConfig.bool("vulkanValidation", true);
    private static final boolean SWAPCHAIN_REQUESTED = Boolean.getBoolean(SWAPCHAIN_PROPERTY);
    private static final boolean HIDE_FD_EXTENSIONS =
            Boolean.getBoolean(VulkanContext.HIDE_FD_EXTENSIONS_PROPERTY);
    private static final boolean PIPELINE_CACHE_DISABLED =
            "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.pipelineCache"));

    private static final int REQUIRED_API_MAJOR = 1;
    private static final int REQUIRED_API_MINOR = 2;

    /**
     * Whether a {@code VK_PHYSICAL_DEVICE_TYPE_CPU} device may be selected. Default off: a software ICD (lavapipe)
     * exists alongside every real GPU, and choosing it silently would render slowly rather than report the
     * no-matching-device failure. {@code -Dceltium.vulkan.allowCpuDevice=true} is for test legs on hosts with no
     * DRM device at all, where the software rasterizer is the intended device.
     */
    private static final boolean ALLOW_CPU_DEVICE = Boolean.getBoolean("celtium.vulkan.allowCpuDevice");

    static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";

    /** Vulkan requires buffer copy offsets and sizes to be a multiple of 4. */
    private static final int COPY_ALIGNMENT = 4;

    /** Floor for the cached staging buffer so a stream of small uploads does not reallocate every time. */
    private static final long STAGING_MINIMUM_BYTES = 64L * 1024L;

    private VkInstance instance;
    /** Extensions actually enabled on this instance (not merely advertised by the loader). */
    private java.util.Set<String> enabledInstanceExtensions = java.util.Collections.emptySet();
    private VkPhysicalDevice physicalDevice;

    /** The device's 2D image dimension limit, or 0 when the contract has not been reported yet. */
    private int maxImageDimension2D;
    /**
     * Whether this context is the OpenGL-interop (bridge) configuration, i.e. whether a GL device identity was
     * available to match against. It is the one thing that decides whether the fd-sharing extensions are part of the
     * device contract: with a bridge there is a GL image and a GL semaphore to share by fd, and without one (the
     * GL-less configuration, {@code openGl == null}) there is nothing on the other end of a fd. The audit's B02 is
     * that the extensions were required and enabled unconditionally, which excluded devices that are otherwise valid
     * for the native path and tied the GL-less path to Linux fd semantics it never uses.
     */
    private boolean interopPath;
    private VkDevice device;
    // Once the driver reports device loss, even fence reset/destroy can crash natively.
    // Deliberately leak device-owned handles until the failed process exits.
    private volatile boolean deviceLost;
    private VkQueue queue;
    private int queueFamilyIndex = -1;
    private String deviceName;
    private String deviceUuid;
    /** The device's own Vulkan API version and type, kept for the driver/contract lines. */
    private int deviceApiVersion;
    private int deviceType = -1;
    private long commandPool = VK_NULL_HANDLE;

    /**
     * The new architecture's single synchronization primitive (design CELTIUM-NEW-ARCH-2026-11-25 section 2.3):
     * one timeline semaphore whose values carry frame completion, deferred release and upload completion, in
     * place of the per-submission fences and the pooled deferred fences this class used to create. Created with
     * the device in {@link #initialize}; null only before that point and after {@link #delete()}.
     */
    private FrameTimeline frameTimeline;
    /**
     * The device-local slab pools {@link VulkanBuffer} allocates from: fixed 64 MiB slabs, sub-allocated, so a
     * buffer no longer owns a {@code vkAllocateMemory} of its own. One pool per memory type, keyed by type
     * index, because a slab can only bind to a buffer whose requirements name the pool's type; pools are
     * created lazily, the first time a buffer's requirements pick their type.
     */
    private final java.util.Map<Integer, SlabAllocator> deviceLocalSlabsByType =
            new java.util.HashMap<Integer, SlabAllocator>();
    /**
     * The host-visible slab pools {@link VulkanHostBuffer} allocates from: same fixed-slab shape as the
     * device-local pools, but host-visible/coherent and persistently mapped, so a host buffer no longer owns
     * a {@code vkAllocateMemory} of its own either. One pool per memory type, created lazily.
     */
    private final java.util.Map<Integer, SlabAllocator> hostVisibleSlabsByType =
            new java.util.HashMap<Integer, SlabAllocator>();
    /** The synchronous present path (the async presenter thread's replacement), created with the queue. */
    private PresentEngine presentEngine;

    /** The bound on a submission's fence wait, matching the renderer's frame waits. */
    private static final long SUBMIT_TIMEOUT_NANOS = 5_000_000_000L;
    /**
     * Queue waits on the bootstrap submit path, where the API offers no timeout. Reported at teardown so a run says
     * how often the unbounded path was taken; the frame path never uses it.
     */
    public static int bootstrapQueueWaits;
    /**
     * How long those waits took, so a session can say whether the unbounded path is where a stall or a spike comes
     * from instead of only how often it was taken: total and worst, in milliseconds.
     * <p>
     * The prime suspect for the "GPU stalled" latch is a wait that never returned within the bound the frame path
     * uses (5 s). The bootstrap waits have no bound at all, so their distribution is the measurement that says
     * whether the unbounded path is a hazard in practice or only in principle.
     */
    public static long bootstrapQueueWaitMillis;
    /** Per-submission fence waits on the bootstrap path: the bounded replacement for the queue-wide idle (BH-27). */
    public static int bootstrapSubmitWaits;
    public static long bootstrapSubmitWaitMillis;
    public static long bootstrapSubmitWaitWorstMillis;
    /**
     * How many submissions this process has confirmed complete, monotone, one per successful (or device-lost) fence
     * wait (OPT-04's completion epoch).
     * <p>
     * Resource reuse must be decided by completion, never by a frame number having moved. This is the single number
     * that carries that fact out of the wait sites, so a resource can record the epoch of the submission that last
     * used it and be reused only when this counter has reached it. A device loss counts: the queue is not reading
     * anything any more, which is the property the reuse rule actually needs.
     */
    public static long completedSubmissions;

    /** {@return the completion epoch, the count of submissions whose completion has been confirmed}. */
    public static long completedEpoch() {
        return completedSubmissions;
    }

    /**
     * Records that a fence wait confirmed a submission's completion (OPT-04).
     * <p>
     * The backend owns its frame fence and waits on it directly rather than through this class's helpers, so the epoch
     * has to be advanced from both places; a lost device counts here for the same reason it counts in
     * {@code submitWithSemaphores}: nothing is executing any more, which is the fact resource reuse needs.
     *
     * @param lostDevice whether the wait reported {@code VK_ERROR_DEVICE_LOST} rather than success
     */
    public static void noteCompletionConfirmed(boolean lostDevice) {
        completedSubmissions++;
        if (lostDevice) {
            submitDeviceLost++;
        }
    }
    public static long bootstrapQueueWaitWorstMillis;
    /** Submissions whose fence did not signal within the bound: a stall, counted so it can be read after the fact. */
    public static int submitTimeouts;
    /**
     * The rest of the wait-outcome matrix, one counter per named state (audit B04): a wait that confirmed completion,
     * a wait that reported the device lost (completion-equivalent for pending/in-use purposes), and a wait that
     * neither completed nor timed out - OOM or UNKNOWN - whose submission is quarantined because the queue may still
     * own it. The four states are distinguished rather than collapsed into "the wait failed".
     */
    public static int submitCompletions;
    public static int submitDeviceLost;
    public static int submitWaitFailures;
    /** Bootstrap-path queue waits that did not confirm completion, and were therefore not treated as done. */
    public static int queueWaitFailures;
    /** Device-wide waits at teardown that did not confirm idle, so quarantined resources were deliberately held. */
    public static int deviceIdleFailures;
    /**
     * Readback buffers whose submission was not confirmed complete.
     * <p>
     * A fence wait that times out (or cannot confirm completion) quarantines the command buffer, because the queue may
     * still be reading the buffer it was copying into. The readback path's own buffer and memory are referenced by that
     * recording too, so freeing them in a finally block - which audit #364 found - is a use-after-free on a live
     * submission. They are held here and released at teardown instead, after the device-wide wait.
     */
    private final java.util.List<long[]> quarantinedReadbackBuffers =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    /** How many readback buffers were quarantined rather than freed, for the verification line. */
    private long readbackQuarantines;

    /** Hands a readback buffer and its memory to the teardown list instead of freeing them. */
    public void quarantineReadbackBuffer(long buffer, long memory, String why) {
        if (buffer == 0L && memory == 0L) {
            return;
        }
        this.readbackQuarantines++;
        this.quarantinedReadbackBuffers.add(new long[] {buffer, memory});
        if (this.readbackQuarantines <= 4) {
            System.out.println("[Celtium] CELTIUM_READBACK_QUARANTINED buffer=" + Long.toHexString(buffer)
                    + " memory=" + Long.toHexString(memory) + " why=" + why);
        }
    }

    /** Frees a readback allocation whose binding failed, where nothing can reference it because nothing was submitted. */
    public void releaseReadbackAllocation(long buffer, long memory) {
        if (buffer != 0L) {
            vkDestroyBuffer(this.device, buffer, null);
        }
        if (memory != 0L) {
            VulkanContext.freeMemory(this.device, memory);
        }
    }

    /** {@return how many readback buffers were quarantined instead of freed}. */
    public long readbackQuarantines() {
        return this.readbackQuarantines;
    }

    /** Frees the quarantined readback buffers. Called at teardown, after the device-wide wait. */
    private void releaseQuarantinedReadbackBuffers() {
        for (long[] entry : this.quarantinedReadbackBuffers) {
            if (entry[0] != 0L) {
                vkDestroyBuffer(this.device, entry[0], null);
            }
            if (entry[1] != 0L) {
                VulkanContext.freeMemory(this.device, entry[1]);
            }
        }
        this.quarantinedReadbackBuffers.clear();
    }

    /** Command buffers whose submission timed out: never freed while the queue may still own them. */
    private final java.util.Set<VkCommandBuffer> quarantinedCommandBuffers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Cleared when a submission times out: a device that stopped making progress gets no further work. */
    private volatile boolean usable = true;
    /**
     * How many times {@link #submitWithSemaphores} ran, and how many of those actually carried a wait.
     * <p>
     * This exists because the validation messages name the acquire semaphore as "not waited on" while the calling code
     * passes it, and the question is whether the call this code builds is the call the driver receives. A counter is
     * used rather than a print: an earlier attempt at printing from here coincided with a run whose terrain passes all
     * errored, and while that link is unproven, a counter costs nothing and cannot perturb anything.
     */
    public static int submitWithSemaphoresCalls;
    /**
     * Every {@code vkQueueSubmit} the process issues, from every path: the per-stage semaphore packets (the owned
     * target's beginFrame signal and endFrame consume), the work submissions, the deferred submissions and the
     * uploads all land here, so "submissions per frame" is one counter instead of a sum over call sites. The
     * semaphore-skip change is judged on this number dropping by the two packets it removes per stage.
     */
    public static int queueSubmitCalls;
    private VkPhysicalDeviceMemoryProperties memoryProperties;
    private long debugMessenger = VK_NULL_HANDLE;

    // Cached host-visible staging buffer. VulkanBuffer and VulkanArena stream through it instead of allocating a
    // transfer buffer per call. The mapping is kept for the lifetime of the buffer, which is legal because the
    // memory is host-coherent and every upload waits for the queue to go idle before it returns.
    private long stagingBuffer = VK_NULL_HANDLE;
    private long stagingMemory = VK_NULL_HANDLE;
    private long stagingMappedAddress;
    /**
     * Ring slots and their size.
     * <p>
     * Sixteen slots rather than eight, from a measurement of the phases themselves: on the (g) leg shape a frame
     * stages 35 copies and 6.65 MB ({@code uploadCopiesBatched=11116}, {@code uploadCopyBytesTotal=2116239008} over
     * 318 frames), so an 8 MiB ring wrapped every 1.2 frames and the reservation path still waited on the oldest
     * deferred submission 211 times - {@code uploadPhaseMicros=[reserve=605028 copy=311460 ...]} puts 1.90 ms of every
     * frame in the reservation path, nearly all of it those waits. Sixteen slots give the ring a ~2.4-frame wrap, so a
     * batch's fence has signalled by the time its slot is wanted again. The memory cost is bounded and reported:
     * {@code uploadRingBytes} goes from 8 MiB to 16 MiB of host-visible staging.
     * <p>
     * The reuse rule is unchanged and is the point of the ring: a slot is free only after the completion value of
     * the submission that read it has been reached ({@link #celtium$pollDeferredSubmits()}), never because a
     * frame number moved.
     */
    private static final int UPLOAD_SLOT_COUNT = 16;
    private static final long UPLOAD_SLOT_BYTES = 1L << 20;
    private long uploadRingBuffer = VK_NULL_HANDLE;
    private long uploadRingMemory = VK_NULL_HANDLE;
    private long uploadRingMapped;
    private long uploadRingCapacity;
    private final long[] uploadSlotCursor = new long[UPLOAD_SLOT_COUNT];
    /**
     * {@code true} while an in-flight batch's copies still read the slot. Set when {@link #flushUploads()} hands a
     * batch to the queue, cleared when that batch's fence is confirmed - by a poll, by the ring-pressure wait, or by
     * {@link #celtium$settleDeferredSubmits()}. The slot's cursor is already zeroed at submit, so the flag is the whole
     * gate: {@link #celtium$writableSlot} never gives the CPU a slot the queue is still reading.
     */
    private final boolean[] uploadSlotFree = new boolean[UPLOAD_SLOT_COUNT];
    {
        // false is "owned by a batch still in flight": a slot starts free and only a submitted batch closes it.
        java.util.Arrays.fill(this.uploadSlotFree, true);
    }
    private int uploadSlot = -1;

    /**
     * One submission the queue owns but the CPU has not confirmed: its fence, its command buffer, and a bitmask of the
     * upload-ring slots its copies read (0 for submissions that touched none).
     * <p>
     * This queue is what replaces "submit, then wait, then free" on the render thread. A submission that only the GPU
     * needs to order - an upload batch, a texture patch, the present blit - is tracked here rather than waited on the
     * spot; its completion value on the frame timeline is what releases the resources it still reads (the command
     * buffer, the ring slots), and that happens at the first {@link #celtium$pollDeferredSubmits()} or bounded wait
     * that sees the timeline counter reach it. Confirmed order is submission order, because the queue executes
     * submissions in order, so polls only ever free from the head.
     */
    private static final class DeferredSubmit {
        private VkCommandBuffer commandBuffer;
        /** The frame-timeline value this submission's batch signals on completion; 0 until it joins a batch. */
        private long completionValue;
        private long uploadSlotMask;
        /** Wait/signal sets carried until the batch flush records them into its VkSubmitInfo array. */
        private long[] waitSemaphores;
        private int[] waitStages;
        private long[] signalSemaphores;
        /** The batch this submission's completion value belongs to. */
        private SharedBatch batch;
        /**
         * {@code >= 0} marks a frame-slot command buffer: it belongs to the frame coordinator's own pool and is
         * NOT freed on reclaim - the slot stays a reusable resource, and {@link #slotReclaim} performs the
         * completion bookkeeping the slot fence would have made. {@code -1} is the pool-owned buffer the original
         * deferred path frees.
         */
        private int frameSlot = -1;
        /**
         * Runs at reclaim (and at batched-submit failure) with the lost-device flag, exactly once, instead of the
         * pool free: the caller's per-slot confirmation - the thing the armed slot fence used to stand for.
         */
        private java.util.function.Consumer<Boolean> slotReclaim;
    }

    /**
     * One batched {@code vkQueueSubmit}: a single frame-timeline value signalled by the call's last chunk and
     * shared by every entry the batch carried. {@link #pending} counts unreclaimed entries, not pending
     * submissions: it starts at the batch's entry count and the reclaim decrements; the holder returns to the
     * pool when the count reaches zero, which is bookkeeping only - a timeline value, unlike the fence this
     * replaced, needs no reset, no pooling and no destruction.
     */
    private static final class SharedBatch {
        private long completionValue;
        private int pending;
        /** The creating context's serial for this batch (item 529, instrument I3). */
        private long serial;
        /** {@code System.nanoTime()} of the successful queue submission; 0 while the batch is unsubmitted (I4). */
        private long submittedNanos;
        /** The batch's composition at submission: frame-slot entries, plain deferred entries, semaphore carriers (I3). */
        private int frameEntries;
        private int deferredEntries;
        private int semaphoreEntries;
    }

    /**
     * Submissions queued for the next {@link #celtium$flushBatchedSubmits()} call, in logical submission order.
     * Each entry is also already in {@link #deferredSubmits} so await/poll lookups find it by completion value;
     * its value is the batch's shared one, signalled only when the flush submits the whole array - which is why
     * every wait and every poll flushes first: waiting on an unsignalled value would read as a stall that is
     * really just "not submitted yet".
     */
    private final java.util.ArrayList<DeferredSubmit> pendingBatchedSubmits = new java.util.ArrayList<DeferredSubmit>();
    private final java.util.ArrayList<DeferredSubmit> flushBatchScratch = new java.util.ArrayList<DeferredSubmit>();
    private SharedBatch pendingBatch;
    /** Serial of the most recent batch this context created; the next takes the next value (item 529, I3). */
    private long batchSerialSequence;
    /** Batched deferred submits are unconditional: every deferred submit joins the batch. */
    /** Lab switch: {@code -Dceltium.vulkan.pollUploadsAlways=true} polls in flushUploads even with nothing to copy. */
    static final boolean POLL_UPLOADS_ALWAYS = Boolean.getBoolean("celtium.vulkan.pollUploadsAlways");
    private static final boolean BATCHED_SUBMITS = true;
    private int batchedFlushes;
    private int batchedSubmissions;

    /**
     * The submissions currently owned by the queue, oldest first. Every bounded wait and every poll reclaims from the
     * head: a completion value the timeline has not reached keeps every newer entry pending too, which is the same
     * ordering guarantee a full drain had, expressed per entry instead of per device.
     */
    private final java.util.ArrayDeque<DeferredSubmit> deferredSubmits = new java.util.ArrayDeque<DeferredSubmit>();
    /** Reclaimed entries wait here for the next submission; the fields are cleared before an entry returns. */
    private final java.util.ArrayDeque<DeferredSubmit> deferredEntryPool = new java.util.ArrayDeque<DeferredSubmit>();
    private static final int DEFERRED_ENTRY_POOL_CAP = 256;
    /** Reclaimed SharedBatch holders, avoiding per-flush allocation. */
    private final java.util.ArrayDeque<SharedBatch> sharedBatchPool = new java.util.ArrayDeque<SharedBatch>();
    private static final int SHARED_BATCH_POOL_CAP = 32;
    private int deferredSubmitsOutstanding;
    private int deferredSubmitsPeak;
    private long deferredReclaims;
    private long deferredWaits;
    private long deferredWaitMillis;
    private long deferredWaitWorstMillis;
    /**
     * Deferred submissions still owned by the queue at teardown. A run that ended with them unconfirmed held onto
     * their command buffers and fences deliberately - the same rule {@link #quarantinedCommandBuffers} already states
     * for timed-out waits, applied to resources a run simply stopped waiting for.
     */
    private int deferredHeldAtTeardown;
    /**
     * Every copy this frame has recorded but not submitted.
     * <p>
     * The destination is a {@link VulkanBuffer} reference, not a handle, because the handle can be replaced between
     * the record and the flush: the arena grows its buffer when a mesh does not fit, and {@code ensureCapacity}
     * destroys the old {@code VkBuffer}. Resolving at flush time is what makes that transparent instead of dangling
     * (the first attempt recorded the handle and lost the device: plan.md 0.224).
     */
    private final java.util.List<PendingUpload> pendingUploads = new java.util.ArrayList<PendingUpload>();
    private final java.util.ArrayDeque<PendingUpload> pendingUploadPool = new java.util.ArrayDeque<PendingUpload>();
    private static final int PENDING_UPLOAD_POOL_CAP = 1024;
    private java.util.Set<String> cachedDeviceExtensionNames;

    /** One pending copy: where it goes, where its bytes already are, and how many. */
    private static final class PendingUpload {
        private VulkanBuffer target;
        private long destinationOffset;
        private long stagingOffset;
        private long size;

        private PendingUpload(VulkanBuffer target, long destinationOffset, long stagingOffset, long size) {
            this.target = target;
            this.destinationOffset = destinationOffset;
            this.stagingOffset = stagingOffset;
            this.size = size;
        }

        private void set(VulkanBuffer target, long destinationOffset, long stagingOffset, long size) {
            this.target = target;
            this.destinationOffset = destinationOffset;
            this.stagingOffset = stagingOffset;
            this.size = size;
        }
    }

    private PendingUpload obtainPendingUpload(VulkanBuffer target, long destinationOffset, long stagingOffset,
            long size) {
        PendingUpload entry = this.pendingUploadPool.pollFirst();
        if (entry == null) {
            return new PendingUpload(target, destinationOffset, stagingOffset, size);
        }
        entry.set(target, destinationOffset, stagingOffset, size);
        return entry;
    }

    private void recyclePendingUploads() {
        for (int i = 0, n = this.pendingUploads.size(); i < n; i++) {
            PendingUpload entry = this.pendingUploads.get(i);
            entry.target = null;
            if (this.pendingUploadPool.size() < PENDING_UPLOAD_POOL_CAP) {
                this.pendingUploadPool.addLast(entry);
            }
        }
        this.pendingUploads.clear();
    }
    private long uploadCopiesBatched;
    private long uploadCopiesFlushed;
    private long uploadFlushes;
    private long uploadFlushMillis;
    private long uploadFlushWorstMillis;
    private long uploadFallbackCopies;
    private long uploadDroppedCopies;
    /** Flushes caused by the ring filling mid-frame rather than by a submission boundary. */
    private long uploadEarlyFlushesForRingPressure;
    /**
     * Bounded waits on the oldest outstanding batch paid while reserving a slot, under the deferred shape: the only
     * GPU wait the upload path still makes, and only when all {@link #UPLOAD_SLOT_COUNT} slots belong to batches
     * whose fences have not yet been confirmed.
     */
    private long uploadRingPressureWaits;
    /** Ring slots freed by a non-blocking poll of the deferred queue rather than by a wait. */
    private long uploadRingPressurePollsReclaimed;
    // The upload path's own phases, so the bracket it lands in can be taken apart per frame rather than guessed at.
    // Counting and timing only: no ordering, no ownership, nothing about when a copy runs changes.
    private long uploadReserveNanos;
    /**
     * The two parts of the reserve bracket that are not booking cost, timed separately so the bracket can be
     * split after the fact (item 363 caveat 2, ITEM363-AUDIT §2.6): the one-time ring creation inside the first
     * reserve, and the ring-pressure waits (the early flush, the deferred-submit poll and the fence waits) that
     * the bracket absorbs when the ring is full. {@code reserveBook} is derived, never timed directly:
     * {@code uploadReserveNanos - uploadRingInitNanos - uploadReserveWaitNanos}.
     */
    private long uploadRingInitNanos;
    private long uploadReserveWaitNanos;
    private long uploadCopyNanos;
    private long uploadCopyBytes;
    /** Upload copies and bytes by producer: 0 = VulkanArena (terrain/immediate meshes), 1 = VulkanBuffer. */
    private final long[] uploadCopiesByKind = new long[2];
    private final long[] uploadBytesByKind = new long[2];
    private long uploadPollNanos;
    private long uploadRecordNanos;
    private long uploadSubmitNanos;
    /** One-line-per-flush trace, for reading what a flush cost without a profiler. */
    private static final boolean UPLOAD_TRACE = Boolean.getBoolean("celtium.vulkan.uploadTrace");
    /** Copies whose destination was destroyed before the flush: nothing reads it, so the copy is skipped. */
    private long uploadSkippedDeletedDestinations;
    private long stagingCapacityBytes;

    /**
     * Context generations across the process: {@link #create(String)} takes the next value for each context it
     * makes (item 529, instrument I6). The crash breadcrumb ring is static and outlives a renderer reload, so
     * the records a context leaves carry its generation - without it, a reload's new context would read the old
     * one's operations as its own.
     */
    private static final java.util.concurrent.atomic.AtomicLong CONTEXT_GENERATIONS =
            new java.util.concurrent.atomic.AtomicLong();
    /** This context's generation, from {@link #CONTEXT_GENERATIONS}; 0 until {@link #create(String)} assigns it. */
    private long generation;

    /**
     * The most recent device-loss report in this process, in the form {@link #celtium$deviceCreationFailure}
     * quotes (item 529, candidate (a)). Static because the reader is a <em>successor</em> context: after a
     * loss, a renderer reload creates a new context whose {@code vkCreateDevice} refusal is the downstream
     * of the loss the old context reported, and the two records used to live in different files. Written
     * only by {@link #celtium$lostDevice(String)}, the one funnel every reported loss passes through, so a
     * loss nobody reported is never claimed here.
     */
    private static volatile String lastDeviceLossReport;

    private VulkanContext() {
    }

    /**
     * Reports whether the LWJGL Vulkan bindings and a Vulkan 1.2 (or newer) loader are present.
     * <p>
     * This is deliberately cheap and never throws: a missing binding class, a missing native loader and a too old
     * loader all report {@code false}. It does not look for a matching physical device, because that needs an
     * instance; {@link #create(String)} does that and reports the reason when it fails.
     */
    public static boolean isAvailable() {
        return availabilityProblem() == null;
    }

    /**
     * Returns null when the Vulkan loader supports at least 1.2, or a bounded,
     * actionable diagnostic. This deliberately does not create a Vulkan instance.
     * A missing Java binding and a broken native loader must not produce the same
     * misleading "bindings unavailable on classpath" error.
     */
    public static String availabilityProblem() {
        try {
            int version = VK.getInstanceVersionSupported();
            if (version >= VK12.VK_API_VERSION_1_2) {
                return null;
            }
            return "Vulkan loader API " + ((version >>> 22) & 0x7f) + "."
                    + ((version >>> 12) & 0x3ff) + " is below the required Vulkan 1.2";
        } catch (NoClassDefFoundError error) {
            return "LWJGL Vulkan Java binding class missing: " + error;
        } catch (RuntimeException | LinkageError error) {
            return "Vulkan loader/binding initialization failed: " + error.getClass().getName()
                    + (error.getMessage() == null ? "" : ": " + error.getMessage());
        }
    }

    /**
     * Creates the Vulkan instance, picks the physical device and creates the logical device, the queue and the command
     * pool. Render thread only. There is no OpenGL context to match against, so the device is selected by the
     * renderer's own requirements (device type, API version, queue families) rather than by a GL UUID.
     *
     * @param applicationName application name reported to the driver; must not be null or empty
     * @throws IllegalStateException when Vulkan is unusable: no bindings or loader, a loader older than 1.2, no
     *                               suitable physical device, missing
     *                               {@code VK_KHR_external_memory_fd}/{@code VK_KHR_external_semaphore_fd}, no
     *                               graphics+transfer queue family, or a failed object creation
     */
    public static VulkanContext create(String applicationName) {
        if (applicationName == null || applicationName.isEmpty()) {
            throw new IllegalArgumentException("applicationName must not be null or empty");
        }
        requireLoader();
        GlDeviceIdentity openGl = inspectOpenGl();
        if (openGl != null && openGl.deviceUuids.isEmpty()) {
            throw new IllegalStateException("The current OpenGL context does not expose device UUIDs"
                    + " (GL_EXT_memory_object is missing), so no Vulkan device can be matched to the GL GPU.");
        }
        if (openGl == null) {
            // No identity at all: there is nothing to match and no interop to set up. This is the no-bridge configuration, and
            // it must fail with a reason rather than with a NullPointerException - which is exactly what it did when the flag
            // that exercises this path was left in the run script for two rounds.
            System.out.println("[Celtium] proceeding with no OpenGL identity: no device matching and no interop bridge");
        }
        VulkanContext context = new VulkanContext();
        context.generation = CONTEXT_GENERATIONS.incrementAndGet();
        try {
            context.initialize(applicationName, openGl);
        } catch (RuntimeException | LinkageError error) {
            // Never leave half a device behind: a failed create() must be as if it had never been called.
            context.delete();
            if (error instanceof IllegalStateException) {
                throw (IllegalStateException) error;
            }
            throw new IllegalStateException("Vulkan device creation failed: " + describe(error), error);
        }
        return context;
    }

    /**
     * {@return whether the logical device was created with this extension}.
     * <p>
     * The swapchain functions are device extensions: enabling the surface extension on the instance without this is
     * what made the first probes fail inside LWJGL's function-pointer check.
     */
    public boolean hasDeviceExtension(String name) {
        if (name == null || this.device == null || this.physicalDevice == null) {
            return false;
        }
        return celtium$cachedDeviceExtensions().contains(name);
    }

    /** {@return whether the physical device offers this extension}, enabled or not; false when it cannot be asked. */
    private boolean celtium$deviceOffers(String name) {
        if (name == null || this.physicalDevice == null) {
            return false;
        }
        try {
            return celtium$cachedDeviceExtensions().contains(name);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private java.util.Set<String> celtium$cachedDeviceExtensions() {
        java.util.Set<String> cached = this.cachedDeviceExtensionNames;
        if (cached != null) {
            return cached;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            cached = deviceExtensionNames(stack, this.physicalDevice);
            this.cachedDeviceExtensionNames = cached;
            return cached;
        }
    }

    /**
     * {@return whether the instance was created with this extension}.
     * <p>
     * Surface extensions such as {@code VK_KHR_wayland_surface} are instance extensions, so the swapchain asks here
     * rather than guessing from the platform name.
     */
    public boolean hasInstanceExtension(String name) {
        return name != null && this.instance != null && this.enabledInstanceExtensions.contains(name);
    }

    /** The Vulkan instance that owns the device. */
    public VkInstance instance() {
        return this.instance;
    }

    /** The physical device matched to the GL GPU. */
    public VkPhysicalDevice physicalDevice() {
        return this.physicalDevice;
    }

    /** The logical device. */
    public VkDevice device() {
        return this.device;
    }

    /** The single queue taken from the graphics+transfer family. */
    public VkQueue queue() {
        celtium$awaitPresenter();
        return this.queue;
    }

    /** Index of the queue family the queue was taken from. */
    public int queueFamilyIndex() {
        return this.queueFamilyIndex;
    }

    /** {@return the frame timeline, the context's one timeline semaphore and its value ledger}. */
    public FrameTimeline frameTimeline() {
        return this.frameTimeline;
    }

    /**
     * Waits for the frame timeline to reach {@code value}, returning the raw {@code VkResult}.
     * <p>
     * The wait sites that must tell success, timeout and device loss apart (the synchronous submit paths, whose
     * cleanup differs per outcome - audit A02/B04) call this rather than the timeline's own boolean await, so the
     * four outcomes stay distinct here exactly as they were when the object waited on was a fence.
     */
    public int celtium$awaitTimeline(long value, long timeoutNanos) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSemaphoreWaitInfo wait = VkSemaphoreWaitInfo.calloc(stack).sType$Default()
                    .semaphoreCount(1)
                    .pSemaphores(stack.longs(this.frameTimeline.semaphore()))
                    .pValues(stack.longs(value));
            return VK12.vkWaitSemaphores(this.device, wait, timeoutNanos);
        }
    }

    /**
     * {@return the device-local slab pool serving buffers whose memory requirements carry
     * {@code memoryTypeBits}}, created on first use.
     * <p>
     * The pool's memory type is the one {@link #memoryTypeIndex} picks from the caller's own requirement bits,
     * so a slab is only ever bound to a buffer that names the pool's type. There is no fallback to an allocation
     * of the buffer's own: the new architecture has no per-buffer {@code vkAllocateMemory} to fall back to, and
     * a device with no device-local type in the bits fails in {@code memoryTypeIndex}, as it did before.
     */
    public SlabAllocator celtium$deviceLocalSlabs(int memoryTypeBits) {
        int type = memoryTypeIndex(memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT,
                "the device-local slab pool");
        SlabAllocator allocator = this.deviceLocalSlabsByType.get(Integer.valueOf(type));
        if (allocator == null) {
            allocator = new SlabAllocator(this, type, SlabAllocator.DEFAULT_SLAB_SIZE, 8, false);
            this.deviceLocalSlabsByType.put(Integer.valueOf(type), allocator);
        }
        return allocator;
    }

    /**
     * {@return the host-visible slab pool serving host buffers whose memory requirements carry
     * {@code memoryTypeBits}}, created on first use. The pool's type is the host-visible coherent type
     * {@link #memoryTypeIndex} picks from the caller's own requirement bits, exactly as
     * {@link VulkanHostBuffer} picked it for its private allocation before. Ordinary slabs are capped at
     * 8 per type, so the live {@code vkAllocateMemory} count for all host buffers combined is bounded by
     * the slab cap (plus one dedicated slab per live oversized &gt;64 MiB request), not by the buffer count.
     */
    public SlabAllocator celtium$hostVisibleSlabs(int memoryTypeBits) {
        int type = memoryTypeIndex(memoryTypeBits,
                VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                "the host-visible slab pool");
        SlabAllocator allocator = this.hostVisibleSlabsByType.get(Integer.valueOf(type));
        if (allocator == null) {
            allocator = new SlabAllocator(this, type, SlabAllocator.DEFAULT_SLAB_SIZE, 8, true);
            this.hostVisibleSlabsByType.put(Integer.valueOf(type), allocator);
        }
        return allocator;
    }

    /** {@return live slab count across the host-visible pools, for the host-buffer census}. */
    public int celtium$hostVisibleSlabCount() {
        int total = 0;
        for (SlabAllocator allocator : this.hostVisibleSlabsByType.values()) {
            total += allocator.liveSlabCount();
        }
        return total;
    }

    /**
     * Makes slab ranges whose deferred releases the frame timeline has already passed reusable again, by
     * asking every pool to collect against the timeline's completed value. Two drive points: the retired
     * buffer drain ({@link #celtium$destroyRetiredBuffers()}) and every frame start, because collection that
     * only runs when a retired buffer happens to drain lets per-frame staging churn hold whole slabs hostage
     * for as long as the drain stays uncalled. A failed counter read simply defers collection to the next
     * drive point.
     */
    public void celtium$collectSlabReleases() {
        if (this.frameTimeline == null
                || (this.deviceLocalSlabsByType.isEmpty() && this.hostVisibleSlabsByType.isEmpty())) {
            return;
        }
        long completed;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer value = stack.mallocLong(1);
            if (VK12.vkGetSemaphoreCounterValue(this.device, this.frameTimeline.semaphore(), value)
                    != VK_SUCCESS) {
                return;
            }
            completed = value.get(0);
        }
        for (SlabAllocator allocator : this.deviceLocalSlabsByType.values()) {
            allocator.collectCompleted(completed);
        }
        for (SlabAllocator allocator : this.hostVisibleSlabsByType.values()) {
            allocator.collectCompleted(completed);
        }
    }

    /**
     * Hands a slab-backed buffer to the deferred list, the slab form of {@link #celtium$retireBuffer(long, long,
     * String)}: the buffer is destroyed and its sub-allocation released at the same safe point, instead of a
     * {@code vkFreeMemory} the slab must never receive.
     */
    public void celtium$retireSlabBuffer(long handle, SlabAllocator.SlabAllocation allocation, String site) {
        if (handle == VK_NULL_HANDLE) {
            return;
        }
        if (allocation == null) {
            // No slab range to return (a create that failed before its allocation): the handle alone is
            // retired through the raw list, whose drain destroys it without freeing any memory.
            celtium$retireBuffer(handle, VK_NULL_HANDLE, site);
            return;
        }
        this.retiredSlabBuffers.add(new Object[] {Long.valueOf(handle), allocation});
        if (this.retiredBufferSites.size() < 8) {
            this.retiredBufferSites.add(site);
            System.out.println("[Celtium] CELTIUM_BUFFER_RETIRED site=" + site + " slab=true outstanding="
                    + this.retiredSlabBuffers.size());
        }
    }

    /** {@return the device's 2D image dimension limit (16384 when it has not been queried)}. */
    public int maxImageDimension2D() {
        return this.maxImageDimension2D > 0 ? this.maxImageDimension2D : 16384;
    }

    /** Driver-reported name of the matched device, for diagnostics. */
    public String deviceName() {
        return this.deviceName;
    }

    /** Lower-case hex form of the matched device UUID, the same form GL reports. */
    public String deviceUuid() {
        return this.deviceUuid;
    }

    /**
     * Allocates a command buffer from the context's pool and starts recording it with
     * {@code VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT}.
     * <p>
     * Render thread only. Every buffer returned here must be handed to {@link #endCommands(VkCommandBuffer)};
     * this is the bootstrap submit path, not a reusable frame command buffer.
     */
    public VkCommandBuffer beginCommands() {
        requireUsable();
        // Loading a large atlas can submit thousands of texture patches without presenting a frame. Frame-boundary
        // polling therefore cannot bound the number of command buffers (and their driver-side state) retained here.
        // First reclaim completed submissions; if the queue is still full, wait for its oldest submission before
        // allocating another buffer. The wait also flushes an unarmed batched submission before inspecting its fence.
        if (this.idleCommandBuffers.isEmpty() && this.deferredSubmitsOutstanding >= MAX_DEFERRED_COMMAND_BUFFERS) {
            celtium$pollDeferredSubmits();
            if (this.idleCommandBuffers.isEmpty() && this.deferredSubmitsOutstanding >= MAX_DEFERRED_COMMAND_BUFFERS) {
                celtium$awaitOldestDeferredSubmit("command buffer pressure");
                celtium$pollDeferredSubmits();
            }
        }
        // The driver may hand back a command buffer whose memory a previous submission is still reading, so the
        // queue is drained before a new one is allocated. Validation flags the alternative as a pending-state
        // reuse, and a half-rewritten command stream is exactly the kind of fault that makes a shader read the
        // wrong data. This is deliberately bootstrap quality; a real frame uses a small pool of buffers and fences.
        // BH-27: this used to drain the whole queue before allocating a command buffer, because a previous
        // submission might still be reading one. That is no longer needed: every buffer on this path is freed only
        // after its own submission's fence has signalled (endCommands), and the pool is never reset, so a new
        // allocation cannot disturb a buffer in flight. The queue-wide wait is gone; bootstrapQueueWaits now counts
        // only the waits that remain (shutdown and rebuild paths).
        VkCommandBuffer commandBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            commandBuffer = this.idleCommandBuffers.pollLast();
            if (commandBuffer == null) {
                VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                        .commandPool(this.commandPool)
                        .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                        .commandBufferCount(1);
                PointerBuffer handle = stack.mallocPointer(1);
                check(vkAllocateCommandBuffers(this.device, allocate, handle), "vkAllocateCommandBuffers");
                commandBuffer = new VkCommandBuffer(handle.get(0), this.device);
                commandBufferAllocations++;
            } else {
                commandBufferReuses++;
            }
            VkCommandBufferBeginInfo begin = VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            int result = vkBeginCommandBuffer(commandBuffer, begin);
            if (result != VK_SUCCESS) {
                // The buffer is already allocated, so it must be freed before the failure is reported.
                vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
                check(result, "vkBeginCommandBuffer");
            }
        }
        // A one-shot recording joins the same layout discipline as a frame recording (audit N05): a shared
        // image's transition noted into this buffer stays pending until the submission carrying it is accepted -
        // endCommands commits after its submit, and a deferred buffer is committed when its batch flush submits
        // it. Without the token, noteForeignTransition's unbound fallback promoted a merely recorded layout to
        // fact at record time, and a pair operation running on a one-shot buffer (a chain-pair bootstrap clear,
        // a texture copy) could declare a shared depth image attached while it still sat in the read layout -
        // the pack terrain route's VUID-vkCmdDraw-None-09600, where the same shared depth alternates between
        // the frame recording and one-shot buffers several times a frame.
        VulkanGlTexture.beginLayoutRecording(commandBuffer);
        return commandBuffer;
    }

    /**
     * Command buffers whose submission has completed, kept for the next {@link #beginCommands()} instead of freed.
     * <p>
     * Every one-shot recording used to allocate a buffer and free it after its fence. Measured on Enigmatica 2 Expert's
     * loading screen (AMD RX 6400, RADV): {@code vkAllocateCommandBuffers} was the largest single item on the client
     * thread, because thousands of texture uploads each paid a fresh driver-side command buffer. The pool carries
     * {@code RESET_COMMAND_BUFFER_BIT}, so {@code vkBeginCommandBuffer} resets a recycled buffer implicitly; a buffer only
     * enters this list once nothing can still execute it.
     */
    private final java.util.ArrayDeque<VkCommandBuffer> idleCommandBuffers = new java.util.ArrayDeque<VkCommandBuffer>();
    private static final int IDLE_COMMAND_BUFFER_CAP = 256;
    private static final int MAX_DEFERRED_COMMAND_BUFFERS = 128;
    static long commandBufferAllocations;
    static long commandBufferReuses;

    /** Returns a completed one-shot buffer to {@link #idleCommandBuffers}, or frees it past the cap. */
    private void celtium$recycleCommandBuffer(VkCommandBuffer commandBuffer) {
        if (this.usable && this.idleCommandBuffers.size() < IDLE_COMMAND_BUFFER_CAP) {
            this.idleCommandBuffers.addLast(commandBuffer);
        } else {
            vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
        }
    }

    /**
     * Names a Vulkan object in validation messages, so a layout or lifetime error says which texture it is about
     * instead of only a handle. A no-op unless validation (and with it debug utils) is on; never throws.
     */
    public void celtium$nameObject(int objectType, long handle, String name) {
        if (this.debugMessenger == VK_NULL_HANDLE || handle == VK_NULL_HANDLE || this.device == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT info = org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT
                    .calloc(stack).sType$Default().objectType(objectType).objectHandle(handle)
                    .pObjectName(stack.UTF8(name));
            EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(this.device, info);
        } catch (RuntimeException | LinkageError ignored) {
            // A name is a diagnostic: a binding or loader without it must not affect rendering.
        }
    }

    /**
     * Frees a command buffer that was begun and will not be submitted.
     * <p>
     * The copy path's native route pre-checks everything it can before {@link #beginCommands()}, but a recording that
     * throws must not leave the buffer allocated: this is the same cleanup the failure paths inside this class do, and
     * it exists so a caller outside it can do the same rather than leak one allocation per refused copy.
     */
    public void celtium$abandonCommands(VkCommandBuffer commandBuffer) {
        if (commandBuffer != null) {
            vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
        }
    }

    /**
     * Ends and submits a command buffer, waiting on one semaphore and signalling another.
     * <p>
     * {@link #endCommands} submits with no semaphores and then drains the queue, which is what the one-shot uploads
     * want. A presentation needs more: the submission that modifies the acquired image must *wait* on the semaphore the
     * acquire signalled, and the presentation must wait on a signal from that submission instead. The validation layer
     * reports the alternative as "pSubmits[0] performs a layout transition on presentable VkImage ... but VkSemaphore
     * ... signaled by image acquire operation was not waited on".
     * <p>
     * The submission's timeline value is waited for, so the command buffer can be freed, but the queue is not
     * drained: the signal semaphore is handed to the caller's presentation, which waits on it.
     */
    public void submitWithSemaphores(VkCommandBuffer commandBuffer, long waitSemaphore, long signalSemaphore) {
        submitWithSemaphores(commandBuffer, new long[] {waitSemaphore}, new long[] {signalSemaphore});
    }

    /**
     * Ends and submits a command buffer waiting on several semaphores and signalling several.
     * <p>
     * The frame's presentation needs exactly this: it must wait both the window-system acquire and the GL-to-Vulkan
     * bridge semaphore - the shared image is only complete once GL has signalled - and it must signal the bridge's done
     * semaphore as well as its own, because GL waits on that one inside {@code endFrame}. Signalling only the
     * render-finished semaphore leaves GL waiting for a submission that never signalled it, which is the deadlock
     * the frame target's contract warns about.
     * <p>
     * Counts are set explicitly: LWJGL's {@code pWaitSemaphores} writes only the pointer, so a calloc'd struct waits on
     * nothing.
     */
    public void submitWithSemaphores(VkCommandBuffer commandBuffer, long[] waitSemaphores, long[] signalSemaphores) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        submitWithSemaphoresCalls++;
        // Deferred (batched) submissions logically precede this direct one; flushing first keeps logical and queue
        // order identical - without it a vkQueueSubmit here would jump ahead of entries the frame already submitted.
        celtium$flushBatchedSubmits();
        try {
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // The submission signals the frame timeline at a fresh value, and the bounded wait below waits
                // on that value through vkWaitSemaphores: the per-submission fence this path used to create,
                // wait on and destroy on every call is gone. The wait's VkResult carries the same outcome
                // vocabulary the fence wait did, so the three-way split below is unchanged in substance.
                long timelineValue = this.frameTimeline.nextValue();
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(commandBuffer));
                // Count first and fill arrays directly: this runs once per window (about 30 a frame), and the two
                // ArrayLists plus a boxed Long per semaphore were per-window garbage for a call that copies the values
                // into stack memory immediately.
                int waitCount = 0;
                for (long semaphore : waitSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        waitCount++;
                    }
                }
                if (waitCount > 0) {
                    // Explicit count, and one destination stage for each semaphore: LWJGL's pWaitSemaphores writes only
                    // the pointer. ALL_COMMANDS because the command buffer begins with a layout transition, which a wait
                    // whose stage mask does not cover it does not order against.
                    long[] waitHandles = new long[waitCount];
                    int[] stages = new int[waitCount];
                    int filled = 0;
                    for (long semaphore : waitSemaphores) {
                        if (semaphore != VK_NULL_HANDLE) {
                            waitHandles[filled] = semaphore;
                            stages[filled] = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                            filled++;
                        }
                    }
                    submit.waitSemaphoreCount(waitCount);
                    submit.pWaitSemaphores(stack.longs(waitHandles));
                    submit.pWaitDstStageMask(stack.ints(stages));
                }
                int signalCount = 0;
                for (long semaphore : signalSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        signalCount++;
                    }
                }
                // The frame timeline rides in the same signal array, last: this submission signals it at
                // timelineValue, and the bounded wait below waits on that value. The timeline submit info
                // carries only the values - the handles live in the submit info itself - and they pair
                // positionally with the signal array, so a caller binary semaphore pairs with 0 (its value is
                // ignored) and the timeline pairs with its value.
                long[] signalHandles = new long[signalCount + 1];
                long[] signalValues = new long[signalCount + 1];
                int filled = 0;
                for (long semaphore : signalSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        signalHandles[filled] = semaphore;
                        filled++;
                    }
                }
                signalHandles[filled] = this.frameTimeline.semaphore();
                signalValues[filled] = timelineValue;
                submit.pSignalSemaphores(stack.longs(signalHandles));
                VkTimelineSemaphoreSubmitInfo timelineInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                        .sType$Default()
                        .signalSemaphoreValueCount(signalCount + 1)
                        .pSignalSemaphoreValues(stack.longs(signalValues));
                submit.pNext(timelineInfo.address());
                VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, VK_NULL_HANDLE), "vkQueueSubmit(submitWithSemaphores)");
                // From here the queue owns the command buffer. The three outcomes are kept apart, because the
                // cleanup that is right for one is a validation error for another (audit A02):
                //
                //  1. a failure BEFORE the submit - the queue never saw it, so freeing the buffer is correct and
                //     is what the finally below does;
                //  2. the timeline reaches the value - the submission completed, so the same cleanup is correct;
                //  3. the wait times out - the submission may still be executing. Freeing its command buffer
                //     while the queue may still read it is exactly what
                //     VUID-vkFreeCommandBuffers-pCommandBuffers-00047 describes.
                //
                // The bounded wait itself stays (an unbounded wait on the present path is a hang with no message),
                // but a timeout no longer throws into the unconditional cleanup. The buffer is handed to a
                // quarantine list, the context marks itself unusable so no further work is submitted on a device
                // that has stopped making progress, and the failure is named.
                int waited = celtium$awaitTimeline(timelineValue, SUBMIT_TIMEOUT_NANOS);
                if (waited == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                    submitTimeouts++;
                    quarantine(commandBuffer);
                    commandBuffer = null;
                    this.usable = false;
                    throw new IllegalStateException("vkWaitSemaphores(submitWithSemaphores): the submission did not"
                            + " signal within " + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s. The device has stopped"
                            + " making progress: the submission's command buffer is quarantined (never"
                            + " reset, re-recorded, freed or destroyed while the queue may own it), the context is"
                            + " marked unusable, and no further work is submitted on it"
                            + " (submitWithSemaphores timeouts=" + submitTimeouts + ")");
                }
                // Audit B04. The three remaining outcomes are not one outcome, and the cleanup that is right for one
                // is a validation error for another (VUID-vkFreeCommandBuffers-pCommandBuffers-00047):
                //
                //   SUCCESS        the submission completed; the finally below is correct.
                //   DEVICE_LOST    the device is gone. The spec treats a lost device as SUCCESS-equivalent for
                //                  pending/in-use purposes, so nothing is still executing and the finally is correct
                //                  too. This must NOT be grouped with "the wait did not complete" - the audit names
                //                  that mistake explicitly ([R4]).
                //   anything else  OUT_OF_HOST_MEMORY, OUT_OF_DEVICE_MEMORY, UNKNOWN: the wait did not confirm
                //                  completion, so the queue may still own the buffer. It is quarantined exactly
                //                  as on a timeout and the finally must not release it.
                if (waited == VK_SUCCESS) {
                    submitCompletions++;
                    completedSubmissions++;
                } else if (waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                    submitDeviceLost++;
                    // SUCCESS-equivalent for reuse (audit B04): a lost device is not executing anything any more, so
                    // the submission is advanced in the completion epoch as well. Grouping it with "the wait did not
                    // complete" is the mistake the audit names, and it would hold every resource forever.
                    completedSubmissions++;
                    this.usable = false;
                    throw new IllegalStateException("vkWaitSemaphores(submitWithSemaphores) reported"
                            + " VK_ERROR_DEVICE_LOST: the submission is not going to complete because the device is"
                            + " gone. The command buffer is released by the caller's cleanup, which is"
                            + " correct here - a lost device is SUCCESS-equivalent for pending/in-use purposes, so the"
                            + " queue no longer owns it (submitWithSemaphores lostDevices=" + submitDeviceLost + ")");
                } else {
                    submitWaitFailures++;
                    quarantine(commandBuffer);
                    commandBuffer = null;
                    this.usable = false;
                    throw new IllegalStateException("vkWaitSemaphores(submitWithSemaphores) did not confirm completion"
                            + " and did not time out: result=" + waited + ". The queue may still own the submission, so"
                            + " its command buffer is quarantined rather than freed, and the context is"
                            + " marked unusable. This is not a lost device: only VK_ERROR_DEVICE_LOST is"
                            + " completion-equivalent for pending/in-use purposes"
                            + " (submitWithSemaphores waitFailures=" + submitWaitFailures + ")");
                }
            }
        } finally {
            if (commandBuffer != null) {
                vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
            }
        }
    }

    /**
     * Holds resources whose submission may still be executing, so nothing resets, re-records, frees or destroys them.
     * <p>
     * They are released only at {@link #delete()}, after a device-wide wait, which is the one place where "the queue
     * is finished with them" is a fact rather than a hope.
     */
    private void quarantine(VkCommandBuffer commandBuffer) {
        // Only the command buffer is held: completion is a timeline value now, and a value needs no holding -
        // it is a number the counter simply never reaches, not an object the queue may still own.
        this.quarantinedCommandBuffers.add(commandBuffer);
        QUARANTINED_COUNTS[0] = this.quarantinedCommandBuffers.size();
        QUARANTINED_COUNTS[1] = 0;
    }

    /** {@return the quarantined command buffers and fences still held, for the report}. */
    public static int[] quarantinedResourceCounts() {
        return new int[] {QUARANTINED_COUNTS[0], QUARANTINED_COUNTS[1]};
    }

    /** Live counts, updated by {@link #quarantine} - the sets themselves are per context, the report is static. */
    private static final int[] QUARANTINED_COUNTS = new int[2];

    /** {@return how many submissions timed out and left quarantined resources}, for the report. */
    public static int quarantinedSubmissions() {
        return submitTimeouts;
    }

    /**
     * {@return the wait-outcome matrix as one line}: how many waits confirmed completion, timed out, reported the
     * device lost, or returned something else - each a state with its own cleanup rule (audit B04).
     */
    public static String waitOutcomeSummary() {
        return "completions=" + submitCompletions
                + " timeouts=" + submitTimeouts
                + " lostDevices=" + submitDeviceLost
                + " unconfirmed=" + submitWaitFailures
                + " queueWaitFailures=" + queueWaitFailures
                + " deviceIdleFailures=" + deviceIdleFailures;
    }

    /** Per-caller count of command buffers that take the one-copy submit path, so the path can be attributed. */
    private final java.util.Map<String, Integer> submitReasons = new java.util.LinkedHashMap<String, Integer>();

    /**
     * {@code endCommands} with the caller named. The one-copy path submits and waits per call, so "which caller
     * issues them" is the question that decides what to fix; the name is recorded here rather than inferred from a
     * stack walk on the hot path.
     */
    public void endCommands(VkCommandBuffer commandBuffer, String reason) {
        Integer seen = this.submitReasons.get(reason);
        this.submitReasons.put(reason, Integer.valueOf(seen == null ? 1 : seen.intValue() + 1));
        endCommands(commandBuffer);
    }

    /** {@return the per-caller counts of the one-copy submit path} */
    public java.util.Map<String, Integer> submitReasons() {
        return this.submitReasons;
    }

    /**
     * Ends recording, submits the command buffer to the context queue, waits for the queue to go idle and frees the
     * command buffer. The wait is what makes the bootstrap-quality upload path safe: when this method returns, the
     * recorded copies have completed and the staging memory can be reused or destroyed.
     * <p>
     * Render thread only.
     */
    public void endCommands(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        // Batched deferred submits precede this bootstrap submission logically, so they go first physically. It
        // matters more than ordering: this path waits on its own timeline value, and a pending batch submitted
        // after it would still complete correctly - the flush simply keeps the queue's order the order callers
        // saw.
        celtium$flushBatchedSubmits();
        boolean releasedToQueue = true;
        try {
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // BH-27: the submit signals the frame timeline and this path waits on that value, not on the
                // whole queue. The queue-wide idle it used to do (twice per copy, once in beginCommands and once
                // here) drained every other submission in flight, so every upload and every present serialised
                // the renderer against the GPU. The timeline value is what makes freeing this command buffer
                // correct: only this submission has to complete, and the queue keeps running while it does.
                // (The timeline submit info carries only the value; the semaphore handle itself is in the
                // submit info's signal array.)
                long timelineValue = this.frameTimeline.nextValue();
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(commandBuffer))
                        .pSignalSemaphores(stack.longs(this.frameTimeline.semaphore()));
                VkTimelineSemaphoreSubmitInfo timelineInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                        .sType$Default()
                        .signalSemaphoreValueCount(1)
                        .pSignalSemaphoreValues(stack.longs(timelineValue));
                submit.pNext(timelineInfo.address());
                VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, VK_NULL_HANDLE), "vkQueueSubmit");
                        // The queue accepted this one-shot submission, so the layout transitions recorded into
                        // it are fact now - and only now (the recording was opened in beginCommands). This path
                        // waits on its own value below, so acceptance here is the exact point the frame path's
                        // submission bookkeeping commits at.
                        VulkanGlTexture.commitLayoutRecording(commandBuffer);
                    long waitStarted = System.nanoTime();
                    int waited = celtium$awaitTimeline(timelineValue, SUBMIT_TIMEOUT_NANOS);
                    celtium$recordBootstrapSubmitWait(waitStarted);
                    if (waited == VK_SUCCESS) {
                        bootstrapSubmitWaits++;
                        completedSubmissions++;
                    } else if (waited == VK_TIMEOUT) {
                        // A submission that does not complete within the bound is the stall the frame path already
                        // reports by name; here it means the command buffer must not be freed.
                        submitTimeouts++;
                        this.usable = false;
                        releasedToQueue = false;
                        quarantine(commandBuffer);
                        throw new IllegalStateException("vkWaitSemaphores(bootstrap submit) did not signal within "
                                + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s; the command buffer is quarantined"
                                + " rather than freed (submitTimeouts=" + submitTimeouts + ")");
                    } else if (waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                        submitDeviceLost++;
                        // The lost-device rule the deferred path states: after a loss, no handle recycling or
                        // destruction may be assumed. The command buffer is therefore not recycled - it is
                        // quarantined for teardown - and the loss is thrown so the caller stops instead of
                        // reusing the staging this copy was writing into.
                        releasedToQueue = false;
                        quarantine(commandBuffer);
                        throw celtium$lostDevice("vkWaitSemaphores(bootstrap submit)");
                    } else {
                        queueWaitFailures++;
                        this.usable = false;
                        releasedToQueue = false;
                        quarantine(commandBuffer);
                        throw new IllegalStateException("vkWaitSemaphores(bootstrap submit) returned " + waited
                                + "; the command buffer is quarantined rather than freed (queueWaitFailures="
                                + queueWaitFailures + ")");
                    }
            }
        } finally {
            if (releasedToQueue) {
                celtium$recycleCommandBuffer(commandBuffer);
            }
        }
    }

    // ------------------------------------------------------------------ deferred submissions

    /**
     * Submits {@code commandBuffer} with no semaphores and tracks it as outstanding instead of waiting for it.
     * {@return the frame-timeline completion value the caller may wait on later through
     * {@link #celtium$awaitDeferredSubmit(long, String)}}.
     * <p>
     * The CPU no longer blocks on the submission the queue just took: the completion value is what frees the
     * command buffer and releases the upload-ring slots the batch read ({@code uploadSlotMask}, 0 when it read
     * none), and that happens at the next {@link #celtium$pollDeferredSubmits()},
     * {@link #celtium$awaitOldestDeferredSubmit(String)} or {@link #celtium$settleDeferredSubmits()} that
     * confirms it - the OPT-04 epoch counts confirmed completions the same way a wait did. This is the path for
     * submissions only the GPU orders: the upload batch, texture patches and the present blit. The render
     * thread used to pay one full {@code vkWaitForFences} for each of them, and every such wait inherited
     * whatever the queue still had in flight - the serialisation the user's frame spikes show.
     * <p>
     * Render thread only.
     */
    public long celtium$submitDeferred(VkCommandBuffer commandBuffer, long uploadSlotMask) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer (deferred)");
        DeferredSubmit entry = takeDeferredEntry();
        entry.commandBuffer = commandBuffer;
        entry.uploadSlotMask = uploadSlotMask;
        entry.batch = celtium$currentBatch();
        entry.batch.pending++;
        entry.completionValue = entry.batch.completionValue;
        this.pendingBatchedSubmits.add(entry);
        this.deferredSubmits.add(entry);
        this.deferredSubmitsOutstanding++;
        if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
            this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
        }
        SubmitTrail.note(SubmitTrail.DEFERRED, entry.completionValue, uploadSlotMask);
        return entry.completionValue;
    }

    /**
     * Queues an ALREADY-ENDED frame-slot command buffer into the batched-submit list.
     * {@return the batch's shared completion value the caller records as the slot's stand-in}.
     * <p>
     * Batching is what keeps every frame recording off its own driver call: ~8-9 recordings per frame were
     * ~8-9 submits. The batch's one completion value stands for every entry, and
     * {@link DeferredSubmit#slotReclaim} runs the slot's completion bookkeeping at reclaim exactly once -
     * the same facts an armed per-slot fence used to publish. Order is preserved by entry order inside the
     * batch: entries flush in the sequence they were queued, which is what the per-call submits expressed.
     * <p>
     * {@link #celtium$submitDeferred}'s contract differs in two places deliberately: the buffer is not ended here
     * (the coordinator already ended it inside its own bookkeeping), and it is never freed on reclaim - a frame
     * slot's buffer belongs to the coordinator's pool and outlives the entry.
     * <p>
     * The buffer joins the batched deferred list; there is no per-call submit path.
     */
    public long celtium$submitDeferredFrame(VkCommandBuffer commandBuffer, long[] waitSemaphores, int[] waitStages,
            long[] signalSemaphores, int frameSlot, java.util.function.Consumer<Boolean> slotReclaim) {
        if (commandBuffer == null || frameSlot < 0 || slotReclaim == null) {
            throw new IllegalArgumentException("deferred frame submit needs a buffer, a slot and a reclaim callback");
        }
        requireUsable();
        DeferredSubmit entry = takeDeferredEntry();
        entry.commandBuffer = commandBuffer;
        entry.batch = celtium$currentBatch();
        entry.batch.pending++;
        entry.completionValue = entry.batch.completionValue;
        entry.waitSemaphores = waitSemaphores;
        entry.waitStages = waitStages;
        entry.signalSemaphores = signalSemaphores;
        entry.frameSlot = frameSlot;
        entry.slotReclaim = slotReclaim;
        this.pendingBatchedSubmits.add(entry);
        this.deferredSubmits.add(entry);
        this.deferredSubmitsOutstanding++;
        if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
            this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
        }
        return entry.completionValue;
    }

    /**
     * Frees a confirmed deferred submission: its command buffer goes back to the pool, and the upload-ring slots
     * it read become writable again. The submission's completion counts once in the OPT-04 epoch. No Vulkan
     * handle is touched for the completion itself: the batch's timeline value is a number, not an object, so
     * there is nothing to reset, pool or destroy - the fence lifecycle this method used to run (and the
     * vkResetFences on it that the 2026-10-29 Windows crash sits inside) has no counterpart here.
     */
    private void celtium$reclaimDeferred(DeferredSubmit entry, boolean lostDevice) {
        // On a lost device no handle recycling or destruction is safe to assume; the guard stays even though
        // the completion object itself no longer needs it.
        if (lostDevice || this.deviceLost) {
            throw celtium$lostDevice("reclaimDeferred");
        }
        if (entry.frameSlot >= 0) {
            // A slot-owned buffer is never freed: the slot's own pool keeps it for the next recording. The reclaim
            // is the completion event the slot fence stood for, so the callback runs the slot bookkeeping instead.
            if (entry.slotReclaim != null) {
                entry.slotReclaim.accept(Boolean.valueOf(lostDevice));
            }
        } else {
            celtium$recycleCommandBuffer(entry.commandBuffer);
        }
        entry.commandBuffer = null;
        entry.slotReclaim = null;
        if (entry.batch != null) {
            // The batch's count is the only lifecycle left: the holder returns to the pool when its last entry
            // reclaims, so a pooled holder never carries a stale count or a stale value into its next batch.
            SharedBatch completedBatch = entry.batch;
            entry.batch = null;
            completedBatch.pending--;
            if (completedBatch.pending == 0 && completedBatch != this.pendingBatch
                    && this.sharedBatchPool.size() < SHARED_BATCH_POOL_CAP) {
                completedBatch.completionValue = 0L;
                this.sharedBatchPool.addLast(completedBatch);
            }
        }
        entry.completionValue = 0L;
        long mask = entry.uploadSlotMask;
        if (mask != 0L) {
            for (int slot = 0; slot < UPLOAD_SLOT_COUNT; slot++) {
                if ((mask & (1L << slot)) != 0L) {
                    this.uploadSlotFree[slot] = true;
                }
            }
            entry.uploadSlotMask = 0L;
        }
        this.deferredSubmitsOutstanding--;
        this.deferredReclaims++;
        noteCompletionConfirmed(lostDevice);
        // The entry is done with: clear what it held so a pooled instance cannot pin a command buffer, a batch or a
        // semaphore array, then hand it back. The handles themselves were released above.
        entry.commandBuffer = null;
        entry.waitSemaphores = null;
        entry.waitStages = null;
        entry.signalSemaphores = null;
        entry.batch = null;
        entry.slotReclaim = null;
        entry.frameSlot = -1;
        if (this.deferredEntryPool.size() < DEFERRED_ENTRY_POOL_CAP) {
            this.deferredEntryPool.addLast(entry);
        }
    }

    /** {@return an entry from the pool, or a new one when it is empty}. */
    private DeferredSubmit takeDeferredEntry() {
        DeferredSubmit pooled = this.deferredEntryPool.pollFirst();
        return pooled != null ? pooled : new DeferredSubmit();
    }

    /** {@return the batch the next deferred submit joins, allocating its completion value on first use}. */
    private SharedBatch celtium$currentBatch() {
        if (this.pendingBatch == null) {
            SharedBatch batch = this.sharedBatchPool.pollFirst();
            if (batch == null) {
                batch = new SharedBatch();
            } else {
                batch.pending = 0;
                batch.submittedNanos = 0L;
                batch.frameEntries = 0;
                batch.deferredEntries = 0;
                batch.semaphoreEntries = 0;
            }
            // The value is allocated at creation, not at flush: every other submit path flushes the pending
            // batch before allocating its own value, so allocation order stays submission order and the
            // timeline's signals stay monotonic on the queue.
            batch.completionValue = this.frameTimeline.nextValue();
            batch.serial = ++this.batchSerialSequence;
            this.pendingBatch = batch;
        }
        return this.pendingBatch;
    }

    /**
     * Wall time inside the enforced submission of the deferred list. This is the path the frame statistics' earlier
     * counters did not see: the batched entries are enqueued elsewhere and only submitted here, so the profile put 55%
     * of the client thread's samples in vkQueueSubmit while the counted calls read a few hundred microseconds.
     */
    static long batchedSubmitNanos;
    static long batchedSubmitCalls;
    static long batchedFlushCalls;

    /** {@return the deferred submission's cost as one line}, for the frame statistics. */
    public static String batchedSubmitSummary() {
        long calls = batchedSubmitCalls;
        return "batchedSubmit[calls=" + calls
                + " totalMs=" + (batchedSubmitNanos / 1000000L)
                + " meanMs=" + String.format(java.util.Locale.ROOT, "%.3f",
                        calls == 0 ? 0.0 : batchedSubmitNanos / 1e6 / calls)
                + " flushes=" + batchedFlushCalls
                + " commandBuffers=" + coalescedCommandBuffers
                + " submitInfosSaved=" + savedSubmitInfos + "]";
    }

    /**
     * Submits every queued deferred submission in one {@code vkQueueSubmit} call: one VkSubmitInfo per entry, each
     * keeping its own command buffer and wait/signal semaphores, and the batch's single shared completion value signalled by the
     * call. The render thread measured its deferred submits at roughly one driver call each - the queue saw about
     * eight group closes per frame inside the offer path alone - so the entries are gathered and the value's
     * completion stands for all of them; per-submit order on one queue is preserved by array order.
     * <p>
     * Every path that could observe a submission completes or starts must flush first: a value a caller was
     * handed is only signalled when this submits, so awaiting it before the flush is a stall of the caller's
     * own making.
     */
    public void celtium$flushBatchedSubmits() {
        if (this.pendingBatchedSubmits.isEmpty()) {
            return;
        }
        celtium$awaitPresenter();
        SharedBatch batch = this.pendingBatch;
        this.pendingBatch = null;
        java.util.ArrayList<DeferredSubmit> entries = this.flushBatchScratch;
        entries.clear();
        entries.addAll(this.pendingBatchedSubmits);
        this.pendingBatchedSubmits.clear();
        Throwable submitError = submitBatch(batch, entries);
        if (submitError != null) {
            this.usable = false;
            this.deviceLost = true;
            // Some chunks may already be executing, and this failure might be
            // VK_ERROR_DEVICE_LOST. Freeing their buffers/fence is unsafe.
            for (DeferredSubmit entry : entries) {
                if (entry.commandBuffer != null) {
                    VulkanGlTexture.abandonLayoutRecording(entry.commandBuffer,
                            "the batched submit failed; the recording is dropped with the batch");
                }
            }
            abandonFailedBatch(batch, new java.util.ArrayList<DeferredSubmit>(entries));
            entries.clear();
            if (submitError instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (LinkageError) submitError;
        }
        // The queue accepted every entry in this batch, so each entry's recorded layout transitions are fact
        // now. Frame-slot entries were already committed by the coordinator's submission bookkeeping and a
        // second commit is a no-op; one-shot entries queued through celtium$submitDeferred are committed here,
        // at the one point their submission is known accepted (their recording was opened in beginCommands).
        for (DeferredSubmit entry : entries) {
            if (entry.commandBuffer != null) {
                VulkanGlTexture.commitLayoutRecording(entry.commandBuffer);
            }
        }
        entries.clear();
    }

    /** About 14 KiB of MemoryStack per chunk, leaving room for frames the callers already hold. */
    private static final int SUBMIT_CHUNK = 128;

    /** {@return the filled prefix count}, writing group sizes into {@code sizes} instead of allocating one. */
    /** Writes group sizes into {@code sizes} and {@return how many were written}. */
    static int planSubmitGroupsInto(boolean[] barriers, int length, int[] sizes) {
        int n = 0;
        for (int i = 0; i < length;) {
            int end = i + 1;
            if (!barriers[i]) {
                while (end < length && !barriers[end]) end++;
            }
            sizes[n++] = end - i;
            i = end;
        }
        return n;
    }

    static long coalescedCommandBuffers;
    static long savedSubmitInfos;

    /** Scratch for {@link #submitChunk}: rebuilt at the head of each call, never retained. */
    private boolean[] submitChunkBarriers = new boolean[SUBMIT_CHUNK];
    private int[] submitChunkGroups = new int[SUBMIT_CHUNK];

    private Throwable submitChunk(java.util.ArrayList<DeferredSubmit> entries, int from, int to,
            long completionValue) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int chunkSize = to - from;
            if (this.submitChunkBarriers.length < chunkSize) {
                this.submitChunkBarriers = new boolean[chunkSize];
                this.submitChunkGroups = new int[chunkSize];
            }
            boolean[] barriers = this.submitChunkBarriers;
            for (int i = from; i < to; i++) {
                DeferredSubmit e = entries.get(i);
                barriers[i - from] = (e.waitSemaphores != null && e.waitSemaphores.length > 0)
                        || (e.signalSemaphores != null && e.signalSemaphores.length > 0);
            }
            int groupCount = planSubmitGroupsInto(barriers, chunkSize, this.submitChunkGroups);
            int[] groups = this.submitChunkGroups;
            VkSubmitInfo.Buffer submits = VkSubmitInfo.calloc(groupCount, stack);
            int position = from;
            for (int i = 0; i < groupCount; i++) {
                int count = groups[i];
                DeferredSubmit entry = entries.get(position);
                VkSubmitInfo submit = submits.get(i).sType$Default();
                if (count == 1) {
                    submit.pCommandBuffers(stack.pointers(entry.commandBuffer));
                } else {
                    PointerBuffer pointers = stack.mallocPointer(count);
                    for (int j = 0; j < count; j++) {
                        pointers.put(entries.get(position + j).commandBuffer);
                    }
                    pointers.flip();
                    submit.pCommandBuffers(pointers);
                }
                if (entry.waitSemaphores != null) {
                    submit.waitSemaphoreCount(entry.waitSemaphores.length);
                    submit.pWaitSemaphores(stack.longs(entry.waitSemaphores));
                    submit.pWaitDstStageMask(stack.ints(entry.waitStages));
                }
                if (entry.signalSemaphores != null) {
                    submit.pSignalSemaphores(stack.longs(entry.signalSemaphores));
                }
                if (completionValue != 0L && i == groupCount - 1) {
                    // The batch's completion signal rides the chunk's last submit: the timeline semaphore joins
                    // its signal array (after any semaphores of the entry's own, whose paired values are 0 and
                    // ignored), so the value signals only after every command buffer in the batch has executed.
                    long[] signalHandles = new long[(entry.signalSemaphores != null
                            ? entry.signalSemaphores.length : 0) + 1];
                    long[] signalValues = new long[signalHandles.length];
                    if (entry.signalSemaphores != null) {
                        System.arraycopy(entry.signalSemaphores, 0, signalHandles, 0,
                                entry.signalSemaphores.length);
                    }
                    signalHandles[signalHandles.length - 1] = this.frameTimeline.semaphore();
                    signalValues[signalValues.length - 1] = completionValue;
                    submit.pSignalSemaphores(stack.longs(signalHandles));
                    VkTimelineSemaphoreSubmitInfo timelineInfo = VkTimelineSemaphoreSubmitInfo.calloc(stack)
                            .sType$Default()
                            .signalSemaphoreValueCount(signalValues.length)
                            .pSignalSemaphoreValues(stack.longs(signalValues));
                    submit.pNext(timelineInfo.address());
                }
                position += count;
            }
            try {
                VulkanContext.queueSubmitCalls++;
                long batchedSubmitStarted = System.nanoTime();
                check(vkQueueSubmit(this.queue, submits, VK_NULL_HANDLE), "vkQueueSubmit (batched deferred)");
                batchedSubmitNanos += System.nanoTime() - batchedSubmitStarted;
                batchedSubmitCalls++;
                coalescedCommandBuffers += to - from;
                savedSubmitInfos += (to - from) - groupCount;
            } catch (RuntimeException | LinkageError submitError) {
                return submitError;
            }
        }
        return null;
    }

    /**
     * Submits a detached batch in one {@code vkQueueSubmit}, on whichever thread holds the queue.
     *
     * @return the failure, or null; on failure the caller quarantines the entries through
     *         {@link #abandonFailedBatch} on its own thread
     */
    private Throwable submitBatch(SharedBatch batch, java.util.ArrayList<DeferredSubmit> entries) {
        // In chunks: a loading screen can queue hundreds of uploads in one batch (measured on Enigmatica 2 Expert's
        // texture stitch: "OutOfMemoryError: Out of stack space" building one VkSubmitInfo array for all of them on the
        // 64 KiB MemoryStack). Chunks go to the queue in order and only the last signals the shared completion
        // value, which the queue reaches after every earlier submission - the same completion the single call gave.
        for (int from = 0; from < entries.size(); from += SUBMIT_CHUNK) {
            int to = Math.min(entries.size(), from + SUBMIT_CHUNK);
            Throwable failure = submitChunk(entries, from, to, to == entries.size() ? batch.completionValue : 0L);
            if (failure != null) {
                // A failure after an earlier chunk reached the queue is a device-level error (the same call with the
                // same kind of entries just succeeded); the caller's free-everything path is then the lost-device one.
                return failure;
            }
        }
        batchedFlushCalls++;
        this.batchedFlushes++;
        this.batchedSubmissions += entries.size();
        // Item 529, instruments I3/I4: the batch's identity, composition and submission time are recorded here,
        // the one point every batch passes on its way to the queue. The composition scan reads fields the
        // submit loop just read, once per flush rather than once per entry's hot path, and the ring note is
        // four array writes; the submission time is what a later loss measures the batch's outstanding time
        // from.
        batch.submittedNanos = System.nanoTime();
        int frameEntries = 0;
        int semaphoreEntries = 0;
        for (DeferredSubmit entry : entries) {
            if (entry.frameSlot >= 0) {
                frameEntries++;
            }
            if ((entry.waitSemaphores != null && entry.waitSemaphores.length > 0)
                    || (entry.signalSemaphores != null && entry.signalSemaphores.length > 0)) {
                semaphoreEntries++;
            }
        }
        batch.frameEntries = frameEntries;
        batch.deferredEntries = entries.size() - frameEntries;
        batch.semaphoreEntries = semaphoreEntries;
        if (CrashBreadcrumbs.enabled()) {
            CrashBreadcrumbs.note3(CrashBreadcrumbs.BATCH_SUBMIT,
                    (this.generation << 32) | (batch.serial & 0xffffffffL), batch.completionValue,
                    CrashBreadcrumbs.packBatchComposition(entries.size(), frameEntries, semaphoreEntries));
        }
        return null;
    }

    /**
     * A batch whose submission failed: the entries leave the deferred queue and their command buffers are
     * quarantined - never freed, because in a multi-chunk batch an earlier chunk may already be executing on
     * the GPU. The batch's completion value needs no quarantine: it simply never signals, and a later batch's
     * higher value supersedes it on the counter when that batch completes. Each entry's
     * {@link DeferredSubmit#slotReclaim} still runs, with the lost-device flag, exactly once: the callback's
     * contract names this path alongside reclaim, and the frame coordinator's slot bookkeeping has no other
     * completion event for a failed batch. Runs on the owning thread.
     */
    private void abandonFailedBatch(SharedBatch batch, java.util.ArrayList<DeferredSubmit> entries) {
        // In a multi-chunk batch, earlier chunks may have reached the GPU.
        // Do not free their VkCommandBuffers.
        this.usable = false;
        this.deviceLost = true;
        for (DeferredSubmit entry : entries) {
            this.deferredSubmits.remove(entry);
            this.deferredSubmitsOutstanding--;
            if (entry.commandBuffer != null && entry.frameSlot < 0) {
                this.quarantinedCommandBuffers.add(entry.commandBuffer);
            }
            entry.commandBuffer = null;
            if (entry.slotReclaim != null) {
                entry.slotReclaim.accept(Boolean.TRUE);
                entry.slotReclaim = null;
            }
        }
        System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: quarantined "
                + entries.size() + " deferred entries without calling the native driver.");
    }


    /**
     * The present runs on the calling thread at the frame's end, through {@link PresentEngine}: the frame's
     * batched submit is flushed first, then the present blit's submit and {@code vkQueuePresentKHR} execute in
     * queue order on the same thread, and the verdict is final when the call returns.
     * <p>
     * This replaces the async presenter thread the renderer used to hand the batch and the present to. That
     * thread bought a measured p50 gain (paired KPI, same jar on/off, Xaero instance, 3 pairs each: Intel UHD
     * 630 p50 2.45 -> 2.31 ms, AMD RX 6400 1.30 -> 1.22 ms) and cost a second owner of the queue: every queue
     * access had to join the worker first, a batch in the worker's hands could not be polled, and the present
     * verdict was written by one thread and read by another - the shape behind the stale-verdict re-application
     * the present audit found (a verdict read repeatedly because nothing marked it consumed). The new
     * architecture's present contract (design section 2.5) is explicit: no async present thread, present at the
     * main thread's frame end, timestamps recorded around it. The verdict below is therefore consume-on-read:
     * {@link #celtium$presenterResult()} returns it once and resets it, so a later frame can never re-apply a
     * previous frame's OUT_OF_DATE.
     */
    // The hardware-profile gate that used to default the async presenter off on Windows stays as the recorded
    // profile (HardwareProfileGates carries it, with its citation - the device losses the old worker caused
    // there). With the worker gone the profile no longer selects a threading model; ASYNC_PRESENT now only
    // routes the swapchain's present between this context's PresentEngine path and the swapchain's own
    // synchronous path, and retiring the profile itself is the present-path phase's work, not this one's.
    static boolean asyncPresentDefaultForOs(String osName) {
        return !HardwareProfileGates.gated(HardwareProfileGates.GatedFeature.ASYNC_PRESENT, osName, null);
    }

    public static final boolean ASYNC_PRESENT = Boolean.parseBoolean(
            System.getProperty("celtium.vulkan.asyncPresent",
                    Boolean.toString(asyncPresentDefaultForOs(System.getProperty("os.name", "")))));

    /** The last synchronous present's verdict; consumed by {@link #celtium$presenterResult()}. */
    private int presenterResult = VK_SUCCESS;
    /** Whether {@link #presenterResult} holds a verdict no await has drained yet. */
    private boolean presenterVerdictPending;
    public static long presenterJoinNanos;
    public static long presenterJoins;
    public static long presenterJobs;

    /**
     * The only recoverable present results: success, a changed swapchain, and a lost surface - SURFACE_LOST is
     * a recreation event, not a device loss, and classifying it as fatal was the present audit's R2.
     */
    static boolean presenterResultIsFatal(int result) {
        return PresentEngine.resultIsFatal(result);
    }

    /**
     * Drains the previous present's verdict: the one place a fatal present result stops the renderer.
     * <p>
     * With the present synchronous there is no worker to join; what remains of the old join's contract is the
     * verdict check it performed. The first await after a present performs it (and is counted as the join, so
     * the report's join line keeps its meaning: the point the verdict became final for the frame path), later
     * awaits are no-ops. A fatal verdict is terminal, not another swapchain-out-of-date event - both Neeyo
     * logs continued after vkQueueSubmit -4, then hung/crashed in the NVIDIA driver - so it fails here, on the
     * first drain, before any more queue operations.
     */
    public void celtium$awaitPresenter() {
        if (!this.presenterVerdictPending) {
            return;
        }
        this.presenterVerdictPending = false;
        presenterJoins++;
        int result = this.presenterResult;
        if (presenterResultIsFatal(result)) {
            this.usable = false;
            this.deviceLost = true;
            throw new IllegalStateException("CELTIUM_PRESENT fatal VkResult " + result
                    + " (VK_ERROR_DEVICE_LOST=-4). Stop the renderer; "
                    + "recreating the swapchain cannot recover a lost device.");
        }
    }

    /**
     * {@return the last present's vkQueuePresentKHR result}, consuming it: the stored verdict resets to
     * VK_SUCCESS as it is read, so a frame that presents nothing new can never re-apply a previous frame's
     * verdict (the stale-verdict mechanism the present audit's R1 named).
     */
    public int celtium$presenterResult() {
        int result = this.presenterResult;
        this.presenterResult = VK_SUCCESS;
        return result;
    }

    /**
     * Presents: flushes the pending batch, submits the present blit and calls {@code vkQueuePresentKHR} - all
     * on the calling thread, through {@link PresentEngine}.
     * <p>
     * The name is the async presenter's, kept because the swapchain's handover contract keeps its shape: the
     * caller hands over the recorded blit and reads the verdict afterwards through
     * {@link #celtium$awaitPresenter()} and {@link #celtium$presenterResult()}. What changed is where the work
     * runs - here, on this thread, before the method returns - so the verdict is already final, a failed batch
     * throws from this thread's own flush exactly as it does at every other flush site, and the handover
     * always succeeds (the old false - the executor refused the job - has no synchronous counterpart).
     *
     * @return true always: the present has been performed when this returns
     */
    public boolean celtium$presentAsync(long blitCommandBuffer, long acquireSemaphore, long renderFinishedSemaphore,
            long swapchain, int imageIndex) {
        celtium$awaitPresenter();
        celtium$flushBatchedSubmits();
        VkCommandBuffer blit = new VkCommandBuffer(blitCommandBuffer, this.device);
        int result = this.presentEngine.submitAndPresent(blit, acquireSemaphore, renderFinishedSemaphore,
                swapchain, imageIndex);
        VulkanContext.queueSubmitCalls++;
        this.presenterResult = result;
        this.presenterVerdictPending = true;
        presenterJobs++;
        if (presenterJobs == 1L || presenterJobs % 2000L == 0L) {
            System.out.println("[Celtium] CELTIUM_ASYNC_PRESENT jobs=" + presenterJobs + " joins=" + presenterJoins
                    + " joinMicros=" + (presenterJoinNanos / 1000L) + " batch=false");
        }
        return true;
    }

    /**
     * Reclaims every deferred submission whose completion value the frame timeline has already reached, in
     * submission order, without waiting. {@return how many were reclaimed}. A head whose value has not been
     * reached ends the walk: the queue executes submissions in order, so nothing newer can have completed
     * either.
     * <p>
     * Deliberately does NOT flush the pending batch first: a poll is a reclaim scan, and flushing here made every
     * mid-frame poll fragment the batch into ~1-entry submits (measured: 10.7 flushes/frame, submissions/batch 1.09).
     * Sites whose ordering or arming actually requires the queue to see the pending work call
     * {@link #celtium$flushBatchedSubmits()} themselves - the awaits, the one-copy submit, the present path, the
     * frame boundary and the slot wait all do. Pending entries left unflushed are simply "not complete": their
     * values are unsignalled, which is the truthful answer a poll owes.
     */
    public int celtium$pollDeferredSubmits() {
        if (this.deviceLost) {
            throw celtium$lostDevice("pollDeferredSubmits after previous loss");
        }
        int reclaimed = 0;
        // One counter read serves the whole walk: values are submission-ordered, so the first head the counter
        // has not reached ends it, and no per-entry driver call is paid (the fence status read this replaces
        // cost one ioctl per entry).
        long completed = -1L;
        while (!this.deferredSubmits.isEmpty()) {
            DeferredSubmit head = this.deferredSubmits.peek();
            if (head.batch != null && head.batch == this.pendingBatch) {
                // Still in the unflushed batch: its value is not signalled, so it reads not-complete - known
                // without asking the driver at all.
                break;
            }
            if (completed < 0L) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    LongBuffer value = stack.mallocLong(1);
                    int read = VK12.vkGetSemaphoreCounterValue(this.device, this.frameTimeline.semaphore(),
                            value);
                    if (read == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                        submitDeviceLost++;
                        throw celtium$lostDevice("vkGetSemaphoreCounterValue(deferred poll)");
                    }
                    if (read != VK_SUCCESS) {
                        // An inconclusive read says nothing about completion: leave every entry pending and
                        // let a later poll or wait answer instead.
                        break;
                    }
                    completed = value.get(0);
                }
            }
            if (completed < head.completionValue) {
                break;
            }
            this.deferredSubmits.poll();
            celtium$reclaimDeferred(head, false);
            reclaimed++;
        }
        return reclaimed;
    }

    /**
     * {@return whether the frame timeline's counter has reached {@code value}}: the non-blocking completion
     * question the slot poll asks about a deferred slot submission. A lost device counts as reached - nothing
     * will ever run again, which is completion-equivalent for pending purposes, the same disposition the fence
     * status poll gave {@code VK_ERROR_DEVICE_LOST}; any other inconclusive read answers not-reached.
     */
    public boolean celtium$timelineReached(long value) {
        if (value == 0L) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer counter = stack.mallocLong(1);
            int read = VK12.vkGetSemaphoreCounterValue(this.device, this.frameTimeline.semaphore(), counter);
            if (read == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                return true;
            }
            return read == VK_SUCCESS && counter.get(0) >= value;
        }
    }

    /**
     * Boundedly waits on the oldest outstanding deferred submission - the one most likely to have completed and the
     * only one whose completion releases anything the head of the queue still holds - then reclaims it.
     * The wait-outcome matrix is {@link #endCommands}'s: success reclaims, a lost device counts as completion, a
     * timeout or another failure quarantines the resources the queue may still own and latches the context unusable.
     */
    private void celtium$awaitOldestDeferredSubmit(String reason) {
        celtium$flushBatchedSubmits();
        DeferredSubmit head = this.deferredSubmits.peek();
        if (head == null) {
            return;
        }
        celtium$awaitDeferredEntry(head, reason);
    }

    /**
     * Boundedly waits on the deferred submission identified by {@code completionValue} - the value
     * {@link #celtium$submitDeferred} returned. {@return false when the value is no longer tracked because a poll or
     * settle already confirmed it}. Used by owners whose next write would overwrite memory the submission still reads
     * (a texture's staging buffer), so the wait covers only that one submission, never the whole queue.
     */
    public boolean celtium$awaitDeferredSubmit(long completionValue, String reason) {
        if (this.deviceLost) {
            throw celtium$lostDevice("awaitDeferredSubmit after previous loss");
        }
        if (completionValue == 0L) {
            return false;
        }
        celtium$flushBatchedSubmits();
        for (DeferredSubmit entry : this.deferredSubmits) {
            if (entry.completionValue == completionValue) {
                celtium$awaitDeferredEntry(entry, reason);
                return true;
            }
        }
        return false;
    }

    static boolean canReclaimDeferred(int waitResult) {
        // A lost device is completion-equivalent for *pending ownership*, NOT a
        // license to call into a driver whose internal objects may already be gone.
        return waitResult == VK_SUCCESS;
    }

    boolean celtium$isDeviceLost() {
        return this.deviceLost;
    }

    IllegalStateException celtium$reportDeviceLost(String operation, int result) {
        return celtium$lostDevice(operation + " (VkResult=" + result + ")");
    }

    private IllegalStateException celtium$lostDevice(String operation) {
        // Item 529, instruments I6/I7: the dump's context section names this context's generation and carries
        // the deferred counters and the submit trail - both existed already and reached no loss report, so a
        // loss log could not say what the queue had been doing in the seconds before the detection.
        CrashBreadcrumbs.dump(operation, "context generation=" + this.generation
                + "\n" + celtium$describeDeferred().trim()
                + "\n" + SubmitTrail.describe());
        // Item 529, candidate (a): the process-level note a later vkCreateDevice refusal quotes, so the
        // refusal's own record names the loss it is downstream of instead of leaving the join to a
        // reporter's next submission.
        lastDeviceLossReport = celtium$deviceLossReport(operation, this.generation,
                System.currentTimeMillis());
        this.usable = false;
        this.deviceLost = true;
        return new IllegalStateException("CELTIUM_VULKAN_DEVICE_LOST at " + operation
                + ": stop all GPU operations. Driver objects/fences are held for process teardown; "
                + "a new Vulkan instance in this process cannot recover this device.");
    }

    /** The process-level loss note's one-line form: the reporting operation, its context generation, the wall time. */
    static String celtium$deviceLossReport(String operation, long generation, long whenMillis) {
        return operation + " (context generation " + generation + ", at " + whenMillis + ")";
    }

    /**
     * The {@code vkCreateDevice} refusal's full sentence (item 529, candidate (a)): the result, the device,
     * the parameters the creation was asked with, and - when this process already reported a device loss -
     * that report, so the refusal reads as the loss's downstream rather than as an unrelated crash. Pure
     * and static so the sentence is pinnable without a device.
     */
    static String celtium$deviceCreationFailure(int result, String deviceName,
            java.util.List<String> enabledExtensions, String featureSummary, String priorLossReport) {
        StringBuilder message = new StringBuilder(256);
        message.append("vkCreateDevice(").append(deviceName).append(") returned VkResult ").append(result)
                .append(". Enabled extensions: ").append(enabledExtensions)
                .append(". Enabled features: ").append(featureSummary).append('.');
        if (priorLossReport != null) {
            message.append(" A device loss was already reported in this process: ").append(priorLossReport)
                    .append(". This refusal is downstream of that loss - the device was already gone when the"
                            + " reload asked for a new one - not a fresh refusal of a healthy device.");
        } else {
            message.append(" No device loss was reported earlier in this process.");
        }
        return message.toString();
    }

    /** {@return the most recent device-loss report in this process}, or {@code null} when none was reported. */
    static String celtium$lastDeviceLossReport() {
        return lastDeviceLossReport;
    }

    /**
     * The attribution a fatal wait adds to its trigger (item 529, instruments I3/I4/I5): how long the wait
     * itself ran, which batch the entry belonged to, and how long that batch had been outstanding. The pair is
     * what separates "the queue died during this wait" from "the queue was already dead when the wait began" -
     * a long outstanding time with a short wait puts the loss before the detection, not inside it. Formatted
     * only on the loss path; a wait that succeeds pays nothing for it.
     */
    private String celtium$batchAttribution(DeferredSubmit entry, long waitedNanos) {
        StringBuilder attribution = new StringBuilder(80);
        attribution.append(" waitedMs=").append(waitedNanos / 1_000_000L);
        SharedBatch batch = entry.batch;
        if (batch != null) {
            attribution.append(" batch=g").append(this.generation).append(':').append(batch.serial);
            if (batch.submittedNanos != 0L) {
                attribution.append(" outstandingMs=")
                        .append((System.nanoTime() - batch.submittedNanos) / 1_000_000L);
            }
        }
        return attribution.toString();
    }

    /** The bounded wait itself, on one tracked entry; only a value the timeline actually reached is reclaimed. */
    private void celtium$awaitDeferredEntry(DeferredSubmit entry, String reason) {
        if (this.deviceLost) {
            throw celtium$lostDevice("awaitDeferredEntry after previous loss");
        }
        long waitStarted = System.nanoTime();
        int waited = celtium$awaitTimeline(entry.completionValue, SUBMIT_TIMEOUT_NANOS);
        long waitedNanos = System.nanoTime() - waitStarted;
        this.deferredWaits++;
        this.deferredWaitMillis += waitedNanos / 1_000_000L;
        if (waitedNanos / 1_000_000L > this.deferredWaitWorstMillis) {
            this.deferredWaitWorstMillis = waitedNanos / 1_000_000L;
        }
        if (waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
            submitDeviceLost++;
            // DO NOT call reclaimDeferred on a lost device: keep all handles untouched and stop the renderer
            // (the rule the fence-era reclaim's vkResetFences crash taught; the guard outlives the fence).
            throw celtium$lostDevice("vkWaitSemaphores(deferred " + reason + ")"
                    + celtium$batchAttribution(entry, waitedNanos));
        }
        if (canReclaimDeferred(waited)) {
            this.deferredSubmits.remove(entry);
            celtium$reclaimDeferred(entry, false);
            return;
        }
        if (waited == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
            submitTimeouts++;
        } else {
            submitWaitFailures++;
        }
        this.usable = false;
        this.deferredSubmits.remove(entry);
        this.deferredSubmitsOutstanding--;
        quarantine(entry.commandBuffer);
        if (entry.batch != null) {
            // This entry never reclaims, so its share of the batch count leaves with it: the count then names
            // only entries that can still reclaim.
            entry.batch.pending--;
            entry.batch = null;
        }
        // The slot confirmation is deliberately not run: the submission is unconfirmed, not lost, and this
        // branch latches the context unusable, so the coordinator submits nothing further against the slot.
        entry.commandBuffer = null;
        entry.uploadSlotMask = 0L;
        if (waited == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
            throw new IllegalStateException("vkWaitSemaphores(deferred " + reason + ") did not signal within "
                    + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s; the command buffer is quarantined"
                    + " rather than freed (submitTimeouts=" + submitTimeouts + ")");
        }
        throw new IllegalStateException("vkWaitSemaphores(deferred " + reason + ") did not confirm completion"
                + " and did not time out: result=" + waited + ". The queue may still own the submission, so its"
                + " command buffer is quarantined rather than freed, and the context is marked unusable."
                + " This is not a lost device: only VK_ERROR_DEVICE_LOST is completion-equivalent"
                + " (submitWaitFailures=" + submitWaitFailures + ")");
    }

    /**
     * Waits, bounded per entry, for every deferred submission still outstanding - the "everything submitted has
     * completed" statement {@code settleUploads}, resource growth and teardown need.
     */
    public void celtium$settleDeferredSubmits() {
        while (!this.deferredSubmits.isEmpty()) {
            celtium$awaitOldestDeferredSubmit("settle");
        }
    }

    /** The deferred-submit counters, appended to the upload-path report. */
    private String celtium$describeDeferred() {
        return " deferredSubmits[outstanding=" + this.deferredSubmitsOutstanding
                + " peak=" + this.deferredSubmitsPeak
                + " reclaims=" + this.deferredReclaims
                + " waits=" + this.deferredWaits
                + " waitMillis=" + this.deferredWaitMillis
                + " waitWorstMillis=" + this.deferredWaitWorstMillis
                + " heldAtTeardown=" + this.deferredHeldAtTeardown + "]"
                + " batched=[flushes=" + this.batchedFlushes + " submissions=" + this.batchedSubmissions
                + " pending=" + this.pendingBatchedSubmits.size() + " enabled=" + BATCHED_SUBMITS + "]";
    }

    /**
     * Copies {@code length} bytes from a native address into a device-local buffer through the cached staging buffer:
     * the source is copied into mapped host-coherent memory, a {@code vkCmdCopyBuffer} is recorded in a one-shot
     * command buffer, submitted, and waited for.
     * <p>
     * The copy size is padded up to a multiple of 4 because Vulkan requires it, so the destination range
     * {@code [destinationOffset, destinationOffset + length)} must be rounded up to 4 bytes by whoever allocated it
     * (the arena does). {@code destinationOffset} itself must already be a multiple of 4. The padding bytes are
     * written as zero, never as uninitialised staging data.
     * <p>
     * Render thread only.
     *
     * @param destination       handle of the destination {@code VkBuffer}
     * @param destinationOffset byte offset in the destination buffer, a multiple of 4
     * @param sourceAddress     native address to copy from
     * @param length            number of bytes to copy; zero performs no work
     */
    public void uploadToBuffer(long destination, long destinationOffset, long sourceAddress, long length) {
        if (sourceAddress == 0L) {
            throw new IllegalArgumentException("sourceAddress must be a non-null native address");
        }
        requireUploadArguments(destination, destinationOffset, length);
        if (length == 0L) {
            return;
        }
        long paddedLength = alignUp4(length);
        // Reverted to the settled one-copy path on 2026-10-12: the batched form below is written but not wired,
        // because it broke an invariant this path is the home of. A copy that is recorded and left pending names a
        // destination buffer handle, and the callers grow and replace destination buffers in the same frame
        // (VulkanArena.allocate -> VulkanBuffer.ensureCapacity destroys the old VkBuffer, and world teardown destroys
        // whole arenas), so the batch submitted later copied into handles that no longer existed: measured as
        // `vkQueueSubmit returned VkResult -4` (device lost) out of flushUploads on three legs. Batching is only
        // correct once a destination cannot be destroyed or have its segments reused while a copy into it is pending;
        // that is the ownership contract OPT-07 needs, and it is not this unit.
        celtium$immediateStagingCopy(destination, destinationOffset, sourceAddress, length, paddedLength);
    }

    /**
     * Records a copy into a buffer this frame, resolved and submitted by {@link #flushUploads()} (OPT-07).
     * <p>
     * The destination is named by the {@link VulkanBuffer} rather than by its handle, so a growth between this call
     * and the flush is transparent: the copy lands in the buffer the object has at flush time, at the same offset
     * (offsets do not move across a growth). A destination released before the flush is skipped and counted. The
     * caller must not need the bytes before the next {@link #flushUploads()}; the frame's own submission calls it
     * before it submits anything that reads them.
     * <p>
     * Render thread only.
     */
    public void uploadToBuffer(VulkanBuffer target, long destinationOffset, long sourceAddress, long length) {
        uploadToBuffer(target, destinationOffset, sourceAddress, length, -1);
    }

    /**
     * The same staging copy, tagged with its producer so the run report can say which subsystem owns the volume.
     * <p>
     * {@code kind} 0 is the arena writer (terrain section meshes and the immediate groups' vertex/index data) and 1
     * is a plain {@link VulkanBuffer} write; -1 keeps the historic untagged path. Added because the upload copy
     * measured 0.68 ms/frame of the frame's 1.18 ms upload phase and the only existing total could not say whether
     * that is the terrain or the immediate path - a 15 GB/run volume with two possible owners.
     */
    public void uploadToBuffer(VulkanBuffer target, long destinationOffset, long sourceAddress, long length,
            int kind) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (sourceAddress == 0L) {
            throw new IllegalArgumentException("sourceAddress must be a non-null native address");
        }
        requireUploadArguments(target.buffer(), destinationOffset, length);
        if (length == 0L) {
            return;
        }
        long paddedLength = alignUp4(length);
        long stagingOffset = celtium$reserveUploadSlot(paddedLength);
        if (stagingOffset < 0L) {
            // Larger than a ring slot: the settled path, which blocks until the copy has completed. Counted, so a
            // workload that needs the blocking form says so instead of hiding inside the batch counters.
            this.uploadFallbackCopies++;
            celtium$immediateStagingCopy(target.buffer(), destinationOffset, sourceAddress, length, paddedLength);
            return;
        }
        long copyStarted = System.nanoTime();
        MemoryUtil.memCopy(sourceAddress, this.uploadRingMapped + stagingOffset, length);
        celtium$zeroUploadTail(stagingOffset, length, paddedLength);
        this.uploadCopyNanos += System.nanoTime() - copyStarted;
        this.uploadCopyBytes += length;
        if (kind >= 0 && kind < this.uploadCopiesByKind.length) {
            this.uploadCopiesByKind[kind]++;
            this.uploadBytesByKind[kind] += length;
        }
        this.pendingUploads.add(obtainPendingUpload(target, destinationOffset, stagingOffset, paddedLength));
        this.uploadCopiesBatched++;
    }

    /** The heap-buffer half of {@link #uploadToBuffer(VulkanBuffer, long, long, long)}. */
    public void uploadToBuffer(VulkanBuffer target, long destinationOffset, ByteBuffer source) {
        uploadToBuffer(target, destinationOffset, source, -1);
    }

    /** The heap-buffer half, tagged with its producer like the native-address form. */
    public void uploadToBuffer(VulkanBuffer target, long destinationOffset, ByteBuffer source, int kind) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        int length = source.remaining();
        requireUploadArguments(target.buffer(), destinationOffset, length);
        if (length == 0) {
            return;
        }
        long paddedLength = alignUp4(length);
        long stagingOffset = celtium$reserveUploadSlot(paddedLength);
        if (stagingOffset < 0L) {
            this.uploadFallbackCopies++;
            celtium$immediateStagingCopy(target.buffer(), destinationOffset, source, paddedLength);
            return;
        }
        ByteBuffer staging = MemoryUtil.memByteBuffer(this.uploadRingMapped + stagingOffset, (int) paddedLength);
        long copyStarted = System.nanoTime();
        MemoryUtil.memCopy(source.duplicate(), staging);
        celtium$zeroUploadTail(stagingOffset, length, paddedLength);
        this.uploadCopyNanos += System.nanoTime() - copyStarted;
        this.uploadCopyBytes += source.remaining();
        if (kind >= 0 && kind < this.uploadCopiesByKind.length) {
            this.uploadCopiesByKind[kind]++;
            this.uploadBytesByKind[kind] += source.remaining();
        }
        this.pendingUploads.add(obtainPendingUpload(target, destinationOffset, stagingOffset, paddedLength));
        this.uploadCopiesBatched++;
    }

    /**
     * Copies the remaining bytes of a heap buffer into a device-local buffer through the cached staging buffer.
     * The source buffer's position is left untouched; only a duplicate is consumed.
     * <p>
     * Render thread only. The same 4-byte padding contract as
     * {@link #uploadToBuffer(long, long, long, long)} applies to the destination range.
     */
    public void uploadToBuffer(long destination, long destinationOffset, ByteBuffer source) {
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        int length = source.remaining();
        requireUploadArguments(destination, destinationOffset, length);
        if (length == 0) {
            return;
        }
        long paddedLength = alignUp4(length);
        // Same revert as the native-address form above: a pending copy must not outlive its destination buffer.
        celtium$immediateStagingCopy(destination, destinationOffset, source, paddedLength);
    }

    /**
     * Picks a memory type index that supports every flag in {@code requiredProperties} and is allowed by
     * {@code typeBits} (the {@code VkMemoryRequirements.memoryTypeBits} of the resource).
     *
     * @param purpose resource description used in the failure message
     * @throws IllegalStateException when no memory type satisfies the request
     */
    public int memoryTypeIndex(int typeBits, int requiredProperties, String purpose) {
        requireUsable();
        for (int i = 0; i < this.memoryProperties.memoryTypeCount(); i++) {
            if ((typeBits & (1 << i)) != 0
                    && (this.memoryProperties.memoryTypes(i).propertyFlags() & requiredProperties) == requiredProperties) {
                return i;
            }
        }
        throw new IllegalStateException("No Vulkan memory type on " + this.deviceName + " satisfies " + purpose
                + " (required flags 0x" + Integer.toHexString(requiredProperties)
                + ", allowed type bits 0x" + Integer.toHexString(typeBits) + ").");
    }

    /**
     * Rounds a byte count up to the next multiple of 4, the alignment Vulkan requires for buffer copies.
     */
    static long alignUp4(long value) {
        return (value + 3L) & ~3L;
    }

    /**
     * Adds one bootstrap submission fence wait's duration (BH-27). Kept apart from the queue-wait totals so a report
     * can say which of the two happened: the queue-wide idle is now zero on the normal path, and these are the
     * bounded per-submission waits that replaced it.
     */
    private static void celtium$recordBootstrapSubmitWait(long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        bootstrapSubmitWaitMillis += millis;
        if (millis > bootstrapSubmitWaitWorstMillis) {
            bootstrapSubmitWaitWorstMillis = millis;
        }
    }

    /**
     * Destroys the command pool, the staging buffer, the device and the instance. Safe to call more than once, and
     * safe to call on a context whose creation failed halfway through.
     * <p>
     * Render thread only.
     */
    public void delete() {
        if (this.deviceLost) {
            // Let the OS reclaim native resources when the process exits. A
            // vkResetFences dereference already faulted in NVIDIA's driver, so
            // repeating vkDeviceWaitIdle/vkDestroyDevice here risks a second AV.
            System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: skipping Vulkan driver cleanup "
                    + "after a fatal native device loss; terminate the Minecraft process.");
            if (this.device != null) {
                // The pipeline cache is not destroyed on this path (the device is not to be touched again), but its
                // registry entry must not survive: a successor device reusing this address would otherwise be
                // served this dead device's cache (BH10-06).
                dropPipelineCache(this.device.address());
                // The destructions this device still has queued can never run - the device is not to be touched
                // again - so they are dropped and counted rather than left in the static queue, where a successor
                // backend's drain would execute them against this dead handle (L06-01/BH10-09).
                int droppedRetirements = VulkanRetireQueue.dropFor(this.device.address());
                if (droppedRetirements > 0) {
                    System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: " + droppedRetirements
                            + " retired destruction(s) of this device were dropped unexecuted");
                }
            }
            return;
        }
        if (this.device != null) {
            final long deviceHandle = this.device.address();
            System.out.println("[Celtium] CELTIUM_WAIT_CENSUS bootstrapQueueWaits=" + bootstrapQueueWaits
                    + " bootstrapQueueWaitMillis=" + bootstrapQueueWaitMillis
                    + " bootstrapQueueWaitWorstMillis=" + bootstrapQueueWaitWorstMillis
                    + " submitWithSemaphoresCalls=" + submitWithSemaphoresCalls
                    + " submitTimeouts=" + submitTimeouts
                    + " " + waitOutcomeSummary()
                    + " " + uploadBatchSummary()
                    + " quarantinedCommandBuffers=" + this.quarantinedCommandBuffers.size()
                    + " (the bootstrap submit path waits on the queue, which the API cannot bound; the frame path"
                    + " waits on its own timeline value with a 5s bound and reports a timeout as a stall)");
            // Every recorded copy is followed by a queue wait, so this only drains work that is already complete.
            //
            // Audit B04: this wait decides whether the quarantined resources may be released, and its result is
            // therefore checked. A device that reports itself idle releases them; a device that is lost releases them
            // too (SUCCESS-equivalent for pending/in-use purposes); a wait that fails for any other reason does NOT,
            // because the queue may still own them and freeing here is the same VUID the quarantine exists to avoid.
            // The resources are then left to the process teardown, and the count is reported.
            celtium$awaitPresenter();
            int idle = vkDeviceWaitIdle(this.device);
            boolean releaseQuarantined = idle == VK_SUCCESS
                    || idle == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
            if (!releaseQuarantined) {
                deviceIdleFailures++;
                System.out.println("[Celtium] CELTIUM_WAIT_CENSUS vkDeviceWaitIdle did not confirm the device was idle"
                        + " (result=" + idle + "): " + this.quarantinedCommandBuffers.size() + " quarantined command"
                        + " buffer(s) are NOT released - the queue"
                        + " may still own them, and freeing here is what VUID-vkFreeCommandBuffers-pCommandBuffers-00047"
                        + " describes");
            }
            // The quarantined submissions are released here and only here: the device-wide wait above is the one point
            // where "the queue is finished with them" is a fact. Before this they are neither reset nor freed.
            if (idle == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                this.deviceLost = true;
                this.usable = false;
                System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: teardown wait reported "
                        + "device loss; skipping further native cleanup.");
                // Same disposition as the deviceLost entry above: this device's queued destructions are dropped
                // unexecuted, so no later drain can run them against the dead handle (L06-01/BH10-09). The
                // pipeline cache registry entry is dropped for the same reason (BH10-06).
                dropPipelineCache(deviceHandle);
                int droppedRetirements = VulkanRetireQueue.dropFor(deviceHandle);
                if (droppedRetirements > 0) {
                    System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: " + droppedRetirements
                            + " retired destruction(s) of this device were dropped unexecuted");
                }
                return;
            }
            if (releaseQuarantined) {
                for (VkCommandBuffer quarantined : this.quarantinedCommandBuffers) {
                    vkFreeCommandBuffers(this.device, this.commandPool, quarantined);
                }
                this.quarantinedCommandBuffers.clear();
                // Readback buffers quarantined with them (#364): the same device-wide wait is the point at which the
                // queue is known to be finished with the copies that were writing into them.
                releaseQuarantinedReadbackBuffers();
                // The retire queue's share of this device, drained at the same point and for the same reason: the
                // wait above confirmed this device is idle, and this is the last moment its queued destructions
                // can legally run - after vkDestroyDevice below, an entry left queued would be executed by a
                // successor backend's drain against a dead handle (L06-01/BH10-09). Only this device's entries
                // are drained; another live device's entries are not this wait's to release.
                int drainedRetirements = VulkanRetireQueue.flushFor(deviceHandle);
                if (drainedRetirements > 0) {
                    System.out.println("[Celtium] CELTIUM_TEARDOWN destroyed " + drainedRetirements
                            + " retired resource(s) of this device at context delete");
                }
            }
            // Deferred submissions are under the same rule as the quarantine list: released only when the device
            // confirmed idle, otherwise counted as held. The fence pool itself is destroyed either way - on a
            // non-confirming wait the objects are already unreachable, which is what makes destroying them safe.
            // The upload ring is decided here too, under the same rule: the batches read it, so it is destroyed
            // only when a drain confirmed after the flush below makes "no batch is reading the ring" a fact.
            boolean ringDrained = false;
            if (releaseQuarantined) {
                try {
                    // Whatever is still queued was logically submitted already: push it to the queue so the drain
                    // below reclaims real completions rather than entries the GPU never saw.
                    celtium$flushBatchedSubmits();
                } catch (RuntimeException | LinkageError ignored) {
                    // Teardown already knows the device state; a failed flush disposes of the entries itself.
                }
                if (this.deviceLost) {
                    // The flush itself reported the loss (its failure path quarantined the batch without
                    // touching handles). The rule at the head of this method applies unchanged: no further
                    // native cleanup. The rule's registry hygiene applies too: the pipeline cache entry and
                    // any retirements queued since the drain above must not survive this device (BH10-06 /
                    // L06-01), the same drops the other device-lost exits make.
                    System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: teardown flush reported "
                            + "device loss; skipping further native cleanup.");
                    dropPipelineCache(deviceHandle);
                    int droppedRetirements = VulkanRetireQueue.dropFor(deviceHandle);
                    if (droppedRetirements > 0) {
                        System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: " + droppedRetirements
                                + " retired destruction(s) of this device were dropped unexecuted");
                    }
                    return;
                }
                // The flush armed real submissions AFTER the device-wide wait above, so that wait says nothing
                // about them: reclaiming here without a second confirmation would recycle their command
                // buffers, reset the batch fence and release the ring slots the queue may still be executing
                // against. Wait again - the same "the queue is finished with them" fact the quarantine
                // release above rests on.
                ringDrained = true;
                if (!this.deferredSubmits.isEmpty()) {
                    int drained = vkDeviceWaitIdle(this.device);
                    if (drained == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                        this.deviceLost = true;
                        this.usable = false;
                        System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: teardown drain reported "
                                + "device loss; skipping further native cleanup.");
                        // The same registry hygiene as the other device-lost exits: the dead device's
                        // pipeline cache entry and queued retirements must not survive it (BH10-06 /
                        // L06-01).
                        dropPipelineCache(deviceHandle);
                        int droppedRetirements = VulkanRetireQueue.dropFor(deviceHandle);
                        if (droppedRetirements > 0) {
                            System.err.println("[Celtium] CELTIUM_VULKAN_DEVICE_LOST: " + droppedRetirements
                                    + " retired destruction(s) of this device were dropped unexecuted");
                        }
                        return;
                    }
                    ringDrained = drained == VK_SUCCESS;
                    if (!ringDrained) {
                        deviceIdleFailures++;
                        System.out.println("[Celtium] CELTIUM_WAIT_CENSUS vkDeviceWaitIdle did not confirm"
                                + " the deferred submissions drained (result=" + drained + "): "
                                + this.deferredSubmits.size() + " deferred submission(s) are NOT reclaimed"
                                + " and the upload ring is NOT destroyed - the queue may still own them");
                    }
                }
                if (ringDrained) {
                    while (!this.deferredSubmits.isEmpty()) {
                        celtium$reclaimDeferred(this.deferredSubmits.poll(), false);
                    }
                } else {
                    this.deferredHeldAtTeardown = this.deferredSubmits.size();
                    this.deferredSubmits.clear();
                }
            } else {
                this.deferredHeldAtTeardown = this.deferredSubmits.size();
                this.deferredSubmits.clear();
                // The wait did not confirm idle, so this device's queued destructions may not run - and the
                // device is destroyed below regardless, so they never legally can. They are dropped and counted,
                // the same disposition as the held deferred submissions above, rather than left in the static
                // queue for a successor backend's drain to execute against the dead handle (L06-01/BH10-09).
                int droppedRetirements = VulkanRetireQueue.dropFor(deviceHandle);
                if (droppedRetirements > 0) {
                    System.out.println("[Celtium] CELTIUM_TEARDOWN " + droppedRetirements
                            + " retired destruction(s) of this device were NOT executed (the teardown wait did not"
                            + " confirm idle) and were dropped; the resources are left to the process teardown");
                }
            }
            destroyStaging();
            if (ringDrained) {
                destroyUploadRing();
            }
            savePipelineCache();
            for (Long ballast : EXPERIMENT_BALLAST) {
                freeMemory(this.device, ballast.longValue());
            }
            EXPERIMENT_BALLAST.clear();
            if (this.commandPool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.device, this.commandPool, null);
                this.commandPool = VK_NULL_HANDLE;
            }
            // The HAL's device children go with the device: the frame timeline's semaphore is destroyed,
            // every slab the device-local allocators carved is freed (one vkFreeMemory per slab through the
            // census-counted path), and the present engine holds no device objects of its own - it only
            // borrows the queue - so dropping the reference is its whole teardown.
            if (this.frameTimeline != null) {
                this.frameTimeline.delete();
                this.frameTimeline = null;
            }
            for (SlabAllocator allocator : this.deviceLocalSlabsByType.values()) {
                allocator.delete();
            }
            this.deviceLocalSlabsByType.clear();
            for (SlabAllocator allocator : this.hostVisibleSlabsByType.values()) {
                allocator.delete();
            }
            this.hostVisibleSlabsByType.clear();
            this.presentEngine = null;
            vkDestroyDevice(this.device, null);
            this.device = null;
        }
        this.queue = null;
        this.physicalDevice = null;
        if (this.memoryProperties != null) {
            this.memoryProperties.free();
            this.memoryProperties = null;
        }
        if (this.instance != null) {
            if (this.debugMessenger != VK_NULL_HANDLE) {
                ValidationBridge.destroy(this, this.instance, this.debugMessenger);
                this.debugMessenger = VK_NULL_HANDLE;
            }
            vkDestroyInstance(this.instance, null);
            this.instance = null;
        }
        this.queueFamilyIndex = -1;
        this.deviceName = null;
        this.deviceUuid = null;
    }

    /**
     * The pipeline caches of every live device, keyed by device address.
     * <p>
     * Every pipeline used to be created with no cache: each first use of a state combination compiled from scratch, in
     * every session, on the frame that needed it. The cache is seeded from {@code celtium/pipeline-cache.bin} under the
     * game directory and written back when the device is destroyed; the driver validates the header (vendor, device,
     * cache UUID) and ignores data from another device or driver version, so a stale file costs nothing.
     * <p>
     * A pipeline cache is a child object of the device that created it, so the registry is keyed by device: this used
     * to be a single static slot holding the most recently created context's cache, and the static pipeline creation
     * below paired it with whatever device the caller passed. With two contexts alive (the window backend beside a
     * world backend, or a second terrain backend, which gets its own device) one device's pipelines were created
     * through the other device's cache - a validation violation and undefined behaviour (BH10-06). Entries are
     * registered at creation and removed on every teardown path, so a destroyed or device-lost context's cache is
     * never served again, not even to a successor device that reuses the address.
     */
    private static final java.util.Map<Long, Long> PIPELINE_CACHES_BY_DEVICE =
            new java.util.concurrent.ConcurrentHashMap<Long, Long>();

    static void registerPipelineCache(long deviceAddress, long cacheHandle) {
        PIPELINE_CACHES_BY_DEVICE.put(deviceAddress, cacheHandle);
    }

    /** The pipeline cache created on {@code deviceAddress}, or {@code VK_NULL_HANDLE} if it has none. */
    static long pipelineCacheFor(long deviceAddress) {
        Long cache = PIPELINE_CACHES_BY_DEVICE.get(deviceAddress);
        return cache != null ? cache.longValue() : VK_NULL_HANDLE;
    }

    /** Removes the entry for {@code deviceAddress} if it still names {@code cacheHandle}. */
    static void unregisterPipelineCache(long deviceAddress, long cacheHandle) {
        PIPELINE_CACHES_BY_DEVICE.remove(deviceAddress, cacheHandle);
    }

    /** Removes whatever entry {@code deviceAddress} has; for teardown paths that must not serve the device again. */
    static void dropPipelineCache(long deviceAddress) {
        PIPELINE_CACHES_BY_DEVICE.remove(deviceAddress);
    }

    /**
     * Creates graphics pipelines through the cache of the device they are created on, and times it: a pipeline the cache does not hold is compiled
     * on the thread that asked, which is the client thread for every pipeline a new draw state needs - a hitch the
     * first time an effect appears. Each creation over 2 ms is logged (bounded by the log limiter) with the running
     * totals, which {@link #pipelineCreationSummary()} also reports.
     */
    public static int createGraphicsPipelines(org.lwjgl.vulkan.VkDevice device,
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info, java.nio.LongBuffer pipelines) {
        return createGraphicsPipelines(device, info, pipelines, "unspecified");
    }

    /**
     * As {@link #createGraphicsPipelines(org.lwjgl.vulkan.VkDevice, org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer, java.nio.LongBuffer)},
     * plus the identity record item 414 found missing: until now a creation left only its duration and the running
     * totals, so when the summary moved (the measured burst was generations 39-45, inside two milliseconds) nothing
     * anywhere said which shader stages, vertex bindings, render pass or layout any of those generations was built
     * from, and the two hypotheses that turned on it could not be separated. Every creation now prints one
     * {@code CELTIUM_PIPELINE_CREATE} line - generation, the calling site's name for its family, the created handle,
     * and the identity read out of the create info itself - and leaves the same binding in the breadcrumb ring.
     * The tag is exempt from the log limiter (creations are one per distinct state, not per frame), and reading the
     * identity is defensive: a struct this code cannot read must never change whether the pipeline is created.
     *
     * @param site the creating family's name, from the call site ({@code terrain}, {@code immediate-item}, ...)
     */
    public static int createGraphicsPipelines(org.lwjgl.vulkan.VkDevice device,
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info, java.nio.LongBuffer pipelines, String site) {
        long start = System.nanoTime();
        int result = vkCreateGraphicsPipelines(device, pipelineCacheFor(device.address()), info, null, pipelines);
        long took = System.nanoTime() - start;
        long generation = ++pipelineCreations;
        pipelineCreationNanos += took;
        if (took > pipelineCreationWorstNanos) {
            pipelineCreationWorstNanos = took;
        }
        if (PipelineCreateCensus.ENABLED) {
            int created = result == VK_SUCCESS ? Math.min(info.remaining(), pipelines.remaining()) : 0;
            PipelineCreateCensus.noteGraphics(site, created, took);
        }
        if (result == VK_SUCCESS) {
            int count = Math.min(info.remaining(), pipelines.remaining());
            if (count > 1) {
                // Generations are per pipeline, not per call, so a multi-create call takes the whole span.
                pipelineCreations += count - 1;
            }
            for (int i = 0; i < count; i++) {
                long handle = pipelines.get(pipelines.position() + i);
                long renderPass = pipelineRenderPass(info, i);
                System.out.println("[Celtium] CELTIUM_PIPELINE_CREATE generation=" + (generation + i)
                        + " site=" + site + " pipeline=0x" + Long.toHexString(handle)
                        + " " + pipelineIdentity(info, i) + " ms=" + took / 1_000_000L);
                if (CrashBreadcrumbs.enabled()) {
                    CrashBreadcrumbs.note3(CrashBreadcrumbs.PIPELINE_CREATE, generation + i, handle, renderPass);
                }
            }
        }
        if (took > 2_000_000L) {
            System.out.println("[Celtium] CELTIUM_PIPELINE_SLOW ms=" + took / 1_000_000L + " thread="
                    + Thread.currentThread().getName() + " " + pipelineCreationSummary());
        }
        return result;
    }

    /** {@return the render pass the {@code index}-th create info names}, or 0 when it cannot be read. */
    private static long pipelineRenderPass(org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info, int index) {
        try {
            return info.get(info.position() + index).renderPass();
        } catch (RuntimeException | LinkageError unreadable) {
            return 0L;
        }
    }

    /**
     * {@return the identity of one create info as log fields}: render pass, layout, stage count, input topology and
     * vertex binding/attribute counts - the fields that separate one generation from the next in a log. Any field
     * that cannot be read is left out rather than guessed; a wholly unreadable struct answers {@code identity=unreadable}.
     */
    private static String pipelineIdentity(org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info, int index) {
        try {
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo one = info.get(info.position() + index);
            StringBuilder identity = new StringBuilder(96);
            identity.append("renderPass=0x").append(Long.toHexString(one.renderPass()));
            identity.append(" layout=0x").append(Long.toHexString(one.layout()));
            identity.append(" stages=").append(one.stageCount());
            org.lwjgl.vulkan.VkPipelineInputAssemblyStateCreateInfo assembly = one.pInputAssemblyState();
            if (assembly != null) {
                identity.append(" topology=").append(assembly.topology());
            }
            org.lwjgl.vulkan.VkPipelineVertexInputStateCreateInfo vertexInput = one.pVertexInputState();
            if (vertexInput != null) {
                identity.append(" bindings=").append(vertexInput.vertexBindingDescriptionCount());
                identity.append(" attributes=").append(vertexInput.vertexAttributeDescriptionCount());
            }
            return identity.toString();
        } catch (RuntimeException | LinkageError unreadable) {
            return "identity=unreadable";
        }
    }

    /** {@return the pipeline creations so far}: count, total and worst milliseconds. */
    public static String pipelineCreationSummary() {
        String summary = "pipelineCreations=" + pipelineCreations + " pipelineCreationMs=" + pipelineCreationNanos / 1_000_000L
                + " pipelineCreationWorstMs=" + pipelineCreationWorstNanos / 1_000_000L;
        return PipelineCreateCensus.ENABLED ? summary + " " + PipelineCreateCensus.summaryLine() : summary;
    }

    /**
     * Allocates device memory and counts it. Intel's driver hands every live allocation to the kernel on each
     * {@code vkQueueSubmit}, so the number of live allocations is a per-frame cost there (leg intel-prof-1: queue submit
     * was the client thread's largest native cost on UHD 630, where Celtium measured 1.68x Celeritas). The live count,
     * its peak and the live bytes are reported by {@link #memoryAllocationSummary()}.
     */
    public static int allocateMemory(org.lwjgl.vulkan.VkDevice device, org.lwjgl.vulkan.VkMemoryAllocateInfo info,
            java.nio.LongBuffer memory) {
        return allocateMemory(device, info, memory, "unspecified");
    }

    /**
     * {@link #allocateMemory} with the allocating site named, so the census can attribute its
     * live and peak counts per site (arch-phase1-fix). The Phase 1 gate leg measured a live
     * peak of 19,630 against a limit of 24 on the new architecture — whose device-local buffers
     * already sub-allocate from fixed slabs — but the single aggregate number could not say
     * which of the remaining direct {@code vkAllocateMemory} paths (mirrored textures, host
     * buffers, uniform buffers, readback staging, frame targets, the post chain) scaled with
     * the world. The per-site peaks in {@link #memoryAllocationSummary()} are that measurement.
     */
    public static int allocateMemory(org.lwjgl.vulkan.VkDevice device, org.lwjgl.vulkan.VkMemoryAllocateInfo info,
            java.nio.LongBuffer memory, String site) {
        int result = vkAllocateMemory(device, info, null, memory);
        if (result == VK_SUCCESS) {
            long size = info.allocationSize();
            Long handle = Long.valueOf(memory.get(memory.position()));
            LIVE_ALLOCATIONS.put(handle, Long.valueOf(size));
            ALLOCATION_SITES.put(handle, site);
            long[] counts = SITE_ALLOCATION_COUNTS.computeIfAbsent(site, key -> new long[2]);
            synchronized (counts) {
                counts[0]++;
                if (counts[0] > counts[1]) {
                    counts[1] = counts[0];
                }
            }
            long live = LIVE_ALLOCATIONS.size();
            if (live > peakAllocations) {
                peakAllocations = live;
            }
            allocationsMade++;
            if (EXPERIMENT_ALLOCATIONS > 0 && !experimentBallastDone) {
                allocateExperimentBallast(device, info.memoryTypeIndex());
            }
        }
        return result;
    }

    /**
     * Frees device memory allocated through {@link #allocateMemory}; a null handle is ignored as Vulkan ignores it.
     * <p>
     * A free slower than 50 ms - the frame boundary's slow threshold, the {@code CELTIUM_BOUNDARY_SLOW} line's -
     * prints one {@code CELTIUM_FREE_MEMORY_SLOW} line with the freed allocation's size ({@code -1} when the handle
     * was not tracked by {@link #allocateMemory}) and the running slow totals. Item 675's desk separation put a
     * same-JVM re-entry stall inside this call, after {@code settleGpu} had already waited the device idle, with the
     * client thread's CPU barely advancing across a 66 s thread-dump interval: the block is inside the native call,
     * not in any fence wait of ours, and until now nothing in the log said which free, or how large, or how many.
     * The timing itself is two {@code nanoTime} reads around the native call, paid on every free; the print is
     * bounded per tag by {@code CeltiumLogLimiter} like every other {@code [Celtium]} line.
     */
    public static void freeMemory(org.lwjgl.vulkan.VkDevice device, long memory) {
        Long size = null;
        if (memory != VK_NULL_HANDLE) {
            size = LIVE_ALLOCATIONS.remove(Long.valueOf(memory));
            String site = ALLOCATION_SITES.remove(Long.valueOf(memory));
            if (site != null) {
                long[] counts = SITE_ALLOCATION_COUNTS.get(site);
                if (counts != null) {
                    synchronized (counts) {
                        counts[0]--;
                    }
                }
            }
        }
        long started = System.nanoTime();
        vkFreeMemory(device, memory, null);
        long took = System.nanoTime() - started;
        if (took > SLOW_FREE_MEMORY_NANOS) {
            slowFreeMemoryCalls++;
            slowFreeMemoryNanos += took;
            if (took > slowFreeMemoryWorstNanos) {
                slowFreeMemoryWorstNanos = took;
            }
            System.out.println("[Celtium] CELTIUM_FREE_MEMORY_SLOW millis=" + (took / 1_000_000L)
                    + " size=" + (size == null ? -1L : size.longValue())
                    + " slowCalls=" + slowFreeMemoryCalls
                    + " slowTotalMillis=" + (slowFreeMemoryNanos / 1_000_000L)
                    + " slowWorstMillis=" + (slowFreeMemoryWorstNanos / 1_000_000L));
        }
    }

    /** {@return the device memory allocations}: live now, peak, made in total, and live bytes in MiB. */
    public static String memoryAllocationSummary() {
        long bytes = 0L;
        for (Long size : LIVE_ALLOCATIONS.values()) {
            bytes += size.longValue();
        }
        StringBuilder summary = new StringBuilder();
        summary.append("memoryAllocationsLive=").append(LIVE_ALLOCATIONS.size())
                .append(" memoryAllocationsPeak=").append(peakAllocations)
                .append(" memoryAllocationsMade=").append(allocationsMade)
                .append(" memoryLiveMiB=").append(bytes >> 20)
                .append(" memoryAllocationsBySite=[");
        java.util.List<java.util.Map.Entry<String, long[]>> sites =
                new java.util.ArrayList<java.util.Map.Entry<String, long[]>>(SITE_ALLOCATION_COUNTS.entrySet());
        sites.sort((left, right) -> Long.compare(right.getValue()[1], left.getValue()[1]));
        boolean first = true;
        for (java.util.Map.Entry<String, long[]> site : sites) {
            if (!first) {
                summary.append(' ');
            }
            first = false;
            long[] counts = site.getValue();
            long live;
            long peak;
            synchronized (counts) {
                live = counts[0];
                peak = counts[1];
            }
            summary.append(site.getKey()).append(":live=").append(live).append(",peak=").append(peak);
        }
        summary.append(']');
        return summary.toString();
    }

    /**
     * Experiment only: {@code -Dceltium.debug.extraAllocations=N} holds N extra 64 KiB allocations of the first
     * allocation's memory type for the device's life, to measure whether the live allocation count alone changes frame
     * time on a driver (Intel's hands every live allocation to each submit). Off (0) by default.
     */
    private static final int EXPERIMENT_ALLOCATIONS = Integer.getInteger("celtium.debug.extraAllocations", 0).intValue();
    private static final java.util.List<Long> EXPERIMENT_BALLAST = new java.util.ArrayList<Long>();
    private static boolean experimentBallastDone;

    private static void allocateExperimentBallast(org.lwjgl.vulkan.VkDevice device, int memoryType) {
        experimentBallastDone = true;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryAllocateInfo info = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(64L * 1024L).memoryTypeIndex(memoryType);
            java.nio.LongBuffer handle = stack.mallocLong(1);
            for (int i = 0; i < EXPERIMENT_ALLOCATIONS; i++) {
                if (allocateMemory(device, info, handle) != VK_SUCCESS) {
                    break;
                }
                EXPERIMENT_BALLAST.add(Long.valueOf(handle.get(0)));
            }
        }
        System.out.println("[Celtium] CELTIUM_ALLOCATION_EXPERIMENT extra=" + EXPERIMENT_BALLAST.size()
                + " memoryType=" + memoryType);
    }

    private static final java.util.Map<Long, Long> LIVE_ALLOCATIONS =
            new java.util.concurrent.ConcurrentHashMap<Long, Long>();
    /** The site label of each live allocation, for the per-site census (arch-phase1-fix). */
    private static final java.util.Map<Long, String> ALLOCATION_SITES =
            new java.util.concurrent.ConcurrentHashMap<Long, String>();
    /** Per-site {live, peak} counts, guarded by synchronizing on the array itself. */
    private static final java.util.Map<String, long[]> SITE_ALLOCATION_COUNTS =
            new java.util.concurrent.ConcurrentHashMap<String, long[]>();
    private static volatile long peakAllocations;
    private static volatile long allocationsMade;

    /** A {@link #freeMemory} call slower than this prints; the frame boundary's slow threshold. */
    private static final long SLOW_FREE_MEMORY_NANOS = 50_000_000L;
    private static long slowFreeMemoryCalls;
    private static long slowFreeMemoryNanos;
    private static long slowFreeMemoryWorstNanos;

    private static long pipelineCreations;
    private static long pipelineCreationNanos;
    private static long pipelineCreationWorstNanos;
    private long pipelineCache = VK_NULL_HANDLE;

    private static final java.nio.file.Path PIPELINE_CACHE_FILE = resolvePipelineCacheFile();

    private static java.nio.file.Path resolvePipelineCacheFile() {
        String configured = System.getProperty("celtium.vulkan.pipelineCacheFile");
        return configured != null ? java.nio.file.Paths.get(configured)
                : java.nio.file.Paths.get(System.getProperty("user.dir"), "celtium", "pipeline-cache.bin");
    }

    private static java.nio.file.Path pipelineCacheFile() {
        return PIPELINE_CACHE_FILE;
    }

    private void createPipelineCache() {
        if (PIPELINE_CACHE_DISABLED) {
            return;
        }
        byte[] seed = null;
        try {
            java.nio.file.Path file = pipelineCacheFile();
            if (java.nio.file.Files.isRegularFile(file)) {
                seed = java.nio.file.Files.readAllBytes(file);
            }
        } catch (java.io.IOException | RuntimeException error) {
            System.out.println("[Celtium] CELTIUM_PIPELINE_CACHE unreadable, starting empty: " + error);
        }
        java.nio.ByteBuffer initial = seed == null || seed.length == 0 ? null : MemoryUtil.memAlloc(seed.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (initial != null) {
                initial.put(seed).flip();
            }
            org.lwjgl.vulkan.VkPipelineCacheCreateInfo info = org.lwjgl.vulkan.VkPipelineCacheCreateInfo.calloc(stack)
                    .sType$Default().pInitialData(initial);
            LongBuffer handle = stack.mallocLong(1);
            int result = org.lwjgl.vulkan.VK10.vkCreatePipelineCache(this.device, info, null, handle);
            if (result != VK_SUCCESS && initial != null) {
                // A rejected seed is not a reason to have no cache at all.
                info.pInitialData(null);
                result = org.lwjgl.vulkan.VK10.vkCreatePipelineCache(this.device, info, null, handle);
            }
            if (result == VK_SUCCESS) {
                this.pipelineCache = handle.get(0);
                registerPipelineCache(this.device.address(), this.pipelineCache);
                System.out.println("[Celtium] CELTIUM_PIPELINE_CACHE created seedBytes=" + (seed == null ? 0 : seed.length));
            }
        } finally {
            if (initial != null) {
                MemoryUtil.memFree(initial);
            }
        }
    }

    /** Writes the cache back (atomically) and destroys it; called before the device is destroyed. */
    private void savePipelineCache() {
        if (this.pipelineCache == VK_NULL_HANDLE) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.PointerBuffer size = stack.mallocPointer(1);
            if (org.lwjgl.vulkan.VK10.vkGetPipelineCacheData(this.device, this.pipelineCache, size, null) == VK_SUCCESS
                    && size.get(0) > 0) {
                java.nio.ByteBuffer data = MemoryUtil.memAlloc((int) size.get(0));
                try {
                    if (org.lwjgl.vulkan.VK10.vkGetPipelineCacheData(this.device, this.pipelineCache, size, data)
                            == VK_SUCCESS) {
                        byte[] bytes = new byte[(int) size.get(0)];
                        data.get(bytes);
                        java.nio.file.Path file = pipelineCacheFile();
                        java.nio.file.Files.createDirectories(file.getParent());
                        java.nio.file.Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
                        java.nio.file.Files.write(temporary, bytes);
                        java.nio.file.Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                    }
                } finally {
                    MemoryUtil.memFree(data);
                }
            }
        } catch (java.io.IOException | RuntimeException error) {
            System.out.println("[Celtium] CELTIUM_PIPELINE_CACHE not saved: " + error);
        } finally {
            org.lwjgl.vulkan.VK10.vkDestroyPipelineCache(this.device, this.pipelineCache, null);
            unregisterPipelineCache(this.device.address(), this.pipelineCache);
            this.pipelineCache = VK_NULL_HANDLE;
        }
    }

    private void initialize(String applicationName, GlDeviceIdentity openGl) {
        this.interopPath = openGl != null;
        createInstance(applicationName);
        matchPhysicalDevice(openGl);
        createLogicalDevice();
        createQueueAndCommandPool();
        // The frame timeline exists from the first submission on: every path that submits signals it, and the
        // synchronous submit paths wait on its values instead of on fences of their own.
        this.frameTimeline = new FrameTimeline(this.device);
        this.presentEngine = new PresentEngine(this.queue);
        createPipelineCache();
        reportDeviceContractIfReady();
        this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(this.physicalDevice, this.memoryProperties);
    }

    /** Select supported instance extensions for the desktop platforms handled by VulkanSwapchain. */
    static List<String> presentationInstanceExtensions(java.util.Set<String> available) {
        List<String> selected = new ArrayList<String>();
        for (String candidate : new String[] {
                "VK_KHR_surface", "VK_KHR_xlib_surface", "VK_KHR_wayland_surface", "VK_KHR_win32_surface"}) {
            if (available.contains(candidate)) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private void createInstance(String applicationName) {
        boolean validation = VALIDATION_REQUESTED;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
                    .pApplicationName(stack.UTF8(applicationName))
                    .applicationVersion(1)
                    .apiVersion(VK12.VK_API_VERSION_1_2);
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack).sType$Default()
                    .pApplicationInfo(application);
            // The platform surface extensions are instance extensions, so they have to be enabled here.
            List<String> enabledInstanceExtensions = new ArrayList<String>();
            // Enabled unconditionally: the window-backend present path always creates a swapchain - the
            // swapchain is the only present route - so the surface extensions cannot be gated on the probe.
            // Gating them on a route switch is what once made the direct route unstartable: the present path
            // retried swapchain creation against a device created without VK_KHR_swapchain until the bounded
            // escalation crashed the run. The selection itself stays capability-checked:
            // presentationInstanceExtensions only returns extensions the instance advertises.
            java.util.Set<String> available = instanceExtensionNames(stack);
            enabledInstanceExtensions.addAll(presentationInstanceExtensions(available));
            Object messengerInfo = null;
            if (validation) {
                // Validation is a diagnostic, not a requirement: a machine without the layer or the debug-utils
                // extension must still start. The tester's AMD crash was a requested-but-missing layer whose
                // InvocationTargetException surfaced as "validation support is present but could not be reached".
                String unavailable = ValidationBridge.unavailableReason();
                if (unavailable != null) {
                    System.err.println("[Celtium] CELTIUM_VALIDATION_UNAVAILABLE reason=" + unavailable
                            + " (continuing without validation)");
                    validation = false;
                } else {
                    try {
                        messengerInfo = ValidationBridge.enable(this, stack, createInfo);
                    } catch (IllegalStateException error) {
                        // The layer is installed but the messenger could not be built (a binding or native-callback
                        // mismatch). Validation is still a diagnostic: start without it and say why.
                        System.err.println("[Celtium] CELTIUM_VALIDATION_UNAVAILABLE reason=" + error.getMessage()
                                + " (continuing without validation)");
                        validation = false;
                    }
                }
            }
            if (!enabledInstanceExtensions.isEmpty()) {
                enabledInstanceExtensions.add(0, EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                if (!validation) {
                    enabledInstanceExtensions.remove(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
                }
                PointerBuffer names = stack.mallocPointer(enabledInstanceExtensions.size());
                for (String name : enabledInstanceExtensions) {
                    names.put(stack.UTF8(name));
                }
                names.flip();
                createInfo.ppEnabledExtensionNames(names);
            }
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateInstance(createInfo, null, handle), "vkCreateInstance");
            this.instance = new VkInstance(handle.get(0), createInfo);
            this.enabledInstanceExtensions = java.util.Collections.unmodifiableSet(
                    new java.util.HashSet<String>(enabledInstanceExtensions));
            if (validation) {
                this.debugMessenger = ValidationBridge.createMessenger(this, messengerInfo);
            }
        }
    }

    /**
     * Matches the physical device by GL device UUID. The rules are the ones {@link VulkanBootstrap} proves on the
     * target GPUs: a CPU device and a device without Vulkan 1.2 are rejected, the UUID must equal one of the GL
     * device UUIDs, and there is no fallback to a different GPU. The first match wins.
     */
    private void matchPhysicalDevice(GlDeviceIdentity openGl) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            check(vkEnumeratePhysicalDevices(this.instance, count, null), "vkEnumeratePhysicalDevices(count)");
            if (count.get(0) == 0) {
                throw new IllegalStateException("No Vulkan physical device is present.");
            }
            PointerBuffer devices = stack.mallocPointer(count.get(0));
            check(vkEnumeratePhysicalDevices(this.instance, count, devices), "vkEnumeratePhysicalDevices");
            for (int i = 0; i < count.get(0); i++) {
                VkPhysicalDevice candidate = new VkPhysicalDevice(devices.get(i), this.instance);
                VkPhysicalDeviceIDProperties ids = VkPhysicalDeviceIDProperties.calloc(stack).sType$Default();
                VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default()
                        .pNext(ids.address());
                vkGetPhysicalDeviceProperties2(candidate, properties);
                String uuid = GlDeviceIdentity.hex(ids.deviceUUID());
                if (openGl != null && !openGl.deviceUuids.contains(uuid)) {
                    continue;
                }
                if (properties.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_CPU
                        && !ALLOW_CPU_DEVICE) {
                    // A CPU device is never chosen by default: on a real system a software ICD sits alongside the
                    // GPUs, and falling back to it would render slowly rather than report the failure. Test legs
                    // that intend it (a host with no DRM device at all) opt in explicitly.
                    continue;
                }
                int apiVersion = properties.properties().apiVersion();
                if (VK_VERSION_MAJOR(apiVersion) < REQUIRED_API_MAJOR
                        || (VK_VERSION_MAJOR(apiVersion) == REQUIRED_API_MAJOR
                        && VK_VERSION_MINOR(apiVersion) < REQUIRED_API_MINOR)) {
                    continue;
                }
                // Audit B02: the fd-sharing extensions are part of the *interop* contract, not of the native one. A
                // candidate is now skipped for lacking them only when this context has an OpenGL identity to share
                // images with; in the GL-less configuration there is no other end of a fd, so requiring them excluded
                // otherwise valid devices (and tied the path to Linux fd semantics). The check moved into the loop
                // for the same audit item: "choose first, then refuse the capability" turned a device that does not
                // satisfy the contract into a failure after selection instead of a candidate that is passed over.
                if (this.interopPath) {
                    Set<String> candidateExtensions = deviceExtensionNames(stack, candidate);
                    if (!candidateExtensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)
                            || !candidateExtensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)) {
                        if (this.skippedForInteropExtensions == null) {
                            this.skippedForInteropExtensions = new ArrayList<String>();
                        }
                        this.skippedForInteropExtensions.add(properties.properties().deviceNameString());
                        continue;
                    }
                }
                // The push-constant budget is part of the device contract, and it is checked here so a device that
                // cannot carry it is *passed over at selection* with its own number rather than failing later inside
                // pipeline creation. Vulkan guarantees only 128 bytes (1.0-1.3; 256 from 1.4), and the renderer spends
                // PUSH_CONSTANT_BYTES_USED per immediate draw: a 128-byte device is not "supported" - the capability
                // branch that would move the payload to a UBO (task-29/57) does not exist yet - so it is skipped
                // rather than selected and then refused.
                int candidatePushLimit = properties.properties().limits().maxPushConstantsSize();
                if (candidatePushLimit < PUSH_CONSTANT_BYTES_USED) {
                    if (this.skippedForPushConstants == null) {
                        this.skippedForPushConstants = new ArrayList<String>();
                    }
                    this.skippedForPushConstants.add(properties.properties().deviceNameString()
                            + "(maxPushConstantsSize=" + candidatePushLimit + " < " + PUSH_CONSTANT_BYTES_USED + ")");
                    continue;
                }
                this.physicalDevice = candidate;
                // Register it with the depth-format contract here, where a device first exists: the terrain pipeline and
                // the render-pass builder are static callers that need a depth format whether or not a frame target
                // exists, and only the GL-less frame target used to choose one.
                org.soutalab.celtium.vulkan.VulkanDepthFormat.noteDevice(candidate);
                // The same number, for the mod-program lowering: glLinkProgram reaches the translator through a static
                // registry with no context in scope, and the translator used to refuse every block above the 128-byte
                // Vulkan minimum - including the 192-byte fixed-function emulation program GLSM generates. The device
                // that got this far carries at least PUSH_CONSTANT_BYTES_USED, so its own limit is the honest ceiling.
                org.soutalab.celtium.vulkan.gl.ModPushConstantLimit.noteDevice(candidatePushLimit);
                this.deviceName = properties.properties().deviceNameString();
                this.deviceUuid = uuid;
                this.deviceApiVersion = apiVersion;
                this.deviceType = properties.properties().deviceType();
                break;
            }
            if (this.physicalDevice == null) {
                String skipped = (this.skippedForInteropExtensions == null ? ""
                        : " Candidates skipped for lacking the interop fd extensions: "
                                + String.join(", ", this.skippedForInteropExtensions) + ".")
                        + (this.skippedForPushConstants == null ? ""
                                : " Candidates skipped for a push-constant budget below " + PUSH_CONSTANT_BYTES_USED
                                        + " bytes: " + String.join(", ", this.skippedForPushConstants)
                                        + " (the immediate draws spend that much and the UBO fallback is not"
                                        + " implemented).");
                throw new IllegalStateException(openGl == null
                        ? "No Vulkan physical device satisfies the requirements (no OpenGL identity to match against, which"
                                + " is the no-bridge case)." + skipped
                        : "No Vulkan physical device matches the OpenGL device UUID "
                                + String.join(", ", openGl.deviceUuids)
                                + ". A different driver or a software Vulkan device is not used instead." + skipped);
            }
            if (openGl == null) {
                System.out.println("[Celtium] device chosen without matching an OpenGL device: " + this.deviceName
                        + " (" + this.deviceUuid + ")");
            }
            // The authoritative device line, printed once the choice is made and before anything can fail later: the
            // harness gates --require-gpu on this rather than on the GL renderer string, which in the GL-less
            // configuration is the stub's own name and therefore says nothing about which GPU is actually rendering.
            // `matchedOpenGl=false` is the honest form of "no GL identity was available to pin the choice", i.e. the
            // first capable device was taken - a run in that state is a measurement of whatever device this names.
            matchedOpenGlDevice = openGl != null ? 1 : 0;
            System.out.println("[Celtium] CELTIUM_NATIVE_DEVICE name=\"" + this.deviceName + "\" uuid="
                    + this.deviceUuid + " matchedOpenGl=" + (openGl != null));
            Set<String> extensions = deviceExtensionNames(stack, this.physicalDevice);
            // Audit B02: on the interop path the fd extensions are still the contract, and a device that lacked them
            // was already skipped above - so this check can only fire if the device list changed under us. On the
            // native (GL-less) path nothing is required of them, and the line below says what the contract is instead.
            if (this.interopPath) {
                List<String> missing = new ArrayList<String>();
                if (!extensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)) {
                    missing.add(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
                }
                if (!extensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)) {
                    missing.add(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
                }
                if (!missing.isEmpty()) {
                    throw new IllegalStateException("The matched Vulkan device " + this.deviceName
                            + " is missing required interop device extensions: " + String.join(", ", missing) + ".");
                }
            }
            this.advertisedDeviceExtensions = extensions;
        }
    }

    /** Devices passed over because the interop path requires the fd-sharing extensions and they lack them. */
    private List<String> skippedForInteropExtensions;
    /** Candidates passed over because their push-constant limit cannot carry what the renderer spends per draw. */
    private List<String> skippedForPushConstants;
    /** The extensions the chosen device advertised, kept until the contract line can be printed with the queue. */
    private Set<String> advertisedDeviceExtensions;
    /** The device extensions this context actually enabled, named in the contract line rather than implied. */
    private List<String> enabledDeviceExtensions = new ArrayList<String>();
    /** Whether VK_KHR_dynamic_rendering was enabled on the device, which the shaderpack path builds pipelines on. */
    private boolean dynamicRenderingSupported;
    /** {@code VkPhysicalDeviceVulkan11Features.shaderDrawParameters}: gl_DrawID in shaders, Vulkan 1.1. */
    private boolean shaderDrawParametersSupported;
    /** Whether the device can index one sampled-image array from the draw (section 11.6). */
    private boolean textureArraySupported;

    /** {@return whether one sampled-image array can be indexed from the draw: the shared-descriptor design's precondition}. */
    public boolean textureArraySupported() {
        return this.textureArraySupported;
    }

    /** {@return whether dynamic rendering is enabled on this device}. */
    public boolean dynamicRenderingSupported() {
        return dynamicRenderingSupported;
    }

    /** Whether multiDrawIndirect was reported by the device and therefore enabled at creation. */
    private boolean multiDrawIndirectSupported;
    /** Whether pipelineStatisticsQuery was reported by the device and therefore enabled at creation. */
    private boolean pipelineStatisticsQuerySupported;
    private boolean storageImageReadWithoutFormatSupported;
    private boolean storageImageWriteWithoutFormatSupported;
    /** Whether drawIndirectFirstInstance was reported by the device and therefore enabled at creation. */
    private boolean drawIndirectFirstInstanceSupported;
    /** Whether {@code VkPhysicalDeviceVulkan12Features.drawIndirectCount} was reported and enabled (E10). */
    private boolean drawIndirectCountSupported;
    /**
     * Whether {@code VkPhysicalDeviceVulkan12Features.timelineSemaphore} was reported and enabled. Unlike the
     * optional features around it this one is required: the new architecture synchronizes exclusively through
     * the one timeline semaphore, and device creation refuses a device that cannot offer it.
     */
    private boolean timelineSemaphoreSupported;
    /** VkPhysicalDeviceLimits.maxDrawIndirectCount, a limit rather than a feature (0 when unreported). */
    private int maxDrawIndirectCount;
    /**
     * Whether {@code VK_EXT_mesh_shader} was advertised and both of its features (mesh and task)
     * were reported and enabled at creation (item 652, stage 0).
     */
    private boolean meshShaderSupported;

    /** {@return whether the device was created with {@code multiDrawIndirect} enabled}. */
    public boolean multiDrawIndirectEnabled() {
        return this.multiDrawIndirectSupported;
    }

    /** {@return whether the device was created with {@code pipelineStatisticsQuery} enabled}. */
    public boolean pipelineStatisticsQuerySupported() {
        return this.pipelineStatisticsQuerySupported;
    }

    /** Both Vulkan formatless storage-image capabilities were queried and enabled at logical-device creation. */
    public boolean celtium$storageImagesWithoutFormat() {
        return this.storageImageReadWithoutFormatSupported && this.storageImageWriteWithoutFormatSupported;
    }

    /**
     * {@return whether the device was created with {@code drawIndirectFirstInstance} enabled}.
     * <p>
     * The terrain indirect path keeps {@code firstInstance = 0} either way, so this is reported rather than required;
     * it is what a path that wanted to instance across sections would need.
     */
    public boolean drawIndirectFirstInstanceEnabled() {
        return this.drawIndirectFirstInstanceSupported;
    }

    /**
     * {@return whether the device was created with {@code drawIndirectCount} enabled}.
     * <p>
     * E10's premise (design section 4): {@code vkCmdDrawIndexedIndirectCount} is Vulkan 1.2
     * core, but the call is illegal unless the feature was enabled at device creation, which
     * is what the query-and-enable block in device creation now does.
     */
    public boolean drawIndirectCountEnabled() {
        return this.drawIndirectCountSupported;
    }

    /**
     * {@return whether the device was created with {@code VK_EXT_mesh_shader} enabled and both
     * the mesh and task shader features on}.
     * <p>
     * Item 652, stage 0: an optional capability in the same shape as
     * {@link #drawIndirectCountEnabled()}. The gate is the pair of features, not the extension
     * alone, because the design's stage-2 form issues through task shaders; a device reporting
     * only the mesh half keeps the indirect path as its only terrain route. The Tier-B issuance
     * stage ({@code org.soutalab.celtium.vulkan.meshb.MeshTerrainExecutor}) gates on this answer.
     */
    public boolean meshShaderEnabled() {
        return this.meshShaderSupported;
    }

    /** {@return the device's {@code maxDrawIndirectCount}}; 0 means the limit was never read (OPT-09's clamp). */
    public int maxDrawIndirectCount() {
        return this.maxDrawIndirectCount;
    }
    /** The frame target's colour format and sample count: two of the native contract's fixed terms. */
    private static final String COLOR_FORMAT_NAME = "R8G8B8A8_UNORM";
    private static final int REQUIRED_SAMPLES = 1;
    /**
     * The largest push-constant block the immediate path uses: the lit ITEM layout's 256 bytes
     * ({@code VulkanImmediateItemPipeline.PUSH_CONSTANT_BYTES_LIT}) - the shared 128, the five-vec4 lighting payload,
     * the light-map coordinate pair at 208 and the fog block at 224/240. The fog-carrying POSITION layout spends 208.
     * <p>
     * This number is the device contract, not a report: a device whose {@code maxPushConstantsSize} is below it is
     * skipped at selection with its own number, because the lit pipeline's layout declares the whole block and a
     * shorter device would fail inside {@code vkCreatePipelineLayout}. It used to read 208 while the lit layout already
     * declared 216, so a device in that 8-byte window would have been selected and then failed; the number now names
     * the largest block any family declares.
     */
    private static final int PUSH_CONSTANT_BYTES_USED = 256;

    /**
     * Prints the device contract this run was selected under, once, as {@code CELTIUM_DEVICE_CONTRACT}.
     * <p>
     * The audit's B02 asked for the native contract to be named rather than implied: the swapchain, a queue that can
     * do graphics and transfer, the colour and depth formats, the API version, and the limits the renderer actually
     * uses - instead of the retired interop extensions. The line carries both sides: what was required and which of it
     * the device has, plus whether the fd extensions are part of the contract at all (they are not, on this path).
     */
    private void reportDeviceContract(MemoryStack stack, Set<String> extensions) {
        VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(this.physicalDevice, properties);
        List<String> required = new ArrayList<String>();
        required.add("api>=" + REQUIRED_API_MAJOR + "." + REQUIRED_API_MINOR);
        required.add("queue=graphics+transfer");
        if (this.interopPath) {
            required.add(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
            required.add(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
        }
        // The swapchain extension is enabled unconditionally (see createLogicalDevice), so the contract
        // names it unconditionally too.
        required.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        // Choosing it here is the same device the renderer will use, and it makes the contract line name the format
        // instead of "unresolved" (the chooser is idempotent and registers the device on first use).
        int depthFormat = VulkanDepthFormat.chosen(this.physicalDevice);
        required.add("color=" + COLOR_FORMAT_NAME + " depth=" + VulkanDepthFormat.chosenName()
                + "(0x" + Integer.toHexString(depthFormat) + ")"
                + " samples=" + REQUIRED_SAMPLES);
        int pushConstantLimit = properties.limits().maxPushConstantsSize();
        // Kept for glGetIntegerv(GL_MAX_TEXTURE_SIZE): the stub answered a fixed 16384, which is wrong on a device whose
        // limit is lower - a mod sizing a texture from that answer asked for an image this device cannot create.
        this.maxImageDimension2D = properties.limits().maxImageDimension2D();
        // The requirement is named, not assumed: the number comes from the same constant the pipeline layouts
        // spend, so the contract line and the code cannot drift apart.
        required.add("pushConstants>=" + PUSH_CONSTANT_BYTES_USED);
        System.out.println("[Celtium] CELTIUM_DEVICE_CONTRACT name=\"" + this.deviceName + "\" uuid=" + this.deviceUuid
                + " deviceType=" + properties.deviceType()
                + " apiVersion=" + VK_VERSION_MAJOR(properties.apiVersion()) + "."
                + VK_VERSION_MINOR(properties.apiVersion())
                + " queueFamily=" + this.queueFamilyIndex
                + " required=[" + String.join(", ", required) + "]"
                + " fdInterop=" + this.interopPath
                + " enabledDeviceExtensions=[" + String.join(", ", this.enabledDeviceExtensions) + "]"
                // Available, not enabled: whether the animated-sprite path could write the atlas from the host without a
                // command buffer per sprite (the warm-window profile puts that path at ~1/5 of the client tick).
                + " hostImageCopyAvailable=" + celtium$deviceOffers("VK_EXT_host_image_copy")
                + " limits=[maxPushConstantsSize=" + pushConstantLimit + " immediatePushBytes=" + PUSH_CONSTANT_BYTES_USED
                + " maxImageDimension2D=" + properties.limits().maxImageDimension2D() + "]"
                + " indirect=[multiDrawIndirect=" + this.multiDrawIndirectSupported
                + " drawIndirectFirstInstance=" + this.drawIndirectFirstInstanceSupported
                + " drawIndirectCount=" + this.drawIndirectCountSupported
                + " maxDrawIndirectCount=" + this.maxDrawIndirectCount + "]"
                + " meshShader=" + this.meshShaderSupported
                + " advertised=[externalMemoryFd="+ extensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)
                + " externalSemaphoreFd=" + extensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)
                + " swapchain=" + extensions.contains(VK_KHR_SWAPCHAIN_EXTENSION_NAME) + "]"
                + (this.skippedForInteropExtensions == null ? ""
                        : " skippedForInteropExtensions=[" + String.join(", ", this.skippedForInteropExtensions) + "]")
                + (this.skippedForPushConstants == null ? ""
                        : " skippedForPushConstants=[" + String.join(", ", this.skippedForPushConstants) + "]"));
        reportDriverInfo(stack);
    }

    // ---- what is rendering right now: the real driver, from the device that renders (task-46) ----------------

    /** The rendering device's own identity, queried once and named in one line. "unavailable" when it cannot be. */
    private static volatile String driverLine = "unavailable";
    /**
     * Whether the selected device was pinned by an OpenGL identity, i.e. the {@code matchedOpenGl=} field of
     * {@code CELTIUM_NATIVE_DEVICE}. Static because the F3 section asks the question before a world exists and must
     * not create a context to answer it; -1 is "no device has been selected yet", which is not the same as false.
     */
    private static volatile int matchedOpenGlDevice = -1;
    /** Vulkan loader version, queried once and guarded: a missing loader must not break a support line. */
    private static volatile String loaderVersion = "unqueried";
    private static volatile String driverName = "unavailable";
    private static volatile String driverVersion = "unavailable";
    private static volatile String deviceLine = "unavailable";

    /**
     * {@return the driver-info summary} - the values behind F3 and {@code GL_RENDERER}: device name, uuid, device
     * type, Vulkan API version and the driver's own {@code driverName}/{@code driverID}/{@code driverInfo}/
     * {@code driverVersion} from {@code VkPhysicalDeviceDriverProperties}, taken from the physical device this
     * context actually created a logical device from.
     * <p>
     * This is deliberately not the GL bootstrap probe's {@code glRenderer}/{@code vulkanDevice}: that probe answers a
     * different and earlier question (which GL identity exists and what may be shared with it by fd) and the plan
     * records that its result must not be read as the live renderer's state. The authoritative line for "what renders
     * now" is {@code CELTIUM_DRIVER_INFO} below, printed from the live context.
     */
    public static String driverSummary() {
        return driverLine;
    }

    /** {@return the driver's own name} ({@code driverName}), or "unavailable". */
    public static String driverName() {
        return driverName;
    }

    /** {@return the rendering device and its API version}, or "unavailable". */
    public static String deviceSummary() {
        return deviceLine;
    }

    /**
     * {@return whether the selected device was pinned by an OpenGL identity}, from the same value
     * {@code CELTIUM_NATIVE_DEVICE ... matchedOpenGl=} prints; {@code null} before any device is selected.
     * <p>
     * Null rather than false on purpose: "no device yet" and "selected without a GL identity" are different answers,
     * and the F3 section shows them differently.
     */
    public static Boolean matchedOpenGlDevice() {
        int recorded = matchedOpenGlDevice;
        return recorded < 0 ? null : Boolean.valueOf(recorded != 0);
    }

    /**
     * {@return the Vulkan loader's version, or "unavailable"}.
     * <p>
     * Queried through {@code VK.getInstanceVersionSupported()}, which loads the loader but creates no instance, device
     * or swapchain - the F3 section must be able to name the loader without initialising Vulkan. Cached, and every
     * failure (no native loader, no bindings) answers "unavailable" rather than throwing into a support line.
     */
    public static String loaderVersion() {
        String cached = loaderVersion;
        if (!"unqueried".equals(cached)) {
            return cached;
        }
        String resolved;
        try {
            resolved = versionString(VK.getInstanceVersionSupported());
        } catch (RuntimeException | LinkageError unavailable) {
            resolved = "unavailable";
        }
        loaderVersion = resolved;
        return resolved;
    }

    /**
     * Queries {@code VkPhysicalDeviceDriverProperties} (core in Vulkan 1.2, which this context requires) and prints
     * {@code CELTIUM_DRIVER_INFO}. A value the driver does not fill in is reported as "unavailable" - never as a stub
     * string, because a driver string that is not the driver's own is worse than a missing one.
     */
    private void reportDriverInfo(MemoryStack stack) {
        String name = "unavailable";
        String info = "unavailable";
        int id = -1;
        long version = 0L;
        try {
            VkPhysicalDeviceDriverProperties driver =
                    VkPhysicalDeviceDriverProperties.calloc(stack).sType$Default();
            VkPhysicalDeviceProperties2 properties = VkPhysicalDeviceProperties2.calloc(stack).sType$Default()
                    .pNext(driver.address());
            vkGetPhysicalDeviceProperties2(this.physicalDevice, properties);
            if (driver.driverNameString() != null && !driver.driverNameString().isEmpty()) {
                name = driver.driverNameString();
            }
            if (driver.driverInfoString() != null && !driver.driverInfoString().isEmpty()) {
                info = driver.driverInfoString();
            }
            id = driver.driverID();
            // The version is not in this struct: it is VkPhysicalDeviceProperties.driverVersion.
            version = Integer.toUnsignedLong(properties.properties().driverVersion());
        } catch (RuntimeException | LinkageError error) {
            name = "unavailable (" + error.getClass().getSimpleName() + ")";
        }
        String versionText = decodeDriverVersion(id, version);
        driverName = name;
        driverVersion = versionText;
        deviceLine = this.deviceName + " api=" + VK_VERSION_MAJOR(this.deviceApiVersion) + "."
                + VK_VERSION_MINOR(this.deviceApiVersion) + " type=" + this.deviceType;
        driverLine = "driverName=\"" + name + "\" driverID=" + id + "(" + driverIdName(id) + ")"
                + " driverInfo=\"" + info + "\" driverVersion=" + version + "(" + versionText + ")"
                + " device=\"" + this.deviceName + "\" uuid=" + this.deviceUuid
                + " type=" + this.deviceType
                + " apiVersion=" + VK_VERSION_MAJOR(this.deviceApiVersion) + "."
                + VK_VERSION_MINOR(this.deviceApiVersion) + "."
                + VK_VERSION_PATCH(this.deviceApiVersion);
        System.out.println("[Celtium] CELTIUM_DRIVER_INFO " + driverLine);
    }

    /** {@return the {@code VkDriverId} as its name}, or the number when it is not one this build knows. */
    private static String driverIdName(int id) {
        switch (id) {
            case KHRDriverProperties.VK_DRIVER_ID_AMD_PROPRIETARY_KHR: return "AMD_PROPRIETARY";
            case KHRDriverProperties.VK_DRIVER_ID_AMD_OPEN_SOURCE_KHR: return "AMD_OPEN_SOURCE";
            case KHRDriverProperties.VK_DRIVER_ID_MESA_RADV_KHR: return "MESA_RADV";
            case KHRDriverProperties.VK_DRIVER_ID_NVIDIA_PROPRIETARY_KHR: return "NVIDIA_PROPRIETARY";
            case KHRDriverProperties.VK_DRIVER_ID_INTEL_PROPRIETARY_WINDOWS_KHR: return "INTEL_PROPRIETARY_WINDOWS";
            case KHRDriverProperties.VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA_KHR: return "INTEL_OPEN_SOURCE_MESA";
            // Not declared by this LWJGL build (they arrived in later headers), so the spec's numbers are used:
            // VK_DRIVER_ID_MESA_LLVMPIPE = 13, MESA_DOZEN = 18, MESA_NVK = 19.
            case 13: return "MESA_LLVMPIPE";
            case 18: return "MESA_DOZEN";
            case 19: return "MESA_NVK";
            default: return "unknown";
        }
    }

    /**
     * {@return {@code driverVersion} decoded the way its vendor encodes it} - the whole point of naming the ID first.
     * <p>
     * The raw number is meaningless without it: Mesa's drivers (RADV, ANV, llvmpipe) report their own version as
     * {@code VK_MAKE_VERSION(major, minor, patch)}, while NVIDIA packs {@code (major << 22) | (minor << 14) |
     * (subminor << 6) | patch} and AMD's proprietary driver packs its own layout. Anything else is reported raw with
     * "encoding not decoded" rather than guessed at.
     */
    private static String decodeDriverVersion(int id, long version) {
        if (id < 0) {
            return "unavailable";
        }
        if (id == KHRDriverProperties.VK_DRIVER_ID_MESA_RADV_KHR
                || id == KHRDriverProperties.VK_DRIVER_ID_INTEL_OPEN_SOURCE_MESA_KHR
                || id == KHRDriverProperties.VK_DRIVER_ID_AMD_OPEN_SOURCE_KHR
                || id == 13 || id == 18 || id == 19) {
            return VK_VERSION_MAJOR((int) version) + "." + VK_VERSION_MINOR((int) version) + "."
                    + VK_VERSION_PATCH((int) version) + " (mesa vk-version layout)";
        }
        if (id == KHRDriverProperties.VK_DRIVER_ID_NVIDIA_PROPRIETARY_KHR) {
            return ((version >> 22) & 0x3FFL) + "." + ((version >> 14) & 0xFFL) + "."
                    + ((version >> 6) & 0xFFL) + "." + (version & 0x3FL) + " (nvidia layout)";
        }
        return version + " (encoding not decoded for this driverID)";
    }

    /** Prints the device contract once the queue family is chosen, so the line can name it. */
    private void reportDeviceContractIfReady() {
        if (this.advertisedDeviceExtensions == null) {
            return;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            reportDeviceContract(stack, this.advertisedDeviceExtensions);
            this.advertisedDeviceExtensions = null;
        }
    }

    private void createLogicalDevice() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.ints(0);
            vkGetPhysicalDeviceQueueFamilyProperties(this.physicalDevice, count, null);
            if (count.get(0) == 0) {
                throw new IllegalStateException("The matched Vulkan device " + this.deviceName
                        + " reports no queue families at all.");
            }
            VkQueueFamilyProperties.Buffer families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(this.physicalDevice, count, families);
            this.queueFamilyIndex = -1;
            for (int i = 0; i < count.get(0); i++) {
                int flags = families.get(i).queueFlags();
                // Both bits are required: this class submits transfers on the same queue it records graphics work
                // for, exactly like the bootstrap contract check.
                if ((flags & VK_QUEUE_GRAPHICS_BIT) != 0 && (flags & VK_QUEUE_TRANSFER_BIT) != 0
                        && families.get(i).queueCount() > 0) {
                    this.queueFamilyIndex = i;
                    break;
                }
            }
            if (this.queueFamilyIndex < 0) {
                throw new IllegalStateException("The matched Vulkan device " + this.deviceName
                        + " has no queue family that supports both graphics and transfer.");
            }
            VkDeviceQueueCreateInfo.Buffer queues = VkDeviceQueueCreateInfo.calloc(1, stack);
            queues.get(0).sType$Default()
                    .queueFamilyIndex(this.queueFamilyIndex)
                    .pQueuePriorities(stack.floats(1.0f));
            List<String> deviceExtensions = new ArrayList<String>();
            // Audit B02: the fd-sharing extensions are enabled only on the interop path. With a GL identity there is a
            // GL image and a GL semaphore to share by fd, and the two extensions are the contract for that; without
            // one (the GL-less configuration) enabling them asks the driver for a capability this path never uses, and
            // on a device that does not advertise them the unconditional enable would have failed the device creation
            // for a path that does not need it.
            if (this.interopPath) {
                deviceExtensions.add(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
                deviceExtensions.add(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
            }
            // The swapchain functions live in a device extension, so a surface alone is not enough: without it
            // enabled the loader table has no vkCreateSwapchainKHR and LWJGL's own capability check reports a
            // NullPointerException on the missing function pointer, which is exactly what the first probes hit.
            // Enabled whenever the device advertises it, independently of the probe: the window-backend
            // present path always creates a swapchain, so gating the extension on anything else would leave
            // the only present path unable to create its swapchain.
            Set<String> available = deviceExtensionNames(stack, this.physicalDevice);
            if (available.contains(VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
                deviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
            }
            // Dynamic rendering, for the shaderpack path: a pack's fragment stage writes however many colour attachments
            // its DRAWBUFFERS line asks for, and a VkPipeline has to be created against render targets whose formats
            // match. The extension form is used rather than the Vulkan 1.3 core feature because this instance is created
            // at API 1.2 - the KHR extension works from 1.0 and every driver that has the core feature advertises it.
            Set<String> advertised = deviceExtensionNames(stack, this.physicalDevice);
            this.dynamicRenderingSupported = advertised.contains(
                    org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
            if (this.dynamicRenderingSupported) {
                deviceExtensions.add(org.lwjgl.vulkan.KHRDynamicRendering.VK_KHR_DYNAMIC_RENDERING_EXTENSION_NAME);
            }
            // Mesh shading (item 652, stage 0): an optional capability in the same shape as dynamic
            // rendering above - the extension is enabled only when the device advertises it AND
            // reports both features (the design's gate is the pair; see meshShaderEnabled()). The
            // feature query runs here, beside the extension decision, because the enabled list is
            // frozen into the names buffer below; the enable struct joins the create-info pNext
            // chain further down, appended to its end like the feature blocks around it. A device
            // without the pair is created exactly as before: no extension, no struct, and the
            // indirect terrain path stays its only route.
            if (advertised.contains(org.lwjgl.vulkan.EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME)) {
                org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT supportedMesh =
                        org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 meshFeatures2 =
                        org.lwjgl.vulkan.VkPhysicalDeviceFeatures2.calloc(stack).sType$Default()
                                .pNext(supportedMesh.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, meshFeatures2);
                this.meshShaderSupported = supportedMesh.meshShader() && supportedMesh.taskShader();
                if (this.meshShaderSupported) {
                    deviceExtensions.add(
                            org.lwjgl.vulkan.EXTMeshShader.VK_EXT_MESH_SHADER_EXTENSION_NAME);
                }
            }
            PointerBuffer names = stack.mallocPointer(deviceExtensions.size());
            for (String extension : deviceExtensions) {
                names.put(stack.UTF8(extension));
            }
            names.flip();
            this.enabledDeviceExtensions = new ArrayList<String>(deviceExtensions);
            // Optional features are opt-in at device creation: a VkDeviceCreateInfo with no pEnabledFeatures enables
            // none of them, so a call that requires one is illegal however well the driver advertises it -
            // vkCmdDrawIndexedIndirect needs multiDrawIndirect, for instance (OPT-09's "query AND enable"). Each
            // feature is enabled only when the device reports it, because enabling an unsupported one fails
            // vkCreateDevice outright, which would leave no renderer at all.
            org.lwjgl.vulkan.VkPhysicalDeviceFeatures supportedFeatures = org.lwjgl.vulkan.VkPhysicalDeviceFeatures.calloc(stack);
            vkGetPhysicalDeviceFeatures(this.physicalDevice, supportedFeatures);
            org.lwjgl.vulkan.VkPhysicalDeviceFeatures enabledFeatures = org.lwjgl.vulkan.VkPhysicalDeviceFeatures.calloc(stack);
            this.multiDrawIndirectSupported = supportedFeatures.multiDrawIndirect();
            this.drawIndirectFirstInstanceSupported = supportedFeatures.drawIndirectFirstInstance();
            this.storageImageReadWithoutFormatSupported = supportedFeatures.shaderStorageImageReadWithoutFormat();
            this.storageImageWriteWithoutFormatSupported = supportedFeatures.shaderStorageImageWriteWithoutFormat();
            this.pipelineStatisticsQuerySupported = supportedFeatures.pipelineStatisticsQuery();
            enabledFeatures.multiDrawIndirect(this.multiDrawIndirectSupported);
            enabledFeatures.drawIndirectFirstInstance(this.drawIndirectFirstInstanceSupported);
            enabledFeatures.shaderStorageImageReadWithoutFormat(this.storageImageReadWithoutFormatSupported);
            enabledFeatures.shaderStorageImageWriteWithoutFormat(this.storageImageWriteWithoutFormatSupported);
            enabledFeatures.pipelineStatisticsQuery(this.pipelineStatisticsQuerySupported);
            org.lwjgl.vulkan.VkPhysicalDeviceProperties deviceProperties =
                    org.lwjgl.vulkan.VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(this.physicalDevice, deviceProperties);
            this.maxDrawIndirectCount = deviceProperties.limits().maxDrawIndirectCount();

            // What a shared-descriptor design (architecture section 11.6) can build here: the array sizes the device
            // promises and whether it can index them non-uniformly. Logged once per device because the answer decides
            // whether a window can bind one set for every group, and it differs per driver.
            {
                org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures indexing =
                        org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 query =
                        org.lwjgl.vulkan.VkPhysicalDeviceFeatures2.calloc(stack).sType$Default()
                                .pNext(indexing.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, query);
                org.lwjgl.vulkan.VkPhysicalDeviceLimits limits = deviceProperties.limits();
                System.out.println("[Celtium] CELTIUM_DEVICE_DESCRIPTOR_LIMITS maxSampledPerStage="
                        + limits.maxPerStageDescriptorSampledImages() + " maxSampledPerSet="
                        + limits.maxDescriptorSetSampledImages() + " maxStoragePerStage="
                        + limits.maxPerStageDescriptorStorageBuffers() + " maxStoragePerSet="
                        + limits.maxDescriptorSetStorageBuffers() + " nonUniformSampledArray="
                        + indexing.shaderSampledImageArrayNonUniformIndexing() + " runtimeArray="
                        + indexing.runtimeDescriptorArray() + " updateAfterBindSampled="
                        + indexing.descriptorBindingSampledImageUpdateAfterBind());
            }
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack).sType$Default()
                    .pQueueCreateInfos(queues)
                    .ppEnabledExtensionNames(names)
                    .pEnabledFeatures(enabledFeatures);
            org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeaturesKHR dynamicRendering = null;
            if (this.dynamicRenderingSupported) {
                dynamicRendering = org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack)
                        .sType$Default().dynamicRendering(true);
                org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeaturesKHR supported =
                        org.lwjgl.vulkan.VkPhysicalDeviceDynamicRenderingFeaturesKHR.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 features2 =
                        org.lwjgl.vulkan.VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(supported.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, features2);
                this.dynamicRenderingSupported = supported.dynamicRendering();
                dynamicRendering.dynamicRendering(this.dynamicRenderingSupported);
                if (this.dynamicRenderingSupported) {
                    createInfo.pNext(dynamicRendering.address());
                }
            }
            // gl_DrawID is the mechanism the per-group data merge is built on: one command draws many groups and the
            // shader indexes the group's block by gl_DrawID. It needs the Vulkan 1.1 shaderDrawParameters feature, so the
            // device is asked for it here and the answer is reported - a device without it can still render, it just
            // cannot take that path, and the report is what keeps that difference visible instead of inferred.
            boolean askDrawParameters = deviceProperties.apiVersion() >= org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;
            org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features drawParametersFeatures = null;
            if (askDrawParameters) {
                org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features supported =
                        org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 features2 = org.lwjgl.vulkan.VkPhysicalDeviceFeatures2
                        .calloc(stack).sType$Default().pNext(supported.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, features2);
                this.shaderDrawParametersSupported = supported.shaderDrawParameters();
                if (this.shaderDrawParametersSupported) {
                    drawParametersFeatures = org.lwjgl.vulkan.VkPhysicalDeviceVulkan11Features.calloc(stack)
                            .sType$Default().shaderDrawParameters(true);
                    // The chain already carries the dynamic-rendering features on some devices, so this one is appended
                    // to the end of it rather than replacing it.
                    if (createInfo.pNext() != 0L) {
                        org.lwjgl.system.MemoryUtil.memPutLong(drawParametersFeatures.address() + 8L, createInfo.pNext());
                    }
                    createInfo.pNext(drawParametersFeatures.address());
                }
            }
            // E10's premise (design section 4.2): vkCmdDrawIndexedIndirectCount is Vulkan 1.2 core, but the call is
            // only legal when the device was created with the drawIndirectCount feature enabled, and the pNext chain
            // carried no Vulkan12Features struct at all - so the feature was neither queried nor enabled, on any
            // device. The block follows the Vulkan11 draw-parameters shape above exactly: asked only when the
            // device's API version can answer, enabled only when reported (enabling an unreported feature fails
            // vkCreateDevice), and appended to the end of the chain rather than replacing its head.
            // The new architecture's timeline semaphore is queried in the same single pass and carried by the
            // same struct: a pNext chain may not hold two VkPhysicalDeviceVulkan12Features, so the two features
            // share one, each field set to exactly what the device reported. timelineSemaphore differs from
            // drawIndirectCount in one respect: it is required, not reported-and-optional, because every
            // submission path synchronizes through the frame timeline now - a device without it is refused
            // here, by name, rather than discovered later by a failing renderer.
            // The descriptor-indexing query runs BEFORE the Vulkan 1.2 block below, not after it
            // (arch-phase1-fix): VUID-VkDeviceCreateInfo-pNext-02830 forbids a pNext chain that
            // holds both VkPhysicalDeviceVulkan12Features and the standalone
            // VkPhysicalDeviceDescriptorIndexingFeatures - on a 1.2+ device the indexing fields
            // are promoted into the Vulkan12 struct, so they are queried here and folded into
            // that one struct below. The Phase 1 gate leg (2026-11-25, UHD 630) failed device
            // creation validation with exactly this VUID because the chain carried both.
            boolean indexingNonUniform;
            boolean indexingRuntimeArray;
            boolean indexingAfterBind;
            boolean indexingPartiallyBound;
            boolean indexingUnusedWhilePending;
            {
                org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures supported =
                        org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 features2 = org.lwjgl.vulkan.VkPhysicalDeviceFeatures2
                        .calloc(stack).sType$Default().pNext(supported.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, features2);
                indexingNonUniform = supported.shaderSampledImageArrayNonUniformIndexing();
                indexingRuntimeArray = supported.runtimeDescriptorArray();
                indexingAfterBind = supported.descriptorBindingSampledImageUpdateAfterBind();
                indexingPartiallyBound = supported.descriptorBindingPartiallyBound();
                // Writing a new array index while earlier frames that bound the set are still executing is only legal
                // with UPDATE_UNUSED_WHILE_PENDING; the model instances register textures at exactly that time.
                indexingUnusedWhilePending = supported.descriptorBindingUpdateUnusedWhilePending();
                this.textureArraySupported = indexingNonUniform && indexingRuntimeArray && indexingAfterBind
                        && indexingPartiallyBound && indexingUnusedWhilePending;
            }
            boolean askIndirectCount = deviceProperties.apiVersion() >= org.lwjgl.vulkan.VK12.VK_API_VERSION_1_2;
            if (askIndirectCount) {
                org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features supported12 =
                        org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 features12 = org.lwjgl.vulkan.VkPhysicalDeviceFeatures2
                        .calloc(stack).sType$Default().pNext(supported12.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, features12);
                this.drawIndirectCountSupported = supported12.drawIndirectCount();
                this.timelineSemaphoreSupported = supported12.timelineSemaphore();
                if (!this.timelineSemaphoreSupported) {
                    throw new IllegalStateException("The matched Vulkan device " + this.deviceName
                            + " does not report the timelineSemaphore feature (Vulkan 1.2 core). The new"
                            + " architecture synchronizes exclusively through one timeline semaphore, so this"
                            + " device cannot run the renderer.");
                }
                if (this.drawIndirectCountSupported || this.timelineSemaphoreSupported
                        || this.textureArraySupported) {
                    org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features vulkan12Features =
                            org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features.calloc(stack)
                                    .sType$Default()
                                    .drawIndirectCount(this.drawIndirectCountSupported)
                                    .timelineSemaphore(this.timelineSemaphoreSupported)
                                    // Promoted descriptor-indexing fields (VUID-02830): on a
                                    // 1.2+ device these live on this struct, never on a second
                                    // standalone indexing struct in the same chain.
                                    .shaderSampledImageArrayNonUniformIndexing(this.textureArraySupported)
                                    .runtimeDescriptorArray(this.textureArraySupported)
                                    .descriptorBindingSampledImageUpdateAfterBind(this.textureArraySupported)
                                    .descriptorBindingPartiallyBound(this.textureArraySupported)
                                    .descriptorBindingUpdateUnusedWhilePending(this.textureArraySupported)
                                    .descriptorBindingVariableDescriptorCount(this.textureArraySupported);
                    if (createInfo.pNext() != 0L) {
                        org.lwjgl.system.MemoryUtil.memPutLong(vulkan12Features.address() + 8L, createInfo.pNext());
                    }
                    createInfo.pNext(vulkan12Features.address());
                }
            }
            // The mesh-shader enable (item 652, stage 0), appended to the end of the chain in the
            // same shape as the blocks above: the extension joined the enabled list when both
            // features reported (beside the dynamic-rendering decision), and this struct is what
            // turns the pair on for the created device. Enabling a feature the device did not
            // report would fail vkCreateDevice, which is why the struct exists only on the
            // supported branch.
            if (this.meshShaderSupported) {
                org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT meshFeatures =
                        org.lwjgl.vulkan.VkPhysicalDeviceMeshShaderFeaturesEXT.calloc(stack)
                                .sType$Default().meshShader(true).taskShader(true);
                if (createInfo.pNext() != 0L) {
                    org.lwjgl.system.MemoryUtil.memPutLong(meshFeatures.address() + 8L, createInfo.pNext());
                }
                createInfo.pNext(meshFeatures.address());
            }
            // One descriptor set for every texture a window draws (architecture section 11.6): the shaders index an
            // array of sampled images with a value that comes from the draw itself, which is why the array needs
            // non-uniform indexing, a runtime-sized binding and update-after-bind - descriptors are written as textures
            // are registered while recorded buffers may still reference the set. All three are optional, so each is
            // asked for separately and reported; a device without them keeps the per-group sets.
            org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures indexingFeatures = null;
            {
                // The query itself moved above the Vulkan 1.2 block (arch-phase1-fix,
                // VUID-VkDeviceCreateInfo-pNext-02830). Only a pre-1.2 device still gets the
                // standalone struct: on 1.2+ the fields were folded into the single
                // VkPhysicalDeviceVulkan12Features above, and a second struct is the exact
                // chain the validation layer rejects.
                if (!askIndirectCount && this.textureArraySupported) {
                    indexingFeatures = org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack)
                            .sType$Default()
                            .shaderSampledImageArrayNonUniformIndexing(true)
                            .runtimeDescriptorArray(true)
                            .descriptorBindingSampledImageUpdateAfterBind(true)
                            .descriptorBindingPartiallyBound(true)
                            .descriptorBindingUpdateUnusedWhilePending(true)
                            .descriptorBindingVariableDescriptorCount(true);
                    if (createInfo.pNext() != 0L) {
                        org.lwjgl.system.MemoryUtil.memPutLong(indexingFeatures.address() + 8L, createInfo.pNext());
                    }
                    createInfo.pNext(indexingFeatures.address());
                }
            }
            PointerBuffer handle = stack.mallocPointer(1);
            int createResult = vkCreateDevice(this.physicalDevice, createInfo, null, handle);
            if (createResult != VK_SUCCESS) {
                // Not the generic check() (item 529, candidate (a)): a creation refusal during a renderer
                // reload reached the user as a bare "returned VkResult -3" in a crash report, while the
                // device loss that caused it sat in a different file (the device-lost log), and joining
                // the two cost a reporter round-trip. The failure therefore carries its own context: the
                // parameters it was asked with, and the loss this process already reported, when it did.
                throw new IllegalStateException(celtium$deviceCreationFailure(createResult, this.deviceName,
                        deviceExtensions,
                        "multiDrawIndirect=" + this.multiDrawIndirectSupported
                                + " drawIndirectFirstInstance=" + this.drawIndirectFirstInstanceSupported
                                + " shaderStorageImageReadWithoutFormat="
                                + this.storageImageReadWithoutFormatSupported
                                + " shaderStorageImageWriteWithoutFormat="
                                + this.storageImageWriteWithoutFormatSupported
                                + " pipelineStatisticsQuery=" + this.pipelineStatisticsQuerySupported
                                + " drawIndirectCount=" + this.drawIndirectCountSupported
                                + " timelineSemaphore=" + this.timelineSemaphoreSupported
                                + " shaderDrawParameters=" + this.shaderDrawParametersSupported
                                + " dynamicRendering=" + this.dynamicRenderingSupported
                                + " textureArray=" + this.textureArraySupported
                                + " meshShader=" + this.meshShaderSupported,
                        lastDeviceLossReport));
            }
            this.device = new VkDevice(handle.get(0), this.physicalDevice, createInfo);
            System.out.println("[Celtium] CELTIUM_DEVICE_FEATURES shaderDrawParameters="
                    + this.shaderDrawParametersSupported + " multiDrawIndirect=" + this.multiDrawIndirectSupported
                    + " drawIndirectCount=" + this.drawIndirectCountSupported
                    + " timelineSemaphore=" + this.timelineSemaphoreSupported
                    + " maxDrawIndirectCount=" + this.maxDrawIndirectCount
                    + " textureArray=" + this.textureArraySupported
                    + " pipelineStatisticsQuery=" + this.pipelineStatisticsQuerySupported
                    + " meshShader=" + this.meshShaderSupported);
        }
    }

    private void createQueueAndCommandPool() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer handle = stack.mallocPointer(1);
            vkGetDeviceQueue(this.device, this.queueFamilyIndex, 0, handle);
            this.queue = new VkQueue(handle.get(0), this.device);
            VkCommandPoolCreateInfo pool = VkCommandPoolCreateInfo.calloc(stack).sType$Default()
                    .queueFamilyIndex(this.queueFamilyIndex)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            LongBuffer poolHandle = stack.mallocLong(1);
            check(vkCreateCommandPool(this.device, pool, null, poolHandle), "vkCreateCommandPool");
            this.commandPool = poolHandle.get(0);
        }
    }

    /**
     * Test-only switch that hides the two interop fd extensions from the advertised set, so the "device without them"
     * configuration can be exercised on a device that has them (audit B02's acceptance asks for exactly that profile).
     * It removes them from the *enumeration* rather than skipping the check, so both directions are exercised by the
     * same code: on the interop path such a device is now skipped as a candidate, and on the GL-less path it is
     * selected and the contract line reports the fd extensions as not required and not enabled.
     */
    private static final String HIDE_FD_EXTENSIONS_PROPERTY = "celtium.vulkan.hideExternalFdExtensions";

    private static Set<String> deviceExtensionNames(MemoryStack stack, VkPhysicalDevice device) {
        IntBuffer count = stack.ints(0);
        check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, null),
                "vkEnumerateDeviceExtensionProperties(count)");
        Set<String> names = new HashSet<String>();
        if (count.get(0) == 0) {
            return names;
        }
        try (VkExtensionProperties.Buffer properties = VkExtensionProperties.calloc(count.get(0))) {
            check(vkEnumerateDeviceExtensionProperties(device, (String) null, count, properties),
                    "vkEnumerateDeviceExtensionProperties");
            for (int i = 0; i < count.get(0); i++) {
                names.add(properties.get(i).extensionNameString());
            }
        }
        if (HIDE_FD_EXTENSIONS) {
            names.remove(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME);
            names.remove(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME);
        }
        return names;
    }

    static Set<String> instanceExtensionNames(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        check(vkEnumerateInstanceExtensionProperties((String) null, count, null),
                "vkEnumerateInstanceExtensionProperties(count)");
        Set<String> names = new HashSet<String>();
        if (count.get(0) == 0) {
            return names;
        }
        try (VkExtensionProperties.Buffer properties = VkExtensionProperties.calloc(count.get(0))) {
            check(vkEnumerateInstanceExtensionProperties((String) null, count, properties),
                    "vkEnumerateInstanceExtensionProperties");
            for (int i = 0; i < count.get(0); i++) {
                names.add(properties.get(i).extensionNameString());
            }
        }
        return names;
    }

    static Set<String> instanceLayerNames(MemoryStack stack) {
        IntBuffer count = stack.ints(0);
        check(vkEnumerateInstanceLayerProperties(count, null), "vkEnumerateInstanceLayerProperties(count)");
        Set<String> names = new HashSet<String>();
        if (count.get(0) == 0) {
            return names;
        }
        try (VkLayerProperties.Buffer layers = VkLayerProperties.calloc(count.get(0))) {
            check(vkEnumerateInstanceLayerProperties(count, layers), "vkEnumerateInstanceLayerProperties");
            for (int i = 0; i < count.get(0); i++) {
                names.add(layers.get(i).layerNameString());
            }
        }
        return names;
    }

    /**
     * Makes the cached staging buffer at least {@code bytes} large, growing it geometrically. The previous contents
     * are never needed: every upload that used them waited for the queue to go idle before it returned, so the old
     * buffer can be unmapped and destroyed here.
     */
    private void ensureStagingCapacity(long bytes) {
        if (bytes <= this.stagingCapacityBytes) {
            return;
        }
        if (bytes > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A single staging upload of " + bytes
                    + " bytes exceeds the maximum staging buffer size.");
        }
        long target = Math.max(bytes, Math.max(this.stagingCapacityBytes * 2L, STAGING_MINIMUM_BYTES));
        target = alignUp4(target);
        destroyStaging();
        createStagingBuffer(target);
    }

    private void createStagingBuffer(long bytes) {
        long buffer = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long mapped = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer bufferHandle = stack.mallocLong(1);
            check(vkCreateBuffer(this.device, createInfo, null, bufferHandle),
                    "vkCreateBuffer(staging, " + bytes + " bytes)");
            buffer = bufferHandle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.device, buffer, requirements);
            int memoryType = memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "a host-visible, host-coherent staging buffer of " + bytes + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanContext:883")) {
                throw new IllegalStateException("injected allocation failure: VulkanContext:883");
            }
            check(VulkanContext.allocateMemory(this.device, allocate, memoryHandle, "contextStaging"),
                    "vkAllocateMemory(staging, " + requirements.size() + " bytes)");
            memory = memoryHandle.get(0);
            check(vkBindBufferMemory(this.device, buffer, memory, 0), "vkBindBufferMemory(staging)");
            PointerBuffer mappedAddress = stack.mallocPointer(1);
            // The mapping is kept forever: host-coherent memory needs no flush, and the staging buffer is only
            // touched between queue submissions.
            check(vkMapMemory(this.device, memory, 0L, VK_WHOLE_SIZE, 0, mappedAddress), "vkMapMemory(staging)");
            mapped = mappedAddress.get(0);
        } catch (RuntimeException | Error error) {
            if (mapped != 0L) {
                vkUnmapMemory(this.device, memory);
            }
            if (buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(this.device, buffer, null);
            }
            if (memory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(this.device, memory);
            }
            throw error;
        }
        this.stagingBuffer = buffer;
        this.stagingMemory = memory;
        this.stagingMappedAddress = mapped;
        this.stagingCapacityBytes = bytes;
    }

    /**
     * Releases the upload ring.
     * <p>
     * This method does not wait: a batch still in flight reads the ring's staging bytes, so destroying the ring
     * under it is exactly the use-after-free the batch's fence exists to prevent. The only caller, {@link #delete()},
     * calls this solely after a device-wide wait that postdates the teardown flush confirmed the queue idle - the
     * one point where "no batch is reading the ring" is a fact. When that confirmation fails, delete() leaves the
     * ring to the process teardown instead of calling this.
     */
    private void destroyUploadRing() {
        this.uploadDroppedCopies += this.pendingUploads.size();
        this.pendingUploads.clear();

        if (this.uploadRingMapped != 0L) {
            vkUnmapMemory(this.device, this.uploadRingMemory);
            this.uploadRingMapped = 0L;
        }
        if (this.uploadRingBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(this.device, this.uploadRingBuffer, null);
            this.uploadRingBuffer = VK_NULL_HANDLE;
        }
        if (this.uploadRingMemory != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(this.device, this.uploadRingMemory);
            this.uploadRingMemory = VK_NULL_HANDLE;
        }
        this.uploadRingCapacity = 0L;
        for (int slot = 0; slot < this.uploadSlotCursor.length; slot++) {
            this.uploadSlotCursor[slot] = 0L;
            this.uploadSlotFree[slot] = true;
        }
        this.uploadSlot = -1;
    }

    private void destroyStaging() {
        if (this.stagingMappedAddress != 0L) {
            vkUnmapMemory(this.device, this.stagingMemory);
            this.stagingMappedAddress = 0L;
        }
        if (this.stagingBuffer != VK_NULL_HANDLE) {
            vkDestroyBuffer(this.device, this.stagingBuffer, null);
            this.stagingBuffer = VK_NULL_HANDLE;
        }
        if (this.stagingMemory != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(this.device, this.stagingMemory);
            this.stagingMemory = VK_NULL_HANDLE;
        }
        this.stagingCapacityBytes = 0L;
    }

    /**
     * Zeroes the padding that the 4-byte copy alignment adds, so the destination never receives bytes that were
     * never written by the caller.
     */
    private void zeroStagingTail(long length, long paddedLength) {
        for (long i = length; i < paddedLength; i++) {
            MemoryUtil.memPutByte(this.stagingMappedAddress + i, (byte) 0);
        }
    }

    /**
     * Reserves {@code bytes} of the staging ring for one copy of the frame's pending batch.
     *
     * @return the byte offset to write at inside the ring's mapped memory, or -1 when the copy is larger than a slot
     *         (the caller then uses the one-copy path)
     */
    private long celtium$reserveUploadSlot(long bytes) {
        long reserveStarted = System.nanoTime();
        try {
            return celtium$reserveUploadSlotTimed(bytes);
        } finally {
            this.uploadReserveNanos += System.nanoTime() - reserveStarted;
        }
    }

    private long celtium$reserveUploadSlotTimed(long bytes) {
        if (bytes > UPLOAD_SLOT_BYTES) {
            return -1L;
        }
        // The ring's one-time creation lands inside this first reserve's bracket; time it into its own
        // counter (only when this call actually creates it) so it can be split back out (item 363).
        boolean ringAbsent = this.uploadRingBuffer == VK_NULL_HANDLE;
        long ringInitStarted = System.nanoTime();
        celtium$ensureUploadRing();
        if (ringAbsent) {
            this.uploadRingInitNanos += System.nanoTime() - ringInitStarted;
        }
        int slot = celtium$writableSlot(bytes);
        if (slot < 0 && !this.pendingUploads.isEmpty()) {
            // The batch being recorded has filled the ring. It goes out now (one submission, no wait) and its slots
            // become the in-flight set until its fence confirms them, which is what the loop below reclaims.
            // Measured without this: a frame with more than four slots' worth of copies sent every further copy down
            // the settled one-copy path - 1546 of 2419 copies on the layered leg, each paying its own submission and
            // fence wait, which is exactly the cost this unit exists to remove.
            this.uploadEarlyFlushesForRingPressure++;
            long flushWaitStarted = System.nanoTime();
            flushUploads();
            this.uploadReserveWaitNanos += System.nanoTime() - flushWaitStarted;
            slot = celtium$writableSlot(bytes);
        }
        if (slot < 0) {
            // Every slot is owned by a batch that has been submitted. A batch whose fence has already signalled frees
            // its slots for the cost of one fence query, so the queue is polled before anything is waited for - the
            // wait below is then only for a batch the GPU is genuinely still reading.
            long pollWaitStarted = System.nanoTime();
            int reclaimed = celtium$pollDeferredSubmits();
            this.uploadReserveWaitNanos += System.nanoTime() - pollWaitStarted;
            if (reclaimed > 0) {
                this.uploadRingPressurePollsReclaimed += reclaimed;
                slot = celtium$writableSlot(bytes);
            }
        }
        while (slot < 0 && !this.deferredSubmits.isEmpty()) {
            // Still no slot: waiting on the OLDEST outstanding submission - the head of the deferred queue - is the
            // cheapest wait that can open one, because the queue completes in submission order. Counted separately
            // from the polls above, so a leg can say which of the two the ring needed.
            this.uploadRingPressureWaits++;
            long awaitWaitStarted = System.nanoTime();
            celtium$awaitOldestDeferredSubmit("upload ring pressure");
            this.uploadReserveWaitNanos += System.nanoTime() - awaitWaitStarted;
            slot = celtium$writableSlot(bytes);
        }
        if (slot < 0) {
            // Even after a flush and a drain no slot can take this copy: it is larger than the room the ring has, so
            // it takes the settled one-copy path (counted by the caller).
            return -1L;
        }
        long offset = slot * UPLOAD_SLOT_BYTES + this.uploadSlotCursor[slot];
        this.uploadSlotCursor[slot] += bytes;
        this.uploadSlot = slot;
        return offset;
    }

    /**
     * Updates upload-ring slot ownership after a {@link #flushUploads()} attempt.
     * <p>
     * Only slots actually written by this batch ({@code usedSlotMask}) transition to closed when the batch is
     * enqueued (or stay free if submission failed). Slots already owned by an earlier in-flight batch
     * ({@code slotFree[slot] == false}) are NEVER reopened here — only fence reclamation may reopen them.
     * Previously, setting {@code slotFree[slot] = !enqueued || (usedSlotMask & (1L << slot)) == 0L} reopened
     * every slot owned by an earlier in-flight batch whenever a subsequent batch did not use that slot, allowing
     * the CPU to overwrite staging bytes while the GPU was still executing {@code vkCmdCopyBuffer} from them
     * (issue #422).
     */
    static void celtium$releaseUploadSlotsAfterFlush(boolean[] slotFree, long[] slotCursor,
            long usedSlotMask, boolean enqueued) {
        for (int slot = 0; slot < slotCursor.length; slot++) {
            boolean usedByThisBatch = (usedSlotMask & (1L << slot)) != 0L;
            if (usedByThisBatch) {
                slotCursor[slot] = 0L;
                slotFree[slot] = !enqueued;
            } else if (slotFree[slot]) {
                slotCursor[slot] = 0L;
            }
        }
    }

    /** {@return a ring slot with room for {@code bytes} that no in-flight batch owns, or -1 when there is none}. */
    private int celtium$writableSlot(long bytes) {
        for (int i = 0; i < UPLOAD_SLOT_COUNT; i++) {
            int candidate = (this.uploadSlot + 1 + i) % UPLOAD_SLOT_COUNT;
            if (this.uploadSlotFree[candidate]
                    && this.uploadSlotCursor[candidate] + bytes <= UPLOAD_SLOT_BYTES) {
                return candidate;
            }
        }
        return -1;
    }

    /** Creates the staging ring on first use: one host-visible buffer, {@link #UPLOAD_SLOT_COUNT} slots inside it. */
    private void celtium$ensureUploadRing() {
        if (this.uploadRingBuffer != VK_NULL_HANDLE) {
            return;
        }
        long bytes = UPLOAD_SLOT_COUNT * UPLOAD_SLOT_BYTES;
        long buffer = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long mapped = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkBufferCreateInfo createInfo = VkBufferCreateInfo.calloc(stack).sType$Default()
                    .size(bytes)
                    .usage(VK_BUFFER_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer bufferHandle = stack.mallocLong(1);
            check(vkCreateBuffer(this.device, createInfo, null, bufferHandle),
                    "vkCreateBuffer(upload ring, " + bytes + " bytes)");
            buffer = bufferHandle.get(0);
            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(this.device, buffer, requirements);
            int memoryType = memoryTypeIndex(requirements.memoryTypeBits(),
                    VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                    "a host-visible, host-coherent upload ring of " + bytes + " bytes");
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(memoryType);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(VulkanContext.allocateMemory(this.device, allocate, memoryHandle, "uploadRing"),
                    "vkAllocateMemory(upload ring, " + requirements.size() + " bytes)");
            memory = memoryHandle.get(0);
            if (vkBindBufferMemory(this.device, buffer, memory, 0L) != VK_SUCCESS) {
                vkDestroyBuffer(this.device, buffer, null);
                VulkanContext.freeMemory(this.device, memory);
                throw new IllegalStateException("vkBindBufferMemory(upload ring) failed");
            }
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            if (vkMapMemory(this.device, memory, 0L, bytes, 0, pointer) != VK_SUCCESS) {
                vkDestroyBuffer(this.device, buffer, null);
                VulkanContext.freeMemory(this.device, memory);
                throw new IllegalStateException("vkMapMemory(upload ring) failed");
            }
            mapped = pointer.get(0);
        }
        this.uploadRingBuffer = buffer;
        this.uploadRingMemory = memory;
        this.uploadRingMapped = mapped;
        this.uploadRingCapacity = bytes;
    }

    /** Zeroes the padding bytes of one ring copy, so a copy never publishes uninitialised staging bytes. */
    private void celtium$zeroUploadTail(long stagingOffset, long length, long paddedLength) {
        if (length < paddedLength) {
            MemoryUtil.memSet(this.uploadRingMapped + stagingOffset + length, 0, paddedLength - length);
        }
    }

    /** The one-copy path: copy into the single staging buffer, submit one command buffer and wait for it. */
    private void celtium$immediateStagingCopy(long destination, long destinationOffset, long sourceAddress,
            long length, long paddedLength) {
        ensureStagingCapacity(paddedLength);
        MemoryUtil.memCopy(sourceAddress, this.stagingMappedAddress, length);
        zeroStagingTail(length, paddedLength);
        recordStagingCopy(destination, destinationOffset, paddedLength);
    }

    /** The {@link ByteBuffer} half of {@link #celtium$immediateStagingCopy}. */
    private void celtium$immediateStagingCopy(long destination, long destinationOffset, ByteBuffer source,
            long paddedLength) {
        ensureStagingCapacity(paddedLength);
        ByteBuffer staging = MemoryUtil.memByteBuffer(this.stagingMappedAddress, (int) paddedLength);
        MemoryUtil.memCopy(source.duplicate(), staging);
        zeroStagingTail(source.remaining(), paddedLength);
        recordStagingCopy(destination, destinationOffset, paddedLength);
    }

    /**
     * Turns every copy the frame has recorded into one command buffer, one submission and one fence wait (OPT-07).
     * <p>
     * Before this, each copy submitted its own command buffer and waited for its own fence - measured on the
     * user-shaped leg at 11,303 submissions and 465 ms of CPU fence wait for one run, 3,265 of them inside the cold
     * window. The batch is submitted as a whole and waited for as a whole, so the copies are complete before the
     * caller's own submission reads them, which is the ordering the per-copy wait used to provide. The staging slot is
     * freed when the deferred submission that carried the copy is confirmed on the frame timeline, and by
     * nothing else.
     * <p>
     * Render thread only. A caller that is about to submit work reading a destination of a pending copy must call this
     * first: the frame's own submission does, at its start.
     */
    public void flushUploads() {
        if (this.pendingUploads.isEmpty() && !POLL_UPLOADS_ALWAYS) {
            // Nothing to copy, so no ring slot is needed here. The poll below used to run anyway, on every frame
            // command submission: vkGetFenceStatus is a kernel ioctl on RADV, and in the warm window of the
            // Supersymmetry KPI scene this poll was the client thread's largest fence cost (19 of 1111 samples,
            // profile wp1-cel, 2026-11-06). Reclaim still runs where it is needed: the frame-slot wait, the ring's
            // own pressure path and beginCommands' command-buffer pressure all poll.
            return;
        }
        // Reclaim confirmed batches first: their ring slots are what the copies below may need, and a poll is the
        // only CPU cost confirmation asks for - the waits the ring-pressure path still pays stay bounded by the
        // oldest outstanding batch rather than by whatever else the queue holds.
        long pollStarted = System.nanoTime();
        celtium$pollDeferredSubmits();
        this.uploadPollNanos += System.nanoTime() - pollStarted;
        if (this.pendingUploads.isEmpty()) {
            return;
        }
        boolean trace = UPLOAD_TRACE;
        long started = System.nanoTime();
        VkCommandBuffer commandBuffer = beginCommands();
        int copies = 0;
        long uploadSlotMask = 0L;
        try {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                // Write-after-read and write-after-write: an arena range freed by a rebuild or a translucent re-sort
                // is handed out again at once, and frames already submitted may still be reading or copying into the
                // old range (four frames can be in flight). Also make prior host writes into the upload ring
                // explicitly visible to TRANSFER_READ (defensive against Windows AMD LLPC/AMDVLK staging cache
                // visibility, issue #422).
                org.lwjgl.vulkan.VkMemoryBarrier.Buffer preCopyBarrier =
                        org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
                preCopyBarrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT | VK_ACCESS_HOST_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
                vkCmdPipelineBarrier(commandBuffer,
                        VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT
                                | VK_PIPELINE_STAGE_HOST_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, preCopyBarrier, null, null);
                int maxBatchRegions = Math.min(64, this.pendingUploads.size());
                VkBufferCopy.Buffer copyRegions = VkBufferCopy.calloc(maxBatchRegions, stack);
                long batchDestination = VK_NULL_HANDLE;
                int batchCount = 0;
                for (int i = 0, n = this.pendingUploads.size(); i < n; i++) {
                    PendingUpload pending = this.pendingUploads.get(i);
                    // Resolved here, not at record time: a growth between the two replaced the handle, and the offset
                    // stayed valid, so the copy belongs in the buffer the object has now. A destination that is gone
                    // resolves to VK_NULL_HANDLE - the arena or the world it belonged to was released, nothing reads
                    // it any more - and the copy is skipped rather than submitted against a dead handle.
                    long destination = pending.target.buffer();
                    if (destination == VK_NULL_HANDLE) {
                        this.uploadSkippedDeletedDestinations++;
                        continue;
                    }
                    if (batchCount > 0 && (destination != batchDestination || batchCount == maxBatchRegions)) {
                        copyRegions.position(0).limit(batchCount);
                        vkCmdCopyBuffer(commandBuffer, this.uploadRingBuffer, batchDestination, copyRegions);
                        batchCount = 0;
                    }
                    batchDestination = destination;
                    copyRegions.limit(maxBatchRegions);
                    copyRegions.get(batchCount).srcOffset(pending.stagingOffset).dstOffset(pending.destinationOffset)
                            .size(pending.size);
                    batchCount++;
                    copies++;
                    uploadSlotMask |= 1L << (int) (pending.stagingOffset / UPLOAD_SLOT_BYTES);
                }
                if (batchCount > 0) {
                    copyRegions.position(0).limit(batchCount);
                    vkCmdCopyBuffer(commandBuffer, this.uploadRingBuffer, batchDestination, copyRegions);
                }
                // The copies are transfer writes; making them available to every later stage here means the caller's
                // submission needs no barrier of its own. One barrier per batch, not per copy.
                org.lwjgl.vulkan.VkMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
                barrier.get(0).sType$Default()
                        .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                        .dstAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT | VK_ACCESS_INDEX_READ_BIT
                                | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_UNIFORM_READ_BIT
                                | VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT
                                | VK_ACCESS_MEMORY_READ_BIT);
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, barrier, null, null);
            }
        } finally {
            this.uploadRecordNanos += System.nanoTime() - started;
            // The batch joins the deferred queue rather than being waited on the spot: the fence is what frees this
            // command buffer and re-opens the ring slots it read, and both happen at the next poll or at the bounded
            // wait the ring-pressure path pays when every slot is still owned. This is the change that removes the
            // render thread's per-pass GPU round trip - measured here as one fence wait and one full queue drain of
            // everything submitted before it, ~10 times a frame on the legs this project runs. The batch is cleared
            // whatever the submit did, because re-recording it later would copy from slots whose contents have been
            // overwritten by newer copies - a wrong picture rather than a failed one.
            boolean enqueued = false;
            try {
                long submitStarted = System.nanoTime();
                celtium$submitDeferred(commandBuffer, uploadSlotMask);
                this.uploadSubmitNanos += System.nanoTime() - submitStarted;
                enqueued = true;
            } finally {
                this.uploadCopiesFlushed += this.pendingUploads.size();
                recyclePendingUploads();
                celtium$releaseUploadSlotsAfterFlush(this.uploadSlotFree, this.uploadSlotCursor,
                        uploadSlotMask, enqueued);
            }
        }
        this.uploadFlushes++;
        long millis = (System.nanoTime() - started) / 1_000_000L;
        this.uploadFlushMillis += millis;
        if (millis > this.uploadFlushWorstMillis) {
            this.uploadFlushWorstMillis = millis;
        }
        if (trace) {
            System.out.println("[Celtium] CELTIUM_UPLOAD_FLUSH copies=" + copies + " flush=" + this.uploadFlushes
                    + " millis=" + millis);
        }
        // The slots the batch owns stay closed until its fence confirms it; the rest are reusable now.
    }

    /**
     * Waits for any upload work that is still in flight.
     * <p>
     * The deferred shape made it real again: {@link #flushUploads()} submits without waiting, so a caller that needs
     * the copies complete - a readback, a teardown, a buffer growth - pays the bounded wait here, on the oldest
     * outstanding batch first because the queue confirms in submission order.
     */
    public void awaitUploads() {
        celtium$settleDeferredSubmits();
    }

    /**
     * Buffers whose replacement has already been created but that a recording may still reference.
     * <p>
     * A buffer that grows while a frame recording is open cannot be destroyed yet: the open command buffer holds the
     * old handle, and the driver invalidates the whole recording when a bound buffer dies (measured on the E2E 1.93a
     * pack with the unified frame buffer: "VkCommandBuffer ... is now in an invalid state ... VkBuffer ... was
     * destroyed, VkDescriptorSet ... was destroyed or updated without UPDATE_AFTER_BIND"). The growth path hands the
     * old objects here instead of destroying them, and {@link #celtium$destroyRetiredBuffers()} frees them at a point
     * where no recording can name them any more.
     */
    private final java.util.ArrayList<long[]> retiredBuffers = new java.util.ArrayList<long[]>();
    /** Retired slab-backed buffers: {handle, SlabAllocation}, drained by {@link #celtium$destroyRetiredBuffers()}. */
    private final java.util.ArrayList<Object[]> retiredSlabBuffers = new java.util.ArrayList<Object[]>();
    private int retiredBuffersDestroyed;
    private int retiredBuffersOutstanding;

    /** Whether the renderer currently holds an open recording that could name a buffer or descriptor set. */
    private java.util.function.BooleanSupplier recordingOpenProbe;

    /** Installs the probe a deleter consults before destroying anything (the backend knows its recording state). */
    public void celtium$setRecordingOpenProbe(java.util.function.BooleanSupplier probe) {
        this.recordingOpenProbe = probe;
    }

    private int pipelineDestroyLogs;
    private int pipelineDestroysWhileRecording;

    /**
     * Records that a pipeline is about to be destroyed while a recording may have bound it.
     * <p>
     * The OPT-07 invalidation list ended in one VkPipeline destroyed while a recording lived, and this probe named its
     * sites (leg uniEB: immediatepipeline and immediateitempipeline), the same way the descriptor-pool probe named
     * texture.delete.
     */
    public void celtium$notePipelineDestroy(String site) {
        if (!celtium$recordingOpen()) {
            return;
        }
        this.pipelineDestroysWhileRecording++;
        if (this.pipelineDestroyLogs < 12) {
            this.pipelineDestroyLogs++;
            System.out.println("[Celtium] CELTIUM_PIPELINE_DESTROYED_WHILE_RECORDING site=" + site
                    + " count=" + this.pipelineDestroysWhileRecording);
        }
    }

    /** {@return how many pipelines were destroyed while a recording could have bound them}. */
    public int celtium$pipelineDestroysWhileRecording() {
        return this.pipelineDestroysWhileRecording;
    }

    private int descriptorDestroyLogs;
    private int descriptorDestroysWhileRecording;
    /**
     * Records that a descriptor pool is about to be destroyed while a recording may name its sets.
     * <p>
     * This is the second half of OPT-07 and the reason the unified frame buffer is still opt-in: the E2E 1.93a
     * validation text after the buffer retirement work names only "VkDescriptorSet ... was destroyed or updated
     * without UPDATE_AFTER_BIND" (legs uniE5). Destroying a pool whose sets an open recording bound invalidates that
     * recording, so this counter and its bounded lines name the site doing it instead of guessing.
     */
    public void celtium$noteDescriptorDestroy(String site) {
        if (!celtium$recordingOpen()) {
            return;
        }
        this.descriptorDestroysWhileRecording++;
        if (this.descriptorDestroyLogs < 8) {
            this.descriptorDestroyLogs++;
            System.out.println("[Celtium] CELTIUM_DESC_POOL_DESTROYED_WHILE_RECORDING site=" + site
                    + " count=" + this.descriptorDestroysWhileRecording);
        }
    }

    /** Deferred destroy actions for resources an open recording may still name, with the sequence they retired at. */
    private static final class RetiredCleanup {
        final long sequence;
        final Runnable destroy;
        final String site;

        RetiredCleanup(long sequence, String site, Runnable destroy) {
            this.sequence = sequence;
            this.site = site;
            this.destroy = destroy;
        }
    }

    private final java.util.ArrayList<RetiredCleanup> retiredCleanups = new java.util.ArrayList<RetiredCleanup>();
    private int retiredCleanupsRan;
    private int retiredCleanupFailures;
    private int retiredCleanupLogs;

    /** Submission sequence sources, so a deferred destroy can prove the recording that named it has completed. */
    private java.util.function.LongSupplier submitSequenceSource;
    private java.util.function.LongSupplier completedSequenceSource;

    /** Installs the frame scheduler's sequence numbers for the retirement contract. */
    public void celtium$setSequenceSources(java.util.function.LongSupplier submitSequence,
            java.util.function.LongSupplier completedSequence) {
        this.submitSequenceSource = submitSequence;
        this.completedSequenceSource = completedSequence;
    }

    private long currentSubmitSequence() {
        java.util.function.LongSupplier source = this.submitSequenceSource;
        return source == null ? 0L : source.getAsLong();
    }

    private long currentCompletedSequence() {
        java.util.function.LongSupplier source = this.completedSequenceSource;
        return source == null ? 0L : source.getAsLong();
    }

    /**
     * Defers a destroy action until the recording that could name the resource has completed.
     * <p>
     * The measured case is a pipeline or pool being torn down while the unified frame buffer still records, or while
     * the submission that read it is still pending: the driver reports VUID-vkCmd*-commandBuffer-recording (recording
     * case) or VUID-vkDestroyPipeline-pipeline-00765 / VUID-vkDestroyDescriptorPool-descriptorPool-00303 (pending use
     * case). Tagging the action with the submission sequence at retirement and running it only once that sequence has
     * been confirmed covers both without adding a wait.
     *
     * @param site     who retired it, for the bounded log line
     * @param destroy  the destruction to run later
     */
    public void celtium$retireCleanup(String site, Runnable destroy) {
        this.retiredCleanups.add(new RetiredCleanup(currentSubmitSequence(), site, destroy));
        if (this.retiredCleanupLogs < 8) {
            this.retiredCleanupLogs++;
            System.out.println("[Celtium] CELTIUM_CLEANUP_RETIRED site=" + site + " outstanding="
                    + this.retiredCleanups.size() + " sequence=" + currentSubmitSequence());
        }
    }

    /**
     * Runs the deferred destroys whose submission sequence has been confirmed.
     *
     * @return how many ran
     */
    public int celtium$runRetiredCleanups() {
        if (this.retiredCleanups.isEmpty()) {
            return 0;
        }
        long completed = currentCompletedSequence();
        int ran = 0;
        java.util.Iterator<RetiredCleanup> it = this.retiredCleanups.iterator();
        while (it.hasNext()) {
            RetiredCleanup cleanup = it.next();
            // The recording that could name the resource is submitted after it retired, so the confirmation of the
            // sequence that was current at retirement is not enough; wait for one more.
            if (cleanup.sequence >= completed) {
                continue;
            }
            it.remove();
            try {
                cleanup.destroy.run();
                this.retiredCleanupsRan++;
                ran++;
            } catch (RuntimeException | LinkageError failure) {
                this.retiredCleanupFailures++;
            }
        }
        return ran;
    }

    /** {@return how many deferred destroys ran and how many failed}. */
    public int[] celtium$retiredCleanupCounts() {
        return new int[] {this.retiredCleanupsRan, this.retiredCleanupFailures};
    }

    /** {@return how many descriptor pools were destroyed while a recording could name them}. */
    public int celtium$descriptorDestroysWhileRecording() {
        return this.descriptorDestroysWhileRecording;
    }

    /** {@return whether an open recording could still name a resource, so destruction has to be deferred}. */
    public boolean celtium$recordingOpen() {
        java.util.function.BooleanSupplier probe = this.recordingOpenProbe;
        if (probe == null) {
            return false;
        }
        try {
            return probe.getAsBoolean();
        } catch (RuntimeException | LinkageError failure) {
            // A probe that cannot answer must not leak: prefer the deferred path (it only delays the free).
            return true;
        }
    }

    /** The site tags of the deferrals, and their count, so a leg can say which path needed the list. */
    private final java.util.ArrayList<String> retiredBufferSites = new java.util.ArrayList<String>();

    /** Hands a replaced buffer to the deferred list instead of destroying it while a recording may hold it. */
    public void celtium$retireBuffer(long handle, long memory) {
        celtium$retireBuffer(handle, memory, "growth");
    }

    /**
     * Hands a resource to the deferred list instead of destroying it while a recording may hold it.
     *
     * @param site who deferred it, for the bounded log line
     */
    public void celtium$retireBuffer(long handle, long memory, String site) {
        if (handle == VK_NULL_HANDLE) {
            return;
        }
        this.retiredBuffers.add(new long[] {handle, memory});
        this.retiredBuffersOutstanding = this.retiredBuffers.size();
        if (this.retiredBufferSites.size() < 8) {
            this.retiredBufferSites.add(site);
            System.out.println("[Celtium] CELTIUM_BUFFER_RETIRED site=" + site + " outstanding="
                    + this.retiredBuffersOutstanding + " total=" + (this.retiredBuffersDestroyed + this.retiredBuffers.size()));
        }
    }

    /**
     * Destroys every deferred buffer. Only called where nothing can be recording: a recording that still names one of
     * these would be invalidated by the destruction, which is the bug this list exists to avoid.
     *
     * @return how many buffers were freed
     */
    public int celtium$destroyRetiredBuffers() {
        celtium$collectSlabReleases();
        if (this.retiredBuffers.isEmpty() && this.retiredSlabBuffers.isEmpty()) {
            return 0;
        }
        VkDevice device = this.device;
        int freed = 0;
        for (int i = 0; i < this.retiredBuffers.size(); i++) {
            long[] entry = this.retiredBuffers.get(i);
            if (device != null) {
                vkDestroyBuffer(device, entry[0], null);
                if (entry[1] != VK_NULL_HANDLE) {
                    VulkanContext.freeMemory(device, entry[1]);
                }
            }
            freed++;
        }
        this.retiredBuffers.clear();
        // The slab form: destroy the buffer, then hand the sub-allocation back to its pool as a deferred release
        // keyed on the newest timeline value handed out. This drain point is where nothing can be recording, and
        // the release itself only becomes reusable once the timeline confirms every submission up to that value -
        // the same completion fact the raw path's vkFreeMemory here relied on, stated through the timeline instead.
        for (int i = 0; i < this.retiredSlabBuffers.size(); i++) {
            Object[] entry = this.retiredSlabBuffers.get(i);
            long handle = ((Long) entry[0]).longValue();
            SlabAllocator.SlabAllocation allocation = (SlabAllocator.SlabAllocation) entry[1];
            if (device != null) {
                vkDestroyBuffer(device, handle, null);
            }
            allocation.owner().release(allocation,
                    this.frameTimeline == null ? 0L : this.frameTimeline.lastAllocatedValue());
            freed++;
        }
        this.retiredSlabBuffers.clear();
        this.retiredBuffersDestroyed += freed;
        this.retiredBuffersOutstanding = 0;
        return freed;
    }

    /** {@return how many deferred buffers are waiting to be freed, and how many have been freed so far}. */
    public int[] celtium$retiredBufferCounts() {
        return new int[] {this.retiredBuffersOutstanding, this.retiredBuffersDestroyed};
    }

    /** Submits every pending copy and waits for it and every other outstanding deferred submission to complete. */
    public void settleUploads() {
        flushUploads();
        celtium$settleDeferredSubmits();
    }

    /**
     * The canonical reserve figure (item 363 caveat 2, adjudicated in ITEM363-AUDIT §2.6): the reserve
     * bracket's pure booking cost - slot selection and cursor updates - per 1,000 copies. The raw cumulative
     * {@code uploadPhaseMicros[reserve]} mixes three things that move independently (run length, copy count,
     * ring-pressure fence waits) plus the one-time ring creation, which is why cross-record comparisons of
     * the raw value kept failing to reproduce; the two non-booking parts are timed separately
     * ({@code uploadRingInitNanos}, {@code uploadReserveWaitNanos}) and subtracted here. Reservation calls
     * are exactly {@code uploadCopiesBatched + uploadFallbackCopies}: every reserve ends in one or the other.
     * Nanoseconds per copy is the same number as microseconds per 1,000 copies.
     *
     * @return the derived figure, or 0 when no copy has been reserved yet
     */
    static long celtium$reserveBookMicrosPerKCopy(long reserveNanos, long ringInitNanos,
            long reserveWaitNanos, long copies) {
        if (copies <= 0L) {
            return 0L;
        }
        return (reserveNanos - ringInitNanos - reserveWaitNanos) / copies;
    }

    /** {@return the batched upload path's census, for the run report}. */
    public String uploadBatchSummary() {
        return "commandBufferAllocations=" + commandBufferAllocations + " commandBufferReuses=" + commandBufferReuses
                + " uploadCopiesBatched=" + this.uploadCopiesBatched + " uploadCopiesFlushed=" + this.uploadCopiesFlushed
                + " uploadFlushes=" + this.uploadFlushes + " uploadFlushMillis=" + this.uploadFlushMillis
                + " uploadFlushWorstMillis=" + this.uploadFlushWorstMillis
                + " uploadBytesByKind=[arena=" + this.uploadBytesByKind[0] + " copies=" + this.uploadCopiesByKind[0]
                + " buffer=" + this.uploadBytesByKind[1] + " copies=" + this.uploadCopiesByKind[1] + "]"
                + " uploadPhaseMicros=[reserve=" + (this.uploadReserveNanos / 1000L)
                + " copy=" + (this.uploadCopyNanos / 1000L)
                + " poll=" + (this.uploadPollNanos / 1000L)
                + " record=" + (this.uploadRecordNanos / 1000L)
                + " submit=" + (this.uploadSubmitNanos / 1000L) + "]"
                + " uploadPhaseMillisTotal=" + ((this.uploadReserveNanos + this.uploadCopyNanos + this.uploadPollNanos
                        + this.uploadRecordNanos + this.uploadSubmitNanos) / 1000000L)
                + " reserveBookMicrosPerKCopy=" + celtium$reserveBookMicrosPerKCopy(this.uploadReserveNanos,
                        this.uploadRingInitNanos, this.uploadReserveWaitNanos,
                        this.uploadCopiesBatched + this.uploadFallbackCopies)
                + " uploadCopyBytesTotal=" + this.uploadCopyBytes
                + " uploadPendingCopies=" + this.pendingUploads.size()
                + " uploadRingBytes=" + this.uploadRingCapacity
                + " uploadRingSlots=" + UPLOAD_SLOT_COUNT
                + " uploadRingPressurePollsReclaimed=" + this.uploadRingPressurePollsReclaimed
                + " uploadFallbackCopies=" + this.uploadFallbackCopies
                + " uploadDroppedCopies=" + this.uploadDroppedCopies
                + " uploadSkippedDeletedDestinations=" + this.uploadSkippedDeletedDestinations
                + " uploadEarlyFlushesForRingPressure=" + this.uploadEarlyFlushesForRingPressure
                + " uploadRingPressureWaits=" + this.uploadRingPressureWaits
                + celtium$describeDeferred();
    }

    /**
     * Records the staged copy and submits it. Queue submission is the implicit memory dependency that makes the
     * host writes to the coherent staging memory visible to the transfer read; no explicit barrier is needed.
     */
    private void recordStagingCopy(long destination, long destinationOffset, long length) {
        VkCommandBuffer commandBuffer = beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The same write-after-read / write-after-write dependency as the batched path (flushUploads): earlier
            // frames may still read or write the destination range, and host writes to staging must be visible.
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer preCopy = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            preCopy.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_HOST_WRITE_BIT | VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT | VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer,
                    VK_PIPELINE_STAGE_HOST_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, preCopy, null, null);
            VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
            copy.get(0).srcOffset(0L).dstOffset(destinationOffset).size(length);
            vkCmdCopyBuffer(commandBuffer, this.stagingBuffer, destination, copy);
            org.lwjgl.vulkan.VkMemoryBarrier.Buffer postCopy = org.lwjgl.vulkan.VkMemoryBarrier.calloc(1, stack);
            postCopy.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_VERTEX_ATTRIBUTE_READ_BIT | VK_ACCESS_INDEX_READ_BIT
                            | VK_ACCESS_SHADER_READ_BIT | VK_ACCESS_TRANSFER_READ_BIT
                            | VK_ACCESS_TRANSFER_WRITE_BIT);
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_VERTEX_INPUT_BIT | VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT
                            | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, postCopy, null, null);
        }
        endCommands(commandBuffer);
    }

    private void requireUploadArguments(long destination, long destinationOffset, long length) {
        requireUsable();
        if (destination == VK_NULL_HANDLE) {
            throw new IllegalArgumentException("destination must be a VkBuffer handle");
        }
        if (length < 0L) {
            throw new IllegalArgumentException("length must not be negative, was " + length);
        }
        if (destinationOffset < 0L) {
            throw new IllegalArgumentException("destinationOffset must not be negative, was " + destinationOffset);
        }
        if (destinationOffset % COPY_ALIGNMENT != 0L) {
            throw new IllegalArgumentException("destinationOffset must be a multiple of " + COPY_ALIGNMENT
                    + " for a Vulkan buffer copy, was " + destinationOffset);
        }
    }

    private void requireUsable() {
        if (this.device == null || this.commandPool == VK_NULL_HANDLE) {
            throw new IllegalStateException("This VulkanContext has been deleted.");
        }
        if (!this.usable) {
            // A submission timed out: the queue may still be executing work whose fence never signalled, so the
            // device's state is unknown. Refusing here is the other half of quarantining the resources - a context
            // that keeps submitting after that is how a stall turns into a validation error storm (audit A02).
            throw new IllegalStateException("This VulkanContext is unusable: a Vulkan submission failed, "
                    + "the device was lost, or a fence wait timed out; further GPU work is disabled "
                    + "(submitWithSemaphores timeouts=" + submitTimeouts + ")");
        }
    }

    private static void requireLoader() {
        int loaderVersion;
        try {
            loaderVersion = VK.getInstanceVersionSupported();
        } catch (RuntimeException | LinkageError error) {
            throw new IllegalStateException("The LWJGL Vulkan bindings or the Vulkan loader are unavailable: "
                    + describe(error), error);
        }
        if (loaderVersion < VK12.VK_API_VERSION_1_2) {
            throw new IllegalStateException("The Vulkan loader supports " + versionString(loaderVersion)
                    + ", but Celtium requires 1.2 or newer.");
        }
    }

    /**
     * Reports that there is no OpenGL device identity.
     * <p>
     * The identity existed for one reason - to pick the Vulkan device that is the <em>same</em> device, so that textures
     * could be shared between the two APIs - and there is no GL surface to share with: the window is GLFW_NO_API and
     * every GL query is answered by the stub, whose strings say "Celtium" rather than a driver. Device selection
     * proceeds by requirements alone.
     */
    private static GlDeviceIdentity inspectOpenGl() {
        System.out.println("[Celtium] starting without an OpenGL device identity: no device matching, no interop");
        return null;
    }

    private static String versionString(int version) {
        return VK_VERSION_MAJOR(version) + "." + VK_VERSION_MINOR(version) + "." + VK_VERSION_PATCH(version);
    }

    private static String describe(Throwable error) {
        return error.getClass().getSimpleName() + ": " + String.valueOf(error.getMessage());
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned VkResult " + result);
        }
    }
}
