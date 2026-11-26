package org.soutalab.celtium.vulkan.bindless;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BindlessTextureBindingTest {

    /** One recorded slot write: (slot, imageView, sampler). */
    private static final class Writes implements BindlessTextureBinding.SlotWriter {
        final List<long[]> calls = new ArrayList<long[]>();

        @Override
        public void writeSlot(int slot, long imageView, long sampler) {
            this.calls.add(new long[]{slot, imageView, sampler});
        }
    }

    private static BindlessTextureBinding binding(Writes writes, AtomicLong retireValue) {
        return new BindlessTextureBinding(null, writes, retireValue::get);
    }

    @Test
    void registrationWritesTheSlotAndReturnsTheHandle() {
        Writes writes = new Writes();
        BindlessTextureBinding binding = binding(writes, new AtomicLong(0L));
        int handle = binding.onTextureRegistered(7, 111L, 222L);
        assertEquals(0, binding.slotOf(handle));
        assertEquals(handle, binding.handleOf(7));
        assertEquals(1, binding.registeredCount());
        assertEquals(1, writes.calls.size());
        assertEquals(0L, writes.calls.get(0)[0]);
        assertEquals(111L, writes.calls.get(0)[1]);
        assertEquals(222L, writes.calls.get(0)[2]);
    }

    @Test
    void reregistrationRewritesTheSameSlotWithTheNewImage() {
        Writes writes = new Writes();
        BindlessTextureBinding binding = binding(writes, new AtomicLong(0L));
        int first = binding.onTextureRegistered(7, 111L, 222L);
        // The mirror's image was replaced (a resize recreates the view): same handle, same
        // slot, new handles written.
        int second = binding.onTextureRegistered(7, 333L, 444L);
        assertEquals(first, second);
        assertEquals(1, binding.registeredCount());
        assertEquals(2, writes.calls.size());
        assertEquals(0L, writes.calls.get(1)[0]);
        assertEquals(333L, writes.calls.get(1)[1]);
        assertEquals(444L, writes.calls.get(1)[2]);
    }

    @Test
    void releaseIsDeferredUntilTheRetireValueCompletes() {
        Writes writes = new Writes();
        AtomicLong retireValue = new AtomicLong(5L);
        BindlessTextureBinding binding = binding(writes, retireValue);
        binding.onTextureRegistered(7, 111L, 222L);
        binding.onTextureReleased(7);
        assertEquals(0, binding.registeredCount());
        assertEquals(1, binding.pendingReleases());
        // The retired slot is not handed out while its frame may still be in flight.
        int other = binding.onTextureRegistered(8, 555L, 666L);
        assertEquals(1, binding.slotOf(other));
        assertEquals(0, binding.advance(4L));
        assertEquals(1, binding.pendingReleases());
        assertEquals(1, binding.advance(5L));
        assertEquals(0, binding.pendingReleases());
        // Only now may a registration reuse slot 0.
        int third = binding.onTextureRegistered(9, 777L, 888L);
        assertEquals(0, binding.slotOf(third));
    }

    @Test
    void aFullTableRefusesAndCountsTheOverflow() {
        Writes writes = new Writes();
        BindlessTextureBinding binding = binding(writes, new AtomicLong(0L));
        for (int glId = 1; glId <= BindlessTextureTable.CAPACITY; glId++) {
            binding.onTextureRegistered(glId, glId, glId);
        }
        assertEquals(BindlessTextureTable.CAPACITY, binding.registeredCount());
        int refused = binding.onTextureRegistered(99999, 1L, 1L);
        assertEquals(BindlessTextureTable.INVALID_HANDLE, refused);
        assertEquals(1, binding.overflowRegistrations());
        // An existing registration still rewrites fine on a full table.
        binding.onTextureRegistered(1, 42L, 43L);
        assertEquals(1, binding.overflowRegistrations());
    }
}
