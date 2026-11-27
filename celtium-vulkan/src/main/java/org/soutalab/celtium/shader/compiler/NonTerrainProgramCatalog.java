/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.compiler;

import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.shader.AttachmentFormatKey;
import org.soutalab.celtium.core.shader.BlendStateSpec;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.NonTerrainPackTarget;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;

import java.lang.foreign.MemorySegment;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Compiled non-terrain programs of a shader pack ({@code gbuffers_entities}, {@code gbuffers_block},
 * {@code gbuffers_skybasic}, {@code gbuffers_clouds}, {@code gbuffers_water}, {@code gbuffers_hand},
 * {@code shadow}, etc.) and factory for runtime {@link NonTerrainPackTarget} views (V040-FINAL-PLAN
 * section 2.3, interface 2).
 */
public final class NonTerrainProgramCatalog {

    public record ProgramSpec(
            String programName,
            SpirvModuleBundle spirv,
            AttachmentFormatKey targetFormats,
            BlendStateSpec blendSpec,
            float alphaDiscardThreshold,
            DescriptorBindingPlan bindings) {
        public ProgramSpec {
            Objects.requireNonNull(programName, "programName");
            Objects.requireNonNull(spirv, "spirv");
            Objects.requireNonNull(targetFormats, "targetFormats");
            Objects.requireNonNull(blendSpec, "blendSpec");
            Objects.requireNonNull(bindings, "bindings");
        }
    }

    private final Map<String, ProgramSpec> programs;

    public NonTerrainProgramCatalog(Map<String, ProgramSpec> programs) {
        this.programs = Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(programs, "programs")));
    }

    public Optional<ProgramSpec> program(String programName) {
        return Optional.ofNullable(programs.get(programName));
    }

    public Set<String> programNames() {
        return programs.keySet();
    }

    public Map<String, ProgramSpec> allPrograms() {
        return programs;
    }

    /**
     * Binds HAL-created {@code VkPipeline} handles to an active stage's render targets and uniform
     * block, producing the {@link NonTerrainPackTarget} consumed directly by Lane 2.
     */
    public NonTerrainPackTarget bindStageTarget(
            String passName,
            ActiveRenderTargets renderTargets,
            MemorySegment uniformBuffer,
            Map<String, Long> pipelineHandles) {
        Objects.requireNonNull(passName, "passName");
        Objects.requireNonNull(renderTargets, "renderTargets");
        Objects.requireNonNull(uniformBuffer, "uniformBuffer");
        Map<String, Long> handlesCopy = Map.copyOf(Objects.requireNonNull(pipelineHandles, "pipelineHandles"));

        return new NonTerrainPackTarget() {
            @Override
            public String passName() {
                return passName;
            }

            @Override
            public ActiveRenderTargets renderTargets() {
                return renderTargets;
            }

            @Override
            public MemorySegment uniformBuffer() {
                return uniformBuffer;
            }

            @Override
            public long pipelineHandle(String programName) {
                return handlesCopy.getOrDefault(programName, NO_PIPELINE);
            }
        };
    }
}
