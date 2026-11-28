package org.soutalab.celtium.vulkan.terrain.lane1;

import org.joml.Matrix4f;
import org.soutalab.celtium.core.frame.CameraPose;
import org.soutalab.celtium.core.frame.FrustumPlanes;

import java.util.Objects;

/** Builds the matching transform and cull planes for a camera-origin-relative view. */
public final class FrustumExtractor {

    public enum DepthConvention {
        VULKAN_ZERO_TO_ONE,
        OPENGL_NEG_ONE_TO_ONE
    }

    /** The origin subtracted from world coordinates, column-major transform, and immutable planes. */
    public record View(CameraPose origin, float[] viewProj, FrustumPlanes planes) {
        public View {
            viewProj = viewProj.clone();
        }

        @Override
        public float[] viewProj() {
            return viewProj.clone();
        }
    }

    private FrustumExtractor() {
    }

    /**
     * Combines column-major projection and model-view matrices, each with 16 floats.
     * The model-view must operate on positions relative to {@code origin}. Retain its
     * eye-height, camera-mode and other local transforms. The depth convention describes
     * the supplied projection. Non-finite or singular transforms disable frustum culling
     * with permissive planes. The view-projection still contains the matrix product.
     */
    public static View extract(CameraPose origin, float[] projection, float[] modelView,
                               DepthConvention depthConvention) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(depthConvention, "depthConvention");
        Matrix4f combined = matrix(projection).mul(matrix(modelView));
        float[] viewProj = combined.get(new float[16]);
        float determinant = combined.determinant();
        FrustumPlanes planes = FrustumPlanes.permissive();
        if (combined.isFinite() && Float.isFinite(determinant) && determinant != 0.0f) {
            float[] coefficients = new float[FrustumPlanes.FLOAT_COUNT];
            writePlane(viewProj, coefficients, FrustumPlanes.LEFT, 3, 0, 1);
            writePlane(viewProj, coefficients, FrustumPlanes.RIGHT, 3, 0, -1);
            writePlane(viewProj, coefficients, FrustumPlanes.BOTTOM, 3, 1, 1);
            writePlane(viewProj, coefficients, FrustumPlanes.TOP, 3, 1, -1);
            if (depthConvention == DepthConvention.VULKAN_ZERO_TO_ONE) {
                writePlane(viewProj, coefficients, FrustumPlanes.NEAR, 2, 2, 0);
            } else {
                writePlane(viewProj, coefficients, FrustumPlanes.NEAR, 3, 2, 1);
            }
            writePlane(viewProj, coefficients, FrustumPlanes.FAR, 3, 2, -1);
            planes = FrustumPlanes.of(coefficients);
        }
        return new View(origin, viewProj, planes);
    }

    private static Matrix4f matrix(float[] values) {
        if (values.length != 16) {
            throw new IllegalArgumentException("a view matrix has exactly 16 coefficients");
        }
        // Use scalar setters for heap arrays, matching the backend's JOML input path.
        return new Matrix4f().set(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
                values[12], values[13], values[14], values[15]);
    }

    private static void writePlane(float[] matrix, float[] planes, int plane,
                                   int row, int otherRow, int sign) {
        int base = plane * 4;
        for (int column = 0; column < 4; column++) {
            int offset = column * 4;
            planes[base + column] = matrix[offset + row] + sign * matrix[offset + otherRow];
        }
    }
}
