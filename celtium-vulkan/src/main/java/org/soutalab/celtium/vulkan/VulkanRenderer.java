package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.RenderPassConfiguration;
import org.embeddedt.embeddium.impl.render.chunk.backend.TerrainGpuBackend;
import org.embeddedt.embeddium.impl.render.chunk.fog.FogService;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegionManager;

/**
 * The root the renderer is reached through (refactoring plan R7).
 * <p>
 * The plan's R7 asks for a root and a frame coordinator, with window lifetime and world lifetime separated and the
 * concrete-backend casts removed. This is the first step of that: everything the game and the terrain factory need is
 * declared here, so the factory can ask a {@code VulkanRenderer} instead of testing for
 * {@link VulkanTerrainGpuBackend} and reaching into the class.
 * <p>
 * It extends {@link TerrainGpuBackend} because that is the contract the game already holds; what this adds is the
 * window-time surface - whether this instance is the one the window created, and the adoption that hands a world's
 * regions to it - plus the lifecycle the factory calls. The lifetime split itself (device and swapchain on one side,
 * world resources on the other) comes next, and the interface is where that shows up as method groups rather than as
 * comments.
 */
public interface VulkanRenderer extends TerrainGpuBackend {

    /**
     * {@return whether this instance is the one the window created}.
     * <p>
     * The window's instance owns the swapchain and the frame target; a world renderer either adopts it or builds its
     * own device. Asking this instead of testing the concrete type is what makes the factory's decision a contract
     * rather than a class check.
     */
    boolean celtium$isWindowBackend();

    /**
     * Hands a world's regions and configuration to the window-time instance, so the first world frame finds the device,
     * the swapchain and the frame target warm instead of a second backend being built beside them.
     */
    void celtium$adoptTerrainState(RenderPassConfiguration<?> configuration, RenderRegionManager regionManager,
            FogService fogService);

    /** Publishes this renderer as the current backend. */
    void install();

    /** Releases the device and everything built on it. */
    void delete();
}
