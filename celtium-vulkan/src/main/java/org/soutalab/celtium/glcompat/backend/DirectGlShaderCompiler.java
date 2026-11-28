package org.soutalab.celtium.glcompat.backend;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import org.soutalab.celtium.core.shader.SpirvModuleBundle;
import org.soutalab.celtium.vulkan.shader.ModGlslTranslator;
import org.soutalab.celtium.vulkan.shader.ModShaderCompiler;

/**
 * The converter's SPIR-V compiler (the plan's {@code SpirvCompiler}): a mod program's GLSL pair
 * goes through the measured translation corpus of the old {@code ModGlslTranslator} - which
 * rewrites GL-style shaders into the Vulkan conventions celtium's pipelines use and reports the
 * program's attributes, its uniform block layout (member offsets and total size) and its sampler
 * bindings - and then through {@code ModShaderCompiler}, whose disk cache
 * ({@code SpirvDiskCache}) serves repeat compiles and whose shaderc binding does the rest. The
 * sink's persistent {@code VkPipelineCache} (inside {@code VulkanContext}) is the third cache
 * layer, at pipeline granularity.
 * <p>
 * A refusal is data, not an exception: translation and compilation failures land in the
 * {@link CompiledProgram#failure()} field and the executor marks the program failed (its draws
 * are dropped and counted by the sink), which is the old path's honest-degradation shape.
 * Compute programs are refused here: the inherited compiler's stage kinds are vertex and
 * fragment only, and no 1.12.2 content mod in the gate set ships GL compute through this lane.
 */
public final class DirectGlShaderCompiler {

    private DirectGlShaderCompiler() {
    }

    /** One compiled (or refused) converter program, with the layout the executor packs against. */
    public record CompiledProgram(
            SpirvModuleBundle bundle,
            List<ModGlslTranslator.Attribute> attributes,
            List<ModGlslTranslator.UniformMember> uniforms,
            int uniformBlockSize,
            List<ModGlslTranslator.SamplerBinding> samplers,
            String failure) {

        public boolean ok() {
            return failure == null && bundle != null;
        }

        static CompiledProgram refused(String failure) {
            return new CompiledProgram(null, List.of(), List.of(), 0, List.of(), failure);
        }
    }

    /** Translates and compiles one vertex+fragment program pair. */
    public static CompiledProgram compileProgram(String vertexSource, String fragmentSource) {
        if (vertexSource == null || fragmentSource == null) {
            return CompiledProgram.refused("missing shader stage source");
        }
        ModGlslTranslator.Result translated;
        try {
            translated = ModGlslTranslator.translate(vertexSource, fragmentSource);
        } catch (RuntimeException failure) {
            return CompiledProgram.refused("translation threw: " + failure.getMessage());
        }
        if (!translated.ok()) {
            return CompiledProgram.refused(translated.failure());
        }
        try {
            int[] vertexWords = toWords(ModShaderCompiler.compile(translated.vertex(), true));
            int[] fragmentWords = toWords(ModShaderCompiler.compile(translated.fragment(), false));
            return new CompiledProgram(
                    SpirvModuleBundle.of(vertexWords, fragmentWords),
                    translated.attributes(), translated.uniforms(), translated.uniformBlockSize(),
                    translated.samplers(), null);
        } catch (RuntimeException failure) {
            return CompiledProgram.refused("compilation failed: " + failure.getMessage());
        }
    }

    /** Compute is refused by the inherited compiler's stage set (see the class doc). */
    public static CompiledProgram compileCompute(String computeSource) {
        return CompiledProgram.refused("compute stages are not supported by the converter compiler");
    }

    private static int[] toWords(byte[] bytes) {
        if (bytes == null || bytes.length % Integer.BYTES != 0) {
            throw new IllegalStateException("compiler returned a non-word-aligned module");
        }
        int[] words = new int[bytes.length / Integer.BYTES];
        ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder()).asIntBuffer().get(words);
        return words;
    }
}
