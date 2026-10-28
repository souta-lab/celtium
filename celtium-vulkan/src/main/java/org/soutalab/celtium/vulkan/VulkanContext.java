package org.soutalab.celtium.vulkan;

import org.lwjgl.PointerBuffer;
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
     * System property that switches the Khronos validation layer and the debug-utils messenger on. Off by default so
     * a shipped installation never needs the layer to be installed.
     */
    public static final String VALIDATION_PROPERTY = "celtium.vulkan.validation";

    /**
     * Enables the S2 swapchain probe: a surface and swapchain are created for the game window and one image is
     * cleared and presented, which is the evidence that the presentation path works. Off by default because GL still
     * presents the window in the hybrid build.
     */
    public static final String SWAPCHAIN_PROPERTY = "celtium.vulkan.swapchainProbe";

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
    private VkPhysicalDevice physicalDevice;
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
    private VkQueue queue;
    private int queueFamilyIndex = -1;
    private String deviceName;
    private String deviceUuid;
    /** The device's own Vulkan API version and type, kept for the driver/contract lines. */
    private int deviceApiVersion;
    private int deviceType = -1;
    private long commandPool = VK_NULL_HANDLE;

    /**
     * How many times {@link #submitWithSemaphores} ran, and how many of those actually carried a wait.
     * <p>
     * This exists because the validation messages name the acquire semaphore as "not waited on" while the calling code
     * passes it, and the question is whether the call this code builds is the call the driver receives. A counter is
     * used rather than a print: an earlier attempt at printing from here coincided with a run whose terrain passes all
     * errored, and while that link is unproven, a counter costs nothing and cannot perturb anything.
     */
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

    /** Command buffers whose queue wait failed and whose command buffer was therefore quarantined instead of freed. */
    /** Command buffers and fences whose submission timed out: never freed while the queue may still own them. */
    private final java.util.Set<VkCommandBuffer> quarantinedCommandBuffers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> quarantinedFences = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Cleared when a submission times out: a device that stopped making progress gets no further work. */
    private volatile boolean usable = true;
    public static int submitWithSemaphoresCalls;
    public static int submitWithSemaphoresWaits;
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
     * OPT-07's staging ring: the bytes of the copies this frame has recorded but not yet submitted.
     * <p>
     * The single staging buffer above serves the immediate one-copy path (a copy larger than a ring slot, and the
     * resource build that must be complete before it returns). Every ordinary upload writes into a slot of this ring
     * and appends a copy record instead of submitting anything; {@link #flushUploads()} turns all of them into one
     * command buffer, one submission and one fence wait. The slot is freed by that submission's fence and by nothing
     * else: the ring's cursor is only moved on after the wait that confirmed the copies completed (OPT-04).
     */
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
     * The reuse rule is unchanged and is the point of the ring: a slot is free only after the fence of the submission
     * that read it has signalled ({@link #celtium$pollDeferredSubmits()}), never because a frame number moved.
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
     * spot; its fence is what releases the resources it still reads (the command buffer, the fence itself, the ring
     * slots), and that happens at the first {@link #celtium$pollDeferredSubmits()} or bounded wait that sees it
     * signal. Confirmed order is submission order, because the queue executes submissions in order, so polls only
     * ever free from the head.
     */
    private static final class DeferredSubmit {
        private VkCommandBuffer commandBuffer;
        private long fence;
        private long uploadSlotMask;
        /** Wait/signal sets carried until the batch flush records them into its VkSubmitInfo array. */
        private long[] waitSemaphores;
        private int[] waitStages;
        private long[] signalSemaphores;
        /** The batch this submission's fence belongs to; null on the legacy per-submit path. */
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
     * One batched {@code vkQueueSubmit}: a single fence armed by the call and shared by every entry the batch
     * carried. The fence stays out of the idle pool until the last entry reclaims - handing it out early would arm
     * it for a new submission while earlier batch entries still complete against it. {@link #pending} counts
     * unreclaimed entries, not pending submissions: it starts at the batch's entry count and the reclaim decrements.
     */
    private static final class SharedBatch {
        private long fence;
        private int pending;
    }

    /**
     * Submissions queued for the next {@link #celtium$flushBatchedSubmits()} call, in logical submission order.
     * Each entry is also already in {@link #deferredSubmits} so await/poll lookups find it by fence; its fence is the
     * batch's shared one, armed only when the flush submits the whole array - which is why every wait and every poll
     * flushes first: waiting on an unarmed fence would read as a stall that is really just "not submitted yet".
     */
    private final java.util.ArrayList<DeferredSubmit> pendingBatchedSubmits = new java.util.ArrayList<DeferredSubmit>();
    private SharedBatch pendingBatch;
    /** Batched deferred submits are the default; {@code -Dceltium.vulkan.batchedSubmits=false} restores per-call submits. */
    private static final boolean BATCHED_SUBMITS =
            !"false".equals(System.getProperty("celtium.vulkan.batchedSubmits", "true"));
    private int batchedFlushes;
    private int batchedSubmissions;

    /**
     * The submissions currently owned by the queue, oldest first. Every bounded wait and every poll reclaims from the
     * head: a fence that has not signaled keeps every newer entry pending too, which is the same ordering guarantee a
     * full drain had, expressed per entry instead of per device.
     */
    private final java.util.ArrayDeque<DeferredSubmit> deferredSubmits = new java.util.ArrayDeque<DeferredSubmit>();
    /** Reset fences ready to arm the next deferred submission; bounded so an idle run does not hoard them. */
    private final java.util.ArrayDeque<Long> idleDeferredFences = new java.util.ArrayDeque<Long>();
    private static final int IDLE_DEFERRED_FENCE_CAP = 16;
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

    /** One pending copy: where it goes, where its bytes already are, and how many. */
    private static final class PendingUpload {
        private final VulkanBuffer target;
        private final long destinationOffset;
        private final long stagingOffset;
        private final long size;

        private PendingUpload(VulkanBuffer target, long destinationOffset, long stagingOffset, long size) {
            this.target = target;
            this.destinationOffset = destinationOffset;
            this.stagingOffset = stagingOffset;
            this.size = size;
        }
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
        try {
            return VK.getInstanceVersionSupported() >= VK12.VK_API_VERSION_1_2;
        } catch (RuntimeException | LinkageError error) {
            return false;
        }
    }

    /**
     * Creates the Vulkan instance, matches the GL device and creates the logical device, the queue and the command
     * pool. Render thread only, with the OpenGL context current: the physical device is matched by the GL device
     * UUID, so GL must be able to answer for the GPU it renders on.
     *
     * @param applicationName application name reported to the driver; must not be null or empty
     * @throws IllegalStateException when Vulkan is unusable: no bindings or loader, a loader older than 1.2, no GL
     *                               device UUIDs, no physical device matching the GL UUID, missing
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

    /** The Vulkan instance that owns the device. */
    /**
     * {@return whether the instance was created with this extension}.
     * <p>
     * Surface extensions such as {@code VK_KHR_wayland_surface} are instance extensions, so the swapchain asks here
     * rather than guessing from the platform name.
     */
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
        try (MemoryStack stack = MemoryStack.stackPush()) {
            return deviceExtensionNames(stack, this.physicalDevice).contains(name);
        }
    }

    public boolean hasInstanceExtension(String name) {
        if (name == null || this.instance == null) {
            return false;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer count = stack.mallocInt(1);
            if (vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, count, null) != VK_SUCCESS) {
                return false;
            }
            org.lwjgl.vulkan.VkExtensionProperties.Buffer properties =
                    org.lwjgl.vulkan.VkExtensionProperties.calloc(count.get(0), stack);
            if (vkEnumerateInstanceExtensionProperties((java.nio.ByteBuffer) null, count, properties) != VK_SUCCESS) {
                return false;
            }
            for (int i = 0; i < properties.capacity(); i++) {
                if (name.equals(properties.get(i).extensionNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

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
     * Ends recording, submits the command buffer to the context queue, waits for the queue to go idle and frees the
     * command buffer. The wait is what makes the bootstrap-quality upload path safe: when this method returns, the
     * recorded copies have completed and the staging memory can be reused or destroyed.
     * <p>
     * Render thread only.
     */
    /**
     * Ends and submits a command buffer, waiting on one semaphore and signalling another.
     * <p>
     * {@link #endCommands} submits with no semaphores and then drains the queue, which is what the one-shot uploads
     * want. A presentation needs more: the submission that modifies the acquired image must *wait* on the semaphore the
     * acquire signalled, and the presentation must wait on a signal from that submission instead. The validation layer
     * reports the alternative as "pSubmits[0] performs a layout transition on presentable VkImage ... but VkSemaphore
     * ... signaled by image acquire operation was not waited on".
     * <p>
     * The fence is waited for, so the command buffer can be freed, but the queue is not drained: the signal semaphore
     * is handed to the caller's presentation, which waits on it.
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
     * {@code VulkanFrameTarget}'s contract warns about.
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
        submitWithSemaphoresWaits += waitSemaphores.length;
        // Deferred (batched) submissions logically precede this direct one; flushing first keeps logical and queue
        // order identical - without it a vkQueueSubmit here would jump ahead of entries the frame already submitted.
        celtium$flushBatchedSubmits();
        long fence = VK_NULL_HANDLE;
        try {
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer fenceHandle = stack.mallocLong(1);
                check(vkCreateFence(this.device, org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType$Default(), null,
                        fenceHandle), "vkCreateFence(submitWithSemaphores)");
                fence = fenceHandle.get(0);
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(commandBuffer));
                java.util.List<Long> waits = new java.util.ArrayList<Long>();
                for (long semaphore : waitSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        waits.add(Long.valueOf(semaphore));
                    }
                }
                if (!waits.isEmpty()) {
                    // Explicit count, and one destination stage for each semaphore: LWJGL's pWaitSemaphores writes only
                    // the pointer. ALL_COMMANDS because the command buffer begins with a layout transition, which a wait
                    // whose stage mask does not cover it does not order against.
                    long[] waitHandles = new long[waits.size()];
                    int[] stages = new int[waits.size()];
                    for (int i = 0; i < waitHandles.length; i++) {
                        waitHandles[i] = waits.get(i).longValue();
                        stages[i] = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    }
                    submit.waitSemaphoreCount(waitHandles.length);
                    submit.pWaitSemaphores(stack.longs(waitHandles));
                    submit.pWaitDstStageMask(stack.ints(stages));
                }
                java.util.List<Long> signals = new java.util.ArrayList<Long>();
                for (long semaphore : signalSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        signals.add(Long.valueOf(semaphore));
                    }
                }
                if (!signals.isEmpty()) {
                    long[] signalHandles = new long[signals.size()];
                    for (int i = 0; i < signalHandles.length; i++) {
                        signalHandles[i] = signals.get(i).longValue();
                    }
                    submit.pSignalSemaphores(stack.longs(signalHandles));
                }
                VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, fence), "vkQueueSubmit(submitWithSemaphores)");
                // From here the queue owns the command buffer and the fence. The three outcomes are kept apart,
                // because the cleanup that is right for one is a validation error for another (audit A02):
                //
                //  1. a failure BEFORE the submit - the queue never saw it, so freeing the buffer and destroying the
                //     fence is correct and is what the finally below does;
                //  2. the fence signals - the submission completed, so the same cleanup is correct;
                //  3. the wait times out - the submission may still be executing. Destroying its fence and freeing
                //     its command buffer while the queue may still read them is exactly what
                //     VUID-vkDestroyFence-fence-01120 and VUID-vkFreeCommandBuffers-pCommandBuffers-00047 describe.
                //
                // The bounded wait itself stays (an unbounded wait on the present path is a hang with no message),
                // but a timeout no longer throws into the unconditional cleanup. The buffer and the fence are handed
                // to a quarantine list, the context marks itself unusable so no further work is submitted on a device
                // that has stopped making progress, and the failure is named.
                int waited = vkWaitForFences(this.device, fence, true, SUBMIT_TIMEOUT_NANOS);
                if (waited == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
                    submitTimeouts++;
                    quarantine(commandBuffer, fence);
                    fence = VK_NULL_HANDLE;
                    commandBuffer = null;
                    this.usable = false;
                    throw new IllegalStateException("vkWaitForFences(submitWithSemaphores): the submission did not"
                            + " signal within " + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s. The device has stopped"
                            + " making progress: the submission's command buffer and fence are quarantined (never"
                            + " reset, re-recorded, freed or destroyed while the queue may own them), the context is"
                            + " marked unusable, and no further work is submitted on it"
                            + " (submitWithSemaphores timeouts=" + submitTimeouts + ")");
                }
                // Audit B04. The three remaining outcomes are not one outcome, and the cleanup that is right for one
                // is a validation error for another (VUID-vkDestroyFence-fence-01120,
                // VUID-vkFreeCommandBuffers-pCommandBuffers-00047):
                //
                //   SUCCESS        the submission completed; the finally below is correct.
                //   DEVICE_LOST    the device is gone. The spec treats a lost device as SUCCESS-equivalent for
                //                  pending/in-use purposes, so nothing is still executing and the finally is correct
                //                  too. This must NOT be grouped with "the wait did not complete" - the audit names
                //                  that mistake explicitly ([R4]).
                //   anything else  OUT_OF_HOST_MEMORY, OUT_OF_DEVICE_MEMORY, UNKNOWN: the wait did not confirm
                //                  completion, so the queue may still own the buffer and the fence. They are
                //                  quarantined exactly as on a timeout and the finally must not release them.
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
                    throw new IllegalStateException("vkWaitForFences(submitWithSemaphores) reported"
                            + " VK_ERROR_DEVICE_LOST: the submission is not going to complete because the device is"
                            + " gone. The command buffer and fence are released by the caller's cleanup, which is"
                            + " correct here - a lost device is SUCCESS-equivalent for pending/in-use purposes, so the"
                            + " queue no longer owns them (submitWithSemaphores lostDevices=" + submitDeviceLost + ")");
                } else {
                    submitWaitFailures++;
                    quarantine(commandBuffer, fence);
                    fence = VK_NULL_HANDLE;
                    commandBuffer = null;
                    this.usable = false;
                    throw new IllegalStateException("vkWaitForFences(submitWithSemaphores) did not confirm completion"
                            + " and did not time out: result=" + waited + ". The queue may still own the submission, so"
                            + " its command buffer and fence are quarantined rather than freed, and the context is"
                            + " marked unusable. This is not a lost device: only VK_ERROR_DEVICE_LOST is"
                            + " completion-equivalent for pending/in-use purposes"
                            + " (submitWithSemaphores waitFailures=" + submitWaitFailures + ")");
                }
            }
        } finally {
            if (fence != VK_NULL_HANDLE) {
                vkDestroyFence(this.device, fence, null);
            }
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
    private void quarantine(VkCommandBuffer commandBuffer, long fence) {
        this.quarantinedCommandBuffers.add(commandBuffer);
        QUARANTINED_COUNTS[0] = this.quarantinedCommandBuffers.size();
        QUARANTINED_COUNTS[1] = this.quarantinedFences.size();
        if (fence != VK_NULL_HANDLE) {
            // A bootstrap-path submission carries no fence: the queue wait is the only completion signal there, and on
            // a non-confirming result the command buffer alone is held.
            this.quarantinedFences.add(Long.valueOf(fence));
        }
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

    public void endCommands(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        // Batched deferred submits precede this bootstrap submission logically, so they go first physically. It
        // matters more than ordering: this path waits on its own fence, and a pending batch submitted after it
        // would still complete correctly - the flush simply keeps the queue's order the order callers saw.
        celtium$flushBatchedSubmits();
        boolean releasedToQueue = true;
        try {
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(commandBuffer));
                // BH-27: the submit gets its own fence and this path waits on that fence, not on the whole queue.
                // The queue-wide idle it used to do (twice per copy, once in beginCommands and once here) drained
                // every other submission in flight, so every upload and every present serialised the renderer against
                // the GPU. The fence is what makes freeing this command buffer correct: only this submission has to
                // complete, and the queue keeps running while it does.
                LongBuffer fenceHandle = stack.mallocLong(1);
                check(vkCreateFence(this.device, VkFenceCreateInfo.calloc(stack).sType$Default(), null, fenceHandle),
                        "vkCreateFence(bootstrap submit)");
                long fence = fenceHandle.get(0);
                try {
                    VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, fence), "vkQueueSubmit");
                    long waitStarted = System.nanoTime();
                    int waited = vkWaitForFences(this.device, fence, true, SUBMIT_TIMEOUT_NANOS);
                    celtium$recordBootstrapFenceWait(waitStarted);
                    if (waited == VK_SUCCESS) {
                        bootstrapSubmitWaits++;
                        completedSubmissions++;
                    } else if (waited == VK_TIMEOUT) {
                        // A submission that does not complete within the bound is the stall the frame path already
                        // reports by name; here it means the command buffer must not be freed.
                        submitTimeouts++;
                        this.usable = false;
                        releasedToQueue = false;
                        quarantine(commandBuffer, fence);
                        throw new IllegalStateException("vkWaitForFences(bootstrap submit) did not signal within "
                                + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s; the command buffer is quarantined"
                                + " rather than freed (submitTimeouts=" + submitTimeouts + ")");
                    } else if (waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                        submitDeviceLost++;
                        this.usable = false;
                    } else {
                        queueWaitFailures++;
                        this.usable = false;
                        releasedToQueue = false;
                        quarantine(commandBuffer, fence);
                        throw new IllegalStateException("vkWaitForFences(bootstrap submit) returned " + waited
                                + "; the command buffer is quarantined rather than freed (queueWaitFailures="
                                + queueWaitFailures + ")");
                    }
                } finally {
                    vkDestroyFence(this.device, fence, null);
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
     * A fence for a deferred submission: reset and reusable, from the pool when one is free, created otherwise.
     * Fence objects are cheap but not free, so the pool caps at {@link #IDLE_DEFERRED_FENCE_CAP} and anything beyond
     * is destroyed at reclaim instead of hoarded.
     */
    private long celtium$borrowDeferredFence() {
        Long pooled = this.idleDeferredFences.poll();
        if (pooled != null) {
            return pooled.longValue();
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateFence(this.device,
                    org.lwjgl.vulkan.VkFenceCreateInfo.calloc(stack).sType$Default(), null, handle),
                    "vkCreateFence(deferred submit)");
            return handle.get(0);
        }
    }

    /**
     * Submits {@code commandBuffer} with no semaphores and tracks it as outstanding instead of waiting for it.
     * {@return the fence the caller may wait on later through {@link #celtium$awaitDeferredSubmit(long, String)}}.
     * <p>
     * The CPU no longer blocks on the submission the queue just took: the fence is what frees the command buffer and
     * releases the upload-ring slots the batch read ({@code uploadSlotMask}, 0 when it read none), and that happens at
     * the next {@link #celtium$pollDeferredSubmits()}, {@link #celtium$awaitOldestDeferredSubmit(String)} or
     * {@link #celtium$settleDeferredSubmits()} that confirms it - the OPT-04 epoch counts confirmed completions the
     * same way a wait did. This is the path for submissions only the GPU orders: the upload batch, texture patches and
     * the present blit. The render thread used to pay one full {@code vkWaitForFences} for each of them, and every
     * such wait inherited whatever the queue still had in flight - the serialisation the user's frame spikes show.
     * <p>
     * Render thread only.
     */
    public long celtium$submitDeferred(VkCommandBuffer commandBuffer, long uploadSlotMask) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer (deferred)");
        if (BATCHED_SUBMITS) {
            DeferredSubmit entry = new DeferredSubmit();
            entry.commandBuffer = commandBuffer;
            entry.uploadSlotMask = uploadSlotMask;
            entry.batch = celtium$currentBatch();
            entry.batch.pending++;
            entry.fence = entry.batch.fence;
            this.pendingBatchedSubmits.add(entry);
            this.deferredSubmits.add(entry);
            this.deferredSubmitsOutstanding++;
            if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
                this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
            }
            return entry.fence;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer));
            long fence = celtium$borrowDeferredFence();
            try {
                VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, fence), "vkQueueSubmit (deferred)");
            } catch (RuntimeException | LinkageError submitError) {
                // The queue never saw the submission, so freeing here is the cleanup endCommands already proves.
                vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
                vkDestroyFence(this.device, fence, null);
                throw submitError;
            }
            DeferredSubmit entry = new DeferredSubmit();
            entry.commandBuffer = commandBuffer;
            entry.fence = fence;
            entry.uploadSlotMask = uploadSlotMask;
            this.deferredSubmits.add(entry);
            this.deferredSubmitsOutstanding++;
            if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
                this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
            }
            return fence;
        }
    }

    /**
     * Submits {@code commandBuffer} with the wait and signal semaphores given, tracked as outstanding rather than
     * waited. The deferred counterpart of {@link #submitWithSemaphores}: the semaphore contract is unchanged, only
     * the fence's job changes - it frees the command buffer later instead of blocking the render thread now.
     * <p>
     * Render thread only.
     */
    public void celtium$submitDeferredWithSemaphores(VkCommandBuffer commandBuffer, long[] waitSemaphores,
            long[] signalSemaphores) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        submitWithSemaphoresCalls++;
        submitWithSemaphoresWaits += waitSemaphores == null ? 0 : waitSemaphores.length;
        // Queue order: deferred (batched) submissions logically precede this direct one, and a vkQueueSubmit placed
        // ahead of them would reorder the queue. Flushing first keeps "logical submission order == queue order" for
        // every path that can submit.
        celtium$flushBatchedSubmits();
        check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer (deferred+semaphores)");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                    .pCommandBuffers(stack.pointers(commandBuffer));
            // Null handles are compacted out, exactly as submitWithSemaphores does: the bridge semaphores are 0 when
            // there is no bridge (GL-less), and a 0 in the array is an invalid-object validation error, not a no-op.
            java.util.List<Long> waitList = new java.util.ArrayList<Long>();
            if (waitSemaphores != null) {
                for (long semaphore : waitSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        waitList.add(Long.valueOf(semaphore));
                    }
                }
            }
            java.util.List<Long> signalList = new java.util.ArrayList<Long>();
            if (signalSemaphores != null) {
                for (long semaphore : signalSemaphores) {
                    if (semaphore != VK_NULL_HANDLE) {
                        signalList.add(Long.valueOf(semaphore));
                    }
                }
            }
            if (!waitList.isEmpty()) {
                long[] waits = new long[waitList.size()];
                int[] stages = new int[waitList.size()];
                for (int i = 0; i < waits.length; i++) {
                    waits[i] = waitList.get(i).longValue();
                    stages[i] = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                }
                submit.waitSemaphoreCount(waits.length);
                submit.pWaitSemaphores(stack.longs(waits));
                submit.pWaitDstStageMask(stack.ints(stages));
            }
            if (!signalList.isEmpty()) {
                long[] signals = new long[signalList.size()];
                for (int i = 0; i < signals.length; i++) {
                    signals[i] = signalList.get(i).longValue();
                }
                submit.pSignalSemaphores(stack.longs(signals));
            }
            if (BATCHED_SUBMITS) {
                DeferredSubmit entry = new DeferredSubmit();
                entry.commandBuffer = commandBuffer;
                entry.batch = celtium$currentBatch();
                entry.batch.pending++;
                entry.fence = entry.batch.fence;
                if (!waitList.isEmpty()) {
                    long[] waits = new long[waitList.size()];
                    int[] stages = new int[waitList.size()];
                    for (int i = 0; i < waits.length; i++) {
                        waits[i] = waitList.get(i).longValue();
                        stages[i] = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    }
                    entry.waitSemaphores = waits;
                    entry.waitStages = stages;
                }
                if (!signalList.isEmpty()) {
                    long[] signals = new long[signalList.size()];
                    for (int i = 0; i < signals.length; i++) {
                        signals[i] = signalList.get(i).longValue();
                    }
                    entry.signalSemaphores = signals;
                }
                this.pendingBatchedSubmits.add(entry);
                this.deferredSubmits.add(entry);
                this.deferredSubmitsOutstanding++;
                if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
                    this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
                }
                return;
            }
            long fence = celtium$borrowDeferredFence();
            try {
                VulkanContext.queueSubmitCalls++;
                        celtium$awaitPresenter();
                        check(vkQueueSubmit(this.queue, submit, fence), "vkQueueSubmit (deferred+semaphores)");
            } catch (RuntimeException | LinkageError submitError) {
                vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
                vkDestroyFence(this.device, fence, null);
                throw submitError;
            }
            DeferredSubmit entry = new DeferredSubmit();
            entry.commandBuffer = commandBuffer;
            entry.fence = fence;
            entry.uploadSlotMask = 0L;
            this.deferredSubmits.add(entry);
            this.deferredSubmitsOutstanding++;
            if (this.deferredSubmitsOutstanding > this.deferredSubmitsPeak) {
                this.deferredSubmitsPeak = this.deferredSubmitsOutstanding;
            }
        }
    }

    /**
     * Queues an ALREADY-ENDED frame-slot command buffer into the batched-submit list.
     * {@return the batch's shared fence the caller records as the slot's stand-in}.
     * <p>
     * The vkQueueSubmit fence limit - one per call - is the only thing keeping every frame recording on its own
     * driver call: ~8-9 recordings per frame were ~8-9 submits. The batch's one armed fence stands for every entry,
     * and {@link DeferredSubmit#slotReclaim} runs the slot's completion bookkeeping at reclaim exactly once -
     * the same facts an armed per-slot fence used to publish. Order is preserved by entry order inside the batch:
     * entries flush in the sequence they were queued, which is what the per-call submits expressed.
     * <p>
     * {@link #celtium$submitDeferred}'s contract differs in two places deliberately: the buffer is not ended here
     * (the coordinator already ended it inside its own bookkeeping), and it is never freed on reclaim - a frame
     * slot's buffer belongs to the coordinator's pool and outlives the entry.
     * <p>
     * Requires {@link #BATCHED_SUBMITS}; callers gate themselves on it.
     */
    public long celtium$submitDeferredFrame(VkCommandBuffer commandBuffer, long[] waitSemaphores, int[] waitStages,
            long[] signalSemaphores, int frameSlot, java.util.function.Consumer<Boolean> slotReclaim) {
        if (!BATCHED_SUBMITS) {
            throw new IllegalStateException("celtium$submitDeferredFrame requires batchedSubmits");
        }
        if (commandBuffer == null || frameSlot < 0 || slotReclaim == null) {
            throw new IllegalArgumentException("deferred frame submit needs a buffer, a slot and a reclaim callback");
        }
        requireUsable();
        DeferredSubmit entry = new DeferredSubmit();
        entry.commandBuffer = commandBuffer;
        entry.batch = celtium$currentBatch();
        entry.batch.pending++;
        entry.fence = entry.batch.fence;
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
        return entry.fence;
    }

    /**
     * Frees a confirmed deferred submission: its command buffer goes back to the pool, its fence is reset and pooled
     * (or destroyed past the cap), and the upload-ring slots it read become writable again. The submission's
     * completion counts once in the OPT-04 epoch.
     */
    private void celtium$reclaimDeferred(DeferredSubmit entry, boolean lostDevice) {
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
            // The fence is the batch's: armed once for every entry it carried, it returns to the pool only when the
            // last entry reclaims - pooling it early would let a new submission arm a fence older entries still
            // complete against.
            entry.batch.pending--;
            if (entry.batch.pending == 0) {
                entry.fence = entry.batch.fence;
                entry.batch = null;
            } else {
                entry.fence = VK_NULL_HANDLE;
            }
        }
        int reset = org.lwjgl.vulkan.VK10.VK_SUCCESS;
        if (entry.fence != VK_NULL_HANDLE && this.idleDeferredFences.size() < IDLE_DEFERRED_FENCE_CAP) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                LongBuffer handle = stack.mallocLong(1);
                handle.put(0, entry.fence);
                reset = vkResetFences(this.device, handle);
            }
        }
        if (entry.fence != VK_NULL_HANDLE) {
            if (reset == org.lwjgl.vulkan.VK10.VK_SUCCESS && this.idleDeferredFences.size() < IDLE_DEFERRED_FENCE_CAP) {
                this.idleDeferredFences.add(Long.valueOf(entry.fence));
            } else {
                vkDestroyFence(this.device, entry.fence, null);
            }
        }
        long mask = entry.uploadSlotMask;
        for (int slot = 0; slot < UPLOAD_SLOT_COUNT; slot++) {
            if ((mask & (1L << slot)) != 0L) {
                this.uploadSlotFree[slot] = true;
            }
        }
        entry.uploadSlotMask = 0L;
        this.deferredSubmitsOutstanding--;
        this.deferredReclaims++;
        noteCompletionConfirmed(lostDevice);
    }

    /** {@return the batch the next deferred submit joins, borrowing a fence on first use}. */
    private SharedBatch celtium$currentBatch() {
        if (this.pendingBatch == null) {
            this.pendingBatch = new SharedBatch();
            this.pendingBatch.fence = celtium$borrowDeferredFence();
        }
        return this.pendingBatch;
    }

    /**
     * Submits every queued deferred submission in one {@code vkQueueSubmit} call: one VkSubmitInfo per entry, each
     * keeping its own command buffer and wait/signal semaphores, and the batch's single shared fence armed by the
     * call. The render thread measured its deferred submits at roughly one driver call each - the queue saw about
     * eight group closes per frame inside the offer path alone - so the entries are gathered and the fence's
     * completion stands for all of them; per-submit order on one queue is preserved by array order.
     * <p>
     * Every path that could observe a submission completes or starts must flush first: a fence a caller was handed
     * is only armed when this submits, so awaiting it before the flush is a stall of the caller's own making.
     */
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
                + " flushes=" + batchedFlushCalls + "]";
    }

    public void celtium$flushBatchedSubmits() {
        if (this.pendingBatchedSubmits.isEmpty()) {
            return;
        }
        celtium$awaitPresenter();
        SharedBatch batch = this.pendingBatch;
        this.pendingBatch = null;
        java.util.ArrayList<DeferredSubmit> entries =
                new java.util.ArrayList<DeferredSubmit>(this.pendingBatchedSubmits);
        this.pendingBatchedSubmits.clear();
        Throwable submitError = submitBatch(batch, entries);
        if (submitError != null) {
            failBatch(batch, entries);
            if (submitError instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (LinkageError) submitError;
        }
    }

    /** About 14 KiB of MemoryStack per chunk, leaving room for frames the callers already hold. */
    private static final int SUBMIT_CHUNK = 128;

    private Throwable submitChunk(java.util.ArrayList<DeferredSubmit> entries, int from, int to, long fence) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSubmitInfo.Buffer submits = VkSubmitInfo.calloc(to - from, stack);
            for (int i = from; i < to; i++) {
                DeferredSubmit entry = entries.get(i);
                VkSubmitInfo submit = submits.get(i - from).sType$Default();
                submit.pCommandBuffers(stack.pointers(entry.commandBuffer));
                if (entry.waitSemaphores != null) {
                    submit.waitSemaphoreCount(entry.waitSemaphores.length);
                    submit.pWaitSemaphores(stack.longs(entry.waitSemaphores));
                    submit.pWaitDstStageMask(stack.ints(entry.waitStages));
                }
                if (entry.signalSemaphores != null) {
                    submit.pSignalSemaphores(stack.longs(entry.signalSemaphores));
                }
            }
            try {
                VulkanContext.queueSubmitCalls++;
                long batchedSubmitStarted = System.nanoTime();
                check(vkQueueSubmit(this.queue, submits, fence), "vkQueueSubmit (batched deferred)");
                batchedSubmitNanos += System.nanoTime() - batchedSubmitStarted;
                batchedSubmitCalls++;
            } catch (RuntimeException | LinkageError submitError) {
                return submitError;
            }
        }
        return null;
    }

    /**
     * Submits a detached batch in one {@code vkQueueSubmit}, on whichever thread holds the queue.
     *
     * @return the failure, or null; the caller's thread frees the entries through {@link #failBatch} on failure
     */
    private Throwable submitBatch(SharedBatch batch, java.util.ArrayList<DeferredSubmit> entries) {
        // In chunks: a loading screen can queue hundreds of uploads in one batch (measured on Enigmatica 2 Expert's
        // texture stitch: "OutOfMemoryError: Out of stack space" building one VkSubmitInfo array for all of them on the
        // 64 KiB MemoryStack). Chunks go to the queue in order and only the last arms the shared fence, which the
        // queue signals after every earlier submission - the same completion the single call gave.
        for (int from = 0; from < entries.size(); from += SUBMIT_CHUNK) {
            int to = Math.min(entries.size(), from + SUBMIT_CHUNK);
            Throwable failure = submitChunk(entries, from, to, to == entries.size() ? batch.fence : VK_NULL_HANDLE);
            if (failure != null) {
                // A failure after an earlier chunk reached the queue is a device-level error (the same call with the
                // same kind of entries just succeeded); the caller's free-everything path is then the lost-device one.
                return failure;
            }
        }
        batchedFlushCalls++;
        this.batchedFlushes++;
        this.batchedSubmissions += entries.size();
        return null;
    }

    /**
     * The queue never saw the batch: the entries leave the deferred queue and their resources free the same way the
     * single-submit failure path frees them, and the shared fence goes with them. Runs on the owning thread.
     */
    private void failBatch(SharedBatch batch, java.util.ArrayList<DeferredSubmit> entries) {
        for (DeferredSubmit entry : entries) {
            this.deferredSubmits.remove(entry);
            this.deferredSubmitsOutstanding--;
            if (entry.frameSlot >= 0) {
                // The slot buffer stays its pool's resource; the callback marks the submission "completed"
                // (lost, for pending purposes) so nothing waits on a fence that never armed.
                if (entry.slotReclaim != null) {
                    entry.slotReclaim.accept(Boolean.TRUE);
                }
            } else {
                vkFreeCommandBuffers(this.device, this.commandPool, entry.commandBuffer);
            }
            entry.commandBuffer = null;
            entry.slotReclaim = null;
            entry.uploadSlotMask = 0L;
        }
        vkDestroyFence(this.device, batch.fence, null);
    }

    /**
     * The frame's batched submit, the present blit's submit and {@code vkQueuePresentKHR} run on one worker thread while
     * the game thread starts the next frame ({@code -Dceltium.vulkan.asyncPresent=false} keeps them on the game thread).
     * Measured (paired KPI, same jar on/off, Xaero instance, 3 pairs each): Intel UHD 630 p50 2.45 -> 2.31 ms (0.942x),
     * AMD RX 6400 1.30 -> 1.22 ms (0.938x), p95 lower in every pair.
     * The driver's submit and present cost is main-thread time on every measured GPU (Intel UHD 630: ~0.35 ms a frame),
     * and the queue is the only object both threads touch: every queue access on the game thread goes through
     * {@link #queue()} or this class's own submit paths, and both join the worker first, so the queue stays externally
     * synchronized. The swapchain joins before its next acquire.
     */
    public static final boolean ASYNC_PRESENT =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.asyncPresent"));
    private java.util.concurrent.ExecutorService presenter;
    private java.util.concurrent.Future<?> presenterJob;
    private SharedBatch presenterBatch;
    private java.util.ArrayList<DeferredSubmit> presenterEntries;
    private volatile Throwable presenterBatchError;
    private volatile int presenterResult = VK_SUCCESS;
    public static long presenterJoinNanos;
    public static long presenterJoins;
    public static long presenterJobs;

    /** Waits for the worker's queue work, then frees a batch it could not submit; a no-op without a job in flight. */
    public void celtium$awaitPresenter() {
        java.util.concurrent.Future<?> job = this.presenterJob;
        if (job == null) {
            return;
        }
        this.presenterJob = null;
        long started = System.nanoTime();
        try {
            job.get();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException failed) {
            this.presenterBatchError = failed.getCause();
        }
        presenterJoinNanos += System.nanoTime() - started;
        presenterJoins++;
        SharedBatch batch = this.presenterBatch;
        java.util.ArrayList<DeferredSubmit> entries = this.presenterEntries;
        Throwable error = this.presenterBatchError;
        this.presenterBatch = null;
        this.presenterEntries = null;
        this.presenterBatchError = null;
        if (error != null && batch != null) {
            failBatch(batch, entries);
            System.out.println("[Celtium] CELTIUM_ASYNC_PRESENT batch submit failed on the presenter: " + error);
        }
    }

    /**
     * {@return whether {@code fence} is the one the presenter is still submitting}, which no other thread may read yet.
     * With {@code join} the caller is about to wait on it, so the worker is joined instead and the answer is false; a
     * finished worker is joined either way.
     */
    public boolean celtium$fenceOnPresenter(long fence, boolean join) {
        SharedBatch batch = this.presenterBatch;
        java.util.concurrent.Future<?> job = this.presenterJob;
        if (job == null || batch == null || batch.fence != fence) {
            return false;
        }
        if (!join && !job.isDone()) {
            return true;
        }
        celtium$awaitPresenter();
        return false;
    }

    /** {@return the last worker present's vkQueuePresentKHR result}; read after {@link #celtium$awaitPresenter()}. */
    public int celtium$presenterResult() {
        return this.presenterResult;
    }

    /**
     * Hands the pending batch, then the present blit and the present itself, to the worker.
     *
     * @return false when the worker could not be started, in which case nothing was handed over
     */
    public boolean celtium$presentAsync(long blitCommandBuffer, long acquireSemaphore, long renderFinishedSemaphore,
            long swapchain, int imageIndex) {
        celtium$awaitPresenter();
        if (this.presenter == null) {
            this.presenter = java.util.concurrent.Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Celtium presenter");
                thread.setDaemon(true);
                return thread;
            });
        }
        if (!this.pendingBatchedSubmits.isEmpty()) {
            this.presenterBatch = this.pendingBatch;
            this.presenterEntries = new java.util.ArrayList<DeferredSubmit>(this.pendingBatchedSubmits);
            this.pendingBatch = null;
            this.pendingBatchedSubmits.clear();
        }
        final SharedBatch batch = this.presenterBatch;
        final java.util.ArrayList<DeferredSubmit> entries = this.presenterEntries;
        final VkCommandBuffer blit = new VkCommandBuffer(blitCommandBuffer, this.device);
        presenterJobs++;
        if (presenterJobs == 1L || presenterJobs % 2000L == 0L) {
            System.out.println("[Celtium] CELTIUM_ASYNC_PRESENT jobs=" + presenterJobs + " joins=" + presenterJoins
                    + " joinMicros=" + (presenterJoinNanos / 1000L) + " batch=" + (batch != null));
        }
        this.presenterJob = this.presenter.submit(() -> {
            if (batch != null) {
                Throwable error = submitBatch(batch, entries);
                if (error != null) {
                    this.presenterBatchError = error;
                    this.presenterResult = VK_ERROR_DEVICE_LOST;
                    return;
                }
            }
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(blit))
                        .waitSemaphoreCount(1)
                        .pWaitSemaphores(stack.longs(acquireSemaphore))
                        .pWaitDstStageMask(stack.ints(org.lwjgl.vulkan.VK10.VK_PIPELINE_STAGE_ALL_COMMANDS_BIT))
                        .pSignalSemaphores(stack.longs(renderFinishedSemaphore));
                VulkanContext.queueSubmitCalls++;
                int submitted = vkQueueSubmit(this.queue, submit, VK_NULL_HANDLE);
                if (submitted != VK_SUCCESS) {
                    this.presenterResult = submitted;
                    return;
                }
                org.lwjgl.vulkan.VkPresentInfoKHR present = org.lwjgl.vulkan.VkPresentInfoKHR.calloc(stack)
                        .sType$Default()
                        .pWaitSemaphores(stack.longs(renderFinishedSemaphore))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(swapchain))
                        .pImageIndices(stack.ints(imageIndex));
                this.presenterResult = org.lwjgl.vulkan.KHRSwapchain.vkQueuePresentKHR(this.queue, present);
            }
        });
        return true;
    }

    /**
     * Reclaims every deferred submission whose fence has already signaled, in submission order, without waiting.
     * {@return how many were reclaimed}. A head whose fence has not signaled ends the walk: the queue executes
     * submissions in order, so nothing newer can have completed either.
     * <p>
     * Deliberately does NOT flush the pending batch first: a poll is a reclaim scan, and flushing here made every
     * mid-frame poll fragment the batch into ~1-entry submits (measured: 10.7 flushes/frame, submissions/batch 1.09).
     * Sites whose ordering or arming actually requires the queue to see the pending work call
     * {@link #celtium$flushBatchedSubmits()} themselves - the awaits, the one-copy submit, the present path, the
     * frame boundary and the slot wait all do. Pending entries left unflushed are simply "not complete": their
     * fences are unarmed and read NOT_READY, which is the truthful answer a poll owes.
     */
    public int celtium$pollDeferredSubmits() {
        int reclaimed = 0;
        while (!this.deferredSubmits.isEmpty()) {
            DeferredSubmit head = this.deferredSubmits.peek();
            if (head.batch != null && head.batch == this.presenterBatch) {
                // The presenter is submitting this fence, and a fence is externally synchronized with the submit that
                // arms it: read it only once the worker is done, and call it not ready until then.
                java.util.concurrent.Future<?> job = this.presenterJob;
                if (job != null && !job.isDone()) {
                    break;
                }
                celtium$awaitPresenter();
            }
            int status = vkGetFenceStatus(this.device, head.fence);
            if (status == org.lwjgl.vulkan.VK10.VK_NOT_READY) {
                break;
            }
            if (status != org.lwjgl.vulkan.VK10.VK_SUCCESS
                    && status != org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                // OUT_OF_HOST_MEMORY and the like say nothing about the fence's state: leave the entry pending and
                // let a later poll or wait answer it instead of treating an inconclusive result as completion.
                break;
            }
            this.deferredSubmits.poll();
            celtium$reclaimDeferred(head, status == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST);
            reclaimed++;
        }
        return reclaimed;
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
     * Boundedly waits on the deferred submission identified by {@code fence} - the handle
     * {@link #celtium$submitDeferred} returned. {@return false when the fence is no longer tracked because a poll or
     * settle already confirmed it}. Used by owners whose next write would overwrite memory the submission still reads
     * (a texture's staging buffer), so the wait covers only that one submission, never the whole queue.
     */
    public boolean celtium$awaitDeferredSubmit(long fence, String reason) {
        if (fence == VK_NULL_HANDLE) {
            return false;
        }
        celtium$flushBatchedSubmits();
        for (DeferredSubmit entry : this.deferredSubmits) {
            if (entry.fence == fence) {
                celtium$awaitDeferredEntry(entry, reason);
                return true;
            }
        }
        return false;
    }

    /** The bounded wait itself, on one tracked entry; on success or device loss the entry is reclaimed in place. */
    private void celtium$awaitDeferredEntry(DeferredSubmit entry, String reason) {
        if (entry.batch != null && entry.batch == this.presenterBatch) {
            celtium$awaitPresenter();
        }
        long waitStarted = System.nanoTime();
        int waited = vkWaitForFences(this.device, entry.fence, true, SUBMIT_TIMEOUT_NANOS);
        long waitedNanos = System.nanoTime() - waitStarted;
        this.deferredWaits++;
        this.deferredWaitMillis += waitedNanos / 1_000_000L;
        if (waitedNanos / 1_000_000L > this.deferredWaitWorstMillis) {
            this.deferredWaitWorstMillis = waitedNanos / 1_000_000L;
        }
        if (waited == org.lwjgl.vulkan.VK10.VK_SUCCESS
                || waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
            boolean lost = waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
            if (lost) {
                this.usable = false;
            }
            this.deferredSubmits.remove(entry);
            celtium$reclaimDeferred(entry, lost);
            if (lost) {
                throw new IllegalStateException("vkWaitForFences(deferred " + reason + ") reported"
                        + " VK_ERROR_DEVICE_LOST: treated as completion-equivalent for the resources, and the context"
                        + " is marked unusable (deferredWaits=" + this.deferredWaits + ")");
            }
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
        quarantine(entry.commandBuffer, entry.fence);
        entry.commandBuffer = null;
        entry.uploadSlotMask = 0L;
        if (waited == org.lwjgl.vulkan.VK10.VK_TIMEOUT) {
            throw new IllegalStateException("vkWaitForFences(deferred " + reason + ") did not signal within "
                    + (SUBMIT_TIMEOUT_NANOS / 1_000_000_000L) + "s; the command buffer and fence are quarantined"
                    + " rather than freed (submitTimeouts=" + submitTimeouts + ")");
        }
        throw new IllegalStateException("vkWaitForFences(deferred " + reason + ") did not confirm completion"
                + " and did not time out: result=" + waited + ". The queue may still own the submission, so its"
                + " command buffer and fence are quarantined rather than freed, and the context is marked unusable."
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
        this.pendingUploads.add(new PendingUpload(target, destinationOffset, stagingOffset, paddedLength));
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
        this.pendingUploads.add(new PendingUpload(target, destinationOffset, stagingOffset, paddedLength));
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
     * Destroys the command pool, the staging buffer, the device and the instance. Safe to call more than once, and
     * safe to call on a context whose creation failed halfway through.
     * <p>
     * Render thread only.
     */
    /**
     * Adds one bootstrap submission fence wait's duration (BH-27). Kept apart from the queue-wait totals so a report
     * can say which of the two happened: the queue-wide idle is now zero on the normal path, and these are the
     * bounded per-submission waits that replaced it.
     */
    private static void celtium$recordBootstrapFenceWait(long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        bootstrapSubmitWaitMillis += millis;
        if (millis > bootstrapSubmitWaitWorstMillis) {
            bootstrapSubmitWaitWorstMillis = millis;
        }
    }

    /** Adds one bootstrap queue wait's duration to the totals task-60 reports; called from both wait sites. */
    private static void celtium$recordBootstrapWait(long startedNanos) {
        long millis = (System.nanoTime() - startedNanos) / 1_000_000L;
        bootstrapQueueWaitMillis += millis;
        if (millis > bootstrapQueueWaitWorstMillis) {
            bootstrapQueueWaitWorstMillis = millis;
        }
    }

    public void delete() {
        if (this.device != null) {
            System.out.println("[Celtium] CELTIUM_WAIT_CENSUS bootstrapQueueWaits=" + bootstrapQueueWaits
                    + " bootstrapQueueWaitMillis=" + bootstrapQueueWaitMillis
                    + " bootstrapQueueWaitWorstMillis=" + bootstrapQueueWaitWorstMillis
                    + " submitTimeouts=" + submitTimeouts
                    + " " + waitOutcomeSummary()
                    + " " + uploadBatchSummary()
                    + " quarantinedCommandBuffers=" + this.quarantinedCommandBuffers.size()
                    + " quarantinedFences=" + this.quarantinedFences.size()
                    + " (the bootstrap submit path waits on the queue, which the API cannot bound; the frame path"
                    + " waits on its own fence with a 5s bound and reports a timeout as a stall)");
            // Every recorded copy is followed by a queue wait, so this only drains work that is already complete.
            //
            // Audit B04: this wait decides whether the quarantined resources may be released, and its result is
            // therefore checked. A device that reports itself idle releases them; a device that is lost releases them
            // too (SUCCESS-equivalent for pending/in-use purposes); a wait that fails for any other reason does NOT,
            // because the queue may still own them and freeing here is the same VUID the quarantine exists to avoid.
            // The resources are then left to the process teardown, and the count is reported.
            celtium$awaitPresenter();
            if (this.presenter != null) {
                this.presenter.shutdown();
            }
            int idle = vkDeviceWaitIdle(this.device);
            boolean releaseQuarantined = idle == VK_SUCCESS
                    || idle == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
            if (!releaseQuarantined) {
                deviceIdleFailures++;
                System.out.println("[Celtium] CELTIUM_WAIT_CENSUS vkDeviceWaitIdle did not confirm the device was idle"
                        + " (result=" + idle + "): " + this.quarantinedCommandBuffers.size() + " quarantined command"
                        + " buffer(s) and " + this.quarantinedFences.size() + " fence(s) are NOT released - the queue"
                        + " may still own them, and freeing here is what VUID-vkFreeCommandBuffers-pCommandBuffers-00047"
                        + " and VUID-vkDestroyFence-fence-01120 describe");
            }
            // The quarantined submissions are released here and only here: the device-wide wait above is the one point
            // where "the queue is finished with them" is a fact. Before this they are neither reset nor freed.
            if (releaseQuarantined) {
                for (VkCommandBuffer quarantined : this.quarantinedCommandBuffers) {
                    vkFreeCommandBuffers(this.device, this.commandPool, quarantined);
                }
                this.quarantinedCommandBuffers.clear();
                for (Long quarantinedFence : this.quarantinedFences) {
                    vkDestroyFence(this.device, quarantinedFence.longValue(), null);
                }
                this.quarantinedFences.clear();
                // Readback buffers quarantined with them (#364): the same device-wide wait is the point at which the
                // queue is known to be finished with the copies that were writing into them.
                releaseQuarantinedReadbackBuffers();
            }
            // Deferred submissions are under the same rule as the quarantine list: released only when the device
            // confirmed idle, otherwise counted as held. The fence pool itself is destroyed either way - on a
            // non-confirming wait the objects are already unreachable, which is what makes destroying them safe.
            if (releaseQuarantined) {
                try {
                    // Whatever is still queued was logically submitted already: push it to the queue so the drain
                    // below reclaims real completions rather than entries the GPU never saw.
                    celtium$flushBatchedSubmits();
                } catch (RuntimeException | LinkageError ignored) {
                    // Teardown already knows the device state; a failed flush frees the entries itself.
                }
                while (!this.deferredSubmits.isEmpty()) {
                    celtium$reclaimDeferred(this.deferredSubmits.poll(), false);
                }
            } else {
                this.deferredHeldAtTeardown = this.deferredSubmits.size();
                this.deferredSubmits.clear();
            }
            for (Long pooledFence : this.idleDeferredFences) {
                vkDestroyFence(this.device, pooledFence.longValue(), null);
            }
            this.idleDeferredFences.clear();
            destroyStaging();
            destroyUploadRing();
            savePipelineCache();
            for (Long ballast : EXPERIMENT_BALLAST) {
                freeMemory(this.device, ballast.longValue());
            }
            EXPERIMENT_BALLAST.clear();
            if (this.commandPool != VK_NULL_HANDLE) {
                vkDestroyCommandPool(this.device, this.commandPool, null);
                this.commandPool = VK_NULL_HANDLE;
            }
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
     * The device's pipeline cache, shared by every pipeline the renderer creates, or {@code VK_NULL_HANDLE}.
     * <p>
     * Every pipeline used to be created with no cache: each first use of a state combination compiled from scratch, in
     * every session, on the frame that needed it. The cache is seeded from {@code celtium/pipeline-cache.bin} under the
     * game directory and written back when the device is destroyed; the driver validates the header (vendor, device,
     * cache UUID) and ignores data from another device or driver version, so a stale file costs nothing.
     */
    public static long sharedPipelineCache() {
        return sharedPipelineCache;
    }

    private static volatile long sharedPipelineCache = VK_NULL_HANDLE;

    /**
     * Creates graphics pipelines through the shared cache, and times it: a pipeline the cache does not hold is compiled
     * on the thread that asked, which is the client thread for every pipeline a new draw state needs - a hitch the
     * first time an effect appears. Each creation over 2 ms is logged (bounded by the log limiter) with the running
     * totals, which {@link #pipelineCreationSummary()} also reports.
     */
    public static int createGraphicsPipelines(org.lwjgl.vulkan.VkDevice device,
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer info, java.nio.LongBuffer pipelines) {
        long start = System.nanoTime();
        int result = vkCreateGraphicsPipelines(device, sharedPipelineCache, info, null, pipelines);
        long took = System.nanoTime() - start;
        pipelineCreations++;
        pipelineCreationNanos += took;
        if (took > pipelineCreationWorstNanos) {
            pipelineCreationWorstNanos = took;
        }
        if (took > 2_000_000L) {
            System.out.println("[Celtium] CELTIUM_PIPELINE_SLOW ms=" + took / 1_000_000L + " thread="
                    + Thread.currentThread().getName() + " " + pipelineCreationSummary());
        }
        return result;
    }

    /** {@return the pipeline creations so far}: count, total and worst milliseconds. */
    public static String pipelineCreationSummary() {
        return "pipelineCreations=" + pipelineCreations + " pipelineCreationMs=" + pipelineCreationNanos / 1_000_000L
                + " pipelineCreationWorstMs=" + pipelineCreationWorstNanos / 1_000_000L;
    }

    /**
     * Allocates device memory and counts it. Intel's driver hands every live allocation to the kernel on each
     * {@code vkQueueSubmit}, so the number of live allocations is a per-frame cost there (leg intel-prof-1: queue submit
     * was the client thread's largest native cost on UHD 630, where Celtium measured 1.68x Celeritas). The live count,
     * its peak and the live bytes are reported by {@link #memoryAllocationSummary()}.
     */
    public static int allocateMemory(org.lwjgl.vulkan.VkDevice device, org.lwjgl.vulkan.VkMemoryAllocateInfo info,
            java.nio.LongBuffer memory) {
        int result = vkAllocateMemory(device, info, null, memory);
        if (result == VK_SUCCESS) {
            long size = info.allocationSize();
            LIVE_ALLOCATIONS.put(Long.valueOf(memory.get(memory.position())), Long.valueOf(size));
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

    /** Frees device memory allocated through {@link #allocateMemory}; a null handle is ignored as Vulkan ignores it. */
    public static void freeMemory(org.lwjgl.vulkan.VkDevice device, long memory) {
        if (memory != VK_NULL_HANDLE) {
            LIVE_ALLOCATIONS.remove(Long.valueOf(memory));
        }
        vkFreeMemory(device, memory, null);
    }

    /** {@return the device memory allocations}: live now, peak, made in total, and live bytes in MiB. */
    public static String memoryAllocationSummary() {
        long bytes = 0L;
        for (Long size : LIVE_ALLOCATIONS.values()) {
            bytes += size.longValue();
        }
        return "memoryAllocationsLive=" + LIVE_ALLOCATIONS.size() + " memoryAllocationsPeak=" + peakAllocations
                + " memoryAllocationsMade=" + allocationsMade + " memoryLiveMiB=" + (bytes >> 20);
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
    private static volatile long peakAllocations;
    private static volatile long allocationsMade;

    private static long pipelineCreations;
    private static long pipelineCreationNanos;
    private static long pipelineCreationWorstNanos;
    private long pipelineCache = VK_NULL_HANDLE;

    private static java.nio.file.Path pipelineCacheFile() {
        String configured = System.getProperty("celtium.vulkan.pipelineCacheFile");
        return configured != null ? java.nio.file.Paths.get(configured)
                : java.nio.file.Paths.get(System.getProperty("user.dir"), "celtium", "pipeline-cache.bin");
    }

    private void createPipelineCache() {
        if ("false".equalsIgnoreCase(System.getProperty("celtium.vulkan.pipelineCache"))) {
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
                sharedPipelineCache = this.pipelineCache;
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
            if (sharedPipelineCache == this.pipelineCache) {
                sharedPipelineCache = VK_NULL_HANDLE;
            }
            this.pipelineCache = VK_NULL_HANDLE;
        }
    }

    private void initialize(String applicationName, GlDeviceIdentity openGl) {
        this.interopPath = openGl != null;
        createInstance(applicationName);
        matchPhysicalDevice(openGl);
        createLogicalDevice();
        createQueueAndCommandPool();
        createPipelineCache();
        reportDeviceContractIfReady();
        this.memoryProperties = VkPhysicalDeviceMemoryProperties.calloc();
        vkGetPhysicalDeviceMemoryProperties(this.physicalDevice, this.memoryProperties);
    }

    private void createInstance(String applicationName) {
        boolean validation = Boolean.getBoolean(VALIDATION_PROPERTY);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkApplicationInfo application = VkApplicationInfo.calloc(stack).sType$Default()
                    .pApplicationName(stack.UTF8(applicationName))
                    .applicationVersion(1)
                    .apiVersion(VK12.VK_API_VERSION_1_2);
            VkInstanceCreateInfo createInfo = VkInstanceCreateInfo.calloc(stack).sType$Default()
                    .pApplicationInfo(application);
            // The platform surface extensions are instance extensions, so they have to be enabled here - but only
            // when the swapchain probe asks for them. A normal run keeps the exact extension set it was verified
            // with, and the probe reports "extension not enabled" instead of the instance being changed silently.
            List<String> enabledInstanceExtensions = new ArrayList<String>();
            // Enabled when anything wants to present: the diagnostic probe, or the frame present itself. Gating it on the probe
            // alone is what made a run with the probe off create no swapchain at all and look validation-clean for the
            // trivial reason that it presented nothing. It is still capability-checked below.
            if (Boolean.getBoolean(SWAPCHAIN_PROPERTY)
                    || ! "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.presentFromComposite", "true"))) {
                java.util.Set<String> available = instanceExtensionNames(stack);
                for (String candidate : new String[]{"VK_KHR_surface", "VK_KHR_xlib_surface", "VK_KHR_wayland_surface"}) {
                    if (available.contains(candidate)) {
                        enabledInstanceExtensions.add(candidate);
                    }
                }
            }
            Object messengerInfo = null;
            if (validation) {
                if (!ValidationBridge.available()) {
                    // The debug-utils callback API changed in LWJGL 3.4: 3.4.1 has VkDebugUtilsMessengerCallbackEXT, 3.4.3
                    // the ...EXTI interface. The renderer itself works on either, so only a run that *asks* for
                    // validation needs 3.4.3 - and it must say so instead of reporting a clean run it never measured.
                    throw new IllegalStateException("Validation was requested with -D" + VALIDATION_PROPERTY
                            + "=true, but this LWJGL Vulkan binding is older than 3.4.3 (no"
                            + " VkDebugUtilsMessengerCallbackEXTI). Install lwjgl-vulkan 3.4.3 or run without validation.");
                }
                messengerInfo = ValidationBridge.enable(this, stack, createInfo);
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
    /** Whether multiDrawIndirect was reported by the device and therefore enabled at creation. */
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

    private boolean multiDrawIndirectSupported;
    /** Whether drawIndirectFirstInstance was reported by the device and therefore enabled at creation. */
    private boolean drawIndirectFirstInstanceSupported;
    /** VkPhysicalDeviceLimits.maxDrawIndirectCount, a limit rather than a feature (0 when unreported). */
    private int maxDrawIndirectCount;

    /** {@return whether the device was created with {@code multiDrawIndirect} enabled}. */
    public boolean multiDrawIndirectEnabled() {
        return this.multiDrawIndirectSupported;
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

    /** {@return the device's {@code maxDrawIndirectCount}}; 0 means the limit was never read (OPT-09's clamp). */
    public int maxDrawIndirectCount() {
        return this.maxDrawIndirectCount;
    }
    /** The frame target's colour format and sample count: two of the native contract's fixed terms. */
    private static final String COLOR_FORMAT_NAME = "R8G8B8A8_UNORM";
    private static final int REQUIRED_SAMPLES = 1;
    /**
     * The largest push-constant block the immediate path uses - the fog-carrying POSITION layout's 208 bytes
     * ({@code VulkanImmediateTexturePipeline.PUSH_CONSTANT_BYTES}) and the lit ITEM layout's, which are equal today.
     * Reported beside the device's {@code maxPushConstantsSize} so the limit the renderer actually spends is named
     * with the device's number next to it (audit B01/B11); this run does not enforce it, it reports it.
     */
    private static final int PUSH_CONSTANT_BYTES_USED = 208;

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
        if (Boolean.getBoolean(SWAPCHAIN_PROPERTY)
                || ! "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.presentFromComposite", "true"))) {
            required.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
        }
        // Choosing it here is the same device the renderer will use, and it makes the contract line name the format
        // instead of "unresolved" (the chooser is idempotent and registers the device on first use).
        int depthFormat = VulkanDepthFormat.chosen(this.physicalDevice);
        required.add("color=" + COLOR_FORMAT_NAME + " depth=" + VulkanDepthFormat.chosenName()
                + "(0x" + Integer.toHexString(depthFormat) + ")"
                + " samples=" + REQUIRED_SAMPLES);
        int pushConstantLimit = properties.limits().maxPushConstantsSize();
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
                + " limits=[maxPushConstantsSize=" + pushConstantLimit + " immediatePushBytes=" + PUSH_CONSTANT_BYTES_USED
                + " maxImageDimension2D=" + properties.limits().maxImageDimension2D() + "]"
                + " indirect=[multiDrawIndirect=" + this.multiDrawIndirectSupported
                + " drawIndirectFirstInstance=" + this.drawIndirectFirstInstanceSupported
                + " maxDrawIndirectCount=" + this.maxDrawIndirectCount + "]"
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
            // Enabled only for the probe, and only when the device advertises it.
            if (Boolean.getBoolean(SWAPCHAIN_PROPERTY)
                    || ! "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.presentFromComposite", "true"))) {
                Set<String> available = deviceExtensionNames(stack, this.physicalDevice);
                if (available.contains(VK_KHR_SWAPCHAIN_EXTENSION_NAME)) {
                    deviceExtensions.add(VK_KHR_SWAPCHAIN_EXTENSION_NAME);
                }
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
            enabledFeatures.multiDrawIndirect(this.multiDrawIndirectSupported);
            enabledFeatures.drawIndirectFirstInstance(this.drawIndirectFirstInstanceSupported);
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
            // One descriptor set for every texture a window draws (architecture section 11.6): the shaders index an
            // array of sampled images with a value that comes from the draw itself, which is why the array needs
            // non-uniform indexing, a runtime-sized binding and update-after-bind - descriptors are written as textures
            // are registered while recorded buffers may still reference the set. All three are optional, so each is
            // asked for separately and reported; a device without them keeps the per-group sets.
            org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures indexingFeatures = null;
            {
                org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures supported =
                        org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures.calloc(stack).sType$Default();
                org.lwjgl.vulkan.VkPhysicalDeviceFeatures2 features2 = org.lwjgl.vulkan.VkPhysicalDeviceFeatures2
                        .calloc(stack).sType$Default().pNext(supported.address());
                org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2(this.physicalDevice, features2);
                boolean nonUniform = supported.shaderSampledImageArrayNonUniformIndexing();
                boolean runtimeArray = supported.runtimeDescriptorArray();
                boolean afterBind = supported.descriptorBindingSampledImageUpdateAfterBind();
                boolean partiallyBound = supported.descriptorBindingPartiallyBound();
                // Writing a new array index while earlier frames that bound the set are still executing is only legal
                // with UPDATE_UNUSED_WHILE_PENDING; the model instances register textures at exactly that time.
                boolean unusedWhilePending = supported.descriptorBindingUpdateUnusedWhilePending();
                this.textureArraySupported = nonUniform && runtimeArray && afterBind && partiallyBound
                        && unusedWhilePending;
                if (this.textureArraySupported) {
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
            check(vkCreateDevice(this.physicalDevice, createInfo, null, handle),
                    "vkCreateDevice(" + this.deviceName + ")");
            this.device = new VkDevice(handle.get(0), this.physicalDevice, createInfo);
            System.out.println("[Celtium] CELTIUM_DEVICE_FEATURES shaderDrawParameters="
                    + this.shaderDrawParametersSupported + " multiDrawIndirect=" + this.multiDrawIndirectSupported
                    + " maxDrawIndirectCount=" + this.maxDrawIndirectCount
                    + " textureArray=" + this.textureArraySupported);
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
        if (Boolean.getBoolean(HIDE_FD_EXTENSIONS_PROPERTY)) {
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
            check(VulkanContext.allocateMemory(this.device, allocate, memoryHandle),
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
     * Releases the upload ring, its batch fence and its semaphore.
     * <p>
     * A batch still in flight is waited for first: its copies read the ring's staging bytes, so destroying the ring
     * under it is exactly the use-after-free the batch's fence exists to prevent. The caller of this method has
     * already waited for device idle, so this wait finds the batch complete and only confirms the epoch.
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
     * Records the staged copy and submits it. Queue submission is the implicit memory dependency that makes the
     * host writes to the coherent staging memory visible to the transfer read; no explicit barrier is needed.
     */
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
        celtium$ensureUploadRing();
        int slot = celtium$writableSlot(bytes);
        if (slot < 0 && !this.pendingUploads.isEmpty()) {
            // The batch being recorded has filled the ring. It goes out now (one submission, no wait) and its slots
            // become the in-flight set until its fence confirms them, which is what the loop below reclaims.
            // Measured without this: a frame with more than four slots' worth of copies sent every further copy down
            // the settled one-copy path - 1546 of 2419 copies on the layered leg, each paying its own submission and
            // fence wait, which is exactly the cost this unit exists to remove.
            this.uploadEarlyFlushesForRingPressure++;
            flushUploads();
            slot = celtium$writableSlot(bytes);
        }
        if (slot < 0) {
            // Every slot is owned by a batch that has been submitted. A batch whose fence has already signalled frees
            // its slots for the cost of one fence query, so the queue is polled before anything is waited for - the
            // wait below is then only for a batch the GPU is genuinely still reading.
            int reclaimed = celtium$pollDeferredSubmits();
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
            celtium$awaitOldestDeferredSubmit("upload ring pressure");
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
            check(VulkanContext.allocateMemory(this.device, allocate, memoryHandle),
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
        for (long i = length; i < paddedLength; i++) {
            MemoryUtil.memPutByte(this.uploadRingMapped + stagingOffset + i, (byte) 0);
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
     * freed by that fence and by nothing else.
     * <p>
     * Render thread only. A caller that is about to submit work reading a destination of a pending copy must call this
     * first: the frame's own submission does, at its start.
     */
    public void flushUploads() {
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
                // Write-after-read: an arena range freed by a rebuild or a translucent re-sort is handed out again at
                // once, and frames already submitted may still be reading the old contents (four frames can be in
                // flight). Submission order alone does not order execution, so without this the copy could overwrite
                // indices a previous frame was drawing - the translucent layer's flicker (stained glass), whose index
                // ranges are replaced on every sort. An execution dependency is enough for a read-then-write hazard.
                vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                        VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, null);
                VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
                for (PendingUpload pending : this.pendingUploads) {
                    // Resolved here, not at record time: a growth between the two replaced the handle, and the offset
                    // stayed valid, so the copy belongs in the buffer the object has now. A destination that is gone
                    // resolves to VK_NULL_HANDLE - the arena or the world it belonged to was released, nothing reads
                    // it any more - and the copy is skipped rather than submitted against a dead handle.
                    long destination = pending.target.buffer();
                    if (destination == VK_NULL_HANDLE) {
                        this.uploadSkippedDeletedDestinations++;
                        continue;
                    }
                    copy.get(0).srcOffset(pending.stagingOffset).dstOffset(pending.destinationOffset)
                            .size(pending.size);
                    vkCmdCopyBuffer(commandBuffer, this.uploadRingBuffer, destination, copy);
                    copies++;
                    uploadSlotMask |= 1L << (int) (pending.stagingOffset / UPLOAD_SLOT_BYTES);
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
                this.pendingUploads.clear();
                for (int slot = 0; slot < this.uploadSlotCursor.length; slot++) {
                    this.uploadSlotCursor[slot] = 0L;
                    // A slot the batch read stays closed until the batch's fence confirms it; a refused submission
                    // owns nothing, so its slots reopen for the next batch.
                    this.uploadSlotFree[slot] = !enqueued || (uploadSlotMask & (1L << slot)) == 0L;
                }
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

    /** Submits every pending copy and waits for it and every other outstanding deferred submission to complete. */
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
        if (this.retiredBuffers.isEmpty()) {
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
        this.retiredBuffersDestroyed += freed;
        this.retiredBuffersOutstanding = 0;
        return freed;
    }

    /** {@return how many deferred buffers are waiting to be freed, and how many have been freed so far}. */
    public int[] celtium$retiredBufferCounts() {
        return new int[] {this.retiredBuffersOutstanding, this.retiredBuffersDestroyed};
    }

    public void settleUploads() {
        flushUploads();
        celtium$settleDeferredSubmits();
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

    private void recordStagingCopy(long destination, long destinationOffset, long length) {
        VkCommandBuffer commandBuffer = beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            // The same write-after-read dependency as the batched path (flushUploads): earlier frames may still read
            // the destination range.
            vkCmdPipelineBarrier(commandBuffer, VK_PIPELINE_STAGE_ALL_GRAPHICS_BIT | VK_PIPELINE_STAGE_TRANSFER_BIT,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null, null);
            VkBufferCopy.Buffer copy = VkBufferCopy.calloc(1, stack);
            copy.get(0).srcOffset(0L).dstOffset(destinationOffset).size(length);
            vkCmdCopyBuffer(commandBuffer, this.stagingBuffer, destination, copy);
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
            throw new IllegalStateException("This VulkanContext is unusable: a submission timed out and its command"
                    + " buffer and fence are quarantined (submitWithSemaphores timeouts=" + submitTimeouts + ")");
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
     * Reads the OpenGL device's identity, or reports that there is none.
     * <p>
     * Condition three is to start and draw without a GL context, and this is the first thing that has to change for it: the
     * identity exists for one reason - to pick the Vulkan device that is the <em>same</em> device, so that textures can be
     * shared between the two APIs - and with no bridge there is nothing to match. Failing the whole start-up because the
     * identity cannot be read makes the renderer depend on GL for a decision that only the bridge needs. The property exists so
     * the no-identity path can be exercised on a machine that does have a context.
     */
    private static GlDeviceIdentity inspectOpenGl() {
        if (Boolean.getBoolean("celtium.vulkan.withoutGlIdentity")
                || org.soutalab.celtium.vulkan.gl.CeltiumGlStub.isInstalled()) {
            // Either explicitly without an identity, or the GL surface is the Celtium stub: its strings say
            // "Celtium", not a driver, so there is nothing to match against. Device selection proceeds by
            // requirements alone.
            System.out.println("[Celtium] starting without an OpenGL device identity: no device matching, no interop");
            return null;
        }
        try {
            return GlDeviceIdentity.inspect();
        } catch (RuntimeException | LinkageError error) {
            System.out.println("[Celtium] no OpenGL device identity available (" + describe(error)
                    + "); continuing without device matching or interop");
            return null;
        }
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
