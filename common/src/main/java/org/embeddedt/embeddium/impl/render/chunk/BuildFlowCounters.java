package org.embeddedt.embeddium.impl.render.chunk;

/**
 * Why the build queues stay populated in a still scene: the counters a leg needs to tell a throttled drain
 * (dispatch budget keeping world-load's tail alive for seconds) from a loop that keeps re-marking sections.
 * <p>
 * Everything here is a plain incremented long: the points are on scheduling and result paths, not per-vertex work, and
 * nothing is printed unless {@code -Dceltium.debug.buildFlow=true} asks for it.
 */
public final class BuildFlowCounters {
    /** Sections scheduled for a rebuild by the public scheduleRebuild entry (block updates, unloads, ...). */
    public static long scheduledRebuild;
    /** Sections scheduled off the render thread. */
    public static long scheduledRebuildAsync;
    /** Whole-scene rebuild requests. */
    public static long scheduledRebuildAll;
    /** Sorts scheduled by the translucency tick. */
    public static long scheduledSorts;
    /** Build outputs that reached the upload path. */
    public static long buildOutputs;
    /** Sort outputs that reached the upload path. */
    public static long sortOutputs;
    /** Frames (terrain update calls) observed, so the line can be per-frame. */
    public static long updateFrames;
    /** Calls to scheduleRebuildForBlockArea, and the sampled classification of who made them. */
    public static long areaCalls;
    public static long areaFromLight;
    public static long areaFromSetBlock;
    public static long areaFromChunkData;
    public static long areaFromChunkLightInit;
    public static long areaFromOther;
    /** Rebuild requests dropped because the same section was already scheduled this frame. */
    public static long scheduleDeduped;

    private BuildFlowCounters() {
    }

    public static String snapshot() {
        return "updateFrames=" + updateFrames
                + " scheduleRebuild=" + scheduledRebuild
                + " scheduleRebuildAsync=" + scheduledRebuildAsync
                + " scheduleRebuildAll=" + scheduledRebuildAll
                + " scheduledSorts=" + scheduledSorts
                + " buildOutputs=" + buildOutputs
                + " sortOutputs=" + sortOutputs
                + " areaCalls=" + areaCalls
                + " scheduleDeduped=" + scheduleDeduped
                + " area[light=" + areaFromLight + " setBlock=" + areaFromSetBlock
                + " chunkData=" + areaFromChunkData + " chunkLightInit=" + areaFromChunkLightInit
                + " other=" + areaFromOther + "]";
    }
}
