package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;

/**
 * Compiles a lowered mod program stage (Vulkan GLSL 450 from {@link ModGlslTranslator}) to SPIR-V in process, with
 * LWJGL's shaderc binding, when that binding and its native library are present.
 * <p>
 * The binding is not part of Cleanroom's LWJGL stack and is not bundled yet (a distribution decision), so this class
 * never references it until {@link #available()} has loaded it: a run without it reports why and keeps refusing the
 * program's batches, instead of failing at class load. The binding was proven to run inside the game on 2026-10-19
 * (the deleted shaderpack frontend, `5c1990ee8`), which is also where the two traps below were measured.
 */
public final class ModShaderCompiler {
    private static String unavailableReason = "not checked";
    private static Boolean available;

    /**
     * glslang parses by recursion; on a game thread with the default stack a large program ran out of stack space
     * (measured on 2026-10-19 with a shaderpack's terrain program), so compilations get a thread with a large stack.
     */
    private static final long COMPILE_STACK_BYTES = 16L * 1024L * 1024L;

    private ModShaderCompiler() {
    }

    /** {@return whether the binding and its native load}; checked once, the failure kept for the log. */
    public static synchronized boolean available() {
        if (available == null) {
            try {
                // Initialising the binding class is where LWJGL loads the native, so its throwable is the diagnosis
                // (2026-10-19: the real failure was the core liblwjgl.so, not shaderc).
                Class.forName("org.lwjgl.util.shaderc.Shaderc");
                Holder.probe();
                available = Boolean.TRUE;
                unavailableReason = "none";
            } catch (Throwable missing) {
                available = Boolean.FALSE;
                StringBuilder chain = new StringBuilder();
                for (Throwable t = missing; t != null && chain.length() < 400; t = t.getCause()) {
                    chain.append(chain.length() == 0 ? "" : " <- ").append(t.getClass().getSimpleName()).append(": ")
                            .append(t.getMessage());
                }
                unavailableReason = chain.toString();
            }
        }
        return available.booleanValue();
    }

    public static synchronized String unavailableReason() {
        return unavailableReason;
    }

    /** {@return the SPIR-V words of one stage}; throws with shaderc's message when the stage does not compile. */
    public static byte[] compile(String source, boolean vertex) {
        if (!available()) {
            throw new IllegalStateException("shaderc unavailable: " + unavailableReason());
        }
        byte[][] out = new byte[1][];
        Throwable[] thrown = new Throwable[1];
        Thread worker = new Thread(null, () -> {
            try {
                out[0] = Holder.compile(source, vertex);
            } catch (Throwable failure) {
                thrown[0] = failure;
            }
        }, "celtium-mod-shaderc", COMPILE_STACK_BYTES);
        worker.setDaemon(true);
        worker.start();
        try {
            worker.join();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while compiling", interrupted);
        }
        if (thrown[0] != null) {
            throw thrown[0] instanceof RuntimeException runtime ? runtime
                    : new IllegalStateException("shaderc threw: " + thrown[0], thrown[0]);
        }
        return out[0];
    }

    /** The only class that names the binding; loaded after {@link #available()} succeeded. */
    private static final class Holder {
        static void probe() {
            long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
            if (compiler == 0L) {
                throw new IllegalStateException("shaderc_compiler_initialize returned null");
            }
            org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
        }

        static byte[] compile(String source, boolean vertex) {
            long compiler = org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_initialize();
            long options = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize();
            long result = 0L;
            try {
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_target_env(options,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_2);
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_optimization_level(options,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_optimization_level_performance);
                result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(compiler, source,
                        vertex ? org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader
                                : org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader,
                        "celtium-mod-program", "main", options);
                if (result == 0L) {
                    throw new IllegalStateException("shaderc_compile_into_spv returned null");
                }
                if (org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_compilation_status(result)
                        != org.lwjgl.util.shaderc.Shaderc.shaderc_compilation_status_success) {
                    String log = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_error_message(result);
                    throw new IllegalStateException("shaderc: " + (log == null ? "no message" : log.strip()));
                }
                ByteBuffer bytes = org.lwjgl.util.shaderc.Shaderc.shaderc_result_get_bytes(result);
                if (bytes == null) {
                    throw new IllegalStateException("shaderc produced no bytes");
                }
                byte[] spirv = new byte[bytes.remaining()];
                bytes.get(spirv);
                return spirv;
            } finally {
                if (result != 0L) {
                    org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
                }
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release(options);
                org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
            }
        }
    }
}
