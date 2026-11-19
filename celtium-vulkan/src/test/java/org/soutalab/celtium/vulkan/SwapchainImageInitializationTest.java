package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A swapchain image's content is undefined until something writes it, so every image gets a clear
 * (issue #422) - but the clear is a transfer, and a surface that does not offer TRANSFER_DST cannot take one.
 * The usage the fixture's surface produced (0x13: TRANSFER_SRC | TRANSFER_DST | COLOR_ATTACHMENT, from the
 * CELTIUM_SWAPCHAIN line) is the measured case the decision has to accept.
 * <p>
 * Since item 515 the clear runs per image at its first acquire instead of in bulk at creation (the bulk
 * clear touched images outside any ownership period - item 514), so the flag decisions behind it are pinned
 * here as pure functions. Cross-generation behaviour is not a pure function's business: the flags live on
 * the swapchain object and a rebuild is always a new object (design section 3.7, case 5).
 */
class SwapchainImageInitializationTest {
    @Test
    void theFixtureUsageTakesTheClear() {
        assertTrue(VulkanSwapchain.imageInitializationWanted(0x13));
    }

    @Test
    void transferDstAloneTakesTheClear() {
        assertTrue(VulkanSwapchain.imageInitializationWanted(0x2));
    }

    @Test
    void noTransferDstNoClear() {
        assertFalse(VulkanSwapchain.imageInitializationWanted(0x10));
        assertFalse(VulkanSwapchain.imageInitializationWanted(0x11));
        assertFalse(VulkanSwapchain.imageInitializationWanted(0));
    }

    @Test
    void newFlagsAreAllFalseAndSizedAsAsked() {
        boolean[] flags = VulkanSwapchain.newInitializationFlags(3);
        assertEquals(3, flags.length);
        for (boolean flag : flags) {
            assertFalse(flag);
        }
        assertEquals(0, VulkanSwapchain.newInitializationFlags(0).length);
    }

    @Test
    void aSkippedGenerationNeverNeedsAClear() {
        boolean[] flags = VulkanSwapchain.newInitializationFlags(3);
        for (int i = 0; i < flags.length; i++) {
            assertFalse(VulkanSwapchain.initializationNeeded(flags, false, i));
        }
    }

    @Test
    void anUnsetFlagNeedsTheClearAndASetFlagDoesNot() {
        boolean[] flags = VulkanSwapchain.newInitializationFlags(2);
        assertTrue(VulkanSwapchain.initializationNeeded(flags, true, 0));
        assertTrue(VulkanSwapchain.initializationNeeded(flags, true, 1));
        flags[0] = true;
        assertFalse(VulkanSwapchain.initializationNeeded(flags, true, 0));
        assertTrue(VulkanSwapchain.initializationNeeded(flags, true, 1));
    }

    @Test
    void outOfRangeAndNullAreNotJudgedNeeded() {
        boolean[] flags = VulkanSwapchain.newInitializationFlags(2);
        assertFalse(VulkanSwapchain.initializationNeeded(flags, true, -1));
        assertFalse(VulkanSwapchain.initializationNeeded(flags, true, 2));
        assertFalse(VulkanSwapchain.initializationNeeded(null, true, 0));
    }

    @Test
    void aFailureIsRecordedPerImage() {
        boolean[] failed = VulkanSwapchain.newInitializationFlags(3);
        assertFalse(VulkanSwapchain.initializationFailed(failed, 1));
        failed[1] = true;
        assertFalse(VulkanSwapchain.initializationFailed(failed, 0));
        assertTrue(VulkanSwapchain.initializationFailed(failed, 1));
        assertFalse(VulkanSwapchain.initializationFailed(failed, 2));
        assertFalse(VulkanSwapchain.initializationFailed(failed, -1));
        assertFalse(VulkanSwapchain.initializationFailed(failed, 3));
        assertFalse(VulkanSwapchain.initializationFailed(null, 0));
    }
}
