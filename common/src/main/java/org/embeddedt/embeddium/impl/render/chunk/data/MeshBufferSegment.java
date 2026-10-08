package org.embeddedt.embeddium.impl.render.chunk.data;

/**
 * A range of a mesh buffer that section render data can point at, independent of the graphics API that owns the
 * buffer. The OpenGL arena and the Vulkan arena both hand these out, so the per-section metadata, the batch
 * assembly and the cached draw lists are shared instead of duplicated per backend.
 */
public interface MeshBufferSegment {
    /**
     * Offset of this range inside its buffer, in units of the owning arena's element stride. The OpenGL arena
     * reports offsets this way and the per-section metadata multiplies them by the vertex count per arena element,
     * so a backend whose segments are byte addressed must divide by its stride before answering.
     */
    int getOffset();

    /** Length of this range in the same units as {@link #getOffset()}. */
    int getLength();

    /** Releases this range back to its owner. Must be safe to call once per segment. */
    void delete();
}
