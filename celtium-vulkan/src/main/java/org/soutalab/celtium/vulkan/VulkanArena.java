package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.data.MeshBufferSegment;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.Collection;

import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_TRANSFER_DST_BIT;
import static org.lwjgl.vulkan.VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT;

/**
 * Segment allocator over one device-local {@link VulkanBuffer}, mirroring the OpenGL arena
 * ({@code org.embeddedt.embeddium.impl.gl.arena.GlBufferArena}) that the shared mesh metadata already works with.
 * <p>
 * The free list is a doubly-linked list of byte ranges ordered by offset, exactly like the OpenGL one: allocation
 * uses <b>best fit</b> (an exact-size free range wins outright, otherwise the smallest free range that is large
 * enough), splitting from the end of the chosen range, and freeing marks the range free and merges it with the
 * adjacent free ranges so the list never contains two neighbours that are both free. {@code used} counts the bytes
 * handed out and {@code capacity} mirrors the buffer size, so {@link #deviceUsedMemoryL()} and
 * {@link #deviceAllocatedMemoryL()} report the same numbers the OpenGL arena reports.
 * <p>
 * Every size and offset is a byte count and a multiple of 4, because Vulkan requires 4-byte alignment for buffer
 * copies; {@link #allocate(int)} rounds a request up, which is what keeps {@link Segment#getOffset()} aligned.
 * <p>
 * <b>Growth contract.</b> When nothing fits, the arena grows its buffer (at least doubling, never below 512 KiB,
 * and always enough that the appended tail alone is a contiguous range of the requested size) and retries.
 * {@link VulkanBuffer#ensureCapacity(long)} copies the existing bytes into the larger buffer, so
 * <b>every live segment keeps its offset and length</b>: no segment is invalidated, no data has to be re-uploaded and
 * the byte ranges never move. What does change is the {@code VkBuffer} handle, so derived state that cached the
 * handle has to be rebuilt by the caller from the existing mod metadata - that is what
 * {@link #upload(Collection)}'s return value reports, and comparing {@link VulkanBuffer#buffer()} (the handle, not
 * the {@link VulkanBuffer} object, which survives a growth) across {@link #allocate(int)} calls gives the same
 * signal.
 * <p>
 * <b>Slab disposition (overhaul Stage 3).</b> The arena itself is not sub-allocated: it is one segment allocator
 * over ONE backing {@link VulkanBuffer}, and the offset contract above - mods cache segment offsets into this
 * single buffer - forbids splitting the arena across slab ranges. What Stage 3 changed is underneath: the
 * backing buffer sub-allocates from the context's device-local slab pool like every other
 * {@code VulkanBuffer}, so the arena no longer owns a {@code vkAllocateMemory} of its own. The doubling copy
 * on growth is <b>retained deliberately</b>: it is the mechanism that keeps every offset stable across a
 * growth, and removing it would mean either moving segments (breaking the contract) or a multi-buffer arena
 * (a different allocator, out of this stage's scope).
 * <p>
 * <b>Threading:</b> every method must be called on the render thread that owns the {@link VulkanContext}, and
 * {@link #delete()} must happen while the context is still alive.
 * <p>
 * <b>Upload census and content check.</b> Whether a mesh reaches the arena whole or only in part cannot be read off
 * the caller's buffers: those are CPU memory the game wrote, and the defect that reaches the frame is the transfer or
 * the offsets. Every {@link #write(int, long, int)} therefore accumulates what it wrote, and the next
 * {@link #buffer()} - the call the draw path makes right before it binds this arena - prints one line with the bytes,
 * the elements and this buffer's size, which is the pair of numbers that separates "the upload was complete" from
 * "the upload was short". With {@code -Dceltium.vulkan.arenaVerify=true} each write additionally copies its own
 * range back from the arena into host memory and compares it byte for byte with what it was given, so a partial or
 * stale transfer is reported as the offset and the byte that differ rather than inferred from the picture. Both are
 * diagnostics: the census costs a counter, and the check is off unless it is asked for.
 * <p>
 * The census line is paired with a {@code CELTIUM_ARENA_FRAGMENTATION} line (item 433): the same report
 * separates the arena's free space into holes stranded between live segments and the tail at the end of the
 * capacity, which is the split that decides whether unused headroom is fragmentation or growth slack. The
 * aggregation is a pure function over a segment-list snapshot ({@link #summarizeFragmentation}); the arena
 * only walks its list, and no allocation path consults the result.
 */
public final class VulkanArena {
    /** Growth never asks for less than this, so small arenas do not grow on every other allocation. */
    private static final int MINIMUM_GROWTH_BYTES = 512 * 1024;

    /**
     * One census line per upload burst (one frame). It used to be on by default "because it costs a counter and says
     * the numbers" - but on the operator's own session every one of those lines is a synchronous stdout write per
     * frame, and the log passed 100,000 lines. It is now on for
     * a diagnostics/validation run ({@link VulkanDiagnostics}) or when asked for explicitly.
     */
    private static final boolean CENSUS_ENABLED =
            Boolean.getBoolean("celtium.vulkan.arenaCensus") || VulkanDiagnostics.enabled();
    /** Off by default: the check drains the queue per write, which is a diagnostic cost, not a frame cost. */
    private static final boolean VERIFY_ENABLED = Boolean.getBoolean("celtium.vulkan.arenaVerify");
    /** Bytes of one write the check compares; a mesh is compared up to this prefix. */
    private static final int VERIFY_MAX_BYTES = Math.max(4, Integer.getInteger("celtium.vulkan.arenaVerifyBytes",
            64 * 1024).intValue());
    /** How many successful checks print a line before only failures speak. */
    private static final int VERIFY_OK_REPORTS = Integer.getInteger("celtium.vulkan.arenaVerifyOkReports", 8).intValue();

    /**
     * BH-01's acceptance, counted: how many segments were handed out with an offset on the stride, and how many were
     * not.
     * <p>
     * The check runs in {@link #allocate} whenever {@code -Dceltium.vulkan.arenaVerify=true} is set, so one ordinary
     * leg covers the initial capacity, every split, every reuse and every post-grow range without a GPU-side test of
     * its own. A violation is not counted and passed on: {@code Segment.getOffset()} divides the offset by the stride,
     * so an unaligned segment is geometry read from the wrong field boundary - the fault this finding is about - and it
     * throws with the numbers rather than drawing it. Before the fix the whole run would have been silent about it.
     */
    private static long alignmentChecks;
    private static long alignmentViolations;

    /** {@return BH-01's acceptance line}: how many segments were aligned, and how many were not. */
    public static String alignmentSummary() {
        return "arenaSegmentAlignChecks=" + alignmentChecks + " arenaSegmentAlignViolations=" + alignmentViolations;
    }

    private final VulkanContext context;
    /** {@code VK_BUFFER_USAGE_*} flags, kept so a census line can name what the arena holds. */
    private final int usageFlags;
    private VulkanBuffer buffer;
    private Segment head;
    private Segment tail;
    private Segment freeHead;
    private int liveSegmentCount;
    /** Fragmentation census snapshot scratch (see {@link #fragmentation()}); grown to the segment count, never shrunk. */
    private int[] fragmentationOffsets;
    private int[] fragmentationLengths;
    private boolean[] fragmentationFree;

    // Upload census: totals for the run, and the slice since the last report.
    private long uploads;
    private long uploadedBytes;
    private long writesSinceReport;
    private long bytesSinceReport;
    private long elementsSinceReport;
    private long firstReportedOffset = -1L;
    private int firstReportedLength;

    // Content check: the host-visible landing buffer, what has been compared and what disagreed.
    private VulkanHostBuffer verifyBuffer;
    private long verifyChecks;
    private long verifiedBytes;
    private long verifyFailures;
    private boolean verifyReported;

    /**
     * Bytes per allocated element. Segment offsets and lengths are reported in these units, matching the OpenGL
     * arena: the mod's per-section metadata multiplies an offset by the vertex count per arena element, so both
     * backends must express ranges the same way.
     */
    private final int stride;
    /**
     * The arena's allocation granularity in bytes: {@code lcm(4, stride)}.
     * <p>
     * BH-01: the capacity used to be rounded to 4 bytes and the free ranges were left at whatever the split produced,
     * while {@link Segment#getOffset()} divides the byte offset by the stride. With a 24-byte compact vertex and a
     * 524,288-byte initial capacity (524,288 mod 24 = 8) the last range started at a non-multiple of the stride, the
     * division truncated it, and the vertices were read from the wrong field boundary - silently wrong geometry rather
     * than a refusal. Every boundary of the arena - the capacity, both ends of every free range, and the tail appended
     * by a grow - is now a multiple of this unit, so a segment's offset is always a whole number of elements.
     * <p>
     * It is {@code lcm} and not the stride alone because the copy alignment Vulkan requires is 4 bytes: a stride of 6
     * (a hypothetical packed format) would otherwise leave boundaries that align to the format but not to the API.
     */
    private final int alignUnit;
    /** The pool name refusals and census lines report (see the named constructor). */
    private final String name;
    private int capacity;
    private int used;
    private boolean deleted;

    /**
     * Creates the arena together with its first buffer.
     *
     * @param context              owning context; the arena must be deleted before the context is
     * @param initialCapacityBytes initial buffer size, rounded up to a multiple of 4; must be positive
     * @param usageFlags           {@code VK_BUFFER_USAGE_*} flags for the buffer; the transfer flags are added by
     *                             {@link VulkanBuffer}
     */
    public VulkanArena(VulkanContext context, int initialCapacityBytes, int usageFlags) {
        this(context, initialCapacityBytes, usageFlags, 1);
    }

    public VulkanArena(VulkanContext context, int initialCapacityBytes, int usageFlags, int stride) {
        this(context, initialCapacityBytes, usageFlags, stride, "arena");
    }

    /**
     * @param name the pool name refusals and census lines report (Stage 0 port of the
     *             experimental pool naming, arch/oom-pool-id @285d67f3: an unnamed allocator's
     *             refusal could not be attributed to a pool, so the r10 leg's 32 KiB failure
     *             stayed anonymous; a named arena's refusal says which pool refused).
     */
    public VulkanArena(VulkanContext context, int initialCapacityBytes, int usageFlags, int stride,
            String name) {
        if (context == null) {
            throw new NullPointerException("context");
        }
        this.name = name == null ? "arena" : name;
        if (stride <= 0) {
            throw new IllegalArgumentException("Stride must be positive, was " + stride);
        }
        if (initialCapacityBytes <= 0) {
            throw new IllegalArgumentException("initialCapacityBytes must be positive, was " + initialCapacityBytes);
        }
        this.context = context;
        this.usageFlags = usageFlags;
        this.stride = stride;
        this.alignUnit = lcm4And(stride);
        this.capacity = alignUpTo(initialCapacityBytes, this.alignUnit);
        long started = System.nanoTime();
        this.buffer = new VulkanBuffer(context, this.capacity, usageFlags);
        arenaCreateCalls++;
        arenaCreateNanos += System.nanoTime() - started;
        this.head = new Segment(this, 0, this.capacity);
        this.tail = this.head;
        linkFree(this.head);
    }

    private void linkFree(Segment segment) {
        segment.free = true;
        segment.prevFree = null;
        segment.nextFree = this.freeHead;
        if (this.freeHead != null) {
            this.freeHead.prevFree = segment;
        }
        this.freeHead = segment;
    }

    private void unlinkFree(Segment segment) {
        segment.free = false;
        Segment pf = segment.prevFree;
        Segment nf = segment.nextFree;
        if (pf != null) {
            pf.nextFree = nf;
        } else if (this.freeHead == segment) {
            this.freeHead = nf;
        }
        if (nf != null) {
            nf.prevFree = pf;
        }
        segment.prevFree = null;
        segment.nextFree = null;
    }

    /** Arenas created, and the total time their buffers took to create - each is a buffer plus a device
     *  allocation, so a rebuild burst that opens many regions at once (a dimension switch is the fixture's
     *  case) pays the driver allocation cost in one frame. Read back by the backend summary. */
    static volatile long arenaCreateCalls;
    static volatile long arenaCreateNanos;

    /**
     * Allocates a range of at least {@code byteSize} bytes. The returned range is a multiple of 4 bytes long and
     * starts at an offset that is a multiple of 4.
     * <p>
     * The free list is searched first (best fit). When no free range fits, the buffer is grown and the search is
     * repeated; growth preserves the contents and does not move existing segments. Render thread only.
     *
     * @throws IllegalStateException when the arena is deleted or the buffer cannot be grown
     */
    public Segment allocate(int byteSize) {
        requireLive();
        if (byteSize <= 0) {
            throw new IllegalArgumentException("byteSize must be positive, was " + byteSize);
        }
        // Ranges are rounded to whole stride elements so that a later offset stays aligned for the vertex format,
        // exactly like the OpenGL arena, whose allocation unit is the stride.
        int needed = alignUpTo(alignUp4Int(byteSize), this.alignUnit);
        Segment free = findFree(needed);
        if (free == null) {
            grow(needed);
            free = findFree(needed);
            if (free == null) {
                // Unreachable: growth always appends a free tail of at least `needed` bytes.
                throw new IllegalStateException("The Vulkan arena '" + this.name + "' grew to " + this.capacity
                        + " bytes but still has no contiguous free range of " + needed
                        + " bytes (largestFreeRun=" + largestFreeRun() + ").");
            }
        }
        Segment result;
        // The exact-fit branch may only hand out the range whole when its own start is on the alignment unit. A free
        // tail appended by a grow starts at the previous capacity, and the buffer's real size is a multiple of the
        // driver's alignment rather than of this arena's unit, so an unaligned start is reachable on a second grow; the
        // split branch below then trims the range down to an aligned element instead of trusting its offset.
        if (free.length == needed && free.offset % this.alignUnit == 0) {
            unlinkFree(free);
            result = free;
        } else {
            // Split from the end so the chosen range keeps its offset and the two ranges stay adjacent. BH-01: the
            // split point is pulled down to the arena's alignment unit, so the new range starts at a whole element and
            // the free range in front of it keeps a length that is a multiple of that unit. Without this the remainder
            // of an unaligned capacity landed at the *start* of every later range, and getOffset()'s division dropped
            // it - the vertices were then read one field out of step.
            int start = splitStart(free.offset, free.length, needed, this.alignUnit);
            int alignedNeeded = free.end() - start;
            result = new Segment(this, start, alignedNeeded);
            result.prev = free;
            result.next = free.next;
            if (result.next != null) {
                result.next.prev = result;
            } else {
                this.tail = result;
            }
            free.length = start - free.offset;
            free.next = result;
        }
        this.liveSegmentCount++;
        this.used += result.length;
        if (VERIFY_ENABLED) {
            verifyAlignment(result);
        }
        return result;
    }

    /**
     * BH-01's check: a segment must start on a whole stride element and be a whole number of them long.
     * <p>
     * {@link Segment#getOffset()} is {@code byteOffset / stride}, so an offset that is not a multiple of the stride is
     * silently truncated and the vertices are read from the wrong field boundary. That is the defect this finding
     * names, so the check throws rather than returning: a caller that wanted geometry and got a misaligned range must
     * not draw it. Counted either way, so the acceptance line says how many segments were examined.
     */
    private void verifyAlignment(Segment segment) {
        alignmentChecks++;
        if (segment.offset % this.stride != 0) {
            alignmentViolations++;
            throw new IllegalStateException("BH-01: arena segment starts at byte " + segment.offset
                    + ", which is not a multiple of the stride " + this.stride
                    + " (offset/stride would truncate to " + (segment.offset / this.stride) + "; capacity="
                    + this.capacity + ", alignUnit=" + this.alignUnit + ")");
        }
        if (segment.length % this.alignUnit != 0) {
            alignmentViolations++;
            throw new IllegalStateException("BH-01: arena segment at byte " + segment.offset + " is " + segment.length
                    + " bytes long, which is not a multiple of the arena's unit " + this.alignUnit
                    + " (stride=" + this.stride + ")");
        }
    }

    /**
     * Uploads one native byte range into arena memory at a byte offset.
     * <p>
     * Counted for the census, and - when {@code -Dceltium.vulkan.arenaVerify=true} - copied back and compared with the
     * source bytes before this returns. Render thread only.
     */
    public void write(int offsetBytes, long sourceAddress, int length) {
        requireLive();
        requireRange(offsetBytes, length);
        if (length == 0) {
            return;
        }
        this.buffer.write(offsetBytes, sourceAddress, length);
        recordUpload(offsetBytes, length);
        if (VERIFY_ENABLED) {
            verifyRange(offsetBytes, sourceAddress, length);
        }
    }

    /**
     * Uploads the remaining bytes of a heap buffer into arena memory at a byte offset. The source buffer's position
     * is left untouched. Render thread only.
     * <p>
     * Counted like {@link #write(int, long, int)}; the content check needs a native source address and stays with the
     * address form, which is the one the terrain upload uses.
     */
    public void write(int offsetBytes, ByteBuffer source) {
        requireLive();
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        int length = source.remaining();
        requireRange(offsetBytes, length);
        if (length == 0) {
            return;
        }
        this.context.uploadToBuffer(this.buffer, offsetBytes, source, 0);
        recordUpload(offsetBytes, length);
    }

    /**
     * Writes every upload into the arena.
     * <p>
     * An upload with a null {@link Upload#segment} asks the arena to place it: the arena allocates a segment, stores
     * it in the upload and may have to grow the buffer to do so. Render thread only.
     *
     * @return {@code true} when the underlying buffer was recreated (grown) during this call, so the caller has to
     *         refresh state that cached the old {@code VkBuffer} handle; the segment offsets themselves stay valid
     */
    public boolean upload(Collection<Upload> uploads) {
        requireLive();
        if (uploads == null) {
            throw new IllegalArgumentException("uploads must not be null");
        }
        // Recording the handle before any work is what detects a reallocation. The VulkanBuffer object survives a
        // growth (it recreates the VkBuffer inside itself), so the handle has to be compared, not the object.
        VulkanBuffer beforeBuffer = this.buffer;
        long beforeHandle = beforeBuffer.buffer();
        for (Upload upload : uploads) {
            if (upload == null) {
                throw new IllegalArgumentException("uploads must not contain null elements");
            }
            if (upload.length < 0) {
                throw new IllegalArgumentException("An upload length must not be negative, was " + upload.length);
            }
            if (upload.length == 0) {
                continue;
            }
            Segment segment = upload.segment;
            if (segment == null) {
                segment = allocate(upload.length);
                upload.segment = segment;
            } else if (segment.arena != this) {
                throw new IllegalArgumentException("An upload segment belongs to a different arena.");
            } else if (upload.length > segment.length) {
                throw new IllegalArgumentException("An upload of " + upload.length
                        + " bytes does not fit its segment of " + segment.length + " bytes.");
            }
            this.buffer.write(segment.offset, upload.sourceAddress, upload.length);
            recordUpload(segment.offset, upload.length);
            if (VERIFY_ENABLED) {
                verifyRange(segment.offset, upload.sourceAddress, upload.length);
            }
        }
        return this.buffer != beforeBuffer || this.buffer.buffer() != beforeHandle;
    }

    /** Bytes currently handed out to live segments. */
    public long deviceUsedMemoryL() {
        return this.used;
    }

    /** Bytes currently allocated on the GPU, which is the current buffer size. */
    public long deviceAllocatedMemoryL() {
        return this.capacity;
    }

    /** Number of buffers the arena owns: one while it is alive, zero after {@link #delete()}. */
    public int bufferCount() {
        return this.deleted ? 0 : 1;
    }

    /** Whether no live segment is allocated. */
    /** {@return this arena's pool name, as reported by refusals and census lines}. */
    public String name() {
        return this.name;
    }

    /**
     * {@return the largest single contiguous free range in bytes, holes and tail alike}.
     * The free list is merged on every free, so each entry is already maximal and the walk is
     * exact. Stage 0 port of the experimental {@code largestFreeRun} instrument
     * (arch/oom-pool-id @285d67f3); no allocation path consults it.
     */
    public long largestFreeRun() {
        long largest = 0L;
        for (Segment segment = this.freeHead; segment != null; segment = segment.nextFree) {
            if (segment.length > largest) {
                largest = segment.length;
            }
        }
        return largest;
    }

    public boolean isEmpty() {
        return this.used <= 0;
    }

    /**
     * The buffer the segments live in. Its handle changes on every growth, so callers that cache it must compare it
     * across allocations.
     * <p>
     * This is the call the draw path makes immediately before it binds the arena, so it is also where the upload
     * census is reported: whatever was written since the previous bind belongs to the frame that is starting, and the
     * one line it prints is what says whether that frame's terrain upload was complete.
     *
     * @throws IllegalStateException when the arena has been deleted
     */
    public VulkanBuffer buffer() {
        requireLive();
        reportUploadCensus();
        return this.buffer;
    }

    /** Writes uploaded since this arena was created. */
    public long uploadCount() {
        return this.uploads;
    }

    /** Bytes uploaded since this arena was created. */
    public long uploadedBytes() {
        return this.uploadedBytes;
    }

    /** Bytes the content check has compared, and the writes it found disagreeing. Zero while the check is off. */
    public long verifiedBytes() {
        return this.verifiedBytes;
    }

    public long verifyFailures() {
        return this.verifyFailures;
    }

    /**
     * Counts one write into this arena and remembers the first range of the current burst, so the census line can be
     * paired with the {@code firstDraw} line the backend prints for the same frame.
     */
    private void recordUpload(int offsetBytes, int length) {
        long elements = length / (long) this.stride;
        this.uploads++;
        this.uploadedBytes += length;
        this.writesSinceReport++;
        this.bytesSinceReport += length;
        this.elementsSinceReport += elements;
        if (this.firstReportedOffset < 0L) {
            this.firstReportedOffset = offsetBytes;
            this.firstReportedLength = length;
        }
    }

    /**
     * Prints one line per upload burst - in practice one per frame - with the numbers that separate a complete upload
     * from a partial one: how many bytes and elements went in since the last bind, the first range they went to, and
     * how big this arena is now. Silence when nothing was written, because then there is nothing to say.
     */
    private void reportUploadCensus() {
        if (!CENSUS_ENABLED || this.writesSinceReport == 0L) {
            return;
        }
        System.out.println("[Celtium] CELTIUM_ARENA_UPLOAD kind=" + kind() + " stride=" + this.stride
                + " writes=" + this.writesSinceReport + " bytes=" + this.bytesSinceReport
                + (isVertexArena() ? " vertices=" : " indices=") + this.elementsSinceReport
                + " first=[offset=" + this.firstReportedOffset + " bytes=" + this.firstReportedLength + "]"
                + " arenaBytes=" + this.capacity + " used=" + this.used + " segments=" + liveSegments()
                + " totalWrites=" + this.uploads + " totalBytes=" + this.uploadedBytes
                + (VERIFY_ENABLED ? " verified=" + this.verifiedBytes + " verifyFailures=" + this.verifyFailures : ""));
        System.out.println("[Celtium] " + fragmentationLine());
        this.writesSinceReport = 0L;
        this.bytesSinceReport = 0L;
        this.elementsSinceReport = 0L;
        this.firstReportedOffset = -1L;
        this.firstReportedLength = 0;
    }

    /**
     * How this arena's free space is shaped right now: the split the D6 record asked for (item 433).
     * <p>
     * The upload census says how much of the arena is used, but the 40/13 MiB headroom question - is the
     * unused part stranded between live segments (fragmentation, an allocator-strategy problem) or sitting
     * at the end (growth slack, no problem at all) - needs the free ranges separated into holes and the
     * tail. This walks the segment list once and changes nothing: no allocation path consults it.
     */
    public Fragmentation fragmentation() {
        int count = 0;
        for (Segment segment = this.head; segment != null; segment = segment.next) {
            count++;
        }
        fillFragmentationScratch(count);
        return summarizeFragmentation(this.capacity, this.fragmentationOffsets, this.fragmentationLengths,
                this.fragmentationFree, count);
    }

    /**
     * Fills this arena's snapshot scratch with the first {@code count} segments, growing the arrays when
     * the segment list outgrows them. The scratch replaces three fresh arrays per census call: the census
     * runs from {@code buffer()} on the recording path whenever it is enabled, and the snapshot is consumed
     * by {@code summarizeFragmentation} before {@link #fragmentation()} returns, so reuse cannot alias
     * anything live. Lifetime owner: this arena instance, confined to the same thread as the segment list
     * it snapshots (the list itself is unsynchronized).
     */
    private void fillFragmentationScratch(int count) {
        if (this.fragmentationOffsets == null || this.fragmentationOffsets.length < count) {
            this.fragmentationOffsets = new int[count];
            this.fragmentationLengths = new int[count];
            this.fragmentationFree = new boolean[count];
        }
        int index = 0;
        for (Segment segment = this.head; segment != null && index < count; segment = segment.next) {
            this.fragmentationOffsets[index] = segment.offset;
            this.fragmentationLengths[index] = segment.length;
            this.fragmentationFree[index] = segment.free;
            index++;
        }
    }

    /** {@return the {@code CELTIUM_ARENA_FRAGMENTATION} line for this arena's current shape}. */
    public String fragmentationLine() {
        return fragmentation().line();
    }

    /**
     * The fragmentation instrument's core: a pure aggregation over a segment-list snapshot, so the counting
     * can be pinned without a device (the arena's constructor requires one).
     * <p>
     * The snapshot is the arena's ranges in offset order, covering {@code [0, capacity)} contiguously - the
     * arena's own invariant, since freeing merges adjacent free ranges and growth appends at the tail. The
     * <b>tail</b> is the one free range that reaches the end of the capacity (the last range, when it is
     * free); every other free range is a <b>hole</b>. Because the free list is merged, each free range is
     * already maximal, so the counts are unique. The identity
     * {@code usedBytes + holeBytes + tailBytes == capacity} holds for every snapshot the arena can produce;
     * a snapshot that breaks it indicts the allocator, not this arithmetic, and the tests pin the identity
     * alongside the split.
     *
     * @param capacity the arena's capacity in bytes
     * @param offsets  each range's start, in offset order
     * @param lengths  each range's length, parallel to {@code offsets}
     * @param free     each range's free flag, parallel to {@code offsets}
     */
    static Fragmentation summarizeFragmentation(int capacity, int[] offsets, int[] lengths, boolean[] free) {
        if (offsets == null || lengths == null || free == null
                || lengths.length != offsets.length || free.length != offsets.length) {
            throw new IllegalArgumentException("A fragmentation snapshot needs parallel offset/length/free arrays");
        }
        return summarizeFragmentation(capacity, offsets, lengths, free, offsets.length);
    }

    /**
     * The count-bounded form: only the first {@code count} snapshot entries are ranges; anything past them
     * in the arrays is caller scratch and is not read. The tail test uses {@code count - 1}, not the array
     * length, so a grown scratch array cannot promote a stale entry into the tail.
     */
    static Fragmentation summarizeFragmentation(int capacity, int[] offsets, int[] lengths, boolean[] free,
            int count) {
        if (offsets == null || lengths == null || free == null
                || count < 0 || count > offsets.length || count > lengths.length || count > free.length) {
            throw new IllegalArgumentException("A fragmentation snapshot needs parallel offset/length/free arrays");
        }
        long used = 0L;
        long holeBytes = 0L;
        long largestHole = 0L;
        int holes = 0;
        long tail = 0L;
        for (int i = 0; i < count; i++) {
            if (!free[i]) {
                used += lengths[i];
            } else if (i == count - 1 && (long) offsets[i] + lengths[i] == capacity) {
                tail = lengths[i];
            } else {
                holes++;
                holeBytes += lengths[i];
                if (lengths[i] > largestHole) {
                    largestHole = lengths[i];
                }
            }
        }
        return new Fragmentation(capacity, used, holeBytes, holes, largestHole, tail);
    }

    /** One fragmentation reading: the arena's used bytes, its holes and its tail, as separate numbers. */
    public static final class Fragmentation {
        /** The arena's capacity in bytes. */
        public final int capacity;
        /** Bytes handed out to live segments. */
        public final long usedBytes;
        /** Bytes in free ranges that do not reach the end of the capacity. */
        public final long holeBytes;
        /** How many hole ranges there are (the free list is merged, so each is maximal). */
        public final int holeCount;
        /** The largest single hole in bytes, or 0 when there are no holes. */
        public final long largestHoleBytes;
        /** Bytes in the free range that reaches the end of the capacity, or 0 when the last range is used. */
        public final long tailBytes;
        /**
         * The largest single contiguous free range, holes and tail alike (Stage 0 port of the
         * experimental {@code largestFreeRun} instrument, arch/oom-pool-id @285d67f3: there the
         * split mattered because one ledger's 15.3 MB free run masked another ledger's 16 KiB
         * exhaustion; here the same figure separates "no room anywhere" from "room, but not
         * contiguous" without walking the segment list a second time).
         */
        public final long largestFreeRunBytes;

        private Fragmentation(int capacity, long usedBytes, long holeBytes, int holeCount,
                long largestHoleBytes, long tailBytes) {
            this.capacity = capacity;
            this.usedBytes = usedBytes;
            this.holeBytes = holeBytes;
            this.holeCount = holeCount;
            this.largestHoleBytes = largestHoleBytes;
            this.tailBytes = tailBytes;
            this.largestFreeRunBytes = Math.max(largestHoleBytes, tailBytes);
        }

        /** {@return the census line for this reading}. */
        public String line() {
            return "CELTIUM_ARENA_FRAGMENTATION capacity=" + this.capacity + " used=" + this.usedBytes
                    + " holes=" + this.holeCount + " holeBytes=" + this.holeBytes
                    + " largestHole=" + this.largestHoleBytes + " tailBytes=" + this.tailBytes
                    + " largestFreeRun=" + this.largestFreeRunBytes;
        }
    }

    /**
     * Copies the just-written range back out of the arena and compares it with the bytes the caller handed in.
     * <p>
     * This is the one measurement that separates the two ways a mesh can arrive incomplete: the arena holds what the
     * upload was given (the defect is elsewhere, in an offset or in the draw) or it does not (the transfer is the
     * defect, and the first differing byte says where). Gated by {@code -Dceltium.vulkan.arenaVerify=true} because it
     * drains the queue per write.
     */
    private void verifyRange(int offsetBytes, long sourceAddress, int length) {
        int checked = Math.min(length, VERIFY_MAX_BYTES);
        try {
            ensureVerifyBuffer(checked);
            this.buffer.readInto(this.verifyBuffer, offsetBytes, checked);
            long mapped = this.verifyBuffer.mappedAddress();
            int firstDifference = -1;
            for (int i = 0; i < checked; i++) {
                if (MemoryUtil.memGetByte(mapped + i) != MemoryUtil.memGetByte(sourceAddress + i)) {
                    firstDifference = i;
                    break;
                }
            }
            this.verifyChecks++;
            this.verifiedBytes += checked;
            if (firstDifference < 0) {
                if (this.verifyChecks <= VERIFY_OK_REPORTS) {
                    System.out.println("[Celtium] CELTIUM_ARENA_VERIFY ok offset=" + offsetBytes + " checked=" + checked
                            + " of=" + length + " matches checks=" + this.verifyChecks
                            + " bytes=" + this.verifiedBytes + " arenaBytes=" + this.capacity);
                }
                return;
            }
            this.verifyFailures++;
            if (!this.verifyReported) {
                this.verifyReported = true;
                System.out.println("[Celtium] CELTIUM_ARENA_VERIFY MISMATCH offset=" + offsetBytes
                        + " firstDifference=" + firstDifference + " of=" + checked
                        + " expected=0x" + Integer.toHexString(MemoryUtil.memGetByte(sourceAddress + firstDifference) & 0xFF)
                        + " arena=0x" + Integer.toHexString(MemoryUtil.memGetByte(mapped + firstDifference) & 0xFF)
                        + " checked=" + checked + " of=" + length + " writes=" + this.uploads
                        + " arenaBytes=" + this.capacity);
            }
        } catch (RuntimeException | LinkageError error) {
            // A diagnostic that throws must not take the frame down; the census above still reports the sizes.
            if (!this.verifyReported) {
                this.verifyReported = true;
                System.out.println("[Celtium] CELTIUM_ARENA_VERIFY UNAVAILABLE offset=" + offsetBytes + " bytes=" + length
                        + " error=" + error);
            }
        }
    }

    private void ensureVerifyBuffer(int bytes) {
        if (this.verifyBuffer == null) {
            this.verifyBuffer = new VulkanHostBuffer(this.context, bytes, VK_BUFFER_USAGE_TRANSFER_DST_BIT);
        } else if (this.verifyBuffer.size() < bytes) {
            this.verifyBuffer.ensureCapacity(bytes);
        }
    }

    /** How many segments are handed out right now, tracked incrementally across allocate/release. */
    private int liveSegments() {
        return this.liveSegmentCount;
    }

    private boolean isVertexArena() {
        return (this.usageFlags & VK_BUFFER_USAGE_VERTEX_BUFFER_BIT) != 0;
    }

    private String kind() {
        if (isVertexArena()) {
            return "vertex";
        }
        return (this.usageFlags & VK_BUFFER_USAGE_INDEX_BUFFER_BIT) != 0 ? "index" : "buffer";
    }

    /**
     * Deletes the buffer and releases every segment. Idempotent. Segments that are still held by the caller become
     * inert: their {@link Segment#delete()} is a no-op and their offsets must not be used any more. Render thread
     * only.
     */
    public void delete() {
        if (this.deleted) {
            return;
        }
        this.deleted = true;
        Segment segment = this.head;
        while (segment != null) {
            Segment next = segment.next;
            segment.free = true;
            segment.offset = 0;
            segment.length = 0;
            segment.prev = null;
            segment.next = null;
            segment.prevFree = null;
            segment.nextFree = null;
            segment = next;
        }
        this.head = null;
        this.tail = null;
        this.freeHead = null;
        this.liveSegmentCount = 0;
        this.used = 0;
        this.capacity = 0;
        if (this.verifyBuffer != null) {
            this.verifyBuffer.delete();
            this.verifyBuffer = null;
        }
        this.buffer.delete();
    }

    /**
     * Releases a segment back to the free list and merges it with its free neighbours. Called by
     * {@link Segment#delete()}; calling it twice for the same segment is a no-op.
     */
    void free(Segment segment) {
        if (segment.arena != this) {
            throw new IllegalArgumentException("The segment belongs to a different arena.");
        }
        if (this.deleted || segment.free || segment.retiring) {
            return;
        }
        // Not reusable yet: the frame being recorded may already have drawn from this range, and with batched frame
        // submits the upload batch that would refill it reaches the queue before that frame's own command buffer -
        // so the draw would read the new owner's bytes for one frame. That is the stained-glass flicker: a
        // translucent re-sort frees and reallocates index ranges every time the camera moves. The range goes back to
        // the free list once the submission that could name it has completed (the retired-cleanup contract).
        segment.retiring = true;
        this.retiringSegments++;
        this.context.celtium$retireCleanup("arena.free", segment);
    }

    /** Segments freed but not yet reusable; see {@link #free}. */
    private long retiringSegments;

    /** The free-list half of {@link #free}, run once nothing in flight can read the range. */
    private void release(Segment segment) {
        segment.retiring = false;
        this.retiringSegments--;
        if (this.deleted || segment.free) {
            return;
        }
        this.liveSegmentCount--;
        this.used -= segment.length;
        linkFree(segment);
        if (segment.next != null && segment.next.free) {
            segment.merge(segment.next);
        }
        if (segment.prev != null && segment.prev.free) {
            segment.prev.merge(segment);
        }
    }

    /**
     * Grows the buffer so it can hold the used bytes plus {@code needed} more.
     * <p>
     * Doubling is the normal case. The {@code capacity + needed} term matters when a single request is larger than
     * the whole current buffer: doubling alone would then leave a tail that is too small and no free range large
     * enough, so growth would never succeed.
     */
    private void grow(int needed) {
        long target = growthTarget(this.capacity, needed, this.alignUnit);
        if (target > Integer.MAX_VALUE) {
            throw new OutOfMemoryError("The Vulkan arena '" + this.name + "' cannot grow beyond "
                    + Integer.MAX_VALUE + " bytes (used " + this.used + " bytes, requested " + needed
                    + " bytes, largestFreeRun=" + largestFreeRun() + ").");
        }
        int oldCapacity = this.capacity;
        this.buffer.ensureCapacity(target);
        // Read the real size back instead of assuming the request: VulkanBuffer owns the exact allocation size.
        this.capacity = (int) this.buffer.size();
        appendFreeTail(oldCapacity, this.capacity - oldCapacity);
    }

    /**
     * The capacity a grow aims for: at least double, at least {@code capacity + needed}, at least the minimum
     * growth - and rounded up to the arena's alignment unit.
     * <p>
     * The rounding is the point: {@link VulkanBuffer#ensureCapacity(long)} rounds only to 4 bytes, and
     * {@link #MINIMUM_GROWTH_BYTES} is not a multiple of every stride's unit (524,288 mod 24 = 8, and likewise
     * for 28 and 36), so the raw maximum could leave the capacity off the unit. The tail a grow appends then
     * ends off the unit, and a later split of that tail derives a start below the range it splits - the
     * allocator corruption {@link #splitStart} now refuses. BH-01's invariant ("every boundary is a multiple
     * of the unit") held for the constructor's capacity but not for this one; it holds for both now.
     */
    static long growthTarget(int capacity, int needed, int alignUnit) {
        long target = Math.max(Math.max((long) capacity * 2L, (long) capacity + needed),
                MINIMUM_GROWTH_BYTES);
        if (alignUnit > 1) {
            target = (target + alignUnit - 1) / alignUnit * (long) alignUnit;
        }
        return target;
    }

    /**
     * Where the split of a free range for {@code needed} bytes begins: the range's end pulled down to the
     * alignment unit, so the handed-out segment starts on a whole element.
     * <p>
     * The start must stay inside the range. A start below the range's offset would make the new segment
     * overlap the live segment in front of it and drive the remainder's length negative - allocator
     * corruption that then propagates through every later merge. Growth and construction keep every boundary
     * on the unit, which makes a below-range start unreachable; the check exists so a future break of that
     * invariant fails loudly here instead of corrupting geometry silently.
     *
     * @throws IllegalStateException when the aligned start would fall below the free range's offset
     */
    static int splitStart(int freeOffset, int freeLength, int needed, int alignUnit) {
        int start = alignDownTo(freeOffset + freeLength - needed, alignUnit);
        if (start < freeOffset) {
            throw new IllegalStateException("Vulkan arena split of the free range at " + freeOffset
                    + " (" + freeLength + " bytes) for " + needed + " bytes would start at " + start
                    + ", below the range: a boundary is off the arena's alignment unit " + alignUnit
                    + ", so the segment would overlap its predecessor and the remainder would go negative.");
        }
        return start;
    }

    /**
     * Appends the newly added tail of the buffer to the free list. The old layout covers
     * {@code [0, oldCapacity)} contiguously, so the tail starts exactly at {@code oldCapacity} and live segments
     * keep their offsets.
     */
    private void appendFreeTail(int offset, int length) {
        if (length <= 0) {
            return;
        }
        Segment last = this.tail;
        if (last == null) {
            Segment tailSeg = new Segment(this, offset, length);
            this.head = tailSeg;
            this.tail = tailSeg;
            linkFree(tailSeg);
            return;
        }
        if (last.free) {
            last.length += length;
            return;
        }
        Segment tailSeg = new Segment(this, offset, length);
        last.next = tailSeg;
        tailSeg.prev = last;
        this.tail = tailSeg;
        linkFree(tailSeg);
    }

    /** Best-fit search with an exact-size short circuit; returns null when no free range is large enough. */
    private Segment findFree(int size) {
        Segment entry = this.freeHead;
        Segment best = null;
        while (entry != null) {
            if (entry.length == size) {
                return entry;
            }
            if (entry.length > size && (best == null || best.length > entry.length)) {
                best = entry;
            }
            entry = entry.nextFree;
        }
        return best;
    }

    private void requireRange(int offsetBytes, int length) {
        if (offsetBytes < 0) {
            throw new IllegalArgumentException("offsetBytes must not be negative, was " + offsetBytes);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative, was " + length);
        }
        if (length > this.capacity - offsetBytes) {
            throw new IllegalArgumentException("A write of " + length + " bytes at offset " + offsetBytes
                    + " exceeds the arena capacity of " + this.capacity + " bytes.");
        }
    }

    private void requireLive() {
        if (this.deleted) {
            throw new IllegalStateException("This VulkanArena has been deleted.");
        }
    }

    private static int alignUp4Int(int value) {
        long aligned = VulkanContext.alignUp4(value);
        if (aligned > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A size of " + value + " bytes is too large for a Vulkan arena.");
        }
        return (int) aligned;
    }

    /** {@code lcm(4, stride)}, bounded to what an int can hold. BH-01: the arena's boundary unit. */
    private static int lcm4And(int stride) {
        int a = 4;
        int b = stride;
        while (b != 0) {
            int t = a % b;
            a = b;
            b = t;
        }
        long lcm = (4L / a) * (long) stride;
        if (lcm > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A stride of " + stride + " bytes is too large for a Vulkan arena.");
        }
        return (int) lcm;
    }

    /** Rounds {@code value} up to the next multiple of {@code unit}, which is assumed positive. */
    private static int alignUpTo(int value, int unit) {
        if (unit <= 1) {
            return value;
        }
        long aligned = ((long) value + unit - 1) / unit * unit;
        if (aligned > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("A size of " + value + " bytes is too large for a Vulkan arena.");
        }
        return (int) aligned;
    }

    /** Rounds {@code value} down to a multiple of {@code unit}, which is assumed positive. */
    private static int alignDownTo(int value, int unit) {
        if (unit <= 1) {
            return value;
        }
        return value / unit * unit;
    }

    /**
     * One write into the arena: the range it goes into, the native bytes it copies and how many.
     * <p>
     * A null {@link #segment} asks the arena to place the upload itself, which is the only case in which
     * {@link #upload(Collection)} can grow the buffer; the arena then fills the field in with the segment it
     * allocated so the caller can store it in the shared mesh metadata.
     */
    public static final class Upload {
        /** Target range, or null to let the arena allocate one. */
        public Segment segment;
        /** Native address the bytes are copied from. */
        public final long sourceAddress;
        /** Number of bytes to copy. */
        public final int length;

        public Upload(Segment segment, long sourceAddress, int length) {
            if (length < 0) {
                throw new IllegalArgumentException("length must not be negative, was " + length);
            }
            if (length > 0 && sourceAddress == 0L) {
                throw new IllegalArgumentException("sourceAddress must be a non-null native address");
            }
            this.segment = segment;
            this.sourceAddress = sourceAddress;
            this.length = length;
        }
    }

    /**
     * A range of the arena's buffer, shared with the mod's mesh metadata through {@link MeshBufferSegment}. Offsets
     * and lengths are reported in {@link VulkanArena#stride} units, exactly like the OpenGL arena, because the
     * mod's metadata converts them into vertex and index positions with the same arithmetic for both backends. The
     * byte range never changes while the segment is alive - the arena grows by copying data in place.
     */
    public static final class Segment implements MeshBufferSegment, Runnable {
        private final VulkanArena arena;
        private int offset;
        private int length;
        private boolean free;
        /** Freed by its owner, waiting for the submission that could still read it; see {@link VulkanArena#free}. */
        private boolean retiring;
        private Segment prev;
        private Segment next;
        private Segment prevFree;
        private Segment nextFree;

        private Segment(VulkanArena arena, int offset, int length) {
            this.arena = arena;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public void run() {
            this.arena.release(this);
        }

        @Override
        public int getOffset() {
            return this.offset / this.arena.stride;
        }

        @Override
        public int getLength() {
            return this.length / this.arena.stride;
        }

        /** First byte of this range. Used for writing into the buffer; the metadata uses {@link #getOffset()}. */
        public int byteOffset() {
            return this.offset;
        }

        /** Length of this range in bytes. */
        public int byteLength() {
            return this.length;
        }

        /** Returns the range to the arena. Idempotent, and a no-op once the arena has been deleted. */
        @Override
        public void delete() {
            this.arena.free(this);
        }

        /** Offset one past the last byte of this range. */
        private int end() {
            return this.offset + this.length;
        }

        /** Absorbs the following range, which must be free, into this one. */
        private void merge(Segment other) {
            this.arena.unlinkFree(other);
            this.length += other.length;
            this.next = other.next;
            if (this.next != null) {
                this.next.prev = this;
            } else {
                this.arena.tail = this;
            }
            other.prev = null;
            other.next = null;
        }

        @Override
        public String toString() {
            return "VulkanArena.Segment[offset=" + this.offset + ", length=" + this.length
                    + (this.free ? ", free]" : ", used]");
        }
    }
}
