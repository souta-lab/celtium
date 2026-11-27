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

    record CompiledProgram(SpirvModuleBundle spirv, DescriptorBindingPlan bindings,
                           java.util.List<NamedDescriptorBinding> namedBindings) {
        public CompiledProgram {
            Objects.requireNonNull(spirv, "spirv");
            Objects.requireNonNull(bindings, "bindings");
            namedBindings = namedBindings == null ? java.util.List.of() : java.util.List.copyOf(namedBindings);
        }

        public CompiledProgram(SpirvModuleBundle spirv, DescriptorBindingPlan bindings) {
            this(spirv, bindings, java.util.List.of());
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

    /** The compute module words of a program returned by {@link #compileCompute}. */
    static int[] computeWords(CompiledProgram compiled) {
        return compiled.spirv().vertexWords();
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
            return new CompiledProgram(bundle, plan, namedBindings(lowered));
        }

        @Override
        public CompiledProgram compileCompute(String programName, String computeGlsl) {
            byte[] bytes = ModShaderCompiler.compileCompute(computeGlsl);
            int[] words = bytesToWords(bytes);
            if (words.length == 0 || words[0] != SpirvModuleBundle.SPIRV_MAGIC) {
                throw new IllegalStateException("compute compile of '" + programName
                        + "' did not produce SPIR-V");
            }
            // The graphics-shaped bundle cannot carry a compute module; the caller
            // (CeltiumPackCompiler) takes the module words from computeWords() instead.
            return new CompiledProgram(
                    new SpirvModuleBundle(words, words, null),
                    DescriptorBindingPlan.empty(),
                    scanComputeNamedBindings(computeGlsl));
        }

        private static java.util.List<NamedDescriptorBinding> namedBindings(
                ModGlslTranslator.Result lowered) {
            java.util.List<NamedDescriptorBinding> named = new ArrayList<>();
            for (ModGlslTranslator.SamplerBinding sampler : lowered.samplers()) {
                named.add(new NamedDescriptorBinding(
                        sampler.name(), sampler.binding(),
                        DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER));
            }
            for (ModGlslTranslator.ImageBinding image : lowered.images()) {
                named.add(new NamedDescriptorBinding(
                        image.name(), image.binding(),
                        DescriptorBindingPlan.Kind.STORAGE_IMAGE));
            }
            return named;
        }

        /**
         * Compute sources reach shaderc without the graphics lowering, so their descriptor
         * bindings are assigned in declaration order by the same rule the graphics route uses:
         * combined samplers first, then storage images, then storage buffers, each numbered
         * from 0 in source order. Only plain {@code uniform} declarations are scanned.
         */
        private static java.util.List<NamedDescriptorBinding> scanComputeNamedBindings(String glsl) {
            java.util.List<NamedDescriptorBinding> samplers = new ArrayList<>();
            java.util.List<NamedDescriptorBinding> images = new ArrayList<>();
            java.util.List<NamedDescriptorBinding> buffers = new ArrayList<>();
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "uniform\\s+(\\w+)\\s+(\\w+)\\s*;").matcher(glsl);
            while (matcher.find()) {
                String type = matcher.group(1);
                String name = matcher.group(2);
                if (type.startsWith("sampler") || type.startsWith("texture")) {
                    samplers.add(new NamedDescriptorBinding(name, samplers.size(),
                            DescriptorBindingPlan.Kind.COMBINED_IMAGE_SAMPLER));
                } else if (type.startsWith("image")) {
                    images.add(new NamedDescriptorBinding(name, images.size(),
                            DescriptorBindingPlan.Kind.STORAGE_IMAGE));
                }
            }
            java.util.regex.Matcher bufferMatcher = java.util.regex.Pattern.compile(
                    "buffer\\s+\\w+\\s*\\{[^}]*\\}\\s*(\\w+)\\s*;").matcher(glsl);
            while (bufferMatcher.find()) {
                buffers.add(new NamedDescriptorBinding(bufferMatcher.group(1), buffers.size(),
                        DescriptorBindingPlan.Kind.STORAGE_BUFFER));
            }
            java.util.List<NamedDescriptorBinding> all = new ArrayList<>();
            int binding = 0;
            for (NamedDescriptorBinding b : samplers) {
                all.add(new NamedDescriptorBinding(b.name(), binding++, b.kind()));
            }
            for (NamedDescriptorBinding b : images) {
                all.add(new NamedDescriptorBinding(b.name(), binding++, b.kind()));
            }
            for (NamedDescriptorBinding b : buffers) {
                all.add(new NamedDescriptorBinding(b.name(), binding++, b.kind()));
            }
            return all;
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
