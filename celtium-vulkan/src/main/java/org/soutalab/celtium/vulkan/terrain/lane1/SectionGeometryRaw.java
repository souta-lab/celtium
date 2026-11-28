package org.soutalab.celtium.vulkan.terrain.lane1;

import java.util.Objects;

/**
 * One built section's geometry in the mesher's own storage shape (S2-b-perf CPU-4): the
 * per-layer backing arrays of the forge driver's {@code MeshBuffers}, carried by reference
 * so the store can bulk-copy them into the slab without materialising a
 * {@code PackedAxisQuad} / {@code GeneralQuad} object - and two list copies - per quad on
 * the render thread.
 * <p>
 * Layout per layer (SOLID, CUTOUT, TRANSLUCENT): axis quads are {@code axisCounts[layer] * 4}
 * packed words in {@code axisWords[layer]} (the {@code PackedAxisQuad} word order); general
 * quads are {@code generalCounts[layer] * 12} position floats in
 * {@code generalPositions[layer]} plus {@code generalCounts[layer] * 8} meta words in
 * {@code generalMeta[layer]} ([textureIndex, lightWord, normalPacked, flags, uvPacked x4] -
 * exactly the 32 meta bytes of the 80 B slab layout, after the 48 position bytes). When
 * {@code auxEnabled} is set, {@code axisAux[layer]} holds one {@code mc_Entity} id per axis
 * quad of that layer. {@code cornerLight} is the per-corner light column in the store's
 * layout (every axis quad's two words in layer order, then every general quad's - the
 * shape {@code SectionGeometry.cornerLight()} carries on the object path), or an empty
 * array when the mesh produced none.
 * <p>
 * <b>Borrowed arrays:</b> the components are the producer's live buffers. They are valid
 * only for the duration of the {@code uploadRaw} call and must not be retained; the store
 * copies what it needs (the slab bytes, and the retained auxiliary column) synchronously.
 */
public record SectionGeometryRaw(
        int[][] axisWords,
        int[] axisCounts,
        short[][] axisAux,
        boolean auxEnabled,
        float[][] generalPositions,
        int[][] generalMeta,
        int[] generalCounts,
        int[] cornerLight) {

    public SectionGeometryRaw {
        Objects.requireNonNull(axisWords, "axisWords");
        Objects.requireNonNull(axisCounts, "axisCounts");
        Objects.requireNonNull(axisAux, "axisAux");
        Objects.requireNonNull(generalPositions, "generalPositions");
        Objects.requireNonNull(generalMeta, "generalMeta");
        Objects.requireNonNull(generalCounts, "generalCounts");
        cornerLight = cornerLight == null ? new int[0] : cornerLight;
        int quadTotal = axisCounts[0] + axisCounts[1] + axisCounts[2]
                + generalCounts[0] + generalCounts[1] + generalCounts[2];
        if (cornerLight.length != 0 && cornerLight.length != quadTotal * 2) {
            throw new IllegalArgumentException("raw cornerLight must hold two words per quad: "
                    + cornerLight.length + " words for " + quadTotal + " quads");
        }
        if (axisWords.length != TerrainSectionStore.LAYER_COUNT
                || axisCounts.length != TerrainSectionStore.LAYER_COUNT
                || axisAux.length != TerrainSectionStore.LAYER_COUNT
                || generalPositions.length != TerrainSectionStore.LAYER_COUNT
                || generalMeta.length != TerrainSectionStore.LAYER_COUNT
                || generalCounts.length != TerrainSectionStore.LAYER_COUNT) {
            throw new IllegalArgumentException("raw geometry is per-layer ("
                    + TerrainSectionStore.LAYER_COUNT + " layers)");
        }
        for (int layer = 0; layer < TerrainSectionStore.LAYER_COUNT; layer++) {
            if (axisCounts[layer] < 0 || generalCounts[layer] < 0
                    || axisWords[layer].length < axisCounts[layer] * 4
                    || generalPositions[layer].length < generalCounts[layer] * 12
                    || generalMeta[layer].length < generalCounts[layer] * 8
                    || (auxEnabled && axisAux[layer].length < axisCounts[layer])) {
                throw new IllegalArgumentException("raw geometry layer " + layer
                        + " arrays are shorter than their counts");
            }
        }
    }

    public int axisQuadCount() {
        return axisCounts[0] + axisCounts[1] + axisCounts[2];
    }

    public int generalQuadCount() {
        return generalCounts[0] + generalCounts[1] + generalCounts[2];
    }

    /**
     * The auxiliary column in the store's shape: the per-layer ids concatenated in layer
     * order, or an empty array when the mesh ran without one. The one allocation the raw
     * path still makes per upload, matching what the list path's {@code auxColumn()} did.
     */
    public short[] concatenatedAux() {
        if (!auxEnabled) {
            return new short[0];
        }
        short[] out = new short[axisQuadCount()];
        int cursor = 0;
        for (int layer = 0; layer < TerrainSectionStore.LAYER_COUNT; layer++) {
            System.arraycopy(axisAux[layer], 0, out, cursor, axisCounts[layer]);
            cursor += axisCounts[layer];
        }
        return out;
    }

    @Override
    public String toString() {
        return "SectionGeometryRaw[axis=" + axisQuadCount() + ", general=" + generalQuadCount()
                + ", aux=" + (auxEnabled ? axisQuadCount() : 0) + "]";
    }

    // Records would compare the array components by reference; equality is not part of this
    // carrier's contract (it is consumed once, synchronously), so keep identity semantics
    // explicit instead of pretending a deep comparison exists.
    @Override
    public boolean equals(Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
