package org.embeddedt.embeddium.impl.render.chunk.compile;

import org.embeddedt.embeddium.impl.render.chunk.RenderSection;

public abstract class ChunkTaskOutput {
    public final RenderSection render;
    public final int buildTime;

    protected ChunkTaskOutput(RenderSection render, int buildTime) {
        this.render = render;
        this.buildTime = buildTime;
    }

    public void delete() {

    }

    /**
     * {@return a copy of this output that owns its native data, deep enough to survive the caller deleting
     * this output}.
     * <p>
     * A consumer that cannot upload an output synchronously - the Vulkan backend's upload budget, which
     * spreads a burst over the following frames - must not hold the caller's output itself: the caller
     * deletes every output on the same call that handed them over, so a held original is freed memory by
     * the time the consumer reaches it (item 730). Only the native buffers are copied; {@link #delete()}
     * frees nothing else, so the section info, the sort state and the vertex ranges are plain Java heap
     * objects the original and the copy safely share. The base implementation, for an output type with no
     * native data of its own, returns {@code this}.
     */
    public ChunkTaskOutput copyForDeferral() {
        return this;
    }
}
