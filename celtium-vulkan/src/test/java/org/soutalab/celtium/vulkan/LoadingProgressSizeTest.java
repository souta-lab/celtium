package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The loading screen's layout size ({@link VulkanTerrainGpuBackend#loadingProgressSize}, QUEUE item 687):
 * Forge's splash replacement passes {@code Minecraft.displayWidth/displayHeight}, which stay at the requested
 * size for the whole FML load, while the swapchain follows the window's real size and the present blit scales
 * the laid-out image to the whole swapchain extent. The decision is pinned here because it is the whole fix -
 * laying out at the request under a differently-sized window is what presented the splash stretched and
 * clipped for the entire load (winisol configuration A: a 1280x720 request in a 626x1056 pane).
 */
class LoadingProgressSizeTest {
    @Test
    void aSwapchainExtentDifferentFromTheRequestWins() {
        assertArrayEquals(new int[] {626, 1056},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 626, 1056));
    }

    @Test
    void aSwapchainExtentLargerThanTheRequestWins() {
        assertArrayEquals(new int[] {1920, 1080},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 1920, 1080));
    }

    @Test
    void aMatchingSwapchainExtentKeepsTheRequestedSize() {
        // The ordinary launch: the window got the size the game asked for, and the layout is exactly the
        // pre-fix one.
        assertArrayEquals(new int[] {1280, 720},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 1280, 720));
    }

    @Test
    void aZeroSwapchainExtentKeepsTheRequestedSize() {
        assertArrayEquals(new int[] {1280, 720},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 0, 0));
    }

    @Test
    void aHalfZeroSwapchainExtentKeepsTheRequestedSize() {
        assertArrayEquals(new int[] {1280, 720},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 626, 0));
        assertArrayEquals(new int[] {1280, 720},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, 0, 1056));
    }

    @Test
    void aNegativeSwapchainExtentKeepsTheRequestedSize() {
        assertArrayEquals(new int[] {1280, 720},
                VulkanTerrainGpuBackend.loadingProgressSize(1280, 720, -1, -1));
    }
}
