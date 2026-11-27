package org.soutalab.celtium.vulkan.terrain.lane1;

import java.lang.foreign.MemorySegment;

/**
 * The memory behind one resident terrain slab (design V040-FINAL-PLAN section 2.1): a flat byte
 * range the {@link TerrainSectionStore} writes section geometry into and the terrain shaders read.
 * Two shapes exist: a host arena (tests, and any host-visible UMA path) and a Vulkan slab
 * allocation whose mapping the HAL owns. The store never sees the difference - it writes through
 * {@link #segment(long, long)} and lets the space decide what backs the bytes.
 */
public interface SlabSpace {

    /** Total bytes this space can hold. */
    long capacity();

    /**
     * A writable view of {@code [offset, offset + length)}. For the Vulkan shape this is the mapped
     * slab memory (host-visible write-through per the memory profile); a device-local space without
     * a mapping refuses here rather than handing out a wild pointer, and staged upload for that
     * shape is the frame executor's business, not the store's.
     */
    MemorySegment segment(long offset, long length);

    /** The {@code VkBuffer} to bind for this space, or 0 when the space has no device side yet. */
    long bufferHandle();

    /** Byte offset to add to a space-relative offset when addressing {@link #bufferHandle()}. */
    long bufferBaseOffset();
}
