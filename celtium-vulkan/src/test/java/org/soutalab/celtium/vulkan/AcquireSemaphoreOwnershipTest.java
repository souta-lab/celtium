package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** An acquire semaphore is handed out only while no image owns it; re-acquiring an image frees its previous one. */
class AcquireSemaphoreOwnershipTest {
    @Test void aSlotIsNotReusedUntilItsImageIsAcquiredAgain() {
        long[] owners = new long[3];
        Arrays.fill(owners, -1L);
        // Three acquires return images 0, 1, 2 and take every slot.
        for (int image = 0; image < 3; image++) {
            int slot = VulkanSwapchain.freeAcquireSlot(owners, image);
            assertEquals(image, slot);
            VulkanSwapchain.assignAcquireOwner(owners, slot, image);
        }
        // Round-robin would hand slot 0 out now although image 0 has not come back: the reuse 01779 reports.
        assertEquals(-1, VulkanSwapchain.freeAcquireSlot(owners, 0), "every slot is owned, so the pool must grow");
    }

    @Test void reacquiringAnImageFreesItsPreviousSlot() {
        long[] owners = { 0L, 1L, 2L, -1L };   // a grown pool: slot 3 is free
        int slot = VulkanSwapchain.freeAcquireSlot(owners, 0);
        assertEquals(3, slot);
        VulkanSwapchain.assignAcquireOwner(owners, slot, 1);   // the acquire returned image 1 again
        assertArrayEquals(new long[] { 0L, -1L, 2L, 1L }, owners);
        assertEquals(1, VulkanSwapchain.freeAcquireSlot(owners, 0));
    }

    @Test void theCursorOnlyChoosesAmongFreeSlots() {
        long[] owners = { -1L, 5L, -1L };
        assertEquals(2, VulkanSwapchain.freeAcquireSlot(owners, 1));
        assertEquals(0, VulkanSwapchain.freeAcquireSlot(owners, 0));
    }
}
