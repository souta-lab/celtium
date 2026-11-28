package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.geometry.GeneralQuad;
import org.soutalab.celtium.core.geometry.PackedAxisQuad;
import org.soutalab.celtium.core.geometry.SectionDesc;
import org.soutalab.celtium.core.shader.TerrainVertexRequirements;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void pressureRecoveryIsKeyedToTheSubmissionWatermark() {
        // The backend stamps uploads and removals with the scheduler's submission ordinal
        // (submitSequence), so a deferred free can only be collected by a value in that same
        // scale: the completed-submission watermark. A count of completion events is a
        // different currency - it advances per confirmed fence wait, not per submission
        // ordinal - and never reaches the stamps, so a slab filled under pressure would
        // never recover if collection were driven by it (R-102).
        TerrainSectionStore store = new TerrainSectionStore(new HostSlabSpace(64L),
                new HostSlabSpace(1 << 16), new HostSlabSpace(1 << 16));
        store.upload(0, 0, 0, geometry(2, new short[0]), 70L);
        store.upload(1, 0, 0, geometry(2, new short[0]), 71L);
        assertEquals(64L, store.axisUsedBytes(), "slab full: two sections of two axis quads");

        assertTrue(store.remove(1, 0, 0, 72L));
        assertEquals(64L, store.axisUsedBytes(),
                "the removed range stays used until its submission stamp is collected");
        assertThrows(IllegalStateException.class,
                () -> store.upload(2, 0, 0, geometry(2, new short[0]), 72L),
                "pressure: no room while the deferred free is uncollected");

        store.collectCompleted(4L);
        assertEquals(64L, store.axisUsedBytes(),
                "a completion-event count is not the stamps' currency: nothing is collected");
        assertThrows(IllegalStateException.class,
                () -> store.upload(2, 0, 0, geometry(2, new short[0]), 72L),
                "still refused after collection in the wrong currency");

        store.collectCompleted(72L);
        assertEquals(32L, store.axisUsedBytes(),
                "the submission watermark collects the deferred free");
        store.upload(2, 0, 0, geometry(2, new short[0]), 72L);
        assertEquals(64L, store.axisUsedBytes());
        assertEquals(2, store.sectionCount());
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
    void cornerLightColumnIsUploadedAndAccounted() {
        TerrainSectionStore store = newStore();
        // 3 axis + 1 general = 4 quads, a distinct word per quad in the column.
        int[] column = {10, 11, 20, 21, 30, 31, 40, 41};
        java.util.List<PackedAxisQuad> solid = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            solid.add(quad(i));
        }
        SectionGeometry geometry = new SectionGeometry(solid, List.of(), List.of(),
                List.of(general()), List.of(), List.of(), new short[]{7, 8, 9}, column);
        SectionDesc desc = store.upload(0, 0, 0, geometry, 1L);
        assertEquals(4L * 8L, store.auxUsedBytes(), "8 B of corner light per quad");
        MemorySegment light = store.auxSpace().segment((long) desc.lightDataBase() * 4L, 32L);
        for (int i = 0; i < column.length; i++) {
            assertEquals(column[i], light.get(ValueLayout.JAVA_INT, (long) i * 4L));
        }

        // A second section's column lands right after the first (offset in 4-byte words).
        SectionDesc second = store.upload(1, 0, 0, geometry(2, new short[0]), 1L);
        assertEquals(desc.lightDataBase() + column.length, second.lightDataBase());

        // A requirements change rewrites only the mc_Entity base; the light base is stable.
        store.setRequirements(MC_ENTITY, 2L);
        assertEquals(desc.lightDataBase(), store.descriptor(0, 0, 0).lightDataBase());
        store.setRequirements(TerrainVertexRequirements.NONE, 3L);
        assertEquals(desc.lightDataBase(), store.descriptor(0, 0, 0).lightDataBase());

        // Removal returns the light bytes with the rest of the residency.
        assertTrue(store.remove(0, 0, 0, 4L));
        assertTrue(store.remove(1, 0, 0, 4L));
        store.collectCompleted(4L);
        assertEquals(0L, store.auxUsedBytes());
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

    @Test
    void layerRangeUploadMatchesDescriptorSlotsAndUsesRelativeQuadOffsets() {
        TerrainSectionStore store = newStore();
        store.upload(5, 0, 0, geometry(2, new short[0]), 1L);
        store.upload(0, 0, 0, new SectionGeometry(
                List.of(), List.of(quad(0)), List.of(quad(1), quad(2)),
                List.of(general()), List.of(general(), general()), List.of(), new short[0]), 1L);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment descriptors = arena.allocate(96, 8);
            MemorySegment ranges = arena.allocate(96, 4);
            assertEquals(2, store.writeDescriptors(descriptors, 0));
            store.writeLayerRanges(ranges);

            SectionDesc first = SectionDesc.readFrom(descriptors, 0);
            assertEquals(0, first.sectionX());
            assertEquals(32L, first.axisSlabOffset());
            assertEquals(80L, first.generalSlabOffset());
            assertEquals(5, SectionDesc.readFrom(descriptors, 48).sectionX());
            assertArrayEquals(new int[]{
                    0, 0, 0, 1,  0, 1, 1, 2,  1, 2, 3, 0,
                    0, 2, 0, 1,  2, 0, 1, 0,  2, 0, 1, 0
            }, ranges.toArray(ValueLayout.JAVA_INT));
        }
    }
}
