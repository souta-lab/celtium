/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.function.Consumer;

/**
 * Records a one-shot command buffer and blocks until the GPU finished it. The pack executor
 * uses this only at activation time (texture uploads), never per frame; the frame hook's owner
 * supplies the implementation from the context's queue.
 */
@FunctionalInterface
public interface PackSubmitter {

    void submitAndWait(String label, Consumer<VkCommandBuffer> recorder);
}
