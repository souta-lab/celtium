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
 * Failure visibility (drawpath audit DP-06): the boolean answers stay - the callers need an
 * answer either way - but a failure is no longer collapsed into the same silent {@code false} /
 * {@code null} as a genuine negative. A guest class that is simply absent
 * ({@link ClassNotFoundException}) is a vanilla run and is not a failure. Anything else - the
 * class is present but its method is gone or changed shape, a linkage error, or an invocation
 * that throws - is a bridge failure: it is counted, the first one is printed, and the latest is
 * retained in {@link #failureSummary()} for {@code CELTIUM_VERIFY}, so "the shadow stage is not
 * being rendered" can be told apart from "the bridge that would know is broken".
 */
final class DemonicaTerrainBridge {
    private static volatile boolean resolved;
    private static Method packProgramMethod;
    private static volatile boolean shadowResolved;
    private static Method shadowMethod;

    // The guest class names, held in fields so a test can point the bridge at a stub guest
    // (see celtium$resetForTests). Production code never rewrites them.
    private static String shadowStateClassName = "net.coderbot.iris.shadows.ShadowRenderingState";
    private static String shaderTerrainClassName = "com.demonica.celeritas.terrain.ShaderTerrain";

    private static long resolveFailures;
    private static long invokeFailures;
    private static String lastFailure;
    private static boolean failurePrinted;

    private DemonicaTerrainBridge() {
    }

    /** Bridge resolution failures: the guest class exists but could not be bound. */
    static synchronized long resolveFailures() {
        return resolveFailures;
    }

    /** Bridge invocation failures: a bound guest method threw when called. */
    static synchronized long invokeFailures() {
        return invokeFailures;
    }

    /** The failure counters and the latest failure's seat, for {@code CELTIUM_VERIFY}. */
    static synchronized String failureSummary() {
        return "resolveFailures=" + resolveFailures + " invokeFailures=" + invokeFailures
                + " lastFailure=" + (lastFailure == null ? "none" : lastFailure.replace(' ', '_'));
    }

    private static synchronized void noteResolveFailure(String seat, Throwable error) {
        if (error instanceof ClassNotFoundException) {
            // The guest is not installed: a vanilla run, not a bridge failure.
            return;
        }
        resolveFailures++;
        noteFailureLocked(seat, error);
    }

    private static synchronized void noteInvokeFailure(String seat, Throwable error) {
        invokeFailures++;
        noteFailureLocked(seat, error);
    }

    private static void noteFailureLocked(String seat, Throwable error) {
        Throwable cause = error instanceof java.lang.reflect.InvocationTargetException
                && error.getCause() != null ? error.getCause() : error;
        lastFailure = seat + ": " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
        if (!failurePrinted) {
            failurePrinted = true;
            System.out.println("[Celtium] CELTIUM_DEMONICA_BRIDGE_FAILED seat=" + seat
                    + " reason=" + lastFailure);
        }
    }

    /** Test seam: restores the production guest names and clears the latches and counters. */
    static synchronized void celtium$resetForTests(String shadowClassName, String terrainClassName) {
        shadowStateClassName = shadowClassName;
        shaderTerrainClassName = terrainClassName;
        resolved = false;
        packProgramMethod = null;
        shadowResolved = false;
        shadowMethod = null;
        resolveFailures = 0L;
        invokeFailures = 0L;
        lastFailure = null;
        failurePrinted = false;
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
        } catch (Throwable error) {
            noteInvokeFailure("shadow-state", error);
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
                    Class<?> cls = Class.forName(shadowStateClassName, false,
                            DemonicaTerrainBridge.class.getClassLoader());
                    shadowMethod = cls.getMethod("areShadowsCurrentlyBeingRendered");
                } catch (Throwable error) {
                    noteResolveFailure("shadow-state", error);
                    shadowMethod = null;
                }
                shadowResolved = true;
            }
            return shadowMethod;
        }
    }

    @SuppressWarnings("unchecked")
    static GlProgram<? extends ChunkShaderInterface> packProgram(TerrainRenderPass pass,
                                                                 RenderPassConfiguration<?> configuration) {
        if (pass == null || configuration == null) {
            return null;
        }
        Method method = resolve();
        if (method == null) {
            return null;
        }
        try {
            return (GlProgram<? extends ChunkShaderInterface>) method.invoke(null, pass, configuration);
        } catch (Throwable error) {
            noteInvokeFailure("pack-program", error);
            return null;
        }
    }

    private static Method resolve() {
        if (resolved) {
            return packProgramMethod;
        }
        synchronized (DemonicaTerrainBridge.class) {
            if (!resolved) {
                try {
                    Class<?> cls = Class.forName(shaderTerrainClassName, false,
                            DemonicaTerrainBridge.class.getClassLoader());
                    packProgramMethod = cls.getMethod("packProgram",
                            TerrainRenderPass.class, RenderPassConfiguration.class);
                } catch (Throwable error) {
                    noteResolveFailure("pack-program", error);
                    packProgramMethod = null;
                }
                resolved = true;
            }
            return packProgramMethod;
        }
    }
}
