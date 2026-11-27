/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.soutalab.celtium.core.shader.PackExecutionPlan;

import java.lang.foreign.MemorySegment;

/**
 * The pack uniform block: one host-visible buffer with one slot per frame in flight, each slot
 * exactly the plan's {@code PackUniformLayout} block. The program set fills a slot through
 * {@code updateFrameUniforms}; the weather feed (below) then writes the weather contract
 * fields into the same slot before any pass records.
 */
final class PackUniformBuffer implements AutoCloseable {

    private final PackGpuBuffer buffer;
    private final int blockSizeBytes;
    private final int slots;
    private final long slotStride;

    PackUniformBuffer(org.lwjgl.vulkan.VkPhysicalDevice physicalDevice,
                      org.lwjgl.vulkan.VkDevice device,
                      PackExecutionPlan plan, int slots) {
        this.blockSizeBytes = Math.max(plan.uniformLayout().blockSizeBytes(), 16);
        this.slots = slots;
        // UBO ranges are bound per slot; keep slots 256-byte aligned for drivers that require
        // aligned dynamic offsets even though this executor binds with a plain offset.
        this.slotStride = align(blockSizeBytes, 256L);
        this.buffer = new PackGpuBuffer(physicalDevice, device,
                slotStride * slots, PackGpuBuffer.USAGE_UNIFORM);
    }

    private static long align(long value, long alignment) {
        return (value + alignment - 1L) & ~(alignment - 1L);
    }

    int blockSizeBytes() {
        return blockSizeBytes;
    }

    long bufferHandle() {
        return buffer.bufferHandle();
    }

    long slotOffset(int slot) {
        return slotStride * Math.floorMod(slot, slots);
    }

    /** The writable UBO view of one frame slot, sized exactly to the plan block. */
    MemorySegment slotSegment(int slot) {
        return buffer.slice(slotOffset(slot), blockSizeBytes);
    }

    @Override
    public void close() {
        buffer.close();
    }
}
