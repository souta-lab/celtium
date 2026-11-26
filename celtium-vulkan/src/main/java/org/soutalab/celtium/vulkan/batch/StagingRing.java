package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;

/**
 * The per-frame host-visible staging ring immediate draws copy their vertices into
 * (new-architecture design section 2.2: the {@code FrameStagingRing}). The ring is split into
 * frame slots; a slot is written for one frame and recycled only once the frame that read it has
 * completed, which the HAL's timeline discipline (design section 2.3) orders. This interface is
 * the seam between the batcher and the memory implementation: production plugs in
 * {@link FrameLinearStagingRingAdapter} over the HAL's per-frame linear ring, tests and headless
 * tooling use {@link DirectStagingRing}.
 *
 * <p>Offsets returned by {@link #allocate} are relative to the start of the current slot: a
 * frame's draw binds the slot's own buffer, so a vertex offset computed at capture time is an
 * offset into that buffer and stays valid for the frame's draw commands.
 */
public interface StagingRing {

    /** Number of frame slots in the ring. */
    int slotCount();

    /** Byte capacity of one frame slot. */
    long slotCapacity();

    /** Index of the slot the current frame writes into. */
    int currentSlot();

    /**
     * Reserves {@code size} bytes in the current slot, honouring {@code alignment}.
     *
     * @return the byte offset of the reservation from the start of the current slot, or -1 if it
     * does not fit in the current slot (the caller decides the fallback; nothing is written
     * either way). A size of 0 is legal and reserves nothing: it returns the aligned offset the
     * next reservation would start at
     */
    long allocate(int size, int alignment);

    /**
     * Copies {@code source}'s remaining bytes to the slot-relative {@code offset} (one previously
     * returned by {@link #allocate}). The source's position is not advanced.
     */
    void write(long offset, ByteBuffer source);

    /**
     * Selects the next frame slot for subsequent reservations. Selection only: making a slot
     * writable from its start again (recycling it) is the ring owner's discipline, ordered by
     * the frame-completion gate, and never happens inside this method - the production adapter
     * does not reset the HAL ring here. (The headless {@link DirectStagingRing} still zeroes its
     * own cursor on advance; that is a local detail of the test implementation, not a guarantee
     * of this contract.)
     */
    void advanceFrame();
}
