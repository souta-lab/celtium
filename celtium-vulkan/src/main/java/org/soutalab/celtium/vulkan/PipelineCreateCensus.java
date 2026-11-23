package org.soutalab.celtium.vulkan;

import org.lwjgl.vulkan.VkComputePipelineCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo;

import java.nio.LongBuffer;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.lwjgl.vulkan.VK10.VK_NULL_HANDLE;
import static org.lwjgl.vulkan.VK10.VK_SUCCESS;
import static org.lwjgl.vulkan.VK10.vkCreateComputePipelines;
import static org.lwjgl.vulkan.VK10.vkCreateGraphicsPipelines;

/**
 * D3's separation instrument (optim-plan-1): how much of the frame tail is pipeline compilation?
 * <p>
 * The graphics route through {@link VulkanContext#createGraphicsPipelines} was already timed in
 * aggregate (one running total for every site together) and printed per creation, but three holes
 * kept a compile storm inseparable from streaming stalls in a log:
 * <ul>
 *   <li>no per-site breakdown - a burst could not be attributed to terrain, a mod program, the
 *       post chain or an immediate family;</li>
 *   <li>the terrain mod-program constructor's direct {@code vkCreateGraphicsPipelines} call
 *       (null cache) bypassed the wrapper, so its creations were in no total at all;</li>
 *   <li>compute pipelines ({@code vkCreateComputePipelines}) had no wrapper and no counting
 *       anywhere - the GPU terrain culling and opaque-copy pipelines were invisible.</li>
 * </ul>
 * This census closes the three holes behind one switch, {@code -Dceltium.vulkan.pipelineCreateCensus=true}
 * (default off). It counts calls and created pipelines per (kind, site), sums and maximises the
 * native call's duration, and separately accumulates the slow creations (at or over
 * {@code celtium.vulkan.pipelineCreateCensusSlowMs}, default 10 ms) - the slow subtotal is the
 * number a leg reads against the frame tail: if the tail's excess over the median is not covered
 * by {@code slowMs} in the same window, compilation is not the tail.
 * <p>
 * Output is deliberately summary-only, on a geometric cadence (after total calls 1, 2, 4, 8, ...):
 * a per-creation line under a fresh tag would be cut by {@link CeltiumLogLimiter} after 16 lines -
 * exactly the storm this instrument exists to measure would erase its own evidence. The same
 * summary string is appended to {@link VulkanContext#pipelineCreationSummary()} while enabled, so
 * the periodic diagnostics and slow-creation lines carry it too. As everywhere, a leg that reads
 * {@code [Celtium]} census lines still passes {@code -Dceltium.debug.logLinesPerTag=100000}.
 * <p>
 * While disabled, every entry point is the original call with the original arguments (the bypass
 * and compute helpers call the native create directly, with the null cache they always used), so
 * the off path creates pipelines exactly as before; the only cost is one static-boolean read.
 */
final class PipelineCreateCensus {
    static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.pipelineCreateCensus");
    /** A creation at or over this duration counts towards the slow subtotal. */
    static final long SLOW_NANOS =
            Long.getLong("celtium.vulkan.pipelineCreateCensusSlowMs", 10L).longValue() * 1_000_000L;

    static final String KIND_GRAPHICS = "graphics";
    static final String KIND_COMPUTE = "compute";

    private static final Ledger LEDGER = new Ledger();

    private PipelineCreateCensus() {
    }

    /** Records one graphics creation made through the {@link VulkanContext} wrapper. Enabled only. */
    static void noteGraphics(String site, int pipelines, long tookNanos) {
        if (LEDGER.note(KIND_GRAPHICS, site, pipelines, tookNanos, SLOW_NANOS)) {
            System.out.println("[Celtium] " + LEDGER.summaryLine());
        }
    }

    /**
     * {@code vkCreateGraphicsPipelines} with a null cache - the shape the terrain mod-program
     * constructor has always used - timed and counted under {@code site} while enabled. The cache
     * behaviour is intentionally unchanged: routing this call through the device pipeline cache
     * is D3's candidate fix, not part of its instrument.
     */
    static int createGraphicsUncached(VkDevice device, VkGraphicsPipelineCreateInfo.Buffer info,
            LongBuffer pipelines, String site) {
        if (!ENABLED) {
            return vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, info, null, pipelines);
        }
        long start = System.nanoTime();
        int result = vkCreateGraphicsPipelines(device, VK_NULL_HANDLE, info, null, pipelines);
        long took = System.nanoTime() - start;
        int created = result == VK_SUCCESS ? Math.min(info.remaining(), pipelines.remaining()) : 0;
        if (LEDGER.note(KIND_GRAPHICS, site, created, took, SLOW_NANOS)) {
            System.out.println("[Celtium] " + LEDGER.summaryLine());
        }
        return result;
    }

    /**
     * {@code vkCreateComputePipelines} with a null cache - the only shape compute creation has in
     * this renderer - timed and counted under {@code site} while enabled; the plain call while not.
     */
    static int createComputeUncached(VkDevice device, VkComputePipelineCreateInfo.Buffer info,
            LongBuffer pipelines, String site) {
        if (!ENABLED) {
            return vkCreateComputePipelines(device, VK_NULL_HANDLE, info, null, pipelines);
        }
        long start = System.nanoTime();
        int result = vkCreateComputePipelines(device, VK_NULL_HANDLE, info, null, pipelines);
        long took = System.nanoTime() - start;
        int created = result == VK_SUCCESS ? Math.min(info.remaining(), pipelines.remaining()) : 0;
        if (LEDGER.note(KIND_COMPUTE, site, created, took, SLOW_NANOS)) {
            System.out.println("[Celtium] " + LEDGER.summaryLine());
        }
        return result;
    }

    /** The running per-site breakdown, for embedding in other summaries. */
    static String summaryLine() {
        return LEDGER.summaryLine();
    }

    /** The counting itself, kept apart from the Vulkan calls so tests can drive it directly. */
    static final class Ledger {
        private final Map<String, Site> sites = new ConcurrentHashMap<String, Site>();
        private final AtomicLong totalCalls = new AtomicLong();

        /**
         * Files one creation call under {@code kind:site}.
         *
         * @return true when the caller should print the summary: the total call count just
         *         reached a power of two, so a whole session prints O(log calls) lines however
         *         hard a storm gets.
         */
        boolean note(String kind, String site, int pipelines, long tookNanos, long slowNanos) {
            Site stats = sites.get(kind + ':' + site);
            if (stats == null) {
                Site fresh = new Site();
                stats = sites.putIfAbsent(kind + ':' + site, fresh);
                if (stats == null) {
                    stats = fresh;
                }
            }
            stats.calls.incrementAndGet();
            stats.pipelines.addAndGet(pipelines);
            stats.totalNanos.addAndGet(tookNanos);
            long worst = stats.worstNanos.get();
            while (tookNanos > worst && !stats.worstNanos.compareAndSet(worst, tookNanos)) {
                worst = stats.worstNanos.get();
            }
            if (tookNanos >= slowNanos) {
                stats.slowCalls.incrementAndGet();
                stats.slowNanos.addAndGet(tookNanos);
            }
            long total = totalCalls.incrementAndGet();
            return (total & (total - 1L)) == 0L;
        }

        long totalCalls() {
            return totalCalls.get();
        }

        /** One line: grand totals, then every site's split in name order. */
        String summaryLine() {
            TreeMap<String, Site> sorted = new TreeMap<String, Site>(sites);
            long calls = 0L;
            long pipelines = 0L;
            long totalNanos = 0L;
            long worstNanos = 0L;
            long slowCalls = 0L;
            long slowNanos = 0L;
            StringBuilder line = new StringBuilder(256);
            for (Map.Entry<String, Site> entry : sorted.entrySet()) {
                Site stats = entry.getValue();
                calls += stats.calls.get();
                pipelines += stats.pipelines.get();
                totalNanos += stats.totalNanos.get();
                worstNanos = Math.max(worstNanos, stats.worstNanos.get());
                slowCalls += stats.slowCalls.get();
                slowNanos += stats.slowNanos.get();
            }
            line.append("CELTIUM_PIPELINE_CREATE_CENSUS calls=").append(calls)
                    .append(" pipelines=").append(pipelines)
                    .append(" totalMs=").append(totalNanos / 1_000_000L)
                    .append(" worstMs=").append(worstNanos / 1_000_000L)
                    .append(" slowCalls=").append(slowCalls)
                    .append(" slowMs=").append(slowNanos / 1_000_000L)
                    .append(" sites=[");
            boolean first = true;
            for (Map.Entry<String, Site> entry : sorted.entrySet()) {
                Site stats = entry.getValue();
                if (!first) {
                    line.append(' ');
                }
                first = false;
                line.append(entry.getKey())
                        .append("{calls=").append(stats.calls.get())
                        .append(" pipelines=").append(stats.pipelines.get())
                        .append(" ms=").append(stats.totalNanos.get() / 1_000_000L)
                        .append(" worstMs=").append(stats.worstNanos.get() / 1_000_000L)
                        .append(" slow=").append(stats.slowCalls.get())
                        .append('/').append(stats.slowNanos.get() / 1_000_000L).append("ms}");
            }
            return line.append(']').toString();
        }
    }

    private static final class Site {
        final AtomicLong calls = new AtomicLong();
        final AtomicLong pipelines = new AtomicLong();
        final AtomicLong totalNanos = new AtomicLong();
        final AtomicLong worstNanos = new AtomicLong();
        final AtomicLong slowCalls = new AtomicLong();
        final AtomicLong slowNanos = new AtomicLong();
    }
}
