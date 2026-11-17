package org.embeddedt.embeddium.impl.render.chunk;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.lists.ChunkRenderListIterable;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkFogMode;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderComponent;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderOptions;
import org.embeddedt.embeddium.impl.render.chunk.shader.TerrainPipelineConfig;
import org.embeddedt.embeddium.impl.render.chunk.shader.TerrainProgramProvider;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.terrain.material.Material;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;
import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShaderChunkRendererSeamTest {

    private static final FogService NOOP_FOG = new FogService() {
        @Override public float getFogEnd() { return 1.0f; }
        @Override public float getFogStart() { return 0.0f; }
        @Override public float getFogDensity() { return 0.0f; }
        @Override public int getFogShapeIndex() { return FOG_SHAPE_SPHERICAL; }
        @Override public float getFogCutoff() { return 1.0f; }
        @Override public float[] getFogColor() { return new float[] { 0f, 0f, 0f, 1f }; }
        @Override public ChunkShaderComponent.Factory<?> getFogMode() { return ChunkFogMode.NONE; }
    };

    private static final class TestShaderChunkRenderer extends ShaderChunkRenderer {
        int compileCalls;
        ChunkShaderOptions lastCompiledOptions;

        TestShaderChunkRenderer(RenderPassConfiguration<?> config) {
            super(null, config, NOOP_FOG, false);
        }

        @Override
        protected GlProgram<ChunkShaderInterface> compileProgram(ChunkShaderOptions options) {
            this.compileCalls++;
            this.lastCompiledOptions = options;
            return null;
        }

        @Override
        public void render(ChunkRenderMatrices matrices, CommandList commandList,
                           ChunkRenderListIterable renderLists, TerrainRenderPass pass,
                           CameraTransform occlusionCamera, CameraTransform camera) {
        }
    }

    private static TerrainRenderPass testPass(String name, AtomicInteger starts, AtomicInteger ends) {
        TerrainRenderPass.PipelineState state = new TerrainRenderPass.PipelineState() {
            @Override public void setup() { starts.incrementAndGet(); }
            @Override public void clear() { ends.incrementAndGet(); }
        };
        return TerrainRenderPass.builder()
                .name(name)
                .pipelineState(state)
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static RenderPassConfiguration<String> testConfig() {
        Material dummy = null;
        return new RenderPassConfiguration<>(Map.of(), Map.of(), dummy, dummy, dummy);
    }

    @Test
    void absentProviderPreservesDefaultBeginEndBehavior() {
        TestShaderChunkRenderer renderer = new TestShaderChunkRenderer(testConfig());
        assertNull(renderer.getProgramProvider());

        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        TerrainRenderPass pass = testPass("solid", starts, ends);

        assertNull(renderer.resolvePassOverride(pass));
        renderer.begin(pass);
        renderer.end(pass);

        assertEquals(1, starts.get());
        assertEquals(1, ends.get());
        assertEquals(1, renderer.compileCalls);
        assertSame(pass, renderer.lastCompiledOptions.pass());
    }

    @Test
    void installedProviderReceivesPassQueryDuringBegin() {
        TestShaderChunkRenderer renderer = new TestShaderChunkRenderer(testConfig());
        List<TerrainRenderPass> queried = new ArrayList<>();
        TerrainProgramProvider.PassOverride expected = new TerrainProgramProvider.PassOverride(
                null, ChunkMeshFormats.VANILLA_LIKE, TerrainProgramProvider.FaceCullingPolicy.FORCE_DISABLE);

        TerrainProgramProvider provider = pass -> {
            queried.add(pass);
            return expected;
        };

        renderer.setProgramProvider(provider);
        assertSame(provider, renderer.getProgramProvider());

        AtomicInteger starts = new AtomicInteger();
        AtomicInteger ends = new AtomicInteger();
        TerrainRenderPass pass = testPass("cutout", starts, ends);

        assertSame(expected, renderer.resolvePassOverride(pass));
        renderer.begin(pass);
        renderer.end(pass);

        assertEquals(2, queried.size());
        assertSame(pass, queried.get(0));
        assertSame(pass, queried.get(1));
        assertEquals(1, starts.get());
        assertEquals(1, ends.get());
        assertEquals(1, renderer.compileCalls);

        // Null policy defaults cleanly to DEFAULT
        TerrainProgramProvider.PassOverride defaulted =
                new TerrainProgramProvider.PassOverride(null, null, null);
        assertEquals(TerrainProgramProvider.FaceCullingPolicy.DEFAULT, defaulted.faceCullingPolicy());

        renderer.setProgramProvider(null);
        assertNull(renderer.resolvePassOverride(pass));
    }

    @Test
    void shadowPassRequestDefaultsToFalse() {
        assertFalse(TerrainPipelineConfig.isShadowPassRequested());
        try {
            TerrainPipelineConfig.setShadowPassRequested(true);
            assertTrue(TerrainPipelineConfig.isShadowPassRequested());
        } finally {
            TerrainPipelineConfig.setShadowPassRequested(false);
        }
        assertFalse(TerrainPipelineConfig.isShadowPassRequested());
    }
}
