package org.embeddedt.embeddium.impl.render.chunk.backend;

import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Loads an alternative terrain backend without linking against it.
 * <p>
 * The Vulkan backend needs LWJGL's Vulkan bindings, which an ordinary Cleanroom installation may not provide. The
 * mod core must therefore keep working when that module cannot be loaded at all, which is why the implementation
 * is resolved by name here and every failure turns into a stated reason instead of an error.
 */
public final class TerrainBackendLoader {
    private static final String VULKAN_FACTORY = "org.soutalab.celtium.vulkan.VulkanTerrainFactory";

    private TerrainBackendLoader() {
    }

    /**
     * @param reasons receives one line per reason Vulkan was not selected
     * @return the backend, or {@code null} when the retained OpenGL backend must stay active
     */
    public static TerrainGpuBackend createVulkan(RenderPassConfiguration<?> configuration,
                                                 ChunkRenderer renderer,
                                                 RenderRegionManager regions,
                                                 List<String> reasons) {
        try {
            Class<?> factory = Class.forName(VULKAN_FACTORY);
            Method create = factory.getMethod("create", RenderPassConfiguration.class, ChunkRenderer.class,
                    RenderRegionManager.class, List.class);
            Object backend = create.invoke(null, configuration, renderer, regions, reasons);
            if (backend == null) {
                return null;
            }
            if (!(backend instanceof TerrainGpuBackend)) {
                reasons.add("the Vulkan backend does not implement TerrainGpuBackend");
                return null;
            }
            return (TerrainGpuBackend) backend;
        } catch (ClassNotFoundException | NoSuchMethodException absent) {
            reasons.add("the Vulkan terrain backend is not part of this build");
            return null;
        } catch (InvocationTargetException error) {
            Throwable cause = error.getTargetException();
            if (cause instanceof VirtualMachineError) {
                throw (VirtualMachineError) cause;
            }
            if (cause instanceof ThreadDeath) {
                throw (ThreadDeath) cause;
            }
            reasons.add("the Vulkan terrain backend failed to initialize: " + cause.getClass().getSimpleName()
                    + ": " + cause.getMessage());
            return null;
        } catch (IllegalAccessException | RuntimeException | LinkageError error) {
            reasons.add("the Vulkan terrain backend is unavailable: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            return null;
        }
    }
}
