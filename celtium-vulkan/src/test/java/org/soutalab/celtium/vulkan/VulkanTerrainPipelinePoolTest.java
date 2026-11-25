package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
import static org.lwjgl.vulkan.VK10.VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC;

/**
 * The terrain descriptor pool must cover every set it allocates: one set per frame slot plus its
 * shadow variant, each consuming the layout's full binding list. The Tier-A wave-2 cutover added
 * the storage bindings 6/7/8 to the layout while the pool kept sizing storage buffers for binding
 * 5 alone, so vkAllocateDescriptorSets failed on every terrain pipeline creation and every terrain
 * pass of the run was skipped (Phase 1 gate leg r6, 2026-11-25). The layout and the pool now read
 * one binding-type table; this test pins the table against the named bindings and the per-type
 * counts the allocation arithmetic stands on.
 */
class VulkanTerrainPipelinePoolTest {

    @Test
    void everyNamedBindingHasItsDeclaredType() {
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, VulkanTerrainPipeline.bindingType(0));
        assertEquals(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VulkanTerrainPipeline.bindingType(1));
        assertEquals(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER, VulkanTerrainPipeline.bindingType(2));
        assertEquals(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.DYNAMIC_LIGHTS_BINDING));
        assertEquals(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.SHADOW_MAP_BINDING));
        assertEquals(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.REGION_OFFSETS_BINDING));
        assertEquals(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.SECTION_DESCS_BINDING));
        assertEquals(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.SECTION_FACTOR_BASES_BINDING));
        assertEquals(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                VulkanTerrainPipeline.bindingType(VulkanTerrainPipeline.RDH_FACTORS_BINDING));
    }

    @Test
    void perSetConsumptionCoversAllFourStorageBindings() {
        // Bindings 5, 6, 7 and 8 are storage buffers: a pool sized below four per set cannot
        // allocate even one full set row, which is the r6 failure this guards.
        assertEquals(4, VulkanTerrainPipeline.descriptorsPerSet(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER));
        assertEquals(3, VulkanTerrainPipeline.descriptorsPerSet(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER));
        assertEquals(1, VulkanTerrainPipeline.descriptorsPerSet(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER));
        assertEquals(1, VulkanTerrainPipeline.descriptorsPerSet(VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC));
    }

    @Test
    void poolCapacityIsPerSetConsumptionTimesEveryAllocatedSet() {
        int setCount = 2 * VulkanTerrainPipeline.DESCRIPTOR_SLOTS;
        int[] types = {VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER_DYNAMIC, VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER,
                VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER, VK_DESCRIPTOR_TYPE_STORAGE_BUFFER};
        int perSetTotal = 0;
        for (int type : types) {
            int perSet = VulkanTerrainPipeline.descriptorsPerSet(type);
            perSetTotal += perSet;
            // The invariant createDescriptorPool's arithmetic must satisfy for the single
            // vkAllocateDescriptorSets call that takes every set at once.
            assertEquals(perSet * setCount, VulkanTerrainPipeline.poolDescriptorCount(type, setCount));
        }
        assertEquals(9, perSetTotal);
    }
}
