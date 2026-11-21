package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The retire queue's device ownership (L06-01 / BH10-09): a destruction belongs to the device whose handles it
 * names, drains are scoped to that device, and a device that dies without a confirming wait has its entries
 * dropped unexecuted rather than left for a successor backend's drain.
 * <p>
 * Every test uses its own fake device handles, so the static queue's state never leaks between tests.
 */
class VulkanRetireQueueTest {

    private static long handle(int n) {
        return 0xFA1700000000L + n;
    }

    @Test
    void flushForRunsOnlyTheNamedDevicesEntries() {
        long mine = handle(1);
        long other = handle(2);
        AtomicInteger mineRan = new AtomicInteger();
        AtomicInteger otherRan = new AtomicInteger();
        try {
            VulkanRetireQueue.retire(mine, mineRan::incrementAndGet);
            VulkanRetireQueue.retire(other, otherRan::incrementAndGet);
            VulkanRetireQueue.retire(mine, mineRan::incrementAndGet);

            assertEquals(2, VulkanRetireQueue.flushFor(mine));
            assertEquals(2, mineRan.get());
            assertEquals(0, otherRan.get(), "another device's entries are not this drain's to execute");
            assertEquals(1, VulkanRetireQueue.pendingFor(other));
            assertEquals(0, VulkanRetireQueue.pendingFor(mine));
        } finally {
            VulkanRetireQueue.dropFor(mine);
            VulkanRetireQueue.dropFor(other);
        }
    }

    @Test
    void flushForADeviceWithNothingQueuedRunsNothing() {
        long device = handle(3);
        assertEquals(0, VulkanRetireQueue.flushFor(device));
        assertEquals(0, VulkanRetireQueue.pendingFor(device));
    }

    @Test
    void dropForRemovesWithoutRunning() {
        long device = handle(4);
        AtomicInteger ran = new AtomicInteger();
        VulkanRetireQueue.retire(device, ran::incrementAndGet);
        VulkanRetireQueue.retire(device, ran::incrementAndGet);

        assertEquals(2, VulkanRetireQueue.dropFor(device));
        assertEquals(0, ran.get(), "dropped destructions must never execute");
        assertEquals(0, VulkanRetireQueue.pendingFor(device));
        assertEquals(0, VulkanRetireQueue.dropFor(device), "a second drop finds nothing left");
    }

    @Test
    void aDestroyerThatRetiresAgainIsDrainedByTheNextFlushFor() {
        // The VulkanDynamicMesh shape: the first drain re-queues the real destruction for a second drain. The
        // re-queued entry belongs to the same device and must not be lost or run early.
        long device = handle(5);
        AtomicInteger secondStage = new AtomicInteger();
        try {
            VulkanRetireQueue.retire(device, () -> VulkanRetireQueue.retire(device, secondStage::incrementAndGet));

            assertEquals(1, VulkanRetireQueue.flushFor(device));
            assertEquals(0, secondStage.get(), "an entry queued during a drain waits for the next one");
            assertEquals(1, VulkanRetireQueue.pendingFor(device));

            assertEquals(1, VulkanRetireQueue.flushFor(device));
            assertEquals(1, secondStage.get());
        } finally {
            VulkanRetireQueue.dropFor(device);
        }
    }

    @Test
    void aThrowingDestroyerNeitherPropagatesNorStopsTheBatch() {
        long device = handle(6);
        AtomicInteger ran = new AtomicInteger();
        try {
            VulkanRetireQueue.retire(device, () -> {
                throw new RuntimeException("destroy failed");
            });
            VulkanRetireQueue.retire(device, ran::incrementAndGet);

            assertEquals(2, VulkanRetireQueue.flushFor(device));
            assertEquals(1, ran.get(), "a failed destruction is leaked, not fatal, and the batch continues");
        } finally {
            VulkanRetireQueue.dropFor(device);
        }
    }

    @Test
    void untaggedEntriesAreNotRunByADeviceScopedDrain() {
        long device = handle(7);
        AtomicInteger untaggedRan = new AtomicInteger();
        try {
            VulkanRetireQueue.retire(untaggedRan::incrementAndGet);
            assertEquals(0, VulkanRetireQueue.flushFor(device));
            assertEquals(0, untaggedRan.get(), "an entry with no known owner is not executed on another device's drain");

            assertEquals(1, VulkanRetireQueue.flush(), "the unscoped drain still performs it");
            assertEquals(1, untaggedRan.get());
        } finally {
            VulkanRetireQueue.dropFor(0L);
            VulkanRetireQueue.dropFor(device);
        }
    }

    @Test
    void aDeadDevicesEntriesNeverReachTheSuccessorsDrain() {
        // The L06-01 sequence, at queue level: device one queues destructions during its teardown and dies
        // without a confirming wait, so its entries are dropped at its context delete. The successor device
        // then drains its own queue; the dead device's destroyers must not run there.
        long dead = handle(8);
        long successor = handle(9);
        AtomicInteger deadRan = new AtomicInteger();
        AtomicInteger successorRan = new AtomicInteger();
        VulkanRetireQueue.retire(dead, deadRan::incrementAndGet);
        VulkanRetireQueue.retire(dead, deadRan::incrementAndGet);
        assertEquals(2, VulkanRetireQueue.dropFor(dead));

        VulkanRetireQueue.retire(successor, successorRan::incrementAndGet);
        assertEquals(1, VulkanRetireQueue.flushFor(successor));
        assertEquals(1, successorRan.get());
        assertEquals(0, deadRan.get());
        assertEquals(0, VulkanRetireQueue.pendingFor(dead));
    }
}
