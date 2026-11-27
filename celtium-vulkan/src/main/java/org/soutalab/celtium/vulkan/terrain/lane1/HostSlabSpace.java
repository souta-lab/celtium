package org.soutalab.celtium.vulkan.terrain.lane1;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/**
 * A {@link SlabSpace} backed by one off-heap arena segment: the shape unit tests drive, and the
 * shape a host-visible UMA device ends up writing through as well (there the segment wraps the
 * mapped slab address instead - see {@link #wrap(MemorySegment)}).
 */
public final class HostSlabSpace implements SlabSpace, AutoCloseable {

    private final Arena arena;
    private final MemorySegment segment;
    private final boolean ownsArena;

    public HostSlabSpace(long capacityBytes) {
        this.arena = Arena.ofAuto();
        this.segment = arena.allocate(capacityBytes, 256L);
        this.ownsArena = true;
    }

    private HostSlabSpace(MemorySegment segment) {
        this.arena = null;
        this.segment = segment;
        this.ownsArena = false;
    }

    /** Wraps an existing segment (e.g. a mapped Vulkan slab) without taking ownership of it. */
    public static HostSlabSpace wrap(MemorySegment segment) {
        return new HostSlabSpace(segment);
    }

    @Override
    public long capacity() {
        return segment.byteSize();
    }

    @Override
    public MemorySegment segment(long offset, long length) {
        if (offset < 0L || length < 0L || offset + length > segment.byteSize()) {
            throw new IndexOutOfBoundsException("range [" + offset + ", +" + length
                    + ") is outside the slab space of " + segment.byteSize() + " bytes");
        }
        return segment.asSlice(offset, length);
    }

    @Override
    public long bufferHandle() {
        return 0L;
    }

    @Override
    public long bufferBaseOffset() {
        return 0L;
    }

    @Override
    public void close() {
        // Arena.ofAuto segments are GC-managed; nothing to release explicitly. The method exists so
        // callers can hold the space in try-with-resources uniformly with the Vulkan shape.
    }
}
