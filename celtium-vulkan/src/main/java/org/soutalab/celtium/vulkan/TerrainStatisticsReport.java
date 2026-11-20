package org.soutalab.celtium.vulkan;

import java.nio.LongBuffer;
import java.util.Locale;
import java.util.Map;

import org.embeddedt.embeddium.impl.render.chunk.terrain.TerrainRenderPass;

import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_CLIPPING_INVOCATIONS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_CLIPPING_PRIMITIVES_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_FRAGMENT_SHADER_INVOCATIONS_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_PRIMITIVES_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_VERTICES_BIT;
import static org.lwjgl.vulkan.VK10.VK_QUERY_PIPELINE_STATISTIC_VERTEX_SHADER_INVOCATIONS_BIT;

/**
 * The pipeline-statistics instrument for the terrain passes (item 359): what the GPU itself counted while a
 * pass drew - input-assembly vertices and primitives, vertex-shader invocations, the clipping pair and
 * fragment-shader invocations - read from a {@code VK_QUERY_TYPE_PIPELINE_STATISTICS} pool that brackets only
 * the section draw loop of {@code recordRenderPass}.
 *
 * <p>The instrument exists to give item D24 (the mesher's per-direction buckets) a measuring stick: the
 * vertex-shader invocation count of a pass is the work the bucketing removes, and the budget's charge
 * ({@link TerrainFrameBudget#verticesOf}, four vertices per quad on an unsorted pass) is the identity the
 * count is checked against - {@code vsi / quad} must read 4 or 6, never anything between.
 *
 * <p>This class owns the instrument's shape the way {@link GpuPassTimingReport} owns the pass timings':
 * the statistic selection, the result layout, the derived ratios and both printed forms. The backend keeps
 * no format knowledge of its own. Collection is opt-in
 * ({@code -Dceltium.vulkan.terrainStatistics=true}, default off): with the switch off no query is begun or
 * ended, so the shipping path records exactly what it recorded before. Two diagnostic switches sit beside
 * it: {@code -Dceltium.vulkan.forceNoTerrainStatistics=true} refuses the pool itself (the device-without-the
 * feature path, measurable on hardware that has it), and
 * {@code -Dceltium.vulkan.forceNoTerrainCulling=true} turns the batch assembler's camera-facing bucket
 * selection off while leaving the pipeline's hardware back-face culling on - the control arm that prices
 * the bucketing. Neither diagnostic switch is for production use.
 */
public final class TerrainStatisticsReport {

    /** Index of the input-assembly vertex count in a decoded counter set. */
    public static final int IA_VERTICES = 0;
    /** Index of the input-assembly primitive count in a decoded counter set. */
    public static final int IA_PRIMITIVES = 1;
    /** Index of the vertex-shader invocation count in a decoded counter set - D24's primary quantity. */
    public static final int VS_INVOCATIONS = 2;
    /** Index of the primitives that entered the clipping stage in a decoded counter set. */
    public static final int CLIPPING_INVOCATIONS = 3;
    /** Index of the primitives that left the clipping stage in a decoded counter set. */
    public static final int CLIPPING_PRIMITIVES = 4;
    /** Index of the fragment-shader invocation count in a decoded counter set. */
    public static final int FS_INVOCATIONS = 5;
    /** How many pipeline statistics one query reads. */
    public static final int COUNTERS = 6;

    /** Index of the pass's logical draw-command count in an accumulated value set. */
    public static final int DRAWS = 6;
    /** Index of the pass's drawn-section count in an accumulated value set. */
    public static final int SECTIONS = 7;
    /** Index of the pass's unsorted budget charge (vertices) in an accumulated value set. */
    public static final int BUDGET_CHARGE = 8;
    /** How many longs one accumulated value set holds: the counters plus draws, sections and charge. */
    public static final int VALUES = 9;

    /**
     * The statistic bits the pool counts. Only the six that need no extra device feature: the geometry and
     * tessellation bits would make pool creation invalid, because celtium enables neither feature, and the
     * compute bit has nothing to count inside a terrain render pass.
     */
    public static final int STATISTICS_MASK = VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_VERTICES_BIT
            | VK_QUERY_PIPELINE_STATISTIC_INPUT_ASSEMBLY_PRIMITIVES_BIT
            | VK_QUERY_PIPELINE_STATISTIC_VERTEX_SHADER_INVOCATIONS_BIT
            | VK_QUERY_PIPELINE_STATISTIC_CLIPPING_INVOCATIONS_BIT
            | VK_QUERY_PIPELINE_STATISTIC_CLIPPING_PRIMITIVES_BIT
            | VK_QUERY_PIPELINE_STATISTIC_FRAGMENT_SHADER_INVOCATIONS_BIT;

    /**
     * Longs per query in the readback: the counters plus one availability long. The read is 64-bit (the
     * timestamp pool's proven shape); the stride and the flags live here so a leg that reports a VUID about
     * the result width changes exactly one place.
     */
    public static final int RESULT_LONGS_PER_QUERY = COUNTERS + 1;
    /** Byte stride between two queries' results in the readback. */
    public static final long RESULT_STRIDE_BYTES = RESULT_LONGS_PER_QUERY * (long) Long.BYTES;

    /** How often the window line is printed, in frames. */
    private static final int EVERY_FRAME = 200;

    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.terrainStatistics");
    private static final boolean FORCE_DISABLED = Boolean.getBoolean("celtium.vulkan.forceNoTerrainStatistics");
    private static final boolean FORCE_NO_CULLING = Boolean.getBoolean("celtium.vulkan.forceNoTerrainCulling");

    private TerrainStatisticsReport() {
    }

    /** {@return whether the instrument collects}, which is also when its queries are recorded. */
    public static boolean enabled() {
        return ENABLED;
    }

    /** {@return whether the pool itself is refused} - the no-feature device path, on hardware that has it. */
    public static boolean forceDisabled() {
        return FORCE_DISABLED;
    }

    /**
     * {@return whether the assembler's facing-bucket selection is forced off} (diagnostic only): the batch
     * is then built as one run per section while the pipeline still culls back faces in hardware, so the
     * picture is unchanged and only the offered work moves - the D24 control arm.
     */
    public static boolean forceNoCulling() {
        return FORCE_NO_CULLING;
    }

    /** {@return whether a window line is due at this frame}. */
    public static boolean windowDue(long frameId) {
        return ENABLED && frameId > 0L && frameId % EVERY_FRAME == 0L;
    }

    /**
     * Decodes one query's counters out of a 64-bit-with-availability readback.
     *
     * @param data       the readback buffer, laid out as {@link #RESULT_LONGS_PER_QUERY} longs per query
     * @param queryIndex which query in the buffer to decode
     * @return the {@link #COUNTERS} counter values, or {@code null} when the query's availability long is 0 -
     *         the sample has not landed yet and must not be read as zeros
     */
    public static long[] decode(LongBuffer data, int queryIndex) {
        int base = queryIndex * RESULT_LONGS_PER_QUERY;
        if (data.get(base + COUNTERS) == 0L) {
            return null;
        }
        long[] values = new long[COUNTERS];
        for (int i = 0; i < COUNTERS; i++) {
            values[i] = data.get(base + i);
        }
        return values;
    }

    /**
     * {@return vertex-shader invocations per quad} for an accumulated value set. A quad is two primitives of
     * the shared index pattern, and the honest readings are exactly 4 (the post-transform cache reused the
     * shared vertices) or 6 (it did not); anything else indicts the decode, not the terrain.
     */
    public static double vsiPerQuad(long[] totals) {
        long quads = totals[IA_PRIMITIVES] / 2L;
        return quads <= 0L ? 0.0 : (double) totals[VS_INVOCATIONS] / (double) quads;
    }

    /** {@return logical draw commands per drawn section}; 1.0 is the one-run-per-section shape. */
    public static double drawsPerSection(long draws, long sections) {
        return sections <= 0L ? 0.0 : (double) draws / (double) sections;
    }

    /**
     * {@return the share of assembled primitives the clipping stage did not pass on}. Whether this is the
     * facing-cull rate is a hypothesis the S0/S1 difference decides (design H1); the number is reported
     * either way and never named a cull rate on its own.
     */
    public static double culledFraction(long[] totals) {
        return totals[IA_PRIMITIVES] <= 0L ? 0.0
                : (double) (totals[CLIPPING_INVOCATIONS] - totals[CLIPPING_PRIMITIVES])
                        / (double) totals[IA_PRIMITIVES];
    }

    /** Prints the window line when due; the caller clears its window when this returns true. */
    public static boolean printWindowIfDue(long frameId, long frames, Map<TerrainRenderPass, long[]> perPass) {
        if (!windowDue(frameId) || perPass.isEmpty()) {
            return false;
        }
        System.out.println("[Celtium] CELTIUM_TERRAIN_STATS frame=" + frameId + " " + formatWindow(frames, perPass));
        return true;
    }

    /** The window line's body: per-pass totals over the window plus the derived ratios. */
    public static String formatWindow(long frames, Map<TerrainRenderPass, long[]> perPass) {
        StringBuilder line = new StringBuilder(256);
        line.append("frames=").append(frames);
        for (Map.Entry<TerrainRenderPass, long[]> entry : perPass.entrySet()) {
            long[] v = entry.getValue();
            line.append(' ').append(entry.getKey().name()).append('[')
                    .append("vsi=").append(v[VS_INVOCATIONS])
                    .append(" iaV=").append(v[IA_VERTICES])
                    .append(" iaP=").append(v[IA_PRIMITIVES])
                    .append(" clipIn=").append(v[CLIPPING_INVOCATIONS])
                    .append(" clipOut=").append(v[CLIPPING_PRIMITIVES])
                    .append(" fs=").append(v[FS_INVOCATIONS])
                    .append(" draws=").append(v[DRAWS])
                    .append(" sections=").append(v[SECTIONS])
                    .append(" charge=").append(v[BUDGET_CHARGE])
                    .append(" vsiPerFrame=").append(perFrame(v[VS_INVOCATIONS], frames))
                    .append(" quadsPerFrame=").append(perFrame(v[IA_PRIMITIVES] / 2L, frames))
                    .append(" vsiPerQuad=").append(ratio(vsiPerQuad(v)))
                    .append(" drawsPerSection=").append(ratio(drawsPerSection(v[DRAWS], v[SECTIONS])))
                    .append(" culledFraction=").append(ratio(culledFraction(v)))
                    .append(']');
        }
        return line.toString();
    }

    /**
     * The cumulative block for the backend's verification summary. The line is printed even when the
     * instrument cannot run - {@code supported=false} with the reason - so a silent instrument is never
     * read as "nothing was drawn".
     */
    public static String formatSummary(boolean supported, String reason, long samples, long droppedPasses,
            long droppedSamples, long readFailures, Map<TerrainRenderPass, long[]> totals) {
        if (!supported) {
            return "terrainStats=[supported=false reason=" + reason + "]";
        }
        StringBuilder line = new StringBuilder(192);
        line.append("terrainStats=[supported=true samples=").append(samples)
                .append(" droppedPasses=").append(droppedPasses)
                .append(" droppedSamples=").append(droppedSamples)
                .append(" readFailures=").append(readFailures);
        for (Map.Entry<TerrainRenderPass, long[]> entry : totals.entrySet()) {
            long[] v = entry.getValue();
            line.append(' ').append(entry.getKey().name()).append('[')
                    .append("vsi=").append(v[VS_INVOCATIONS])
                    .append(" iaP=").append(v[IA_PRIMITIVES])
                    .append(" clipIn=").append(v[CLIPPING_INVOCATIONS])
                    .append(" clipOut=").append(v[CLIPPING_PRIMITIVES])
                    .append(" fs=").append(v[FS_INVOCATIONS])
                    .append(" draws=").append(v[DRAWS])
                    .append(" sections=").append(v[SECTIONS])
                    .append(" charge=").append(v[BUDGET_CHARGE])
                    .append(']');
        }
        return line.append(']').toString();
    }

    private static String ratio(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String perFrame(long total, long frames) {
        return frames <= 0L ? "0.0" : String.format(Locale.ROOT, "%.1f", (double) total / (double) frames);
    }
}
