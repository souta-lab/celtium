package org.soutalab.celtium.vulkan.terrain.lane1;

import org.lwjgl.vulkan.VK10;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.VulkanHostBuffer;

import java.lang.foreign.MemorySegment;

/**
 * A {@link SlabSpace} backed by one host-visible slab buffer (design V040-FINAL-PLAN section 2.1):
 * the terrain slab the main pass and the shadow pass share. Host-visible is the write shape the
 * plan's memory profile asks for on the terrain path - the mesher's output is written through the
 * mapping with no staging copy - and {@link VulkanMemoryProfile} is what chooses this shape over a
 * device-local slab where the device is not UMA (that choice belongs to the world builder, which
 * constructs this space only when the profile says host-visible).
 * <p>
 * The capacity is fixed at construction: the store's regions live inside this one buffer, so a
 * {@code SectionDesc} offset plus {@link #bufferBaseOffset()} addresses the buffer directly.
 * Render thread only, like every HAL object.
 */
public final class VulkanSlabSpace implements SlabSpace, AutoCloseable {

    private final VulkanHostBuffer buffer;

    public VulkanSlabSpace(VulkanContext context, long capacityBytes) {
        this.buffer = new VulkanHostBuffer(context, capacityBytes,
                VK10.VK_BUFFER_USAGE_STORAGE_BUFFER_BIT | VK10.VK_BUFFER_USAGE_TRANSFER_SRC_BIT);
    }

    @Override
    public long capacity() {
        return buffer.size();
    }

    @Override
    public MemorySegment segment(long offset, long length) {
        if (offset < 0L || length < 0L || offset + length > buffer.size()) {
            throw new IndexOutOfBoundsException("range [" + offset + ", +" + length
                    + ") is outside the slab space of " + buffer.size() + " bytes");
        }
        long address = buffer.mappedAddress();
        if (address == 0L) {
            throw new IllegalStateException("the terrain slab buffer is not mapped");
        }
        return MemorySegment.ofAddress(address + offset).reinterpret(length);
    }

    @Override
    public long bufferHandle() {
        return buffer.buffer();
    }

    @Override
    public long bufferBaseOffset() {
        return 0L;
    }

    @Override
    public void close() {
        buffer.delete();
    }
}
