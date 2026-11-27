/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.compiler;

import org.soutalab.celtium.core.shader.PackExecutionPlan;
import org.soutalab.celtium.core.shader.TerrainPackProgramSet;
import org.soutalab.celtium.shader.block_rendering.BlockRenderingSettings;
import org.soutalab.celtium.shader.shaderpack.ShaderPack;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;

import java.util.Objects;

/**
 * Complete native output of {@link CeltiumPackCompiler}: the immutable {@link PackExecutionPlan},
 * the {@link TerrainPackProgramSet}, the {@link NonTerrainProgramCatalog}, resolved
 * {@link BlockRenderingSettings}, the mutable {@link ShaderFrameState} fed each frame, and the
 * parsed {@link ShaderPack} metadata (option tree / profiles / language map).
 */
public record CompiledShaderPack(
        PackExecutionPlan executionPlan,
        TerrainPackProgramSet terrainPrograms,
        NonTerrainProgramCatalog nonTerrainPrograms,
        BlockRenderingSettings blockSettings,
        ShaderFrameState frameState,
        ShaderPack sourcePack) implements AutoCloseable {

    public CompiledShaderPack {
        Objects.requireNonNull(executionPlan, "executionPlan");
        Objects.requireNonNull(terrainPrograms, "terrainPrograms");
        Objects.requireNonNull(nonTerrainPrograms, "nonTerrainPrograms");
        Objects.requireNonNull(blockSettings, "blockSettings");
        Objects.requireNonNull(frameState, "frameState");
        Objects.requireNonNull(sourcePack, "sourcePack");
    }

    @Override
    public void close() {
        terrainPrograms.close();
    }
}
