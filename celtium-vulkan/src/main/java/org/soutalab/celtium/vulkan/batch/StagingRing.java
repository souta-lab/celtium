package org.soutalab.celtium.vulkan.batch;

import java.nio.ByteBuffer;

/**
 * The per-frame host-visible staging ring immediate draws copy their vertices into
 * (new-architecture design section 2.2: the {@code FrameStagingRing}). The ring is split into
 * frame slots; a slot is written for one frame and recycled only once the frame that read it has
 * completed, which the HAL's timeline discipline (design section 2.3) orders. This interface is
 * the seam between the batcher and the memory implementation: production plugs in the HAL's
 * host-visible Vulkan ring, tests and headless tooling use {@link DirectStagingRing}.
 *
 * <p>Offsets returned by {@link #allocate} are relative to the start of the whole ring, so a
 * vertex offset computed at capture time stays valid for the frame's draw commands.
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
     * @return the ring-absolute byte offset of the reservation, or -1 if it does not fit in the
     * current slot (the caller decides the fallback; nothing is written either way)
     */
    long allocate(int size, int alignment);

    /** Copies {@code source}'s remaining bytes to the ring-absolute {@code offset}. */
    void write(long offset, ByteBuffer source);

    /** Moves to the next frame slot and makes it writable from its start. */
    void advanceFrame();
}
