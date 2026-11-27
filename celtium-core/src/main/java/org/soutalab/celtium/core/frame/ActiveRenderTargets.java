package org.soutalab.celtium.core.frame;

import java.util.List;
import java.util.Objects;

/**
 * The concrete attachments a {@code dynamic_rendering} pass renders into right now: opaque image-view
 * handles owned by the Vulkan HAL ({@code TransientAttachmentSlab} or {@code PresentSwapchain}), the
 * formats those views were created with (Vulkan {@code VkFormat} values, carried opaquely - the core
 * contract never interprets them), and the render area. This is the celtium-native replacement for
 * every framebuffer-name indirection the old GLSM-shaped interfaces carried: the decision about which
 * images exist belongs to the HAL's target pool alone (design section 2.3, gap G-2).
 */
public record ActiveRenderTargets(
        List<RenderTargetView> colorAttachments,
        RenderTargetView depthAttachment,
        int width,
        int height) {

    public ActiveRenderTargets {
        colorAttachments = List.copyOf(Objects.requireNonNull(colorAttachments, "colorAttachments"));
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("render area must be positive: " + width + "x" + height);
        }
    }

    /** One attachment of an {@link ActiveRenderTargets}: a view handle, its format, and its load/store ops. */
    public record RenderTargetView(long imageViewHandle, int format, LoadOp loadOp, StoreOp storeOp) {
        public RenderTargetView {
            Objects.requireNonNull(loadOp, "loadOp");
            Objects.requireNonNull(storeOp, "storeOp");
        }
    }

    /** What happens to an attachment's previous contents when a pass begins (VkAttachmentLoadOp). */
    public enum LoadOp {
        LOAD, CLEAR, DONT_CARE
    }

    /** What happens to an attachment's contents when a pass ends (VkAttachmentStoreOp). */
    public enum StoreOp {
        STORE, DONT_CARE
    }
}
