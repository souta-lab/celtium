package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * E10-a (item 723): the draw-source table shapes, the reference visibility test and the
 * oracle comparison of {@link GpuTerrainDrawTable}, plus the capability gate order of
 * {@link GpuTerrainCulling}. These are the layers design section 5.3 (b) assigns to unit
 * tests: a transcription slip in the visibility formulas or the packing must fail here,
 * not in a leg.
 */
class GpuTerrainDrawTableTest {

    private static GpuTerrainDrawTable.Entry entry(int dict, int sx, int sy, int sz) {
        return new GpuTerrainDrawTable.Entry(36, 0, 100, dict, sx, sy, sz, 0);
    }

    @Test
    void entryPackingRoundTrips() {
        GpuTerrainDrawTable.Entry source =
                new GpuTerrainDrawTable.Entry(123456, 789, -42, 7, -3, 12, 250, (2 << 8) | 1);
        ByteBuffer buffer = GpuTerrainDrawTable.nativeBuffer(GpuTerrainDrawTable.ENTRY_BYTES);
        source.pack(buffer, 0);
        GpuTerrainDrawTable.Entry back = GpuTerrainDrawTable.Entry.unpack(buffer, 0);
        assertEquals(source.indexCount, back.indexCount);
        assertEquals(source.firstIndex, back.firstIndex);
        assertEquals(source.vertexOffset, back.vertexOffset);
        assertEquals(source.dictIndex, back.dictIndex);
        assertEquals(source.sectionX, back.sectionX);
        assertEquals(source.sectionY, back.sectionY);
        assertEquals(source.sectionZ, back.sectionZ);
        assertEquals(source.flags, back.flags);
        assertEquals(2, back.passOrdinal());
        assertEquals(GpuTerrainDrawTable.ENTRY_BYTES, 32);
    }

    @Test
    void regionBoxMatchesRenderRegionDimensions() {
        assertEquals(org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion.REGION_WIDTH * 16,
                GpuTerrainDrawTable.REGION_SIZE_X_BLOCKS);
        assertEquals(org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion.REGION_HEIGHT * 16,
                GpuTerrainDrawTable.REGION_SIZE_Y_BLOCKS);
        assertEquals(org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion.REGION_LENGTH * 16,
                GpuTerrainDrawTable.REGION_SIZE_Z_BLOCKS);
    }

    @Test
    void tableIsSectionMajorAndRemapFollowsTheDictionary() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int regionB = builder.dictIndexFor(200, 5);
        int regionA = builder.dictIndexFor(100, 3);
        assertEquals(regionB, builder.dictIndexFor(200, 5));
        builder.add(entry(regionB, 4, 0, 0));
        builder.add(entry(regionA, -2, 0, 0));
        builder.add(entry(regionA, -2, 0, 9));
        GpuTerrainDrawTable.Table table = builder.build();
        assertEquals(3, table.entries.length);
        // Section-major: (-2,0,0), (-2,0,9), (4,0,0).
        assertEquals(-2, table.entries[0].sectionX);
        assertEquals(0, table.entries[0].sectionZ);
        assertEquals(9, table.entries[1].sectionZ);
        assertEquals(4, table.entries[2].sectionX);
        assertEquals(5, table.remap[regionB]);
        assertEquals(3, table.remap[regionA]);
        // Every collected region's mask bit is set.
        for (int dict = 0; dict < 2; dict++) {
            assertTrue((table.maskWords[dict >>> 5] & (1 << (dict & 31))) != 0);
        }
    }

    /** An identity view-projection: the visible box is the [-1,1] cube in world units. */
    private static float[] identityPlanes() {
        float[] m = new float[16];
        m[0] = 1;
        m[5] = 1;
        m[10] = 1;
        m[15] = 1;
        return GpuTerrainDrawTable.extractPlanes(m);
    }

    @Test
    void visibilityAcceptsBoxInsideAndRejectsBoxOutside() {
        float[] planes = identityPlanes();
        // The identity frustum is the [-1,1] cube, measured here in blocks. A region box is
        // 128 blocks wide, so the box at section (1,0,0) (x in [16,144]) clears the cube's
        // right plane entirely and must be rejected...
        assertFalse(GpuTerrainDrawTable.isVisible(planes, 0, 0, 0, 4096, entry(0, 1, 0, 0)));
        // ...while boxes that merely straddle the cube are kept (the conservative side):
        // section (0,0,0) spans [0,128] and overlaps the cube at its corner, and section
        // (-1,0,0) spans [-16,112] across it.
        assertTrue(GpuTerrainDrawTable.isVisible(planes, 0, 0, 0, 4096, entry(0, 0, 0, 0)));
        assertTrue(GpuTerrainDrawTable.isVisible(planes, 0, 0, 0, 4096, entry(0, -1, 0, 0)));
    }

    @Test
    void distanceTermRejectsAFarBoxTheFrustumKeeps() {
        float[] planes = identityPlanes();
        GpuTerrainDrawTable.Entry near = entry(0, -1, 0, 0);
        assertTrue(GpuTerrainDrawTable.isVisible(planes, 0, 0, 0, 4096, near));
        assertFalse(GpuTerrainDrawTable.isVisible(planes, 5000, 0, 0, 100, near));
    }

    @Test
    void visibilityNeverRejectsABruteForceVisibleBox() {
        // Property (design 5.3 (b)): for random boxes and a random perspective-ish matrix,
        // whenever any sampled point of the box is inside every plane's half-space and
        // within distance, the reference test must not reject the box.
        Random random = new Random(723);
        for (int trial = 0; trial < 400; trial++) {
            float[] m = new float[16];
            for (int i = 0; i < 16; i++) {
                m[i] = random.nextFloat() * 2 - 1;
            }
            m[15] = 1.0f;
            float[] planes = GpuTerrainDrawTable.extractPlanes(m);
            int sx = random.nextInt(9) - 4;
            int sy = random.nextInt(5) - 2;
            int sz = random.nextInt(9) - 4;
            GpuTerrainDrawTable.Entry e = entry(0, sx, sy, sz);
            float minX = sx * 16f, minY = sy * 16f, minZ = sz * 16f;
            boolean anyPointInside = false;
            for (int s = 0; s < 512 && !anyPointInside; s++) {
                float px = minX + random.nextFloat() * GpuTerrainDrawTable.REGION_SIZE_X_BLOCKS;
                float py = minY + random.nextFloat() * GpuTerrainDrawTable.REGION_SIZE_Y_BLOCKS;
                float pz = minZ + random.nextFloat() * GpuTerrainDrawTable.REGION_SIZE_Z_BLOCKS;
                boolean inside = true;
                for (int p = 0; p < 6; p++) {
                    if (planes[p * 4] * px + planes[p * 4 + 1] * py + planes[p * 4 + 2] * pz
                            + planes[p * 4 + 3] < 0) {
                        inside = false;
                        break;
                    }
                }
                anyPointInside = inside;
            }
            if (anyPointInside) {
                assertTrue(GpuTerrainDrawTable.isVisible(planes, 0, 0, 0, 1.0e9f, e),
                        "reference test rejected a box with an inside point, trial " + trial);
            }
        }
    }

    @Test
    void generationMirrorsTheCanonicalListWhenEverythingPasses() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int dict = builder.dictIndexFor(42, 9);
        builder.add(new GpuTerrainDrawTable.Entry(36, 0, 100, dict, -1, 0, 0, 0));
        builder.add(new GpuTerrainDrawTable.Entry(72, 0, 260, dict, -1, 0, 0, 0));
        GpuTerrainDrawTable.Table table = builder.build();
        int[] generated = GpuTerrainDrawTable.generate(table, 0, table.entries.length,
                identityPlanes(), 0, 0, 0, 4096);
        int[] canonical = {36, 1, 0, 100, 9, 72, 1, 0, 260, 9};
        GpuTerrainDrawTable.Diff diff = GpuTerrainDrawTable.compareCommands(canonical, generated);
        assertEquals(2, diff.cpuCount);
        assertEquals(2, diff.gpuCount);
        assertEquals(2, diff.matched);
        assertEquals(0, diff.cpuOnly);
        assertEquals(0, diff.gpuOnly);
    }

    @Test
    void generationDropsMaskedOutAndUnmappedRegions() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int drawn = builder.dictIndexFor(1, 4);
        int undrawn = builder.dictIndexFor(2, -1);
        builder.add(entry(drawn, -1, 0, 0));
        builder.add(entry(undrawn, -1, 0, 0));
        GpuTerrainDrawTable.Table table = builder.build();
        int[] generated = GpuTerrainDrawTable.generate(table, 0, table.entries.length,
                identityPlanes(), 0, 0, 0, 4096);
        assertEquals(GpuTerrainDrawTable.COMMAND_INTS, generated.length);
        assertEquals(4, generated[4]);
    }

    @Test
    void compareCountsDuplicatesAsMultiset() {
        int[] cpu = {36, 1, 0, 100, 9, 36, 1, 0, 100, 9, 72, 1, 0, 260, 9};
        int[] gpu = {36, 1, 0, 100, 9, 72, 1, 0, 260, 9, 72, 1, 0, 260, 9};
        GpuTerrainDrawTable.Diff diff = GpuTerrainDrawTable.compareCommands(cpu, gpu);
        assertEquals(3, diff.cpuCount);
        assertEquals(3, diff.gpuCount);
        assertEquals(2, diff.matched);
        assertEquals(1, diff.cpuOnly);
        assertEquals(1, diff.gpuOnly);
    }

    @Test
    void frameGateOrdersMaskBeforeCount() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        builder.generations(7, 7);
        int dict = builder.dictIndexFor(1, 0);
        builder.add(entry(dict, 0, 0, 0));
        builder.add(entry(dict, 1, 0, 0));
        GpuTerrainDrawTable.Table table = builder.build();
        // A stale mask generation gates first, ahead of the count limit.
        assertEquals(GpuTerrainCulling.GateReason.MASK_GENERATION_MISMATCH,
                GpuTerrainDrawTable.frameGate(table, 1, 8));
        // Two entries against a device limit of one.
        assertEquals(GpuTerrainCulling.GateReason.OVER_MAX_DRAW_INDIRECT_COUNT,
                GpuTerrainDrawTable.frameGate(table, 1, 7));
        // A limit of 0 means "never read" (the context's convention) and does not gate.
        assertEquals(GpuTerrainCulling.GateReason.NONE, GpuTerrainDrawTable.frameGate(table, 0, 7));
        assertEquals(GpuTerrainCulling.GateReason.NONE, GpuTerrainDrawTable.frameGate(table, 100, 7));
    }

    @Test
    void deterministicSlotKeepsEntrySlotsAndZeroFillsCulledEntries() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int drawn = builder.dictIndexFor(1, 4);
        int undrawn = builder.dictIndexFor(2, -1);
        builder.add(new GpuTerrainDrawTable.Entry(36, 0, 100, drawn, -1, 0, 0, 0));
        builder.add(new GpuTerrainDrawTable.Entry(72, 0, 260, undrawn, -1, 0, 0, 0));
        builder.add(new GpuTerrainDrawTable.Entry(48, 0, 380, drawn, -1, 0, 0, 0));
        GpuTerrainDrawTable.Table table = builder.build();
        int[] slotted = GpuTerrainDrawTable.generateDeterministicSlot(table, 0, table.entries.length,
                identityPlanes(), 0, 0, 0, 4096);
        // Length is the entry count itself: the draw count needs no count buffer.
        assertEquals(3 * GpuTerrainDrawTable.COMMAND_INTS, slotted.length);
        // Table order is section-major, so slot order follows the sorted entries; every
        // surviving command sits at its own entry slot and the unmapped region's slot
        // (whichever it is) is a zero command. Stripping must recover exactly the
        // append-form generation, in order.
        int[] stripped = GpuTerrainDrawTable.stripZeroCommands(slotted);
        int[] appended = GpuTerrainDrawTable.generate(table, 0, table.entries.length,
                identityPlanes(), 0, 0, 0, 4096);
        assertEquals(appended.length, stripped.length);
        for (int i = 0; i < appended.length; i++) {
            assertEquals(appended[i], stripped[i]);
        }
        int zeroSlots = 0;
        for (int s = 0; s < 3; s++) {
            if (slotted[s * GpuTerrainDrawTable.COMMAND_INTS] == 0
                    && slotted[s * GpuTerrainDrawTable.COMMAND_INTS + 1] == 0) {
                zeroSlots++;
            }
        }
        assertEquals(1, zeroSlots);
    }

    @Test
    void stripZeroCommandsDropsOnlyZeroCommands() {
        int[] in = {36, 1, 0, 100, 9, 0, 0, 0, 0, 0, 72, 1, 0, 260, 9};
        int[] out = GpuTerrainDrawTable.stripZeroCommands(in);
        assertEquals(2 * GpuTerrainDrawTable.COMMAND_INTS, out.length);
        assertEquals(36, out[0]);
        assertEquals(72, out[5]);
    }

    @Test
    void capabilityGateOrdersCapabilityBeforeBudget() {
        assertEquals(GpuTerrainCulling.GateReason.NO_DRAW_INDIRECT_COUNT,
                GpuTerrainCulling.capabilityGate(false, true));
        assertEquals(GpuTerrainCulling.GateReason.BUDGET_ACTIVE,
                GpuTerrainCulling.capabilityGate(true, true));
        assertEquals(GpuTerrainCulling.GateReason.NONE,
                GpuTerrainCulling.capabilityGate(true, false));
    }
}
