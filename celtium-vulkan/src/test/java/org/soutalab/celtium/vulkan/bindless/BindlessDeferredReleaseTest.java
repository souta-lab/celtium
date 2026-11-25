package org.soutalab.celtium.vulkan.bindless;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BindlessDeferredReleaseTest {

    @Test
    void detachEndsTheRegistrationButHoldsTheSlot() {
        BindlessTextureTable table = new BindlessTextureTable();
        int first = table.register(100);
        table.register(200);
        int detached = table.detachForDeferredRelease(100);
        assertEquals(first, detached);
        // The name is gone: it no longer resolves, and its handle is stale for new draws.
        assertEquals(BindlessTextureTable.INVALID_HANDLE, table.handleOf(100));
        assertEquals(-1, table.slotOf(first));
        assertEquals(1, table.size());
        assertEquals(1, table.retiringCount());
        // The slot is held: the next registration takes a fresh slot, not the detached one.
        int third = table.register(300);
        assertEquals(2, table.slotOf(third));
    }

    @Test
    void completionFreesTheSlotForReuseWithAFreshGeneration() {
        BindlessTextureTable table = new BindlessTextureTable();
        int first = table.register(100);
        int slot = table.slotOf(first);
        table.detachForDeferredRelease(100);
        assertTrue(table.completeDeferredRelease(first));
        assertEquals(0, table.retiringCount());
        int second = table.register(200);
        assertEquals(slot, table.slotOf(second));
        // The detached handle stays stale after reuse: the generation moved on.
        assertEquals(-1, table.slotOf(first));
    }

    @Test
    void aNameReregisteredWhileRetiringGetsItsOwnSlot() {
        BindlessTextureTable table = new BindlessTextureTable();
        int first = table.register(100);
        table.detachForDeferredRelease(100);
        // The game deleted the name and created a new texture under it before the old slot's
        // completion arrived: the new texture must not inherit the retiring slot.
        int second = table.register(100);
        assertEquals(1, table.slotOf(second));
        assertTrue(table.completeDeferredRelease(first));
        assertEquals(second, table.handleOf(100));
        assertEquals(1, table.slotOf(second));
    }

    @Test
    void completionRefusesLiveRegistrationsAndForgedHandles() {
        BindlessTextureTable table = new BindlessTextureTable();
        int live = table.register(100);
        assertFalse(table.completeDeferredRelease(live));
        assertEquals(0, table.retiringCount());
        int detached = table.detachForDeferredRelease(100);
        int forged = detached + (1 << 12);
        assertFalse(table.completeDeferredRelease(forged));
        assertEquals(1, table.retiringCount());
        assertTrue(table.completeDeferredRelease(detached));
        // A second completion of the same handle frees nothing more.
        assertFalse(table.completeDeferredRelease(detached));
    }

    @Test
    void queueDrainRespectsRetireValues() {
        BindlessTextureTable table = new BindlessTextureTable();
        BindlessDeferredReleaseQueue queue = new BindlessDeferredReleaseQueue();
        table.register(100);
        table.register(200);
        assertTrue(queue.defer(table, 100, 10L));
        assertTrue(queue.defer(table, 200, 20L));
        assertEquals(2, queue.pending());
        assertEquals(0, queue.drain(table, 9L));
        assertEquals(1, queue.drain(table, 10L));
        assertEquals(1, queue.pending());
        assertEquals(1, table.retiringCount());
        assertEquals(1, queue.drain(table, 20L));
        assertEquals(0, queue.pending());
        assertEquals(0, table.retiringCount());
    }

    @Test
    void queueDeferOfAnUnknownNameQueuesNothing() {
        BindlessTextureTable table = new BindlessTextureTable();
        BindlessDeferredReleaseQueue queue = new BindlessDeferredReleaseQueue();
        assertFalse(queue.defer(table, 999, 10L));
        assertEquals(0, queue.pending());
    }

    @Test
    void queueDrainIsNotBlockedByAnOutOfOrderHighStamp() {
        BindlessTextureTable table = new BindlessTextureTable();
        BindlessDeferredReleaseQueue queue = new BindlessDeferredReleaseQueue();
        table.register(100);
        table.register(200);
        assertTrue(queue.defer(table, 100, 50L));
        assertTrue(queue.defer(table, 200, 5L));
        assertEquals(1, queue.drain(table, 5L));
        assertEquals(1, queue.pending());
    }
}
