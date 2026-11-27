package org.soutalab.celtium.shader;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;
import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.core.shader.PackExecutionPlan.OrderedPass;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.core.shader.TerrainPackProgramSet;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.shader.compiler.CeltiumPackCompiler;
import org.soutalab.celtium.shader.compiler.CompiledShaderPack;
import org.soutalab.celtium.shader.features.PackCompilerCapabilities;
import org.soutalab.celtium.shader.shaderpack.materialmap.NamespacedId;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CeltiumPackCompilerTest {

    private static final String COMMON_GLSL = """
            // Shared helper included via #include
            vec4 boostColor(vec4 c) {
                return c * vec4(1.0);
            }
            """;

    private static final String TERRAIN_VSH = """
            #version 120
            attribute vec2 mc_Entity;
            varying vec2 texcoord;
            varying vec4 tint;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                tint = gl_Color;
                float entityFlag = mc_Entity.x;
                gl_Position = ftransform() + vec4(entityFlag * 0.0, 0.0, 0.0, 0.0);
            }
            """;

    private static final String TERRAIN_FSH = """
            #version 120
            #include "/common.glsl"
            uniform sampler2D gtexture;
            uniform float myDayFactor;
            varying vec2 texcoord;
            varying vec4 tint;
            void main() {
                gl_FragData[0] = boostColor(texture2D(gtexture, texcoord) * tint) * myDayFactor;
            }
            """;

    private static final String ENTITY_VSH = """
            #version 120
            varying vec2 texcoord;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                gl_Position = ftransform();
            }
            """;

    private static final String ENTITY_FSH = """
            #version 120
            uniform sampler2D gtexture;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(gtexture, texcoord);
            }
            """;

    private static final String POST_VSH = """
            #version 120
            varying vec2 texcoord;
            void main() {
                texcoord = gl_MultiTexCoord0.xy;
                gl_Position = ftransform();
            }
            """;

    private static final String POST_FSH = """
            #version 120
            uniform sampler2D colortex0;
            varying vec2 texcoord;
            void main() {
                gl_FragData[0] = texture2D(colortex0, texcoord);
            }
            """;

    @Test
    void compilesMiniPackEndToEndIntoNativeContractsWithRealShaderc(@TempDir Path tempDir) throws IOException {
        Path shaders = tempDir.resolve("shaders");
        Files.createDirectories(shaders);

        Files.writeString(shaders.resolve("shaders.properties"), """
                uniform.float.myDayFactor = clamp(sunAngle * 2.0, 0.0, 1.0)
                """, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("block.properties"), """
                block.10008 = minecraft:water
                block.10018 = minecraft:leaves
                """, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("common.glsl"), COMMON_GLSL, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("shadow.vsh"), TERRAIN_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("shadow.fsh"), TERRAIN_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"), TERRAIN_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), TERRAIN_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_water.vsh"), TERRAIN_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_water.fsh"), TERRAIN_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_entities.vsh"), ENTITY_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_entities.fsh"), ENTITY_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("deferred.vsh"), POST_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("deferred.fsh"), POST_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("composite.vsh"), POST_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("composite.fsh"), POST_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("final.vsh"), POST_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("final.fsh"), POST_FSH, StandardCharsets.UTF_8);

        CeltiumPackCompiler compiler = new CeltiumPackCompiler();
        try (CompiledShaderPack compiled = compiler.compile(
                shaders, Map.of(), PackCompilerCapabilities.DEFAULT_VULKAN_13)) {

            // 1. PackExecutionPlan (Interface 2)
            PackExecutionPlan plan = compiled.executionPlan();
            assertTrue(plan.targetNamed("shadowtex0").isPresent());
            assertTrue(plan.targetNamed("colortex0").isPresent());
            assertTrue(plan.targetNamed("depthtex0").isPresent());
            assertTrue(plan.uniformLayout().fieldNamed("myDayFactor").isPresent());

            List<OrderedPass> passes = plan.passes();
            assertTrue(passes.stream().anyMatch(p -> p instanceof OrderedPass.ShadowPass));
            assertTrue(passes.stream().anyMatch(p -> p instanceof OrderedPass.GBufferPass));
            assertTrue(passes.stream().anyMatch(p -> p instanceof OrderedPass.DeferredPass));
            assertTrue(passes.stream().anyMatch(p -> p instanceof OrderedPass.CompositePass cp && cp.flip()));
            OrderedPass.FinalPass finalPass = passes.stream()
                    .filter(p -> p instanceof OrderedPass.FinalPass)
                    .map(p -> (OrderedPass.FinalPass) p)
                    .findFirst()
                    .orElseThrow();
            assertEquals(List.of(OrderedPass.FinalPass.SWAPCHAIN), finalPass.outputTargets());
            assertTrue(finalPass.program().isPresent());
            assertEquals(SpirvModuleBundle.SPIRV_MAGIC, finalPass.program().get().vertexWords()[0]);
            assertEquals(SpirvModuleBundle.SPIRV_MAGIC, finalPass.program().get().fragmentWords()[0]);

            // 2. TerrainPackProgramSet (Interface 1)
            TerrainPackProgramSet terrain = compiled.terrainPrograms();
            assertTrue(terrain.hasShadowPass());
            assertTrue(terrain.vertexRequirements().mcEntity());
            assertEquals(2, terrain.vertexRequirements().auxiliaryBytesPerQuad());

            for (TerrainPassKind kind : TerrainPassKind.values()) {
                TerrainStageProgram prog = terrain.programFor(kind).orElseThrow(
                        () -> new AssertionError("Missing compiled terrain stage: " + kind));
                assertEquals(SpirvModuleBundle.SPIRV_MAGIC, prog.spirv().vertexWords()[0]);
                assertEquals(SpirvModuleBundle.SPIRV_MAGIC, prog.spirv().fragmentWords()[0]);
            }
            assertEquals(0.0f, terrain.programFor(TerrainPassKind.MAIN_SOLID).orElseThrow().alphaDiscardThreshold(), 1e-6f);
            assertEquals(0.1f, terrain.programFor(TerrainPassKind.MAIN_CUTOUT).orElseThrow().alphaDiscardThreshold(), 1e-6f);
            assertEquals(0.0001f, terrain.programFor(TerrainPassKind.MAIN_TRANSLUCENT).orElseThrow().alphaDiscardThreshold(), 1e-6f);

            // 3. Single-block std140 UBO update via FFM MemorySegment
            compiled.frameState().sunAngle = 0.35f;
            PackExecutionPlan.PackUniformLayout.UniformField dayField =
                    plan.uniformLayout().fieldNamed("myDayFactor").orElseThrow();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment ubo = arena.allocate(plan.uniformLayout().blockSizeBytes());
                terrain.updateFrameUniforms(ubo, new FrameContext(5L, 120, 0.5f));
                assertEquals(0.70f, ubo.get(ValueLayout.JAVA_FLOAT_UNALIGNED, dayField.offsetBytes()), 1e-5f);

                // 4. NonTerrainPackTarget (Interface 2 -> Lane 2)
                assertTrue(compiled.nonTerrainPrograms().program("gbuffers_entities").isPresent());
                ActiveRenderTargets dummyTargets = new ActiveRenderTargets(List.of(), null, 1280, 720);
                NonTerrainPackTarget target = compiled.nonTerrainPrograms().bindStageTarget(
                        "gbuffers_entities", dummyTargets, ubo, Map.of("gbuffers_entities", 0xCAFE_BABEL));
                assertEquals(0xCAFE_BABEL, target.pipelineHandle("gbuffers_entities"));
                assertEquals(NonTerrainPackTarget.NO_PIPELINE, target.pipelineHandle("gbuffers_nonexistent"));
            }

            // 5. BlockRenderingSettings from block.properties
            assertEquals(10008, compiled.blockSettings().resolveBlockId(new NamespacedId("minecraft", "water"), 0));
            assertEquals(10018, compiled.blockSettings().resolveBlockId(new NamespacedId("minecraft", "leaves"), 0));
        }
    }

    @Test
    void compilesShadowlessPackWithoutShadowPassOrShadowTargets(@TempDir Path tempDir) throws IOException {
        Path shaders = tempDir.resolve("shaders");
        Files.createDirectories(shaders);
        Files.writeString(shaders.resolve("gbuffers_terrain.vsh"), ENTITY_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("gbuffers_terrain.fsh"), ENTITY_FSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("final.vsh"), POST_VSH, StandardCharsets.UTF_8);
        Files.writeString(shaders.resolve("final.fsh"), POST_FSH, StandardCharsets.UTF_8);

        CeltiumPackCompiler compiler = new CeltiumPackCompiler();
        try (CompiledShaderPack compiled = compiler.compile(
                shaders, Map.of(), PackCompilerCapabilities.DEFAULT_VULKAN_13)) {
            assertFalse(compiled.terrainPrograms().hasShadowPass());
            assertTrue(compiled.executionPlan().targetNamed("shadowtex0").isEmpty());
            assertTrue(compiled.executionPlan().passes().stream().noneMatch(p -> p instanceof OrderedPass.ShadowPass));
            assertFalse(compiled.terrainPrograms().vertexRequirements().mcEntity());
            assertEquals(0, compiled.terrainPrograms().vertexRequirements().auxiliaryBytesPerQuad());
        }
    }
}
