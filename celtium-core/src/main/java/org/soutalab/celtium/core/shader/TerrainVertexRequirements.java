package org.soutalab.celtium.core.shader;

/**
 * What a pack's terrain programs read from each vertex, beyond the packed quad itself (design
 * section 2.3, gap G-5). When a program set's requirements change, the section store regenerates
 * only the auxiliary columns the new requirements ask for - the 16-byte main slab never changes
 * shape - and a section whose auxiliary column is missing yet is drawn under the store's
 * fully-armed rule, never with garbage.
 */
public record TerrainVertexRequirements(
        boolean mcEntity,
        boolean normal,
        boolean tangent,
        boolean midTexCoord) {

    /** No auxiliary data at all: the pack-OFF terrain shape. */
    public static final TerrainVertexRequirements NONE = new TerrainVertexRequirements(false, false, false, false);

    /** Bytes of auxiliary column each axis quad needs under these requirements (the mc_Entity short). */
    public int auxiliaryBytesPerQuad() {
        return mcEntity ? 2 : 0;
    }

    public boolean needsAuxiliaryColumn() {
        return mcEntity;
    }
}
