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
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackEXT;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCreateInfoEXT;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkDeviceQueueCreateInfo;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkInstanceCreateInfo;
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

    private static final String VALIDATION_LAYER_NAME = "VK_LAYER_KHRONOS_validation";

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
    /** Command buffers whose queue wait failed and whose command buffer was therefore quarantined instead of freed. */
    private int quarantinedQueueWaitBuffers;
    /** Command buffers and fences whose submission timed out: never freed while the queue may still own them. */
    private final java.util.Set<VkCommandBuffer> quarantinedCommandBuffers = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Set<Long> quarantinedFences = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Cleared when a submission times out: a device that stopped making progress gets no further work. */
    private volatile boolean usable = true;
    public static int submitWithSemaphoresCalls;
    public static int submitWithSemaphoresWaits;
    private VkPhysicalDeviceMemoryProperties memoryProperties;
    private long debugMessenger = VK_NULL_HANDLE;
    private VkDebugUtilsMessengerCallbackEXT validationCallback;

    // Cached host-visible staging buffer. VulkanBuffer and VulkanArena stream through it instead of allocating a
    // transfer buffer per call. The mapping is kept for the lifetime of the buffer, which is legal because the
    // memory is host-coherent and every upload waits for the queue to go idle before it returns.
    private long stagingBuffer = VK_NULL_HANDLE;
    private long stagingMemory = VK_NULL_HANDLE;
    private long stagingMappedAddress;
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
        // The driver may hand back a command buffer whose memory a previous submission is still reading, so the
        // queue is drained before a new one is allocated. Validation flags the alternative as a pending-state
        // reuse, and a half-rewritten command stream is exactly the kind of fault that makes a shader read the
        // wrong data. This is deliberately bootstrap quality; a real frame uses a small pool of buffers and fences.
        bootstrapQueueWaits++;
        // Unbounded by the API: vkQueueWaitIdle takes no timeout. Counted rather than described, so the claim that
        // the frame path is bounded and only this bootstrap path is not can be read off a run instead of argued from
        // the call sites (audit N14). The frame path waits on its own fence with a bound, and reports a stall by name.
        check(vkQueueWaitIdle(this.queue), "vkQueueWaitIdle(beginCommands)");
        VkCommandBuffer commandBuffer;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                    .commandPool(this.commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(this.device, allocate, handle), "vkAllocateCommandBuffers");
            commandBuffer = new VkCommandBuffer(handle.get(0), this.device);
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
                } else if (waited == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                    submitDeviceLost++;
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

    public void endCommands(VkCommandBuffer commandBuffer) {
        if (commandBuffer == null) {
            throw new IllegalArgumentException("commandBuffer must not be null");
        }
        requireUsable();
        boolean releasedToQueue = true;
        try {
            check(vkEndCommandBuffer(commandBuffer), "vkEndCommandBuffer");
            try (MemoryStack stack = MemoryStack.stackPush()) {
                VkSubmitInfo submit = VkSubmitInfo.calloc(stack).sType$Default()
                        .pCommandBuffers(stack.pointers(commandBuffer));
                check(vkQueueSubmit(this.queue, submit, VK_NULL_HANDLE), "vkQueueSubmit");
                bootstrapQueueWaits++;
                // Audit B04, the bootstrap half. Once the submit succeeded the queue owns the command buffer, and the
                // only thing that makes freeing it correct is a queue wait that confirms the queue is idle. A wait
                // that returns VK_ERROR_DEVICE_LOST does confirm that (the device is gone; the spec treats it as
                // SUCCESS-equivalent for pending/in-use purposes); anything else does not, and the buffer is then
                // quarantined instead of freed.
                int idle = vkQueueWaitIdle(this.queue);
                if (idle == VK_SUCCESS || idle == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                    if (idle == org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST) {
                        submitDeviceLost++;
                        this.usable = false;
                    }
                } else {
                    queueWaitFailures++;
                    this.usable = false;
                    releasedToQueue = false;
                    quarantine(commandBuffer, VK_NULL_HANDLE);
                    throw new IllegalStateException("vkQueueWaitIdle did not confirm the queue was idle: result="
                            + idle + ". The command buffer submitted just before it may still be owned by the queue,"
                            + " so it is quarantined rather than freed (this is not a lost device: only"
                            + " VK_ERROR_DEVICE_LOST is completion-equivalent for pending/in-use purposes)"
                            + " (queueWaitFailures=" + queueWaitFailures + ")");
                }
            }
        } finally {
            if (releasedToQueue) {
                vkFreeCommandBuffers(this.device, this.commandPool, commandBuffer);
            }
        }
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
        ensureStagingCapacity(paddedLength);
        MemoryUtil.memCopy(sourceAddress, this.stagingMappedAddress, length);
        zeroStagingTail(length, paddedLength);
        recordStagingCopy(destination, destinationOffset, paddedLength);
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
        ensureStagingCapacity(paddedLength);
        ByteBuffer staging = MemoryUtil.memByteBuffer(this.stagingMappedAddress, (int) paddedLength);
        MemoryUtil.memCopy(source.duplicate(), staging);
        zeroStagingTail(length, paddedLength);
        recordStagingCopy(destination, destinationOffset, paddedLength);
    }

    /**
     * Picks a memory type index that supports every flag in {@code requiredProperties} and is allowed by
     * {@code typeBits} (the {@code VkMemoryRequirements.memoryTypeBits} of the resource).
     *
     * @param purpose resource description used in the failure message
     * @throws IllegalStateException when no memory type satisfies the request
     */
    int memoryTypeIndex(int typeBits, int requiredProperties, String purpose) {
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
    public void delete() {
        if (this.device != null) {
            System.out.println("[Celtium] CELTIUM_WAIT_CENSUS bootstrapQueueWaits=" + bootstrapQueueWaits
                    + " submitTimeouts=" + submitTimeouts
                    + " " + waitOutcomeSummary()
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
            }
            destroyStaging();
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
                EXTDebugUtils.vkDestroyDebugUtilsMessengerEXT(this.instance, this.debugMessenger, null);
                this.debugMessenger = VK_NULL_HANDLE;
            }
            vkDestroyInstance(this.instance, null);
            this.instance = null;
        }
        if (this.validationCallback != null) {
            this.validationCallback.free();
            this.validationCallback = null;
        }
        this.queueFamilyIndex = -1;
        this.deviceName = null;
        this.deviceUuid = null;
    }

    private void initialize(String applicationName, GlDeviceIdentity openGl) {
        this.interopPath = openGl != null;
        createInstance(applicationName);
        matchPhysicalDevice(openGl);
        createLogicalDevice();
        createQueueAndCommandPool();
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
            VkDebugUtilsMessengerCreateInfoEXT messengerInfo = null;
            if (validation) {
                messengerInfo = enableValidation(stack, createInfo);
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
                LongBuffer messenger = stack.mallocLong(1);
                check(EXTDebugUtils.vkCreateDebugUtilsMessengerEXT(this.instance, messengerInfo, null, messenger),
                        "vkCreateDebugUtilsMessengerEXT");
                this.debugMessenger = messenger.get(0);
            }
        }
    }

    /**
     * Adds the validation layer and the debug-utils messenger to the instance create info. Only reached when
     * {@link #VALIDATION_PROPERTY} is set, so a normal run enables no layer and no instance extension at all.
     */
    private VkDebugUtilsMessengerCreateInfoEXT enableValidation(MemoryStack stack, VkInstanceCreateInfo createInfo) {
        if (!instanceExtensionNames(stack).contains(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)) {
            throw new IllegalStateException("Validation was requested with -D" + VALIDATION_PROPERTY + "=true, but the"
                    + " loader does not offer " + EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME + ".");
        }
        if (!instanceLayerNames(stack).contains(VALIDATION_LAYER_NAME)) {
            throw new IllegalStateException("Validation was requested with -D" + VALIDATION_PROPERTY + "=true, but the"
                    + " layer " + VALIDATION_LAYER_NAME + " is not installed.");
        }
        createInfo.ppEnabledLayerNames(stack.pointers(stack.UTF8(VALIDATION_LAYER_NAME)));
        createInfo.ppEnabledExtensionNames(stack.pointers(stack.UTF8(EXTDebugUtils.VK_EXT_DEBUG_UTILS_EXTENSION_NAME)));
        this.validationCallback = VkDebugUtilsMessengerCallbackEXT.create((severity, types, data, user) -> {
            VkDebugUtilsMessengerCallbackDataEXT callbackData = VkDebugUtilsMessengerCallbackDataEXT.create(data);
            String idName = callbackData.pMessageIdNameString();
            long idNumber = callbackData.messageIdNumber();
            String message = callbackData.pMessageString();
            // Structured, one line per message, because the harness has to decide from this text whether a run is clean and
            // a free-form message is exactly what defeated the old check: it skipped anything not starting with "vk", so a
            // line reading "Validation Error: [ VUID-... ]" counted as nothing. Severity and message id are what the gate
            // keys on now, and newlines are folded so one message cannot look like two lines or like none.
            String severityName;
            if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT) != 0) {
                severityName = "ERROR";
            } else if ((severity & VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT) != 0) {
                severityName = "WARNING";
            } else if ((severity & org.lwjgl.vulkan.EXTDebugUtils.VK_DEBUG_UTILS_MESSAGE_SEVERITY_INFO_BIT_EXT) != 0) {
                severityName = "INFO";
            } else {
                severityName = "VERBOSE";
            }
            String identifier = idName != null && !idName.isEmpty() ? idName : Long.toString(idNumber);
            String single = message == null ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
            System.err.println("Celtium Vulkan validation: severity=" + severityName + " id=" + identifier + " " + single);
            return VK_FALSE;
        });
        VkDebugUtilsMessengerCreateInfoEXT messengerInfo = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
                .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_SEVERITY_WARNING_BIT_EXT)
                .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_GENERAL_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT
                        | VK_DEBUG_UTILS_MESSAGE_TYPE_PERFORMANCE_BIT_EXT)
                .pfnUserCallback(this.validationCallback);
        // Chained into the instance create info as well, so the messenger also reports whatever the driver says
        // while the instance itself is created and destroyed.
        createInfo.pNext(messengerInfo.address());
        return messengerInfo;
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
                if (properties.properties().deviceType() == VK_PHYSICAL_DEVICE_TYPE_CPU) {
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
                String skipped = this.skippedForInteropExtensions == null ? ""
                        : " Candidates skipped for lacking the interop fd extensions: "
                                + String.join(", ", this.skippedForInteropExtensions) + ".";
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
    /** The extensions the chosen device advertised, kept until the contract line can be printed with the queue. */
    private Set<String> advertisedDeviceExtensions;
    /** The device extensions this context actually enabled, named in the contract line rather than implied. */
    private List<String> enabledDeviceExtensions = new ArrayList<String>();
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
                + " advertised=[externalMemoryFd=" + extensions.contains(VK_KHR_EXTERNAL_MEMORY_FD_EXTENSION_NAME)
                + " externalSemaphoreFd=" + extensions.contains(VK_KHR_EXTERNAL_SEMAPHORE_FD_EXTENSION_NAME)
                + " swapchain=" + extensions.contains(VK_KHR_SWAPCHAIN_EXTENSION_NAME) + "]"
                + (this.skippedForInteropExtensions == null ? ""
                        : " skippedForInteropExtensions=[" + String.join(", ", this.skippedForInteropExtensions) + "]"));
        reportDriverInfo(stack);
    }

    // ---- what is rendering right now: the real driver, from the device that renders (task-46) ----------------

    /** The rendering device's own identity, queried once and named in one line. "unavailable" when it cannot be. */
    private static volatile String driverLine = "unavailable";
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
            PointerBuffer names = stack.mallocPointer(deviceExtensions.size());
            for (String extension : deviceExtensions) {
                names.put(stack.UTF8(extension));
            }
            names.flip();
            this.enabledDeviceExtensions = new ArrayList<String>(deviceExtensions);
            VkDeviceCreateInfo createInfo = VkDeviceCreateInfo.calloc(stack).sType$Default()
                    .pQueueCreateInfos(queues)
                    .ppEnabledExtensionNames(names);
            PointerBuffer handle = stack.mallocPointer(1);
            check(vkCreateDevice(this.physicalDevice, createInfo, null, handle),
                    "vkCreateDevice(" + this.deviceName + ")");
            this.device = new VkDevice(handle.get(0), this.physicalDevice, createInfo);
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

    private static Set<String> instanceExtensionNames(MemoryStack stack) {
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

    private static Set<String> instanceLayerNames(MemoryStack stack) {
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
            check(vkAllocateMemory(this.device, allocate, null, memoryHandle),
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
                vkFreeMemory(this.device, memory, null);
            }
            throw error;
        }
        this.stagingBuffer = buffer;
        this.stagingMemory = memory;
        this.stagingMappedAddress = mapped;
        this.stagingCapacityBytes = bytes;
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
            vkFreeMemory(this.device, this.stagingMemory, null);
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
    private void recordStagingCopy(long destination, long destinationOffset, long length) {
        VkCommandBuffer commandBuffer = beginCommands();
        try (MemoryStack stack = MemoryStack.stackPush()) {
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
