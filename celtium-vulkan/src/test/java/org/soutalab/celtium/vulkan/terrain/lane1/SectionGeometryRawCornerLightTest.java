package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-107 on the raw upload path (ruling #15648, condition 1): the per-corner light column
 * must be pinned through {@link SectionGeometryRaw} and {@code uploadRaw}, not only
 * through the object path ({@code TerrainSectionStoreTest#cornerLightColumnIsUploadedAndAccounted}).
 * The raw carrier has its own length rule and the store's {@code doUpload} receives the
 * column as a plain argument from {@code uploadRaw} - both seats are exercised here with
 * a distinct word per quad, so a dropped, truncated or reordered column cannot pass.
 */
class SectionGeometryRawCornerLightTest {

    private TerrainSectionStore newStore() {
        return new TerrainSectionStore(new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 16));
    }

    /** A raw carrier with exactly-sized backing arrays for the given per-layer counts. */
    private static SectionGeometryRaw rawWithCounts(int[] axisCounts, int[] generalCounts,
                                                    int[] cornerLight) {
        int layers = TerrainSectionStore.LAYER_COUNT;
        int[][] axisWords = new int[layers][];
        short[][] axisAux = new short[layers][];
        float[][] generalPositions = new float[layers][];
        int[][] generalMeta = new int[layers][];
        for (int layer = 0; layer < layers; layer++) {
            axisWords[layer] = new int[axisCounts[layer] * 4];
            axisAux[layer] = new short[axisCounts[layer]];
            generalPositions[layer] = new float[generalCounts[layer] * 12];
            generalMeta[layer] = new int[generalCounts[layer] * 8];
        }
        return new SectionGeometryRaw(axisWords, axisCounts, axisAux, false,
                generalPositions, generalMeta, generalCounts, cornerLight);
    }

    @Test
    void rawCornerLightMustHoldTwoWordsPerQuadAcrossBothKinds() {
        // 3 axis + 2 general = 5 quads, spread over the layers: the rule counts both
        // kinds together, exactly like SectionGeometry's.
        int[] axisCounts = {2, 1, 0};
        int[] generalCounts = {1, 0, 1};
        rawWithCounts(axisCounts, generalCounts, new int[10]);
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(axisCounts, generalCounts, new int[9]),
                "one word short of two per quad");
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(axisCounts, generalCounts, new int[11]),
                "one word over two per quad");
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(axisCounts, generalCounts, new int[6]),
                "sized for the axis quads only - the general quads count too");
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(axisCounts, generalCounts, new int[4]),
                "sized for the general quads only - the axis quads count too");
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(axisCounts, generalCounts, new int[2]),
                "a single quad's worth for five quads");
    }

    @Test
    void rawCornerLightAcceptsAnEmptyOrNullColumn() {
        int[] axisCounts = {1, 0, 0};
        int[] generalCounts = {0, 0, 0};
        assertEquals(0, rawWithCounts(axisCounts, generalCounts, new int[0]).cornerLight().length,
                "an empty column is the mesher-produced-none shape and stays legal");
        assertEquals(0, rawWithCounts(axisCounts, generalCounts, null).cornerLight().length,
                "null normalises to the empty column");
        assertThrows(IllegalArgumentException.class,
                () -> rawWithCounts(new int[3], new int[3], new int[2]),
                "with no quads at all, only the empty column is legal");
    }

    private static PackedAxisQuad axisQuad(int seed) {
        return PackedAxisQuad.of(seed % 18, (seed * 3) % 18, (seed * 7) % 18, seed % 3,
                seed % 2 == 0, 1 + seed % 8, 1 + (seed * 5) % 8, 100 + seed,
                seed % 256, (seed * 11) % 256, seed % 16, (seed * 3) % 16, (seed * 37) % 256,
                (seed * 13) % 256, (seed * 17) % 256, (seed * 19) % 256, seed % 2);
    }

    private static GeneralQuad generalQuad(int seed) {
        float[] positions = new float[12];
        for (int i = 0; i < 12; i++) {
            positions[i] = seed * 0.5f + i * 0.25f;
        }
        int[] uv = {GeneralQuad.packUv(0.0f, 0.0f), GeneralQuad.packUv(0.5f, 0.25f),
                GeneralQuad.packUv(1.0f, 1.0f), GeneralQuad.packUv(0.75f, 0.5f)};
        return new GeneralQuad(positions, 7 + seed, 0x1234 + seed,
                GeneralQuad.packNormal(0.0f, 1.0f, 0.0f), seed % 4, uv);
    }

    /** The same logical geometry as a raw carrier, in the mesher's storage shape. */
    private static SectionGeometryRaw rawOf(SectionGeometry geometry) {
        int layers = TerrainSectionStore.LAYER_COUNT;
        int[][] axisWords = new int[layers][];
        int[] axisCounts = new int[layers];
        short[][] axisAux = new short[layers][];
        float[][] generalPositions = new float[layers][];
        int[][] generalMeta = new int[layers][];
        int[] generalCounts = new int[layers];
        for (int layer = 0; layer < layers; layer++) {
            List<PackedAxisQuad> quads = geometry.axisLayer(layer);
            axisCounts[layer] = quads.size();
            int[] words = new int[quads.size() * 4];
            for (int i = 0; i < quads.size(); i++) {
                PackedAxisQuad quad = quads.get(i);
                words[i * 4] = quad.word0();
                words[i * 4 + 1] = quad.word1();
                words[i * 4 + 2] = quad.word2();
                words[i * 4 + 3] = quad.word3();
            }
            axisWords[layer] = words;
            axisAux[layer] = new short[quads.size()];
            List<GeneralQuad> quadsGeneral = geometry.generalLayer(layer);
            generalCounts[layer] = quadsGeneral.size();
            float[] positions = new float[quadsGeneral.size() * 12];
            int[] meta = new int[quadsGeneral.size() * 8];
            for (int i = 0; i < quadsGeneral.size(); i++) {
                GeneralQuad quad = quadsGeneral.get(i);
                System.arraycopy(quad.positions(), 0, positions, i * 12, 12);
                meta[i * 8] = quad.textureIndex();
                meta[i * 8 + 1] = quad.lightWord();
                meta[i * 8 + 2] = quad.normalPacked();
                meta[i * 8 + 3] = quad.flags();
                System.arraycopy(quad.uvPacked(), 0, meta, i * 8 + 4, 4);
            }
            generalPositions[layer] = positions;
            generalMeta[layer] = meta;
        }
        return new SectionGeometryRaw(axisWords, axisCounts, axisAux, false,
                generalPositions, generalMeta, generalCounts, geometry.cornerLight());
    }

    private static int[] lightColumnOf(TerrainSectionStore store, SectionDesc desc, int words) {
        MemorySegment light = store.auxSpace().segment((long) desc.lightDataBase() * 4L,
                (long) words * 4L);
        return light.toArray(ValueLayout.JAVA_INT);
    }

    @Test
    void rawUploadLandsTheCornerLightColumnVerbatim() {
        // 3 axis (layers 0,0,1) + 2 general (layers 0,2) = 5 quads, a distinct word
        // per quad half, in the store's layout (axis in layer order, then general).
        int[] column = {100, 101, 110, 111, 120, 121, 200, 201, 220, 221};
        List<PackedAxisQuad> solid = new ArrayList<>();
        solid.add(axisQuad(1));
        solid.add(axisQuad(2));
        List<GeneralQuad> solidGeneral = new ArrayList<>();
        solidGeneral.add(generalQuad(3));
        SectionGeometry geometry = new SectionGeometry(solid, List.of(axisQuad(4)), List.of(),
                solidGeneral, List.of(), List.of(generalQuad(5)), new short[0], column);

        TerrainSectionStore rawStore = newStore();
        SectionDesc viaRaw = rawStore.uploadRaw(2, 3, 4, rawOf(geometry), 1L);
        assertEquals(5L * 8L, rawStore.auxUsedBytes(),
                "8 B of corner light per quad, reserved on the raw path too");
        assertArrayEquals(column, lightColumnOf(rawStore, viaRaw, column.length),
                "the column lands verbatim at the descriptor's light base");

        // The same geometry through the object path leaves the identical light bytes:
        // doUpload receives the same column from both entry points.
        TerrainSectionStore objectStore = newStore();
        SectionDesc viaObjects = objectStore.upload(2, 3, 4, geometry, 1L);
        assertEquals(viaObjects, viaRaw, "descriptor of the raw upload");
        assertArrayEquals(lightColumnOf(objectStore, viaObjects, column.length),
                lightColumnOf(rawStore, viaRaw, column.length),
                "light bytes of the raw upload match the object upload");

        // Removal returns the light bytes the raw upload reserved.
        assertTrue(rawStore.remove(2, 3, 4, 2L));
        rawStore.collectCompleted(2L);
        assertEquals(0L, rawStore.auxUsedBytes());
    }
}
