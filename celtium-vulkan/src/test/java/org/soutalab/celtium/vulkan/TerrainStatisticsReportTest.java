package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.LongBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

import org.embeddedt.embeddium.impl.render.chunk.compile.sorting.QuadPrimitiveType;
import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;
import org.embeddedt.embeddium.impl.render.chunk.vertex.format.ChunkMeshFormats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pipeline-statistics instrument's own arithmetic (item 359), without a device: the statistic mask, the
 * readback layout and decode, the derived ratios the D24 ruling reads, and the two printed forms. The GPU
 * side - whether the driver reports 4 or 6 invocations per quad - is a leg question; what is pinned here is
 * that the numbers a leg prints are the numbers the pool returned.
 */
class TerrainStatisticsReportTest {

    private static TerrainRenderPass pass(String name) {
        return TerrainRenderPass.builder()
                .name(name)
                .pipelineState(new TerrainRenderPass.PipelineState() {
                    @Override public void setup() { }
                    @Override public void clear() { }
                })
                .vertexType(ChunkMeshFormats.COMPACT)
                .primitiveType(QuadPrimitiveType.TRIANGULATED)
                .build();
    }

    private static long[] totals(long iaV, long iaP, long vsi, long clipIn, long clipOut, long fs,
            long draws, long sections, long charge) {
        long[] v = new long[TerrainStatisticsReport.VALUES];
        v[TerrainStatisticsReport.IA_VERTICES] = iaV;
        v[TerrainStatisticsReport.IA_PRIMITIVES] = iaP;
        v[TerrainStatisticsReport.VS_INVOCATIONS] = vsi;
        v[TerrainStatisticsReport.CLIPPING_INVOCATIONS] = clipIn;
        v[TerrainStatisticsReport.CLIPPING_PRIMITIVES] = clipOut;
        v[TerrainStatisticsReport.FS_INVOCATIONS] = fs;
        v[TerrainStatisticsReport.DRAWS] = draws;
        v[TerrainStatisticsReport.SECTIONS] = sections;
        v[TerrainStatisticsReport.BUDGET_CHARGE] = charge;
        return v;
    }

    @Test
    void theMaskIsExactlyTheSixFeatureFreeStatistics() {
        // 1 | 2 | 4 | 32 | 64 | 128: IA vertices, IA primitives, VS invocations, clipping in, clipping out, FS.
        assertEquals(231, TerrainStatisticsReport.STATISTICS_MASK);
        assertEquals(6, TerrainStatisticsReport.COUNTERS);
    }

    @Test
    void theReadbackLayoutIsSixCountersPlusAvailabilityAtEightBytesEach() {
        assertEquals(7, TerrainStatisticsReport.RESULT_LONGS_PER_QUERY);
        assertEquals(56L, TerrainStatisticsReport.RESULT_STRIDE_BYTES);
    }

    @Test
    void decodeReadsTheCountersAndRefusesAnUnavailableQuery() {
        LongBuffer data = LongBuffer.allocate(2 * TerrainStatisticsReport.RESULT_LONGS_PER_QUERY);
        // Query 0: available, counters in mask order.
        data.put(0, 600L);
        data.put(1, 200L);
        data.put(2, 400L);
        data.put(3, 190L);
        data.put(4, 180L);
        data.put(5, 5000L);
        data.put(6, 1L);
        // Query 1: availability 0 - the sample has not landed and must not decode as zeros.
        long[] decoded = TerrainStatisticsReport.decode(data, 0);
        assertEquals(600L, decoded[TerrainStatisticsReport.IA_VERTICES]);
        assertEquals(200L, decoded[TerrainStatisticsReport.IA_PRIMITIVES]);
        assertEquals(400L, decoded[TerrainStatisticsReport.VS_INVOCATIONS]);
        assertEquals(190L, decoded[TerrainStatisticsReport.CLIPPING_INVOCATIONS]);
        assertEquals(180L, decoded[TerrainStatisticsReport.CLIPPING_PRIMITIVES]);
        assertEquals(5000L, decoded[TerrainStatisticsReport.FS_INVOCATIONS]);
        assertNull(TerrainStatisticsReport.decode(data, 1));
    }

    @Test
    void vsiPerQuadReadsFourOrSixAndNothingBetween() {
        // 200 primitives are 100 quads: 400 invocations is the reused-vertex reading, 600 the unreused one.
        assertEquals(4.0, TerrainStatisticsReport.vsiPerQuad(totals(600, 200, 400, 0, 0, 0, 0, 0, 400)), 1e-9);
        assertEquals(6.0, TerrainStatisticsReport.vsiPerQuad(totals(600, 200, 600, 0, 0, 0, 0, 0, 400)), 1e-9);
        assertEquals(0.0, TerrainStatisticsReport.vsiPerQuad(totals(0, 0, 0, 0, 0, 0, 0, 0, 0)), 1e-9);
    }

    @Test
    void drawsPerSectionIsOneForTheSingleRunShape() {
        assertEquals(1.0, TerrainStatisticsReport.drawsPerSection(28, 28), 1e-9);
        assertEquals(3.0, TerrainStatisticsReport.drawsPerSection(30, 10), 1e-9);
        assertEquals(0.0, TerrainStatisticsReport.drawsPerSection(5, 0), 1e-9);
    }

    @Test
    void culledFractionIsTheClippingDropOverAssembledPrimitives() {
        assertEquals(0.1, TerrainStatisticsReport.culledFraction(totals(600, 200, 400, 100, 80, 0, 0, 0, 0)), 1e-9);
        assertEquals(0.0, TerrainStatisticsReport.culledFraction(totals(0, 0, 0, 0, 0, 0, 0, 0, 0)), 1e-9);
    }

    @Test
    void theWindowLineCarriesTheCountersAndTheDerivedRatios() {
        Map<TerrainRenderPass, long[]> perPass = new LinkedHashMap<>();
        perPass.put(pass("solid"), totals(120000, 40000, 80000, 39000, 38000, 900000, 600, 200, 80000));
        String line = TerrainStatisticsReport.formatWindow(200L, perPass);
        assertTrue(line.contains("frames=200"), line);
        assertTrue(line.contains("solid[vsi=80000 iaV=120000 iaP=40000"), line);
        assertTrue(line.contains("draws=600 sections=200 charge=80000"), line);
        assertTrue(line.contains("vsiPerQuad=4.000"), line);
        assertTrue(line.contains("drawsPerSection=3.000"), line);
    }

    @Test
    void theSummaryNamesAnUnsupportedInstrumentInsteadOfGoingSilent() {
        String off = TerrainStatisticsReport.formatSummary(false, "device", 0, 0, 0, 0, Map.of());
        assertEquals("terrainStats=[supported=false reason=device]", off);
        Map<TerrainRenderPass, long[]> totals = new LinkedHashMap<>();
        totals.put(pass("solid"), totals(120000, 40000, 80000, 39000, 38000, 900000, 600, 200, 80000));
        String on = TerrainStatisticsReport.formatSummary(true, "", 612, 0, 0, 0, totals);
        assertTrue(on.contains("terrainStats=[supported=true samples=612 droppedPasses=0 droppedSamples=0 readFailures=0"), on);
        assertTrue(on.contains("solid[vsi=80000"), on);
    }
}
