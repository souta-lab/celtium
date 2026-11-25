package org.soutalab.celtium.vulkan.graph;

/**
 * Records one pass's commands at execution time. The context is deliberately opaque to the
 * graph: the executor hands through whatever the recording side uses (a command buffer wrapper
 * owned by the HAL), so the graph package never depends on Vulkan types.
 */
public interface PassRecorder {

    void record(Object context);
}
