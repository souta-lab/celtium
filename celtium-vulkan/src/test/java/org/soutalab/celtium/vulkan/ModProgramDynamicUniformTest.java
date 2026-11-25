package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;

/**
 * Contract tests for non-terrain mod program dynamic uniform buffers
 * ({@code -Dceltium.vulkan.modProgramDynamicUniform}, default off): verifies the flag default,
 * ring sizing and per-epoch two-frames-in-flight cap, and descriptor-set key/offset equivalence.
 */
class ModProgramDynamicUniformTest {

    @Test
    @DisplayName("modProgramDynamicUniform flag defaults to off for paired A/B legs")
    void flagDefaultsToOff() {
        assertFalse(VulkanModProgramPipeline.MOD_DYNAMIC_UNIFORM,
                "modProgramDynamicUniform must default off unless explicitly enabled");
    }

    @Test
    @DisplayName("non-terrain ring expands to 512 slots only when both buffer-backed and dynamic-enabled")
    void ringSizingFollowsBufferAndFlag() {
        assertEquals(32, VulkanModProgramPipeline.celtium$modUniformRingSlots(true, false),
                "flag off preserves the legacy 32-slot ring");
        assertEquals(32, VulkanModProgramPipeline.celtium$modUniformRingSlots(false, true),
                "push-constant programs do not expand the ring");
        assertEquals(32, VulkanModProgramPipeline.celtium$modUniformRingSlots(false, false));
        assertEquals(VulkanModProgramPipeline.MOD_DYNAMIC_UNIFORM_RING_SLOTS,
                VulkanModProgramPipeline.celtium$modUniformRingSlots(true, true),
                "flag on with uniformsViaBuffer expands to MOD_DYNAMIC_UNIFORM_RING_SLOTS");
        assertEquals(512, VulkanModProgramPipeline.MOD_DYNAMIC_UNIFORM_RING_SLOTS);
    }

    @Test
    @DisplayName("per-epoch claim cap protects two frames in flight on the dynamic route while preserving legacy 32")
    void maxClaimsPerEpochProtectsTwoFramesInFlight() {
        assertEquals(32, VulkanModProgramPipeline.celtium$maxUniformClaimsPerEpoch(32, false),
                "legacy plain route keeps its 32-claim epoch cap");
        assertEquals(256, VulkanModProgramPipeline.celtium$maxUniformClaimsPerEpoch(512, true),
                "dynamic 512-slot ring caps per-epoch claims to half the ring (256) for 2 frames in flight");
        assertEquals(1024, VulkanModProgramPipeline.celtium$maxUniformClaimsPerEpoch(2048, true),
                "terrain 2048-slot ring keeps its 1024-claim epoch cap");
    }

    @Test
    @DisplayName("dynamic mod pipelines share one descriptor set across all 512 ring slots with identical effective offset")
    void dynamicModSlotsShareSetAndPreserveOffset() {
        long samplerHash = 0x55AA55AA1234L;
        long buffer = 0xCAFEBABEL;
        int stride = 1024;
        long baseKey = VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, 0, true);
        for (int slot : new int[] {0, 1, 31, 32, 255, 511}) {
            assertEquals(baseKey,
                    VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, slot, true),
                    "every slot of the 512-slot ring must reuse the same descriptor set key");
            long effectiveDynamic = VulkanModProgramPipeline.celtium$uniformWriteOffset(true, slot, stride)
                    + VulkanModProgramPipeline.celtium$uniformDynamicOffset(slot, stride);
            long effectivePlain = VulkanModProgramPipeline.celtium$uniformWriteOffset(false, slot, stride);
            assertEquals(effectivePlain, effectiveDynamic,
                    "effective UBO byte offset must match the plain route for slot " + slot);
        }
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,
                VulkanModProgramPipeline.celtium$uniformBindingType(true));
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                VulkanModProgramPipeline.celtium$uniformBindingType(false));
        assertTrue(VulkanModProgramPipeline.celtium$routeSnapshotSlot(42, true) == -1);
    }
}
