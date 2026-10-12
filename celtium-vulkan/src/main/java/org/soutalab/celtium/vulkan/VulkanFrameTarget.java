package org.soutalab.celtium.vulkan;

import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.EXTMemoryObject;
import org.lwjgl.opengl.EXTMemoryObjectFD;
import org.lwjgl.opengl.EXTSemaphore;
import org.lwjgl.opengl.EXTSemaphoreFD;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.linux.UNISTD;
import org.lwjgl.vulkan.KHRExternalMemoryFd;
import org.lwjgl.vulkan.KHRExternalSemaphoreFd;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkExportMemoryAllocateInfo;
import org.lwjgl.vulkan.VkExportSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkExternalImageFormatProperties;
import org.lwjgl.vulkan.VkExternalMemoryImageCreateInfo;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageFormatProperties2;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkInstance;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryDedicatedAllocateInfo;
import org.lwjgl.vulkan.VkMemoryGetFdInfoKHR;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceExternalImageFormatInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceImageFormatInfo2;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkQueue;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreGetFdInfoKHR;
import org.lwjgl.vulkan.VkSubmitInfo;

import java.nio.ByteBuffer;
import java.nio.DoubleBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

import static org.lwjgl.opengl.GL11C.*;
import static org.lwjgl.opengl.GL20C.*;
import static org.lwjgl.opengl.GL30C.*;
import static org.lwjgl.opengl.GL32C.*;
import static org.lwjgl.opengl.GL45C.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.*;

/**
 * GL/Vulkan frame bridge: the two images the game's OpenGL framebuffer and the Vulkan terrain backend share, plus
 * the binary semaphores that hand them over.
 * <p>
 * <b>Threading.</b> Every method runs on Minecraft's render thread with the game's OpenGL context current and LWJGL
 * GL capabilities created for that thread ({@code GL.createCapabilities()}); the class issues GL calls and manipulates
 * GL state. It never creates a context, never changes the current context and never submits the terrain work: the
 * caller owns its command buffers, its queue submissions and its fences. The only Vulkan submission this class makes
 * is the single one-shot initial ownership handoff inside {@link #create}.
 * <p>
 * <b>What is shared.</b> A Vulkan-owned {@code VK_FORMAT_R8G8B8A8_UNORM} colour image and a Vulkan-owned
 * {@code VK_FORMAT_D32_SFLOAT} depth image are allocated as dedicated allocations with
 * {@code VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT} memory, exported as opaque file descriptors and imported by
 * GL as memory objects backing two GL textures. GL uses them through an FBO of its own; Vulkan renders into the same
 * memory through the image and image view handed out below. Neither API owns the memory of the other.
 * <p>
 * <b>Frame sequence (caller's view, all on the render thread):</b>
 * <ol>
 * <li>the game binds its own framebuffer;</li>
 * <li>{@link #beginFrame()} captures and validates that framebuffer, copies the game's colour and depth into the
 * shared images and makes GL signal {@link #waitSemaphore()};</li>
 * <li>{@link #recordAcquireBarriers(VkCommandBuffer)} is recorded <i>first</i> in the caller's command buffer: it
 * takes ownership of both images from the external client (GL) and leaves them in the layout the render pass
 * declares, {@link #COLOR_RENDER_LAYOUT} / {@link #DEPTH_RENDER_LAYOUT};</li>
 * <li>the caller records its terrain rendering into the same command buffer. Render passes must declare
 * {@code initialLayout} and {@code finalLayout} of {@link #COLOR_RENDER_LAYOUT} for the colour attachment and
 * {@link #DEPTH_RENDER_LAYOUT} for the depth attachment, and must use {@code VK_ATTACHMENT_LOAD_OP_LOAD} for both:
 * the game's colour and the game's depth must survive and keep participating in depth testing;</li>
 * <li>{@link #recordReleaseBarriers(VkCommandBuffer)} is recorded <i>last</i>: it gives both images back to the
 * external client (GL) in {@code VK_IMAGE_LAYOUT_GENERAL}, the layout GL's {@code GL_LAYOUT_GENERAL_EXT} declares;</li>
 * <li>the caller ends the command buffer and submits it with {@code pWaitSemaphores = }{@link #waitSemaphore()} and
 * {@code pSignalSemaphores = }{@link #doneSemaphore()};</li>
 * <li>{@link #endFrame()} makes GL wait on {@link #doneSemaphore()}, copies the shared images back into the game's
 * framebuffer and restores every piece of GL state it touched.</li>
 * </ol>
 * {@link #endFrame()} must only be called <b>after</b> that submission has been made: GL blocks its command stream on
 * {@link #doneSemaphore()}, so waiting for a submission that was never sent deadlocks the render thread.
 * <p>
 * <b>Layouts.</b> The layout Vulkan renders in is {@link #COLOR_RENDER_LAYOUT} for the colour image and
 * {@link #DEPTH_RENDER_LAYOUT} for the depth image. Both are {@code VK_IMAGE_LAYOUT_GENERAL}, which is what the
 * interop experiment proved: it takes ownership of these images from {@code VK_QUEUE_FAMILY_EXTERNAL}, renders into
 * them and gives them back, all in GENERAL layout, so the release layout is exactly the layout GL's
 * {@code GL_LAYOUT_GENERAL_EXT} waits for. Those two constants and {@link #recordAcquireBarriers(VkCommandBuffer)} /
 * {@link #recordReleaseBarriers(VkCommandBuffer)} are the only place the policy lives: a backend that moves to the
 * faster {@code VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL} / {@code VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL}
 * layouts changes the two constants and its render pass layouts together, and the barriers below then perform the
 * transition inside the ownership transfer.
 * <p>
 * <b>Semaphores.</b> Both are binary semaphores created by Vulkan, exported as opaque file descriptors and imported
 * by GL. The caller must wait on {@link #waitSemaphore()} with a destination stage mask that contains
 * {@code VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT} (add {@code VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT} and
 * {@code VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT} so the shared depth image is ordered as well) and must signal
 * {@link #doneSemaphore()}. A binary semaphore may only be signalled again after its previous signal has been
 * consumed, which is why exactly one {@code beginFrame}/{@code endFrame} pair is allowed per frame and why
 * {@link #create} consumes the initial signal itself.
 * <p>
 * <b>Verified contract.</b> The bridge only works with the framebuffer shape the interop experiment proved on both
 * target GPUs: {@code GL_COLOR_ATTACHMENT0} is an RGBA8 texture of exactly the target size, {@code GL_DEPTH_ATTACHMENT}
 * is a {@code GL_DEPTH_COMPONENT24} renderbuffer of exactly the target size with no stencil and one sample, and the
 * framebuffer's read buffer and draw buffer 0 are {@code GL_COLOR_ATTACHMENT0}. Anything else throws
 * {@link IllegalStateException} instead of rendering into an unverified layout; the caller then keeps the OpenGL
 * terrain path.
 * <p>
 * <b>Ownership.</b> The instance, physical device, logical device and queue are supplied by the caller and are never
 * destroyed here. Everything else this class creates is destroyed by {@link #delete()}.
 */
public final class VulkanFrameTarget implements FrameImageTarget {
    /** Lab switch: leaves the terrain's depth out of the write-back to OpenGL, to measure whether GL needs it. */
    private static final boolean SKIP_DEPTH_RETURN = Boolean.getBoolean("celtium.vulkan.skipDepthReturn");
    /** Lab switch: leaves the frame's colour out of the write-back to OpenGL, for the frame the present path has taken. */
    private static final boolean SKIP_COLOR_RETURN = Boolean.getBoolean("celtium.vulkan.skipColorReturn");

    /**
     * The layout the shared colour image has while the caller renders, and therefore the {@code initialLayout} and
     * {@code finalLayout} the caller's colour attachment must declare.
     * <p>
     * It is {@code VK_IMAGE_LAYOUT_GENERAL}, the layout the interop experiment proved end to end on both target GPUs:
     * it transfers ownership to and from {@code VK_QUEUE_FAMILY_EXTERNAL} in that layout and renders in it, so no
     * layout transition is needed at either boundary. {@code VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL} would be the
     * faster attachment layout, but it is not the proven configuration and it would have to be changed here and in the
     * caller's render pass together.
     */
    public static final int COLOR_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;

    /**
     * The layout the shared depth image has while the caller renders, and therefore the {@code initialLayout} and
     * {@code finalLayout} the caller's depth/stencil attachment must declare. See {@link #COLOR_RENDER_LAYOUT} for why
     * this is {@code VK_IMAGE_LAYOUT_GENERAL}.
     */
    public static final int DEPTH_RENDER_LAYOUT = VK_IMAGE_LAYOUT_GENERAL;

    /** GL names the layout the other API left the textures in; the Vulkan side always hands them over in GENERAL. */
    private static final int GL_SHARED_LAYOUT = EXTSemaphore.GL_LAYOUT_GENERAL_EXT;

    private static final long RETIREMENT_TIMEOUT_NANOS = 5_000_000_000L;

    /** Bounded so a driver that keeps reporting errors can never spin this loop forever. */
    private static final int MAX_DRAINED_GL_ERRORS = 16;

    private static final String VERTEX_SHADER_SOURCE =
            "#version 330 core\nuniform float depthValue;\n"
            + "void main(){vec2 p[3]=vec2[](vec2(-1,-1),vec2(3,-1),vec2(-1,3));"
            + "gl_Position=vec4(p[gl_VertexID],depthValue*2.0-1.0,1.0);}\n";

    /**
     * Writes the sampled depth of an ordinary D24 depth texture into the depth attachment through
     * {@code gl_FragDepth}. A cross-format depth blit is not portable, so the D24 to D32 conversion (and back) has to
     * be a shader operation; the colour output is masked off for both directions.
     */
    private static final String FRAGMENT_SHADER_SOURCE =
            "#version 330 core\nuniform int copyDepth; uniform sampler2D sourceDepth;"
            + "uniform vec4 colorValue; uniform float depthValue; out vec4 outputColor;\n"
            + "void main(){outputColor=colorValue; gl_FragDepth=copyDepth==1"
            + "?texelFetch(sourceDepth,ivec2(gl_FragCoord.xy),0).r:depthValue;}\n";

    private final VkPhysicalDevice physicalDevice;
    private final VkDevice device;
    private final VkQueue queue;
    private final int queueFamilyIndex;
    private final int width;
    private final int height;

    /** ARB_clip_control is required to pin the NDC depth convention the conversion shader assumes. */
    private final boolean clipControlAvailable;

    /** The shared colour image and its GL import. */
    private ExternalImage color;
    /** The shared depth image and its GL import. */
    private ExternalImage depth;

    /** Signalled by GL in {@link #beginFrame()}, waited on by the caller's submission. */
    private SemaphorePair waitSemaphore;
    /** Signalled by the caller's submission in {@code pSignalSemaphores}, waited on by GL in {@link #endFrame()}. */
    private SemaphorePair doneSemaphore;

    private int sharedFbo;
    /** Whether the game is currently drawing into the shared images rather than its own framebuffer. */
    private boolean sharedGameFramebuffer;
    /** The first GL error seen at a frame entry, with the entry's name; null while none was seen. */
    private String firstEntryGlError;
    private int scratchDepthTexture;
    private int scratchFbo;
    private int conversionProgram;
    private int conversionVao;
    private int copyDepthLocation;
    private int depthValueLocation;
    private int sourceDepthLocation;

    /** The game framebuffer the current frame started from, captured by {@link #beginFrame()}. */
    private int gameFbo;
    private int gameColorTexture;
    private int gameDepthRenderbuffer;

    private boolean frameOpen;
    /** Frames closed by a failure path rather than by {@link #endFrame()}; part of the failure record. */
    private int abandonedFrames;
    private String lastAbandonReason;
    /** A frame opened while the previous one was still open - a leak that used to be a crash. */
    private int leakedFrames;
    /** Set by {@link #abandonFrame(String)} so the caller's later {@link #endFrame()} is a no-op. */
    private boolean frameAbandoned;

    private void recordAbandonedFrame() {
        // Recorded once per abandoned frame so a run can say how many frames ended without a write-back.
        this.abandonedWrites++;
    }

    /** Frames whose {@link #endFrame()} was skipped because a failure path had already abandoned them. */
    private int abandonedWrites;

    /**
     * Per-step CPU time of the handoff, so the debug overlay can show where the milliseconds go instead of leaving
     * it to guesswork. Each accumulator holds nanoseconds since the last {@link #takeTimingSummary()}.
     */
    private long tWait;
    private long tColorIn;
    private long tDepthIn;
    private long tConvertIn;
    private long tColorOut;
    private long tConvertOut;
    private long tTimedFrames;
    private boolean deleted;

    private VulkanFrameTarget(VkPhysicalDevice physicalDevice, VkDevice device, VkQueue queue, int queueFamilyIndex,
            int width, int height, boolean clipControlAvailable) {
        this.physicalDevice = physicalDevice;
        this.device = device;
        this.queue = queue;
        this.queueFamilyIndex = queueFamilyIndex;
        this.width = width;
        this.height = height;
        this.clipControlAvailable = clipControlAvailable;
    }

    /**
     * Allocates both shared images, imports them into the current GL context, builds the GL framebuffers and the
     * depth conversion program, creates both exported binary semaphores and hands the initial ownership of the
     * images to GL (one one-shot Vulkan submission on {@code queue}, waited for before this method returns).
     * <p>
     * The GL state that this call and every later call touches is saved and restored, so the caller's bindings,
     * program, viewport and enables are unchanged afterwards. Nothing is left bound, and a failure cleans up
     * everything created so far before the exception is rethrown.
     * <p>
     * Preconditions the caller owns: the logical device must have been created with
     * {@code VK_KHR_external_memory_fd} and {@code VK_KHR_external_semaphore_fd} enabled (the device bootstrap
     * already enables both), it must be the device the current GL context's GPU was matched to by device and driver
     * UUID, and {@code queueFamilyIndex} must be the family of {@code queue}.
     *
     * @param instance         the Vulkan instance that owns {@code physicalDevice}; not destroyed here
     * @param physicalDevice   the physical device the current GL context's GPU was matched to
     * @param device           the logical device that owns {@code queue}; not destroyed here
     * @param queue            the graphics queue the terrain backend submits on
     * @param queueFamilyIndex the family of {@code queue} (also the family that owns the images while Vulkan renders)
     * @param width            target width in pixels, must equal the game framebuffer's colour texture width
     * @param height           target height in pixels, must equal the game framebuffer's colour texture height
     * @return a ready frame bridge; the caller owns it and must {@link #delete()} it
     * @throws IllegalArgumentException if a handle is null, a handle does not belong to the handle before it, or the
     *                                  size is not positive
     * @throws IllegalStateException    if the current GL context lacks OpenGL 4.5 / {@code ARB_direct_state_access},
     *                                  {@code GL_EXT_memory_object(_fd)} or {@code GL_EXT_semaphore(_fd)}, if the
     *                                  device cannot share these formats, or if any step fails
     */
    public static VulkanFrameTarget create(VkInstance instance, VkPhysicalDevice physicalDevice, VkDevice device,
            VkQueue queue, int queueFamilyIndex, int width, int height) {
        if (instance == null || physicalDevice == null || device == null || queue == null) {
            throw new IllegalArgumentException("VulkanFrameTarget.create needs non-null instance, physical device, device and queue handles");
        }
        // Fail on a wiring mistake before any native object is created: a handle from another device would otherwise
        // only surface as an opaque Vulkan error much later.
        if (physicalDevice.getInstance() != instance) {
            throw new IllegalArgumentException("The physical device does not belong to the supplied VkInstance");
        }
        if (device.getPhysicalDevice() != physicalDevice) {
            throw new IllegalArgumentException("The logical device does not belong to the supplied VkPhysicalDevice");
        }
        if (queue.getDevice() != device) {
            throw new IllegalArgumentException("The queue does not belong to the supplied VkDevice");
        }
        if (queueFamilyIndex < 0) {
            throw new IllegalArgumentException("A valid graphics queue family index is required, got " + queueFamilyIndex);
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid target size " + width + "x" + height);
        }
        GLCapabilities capabilities = requireGlCapabilities("VulkanFrameTarget.create");
        requireBridgeExtensions(capabilities);

        VulkanFrameTarget target = new VulkanFrameTarget(physicalDevice, device, queue, queueFamilyIndex,
                width, height, capabilities.OpenGL45 || capabilities.GL_ARB_clip_control);
        try {
            // The snapshot is closed on success and on failure, so a partially created target never leaves the
            // caller's GL state altered either.
            try (GlState state = new GlState()) {
                target.allocateSharedImages();
                target.allocateGlFramebuffers();
                target.allocateConversionProgram();
                target.allocateSemaphores();
                target.handInitialOwnershipToGl();
            }
            return target;
        } catch (RuntimeException | LinkageError error) {
            target.delete();
            throw error;
        }
    }

    /**
     * Starts a frame: captures the game framebuffer that is currently the draw framebuffer, validates it against the
     * verified contract, copies its colour and depth into the shared images and signals {@link #waitSemaphore()} so
     * the caller's submission may run.
     * <p>
     * The colour copy is a {@code GL_NEAREST} blit of the game's {@code GL_COLOR_ATTACHMENT0} texture. Depth cannot be
     * blitted across formats, so the game's D24 renderbuffer is blitted into a D24 scratch texture and the conversion
     * program then writes those normalised values into the shared D32 image. GL's command stream is flushed after the
     * signal so the terrain backend's Semaphore wait can never observe an unsignalled semaphore.
     * <p>
     * Safe to call only with the game framebuffer bound and no earlier frame open.
     *
     * @throws IllegalStateException if a previous {@code beginFrame} was not closed by {@link #endFrame()}, or if the
     *                               bound framebuffer does not match the verified contract (the message names the
     *                               difference)
     */
    /** Preflight the CURRENT bound Minecraft FBO without opening/signalling an interop handoff. */
    public String currentFramebufferViolation() {
        // The contract describes a game framebuffer with its own RGBA8 colour and D24 depth renderbuffer. When the game
        // draws into the shared images there is no such framebuffer to judge: the depth is the Vulkan-owned D32 texture
        // and the colour is the shared one, both of which this class created. Judging them by the game contract refused
        // every immediate batch once the swap was on (measured: accepted 9042 -> 982, refusals all reading
        // "target is outside the RGBA8/D24/full-viewport bridge contract"), while the picture stayed correct because the
        // game drew them itself.
        if (isSharedFramebuffer(org.lwjgl.opengl.GL11.glGetInteger(org.lwjgl.opengl.GL30C.GL_DRAW_FRAMEBUFFER_BINDING))) {
            return null;
        }
        checkAlive();
        try (GlState state = new GlState()) {
            int fbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            if (fbo == 0) return "default framebuffer";
            int texture = glGetNamedFramebufferAttachmentParameteri(fbo, GL_COLOR_ATTACHMENT0,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            int depth = glGetNamedFramebufferAttachmentParameteri(fbo, GL_DEPTH_ATTACHMENT,
                    GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
            return contractViolation(fbo, texture, depth, true);
        }
    }

    /** Records that the game's framebuffer has been pointed at the shared images. */
    public void noteGameFramebufferAdopted() {
        this.gameFbo = this.sharedFbo;
        this.gameColorTexture = this.color == null ? 0 : this.color.glTexture;
        this.gameDepthRenderbuffer = 0;
        this.sharedGameFramebuffer = true;
    }

    /** {@return the framebuffer object the game's main framebuffer may be pointed at}, or 0 when it may not. */
    public int adoptableFramebuffer(int width, int height) {
        if (sharedFbo == 0 || color == null || depth == null || color.glTexture == 0 || depth.glTexture == 0
                || this.width != width || this.height != height) {
            return 0;
        }
        return sharedFbo;
    }

    /** {@return the first GL error seen at a frame entry with its name}, or null. */
    public String firstEntryGlError() {
        return this.firstEntryGlError;
    }

    public int sharedColorTexture() {
        return color == null ? 0 : color.glTexture;
    }

    public int sharedDepthTexture() {
        return depth == null ? 0 : depth.glTexture;
    }

    public boolean isSharedFramebuffer(int framebufferObject) {
        return framebufferObject != 0 && framebufferObject == sharedFbo;
    }

    public void beginFrame() {
        // Probe B: this class's own entry, before GlState and before any of the copies below.
        try {
            int probe = org.lwjgl.opengl.GL11.glGetError();
            if (probe != 0) {
                this.firstEntryGlError = "beginFrame entry: 0x" + Integer.toHexString(probe);
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe must never break the frame.
        }
        beginFrame(true);
    }

    /**
     * Begins a frame using the game framebuffer this bridge already validated, even when it is not the bound one.
     * <p>
     * Minecraft's final composite - {@code Framebuffer.func_178038_a}, identified by its stack trace - draws to the
     * default framebuffer while *reading* the game framebuffer's texture as a sampler. At that moment the game
     * framebuffer is not bound, so the ordinary {@link #beginFrame()} refuses; this variant uses the remembered target
     * instead, and only when it was validated before, which is what makes presenting the frame's own content possible
     * at the one moment that matters.
     */
    public void beginFrameForPresent() {
        beginFrame(true);
    }

    private void beginFrame(boolean useRememberedTarget) {
        checkAlive();
        if (frameOpen) {
            // A frame can be left open by a failure inside a frame - a refused submission, a stalled device, an
            // exception thrown between begin and end. Throwing here turned that into a crash on the *next* frame and
            // the client then sat on the crash screen instead of ending the run, which is what the failure injection
            // measured (exit 124, report missing). The previous frame is abandoned and counted instead: the run keeps
            // going far enough to report the failure, and the counter keeps the leak visible.
            frameOpen = false;
        org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setFrameOpen(false);
            leakedFrames++;
            lastAbandonReason = "beginFrame found the previous frame open";
        }
        requireGlCapabilities("beginFrame");
        try (GlState state = new GlState()) {
            drainGlErrors();
            int fbo = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int colorTexture;
            int depthRenderbuffer;
            if (fbo == 0 && useRememberedTarget && gameFbo != 0) {
                // Reuse the target validated earlier in the frame: the composite's moment has the default framebuffer
                // bound, and the game framebuffer is still the source of the picture.
                fbo = gameFbo;
                colorTexture = gameColorTexture;
                depthRenderbuffer = gameDepthRenderbuffer;
            } else if (fbo == 0) {
                // Querying attachments of the default framebuffer is not what this bridge is built on, and some
                // drivers reject it; fail with the contract message before touching it.
                throw new IllegalStateException("beginFrame: the bound draw framebuffer is the default framebuffer [bound=" + fbo + " gameFbo=" + gameFbo + " sharedFbo=" + sharedFbo + " useRemembered=" + useRememberedTarget + "]; the"
                        + " verified bridge needs an FBO whose colour attachment is an RGBA8 texture and whose depth"
                        + " attachment is a D24 renderbuffer");
            } else {
                colorTexture = glGetNamedFramebufferAttachmentParameteri(fbo, GL_COLOR_ATTACHMENT0,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                depthRenderbuffer = glGetNamedFramebufferAttachmentParameteri(fbo, GL_DEPTH_ATTACHMENT,
                        GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
                if (fbo == sharedFbo && sharedFbo != 0) {
                    gameFbo = fbo;
                    gameColorTexture = color.glTexture;
                    gameDepthRenderbuffer = 0;
                    sharedGameFramebuffer = true;
                } else {
                    requireVerifiedContract(fbo, colorTexture, depthRenderbuffer, "beginFrame");
                    gameFbo = fbo;
                    gameColorTexture = colorTexture;
                    gameDepthRenderbuffer = depthRenderbuffer;
                    sharedGameFramebuffer = false;
                }
            }

            long step = System.nanoTime();
            // An identity copy is invalid, and with the game rendering into the shared images both of these are:
            // the colour copy would be the image onto itself and the depth is already the D32 one the scratch holds.
            if (!sharedGameFramebuffer) {
            glBlitNamedFramebuffer(fbo, sharedFbo, 0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT,
                    GL_NEAREST);
            }
            long afterColor = System.nanoTime();
            this.tColorIn += afterColor - step;
            // The bridge's commands leave the shared colour image as a colour attachment from here on; recording it is
            // what lets the present path stop being told a layout.
            this.colorImageLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL;
            // An identity copy is invalid, and with the game rendering into the shared images both of these are:
            // the colour copy would be the image onto itself and the depth is already the D32 one the scratch holds.
            if (!sharedGameFramebuffer) {
            glBlitNamedFramebuffer(fbo, scratchFbo, 0, 0, width, height, 0, 0, width, height, GL_DEPTH_BUFFER_BIT,
                    GL_NEAREST);
            }
            long afterDepth = System.nanoTime();
            this.tDepthIn += afterDepth - afterColor;
            if (!sharedGameFramebuffer) {
                convertDepth(scratchDepthTexture, sharedFbo);
            }
            this.tConvertIn += System.nanoTime() - afterDepth;
            glCheck("beginFrame: RGBA8 colour copy and D24 to D32 depth conversion [bound=" + fbo + " gameFbo=" + gameFbo + " sharedFbo=" + sharedFbo + "]");

            step = System.nanoTime();
            signalFromGl(waitSemaphore.gl, "beginFrame: glSignalSemaphoreEXT(GL to Vulkan)");
            glFlush();
            this.tWait += System.nanoTime() - step;
            this.tTimedFrames++;
            frameOpen = true;
            org.soutalab.celtium.vulkan.VulkanDisplayListCensus.setFrameOpen(true);
        }
    }

    /**
     * The shared colour image, in {@link #COLOR_RENDER_LAYOUT} between
     * {@link #recordAcquireBarriers(VkCommandBuffer)} and {@link #recordReleaseBarriers(VkCommandBuffer)}.
     * <p>
     * In LWJGL 3 every non-dispatchable Vulkan handle is a plain {@code long} (there is no {@code VkImage} type), so
     * the handle is returned as the {@code long} the caller's Vulkan calls take. The caller does not own it and must
     * not destroy it.
     */
    /**
     * {@return the layout the shared colour image is currently in}, as far as Vulkan is concerned.
     * <p>
     * The bridge's own commands are the only ones that move it - the copy-in leaves it as a colour attachment, and GL's
     * blits are not Vulkan commands and do not enter this bookkeeping - so this stays consistent across a frame and a
     * caller may read it instead of asserting a layout.
     */
    public int colorImageLayout() {
        return this.colorImageLayout;
    }

    /** Records the layout a caller left the shared colour image in. */
    public void noteColorImageLayout(int layout) {
        this.colorImageLayout = layout;
    }

    private int colorImageLayout = org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_UNDEFINED;

    public long colorImage() {
        checkAlive();
        return color.image;
    }

    /**
     * Probes whether one shared depth image could serve both APIs directly.
     * <p>
     * The shared depth image is {@code VK_FORMAT_D32_SFLOAT} because that is what the bridge was able to share, and
     * the game's depth attachment is a D24 renderbuffer, so every handoff converts between the two with two
     * full-screen shader passes. If the device can also import and export a {@code D24_UNORM_S8_UINT} image as a
     * depth attachment, the conversion could be replaced by a plain same-format blit, so the answer is reported in
     * the debug overlay rather than guessed at.
     *
     * @return a one-line report of what each depth format can do as a shared image
     */
    public static String describeDepthFormatSharing(VulkanContext context) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            StringBuilder line = new StringBuilder("shared depth formats:");
            int[] formats = {VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT};
            for (int format : formats) {
                line.append(' ').append(format == VK_FORMAT_D24_UNORM_S8_UINT ? "D24S8" : "D32F").append('=');
                VkPhysicalDeviceExternalImageFormatInfo externalInfo =
                        VkPhysicalDeviceExternalImageFormatInfo.calloc(stack).sType$Default()
                                .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
                VkPhysicalDeviceImageFormatInfo2 query = VkPhysicalDeviceImageFormatInfo2.calloc(stack)
                        .sType$Default()
                        .pNext(externalInfo.address())
                        .format(format)
                        .type(VK_IMAGE_TYPE_2D)
                        .tiling(VK_IMAGE_TILING_OPTIMAL)
                        .usage(VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT
                                | VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT);
                VkExternalImageFormatProperties externalProperties =
                        VkExternalImageFormatProperties.calloc(stack).sType$Default();
                VkImageFormatProperties2 properties = VkImageFormatProperties2.calloc(stack).sType$Default()
                        .pNext(externalProperties.address());
                int result = vkGetPhysicalDeviceImageFormatProperties2(context.physicalDevice(), query, properties);
                if (result != VK_SUCCESS) {
                    line.append("query failed (").append(result).append(')');
                    continue;
                }
                int features = externalProperties.externalMemoryProperties().externalMemoryFeatures();
                int required = VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT | VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
                line.append((features & required) == required ? "shareable" : "not-shareable");
            }
            return line.toString();
        }
    }

    /** The shared {@code VK_FORMAT_D32_SFLOAT} depth image; see {@link #colorImage()} for the handle type. */
    public long depthImage() {
        checkAlive();
        return depth.image;
    }

    /** The colour image view for the caller's framebuffer; see {@link #colorImage()} for the handle type. */
    public long colorView() {
        checkAlive();
        return color.view;
    }

    /** The depth image view for the caller's framebuffer; see {@link #colorImage()} for the handle type. */
    public long depthView() {
        checkAlive();
        return depth.view;
    }

    /**
     * The binary semaphore GL signals in {@link #beginFrame()} and the caller's {@code vkQueueSubmit} must wait on:
     * {@code pWaitSemaphores} must contain this handle with a destination stage mask containing
     * {@code VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT} (plus the early and late fragment test stages so the
     * depth image is ordered too). See {@link #colorImage()} for the handle type.
     */
    public long waitSemaphore() {
        checkAlive();
        return waitSemaphore.vk;
    }

    /**
     * The binary semaphore the caller's {@code vkQueueSubmit} must signal ({@code pSignalSemaphores}) and GL waits on
     * in {@link #endFrame()}. See {@link #colorImage()} for the handle type.
     */
    public long doneSemaphore() {
        checkAlive();
        return doneSemaphore.vk;
    }

    /**
     * Records the ownership acquire of both shared images into the caller's command buffer: each image is taken from
     * {@code VK_QUEUE_FAMILY_EXTERNAL} (GL) to {@code queueFamilyIndex}, and it is put into
     * {@link #COLOR_RENDER_LAYOUT} / {@link #DEPTH_RENDER_LAYOUT} for the caller's rendering.
     * <p>
     * The queue family indices, the access masks, the stage masks and the layouts are the ones the interop experiment
     * ran with validation enabled on both target GPUs: an acquire claims the external client's writes with the
     * attachment accesses as its destination mask, and {@code VK_PIPELINE_STAGE_ALL_COMMANDS_BIT} on both sides makes
     * everything recorded later depend on that acquisition.
     * <p>
     * Must be recorded after the submission has waited on {@link #waitSemaphore()} and before any command that reads
     * or writes the shared images.
     *
     * @throws IllegalArgumentException if {@code commandBuffer} is null
     */
    public void recordAcquireBarriers(VkCommandBuffer commandBuffer) {
        checkAlive();
        if (commandBuffer == null) {
            throw new IllegalArgumentException("recordAcquireBarriers needs a recording command buffer");
        }
        recordOwnershipBarriers(commandBuffer, false, false);
    }

    /**
     * Records the ownership release of both shared images and their return to the layout GL expects: each image goes
     * from {@code queueFamilyIndex} back to {@code VK_QUEUE_FAMILY_EXTERNAL}, publishing the caller's attachment
     * writes so GL may read them after waiting on {@link #doneSemaphore()}.
     * <p>
     * Must be recorded after the last command that touches the shared images and before the command buffer is ended,
     * so the semaphore the caller signals with that submission is only signalled once the images are GL's again.
     *
     * @throws IllegalArgumentException if {@code commandBuffer} is null
     */
    public void recordReleaseBarriers(VkCommandBuffer commandBuffer) {
        checkAlive();
        if (commandBuffer == null) {
            throw new IllegalArgumentException("recordReleaseBarriers needs a recording command buffer");
        }
        recordOwnershipBarriers(commandBuffer, true, false);
    }

    /**
     * Ends the frame: makes GL wait on {@link #doneSemaphore()}, copies the shared colour image back over the game's
     * colour attachment, converts the shared D32 depth back into the game's D24 depth renderbuffer and restores every
     * piece of GL state it changed.
     * <p>
     * The write-back always targets the framebuffer captured by {@link #beginFrame()}, even when the game has bound a
     * different framebuffer in the meantime, because that framebuffer holds the colour and depth this frame was built
     * from. That captured framebuffer is re-validated first; the binding that is current when this method is entered is
     * restored before it returns, exactly as it was found.
     * <p>
     * Must only be called after the caller has submitted the command buffer that signals {@link #doneSemaphore()};
     * otherwise GL blocks the render thread forever waiting for a signal that will never arrive.
     *
     * @throws IllegalStateException if no frame is open, or if the captured framebuffer (for example after the game
     *                               recreated it) no longer matches the verified contract; the caller must then
     *                               discard this target and create a new one
     */
    /**
     * {@return a one-line breakdown of the handoff cost in microseconds per frame, or {@code null} when nothing was
     * timed yet}. The accumulators are reset, so the caller gets the average over the period since the last call.
     */
    public String takeTimingSummary() {
        if (this.tTimedFrames == 0L) {
            return null;
        }
        // tTimedFrames counts beginFrame() calls, i.e. one per handoff, so this is an average per handoff. The
        // numbers are CPU time inside the GL calls - for the semaphore waits that equals the time the GL thread was
        // blocked, but for the blits and the conversion pass it is only the time spent enqueueing the work, not the
        // time the GPU spends executing it.
        long frames = Math.max(1L, this.tTimedFrames);
        String summary = "handoff detail (CPU API time, avg/handoff): wait " + (this.tWait / frames / 1000L) + " us, colour-in "
                + (this.tColorIn / frames / 1000L) + " us, depth-in " + (this.tDepthIn / frames / 1000L)
                + " us, convert-in " + (this.tConvertIn / frames / 1000L) + " us, colour-out "
                + (this.tColorOut / frames / 1000L) + " us, convert-out " + (this.tConvertOut / frames / 1000L)
                + " us"
                // The raw totals as well, and how many handoffs they cover: the averages cannot be turned into a
                // per-frame cost without both, and a game frame contains several handoffs.
                + " | totals(ms)/handoffs: wait " + (this.tWait / 1000000L) + " colour-in "
                + (this.tColorIn / 1000000L) + " depth-in " + (this.tDepthIn / 1000000L) + " convert-in "
                + (this.tConvertIn / 1000000L) + " colour-out " + (this.tColorOut / 1000000L) + " convert-out "
                + (this.tConvertOut / 1000000L) + " / " + frames;
        this.tWait = 0L;
        this.tColorIn = 0L;
        this.tDepthIn = 0L;
        this.tConvertIn = 0L;
        this.tColorOut = 0L;
        this.tConvertOut = 0L;
        this.tTimedFrames = 0L;
        return summary;
    }

    /**
     * Closes the frame without the write-back to the game framebuffer.
     * <p>
     * A submission the driver refused leaves the frame open and the frame's semaphores unconsumed, so the write-back
     * {@link #endFrame()} performs would wait on a signal that never comes - the client then hangs instead of ending the
     * run with a message. The failure paths call this instead: the frame is over, nothing is returned to OpenGL, and the
     * next frame is refused by the backend's stalled flag rather than by an open frame. Idempotent, and safe to call
     * with no frame open.
     */
    public void abandonFrame() {
        abandonFrame("unspecified");
    }

    /** {@see #abandonFrame()} - the reason is recorded so the failure reproduction says which path abandoned it. */
    public void abandonFrame(String reason) {
        if (frameOpen) {
            frameOpen = false;
            frameAbandoned = true;
            abandonedFrames++;
            lastAbandonReason = reason;
        }
    }

    public void endFrame() {
        // Probe B: the frame's own entry, before any of its state work, so "the bind path" and "our own begin" separate.
        try {
            int probe = org.lwjgl.opengl.GL11.glGetError();
            if (probe != 0) {
                if (this.firstEntryGlError == null) {
                    this.firstEntryGlError = "endFrame entry: 0x" + Integer.toHexString(probe);
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A probe must never break the frame.
        }
        checkAlive();
        if (frameAbandoned) {
            // The failure path already closed this frame without the write-back; endFrame() from the caller that was
            // interrupted is a no-op rather than a second failure.
            frameAbandoned = false;
            recordAbandonedFrame();
            return;
        }
        if (!frameOpen) {
            throw new IllegalStateException("endFrame() was called without a matching beginFrame()");
        }
        // The frame is over even if the write-back below fails: the caller already submitted its work and the
        // semaphores are consumed, so leaving the frame open would only wedge the next beginFrame().
        frameOpen = false;
        requireGlCapabilities("endFrame");
        try (GlState state = new GlState()) {
            drainGlErrors();
                // The contract describes the framebuffer the game owns. When the game draws into the shared
                // images the objects to judge are this class own ones and the liveness test at entry has just
                // checked them; judging them by the game contract counted one error per pass (errors=2883 of
                // 2883 passes), which is what kept the fixture FAILED.
                if (!sharedGameFramebuffer) {
                requireVerifiedContract(gameFbo, gameColorTexture, gameDepthRenderbuffer, "endFrame");
                }
            long step = System.nanoTime();
            waitGlOn(doneSemaphore.gl, "endFrame: glWaitSemaphoreEXT(Vulkan to GL)");
            glFlush();
            this.tWait += System.nanoTime() - step;

            step = System.nanoTime();
            // Lab switch, the colour counterpart of SKIP_DEPTH_RETURN: the write-back into the game's framebuffer is what
            // keeps OpenGL's own composite and the game's screenshot valid, and once the frame is presented from Vulkan
            // neither is needed for the picture the user sees. Off by default; measured with the presented image as the
            // evidence, since with this on the GL framebuffer deliberately keeps whatever it had.
            if (!SKIP_COLOR_RETURN) {
                if (!sharedGameFramebuffer) {
                glBlitNamedFramebuffer(sharedFbo, gameFbo, 0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT,
                        GL_NEAREST);
                }
            }
            long afterColor = System.nanoTime();
            this.tColorOut += afterColor - step;
            // Lab switch: does anything on the OpenGL side actually need the terrain's depth back? The handback exists
            // so that what GL draws after this point (the hand, the GUI, entities in some orders) is depth tested
            // against the terrain. If the image does not change without it, the depth return is one of the bridge
            // operations that a native frame could drop - which is what completion condition (b) needs to know.
            if (!SKIP_DEPTH_RETURN && !sharedGameFramebuffer) {
                convertDepth(depth.glTexture, gameFbo);
            }
            this.tConvertOut += System.nanoTime() - afterColor;
            glCheck("endFrame: RGBA8 colour return and D32 to D24 depth conversion");
        }
    }

    /**
     * Whether this target can still be used for {@code width}x{@code height}: its own size, its own GL objects (a
     * recreated GL context invalidates the imported textures and the conversion program) and, once a frame has been
     * observed, the captured game framebuffer's attachments must all still be the verified ones.
     * <p>
     * Query only, and it never throws for a stale target: {@code false} tells the caller to {@link #delete()} this
     * bridge and {@link #create} a new one for the new size. Requires a current GL context like every other method.
     */
    public boolean matches(int width, int height) {
        checkAlive();
        if (width != this.width || height != this.height) {
            return false;
        }
        if (!glContextAvailable()) {
            // A lost GL context destroyed the imported textures, the semaphores and the conversion program with it.
            return false;
        }
        // A lost GL context, or another mod deleting the imported objects, makes the bridge unusable even when the
        // size and the game framebuffer are unchanged. Object names are reused after a context recreation, so this is
        // a best-effort liveness check, not a proof.
        if (!glIsFramebuffer(sharedFbo) || !glIsTexture(color.glTexture) || !glIsTexture(depth.glTexture)
                || !glIsProgram(conversionProgram)) {
            return false;
        }
        if (gameFbo == 0) {
            // Nothing observed yet: only the size can be judged, the game framebuffer is validated by beginFrame().
            return true;
        }
        if (sharedGameFramebuffer) {
            // The game draws into the shared images, so the contract is about this class's own objects, which the liveness
            // check above just passed. Judging it by the game-framebuffer contract said no (the shared depth is a texture
            // and the contract wants a D24 renderbuffer), which rebuilt the target every frame and threw away the swap.
            return true;
        }
        return contractViolation(gameFbo, gameColorTexture, gameDepthRenderbuffer, false) == null;
    }

    /**
     * Destroys every GL object, Vulkan object and semaphore this bridge owns. Idempotent.
     * <p>
     * This is a retirement path, not a frame path, so it is allowed to wait: GL is retired with a completion fence
     * (all GL work that touched the imported textures must be finished before their memory objects disappear) and the
     * caller's queue is drained with {@code vkQueueWaitIdle}, because this bridge holds no fence for the caller's
     * submissions. Call it on the render thread, after the last {@link #endFrame()}, and only once the caller has
     * stopped submitting work that uses these images.
     * <p>
     * The instance, physical device, logical device and queue belong to the caller and are not destroyed.
     *
     * @throws IllegalStateException if the GPU does not retire the GL work in time, or the queue does not become idle;
     *                               in that case nothing further is destroyed
     */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        frameOpen = false;

        // Destroying a GL context already destroyed the objects it owned, so during shutdown the GL side may be
        // skipped; the Vulkan allocations are ours and are always released.
        boolean glCurrent = glContextAvailable();
        if (glCurrent) {
            retireGl();
        }
        // No caller fence exists for the terrain submissions, so the queue is the only way to know that no pending
        // command still touches these images.
        int idle = vkQueueWaitIdle(queue);
        if (idle != VK_SUCCESS) {
            throw new IllegalStateException("vkQueueWaitIdle before deleting the frame target returned VkResult " + idle);
        }

        if (glCurrent) {
            if (sharedFbo != 0) {
                glDeleteFramebuffers(sharedFbo);
                sharedFbo = 0;
            }
            if (scratchFbo != 0) {
                glDeleteFramebuffers(scratchFbo);
                scratchFbo = 0;
            }
            if (scratchDepthTexture != 0) {
                glDeleteTextures(scratchDepthTexture);
                scratchDepthTexture = 0;
            }
            if (conversionVao != 0) {
                glDeleteVertexArrays(conversionVao);
                conversionVao = 0;
            }
            if (conversionProgram != 0) {
                glDeleteProgram(conversionProgram);
                conversionProgram = 0;
            }
            deleteGlImport(color);
            deleteGlImport(depth);
            if (waitSemaphore != null && waitSemaphore.gl != 0) {
                EXTSemaphore.glDeleteSemaphoresEXT(waitSemaphore.gl);
                waitSemaphore.gl = 0;
            }
            if (doneSemaphore != null && doneSemaphore.gl != 0) {
                EXTSemaphore.glDeleteSemaphoresEXT(doneSemaphore.gl);
                doneSemaphore.gl = 0;
            }
        }

        destroyVulkanImage(color);
        destroyVulkanImage(depth);
        if (waitSemaphore != null && waitSemaphore.vk != 0) {
            vkDestroySemaphore(device, waitSemaphore.vk, null);
            waitSemaphore.vk = 0;
        }
        if (doneSemaphore != null && doneSemaphore.vk != 0) {
            vkDestroySemaphore(device, doneSemaphore.vk, null);
            doneSemaphore.vk = 0;
        }
        color = null;
        depth = null;
        waitSemaphore = null;
        doneSemaphore = null;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Allocation
    // ---------------------------------------------------------------------------------------------------------------

    private void allocateSharedImages() {
        // The image objects are published in their fields before they are filled in, so a failure half way through
        // still reaches delete() and cannot leak a Vulkan allocation or an imported GL object.
        color = new ExternalImage(VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT);
        allocateImage(color, GL_RGBA8, VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT, "RGBA8 colour");
        depth = new ExternalImage(VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT);
        allocateImage(depth, GL_DEPTH_COMPONENT32F, VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT, "D32 depth");
    }

    /**
     * Allocates one dedicated, optimal, single-sample Vulkan image with external opaque-FD memory, exports that
     * memory and imports it into GL as a memory object backing a texture. This is the sequence the interop experiment
     * proved on both target GPUs, including the dedicated-allocation flag GL needs to import a dedicated allocation,
     * the mutable-format bit GL's texture views require and the rule that a successful GL import consumes the FD.
     */
    private void allocateImage(ExternalImage image, int glInternalFormat, int attachmentUsage, String what) {
        int format = image.format;
        int aspect = image.aspect;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties formatProperties = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(physicalDevice, format, formatProperties);
            int supported = formatProperties.optimalTilingFeatures();
            // EXT_external_objects wants every ordinary usage the format supports to be requested on the Vulkan
            // side, because GL cannot state usages of its own.
            int usage = VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if ((supported & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) != 0) {
                usage |= VK_IMAGE_USAGE_SAMPLED_BIT;
            }
            if ((supported & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0) {
                usage |= VK_IMAGE_USAGE_STORAGE_BIT;
            }
            if ((supported & VK_FORMAT_FEATURE_COLOR_ATTACHMENT_BIT) != 0) {
                usage |= VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            }
            if ((supported & VK_FORMAT_FEATURE_DEPTH_STENCIL_ATTACHMENT_BIT) != 0) {
                usage |= VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT | VK_IMAGE_USAGE_INPUT_ATTACHMENT_BIT;
            }
            // The terrain backend renders into these images as render pass attachments, so the matching usage bit is
            // not optional; the experiment only blitted, this bridge is the first thing that requires it.
            if ((usage & attachmentUsage) == 0) {
                throw new IllegalStateException("Vulkan format " + format + " cannot be used as an external "
                        + what + " attachment on this device");
            }
            int flags = VK_IMAGE_CREATE_MUTABLE_FORMAT_BIT;

            VkPhysicalDeviceExternalImageFormatInfo externalInfo = VkPhysicalDeviceExternalImageFormatInfo.calloc(stack)
                    .sType$Default()
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkPhysicalDeviceImageFormatInfo2 query = VkPhysicalDeviceImageFormatInfo2.calloc(stack)
                    .sType$Default()
                    .pNext(externalInfo.address())
                    .format(format)
                    .type(VK_IMAGE_TYPE_2D)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .flags(flags);
            VkExternalImageFormatProperties externalProperties = VkExternalImageFormatProperties.calloc(stack)
                    .sType$Default();
            VkImageFormatProperties2 properties = VkImageFormatProperties2.calloc(stack)
                    .sType$Default()
                    .pNext(externalProperties.address());
            check(vkGetPhysicalDeviceImageFormatProperties2(physicalDevice, query, properties),
                    "vkGetPhysicalDeviceImageFormatProperties2(" + format + ")");
            int externalFeatures = externalProperties.externalMemoryProperties().externalMemoryFeatures();
            int requiredFeatures = VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT | VK_EXTERNAL_MEMORY_FEATURE_EXPORTABLE_BIT;
            if ((externalFeatures & requiredFeatures) != requiredFeatures
                    || (externalProperties.externalMemoryProperties().compatibleHandleTypes()
                    & VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT) == 0) {
                throw new IllegalStateException("The device cannot import and export " + what
                        + " external images as opaque file descriptors");
            }
            if (width > properties.imageFormatProperties().maxExtent().width()
                    || height > properties.imageFormatProperties().maxExtent().height()
                    || (properties.imageFormatProperties().sampleCounts() & VK_SAMPLE_COUNT_1_BIT) == 0) {
                throw new IllegalStateException("The device does not support a " + width + "x" + height
                        + " single-sample external " + what + " image");
            }

            VkExternalMemoryImageCreateInfo sharing = VkExternalMemoryImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            VkImageCreateInfo create = VkImageCreateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(sharing.address())
                    .flags(flags)
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(format)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(usage)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            create.extent().set(width, height, 1);
            LongBuffer handle = stack.mallocLong(1);
            check(vkCreateImage(device, create, null, handle), "vkCreateImage(" + what + ")");
            image.image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.calloc(stack);
            vkGetImageMemoryRequirements(device, image.image, requirements);
            image.allocationBytes = requirements.size();
            // A dedicated allocation is always used: the RX 6400 requires it for external images, and GL must be told
            // about it through GL_DEDICATED_MEMORY_OBJECT_EXT so it takes the same import path.
            VkMemoryDedicatedAllocateInfo dedicated = VkMemoryDedicatedAllocateInfo.calloc(stack)
                    .sType$Default()
                    .image(image.image);
            VkExportMemoryAllocateInfo export = VkExportMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .handleTypes(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT)
                    .pNext(dedicated.address());
            VkPhysicalDeviceMemoryProperties memoryProperties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(physicalDevice, memoryProperties);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack)
                    .sType$Default()
                    .pNext(export.address())
                    .allocationSize(image.allocationBytes)
                    .memoryTypeIndex(memoryType(memoryProperties, requirements.memoryTypeBits(),
                            VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT));
            if (org.soutalab.celtium.vulkan.VulkanAllocationFault.shouldFail("VulkanFrameTarget:927")) {
                throw new IllegalStateException("injected allocation failure: VulkanFrameTarget:927");
            }
            check(vkAllocateMemory(device, allocate, null, handle), "vkAllocateMemory(" + what + ")");
            image.memory = handle.get(0);
            check(vkBindImageMemory(device, image.image, image.memory, 0), "vkBindImageMemory(" + what + ")");

            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                    .sType$Default()
                    .image(image.image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(format);
            viewInfo.subresourceRange().set(aspect, 0, 1, 0, 1);
            check(vkCreateImageView(device, viewInfo, null, handle), "vkCreateImageView(" + what + ")");
            image.view = handle.get(0);

            glCheck("before importing the " + what + " memory FD");
            image.glMemory = EXTMemoryObject.glCreateMemoryObjectsEXT();
            EXTMemoryObject.glMemoryObjectParameterivEXT(image.glMemory,
                    EXTMemoryObject.GL_DEDICATED_MEMORY_OBJECT_EXT, stack.ints(GL_TRUE));
            glCheck("glMemoryObjectParameterivEXT(GL_DEDICATED_MEMORY_OBJECT_EXT) for " + what);

            VkMemoryGetFdInfoKHR fdInfo = VkMemoryGetFdInfoKHR.calloc(stack)
                    .sType$Default()
                    .memory(image.memory)
                    .handleType(VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT);
            IntBuffer descriptor = stack.mallocInt(1);
            check(KHRExternalMemoryFd.vkGetMemoryFdKHR(device, fdInfo, descriptor), "vkGetMemoryFdKHR(" + what + ")");
            int fd = descriptor.get(0);
            try {
                EXTMemoryObjectFD.glImportMemoryFdEXT(image.glMemory, image.allocationBytes,
                        EXTMemoryObjectFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
                glCheck("glImportMemoryFdEXT(" + what + ")");
                // A successful GL import consumes the descriptor; only a failure leaves it open here.
                fd = -1;
            } finally {
                if (fd >= 0) {
                    UNISTD.close(null, fd);
                }
            }

            image.glTexture = glCreateTextures(GL_TEXTURE_2D);
            // The tiling parameter must be set before the storage is allocated from the memory object.
            glTextureParameteri(image.glTexture, EXTMemoryObject.GL_TEXTURE_TILING_EXT,
                    EXTMemoryObject.GL_OPTIMAL_TILING_EXT);
            EXTMemoryObject.glTextureStorageMem2DEXT(image.glTexture, 1, glInternalFormat, width, height,
                    image.glMemory, 0);
            glCheck("glTextureStorageMem2DEXT(" + what + ")");
            glTextureParameteri(image.glTexture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(image.glTexture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(image.glTexture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTextureParameteri(image.glTexture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glTextureParameteri(image.glTexture, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            glCheck("external " + what + " texture parameters");
        }
    }

    private void allocateGlFramebuffers() {
        // The FBO whose attachments are the two imported textures: GL writes the game's frame into them in
        // beginFrame(), the conversion program writes the D32 depth into its depth attachment, and endFrame() reads
        // the colour back out of it.
        sharedFbo = glCreateFramebuffers();
        glNamedFramebufferTexture(sharedFbo, GL_COLOR_ATTACHMENT0, color.glTexture, 0);
        glNamedFramebufferTexture(sharedFbo, GL_DEPTH_ATTACHMENT, depth.glTexture, 0);
        glNamedFramebufferDrawBuffer(sharedFbo, GL_COLOR_ATTACHMENT0);
        glNamedFramebufferReadBuffer(sharedFbo, GL_COLOR_ATTACHMENT0);
        requireComplete(sharedFbo, "shared colour/depth framebuffer");

        // The blit destination for the game's D24 renderbuffer: the depth conversion program samples this ordinary
        // texture, because a renderbuffer cannot be sampled and a D24 to D32 depth blit is not portable.
        scratchDepthTexture = glCreateTextures(GL_TEXTURE_2D);
        glTextureStorage2D(scratchDepthTexture, 1, GL_DEPTH_COMPONENT24, width, height);
        glTextureParameteri(scratchDepthTexture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(scratchDepthTexture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(scratchDepthTexture, GL_TEXTURE_COMPARE_MODE, GL_NONE);
        scratchFbo = glCreateFramebuffers();
        glNamedFramebufferTexture(scratchFbo, GL_DEPTH_ATTACHMENT, scratchDepthTexture, 0);
        glNamedFramebufferDrawBuffer(scratchFbo, GL_NONE);
        glNamedFramebufferReadBuffer(scratchFbo, GL_NONE);
        requireComplete(scratchFbo, "D24 scratch depth framebuffer");
        glCheck("create the shared and scratch framebuffers");
    }

    private void allocateConversionProgram() {
        int vertex = 0;
        int fragment = 0;
        try {
            vertex = compileShader(GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
            fragment = compileShader(GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);
            conversionProgram = glCreateProgram();
            glAttachShader(conversionProgram, vertex);
            glAttachShader(conversionProgram, fragment);
            glLinkProgram(conversionProgram);
            if (glGetProgrami(conversionProgram, GL_LINK_STATUS) == GL_FALSE) {
                String log = glGetProgramInfoLog(conversionProgram);
                glDeleteProgram(conversionProgram);
                conversionProgram = 0;
                throw new IllegalStateException("The depth conversion program failed to link: " + log);
            }
        } finally {
            if (vertex != 0) {
                glDeleteShader(vertex);
            }
            if (fragment != 0) {
                glDeleteShader(fragment);
            }
        }
        copyDepthLocation = glGetUniformLocation(conversionProgram, "copyDepth");
        depthValueLocation = glGetUniformLocation(conversionProgram, "depthValue");
        sourceDepthLocation = glGetUniformLocation(conversionProgram, "sourceDepth");
        // The full-screen triangle needs no attributes, but core profile requires a bound vertex array to draw.
        conversionVao = glCreateVertexArrays();
        glCheck("create the depth conversion program and vertex array");
    }

    private void allocateSemaphores() {
        waitSemaphore = createSemaphorePair("GL to Vulkan");
        doneSemaphore = createSemaphorePair("Vulkan to GL");
    }

    /**
     * Creates one binary semaphore with opaque-FD export, imports it into GL and returns both handles. The initial
     * signal of {@code doneSemaphore} is produced by {@link #handInitialOwnershipToGl()}, which is what makes the
     * first GL wait legal.
     */
    private SemaphorePair createSemaphorePair(String direction) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            SemaphorePair pair = new SemaphorePair();
            int fd = -1;
            try {
                VkExportSemaphoreCreateInfo export = VkExportSemaphoreCreateInfo.calloc(stack)
                        .sType$Default()
                        .handleTypes(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
                VkSemaphoreCreateInfo create = VkSemaphoreCreateInfo.calloc(stack)
                        .sType$Default()
                        .pNext(export.address());
                LongBuffer handle = stack.mallocLong(1);
                check(vkCreateSemaphore(device, create, null, handle), "vkCreateSemaphore(" + direction + ")");
                pair.vk = handle.get(0);

                VkSemaphoreGetFdInfoKHR fdInfo = VkSemaphoreGetFdInfoKHR.calloc(stack)
                        .sType$Default()
                        .semaphore(pair.vk)
                        .handleType(VK_EXTERNAL_SEMAPHORE_HANDLE_TYPE_OPAQUE_FD_BIT);
                IntBuffer descriptor = stack.mallocInt(1);
                check(KHRExternalSemaphoreFd.vkGetSemaphoreFdKHR(device, fdInfo, descriptor),
                        "vkGetSemaphoreFdKHR(" + direction + ")");
                fd = descriptor.get(0);

                pair.gl = EXTSemaphore.glGenSemaphoresEXT();
                EXTSemaphoreFD.glImportSemaphoreFdEXT(pair.gl, EXTSemaphoreFD.GL_HANDLE_TYPE_OPAQUE_FD_EXT, fd);
                glCheck("glImportSemaphoreFdEXT(" + direction + ")");
                // A successful GL import consumes the descriptor; only a failure leaves it open here.
                fd = -1;
                return pair;
            } catch (RuntimeException | LinkageError error) {
                // Nothing is published in a field yet, so a half created pair has to be released here.
                if (pair.gl != 0) {
                    EXTSemaphore.glDeleteSemaphoresEXT(pair.gl);
                    pair.gl = 0;
                }
                if (pair.vk != 0) {
                    vkDestroySemaphore(device, pair.vk, null);
                    pair.vk = 0;
                }
                throw error;
            } finally {
                if (fd >= 0) {
                    UNISTD.close(null, fd);
                }
            }
        }
    }

    /**
     * The one-time handoff that makes the images GL's: Vulkan releases both from its queue family to
     * {@code VK_QUEUE_FAMILY_EXTERNAL} with the initial {@code UNDEFINED -> GENERAL} transition and signals
     * {@code doneSemaphore}, and GL waits on that signal. Without it the first GL blit would write to memory whose
     * layout no API ever declared, and the first GL wait would wait on a semaphore that was never signalled.
     * <p>
     * The submission is waited for on the host so the temporary command pool and fence can be destroyed here instead
     * of lingering for the lifetime of the target. The signal itself is only consumed by GL's wait, not by the fence.
     */
    private void handInitialOwnershipToGl() {
        long commandPool = 0;
        long fence = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer handle = stack.mallocLong(1);
            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                    .sType$Default()
                    .queueFamilyIndex(queueFamilyIndex)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            check(vkCreateCommandPool(device, poolInfo, null, handle), "vkCreateCommandPool(initial handoff)");
            commandPool = handle.get(0);

            VkCommandBufferAllocateInfo allocate = VkCommandBufferAllocateInfo.calloc(stack)
                    .sType$Default()
                    .commandPool(commandPool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                    .commandBufferCount(1);
            PointerBuffer buffers = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, allocate, buffers), "vkAllocateCommandBuffers(initial handoff)");
            VkCommandBuffer command = new VkCommandBuffer(buffers.get(0), device);
            check(vkBeginCommandBuffer(command, VkCommandBufferBeginInfo.calloc(stack)
                    .sType$Default()
                    .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT)), "vkBeginCommandBuffer(initial handoff)");
            recordOwnershipBarriers(command, true, true);
            check(vkEndCommandBuffer(command), "vkEndCommandBuffer(initial handoff)");

            check(vkCreateFence(device, VkFenceCreateInfo.calloc(stack).sType$Default(), null, handle),
                    "vkCreateFence(initial handoff)");
            fence = handle.get(0);
            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                    .sType$Default()
                    .pCommandBuffers(stack.pointers(command))
                    .pSignalSemaphores(stack.longs(doneSemaphore.vk));
            VulkanContext.queueSubmitCalls++;
                        check(vkQueueSubmit(queue, submit, fence), "vkQueueSubmit(initial handoff)");
            check(vkWaitForFences(device, fence, true, RETIREMENT_TIMEOUT_NANOS), "vkWaitForFences(initial handoff)");
            waitGlOn(doneSemaphore.gl, "initial Vulkan to GL ownership handoff");
            glFlush();
        } finally {
            if (fence != 0) {
                vkDestroyFence(device, fence, null);
            }
            if (commandPool != 0) {
                vkDestroyCommandPool(device, commandPool, null);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Frame operations
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Records the two image barriers that either take both images from GL ({@code acquire}) or give them back to GL
     * ({@code release}). Every barrier performs a queue family ownership transfer; it also performs a layout
     * transition whenever {@link #COLOR_RENDER_LAYOUT} / {@link #DEPTH_RENDER_LAYOUT} differ from
     * {@code VK_IMAGE_LAYOUT_GENERAL}, which they do not in the proven GENERAL configuration.
     * <p>
     * The queue family indices, the access masks and the stage masks are the ones the interop experiment ran with
     * validation enabled on both target GPUs: a release publishes the attachment writes with a source access mask of
     * the attachment accesses, an acquire claims them with that same mask as its destination, and
     * {@code VK_PIPELINE_STAGE_ALL_COMMANDS_BIT} on both sides makes the acquire visible to everything recorded later.
     */
    private void recordOwnershipBarriers(VkCommandBuffer command, boolean release, boolean initialize) {
        int colorAccess = VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT;
        int depthAccess = VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageMemoryBarrier.Buffer barriers = VkImageMemoryBarrier.calloc(2, stack);

            VkImageMemoryBarrier colorBarrier = barriers.get(0);
            colorBarrier.sType$Default()
                    .image(color.image)
                    .oldLayout(initialize ? VK_IMAGE_LAYOUT_UNDEFINED
                            : (release ? COLOR_RENDER_LAYOUT : VK_IMAGE_LAYOUT_GENERAL))
                    .newLayout(release ? VK_IMAGE_LAYOUT_GENERAL : COLOR_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(release ? queueFamilyIndex : VK_QUEUE_FAMILY_EXTERNAL)
                    .dstQueueFamilyIndex(release ? VK_QUEUE_FAMILY_EXTERNAL : queueFamilyIndex)
                    .srcAccessMask(release && !initialize ? colorAccess : 0)
                    .dstAccessMask(release ? 0 : colorAccess);
            colorBarrier.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);

            VkImageMemoryBarrier depthBarrier = barriers.get(1);
            depthBarrier.sType$Default()
                    .image(depth.image)
                    .oldLayout(initialize ? VK_IMAGE_LAYOUT_UNDEFINED
                            : (release ? DEPTH_RENDER_LAYOUT : VK_IMAGE_LAYOUT_GENERAL))
                    .newLayout(release ? VK_IMAGE_LAYOUT_GENERAL : DEPTH_RENDER_LAYOUT)
                    .srcQueueFamilyIndex(release ? queueFamilyIndex : VK_QUEUE_FAMILY_EXTERNAL)
                    .dstQueueFamilyIndex(release ? VK_QUEUE_FAMILY_EXTERNAL : queueFamilyIndex)
                    .srcAccessMask(release && !initialize ? depthAccess : 0)
                    .dstAccessMask(release ? 0 : depthAccess);
            depthBarrier.subresourceRange().set(VK_IMAGE_ASPECT_DEPTH_BIT, 0, 1, 0, 1);

            vkCmdPipelineBarrier(command, initialize ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                    VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barriers);
        }
    }

    /**
     * Runs the conversion program over the whole target, writing the sampled depth of {@code sourceTexture} into the
     * depth attachment of {@code targetFramebuffer} through {@code gl_FragDepth}. Both depth directions use it: D24
     * into D32 in {@link #beginFrame()} and D32 into the game's D24 renderbuffer in {@link #endFrame()}.
     * <p>
     * Depth testing is enabled with {@code GL_ALWAYS} and the depth mask on, because a disabled depth test would
     * discard the depth write as well. Colour writes are masked off, so the colour attachment of the target is
     * untouched and the full-screen triangle always covers the whole attachment.
     */
    private void convertDepth(int sourceTexture, int targetFramebuffer) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, targetFramebuffer);
        glViewport(0, 0, width, height);
        glDisable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDisable(GL_STENCIL_TEST);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_FRAMEBUFFER_SRGB);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
        glDepthRange(0, 1);
        glDepthFunc(GL_ALWAYS);
        if (clipControlAvailable) {
            // The vertex shader builds a full-screen triangle with z = depthValue * 2 - 1, which is only inside the
            // clip volume under the -1..1 convention; pin it instead of trusting the context's current setting.
            glClipControl(GL_LOWER_LEFT, GL_NEGATIVE_ONE_TO_ONE);
        }
        glColorMask(false, false, false, false);
        glUseProgram(conversionProgram);
        glBindVertexArray(conversionVao);
        glActiveTexture(GL_TEXTURE0);
        glBindSampler(0, 0);
        glBindTexture(GL_TEXTURE_2D, sourceTexture);
        glUniform1i(sourceDepthLocation, 0);
        glUniform1i(copyDepthLocation, 1);
        glUniform1f(depthValueLocation, 0f);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindTexture(GL_TEXTURE_2D, 0);
        glColorMask(true, true, true, true);
    }

    private void signalFromGl(int glSemaphore, String operation) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            EXTSemaphore.glSignalSemaphoreEXT(glSemaphore, stack.mallocInt(0),
                    stack.ints(color.glTexture, depth.glTexture),
                    stack.ints(GL_SHARED_LAYOUT, GL_SHARED_LAYOUT));
            glCheck(operation);
        }
    }

    private void waitGlOn(int glSemaphore, String operation) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            EXTSemaphore.glWaitSemaphoreEXT(glSemaphore, stack.mallocInt(0),
                    stack.ints(color.glTexture, depth.glTexture),
                    stack.ints(GL_SHARED_LAYOUT, GL_SHARED_LAYOUT));
            glCheck(operation);
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Contract validation
    // ---------------------------------------------------------------------------------------------------------------

    private void requireVerifiedContract(int fbo, int colorTexture, int depthRenderbuffer, String operation) {
        String violation = contractViolation(fbo, colorTexture, depthRenderbuffer, true);
        if (violation != null) {
            throw new IllegalStateException(operation + ": the game framebuffer does not match the verified RGBA8 colour /"
                    + " D24 renderbuffer / no stencil / single sample contract: " + violation);
        }
    }

    /**
     * Returns {@code null} when the framebuffer is exactly the shape the interop experiment proved (and therefore the
     * shape this bridge knows how to copy), or a sentence naming the first difference found.
     * <p>
     * {@code checkBlitBuffers} additionally reads the framebuffer's read buffer and draw buffer 0, which is what a
     * named blit actually copies from and to; it binds the framebuffer to query them, so it is only used where a state
     * snapshot is active. The format, size and sample checks need no binding at all.
     */
    private String contractViolation(int fbo, int colorTexture, int depthRenderbuffer, boolean checkBlitBuffers) {
        if (fbo == 0) {
            return "the bound draw framebuffer is the default framebuffer, which has no texture or renderbuffer attachments";
        }
        // Checked before the status query, because querying a deleted framebuffer name would raise GL_INVALID_OPERATION
        // and the captured framebuffer can be stale when a caller asks whether this target still matches.
        if (!glIsFramebuffer(fbo)) {
            return "framebuffer " + fbo + " is not a framebuffer object";
        }
        int status = glCheckNamedFramebufferStatus(fbo, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            return "framebuffer " + fbo + " is incomplete (0x" + Integer.toHexString(status) + ")";
        }
        int colorType = glGetNamedFramebufferAttachmentParameteri(fbo, GL_COLOR_ATTACHMENT0,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (colorType != GL_TEXTURE) {
            return "the colour attachment of framebuffer " + fbo + " is not a texture";
        }
        if (colorTexture == 0 || !glIsTexture(colorTexture)) {
            return "the colour attachment of framebuffer " + fbo + " is not a live texture";
        }
        int colorFormat = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_INTERNAL_FORMAT);
        if (colorFormat != GL_RGBA8) {
            return "the colour texture " + colorTexture + " has internal format 0x" + Integer.toHexString(colorFormat)
                    + " instead of RGBA8";
        }
        int colorWidth = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_WIDTH);
        int colorHeight = glGetTextureLevelParameteri(colorTexture, 0, GL_TEXTURE_HEIGHT);
        if (colorWidth != width || colorHeight != height) {
            return "the colour texture " + colorTexture + " is " + colorWidth + "x" + colorHeight + " but the target is "
                    + width + "x" + height;
        }
        int depthType = glGetNamedFramebufferAttachmentParameteri(fbo, GL_DEPTH_ATTACHMENT,
                GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE);
        if (depthType != GL_RENDERBUFFER) {
            return "the depth attachment of framebuffer " + fbo + " is not a renderbuffer";
        }
        if (depthRenderbuffer == 0 || !glIsRenderbuffer(depthRenderbuffer)) {
            return "the depth attachment of framebuffer " + fbo + " is not a live renderbuffer";
        }
        int depthFormat = glGetNamedRenderbufferParameteri(depthRenderbuffer, GL_RENDERBUFFER_INTERNAL_FORMAT);
        if (depthFormat != GL_DEPTH_COMPONENT24) {
            return "the depth renderbuffer " + depthRenderbuffer + " has internal format 0x"
                    + Integer.toHexString(depthFormat) + " instead of D24";
        }
        int stencil = glGetNamedRenderbufferParameteri(depthRenderbuffer, GL_RENDERBUFFER_STENCIL_SIZE);
        if (stencil != 0) {
            return "the depth renderbuffer " + depthRenderbuffer + " carries " + stencil
                    + " stencil bits, which this bridge does not convert";
        }
        int samples = glGetNamedRenderbufferParameteri(depthRenderbuffer, GL_RENDERBUFFER_SAMPLES);
        if (samples > 1) {
            return "the depth renderbuffer " + depthRenderbuffer + " is multisampled (" + samples
                    + " samples), which this bridge does not resolve";
        }
        int depthWidth = glGetNamedRenderbufferParameteri(depthRenderbuffer, GL_RENDERBUFFER_WIDTH);
        int depthHeight = glGetNamedRenderbufferParameteri(depthRenderbuffer, GL_RENDERBUFFER_HEIGHT);
        if (depthWidth != width || depthHeight != height) {
            return "the depth renderbuffer " + depthRenderbuffer + " is " + depthWidth + "x" + depthHeight
                    + " but the target is " + width + "x" + height;
        }
        if (checkBlitBuffers) {
            // glBlitNamedFramebuffer copies the source framebuffer's read buffer and writes the destination
            // framebuffer's draw buffer 0, so a redirection by another mod would silently copy the wrong attachment.
            glBindFramebuffer(GL_READ_FRAMEBUFFER, fbo);
            if (glGetInteger(GL_READ_BUFFER) != GL_COLOR_ATTACHMENT0) {
                return "the read buffer of framebuffer " + fbo + " is 0x"
                        + Integer.toHexString(glGetInteger(GL_READ_BUFFER)) + " instead of GL_COLOR_ATTACHMENT0";
            }
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fbo);
            if (glGetInteger(GL_DRAW_BUFFER0) != GL_COLOR_ATTACHMENT0) {
                return "the draw buffer 0 of framebuffer " + fbo + " is 0x"
                        + Integer.toHexString(glGetInteger(GL_DRAW_BUFFER0)) + " instead of GL_COLOR_ATTACHMENT0";
            }
        }
        return null;
    }

    private static void requireComplete(int fbo, String what) {
        int status = glCheckNamedFramebufferStatus(fbo, GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("The " + what + " is not complete: 0x" + Integer.toHexString(status));
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Retirement
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Waits until every GL command that touched the imported textures has completed. The texture memory belongs to
     * Vulkan, so deleting the memory objects while GL still reads them would be a use-after-free on the GPU.
     */
    private void retireGl() {
        long completion = glFenceSync(GL_SYNC_GPU_COMMANDS_COMPLETE, 0);
        glFlush();
        int waited = glClientWaitSync(completion, 0, RETIREMENT_TIMEOUT_NANOS);
        glDeleteSync(completion);
        if (waited != GL_ALREADY_SIGNALED && waited != GL_CONDITION_SATISFIED) {
            throw new IllegalStateException("GL did not retire the shared images before deletion (0x"
                    + Integer.toHexString(waited) + ")");
        }
    }

    private void deleteGlImport(ExternalImage image) {
        if (image == null) {
            return;
        }
        // Order matters: the texture references the memory object, and the memory object references the allocation.
        if (image.glTexture != 0) {
            glDeleteTextures(image.glTexture);
            image.glTexture = 0;
        }
        if (image.glMemory != 0) {
            EXTMemoryObject.glDeleteMemoryObjectsEXT(image.glMemory);
            image.glMemory = 0;
        }
    }

    private void destroyVulkanImage(ExternalImage image) {
        if (image == null) {
            return;
        }
        if (image.view != 0) {
            vkDestroyImageView(device, image.view, null);
            image.view = 0;
        }
        if (image.image != 0) {
            vkDestroyImage(device, image.image, null);
            image.image = 0;
        }
        if (image.memory != 0) {
            vkFreeMemory(device, image.memory, null);
            image.memory = 0;
        }
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------------------------

    private static int compileShader(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("The depth conversion shader failed to compile: " + log);
        }
        return shader;
    }

    /**
     * Picks a memory type for an image on {@code physicalDevice}. The bridge owns this instead of borrowing a
     * device-level helper because it is given raw handles and must not depend on how the caller wraps them.
     */
    private static int memoryType(VkPhysicalDeviceMemoryProperties properties, int allowed, int required) {
        for (int i = 0; i < properties.memoryTypeCount(); i++) {
            if ((allowed & (1 << i)) != 0 && (properties.memoryTypes(i).propertyFlags() & required) == required) {
                return i;
            }
        }
        throw new IllegalStateException("No Vulkan memory type satisfies the required property flags 0x"
                + Integer.toHexString(required));
    }

    private static void requireBridgeExtensions(GLCapabilities capabilities) {
        // The bridge uses the 4.5 named framebuffer, texture and renderbuffer queries the in-game validation fixture
        // already ran on both target GPUs; the EXT imports below are the ones the interop experiment proved.
        if (!capabilities.OpenGL45 && !capabilities.GL_ARB_direct_state_access) {
            throw new IllegalStateException("The GL/Vulkan frame bridge requires OpenGL 4.5 (or ARB_direct_state_access)"
                    + " for the named queries it validates and builds the shared FBO with");
        }
        if (!capabilities.GL_EXT_memory_object || !capabilities.GL_EXT_memory_object_fd) {
            throw new IllegalStateException("The GL/Vulkan frame bridge requires GL_EXT_memory_object and"
                    + " GL_EXT_memory_object_fd to import the shared Vulkan memory");
        }
        if (!capabilities.GL_EXT_semaphore || !capabilities.GL_EXT_semaphore_fd) {
            throw new IllegalStateException("The GL/Vulkan frame bridge requires GL_EXT_semaphore and"
                    + " GL_EXT_semaphore_fd for the binary semaphores that order GL and Vulkan");
        }
    }

    private static GLCapabilities requireGlCapabilities(String operation) {
        GLCapabilities capabilities;
        try {
            capabilities = GL.getCapabilities();
        } catch (RuntimeException | LinkageError unavailable) {
            throw new IllegalStateException(operation + " requires a current OpenGL context on the render thread with"
                    + " LWJGL GL capabilities created for it", unavailable);
        }
        if (capabilities == null) {
            throw new IllegalStateException(operation + " requires a current OpenGL context on the render thread");
        }
        return capabilities;
    }

    /**
     * Whether this thread still has LWJGL GL capabilities. Only {@link #delete()} uses this: it must stay usable while
     * the game shuts down, where the context may already be gone, and destroying a context already destroyed the
     * objects it owned.
     */
    private static boolean glContextAvailable() {
        try {
            return GL.getCapabilities() != null;
        } catch (RuntimeException | LinkageError unavailable) {
            return false;
        }
    }

    /**
     * Discards errors the game left behind, so a stale error from another mod's GL use is never reported as a failure
     * of this bridge. Errors produced by the calls below are still checked explicitly.
     */
    private static void drainGlErrors() {
        for (int i = 0; i < MAX_DRAINED_GL_ERRORS && glGetError() != GL_NO_ERROR; i++) {
            // Discard one pending error per iteration.
        }
    }

    private static void glCheck(String operation) {
        int error = glGetError();
        if (error != GL_NO_ERROR) {
            throw new IllegalStateException(operation + ": GL error 0x" + Integer.toHexString(error));
        }
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned VkResult " + result);
        }
    }

    private void checkAlive() {
        if (deleted) {
            throw new IllegalStateException("This VulkanFrameTarget has already been deleted");
        }
    }

    /** One shared image: the Vulkan handles, the GL import of its memory and the GL texture wrapping it. */
    private static final class ExternalImage {
        final int format;
        final int aspect;
        long image;
        long memory;
        long view;
        long allocationBytes;
        int glMemory;
        int glTexture;

        ExternalImage(int format, int aspect) {
            this.format = format;
            this.aspect = aspect;
        }
    }

    /** One binary semaphore: the Vulkan handle the caller submits with and the GL import GL signals or waits on. */
    private static final class SemaphorePair {
        long vk;
        int gl;
    }

    /**
     * Saves and restores every piece of GL state this bridge changes, so the game never observes a modified context.
     * Restoring the draw and read framebuffer bindings is also what leaves the game's framebuffer bound exactly as the
     * caller had it.
     */
    private static final class GlState implements AutoCloseable {
        private static final int[] TOGGLED_STATES = {
            GL_DEPTH_TEST, GL_BLEND, GL_CULL_FACE, GL_STENCIL_TEST, GL_SCISSOR_TEST, GL_FRAMEBUFFER_SRGB,
        };

        private final int program = glGetInteger(GL_CURRENT_PROGRAM);
        private final int vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        private final int drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        private final int readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int depthFunction = glGetInteger(GL_DEPTH_FUNC);
        private final boolean depthWrite = glGetBoolean(GL_DEPTH_WRITEMASK);
        private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int texture0;
        private final int sampler0 = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        private final boolean clipControlAvailable;
        private final int clipOrigin;
        private final int clipDepthMode;
        private final int[] viewport = new int[4];
        private final int[] scissor = new int[4];
        private final boolean[] colorWrite = new boolean[4];
        private final boolean[] toggled = new boolean[TOGGLED_STATES.length];
        private final double[] depthRange = new double[2];

        GlState() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer values = stack.mallocInt(4);
                glGetIntegerv(GL_VIEWPORT, values);
                values.get(viewport);
                values.clear();
                glGetIntegerv(GL_SCISSOR_BOX, values);
                values.get(scissor);
                ByteBuffer mask = stack.malloc(4);
                glGetBooleanv(GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) {
                    colorWrite[i] = mask.get(i) != 0;
                }
                DoubleBuffer range = stack.mallocDouble(2);
                glGetDoublev(GL_DEPTH_RANGE, range);
                range.get(depthRange);
            }
            for (int i = 0; i < TOGGLED_STATES.length; i++) {
                toggled[i] = glIsEnabled(TOGGLED_STATES[i]);
            }
            GLCapabilities capabilities = GL.getCapabilities();
            clipControlAvailable = capabilities != null
                    && (capabilities.OpenGL45 || capabilities.GL_ARB_clip_control);
            clipOrigin = clipControlAvailable ? glGetInteger(GL_CLIP_ORIGIN) : GL_LOWER_LEFT;
            clipDepthMode = clipControlAvailable ? glGetInteger(GL_CLIP_DEPTH_MODE) : GL_NEGATIVE_ONE_TO_ONE;
            glActiveTexture(GL_TEXTURE0);
            texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(activeTexture);
        }

        @Override
        public void close() {
            glUseProgram(program);
            glBindVertexArray(vertexArray);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
            glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            glScissor(scissor[0], scissor[1], scissor[2], scissor[3]);
            glDepthFunc(depthFunction);
            glDepthMask(depthWrite);
            glDepthRange(depthRange[0], depthRange[1]);
            if (clipControlAvailable) {
                glClipControl(clipOrigin, clipDepthMode);
            }
            glColorMask(colorWrite[0], colorWrite[1], colorWrite[2], colorWrite[3]);
            for (int i = 0; i < TOGGLED_STATES.length; i++) {
                if (toggled[i]) {
                    glEnable(TOGGLED_STATES[i]);
                } else {
                    glDisable(TOGGLED_STATES[i]);
                }
            }
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texture0);
            glBindSampler(0, sampler0);
            glActiveTexture(activeTexture);
            // Deliberately not error-checked: this runs on the failure path too, where an informative exception from
            // beginFrame()/endFrame() is already propagating and a second one from the restore would hide it. The
            // calls above only replay values read from GL itself, and any error flag they set is drained before the
            // next frame's own work is checked.
        }
    }
}
