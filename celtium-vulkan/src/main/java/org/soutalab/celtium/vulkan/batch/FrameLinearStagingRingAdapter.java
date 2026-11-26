package org.soutalab.celtium.vulkan.batch;

import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.hal.FrameLinearRing;

import java.nio.ByteBuffer;

/**
 * The production {@link StagingRing}: a thin translation over the HAL's {@link FrameLinearRing}
 * (design NEON-S5-GLSM-SLICING section 4 - the ring's one implementation is the Stage 2 linear
 * ring; this adapter is the only place the batch package touches it).
 *
 * <p>Translation, nothing more:
 * <ul>
 *   <li>{@link #allocate} delegates to {@link FrameLinearRing#alloc} on the adapter's current
 *   slot and returns its slot-relative offset unchanged - the two contracts state the same
 *   offset basis, so no arithmetic happens here.</li>
 *   <li>{@link #write} copies into the slot's persistently mapped memory at
 *   {@link FrameLinearRing#mappedAddress}. The source must be a direct buffer (the capture
 *   path's vertex data always is; the ring is raw host memory and there is no heap staging
 *   copy), and the source's position is not advanced.</li>
 *   <li>{@link #advanceFrame} rotates only the adapter's own slot counter. It never calls the
 *   ring's {@code reset}, {@code hold} or {@code release}: recycling a slot is the frame-slot
 *   completion gate's alone ({@code VulkanContext.celtium$resetFrameLinearSlot}), and a second
 *   slot discipline inside the adapter is exactly what the unification removes. How the
 *   counter is kept in step with the frame scheduler's slot is a wiring decision for the lane
 *   that connects the batcher to the backend (S5-2); this class goes no further than the
 *   counter.</li>
 * </ul>
 *
 * <p>One contract nuance is absorbed here: {@link StagingRing} allows a size of 0 (the batcher
 * asks for one on an empty capture), while the linear ring's allocator rejects non-positive
 * sizes. A zero reservation therefore never reaches the allocator: the adapter returns the
 * aligned offset the next reservation would start at, or -1 when even that lies past the slot's
 * end - the same values {@link DirectStagingRing} returns. The one difference from the headless
 * ring is accounting: the headless ring consumes the alignment padding on a zero reservation,
 * this adapter does not (the allocator's cursor only moves on real reservations). Returned
 * offsets are unaffected for callers whose cursor stays aligned to their stride, which the
 * batcher's stride-multiple captures guarantee.
 *
 * <p>The adapter takes no hold on the slots it writes: a captured slice is consumed by the
 * submission of the frame that captured it (the batch plan executes inside that frame). If a
 * later slice ever defers execution across frames, the hold question reopens at that wiring,
 * not here.
 */
public final class FrameLinearStagingRingAdapter implements StagingRing {

    private final FrameLinearRing ring;
    private int currentSlot;

    public FrameLinearStagingRingAdapter(FrameLinearRing ring) {
        if (ring == null) {
            throw new IllegalArgumentException("ring must not be null");
        }
        this.ring = ring;
    }

    /** Creates an adapter over the context's frame linear ring (created on first demand). */
    public static FrameLinearStagingRingAdapter create(VulkanContext context) {
        return new FrameLinearStagingRingAdapter(context.celtium$frameLinearRing());
    }

    @Override
    public int slotCount() {
        return this.ring.slots();
    }

    @Override
    public long slotCapacity() {
        return this.ring.slotCapacity(this.currentSlot);
    }

    @Override
    public int currentSlot() {
        return this.currentSlot;
    }

    @Override
    public long allocate(int size, int alignment) {
        if (size < 0) {
            throw new IllegalArgumentException("size " + size);
        }
        long effectiveAlignment = Math.max(1, alignment);
        if (size == 0) {
            // Absorbed (see the class comment): the allocator rejects non-positive sizes, so a
            // zero reservation is answered from the slot's current position instead.
            long used = this.ring.used(this.currentSlot);
            long aligned = (used + effectiveAlignment - 1L) / effectiveAlignment * effectiveAlignment;
            return aligned > this.ring.slotCapacity(this.currentSlot) ? -1L : aligned;
        }
        return this.ring.alloc(this.currentSlot, size, effectiveAlignment);
    }

    @Override
    public void write(long offset, ByteBuffer source) {
        int remaining = source.remaining();
        long capacity = this.ring.slotCapacity(this.currentSlot);
        if (offset < 0L || remaining > capacity - offset) {
            throw new IllegalArgumentException("write of " + remaining + " bytes at slot offset "
                    + offset + " exceeds the slot capacity " + capacity);
        }
        if (remaining == 0) {
            return;
        }
        long destination = this.ring.mappedAddress(this.currentSlot, offset);
        MemoryUtil.memCopy(MemoryUtil.memAddress(source.duplicate()), destination, remaining);
    }

    @Override
    public void advanceFrame() {
        // Selection only (see the class comment): the completion gate owns recycling, so the
        // ring is deliberately not reset here.
        this.currentSlot = (this.currentSlot + 1) % this.ring.slots();
    }
}
