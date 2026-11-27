/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.soutalab.celtium.shader.model.ShaderStorageInfo;
import org.soutalab.celtium.shader.shaderpack.FilledIndirectPointer;
import org.soutalab.celtium.shader.shaderpack.IndirectPointer;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The pack's shader storage buffers: one GPU buffer per {@code bufferObject.<index>} declared
 * in shaders.properties, sized absolutely or relative to the view extent. This is also the
 * resolution seat for {@link FilledIndirectPointer}: the pack model deliberately shipped the
 * record without a factory because resolution belongs to the renderer's own buffer
 * allocation - here, a declared index becomes an arena slot with a real {@code VkBuffer}, and
 * a filled pointer is that slot plus the pack-declared byte offset, ready for
 * {@code vkCmdDispatchIndirect}.
 */
final class PackSsboArena implements AutoCloseable {

    /** Size for an indirect pointer whose buffer index the pack never declared. */
    private static final long UNDECLARED_BUFFER_BYTES = 4096L;

    private final VulkanContext context;
    private final Map<Integer, PackGpuBuffer> buffers = new LinkedHashMap<>();
    private final Map<Integer, ShaderStorageInfo> declared;

    PackSsboArena(VulkanContext context, Int2ObjectMap<ShaderStorageInfo> bufferObjects) {
        this.context = context;
        this.declared = new LinkedHashMap<>();
        if (bufferObjects != null) {
            bufferObjects.forEach((index, info) -> declared.put(index, info));
        }
    }

    /** Creates (or recreates, on resize) every declared buffer. Contents do not survive a resize. */
    void allocate(int viewWidth, int viewHeight) {
        close();
        for (Map.Entry<Integer, ShaderStorageInfo> entry : declared.entrySet()) {
            buffers.put(entry.getKey(), create(entry.getValue(), viewWidth, viewHeight));
        }
    }

    private PackGpuBuffer create(ShaderStorageInfo info, int viewWidth, int viewHeight) {
        long size = info.relative()
                ? Math.max(16L, (long) (viewWidth * info.scaleX()) * Math.max(1L, (long) (viewHeight * info.scaleY())))
                : Math.max(16L, info.size());
        PackGpuBuffer buffer = new PackGpuBuffer(context.physicalDevice(), context.device(),
                size, PackGpuBuffer.USAGE_STORAGE | PackGpuBuffer.USAGE_INDIRECT);
        buffer.mapped().fill((byte) 0);
        return buffer;
    }

    /**
     * Resolves a pack-declared indirect pointer against this arena: the returned pointer's
     * buffer index names an arena slot that exists (an undeclared index gets a scratch slot),
     * and its offset is the pack-declared byte offset into that slot.
     */
    FilledIndirectPointer fill(IndirectPointer pointer, int viewWidth, int viewHeight) {
        int index = pointer.buffer();
        if (!buffers.containsKey(index)) {
            PackGpuBuffer scratch = new PackGpuBuffer(context.physicalDevice(), context.device(),
                    UNDECLARED_BUFFER_BYTES,
                    PackGpuBuffer.USAGE_STORAGE | PackGpuBuffer.USAGE_INDIRECT);
            scratch.mapped().fill((byte) 0);
            buffers.put(index, scratch);
        }
        return new FilledIndirectPointer(index, pointer.offset());
    }

    /** The VkBuffer handle of an arena slot, or 0 when the slot does not exist. */
    long bufferHandle(int index) {
        PackGpuBuffer buffer = buffers.get(index);
        return buffer == null ? 0L : buffer.bufferHandle();
    }

    /** The declared buffer indices, ascending: the order storage-buffer bindings resolve in. */
    java.util.List<Integer> declaredIndices() {
        java.util.List<Integer> indices = new java.util.ArrayList<>(declared.keySet());
        java.util.Collections.sort(indices);
        return indices;
    }

    @Override
    public void close() {
        for (PackGpuBuffer buffer : buffers.values()) {
            buffer.close();
        }
        buffers.clear();
    }
}
