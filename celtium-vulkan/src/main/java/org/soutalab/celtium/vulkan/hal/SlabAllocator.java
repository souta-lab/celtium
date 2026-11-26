package org.soutalab.celtium.vulkan.hal;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkMemoryAllocateInfo;
import org.soutalab.celtium.vulkan.VulkanContext;

import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkBindBufferMemory;
import static org.lwjgl.vulkan.VK10.vkBindImageMemory;
import static org.lwjgl.vulkan.VK10.vkMapMemory;
import static org.lwjgl.vulkan.VK10.vkUnmapMemory;

/**
 * The Vulkan-owning half of the slab allocator: a {@link SlabPool} ledger decides where every allocation goes, and
 * this class holds the real {@code VkDeviceMemory} behind each slab the ledger creates.
 * <p>
 * <b>Handles:</b> as everywhere in this backend, LWJGL represents non-dispatchable handles such as {@code VkBuffer},
 * {@code VkImage} and {@code VkDeviceMemory} as plain {@code long} values, so {@link #bindBuffer(long, SlabAllocation)}
 * and {@link #bindImage(long, SlabAllocation)} take the raw handle.
 * <p>
 * <b>Ledger/device linkage:</b> {@link SlabPool} has no hook that fires before it appends a slab, so the linkage is
 * driven by observation instead: {@link #allocate(long, long)} records {@link SlabPool#slabCount()} before and after
 * the ledger call and creates the {@code VkDeviceMemory} for every slab index that appeared. This is sound because
 * the ledger is append-only - a slab index, once created, keeps its meaning for the life of the pool, and slabs are
 * created in index order - so the device slabs can be kept in a list indexed exactly like the ledger's slabs, filling
 * in only the missing tail. If the ledger call returns {@code null}, no device memory has been created for it yet at
 * the point of the check, so there is nothing to roll back; if creating the device memory for a fresh slab fails, the
 * ledger allocation that prompted it is freed back to the pool before the failure propagates, so the ledger never
 * points at a slab this class does not hold.
 * <p>
 * <b>Oversized requests:</b> a request larger than one slab is not dropped to an individual allocation outside the
 * slab system. It gets a dedicated slab of its own - one {@code VkDeviceMemory} sized to the request rounded up to
 * whole pages - so it is counted by {@link #liveSlabCount()} like every other slab and follows the same deferred
 * release rule; the only difference is that {@link #collectCompleted(long)} frees its memory once it is empty,
 * because a slab shaped for one request is unlikely to fit the next one, while ordinary slabs are kept for reuse.
 * <p>
 * <b>Fixed-page path (r8 ruling #8945 (A)):</b> a request whose 4096-page-rounded size is exactly
 * {@link FixedPagePool#FIXED_PAGE_SIZE_BYTES} (the 64 KiB staging class) is served first by a
 * {@link FixedPagePool} whose pages are exactly that size, so a free page is always a contiguous run of the
 * full request size and the r8 refusal (151,552 bytes free, largest run 28,672) cannot recur on this path.
 * The fixed pool's slabs are ordinary slabs of this allocator - same memory type, same
 * {@code VulkanContext.allocateMemory} accounting, same deferred release rule, counted in
 * {@link #liveSlabCount()} and {@link #usedBytes()} - so the path adds no allocation outside the slab census.
 * When the fixed pool cannot serve (its cap is reached, or the alignment does not divide the page size), the
 * request falls back to the ordinary pool, so this path never makes a request fail that the ordinary pool
 * would have served. Fixed slabs are numbered above the ordinary range with a wide offset
 * ({@link #FIXED_SLAB_INDEX_BASE}) so an index never collides with an ordinary or oversized slab index.
 * <p>
 * <b>Memory accounting:</b> every allocation goes through
 * {@link VulkanContext#allocateMemory(VkDevice, VkMemoryAllocateInfo, LongBuffer, String)} and every free through
 * {@link VulkanContext#freeMemory(VkDevice, long)}, never the raw {@code vkAllocateMemory}/{@code vkFreeMemory}, so
 * each slab is counted in the context's live-allocation census - the count a frame pays for on submit.
 * <p>
 * <b>Release:</b> there is deliberately no immediate release. {@link #release(SlabAllocation, long)} defers to a
 * completion value through the ledger, because a buffer or image bound from a slab may still be read by an
 * in-flight submission; {@link #collectCompleted(long)} is what makes the bytes reusable, once the completion
 * currency proves the GPU is done with them. The currency is the context's slab completion value
 * ({@link VulkanContext#celtium$slabReleaseValue()} / {@link VulkanContext#celtium$slabCompletedValue()}): on a
 * timeline device it is the frame timeline - a release keyed on value {@code V} becomes reusable once the
 * timeline's GPU-confirmed counter passes {@code V} - and where the device has no timeline it is the drain
 * serial, under which a release keyed on serial {@code S} becomes reusable once a retired-buffer drain has
 * completed with the queue idle after the release was recorded. The ledger itself is currency-agnostic (a
 * monotone {@code long}), so the re-keying touched only the context's two value methods, not this class.
 * <p>
 * <b>Pool naming and OOM identification (r10 follow-up):</b> every allocator carries a {@link #name()} given by
 * its factory (the context pools are {@code deviceLocal}, {@code hostVisible} and {@code deviceLocalImage} per
 * memory type; the terrain geometry allocator is {@code terrainGeometry}). The Phase 1 leg r10 died on a
 * 32768-byte request refused by an unnamed ordinary ledger at its 8-slab cap, and the final census could not
 * attribute the failure: the per-pool summary aggregates the ordinary and fixed-page ledgers, so a healthy
 * fixed ledger's free runs masked the ordinary ledger's exhaustion in the same allocator. The out-of-memory
 * refusal therefore names its pool and reports the two ledgers separately, and the context summary reports
 * the same split, so a refusal is attributable from the crash text alone.
 * <p>
 * <b>Threading:</b> every method must be called on the render thread that owns the {@link VulkanContext}, and
 * {@link #delete()} must happen before the context is deleted.
 */
public final class SlabAllocator {

    /** Default size of one ordinary slab: 64 MiB. */
    public static final long DEFAULT_SLAB_SIZE = 64L * 1024 * 1024;

    /**
     * Slab index base for fixed-page slabs: fixed slab {@code i} of an allocator is numbered
     * {@code maxSlabs + FIXED_SLAB_INDEX_BASE + i}, far above the ordinary range and the oversized range that
     * grows upward from {@code maxSlabs}, so an index always names exactly one kind of slab.
     */
    static final int FIXED_SLAB_INDEX_BASE = 1024;

    private final VulkanContext context;
    private final String name;
    private final int memoryTypeIndex;
    private final long slabSizeBytes;
    private final int maxSlabs;
    private final boolean hostVisible;
    private final SlabPool pool;
    private final FixedPagePool fixedPool;

    /** Device slabs backing the ledger's slabs, in the same index order; entries are appended, never removed. */
    private final List<SlabRecord> poolSlabs = new ArrayList<SlabRecord>();

    /** Device slabs backing the fixed-page ledger's slabs, in the same index order; appended, never removed. */
    private final List<SlabRecord> fixedSlabs = new ArrayList<SlabRecord>();

    /** Dedicated slabs created for oversized requests; an entry is removed when its memory is freed. */
    private final List<SlabRecord> oversizedSlabs = new ArrayList<SlabRecord>();

    /** Oversized allocations released through {@link #release} and waiting for their timeline value. */
    private final List<PendingOversizedRelease> pendingOversized = new ArrayList<PendingOversizedRelease>();

    private boolean deleted;

    /**
     * Creates an allocator over one memory type. No device memory is allocated yet: slabs are created lazily, the
     * first time the ledger needs one.
     *
     * @param context         owning context; the allocator must be deleted before the context is
     * @param memoryTypeIndex the memory type every slab is allocated from
     * @param slabSizeBytes   size of every ordinary slab; must be a multiple of
     *                        {@link SlabPool#DEFAULT_PAGE_SIZE_BYTES}
     * @param maxSlabs        cap on ordinary slabs; dedicated oversized slabs are not counted against it
     * @param hostVisible     when true, every slab is mapped for its whole lifetime and
     *                        {@link #mappedAddress(SlabAllocation)} is available
     */
    public SlabAllocator(VulkanContext context, int memoryTypeIndex, long slabSizeBytes, int maxSlabs,
            boolean hostVisible) {
        this(context, hostVisible ? "hostVisible" : "deviceLocal", memoryTypeIndex, slabSizeBytes, maxSlabs,
                hostVisible);
    }

    /**
     * Creates an allocator with an explicit pool {@code name}: the name the out-of-memory refusal and the
     * per-pool census summary attribute this allocator's ledgers to (see the class documentation). Factories
     * that keep several allocators of the same visibility apart (the context's buffer and image pools) pass
     * their own names; the unnamed constructor keeps a visibility-derived default.
     */
    public SlabAllocator(VulkanContext context, String name, int memoryTypeIndex, long slabSizeBytes,
            int maxSlabs, boolean hostVisible) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        if (memoryTypeIndex < 0) {
            throw new IllegalArgumentException("memoryTypeIndex must not be negative, was " + memoryTypeIndex);
        }
        this.context = context;
        this.name = name;
        this.memoryTypeIndex = memoryTypeIndex;
        this.slabSizeBytes = slabSizeBytes;
        this.maxSlabs = maxSlabs;
        this.hostVisible = hostVisible;
        this.pool = new SlabPool(slabSizeBytes, SlabPool.DEFAULT_PAGE_SIZE_BYTES, maxSlabs);
        this.fixedPool = new FixedPagePool(slabSizeBytes, FixedPagePool.FIXED_PAGE_SIZE_BYTES, maxSlabs);
        // Collect-before-grow: when either ledger is about to append a slab while deferred releases are
        // pending, it first collects them against the context's slab completed value - the same currency
        // the collect-on-allocate retry further below uses - so a burst does not raise this allocator's
        // slab peak for ranges the GPU has already finished with.
        this.pool.setCompletedValueSource(() -> this.context.celtium$slabCompletedValue());
        this.fixedPool.setCompletedValueSource(() -> this.context.celtium$slabCompletedValue());
    }

    /**
     * Allocates {@code sizeBytes} from a slab, creating a slab first when the ledger grows.
     * <p>
     * A request larger than {@link #slabSize()} is served by a dedicated slab of its own size (see the class
     * documentation) rather than failing.
     * <p>
     * When the ledger refuses a request, the refusal is not reported at once: deferred releases nobody has
     * collected yet are the refusal's common cause (a per-frame staging churn can hold a whole pool hostage
     * between two collection drive points), so one collection against the context's current slab completed
     * value runs first and the ledger is retried. Only a refusal that survives that retry is real.
     *
     * @throws IllegalStateException when the ledger has no room and its slab cap has been reached, when the device
     *                               memory for a fresh slab cannot be allocated, or after {@link #delete()}
     */
    public SlabAllocation allocate(long sizeBytes, long alignment) {
        requireLive();
        if (sizeBytes <= 0L) {
            throw new IllegalArgumentException("sizeBytes must be positive, was " + sizeBytes);
        }
        if (alignment < 1L) {
            throw new IllegalArgumentException("alignment must be at least 1, was " + alignment);
        }
        long pagedSize = alignUp(sizeBytes, SlabPool.DEFAULT_PAGE_SIZE_BYTES);
        if (pagedSize > this.slabSizeBytes) {
            return allocateOversized(pagedSize);
        }
        if (pagedSize == FixedPagePool.FIXED_PAGE_SIZE_BYTES) {
            SlabAllocation fixed = allocateFixedPage(sizeBytes, alignment);
            if (fixed != null) {
                return fixed;
            }
            // The fixed pool is full or cannot honour the alignment: fall through to the ordinary pool, so
            // the fixed path never refuses a request the ordinary pool would have served.
        }
        SlabPool.Allocation pooled = this.pool.allocate(sizeBytes, alignment);
        if (pooled == null) {
            pooled = collectAndRetry(sizeBytes, alignment);
        }
        if (pooled == null) {
            // The refusal names its pool and reports the ordinary and fixed-page ledgers separately: the
            // aggregated figures (usedBytes/freeBytes/largestFreeRun) mix the two ledgers, and in the r10 leg
            // that mix is exactly what hid this ledger's exhaustion behind the fixed ledger's free runs.
            throw new IllegalStateException("SlabAllocator is out of memory: pool " + this.name
                    + " (memoryType=" + this.memoryTypeIndex + ", hostVisible=" + this.hostVisible
                    + ") cannot serve " + sizeBytes + " bytes with alignment " + alignment
                    + " [ordinary: " + this.pool.slabCount() + " of " + this.maxSlabs + " slabs in use, "
                    + this.pool.usedBytes() + " bytes used, " + this.pool.freeBytes()
                    + " bytes free but fragmented, largest free run " + this.pool.largestFreeRun() + " bytes, "
                    + this.pool.liveAllocationCount() + " live allocations, " + this.pool.pendingDeferredCount()
                    + " pending deferred releases; fixedPage: " + this.fixedPool.slabCount() + " of "
                    + this.maxSlabs + " slabs in use, " + this.fixedPool.freeBytes()
                    + " bytes free, largest free run " + this.fixedPool.largestFreeRun()
                    + " bytes; oversized slabs: " + this.oversizedSlabs.size() + "]");
        }
        try {
            ensurePoolSlabs();
        } catch (RuntimeException | Error error) {
            // The ledger allocation names a slab whose device memory could not be created; hand the range back so
            // the ledger never counts bytes this class cannot bind.
            this.pool.free(pooled);
            throw error;
        }
        return new SlabAllocation(this, this.poolSlabs.get(pooled.slab()), pooled);
    }

    /**
     * Binds a buffer to an allocation's slab memory at the allocation's offset:
     * {@code vkBindBufferMemory(device, buffer, alloc.deviceMemory(), alloc.offset())}. Render thread only.
     */
    public void bindBuffer(long buffer, SlabAllocation allocation) {
        requireLive();
        requireAllocation(allocation);
        check(vkBindBufferMemory(this.context.device(), buffer, allocation.deviceMemory(), allocation.offset()),
                "vkBindBufferMemory(slab allocation)");
    }

    /**
     * Binds an image to an allocation's slab memory at the allocation's offset:
     * {@code vkBindImageMemory(device, image, alloc.deviceMemory(), alloc.offset())}. Render thread only.
     */
    public void bindImage(long image, SlabAllocation allocation) {
        requireLive();
        requireAllocation(allocation);
        check(vkBindImageMemory(this.context.device(), image, allocation.deviceMemory(), allocation.offset()),
                "vkBindImageMemory(slab allocation)");
    }

    /**
     * Releases an allocation once the completion currency reaches {@code timelineValue}. The bytes stay
     * reserved - and keep counting as used - until {@link #collectCompleted(long)} collects the release; see the
     * class documentation for why reuse waits for the completion proof rather than for this call.
     *
     * @throws IllegalStateException when the allocation was already released
     */
    public void release(SlabAllocation allocation, long timelineValue) {
        requireLive();
        requireAllocation(allocation);
        if (allocation.released) {
            throw new IllegalStateException("This SlabAllocation has already been released.");
        }
        allocation.released = true;
        if (allocation.fixed != null) {
            this.fixedPool.freeDeferred(allocation.fixed, timelineValue);
        } else if (allocation.pooled != null) {
            this.pool.freeDeferred(allocation.pooled, timelineValue);
        } else {
            this.pendingOversized.add(new PendingOversizedRelease(allocation.record, timelineValue));
        }
    }

    /**
     * Collects every release whose timeline value has completed. Pooled ranges become reusable; a dedicated
     * oversized slab whose only allocation was collected is freed outright, while ordinary slabs are kept (empty or
     * not) for reuse, because recreating one costs an allocation the next growth would pay again.
     */
    public void collectCompleted(long completedTimelineValue) {
        requireLive();
        this.pool.collectCompleted(completedTimelineValue);
        this.fixedPool.collectCompleted(completedTimelineValue);
        Iterator<PendingOversizedRelease> iterator = this.pendingOversized.iterator();
        while (iterator.hasNext()) {
            PendingOversizedRelease pending = iterator.next();
            if (pending.timelineValue <= completedTimelineValue) {
                iterator.remove();
                this.oversizedSlabs.remove(pending.record);
                freeSlab(pending.record);
            }
        }
    }

    /**
     * Host address of an allocation: the slab's mapping base plus the allocation's offset. Slabs are mapped once, at
     * creation, and stay mapped until {@link #delete()}, so the address is stable for the allocation's whole life.
     *
     * @throws IllegalStateException when this allocator is not host-visible - a device-local slab has no host
     *                               address to hand out, and returning one anyway would be a wild pointer
     */
    public long mappedAddress(SlabAllocation allocation) {
        requireAllocation(allocation);
        if (!this.hostVisible) {
            throw new IllegalStateException("mappedAddress is only available on a host-visible SlabAllocator.");
        }
        if (allocation.record.mapped == 0L) {
            throw new IllegalStateException("The slab backing this allocation is not mapped.");
        }
        return allocation.record.mapped + allocation.offset();
    }

    /** Number of live {@code VkDeviceMemory} objects held by this allocator, ordinary and oversized slabs alike. */
    public int liveSlabCount() {
        return this.poolSlabs.size() + this.fixedSlabs.size() + this.oversizedSlabs.size();
    }

    /** Number of live fixed-page slabs held by this allocator, a subset of {@link #liveSlabCount()}. */
    public int fixedSlabCount() {
        return this.fixedSlabs.size();
    }

    /** Size of one ordinary slab in bytes. */
    public long slabSize() {
        return this.slabSizeBytes;
    }

    /** The memory type every slab of this allocator is allocated from. */
    public int memoryTypeIndex() {
        return this.memoryTypeIndex;
    }

    /**
     * Bytes currently held by live allocations in this pool: the ledger's used bytes plus the full size of
     * every dedicated oversized slab still held (a dedicated slab is one allocation, so all of it is live
     * until its release is collected and the slab is freed). This is the per-pool live figure the memory
     * allocation summary reports, so a leg can say which pool holds the memory instead of only how much the
     * process holds in total.
     */
    public long usedBytes() {
        long used = this.pool.usedBytes() + this.fixedPool.usedBytes();
        for (SlabRecord record : this.oversizedSlabs) {
            used += record.size;
        }
        return used;
    }

    /** Bytes not held by any live allocation across this pool's ordinary and fixed-page slabs. */
    public long freeBytes() {
        return this.pool.freeBytes() + this.fixedPool.freeBytes();
    }

    /** Length of the longest single free run in any ordinary or fixed-page slab of this pool. */
    public long largestFreeRun() {
        return Math.max(this.pool.largestFreeRun(), this.fixedPool.largestFreeRun());
    }

    /** The pool name this allocator's refusals and census entries are attributed to. */
    public String name() {
        return this.name;
    }

    /** Whether this allocator's slabs are host-visible and mapped for their whole lifetime. */
    public boolean hostVisible() {
        return this.hostVisible;
    }

    /** Number of ordinary slabs the ledger currently holds, against the {@code maxSlabs} cap. */
    public int ordinarySlabCount() {
        return this.pool.slabCount();
    }

    /** Bytes held by live allocations in the ordinary ledger alone (see {@link #usedBytes()} for the total). */
    public long ordinaryUsedBytes() {
        return this.pool.usedBytes();
    }

    /** Bytes not held by any live allocation in the ordinary ledger alone. */
    public long ordinaryFreeBytes() {
        return this.pool.freeBytes();
    }

    /** Length of the longest single free run in the ordinary ledger alone. */
    public long ordinaryLargestFreeRun() {
        return this.pool.largestFreeRun();
    }

    /** Bytes held by live allocations in the fixed-page ledger alone. */
    public long fixedUsedBytes() {
        return this.fixedPool.usedBytes();
    }

    /** Bytes not held by any live allocation in the fixed-page ledger alone. */
    public long fixedFreeBytes() {
        return this.fixedPool.freeBytes();
    }

    /** Length of the longest run of consecutive free pages in the fixed-page ledger alone, in bytes. */
    public long fixedLargestFreeRun() {
        return this.fixedPool.largestFreeRun();
    }

    /** Number of dedicated oversized slabs currently held. */
    public int oversizedSlabCount() {
        return this.oversizedSlabs.size();
    }

    /**
     * {@return the size in bytes of every dedicated oversized slab currently held, in creation order}. The
     * census reported only the count, but the dispositions that turn on oversized slabs - absorbing them
     * into a larger ordinary slab size, or retiring the step-growth source - turn on how big each one is
     * (BO convergence design §5.1), so the sizes ride the same summary as the count.
     */
    public long[] oversizedSlabSizes() {
        long[] sizes = new long[this.oversizedSlabs.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = this.oversizedSlabs.get(i).size;
        }
        return sizes;
    }

    /**
     * Frees every slab, ordinary and oversized, mapped or not. This is teardown, not a release: allocations that
     * were never released are freed along with the rest, because once the allocator is gone nothing can collect them.
     * Idempotent, and safe to call after the context is gone (destroying the logical device frees its children
     * implicitly). Render thread only.
     */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        for (SlabRecord record : this.poolSlabs) {
            freeSlab(record);
        }
        for (SlabRecord record : this.fixedSlabs) {
            freeSlab(record);
        }
        for (SlabRecord record : this.oversizedSlabs) {
            freeSlab(record);
        }
        this.poolSlabs.clear();
        this.fixedSlabs.clear();
        this.oversizedSlabs.clear();
        this.pendingOversized.clear();
    }

    /**
     * Serves one fixed-page-class request from the fixed pool, with the same collect-on-allocate retry the
     * ordinary path gets: a refusal whose cause is uncollected deferred releases is retried once after a
     * collection against the context's slab completed value. Returns {@code null} when the fixed pool still
     * cannot serve, and the caller falls back to the ordinary pool.
     */
    private SlabAllocation allocateFixedPage(long sizeBytes, long alignment) {
        FixedPagePool.Allocation fixed = this.fixedPool.allocate(sizeBytes, alignment);
        if (fixed == null && this.fixedPool.pendingDeferredCount() > 0) {
            collectAndRetryFixed();
            fixed = this.fixedPool.allocate(sizeBytes, alignment);
        }
        if (fixed == null) {
            return null;
        }
        try {
            ensureFixedSlabs();
        } catch (RuntimeException | Error error) {
            this.fixedPool.free(fixed);
            throw error;
        }
        return new SlabAllocation(this, this.fixedSlabs.get(fixed.slab()), null, fixed);
    }

    /** Collection half of the fixed-page retry; mirrors {@link #collectAndRetry} without the ledger retry. */
    private void collectAndRetryFixed() {
        collectCompleted(this.context.celtium$slabCompletedValue());
    }

    private SlabPool.Allocation collectAndRetry(long sizeBytes, long alignment) {
        if (this.pool.pendingDeferredCount() == 0 && this.pendingOversized.isEmpty()) {
            return null;
        }
        collectCompleted(this.context.celtium$slabCompletedValue());
        return this.pool.allocate(sizeBytes, alignment);
    }

    private SlabAllocation allocateOversized(long pagedSize) {
        // The slab index reported for a dedicated slab starts above the ordinary range, so an index always names
        // exactly one kind of slab and the ordinary indices keep matching the ledger's.
        SlabRecord record = createSlab(pagedSize, this.maxSlabs + this.oversizedSlabs.size(), true);
        this.oversizedSlabs.add(record);
        return new SlabAllocation(this, record, null);
    }

    /** Creates the device memory for every fixed-page slab that does not have one yet, in slab index order. */
    private void ensureFixedSlabs() {
        while (this.fixedSlabs.size() < this.fixedPool.slabCount()) {
            int slabIndex = this.fixedSlabs.size();
            this.fixedSlabs.add(createSlab(this.slabSizeBytes,
                    this.maxSlabs + FIXED_SLAB_INDEX_BASE + slabIndex, false));
        }
    }

    /** Creates the device memory for every ledger slab that does not have one yet, in slab index order. */
    private void ensurePoolSlabs() {
        while (this.poolSlabs.size() < this.pool.slabCount()) {
            int slabIndex = this.poolSlabs.size();
            this.poolSlabs.add(createSlab(this.slabSizeBytes, slabIndex, false));
        }
    }

    private SlabRecord createSlab(long sizeBytes, int slabIndex, boolean oversized) {
        VkDevice device = this.context.device();
        long memory = VK_NULL_HANDLE;
        long mapped = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkMemoryAllocateInfo allocateInfo = VkMemoryAllocateInfo.calloc(stack).sType$Default()
                    .allocationSize(sizeBytes)
                    .memoryTypeIndex(this.memoryTypeIndex);
            LongBuffer memoryHandle = stack.mallocLong(1);
            check(VulkanContext.allocateMemory(device, allocateInfo, memoryHandle, "slab"),
                    "vkAllocateMemory(slab of " + sizeBytes + " bytes, memory type " + this.memoryTypeIndex + ")");
            memory = memoryHandle.get(0);
            if (this.hostVisible) {
                PointerBuffer pointer = stack.mallocPointer(1);
                check(vkMapMemory(device, memory, 0L, sizeBytes, 0, pointer),
                        "vkMapMemory(slab of " + sizeBytes + " bytes)");
                mapped = pointer.get(0);
            }
        } catch (RuntimeException | Error error) {
            if (memory != VK_NULL_HANDLE) {
                VulkanContext.freeMemory(device, memory);
            }
            throw error;
        }
        return new SlabRecord(slabIndex, sizeBytes, oversized, memory, mapped);
    }

    private void freeSlab(SlabRecord record) {
        VkDevice device = this.context.device();
        if (device == null) {
            record.mapped = 0L;
            record.memory = VK_NULL_HANDLE;
            return;
        }
        if (record.mapped != 0L) {
            vkUnmapMemory(device, record.memory);
            record.mapped = 0L;
        }
        if (record.memory != VK_NULL_HANDLE) {
            VulkanContext.freeMemory(device, record.memory);
            record.memory = VK_NULL_HANDLE;
        }
    }

    private void requireLive() {
        if (this.deleted) {
            throw new IllegalStateException("This SlabAllocator has been deleted.");
        }
    }

    private static void requireAllocation(SlabAllocation allocation) {
        if (allocation == null) {
            throw new IllegalArgumentException("allocation must not be null");
        }
    }

    private static long alignUp(long value, long alignment) {
        long remainder = value % alignment;
        return remainder == 0L ? value : value + (alignment - remainder);
    }

    private static void check(int result, String operation) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " returned VkResult " + result);
        }
    }

    /** One {@code VkDeviceMemory} held by this allocator, with its mapping when host-visible. */
    private static final class SlabRecord {
        final int slabIndex;
        final long size;
        final boolean oversized;
        long memory;
        long mapped;

        SlabRecord(int slabIndex, long size, boolean oversized, long memory, long mapped) {
            this.slabIndex = slabIndex;
            this.size = size;
            this.oversized = oversized;
            this.memory = memory;
            this.mapped = mapped;
        }
    }

    /** One oversized allocation's deferred release: the slab to free and the timeline value that gates it. */
    private static final class PendingOversizedRelease {
        final SlabRecord record;
        final long timelineValue;

        PendingOversizedRelease(SlabRecord record, long timelineValue) {
            this.record = record;
            this.timelineValue = timelineValue;
        }
    }

    /**
     * One allocation handed out by {@link SlabAllocator#allocate(long, long)}: the slab memory to bind, and where
     * inside it the allocation lives. For an ordinary allocation this wraps the ledger's
     * {@link SlabPool.Allocation}; for a fixed-page allocation it wraps a {@link FixedPagePool.Allocation}; a
     * dedicated oversized slab has no ledger entry, so its offset is 0 and its size is the whole slab.
     */
    public static final class SlabAllocation {
        private final SlabAllocator owner;
        private final SlabRecord record;
        private final SlabPool.Allocation pooled;
        private final FixedPagePool.Allocation fixed;
        private boolean released;

        SlabAllocation(SlabAllocator owner, SlabRecord record, SlabPool.Allocation pooled) {
            this(owner, record, pooled, null);
        }

        SlabAllocation(SlabAllocator owner, SlabRecord record, SlabPool.Allocation pooled,
                FixedPagePool.Allocation fixed) {
            this.owner = owner;
            this.record = record;
            this.pooled = pooled;
            this.fixed = fixed;
        }

        /** Whether this allocation is a fixed page of the 64 KiB staging class (see {@link FixedPagePool}). */
        public boolean fixedPage() {
            return this.fixed != null;
        }

        /** The allocator that created this allocation; the only allocator this allocation may be released to. */
        public SlabAllocator owner() {
            return this.owner;
        }

        /** The {@code VkDeviceMemory} of the slab holding this allocation. */
        public long deviceMemory() {
            return this.record.memory;
        }

        /** Byte offset of the allocation inside its slab. */
        public long offset() {
            if (this.fixed != null) {
                return this.fixed.offset();
            }
            return this.pooled != null ? this.pooled.offset() : 0L;
        }

        /** Size in bytes after rounding up to whole pages; the range actually reserved. */
        public long size() {
            if (this.fixed != null) {
                return this.fixed.size();
            }
            return this.pooled != null ? this.pooled.size() : this.record.size;
        }

        /**
         * Index of the backing slab. Ordinary slabs use the ledger's slab index; a dedicated oversized slab is
         * numbered above the ordinary range, starting at the allocator's slab cap, and a fixed-page slab is
         * numbered above that range plus {@link SlabAllocator#FIXED_SLAB_INDEX_BASE}.
         */
        public int slab() {
            return this.record.slabIndex;
        }
    }
}
