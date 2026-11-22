package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_SRC_ALPHA;
import static org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO;
import static org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD;

/**
 * The content equality {@code CELTIUM_WEDGE_PIPE} found missing: the pack terrain pipeline key carries
 * this state's hash, so two states with the same fields must hash alike or the cache cannot hit.
 * <p>
 * What this proves: the equality and hash arithmetic only. Whether a measured leg's pipeline creations
 * actually drop is a GPU-run question this test does not answer.
 */
class VulkanColorBlendStateTest {

    /** The blend-disabled shape the pack terrain site builds fresh on every execution. */
    private static VulkanColorBlendState opaque() {
        return VulkanColorBlendState.of(false, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels());
    }

    @Test
    void freshlyBuiltEqualStatesAreEqualAndHashAlike() {
        // The regression pin: with the identity hash this pair hashed differently on every build, which
        // is what re-created the opaque gbuffers pipelines on every execution.
        VulkanColorBlendState a = opaque();
        VulkanColorBlendState b = opaque();
        assertEquals(a, b, "same fields, same state");
        assertEquals(a.hashCode(), b.hashCode(), "equal states must hash alike or no key built on the hash can hit");
    }

    @Test
    void legacyMatchesItsFieldShape() {
        VulkanColorBlendState built = VulkanColorBlendState.of(true, VK_BLEND_FACTOR_SRC_ALPHA,
                VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels());
        assertEquals(VulkanColorBlendState.legacy(), built);
        assertEquals(VulkanColorBlendState.legacy().hashCode(), built.hashCode());
    }

    @Test
    void anySingleFieldDifferenceBreaksEquality() {
        VulkanColorBlendState base = opaque();
        assertNotEquals(base, VulkanColorBlendState.of(true, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "blend flag");
        assertNotEquals(base, VulkanColorBlendState.of(false, 1, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "srcColor");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 1, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "dstColor");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 0, 0, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "srcAlpha");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ONE,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "dstAlpha");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                0, VK_BLEND_OP_ADD, VulkanColorBlendState.allChannels()), "colorOp");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, 0, VulkanColorBlendState.allChannels()), "alphaOp");
        assertNotEquals(base, VulkanColorBlendState.of(false, 0, 0, VK_BLEND_FACTOR_ONE, VK_BLEND_FACTOR_ZERO,
                VK_BLEND_OP_ADD, VK_BLEND_OP_ADD, 0), "writeMask");
    }

    @Test
    void equalityHandlesNullAndForeignTypes() {
        VulkanColorBlendState base = opaque();
        assertEquals(base, base, "reflexive");
        assertNotEquals(base, null);
        assertNotEquals(base, "noBlend");
    }
}
