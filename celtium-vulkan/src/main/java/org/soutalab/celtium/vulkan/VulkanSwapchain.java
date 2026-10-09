package org.soutalab.celtium.vulkan;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWayland;
import org.lwjgl.glfw.GLFWNativeX11;
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
                || !Boolean.getBoolean(VulkanContext.SWAPCHAIN_PROPERTY)) {
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
            if (this.acquireSemaphore == VK_NULL_HANDLE) {
                LongBuffer semaphore = stack.mallocLong(1);
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    return "present probe: could not create an acquire semaphore";
                }
                this.acquireSemaphore = semaphore.get(0);
            }
            long acquire = this.acquireSemaphore;
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
                    this.context.endCommands(commands);
                }
                VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                        .pWaitSemaphores(stack.longs(acquire))
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
        if (this.swapchain == VK_NULL_HANDLE || sourceImage == VK_NULL_HANDLE) {
            return "present image: no swapchain or no source image";
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer index = stack.mallocInt(1);
            if (this.acquireSemaphore == VK_NULL_HANDLE) {
                LongBuffer semaphore = stack.mallocLong(1);
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    return "present image: could not create an acquire semaphore";
                }
                this.acquireSemaphore = semaphore.get(0);
            }
            long acquire = this.acquireSemaphore;
            int result = vkAcquireNextImageKHR(this.context.device(), this.swapchain, Long.MAX_VALUE, acquire,
                    VK_NULL_HANDLE, index);
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                return "present image: vkAcquireNextImageKHR returned " + result;
            }
            long image = this.images[index.get(0)];
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
                transitionToPresent(stack, commands, image);
            } finally {
                this.context.endCommands(commands);
            }
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                    .pWaitSemaphores(stack.longs(acquire))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.swapchain))
                    .pImageIndices(index);
            int presented = vkQueuePresentKHR(this.context.queue(), present);
            if (presented != VK_SUCCESS && presented != VK_SUBOPTIMAL_KHR) {
                return "present image: vkQueuePresentKHR returned " + presented;
            }
            return "present image: blitted " + width + "x" + height + " onto image " + index.get(0) + " (" + presented
                    + ")";
        }
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
                    .imageUsage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT)
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
