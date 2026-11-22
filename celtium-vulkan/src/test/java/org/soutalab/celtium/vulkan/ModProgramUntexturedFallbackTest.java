package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The untextured fallback's decision, exercised on the inputs the refusal decision itself reads: a
 * lowered and compiled program whose interface declares a texture coordinate, offered batches of the
 * layouts the rectangle family submits. The program below is the measured arrival's shape - Demonica's
 * GLSM textured FFP program (the reporter's program 74), which stays bound across the menu's
 * {@code Gui.drawRect} draws: those batches are POSITION (7) with the texture state disabled.
 */
class ModProgramUntexturedFallbackTest {

    @BeforeEach
    void reset() {
        ModProgramRegistry.clear();
    }

    /** Links a textured program of the GLSM FFP shape through the registry's own record. */
    private static ModProgramRegistry.Program texturedProgram() {
        ModProgramRegistry.shaderStage(7202, 0x8B31);
        ModProgramRegistry.shaderSource(7202,
                "attribute vec3 a_Position; attribute vec2 a_TexCoord0; varying vec2 v_TexCoord0;\n"
                        + "void main() { gl_Position = vec4(a_Position, 1.0); v_TexCoord0 = a_TexCoord0; }");
        ModProgramRegistry.shaderStage(7203, 0x8B30);
        ModProgramRegistry.shaderSource(7203,
                "uniform sampler2D u_Sampler0; varying vec2 v_TexCoord0;\n"
                        + "void main() { gl_FragColor = texture2D(u_Sampler0, v_TexCoord0); }");
        ModProgramRegistry.attach(7201, 7202);
        ModProgramRegistry.attach(7201, 7203);
        ModProgramRegistry.Program program = ModProgramRegistry.link(7201);
        assertTrue(program.lowered().ok(), () -> "lowering failed: " + program.lowered().failure());
        assertTrue(program.compiled(), () -> "compile failed: " + program.compileFailure());
        return program;
    }

    @Test
    void aPositionBatchUnderTheTexturedProgramIsRefusedOnTheAttributeGap() {
        // The pre-fix disposition, on the reporter's exact inputs: this is the refusal the fix removes
        // for texture-disabled batches only - the reason itself is unchanged.
        assertEquals("attribute a_TexCoord0 has no source in format 7",
                VulkanModProgramPipeline.unsupported(texturedProgram(), 7));
    }

    @Test
    void aTextureDisabledPositionBatchFallsBackAndATexturedOneDoesNot() {
        ModProgramRegistry.Program program = texturedProgram();
        assertTrue(VulkanModProgramPipeline.untexturedBuiltInFallback(program, 7, true),
                "a drawRect-shaped batch (POSITION, texture state disabled) must take the built-in path");
        assertFalse(VulkanModProgramPipeline.untexturedBuiltInFallback(program, 7, false),
                "the same batch with its texture state enabled is still refused");
    }

    @Test
    void theGradientRectLayoutFallsBackTheSameWay() {
        // Format 1 (POSITION_COLOR) is not mapped onto any program's attributes; the gradient-rect
        // family submits it with texturing disabled, so it shares the hole and the fallback.
        ModProgramRegistry.Program program = texturedProgram();
        assertEquals("batch format 1 is not mapped onto program attributes",
                VulkanModProgramPipeline.unsupported(program, 1));
        assertTrue(VulkanModProgramPipeline.untexturedBuiltInFallback(program, 1, true));
        assertFalse(VulkanModProgramPipeline.untexturedBuiltInFallback(program, 1, false));
    }

    @Test
    void aBatchTheProgramCanDrawIsNotAFallbackCandidate() {
        // POSITION_TEX (6) sources both attributes: the program draws the batch itself.
        ModProgramRegistry.Program program = texturedProgram();
        assertNull(VulkanModProgramPipeline.unsupported(program, 6));
        assertFalse(VulkanModProgramPipeline.untexturedBuiltInFallback(program, 6, true));
    }

    @Test
    void intentionalRefusalsStillRefuse() {
        // The ShaderGroup shape: a program the stub never lowered (here, a link that failed) is refused
        // whatever the batch's texture state - the fallback never rescues it.
        ModProgramRegistry.shaderStage(7302, 0x8B31);
        ModProgramRegistry.shaderSource(7302, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7301, 7302);
        ModProgramRegistry.Program unlowered = ModProgramRegistry.link(7301);
        assertFalse(unlowered.lowered().ok());
        assertFalse(VulkanModProgramPipeline.untexturedBuiltInFallback(unlowered, 7, true));
        // And no program at all is never a fallback candidate.
        assertFalse(VulkanModProgramPipeline.untexturedBuiltInFallback(null, 7, true));
    }
}
