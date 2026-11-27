package org.soutalab.celtium.vulkan.terrain.lane1;

import java.util.ArrayList;
import java.util.List;

/**
 * A first-fit sub-allocator over one fixed byte range of a resident slab (design V040-FINAL-PLAN
 * section 2.1, Lane 1): the terrain store carves its axis, general and auxiliary regions out of
 * slab memory with this ledger, so a {@code SectionDesc}'s offsets are stable for the section's
 * whole residency. Frees are timeline-deferred - a range handed back while a submission may still
 * read it becomes reusable only once {@link #collectCompleted(long)} proves the GPU passed the
 * release's timeline value, the same rule the HAL slab allocator lives by.
 * <p>
 * Pure ledger: no memory, no Vulkan. Thread confinement is the caller's (the store synchronizes).
 */
public final class SlabRegion {

    private final long capacity;
    private final List<long[]> freeRuns = new ArrayList<>(); // [offset, size], sorted by offset
    private final List<long[]> pendingFrees = new ArrayList<>(); // [offset, size, timelineValue]
    private long usedBytes;

    public SlabRegion(long capacityBytes) {
        if (capacityBytes <= 0L) {
            throw new IllegalArgumentException("capacity must be positive, was " + capacityBytes);
        }
        this.capacity = capacityBytes;
        this.freeRuns.add(new long[]{0L, capacityBytes});
    }

    public long capacity() {
        return capacity;
    }

    public long usedBytes() {
        return usedBytes;
    }

    public long freeBytes() {
        long free = 0L;
        for (long[] run : freeRuns) {
            free += run[1];
        }
        return free;
    }

    /**
     * Allocates {@code sizeBytes} aligned to {@code alignment}; returns the offset, or -1 when
     * no run fits. The alignment need not be a power of two: the general slab's record stride is
     * 80 since the GeneralQuad UV extension, and section ranges must start on a record boundary.
     */
    public long allocate(long sizeBytes, long alignment) {
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("alignment must be positive, was " + alignment);
        }
        for (int i = 0; i < freeRuns.size(); i++) {
            long[] run = freeRuns.get(i);
            long start = alignUp(run[0], alignment);
            long end = start + sizeBytes;
            long runEnd = run[0] + run[1];
            if (end > runEnd) {
                continue;
            }
            freeRuns.remove(i);
            if (start > run[0]) {
                insertRun(run[0], start - run[0]);
            }
            if (end < runEnd) {
                insertRun(end, runEnd - end);
            }
            usedBytes += sizeBytes;
            return start;
        }
        return -1L;
    }

    /** Defers the return of a range until the completion currency passes {@code timelineValue}. */
    public void releaseDeferred(long offset, long sizeBytes, long timelineValue) {
        if (offset < 0L || sizeBytes <= 0L || offset + sizeBytes > capacity) {
            throw new IllegalArgumentException("release range [" + offset + ", +" + sizeBytes
                    + ") is outside the region of " + capacity + " bytes");
        }
        pendingFrees.add(new long[]{offset, sizeBytes, timelineValue});
    }

    /** Makes every deferred release whose timeline value has completed reusable, coalescing neighbours. */
    public void collectCompleted(long completedTimelineValue) {
        for (int i = pendingFrees.size() - 1; i >= 0; i--) {
            long[] pending = pendingFrees.get(i);
            if (pending[2] <= completedTimelineValue) {
                pendingFrees.remove(i);
                usedBytes -= pending[1];
                insertRun(pending[0], pending[1]);
            }
        }
    }

    public int pendingFreeCount() {
        return pendingFrees.size();
    }

    private void insertRun(long offset, long size) {
        int i = 0;
        while (i < freeRuns.size() && freeRuns.get(i)[0] < offset) {
            i++;
        }
        // Coalesce with the following run.
        if (i < freeRuns.size() && offset + size == freeRuns.get(i)[0]) {
            size += freeRuns.get(i)[1];
            freeRuns.remove(i);
        }
        // Coalesce with the preceding run.
        if (i > 0 && freeRuns.get(i - 1)[0] + freeRuns.get(i - 1)[1] == offset) {
            long[] previous = freeRuns.get(i - 1);
            previous[1] += size;
            return;
        }
        freeRuns.add(i, new long[]{offset, size});
    }

    /** Rounds {@code value} up to a multiple of {@code alignment} (the division form: any positive alignment). */
    private static long alignUp(long value, long alignment) {
        return ((value + alignment - 1L) / alignment) * alignment;
    }
}
