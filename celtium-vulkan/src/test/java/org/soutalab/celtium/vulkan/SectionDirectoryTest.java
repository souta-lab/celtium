package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the mesh stage-2/5 section directory, Wave32 task-shader
 * amplification arithmetic, 64-quad meshlet splitting, and {@link GpuTerrainDrawTable.Table#withMask}
 * reuse contract (item 652 / 677).
 */
class SectionDirectoryTest {

    private static GpuTerrainDrawTable.Entry sectionEntry(int indexCount, int vertexOffset,
                                                          int dictIndex, int sx, int sy, int sz) {
        return new GpuTerrainDrawTable.Entry(indexCount, 0, vertexOffset, dictIndex,
                sx, sy, sz, GpuTerrainDrawTable.FLAG_SECTION_BOX);
    }

    @Test
    void rejectsNonSectionGranularTable() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int dict = builder.dictIndexFor(1, 0);
        builder.add(new GpuTerrainDrawTable.Entry(36, 0, 0, dict, 0, 0, 0, 0));
        assertNull(SectionDirectory.build(builder.build()));
    }

    @Test
    void groupsSectionMajorRunsAndRoundTripsPacking() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int dict = builder.dictIndexFor(10, 3);
        builder.add(sectionEntry(36, 0, dict, 2, 1, -3));
        builder.add(sectionEntry(72, 24, dict, 2, 1, -3));
        builder.add(sectionEntry(600, 72, dict, -1, 0, 4));
        GpuTerrainDrawTable.Table table = builder.build();

        SectionDirectory.Directory dir = SectionDirectory.build(table);
        assertNotNull(dir);
        assertEquals(2, dir.sectionCount());
        assertEquals(0, dir.ineligibleSections);

        // Section-major order: (-1,0,4) first (100 quads -> 2 meshlets at 64),
        // then (2,1,-3) with 2 entries totaling 18 quads -> 1 meshlet at 64.
        SectionDirectory.Record first = dir.records[0];
        assertEquals(-1, first.sectionX);
        assertEquals(0, first.sectionY);
        assertEquals(4, first.sectionZ);
        assertEquals(0, first.firstEntry);
        assertEquals(1, first.entryCount);
        assertEquals(100, first.quadCount());
        assertEquals(2, first.splitCount(VulkanMeshStage2Pipeline.SHADER_QUADS_PER_WORKGROUP));
        assertTrue(first.stage2Eligible());

        SectionDirectory.Record second = dir.records[1];
        assertEquals(2, second.sectionX);
        assertEquals(1, second.sectionY);
        assertEquals(-3, second.sectionZ);
        assertEquals(1, second.firstEntry);
        assertEquals(2, second.entryCount);
        assertEquals(18, second.quadCount());
        assertEquals(1, second.splitCount(VulkanMeshStage2Pipeline.SHADER_QUADS_PER_WORKGROUP));

        ByteBuffer packed = ByteBuffer.allocateDirect(dir.recordsBytes()).order(ByteOrder.nativeOrder());
        dir.packRecords(packed, 0);
        SectionDirectory.Record unpacked = SectionDirectory.Record.unpack(packed, SectionDirectory.RECORD_BYTES);
        assertEquals(second.sectionX, unpacked.sectionX);
        assertEquals(second.sectionY, unpacked.sectionY);
        assertEquals(second.sectionZ, unpacked.sectionZ);
        assertEquals(second.dictIndex, unpacked.dictIndex);
        assertEquals(second.firstEntry, unpacked.firstEntry);
        assertEquals(second.entryCount, unpacked.entryCount);
        assertEquals(second.totalIndexCount, unpacked.totalIndexCount);
        assertEquals(second.flags, unpacked.flags);
    }

    @Test
    void flagsNonQuadAndMixedDictSectionsIneligible() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int dictA = builder.dictIndexFor(1, 0);
        int dictB = builder.dictIndexFor(2, 1);
        builder.add(sectionEntry(9, 0, dictA, 0, 0, 0));
        builder.add(sectionEntry(36, 16, dictA, 1, 0, 0));
        builder.add(sectionEntry(36, 40, dictB, 1, 0, 0));

        SectionDirectory.Directory dir = SectionDirectory.build(builder.build());
        assertNotNull(dir);
        assertEquals(2, dir.ineligibleSections);
        assertFalse(dir.records[0].stage2Eligible());
        assertTrue((dir.records[0].flags & SectionDirectory.FLAG_NON_QUAD_ENTRY) != 0);
        assertFalse(dir.records[1].stage2Eligible());
        assertTrue((dir.records[1].flags & SectionDirectory.FLAG_MIXED_DICT_INDEX) != 0);
    }

    @Test
    void wave32TaskWorkGroupCountAndBudgetAlignment() {
        assertEquals(32, VulkanMeshTaskDraw.TASK_WORKGROUP_SIZE);
        assertEquals(64, VulkanMeshStage2Pipeline.SHADER_QUADS_PER_WORKGROUP);
        assertEquals(0, VulkanMeshTaskDraw.taskWorkGroupCount(0));
        assertEquals(1, VulkanMeshTaskDraw.taskWorkGroupCount(1));
        assertEquals(1, VulkanMeshTaskDraw.taskWorkGroupCount(32));
        assertEquals(2, VulkanMeshTaskDraw.taskWorkGroupCount(33));
        assertEquals(2, VulkanMeshTaskDraw.taskWorkGroupCount(64));
        assertEquals(3, VulkanMeshTaskDraw.taskWorkGroupCount(65));
    }

    @Test
    void wave32PayloadCompactionAndMeshletBinarySearchCoverEveryQuadOnce() {
        // Mirror terrain_section.task's deterministic Wave32 compaction and
        // terrain_section.mesh's binary search over meshletEnd[].
        int tileSections = VulkanMeshTaskDraw.TASK_WORKGROUP_SIZE;
        int budget = VulkanMeshStage2Pipeline.SHADER_QUADS_PER_WORKGROUP;
        int[] sectionQuads = new int[tileSections];
        boolean[] survives = new boolean[tileSections];
        for (int i = 0; i < tileSections; i++) {
            survives[i] = (i % 3) != 0; // mix of culled and surviving lanes
            sectionQuads[i] = (i == 1) ? 1 : (i == 5 ? 64 : (i == 8 ? 65 : (i + 1) * 19));
        }

        int[] meshletEnd = new int[tileSections];
        int[] compactedSection = new int[tileSections];
        int[] compactedQuads = new int[tileSections];
        int outCount = 0;
        int totalMeshlets = 0;
        for (int i = 0; i < tileSections; i++) {
            if (!survives[i]) {
                continue;
            }
            int split = (sectionQuads[i] + budget - 1) / budget;
            if (split > 0) {
                totalMeshlets += split;
                meshletEnd[outCount] = totalMeshlets;
                compactedSection[outCount] = i;
                compactedQuads[outCount] = sectionQuads[i];
                outCount++;
            }
        }

        int[] emittedQuadsPerSection = new int[tileSections];
        for (int meshletId = 0; meshletId < totalMeshlets; meshletId++) {
            int lo = 0;
            int hi = outCount;
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                if (meshletEnd[mid] <= meshletId) {
                    lo = mid + 1;
                } else {
                    hi = mid;
                }
            }
            assertTrue(lo < outCount);
            int prevEnd = (lo == 0) ? 0 : meshletEnd[lo - 1];
            int splitIndex = meshletId - prevEnd;
            int quadStart = splitIndex * budget;
            int emitted = Math.min(budget, compactedQuads[lo] - quadStart);
            assertTrue(emitted > 0 && emitted <= budget);
            assertEquals(emittedQuadsPerSection[compactedSection[lo]], quadStart);
            emittedQuadsPerSection[compactedSection[lo]] += emitted;
        }

        for (int i = 0; i < tileSections; i++) {
            assertEquals(survives[i] ? sectionQuads[i] : 0, emittedQuadsPerSection[i]);
        }
    }

    @Test
    void tableWithMaskSharesEntriesAndRemapWithoutRebuilding() {
        GpuTerrainDrawTable.Builder builder = new GpuTerrainDrawTable.Builder();
        int dict = builder.dictIndexFor(7, 2);
        builder.add(sectionEntry(36, 0, dict, 0, 0, 0));
        GpuTerrainDrawTable.Table base = builder.build();

        int[] latticeWords = new int[GpuTerrainDrawTable.MASK_HEADER_WORDS + 1];
        latticeWords[3] = 4;
        latticeWords[4] = 4;
        latticeWords[5] = 4;
        GpuTerrainDrawTable.Table updated = base.withMask(latticeWords, 99L, 0, 0, 0, 4, 4, 4);

        assertSame(base.entries, updated.entries);
        assertSame(base.remap, updated.remap);
        assertSame(latticeWords, updated.maskWords);
        assertTrue(updated.hasLatticeMask());
        assertEquals(99L, updated.maskGeneration);
    }
}
