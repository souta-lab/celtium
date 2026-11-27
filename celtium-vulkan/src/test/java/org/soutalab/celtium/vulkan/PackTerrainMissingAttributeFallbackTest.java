package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_SINT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32A32_UINT;

/**
 * Pins the zero-fallback contract for optional shader-pack terrain vertex attributes (e.g. {@code at_midBlock})
 * that are declared by the shader but absent from the active {@code GlVertexFormat}.
 */
final class PackTerrainMissingAttributeFallbackTest {

    @Test
    void zeroAttributeVkFormatMatchesGlslScalarFamily() {
        assertEquals(VK_FORMAT_R32G32B32A32_SFLOAT, VulkanModProgramPipeline.zeroAttributeVkFormat("vec4"));
        assertEquals(VK_FORMAT_R32G32B32A32_SFLOAT, VulkanModProgramPipeline.zeroAttributeVkFormat("vec3"));
        assertEquals(VK_FORMAT_R32G32B32A32_SFLOAT, VulkanModProgramPipeline.zeroAttributeVkFormat("float"));
        assertEquals(VK_FORMAT_R32G32B32A32_SFLOAT, VulkanModProgramPipeline.zeroAttributeVkFormat(null));
        assertEquals(VK_FORMAT_R32G32B32A32_UINT, VulkanModProgramPipeline.zeroAttributeVkFormat("uvec4"));
        assertEquals(VK_FORMAT_R32G32B32A32_UINT, VulkanModProgramPipeline.zeroAttributeVkFormat("uint"));
        assertEquals(VK_FORMAT_R32G32B32A32_SINT, VulkanModProgramPipeline.zeroAttributeVkFormat("ivec4"));
        assertEquals(VK_FORMAT_R32G32B32A32_SINT, VulkanModProgramPipeline.zeroAttributeVkFormat("int"));
    }

}
