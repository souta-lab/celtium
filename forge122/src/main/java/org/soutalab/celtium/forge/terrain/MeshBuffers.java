package org.soutalab.celtium.forge.terrain;

import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;

import java.util.Arrays;

/**
 * The reusable output of one section mesh: axis quads and general quads, each grouped by layer
 * (SOLID, CUTOUT, TRANSLUCENT in that order) so the section store can lay a section's quads down
 * as one contiguous, layer-ordered range per slab. Buffers grow to the largest section seen and
 * are then reused for every later build - a mesher worker owns one instance and allocates
 * nothing per section once warm.
 * <p>
 * Axis quads are stored as their four packed words. When the mesh ran with an auxiliary column
 * (a pack asked for {@code mc_Entity}), a parallel {@code short} per axis quad is stored in the
 * matching {@code aux} arrays; otherwise they stay empty. General quads are stored raw (12
 * position floats + texture/light/normal/flags) and materialise as {@link GeneralQuad} only on
 * demand - the store writes them into slab segments straight from these arrays.
 */
public final class MeshBuffers {
    private final int[][] axisWords = new int[TerrainLayer.COUNT][];
    private final int[] axisCounts = new int[TerrainLayer.COUNT];
    private final short[][] axisAux = new short[TerrainLayer.COUNT][];
    private final float[][] generalPositions = new float[TerrainLayer.COUNT][];
    private final int[][] generalMeta = new int[TerrainLayer.COUNT][];
    private final int[] generalCounts = new int[TerrainLayer.COUNT];
    private boolean auxEnabled;

    public MeshBuffers() {
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            axisWords[layer] = new int[256 * 4];
            axisAux[layer] = new short[256];
            generalPositions[layer] = new float[256 * 12];
            generalMeta[layer] = new int[256 * 4];
        }
    }

    /** Clears all counts for a new build. {@code aux} decides whether aux values are recorded. */
    public void reset(boolean aux) {
        Arrays.fill(axisCounts, 0);
        Arrays.fill(generalCounts, 0);
        this.auxEnabled = aux;
    }

    public boolean auxEnabled() {
        return auxEnabled;
    }

    public void addAxisQuad(int layer, PackedAxisQuad quad, short aux) {
        int count = axisCounts[layer];
        int needed = (count + 1) * 4;
        if (needed > axisWords[layer].length) {
            axisWords[layer] = Arrays.copyOf(axisWords[layer], axisWords[layer].length * 2);
            axisAux[layer] = Arrays.copyOf(axisAux[layer], axisAux[layer].length * 2);
        }
        int base = count * 4;
        axisWords[layer][base] = quad.word0();
        axisWords[layer][base + 1] = quad.word1();
        axisWords[layer][base + 2] = quad.word2();
        axisWords[layer][base + 3] = quad.word3();
        axisAux[layer][count] = aux;
        axisCounts[layer] = count + 1;
    }

    public void addGeneralQuad(int layer, float[] positions, int textureIndex, int lightWord,
                               int normalPacked, int flags) {
        int count = generalCounts[layer];
        if ((count + 1) * 12 > generalPositions[layer].length) {
            generalPositions[layer] = Arrays.copyOf(generalPositions[layer], generalPositions[layer].length * 2);
            generalMeta[layer] = Arrays.copyOf(generalMeta[layer], generalMeta[layer].length * 2);
        }
        System.arraycopy(positions, 0, generalPositions[layer], count * 12, 12);
        int meta = count * 4;
        generalMeta[layer][meta] = textureIndex;
        generalMeta[layer][meta + 1] = lightWord;
        generalMeta[layer][meta + 2] = normalPacked;
        generalMeta[layer][meta + 3] = flags;
        generalCounts[layer] = count + 1;
    }

    public int axisCount(int layer) {
        return axisCounts[layer];
    }

    public int axisTotal() {
        return axisCounts[0] + axisCounts[1] + axisCounts[2];
    }

    public int generalCount(int layer) {
        return generalCounts[layer];
    }

    public int generalTotal() {
        return generalCounts[0] + generalCounts[1] + generalCounts[2];
    }

    public PackedAxisQuad axisQuad(int layer, int i) {
        int base = i * 4;
        int[] words = axisWords[layer];
        return new PackedAxisQuad(words[base], words[base + 1], words[base + 2], words[base + 3]);
    }

    public short axisAux(int layer, int i) {
        return axisAux[layer][i];
    }

    public GeneralQuad generalQuad(int layer, int i) {
        float[] positions = new float[12];
        System.arraycopy(generalPositions[layer], i * 12, positions, 0, 12);
        int meta = i * 4;
        int[] m = generalMeta[layer];
        return new GeneralQuad(positions, m[meta], m[meta + 1], m[meta + 2], m[meta + 3]);
    }

    /** One layer's axis quads as a list, in emission order - the store's input shape. */
    public java.util.List<PackedAxisQuad> axisLayerList(int layer) {
        java.util.List<PackedAxisQuad> out = new java.util.ArrayList<>(axisCounts[layer]);
        for (int i = 0; i < axisCounts[layer]; i++) {
            out.add(axisQuad(layer, i));
        }
        return out;
    }

    /** One layer's general quads as a list, in emission order. */
    public java.util.List<GeneralQuad> generalLayerList(int layer) {
        java.util.List<GeneralQuad> out = new java.util.ArrayList<>(generalCounts[layer]);
        for (int i = 0; i < generalCounts[layer]; i++) {
            out.add(generalQuad(layer, i));
        }
        return out;
    }

    /**
     * The auxiliary column for the whole section: the per-quad {@code mc_Entity} ids concatenated
     * in layer order (SOLID, CUTOUT, TRANSLUCENT), matching the store's axis range layout. Empty
     * when the mesh ran without an auxiliary column.
     */
    public short[] auxColumn() {
        if (!auxEnabled) {
            return new short[0];
        }
        short[] out = new short[axisTotal()];
        int cursor = 0;
        for (int layer = 0; layer < TerrainLayer.COUNT; layer++) {
            System.arraycopy(axisAux[layer], 0, out, cursor, axisCounts[layer]);
            cursor += axisCounts[layer];
        }
        return out;
    }
}
