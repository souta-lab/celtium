package org.soutalab.celtium.core.frame;

import org.soutalab.celtium.core.shader.TerrainStageProgram;

/**
 * The recording surface a {@code PackTerrainDriver} is handed for one pass: celtium-vulkan's own
 * command recording, narrowed to what the terrain contract needs. The native handle is the
 * {@code VkCommandBuffer} address, exposed because the HAL's other stages share the same buffer
 * within a frame; interface implementations are not required to be thread-safe - recording is a
 * render-thread activity, one stream per pass.
 * <p>
 * Rendering here is Vulkan 1.3 {@code dynamic_rendering} only: there is no render-pass or
 * framebuffer object anywhere in the contract, by design.
 */
public interface VulkanCommandStream {

    /** The {@code VkCommandBuffer} handle this stream records into. */
    long nativeCommandBuffer();

    /** Begins rendering against the given targets ({@code vkCmdBeginRendering}). */
    void beginRendering(ActiveRenderTargets targets);

    /** Ends the rendering begun by {@link #beginRendering} ({@code vkCmdEndRendering}). */
    void endRendering();

    /** Binds the pipeline the HAL built from a terrain stage program (cached per program). */
    void bindTerrainProgram(TerrainStageProgram program);

    /**
     * The frame slot of the frame this stream is recording. The raw value is in the frame
     * owner's (the submission scheduler's) slot space; consumers floorMod it by their own
     * slot count. It is a different space from any executor-local slot (such as the one
     * PackFrameExecutor derives from the frame id and its own frames-in-flight count) and
     * the two are not interchangeable.
     */
    int frameSlot();

    /**
     * The frame id of the frame this stream is recording (the frame owner's counter; the
     * same id handed to {@code PackTerrainDriver.prepareMainView}).
     */
    long frameId();
}
