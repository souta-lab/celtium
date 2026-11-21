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

    /**
     * The longest one compile may hold its caller. glslang/shaderc has no internal deadline, so a pathological
     * mod shader could otherwise park the calling (render) thread forever and take the worker and its shaderc
     * handles with it (audit-gl-deep G3). A healthy compile of even a large program is seconds; thirty seconds
     * separates "slow" from "stuck" with room to spare. {@code -Dceltium.shaderc.timeoutMs=<n>} overrides, and a
     * value of 0 or less disables the deadline (the pre-fix behaviour, kept as a debugging escape hatch).
     */
    private static final long COMPILE_TIMEOUT_MS = Long.getLong("celtium.shaderc.timeoutMs", 30_000L).longValue();

    /**
     * Compiles in flight at once. Each worker carries a 16 MB stack reservation and its own shaderc compiler, and
     * nothing bounded how many a burst of program links could start. Over the limit a compile is refused at once
     * rather than queued - a queue would just move the unbounded wait to a different thread.
     */
    private static final int MAX_CONCURRENT_COMPILES =
            Integer.getInteger("celtium.shaderc.maxConcurrent", 4).intValue();

    private static final java.util.concurrent.atomic.AtomicInteger COMPILES_IN_FLIGHT =
            new java.util.concurrent.atomic.AtomicInteger();
    /** Compiles abandoned at the deadline; the worker's fate is named in {@link #compile}'s timeout branch. */
    private static final java.util.concurrent.atomic.AtomicLong COMPILE_TIMEOUTS =
            new java.util.concurrent.atomic.AtomicLong();
    /** Compiles refused because {@link #MAX_CONCURRENT_COMPILES} were already in flight. */
    private static final java.util.concurrent.atomic.AtomicLong COMPILE_CONCURRENCY_REFUSALS =
            new java.util.concurrent.atomic.AtomicLong();

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

    /**
     * {@return the SPIR-V words of one stage}; throws with shaderc's message when the stage does not compile, and
     * throws when the stage does not compile <em>in time</em> - the caller ({@code ModProgramRegistry}) already
     * turns any {@code RuntimeException} from here into a refused program, so a stuck compile now fails the one
     * program instead of the whole frame loop.
     */
    public static byte[] compile(String source, boolean vertex) {
        if (!available()) {
            throw new IllegalStateException("shaderc unavailable: " + unavailableReason());
        }
        if (COMPILES_IN_FLIGHT.incrementAndGet() > MAX_CONCURRENT_COMPILES) {
            COMPILES_IN_FLIGHT.decrementAndGet();
            COMPILE_CONCURRENCY_REFUSALS.incrementAndGet();
            throw new IllegalStateException("too many concurrent shader compiles (limit "
                    + MAX_CONCURRENT_COMPILES + "); refusing this program rather than queueing");
        }
        try {
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
                if (COMPILE_TIMEOUT_MS > 0L) {
                    worker.join(COMPILE_TIMEOUT_MS);
                } else {
                    worker.join();
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while compiling", interrupted);
            }
            if (worker.isAlive()) {
                // The deadline passed with the worker still inside shaderc. Releasing its compiler from here
                // would race the native compile it is running, so the worker is abandoned: it is a daemon (the
                // process can still exit), and if it ever returns, its own finally releases the handles. What
                // leaks for a truly stuck compile is one worker and one shaderc compiler until process exit -
                // named and counted here, against the previous shape where the render thread itself never
                // came back.
                long timeouts = COMPILE_TIMEOUTS.incrementAndGet();
                System.out.println("[Celtium] CELTIUM_MOD_SHADERC_TIMEOUT timeoutMs=" + COMPILE_TIMEOUT_MS
                        + " vertex=" + vertex + " sourceChars=" + source.length() + " timeouts=" + timeouts
                        + " (the worker is abandoned; the program is refused)");
                throw new IllegalStateException("shaderc compile did not finish within " + COMPILE_TIMEOUT_MS
                        + " ms; refusing the program (worker abandoned)");
            }
            if (thrown[0] != null) {
                throw thrown[0] instanceof RuntimeException runtime ? runtime
                        : new IllegalStateException("shaderc threw: " + thrown[0], thrown[0]);
            }
            return out[0];
        } finally {
            COMPILES_IN_FLIGHT.decrementAndGet();
        }
    }

    /** {@return the timeout and concurrency refusals so far}, in the census style of the stub's summaries. */
    public static String compileRefusalSummary() {
        return "shadercTimeouts=" + COMPILE_TIMEOUTS.get()
                + " shadercConcurrencyRefusals=" + COMPILE_CONCURRENCY_REFUSALS.get();
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
            if (compiler == 0L) {
                throw new IllegalStateException("shaderc_compiler_initialize returned null");
            }
            long options = 0L;
            long result = 0L;
            ByteBuffer sourceBytes = null;
            ByteBuffer fileNameBytes = null;
            ByteBuffer entryPointBytes = null;
            try {
                options = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_initialize();
                if (options == 0L) {
                    // probe() has always checked its compiler handle; compile() did not check either of its own,
                    // so a native-side allocation failure would have been called through a null handle
                    // (audit-gl-deep G5).
                    throw new IllegalStateException("shaderc_compile_options_initialize returned null");
                }
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_target_env(options,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_target_env_vulkan,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_env_version_vulkan_1_2);
                org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_set_optimization_level(options,
                        org.lwjgl.util.shaderc.Shaderc.shaderc_optimization_level_performance);
                // Use native heap allocation instead of LWJGL's CharSequence overload, which encodes onto the
                // 64 KiB thread-local MemoryStack and throws OutOfMemoryError ("Out of stack space.") on large
                // shader-pack stages such as ComplementaryReimagined's gbuffers_terrain.fsh.
                sourceBytes = org.lwjgl.system.MemoryUtil.memUTF8(source, false);
                fileNameBytes = org.lwjgl.system.MemoryUtil.memUTF8("celtium-mod-program", true);
                entryPointBytes = org.lwjgl.system.MemoryUtil.memUTF8("main", true);
                result = org.lwjgl.util.shaderc.Shaderc.shaderc_compile_into_spv(compiler, sourceBytes,
                        vertex ? org.lwjgl.util.shaderc.Shaderc.shaderc_vertex_shader
                                : org.lwjgl.util.shaderc.Shaderc.shaderc_fragment_shader,
                        fileNameBytes, entryPointBytes, options);
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
                if (entryPointBytes != null) {
                    org.lwjgl.system.MemoryUtil.memFree(entryPointBytes);
                }
                if (fileNameBytes != null) {
                    org.lwjgl.system.MemoryUtil.memFree(fileNameBytes);
                }
                if (sourceBytes != null) {
                    org.lwjgl.system.MemoryUtil.memFree(sourceBytes);
                }
                if (result != 0L) {
                    org.lwjgl.util.shaderc.Shaderc.shaderc_result_release(result);
                }
                if (options != 0L) {
                    org.lwjgl.util.shaderc.Shaderc.shaderc_compile_options_release(options);
                }
                org.lwjgl.util.shaderc.Shaderc.shaderc_compiler_release(compiler);
            }
        }
    }
}
