package org.soutalab.celtium.vulkan;

import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentationInstanceExtensionsTest {
    @Test void win32SurfaceIsRequestedWhenLoaderSupportsIt() {
        assertEquals(List.of("VK_KHR_surface", "VK_KHR_win32_surface"),
                VulkanContext.presentationInstanceExtensions(
                        Set.of("VK_KHR_surface", "VK_KHR_win32_surface", "VK_EXT_debug_utils")));
    }

    @Test void linuxSurfaceExtensionsRemainSupportedAndMissingOnesAreNotForced() {
        assertEquals(List.of("VK_KHR_surface", "VK_KHR_xlib_surface", "VK_KHR_wayland_surface"),
                VulkanContext.presentationInstanceExtensions(
                        Set.of("VK_KHR_surface", "VK_KHR_xlib_surface", "VK_KHR_wayland_surface")));
        assertEquals(List.of(), VulkanContext.presentationInstanceExtensions(Set.of()));
    }
}
