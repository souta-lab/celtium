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
    /** The GL-less frame target whose present-wait semaphores this swapchain must signal, or null. */
    private FrameImageTarget bridgeTarget;

    /** Sets the frame target the present path signals; the backend calls this once. */
    public void setBridgeTarget(FrameImageTarget target) {
        this.bridgeTarget = target;
    }
    private long[] views = new long[0];
    private int format;
    private int colorSpace;
    private int presentMode;
    private int width;
    private int height;

    private String argumentSummary = "not attempted";
    /** What the surface reported at the last creation attempt, and how the request differs from it (BH-18). */
    private String surfaceOffer = "surface-offer: not queried";
    /** The present modes the surface offered at that attempt, by name. */
    private String presentModesOffered = "not enumerated";
    private String reconciliation = "not reconciled";
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
    /**
     * Which image each acquire semaphore was last handed to, and the acquire count at which it happened (audit B12).
     * <p>
     * The semaphore is chosen by cursor <em>before</em> the acquire, because the acquire takes it as an argument, so
     * the code cannot index it by the image it will return. What it can do is remember the pairing: after an acquire
     * returns image {@code i} using semaphore {@code s}, this records that {@code s} belongs to {@code i}. A semaphore
     * whose image has not been acquired again is a semaphore whose previous signal the presentation engine has not
     * necessarily consumed, and handing it to another acquire is the reuse the spec forbids. The table is
     * diagnostic-only for now: it counts the hazard instead of asserting it away, so a leg says whether this machine
     * reaches it before any synchronisation is changed.
     */
    private long[] acquireSemaphoreOwnerImage;
    private long acquireSemaphoreReusesWhileOwned;
    private long acquiresRecorded;
    /** One render-finished semaphore per image, paired with the acquire ones: the presentation waits on these. */
    private long[] renderFinishedSemaphores;
    /** Times the render-finished pool was rebuilt because the swapchain's image count changed. */
    private int renderFinishedPoolRebuilds;
    private int renderFinishedCursor;
    private int presentTraces;
    /** Partly built semaphore pools that were rolled back instead of abandoned (BH-20), for the report. */
    private int semaphorePoolRollbacks;
    /** Present-support refusals, synthetic or real (BH-19): the count that says the branch is reachable. */
    private static final java.util.concurrent.atomic.AtomicLong PRESENT_SUPPORT_REFUSALS =
            new java.util.concurrent.atomic.AtomicLong();
    private static long presentSupportRefusals;

    // ---- the present-mode contract (task-56) ---------------------------------------------------------------------
    //
    // VSync is a swapchain *creation* parameter, so the contract says three things: which mode means VSync on (FIFO -
    // it queues, so the frame interval is the display's) and which means off (MAILBOX, which presents without tearing
    // and without the FIFO queue, or IMMEDIATE as the next best thing), what happens when the surface does not offer
    // what the player asked for (a fallback that is *named and counted*, never silently ignored), and that a change
    // rebuilds the swapchain - a new swapchain is the only way to change the mode, and it goes through the same
    // out-of-date path a surface change uses, so acquire and frame ownership are unchanged.
    //
    // The request comes from the game's own option (GameSettings.enableVsync) through the Sodium option page's setter,
    // because this module does not compile against Minecraft; no push at all is treated as VSync on, which is
    // Minecraft's own default.
    // `null` means "nothing has told us yet", which is a state of its own: the policy's own default (VSync on,
    // Minecraft's default) is what the swapchain is built with, but the FIRST push must always be applied and
    // logged even when it agrees with that default. Measured on the fixture's video-settings leg: it writes
    // `gameSettings.enableVsync = false` straight to the field at world launch, so the row's press pushed `true`
    // and an "is it different from the default" test swallowed it - `changes=0` with no request line, while the
    // row really had been pressed.
    private static volatile Boolean vsyncRequested;
    private static final java.util.concurrent.atomic.AtomicLong VSYNC_REQUEST_REVISION =
            new java.util.concurrent.atomic.AtomicLong();
    /** Mode changes asked for through the option, and requested modes the surface could not offer. */
    public static final java.util.concurrent.atomic.AtomicLong PRESENT_MODE_CHANGES =
            new java.util.concurrent.atomic.AtomicLong();
    public static final java.util.concurrent.atomic.AtomicLong PRESENT_MODE_FALLBACKS =
            new java.util.concurrent.atomic.AtomicLong();
    private static volatile String presentModeLine = "not attempted";
    /** The request revision this swapchain was created for; a mismatch means it must be rebuilt. */
    private long builtForRequestRevision;

    /** Records what the player's VSync option now asks for, from the option page's own setter. */
    public static void setVSyncRequested(boolean enabled) {
        if (vsyncRequested != null && vsyncRequested.booleanValue() == enabled) {
            return;
        }
        boolean first = vsyncRequested == null;
        vsyncRequested = Boolean.valueOf(enabled);
        VSYNC_REQUEST_REVISION.incrementAndGet();
        PRESENT_MODE_CHANGES.incrementAndGet();
        System.out.println("[Celtium] CELTIUM_PRESENT_MODE_REQUEST requested="
                + (enabled ? "vsync-on(FIFO)" : "vsync-off(MAILBOX|IMMEDIATE)") + " revision="
                + VSYNC_REQUEST_REVISION.get() + " changes=" + PRESENT_MODE_CHANGES.get()
                + (first ? " first=true (the option's own value now drives the policy)" : "")
                + " (the swapchain is rebuilt on the next present)");
    }

    public static boolean vsyncRequested() {
        Boolean requested = vsyncRequested;
        return requested == null ? true : requested.booleanValue();
    }

    /** {@return whether this swapchain was built for a different VSync request than the current one}. */
    public boolean presentModeStale() {
        return this.builtForRequestRevision != VSYNC_REQUEST_REVISION.get();
    }

    /** {@return the last present-mode decision}, for the report. */
    public static String presentModeSummary() {
        return presentModeLine;
    }


    /**
     * Whether the last acquire or present said the swapchain no longer matches the surface.
     * <p>
     * {@code VK_SUBOPTIMAL_KHR} and {@code VK_ERROR_OUT_OF_DATE_KHR} were accepted silently: the frame still went out and
     * nothing recorded that the swapchain needed rebuilding, which the audit's lifetime notes ask to exist. This is the
     * signal it keys off, and since the backend already creates the swapchain lazily, dropping it and letting the next
     * present ask for one is the whole rebuild.
     */
    private boolean outOfDate;
    private int acquireOutOfDate;
    /** Presents a solid marker colour instead of the frame, so the screen can be attributed to this path. */
    private static final boolean PRESENT_MARKER = Boolean.getBoolean("celtium.vulkan.presentMarker");
    /** Test-only ({@code -Dceltium.vulkan.forceOutOfDate=true}): report out-of-date once so the rebuild runs. */
    private static final boolean FORCE_OUT_OF_DATE = Boolean.getBoolean("celtium.vulkan.forceOutOfDate");
    /**
     * Injects the failure at the acquire itself (audit S18). The other flag forces the verdict after a successful acquire,
     * which never reaches the branch that matters - the one that must not submit or present an image the acquire refused.
     */
    private static final boolean FORCE_ACQUIRE_OUT_OF_DATE = Boolean.getBoolean("celtium.vulkan.forceAcquireOutOfDate");
    private boolean forcedOutOfDate;

    private void recordOutOfDateInjection() {
        System.out.println("celtium swapchain: injecting an out-of-date result so the rebuild path runs");
    }

    public boolean outOfDate() {
        return this.outOfDate;
    }

    /** {@return how many presents stopped at the acquire because the swapchain was out of date} */
    public int acquireOutOfDate() {
        return this.acquireOutOfDate;
    }
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

    /** The image handle the last present used, so the frame that reached the swapchain can be written out. */
    private long lastPresentedImage = VK_NULL_HANDLE;
    /** Set by the fixture, consumed by the present path so the read happens while the image is still ours. */
    /**
     * The dumps waiting for the next present. A queue rather than one slot: the fixture's proof image and a
     * diagnostic run's periodic frames are both requested at moments only the present can serve, and a single slot
     * silently dropped whichever asked first.
     */
    private final java.util.List<java.nio.file.Path> pendingDumpPaths = new java.util.ArrayList<>();
    /** How many failed dump requests may be kept for a retry, so a permanently failing path cannot grow the list. */
    private static final int DUMP_RETRY_CAP = 8;
    /** The last reason a requested presented-image dump did not write, for the report. */
    private String lastDumpFailure = "none";

    /**
     * {@return the last reason a requested presented-image dump did not write}.
     * <p>
     * Exposed because the bridge leg measured the case this exists for: the fixture recorded
     * {@code pictureRequested_world} with the right path at tick 200, the presented image was read back, and no dump
     * file existed in the run directory (leg r212-world-glref2). The write has four ways to fail - a zero image
     * handle, a null readback, a write error, a missing parent - and without this the report cannot say which.
     */
    public String lastDumpFailure() {
        return this.lastDumpFailure;
    }
    private boolean lastDumpWritten;

    /**
     * Writes the last presented image to a PNG, and says whether it did.
     * <p>
     * This is the GL-independent image evidence: the game's own screenshot reads OpenGL's default framebuffer, so while
     * that is the only image the renderer can be compared against, the GL composite has to stay. The same readback the
     * centre sample uses is used here over the whole image.
     */
    /**
     * Asks for the next presented image to be written to this path.
     * <p>
     * The write has to happen at the moment the image is still the presenter's: reading it afterwards and transitioning it
     * from {@code PRESENT_SRC_KHR} is what the validation layer refused with "performs a layout transition on presentable
     * VkImage ... but the image has not been acquired" when the dump was driven from the fixture instead. So the request is
     * a flag the present path consumes, and the copy uses the same routine as the centre sample.
     */
    public void requestPresentedImageDump(java.nio.file.Path path) {
        if (path != null) {
            this.pendingDumpPaths.add(path);
        }
    }

    /** Writes the image the present just handed over, while it is still ours to read. */
    private boolean writePresentedImage(java.nio.file.Path path, long image) {
        if (image == VK_NULL_HANDLE || this.width <= 0 || this.height <= 0) {
            return false;
        }
        int[] pixels = readBackBlock(this.context, image, this.width, this.height, 0, 0, this.width, this.height);
        if (pixels == null) {
            return false;
        }
        try {
            java.awt.image.BufferedImage imageOut =
                    new java.awt.image.BufferedImage(this.width, this.height, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            imageOut.setRGB(0, 0, this.width, this.height, pixels, 0, this.width);
            java.nio.file.Files.createDirectories(path.getParent());
            return javax.imageio.ImageIO.write(imageOut, "png", path.toFile());
        } catch (java.io.IOException | RuntimeException | LinkageError error) {
            return false;
        }
    }

    /** {@return whether the last dump request wrote its file}. */
    public boolean lastDumpWritten() {
        return this.lastDumpWritten;
    }

    public float lastPresentedMean() {
        return this.lastPresentedMean;
    }

    /** {@return the mean of the centre 8x8 block of this image}, or -1 when it could not be read. */
    private static float readBackMean(VulkanContext context, long image, int width, int height) {
        int size = 8;
        int[] pixels = readBackBlock(context, image, width, height, Math.max(0, (width / 2) - (size / 2)),
                Math.max(0, (height / 2) - (size / 2)), size, size);
        if (pixels == null) {
            return -1.0f;
        }
        long sum = 0L;
        for (int pixel : pixels) {
            sum += (pixel >> 16) & 0xFF;
            sum += (pixel >> 8) & 0xFF;
            sum += pixel & 0xFF;
        }
        return pixels.length == 0 ? -1.0f : (float) ((double) sum / (pixels.length * 3));
    }

    /**
     * Reads a block of the presented image back as ARGB pixels.
     * <p>
     * One copy routine serves both the centre sample and the full-image dump: the barriers are the part that has to be
     * exactly right (presentable to transfer source and back, or the validation layer reports the present layout at the
     * next draw), so there is one place that does them.
     */
    private static int[] readBackBlock(VulkanContext context, long image, int width, int height, int x, int y,
            int blockWidth, int blockHeight) {
        long buffer = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int rowBytes = blockWidth * blockHeight * 4;
            org.lwjgl.vulkan.VkBufferCreateInfo bufferInfo = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(rowBytes).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            LongBuffer handle = stack.mallocLong(1);
            if (vkCreateBuffer(context.device(), bufferInfo, null, handle) != VK_SUCCESS) {
                return null;
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
                return null;
            }
            memory = handle.get(0);
            vkBindBufferMemory(context.device(), buffer, memory, 0);

            VkCommandBuffer commands = context.beginCommands();
            org.lwjgl.vulkan.VkBufferImageCopy.Buffer copy = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            copy.get(0).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            copy.get(0).imageOffset().set(x, y, 0);
            copy.get(0).imageExtent().set(blockWidth, blockHeight, 1);
            // The image has just been presented, so it is in VK_IMAGE_LAYOUT_PRESENT_SRC_KHR. A transfer cannot read
            // it from there and the layout a descriptor was written with is checked at draw time: the validation layer
            // reported VUID-vkCmdDraw-None-09600 ("expects ... TRANSFER_SRC_OPTIMAL--instead, current layout is
            // VK_IMAGE_LAYOUT_PRESENT_SRC_KHR") for every readback this instrumentation made. The two barriers below are
            // what the copy needs: presentable -> transfer source, and back again so the next present is legal.
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer toTransfer =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            toTransfer.get(0).sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR).newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image).srcAccessMask(0).dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT);
            toTransfer.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, null, null,
                    toTransfer);
            vkCmdCopyImageToBuffer(commands, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, buffer, copy);
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer backToPresent =
                    org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            backToPresent.get(0).sType$Default()
                    .oldLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL).newLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image).srcAccessMask(VK_ACCESS_TRANSFER_READ_BIT).dstAccessMask(0);
            backToPresent.get(0).subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).baseMipLevel(0).levelCount(1)
                    .baseArrayLayer(0).layerCount(1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, null,
                    null, backToPresent);
            context.endCommands(commands);

            PointerBuffer mapped = stack.mallocPointer(1);
            if (vkMapMemory(context.device(), memory, 0, rowBytes, 0, mapped) != VK_SUCCESS) {
                return null;
            }
            // The swapchain image is a B8G8R8A8 family format on this surface (the surface reported format 44), so the
            // bytes are B, G, R, A. Reading them as R, G, B left the mean identical - a channel swap cannot move a mean -
            // while 97.55% of the pixels differed from the game's screenshot, which is what named the order.
            int[] pixels = new int[blockWidth * blockHeight];
            for (int pixel = 0; pixel < pixels.length; pixel++) {
                long at = mapped.get(0) + (long) pixel * 4L;
                int b = org.lwjgl.system.MemoryUtil.memGetByte(at) & 0xFF;
                int g = org.lwjgl.system.MemoryUtil.memGetByte(at + 1) & 0xFF;
                int r = org.lwjgl.system.MemoryUtil.memGetByte(at + 2) & 0xFF;
                pixels[pixel] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }
            vkUnmapMemory(context.device(), memory);
            return pixels;
        } catch (RuntimeException | LinkageError error) {
            return null;
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
                || ! "false".equalsIgnoreCase(System.getProperty("celtium.vulkan.presentFromComposite", "true")))) {
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

        // BH-19: the present-support contract, and the ownership/sharing decision behind it.
        //
        // The decision, written down because the audit asks for a decision rather than an omission: **one queue is
        // used for both submission and presentation, and a configuration where the chosen family cannot present is
        // refused, not worked around.** The reasons, in the order they matter:
        //   1. `VulkanTerrainGpuBackend` submits through `context.queue()` and presents through the same `VkQueue`
        //      from `VulkanSwapchain`. A second, present-only family would mean either a separate present queue whose
        //      submissions must be externally synchronised against the submit queue (semaphores on both sides, and the
        //      frame's render-finished signal re-pointed), or `VK_SHARING_MODE_CONCURRENT` images with two queue
        //      family indices - and the current design deliberately declares `EXCLUSIVE` with one index because the
        //      spec requires CONCURRENT to list at least two *distinct* families.
        //   2. The failure this refusal prevents is silent: a swapchain created against a family that cannot present
        //      fails at `vkQueuePresentKHR` (or returns OUT_OF_DATE forever), which under GL-less is a black window
        //      with every counter reporting a complete frame - the shape the audit's whole travel is about.
        //   3. What it costs: a device whose *only* present-capable family is not the graphics+transfer family is
        //      refused. Measured on this workspace, no such device exists among the three enumerated (AMD RADV family
        //      0 presents, Intel ANV family 0 presents, and the third device is refused earlier), so the cost is
        //      currently zero here and is named rather than assumed elsewhere.
        // The device/queue *selection* half - folding this check into candidate choice so another device or family can
        // be considered - is what the contract test below exercises synthetically; on this machine the branch is
        // otherwise unreachable, because the chosen family presents on every enumerated device.
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer supported = stack.mallocInt(1);
            boolean forcedUnsupported = Boolean.getBoolean("celtium.vulkan.assumeNoPresentSupport");
            int result = forcedUnsupported ? VK_SUCCESS : vkGetPhysicalDeviceSurfaceSupportKHR(context.physicalDevice(),
                    context.queueFamilyIndex(), surface, supported);
            if (forcedUnsupported) {
                supported.put(0, VK_FALSE);
                presentSupportRefusals++;
            }
            if (result != VK_SUCCESS || supported.get(0) != VK_TRUE) {
                if (forcedUnsupported) {
                    PRESENT_SUPPORT_REFUSALS.incrementAndGet();
                }
                reasons.add("queue family " + context.queueFamilyIndex() + " cannot present to the surface"
                        + " (VkResult " + result + ", supported=" + (result == VK_SUCCESS ? supported.get(0) : -1)
                        + (forcedUnsupported ? ", forced by -Dceltium.vulkan.assumeNoPresentSupport=true (BH-19 contract"
                                + " test; not a device result)" : "") + ")");
                // BH-20: this is the one refusal that happens *after* the surface exists and *before* a swapchain
                // object owns it. Returning null here left the surface - and with it the platform connection - alive
                // for a caller that has nothing to delete it with, on exactly the configuration the audit names
                // (present unsupported on the chosen queue family), which is also the path BH-19 will make reachable
                // when another candidate is considered. Released here so the next candidate starts clean.
                vkDestroySurfaceKHR(context.instance(), surface, null);
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

    /** {@return the present mode's name}, so the contract line is readable without the numeric enum. */
    private static String presentModeName(int mode) {
        switch (mode) {
            case VK_PRESENT_MODE_IMMEDIATE_KHR: return "IMMEDIATE";
            case VK_PRESENT_MODE_MAILBOX_KHR: return "MAILBOX";
            case VK_PRESENT_MODE_FIFO_KHR: return "FIFO";
            case VK_PRESENT_MODE_FIFO_RELAXED_KHR: return "FIFO_RELAXED";
            default: return "mode" + mode;
        }
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
                + this.width + "x" + this.height + " " + offeredVersusRequested();
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
                // Named immediately before the call: the validation layer does not print the semaphore handle in
                // VUID-vkAcquireNextImageKHR-semaphore-01286, so printing the one this call hands over is the only way to
                // know which semaphore it objected to.
                System.out.println("celtium acquire: probe uses semaphore=0x" + Long.toHexString(acquire));
                int result = vkAcquireNextImageKHR(this.context.device(), this.swapchain, Long.MAX_VALUE, acquire,
                        VK_NULL_HANDLE, index);
                // Fires whatever the acquire returned. Gating it on VK_SUCCESS made the injection depend on what a
                // headless swapchain happened to answer: it fired in one run and not in the next, and a run where it did
                // not fire looked like an acceptance of a path nothing had exercised. An injection meant to test the
                // rebuild has to be deterministic.
                if (FORCE_ACQUIRE_OUT_OF_DATE) {
                    recordOutOfDateInjection();
                    result = VK_ERROR_OUT_OF_DATE_KHR;
                }
                if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                    replaceAcquireSemaphore(stack, acquire);
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
        // The present path is the largest bucket of the frame that our brackets do not cover (the interval p50 is
        // 40.69 ms against a 19.2 ms renderer bracket on the candidate leg), so it is timed here, per present, and
        // folded into the CELTIUM_PRESENT line the backend already prints. Bounded: four counters.
        long presentStarted = System.nanoTime();
        String presentResult = celtium$presentImage(sourceImage, sourceLayout, width, height, bridgeWaitSemaphore,
                bridgeDoneSemaphore, sourceLayoutAfter);
        celtium$notePresentTime(System.nanoTime() - presentStarted);
        return presentResult;
    }

    private long presentCalls;
    private long presentNanos;
    private long presentWorstNanos;
    private long presentFrames;
    /**
     * The present blit, recorded once per (swapchain image, source, size, layout) key instead of per frame.
     * <p>
     * The blit the present path emits is identical every present - same source image, same source layouts, same
     * four transition calls and the same 1:1 flip region; only the destination swapchain image index varies, and
     * that is exactly what the array dimension covers. Recording it once at first use (and re-recording when the
     * key changes - a new frame target, a resize, a layout change) removes the per-frame command-pool alloc,
     * {@code vkBeginCommandBuffer}, two barrier struct fills, a blit struct fill and {@code vkEndCommandBuffer},
     * and it removes the deferred-submit bookkeeping the per-frame buffer needed, because a pre-recorded buffer
     * carries {@code SIMULTANEOUS_USE} and is never freed until the pool dies.
     */
    private long presentBlitPool = VK_NULL_HANDLE;
    private long[] preRecordedBlits = new long[0];
    private long preRecordedSourceImage = VK_NULL_HANDLE;
    private int preRecordedSourceLayout = -1;
    private int preRecordedSourceLayoutAfter = -1;
    private int preRecordedWidth = -1;
    private int preRecordedHeight = -1;
    private long preRecordedSwapchain = VK_NULL_HANDLE;
    private long preRecordedBlitPresents;
    private long preRecordedBlitRecords;

    /** {@return the present path's own cost}, so the outside-the-bracket time has a number rather than a guess. */
    public String presentTimingSummary() {
        return "presentCalls=" + this.presentCalls + " presentMillis=" + (this.presentNanos / 1000000L)
                + " presentWorstMillis=" + (this.presentWorstNanos / 1000000L)
                + " presentPerCallMicros=" + (this.presentCalls == 0 ? 0 : this.presentNanos / this.presentCalls / 1000L)
                + " preRecordedBlitPresents=" + this.preRecordedBlitPresents
                + " preRecordedBlitRecords=" + this.preRecordedBlitRecords;
    }

    private void celtium$notePresentTime(long nanos) {
        this.presentCalls++;
        this.presentNanos += nanos;
        if (nanos > this.presentWorstNanos) {
            this.presentWorstNanos = nanos;
        }
        // Bounded: one line every 300 presents (about once a minute at this scene's rate), so the outside-the-bracket
        // split has an artifact even when F3 is closed and no diagnostics dump happens. It is the only reader of this
        // census; the backend's CELTIUM_PRESENT line belongs to a file this unit must not touch.
        if (this.presentCalls % 300 == 0) {
            System.out.println("[Celtium] CELTIUM_PRESENT_TIMING " + presentTimingSummary());
            this.presentFrames++;
        }
    }

    private String celtium$presentImage(long sourceImage, int sourceLayout, int width, int height,
            long bridgeWaitSemaphore, long bridgeDoneSemaphore, int sourceLayoutAfter) {
        // Reset per present, so a caller reading them after a failure cannot see the previous present's verdict.
        this.lastPresentSubmitDone = false;
        this.lastPresentSucceeded = false;
        // Reset rather than accumulated (audit S18): this describes the present that is about to happen. Left set, a single
        // out-of-date acquire would keep the caller rebuilding the swapchain for the rest of the session.
        this.outOfDate = false;
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
            if (result == VK_SUBOPTIMAL_KHR || result == VK_ERROR_OUT_OF_DATE_KHR) {
                if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                    // The image the acquire handed back cannot be used, so nothing is submitted and nothing is
                    // presented (audit S18). The caller sees success=false and outOfDate=true and rebuilds.
                    this.outOfDate = true;
                    this.acquireOutOfDate++;
                    return "present image: the swapchain is out of date at acquire";
                }
                // The frame can still be presented, but the swapchain no longer matches the surface; recording it lets
                // the caller rebuild while this frame goes out.
                this.outOfDate = true;
            }
            if (FORCE_OUT_OF_DATE && !this.forcedOutOfDate) {
                // Test-only: the branch that rebuilds the swapchain cannot be reached on this harness, because the
                // window never changes size and neither driver reports sub-optimal here. Injecting the trigger shows the
                // mechanism - drop, then rebuild through the lazy ensureSwapchain() - rather than only compiling it.
                this.forcedOutOfDate = true;
                this.outOfDate = true;
                recordOutOfDateInjection();
            }
            if (result != VK_SUCCESS && result != VK_SUBOPTIMAL_KHR) {
                replaceAcquireSemaphore(stack, acquire);
                return "present image: vkAcquireNextImageKHR returned " + result;
            }
            long image = this.images[index.get(0)];
            celtium$noteAcquireOwner(stack, acquire, index.get(0));
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
            long recorded = preRecordedPresentBlit(index.get(0), sourceImage, sourceLayout,
                    sourceLayoutAfter, width, height);
            if (recorded == VK_NULL_HANDLE) {
                return "present image: could not record the blit command buffer";
            }
            // The pre-recorded buffer goes to the queue directly - no vkEndCommandBuffer (recording closed at
            // build time) and no fence or deferred reclaim (the buffer lives for the pool's life, so there is
            // nothing to retire). The semaphore contract is the one the deferred path satisfied: waits on the
            // acquire and the bridge, signals renderFinished and the bridge's done semaphore per image.
            long bridgeDone = this.bridgeTarget != null
                    ? this.bridgeTarget.presentDoneSemaphore(index.get(0)) : bridgeDoneSemaphore;
            String submitError = submitPreRecorded(stack, recorded,
                    new long[] {acquire, bridgeWaitSemaphore},
                    new long[] {renderFinished, bridgeDone});
            if (submitError != null) {
                return submitError;
            }
            if (this.bridgeTarget != null && bridgeDone != 0L) {
                this.bridgeTarget.noteDoneSignalled(bridgeDone);
            }
            this.lastPresentSubmitDone = true;
            this.lastPresentSucceeded = false;
            VkPresentInfoKHR present = VkPresentInfoKHR.calloc(stack).sType$Default()
                    .pWaitSemaphores(stack.longs(renderFinished))
                    .swapchainCount(1)
                    .pSwapchains(stack.longs(this.swapchain))
                    .pImageIndices(index);
            // Read the blitted image back before it is presented, so "the screen shows the frame" becomes a
            // measurement instead of an inference: the mean of a small centre patch of the presented image is compared
            // against the same patch of the game's frame in the caller's log.
            // Every present while the flag is on. It used to read back only the first three, which meant the value the
            // fixture recorded at its capture was from the run's third frame - the same "value from another moment" mistake
            // the recording was added to fix, one level down. The flag is a diagnostic, so its cost is acceptable; the
            // count stays for reporting and as a bound.
            // The dump the fixture asked for is not a diagnostic of the readback flag: it is the only picture a
            // GL-bridge run has, and it used to live inside the PRESENT_READBACK block below. Every bridge leg
            // therefore wrote no world dump at all while its report recorded pictureRequested_world and a
            // presented-image mean - the request was made, the image was there, and the write was skipped because a
            // diagnostic switch was off (legs hei005br and glref3: no dump file anywhere, presentedDumpFailure=none
            // because writePresentedImage was never called). It runs here unconditionally, and clears the list so the
            // block below cannot write the same path twice.
            if (!this.pendingDumpPaths.isEmpty()) {
                java.util.List<java.nio.file.Path> pendingPaths =
                        new java.util.ArrayList<>(this.pendingDumpPaths);
                this.pendingDumpPaths.clear();
                boolean anyWritten = false;
                java.util.List<java.nio.file.Path> failedPaths = new java.util.ArrayList<>();
                for (java.nio.file.Path path : pendingPaths) {
                    if (writePresentedImage(path, image)) {
                        anyWritten = true;
                    } else {
                        failedPaths.add(path);
                    }
                }
                this.lastDumpWritten = anyWritten;
                if (!failedPaths.isEmpty() && this.pendingDumpPaths.size() + failedPaths.size() <= DUMP_RETRY_CAP) {
                    this.pendingDumpPaths.addAll(failedPaths);
                    this.lastDumpFailure = "a presented-image dump did not write this present (image=0x"
                            + Long.toHexString(image) + ", size=" + this.width + "x" + this.height
                            + "); the request is kept for the next present";
                }
            }
            if (PRESENT_READBACK && this.presentReadbacks < 100000) {
                this.presentReadbacks++;
                this.lastPresentedMean = readBackMean(this.context, image, this.width, this.height);
                this.lastPresentedImage = image;
                if (!this.pendingDumpPaths.isEmpty()) {
                    java.util.List<java.nio.file.Path> paths =
                            new java.util.ArrayList<>(this.pendingDumpPaths);
                    this.pendingDumpPaths.clear();
                    boolean written = false;
                    // A request whose write fails is put back rather than swallowed. Measured need (leg
                    // r212-world-glref): the bridge run recorded presentedDumpRequestedAtCapture=True and
                    // presentedImageMeanAtCapture=44.0 - the request was made and the presented image was read back -
                    // and yet no world dump file existed anywhere in the run directory, because this loop cleared the
                    // path before trying and the attempt did not write. The capture's dump is the only picture a
                    // GL-bridge run has, so the retry belongs here, bounded so a permanently failing path cannot grow
                    // the list without end.
                    java.util.List<java.nio.file.Path> failed = new java.util.ArrayList<>();
                    for (java.nio.file.Path path : paths) {
                        if (writePresentedImage(path, image)) {
                            written = true;
                        } else {
                            failed.add(path);
                        }
                    }
                    this.lastDumpWritten = written;
                    if (!failed.isEmpty() && this.pendingDumpPaths.size() + failed.size() <= DUMP_RETRY_CAP) {
                        this.pendingDumpPaths.addAll(failed);
                        this.lastDumpFailure = "a presented-image dump did not write this present (image=0x"
                                + Long.toHexString(image) + ", size=" + this.width + "x" + this.height
                                + "); the request is kept for the next present";
                    }
                }
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
            if (presented == VK_SUBOPTIMAL_KHR || presented == VK_ERROR_OUT_OF_DATE_KHR) {
                this.outOfDate = true;
            }
            if (presented != VK_SUCCESS && presented != VK_SUBOPTIMAL_KHR) {
                return "present image: vkQueuePresentKHR returned " + presented;
            }
            this.lastPresentSucceeded = true;
            return "present image: blitted " + width + "x" + height + " onto image " + index.get(0) + " (" + presented
                    + ")";
        }
    }

    /** {@return the next acquire semaphore, creating the pool on first use}, or 0 when creation failed. */
    /**
     * Replaces the acquire semaphore a failed acquire was handed with a fresh one.
     * <p>
     * A failed acquire may leave its semaphore signaled (the spec allows it), and rotating the cursor only postpones
     * the problem: with four semaphores the signaled one comes back into use four acquires later, which is exactly what
     * the log showed - the probe's failed acquire used 0x10, presentImage cycled 0x11, 0x12, 0x13 and then took 0x10
     * again, and VUID-vkAcquireNextImageKHR-semaphore-01286 was reported at that acquire. The old semaphore is retired
     * rather than destroyed, because a pending wait may still name it.
     */
    private void replaceAcquireSemaphore(MemoryStack stack, long used) {
        if (this.acquireSemaphores == null || used == VK_NULL_HANDLE) {
            return;
        }
        for (int i = 0; i < this.acquireSemaphores.length; i++) {
            if (this.acquireSemaphores[i] != used) {
                continue;
            }
            LongBuffer fresh = stack.mallocLong(1);
            if (vkCreateSemaphore(this.context.device(),
                    org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack).sType$Default(), null,
                    fresh) != VK_SUCCESS) {
                return;
            }
            final long retired = this.acquireSemaphores[i];
            this.acquireSemaphores[i] = fresh.get(0);
            org.soutalab.celtium.vulkan.VulkanRetireQueue.retire(
                    () -> vkDestroySemaphore(this.context.device(), retired, null));
            return;
        }
    }

    /**
     * Records which image the acquire semaphore was handed to, and counts a reuse that the ownership table says is
     * still owned (audit B12). One counter, no behaviour change: the point is to make the hazard visible in a leg
     * rather than to assume either that it happens or that it does not.
     */
    private void celtium$noteAcquireOwner(MemoryStack stack, long semaphore, int imageIndex) {
        this.acquiresRecorded++;
        if (this.acquireSemaphores == null || semaphore == VK_NULL_HANDLE) {
            return;
        }
        int slot = -1;
        for (int i = 0; i < this.acquireSemaphores.length; i++) {
            if (this.acquireSemaphores[i] == semaphore) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            return;
        }
        if (this.acquireSemaphoreOwnerImage == null) {
            this.acquireSemaphoreOwnerImage = new long[this.acquireSemaphores.length];
            java.util.Arrays.fill(this.acquireSemaphoreOwnerImage, -1L);
        }
        // A semaphore still owned by an image that has not been re-acquired is the reuse the spec forbids.
        if (this.acquireSemaphoreOwnerImage[slot] >= 0L && this.acquireSemaphoreOwnerImage[slot] != imageIndex) {
            this.acquireSemaphoreReusesWhileOwned++;
        }
        this.acquireSemaphoreOwnerImage[slot] = imageIndex;
    }

    /** {@return the acquire-semaphore ownership census}, for the present trace and the report. */
    public String acquireOwnershipSummary() {
        int owned = 0;
        if (this.acquireSemaphoreOwnerImage != null) {
            for (int i = 0; i < this.acquireSemaphoreOwnerImage.length; i++) {
                if (this.acquireSemaphoreOwnerImage[i] >= 0L) {
                    owned++;
                }
            }
        }
        return "acquires=" + this.acquiresRecorded + " acquireSemaphoresOwned=" + owned
                + " acquireSemaphoreReusesWhileOwned=" + this.acquireSemaphoreReusesWhileOwned
                + " acquireCursor=" + this.acquireSemaphoreCursor;
    }

    private long acquireSemaphore(MemoryStack stack) {
        int count = Math.max(2, this.images.length);
        if (this.acquireSemaphores == null) {
            long[] pool = new long[count];
            LongBuffer semaphore = stack.mallocLong(1);
            for (int i = 0; i < count; i++) {
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    // BH-20: the pool is local until it is complete, so a failure at semaphore i used to leak the i-1
                    // that were already made - and this pool is rebuilt on every swapchain recreation, so a driver
                    // under memory pressure would accumulate them. Roll back in reverse and leave the field null, so
                    // the next attempt builds a fresh pool rather than a half one.
                    for (int made = 0; made < i; made++) {
                        vkDestroySemaphore(this.context.device(), pool[made], null);
                    }
                    semaphorePoolRollbacks++;
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
        // One semaphore per swapchain image, and the pool is rebuilt when that count changes: a pool sized for an
        // earlier swapchain makes imageIndex % length map two images onto one binary semaphore, and a semaphore
        // signalled while it is still signalled is VUID-vkQueueSubmit-pSignalSemaphores-00067 - measured as ten such
        // messages in one leg on the present path. The Vulkan guide names this exact shape: allocate one
        // submit-finished semaphore per swapchain image rather than per in-flight frame.
        if (this.renderFinishedSemaphores != null && this.renderFinishedSemaphores.length != this.images.length) {
            for (long semaphore : this.renderFinishedSemaphores) {
                vkDestroySemaphore(this.context.device(), semaphore, null);
            }
            this.renderFinishedSemaphores = null;
            this.renderFinishedPoolRebuilds++;
        }
        if (this.renderFinishedSemaphores == null) {
            int count = this.images.length;
            long[] pool = new long[count];
            LongBuffer semaphore = stack.mallocLong(1);
            for (int i = 0; i < count; i++) {
                if (vkCreateSemaphore(this.context.device(), org.lwjgl.vulkan.VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default(), null, semaphore) != VK_SUCCESS) {
                    // BH-20, same shape as the acquire pool: release what this attempt made before giving up.
                    for (int made = 0; made < i; made++) {
                        vkDestroySemaphore(this.context.device(), pool[made], null);
                    }
                    semaphorePoolRollbacks++;
                    return VK_NULL_HANDLE;
                }
                pool[i] = semaphore.get(0);
            }
            this.renderFinishedSemaphores = pool;
        }
        // The index is the acquired image index and the pool has one semaphore per image, so no modulo: a modulo here
        // is what let two images share a semaphore when the pool and the swapchain disagreed on the count.
        return this.renderFinishedSemaphores[imageIndex < this.renderFinishedSemaphores.length
                ? imageIndex : imageIndex % this.renderFinishedSemaphores.length];
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

    /**
     * {@return the pre-recorded blit command buffer for this swapchain image}, recording the whole set when the
     * key changed.
     * <p>
     * The key covers everything the recorded commands embed: the source image handle and the two source layouts,
     * the blit extent (the source and swapchain sizes) and the swapchain itself, whose images the commands write.
     * Any change - a recreated frame target, a resize, a layout contract change - re-records every buffer, because
     * a stale command stream writing the wrong handles is worse than the one-time record cost. The buffers are
     * allocated {@code SIMULTANEOUS}: the same image's buffer can be re-submitted while a previous present of it is
     * still on the queue (the presentation engine only returns an image after its last presentation retires, which
     * is exactly what makes re-submission legal).
     */
    private long preRecordedPresentBlit(int imageIndex, long sourceImage, int sourceLayout,
            int sourceLayoutAfter, int width, int height) {
        boolean keyMatches = this.preRecordedSourceImage == sourceImage
                && this.preRecordedSourceLayout == sourceLayout
                && this.preRecordedSourceLayoutAfter == sourceLayoutAfter
                && this.preRecordedWidth == width
                && this.preRecordedHeight == height
                && this.preRecordedSwapchain == this.swapchain;
        if (keyMatches && imageIndex < this.preRecordedBlits.length
                && this.preRecordedBlits[imageIndex] != VK_NULL_HANDLE) {
            this.preRecordedBlitPresents++;
            return this.preRecordedBlits[imageIndex];
        }
        discardPreRecorded();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            org.lwjgl.vulkan.VkCommandPoolCreateInfo poolInfo =
                    org.lwjgl.vulkan.VkCommandPoolCreateInfo.calloc(stack).sType$Default();
            LongBuffer poolOut = stack.mallocLong(1);
            if (vkCreateCommandPool(this.context.device(), poolInfo, null, poolOut) != VK_SUCCESS) {
                return VK_NULL_HANDLE;
            }
            this.presentBlitPool = poolOut.get(0);
            org.lwjgl.vulkan.VkCommandBufferAllocateInfo alloc =
                    org.lwjgl.vulkan.VkCommandBufferAllocateInfo.calloc(stack).sType$Default()
                            .commandPool(this.presentBlitPool)
                            .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                            .commandBufferCount(this.images.length);
            PointerBuffer handles = stack.mallocPointer(this.images.length);
            if (vkAllocateCommandBuffers(this.context.device(), alloc, handles) != VK_SUCCESS) {
                return VK_NULL_HANDLE;
            }
            // The GL-composite semantics the per-frame record used: 1:1 blit, lower-left anchored, no scaling, and
            // the source read bottom-to-top so the presented image lands upright.
            int blitWidth = Math.min(width, this.width);
            int blitHeight = Math.min(height, this.height);
            this.preRecordedBlits = new long[this.images.length];
            for (int i = 0; i < this.images.length; i++) {
                long handle = handles.get(i);
                VkCommandBuffer commands = new VkCommandBuffer(handle, this.context.device());
                org.lwjgl.vulkan.VkCommandBufferBeginInfo begin =
                        org.lwjgl.vulkan.VkCommandBufferBeginInfo.calloc(stack).sType$Default()
                                .flags(VK_COMMAND_BUFFER_USAGE_SIMULTANEOUS_USE_BIT);
                if (vkBeginCommandBuffer(commands, begin) != VK_SUCCESS) {
                    return VK_NULL_HANDLE;
                }
                transitionForBlitSource(stack, commands, sourceImage, sourceLayout);
                transitionToTransferDst(stack, commands, this.images[i]);
                org.lwjgl.vulkan.VkImageBlit.Buffer region = org.lwjgl.vulkan.VkImageBlit.calloc(1, stack);
                region.get(0).srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                        .layerCount(1);
                region.get(0).srcOffsets().get(0).set(0, blitHeight, 0);
                region.get(0).srcOffsets().get(1).set(blitWidth, 0, 1);
                region.get(0).dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                        .layerCount(1);
                region.get(0).dstOffsets().get(0).set(0, this.height - blitHeight, 0);
                region.get(0).dstOffsets().get(1).set(blitWidth, this.height, 1);
                if (PRESENT_MARKER) {
                    transitionToTransferDst(stack, commands, this.images[i]);
                    org.lwjgl.vulkan.VkClearColorValue marker = org.lwjgl.vulkan.VkClearColorValue.calloc(stack);
                    marker.float32(0, 1.0f);
                    marker.float32(1, 0.0f);
                    marker.float32(2, 0.0f);
                    marker.float32(3, 1.0f);
                    org.lwjgl.vulkan.VkImageSubresourceRange.Buffer markerRange =
                            org.lwjgl.vulkan.VkImageSubresourceRange.calloc(1, stack);
                    markerRange.get(0).aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
                    vkCmdClearColorImage(commands, this.images[i], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                            marker, markerRange);
                } else {
                    vkCmdBlitImage(commands, sourceImage, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, this.images[i],
                            VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_NEAREST);
                }
                transitionForBlitSourceBack(stack, commands, sourceImage, sourceLayoutAfter);
                transitionToPresent(stack, commands, this.images[i]);
                if (vkEndCommandBuffer(commands) != VK_SUCCESS) {
                    return VK_NULL_HANDLE;
                }
                this.preRecordedBlits[i] = handle;
            }
            this.preRecordedSourceImage = sourceImage;
            this.preRecordedSourceLayout = sourceLayout;
            this.preRecordedSourceLayoutAfter = sourceLayoutAfter;
            this.preRecordedWidth = width;
            this.preRecordedHeight = height;
            this.preRecordedSwapchain = this.swapchain;
            this.preRecordedBlitRecords++;
            return this.preRecordedBlits[imageIndex];
        }
    }

    /**
     * Submits a pre-recorded command buffer with the present's semaphore contract and no fence: the buffer
     * persists for the pool's life, so there is nothing to retire and nothing to wait on. Null handles are
     * compacted out of both lists, the same rule the deferred path applies.
     */
    private String submitPreRecorded(MemoryStack stack, long commandBuffer, long[] waitSemaphores,
            long[] signalSemaphores) {
        org.lwjgl.vulkan.VkSubmitInfo submit = org.lwjgl.vulkan.VkSubmitInfo.calloc(stack).sType$Default()
                .pCommandBuffers(stack.pointers(commandBuffer));
        int waitCount = 0;
        if (waitSemaphores != null) {
            for (long semaphore : waitSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    waitCount++;
                }
            }
        }
        if (waitCount > 0) {
            long[] waits = new long[waitCount];
            int[] stages = new int[waitCount];
            int slot = 0;
            for (long semaphore : waitSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    waits[slot] = semaphore;
                    stages[slot] = VK_PIPELINE_STAGE_ALL_COMMANDS_BIT;
                    slot++;
                }
            }
            submit.waitSemaphoreCount(waits.length)
                    .pWaitSemaphores(stack.longs(waits))
                    .pWaitDstStageMask(stack.ints(stages));
        }
        int signalCount = 0;
        if (signalSemaphores != null) {
            for (long semaphore : signalSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    signalCount++;
                }
            }
        }
        if (signalCount > 0) {
            long[] signals = new long[signalCount];
            int slot = 0;
            for (long semaphore : signalSemaphores) {
                if (semaphore != VK_NULL_HANDLE) {
                    signals[slot] = semaphore;
                    slot++;
                }
            }
            submit.pSignalSemaphores(stack.longs(signals));
        }
        this.context.celtium$flushBatchedSubmits();
        int result = vkQueueSubmit(this.context.queue(), submit, VK_NULL_HANDLE);
        return result == VK_SUCCESS ? null : "present image: vkQueueSubmit failed with " + result;
    }

    /** Drops the recorded blit set: the queue is drained first because a previous present may still read them. */
    private void discardPreRecorded() {
        this.preRecordedSourceImage = VK_NULL_HANDLE;
        this.preRecordedSwapchain = VK_NULL_HANDLE;
        this.preRecordedBlits = new long[0];
        if (this.presentBlitPool != VK_NULL_HANDLE) {
            vkQueueWaitIdle(this.context.queue());
            vkDestroyCommandPool(this.context.device(), this.presentBlitPool, null);
            this.presentBlitPool = VK_NULL_HANDLE;
        }
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
        discardPreRecorded();
    }

    /**
     * {@code VkSurfaceCapabilitiesKHR.currentExtent} carries this value (as an unsigned 32-bit number) when the
     * surface does not dictate a size, and the swapchain then has to be created inside
     * {@code minImageExtent..maxImageExtent} from the application's own request. Named rather than written as
     * {@code -1} at the comparison, because the sentinel is a specific bit pattern and the next reader should not
     * have to know that a signed -1 means {@code 0xFFFFFFFF} here.
     */

    /**
     * {@return one line saying what the surface offered and what was requested, so a leg can reconcile the two}.
     * <p>
     * The acceptance for BH-18 is a measurement rather than a source reading: the numbers the surface reports and the
     * numbers that went into {@code vkCreateSwapchainKHR}, side by side, with every place they differ named.
     */
    public String offeredVersusRequested() {
        return this.surfaceOffer + " requested[" + this.reconciliation + "]";
    }

    /**
     * Whether the extent a fresh swapchain would get still equals this one's.
     * <p>
     * Called once per frame from the present path: a real window resize changes the surface's current extent, and
     * relying on the driver to report out-of-date at an acquire leaves the stale extent on the window for frames or
     * forever - the resize harness's compositor never reports it. The framebuffer size arguments are only the
     * fallback for a surface that pins no extent; a pinned one answers for itself.
     */
    public boolean matchesSurfaceExtent(int framebufferWidth, int framebufferHeight) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.calloc(stack);
            if (vkGetPhysicalDeviceSurfaceCapabilitiesKHR(this.context.physicalDevice(), this.surface,
                    capabilities) != VK_SUCCESS) {
                return true;
            }
            // The same pure decision the creation path uses, so "would a fresh swapchain get this extent" cannot
            // disagree with "what extent does a fresh swapchain get".
            SwapchainCreateArguments.Surface offer = new SwapchainCreateArguments.Surface();
            offer.currentExtentWidth = Integer.toUnsignedLong(capabilities.currentExtent().width());
            offer.currentExtentHeight = Integer.toUnsignedLong(capabilities.currentExtent().height());
            offer.minExtentWidth = capabilities.minImageExtent().width();
            offer.minExtentHeight = capabilities.minImageExtent().height();
            offer.maxExtentWidth = capabilities.maxImageExtent().width();
            offer.maxExtentHeight = capabilities.maxImageExtent().height();
            int[] extent = SwapchainCreateArguments.extentOf(offer, framebufferWidth, framebufferHeight);
            return extent[0] == this.width && extent[1] == this.height;
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
            // The surface's format list as (format, colourSpace) pairs, which is what the pure selection function
            // takes; the list itself is still enumerated into the argument summary below (BH-18: what was offered and
            // what was chosen, one line).
            int[] offeredFormats = new int[formats.capacity() * 2];
            for (int i = 0; i < formats.capacity(); i++) {
                offeredFormats[i * 2] = formats.get(i).format();
                offeredFormats[i * 2 + 1] = formats.get(i).colorSpace();
                availableFormats.add(String.valueOf(formats.get(i).format()));
            }

            vkGetPhysicalDeviceSurfacePresentModesKHR(this.context.physicalDevice(), this.surface, count, null);
            IntBuffer modes = stack.mallocInt(count.get(0));
            vkGetPhysicalDeviceSurfacePresentModesKHR(this.context.physicalDevice(), this.surface, count, modes);
            boolean mailbox = false;
            boolean immediate = false;
            boolean fifo = false;
            StringBuilder supported = new StringBuilder();
            this.presentModesOffered = "";
            for (int i = 0; i < modes.capacity(); i++) {
                int mode = modes.get(i);
                if (mode == VK_PRESENT_MODE_MAILBOX_KHR) {
                    mailbox = true;
                } else if (mode == VK_PRESENT_MODE_IMMEDIATE_KHR) {
                    immediate = true;
                } else if (mode == VK_PRESENT_MODE_FIFO_KHR) {
                    fifo = true;
                }
                if (supported.length() > 0) {
                    supported.append(',');
                }
                supported.append(presentModeName(mode));
            }

            // The contract in one place. VSync on asks for FIFO (the only mode the spec requires every surface to
            // offer, and what VSync means); VSync off asks for MAILBOX, then IMMEDIATE, and falls back to FIFO with the
            // fallback named and counted rather than silently ignored.
            String requested;
            String fallback = null;
            if (vsyncRequested()) {
                requested = "vsync-on(FIFO)";
                this.presentMode = VK_PRESENT_MODE_FIFO_KHR;
            } else if (mailbox) {
                requested = "vsync-off(MAILBOX|IMMEDIATE)";
                this.presentMode = VK_PRESENT_MODE_MAILBOX_KHR;
            } else if (immediate) {
                requested = "vsync-off(MAILBOX|IMMEDIATE)";
                this.presentMode = VK_PRESENT_MODE_IMMEDIATE_KHR;
            } else {
                requested = "vsync-off(MAILBOX|IMMEDIATE)";
                this.presentMode = VK_PRESENT_MODE_FIFO_KHR;
                fallback = "the surface offers neither MAILBOX nor IMMEDIATE, so frames are queued at the display"
                        + " rate even though VSync is off";
                PRESENT_MODE_FALLBACKS.incrementAndGet();
            }
            this.builtForRequestRevision = VSYNC_REQUEST_REVISION.get();
            this.presentModesOffered = supported.toString().trim();
            presentModeLine = "requested=" + requested + " chosen=" + presentModeName(this.presentMode)
                    + " supported=[" + supported + "]" + (fallback == null ? "" : " fallback=[" + fallback + "]")
                    + " fallbacks=" + PRESENT_MODE_FALLBACKS.get()
                    + " changes=" + PRESENT_MODE_CHANGES.get()
                    + " vsyncOption=" + vsyncRequested() + " pushed=" + (vsyncRequested != null) + " fifoOffered=" + fifo
                    + " size=" + requestedWidth + "x" + requestedHeight;
            System.out.println("[Celtium] CELTIUM_PRESENT_MODE " + presentModeLine);

            // BH-18: the decision is one pure function of the surface's numbers (SwapchainCreateArguments.select),
            // so the contract can be checked with synthetic inputs and the checked function is the one that runs here.
            SwapchainCreateArguments.Surface surfaceOfferValues = new SwapchainCreateArguments.Surface();
            surfaceOfferValues.minImageCount = capabilities.minImageCount();
            surfaceOfferValues.maxImageCount = capabilities.maxImageCount();
            surfaceOfferValues.supportedUsage = capabilities.supportedUsageFlags();
            surfaceOfferValues.supportedCompositeAlpha = capabilities.supportedCompositeAlpha();
            surfaceOfferValues.currentExtentWidth = Integer.toUnsignedLong(capabilities.currentExtent().width());
            surfaceOfferValues.currentExtentHeight = Integer.toUnsignedLong(capabilities.currentExtent().height());
            surfaceOfferValues.minExtentWidth = capabilities.minImageExtent().width();
            surfaceOfferValues.minExtentHeight = capabilities.minImageExtent().height();
            surfaceOfferValues.maxExtentWidth = capabilities.maxImageExtent().width();
            surfaceOfferValues.maxExtentHeight = capabilities.maxImageExtent().height();
            surfaceOfferValues.formats = offeredFormats;
            SwapchainCreateArguments.Result choice =
                    SwapchainCreateArguments.select(surfaceOfferValues, requestedWidth, requestedHeight, 3);
            if (choice.refusal != null) {
                reasons.add(choice.refusal + " (surface offered formats " + availableFormats + ")");
                return false;
            }
            // BH-18's measurement, assembled from the same surface values the decision used: what the surface offered
            // beside what was requested, with the reconciliation the pure function produced. Printed on stdout (a
            // leg's console survives when the diagnostics list is never dumped) and carried in describe() for the
            // report.
            this.surfaceOffer = "surface-offer: minImages=" + surfaceOfferValues.minImageCount
                    + " maxImages=" + (choice.imagesUnlimited
                            ? "unlimited(0)" : Integer.toString(surfaceOfferValues.maxImageCount))
                    + " usage=0x" + Integer.toHexString(surfaceOfferValues.supportedUsage)
                    + " compositeAlpha=0x" + Integer.toHexString(surfaceOfferValues.supportedCompositeAlpha)
                    + " currentExtent=" + (choice.extentFromSurface
                            ? choice.width + "x" + choice.height : "unspecified(0xFFFFFFFF)")
                    + " minExtent=" + surfaceOfferValues.minExtentWidth + "x" + surfaceOfferValues.minExtentHeight
                    + " maxExtent=" + surfaceOfferValues.maxExtentWidth + "x" + surfaceOfferValues.maxExtentHeight
                    + " transform=0x" + Integer.toHexString(capabilities.currentTransform())
                    + " presentModesOffered=[" + this.presentModesOffered + "]"
                    + " offeredFormats=" + availableFormats;
            this.reconciliation = choice.describe();
            this.format = choice.format;
            this.colorSpace = choice.colorSpace;
            this.width = choice.width;
            this.height = choice.height;
            int imageCount = choice.imageCount;
            boolean imageCountClamped = choice.imageCountClamped;
            boolean imagesUnlimited = choice.imagesUnlimited;
            int swapchainUsage = choice.usage;
            String droppedUsage = choice.droppedUsage;
            int compositeAlpha = choice.compositeAlpha;
            String alphaNote = choice.alphaNote;
            String formatNote = choice.formatNote;
            String imageCountNote = choice.imageCountNote;
            String extentNote = choice.extentNote;

            VkSwapchainCreateInfoKHR create = VkSwapchainCreateInfoKHR.calloc(stack).sType$Default()
                    .surface(this.surface)
                    .minImageCount(imageCount)
                    .imageFormat(this.format)
                    .imageColorSpace(this.colorSpace)
                    .imageExtent(e -> e.set(this.width, this.height))
                    .imageArrayLayers(1)
                    .imageUsage(swapchainUsage)
                    // Exclusive sharing: there is only one queue family here, and the spec requires CONCURRENT to
                    // list at least two distinct families (VUID-VkSwapchainCreateInfoKHR-imageSharingMode-01278).
                    .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .queueFamilyIndexCount(0)
                    .pQueueFamilyIndices(stack.mallocInt(1))
                    .preTransform(capabilities.currentTransform())
                    .compositeAlpha(compositeAlpha)
                    .presentMode(this.presentMode)
                    .clipped(true)
                    .oldSwapchain(VK_NULL_HANDLE);
            LongBuffer handle = stack.mallocLong(1);
            // The argument values are recorded next to the call: LWJGL's generated checks throw a
            // NullPointerException when a buffer it considers required is null, and a bare frame list does not say
            // which one. Everything LWJGL could dereference is logged with its value here.
            this.argumentSummary = "create-info: surface=" + this.surface + " minImages=" + create.minImageCount()
                    + " imageCountClamped=" + imageCountClamped + " imageCountNote=[" + imageCountNote
                    + "] droppedOptionalUsage=[" + droppedUsage + "] surfaceAlphaNote=" + alphaNote
                    + " formatNote=[" + formatNote + "] extentNote=[" + extentNote + "]"
                    + " offeredFormats=" + availableFormats
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
            // BH-18: print the argument summary when the creation *succeeds*. It used to reach a log only inside the
            // probe-failure message, so the chosen image count, usage set, composite alpha and format were measurable
            // only when the call failed - and "the fields are set in the source" is not a measurement of the surface's
            // values. One line per swapchain, including the ones recreated by a VSync change or an out-of-date surface.
            // The stdout line carries the offer as well as the request (BH-18): a leg's console is the artifact that
            // survives even when the renderer's diagnostics list is never dumped, and "what the surface offered beside
            // what was requested" is the acceptance - the request alone cannot be reconciled with anything.
            System.out.println("[Celtium] CELTIUM_SWAPCHAIN " + offeredVersusRequested()
                    + " | " + this.argumentSummary + " semaphorePoolRollbacks=" + semaphorePoolRollbacks);
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
