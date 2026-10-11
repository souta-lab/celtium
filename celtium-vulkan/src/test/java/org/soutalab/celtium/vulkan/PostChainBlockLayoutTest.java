package org.soutalab.celtium.vulkan;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The uniform-buffer shader variants must declare the block the push variants declare.
 * <p>
 * The executor compares the two modules' SPIR-V member offsets at run time as well, but that comparison only happens
 * on a device whose push-constant limit cannot carry the block - on a device with a 256-byte limit color_convolve
 * keeps pushing and a variant that had drifted would never be looked at. This test is where the translation is
 * checked on every build, from the same two modules the driver is handed.
 * <p>
 * The block sizes are asserted, not restated: they are the reason the route exists at all (each exceeds the 128-byte
 * Vulkan minimum {@code maxPushConstantsSize}), and a variant that shrank below that would silently stop exercising
 * the buffer route.
 */
class PostChainBlockLayoutTest {

    /** program -> (push vertex, push fragment); the variant of each module is {@code <name>_ubo.<stage>}. */
    private static final String[][] UBO_PROGRAMS = {
        { "color_convolve", "post_sobel.vert", "post_color_convolve.frag" },
        { "spider", "post_rotscale.vert", "post_spiderclip.frag" },
    };

    /**
     * The module's block as the executor reads it: the compiled module from the classpath, parsed by the same reader
     * the renderer uses. Read here without LWJGL's native memory, which a unit test JVM does not load.
     */
    private static VulkanPostChain.SpirvBlock block(String module) throws Exception {
        String resource = "/org/soutalab/celtium/vulkan/spirv/" + module + ".spv";
        try (InputStream stream = PostChainBlockLayoutTest.class.getResourceAsStream(resource)) {
            assertNotNull(stream, resource + " is not on the test classpath; the shader build did not run");
            ByteBuffer code = ByteBuffer.wrap(stream.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN);
            return VulkanPostChain.SpirvBlock.read(module, code);
        }
    }

    @Test
    void everyUniformBufferVariantDeclaresThePushVariantBlockMemberForMember() throws Exception {
        for (String[] program : UBO_PROGRAMS) {
            for (int module = 1; module < program.length; ++module) {
                String push = program[module];
                String ubo = VulkanPostChain.uboVariant(push);
                Map<String, Integer> pushBlock = block(push).offsets;
                VulkanPostChain.SpirvBlock variant = block(ubo);
                assertEquals(pushBlock, variant.offsets, ubo + " declares a different block than " + push
                        + ": the buffer route would write a block the shader does not read");
                assertFalse(pushBlock.isEmpty(), push + " declares no block member");
            }
        }
    }

    @Test
    void eachProgramsBlockExceedsTheGuaranteedPushConstantLimit() throws Exception {
        for (String[] program : UBO_PROGRAMS) {
            int bytes = 0;
            for (int module = 1; module < program.length; ++module) {
                bytes = Math.max(bytes, block(program[module]).bytes);
            }
            assertTrue(bytes > 128, program[0] + "'s block is " + bytes + "B, which fits the 128-byte guaranteed"
                    + " maxPushConstantsSize: it would keep the push route and this variant would go unexercised");
            assertEquals(bytes, block(VulkanPostChain.uboVariant(program[2])).bytes,
                    "the fragment stage carries the whole block");
        }
    }

    @Test
    void theVertexStageDeclaresASubsetOfTheFragmentBlockAtTheSameOffsets() throws Exception {
        for (String[] program : UBO_PROGRAMS) {
            Map<String, Integer> vertex = block(program[1]).offsets;
            Map<String, Integer> fragment = block(program[2]).offsets;
            for (Map.Entry<String, Integer> member : vertex.entrySet()) {
                assertEquals(member.getValue(), fragment.get(member.getKey()), program[0] + ": " + member.getKey()
                        + " is at " + member.getValue() + "B in the vertex stage and "
                        + fragment.get(member.getKey()) + "B in the fragment stage");
            }
        }
    }
}
