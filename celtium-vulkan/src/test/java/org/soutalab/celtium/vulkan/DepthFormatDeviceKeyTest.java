package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The depth format's device key, pinned without a device.
 * <p>
 * The choice {@link VulkanDepthFormat} latches is a probe answer about one physical device, and a process
 * can select a device more than once (a backend replacement, a renderer reload), so the latch may only be
 * spent on the device it was probed on. The identity is the device name: each context enumerates its own
 * handle wrapper for the same GPU, so wrapper identity alone would re-probe on every second context, and
 * two identical models share both a name and - for this contract - a probe answer, so a shared name is a
 * shared answer rather than a collision. What a unit test can pin is the rule itself; the probe stays gated
 * on the device, like the candidate machinery in {@link DepthFormatPairPinTest}.
 */
class DepthFormatDeviceKeyTest {

    @Test
    void theSameNameIsTheSameDevice() {
        assertTrue(VulkanDepthFormat.sameDeviceName("AMD Radeon RX 6400", "AMD Radeon RX 6400"));
        assertTrue(VulkanDepthFormat.sameDeviceName("Intel(R) UHD Graphics 630", "Intel(R) UHD Graphics 630"));
    }

    @Test
    void aDifferentNameIsADifferentDevice() {
        assertFalse(VulkanDepthFormat.sameDeviceName("AMD Radeon RX 6400", "Intel(R) UHD Graphics 630"));
        assertFalse(VulkanDepthFormat.sameDeviceName("AMD Radeon RX 6400", "AMD Radeon RX 6500 XT"));
    }

    @Test
    void anUnreadNameNeverMatches() {
        // A device whose name could not be read must not inherit another device's probe answer: the latch
        // falls back to wrapper identity, and a fresh wrapper for an unnamed device re-probes.
        assertFalse(VulkanDepthFormat.sameDeviceName(null, "AMD Radeon RX 6400"));
        assertFalse(VulkanDepthFormat.sameDeviceName("AMD Radeon RX 6400", null));
        assertFalse(VulkanDepthFormat.sameDeviceName(null, null));
    }
}
