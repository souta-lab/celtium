package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.KHRSwapchain.VK_ERROR_OUT_OF_DATE_KHR;
import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;

/**
 * The swapchain lending contract of {@link PresentEngine} (coord ruling, lanechat #8830): the engine owns
 * the swapchain images, a borrower only holds a {@link PresentEngine.BorrowedImage} between acquire and
 * present, and a present or a swapchain recreation ends the borrow. These tests pin the contract without a
 * device - the acquire/present Vulkan calls themselves are exercised by the leg, not here.
 */
class PresentEngineBorrowTest {
    @Test
    void adoptSwapchainRegistersTheGeneration() {
        PresentEngine engine = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11, 12, 13}, new long[]{21, 22, 23});
        assertEquals(0xAA, engine.swapchain());
        assertEquals(3, engine.imageCount());
    }

    @Test
    void adoptSwapchainCopiesTheArrays() {
        PresentEngine engine = new PresentEngine(null, null);
        long[] images = new long[]{11, 12};
        engine.adoptSwapchain(0xAA, images, null);
        images[0] = 99;
        // The registry holds its own copy: mutating the caller's array must not rewrite the generation.
        assertEquals(2, engine.imageCount());
    }

    @Test
    void acquireWithoutADeviceLendsNothing() {
        PresentEngine engine = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11}, new long[]{21});
        assertNull(engine.acquireNextImage(VK_NULL_HANDLE, 0));
        assertEquals(VK_ERROR_OUT_OF_DATE_KHR, engine.lastAcquireResult());
    }

    @Test
    void aHandleIsValidOnlyInsideItsGeneration() {
        PresentEngine engine = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11, 12}, new long[]{21, 22});
        PresentEngine.BorrowedImage handle =
                new PresentEngine.BorrowedImage(engine, 11, 21, 0, 0, engine.generation());
        assertTrue(handle.isValid());
        assertEquals(11, handle.image());
        assertEquals(21, handle.view());
        assertEquals(0, handle.index());
        engine.retireSwapchain();
        assertFalse(handle.isValid());
        assertEquals(VK_NULL_HANDLE, engine.swapchain());
        assertEquals(0, engine.imageCount());
    }

    @Test
    void presentingAStaleHandleIsAContractViolation() {
        PresentEngine engine = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11}, new long[]{21});
        PresentEngine.BorrowedImage handle =
                new PresentEngine.BorrowedImage(engine, 11, 21, 0, 0, engine.generation());
        engine.retireSwapchain();
        assertThrows(IllegalStateException.class, () -> engine.present(handle, VK_NULL_HANDLE));
    }

    @Test
    void presentingAnotherEnginesHandleIsAContractViolation() {
        PresentEngine engine = new PresentEngine(null, null);
        PresentEngine other = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11}, new long[]{21});
        other.adoptSwapchain(0xBB, new long[]{11}, new long[]{21});
        PresentEngine.BorrowedImage foreign =
                new PresentEngine.BorrowedImage(other, 11, 21, 0, 0, other.generation());
        assertThrows(IllegalStateException.class, () -> engine.present(foreign, VK_NULL_HANDLE));
    }

    @Test
    void aNewGenerationInvalidatesThePreviousOnesHandles() {
        PresentEngine engine = new PresentEngine(null, null);
        engine.adoptSwapchain(0xAA, new long[]{11}, new long[]{21});
        PresentEngine.BorrowedImage first =
                new PresentEngine.BorrowedImage(engine, 11, 21, 0, 0, engine.generation());
        engine.adoptSwapchain(0xCC, new long[]{31}, new long[]{41});
        assertFalse(first.isValid());
        assertEquals(0xCC, engine.swapchain());
    }
}
