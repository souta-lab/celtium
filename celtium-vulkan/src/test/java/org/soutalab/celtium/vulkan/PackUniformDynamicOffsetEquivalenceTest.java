package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;

/**
 * The B3 equivalence contract (item 678 Layer B): moving a terrain pipeline's uniform binding from a
 * plain uniform buffer to a dynamic one must not change what any draw reads. Three rules carry that,
 * and each is pinned here without a device, in the shape of {@link DescriptorSetKeyContractTest}: the
 * offset the shader effectively reads is the same on both routes (the set's written offset plus the
 * bind's dynamic offset), the set's identity and its input snapshot stop seeing the staged slot (one
 * set serves every slot of a sampler group), and the binding type follows the route everywhere the
 * type is named (layout, pool, write).
 */
class PackUniformDynamicOffsetEquivalenceTest {

    @Test
    @DisplayName("the offset the shader reads is the same on the plain and dynamic routes")
    void effectiveOffsetIsTheSameOnBothRoutes() {
        int[] strides = {256, 512, 2048};
        int[] slots = {0, 1, 2, 1023, 2047};
        for (int stride : strides) {
            for (int slot : slots) {
                long plain = VulkanModProgramPipeline.celtium$uniformWriteOffset(false, slot, stride);
                long dynamic = VulkanModProgramPipeline.celtium$uniformWriteOffset(true, slot, stride)
                        + VulkanModProgramPipeline.celtium$uniformDynamicOffset(slot, stride);
                assertEquals((long) slot * stride, plain,
                        "the plain route's set names the slot's offset directly");
                assertEquals(plain, dynamic,
                        "the dynamic route's written offset plus its bind offset reads the same bytes");
            }
        }
        assertEquals(0L, VulkanModProgramPipeline.celtium$uniformWriteOffset(true, 7, 2048),
                "the dynamic route writes the ring's base, not a slot");
    }

    @Test
    @DisplayName("the dynamic route's set identity ignores the slot and still covers the sampler group")
    void dynamicSetIdentityIgnoresTheSlot() {
        long samplerHash = 0x123456789L;
        long buffer = 0xABCDEFL;
        long first = VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, 0, true);
        assertEquals(first, VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, 2047, true),
                "every slot of a sampler group keys to the same set on the dynamic route");
        assertNotEquals(first, VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash + 1, buffer, 0, true),
                "a different sampler group still keys to a different set");
        assertNotEquals(
                VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, 0, false),
                VulkanModProgramPipeline.celtium$uniformRouteSetKey(samplerHash, buffer, 1, false),
                "the plain route keeps the slot in the set's identity");
    }

    @Test
    @DisplayName("a set written once serves a later region whose staged slot differs")
    void dynamicSnapshotSlotServesEverySlot() {
        assertEquals(-1, VulkanModProgramPipeline.celtium$routeSnapshotSlot(5, true),
                "the dynamic route records the constant snapshot slot");
        assertEquals(VulkanModProgramPipeline.celtium$routeSnapshotSlot(5, true),
                VulkanModProgramPipeline.celtium$routeSnapshotSlot(900, true),
                "the recorded slot does not depend on the staged slot");
        assertEquals(5, VulkanModProgramPipeline.celtium$routeSnapshotSlot(5, false),
                "the plain route records the slot itself");

        long[] views = {10L, 20L};
        long[] samplers = {1L, 2L};
        int[] layouts = {7, 8};
        long[] imageViews = {};
        long[] snapshot = VulkanModProgramPipeline.celtium$packSetInputs(views, samplers, layouts, imageViews,
                2, 0, VulkanModProgramPipeline.celtium$routeSnapshotSlot(3, true));
        assertTrue(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot, views, samplers, layouts, imageViews,
                        2, 0, VulkanModProgramPipeline.celtium$routeSnapshotSlot(977, true)),
                "the snapshot taken for one region's slot matches the next region's lookup");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot, views, samplers, layouts, imageViews,
                        2, 0, VulkanModProgramPipeline.celtium$routeSnapshotSlot(977, false)),
                "a plain-route lookup for a real slot does not match the dynamic snapshot");
    }

    @Test
    @DisplayName("the binding type follows the route")
    void bindingTypeFollowsTheRoute() {
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC,
                VulkanModProgramPipeline.celtium$uniformBindingType(true),
                "the dynamic route's layout, pool and write name the dynamic type");
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                VulkanModProgramPipeline.celtium$uniformBindingType(false),
                "the plain route keeps the plain type");
        assertNotEquals(VulkanModProgramPipeline.celtium$uniformBindingType(true),
                VulkanModProgramPipeline.celtium$uniformBindingType(false),
                "the two routes name different descriptor types");
    }
}
