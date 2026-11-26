package org.soutalab.celtium.vulkan.terrain;

import org.embeddedt.embeddium.impl.common.util.NativeBuffer;
import org.embeddedt.embeddium.impl.gl.util.VertexRange;
import org.embeddedt.embeddium.impl.model.quad.properties.ModelQuadFacing;
import org.embeddedt.embeddium.impl.render.chunk.data.BuiltSectionMeshParts;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.impl.TerrainVertex16;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * T3 condition (2): the store's published shape is captured atomically by
 * {@link TerrainSectionStore#captureSnapshot()} — generation, record count,
 * both images and the per-pass slab counts of both spaces read in one locked
 * pass. A frame snapshot built on a capture therefore cannot mix one
 * generation's image with another's counts (INV-2a), and the per-pass form
 * (M2) keeps prescription B's isolation: a pass' frozen counts never include
 * another pass' records, which a single combined count array would regress.
 */
class TerrainSnapshotCaptureTest {

    private static BuiltSectionMeshParts parts(int quadsPosX, int quadsUnassigned, boolean factors) {
        int quads = quadsPosX + quadsUnassigned;
        NativeBuffer vertices = new NativeBuffer(quads * 4 * TerrainVertex16.STRIDE);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        if (quadsPosX > 0) {
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quadsPosX * 4));
        }
        if (quadsUnassigned > 0) {
            ranges.put(ModelQuadFacing.UNASSIGNED,
                    new VertexRange(quadsPosX * 4, quadsUnassigned * 4));
        }
        int[] rdh = null;
        if (factors) {
            rdh = new int[quads];
            for (int i = 0; i < quads; i++) {
                rdh[i] = 100 + i;
            }
        }
        return new BuiltSectionMeshParts(vertices, null, null, ranges, rdh);
    }

    private static BuiltSectionMeshParts partsForStride(int quadsPosX, int quadsUnassigned, int stride) {
        int quads = quadsPosX + quadsUnassigned;
        NativeBuffer vertices = new NativeBuffer(quads * 4 * stride);
        Map<ModelQuadFacing, VertexRange> ranges = new EnumMap<>(ModelQuadFacing.class);
        if (quadsPosX > 0) {
            ranges.put(ModelQuadFacing.POS_X, new VertexRange(0, quadsPosX * 4));
        }
        if (quadsUnassigned > 0) {
            ranges.put(ModelQuadFacing.UNASSIGNED,
                    new VertexRange(quadsPosX * 4, quadsUnassigned * 4));
        }
        return new BuiltSectionMeshParts(vertices, null, null, ranges, null);
    }

    @Test
    void captureFreezesCountsAcrossALaterRegistration() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1));

        TerrainSectionStore.SnapshotData capture = store.captureSnapshot();
        assertEquals(1, capture.recordCount());
        assertEquals(1, capture.vanillaSlabCounts()[0][0]);

        assertNotNull(store.register(mesher.build(2, 0, 0, 0, parts(1, 0, true), 0), 2));
        assertEquals(2, store.recordCount(), "the live store moved on");
        assertEquals(1, capture.recordCount(), "the capture still reads its own generation");
        assertEquals(1, capture.vanillaSlabCounts()[0][0],
                "the frozen count does not see the registration that landed after the capture");
    }

    @Test
    void oneCaptureIsAtomicAcrossVanillaAndPack() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1));
        assertNotNull(store.register(mesher.build(2, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 2));

        TerrainSectionStore.SnapshotData capture = store.captureSnapshot();
        assertEquals(store.imageGeneration(), capture.generation(),
                "the capture names the generation it was taken from");
        assertEquals(2, capture.recordCount(), "both spaces' records are in the one count");
        assertEquals(1, capture.vanillaSlabCounts()[0][0]);
        assertEquals(1, capture.packSlabCounts()[1][TerrainSectionStore.PACK_SLAB_BASE]);
        assertArrayEquals(store.descImage(), capture.descImage(),
                "the captured desc image is the store's image at capture time");
        assertArrayEquals(store.factorBaseImage(), capture.factorBaseImage(),
                "the captured factor-base image is the store's image at capture time");
    }

    @Test
    void frozenCountsKeepPrescriptionBPerPassIsolation() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        // Two vanilla passes register into the same first slab (the shape
        // TerrainSectionStoreTest.perPassSlabCountsSeeOnlyThatPassRecords
        // established for the live counters).
        assertNotNull(store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1));
        assertNotNull(store.register(mesher.build(2, 0, 0, 1, parts(1, 0, true), 0), 2));

        TerrainSectionStore.SnapshotData capture = store.captureSnapshot();
        assertEquals(2, capture.vanillaSlabCounts().length,
                "one count array per pass that held a record at capture time");
        assertEquals(1, capture.vanillaSlabCounts()[0][0], "pass 0 froze only its own record");
        assertEquals(1, capture.vanillaSlabCounts()[1][0],
                "pass 1 froze only its own record: a single combined array (the M2 regression)"
                        + " would read the store-wide 2 for both passes and re-arm the empty copy");
    }

    @Test
    void capturedImagesAreTheCapturesOwnCopies() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(1, 0, 0, 0, parts(1, 0, true), 0), 1));

        TerrainSectionStore.SnapshotData capture = store.captureSnapshot();
        int[] descAtCapture = capture.descImage().clone();
        int[] basesAtCapture = capture.factorBaseImage().clone();
        assertNotSame(store.descImage(), capture.descImage(),
                "the capture does not alias the store's live desc image");
        assertNotSame(store.factorBaseImage(), capture.factorBaseImage(),
                "the capture does not alias the store's live factor-base image");

        // Churn the store: a swap-remove rewrites slot 0 of the live image.
        assertNotNull(store.register(mesher.build(2, 0, 0, 0, parts(1, 0, true), 0), 2));
        assertNotNull(store.unregister(new TerrainSectionStore.Key(1, 0, 0, 0), 5));
        assertArrayEquals(descAtCapture, capture.descImage(),
                "later store mutations never rewrite a capture already handed out");
        assertArrayEquals(basesAtCapture, capture.factorBaseImage(),
                "later store mutations never rewrite a capture already handed out");
    }

    @Test
    void packCountsAreFrozenPerPass() {
        AsyncSectionMesher mesher = new AsyncSectionMesher();
        TerrainSectionStore store = new TerrainSectionStore();
        assertNotNull(store.register(mesher.build(1, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 1));
        assertNotNull(store.register(mesher.build(2, 0, 0, 1, partsForStride(1, 0, 44), 0, 44), 2));
        assertNotNull(store.register(mesher.build(3, 0, 0, 2, partsForStride(1, 0, 44), 0, 44), 3));

        TerrainSectionStore.SnapshotData capture = store.captureSnapshot();
        int base = TerrainSectionStore.PACK_SLAB_BASE;
        assertEquals(2, capture.packSlabCounts()[1][base], "pass 1 froze its two pack records");
        assertEquals(1, capture.packSlabCounts()[2][base], "pass 2 froze its one pack record");
        assertEquals(0, capture.packSlabCounts()[0][base], "pass 0 holds no pack records");
        assertEquals(2, store.packSlabRecordCounts(1)[base],
                "the live per-pass counter the capture is built from agrees");
        assertEquals(3, store.packSlabRecordCounts()[base],
                "the store-wide form still counts every pass, as before");
    }
}
