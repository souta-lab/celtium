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

    @Test
    void terrainFallbackUsesDedicatedBufferWithoutPollutingReadsConstant() throws Exception {
        Path source = Path.of("src/main/java/org/soutalab/celtium/vulkan/VulkanModProgramPipeline.java");
        if (!Files.exists(source)) {
            source = Path.of("celtium-vulkan").resolve(source);
        }
        String text = Files.readString(source);
        assertTrue(text.contains("private VulkanUniformBuffer terrainZeroAttributeBuffer;"),
                "terrain zero-attribute fallback must use its own dedicated field");
        int createStart = text.indexOf("private void createTerrainPipeline(");
        int createEnd = text.indexOf("private static org.embeddedt.embeddium.impl.gl.attribute.GlVertexAttribute matchTerrainAttribute(");
        String createBody = text.substring(createStart, createEnd);
        assertTrue(createBody.contains("VK_BUFFER_USAGE_VERTEX_BUFFER_BIT"),
                "terrainZeroAttributeBuffer must be allocated with VK_BUFFER_USAGE_VERTEX_BUFFER_BIT");
        assertFalse(createBody.contains("this.constantRing"),
                "createTerrainPipeline must not reuse constantRing (keeps readsConstant() pure)");
        int bindStart = text.indexOf("void bindTerrainPipeline(");
        int bindEnd = text.indexOf("void bindTerrainUniformsAndSet(");
        String bindBody = text.substring(bindStart, bindEnd);
        assertTrue(bindBody.contains("this.terrainZeroAttributeBuffer"),
                "bindTerrainPipeline must bind terrainZeroAttributeBuffer at binding 1");
        int deleteStart = text.indexOf("public void delete()");
        int deleteEnd = text.indexOf("private void destroyHandles(");
        String deleteBody = text.substring(deleteStart, deleteEnd);
        assertTrue(deleteBody.contains("this.terrainZeroAttributeBuffer.delete()"),
                "delete() must free terrainZeroAttributeBuffer");
    }
}
