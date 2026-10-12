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
 * Threading: render thread only, like the rest of the backend.
 */
public final class VulkanFrameArena {
    /**
     * Two slots: one being recorded and one whose submission may still be in flight. More would only add memory; the
     * completion gate, not the count, is what makes reuse correct.
     */
    private static final int SLOT_COUNT = 2;
    /** Every slice starts on this boundary: it satisfies the 4-byte rule for vertex attributes and 32-bit indices. */
    private static final int SLICE_ALIGNMENT = 16;
    private static final long INITIAL_VERTEX_BYTES = 1L << 20;
    private static final long INITIAL_INDEX_BYTES = 1L << 18;
    private static final long MAX_VERTEX_BYTES = 16L << 20;
    private static final long MAX_INDEX_BYTES = 4L << 20;

    private final VulkanContext context;
    private final Slot[] slots = new Slot[SLOT_COUNT];
    private long frameId = -1L;
    private long completedEpoch;
    private long regionsOpened;
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

    /** The frame's own slot, or a free one, or null when both are still held by earlier submissions. */
    private Slot slotForThisFrame() {
        for (Slot slot : this.slots) {
            if (slot.inUse && slot.frameId == this.frameId) {
                return slot;
            }
        }
        for (Slot slot : this.slots) {
            if (!slot.inUse) {
                slot.inUse = true;
                slot.frameId = this.frameId;
                slot.epoch = -1L;
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
        return "frameArena regionsOpened=" + this.regionsOpened + " blockedOpenings=" + this.blockedOpenings
                + " reclaimedSlots=" + this.reclaimedSlots + " promotions=" + this.promotions
                + " completionsRecorded=" + this.completionsRecorded
                + " peakVertexBytes=" + this.peakVertexBytes + " peakIndexBytes=" + this.peakIndexBytes
                + " " + perSlot;
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
    public static final class Region {
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
            long start = this.vertexStart < 0L ? align(this.slot.vertexCursor) : this.vertexStart;
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

    // ------------------------------------------------------------------ slots

    private static long align(long value) {
        return (value + SLICE_ALIGNMENT - 1L) & ~(SLICE_ALIGNMENT - 1L);
    }

    private static final class Slot {
        private final VulkanContext context;
        private VulkanHostBuffer vertices;
        private VulkanHostBuffer indices;
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
            this.vertexCursor = 0L;
            this.indexCursor = 0L;
            this.inUse = false;
            this.frameId = -1L;
            this.epoch = -1L;
            this.submitSequence = 0L;
            this.awaitingCompletion = false;
        }

        private void freeBuffers() {
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
                this.vertices.ensureCapacity(align(target));
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
                this.indices.ensureCapacity(align(target));
            }
            return true;
        }
    }
}
