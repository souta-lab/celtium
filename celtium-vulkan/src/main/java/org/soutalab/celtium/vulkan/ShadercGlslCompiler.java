package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.shaderpack.GlslCompiler;
import org.soutalab.celtium.shaderpack.SpirvModule;
import org.soutalab.celtium.shaderpack.SpirvStageCompiler;
import org.soutalab.celtium.shaderpack.Stage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Compiles lowered Vulkan GLSL to SPIR-V in process, through LWJGL's shaderc binding.
 * <p>
 * The frontend's own probe shells out to a {@code glslangValidator} executable, which is fine while iterating on a
 * development machine and impossible in a distributed mod: a shipped renderer cannot ask the player to install a
 * compiler. shaderc is the same compiler (glslang + SPIRV-Tools, Apache-2.0/BSD) as a library, and LWJGL publishes
 * bindings for it under the LWJGL licence the rest of this module already builds against.
 * <p>
 * The bindings are not part of Cleanroom's LWJGL stack today, so the class checks for them and reports rather than
 * throwing: {@link #available()} answers false and {@link #compile} explains what is missing. That keeps a run without
 * them at "shaderpacks cannot be compiled here" instead of a crash at class load.
 */
public final class ShadercGlslCompiler implements GlslCompiler {
    // Declared before the fields that are initialised by calling the methods that write it: static initialisers run in
    // declaration order, so a "none" default placed after them overwrote the very failure they recorded. Third time
    // this shape has bitten in this codebase (FramePassTimings.INDEX and its cache, then this).
    private static String bindingFailure = "none";

    private static final boolean BINDINGS_PRESENT = bindingsPresent();
    private static final boolean NATIVES_PRESENT = bindingsPresent() && nativesPresent();

    private static boolean bindingsPresent() {
        try {
            // Class.forName initialises the class, which is where LWJGL loads the native library - so this single call
            // answers both questions and its throwable is the whole diagnosis. Recording it matters: the first version
            // caught it and reported "not on the classpath", which sent the search to the dependency list while the
            // actual failure was the native load.
            Class.forName("org.lwjgl.util.shaderc.Shaderc");
            return true;
        } catch (Throwable missing) {
            StringBuilder chain = new StringBuilder();
            for (Throwable t = missing; t != null; t = t.getCause()) {
                chain.append(chain.length() == 0 ? "" : " <- ").append(t.getClass().getSimpleName())
                        .append(": ").append(t.getMessage());
                if (chain.length() > 600) {
                    break;
                }
            }
            bindingFailure = chain.toString();
            return false;
        }
    }

    private static boolean nativesPresent() {
        if (!BINDINGS_PRESENT) {
            return false;
        }
        try {
            org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
            return true;
        } catch (Throwable missing) {
            return false;
        }
    }

    @Override public String name() {
        return "shaderc";
    }

    @Override public boolean available() {
        return BINDINGS_PRESENT && NATIVES_PRESENT;
    }

    /** {@return why the compiler cannot run here}, for the log line, never null. */
    public String unavailableReason() {
        if (!BINDINGS_PRESENT) {
            return "the shaderc binding could not be loaded (" + bindingFailure + ")";
        }
        if (!NATIVES_PRESENT) {
            return "the shaderc native library could not be loaded";
        }
        return "none";
    }

    @Override public SpirvModule compile(Stage stage, String source) throws SpirvStageCompiler.CompileFailure {
        if (!available()) {
            throw new SpirvStageCompiler.CompileFailure("shaderc unavailable: " + unavailableReason());
        }
        if (stage != Stage.VERTEX && stage != Stage.FRAGMENT) {
            throw new SpirvStageCompiler.CompileFailure("unsupported stage: " + stage);
        }
        long compiler = 0L;
        long options = 0L;
        long result = 0L;
        try {
            compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
            if (compiler == 0L) {
                throw new SpirvStageCompiler.CompileFailure("shaderc_compiler_initialize returned null");
            }
            options = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize();
            org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_target_env(options,
                    org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan,
                    org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_2);
            org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_optimization_level(options,
                    org.lwjgl.util.shaderc.Shaderc.shaderc_optimization_level_performance);
            result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(compiler, source,
                    stage == Stage.VERTEX
                            ? org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader
                            : org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader,
                    "celtium-stage", "main", options);
            if (result == 0L) {
                throw new SpirvStageCompiler.CompileFailure("shaderc_compile_into_spv returned null");
            }
            int status = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result);
            if (status != org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success) {
                String log = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
                throw new SpirvStageCompiler.CompileFailure("shaderc failed (" + status + "): "
                        + (log == null ? "no message" : log.strip()));
            }
            ByteBuffer spirv = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes(result);
            if (spirv == null) {
                throw new SpirvStageCompiler.CompileFailure("shaderc produced no bytes");
            }
            byte[] binary = new byte[spirv.remaining()];
            spirv.get(binary);
            return SpirvModule.inspect(stage, binary);
        } catch (SpirvStageCompiler.CompileFailure failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new SpirvStageCompiler.CompileFailure("shaderc threw: " + failure);
        } finally {
            if (result != 0L) {
                org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
            }
            if (options != 0L) {
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release(options);
            }
            if (compiler != 0L) {
                org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
            }
        }
    }

    /** {@return the SPIR-V word count of a module}, for a log line that does not have to print the whole blob. */
    public static int wordCount(SpirvModule module) {
        return module.binary().length / 4;
    }

    /** {@return the raw byte length of a UTF-8 source}, for the same reason. */
    public static int sourceBytes(String source) {
        return source.getBytes(StandardCharsets.UTF_8).length;
    }

    /** {@return the SPIR-V header version of a module}, for the log line; 0 when it cannot be read. */
    public static int headerVersion(SpirvModule module) {
        byte[] binary = module.binary();
        if (binary.length < 8) {
            return 0;
        }
        return ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
    }
}
