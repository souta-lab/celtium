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
 */
final class DemonicaTerrainBridge {
    private static volatile boolean resolved;
    private static Method packProgramMethod;
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
        } catch (Throwable ignored) {
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
                    Class<?> cls = Class.forName("com.demonica.celeritas.terrain.ShaderTerrain", false,
                            DemonicaTerrainBridge.class.getClassLoader());
                    packProgramMethod = cls.getMethod("packProgram",
                            TerrainRenderPass.class, RenderPassConfiguration.class);
                } catch (Throwable ignored) {
                    packProgramMethod = null;
                }
                resolved = true;
            }
            return packProgramMethod;
        }
    }
}
