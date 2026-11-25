package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.KHRSurface.VK_ERROR_SURFACE_LOST_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_FIFO_RELAXED_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_IMMEDIATE_KHR;
import static org.lwjgl.vulkan.KHRSurface.VK_PRESENT_MODE_MAILBOX_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.KHRSwapchain.VK_SUBOPTIMAL_KHR;
import static org.lwjgl.vulkan.VK10.VK_ERROR_DEVICE_LOST;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;

class PresentEnginePolicyTest {
    @Test
    void desiredImageCountIsOneMoreThanTheMinimum() {
        assertEquals(3, PresentEngine.desiredImageCount(2, 0));
        assertEquals(2, PresentEngine.desiredImageCount(1, 0));
    }

    @Test
    void desiredImageCountIsClampedToAFiniteMaximum() {
        assertEquals(3, PresentEngine.desiredImageCount(2, 3));
        assertEquals(4, PresentEngine.desiredImageCount(4, 4));
    }

    @Test
    void desiredImageCountTreatsAMaximumOfZeroAsUnlimited() {
        assertEquals(9, PresentEngine.desiredImageCount(8, 0));
    }

    @Test
    void vsyncOffPrefersMailbox() {
        List<Integer> available = Arrays.asList(
                VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR, VK_PRESENT_MODE_MAILBOX_KHR);
        assertEquals(VK_PRESENT_MODE_MAILBOX_KHR, PresentEngine.choosePresentMode(available, false));
    }

    @Test
    void vsyncOffFallsBackToImmediateWithoutMailbox() {
        List<Integer> available = Arrays.asList(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_IMMEDIATE_KHR);
        assertEquals(VK_PRESENT_MODE_IMMEDIATE_KHR, PresentEngine.choosePresentMode(available, false));
    }

    @Test
    void vsyncOnPrefersFifoRelaxed() {
        List<Integer> available = Arrays.asList(
                VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_FIFO_RELAXED_KHR, VK_PRESENT_MODE_MAILBOX_KHR);
        assertEquals(VK_PRESENT_MODE_FIFO_RELAXED_KHR, PresentEngine.choosePresentMode(available, true));
    }

    @Test
    void vsyncOnFallsBackToFifoWithoutFifoRelaxed() {
        List<Integer> available = Arrays.asList(VK_PRESENT_MODE_FIFO_KHR, VK_PRESENT_MODE_MAILBOX_KHR);
        assertEquals(VK_PRESENT_MODE_FIFO_KHR, PresentEngine.choosePresentMode(available, true));
    }

    @Test
    void anEmptyModeListStillAnswersFifo() {
        assertEquals(VK_PRESENT_MODE_FIFO_KHR,
                PresentEngine.choosePresentMode(Collections.<Integer>emptyList(), false));
        assertEquals(VK_PRESENT_MODE_FIFO_KHR,
                PresentEngine.choosePresentMode(Collections.<Integer>emptyList(), true));
    }

    @Test
    void recoverableResultsAreNotFatal() {
        assertFalse(PresentEngine.resultIsFatal(VK_SUCCESS));
        assertFalse(PresentEngine.resultIsFatal(VK_SUBOPTIMAL_KHR));
        assertFalse(PresentEngine.resultIsFatal(VK_ERROR_OUT_OF_DATE_KHR));
        // A lost surface is answered by recreating the surface and swapchain; it is not a lost device (R2).
        assertFalse(PresentEngine.resultIsFatal(VK_ERROR_SURFACE_LOST_KHR));
    }

    @Test
    void aLostDeviceIsFatal() {
        assertTrue(PresentEngine.resultIsFatal(VK_ERROR_DEVICE_LOST));
    }
}
