package org.soutalab.celtium.vulkan;

/**
 * The last model replay's whole resolved tuple, so a replay that repeats it can skip resolving it again.
 * <p>
 * Measured need, leg {@code r16b-model-growth-4k} (Supersymmetry, AMD RX 6400, world): the pack asked for 2,509,753
 * model replays in 112 frames and the enqueue path took 781 ms for them (about 311 ns a replay), while
 * {@code modelInstances runs=98} says all of them were drawn as about one run per frame. A run is only extended when
 * {@code stateKey}, target, projection, fog, viewport, render pass and the frame all match, so for every replay of a
 * run the <em>state</em> was already known to be identical - yet the path captured it, hashed it, looked the texture
 * up twice, registered two array indices, read the texture matrix, rebuilt the light payload's inputs and re-derived
 * the extend predicate, and then counted the repeat it had just discovered.
 * <p>
 * What a replay still has to do is its own matrix: a display list replayed at another entity's position is a
 * different 64-byte matrix and therefore a different record. Everything else in the record (colour, combine, texture
 * affine, the lit payload, the two array indices, the alpha reference and function) is a pure function of inputs this
 * class holds, so a hit writes the same bytes the slow path would and only pays for the matrix.
 * <p>
 * The guard is the whole contract: every input of {@link Probe} is compared, and any difference is a miss, which
 * takes the unchanged path. Two groups - {@link #matchesShape} for the identities and {@link #matchesValues} for the
 * values - let the caller stop before resolving the expensive half. The values are the caller's live arrays rather
 * than copies. {@link #describe()} says how many replays were served, why the others were not, and - with
 * {@code -Dceltium.vulkan.batchTimings=true} - what the served and refused attempts cost inside one leg, so a scene
 * whose replays never repeat reports its misses instead of a saving.
 * <p>
 * Render thread only. Not thread safe.
 */
final class ModelReplayFastPath {

    /**
     * {@code -Dceltium.vulkan.modelReplayFastPath=false}: the control arm for the next measurement.
     * <p>
     * A path that is on by default must be measurable against the same jar in the same scene, or its saving is a
     * cross-build comparison. With this off every replay takes the unchanged path and the counters read all misses.
     */
    static final boolean ENABLED =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.modelReplayFastPath", "true"));

    /**
     * {@code -Dceltium.vulkan.replayValueVersions=false}: the control arm for the same jar.
     * <p>
     * With this off {@link #sameVersions} answers false for every replay, so the guard captures and compares every value
     * the way it did before the versions existed - same jar, same scene, same counters. A saving that only exists in a
     * cross-build comparison is not a saving, and this switch is what makes that testable in one leg pair.
     */
    static final boolean VALUE_VERSIONS =
            !"false".equalsIgnoreCase(System.getProperty("celtium.vulkan.replayValueVersions", "true"));

    /**
     * The raw inputs the sixteen-int state array is a function of, in the order {@link VulkanTerrainGpuBackend}
     * fills them: the blend enable and its four factors and two equations, the colour mask, the alpha test's enable,
     * reference and function, cull, depth enable/write/function, lighting, unit 0's texture environment mode and RGB
     * function, and whether the current target is the window (which masks the alpha write). Comparing these is
     * cheaper than the VP conversion, the light-map lookups and the sixteen stores the state capture performs, and it
     * is exactly as strong: every one of the sixteen captured values reads only these.
     */
    static final int SIGNATURE = 19;

    /**
     * One replay's values, and the storage of a cached tuple.
     * <p>
     * The arrays are the tuple's own bytes: {@link #note} copies the live values into them, and a hit writes the record
     * from them. Four of the values are not carried here on the live side: {@link #matchesValues} takes the
     * projection, the light-map coordinate, the combine colour and the lit payload as arguments, so a hit compares the
     * caller's arrays directly instead of moving forty-two floats into a scratch first. The colour and the affine stay
     * on the probe because one is read as four scalars and the other is filled into the probe's own six-float scratch.
     * Reused: the caller fills it in place, so a guard costs no allocation.
     */
    static final class Probe {
        final int[] signature = new int[SIGNATURE];
        final int[] state = new int[16];
        /** The tuple's sixteen projection floats; written by {@link #note}, compared against the live array. */
        final float[] projection = new float[16];
        final float[] colour = new float[4];
        final float[] affine = new float[6];
        final float[] lightCoord = new float[2];
        /** The tuple's twenty lit-payload floats; written by {@link #note}. */
        final float[] light = new float[20];
        final float[] combineColour = new float[4];

        /** The GL name of the display list, which is what the record's two array indices are resolved from. */
        int listId;
        int texture;
        /** The render pass the open run's pipeline was created for. */
        long renderPass;
        int quads;
        int textureIndex;
        int lightmapIndex;
        int alphaFunction;
        long frameId;
        long target;
        long stateKey;
        boolean colourValid;
        boolean affineValid;
        boolean lightCoordValid;
        boolean lightValid;
        boolean combineValid;
        /** The draw pass object the state belongs to; the run's pass is derived from it. */
        Object pass;
        Object meshTable;
        Object openGroup;
        Object instances;
        Object registry;
        /** The bindless texture array the two indices name; a rebuilt array makes a cached index meaningless. */
        Object textureArray;
        Object lightmap;
        Object combine;
        /**
         * The four versions that prove the non-matrix half of the tuple is still the live value: the state and colour,
         * the projection stack, the texture-matrix stacks and the texture environment. A hit compares these four - one
         * read and one compare each - instead of capturing nineteen state inputs, reading the colour and the affine and
         * comparing seventy-one values; a miss on any of them falls back to the full comparison, so a version that is
         * too coarse costs work, never a wrong draw.
         */
        long stateVersion;
        long projectionVersion;
        long texMatrixVersion;
        long envVersion;
        /** The pool slice the record's command points at, so a hit does not look the list up again. */
        int firstIndex;
        int vertexOffset;
        int indexCount;
    }

    /**
     * One cached tuple per display list, direct-mapped by the list id.
     * <p>
     * Measured need on the first leg that ran this path ({@code r18e-shape}, AMD RX 6400, plain scene): all 1,828,826
     * replays missed, and the last one named {@code list} - the pack draws list A, then list B, then A again, so a
     * single-slot cache of "the previous replay" can never be the same replay's inputs. The tuple is per list, not per
     * replay: what changes between two replays of one list is only the matrix. 64 slots hold a pack's whole model set
     * (measured: 8 lists) and still fit in a few tens of kilobytes; two lists that collide evict each other and take
     * the unchanged path, which is a miss and never a wrong draw.
     */
    static final int SLOTS = 64;
    private static final int SLOT_MASK = SLOTS - 1;

    private final Probe probe = new Probe();
    private final Probe[] cached = new Probe[SLOTS];
    private final boolean[] armed = new boolean[SLOTS];
    /** The slot the last successful shape guard matched, which is what {@link #matchesValues} and {@link #cached} read. */
    private Probe matched;

    ModelReplayFastPath() {
        for (int i = 0; i < SLOTS; i++) {
            this.cached[i] = new Probe();
        }
    }

    private long hits;
    private long misses;
    /** Misses that failed an identity, a value, or the run's own conditions. One each per miss. */
    private long shapeMisses;
    private long inputMisses;
    private long runMisses;
    /**
     * What a served attempt and a refused attempt cost, in nanoseconds, when {@code -Dceltium.vulkan.batchTimings=true}.
     * <p>
     * Measured need: leg {@code r18g-on} reported {@code modelEnqueueMillis=865} for 2,149,974 replays (402 ns each),
     * which is the whole enqueue path - the texture lookup, the matrix relativisation, the projection remap and this
     * guard - so it could not say which half to attack next. The call site times the attempt inside the enqueue's own
     * bracket, so one leg gives both: a hit's cost is this function's, and the preamble is the enqueue's total minus
     * these nanoseconds. Off in an ordinary run, like every other timing on that switch.
     */
    private long hitNanos;
    private long hitCalls;
    private long missNanos;
    private long missCalls;

    /** Records one timed attempt: the fast path's own cost, split by the answer the guard gave. */
    void noteAttempt(long nanos, boolean served) {
        if (served) {
            this.hitNanos += nanos;
            this.hitCalls++;
        } else {
            this.missNanos += nanos;
            this.missCalls++;
        }
    }

    /** {@return the live probe, to be filled by the caller before asking}. */
    Probe probe() {
        return this.probe;
    }

    /** {@return the tuple the last successful guard matched, read by the draw path}. */
    Probe cached() {
        return this.matched;
    }

    /** {@return whether any list's tuple has been recorded}. */
    boolean armed() {
        for (int i = 0; i < SLOTS; i++) {
            if (this.armed[i]) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first half of the guard: the identities that decide which objects a replay is about.
     * <p>
     * A success arms {@link #matchesValues} for this probe: the tuple it compared against becomes {@link #cached()}.
     *
     * @return whether every identity is the one the cached tuple was resolved for
     */
    boolean matchesShape(Probe now) {
        if (!ENABLED) {
            return false;
        }
        int slot = now.listId & SLOT_MASK;
        if (!this.armed[slot]) {
            this.matched = null;
            return false;
        }
        Probe last = this.cached[slot];
        // Named one at a time: a scene that repeats nothing reports which identity moved, so a guard that is too
        // strict costs a field name and not a round of guessing.
        if (now.listId != last.listId) return shapeMiss("list");
        if (now.frameId != last.frameId) return shapeMiss("frame");
        if (now.texture != last.texture) return shapeMiss("texture");
        if (now.target != last.target) return shapeMiss("target");
        if (now.pass != last.pass) return shapeMiss("pass");
        if (now.meshTable != last.meshTable) return shapeMiss("meshTable");
        if (now.openGroup != last.openGroup) return shapeMiss("openGroup");
        if (now.instances != last.instances) return shapeMiss("instances");
        if (now.registry != last.registry) return shapeMiss("registry");
        if (now.textureArray != last.textureArray) return shapeMiss("textureArray");
        if (now.lightmap != last.lightmap) return shapeMiss("lightmap");
        if (now.combine != last.combine) return shapeMiss("combine");
        // Only a success names the tuple: a refused shape must not leave the previous list's tuple looking matched.
        this.matched = last;
        return true;
    }

    /** {@return false, remembering which identity of the shape guard moved}. */
    private boolean shapeMiss(String what) {
        this.lastShapeMiss = what;
        return false;
    }

    /** The last identity {@link #matchesShape} refused, for the report line. */
    private String lastShapeMiss = "none";

    /**
     * The value half, for a caller that has already passed {@link #matchesShape} with this probe: re-running the
     * twelve identity comparisons on every hit was the one thing the split existed to avoid.
     * <p>
     * The values are the caller's live arrays, not copies into the probe: the guard only reads them, and moving them
     * into a scratch first was forty-two floats per replay. The lit payload is compared as the twenty floats the
     * record carries, not through the inputs it was built from. <b>Measured</b> (leg {@code r19b-fastpath}, 2,940,981
     * replays): an input-keyed comparison - the light generation plus the modelview's nine rotation floats - produced
     * <em>zero</em> hits, because the payload is invariant under a change of modelview (the inverse of the rotation
     * cancels the rotation the light was set under), so a scene that replays one list at many entity orientations
     * rebuilds the same twenty floats every time. Comparing the bytes is what lets that repetition hit; comparing the
     * inputs was sound but refused every replay it was meant to serve.
     *
     * @param light the payload this replay resolved, or null when it is unlit
     * @return whether every value the record carries is the cached tuple's
     */
    boolean matchesValues(Probe now, float[] projection, float[] lightCoord, float[] combineColour, float[] light) {
        return matchesValues(now, projection, lightCoord, combineColour, light, sameVersions(now, this.matched));
    }

    /**
     * {@return whether the four versions say the tuple's non-matrix values are the live ones}.
     * <p>
     * Each version is bumped by every mutator of the state it names - see {@link VulkanGlState#stateVersion()} and
     * {@link NativeTextureEnvironment#version()} - so equality means every byte the version covers is unchanged. It is
     * an over-approximation on purpose: a coarse version refuses replays it could have served, and no version can serve
     * one it should have refused. The modelview is not one of them: it is the replay's own matrix, and the tuple
     * deliberately does not carry it.
     */
    static boolean sameVersions(Probe now, Probe last) {
        if (!VALUE_VERSIONS) {
            return false;
        }
        return last != null
                && now.stateVersion == last.stateVersion
                && now.projectionVersion == last.projectionVersion
                && now.texMatrixVersion == last.texMatrixVersion
                && now.envVersion == last.envVersion;
    }

    /**
     * The value half, with the versions' answer handed in: {@code versionsProven} means the state signature, the colour,
     * the texture affine, the projection and the combine colour are unchanged by construction, so they are not captured
     * or compared. The light-map coordinate and the lit payload are always compared: they are the values a replay's own
     * matrix and the frame's light state can move without any version moving.
     */
    boolean matchesValues(Probe now, float[] projection, float[] lightCoord, float[] combineColour, float[] light,
            boolean versionsProven) {
        if (!ENABLED) {
            return false;
        }
        Probe last = this.matched;
        if (last == null || last.listId != now.listId) {
            return false;
        }
        if (versionsProven) {
            return now.lightCoordValid == last.lightCoordValid
                    && (!now.lightCoordValid || java.util.Arrays.equals(lightCoord, last.lightCoord))
                    && now.lightValid == last.lightValid
                    && (!now.lightValid || java.util.Arrays.equals(light, last.light))
                    && now.combineValid == last.combineValid
                    && (!now.combineValid || java.util.Arrays.equals(combineColour, last.combineColour));
        }
        // Arrays whose "no value" answer is a stale buffer are compared only when both sides carry one: the record
        // ignores them when the flag is clear, so comparing the stale contents could only invent a miss.
        return java.util.Arrays.equals(now.signature, last.signature)
                && projection != null && java.util.Arrays.equals(projection, last.projection)
                && now.colourValid == last.colourValid
                && (!now.colourValid || java.util.Arrays.equals(now.colour, last.colour))
                && now.affineValid == last.affineValid
                && (!now.affineValid || java.util.Arrays.equals(now.affine, last.affine))
                && now.lightCoordValid == last.lightCoordValid
                && (!now.lightCoordValid || java.util.Arrays.equals(lightCoord, last.lightCoord))
                && now.lightValid == last.lightValid
                && (!now.lightValid || java.util.Arrays.equals(light, last.light))
                && now.combineValid == last.combineValid
                && (!now.combineValid || java.util.Arrays.equals(combineColour, last.combineColour));
    }

    /** Counts one replay served by the unchanged path, classifying what failed the guard. */
    void noteMiss(int reason) {
        if (!ENABLED) {
            return;
        }
        this.misses++;
        if (reason == MISS_SHAPE) {
            this.shapeMisses++;
        } else if (reason == MISS_INPUTS) {
            this.inputMisses++;
        } else {
            this.runMisses++;
        }
    }

    /** Counts one replay served from the cached tuple. */
    void noteHit() {
        this.hits++;
    }

    static final int MISS_SHAPE = 0;
    static final int MISS_INPUTS = 1;
    static final int MISS_RUN = 2;

    /**
     * Records the tuple a replay resolved, for the next replay to compare against. The values are copied: the caller
     * writes its scratches on the next replay, and a cached tuple that aliased them could fake a hit.
     * <p>
     * The resolved buffers arrive as arguments rather than through the probe because the live probe does not copy them
     * any more: this is the one place the copy has to happen, and it is one copy instead of two.
     */
    void note(Probe now, float[] projection, float[] light, float[] lightCoord, float[] combineColour) {
        if (!ENABLED) {
            return;
        }
        int slot = now.listId & SLOT_MASK;
        Probe last = this.cached[slot];
        if (projection == null
                || (now.lightValid && light == null)
                || (now.lightCoordValid && lightCoord == null)
                || (now.combineValid && combineColour == null)) {
            // A tuple whose resolved buffers are missing is not one a hit may be served from: disarm rather than keep
            // the previous list's bytes behind an identity that now means something else.
            this.armed[slot] = false;
            return;
        }
        System.arraycopy(now.signature, 0, last.signature, 0, SIGNATURE);
        System.arraycopy(now.state, 0, last.state, 0, 16);
        System.arraycopy(projection, 0, last.projection, 0, 16);
        System.arraycopy(now.colour, 0, last.colour, 0, 4);
        System.arraycopy(now.affine, 0, last.affine, 0, 6);
        if (now.lightCoordValid) {
            System.arraycopy(lightCoord, 0, last.lightCoord, 0, 2);
        }
        if (now.lightValid) {
            System.arraycopy(light, 0, last.light, 0, 20);
        }
        if (now.combineValid) {
            System.arraycopy(combineColour, 0, last.combineColour, 0, 4);
        }
        last.listId = now.listId;
        last.texture = now.texture;
        last.renderPass = now.renderPass;
        last.quads = now.quads;
        last.textureIndex = now.textureIndex;
        last.lightmapIndex = now.lightmapIndex;
        last.alphaFunction = now.alphaFunction;
        last.frameId = now.frameId;
        last.target = now.target;
        last.stateKey = now.stateKey;
        last.colourValid = now.colourValid;
        last.affineValid = now.affineValid;
        last.lightCoordValid = now.lightCoordValid;
        last.lightValid = now.lightValid;
        last.combineValid = now.combineValid;
        last.pass = now.pass;
        last.meshTable = now.meshTable;
        last.openGroup = now.openGroup;
        last.instances = now.instances;
        last.registry = now.registry;
        last.textureArray = now.textureArray;
        last.lightmap = now.lightmap;
        last.combine = now.combine;
        last.stateVersion = now.stateVersion;
        last.projectionVersion = now.projectionVersion;
        last.texMatrixVersion = now.texMatrixVersion;
        last.envVersion = now.envVersion;
        last.firstIndex = now.firstIndex;
        last.vertexOffset = now.vertexOffset;
        last.indexCount = now.indexCount;
        this.armed[slot] = true;
    }

    /** Drops every list's tuple, for a teardown that would otherwise compare against a dead world. */
    void invalidate() {
        for (int i = 0; i < SLOTS; i++) {
            this.armed[i] = false;
        }
        this.matched = null;
    }

    /** {@return the hit/miss census, for the report line}. */
    String describe() {
        return "hit=" + this.hits + " miss=" + this.misses
                + "(shape=" + this.shapeMisses + " inputs=" + this.inputMisses + " run=" + this.runMisses + ")"
                + " armed=" + this.armed() + " lastShape=" + this.lastShapeMiss + " enabled=" + ENABLED
                + " hitNanos=" + this.hitNanos + " hitCalls=" + this.hitCalls
                + " missNanos=" + this.missNanos + " missCalls=" + this.missCalls;
    }
}
