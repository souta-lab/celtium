package org.soutalab.celtium.vulkan;

/**
 * Why an accepted immediate batch opened a new group instead of joining the open one, counted by cause.
 * <p>
 * One counter per reason, not one total: the number of groups a frame creates is what the frame pays for, and
 * "groups are too many" cannot be acted on without knowing which of the twelve terms in the merge predicate actually
 * binds. {@link #summary()} is the bounded line the verification report prints; the array is sized from the enum, so a
 * reason cannot be added without a slot.
 */
final class ImmediateGroupSplitCensus {

    /** The merge predicate's terms, in the order the predicate tests them. */
    enum Reason {
        /** The bound target or the viewport changed: a pass boundary, not a state that could be merged across. */
        PASS,
        /** The projection or the model-view moved and the appender could not bake it into the vertices. */
        MATRIX,
        /** The projection or model-view moved on a pre-transformed group but is no longer axis-aligned. */
        MATRIX_NOT_BAKABLE,
        /** Scissor test or the rectangle changed. */
        SCISSOR,
        /** A lit group's light payload changed. */
        LIGHT,
        /** The vertex layout changed. */
        FORMAT,
        /** A different texture (or none) is bound. */
        TEXTURE,
        /** The pipeline/state key changed: blend, alpha, depth, cull, colour mask, fog enable... */
        STATE_KEY,
        /** The prepared pipeline or descriptor set is a different object than the open group's. */
        PREPARED,
        /** Current colour changed on a layout that carries it as the tint. */
        COLOUR,
        /** The texture-coordinate affine changed. */
        TEXTURE_AFFINE,
        /** The fog bracket changed. */
        FOG,
        /** A retained mesh cannot join a group: its geometry is immutable and shared. */
        RETAINED;

        static final Reason[] VALUES = values();
    }

    private final long[] counts = new long[Reason.VALUES.length];

    /** How many distinct texture affines one frame's groups are recorded against before the table saturates. */
    private static final int AFFINE_SLOTS = 8;
    private final float[][] affines = new float[AFFINE_SLOTS][];
    private long affineRecorded;
    private long affineRepeats;
    /** Bounds of the translate components seen, and how many recorded groups had a non-identity affine at all. */
    private float affineTxMin = Float.MAX_VALUE;
    private float affineTxMax = -Float.MAX_VALUE;
    private float affineTyMin = Float.MAX_VALUE;
    private float affineTyMax = -Float.MAX_VALUE;
    private float affineScaleMin = Float.MAX_VALUE;
    private float affineScaleMax = -Float.MAX_VALUE;
    private long affineNonIdentity;

    /**
     * Records the texture affine a group was opened with.
     * <p>
     * This separates the two shapes a split census cannot tell apart: a few values used over and over again is the
     * game alternating states between draws (no grouping can merge across it without per-draw state), while a new
     * value every time means this renderer's own mirror is drifting and the boundary is an artefact.
     *
     * @param affine the captured affine, or {@code null} for the identity
     */
    void noteTextureAffine(float[] affine) {
        // A zero-length array is how a captured identity is represented; both shapes mean "no transform".
        boolean identity = affine == null || affine.length < 6;
        this.affineRecorded++;
        if (!identity) {
            this.affineNonIdentity++;
            this.affineTxMin = Math.min(this.affineTxMin, affine[4]);
            this.affineTxMax = Math.max(this.affineTxMax, affine[4]);
            this.affineTyMin = Math.min(this.affineTyMin, affine[5]);
            this.affineTyMax = Math.max(this.affineTyMax, affine[5]);
            this.affineScaleMin = Math.min(this.affineScaleMin, Math.min(affine[0], affine[3]));
            this.affineScaleMax = Math.max(this.affineScaleMax, Math.max(affine[0], affine[3]));
        }
        int used = 0;
        while (used < AFFINE_SLOTS && this.affines[used] != null) {
            if (sameAffine(this.affines[used], affine)) {
                this.affineRepeats++;
                return;
            }
            used++;
        }
        if (used < AFFINE_SLOTS) {
            this.affines[used] = affine == null ? new float[0] : affine.clone();
        }
    }

    private static boolean sameAffine(float[] left, float[] right) {
        float[] other = right == null ? EMPTY : right;
        if (left.length != other.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (left[i] != other[i]) {
                return false;
            }
        }
        return true;
    }

    private static final float[] EMPTY = new float[0];

    /** {@return recorded groups, how many distinct affines they used, and how many reused one}. */
    String affineSummary() {
        int distinct = 0;
        while (distinct < AFFINE_SLOTS && this.affines[distinct] != null) {
            distinct++;
        }
        return "affineSamples=" + this.affineRecorded + " affineDistinct" + (distinct >= AFFINE_SLOTS ? "+" : "")
                + "=" + distinct + " affineRepeats=" + this.affineRepeats
                + " affineNonIdentity=" + this.affineNonIdentity
                // A drifter's translate keeps growing; a genuine per-draw transform stays inside a small band, and the
                // two are told apart by these bounds rather than by the number of distinct values alone.
                + " affineTx=[" + bounded(this.affineTxMin, this.affineTxMax) + "]"
                + " affineTy=[" + bounded(this.affineTyMin, this.affineTyMax) + "]"
                + " affineScale=[" + bounded(this.affineScaleMin, this.affineScaleMax) + "]";
    }

    private static String bounded(float min, float max) {
        if (min > max) {
            return "none";
        }
        return String.format(java.util.Locale.ROOT, "%.3f..%.3f", Float.valueOf(min), Float.valueOf(max));
    }

    /**
     * Classifies why the merge predicate refused an open group, in the predicate's own term order.
     *
     * @return the cause, or {@code null} when opening a group here is not a split: retained geometry is immutable, and
     *         the first group of a window has nothing to join
     */
    static Reason classify(boolean retained, boolean openStarved, boolean formatChanged, boolean textureChanged,
                           boolean stateKeyChanged, boolean samePass, boolean scissorMoved, boolean preparedAbsent,
                           boolean pipelineChanged, boolean descriptorChanged, boolean projectionMoved,
                           boolean modelViewNotBakable, boolean modelViewMoved, boolean colourChanged,
                           boolean affineChanged, boolean fogChanged, boolean lightMoved) {
        if (retained) return Reason.RETAINED;
        if (openStarved) return null;
        if (formatChanged) return Reason.FORMAT;
        if (textureChanged) return Reason.TEXTURE;
        if (stateKeyChanged) return Reason.STATE_KEY;
        if (!samePass) return Reason.PASS;
        if (scissorMoved) return Reason.SCISSOR;
        if (preparedAbsent || pipelineChanged || descriptorChanged) return Reason.PREPARED;
        if (projectionMoved) return Reason.MATRIX;
        if (modelViewNotBakable) return Reason.MATRIX_NOT_BAKABLE;
        if (modelViewMoved) return Reason.MATRIX;
        if (colourChanged) return Reason.COLOUR;
        if (affineChanged) return Reason.TEXTURE_AFFINE;
        if (fogChanged) return Reason.FOG;
        if (lightMoved) return Reason.LIGHT;
        return null;
    }

    void note(Reason reason) {
        // A null cause is "opening here is not a split" (the first group of a window); it is not an error.
        if (reason != null) {
            this.counts[reason.ordinal()]++;
        }
    }

    long count(Reason reason) {
        return this.counts[reason.ordinal()];
    }

    /** {@return total group splits counted}. */
    long total() {
        long total = 0L;
        for (long count : this.counts) {
            total += count;
        }
        return total;
    }

    /** {@return a bounded one-line census, largest cause first, omitting causes that never fired}. */
    String summary() {
        java.util.List<Reason> ordered = new java.util.ArrayList<>();
        java.util.Collections.addAll(ordered, Reason.VALUES);
        ordered.sort((left, right) -> Long.compare(this.counts[right.ordinal()], this.counts[left.ordinal()]));
        StringBuilder text = new StringBuilder("total=").append(total());
        for (Reason reason : ordered) {
            if (this.counts[reason.ordinal()] > 0L) {
                text.append(' ').append(reason.name().toLowerCase(java.util.Locale.ROOT)).append('=')
                        .append(this.counts[reason.ordinal()]);
            }
        }
        return text.toString();
    }
}
