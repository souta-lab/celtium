package org.soutalab.celtium.vulkan;

import java.util.concurrent.atomic.AtomicLong;

/**
 * The null-renderer measurement mode (Stage 0 of the A-to-B overhaul): with
 * {@code -Dceltium.vulkan.nullRenderer=true}, every draw submission into the backend is
 * accepted and dropped - the terrain passes refuse at the same point as the
 * {@code celtium.vulkan.refuseTerrainPass} isolation switch, and immediate / retained
 * batches return "accepted" without recording or submitting any GPU work.
 * <p>
 * What still runs is everything that is not draw submission: the game tick, chunk
 * building and meshing, geometry and texture uploads, the frame target bracket and the
 * swapchain present (of whatever the target already holds). The presented frame rate
 * under this mode is therefore the vanilla supply floor {@code T_floor} of the A-to-B
 * plan - the frame time no renderer on this game can go below, because the game itself
 * spends it before and around the renderer. Stage gates compare renderer frame times
 * against this floor instead of against zero.
 * <p>
 * The mode is a measurement instrument only: it is off by default, the flag is read
 * once at class load, and with the flag off every hook is a single static-final boolean
 * test on paths that already carry isolation switches. The picture under the mode is
 * meaningless by construction (stale frame target); only the pacing is data.
 */
public final class NullRenderer {

    /** Whether the null-renderer mode is on for this process. */
    public static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.nullRenderer");

    private static final AtomicLong TERRAIN_PASSES_DROPPED = new AtomicLong();
    private static final AtomicLong IMMEDIATE_BATCHES_DROPPED = new AtomicLong();
    private static final AtomicLong RETAINED_BATCHES_DROPPED = new AtomicLong();
    private static final AtomicLong VERTICES_DROPPED = new AtomicLong();
    private static volatile boolean announced;

    private NullRenderer() {
    }

    /** Counts one refused terrain pass (called from the pass entry, once per pass). */
    public static void noteTerrainPassDropped() {
        announce();
        TERRAIN_PASSES_DROPPED.incrementAndGet();
        reportPeriodically();
    }

    /** Counts one accepted-and-dropped immediate batch of {@code vertexCount} vertices. */
    public static void noteImmediateBatchDropped(int vertexCount) {
        announce();
        IMMEDIATE_BATCHES_DROPPED.incrementAndGet();
        VERTICES_DROPPED.addAndGet(Math.max(0, vertexCount));
        reportPeriodically();
    }

    /** Counts one accepted-and-dropped retained-geometry batch of {@code vertexCount} vertices. */
    public static void noteRetainedBatchDropped(int vertexCount) {
        announce();
        RETAINED_BATCHES_DROPPED.incrementAndGet();
        VERTICES_DROPPED.addAndGet(Math.max(0, vertexCount));
        reportPeriodically();
    }

    /** {@return the {@code CELTIUM_NULL_RENDERER} counter line for this process}. */
    public static String summaryLine() {
        return "CELTIUM_NULL_RENDERER terrainPassesDropped=" + TERRAIN_PASSES_DROPPED.get()
                + " immediateBatchesDropped=" + IMMEDIATE_BATCHES_DROPPED.get()
                + " retainedBatchesDropped=" + RETAINED_BATCHES_DROPPED.get()
                + " verticesDropped=" + VERTICES_DROPPED.get();
    }

    private static void announce() {
        if (!announced) {
            announced = true;
            System.out.println("[Celtium] CELTIUM_NULL_RENDERER active: draw submission is "
                    + "nullified for the vanilla supply floor (T_floor) measurement; tick, chunk "
                    + "building, uploads and present still run, and the presented picture is stale "
                    + "by construction.");
        }
    }

    /** Prints the counter line every 4096 dropped submissions, so a leg log proves the mode was live throughout. */
    private static void reportPeriodically() {
        long total = TERRAIN_PASSES_DROPPED.get() + IMMEDIATE_BATCHES_DROPPED.get()
                + RETAINED_BATCHES_DROPPED.get();
        if (total % 4096L == 0L) {
            System.out.println("[Celtium] " + summaryLine());
        }
    }
}
