package org.soutalab.celtium.core.shader;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Interface 2 (design V040-FINAL-PLAN section 2.3): a loaded pack's complete, pre-resolved frame
 * graph. The pack compiler builds exactly one plan per pack load; the frame executor then runs it
 * verbatim - every render target, the whole uniform layout and every pass with its barriers
 * precomputed - without consulting the GLSM-shaped machinery this replaces. A plan is immutable:
 * a pack reload builds a new one.
 * <p>
 * Targets are named ({@code shadowtex0}, {@code colortex0..15}, {@code depthtex0..2}, custom
 * textures) and the executor aliases them all into the HAL's {@code TransientAttachmentSlab} in one
 * allocation. Barriers are {@code synchronization2} masks, precomputed per pass edge.
 */
public record PackExecutionPlan(
        List<RenderTargetSpec> renderTargets,
        PackUniformLayout uniformLayout,
        List<OrderedPass> passes) {

    public PackExecutionPlan {
        renderTargets = List.copyOf(Objects.requireNonNull(renderTargets, "renderTargets"));
        Objects.requireNonNull(uniformLayout, "uniformLayout");
        passes = List.copyOf(Objects.requireNonNull(passes, "passes"));
    }

    public Optional<RenderTargetSpec> targetNamed(String name) {
        return renderTargets.stream().filter(target -> target.name().equals(name)).findFirst();
    }

    /** One named render target of the plan. */
    public record RenderTargetSpec(
            String name,
            Kind kind,
            int format,
            float sizeScale,
            boolean pingPong,
            float[] clearColor) {

        public RenderTargetSpec {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(kind, "kind");
            if (sizeScale <= 0.0f) {
                throw new IllegalArgumentException("sizeScale must be positive: " + sizeScale);
            }
            clearColor = clearColor == null ? new float[4] : checkedClearColor(clearColor);
        }

        private static float[] checkedClearColor(float[] clearColor) {
            if (clearColor.length != 4) {
                throw new IllegalArgumentException("clearColor must be RGBA (4 floats)");
            }
            return clearColor.clone();
        }

        @Override
        public float[] clearColor() {
            return clearColor.clone();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof RenderTargetSpec spec)) {
                return false;
            }
            return format == spec.format && pingPong == spec.pingPong
                    && Float.compare(sizeScale, spec.sizeScale) == 0
                    && name.equals(spec.name) && kind == spec.kind
                    && Arrays.equals(clearColor, spec.clearColor);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(name, kind, format, sizeScale, pingPong);
            return 31 * result + Arrays.hashCode(clearColor);
        }

        public enum Kind {
            SHADOW_MAP,
            SHADOW_COLOR,
            COLOR_TEXTURE,
            DEPTH_TEXTURE,
            CUSTOM_TEXTURE
        }
    }

    /**
     * The pack's single uniform block layout: the built-in uniforms and the pack's custom
     * ({@code stareval}) uniforms packed into one std140 block, written once per frame through a
     * {@code MemorySegment} (see {@code TerrainPackProgramSet.updateFrameUniforms}).
     */
    public record PackUniformLayout(List<UniformField> fields, int blockSizeBytes) {

        public PackUniformLayout {
            fields = List.copyOf(Objects.requireNonNull(fields, "fields"));
            if (blockSizeBytes < 0) {
                throw new IllegalArgumentException("blockSizeBytes must not be negative");
            }
        }

        public Optional<UniformField> fieldNamed(String name) {
            return fields.stream().filter(field -> field.name().equals(name)).findFirst();
        }

        public record UniformField(String name, UniformType type, int offsetBytes, int sizeBytes) {
            public UniformField {
                Objects.requireNonNull(name, "name");
                Objects.requireNonNull(type, "type");
                if (offsetBytes < 0 || sizeBytes <= 0) {
                    throw new IllegalArgumentException("field extent must be positive");
                }
            }
        }

        public enum UniformType {
            FLOAT, INT, UINT,
            VEC2, VEC3, VEC4,
            IVEC2, IVEC3, IVEC4,
            MAT3, MAT4
        }
    }

    /**
     * A precomputed {@code synchronization2} barrier for one pass edge: pipeline stage and access
     * masks (64-bit, {@code VkPipelineStageFlags2} / {@code VkAccessFlags2} values carried opaquely).
     */
    public record BarrierSpec(long srcStageMask, long srcAccessMask, long dstStageMask, long dstAccessMask) {
        public static final BarrierSpec NONE = new BarrierSpec(0L, 0L, 0L, 0L);
    }

    /**
     * One pass of the plan, fully resolved: its program (absent for pure copy/clear passes), the
     * target names it reads and writes, and the barrier that must precede it. The eight record
     * kinds are the closed pass vocabulary of the design's D+ executor template (P0..P5).
     */
    public sealed interface OrderedPass {

        String name();

        Optional<SpirvModuleBundle> program();

        List<String> inputTargets();

        List<String> outputTargets();

        BarrierSpec barrier();

        /** A compute pass that runs before everything else (the template's {@code begin} programs). */
        record BeginComputePass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                                List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public BeginComputePass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }
        }

        /** A compute pass belonging to the shadow stage (the template's {@code shadowcomp} programs). */
        record ShadowComputePass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                                 List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public ShadowComputePass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }
        }

        /** The shadow rendering stage: the terrain kinds it draws, in order, plus the shadow entities. */
        record ShadowPass(String name, List<TerrainPassKind> terrainKinds, List<String> inputTargets,
                          List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public ShadowPass {
                name = requireName(name);
                terrainKinds = List.copyOf(terrainKinds);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.empty();
            }
        }

        /** A fullscreen prepare pass ahead of the G-buffer stage. */
        record PreparePass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                           List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public PreparePass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }
        }

        /** The G-buffer stage: terrain kinds in order, interleaved with the non-terrain programs. */
        record GBufferPass(String name, List<TerrainPassKind> terrainKinds, List<String> inputTargets,
                           List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public GBufferPass {
                name = requireName(name);
                terrainKinds = List.copyOf(terrainKinds);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.empty();
            }
        }

        /** A deferred lighting pass over the G-buffers. */
        record DeferredPass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                            List<String> outputTargets, BarrierSpec barrier) implements OrderedPass {
            public DeferredPass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }
        }

        /** One composite pass of the post chain; {@code flip} swaps the ping-pong colour targets. */
        record CompositePass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                             List<String> outputTargets, BarrierSpec barrier,
                             boolean flip) implements OrderedPass {
            public CompositePass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                outputTargets = List.copyOf(outputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }
        }

        /** The final pass: writes the swapchain image directly (the template's P5). */
        record FinalPass(String name, SpirvModuleBundle spirv, List<String> inputTargets,
                         BarrierSpec barrier) implements OrderedPass {
            /** The reserved output name of the swapchain image. */
            public static final String SWAPCHAIN = "swapchain";

            public FinalPass {
                name = requireName(name);
                inputTargets = List.copyOf(inputTargets);
                Objects.requireNonNull(barrier, "barrier");
            }

            @Override
            public Optional<SpirvModuleBundle> program() {
                return Optional.ofNullable(spirv);
            }

            @Override
            public List<String> outputTargets() {
                return List.of(SWAPCHAIN);
            }
        }

        private static String requireName(String name) {
            Objects.requireNonNull(name, "name");
            if (name.isEmpty()) {
                throw new IllegalArgumentException("a pass needs a name");
            }
            return name;
        }
    }
}
