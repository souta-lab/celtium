package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Source contract for the shared Vulkan factory's partial-create and success paths. */
class PostChainProgramCreateCleanupTest {
    private static final Path PIPELINE = Path.of(
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/FullscreenPassPipeline.java");

    @Test
    void failedGraphicsCreateDestroysEveryProducedHandle() throws Exception {
        String source = Files.readString(Files.exists(PIPELINE) ? PIPELINE : Path.of("..").resolve(PIPELINE));
        assertInOrder(source, "vertexModule = shaderModule(vertex);", "fragmentModule = shaderModule(fragment);",
                "handle.put(0, VK_NULL_HANDLE);", "VulkanContext.createGraphicsPipelines",
                "pipeline = handle.get(0);", "check(result, \"graphics pipeline\");",
                "catch (RuntimeException | Error failure)", "destroyIdle();", "throw failure;",
                "finally", "if (fragmentModule != 0) vkDestroyShaderModule",
                "if (vertexModule != 0) vkDestroyShaderModule");
        assertInOrder(source, "void destroyIdle()", "if (pipeline != 0) vkDestroyPipeline",
                "if (layout != 0) vkDestroyPipelineLayout", "if (setLayout != 0) vkDestroyDescriptorSetLayout",
                "pool = pipeline = layout = setLayout = 0;");
    }

    private static void assertInOrder(String source, String... fragments) {
        int index = 0;
        for (String fragment : fragments) {
            int next = source.indexOf(fragment, index);
            assertTrue(next >= 0, fragment);
            index = next + fragment.length();
        }
    }
}
