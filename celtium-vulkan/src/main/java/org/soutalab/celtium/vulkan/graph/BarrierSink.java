package org.soutalab.celtium.vulkan.graph;

import java.util.List;

/**
 * Where the executor sends a pass boundary's synthesized barriers. The HAL side implements this
 * over its command buffer (translating {@link GraphBarrier} into {@code VkImageMemoryBarrier2} /
 * {@code VkMemoryBarrier2}); tests implement it as a recorder. The context is the same opaque
 * object the pass recorders receive.
 */
public interface BarrierSink {

    void applyBarriers(List<GraphBarrier> barriers, Object context);
}
