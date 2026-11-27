package org.soutalab.celtium.core.frame;

import java.util.Arrays;

/**
 * Six frustum planes as (a, b, c, d) coefficient quadruples, in the order left, right, bottom, top,
 * near, far. A point is inside the frustum when every plane's {@code a*x + b*y + c*z + d} is
 * non-negative. The culling stage uploads these verbatim, so the representation is the flat
 * 24-float array the GPU consumes - no matrix type crosses the interface.
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

    private final float[] coefficients;

    private FrustumPlanes(float[] coefficients) {
        this.coefficients = coefficients;
    }

    /** @param coefficients exactly {@link #FLOAT_COUNT} floats, six (a, b, c, d) quadruples. Copied. */
    public static FrustumPlanes of(float[] coefficients) {
        if (coefficients == null || coefficients.length != FLOAT_COUNT) {
            throw new IllegalArgumentException(
                    "a frustum is exactly " + FLOAT_COUNT + " coefficients (6 planes x abcd)");
        }
        return new FrustumPlanes(coefficients.clone());
    }

    public float coefficient(int plane, int component) {
        if (plane < 0 || plane >= PLANE_COUNT || component < 0 || component >= 4) {
            throw new IndexOutOfBoundsException("plane " + plane + ", component " + component);
        }
        return coefficients[plane * 4 + component];
    }

    /** Signed distance of a point from a plane; non-negative means inside that plane's half-space. */
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
