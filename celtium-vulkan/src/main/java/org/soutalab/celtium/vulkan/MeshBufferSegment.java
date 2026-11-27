package org.soutalab.celtium.vulkan;

/**
 * A range of a mesh buffer that section render data can point at, independent of the graphics
 * API that owns the buffer (S2-b relocation: celtium's own copy of the contract the Vulkan
 * arena's segments implement).
 */
public interface MeshBufferSegment {

    /**
     * Offset of this range inside its buffer, in units of the owning arena's element stride.
     * The per-section metadata multiplies offsets by the vertex count per arena element, so a
     * backend whose segments are byte addressed must divide by its stride before answering.
     */
    int getOffset();

    /** Length of this range in the same units as {@link #getOffset()}. */
    int getLength();

    /** Releases this range back to its owner. Must be safe to call once per segment. */
    void delete();
}
