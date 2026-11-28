package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainSectionStoreTest {

    private static final TerrainVertexRequirements MC_ENTITY =
            new TerrainVertexRequirements(true, false, false, false);

    private TerrainSectionStore newStore() {
        return new TerrainSectionStore(new HostSlabSpace(1 << 20), new HostSlabSpace(1 << 20),
                new HostSlabSpace(1 << 16));
    }

    private static PackedAxisQuad quad(int x) {
        return PackedAxisQuad.of(x, 0, 0, 1, false, 1, 1, 3, 0, 0, 15, 15, 255, 0, 0, 0, 0);
    }

    private static GeneralQuad general() {
        float[] positions = new float[12];
        return new GeneralQuad(positions, 1, 0, 0, 0);
    }

    private static SectionGeometry geometry(int axisQuads, short[] ids) {
        java.util.List<PackedAxisQuad> solid = new java.util.ArrayList<>();
        for (int i = 0; i < axisQuads; i++) {
            solid.add(quad(i));
        }
        return new SectionGeometry(solid, List.of(), List.of(),
                List.of(general()), List.of(), List.of(), ids);
    }

    @Test
    void uploadWritesDescriptorAndLayerRanges() {
        TerrainSectionStore store = newStore();
        SectionDesc desc = store.upload(2, 3, 4, geometry(5, new short[0]), 1L);
        assertEquals(2, desc.sectionX());
        assertEquals(5, desc.axisQuadCount());
        assertEquals(1, desc.generalQuadCount());
        assertEquals(0L, desc.axisSlabOffset());
        assertEquals(SectionDesc.NO_AUXILIARY, desc.mcEntityBase(), "pack OFF: no aux column");
        TerrainSectionStore.LayerRange[] ranges = store.layerRanges(2, 3, 4);
        assertEquals(5, ranges[0].axisQuadCount());
        assertEquals(1, ranges[0].generalQuadCount());
        assertEquals(0, ranges[1].axisQuadCount());
        // The slab holds the quads verbatim, readable through the core contract.
        MemorySegment slab = store.axisSpace().segment(0, 5 * 16L);
        assertEquals(quad(3), PackedAxisQuad.readFrom(slab, 3 * 16L));
    }

    @Test
    void replaceDefersTheOldRanges() {
        TerrainSectionStore store = newStore();
        store.upload(0, 0, 0, geometry(4, new short[0]), 1L);
        long usedBefore = store.axisUsedBytes();
        store.upload(0, 0, 0, geometry(4, new short[0]), 5L);
        assertEquals(usedBefore * 2, store.axisUsedBytes(), "old range held until its timeline");
        store.collectCompleted(5L);
        assertEquals(usedBefore, store.axisUsedBytes());
        assertEquals(1, store.sectionCount());
    }

    @Test
    void removeReleasesTheSection() {
        TerrainSectionStore store = newStore();
        store.upload(1, 1, 1, geometry(2, new short[0]), 1L);
        assertTrue(store.remove(1, 1, 1, 2L));
        assertFalse(store.remove(1, 1, 1, 2L));
        assertNull(store.descriptor(1, 1, 1));
        store.collectCompleted(2L);
        assertEquals(0L, store.axisUsedBytes());
    }

    @Test
    void requirementsChangeRegeneratesOnlyTheAuxColumn() {
        TerrainSectionStore store = newStore();
        short[] ids = {10, 11, 12, 13};
        SectionDesc before = store.upload(0, 0, 0, geometry(4, ids), 1L);
        assertEquals(SectionDesc.NO_AUXILIARY, before.mcEntityBase());

        store.setRequirements(MC_ENTITY, 2L);
        SectionDesc armed = store.descriptor(0, 0, 0);
        assertNotNull(armed);
        assertTrue(armed.hasAuxiliaryColumn(), "column materialized on the pack switch (G-5)");
        assertEquals(before.axisSlabOffset(), armed.axisSlabOffset(), "geometry never moves");
        assertEquals(before.generalSlabOffset(), armed.generalSlabOffset());
        MemorySegment aux = store.auxSpace().segment((long) armed.mcEntityBase() * 2L, 8L);
        for (int i = 0; i < 4; i++) {
            assertEquals(ids[i], aux.get(ValueLayout.JAVA_SHORT, (long) i * 2L));
        }

        store.setRequirements(TerrainVertexRequirements.NONE, 3L);
        SectionDesc disarmed = store.descriptor(0, 0, 0);
        assertEquals(SectionDesc.NO_AUXILIARY, disarmed.mcEntityBase());
        assertEquals(before.axisSlabOffset(), disarmed.axisSlabOffset());
    }

    @Test
    void sectionWithoutIdsStaysUnarmed() {
        TerrainSectionStore store = newStore();
        store.setRequirements(MC_ENTITY, 1L);
        SectionDesc desc = store.upload(0, 0, 0, geometry(3, new short[0]), 1L);
        assertEquals(SectionDesc.NO_AUXILIARY, desc.mcEntityBase(),
                "no source ids: unarmed fallback, never garbage");
    }

    @Test
    void descriptorsRoundTripThroughTheBuffer() {
        TerrainSectionStore store = newStore();
        store.upload(1, 0, 0, geometry(1, new short[0]), 1L);
        store.upload(0, 0, 0, geometry(2, new short[0]), 1L);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment out = arena.allocate(SectionDesc.BYTE_SIZE * 4L);
            int count = store.writeDescriptors(out, 0L);
            assertEquals(2, count);
            SectionDesc first = SectionDesc.readFrom(out, 0L);
            SectionDesc second = SectionDesc.readFrom(out, SectionDesc.BYTE_SIZE);
            assertEquals(0, first.sectionX(), "key order: (0,0,0) before (1,0,0)");
            assertEquals(1, second.sectionX());
        }
    }

    // ------------------------------------------------------------------
    // S2-b-perf CPU-4: the raw upload path and the incremental descriptor index.
    // The pass condition is byte equivalence: the same logical geometry through
    // the object path and through the raw bulk-copy path must leave identical
    // slab bytes, descriptors and layer ranges - not one byte may differ.
    // ------------------------------------------------------------------

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

    private static SectionGeometry richGeometry(int[] axisPerLayer, int[] generalPerLayer,
                                                short[] ids, int seedBase) {
        List<List<PackedAxisQuad>> axis = new ArrayList<>();
        List<List<GeneralQuad>> general = new ArrayList<>();
        for (int layer = 0; layer < TerrainSectionStore.LAYER_COUNT; layer++) {
            List<PackedAxisQuad> layerAxis = new ArrayList<>();
            for (int i = 0; i < axisPerLayer[layer]; i++) {
                layerAxis.add(axisQuad(seedBase + layer * 31 + i));
            }
            axis.add(layerAxis);
            List<GeneralQuad> layerGeneral = new ArrayList<>();
            for (int i = 0; i < generalPerLayer[layer]; i++) {
                layerGeneral.add(generalQuad(seedBase + layer * 17 + i));
            }
            general.add(layerGeneral);
        }
        return new SectionGeometry(axis.get(0), axis.get(1), axis.get(2),
                general.get(0), general.get(1), general.get(2), ids);
    }

    /** The mesher's storage shape for the same logical geometry the object path carries. */
    private static SectionGeometryRaw rawOf(SectionGeometry geometry, boolean auxEnabled) {
        int layers = TerrainSectionStore.LAYER_COUNT;
        int[][] axisWords = new int[layers][];
        int[] axisCounts = new int[layers];
        short[][] axisAux = new short[layers][];
        float[][] generalPositions = new float[layers][];
        int[][] generalMeta = new int[layers][];
        int[] generalCounts = new int[layers];
        short[] ids = geometry.mcEntityIds();
        int idCursor = 0;
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
            short[] aux = new short[quads.size()];
            if (auxEnabled) {
                System.arraycopy(ids, idCursor, aux, 0, quads.size());
            }
            idCursor += quads.size();
            axisAux[layer] = aux;
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
        return new SectionGeometryRaw(axisWords, axisCounts, axisAux, auxEnabled,
                generalPositions, generalMeta, generalCounts);
    }

    private static byte[] rangeBytes(SlabSpace space, long offset, long bytes) {
        if (bytes == 0L) {
            return new byte[0];
        }
        return space.segment(offset, bytes).toArray(ValueLayout.JAVA_BYTE);
    }

    /** The slab bytes one resident section occupies, read through its own descriptor. */
    private static byte[][] sectionBytes(TerrainSectionStore store, SectionDesc desc) {
        return new byte[][]{
                rangeBytes(store.axisSpace(), desc.axisSlabOffset(),
                        desc.axisQuadCount() * PackedAxisQuad.BYTE_SIZE),
                rangeBytes(store.generalSpace(), desc.generalSlabOffset(),
                        desc.generalQuadCount() * GeneralQuad.BYTE_SIZE),
                desc.hasAuxiliaryColumn()
                        ? rangeBytes(store.auxSpace(), (long) desc.mcEntityBase() * 2L,
                                desc.axisQuadCount() * 2L)
                        : new byte[0]};
    }

    @Test
    void rawUploadProducesIdenticalSlabBytesDescriptorsAndRanges() {
        TerrainSectionStore objectStore = newStore();
        TerrainSectionStore rawStore = newStore();
        objectStore.setRequirements(MC_ENTITY, 1L);
        rawStore.setRequirements(MC_ENTITY, 1L);

        short[] ids = {10, 11, 12, 13, 14, 15};
        SectionGeometry geometry = richGeometry(new int[]{3, 2, 1}, new int[]{2, 0, 1}, ids, 5);
        SectionDesc viaObjects = objectStore.upload(2, 3, 4, geometry, 1L);
        SectionDesc viaRaw = rawStore.uploadRaw(2, 3, 4, rawOf(geometry, true), 1L);
        assertEquals(viaObjects, viaRaw, "descriptor of the raw upload");
        assertTrue(viaRaw.hasAuxiliaryColumn(), "aux armed on both paths");

        SectionGeometry empty = richGeometry(new int[3], new int[3], new short[0], 0);
        assertEquals(objectStore.upload(-1, 0, 7, empty, 1L),
                rawStore.uploadRaw(-1, 0, 7, rawOf(empty, false), 1L),
                "empty section through both paths");

        SectionGeometry noAux = richGeometry(new int[]{2, 0, 0}, new int[]{0, 1, 0},
                new short[0], 40);
        assertEquals(objectStore.upload(5, -2, 5, noAux, 1L),
                rawStore.uploadRaw(5, -2, 5, rawOf(noAux, false), 1L),
                "section without an auxiliary column through both paths");

        for (int[] coords : new int[][]{{2, 3, 4}, {-1, 0, 7}, {5, -2, 5}}) {
            assertArrayEquals(objectStore.layerRanges(coords[0], coords[1], coords[2]),
                    rawStore.layerRanges(coords[0], coords[1], coords[2]),
                    "layer ranges at " + Arrays.toString(coords));
            byte[][] objectBytes = sectionBytes(objectStore,
                    objectStore.descriptor(coords[0], coords[1], coords[2]));
            byte[][] rawBytes = sectionBytes(rawStore,
                    rawStore.descriptor(coords[0], coords[1], coords[2]));
            assertArrayEquals(objectBytes[0], rawBytes[0],
                    "axis slab bytes at " + Arrays.toString(coords));
            assertArrayEquals(objectBytes[1], rawBytes[1],
                    "general slab bytes at " + Arrays.toString(coords));
            assertArrayEquals(objectBytes[2], rawBytes[2],
                    "auxiliary slab bytes at " + Arrays.toString(coords));
        }
        assertEquals(objectStore.descriptors(), rawStore.descriptors());
        assertEquals(objectStore.axisUsedBytes(), rawStore.axisUsedBytes(), "axis bytes in use");
        assertEquals(objectStore.generalUsedBytes(), rawStore.generalUsedBytes(),
                "general bytes in use");
        assertEquals(objectStore.auxUsedBytes(), rawStore.auxUsedBytes(), "auxiliary bytes in use");
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment outObjects = arena.allocate(SectionDesc.BYTE_SIZE * 4L);
            MemorySegment outRaw = arena.allocate(SectionDesc.BYTE_SIZE * 4L);
            assertEquals(objectStore.writeDescriptors(outObjects, 0L),
                    rawStore.writeDescriptors(outRaw, 0L), "descriptor count");
            assertEquals(-1L, outObjects.mismatch(outRaw),
                    "descriptor byte stream");
        }
    }

    @Test
    void incrementalDescriptorsMatchAFreshSortAfterEveryChange() {
        TerrainSectionStore store = newStore();
        TreeMap<Long, SectionDesc> reference = new TreeMap<>();
        Random random = new Random(20261128L);
        int[][] pool = new int[24][];
        for (int i = 0; i < pool.length; i++) {
            pool[i] = new int[]{i % 5 - 2, (i / 5) % 3 - 1, (i * 7) % 9 - 4};
        }
        for (int op = 0; op < 200; op++) {
            int[] coords = pool[random.nextInt(pool.length)];
            long key = TerrainSectionStore.sectionKey(coords[0], coords[1], coords[2]);
            if (random.nextInt(3) == 0) {
                if (store.remove(coords[0], coords[1], coords[2], op + 1L)) {
                    reference.remove(key);
                }
            } else {
                store.upload(coords[0], coords[1], coords[2], richGeometry(
                        new int[]{random.nextInt(4), random.nextInt(3), random.nextInt(2)},
                        new int[]{random.nextInt(3), 0, random.nextInt(2)},
                        new short[0], op), op + 1L);
                reference.put(key, store.descriptor(coords[0], coords[1], coords[2]));
            }
            assertEquals(new ArrayList<>(reference.values()), store.descriptors(),
                    "descriptors() after op " + op);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment out = arena.allocate(SectionDesc.BYTE_SIZE * (reference.size() + 1L));
                assertEquals(reference.size(), store.writeDescriptors(out, 0L),
                        "writeDescriptors count after op " + op);
                long cursor = 0L;
                for (SectionDesc expected : reference.values()) {
                    assertEquals(expected, SectionDesc.readFrom(out, cursor),
                            "writeDescriptors entry after op " + op);
                    cursor += SectionDesc.BYTE_SIZE;
                }
            }
        }
    }
}
