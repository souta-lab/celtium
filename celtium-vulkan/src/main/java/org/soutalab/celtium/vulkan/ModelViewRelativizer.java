package org.soutalab.celtium.vulkan;

import org.joml.Matrix4f;

/**
 * Turns the live model view into the transform a display list's stored vertices need.
 * <p>
 * The stored vertices are model space, so a replay needs the camera's change since the list was captured:
 * {@code V_now * C_compile^-1}. That is what this computes, and the two things it must not do are what the model path
 * used to pay for on every replay - allocate the matrices and invert a matrix whose inverse is already known.
 * <p>
 * Today {@link VulkanModelQuadCapture#matrixForList(int)} answers with the identity for every list, so the relative
 * transform is the live model view unchanged and the inversion is dead work. The general path stays because that
 * answer is a decision of the capture, not a constant of the renderer: if it ever reports a real compile matrix this
 * still composes correctly, and the inverse is cached per list and capture generation rather than recomputed.
 * <p>
 * Not thread safe; belongs to the render thread, like everything that calls it.
 */
final class ModelViewRelativizer {

    private final Matrix4f live = new Matrix4f();
    private final Matrix4f compileView = new Matrix4f();
    private final Matrix4f compileInverse = new Matrix4f();
    private final Matrix4f relative = new Matrix4f();

    /** The list whose inverse is cached, and the capture generation it belongs to. */
    private int cachedList = Integer.MIN_VALUE;
    private int cachedGeneration = Integer.MIN_VALUE;
    private boolean cachedUsable;

    /** How many times a replay needed the live model view as it stood, i.e. the identity short-circuit. */
    private long identityReplays;
    /** How many times the cached inverse was reused. */
    private long inverseReuses;
    /** How many times an inverse had to be built. */
    private long inverseBuilds;

    /**
     * Rewrites {@code liveModelView} in place into the transform the list's vertices need at this replay.
     *
     * @param liveModelView the live model view, column-major, sixteen floats
     * @param listId        the display list being replayed
     * @param generation    the capture generation the list's geometry belongs to
     */
    void makeRelative(float[] liveModelView, int listId, int generation) {
        float[] compileMatrix = VulkanModelQuadCapture.matrixForList(listId);
        if (compileMatrix == null || isIdentity(compileMatrix)) {
            // V * I = V: nothing to compose, and nothing to allocate to notice it.
            this.identityReplays++;
            return;
        }
        composeRelative(liveModelView, compileMatrix, listId, generation);
    }

    /**
     * As {@link #makeRelative(float[], int, int)}, asking the list's generation only when there is a compile matrix to
     * compose: the capture answers the identity for nearly every list, and the generation is a synchronized lookup.
     */
    void makeRelative(float[] liveModelView, int listId) {
        float[] compileMatrix = VulkanModelQuadCapture.matrixForList(listId);
        if (compileMatrix == null || isIdentity(compileMatrix)) {
            this.identityReplays++;
            return;
        }
        composeRelative(liveModelView, compileMatrix, listId, VulkanModelQuadCapture.generationOf(listId));
    }

    private void composeRelative(float[] liveModelView, float[] compileMatrix, int listId, int generation) {
        if (!this.cachedUsable || this.cachedList != listId || this.cachedGeneration != generation) {
            this.compileView.set(compileMatrix);
            this.compileView.invert(this.compileInverse);
            this.cachedList = listId;
            this.cachedGeneration = generation;
            this.cachedUsable = true;
            this.inverseBuilds++;
        } else {
            this.inverseReuses++;
        }
        this.live.set(liveModelView);
        this.live.mul(this.compileInverse, this.relative);
        this.relative.get(liveModelView);
    }

    /** {@return whether {@code matrix} is the identity, compared without a temporary}. */
    static boolean isIdentity(float[] matrix) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float expected = column == row ? 1.0f : 0.0f;
                if (matrix[column * 4 + row] != expected) {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@return a bounded one-line census of which path replays took}. */
    String summary() {
        return "identity=" + this.identityReplays + " inverseReuses=" + this.inverseReuses
                + " inverseBuilds=" + this.inverseBuilds;
    }
}
