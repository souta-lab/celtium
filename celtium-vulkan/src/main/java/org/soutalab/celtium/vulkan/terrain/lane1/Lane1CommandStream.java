package org.soutalab.celtium.vulkan.terrain.lane1;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.frame.VulkanCommandStream;
import org.soutalab.celtium.core.shader.TerrainStageProgram;
import org.soutalab.celtium.vulkan.VulkanContext;
import org.soutalab.celtium.vulkan.hal.VulkanDynamicRendering;

import java.util.function.Consumer;

/**
 * The {@link VulkanCommandStream} the frame executor hands the terrain lane (design section 2.3):
 * dynamic rendering brackets from the HAL, and pipeline binding delegated to the executor's
 * pipeline cache (keyed by the {@link TerrainStageProgram} itself - the HAL builds pipelines from
 * programs and decides nothing else). Clear values are the executor's, passed at construction.
 */
public final class Lane1CommandStream implements VulkanCommandStream {

    private final VkCommandBuffer commandBuffer;
    private final Consumer<TerrainStageProgram> pipelineBinder;
    private final float[][] clearColors;
    private final float clearDepth;
    private final int clearStencil;
    private final long frameId;
    private final int frameSlot;

    public Lane1CommandStream(VulkanContext context, long nativeCommandBuffer,
                              Consumer<TerrainStageProgram> pipelineBinder,
                              float[][] clearColors, float clearDepth, int clearStencil,
                              long frameId, int frameSlot) {
        this.commandBuffer = new VkCommandBuffer(nativeCommandBuffer, context.device());
        this.pipelineBinder = pipelineBinder;
        this.clearColors = clearColors;
        this.clearDepth = clearDepth;
        this.clearStencil = clearStencil;
        this.frameId = frameId;
        this.frameSlot = frameSlot;
    }

    @Override
    public long nativeCommandBuffer() {
        return commandBuffer.address();
    }

    /** The LWJGL command buffer view, for the draw sink's indirect calls. */
    public VkCommandBuffer commandBuffer() {
        return commandBuffer;
    }

    @Override
    public void beginRendering(ActiveRenderTargets targets) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanDynamicRendering.beginRendering(commandBuffer, stack, targets,
                    clearColors, clearDepth, clearStencil);
        }
    }

    @Override
    public void endRendering() {
        VulkanDynamicRendering.endRendering(commandBuffer);
    }

    @Override
    public void bindTerrainProgram(TerrainStageProgram program) {
        pipelineBinder.accept(program);
    }

    /** The seat's frame slot, presented by the frame owner through the executor (raw value). */
    @Override
    public int frameSlot() {
        return frameSlot;
    }

    /** The seat's frame id, presented by the frame owner through the executor. */
    @Override
    public long frameId() {
        return frameId;
    }
}
