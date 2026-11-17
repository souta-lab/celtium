package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_GENERAL;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/**
 * Verifies the behavioral equivalence and invariants of the hot-path performance optimizations across
 * {@code celtium-vulkan}.
 */
class PerformanceOptimizationsTest {

    @Test
    void modProgramRegistryZeroAllocUniformsAndVersionTracking() {
        ModProgramRegistry.shaderSource(910,
                "#version 120\nuniform float u_Time;\nuniform vec4 u_Color;\nvoid main() { gl_Position = ftransform(); }\n");
        ModProgramRegistry.shaderSource(911,
                "#version 120\nuniform sampler2D u_Tex;\nuniform float u_Time;\nuniform vec4 u_Color;\n"
                        + "void main() { gl_FragColor = texture2D(u_Tex, vec2(u_Time)) * u_Color; }\n");
        ModProgramRegistry.attach(905, 910);
        ModProgramRegistry.attach(905, 911);
        ModProgramRegistry.Program prog = ModProgramRegistry.link(905);
        assertNotNull(prog);
        assertTrue(prog.lowered().ok(), () -> "expected ok lowered program, got: " + prog.lowered().failure());

        int timeLoc = ModProgramRegistry.location("u_Time");
        int colorLoc = ModProgramRegistry.location("u_Color");
        int texLoc = ModProgramRegistry.location("u_Tex");

        long v0 = prog.uniformVersion();
        ModProgramRegistry.uniformFloat1(905, timeLoc, 1.5f);
        long v1 = prog.uniformVersion();
        assertTrue(v1 > v0, "writing a new float uniform advances uniformVersion");
        assertArrayEquals(new float[] {1.5f}, prog.floats("u_Time"));

        ModProgramRegistry.uniformFloat1(905, timeLoc, 1.5f);
        assertEquals(v1, prog.uniformVersion(), "rewriting bit-identical float uniform does not bump uniformVersion");

        ModProgramRegistry.uniformFloat4(905, colorLoc, 0.1f, 0.2f, 0.3f, 0.4f);
        long v2 = prog.uniformVersion();
        assertTrue(v2 > v1);
        assertArrayEquals(new float[] {0.1f, 0.2f, 0.3f, 0.4f}, prog.floats("u_Color"));

        ModProgramRegistry.uniformInt1(905, texLoc, 2);
        long v3 = prog.uniformVersion();
        assertTrue(v3 > v2);
        assertArrayEquals(new int[] {2}, prog.ints("u_Tex"));
        ModProgramRegistry.uniformInt1(905, texLoc, 2);
        assertEquals(v3, prog.uniformVersion(), "rewriting identical int uniform does not bump uniformVersion");

        var directFloats = MemoryUtil.memAlloc(16);
        try {
            long addr = MemoryUtil.memAddress(directFloats);
            MemoryUtil.memPutFloat(addr, 0.5f);
            MemoryUtil.memPutFloat(addr + 4, 0.6f);
            MemoryUtil.memPutFloat(addr + 8, 0.7f);
            MemoryUtil.memPutFloat(addr + 12, 0.8f);
            ModProgramRegistry.uniformFloatPtr(905, colorLoc, 1, addr, 4, false, 4);
            long v4 = prog.uniformVersion();
            assertTrue(v4 > v3);
            assertArrayEquals(new float[] {0.5f, 0.6f, 0.7f, 0.8f}, prog.floats("u_Color"));
            ModProgramRegistry.uniformFloatPtr(905, colorLoc, 1, addr, 4, false, 4);
            assertEquals(v4, prog.uniformVersion());
        } finally {
            MemoryUtil.memFree(directFloats);
        }
    }

    @Test
    void modGlslTranslatorReservedWordsAndPatterns() {
        String vert = "#version 120\n"
                + "uniform float sample;\n"
                + "uniform float filter;\n"
                + "varying vec2 v_Tex;\n"
                + "void main() {\n"
                + "    v_Tex = gl_MultiTexCoord0.xy * sample + filter;\n"
                + "    gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;\n"
                + "}\n";
        String frag = "#version 120\n"
                + "uniform sampler2D DiffuseSampler;\n"
                + "uniform float sample;\n"
                + "varying vec2 v_Tex;\n"
                + "void main() {\n"
                + "    gl_FragColor = texture2D(DiffuseSampler, v_Tex) * sample;\n"
                + "}\n";
        ModGlslTranslator.Result res = ModGlslTranslator.translate(vert, frag);
        assertTrue(res.ok(), () -> "expected ok lowered result, got: " + res.failure());
        assertTrue(res.vertex().contains("celtium_r_sample"));
        assertTrue(res.vertex().contains("celtium_r_filter"));
        assertTrue(res.fragment().contains("celtium_r_sample"));
    }

    @Test
    void foreignTransitionZeroAllocOverloadReusesPendingSlot() {
        long cmd = 0x7000L;
        long image = 0xABCD0099L;
        VulkanGlTexture.declareForeignImage(image, VK_IMAGE_LAYOUT_GENERAL);
        VulkanGlTexture.beginLayoutRecording(cmd, "perf test frame");
        int from1 = VulkanGlTexture.foreignTransitionFrom(cmd, image, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, from1);
        assertFalse(VulkanGlTexture.noteForeignTransition(cmd, image, from1, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL));

        int from2 = VulkanGlTexture.foreignTransitionFrom(cmd, image, VK_IMAGE_LAYOUT_GENERAL);
        assertEquals(VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, from2);
        assertFalse(VulkanGlTexture.noteForeignTransition(cmd, image, from2, VK_IMAGE_LAYOUT_GENERAL));

        VulkanGlTexture.commitLayoutRecording(cmd);
        assertEquals(VK_IMAGE_LAYOUT_GENERAL, VulkanGlTexture.foreignLayoutOf(image));
    }

    @Test
    void nativeTextureEnvironmentUnit0FastPath() {
        NativeRasterState raster = new NativeRasterState();
        NativeTextureBindings bindings = new NativeTextureBindings(raster);
        NativeTextureEnvironment env = new NativeTextureEnvironment(bindings, raster);
        assertNull(env.combinedColor());

        env.color(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.COLOR, 0.2f, 0.4f, 0.6f, 0.8f);
        env.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.MODE,
                NativeTextureEnvironment.COMBINE);
        env.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.RGB,
                NativeTextureEnvironment.INTERPOLATE);
        float[] combined = env.combinedColor();
        assertNotNull(combined);
        assertArrayEquals(new float[] {0.2f, 0.4f, 0.6f, 0.8f}, combined);
        assertSame(combined, env.combinedColor(), "repeated queries reuse cached combinedColor");

        env.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.MODE,
                NativeTextureEnvironment.MODULATE);
        assertNull(env.combinedColor());
    }

    @Test
    void immediateFogStateZeroAllocMovedFromAndCachedColor() {
        ImmediateFogState fogState = new ImmediateFogState(new NativeRasterState());
        fogState.enabled(true);
        fogState.parameter(0x0B65, 9729);
        fogState.parameter(0x0B63, 10.0f);
        fogState.parameter(0x0B64, 100.0f);
        fogState.color(0.1f, 0.2f, 0.3f, 1.0f);

        float[] captured = fogState.snapshot();
        assertNotNull(captured);
        assertFalse(fogState.movedFrom(captured));

        float[] firstColor = fogState.getFogColor();
        float[] secondColor = fogState.getFogColor();
        assertSame(firstColor, secondColor, "getFogColor() must return cached array without per-call allocation");

        fogState.color(0.2f, 0.4f, 0.6f, 1.0f);
        assertTrue(fogState.movedFrom(captured));
        float[] updatedColor = fogState.getFogColor();
        assertEquals(0.2f, updatedColor[0], 1e-6f);
        assertEquals(0.4f, updatedColor[1], 1e-6f);
        assertEquals(0.6f, updatedColor[2], 1e-6f);
    }

    @Test
    void remapClipDepthFastPath() {
        float[] glProj = {
            1.2f, 0.1f, 0.2f, 0.3f,
            0.0f, 1.5f, 0.4f, 0.5f,
            0.0f, 0.0f, -1.002f, -1.0f,
            0.0f, 0.0f, -0.2002f, 0.0f
        };
        float[] vkProj = new float[16];
        VulkanTerrainGpuBackend.remapClipDepth(glProj, vkProj);
        for (int col = 0; col < 4; col++) {
            float m2c = glProj[col * 4 + 2];
            float m3c = glProj[col * 4 + 3];
            assertEquals(glProj[col * 4], vkProj[col * 4], 1e-6f);
            assertEquals(glProj[col * 4 + 1], vkProj[col * 4 + 1], 1e-6f);
            assertEquals(0.5f * (m2c + m3c), vkProj[col * 4 + 2], 1e-6f);
            assertEquals(m3c, vkProj[col * 4 + 3], 1e-6f);
        }
    }
}
