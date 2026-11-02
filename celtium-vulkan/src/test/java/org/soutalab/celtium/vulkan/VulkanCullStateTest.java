package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_BACK_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_AND_BACK;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_BIT;
import static org.lwjgl.vulkan.VK10.VK_CULL_MODE_NONE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_CLOCKWISE;
import static org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE;

/**
 * The encoded cull state, and above all the promise its introduction rests on: GL's default pair has to select exactly
 * the pipeline the renderer baked before it existed, and every other pair has to select a different one.
 */
class VulkanCullStateTest {

    @Test
    void theGlDefaultPairIsThePipelineTheRendererAlwaysBaked() {
        int state = VulkanCullState.encode(true, VulkanCullState.GL_BACK, VulkanCullState.GL_CCW);
        assertEquals(VulkanCullState.BACK, state, "the encoded value is still 1, so every existing state key holds");
        assertEquals(VK_CULL_MODE_BACK_BIT, VulkanCullState.vkCullMode(state));
        assertEquals(VK_FRONT_FACE_CLOCKWISE, VulkanCullState.vkFrontFace(state),
                "GL counter-clockwise is Vulkan clockwise because the framebuffer Y axis is flipped");
        assertTrue(VulkanCullState.culls(state));
    }

    @Test
    void theFaceIsTheFaceGlNamesNotAModeNumber() {
        assertEquals(VK_CULL_MODE_FRONT_BIT,
                VulkanCullState.vkCullMode(VulkanCullState.encode(true, VulkanCullState.GL_FRONT,
                        VulkanCullState.GL_CCW)),
                "GL_FRONT must cull the faces GL calls front, which are also Vulkan's front here");
        assertEquals(VK_CULL_MODE_FRONT_AND_BACK,
                VulkanCullState.vkCullMode(VulkanCullState.encode(true, VulkanCullState.GL_FRONT_AND_BACK,
                        VulkanCullState.GL_CCW)));
        assertNotEquals(VulkanCullState.encode(true, VulkanCullState.GL_BACK, VulkanCullState.GL_CCW),
                VulkanCullState.encode(true, VulkanCullState.GL_FRONT, VulkanCullState.GL_CCW),
                "a different face has to key a different pipeline");
    }

    @Test
    void theWindingReversesTheVulkanFrontFace() {
        int clockwise = VulkanCullState.encode(true, VulkanCullState.GL_BACK, VulkanCullState.GL_CW);
        assertEquals(VK_FRONT_FACE_COUNTER_CLOCKWISE, VulkanCullState.vkFrontFace(clockwise));
        assertEquals(VK_CULL_MODE_BACK_BIT, VulkanCullState.vkCullMode(clockwise));
        assertNotEquals(VulkanCullState.encode(true, VulkanCullState.GL_BACK, VulkanCullState.GL_CCW), clockwise);
    }

    @Test
    void aDisabledCullForgetsTheFaceAndTheWinding() {
        int off = VulkanCullState.encode(false, VulkanCullState.GL_FRONT_AND_BACK, VulkanCullState.GL_CW);
        assertEquals(VulkanCullState.NONE, off);
        assertEquals(VK_CULL_MODE_NONE, VulkanCullState.vkCullMode(off));
        assertFalse(VulkanCullState.culls(off));
        assertEquals(off, VulkanCullState.encode(false, VulkanCullState.GL_BACK, VulkanCullState.GL_CCW),
                "a batch with culling off must not key itself by state it does not use");
    }

    @Test
    void anUnknownFaceFallsBackToGlBack() {
        // GL raises GL_INVALID_ENUM and keeps the previous mode; the previous mode for every caller that never asked is
        // back culling. Answering something else would cull the wrong side of every such batch.
        assertEquals(VulkanCullState.BACK, VulkanCullState.encode(true, 0, VulkanCullState.GL_CCW));
        assertEquals(VulkanCullState.BACK, VulkanCullState.encode(true, 0x1234, VulkanCullState.GL_CCW));
    }

    @Test
    void describeNamesTheStateForTheGroupLines() {
        assertEquals("off", VulkanCullState.describe(VulkanCullState.NONE));
        assertEquals("back/ccw", VulkanCullState.describe(VulkanCullState.BACK));
        assertEquals("front/ccw", VulkanCullState.describe(VulkanCullState.FRONT));
        assertEquals("front-and-back/cw", VulkanCullState.describe(VulkanCullState.FRONT_AND_BACK
                | VulkanCullState.FRONT_FACE_CW));
    }
}
