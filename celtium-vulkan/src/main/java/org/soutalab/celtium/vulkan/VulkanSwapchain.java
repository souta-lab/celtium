package org.soutalab.celtium.vulkan;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWNativeX11;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRWaylandSurface;
import org.lwjgl.vulkan.KHRXlibSurface;
import org.lwjgl.vulkan.VkClearColorValue;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;
import org.lwjgl.vulkan.VkWaylandSurfaceCreateInfoKHR;
import org.lwjgl.vulkan.VkXlibSurfaceCreateInfoKHR;

import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

/**
 * Native Vulkan presentation: the S2 foundation for the full-Vulkan goal.
 * <p>
 * Creates a {@code VkSurfaceKHR} from the window GLFW owns, picks a format, present mode and image count from what
 * the surface actually advertises, builds the swapchain and its views, and can acquire, clear and present an image.
 * Every choice is made from the queried capabilities - the platform is taken from {@code glfwGetPlatform()}, the
 * format from the surface's own list - never from a GPU name, and the Wayland and X11 paths are both implemented
 * because the validation harness runs Sway with Xwayland.
 * <p>
 * This class deliberately does not take over presentation from GL yet: in the current hybrid build GL still
 * presents the window, so the swapchain is used for capability evidence and a one-shot present probe until the
 * stages that replace Minecraft's framebuffers land. Threading: render thread only.
 */
public final class VulkanSwapchain {
    private final VulkanContext context;
    private final long surface;
    private final String platform;

    private long swapchain = VK_NULL_HANDLE;
    private long[] images = new long[0];
    private long[] views = new long[0];
    private int format;
    private int colorSpace;
    private int presentMode;
    private int width;
    private int height;

    private String argumentSummary = "not attempted";
    private long acquireSemaphore = VK_NULL_HANDLE;

    /**
     * One acquire semaphore per swapchain image, used round-robin.
     * <p>
     * A single semaphore cannot be reused while a previous wait on it is still pending, which the validation layer
     * reported as VUID-vkAcquireNextImageKHR-semaphore-01779 once this ran per frame instead of once in a probe. Cycling
     * through as many semaphores as there are images keeps the reuse distance at least one full set of images, which is
     * what the presentation engine guarantees.
     */
    private long[] acquireSemaphores;
    private int acquireSemaphoreCursor;
    /** One render-finished semaphore per image, paired with the acquire ones: the presentation waits on these. */
    private long[] renderFinishedSemaphores;
    private int renderFinishedCursor;
    private int presentTraces;
    /** Freshly created semaphores awaiting destruction with the swapchain (the {@code FRESH_SEMAPHORES} experiment). */
    private final java.util.List<Long> freshSemaphorePool = new java.util.ArrayList<Long>();
    private int presentReadbacks;
    /**
     * Whether the last {@code presentImage} submission reached the queue, and whether its present reached the screen.
     * <p>
     * Both are recorded for a caller that wants to fail closed - {@code endFrame} is only safe once the submission that
     * signals the bridge's done semaphore has been made, and GL's composite should only be cancelled once the frame
     * really reached the screen. A first attempt to have the caller act on them made the run fail (fixture FAILED,
     * four terrain passes), so they are reported without a caller relying on them yet; the attempt and its evidence are
     * in the history log.
     */
    private boolean lastPresentSubmitDone;
    /** Whether the last present actually reached the presentation engine; GL's composite may only be cancelled then. */
    private boolean lastPresentSucceeded;
    private float lastPresentedMean = -1.0f;
    /** {@code -Dceltium.vulkan.presentReadback=true} reads the presented image back and records its mean. */
    private static final boolean PRESENT_READBACK = Boolean.getBoolean("celtium.vulkan.presentReadback");
    /** Bisection switch: present without recording anything, to separate the recording from the semaphores. */
    private static final boolean PRESENT_NO_RECORD = Boolean.getBoolean("celtium.vulkan.presentNoRecord");
    /** Experiment: a fresh semaphore pair per present, to test whether reuse is what validation objects to. */
    private static final boolean FRESH_SEMAPHORES = Boolean.getBoolean("celtium.vulkan.freshPresentSemaphores");
    /** Puts the clear-and-present probe back on its earlier, previously clean submit shape. */
    private static final boolean PROBE_OLD_SHAPE = Boolean.getBoolean("celtium.vulkan.probeOldShape");

    /** {@return the mean of a centre patch of a swapchain image}, or -1 when it cannot be read. */
    public boolean lastPresentSubmitDone() {
        return this.lastPresentSubmitDone;
    }

    public boolean lastPresentSucceeded() {
        return this.lastPresentSucceeded;
    }

    public float lastPresentedMean() {
        return this.lastPresentedMean;
    }

    private static float readBackMean(VulkanContext context, long image, int width, int height) {
        long buffer = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int size = 8;
            int rowBytes = size * size * 4;
            org.lwjgl.vulkan.VkBufferCreateInfo bufferInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(rowBytes).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateBuffer(context.device(), bufferInfo, null, handle) != VK_SUCCESS) {
                return -1.0f;
            }
            buffer = handle.get(0);
            org.lwjgl.vulkan.VkMemoryRequirements requirements =
                    org.lwjgl.vulkan.VkMemoryRequirements.calloc(stack);
            vkGetBufferMemoryRequirements(context.device(), buffer, requirements);
            org.lwjgl.vulkan.VkMemoryAllocateInfo allocate = org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default().allocationSize(requirements.size())
                    .memoryTypeIndex(context.memoryTypeIndex(requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT,
                            "the present readback buffer"));
            if (vkAllocateMemory(context.device(), allocate, null, handle) != VK_SUCCESS) {
                return -1.0f;
            }
            memory = handle.get(0);
            vkBindBufferMemory(context.device(), buffer, memory, 0);

            VkCommandBuffer commands = context.beginCommands();
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            copy.get(0).imageOffset().set(Math.max(0, (width / 2) - (size / 2)),
                    Math.max(0, (height / 2) - (size / 2)), 0);
            copy.get(0).imageExtent().set(size, size, 1);
            vkCmdCopyImageToBuffer(commands, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
            context.endCommands(commands);

            PointerBuffer mapped = stack.mallocPointer(1);
            if (vkMapMemory(context.device(), memory, 0, rowBytes, 0, mapped) != VK_SUCCESS) {
                return -1.0f;
            }
            long total = 0;
            for (int i = 0; i < rowBytes; i++) {
                total += org.lwjgl.system.MemoryUtil.memGetByte(mapped.get(0) + i) & 0xFF;
            }
            vkUnmapMemory(context.device(), memory);
            return (float) ((double) total / rowBytes);
        } catch (RuntimeException | LinkageError error) {
            return -1.0f;
        } finally {
            if (buffer != VK_NULL_HANDLE) {
                vkDestroyBuffer(context.device(), buffer, null);
            }
            if (memory != VK_NULL_HANDLE) {
                vkFreeMemory(context.device(), memory, null);
            }
        }
    }

    private VulkanSwapchain(VulkanContext context, long surface, String platform) {
        this.context = context;
        this.surface = surface;
        this.platform = platform;
    }

    /**
     * Creates a surface for the window and a swapchain for it.
     *
     * @param window the GLFW window handle Minecraft renders into
     * @return the swapchain, or {@code null} with a reason appended to {@code reasons} when the platform or the
     *         surface is not usable
     */
    public static VulkanSwapchain create(VulkanContext context, long window, int width, int height,
                                         List<String> reasons) {
        if (context == null || window == 0L) {
            reasons.add("no window handle was available for a Vulkan surface");
            return null;
        }
        if (!context.hasDeviceExtension("VK_KHR_swapchain")
                || !(Boolean.getBoolean(VulkanContext.SWAPCHAIN_PROPERTY)
                || Boolean.getBoolean("celtium.vulkan.presentFromComposite"))) {
            reasons.add("the device was not created with VK_KHR_swapchain enabled (the swapchain probe enables it)");
            return null;
        }
        int platformId = GLFW.glfwGetPlatform();
        String platform;
        long surface;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            if (platformId == GLFW.GLFW_PLATFORM_WAYLAND) {
                platform = "wayland";
                long display = GLFWNativeWayland.glfwGetWaylandDisplay();
                long wlSurface = GLFWNativeWayland.glfwGetWaylandWindow(window);
                if (display == 0L || wlSurface == 0L) {
                    reasons.add("GLFW reported Wayland but no wl_display/wl_surface was available");
                    return null;
                }
                if (!context.hasInstanceExtension("VK_KHR_wayland_surface")) {
                    reasons.add("the device does not expose VK_KHR_wayland_surface");
                    return null;
                }
                VkWaylandSurfaceCreateInfoKHR create = VkWaylandSurfaceCreateInfoKHR.calloc(stack).sType$Default()
                        .display(display).surface(wlSurface);
                int result = KHRWaylandSurface.vkCreateWaylandSurfaceKHR(context.instance(), create, null, handle);
                if (result != VK_SUCCESS) {
                    reasons.add("vkCreateWaylandSurfaceKHR failed with VkResult " + result);
                    return null;
                }
                surface = handle.get(0);
            } else if (platformId == GLFW.GLFW_PLATFORM_X11) {
                platform = "x11";
                long display = GLFWNativeX11.glfwGetX11Display();
                long x11Window = GLFWNativeX11.glfwGetX11Window(window);
                if (display == 0L || x11Window == 0L) {
                    reasons.add("GLFW reported X11 but no Display/window was available");
                    return null;
                }
                if (!context.hasInstanceExtension("VK_KHR_xlib_surface")) {
                    reasons.add("the device does not expose VK_KHR_xlib_surface");
                    return null;
                }
                VkXlibSurfaceCreateInfoKHR create = VkXlibSurfaceCreateInfoKHR.calloc(stack).sType$Default()
                        .dpy(display).window(x11Window);
                int result = KHRXlibSurface.vkCreateXlibSurfaceKHR(context.instance(), create, null, handle);
                if (result != VK_SUCCESS) {
                    reasons.add("vkCreateXlibSurfaceKHR failed with VkResult " + result);
                    return null;
                }
                surface = handle.get(0);
            } else {
                reasons.add("GLFW platform " + platformId + " has no Vulkan surface path in this build");
                return null;
            }
        }

        // The presentation queue must be the queue this backend submits on; if the family cannot present, the
        // surface is unusable for this design and the caller is told rather than silently picking another queue.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer supported = stack.mallocInt(1);
            int result = vkGetPhysicalDeviceSurfaceSupportKHR(context.physicalDevice(), context.queueFamilyIndex(),
                    surface, supported);
            if (result != VK_SUCCESS || supported.get(0) != VK_TRUE) {
                reasons.add("queue family " + context.queueFamilyIndex() + " cannot present to the surface"
                        + " (VkResult " + result + ", supported=" + (result == VK_SUCCESS ? supported.get(0) : -1) + ")");
                return null;
            }
        }

        VulkanSwapchain swapchain = new VulkanSwapchain(context, surface, platform);
        try {
            if (!swapchain.createSwapchain(width, height, reasons)) {
                swapchain.delete();
                return null;
            }
        } catch (RuntimeException | LinkageError error) {
            // Fail closed with the reasons intact: an exception that escaped here would take the argument summary
            // with it, and the summary is the only thing that makes the next attempt diagnosable.
            reasons.add("swapchain creation threw " + error.getClass().getSimpleName() + ": " + error.getMessage());
            if (swapchain.argumentSummary != null && !"not attempted".equals(swapchain.argumentSummary)) {
                reasons.add("arguments: " + swapchain.argumentSummary);
            }
            swapchain.delete();
            return null;
        }
        return swapchain;
    }

    public long swapchain() {
        return this.swapchain;
    }

    /** {@return the argument values of the last swapchain creation attempt}, for diagnostics */
    public String argumentSummary() {
        return this.argumentSummary;
    }

    public long surface() {
        return this.surface;
    }

    public String platform() {
        return this.platform;
    }

    public int format() {
        return this.format;
    }

    public int presentMode() {
        return this.presentMode;
    }

    public int imageCount() {
        return this.images.length;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    /** {@return a one-line description of what the surface advertised and what was chosen} */
    public String describe() {
        return "swapchain: platform=" + this.platform + " format=" + this.format + " colourSpace="
                + this.colorSpace + " presentMode=" + this.presentMode + " images=" + this.images.length + " size="
                + this.width + "x" + this.height;
    }

    /**
     * Acquires an image, clears it to {@code argb} through a transfer-to-present transition and presents it.
     * <p>
     * This is the present path in miniature: it proves the surface, the swapchain, the acquire/present semaphores
     * and the layout transition work on the real window. It is not the final frame path - the full-Vulkan stages
     * will render into these images instead of clearing them.
     *
     * @return a description of the outcome, or a diagnostic when the cycle could not complete
     */
    public String clearAndPresent(float red, float green, float blue, float alpha) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            // One semaphore for the life of the swapchain, reused by every acquire: a semaphore waited by a present
            // must not be destroyed while that present is pending, and reusing it means the next acquire also orders
            // against the previous present.
            long acquire = acquireSemaphore(stack);
            if (acquire == VK_NULL_HANDLE) {
                return "present probe: could not create the acquire semaphore";
            }
            long renderFinished = VK_NULL_HANDLE;
            {
                int result = vkAcquireNextImageKHR(this.context.device(), this.swapchain, Long.MAX_VALUE, acquire,
                        VK_NULL_HANDLE, index);
                if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                    return "present probe: vkAcquireNextImageKHR returned " + result;
                }
                long image = this.images[index.get(0)];
                VkCommandBuffer commands = this.context.beginCommands();
                try {
                    transitionToTransferDst(stack, commands, image);
                    org.lwjgl.vulkan.VkClearColorValue color = org.lwjgl.vulkan.VkClearColorValue.calloc(stack);
                    color.float32(0, red);
                    color.float32(1, green);
                    color.float32(2, blue);
                    color.float32(3, alpha);
                    org.lwjgl.vulkan.VkImageSubresourceRange.Buffer range =
                            org.lwjgl.vulkan.VkImageSubresourceRange.calloc(1, stack);
                    range.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
                    vkCmdClearColorImage(commands, image, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, color, range);
                    transitionToPresent(stack, commands, image);
                } finally {
                    // Two shapes, one switch. The wait/signal pair is the shape the frame present uses, because a
                    // submission that writes the acquired image has to wait for the acquire. The earlier shape -
                    // submit with no semaphores, present waiting the acquire itself - measured zero validation
                    // messages in this very probe, so putting it back isolates whether the pair is what the layer
                    // objects to.
                    if (PROBE_OLD_SHAPE) {
                        this.context.endCommands(commands);
                    } else {
                        // The probe has no bridge: it presents a cleared image, so only the window-system
                        // acquire is waited and only its own render-finished semaphore is signalled.
                        this.context.submitWithSemaphores(commands, new long[] {acquire},
                                new long[] {renderFinishedFor(stack, index.get(0))});
                    }
                }
                VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                        // No explicit waitSemaphoreCount here: unlike VkSubmitInfo, LWJGL's
                        // VkPresentInfoKHR.pWaitSemaphores does set the count (verified in the 3.4.1 bytecode).
                        .pWaitSemaphores(stack.longs(PROBE_OLD_SHAPE ? acquire : renderFinishedFor(stack, index.get(0))))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(this.swapchain))
                        .pImageIndices(index);
                int presented = vkQueuePresentKHR(this.context.queue(), present);
                if (presented != VK_SUCCESS && presented != VK_SUBOPTIMAL_KHR) {
                    return "present probe: vkQueuePresentKHR returned " + presented;
                }
                return "present probe: cleared and presented image " + index.get(0) + " (" + presented + ")";
            }
        }
    }

    /**
     * Blits a Vulkan image into the swapchain and presents it - the mechanism that will replace Minecraft's
     * {@code Framebuffer.func_178038_a} shader blit.
     * <p>
     * That method was identified by capturing its stack: it is the game framebuffer's composite onto the screen,
     * implemented as a shader program over a full-screen quad with white vertices and uv 0..1, which is exactly the
     * batch the immediate path used to take and flatten the picture with. Presenting the shared colour image is what
     * replaces it.
     * <p>
     * The synchronisation follows the verified clear-and-present probe, which the validation layer accepts: the image is
     * acquired with the reusable semaphore and the presentation waits on it, so presentation is ordered after the
     * acquire. Wired to the frame path only when the backend asks for it, so this can be measured before it is trusted
     * with the frame.
     */
    public String presentImage(long sourceImage, int sourceLayout, int width, int height) {
        return presentImage(sourceImage, sourceLayout, width, height, VK_NULL_HANDLE, VK_NULL_HANDLE);
    }

    /**
     * Presents an image that the GL-to-Vulkan bridge wrote, honouring the bridge's contract as well as the swapchain's.
     * <p>
     * The frame target's contract is explicit: the caller's submission must wait {@code waitSemaphore()} - the shared
     * image is only complete once GL has signalled it - and must signal {@code doneSemaphore()}, because {@code endFrame}
     * makes GL wait on that one. Signalling only a render-finished semaphore leaves GL waiting for a submission that never
     * signalled it, which is the deadlock the audit's item B describes. Both contracts are satisfied by the same
     * submission here: it waits the acquire and the bridge, and signals its own semaphore and the bridge's.
     */
    public String presentImage(long sourceImage, int sourceLayout, int width, int height, long bridgeWaitSemaphore,
            long bridgeDoneSemaphore) {
        return presentImage(sourceImage, sourceLayout, width, height, bridgeWaitSemaphore, bridgeDoneSemaphore,
                sourceLayout);
    }

    /**
     * Presents an image and hands it back in {@code sourceLayoutAfter}.
     * <p>
     * The audit's B asks for the source to be returned in the layout its owner expects: the frame target keeps using the
     * shared colour image as a colour attachment, so leaving it in the transfer layout would force the next frame to
     * guess. The layout it goes back to is passed in rather than assumed here.
     */
    public String presentImage(long sourceImage, int sourceLayout, int width, int height, long bridgeWaitSemaphore,
            long bridgeDoneSemaphore, int sourceLayoutAfter) {
        if (this.swapchain == VK_NULL_HANDLE || sourceImage == VK_NULL_HANDLE) {
            return "present image: no swapchain or no source image";
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            long acquire;
            long renderFinished;
            if (FRESH_SEMAPHORES) {
                // Experiment: a new pair per present, never reused, so that "the acquire semaphore is reused before its
                // presentation retires" either shows up as zero messages or is eliminated. Written one handle per
                // mallocLong(1), the idiom the pool creation already uses: the LongBuffer-with-position shape made
                // javac itself fail under this module's -source 8.
                org.lwjgl.vulkan.VkSemaphoreCreateInfo semaphoreInfo =
                        org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack).sType$Default();
                LongBuffer one = stack.mallocLong(1);
                if (vkCreateSemaphore(this.context.device(), semaphoreInfo, null, one) != VK_SUCCESS) {
                    return "present image: could not create the acquire semaphore";
                }
                acquire = one.get(0);
                if (vkCreateSemaphore(this.context.device(), semaphoreInfo, null, one) != VK_SUCCESS) {
                    return "present image: could not create the render-finished semaphore";
                }
                renderFinished = one.get(0);
            } else {
                acquire = acquireSemaphore(stack);
                if (acquire == VK_NULL_HANDLE) {
                    return "present image: could not create the acquire semaphore";
                }
                // Chosen after the acquire, from the image it returned: a cursor can hand out a semaphore whose last
                // presentation has not retired, while the image index cannot - the presentation engine only returns an
                // image once its previous presentation is done, which is exactly the guarantee this needs.
                renderFinished = VK_NULL_HANDLE;
            }
            int result = vkAcquireNextImageKHR(this.context.device(), this.swapchain, Long.MAX_VALUE, acquire,
                    VK_NULL_HANDLE, index);
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                return "present image: vkAcquireNextImageKHR returned " + result;
            }
            long image = this.images[index.get(0)];
            if (renderFinished == VK_NULL_HANDLE) {
                renderFinished = renderFinishedFor(stack, index.get(0));
                if (renderFinished == VK_NULL_HANDLE) {
                    return "present image: could not create the render-finished semaphore";
                }
            }
            if (PRESENT_NO_RECORD) {
                // Bisection switch: present the acquired image without touching it or recording anything. If the
                // validation messages disappear, they come from the command recording (the layout transitions and the
                // blit); if they stay, they come from the acquire/present semaphore usage itself.
                VkPresentInfoKHR bare = VkPresentInfoKHR.calloc(stack).sType$Default()
                        .pWaitSemaphores(stack.longs(acquire))
                        .swapchainCount(1)
                        .pSwapchains(stack.longs(this.swapchain))
                        .pImageIndices(index);
                int bareResult = vkQueuePresentKHR(this.context.queue(), bare);
                return "present image (no record): presented image " + index.get(0) + " (" + bareResult + ")";
            }
            VkCommandBuffer commands = this.context.beginCommands();
            try {
                transitionForBlitSource(stack, commands, sourceImage, sourceLayout);
                transitionToTransferDst(stack, commands, image);
                org.lwjgl.vulkan.VkImageBlit.Buffer region = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
                region.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                        .layerCount(1);
                region.get(0).srcOffsets().get(0).set(0, 0, 0);
                region.get(0).srcOffsets().get(1).set(width, height, 1);
                region.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                        .layerCount(1);
                region.get(0).dstOffsets().get(0).set(0, 0, 0);
                region.get(0).dstOffsets().get(1).set(this.width, this.height, 1);
                vkCmdBlitImage(commands, sourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, image,
                        VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_NEAREST);
                transitionForBlitSourceBack(stack, commands, sourceImage, sourceLayoutAfter);
                transitionToPresent(stack, commands, image);
            } finally {
                // Waiting on the acquire semaphore and signalling a render-finished one: the submission writes the
                // acquired image, so it has to wait for the presentation engine to release it, and the presentation has
                // to wait for this submission rather than for the acquire.
                this.context.submitWithSemaphores(commands, acquire, renderFinished);
                this.lastPresentSubmitDone = true;
                this.lastPresentSucceeded = false;
            }
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                    .pWaitSemaphores(stack.longs(renderFinished))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.swapchain))
                    .pImageIndices(index);
            // Read the blitted image back before it is presented, so "the screen shows the frame" becomes a
            // measurement instead of an inference: the mean of a small centre patch of the presented image is compared
            // against the same patch of the game's frame in the caller's log.
            if (PRESENT_READBACK && this.presentReadbacks < 3) {
                this.presentReadbacks++;
                this.lastPresentedMean = readBackMean(this.context, image, this.width, this.height);
            }
            if (this.presentTraces < 4) {
                // One line per present with the handles involved: the validation messages named the acquire semaphore
                // in both a submit complaint and a present complaint, and with the handles in hand the pairing error is
                // arithmetic instead of inference.
                this.presentTraces++;
                System.out.println("celtium present trace: image=" + index.get(0) + " acquire=0x"
                        + Long.toHexString(acquire) + " renderFinished=0x" + Long.toHexString(renderFinished)
                        + " submitWaits=0x" + Long.toHexString(acquire) + " presentWaits=0x"
                        + Long.toHexString(renderFinished) + " acquireCursor=" + this.acquireSemaphoreCursor
                        + " renderCursor=" + this.renderFinishedCursor);
            }
            int presented = vkQueuePresentKHR(this.context.queue(), present);
            if (presented != VK_SUCCESS && presented != VK_SUBOPTIMAL_KHR) {
                return "present image: vkQueuePresentKHR returned " + presented;
            }
            this.lastPresentSucceeded = true;
            return "present image: blitted " + width + "x" + height + " onto image " + index.get(0) + " (" + presented
                    + ")";
        }
    }

    /** {@return the next acquire semaphore, creating the pool on first use}, or 0 when creation failed. */
    private long acquireSemaphore(MemoryStack stack) {
        int count = Math.max(2, this.images.length);
        if (this.acquireSemaphores == null) {
            long[] pool = new long[count];
            LongBuffer semaphore = stack.mallocLong(1);
            for (int i = 0; i < count; i++) {
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    return VK_NULL_HANDLE;
                }
                pool[i] = semaphore.get(0);
            }
            this.acquireSemaphores = pool;
        }
        long result = this.acquireSemaphores[this.acquireSemaphoreCursor];
        this.acquireSemaphoreCursor = (this.acquireSemaphoreCursor + 1) % this.acquireSemaphores.length;
        return result;
    }

    /** {@return the render-finished semaphore for a swapchain image}, created alongside the acquire pool on first use. */
    private long renderFinishedFor(MemoryStack stack, int imageIndex) {
        if (this.renderFinishedSemaphores == null) {
            int count = Math.max(2, this.images.length);
            long[] pool = new long[count];
            LongBuffer semaphore = stack.mallocLong(1);
            for (int i = 0; i < count; i++) {
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    return VK_NULL_HANDLE;
                }
                pool[i] = semaphore.get(0);
            }
            this.renderFinishedSemaphores = pool;
        }
        return this.renderFinishedSemaphores[imageIndex % this.renderFinishedSemaphores.length];
    }

    /** Moves the source image out of the transfer layout, back to the layout its owner uses it in. */
    private void transitionForBlitSourceBack(MemoryStack stack, VkCommandBuffer commands, long image, int target) {
        if (target == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            return;
        }
        org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .newLayout(target)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
        barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        barrier.get(0).srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(VK_ACCESS_MEMORY_WRITE_BIT);
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null,
                barrier);
    }

    /** Moves the source image to the layout a blit reads from, from wherever the frame left it. */
    private void transitionForBlitSource(MemoryStack stack, VkCommandBuffer commands, long image, int currentLayout) {
        if (currentLayout == VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL) {
            return;
        }
        org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier =
                org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .oldLayout(currentLayout)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
        barrier.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        barrier.get(0).srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null,
                barrier);
    }

    public void delete() {
        if (!this.freshSemaphorePool.isEmpty()) {
            vkQueueWaitIdle(this.context.queue());
            for (Long semaphore : this.freshSemaphorePool) {
                vkDestroySemaphore(this.context.device(), semaphore.longValue(), null);
            }
            this.freshSemaphorePool.clear();
        }
        if (this.renderFinishedSemaphores != null) {
            vkQueueWaitIdle(this.context.queue());
            for (long semaphore : this.renderFinishedSemaphores) {
                vkDestroySemaphore(this.context.device(), semaphore, null);
            }
            this.renderFinishedSemaphores = null;
        }
        if (this.acquireSemaphores != null) {
            vkQueueWaitIdle(this.context.queue());
            for (long semaphore : this.acquireSemaphores) {
                vkDestroySemaphore(this.context.device(), semaphore, null);
            }
            this.acquireSemaphores = null;
        }
        if (this.acquireSemaphore != VK_NULL_HANDLE) {
            // The queue is drained first: the semaphore may still be referenced by a pending present.
            vkQueueWaitIdle(this.context.queue());
            vkDestroySemaphore(this.context.device(), this.acquireSemaphore, null);
            this.acquireSemaphore = VK_NULL_HANDLE;
        }
        for (long view : this.views) {
            if (view != VK_NULL_HANDLE) {
                vkDestroyImageView(this.context.device(), view, null);
            }
        }
        this.views = new long[0];
        this.images = new long[0];
        if (this.swapchain != VK_NULL_HANDLE) {
            vkDestroySwapchainKHR(this.context.device(), this.swapchain, null);
            this.swapchain = VK_NULL_HANDLE;
        }
        if (this.surface != VK_NULL_HANDLE) {
            vkDestroySurfaceKHR(this.context.instance(), this.surface, null);
        }
    }

    private boolean createSwapchain(int requestedWidth, int requestedHeight, List<String> reasons) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            int result = vkGetPhysicalDeviceSurfaceCapabilitiesKHR(this.context.physicalDevice(), this.surface,
                    capabilities);
            if (result != VK_SUCCESS) {
                reasons.add("vkGetPhysicalDeviceSurfaceCapabilitiesKHR returned " + result);
                return false;
            }
            IntBuffer count = stack.mallocInt(1);
            vkGetPhysicalDeviceSurfaceFormatsKHR(this.context.physicalDevice(), this.surface, count, null);
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.calloc(count.get(0), stack);
            vkGetPhysicalDeviceSurfaceFormatsKHR(this.context.physicalDevice(), this.surface, count, formats);
            List<String> availableFormats = new ArrayList<>();
            boolean chosen = false;
            for (int i = 0; i < formats.capacity(); i++) {
                int candidate = formats.get(i).format();
                availableFormats.add(String.valueOf(candidate));
                if (!chosen && (candidate == VK_FORMAT_B8G8R8A8_UNORM || candidate == VK_FORMAT_R8G8B8A8_UNORM)) {
                    this.format = candidate;
                    this.colorSpace = formats.get(i).colorSpace();
                    chosen = true;
                }
            }
            if (!chosen) {
                if (formats.capacity() > 0) {
                    this.format = formats.get(0).format();
                    this.colorSpace = formats.get(0).colorSpace();
                } else {
                    reasons.add("the surface advertises no formats");
                    return false;
                }
            }

            vkGetPhysicalDeviceSurfacePresentModesKHR(this.context.physicalDevice(), this.surface, count, null);
            IntBuffer modes = stack.mallocInt(count.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(this.context.physicalDevice(), this.surface, count, modes);
            this.presentMode = VK_PRESENT_MODE_FIFO_KHR;
            boolean mailbox = false;
            for (int i = 0; i < modes.capacity(); i++) {
                if (modes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR) {
                    mailbox = true;
                }
            }

            // Mailbox is preferred when the surface offers it: it presents without tearing and without the FIFO
            // queue's latency, and it is core Vulkan, not a vendor extension.
            if (mailbox) {
                this.presentMode = VK_PRESENT_MODE_MAILBOX_KHR;
            }

            int extentWidth = capabilities.currentExtent().width() == -1 ? requestedWidth
                    : capabilities.currentExtent().width();
            int extentHeight = capabilities.currentExtent().height() == -1 ? requestedHeight
                    : capabilities.currentExtent().height();
            this.width = extentWidth;
            this.height = extentHeight;
            int imageCount = Math.max(capabilities.minImageCount() + 1,
                    Math.min(capabilities.maxImageCount() == 0 ? 3 : capabilities.maxImageCount(), 3));

            VkSwapchainCreateInfoKHR create = VkSwapchainCreateInfoKHR.calloc(stack).sType$Default()
                    .surface(this.surface)
                    .minImageCount(imageCount)
                    .imageFormat(this.format)
                    .imageColorSpace(this.colorSpace)
                    .imageExtent(e -> e.set(this.width, this.height))
                    .imageArrayLayers(1)
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                            | (capabilities.supportedUsageFlags() & VK_IMAGE_USAGE_TRANSFER_SRC_BIT))
                    // Exclusive sharing: there is only one queue family here, and the spec requires CONCURRENT to
                    // list at least two distinct families (VUID-VkSwapchainCreateInfoKHR-imageSharingMode-01278).
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .queueFamilyIndexCount(0)
                    .pQueueFamilyIndices(stack.mallocInt(1))
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                    .presentMode(this.presentMode)
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);
            LongBuffer handle = stack.mallocLong(1);
            // The argument values are recorded next to the call: LWJGL's generated checks throw a
            // NullPointerException when a buffer it considers required is null, and a bare frame list does not say
            // which one. Everything LWJGL could dereference is logged with its value here.
            this.argumentSummary = "create-info: surface=" + this.surface + " minImages=" + create.minImageCount()
                    + " format=" + create.imageFormat() + " colourSpace=" + create.imageColorSpace()
                    + " extent=" + create.imageExtent().width() + "x" + create.imageExtent().height()
                    + " layers=" + create.imageArrayLayers() + " usage=" + create.imageUsage()
                    + " sharing=" + create.imageSharingMode() + " queueFamilies=" + create.queueFamilyIndexCount()
                    + " queueFamilyPtr=" + (create.pQueueFamilyIndices() == null ? "null" : "set")
                    + " transform=" + create.preTransform() + " compositeAlpha=" + create.compositeAlpha()
                    + " presentMode=" + create.presentMode() + " clipped=" + create.clipped()
                    + " oldSwapchain=" + create.oldSwapchain() + " next=" + (create.pNext() == 0L ? "null" : "set")
                    + " swapchainOut=" + handle.remaining();
            // The summary goes into the reason list before the call: if the call throws (or fails), the swapchain
            // object is discarded and nothing else would carry the arguments the next attempt needs.
            reasons.add("arguments: " + this.argumentSummary);
            // A null allocator is the correct way to ask for the implementation's default; a zeroed
            // VkAllocationCallbacks is invalid (its function pointers must be non-null) and the driver dereferenced
            // them, which crashed the client.
            result = vkCreateSwapchainKHR(this.context.device(), create, null, handle);
            if (result != VK_SUCCESS) {
                reasons.add("vkCreateSwapchainKHR returned " + result + " (formats " + availableFormats + ") ["
                        + this.argumentSummary + "]");
                return false;
            }
            this.swapchain = handle.get(0);

            // A dedicated count buffer: LWJGL's argument check requires a null image buffer to come with a count of
            // zero, and the buffer used for the format and present-mode queries still holds their count.
            IntBuffer actualImageCount = stack.mallocInt(1);
            vkGetSwapchainImagesKHR(this.context.device(), this.swapchain, actualImageCount, null);
            int countImages = actualImageCount.get(0);
            LongBuffer imageHandles = stack.mallocLong(countImages);
            vkGetSwapchainImagesKHR(this.context.device(), this.swapchain, actualImageCount, imageHandles);
            this.images = new long[countImages];
            this.views = new long[countImages];
            for (int i = 0; i < this.images.length; i++) {
                this.images[i] = imageHandles.get(i);
                org.lwjgl.vulkan.VkImageViewCreateInfo view = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack)
                        .sType$Default()
                        .image(this.images[i])
                        .viewType(VK_IMAGE_VIEW_TYPE_2D)
                        .format(this.format);
                view.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
                LongBuffer viewHandle = stack.mallocLong(1);
                if (vkCreateImageView(this.context.device(), view, null, viewHandle) != VK_SUCCESS) {
                    reasons.add("could not create a view for swapchain image " + i);
                    return false;
                }
                this.views[i] = viewHandle.get(0);
            }
            return true;
        }
    }

    private void transitionToTransferDst(MemoryStack stack, VkCommandBuffer commands, long image) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .srcAccessMask(0)
                .dstAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .oldLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                .newLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
        barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null,
                null, barrier);
    }

    private void transitionToPresent(MemoryStack stack, VkCommandBuffer commands, long image) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack);
        barrier.get(0).sType$Default()
                .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
                .dstAccessMask(0)
                .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
                .newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                .image(image);
        barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
        vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null,
                null, barrier);
    }
}
