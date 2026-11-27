package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.vulkan.VkDevice;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.List;

import org.lwjgl.system.MemoryStack;

import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkAllocateMemory;
import static org.lwjgl.vulkan.VK10.vkFreeMemory;

/**
 * The one-allocation home for the pack path's transient attachments (design section 2.2: "one
 * slab, {@code vkAllocateMemory} per attachment = 0; while packs are off the transient slab is
 * 0 MB"). The slab owns a single {@code VkDeviceMemory} and hands out offsets into it; images are
 * created by the target pool (stage S5) and bound to these offsets, never given their own
 * allocation.
 * <p>
 * Growth is deliberately coarse: the backing memory is replaced by one fresh allocation (the
 * first is 32 MiB, later ones double) and only while nothing is live - the frame discipline is
 * that every acquisition is released when its frame retires, so growth always lands between
 * frames, never inside one. The offset bookkeeping is a first-fit free-list with coalescing,
 * kept in {@link OffsetAllocator} where unit tests can reach it without a GPU.
 */
public final class TransientAttachmentSlab implements AutoCloseable {

    static final long FIRST_ALLOCATION_BYTES = 32L * 1024 * 1024;

    /** The backing-memory seam: the Vulkan implementation below, a recording fake in tests. */
    interface Backing {
        long allocate(long bytes);

        void free(long handle);
    }

    private final Backing backing;
    private OffsetAllocator allocator = new OffsetAllocator(0);
    private long memoryHandle;
    private int liveAllocations;

    public TransientAttachmentSlab(VkDevice device, int memoryTypeIndex) {
        this(new Backing() {
            @Override
            public long allocate(long bytes) {
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    org.lwjgl.vulkan.VkMemoryAllocateInfo info =
                            org.lwjgl.vulkan.VkMemoryAllocateInfo.calloc(stack).sType$Default()
                                    .allocationSize(bytes)
                                    .memoryTypeIndex(memoryTypeIndex);
                    LongBuffer handle = stack.longs(0);
                    int result = vkAllocateMemory(device, info, null, handle);
                    if (result != VK_SUCCESS) {
                        throw new IllegalStateException("vkAllocateMemory for the transient "
                                + "attachment slab failed with " + result);
                    }
                    return handle.get(0);
                }
            }

            @Override
            public void free(long handle) {
                vkFreeMemory(device, handle, null);
            }
        });
    }

    TransientAttachmentSlab(Backing backing) {
        this.backing = backing;
    }

    /** One live acquisition: an offset range inside the slab's single memory object. */
    public record Allocation(long offset, long sizeBytes) {
    }

    public synchronized Allocation acquire(long sizeBytes, long alignment) {
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be positive: " + sizeBytes);
        }
        Long offset = this.allocator.tryAllocate(sizeBytes, alignment);
        if (offset == null) {
            growToFit(sizeBytes, alignment);
            offset = this.allocator.tryAllocate(sizeBytes, alignment);
            if (offset == null) {
                throw new IllegalStateException("transient attachment slab could not satisfy "
                        + sizeBytes + " bytes after growth");
            }
        }
        this.liveAllocations++;
        return new Allocation(offset, sizeBytes);
    }

    /**
     * The slab's single device-memory handle, for callers that bind their own images into an
     * acquired range ({@code vkBindImageMemory} at the allocation's offset) - the direct-GL
     * sink's custom-framebuffer attachments are the first such caller. {@code VK_NULL_HANDLE}
     * until the first {@link #acquire} allocates the backing memory.
     */
    public synchronized long memoryHandle() {
        return memoryHandle;
    }

    public synchronized void release(Allocation allocation) {
        this.allocator.release(allocation.offset(), allocation.sizeBytes());
        this.liveAllocations--;
    }

    /** Bytes of backing memory currently owned (0 while nothing was ever acquired). */
    public synchronized long allocatedBytes() {
        return this.allocator.capacity();
    }

    public synchronized int liveAllocations() {
        return this.liveAllocations;
    }

    private void growToFit(long sizeBytes, long alignment) {
        if (this.liveAllocations > 0) {
            throw new IllegalStateException("transient attachment slab cannot grow while "
                    + this.liveAllocations + " allocations are live; growth happens between frames");
        }
        long needed = sizeBytes + Math.max(0, alignment - 1);
        long target = this.allocator.capacity() == 0
                ? Math.max(FIRST_ALLOCATION_BYTES, needed)
                : Math.max(this.allocator.capacity() * 2, this.allocator.capacity() + needed);
        long fresh = this.backing.allocate(target);
        if (this.memoryHandle != 0) {
            this.backing.free(this.memoryHandle);
        }
        this.memoryHandle = fresh;
        this.allocator = new OffsetAllocator(target);
    }

    @Override
    public synchronized void close() {
        if (this.memoryHandle != 0) {
            this.backing.free(this.memoryHandle);
            this.memoryHandle = 0;
        }
        this.allocator = new OffsetAllocator(0);
        this.liveAllocations = 0;
    }

    /**
     * First-fit offset bookkeeping over a fixed capacity, with free-list coalescing. Pure
     * arithmetic: no Vulkan anywhere, so the slab's allocation policy is unit-tested directly.
     */
    static final class OffsetAllocator {
        private final long capacity;
        private final List<long[]> freeRanges = new ArrayList<>(); // each [offset, size), sorted by offset

        OffsetAllocator(long capacity) {
            this.capacity = capacity;
            if (capacity > 0) {
                this.freeRanges.add(new long[]{0, capacity});
            }
        }

        long capacity() {
            return this.capacity;
        }

        Long tryAllocate(long sizeBytes, long alignment) {
            if (alignment <= 0 || (alignment & (alignment - 1)) != 0) {
                throw new IllegalArgumentException("alignment must be a power of two: " + alignment);
            }
            for (int i = 0; i < this.freeRanges.size(); i++) {
                long[] range = this.freeRanges.get(i);
                long aligned = (range[0] + alignment - 1) & -alignment;
                long end = aligned + sizeBytes;
                long rangeEnd = range[0] + range[1];
                if (end > rangeEnd) {
                    continue;
                }
                this.freeRanges.remove(i);
                if (aligned > range[0]) {
                    this.freeRanges.add(i, new long[]{range[0], aligned - range[0]});
                    i++;
                }
                if (end < rangeEnd) {
                    this.freeRanges.add(i, new long[]{end, rangeEnd - end});
                }
                return aligned;
            }
            return null;
        }

        void release(long offset, long sizeBytes) {
            int at = 0;
            while (at < this.freeRanges.size() && this.freeRanges.get(at)[0] < offset) {
                at++;
            }
            this.freeRanges.add(at, new long[]{offset, sizeBytes});
            // Coalesce with the next range, then with the previous one.
            if (at + 1 < this.freeRanges.size()) {
                long[] mine = this.freeRanges.get(at);
                long[] next = this.freeRanges.get(at + 1);
                if (mine[0] + mine[1] == next[0]) {
                    mine[1] += next[1];
                    this.freeRanges.remove(at + 1);
                }
            }
            if (at > 0) {
                long[] previous = this.freeRanges.get(at - 1);
                long[] mine = this.freeRanges.get(at);
                if (previous[0] + previous[1] == mine[0]) {
                    previous[1] += mine[1];
                    this.freeRanges.remove(at);
                }
            }
        }
    }
}
