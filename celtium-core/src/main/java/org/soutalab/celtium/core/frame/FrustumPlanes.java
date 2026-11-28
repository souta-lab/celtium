package org.soutalab.celtium.core.frame;

import java.util.Arrays;

/**
 * Six frustum planes as (a, b, c, d) coefficient quadruples, in the order left, right, bottom, top,
 * near, far, in camera-origin-relative space. Subtract the view's world origin in double
 * precision before evaluating a world-space point. The same origin must be used for the
 * view-projection transform and for plane evaluation. A point is inside when every plane's
 * {@code a*x + b*y + c*z + d} is non-negative. The GPU consumes the same 24 coefficients.
 */
public final class FrustumPlanes {

    public static final int PLANE_COUNT = 6;
    public static final int FLOAT_COUNT = PLANE_COUNT * 4;

    public static final int LEFT = 0;
    public static final int RIGHT = 1;
    public static final int BOTTOM = 2;
    public static final int TOP = 3;
    public static final int NEAR = 4;
    public static final int FAR = 5;

    private static final FrustumPlanes PERMISSIVE = new FrustumPlanes(new float[FLOAT_COUNT]);

    private final float[] coefficients;

    private FrustumPlanes(float[] coefficients) {
        this.coefficients = coefficients;
    }

    /**
     * Copies six camera-origin-relative (a, b, c, d) quadruples.
     *
     * @throws IllegalArgumentException if the array is null or has a length other than 24
     * @return the permissive frustum if any coefficient is non-finite
     */
    public static FrustumPlanes of(float[] coefficients) {
        if (coefficients == null || coefficients.length != FLOAT_COUNT) {
            throw new IllegalArgumentException(
                    "a frustum is exactly " + FLOAT_COUNT + " coefficients (6 planes x abcd)");
        }
        float[] copy = coefficients.clone();
        for (float coefficient : copy) {
            if (!Float.isFinite(coefficient)) {
                return permissive();
            }
        }
        return new FrustumPlanes(copy);
    }

    /** An immutable frustum whose six plane evaluations are zero for every finite point. */
    public static FrustumPlanes permissive() {
        return PERMISSIVE;
    }

    public float coefficient(int plane, int component) {
        if (plane < 0 || plane >= PLANE_COUNT || component < 0 || component >= 4) {
            throw new IndexOutOfBoundsException("plane " + plane + ", component " + component);
        }
        return coefficients[plane * 4 + component];
    }

    /** Evaluates a camera-origin-relative point. A non-negative result is inside the half-space. */
    public float signedDistance(int plane, double x, double y, double z) {
        int base = plane * 4;
        return (float) (coefficients[base] * x + coefficients[base + 1] * y
                + coefficients[base + 2] * z + coefficients[base + 3]);
    }

    /** The flat coefficient array (a copy; the frustum stays immutable). */
    public float[] coefficients() {
        return coefficients.clone();
    }

    /** Writes the 24 coefficients into {@code out} at {@code offset}; returns the next free offset. */
    public int copyInto(float[] out, int offset) {
        System.arraycopy(coefficients, 0, out, offset, FLOAT_COUNT);
        return offset + FLOAT_COUNT;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FrustumPlanes planes
                && Arrays.equals(coefficients, planes.coefficients);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(coefficients);
    }
}
