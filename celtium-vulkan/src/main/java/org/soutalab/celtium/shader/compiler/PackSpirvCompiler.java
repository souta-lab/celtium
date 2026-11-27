/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.shader.compiler;

import org.jetbrains.annotations.Nullable;
import org.soutalab.celtium.core.shader.DescriptorBindingPlan;
import org.soutalab.celtium.core.shader.ShaderStage;
import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModShaderCompiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/**
 * Compiles transformed GLSL sources into Vulkan SPIR-V {@link SpirvModuleBundle}s and derives
 * their {@link DescriptorBindingPlan}.
 */
public interface PackSpirvCompiler {

    record CompiledProgram(SpirvModuleBundle spirv, DescriptorBindingPlan bindings) {
        public CompiledProgram {
            Objects.requireNonNull(spirv, "spirv");
            Objects.requireNonNull(bindings, "bindings");
        }
    }

    CompiledProgram compileGraphics(
            String programName,
            String vertexGlsl,
            String fragmentGlsl,
            @Nullable String geometryGlsl);

    @Nullable
    default CompiledProgram compileCompute(String programName, String computeGlsl) {
        return null;
    }

    /**
     * Default production compiler backed by Celtium's {@link ModGlslTranslator} (Vulkan GLSL 450
     * lowering + fractional pow NaN guard + std140 UBO packing) and {@link ModShaderCompiler}
     * (LWJGL shaderc + SpirvDiskCache).
     */
    PackSpirvCompiler SHADERC = new PackSpirvCompiler() {
        @Override
        public CompiledProgram compileGraphics(
                String programName,
                String vertexGlsl,
                String fragmentGlsl,
                @Nullable String geometryGlsl) {
            ModGlslTranslator.Result lowered = ModGlslTranslator.translate(vertexGlsl, fragmentGlsl);
            if (!lowered.ok()) {
                throw new IllegalStateException(
                        "Failed to lower shader program '" + programName + "' to Vulkan GLSL 450: " + lowered.failure());
            }
            byte[] vertBytes = ModShaderCompiler.compile(lowered.vertex(), true);
            byte[] fragBytes = ModShaderCompiler.compile(lowered.fragment(), false);
            int[] vertWords = bytesToWords(vertBytes);
            int[] fragWords = bytesToWords(fragBytes);

            SpirvModuleBundle bundle = SpirvModuleBundle.of(vertWords, fragWords);
            DescriptorBindingPlan plan = buildDescriptorPlan(lowered, geometryGlsl != null);
            return new CompiledProgram(bundle, plan);
        }

        private static int[] bytesToWords(byte[] bytes) {
            if (bytes == null || bytes.length == 0 || (bytes.length & 3) != 0) {
                throw new IllegalStateException("SPIR-V byte output has invalid length: "
                        + (bytes == null ? -1 : bytes.length));
            }
            int[] words = new int[bytes.length >>> 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(words);
            return words;
        }

        private static DescriptorBindingPlan buildDescriptorPlan(
                ModGlslTranslator.Result lowered, boolean hasGeometry) {
            List<DescriptorBindingPlan.Binding> bindings = new ArrayList<>();
            EnumSet<ShaderStage> graphicsStages = hasGeometry
                    ? EnumSet.of(ShaderStage.VERTEX, ShaderStage.GEOMETRY, ShaderStage.FRAGMENT)
                    : EnumSet.of(ShaderStage.VERTEX, ShaderStage.FRAGMENT);

            for (ModGlslTranslator.SamplerBinding sampler : lowered.samplers()) {
                bindings.add(new DescriptorBindingPlan.Binding(
                        sampler.binding(),
                        DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER,
                        graphicsStages,
                        1));
            }
            for (ModGlslTranslator.ImageBinding image : lowered.images()) {
                bindings.add(new DescriptorBindingPlan.Binding(
                        image.binding(),
                        DescriptorBindingPlan.Kind.STORAGE_IMAGE,
                        EnumSet.of(ShaderStage.FRAGMENT),
                        1));
            }
            if (lowered.uniformsViaBuffer() || lowered.uniformBlockSize() > 0) {
                int uboBinding = lowered.samplers().size() + lowered.images().size();
                bindings.add(new DescriptorBindingPlan.Binding(
                        uboBinding,
                        DescriptorBindingPlan.Kind.UNIFORM_BUFFER,
                        graphicsStages,
                        1));
            }
            return new DescriptorBindingPlan(bindings);
        }
    };
}
