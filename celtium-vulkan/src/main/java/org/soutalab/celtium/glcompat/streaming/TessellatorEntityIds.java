package org.soutalab.celtium.glcompat.streaming;
/*
 * Converted natively into celtium from Actinium's GLSM (Angelica lineage).
 * Original: com.gtnewhorizons.angelica.glsm, Actinium-Dev/Actinium @ ce77eed9.
 * License: LGPL-3.0 (with Mesa MIT pockets where the original file notes them).
 * See THIRD_PARTY_NOTICES.md. The supplier backend SPI (RenderBackend /
 * BackendManager) was NOT carried over; execution bottoms out on celtium's
 * own DirectGlVulkanSink through DirectGlExecutor.
 */


import org.soutalab.celtium.glcompat.ITessellatorData;

import java.util.Arrays;

import static org.soutalab.celtium.glcompat.gtnh.bytebuf.MemoryUtilities.memPutLong;

/**
 * Entity, block entity and item ids of the vertices in the main Tessellator batch.
 *
 * <p>Ids only change at entity boundaries, so the batch is stored as runs: a start vertex plus the packed
 * ids that apply from there on. Runs are merged when a new id equals the previous one, which keeps the
 * expanded per-vertex array cheap to write at draw time.</p>
 */
public final class TessellatorEntityIds {

    private static Object owner;
    private static int runCount;
    private static int[] runStarts = new int[8];
    private static long[] runIds = new long[8];

    private TessellatorEntityIds() {}

    /**
     * Records that the packed ids changed while {@code previous} covered the vertices
     * before this point. The tessellator is passed explicitly: celtium has no global
     * tessellator singleton for this layer to consult, so the owner of the batch being
     * accumulated (the pack executor) names it.
     */
    public static void onIdsChanged(ITessellatorData data, long previous, long next) {
        if (!data.isDrawing() || data.getVertexCount() == 0) return;
        final Object key = data;
        final int start = data.getVertexCount();
        if (owner != key) {
            owner = key;
            runCount = 0;
        }
        if (runCount == 0) addRun(0, previous);
        if (runStarts[runCount - 1] == start) {
            runIds[runCount - 1] = next;
            if (runCount > 1 && runIds[runCount - 2] == next) runCount--;
        } else {
            addRun(start, next);
        }
    }

    /** Drops the tracked runs when the tessellator resets. */
    public static void onReset(Object tessellator) {
        if (tessellator != owner) return;
        owner = null;
        runCount = 0;
    }

    /** @return whether the given tessellator's queued vertices span more than one set of ids */
    public static boolean hasIdChanges(ITessellatorData tessellator) {
        return tessellator == owner && runCount > 1;
    }

    /** @return how many id runs cover the first {@code vertexCount} vertices of the given tessellator or buffer */
    public static int runsFor(Object tessellator, int vertexCount) {
        if (tessellator != owner) return 0;
        int runs = runCount;
        while (runs > 0 && runStarts[runs - 1] >= vertexCount) runs--;
        return runs;
    }

    static long firstRunIds() {
        return runIds[0];
    }

    /** Writes the packed ids of every vertex into {@code dst}, using {@code stride} bytes per vertex. */
    public static void write(long dst, int stride, int vertexCount, int runs) {
        for (int run = 0; run < runs; run++) {
            final int end = run + 1 < runs ? runStarts[run + 1] : vertexCount;
            final long ids = runIds[run];
            for (int vertex = runStarts[run]; vertex < end; vertex++) {
                memPutLong(dst + (long) vertex * stride, ids);
            }
        }
    }

    private static void addRun(int start, long ids) {
        if (runCount == runStarts.length) {
            runStarts = Arrays.copyOf(runStarts, runCount * 2);
            runIds = Arrays.copyOf(runIds, runCount * 2);
        }
        runStarts[runCount] = start;
        runIds[runCount] = ids;
        runCount++;
    }
}
