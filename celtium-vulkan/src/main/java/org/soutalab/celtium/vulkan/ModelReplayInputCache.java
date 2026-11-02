package org.soutalab.celtium.vulkan;

/**
 * The two per-replay values whose inputs change far less often than the replay count: the lit payload and the
 * depth-remapped projection.
 * <p>
 * Measured need, leg {@code r16b-model-growth-4k} (Supersymmetry, AMD RX 6400, world): the pack asked for 2,509,753
 * model replays in 112 frames and the enqueue path took 781 ms for them, about 311 ns a replay. Two of the things a
 * replay builds depend on values that did not change between most consecutive replays:
 * <ul>
 *   <li>the lit payload ({@code VulkanTerrainGpuBackend.celtium$lightParams}) is the inverse of the modelview's upper
 *       3x3 times the two light directions, two normalisations and the ambient/diffuse copy - a 3x3 inverse and two
 *       square roots per replay. It reads only {@code modelView[0..10]} (the rotation; the translation at 12..14 is
 *       never read), {@code state[8]} and the light state, whose {@link VulkanGlState#lightGeneration()} moves on every
 *       light change. A scene of upright entities and block entities replays many rotations that are exactly equal.
 *   <li>the projection remap is a 4x4 multiply of the live projection with a constant matrix, and the projection is
 *       installed per pass, not per replay.
 * </ul>
 * Both answers are pure functions of the inputs kept here, so a hit returns the same bytes a recomputation would: this
 * cache can only skip work, never change a draw. The counters make the hit rate measurable - a replay path that saves
 * nothing on a scene whose rotations all differ must not be claimed as a win.
 * <p>
 * Not thread safe; render thread only.
 */
final class ModelReplayInputCache {

    /** The nine rotation floats of the modelview the light payload was built from, in the order they are read. */
    private static final int[] ROTATION = {0, 1, 2, 4, 5, 6, 8, 9, 10};
    private static final int ROTATION_FLOATS = ROTATION.length;

    /**
     * {@return whether two modelviews carry the same nine rotation floats the lit payload reads}.
     * <p>
     * The single owner of that list: {@code VulkanTerrainGpuBackend.celtium$lightParams} reads the payload from a
     * {@link #copyRotation} of the modelview, and {@link #reuseLightPayload} asks this comparison, so the cache's key
     * and the function's inputs cannot drift. It is deliberately <em>not</em> the model replay's hit test: that
     * compares the payload's twenty bytes, because the payload is invariant under a change of modelview (the inverse
     * of the rotation cancels the rotation the light was set under). A guard keyed on this pair was measured to serve
     * zero of 2,940,981 replays of the pack's scene while the bytes repeated (leg {@code r19b-fastpath}).
     */
    static boolean sameRotation(float[] modelView, float[] rotation) {
        if (modelView == null || modelView.length < 11 || rotation == null || rotation.length < ROTATION_FLOATS) {
            return false;
        }
        for (int i = 0; i < ROTATION_FLOATS; i++) {
            if (modelView[ROTATION[i]] != rotation[i]) {
                return false;
            }
        }
        return true;
    }

    /** Copies the nine rotation floats the payload is built from, so its inputs are named in one place. */
    static void copyRotation(float[] modelView, float[] out) {
        for (int i = 0; i < ROTATION_FLOATS; i++) {
            out[i] = modelView[ROTATION[i]];
        }
    }

    /**
     * {@code -Dceltium.vulkan.replayInputCache=false}: the control arm for the next measurement.
     * <p>
     * A cache that is on by default must be measurable against the same jar in the same scene, or its saving is a
     * cross-leg comparison. With this off, both {@code reuse*} answer false for every replay - the counters then read
     * all misses - and the path does exactly the work it did before the cache existed. The two arms differ in nothing
     * else, so the difference in {@code modelEnqueueMicrosPerCall} is the cache's own.
     */
    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.replayInputCache", "true"));

    private final float[] lightRotation = new float[ROTATION_FLOATS];
    private long lightGeneration = Long.MIN_VALUE;
    private boolean lightValid;

    private final float[] projectionSource = new float[16];
    private boolean projectionValid;

    private long lightHits;
    private long lightMisses;
    private long projectionHits;
    private long projectionMisses;

    /**
     * Asks whether the light payload can be reused, and counts the answer as a hit or a miss: every call is one of the
     * two, so the pair is the number of rewrites this cache avoided and the number it could not.
     *
     * @param generation {@link VulkanGlState#lightGeneration()} now
     * @param modelView  the replay's modelview, column-major, which the payload is derived from
     * @return whether the payload built for the same generation and rotation is still the right one
     */
    boolean reuseLightPayload(long generation, float[] modelView) {
        boolean reuse = ENABLED && this.lightValid && this.lightGeneration == generation && sameRotation(modelView);
        if (reuse) {
            this.lightHits++;
        } else {
            this.lightMisses++;
        }
        return reuse;
    }

    /** Records the inputs the payload was just built from. */
    void noteLightPayload(long generation, float[] modelView) {
        for (int i = 0; i < ROTATION_FLOATS; i++) {
            this.lightRotation[i] = modelView[ROTATION[i]];
        }
        this.lightGeneration = generation;
        this.lightValid = true;
    }

    private boolean sameRotation(float[] modelView) {
        if (modelView == null || modelView.length < 11) {
            return false;
        }
        for (int i = 0; i < ROTATION_FLOATS; i++) {
            if (modelView[ROTATION[i]] != this.lightRotation[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Asks whether the remapped projection can be reused, counting the answer like {@link #reuseLightPayload}.
     *
     * @param projection the live projection the remap is computed from
     * @return whether the remapped projection built from the same contents is still the right one
     */
    boolean reuseProjection(float[] projection) {
        boolean reuse = ENABLED && this.projectionValid && projection != null && projection.length >= 16
                && java.util.Arrays.equals(this.projectionSource, projection);
        if (reuse) {
            this.projectionHits++;
        } else {
            this.projectionMisses++;
        }
        return reuse;
    }

    /** Records the projection contents the remap was just built from. */
    void noteProjection(float[] projection) {
        if (projection == null || projection.length < 16) {
            this.projectionValid = false;
            return;
        }
        System.arraycopy(projection, 0, this.projectionSource, 0, 16);
        this.projectionValid = true;
    }

    /** {@return the hit/miss census, for the report line}. */
    String describe() {
        return "light[hit=" + this.lightHits + " miss=" + this.lightMisses + "]"
                + " projection[hit=" + this.projectionHits + " miss=" + this.projectionMisses + "]";
    }
}
