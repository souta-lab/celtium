package org.soutalab.celtium.vulkan;

/**
 * The {@code -Dceltium.vulkan.groupDecayCensus=true} per-frame series for QUEUE 632: one
 * {@code CELTIUM_GROUP_DECAY} line per world frame, carrying the frame's deltas of the counters that until now
 * existed only as run totals.
 * <p>
 * The measured shape this separates: on the f5-fixed scene the item frames' groups close correctly at the first
 * world frame (GROUP_BOUNDS, frame 4) but the closure rate decays from roughly 289 a frame to roughly 9 a frame,
 * and at frame 200 the windows draw four groups in total. Every acceptance-side cap on that path is counted,
 * evicting or growing (see the backend's prepare / descriptor / arena code), so no single total can say which
 * stage stopped: offers (the game stopped submitting), acceptance (refusals before append), closure, or the
 * draw. This series puts the four stages' deltas on one line per frame, with the frame number, so the first
 * frame at which one stage moves while the others hold is the discriminator. The closure split follows the
 * group's own classification problem: {@code endWorldPass()} clears {@code worldPassActive} before its ordering
 * flush, so a group closed by that flush would read as a GUI closure without the backend's flush flag.
 * <p>
 * Bounded by {@code -Dceltium.vulkan.groupDecayCensusFrames=<n>} (default 900 frames), because the question is
 * where in the run the decay starts; a run that needs a later window sets the cap. The first line of a run is
 * the positive control: on the f5-fixed scene its early frames must show {@code closedWorld} in the hundreds,
 * matching the GROUP_BOUNDS observation, or the instrument is not seeing the path it claims to.
 */
final class GroupDecayCensus {
    static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.groupDecayCensus");
    private static final int MAX_FRAMES =
            Integer.getInteger("celtium.vulkan.groupDecayCensusFrames", 900).intValue();

    /** Groups closed while the world pass was active, since the last printed frame. */
    private static long closedWorld;
    /** Groups closed by {@code endWorldPass()}'s ordering flush, since the last printed frame. */
    private static long closedWorldFlush;
    /** Groups closed anywhere else (the GUI stage, frame boundaries), since the last printed frame. */
    private static long closedOther;

    private static long lastSubmitted;
    private static long lastAppended;
    private static long lastRefused;
    private static long lastPrepFailures;
    private static long lastDrawn;
    private static long lastDiscarded;
    private static long lastStaleRefusals;
    private static long lastStaleGeneration;
    private static long lastStaleEpoch;
    private static long lastKept;
    private static long lastUnflushed;
    private static long lastArenaFallbacks;
    private static boolean started;

    private GroupDecayCensus() {
    }

    /** Records one group closure under the phase it closed in. Called only when {@link #ENABLED}. */
    static void noteClosure(boolean worldPass, boolean worldFlush) {
        if (worldPass) {
            closedWorld++;
        } else if (worldFlush) {
            closedWorldFlush++;
        } else {
            closedOther++;
        }
    }

    /**
     * Prints the ended frame's line and resets the per-frame closure split. The counters arrive as the run's
     * cumulative totals; the line carries their deltas, so a stage that stops moving reads as zero beside the
     * stage that took its place.
     */
    static void frameEnd(long frame, int offers, int queueDepth, int prepareDescriptorFailures,
            int unflushedDropped, long arenaFallbacks, String lastRefusal, ImmediateStatistics stats) {
        if (frame > MAX_FRAMES) {
            return;
        }
        long submitted = stats.immediateSubmittedBatches();
        long appended = stats.appendCount();
        long refused = stats.refusedBeforeAppend();
        long drawn = stats.immediateGroupsDrawn();
        long discarded = stats.immediateGroupsDiscarded();
        long staleRefusals = stats.staleGroupRefusals();
        long staleGeneration = stats.groupsStaleTargetGeneration();
        long staleEpoch = stats.groupsStaleWorldEpoch();
        long kept = stats.immediateGroupsKeptForOtherTargets();
        System.out.println("[Celtium] CELTIUM_GROUP_DECAY frame=" + frame
                + (started ? "" : " start=1")
                + " offers=" + offers
                + " submitted=" + (submitted - lastSubmitted)
                + " appended=" + (appended - lastAppended)
                + " closedWorld=" + closedWorld
                + " closedFlush=" + closedWorldFlush
                + " closedOther=" + closedOther
                + " refused=" + (refused - lastRefused)
                + " prepFailures=" + (prepareDescriptorFailures - lastPrepFailures)
                + " drawn=" + (drawn - lastDrawn)
                + " discarded=" + (discarded - lastDiscarded)
                + " staleRefusals=" + (staleRefusals - lastStaleRefusals)
                + " staleGen=" + (staleGeneration - lastStaleGeneration)
                + " staleEpoch=" + (staleEpoch - lastStaleEpoch)
                + " kept=" + (kept - lastKept)
                + " unflushed=" + (unflushedDropped - lastUnflushed)
                + " arenaFallbacks=" + (arenaFallbacks - lastArenaFallbacks)
                + " queue=" + queueDepth
                + ((refused - lastRefused) > 0 ? " lastRefusal=[" + shorten(lastRefusal) + "]" : ""));
        started = true;
        lastSubmitted = submitted;
        lastAppended = appended;
        lastRefused = refused;
        lastPrepFailures = prepareDescriptorFailures;
        lastDrawn = drawn;
        lastDiscarded = discarded;
        lastStaleRefusals = staleRefusals;
        lastStaleGeneration = staleGeneration;
        lastStaleEpoch = staleEpoch;
        lastKept = kept;
        lastUnflushed = unflushedDropped;
        lastArenaFallbacks = arenaFallbacks;
        closedWorld = 0;
        closedWorldFlush = 0;
        closedOther = 0;
    }

    /** Keeps a refusal reason on one line and at a bounded length; the full text stays in the shutdown census. */
    private static String shorten(String refusal) {
        if (refusal == null) {
            return "none";
        }
        String flat = refusal.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= 160 ? flat : flat.substring(0, 160) + "...";
    }
}
