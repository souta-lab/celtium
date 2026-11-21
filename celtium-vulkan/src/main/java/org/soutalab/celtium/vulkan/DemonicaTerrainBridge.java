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

    private DemonicaTerrainBridge() {
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
