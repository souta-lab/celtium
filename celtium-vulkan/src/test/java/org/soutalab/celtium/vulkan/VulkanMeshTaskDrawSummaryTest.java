package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the stage-2 mesh census line (item 652,
 * alpha-mesh-next3). The line is the proof leg's only window into why the
 * mesh path did or did not draw, so every gate reason must be present and
 * every count must land under its own name — the previous periodic line
 * named only six of the eleven reasons and never printed at all when no
 * pass had succeeded yet.
 */
class VulkanMeshTaskDrawSummaryTest {

    private static long[] counts() {
        long[] counts = new long[VulkanMeshTaskDraw.GateReason.values().length];
        counts[VulkanMeshTaskDraw.GateReason.NONE.ordinal()] = 7L;
        counts[VulkanMeshTaskDraw.GateReason.NO_MESH_SHADER.ordinal()] = 1L;
        counts[VulkanMeshTaskDraw.GateReason.NO_MESH_LIMITS.ordinal()] = 2L;
        counts[VulkanMeshTaskDraw.GateReason.BUDGET_ACTIVE.ordinal()] = 3L;
        counts[VulkanMeshTaskDraw.GateReason.NO_PIPELINE.ordinal()] = 4L;
        counts[VulkanMeshTaskDraw.GateReason.TABLE_NOT_BOUND.ordinal()] = 5L;
        counts[VulkanMeshTaskDraw.GateReason.DIRECTORY_NOT_BUILT.ordinal()] = 6L;
        counts[VulkanMeshTaskDraw.GateReason.INELIGIBLE_SECTIONS.ordinal()] = 8L;
        counts[VulkanMeshTaskDraw.GateReason.NO_SECTIONS.ordinal()] = 9L;
        counts[VulkanMeshTaskDraw.GateReason.OVER_TASK_WORKGROUP_LIMIT.ordinal()] = 10L;
        counts[VulkanMeshTaskDraw.GateReason.DISABLED.ordinal()] = 11L;
        return counts;
    }

    @Test
    void summaryNamesEveryGateReasonWithItsOwnCount() {
        String line = VulkanMeshTaskDraw.summaryLine(7L, 1234L, counts());
        assertTrue(line.startsWith("frames=7 sections=1234 "), line);
        assertTrue(line.contains("directoryNotBuilt=6"), line);
        assertTrue(line.contains("ineligibleSections=8"), line);
        assertTrue(line.contains("budgetActive=3"), line);
        assertTrue(line.contains("noMeshShader=1"), line);
        assertTrue(line.contains("noMeshLimits=2"), line);
        assertTrue(line.contains("noPipeline=4"), line);
        assertTrue(line.contains("tableNotBound=5"), line);
        assertTrue(line.contains("noSections=9"), line);
        assertTrue(line.contains("overLimit=10"), line);
        assertTrue(line.contains("disabled=11"), line);
    }

    @Test
    void neverSucceededLegStillProducesAReadableLine() {
        long[] counts = new long[VulkanMeshTaskDraw.GateReason.values().length];
        counts[VulkanMeshTaskDraw.GateReason.NO_PIPELINE.ordinal()] = 42L;
        String line = VulkanMeshTaskDraw.summaryLine(0L, 0L, counts);
        assertTrue(line.startsWith("frames=0 sections=0 "), line);
        assertTrue(line.contains("noPipeline=42"), line);
    }

    @Test
    void shortOrNullCountsReadAsZero() {
        assertEquals(VulkanMeshTaskDraw.summaryLine(0L, 0L, new long[0]),
                VulkanMeshTaskDraw.summaryLine(0L, 0L, null));
        assertTrue(VulkanMeshTaskDraw.summaryLine(1L, 2L, new long[1])
                .contains("disabled=0"));
    }
}
