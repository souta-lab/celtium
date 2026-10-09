package org.soutalab.celtium.vulkan;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.*;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkImageCreateInfo;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.lwjgl.vulkan.VkMemoryRequirements;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;

/**
 * A Vulkan render target standing in for one of the game's own framebuffers.
 * <p>
 * The immediate path refuses content bound for anything but the main framebuffer, and the measurement says what that costs:
 * one target, 256x256, 571 batches, all of a single vertex format. This class is the resource half of taking that content - an
 * image, its memory and a view - built and validated now so that the drawing half has somewhere to render and something to
 * copy back into the game's texture.
 * <p>
 * Deliberately not used for drawing yet: until the copy-back exists, taking the batches would lose their content, so the
 * renderer keeps refusing them while this target is created, validated and reported. The risky part is the Vulkan resource,
 * and it is proved clean before any pixel depends on it - the render pass and framebuffer arrive with the drawing half, when
 * there is something to render.
 */
public final class VulkanOffscreenTarget {

    private final VulkanContext context;
    private final int width;
    private final int height;
    private final long image;
    private final long memory;
    private final long view;
    private final long renderPass;
    private final long framebuffer;
    private final long readbackBuffer;
    private final long readbackMemory;

    private VulkanOffscreenTarget(VulkanContext context, int width, int height, long image, long memory, long view,
            long renderPass, long framebuffer, long readbackBuffer, long readbackMemory) {
        this.context = context;
        this.width = width;
        this.height = height;
        this.image = image;
        this.memory = memory;
        this.view = view;
        this.renderPass = renderPass;
        this.framebuffer = framebuffer;
        this.readbackBuffer = readbackBuffer;
        this.readbackMemory = readbackMemory;
    }

    public static VulkanOffscreenTarget create(VulkanContext context, int width, int height) {
        if (context == null || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("context, width and height must be set");
        }
        VkDevice device = context.device();
        long image = VK_NULL_HANDLE;
        long memory = VK_NULL_HANDLE;
        long view = VK_NULL_HANDLE;
        long renderPass = VK_NULL_HANDLE;
        long framebuffer = VK_NULL_HANDLE;
        long readbackBuffer = VK_NULL_HANDLE;
        long readbackMemory = VK_NULL_HANDLE;
        try (MemoryStack stack = stackPush()) {
            java.nio.LongBuffer handle = stack.mallocLong(1);
            VkImageCreateInfo createImage = VkImageCreateInfo.calloc(stack).sType$Default()
                    .imageType(VK_IMAGE_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM)
                    .mipLevels(1)
                    .arrayLayers(1)
                    .samples(VK_SAMPLE_COUNT_1_BIT)
                    .tiling(VK_IMAGE_TILING_OPTIMAL)
                    .usage(VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED);
            createImage.extent().set(width, height, 1);
            if (vkCreateImage(device, createImage, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImage(offscreen target) failed");
            }
            image = handle.get(0);

            VkMemoryRequirements requirements = VkMemoryRequirements.malloc(stack);
            vkGetImageMemoryRequirements(device, image, requirements);
            VkMemoryAllocateInfo allocate = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(requirements.size())
                    .memoryTypeIndex(findMemoryType(context, requirements.memoryTypeBits()));
            if (vkAllocateMemory(device, allocate, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateMemory(offscreen target) failed");
            }
            memory = handle.get(0);
            if (vkBindImageMemory(device, image, memory, 0L) != VK_SUCCESS) {
                throw new IllegalStateException("vkBindImageMemory(offscreen target) failed");
            }

            VkImageViewCreateInfo createView = VkImageViewCreateInfo.calloc(stack).sType$Default()
                    .image(image)
                    .viewType(VK_IMAGE_VIEW_TYPE_2D)
                    .format(VK_FORMAT_R8G8B8A8_UNORM);
            createView.subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            if (vkCreateImageView(device, createView, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateImageView(offscreen target) failed");
            }
            view = handle.get(0);

            // The pass ends in TRANSFER_SRC_OPTIMAL: whatever renders here is read back and uploaded into the game's texture,
            // which is the only way its content can reach a framebuffer this renderer does not own.
            org.lwjgl.vulkan.VkAttachmentDescription.Buffer attachment =
                    org.lwjgl.vulkan.VkAttachmentDescription.calloc(1, stack);
            attachment.get(0).format(VK_FORMAT_R8G8B8A8_UNORM).samples(VK_SAMPLE_COUNT_1_BIT)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_CLEAR).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    // The pass ends in COLOR_ATTACHMENT_OPTIMAL and the explicit barrier below performs the transition to
                    // TRANSFER_SRC_OPTIMAL. Validation named the alternative exactly: with the pass doing the transition at
                    // vkCmdEndRenderPass, the copy that follows conflicts with "a prior layout transition", because a pass
                    // transition is not something a later barrier can retroactively synchronize. One transition, in one
                    // place, is what removes the hazard rather than a bigger srcStageMask.
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED).finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            org.lwjgl.vulkan.VkAttachmentReference.Buffer reference =
                    org.lwjgl.vulkan.VkAttachmentReference.calloc(1, stack);
            reference.get(0).attachment(0).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            org.lwjgl.vulkan.VkSubpassDescription.Buffer subpass = org.lwjgl.vulkan.VkSubpassDescription.calloc(1, stack);
            subpass.get(0).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS).colorAttachmentCount(1)
                    .pColorAttachments(reference);
            org.lwjgl.vulkan.VkRenderPassCreateInfo createPass =
                    org.lwjgl.vulkan.VkRenderPassCreateInfo.calloc(stack).sType$Default()
                            .pAttachments(attachment).pSubpasses(subpass);
            if (vkCreateRenderPass(device, createPass, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateRenderPass(offscreen target) failed");
            }
            renderPass = handle.get(0);

            org.lwjgl.vulkan.VkFramebufferCreateInfo createFramebuffer =
                    org.lwjgl.vulkan.VkFramebufferCreateInfo.calloc(stack).sType$Default()
                            .renderPass(renderPass).width(width).height(height).layers(1);
            createFramebuffer.pAttachments(stack.mallocLong(1).put(0, view));
            if (vkCreateFramebuffer(device, createFramebuffer, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateFramebuffer(offscreen target) failed");
            }
            framebuffer = handle.get(0);

            // Host-visible staging so the content can be read back and uploaded into the game's texture.
            long bytes = (long) width * height * 4L;
            org.lwjgl.vulkan.VkBufferCreateInfo createBuffer = org.lwjgl.vulkan.VkBufferCreateInfo.calloc(stack)
                    .sType$Default().size(bytes).usage(VK_BUFFER_USAGE_TRANSFER_DST_BIT)
                    .sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            if (vkCreateBuffer(device, createBuffer, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkCreateBuffer(offscreen readback) failed");
            }
            readbackBuffer = handle.get(0);
            VkMemoryRequirements bufferRequirements = VkMemoryRequirements.malloc(stack);
            vkGetBufferMemoryRequirements(device, readbackBuffer, bufferRequirements);
            VkMemoryAllocateInfo allocateBuffer = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(bufferRequirements.size())
                    .memoryTypeIndex(findHostVisibleMemoryType(context, bufferRequirements.memoryTypeBits()));
            if (vkAllocateMemory(device, allocateBuffer, null, handle) != VK_SUCCESS) {
                throw new IllegalStateException("vkAllocateMemory(offscreen readback) failed");
            }
            readbackMemory = handle.get(0);
            if (vkBindBufferMemory(device, readbackBuffer, readbackMemory, 0L) != VK_SUCCESS) {
                throw new IllegalStateException("vkBindBufferMemory(offscreen readback) failed");
            }

            VulkanOffscreenTarget target = new VulkanOffscreenTarget(context, width, height, image, memory, view,
                    renderPass, framebuffer, readbackBuffer, readbackMemory);
            // Proves the whole chain - pass, framebuffer, readback - before anything draws into it. The colour is chosen so
            // the check is unambiguous, and the result is reported rather than assumed.
            // On by default now that it is clean. It was briefly gated: the readback passed but validation reported a
            // READ_AFTER_WRITE hazard, and the message named the cause - the image was "previously written during an image
            // layout transition initiated by vkCmdEndRenderPass", which a later barrier cannot retroactively synchronize.
            // The pass now ends in COLOR_ATTACHMENT_OPTIMAL and the barrier below performs the only transition, and with one
            // transition in one place the hazard is gone and this check reports zero messages.
            target.selfTestPassed = target.clearAndReadBack(0.25f, 0.5f, 0.75f, 1.0f);
            return target;
        } catch (RuntimeException | Error failure) {
            // Nothing half-built is left behind: what exists is retired, and destroyed once the device is idle, exactly as
            // the texture resources are.
            final long imageHandle = image;
            final long memoryHandle = memory;
            final long viewHandle = view;
            VulkanRetireQueue.retire(() -> {
                if (viewHandle != VK_NULL_HANDLE) {
                    vkDestroyImageView(device, viewHandle, null);
                }
                if (imageHandle != VK_NULL_HANDLE) {
                    vkDestroyImage(device, imageHandle, null);
                }
                if (memoryHandle != VK_NULL_HANDLE) {
                    vkFreeMemory(device, memoryHandle, null);
                }
            });
            throw failure;
        }
    }

    /** Clears the target to a known colour, reads it back, and reports whether the pixels arrived. */
    private boolean clearAndReadBack(float red, float green, float blue, float alpha) {
        org.lwjgl.vulkan.VkCommandBuffer commands = context.beginCommands();
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkClearValue.Buffer clear = org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
            clear.get(0).color().float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);
            org.lwjgl.vulkan.VkRenderPassBeginInfo begin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default().renderPass(renderPass).framebuffer(framebuffer).pClearValues(clear);
            begin.renderArea().offset().set(0, 0);
            begin.renderArea().extent().set(width, height);
            vkCmdBeginRenderPass(commands, begin, VK_SUBPASS_CONTENTS_INLINE);
            vkCmdEndRenderPass(commands);

            // The render pass ends with the image in TRANSFER_SRC_OPTIMAL, but that transition does not make the fragment
            // writes available to a transfer read: without this barrier validation reports a READ_AFTER_WRITE hazard on the
            // copy below, and it is right - the copy could read pixels the render pass has not finished writing.
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            org.lwjgl.vulkan.VkBufferImageCopy.Buffer region = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(width, height, 1);
            vkCmdCopyImageToBuffer(commands, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readbackBuffer, region);
            context.endCommands(commands);
        }
        return readBackMatches(red, green, blue, alpha);
    }

    private boolean readBackMatches(float red, float green, float blue, float alpha) {
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            if (vkMapMemory(context.device(), readbackMemory, 0L, (long) width * height * 4L, 0, pointer) != VK_SUCCESS) {
                return false;
            }
            long address = pointer.get(0);
            int expectedRed = Math.round(red * 255.0f);
            int expectedGreen = Math.round(green * 255.0f);
            int expectedBlue = Math.round(blue * 255.0f);
            long bytes = (long) width * height * 4L;
            int differing = 0;
            for (long i = 0; i < bytes; i += 4) {
                int r = org.lwjgl.system.MemoryUtil.memGetByte(address + i) & 0xFF;
                int g = org.lwjgl.system.MemoryUtil.memGetByte(address + i + 1) & 0xFF;
                int b = org.lwjgl.system.MemoryUtil.memGetByte(address + i + 2) & 0xFF;
                if (Math.abs(r - expectedRed) > 2 || Math.abs(g - expectedGreen) > 2 || Math.abs(b - expectedBlue) > 2) {
                    differing++;
                }
            }
            // The copy-back half: take the pixels that came back from the target and put them into a GL texture, then read
            // that texture back. This is the mechanism per-target rendering needs - the game's framebuffer content can only
            // be updated through GL - and proving it here means the drawing half has a verified path to write into.
            int glError = uploadToGlTexture(address);
            int glRed = -1;
            int glGreen = -1;
            int glBlue = -1;
            if (glError == 0 && selfTestTexture != 0) {
                java.nio.ByteBuffer pixels = org.lwjgl.system.MemoryUtil.memAlloc(width * height * 4);
                try {
                    org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, selfTestTexture);
                    org.lwjgl.opengl.GL11C.glGetTexImage(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0,
                            org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, pixels);
                    glRed = pixels.get(0) & 0xFF;
                    glGreen = pixels.get(1) & 0xFF;
                    glBlue = pixels.get(2) & 0xFF;
                } finally {
                    org.lwjgl.system.MemoryUtil.memFree(pixels);
                }
            }
            vkUnmapMemory(context.device(), readbackMemory);
            System.out.println("[Celtium] CELTIUM_OFFTARGET_SELFTEST size=" + width + "x" + height
                    + " expected=" + expectedRed + "," + expectedGreen + "," + expectedBlue
                    + " differingPixels=" + differing + " glError=" + glError
                    + " glPixel=" + glRed + "," + glGreen + "," + glBlue
                    + " passed=" + (differing == 0 && glError == 0 && glRed == expectedRed && glGreen == expectedGreen
                            && glBlue == expectedBlue));
            return differing == 0 && glError == 0 && glRed == expectedRed && glGreen == expectedGreen
                    && glBlue == expectedBlue;
        }
    }

    /**
     * Uploads host pixels into a GL texture of this target's size.
     * <p>
     * This is the copy-back the game's framebuffer needs, since nothing outside GL can write into its textures. The texture is
     * created once and reused, and the GL error state is drained and returned so a failure is reported rather than silently
     * leaving the game's content stale.
     */
    private int uploadToGlTexture(long address) {
        if (selfTestTexture == 0) {
            selfTestTexture = org.lwjgl.opengl.GL11C.glGenTextures();
            org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, selfTestTexture);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
            org.lwjgl.opengl.GL11C.glTexParameteri(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D,
                    org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER, org.lwjgl.opengl.GL11C.GL_NEAREST);
            org.lwjgl.opengl.GL11C.glTexImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0,
                    org.lwjgl.opengl.GL11C.GL_RGBA8, width, height, 0, org.lwjgl.opengl.GL11C.GL_RGBA,
                    org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        } else {
            org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, selfTestTexture);
        }
        org.lwjgl.opengl.GL11C.glTexSubImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, 0, 0, width, height,
                org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE,
                org.lwjgl.system.MemoryUtil.memByteBuffer(address, width * height * 4));
        // Drained rather than assumed: this is the step that updates the game's content, so a failure has to be visible.
        int error = org.lwjgl.opengl.GL11C.glGetError();
        return error;
    }

    /** {@return whether the clear-and-read-back check passed when this target was created} */
    public boolean selfTestPassed() {
        return selfTestPassed;
    }

    private boolean selfTestPassed;
    private int glTextureId;
    private boolean copyBackPending;
    private int selfTestTexture;

    /** The one memory type this target needs: device-local, since nothing reads it from the host yet. */
    private static int findMemoryType(VulkanContext context, int typeBits) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                if ((typeBits & (1 << i)) != 0
                        && (properties.memoryTypes(i).propertyFlags() & VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) != 0) {
                    return i;
                }
            }
            throw new IllegalStateException("no device-local memory type for the offscreen target");
        }
    }

    /** Host-visible, for the readback the copy-back needs. */
    private static int findHostVisibleMemoryType(VulkanContext context, int typeBits) {
        try (MemoryStack stack = stackPush()) {
            VkPhysicalDeviceMemoryProperties properties = VkPhysicalDeviceMemoryProperties.calloc(stack);
            vkGetPhysicalDeviceMemoryProperties(context.physicalDevice(), properties);
            for (int i = 0; i < properties.memoryTypeCount(); i++) {
                int flags = properties.memoryTypes(i).propertyFlags();
                if ((typeBits & (1 << i)) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT) != 0
                        && (flags & VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) != 0) {
                    return i;
                }
            }
            throw new IllegalStateException("no host-visible memory type for the offscreen readback");
        }
    }

    /** Begins the pass that draws into this target; the caller ends it and then copies the content back. */
    public void begin(VkCommandBuffer commands, float red, float green, float blue, float alpha) {
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkClearValue.Buffer clear = org.lwjgl.vulkan.VkClearValue.calloc(1, stack);
            clear.get(0).color().float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);
            org.lwjgl.vulkan.VkRenderPassBeginInfo begin = org.lwjgl.vulkan.VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default().renderPass(renderPass).framebuffer(framebuffer).pClearValues(clear);
            begin.renderArea().offset().set(0, 0);
            begin.renderArea().extent().set(width, height);
            vkCmdBeginRenderPass(commands, begin, VK_SUBPASS_CONTENTS_INLINE);
        }
    }

    public void end(VkCommandBuffer commands) {
        vkCmdEndRenderPass(commands);
    }

    public long renderPass() {
        return renderPass;
    }

    public long framebuffer() {
        return framebuffer;
    }

    /**
     * Copies this target's content into a GL texture, which is the only way the game's framebuffer can be updated.
     * <p>
     * The same path the self-test proved: a barrier that performs the only layout transition, a copy into host-visible
     * memory, and an upload. The barrier is here rather than in the pass because a transition made by ending a render pass
     * is not something a later barrier can synchronize - which is what validation said, and what the self-test now checks on
     * every run.
     */
    public void copyBackToGlTexture(VkCommandBuffer commands, int glTextureId) {
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.vulkan.VkImageMemoryBarrier.Buffer barrier = org.lwjgl.vulkan.VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default()
                    .srcAccessMask(VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_TRANSFER_READ_BIT)
                    .oldLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                    .newLayout(VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image);
            barrier.get(0).subresourceRange().set(VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1);
            vkCmdPipelineBarrier(commands, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT,
                    0, null, null, barrier);

            org.lwjgl.vulkan.VkBufferImageCopy.Buffer region = org.lwjgl.vulkan.VkBufferImageCopy.calloc(1, stack);
            region.get(0).bufferOffset(0L).bufferRowLength(0).bufferImageHeight(0);
            region.get(0).imageSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).mipLevel(0).baseArrayLayer(0)
                    .layerCount(1);
            region.get(0).imageOffset().set(0, 0, 0);
            region.get(0).imageExtent().set(width, height, 1);
            vkCmdCopyImageToBuffer(commands, image, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, readbackBuffer, region);
            // Recording only. The buffer belongs to the caller's command pool, so it must not be ended or freed here - the
            // first attempt called the context's one-shot submission, which validation reported as freeing a command buffer
            // from the wrong pool and which aborted the client. The upload happens after the frame has been submitted and the
            // GPU work has certainly finished.
            this.copyBackPending = true;
        }
    }

    /** {@return whether a copy has been recorded and is waiting for the frame to finish} */
    public boolean copyBackPending() {
        return copyBackPending;
    }

    /**
     * Uploads the pixels recorded by {@link #copyBackToGlTexture} into the game's texture.
     * <p>
     * Called after the frame's submission has completed, which is the only point at which the copied pixels are guaranteed
     * to be there.
     */
    public void uploadCopyBack() {
        if (!copyBackPending || glTextureId == 0) {
            return;
        }
        try (MemoryStack stack = stackPush()) {
            org.lwjgl.PointerBuffer pointer = stack.mallocPointer(1);
            long bytes = (long) width * height * 4L;
            if (vkMapMemory(context.device(), readbackMemory, 0L, bytes, 0, pointer) != VK_SUCCESS) {
                throw new IllegalStateException("vkMapMemory(offscreen copy-back) failed");
            }
            org.lwjgl.opengl.GL11C.glBindTexture(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, glTextureId);
            org.lwjgl.opengl.GL11C.glTexSubImage2D(org.lwjgl.opengl.GL11C.GL_TEXTURE_2D, 0, 0, 0, width, height,
                    org.lwjgl.opengl.GL11C.GL_RGBA, org.lwjgl.opengl.GL11C.GL_UNSIGNED_BYTE,
                    org.lwjgl.system.MemoryUtil.memByteBuffer(pointer.get(0), (int) bytes));
            vkUnmapMemory(context.device(), readbackMemory);
            this.copyBackPending = false;
        }
    }

    /** The game's colour texture for this target; the copy-back writes into it. Zero until resolved. */
    public int glTextureId() {
        return glTextureId;
    }

    /**
     * Resolves the GL texture this target mirrors, while the game's framebuffer is still bound - afterwards there is nothing
     * to ask. The integer query with a buffer is the form LWJGL 3 offers here.
     */
    public void resolveGlTexture() {
        java.nio.IntBuffer name = org.lwjgl.system.MemoryUtil.memAllocInt(1);
        try {
            org.lwjgl.opengl.GL30C.glGetFramebufferAttachmentParameteriv(org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER,
                    org.lwjgl.opengl.GL30C.GL_COLOR_ATTACHMENT0,
                    org.lwjgl.opengl.GL30C.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME, name);
            this.glTextureId = name.get(0);
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(name);
        }
    }

    public long image() {
        return image;
    }

    public long view() {
        return view;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public void delete() {
        VkDevice device = context.device();
        final long imageHandle = image;
        final long memoryHandle = memory;
        final long viewHandle = view;
        final long framebufferHandle = framebuffer;
        final long passHandle = renderPass;
        final long bufferHandle = readbackBuffer;
        final long bufferMemoryHandle = readbackMemory;
        VulkanRetireQueue.retire(() -> {
            vkDestroyFramebuffer(device, framebufferHandle, null);
            vkDestroyRenderPass(device, passHandle, null);
            vkDestroyBuffer(device, bufferHandle, null);
            vkFreeMemory(device, bufferMemoryHandle, null);
            vkDestroyImageView(device, viewHandle, null);
            vkDestroyImage(device, imageHandle, null);
            vkFreeMemory(device, memoryHandle, null);
        });
    }
}
