package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An acquired image that cannot be presented has to be recovered by retiring the swapchain, because the 1.2 baseline has
 * no way to release an image (audit #367). The disposition is decided from the stages, not from the error text.
 */
class PresentStageDispositionTest {
    @Test
    void aFullySuccessfulAttemptPresents() {
        assertEquals("present", VulkanSwapchain.presentStageDisposition(true, true, true));
    }

    @Test
    void anAttemptThatNeverAcquiredHasNothingToRetire() {
        assertEquals("present", VulkanSwapchain.presentStageDisposition(false, false, false));
        assertEquals("present", VulkanSwapchain.presentStageDisposition(false, true, false));
    }

    @Test
    void anyFailureAfterTheAcquireRetires() {
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, false, false));
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, true, false));
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, false, true));
    }

    @Test
    void aPresentThatRunsAfterASuccessfulSubmissionPresents() {
        assertEquals("present", VulkanSwapchain.presentStageDisposition(true, true, true, true));
        assertEquals("present", VulkanSwapchain.presentStageDisposition(false, true, true, false));
    }

    @Test
    void aRefusedPresentRetiresEvenThoughTheSubmissionSignalled() {
        // The submission already signalled render-finished; a present that never runs never waits it,
        // so the refusal is a post-acquire failure like the earlier stages (R-51).
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, true, true, false));
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, true, false, false));
        assertEquals("retire", VulkanSwapchain.presentStageDisposition(true, false, true, false));
    }
}
