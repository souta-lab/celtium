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
        ShaderPack sourcePack,
        java.util.Map<String, java.util.List<NamedDescriptorBinding>> namedBindings,
        java.util.Map<String, int[]> computeModules,
        java.util.Map<String, org.soutalab.celtium.shader.shaderpack.IndirectPointer> computeIndirectPointers)
        implements AutoCloseable {

    public CompiledShaderPack {
        Objects.requireNonNull(executionPlan, "executionPlan");
        Objects.requireNonNull(terrainPrograms, "terrainPrograms");
        Objects.requireNonNull(nonTerrainPrograms, "nonTerrainPrograms");
        Objects.requireNonNull(blockSettings, "blockSettings");
        Objects.requireNonNull(frameState, "frameState");
        Objects.requireNonNull(sourcePack, "sourcePack");
        namedBindings = namedBindings == null ? java.util.Map.of() : java.util.Map.copyOf(namedBindings);
        computeModules = computeModules == null ? java.util.Map.of() : java.util.Map.copyOf(computeModules);
        computeIndirectPointers = computeIndirectPointers == null
                ? java.util.Map.of() : java.util.Map.copyOf(computeIndirectPointers);
    }

    /** The named descriptor bindings of one program (terrain kind key, program name or pass name). */
    public java.util.List<NamedDescriptorBinding> namedBindingsFor(String programKey) {
        return namedBindings.getOrDefault(programKey, java.util.List.of());
    }

    @Override
    public void close() {
        terrainPrograms.close();
    }
}
