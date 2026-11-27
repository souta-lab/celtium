package org.soutalab.celtium.core.shader;

import java.util.List;
import java.util.Objects;

/**
 * The attachment formats a terrain stage program renders into, as Vulkan {@code VkFormat} values:
 * the colour formats in attachment order, then the depth and stencil formats. Pipelines are created
 * against exactly this key ({@code dynamic_rendering} has no render-pass object to carry the
 * information), and two programs that share a key can share pipeline-cache entries.
 * <p>
 * The values are carried opaquely: which integer means which format is the Vulkan HAL's knowledge,
 * the contract only fixes the shape. {@link #FORMAT_UNDEFINED} marks an absent depth/stencil.
 */
public record AttachmentFormatKey(List<Integer> colorFormats, int depthFormat, int stencilFormat) {

    /** VK_FORMAT_UNDEFINED: the attachment does not exist. */
    public static final int FORMAT_UNDEFINED = 0;

    public AttachmentFormatKey {
        colorFormats = List.copyOf(Objects.requireNonNull(colorFormats, "colorFormats"));
    }

    public static AttachmentFormatKey of(int[] colorFormats, int depthFormat, int stencilFormat) {
        Objects.requireNonNull(colorFormats, "colorFormats");
        Integer[] boxed = new Integer[colorFormats.length];
        for (int i = 0; i < colorFormats.length; i++) {
            boxed[i] = colorFormats[i];
        }
        return new AttachmentFormatKey(List.of(boxed), depthFormat, stencilFormat);
    }

    public boolean hasDepth() {
        return depthFormat != FORMAT_UNDEFINED;
    }

    public boolean hasStencil() {
        return stencilFormat != FORMAT_UNDEFINED;
    }

    public int colorAttachmentCount() {
        return colorFormats.size();
    }
}
