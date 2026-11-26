package org.soutalab.celtium.vulkan.hal;

/**
 * The narrow surface of {@link FrameLinearRing} that the batch package's staging-ring adapter
 * translates over - the whole of what {@code FrameLinearStagingRingAdapter} is allowed to ask
 * of the ring, and nothing else.
 *
 * <p>This seam exists for one reason (ringmap section 7 item 2, decided as option (a) at the
 * Stage 5 stage 1 intake): {@link FrameLinearRing} is a final concrete class whose construction
 * builds real Vulkan buffers, so an adapter written directly against it cannot be unit-tested
 * and its translation correctness would rest on device legs alone. Against this interface the
 * adapter's translation - which slot a reservation is placed in, how a size of 0 is absorbed,
 * where a write lands - is fixed by plain unit tests over a fake ring, while the real ring's
 * device behaviour (that a slot's mapped address really is that slot's buffer) stays the
 * wiring leg's business, exactly as it was before the seam.
 *
 * <p>The surface is deliberately read-and-reserve only. The ring's slot discipline -
 * {@code reset}, {@code hold}, {@code release} - is <em>not</em> part of it: recycling belongs
 * to the frame-slot completion gate alone, and an adapter that could name those methods could
 * grow a second slot discipline, which the unification exists to remove. Callers that own the
 * ring (the context, the backend) keep using the concrete {@link FrameLinearRing} and are
 * unaffected by this interface.
 */
public interface FrameLinearRingAccess {

    /** {@return how many slots the ring holds} - the frame-slot count it was built against. */
    int slots();

    /** {@return the fixed capacity of one slot in bytes}. */
    long slotCapacity(int slot);

    /**
     * Hands out a slice of {@code slot}'s buffer.
     *
     * @return the slice's offset from the slot buffer's base, or {@code -1} when the slice does
     *         not fit in what remains of the slot
     */
    long alloc(int slot, long size, long alignment);

    /** {@return the bytes handed out of {@code slot} since its last applied reset}, including alignment padding. */
    long used(int slot);

    /** {@return the mapped host address of {@code slot}'s base plus {@code offset}}. */
    long mappedAddress(int slot, long offset);
}
