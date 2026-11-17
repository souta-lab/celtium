package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.NativePassRegistry.NativePassSpec;
import org.soutalab.celtium.vulkan.NativePassRegistry.PassStage;
import org.soutalab.celtium.vulkan.NativePassRegistry.PassState;
import org.soutalab.celtium.vulkan.NativePassRegistry.RegisteredPass;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.NamedTargetSpec;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetClearPolicy;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetSizing;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;
import org.soutalab.celtium.vulkan.gl.ModShaderCompiler;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativePassRegistryTest {

    private static final String VALID_VERTEX_GLSL = """
            #version 450
            layout(location = 0) in vec3 inPos;
            layout(location = 1) in vec2 inUv;
            layout(location = 0) out vec2 vUv;
            void main() {
                vUv = inUv;
                gl_Position = vec4(inPos, 1.0);
            }
            """;

    private static final String VALID_FRAGMENT_GLSL = """
            #version 450
            layout(set = 0, binding = 0) uniform sampler2D uInput;
            layout(location = 0) in vec2 vUv;
            layout(location = 0) out vec4 outColor;
            void main() {
                outColor = texture(uInput, vUv);
            }
            """;

    private static final String INVALID_FRAGMENT_GLSL = """
            #version 450
            layout(location = 0) out vec4 outColor;
            void main() {
                outColor = undeclared_symbol_that_fails_compile;
            }
            """;

    @Test
    void emptyRegistryIsANoopAcrossStages() {
        RenderTargetRegistry targets = new RenderTargetRegistry();
        PublishedFrameTextures published = new PublishedFrameTextures();
        NativePassRegistry passes = new NativePassRegistry(targets);
        PassExecutor executor = new PassExecutor(null, targets, published, passes);

        assertFalse(passes.hasPasses());
        for (PassStage stage : PassStage.values()) {
            assertFalse(passes.hasPassesFor(stage));
            assertEquals(0, executor.executeStage(stage, null, 1280, 720, 1L, null));
            assertEquals(0, executor.dryRunStageForTest(stage));
        }
        assertEquals(0, passes.registeredCount());
        assertEquals(0, passes.compiledCount());
        assertEquals(0, passes.compileRefusalCount());
        assertEquals(0L, passes.executionCount());
        assertEquals(0L, passes.executionRefusalCount());
    }

    @Test
    void rejectsUndeclaredInputsDoubleWriteOutputsAndInvalidStages() {
        RenderTargetRegistry targets = new RenderTargetRegistry();
        targets.declareNamedTarget(NamedTargetSpec.color("scene_a", TargetFormatClass.RGBA8_UNORM,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));
        targets.declareNamedTarget(NamedTargetSpec.color("scene_b", TargetFormatClass.RGBA8_UNORM,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));

        NativePassRegistry passes = new NativePassRegistry(targets);

        // Invalid stage: null or unknown name
        assertThrows(IllegalArgumentException.class, () -> new NativePassSpec(
                "null_stage", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("scene_a"), "scene_b", PassState.opaqueFullscreen(), null));
        assertThrows(IllegalArgumentException.class, () -> PassStage.parse("NOT_A_STAGE"));

        // Undeclared input target
        assertThrows(IllegalArgumentException.class, () -> passes.register(new NativePassSpec(
                "missing_in", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("undeclared_input"), "scene_b", PassState.opaqueFullscreen(), PassStage.BEFORE_PRESENT)));

        // Undeclared output target
        assertThrows(IllegalArgumentException.class, () -> passes.register(new NativePassSpec(
                "missing_out", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("scene_a"), "undeclared_output", PassState.opaqueFullscreen(), PassStage.BEFORE_PRESENT)));

        // Self-read-write hazard
        assertThrows(IllegalArgumentException.class, () -> passes.register(new NativePassSpec(
                "self_rw", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("scene_a"), "scene_a", PassState.opaqueFullscreen(), PassStage.BEFORE_PRESENT)));

        // Register first writer to scene_b, then reject a second writer to scene_b (double write)
        passes.register(new NativePassSpec(
                "writer_1", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("scene_a"), "scene_b", PassState.opaqueFullscreen(), PassStage.AFTER_TERRAIN_SOLID));
        assertThrows(IllegalArgumentException.class, () -> passes.register(new NativePassSpec(
                "writer_2", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("scene_a"), "scene_b", PassState.opaqueFullscreen(), PassStage.BEFORE_PRESENT)));

        // Duplicate pass name
        assertThrows(IllegalArgumentException.class, () -> passes.register(new NativePassSpec(
                "writer_1", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of(), "scene_a", PassState.opaqueFullscreen(), PassStage.AFTER_TERRAIN_SOLID)));
    }

    @Test
    void compilesValidGlslAndRefusesInvalidGlslWithoutThrowingOrMixingWithStubCensus() {
        assertTrue(ModShaderCompiler.available(),
                "shaderc must be available in test runtime: " + ModShaderCompiler.unavailableReason());

        RenderTargetRegistry targets = new RenderTargetRegistry();
        targets.declareNamedTarget(NamedTargetSpec.color("src", TargetFormatClass.RGBA8_UNORM,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));
        targets.declareNamedTarget(NamedTargetSpec.color("dst_ok", TargetFormatClass.RGBA8_UNORM,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));
        targets.declareNamedTarget(NamedTargetSpec.color("dst_bad", TargetFormatClass.RGBA8_UNORM,
                TargetSizing.scaled(1.0f), TargetClearPolicy.CLEAR_EACH_FRAME));

        targets.noteNamedTargetAllocated("src", 1L, 11L, 21L, 1280, 720, 0);
        targets.noteNamedTargetAllocated("dst_ok", 2L, 12L, 22L, 1280, 720, 0);
        targets.noteNamedTargetAllocated("dst_bad", 3L, 13L, 23L, 1280, 720, 0);

        PublishedFrameTextures published = new PublishedFrameTextures();
        NativePassRegistry passes = new NativePassRegistry(targets);
        PassExecutor executor = new PassExecutor(null, targets, published, passes);

        RegisteredPass good = passes.register(new NativePassSpec(
                "composite_ok", VALID_VERTEX_GLSL, VALID_FRAGMENT_GLSL,
                List.of("src"), "dst_ok", PassState.alphaBlendFullscreen(), PassStage.AFTER_TERRAIN_TRANSLUCENT));
        assertTrue(good.compiled(), () -> "expected valid GLSL to compile, failure=" + good.compileFailure());
        assertNotNull(good.vertexSpirv());
        assertNotNull(good.fragmentSpirv());
        assertTrue(good.vertexSpirv().length > 20);
        assertTrue(good.fragmentSpirv().length > 20);

        RegisteredPass bad = passes.register(new NativePassSpec(
                "composite_bad", VALID_VERTEX_GLSL, INVALID_FRAGMENT_GLSL,
                List.of("src"), "dst_bad", PassState.opaqueFullscreen(), PassStage.AFTER_TERRAIN_TRANSLUCENT));
        assertFalse(bad.compiled());
        assertNull(bad.vertexSpirv());
        assertNull(bad.fragmentSpirv());
        assertNotNull(bad.compileFailure());

        assertEquals(2, passes.registeredCount());
        assertEquals(1, passes.compiledCount());
        assertEquals(1, passes.compileRefusalCount());

        int drawn = executor.dryRunStageForTest(PassStage.AFTER_TERRAIN_TRANSLUCENT);
        assertEquals(1, drawn);
        assertEquals(1L, passes.executionCount());
        assertEquals(1L, passes.executionRefusalCount());

        // Census separation: stub-side ModProgramRegistry and ImmediateStatistics must not receive native passes.
        assertNull(ModProgramRegistry.program(1));
        ImmediateStatistics immediateStats = new ImmediateStatistics();
        assertEquals(0L, immediateStats.shaderProgramRefusals());

        String census = passes.censusLine("unit-test");
        assertTrue(census.startsWith("[Celtium] CELTIUM_NATIVE_PASS via=unit-test "));
        assertTrue(census.contains("registered=2"));
        assertTrue(census.contains("compiled=1"));
        assertTrue(census.contains("compileRefusals=1"));
        assertTrue(census.contains("executed=1"));
        assertTrue(census.contains("execRefusals=1"));
    }
}
