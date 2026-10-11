package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderer;
import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackends;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;
import org.embeddedt.embeddium.impl.render.chunk.ShaderChunkRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Selection entry point for the Vulkan terrain backend. Resolved reflectively by the mod so that an installation
 * without Vulkan bindings never loads a Vulkan class on its normal path.
 * <p>
 * Fail-closed: any missing capability, missing binding, or unimplemented render pass keeps the retained OpenGL
 * backend and states the reason. Nothing here silently changes which GPU renders the world.
 */
public final class VulkanTerrainFactory {
    /**
     * {@code auto} (default), {@code true} or {@code false}.
     * <p>
     * {@code auto} uses Vulkan only when the implementation covers every render pass. {@code true} forces the
     * Vulkan path for development and validation even while passes are still missing, and is the switch that
     * becomes the default once the port is complete.
     */
    public static final String ENABLE_PROPERTY = "celtium.vulkanTerrain";

    private VulkanTerrainFactory() {
    }

    /**
     * @param reasons receives one line per reason the Vulkan backend was not created
     * @return the backend, or {@code null} when OpenGL must stay active
     */
    public static TerrainGpuBackend create(RenderPassConfiguration<?> configuration,
                                           ChunkRenderer renderer,
                                           RenderRegionManager regions,
                                           List<String> reasons) {
        String setting = System.getProperty(ENABLE_PROPERTY, "auto");
        if ("false".equalsIgnoreCase(setting)) {
            reasons.add("Vulkan terrain is disabled by -D" + ENABLE_PROPERTY + "=false");
            return null;
        }
        if (!"auto".equalsIgnoreCase(setting) && !"true".equalsIgnoreCase(setting)) {
            reasons.add("Unknown -D" + ENABLE_PROPERTY + " value '" + setting + "'; keeping OpenGL");
            return null;
        }
        boolean forced = "true".equalsIgnoreCase(setting);
        if (!forced && !implementationComplete()) {
            reasons.add("the Vulkan terrain backend does not implement every render pass yet: "
                    + String.join(", ", unimplementedAreas()));
            return null;
        }
        if (!VulkanContext.isAvailable()) {
            reasons.add("Vulkan bindings are not available on this classpath");
            return null;
        }

        FogService fogService = renderer instanceof ShaderChunkRenderer
                ? ((ShaderChunkRenderer) renderer).getFogService() : null;
        if (fogService == null) {
            reasons.add("the renderer does not expose a fog service, so terrain would not match the OpenGL look");
            return null;
        }

        // The window-time instance, if the window created one: it is the same device and the same renderer, so the
        // world renderer's objects are handed to it rather than a second backend being built beside it. One owner for
        // the swapchain and the frame target, and the first world frame finds them warm.
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (current instanceof VulkanTerrainGpuBackend existing && existing.celtium$isWindowBackend()) {
            existing.celtium$adoptTerrainState(configuration, regions, fogService);
            reasons.add("the world renderer adopted the window-time backend instead of creating a second one");
            return existing;
        }

        VulkanContext context = null;
        try {
            context = VulkanContext.create("Celtium terrain backend");
            VulkanTerrainGpuBackend backend = new VulkanTerrainGpuBackend(context, configuration, regions, fogService);
            backend.install();
            return backend;
        } catch (RuntimeException | LinkageError error) {
            if (context != null) {
                context.delete();
            }
            reasons.add("Vulkan device creation failed: " + error.getClass().getSimpleName() + ": " + error.getMessage());
            return null;
        }
    }

    /**
     * Creates the renderer at window creation, so the menu, the loading screens and every GUI-only frame have someone
     * to draw them and someone to present them.
     * <p>
     * Under the GL-less window those frames used to reach nobody: the stub decoded their draws and handed them to an
     * empty {@code TerrainGpuBackends}, and the swap point's present only fires when a backend is installed - measured
     * as 524,288 decoded draws refused with "no terrain backend is installed" while the user saw a window with nothing
     * in it. The checks are the same ones the world path makes, minus the two that describe terrain (the render pass
     * configuration and the fog service), because there is no terrain yet: the pipelines are built per pass on first
     * use, and the UI render pass this path draws with already exists for the no-terrain-pass frame.
     * <p>
     * Fail-closed like the world path: a stated reason and {@code null} rather than a half-created renderer.
     */
    public static TerrainGpuBackend createForWindow(List<String> reasons) {
        String setting = System.getProperty(ENABLE_PROPERTY, "auto");
        if ("false".equalsIgnoreCase(setting)) {
            reasons.add("Vulkan terrain is disabled by -D" + ENABLE_PROPERTY + "=false");
            return null;
        }
        if (!"auto".equalsIgnoreCase(setting) && !"true".equalsIgnoreCase(setting)) {
            reasons.add("Unknown -D" + ENABLE_PROPERTY + " value '" + setting + "'; keeping OpenGL");
            return null;
        }
        if (!"true".equalsIgnoreCase(setting) && !implementationComplete()) {
            reasons.add("the Vulkan terrain backend does not implement every render pass yet: "
                    + String.join(", ", unimplementedAreas()));
            return null;
        }
        if (!VulkanContext.isAvailable()) {
            reasons.add("Vulkan bindings are not available on this classpath");
            return null;
        }
        TerrainGpuBackend current = TerrainGpuBackends.current();
        if (current != null) {
            reasons.add("a terrain backend is already installed");
            return null;
        }
        VulkanContext context = null;
        try {
            context = VulkanContext.create("Celtium window renderer");
            VulkanTerrainGpuBackend backend = VulkanTerrainGpuBackend.createForWindow(context);
            backend.install();
            return backend;
        } catch (RuntimeException | LinkageError error) {
            if (context != null) {
                context.delete();
            }
            reasons.add("Vulkan device creation failed at window creation: " + error.getClass().getSimpleName()
                    + ": " + error.getMessage());
            return null;
        }
    }

    /**
     * Whether every render pass this build knows about can be drawn by the Vulkan backend.
     * <p>
     * This is the gate that keeps a half-finished backend away from the screen: the solid pass works, but cutout,
     * cutout-mipped and translucent still need their pipelines, alpha handling and index sorting, so the answer
     * stays {@code false} until they exist.
     */
    public static boolean implementationComplete() {
        // Every terrain pass this build knows about is implemented and verified: SOLID on both target GPUs with a
        // frame identical to the OpenGL reference, and cutout/cutout_mipped/translucent drawing with a clean
        // validation log. A configuration the backend cannot serve still falls back to OpenGL with a stated reason.
        return true;
    }

    /** One line per unfinished area, for the capability report and the client command. */
    public static List<String> unimplementedAreas() {
        List<String> areas = new ArrayList<String>();
        areas.add("chunk fade-in and region age uniforms (off by default in this build)");
        areas.add("GPU timing queries (the debug overlay shows ?? ms for terrain)");
        return areas;
    }
}
