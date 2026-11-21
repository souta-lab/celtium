package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.CommandList;

/**
 * Minimal binary-compatibility shell retained for Demonica Tier A bytecode references
 * ({@code ensureCapacity(CommandList, int)}). Celtium renders terrain exclusively through Vulkan.
 */
public class SharedQuadIndexBuffer {
    public void ensureCapacity(CommandList commandList, int elementCount) {
        // No-op: Vulkan terrain passes own their shared quad index buffer in TerrainRenderer.
    }
}
