package org.soutalab.celtium.vulkan;

/**
 * The light's view-projection for a shadow map, as a column-major 4x4 matrix in Vulkan's clip convention.
 * <p>
 * Stage (c) of {@code docs/plan.md}'s shadow work needs one matrix the depth-only pass can push per frame: the sun's
 * orthographic camera looking at the player. It is a separate, pure class because the arithmetic is the part that can
 * be checked without a device - an orthographic box whose centre lands at the middle of the shadow map and whose depth
 * runs from 0 (at the light) to 1 (furthest) is either right or wrong by inspection.
 * <p>
 * The matrix maps world space to clip space the way a Vulkan pipeline expects: x and y in [-1, 1], z in [0, 1]. The
 * terrain's own camera matrices carry GL's [-1, 1] depth and are remapped in the shaders, but the shadow pipeline will be
 * this project's own, so no remap is worth inheriting.
 */
public final class ShadowLightMatrix {

    private static final float[] EMPTY = new float[16];

    private ShadowLightMatrix() {
    }

    /**
     * Builds the light's view-projection.
     *
     * @param centerX  the point the shadow map is centred on (the player)
     * @param centerY  the point the shadow map is centred on (the player)
     * @param centerZ  the point the shadow map is centred on (the player)
     * @param toSunX   direction from the centre towards the sun, normalised internally
     * @param toSunY   direction from the centre towards the sun, normalised internally
     * @param toSunZ   direction from the centre towards the sun, normalised internally
     * @param halfExtent half the width of the box the map covers, in blocks
     * @param depthRange depth the box covers, in blocks; the light sits half of it away from the centre
     * @return a column-major 16-float view-projection, or an identity matrix when the inputs cannot define one
     */
    public static float[] viewProjection(float centerX, float centerY, float centerZ,
            float toSunX, float toSunY, float toSunZ, float halfExtent, float depthRange) {
        float[] identity = EMPTY.clone();
        identity[0] = 1.0f;
        identity[5] = 1.0f;
        identity[10] = 1.0f;
        identity[15] = 1.0f;
        if (!(halfExtent > 0.0f) || !(depthRange > 0.0f)) {
            return identity;
        }
        double sunLength = Math.sqrt((double) toSunX * toSunX + (double) toSunY * toSunY
                + (double) toSunZ * toSunZ);
        if (sunLength < 1.0e-6) {
            return identity;
        }
        double sx = toSunX / sunLength;
        double sy = toSunY / sunLength;
        double sz = toSunZ / sunLength;

        // The light stands back from the centre along the sun direction, so the centre sits half the depth range
        // in front of it and the near plane can be a small positive value.
        double eyeX = centerX + sx * depthRange * 0.5;
        double eyeY = centerY + sy * depthRange * 0.5;
        double eyeZ = centerZ + sz * depthRange * 0.5;

        // Forward is from the eye towards the centre: -normalised sun direction.
        double fx = -sx;
        double fy = -sy;
        double fz = -sz;

        // Up is world up unless the light looks straight down, where it would be parallel to forward.
        double upX = 0.0;
        double upY = 1.0;
        double upZ = 0.0;
        if (Math.abs(fy) > 0.999) {
            // Looking straight down, world up is parallel to forward. The fallback has to be -z rather than +z: with +z
            // the right vector comes out as -x and the map is mirrored, which the first version of the test caught as
            // "+x lands at -0.5".
            upX = 0.0;
            upY = 0.0;
            upZ = -1.0;
        }

        double rightX = fy * upZ - fz * upY;
        double rightY = fz * upX - fx * upZ;
        double rightZ = fx * upY - fy * upX;
        double rightLength = Math.sqrt(rightX * rightX + rightY * rightY + rightZ * rightZ);
        if (rightLength < 1.0e-6) {
            return identity;
        }
        rightX /= rightLength;
        rightY /= rightLength;
        rightZ /= rightLength;

        double trueUpX = rightY * fz - rightZ * fy;
        double trueUpY = rightZ * fx - rightX * fz;
        double trueUpZ = rightX * fy - rightY * fx;

        // View matrix, column-major: the rows are (right, trueUp, -forward) and the translation is their dot with the
        // negative eye position.
        double[] view = new double[16];
        view[0] = rightX;
        view[1] = trueUpX;
        view[2] = -fx;
        view[3] = 0.0;
        view[4] = rightY;
        view[5] = trueUpY;
        view[6] = -fy;
        view[7] = 0.0;
        view[8] = rightZ;
        view[9] = trueUpZ;
        view[10] = -fz;
        view[11] = 0.0;
        view[12] = -(rightX * eyeX + rightY * eyeY + rightZ * eyeZ);
        view[13] = -(trueUpX * eyeX + trueUpY * eyeY + trueUpZ * eyeZ);
        view[14] = fx * eyeX + fy * eyeY + fz * eyeZ;
        view[15] = 1.0;

        // Orthographic projection for Vulkan's [0, 1] depth: x and y scaled by one half-extent, z mapped from
        // [-near, -far] in view space to [0, 1].
        double near = 0.5;
        double far = depthRange + near;
        double[] projection = new double[16];
        projection[0] = 1.0 / halfExtent;
        projection[5] = 1.0 / halfExtent;
        projection[10] = -1.0 / (far - near);
        projection[14] = -near / (far - near);
        projection[15] = 1.0;

        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                double sum = 0.0;
                for (int k = 0; k < 4; k++) {
                    sum += projection[k * 4 + row] * view[column * 4 + k];
                }
                result[column * 4 + row] = (float) sum;
            }
        }
        return result;
    }

    /** {@return the clip-space position of a world point under {@code matrix}, as {x, y, z, w}} */
    public static float[] transform(float[] matrix, float x, float y, float z) {
        float[] out = new float[4];
        for (int row = 0; row < 4; row++) {
            out[row] = matrix[row] * x + matrix[4 + row] * y + matrix[8 + row] * z + matrix[12 + row];
        }
        return out;
    }
}
