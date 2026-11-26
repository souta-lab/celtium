package org.soutalab.celtium.vulkan.bindless;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT;
import static org.lwjgl.vulkan.EXTDescriptorIndexing.VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT;

class BindlessDescriptorArrayTest {

    @Test
    void bindingFlagsCarryTheWholeUpdateTrio() {
        int flags = BindlessDescriptorArray.bindingFlags();
        // Partially bound: unwritten slots may stay unbound. Update after bind: slots can be
        // written while the set is bound. Update unused while pending: a slot no in-flight
        // submission references may be written even then - the bit S5-1 adds, without which a
        // texture registering mid-frame is a validation error.
        assertEquals(VK_DESCRIPTOR_BINDING_PARTIALLY_BOUND_BIT_EXT
                | VK_DESCRIPTOR_BINDING_UPDATE_AFTER_BIND_BIT_EXT
                | VK_DESCRIPTOR_BINDING_UPDATE_UNUSED_WHILE_PENDING_BIT_EXT, flags);
    }
}
