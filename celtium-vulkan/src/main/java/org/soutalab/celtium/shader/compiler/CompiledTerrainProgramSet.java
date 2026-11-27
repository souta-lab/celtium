/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.compiler;

import org.soutalab.celtium.core.frame.FrameContext;
import org.soutalab.celtium.core.shader.TerrainPackProgramSet;
import org.soutalab.celtium.core.shader.TerrainPassKind;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;
import org.soutalab.celtium.shader.uniforms.ShaderFrameState;
import org.soutalab.celtium.shader.uniforms.Std140UniformPacker;

import java.lang.foreign.MemorySegment;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Native Celtium implementation of {@link TerrainPackProgramSet} (V040-FINAL-PLAN section 2.3,
 * interface 1). Holds the precompiled SPIR-V programs for the six closed {@link TerrainPassKind}
 * stages and fills the single std140 uniform block directly via Java 25 FFM {@link MemorySegment}.
 */
public final class CompiledTerrainProgramSet implements TerrainPackProgramSet {

    private final Map<TerrainPassKind, TerrainStageProgram> programs;
    private final TerrainVertexRequirements vertexRequirements;
    private final Std140UniformPacker uniformPacker;
    private final ShaderFrameState frameState;
    private final boolean hasShadowPass;
    private volatile boolean closed;

    public CompiledTerrainProgramSet(
            Map<TerrainPassKind, TerrainStageProgram> programs,
            TerrainVertexRequirements vertexRequirements,
            Std140UniformPacker uniformPacker,
            ShaderFrameState frameState) {
        this.programs = new EnumMap<>(TerrainPassKind.class);
        this.programs.putAll(Objects.requireNonNull(programs, "programs"));
        this.vertexRequirements = Objects.requireNonNull(vertexRequirements, "vertexRequirements");
        this.uniformPacker = Objects.requireNonNull(uniformPacker, "uniformPacker");
        this.frameState = Objects.requireNonNull(frameState, "frameState");
        this.hasShadowPass = this.programs.containsKey(TerrainPassKind.SHADOW_SOLID)
                || this.programs.containsKey(TerrainPassKind.SHADOW_CUTOUT)
                || this.programs.containsKey(TerrainPassKind.SHADOW_TRANSLUCENT);
    }

    @Override
    public boolean hasShadowPass() {
        return hasShadowPass;
    }

    @Override
    public Optional<TerrainStageProgram> programFor(TerrainPassKind kind) {
        Objects.requireNonNull(kind, "kind");
        return Optional.ofNullable(programs.get(kind));
    }

    @Override
    public TerrainVertexRequirements vertexRequirements() {
        return vertexRequirements;
    }

    @Override
    public void updateFrameUniforms(MemorySegment packUniformUbo, FrameContext ctx) {
        if (closed) {
            throw new IllegalStateException("TerrainPackProgramSet has been closed");
        }
        uniformPacker.writeFrame(packUniformUbo, ctx, frameState);
    }

    @Override
    public void close() {
        closed = true;
    }
}
