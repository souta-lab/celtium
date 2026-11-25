package org.soutalab.celtium.vulkan.terrain;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TerrainUploadBudgetAndStoreTest {

    private static final class TrackedPayload implements AutoCloseable {
        final String tag;
        final AtomicBoolean closed = new AtomicBoolean(false);

        TrackedPayload(String tag) {
            this.tag = tag;
        }

        @Override
        public void close() {
            closed.set(true);
        }
    }

    @Test
    void uploadBudgetPrioritizesImmediateSupersedesDuplicatesAndCarriesOver() {
        TerrainUploadBudget<TrackedPayload> budget = new TerrainUploadBudget<>(1024);

        TrackedPayload bgOld = new TrackedPayload("bg-old");
        TrackedPayload bgNew = new TrackedPayload("bg-new");
        TrackedPayload imm = new TrackedPayload("imm");
        TrackedPayload bgCarry = new TrackedPayload("bg-carry");

        long k1 = TerrainSectionStore.sectionPassKey(1, 0, 1, 0);
        long k2 = TerrainSectionStore.sectionPassKey(2, 0, 2, 0);
        long k3 = TerrainSectionStore.sectionPassKey(3, 0, 3, 0);

        budget.enqueue(k1, 600, TerrainUploadBudget.Priority.BACKGROUND, 1, bgOld);
        budget.enqueue(k1, 600, TerrainUploadBudget.Priority.BACKGROUND, 2, bgNew);
        assertTrue(bgOld.closed.get(), "Superseded payload must be closed immediately");
        assertFalse(bgNew.closed.get());
        assertEquals(1, budget.totalSupersededCount());

        budget.enqueue(k2, 500, TerrainUploadBudget.Priority.IMMEDIATE, 1, imm);
        budget.enqueue(k3, 600, TerrainUploadBudget.Priority.BACKGROUND, 1, bgCarry);

        List<TerrainUploadBudget.Entry<TrackedPayload>> frame1 = new ArrayList<>();
        TerrainUploadBudget.DrainStats stats1 = budget.drainFrame(frame1);
        assertEquals(1, stats1.admittedCount());
        assertEquals(500, stats1.admittedBytes());
        assertEquals("imm", frame1.get(0).payload().tag);
        assertEquals(2, stats1.remainingCount());

        List<TerrainUploadBudget.Entry<TrackedPayload>> frame2 = new ArrayList<>();
        TerrainUploadBudget.DrainStats stats2 = budget.drainFrame(frame2);
        assertEquals(1, stats2.admittedCount());
        assertEquals("bg-new", frame2.get(0).payload().tag);
        assertEquals(1, stats2.remainingCount());

        List<TerrainUploadBudget.Entry<TrackedPayload>> frame3 = new ArrayList<>();
        TerrainUploadBudget.DrainStats stats3 = budget.drainFrame(frame3);
        assertEquals(1, stats3.admittedCount());
        assertEquals("bg-carry", frame3.get(0).payload().tag);
        assertEquals(0, stats3.remainingCount());
    }

    @Test
    void sectionStoreAllocatesFixedSlabsSwapRemovesAndReclaimsAfterTimeline() {
        TerrainSectionStore.FreeListSlabPool pool = new TerrainSectionStore.FreeListSlabPool(
                8192, // 2 pages of 4096 B per slab
                4096,
                4
        );
        TerrainSectionStore store = new TerrainSectionStore(pool, 8);

        int[] counts = new int[] {4, 0, 0, 0, 0, 0, 0};
        TerrainSectionStore.SlabSlice s1 = store.upsert(0, 0, 0, 0, counts, 4096, false, SectionDesc.UNUSED_SORTED_INDEX_BASE, 1L);
        TerrainSectionStore.SlabSlice s2 = store.upsert(1, 0, 0, 0, counts, 4096, false, SectionDesc.UNUSED_SORTED_INDEX_BASE, 1L);
        TerrainSectionStore.SlabSlice s3 = store.upsert(2, 0, 0, 0, counts, 4096, false, SectionDesc.UNUSED_SORTED_INDEX_BASE, 1L);

        assertEquals(0, s1.slabId());
        assertEquals(0, s2.slabId());
        assertEquals(1, s3.slabId(), "Full slab must append new fixed slab without copying");
        assertEquals(2, store.liveSlabCount());
        assertEquals(3, store.activeRecordCount());
        assertEquals(21, store.requiredIndirectCommandCapacity());

        // Flush dirty records to staging buffer.
        ByteBuffer staging = ByteBuffer.allocateDirect(3 * SectionDesc.STRIDE_BYTES).order(ByteOrder.nativeOrder());
        int flushed = store.flushDirtyToBuffer(staging, 0);
        assertEquals(3 * SectionDesc.STRIDE_BYTES, flushed);
        assertFalse(store.hasDirtyRange());

        // Swap-remove middle slot (1,0,0) -> (2,0,0) moves into slot 1.
        assertTrue(store.remove(1, 0, 0, 0, 5L));
        assertEquals(2, store.activeRecordCount());
        assertNull(store.getRecord(1, 0, 0, 0));
        assertNotNull(store.getRecord(2, 0, 0, 0));
        assertEquals(2, store.getRecordAtSlot(1).sectionX());

        // Retired slice must not be reclaimed before timeline 5.
        assertEquals(0, store.reclaimCompleted(4L));
        assertEquals(1, store.reclaimCompleted(5L));

        // Reclaimed page in slab 0 can now satisfy a new 4096 B allocation without adding slab 2.
        TerrainSectionStore.SlabSlice s4 = store.upsert(3, 0, 0, 0, counts, 4096, false, SectionDesc.UNUSED_SORTED_INDEX_BASE, 6L);
        assertEquals(0, s4.slabId());
        assertEquals(2, store.liveSlabCount());
    }
}
