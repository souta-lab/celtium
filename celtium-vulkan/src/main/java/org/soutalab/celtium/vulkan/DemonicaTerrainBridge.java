package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderInterface;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

import java.lang.reflect.Method;

/**
 * Reflection bridge to Demonica's {@code com.demonica.celeritas.terrain.ShaderTerrain.packProgram}.
 * Keeps {@code celtium-vulkan} free of any compile-time dependency on Demonica while allowing
 * {@link VulkanTerrainGpuBackend} to obtain the active shader pack's {@code gbuffers_terrain} /
 * {@code gbuffers_water} / {@code shadow} program and let Iris push its uniforms, samplers, and MRT FBO.
 * <p>
 * The bridge is also the connection's adjudication surface: {@link #state} answers whether
 * Demonica is absent, present without a program for a pass, or supplying one, with the reason,
 * and reports the answer through {@link ConnectionGate}. A failed resolution or invocation is
 * a reason string here, never a swallowed exception indistinguishable from absence.
 */
final class DemonicaTerrainBridge {
    private static volatile boolean resolved;
    private static Method packProgramMethod;
    private static volatile String resolveFailure;
    private static volatile boolean resolveClassMissing;
    private static volatile boolean shadowResolved;
    private static Method shadowMethod;

    private DemonicaTerrainBridge() {
    }

    /**
     * {@return whether Iris is currently rendering the shadow stage}, read from Iris's own
     * {@code ShadowRenderingState} - the same state Demonica's {@code ShaderTerrain} consults when it picks
     * the {@code shadow} program over {@code gbuffers_terrain}/{@code gbuffers_water}. The pack-tail-2
     * brackets name a pack terrain execution by this signal, so the attribution follows the guest's own
     * stage bookkeeping rather than a target-shape guess. Absent Iris (a vanilla run), the answer is false
     * and the caller never reaches the bracket anyway: no pack program exists there.
     */
    static boolean shadowsBeingRendered() {
        Method method = resolveShadow();
        if (method == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(method.invoke(null));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Method resolveShadow() {
        if (shadowResolved) {
            return shadowMethod;
        }
        synchronized (DemonicaTerrainBridge.class) {
            if (!shadowResolved) {
                try {
                    Class<?> cls = Class.forName("net.coderbot.iris.shadows.ShadowRenderingState", false,
                            DemonicaTerrainBridge.class.getClassLoader());
                    shadowMethod = cls.getMethod("areShadowsCurrentlyBeingRendered");
                } catch (Throwable ignored) {
                    shadowMethod = null;
                }
                shadowResolved = true;
            }
            return shadowMethod;
        }
    }

    /** Whether Demonica is absent, present without a program for the pass, or supplying one. */
    enum State {
        ABSENT, NO_PROGRAM, OK
    }

    /** The connection's state for one program request, with the program when there is one. */
    static final class Status {
        final State state;
        final String reason;
        final GlProgram<? extends ChunkShaderInterface> program;

        Status(State state, String reason, GlProgram<? extends ChunkShaderInterface> program) {
            this.state = state;
            this.reason = reason;
            this.program = program;
        }
    }

    /**
     * {@return the connection's adjudicated state for one pack-program request}. The backend's
     * go/no-go reads this and only this: ABSENT when Demonica's terrain class cannot be loaded
     * at all, NO_PROGRAM when it is there but supplied nothing for this pass (its bridge method
     * is missing, it answered null, or the call itself failed), OK with the program otherwise.
     * The state is reported through the connection gate on first adjudication and on change, so
     * which of the three a silent fallback was - and why - is in the log.
     */
    @SuppressWarnings("unchecked")
    static Status state(TerrainRenderPass pass, RenderPassConfiguration<?> configuration) {
        Status status;
        if (pass == null || configuration == null) {
            status = new Status(State.NO_PROGRAM,
                    "no pass or no configuration to ask for a program with", null);
        } else {
            Method method = resolve();
            if (method == null) {
                status = new Status(resolveClassMissing ? State.ABSENT : State.NO_PROGRAM,
                        resolveFailure != null ? resolveFailure
                                : "Demonica's ShaderTerrain could not be resolved", null);
            } else {
                try {
                    GlProgram<? extends ChunkShaderInterface> program =
                            (GlProgram<? extends ChunkShaderInterface>) method.invoke(null, pass, configuration);
                    status = program != null
                            ? new Status(State.OK, "Demonica's ShaderTerrain supplied the pack program", program)
                            : new Status(State.NO_PROGRAM,
                                    "Demonica's ShaderTerrain supplied no program for this pass", null);
                } catch (Throwable t) {
                    status = new Status(State.NO_PROGRAM,
                            "Demonica's packProgram invocation failed: " + describe(t), null);
                }
            }
        }
        ConnectionGate.reportConnection("demonica", status.state.name(), status.reason);
        return status;
    }

    static GlProgram<? extends ChunkShaderInterface> packProgram(TerrainRenderPass pass,
                                                                 RenderPassConfiguration<?> configuration) {
        Status status = state(pass, configuration);
        return status.state == State.OK ? status.program : null;
    }

    /** {@return a compact description of a failure, with an invocation's cause unwrapped}. */
    private static String describe(Throwable t) {
        if (t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null) {
            t = t.getCause();
        }
        String message = t.getMessage();
        return t.getClass().getSimpleName() + (message != null ? ": " + message : "");
    }

    private static Method resolve() {
        if (resolved) {
            return packProgramMethod;
        }
        synchronized (DemonicaTerrainBridge.class) {
            if (!resolved) {
                try {
                    Class<?> cls = Class.forName("com.demonica.celeritas.terrain.ShaderTerrain", false,
                            DemonicaTerrainBridge.class.getClassLoader());
                    try {
                        packProgramMethod = cls.getMethod("packProgram",
                                TerrainRenderPass.class, RenderPassConfiguration.class);
                    } catch (Throwable t) {
                        resolveFailure = "Demonica is present but ShaderTerrain.packProgram(pass, configuration)"
                                + " is not available: " + describe(t);
                        packProgramMethod = null;
                    }
                } catch (Throwable t) {
                    resolveFailure = "Demonica's ShaderTerrain class is not loaded: " + describe(t);
                    resolveClassMissing = true;
                    packProgramMethod = null;
                }
                resolved = true;
            }
            return packProgramMethod;
        }
    }
}
