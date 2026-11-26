package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VK10;

/**
 * The immediate path's vertex and index memory, suballocated per group and reset per frame (OPT-05).
 * <p>
 * Before this class every immediate group allocated its own {@code VkBuffer}, {@code VkDeviceMemory} and mapping -
 * two of each, a 64 KiB vertex stream and a 32 KiB index stream - and freed them when the group was retired (BH-28).
 * A group is a draw packet, not an owner of device memory: its geometry is written while the frame is being built and
 * read once when that frame's submission runs, so the memory can be a slice of a bounded arena.
 * <p>
 * <b>Reuse is decided by completion, never by a frame number.</b> The arena holds {@link #SLOT_COUNT} slots and gives
 * one to each frame. The frame records the completion epoch its submission must reach
 * ({@link #submitted(long, long)}); the slot is reset only once that epoch has completed <em>and</em> every group
 * holding a slice of it has been released ({@link Region#release()}). {@link VulkanContext#completedEpoch()} is that
 * epoch and is advanced by the fence waits that confirm a submission - including a lost device, where nothing is
 * executing any more. Today the renderer waits for each window's submission inside {@code submitImmediateWindow}, so a
 * slot is normally free by the time the next frame asks for one; when OPT-08 allows frames in flight, the same rule
 * keeps the arena correct without a change here.
 * <p>
 * <b>Growth is bounded.</b> A slot's two buffers start small and double up to a fixed cap. A group that does not fit
 * even at the cap is not dropped and does not grow the arena without limit: the mesh promotes itself to buffers it
 * owns for that one group ({@code VulkanDynamicMesh.celtium$promoteFromArena}), and the promotion is counted. A frame
 * that finds both slots still held (a group waiting for another render target) is likewise not reset - the arena
 * reports the opening as blocked and the caller falls back - so a slot is never recycled while its groups are alive.
 * <p>
 * <b>Two backings, one region contract (Stage 2).</b> A region's slices can live in this arena's own slot buffers
 * (above) or in the per-frame linear ring ({@link RingRegion}): the backend asks for a ring-backed mesh first and
 * falls back to an arena region, then to an owning mesh, in that order. The ring is where frame-lifetime geometry
 * belongs in the A-to-B design - its bump-only slices cannot fragment a persistent allocator (the tick383 shape) -
 * while this arena remains the fallback for groups the ring cannot serve: a full ring slot answers a reservation
 * with 0 exactly like a full arena slot does, and the mesh's promotion path takes over unchanged. The lifetime
 * rules differ in mechanism but not in force: an arena slot is reset only when its submission completed and its
 * groups were released; a ring slot is reset only when its frame-slot wait confirmed completion, and a sealed
 * ring region holds its slot's reset off until the group is deleted.
 * <p>
 * Threading: render thread only, like the rest of the backend.
 */
public final class VulkanFrameArena {
    /**
     * One slot per frame the submission scheduler lets be in flight, plus the one being recorded. The completion gate,
     * not the count, is what makes reuse correct - but the count decides whether reuse is possible: with two slots and
     * up to {@link FrameSubmissionScheduler#FRAME_SLOTS} frames in flight, a CPU that runs more than one frame ahead
     * found both slots held and every group fell back to an owning mesh with its own {@code vkAllocateMemory}
     * (leg {@code tickjfr-celtium-3}: 23,494 blocked openings, the allocation 695 of 2,831 client-thread samples).
     */
    private static final int SLOT_COUNT = FrameSubmissionScheduler.FRAME_SLOTS + 1;
    /** Every slice starts on this boundary: it satisfies the 4-byte rule for vertex attributes and 32-bit indices. */
    private static final int SLICE_ALIGNMENT = 16;
    // The initial sizes are properties so the growth path (HostBuffer.ensureCapacity) can be crossed on demand: on the
    // pinned validation scene a frame peaks at 781 KB of vertices and 215 KB of indices, which never reaches the 1 MiB
    // and 256 KiB defaults, so the audit's B05 acceptance (a submit before and after the growth) is unreachable with
    // them. Lowering these in a leg crosses the same code path with the same submits; the defaults are unchanged.
    private static final long INITIAL_VERTEX_BYTES =
            Math.max(1L << 12, Long.getLong("celtium.vulkan.frameArenaInitialVertexBytes", 1L << 20).longValue());
    private static final long INITIAL_INDEX_BYTES =
            Math.max(1L << 12, Long.getLong("celtium.vulkan.frameArenaInitialIndexBytes", 1L << 18).longValue());
    private static final long MAX_VERTEX_BYTES = 16L << 20;
    private static final long MAX_INDEX_BYTES = 4L << 20;

    /**
     * The ring reservation decision point's fallback counters (Stage 2): every {@code FrameLinearRing}
     * slice reservation a {@link RingRegion} makes is one attempt, served by the ring (fast) or refused
     * with {@code -1} so the mesh promotes to buffers it owns (fallback). Counting only - a refused
     * reservation answers 0 exactly as before.
     */
    private static final FallbackPaths.Handle RING_ALLOC_PATH =
            FallbackPaths.register(FallbackPaths.RING_ALLOC);

    private final VulkanContext context;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private Slot activeSlot;
    private long frameId = -1L;
    private long completedEpoch;
    private long regionsOpened;
    private long ringRegionsOpened;
    private long blockedOpenings;
    private long reclaimedSlots;
    private long promotions;
    private long completionsRecorded;
    private long peakVertexBytes;
    private long peakIndexBytes;

    public VulkanFrameArena(VulkanContext context) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        this.context = context;
        for (int i = 0; i < this.slots.length; i++) {
            this.slots[i] = new Slot(context);
        }
    }

    /** {@return the context the arena's buffers belong to}. */
    public VulkanContext context() {
        return this.context;
    }

    /**
     * Opens a frame, then reclaims every slot whose submission has completed and whose groups are all released.
     * <p>
     * Called once per frame before any group is recorded. A slot that is not reclaimable stays reserved - that is the
     * backpressure the plan asks for instead of an unbounded arena - and {@link #openMesh()} counts the blocked
     * opening rather than resetting memory a later submission may still be reading.
     */
    public void beginFrame(long frameId, long completedEpoch) {
        this.frameId = frameId;
        this.completedEpoch = completedEpoch;
        if (this.activeSlot != null && (!this.activeSlot.inUse || this.activeSlot.frameId != frameId)) {
            this.activeSlot = null;
        }
        for (Slot slot : this.slots) {
            if (!slot.inUse || slot.pendingGroups > 0) {
                continue;
            }
            // Completion is what makes the reset legal. A slot whose frame never reached the queue has no work to
            // wait for (awaitingCompletion is false), so it is free as soon as its groups are: that is the abandoned
            // and the never-submitted frame, without needing a second entry point for them.
            if (!slot.awaitingCompletion && (slot.epoch < 0L || slot.epoch <= completedEpoch)) {
                this.peakVertexBytes = Math.max(this.peakVertexBytes, slot.vertexCursor);
                this.peakIndexBytes = Math.max(this.peakIndexBytes, slot.indexCursor);
                if (this.activeSlot == slot) {
                    this.activeSlot = null;
                }
                slot.reset();
                this.reclaimedSlots++;
            }
        }
    }

    /**
     * {@return a mesh that writes into this frame's arena, or {@code null} when no slot can be handed out}.
     * <p>
     * The mesh owns no device memory: its slice is taken from the slot the frame is using, and the slot is not reset
     * until the submission that reads it has completed and the mesh has been deleted. A null answer is the caller's
     * signal to fall back to an owning mesh for that group, and {@link #blockedOpenings()} says how often it happened,
     * so "the arena refused" is a number rather than an inference.
     */
    public VulkanDynamicMesh openMesh() {
        Slot slot = slotForThisFrame();
        if (slot == null) {
            this.blockedOpenings++;
            return null;
        }
        this.regionsOpened++;
        return new VulkanDynamicMesh(this, new Region(slot));
    }

    /**
     * {@return a mesh that writes into the per-frame linear ring's slot for the frame being recorded}, or
     * {@code null} when no ring was supplied. The mesh is this arena's for promotion counting, but its region is
     * a {@link RingRegion}: the slices are reserved lazily at the first write, so a group that is created and
     * dropped unwritten costs the ring nothing, and a reservation the ring cannot satisfy answers 0 - the mesh
     * then promotes to buffers it owns, the same fallback an over-cap arena group takes.
     *
     * @param ring     the context's frame linear ring
     * @param ringSlot the frame slot being recorded, which is the ring slot whose slices this frame may consume
     */
    public VulkanDynamicMesh openRingMesh(org.soutalab.celtium.vulkan.hal.FrameLinearRing ring, int ringSlot) {
        if (ring == null) {
            return null;
        }
        this.ringRegionsOpened++;
        return new VulkanDynamicMesh(this, new RingRegion(ring, ringSlot));
    }

    /** The frame's own slot, or a free one, or null when both are still held by earlier submissions. */
    private Slot slotForThisFrame() {
        Slot active = this.activeSlot;
        if (active != null && active.inUse && active.frameId == this.frameId) {
            return active;
        }
        for (Slot slot : this.slots) {
            if (slot.inUse && slot.frameId == this.frameId) {
                this.activeSlot = slot;
                return slot;
            }
        }
        for (Slot slot : this.slots) {
            if (!slot.inUse) {
                slot.inUse = true;
                slot.frameId = this.frameId;
                slot.epoch = -1L;
                this.activeSlot = slot;
                return slot;
            }
        }
        return null;
    }

    /**
     * Records that the current frame's slot has work in flight, so it may not be reset until a completion is
     * confirmed.
     * <p>
     * Called after {@code vkQueueSubmit} accepted the frame's command buffer. The submission's own sequence is
     * recorded with it: a confirmation now arrives per slot rather than as a queue-wide drain, so "this slot's work
     * is done" has to be a statement about that sequence, not about whichever fence happened to be observed last.
     */
    public void submitted(long frameId, long submitSequence) {
        Slot active = this.activeSlot;
        if (active != null && active.inUse && active.frameId == frameId) {
            active.awaitingCompletion = true;
            active.submitSequence = submitSequence;
            return;
        }
        for (Slot slot : this.slots) {
            if (slot.inUse && slot.frameId == frameId) {
                slot.awaitingCompletion = true;
                slot.submitSequence = submitSequence;
            }
        }
    }

    /**
     * Records a confirmed completion for every slot whose work was in flight <em>and covered by the confirmed
     * sequence</em>.
     * <p>
     * A Vulkan queue completes in submission order, so when the fence of sequence {@code confirmedSubmitSequence}
     * signals, every earlier submission has completed too - but a later one has not, and marking its slot reclaimable
     * anyway is the use-after-free the sequence gate exists to prevent. Slots still awaiting take this epoch only
     * when their own submission is covered, which is what makes them reclaimable at the next {@link #beginFrame} -
     * and only then.
     * <p>
     * A wait with nothing in flight passes through here as well; that is not an error, and the counter
     * {@code describe()} would show it as a slot that is still awaiting.
     */
    public void completed(long epoch, long confirmedSubmitSequence) {
        for (Slot slot : this.slots) {
            if (slot.inUse && slot.awaitingCompletion && slot.submitSequence <= confirmedSubmitSequence) {
                slot.epoch = epoch;
                slot.awaitingCompletion = false;
                this.completionsRecorded++;
            }
        }
    }

    /** Frees every slot's buffers; the backend calls this after a wait for device idle that succeeded. */
    public void delete() {
        for (Slot slot : this.slots) {
            this.peakVertexBytes = Math.max(this.peakVertexBytes, slot.vertexCursor);
            this.peakIndexBytes = Math.max(this.peakIndexBytes, slot.indexCursor);
            slot.freeBuffers();
            slot.reset();
            slot.pendingGroups = 0;
        }
    }

    /** Counts one group that had to own its buffers because the arena could not hold it (OPT-05's fallback). */
    void notePromotion() {
        this.promotions++;
    }

    /** {@return one line of the arena's state for the run report}. */
    public String describe() {
        StringBuilder perSlot = new StringBuilder();
        for (int i = 0; i < this.slots.length; i++) {
            Slot slot = this.slots[i];
            perSlot.append(i == 0 ? "" : " ").append("slot").append(i).append("=[inUse=").append(slot.inUse)
                    .append(" frame=").append(slot.frameId)
                    .append(" epoch=").append(slot.epoch)
                    .append(" awaiting=").append(slot.awaitingCompletion)
                    .append(" groups=").append(slot.pendingGroups)
                    .append(" vertexUsed=").append(slot.vertexCursor)
                    .append(" vertexCapacity=").append(slot.vertexCapacity())
                    .append(" indexUsed=").append(slot.indexCursor)
                    .append(" indexCapacity=").append(slot.indexCapacity()).append(']');
        }
        return "frameArena regionsOpened=" + this.regionsOpened + " ringRegionsOpened=" + this.ringRegionsOpened
                + " blockedOpenings=" + this.blockedOpenings
                + " reclaimedSlots=" + this.reclaimedSlots + " promotions=" + this.promotions
                + " completionsRecorded=" + this.completionsRecorded
                + " peakVertexBytes=" + this.peakVertexBytes + " peakIndexBytes=" + this.peakIndexBytes
                + " " + perSlot;
    }

    /** {@return how many meshes outgrew the arena and were promoted to owning buffers}. */
    public long promotions() {
        return this.promotions;
    }

    /** {@return how many group openings found no free slot, so the caller fell back to an owning mesh}. */
    public long blockedOpenings() {
        return this.blockedOpenings;
    }

    /** {@return how many mesh slices the arena handed out}. */
    public long regionsOpened() {
        return this.regionsOpened;
    }

    // ------------------------------------------------------------------ the slice a group writes into

    /**
     * One group's slice of a slot: where its vertices and indices start, and how much of each has been written.
     * <p>
     * Addresses are re-read from the slot on every reserve, because growing a slot replaces its buffer and moves the
     * mapping; nothing here holds a mapped address across a call. A sealed region is held by the group that owns it
     * until {@link #release()}.
     */
    public static class Region {
        private final Slot slot;
        private long vertexStart = -1L;
        private long indexStart = -1L;
        private long vertexBytes;
        private long indexBytes;
        private boolean sealed;
        private boolean released;

        private Region(Slot slot) {
            this.slot = slot;
        }

        /**
         * The backing-free constructor {@link RingRegion} uses: it overrides every method the mesh calls, so the
         * arena slot stays null and no base method may run on it. Kept protected and parameterless so the only
         * subclass is the ring region declared in this file.
         */
        protected Region() {
            this.slot = null;
        }

        /** {@return the byte offset of this slice's first vertex inside the slot's vertex buffer}. */
        public long vertexByteOffset() {
            return this.vertexStart < 0L ? 0L : this.vertexStart;
        }

        /** {@return the byte offset of this slice's first index inside the slot's index buffer}. */
        public long indexByteOffset() {
            return this.indexStart < 0L ? 0L : this.indexStart;
        }

        /** {@return this slice's vertex buffer handle}. */
        public long vertexBuffer() {
            return this.slot.vertices == null ? 0L : this.slot.vertices.buffer();
        }

        /** {@return this slice's index buffer handle}. */
        public long indexBuffer() {
            return this.slot.indices == null ? 0L : this.slot.indices.buffer();
        }

        /**
         * Reserves {@code length} bytes at {@code byteOffset} of this slice's vertex stream.
         *
         * @return the mapped address to write at, or 0 when the slot cannot hold it even at its cap
         */
        public long vertexAddressAt(long byteOffset, long length) {
            return vertexAddressAt(byteOffset, length, SLICE_ALIGNMENT);
        }

        /**
         * The same reservation with the slice start aligned to {@code alignment}.
         * <p>
         * The vertex start is what the draw path binds at, and the cheap draw form needs it to be a whole number of
         * vertices: a slice that starts at a 16-byte boundary and steps a 20- or 24-byte vertex cannot put its offset in
         * the draw call and has to re-bind instead. The mesh knows its stride, so it asks for a start that satisfies both
         * the 4-byte attribute rule and its own stride.
         */
        public long vertexAddressAt(long byteOffset, long length, long alignment) {
            long start = this.vertexStart < 0L ? alignTo(this.slot.vertexCursor, alignment) : this.vertexStart;
            if (!this.slot.ensureVertices(start + byteOffset + length)) {
                return 0L;
            }
            if (this.vertexStart < 0L) {
                this.vertexStart = start;
            }
            if (byteOffset + length > this.vertexBytes) {
                this.vertexBytes = byteOffset + length;
                this.slot.vertexCursor = this.vertexStart + this.vertexBytes;
            }
            return this.slot.vertices.mappedAddress() + this.vertexStart + byteOffset;
        }

        /**
         * Reserves {@code length} bytes at {@code byteOffset} of this slice's index stream.
         *
         * @return the mapped address to write at, or 0 when the slot cannot hold it even at its cap
         */
        public long indexAddressAt(long byteOffset, long length) {
            long start = this.indexStart < 0L ? align(this.slot.indexCursor) : this.indexStart;
            if (!this.slot.ensureIndices(start + byteOffset + length)) {
                return 0L;
            }
            if (this.indexStart < 0L) {
                this.indexStart = start;
            }
            if (byteOffset + length > this.indexBytes) {
                this.indexBytes = byteOffset + length;
                this.slot.indexCursor = this.indexStart + this.indexBytes;
            }
            return this.slot.indices.mappedAddress() + this.indexStart + byteOffset;
        }

        /** {@return the mapped address of this slice's first vertex, without reserving anything}. */
        public long vertexBaseAddress() {
            return this.slot.vertices == null || this.vertexStart < 0L
                    ? 0L : this.slot.vertices.mappedAddress() + this.vertexStart;
        }

        /** {@return the mapped address of this slice's first index, without reserving anything}. */
        public long indexBaseAddress() {
            return this.slot.indices == null || this.indexStart < 0L
                    ? 0L : this.slot.indices.mappedAddress() + this.indexStart;
        }

        /** The group is complete: from here the slice is held by whoever owns this region. */
        public void seal() {
            if (!this.sealed) {
                this.sealed = true;
                this.slot.pendingGroups++;
            }
        }

        /**
         * The group no longer needs its slice.
         * <p>
         * Called when the group's mesh is deleted, which happens after the submission that drew it has completed (the
         * retired-group drain) or after a wait that succeeded (teardown). Idempotent: several drop paths delete the
         * same mesh, and a double release would free a slot another group is still reading.
         */
        public void release() {
            if (this.released) {
                return;
            }
            this.released = true;
            if (this.sealed && this.slot.pendingGroups > 0) {
                this.slot.pendingGroups--;
            }
        }

        /**
         * Gives the reservation back without sealing it, because the writer moved to a buffer it owns.
         * <p>
         * Only the last region of a slot can do this - its bytes are the ones past every other region's - so the
         * cursor is rolled back to this slice's start and the space is available to the next group. A slot that is not
         * last keeps the reservation: the bytes are already interleaved with another group's, and rolling back would
         * overlap them.
         */
        public void abandon() {
            if (this.sealed || this.released) {
                return;
            }
            this.released = true;
            if (this.vertexStart >= 0L && this.slot.vertexCursor == this.vertexStart + this.vertexBytes) {
                this.slot.vertexCursor = this.vertexStart;
            }
            if (this.indexStart >= 0L && this.slot.indexCursor == this.indexStart + this.indexBytes) {
                this.slot.indexCursor = this.indexStart;
            }
        }

        /** {@return the bytes this slice's vertex stream holds, for the arena census}. */
        public long vertexBytes() {
            return this.vertexBytes;
        }

        /** {@return the bytes this slice's index stream holds, for the arena census}. */
        public long indexBytes() {
            return this.indexBytes;
        }
    }

    // ------------------------------------------------------------------ the ring-backed region

    /**
     * A group's slice of the per-frame linear ring (Stage 2): the {@link Region} contract served from ring slices
     * instead of an arena slot's buffers.
     * <p>
     * The mesh's write pattern is incremental and its total is known only as it grows, while a ring slice must be
     * sized when it is taken - so each stream reserves once, at its first write, with the historical per-group
     * buffer sizes as the floor (64 KiB of vertices, 32 KiB of indices: the sizes a group owned outright before
     * the arena existed, and the sizes in the tick383 crash signature). A write past the reservation answers 0,
     * which is the mesh's promotion signal exactly as an over-cap arena slot is; the bytes written so far are
     * copied out by the promotion, and this region is abandoned. A reservation the ring itself refuses (slot
     * exhausted) answers 0 the same way, so the fallback chain - ring, then arena, then owning buffers - needs no
     * new branch in the mesh.
     * <p>
     * Lifetime: sealing takes a hold on the ring slot (see {@link org.soutalab.celtium.vulkan.hal.FrameLinearRing}),
     * and {@link #release()} - the mesh's delete - gives it back. The slot's confirmed reset is deferred while
     * the hold stands, so a group held for another render target keeps its bytes exactly as an arena slot's
     * pending-group count keeps that slot unreset. The vertex slice is taken with the caller's own alignment
     * (the mesh's {@code lcm(16, stride)}), which the ring's division alignment honours for non-power-of-two
     * strides, so the draw form's offset-to-vertex conversion stays exact.
     */
    public static final class RingRegion extends Region {
        /** The first vertex reservation's floor: the per-group vertex buffer size from before the arena (BH-28). */
        private static final long MIN_VERTEX_SLICE_BYTES = 64L * 1024L;
        /** The first index reservation's floor: the per-group index buffer size from before the arena (BH-28). */
        private static final long MIN_INDEX_SLICE_BYTES = 32L * 1024L;

        private final org.soutalab.celtium.vulkan.hal.FrameLinearRing ring;
        private final int ringSlot;
        /** The vertex slice's offset inside the ring slot's buffer, or -1 before the first vertex reservation. */
        private long vertexSlice = -1L;
        private long vertexSliceBytes;
        private long indexSlice = -1L;
        private long indexSliceBytes;
        private long vertexBytes;
        private long indexBytes;
        private boolean sealed;
        private boolean released;

        private RingRegion(org.soutalab.celtium.vulkan.hal.FrameLinearRing ring, int ringSlot) {
            super();
            this.ring = ring;
            this.ringSlot = ringSlot;
        }

        /** {@return the ring offset of this slice's first vertex}, or 0 before the first reservation. */
        @Override
        public long vertexByteOffset() {
            return this.vertexSlice < 0L ? 0L : this.vertexSlice;
        }

        /** {@return the ring offset of this slice's first index}, or 0 before the first reservation. */
        @Override
        public long indexByteOffset() {
            return this.indexSlice < 0L ? 0L : this.indexSlice;
        }

        /** {@return the ring slot's buffer handle once a vertex slice exists}, else 0 like an unbuilt arena slot. */
        @Override
        public long vertexBuffer() {
            return this.vertexSlice < 0L ? 0L : this.ring.buffer(this.ringSlot);
        }

        /** {@return the ring slot's buffer handle once an index slice exists}, else 0. */
        @Override
        public long indexBuffer() {
            return this.indexSlice < 0L ? 0L : this.ring.buffer(this.ringSlot);
        }

        /**
         * Reserves (at the first call) and addresses this slice's vertex stream. The reservation is sized to the
         * need already visible, floored at {@link #MIN_VERTEX_SLICE_BYTES}; a later call past the reservation
         * answers 0 so the mesh promotes, because a ring slice cannot grow and a second slice would not be
         * contiguous with the first.
         *
         * @return the mapped address to write at, or 0 when the ring cannot hold this stream
         */
        @Override
        public long vertexAddressAt(long byteOffset, long length, long alignment) {
            if (this.released) {
                return 0L;
            }
            if (this.vertexSlice < 0L) {
                long size = Math.max(byteOffset + length, MIN_VERTEX_SLICE_BYTES);
                RING_ALLOC_PATH.attempt();
                long offset = this.ring.alloc(this.ringSlot, size, alignment);
                RING_ALLOC_PATH.verdict(offset >= 0L);
                if (offset < 0L) {
                    return 0L;
                }
                this.vertexSlice = offset;
                this.vertexSliceBytes = size;
            }
            if (byteOffset + length > this.vertexSliceBytes) {
                return 0L;
            }
            if (byteOffset + length > this.vertexBytes) {
                this.vertexBytes = byteOffset + length;
            }
            return this.ring.mappedAddress(this.ringSlot, this.vertexSlice) + byteOffset;
        }

        /** The index half of {@link #vertexAddressAt(long, long, long)}, aligned and floored like an arena slice. */
        @Override
        public long indexAddressAt(long byteOffset, long length) {
            if (this.released) {
                return 0L;
            }
            if (this.indexSlice < 0L) {
                long size = Math.max(byteOffset + length, MIN_INDEX_SLICE_BYTES);
                RING_ALLOC_PATH.attempt();
                long offset = this.ring.alloc(this.ringSlot, size, SLICE_ALIGNMENT);
                RING_ALLOC_PATH.verdict(offset >= 0L);
                if (offset < 0L) {
                    return 0L;
                }
                this.indexSlice = offset;
                this.indexSliceBytes = size;
            }
            if (byteOffset + length > this.indexSliceBytes) {
                return 0L;
            }
            if (byteOffset + length > this.indexBytes) {
                this.indexBytes = byteOffset + length;
            }
            return this.ring.mappedAddress(this.ringSlot, this.indexSlice) + byteOffset;
        }

        /** {@return the mapped address of this slice's first vertex, without reserving anything}. */
        @Override
        public long vertexBaseAddress() {
            return this.vertexSlice < 0L ? 0L : this.ring.mappedAddress(this.ringSlot, this.vertexSlice);
        }

        /** {@return the mapped address of this slice's first index, without reserving anything}. */
        @Override
        public long indexBaseAddress() {
            return this.indexSlice < 0L ? 0L : this.ring.mappedAddress(this.ringSlot, this.indexSlice);
        }

        /** The group is complete: its slices are held against the ring slot's reset until {@link #release()}. */
        @Override
        public void seal() {
            if (!this.sealed && !this.released) {
                this.sealed = true;
                this.ring.hold(this.ringSlot);
            }
        }

        /**
         * The group no longer needs its slices. Idempotent, like the arena region's: several drop paths delete
         * the same mesh, and a double release would free a slot another group is still reading.
         */
        @Override
        public void release() {
            if (this.released) {
                return;
            }
            this.released = true;
            if (this.sealed) {
                this.ring.release(this.ringSlot);
            }
        }

        /**
         * Gives the reservation back because the writer moved to buffers it owns (promotion).
         * <p>
         * A sealed region gives its hold back here rather than at {@link #release()}: promotion runs inside a
         * write call, while the group is still being recorded, so the slices have never been named by a
         * submission and nothing can still read them - and the mesh nulls this region at promotion, so the
         * delete-time release would never arrive. An unsealed region has no hold to return; its reserved slices
         * simply stay consumed until the slot's reset, because the ring is bump-only and has no rollback.
         */
        @Override
        public void abandon() {
            if (this.released) {
                return;
            }
            this.released = true;
            if (this.sealed) {
                this.ring.release(this.ringSlot);
            }
        }

        /** {@return the bytes this slice's vertex stream holds, for the arena census}. */
        @Override
        public long vertexBytes() {
            return this.vertexBytes;
        }

        /** {@return the bytes this slice's index stream holds, for the arena census}. */
        @Override
        public long indexBytes() {
            return this.indexBytes;
        }
    }

    // ------------------------------------------------------------------ slots

    private static long align(long value) {
        return (value + SLICE_ALIGNMENT - 1L) & ~(SLICE_ALIGNMENT - 1L);
    }

    /** Rounds {@code value} up to a multiple of {@code alignment}; any positive alignment, not only a power of two. */
    private static long alignTo(long value, long alignment) {
        return ((value + alignment - 1L) / alignment) * alignment;
    }

    private static final class Slot {
        private final VulkanContext context;
        private VulkanHostBuffer vertices;
        private VulkanHostBuffer indices;
        /** Buffers this slot replaced by growth and still owes a free to, released at reset (audit B05). */
        private final java.util.ArrayDeque<VulkanHostBuffer> retiredBuffers =
                new java.util.ArrayDeque<VulkanHostBuffer>();
        private long vertexCursor;
        private long indexCursor;
        private boolean inUse;
        private long frameId = -1L;
        /** The completion epoch that must be reached before this slot may be reset; -1 while unsubmitted. */
        private long epoch = -1L;
        /** True between a successful submit of this slot's frame and the wait that confirms it completed. */
        private boolean awaitingCompletion;
        /**
         * The backend submission sequence this slot's frame was carried in, recorded at {@link #submitted}. A
         * confirmation marks the slot complete only when the confirmed watermark covers this sequence.
         */
        private long submitSequence;
        private int pendingGroups;

        private Slot(VulkanContext context) {
            this.context = context;
        }

        private long vertexCapacity() {
            return this.vertices == null ? 0L : this.vertices.size();
        }

        private long indexCapacity() {
            return this.indices == null ? 0L : this.indices.size();
        }

        private void reset() {
            // Audit B05: allocations replaced by growth are freed here, not at the moment of growth. A buffer that was
            // replaced while this slot was being recorded may already have been captured by a batch command buffer and
            // by the frame submission that consumes it, and this reset runs only once that submission has completed
            // (the precondition is !awaitingCompletion && epoch <= completedEpoch).
            while (!this.retiredBuffers.isEmpty()) {
                this.retiredBuffers.poll().delete();
            }
            this.vertexCursor = 0L;
            this.indexCursor = 0L;
            this.inUse = false;
            this.frameId = -1L;
            this.epoch = -1L;
            this.submitSequence = 0L;
            this.awaitingCompletion = false;
        }

        private void freeBuffers() {
            while (!this.retiredBuffers.isEmpty()) {
                this.retiredBuffers.poll().delete();
            }
            if (this.vertices != null) {
                this.vertices.delete();
                this.vertices = null;
            }
            if (this.indices != null) {
                this.indices.delete();
                this.indices = null;
            }
        }

        /**
         * Makes room for {@code required} bytes in this slot's vertex buffer, doubling up to the cap.
         *
         * @return false when the request does not fit even at the cap, so the writer promotes to owned buffers rather
         *         than dropping the group
         */
        private boolean ensureVertices(long required) {
            if (this.vertices != null && required <= this.vertices.size()) {
                return true;
            }
            long target = Math.min(MAX_VERTEX_BYTES,
                    Math.max(required, (this.vertices == null ? INITIAL_VERTEX_BYTES : this.vertices.size() * 2L)));
            if (required > target) {
                return false;
            }
            if (this.vertices == null) {
                this.vertices = new VulkanHostBuffer(this.context, align(target),
                        VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, true);
            } else {
                // The replacement is a new object and the old one is retired to this slot, because ensureCapacity frees
                // the allocation it replaces the moment the copy is done - and at growth time, while the frame is still
                // being recorded, no ticket names the reader (audit B05, both measured: the deferred fence belongs to
                // an older batch, the slot's epoch is still -1).
                VulkanHostBuffer replacement = new VulkanHostBuffer(this.context, align(target),
                        VK10.VK_BUFFER_USAGE_VERTEX_BUFFER_BIT, true);
                long copyBytes = Math.min(this.vertexCursor, this.vertices.size());
                if (copyBytes > 0L) {
                    org.lwjgl.system.MemoryUtil.memCopy(this.vertices.mappedAddress(), replacement.mappedAddress(),
                            copyBytes);
                }
                this.retiredBuffers.add(this.vertices);
                this.vertices = replacement;
            }
            return true;
        }

        /** The index half of {@link #ensureVertices(long)}. */
        private boolean ensureIndices(long required) {
            if (this.indices != null && required <= this.indices.size()) {
                return true;
            }
            long target = Math.min(MAX_INDEX_BYTES,
                    Math.max(required, (this.indices == null ? INITIAL_INDEX_BYTES : this.indices.size() * 2L)));
            if (required > target) {
                return false;
            }
            if (this.indices == null) {
                this.indices = new VulkanHostBuffer(this.context, align(target),
                        VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, true);
            } else {
                VulkanHostBuffer replacement = new VulkanHostBuffer(this.context, align(target),
                        VK10.VK_BUFFER_USAGE_INDEX_BUFFER_BIT, true);
                long copyBytes = Math.min(this.indexCursor, this.indices.size());
                if (copyBytes > 0L) {
                    org.lwjgl.system.MemoryUtil.memCopy(this.indices.mappedAddress(), replacement.mappedAddress(),
                            copyBytes);
                }
                this.retiredBuffers.add(this.indices);
                this.indices = replacement;
            }
            return true;
        }
    }
}
