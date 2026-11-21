package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusal {@link VulkanModProgramPipeline#unsupported} gives for a program that did not lower: it must
 * carry the lowering's own reason, because the per-batch refusal line is the one a leg reads - the LINKED
 * line printed the reason once, at link time, possibly thousands of frames earlier.
 */
class ModProgramUnsupportedReasonTest {

    @Test
    void anUnloweredProgramRefusesWithTheLoweringReason() {
        // A vertex stage alone: the link fails for having no fragment stage.
        ModProgramRegistry.shaderSource(9102, "attribute vec2 position;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); }");
        ModProgramRegistry.attach(9101, 9102);
        ModProgramRegistry.Program program = ModProgramRegistry.link(9101);

        String refusal = VulkanModProgramPipeline.unsupported(program, 0);
        assertNotNull(refusal);
        assertTrue(refusal.contains("no fragment stage"),
                "the refusal names why the program did not lower: " + refusal);
    }

    @Test
    void anUnseenProgramRefusesPlainly() {
        assertNotNull(VulkanModProgramPipeline.unsupported(null, 0));
    }
}
