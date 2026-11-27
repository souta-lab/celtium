package org.soutalab.celtium.forge;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.frame.FrustumPlanes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract for {@link ShaderFrameStateFeeder#frustumFromMatrix}: the planes it extracts
 * from a combined projection*view matrix must be the frustum of that matrix - centred on
 * the camera the matrix was built for, at any world coordinates.
 *
 * The seat this pins: the feeder originally read the matrix rows through
 * {@code Matrix4f.get(int, int)}, which on JOML 1.10.5 returns the element transposed
 * relative to the logical matrix {@code transform} applies (a translation's x offset,
 * logically at row 0 / column 3, comes back from {@code get(3, 0)}). The extraction
 * therefore produced the transposed matrix's frustum - a frustum around the world origin
 * instead of around the camera, invisible near the origin and grossly wrong at large
 * coordinates. The production read now goes through the column-major flat array
 * ({@code flat[col * 4 + row]}), the same read the vulkan-side dual-cull contract test
 * pins for its fixtures.
 */
class ShaderFrameStateFeederFrustumTest {

    /**
     * Reference Gribb-Hartmann extraction over the flat column-major array, written
     * independently of the production method: rows are flat[col * 4 + row], planes are
     * row3 +/- rowN, each normalised. A production read through any other route (in
     * particular the transposed get(int, int)) diverges from this on asymmetric
     * matrices by whole plane coefficients, not by rounding.
     */
    private static float[] referencePlanes(Matrix4f m) {
        float[] flat = m.get(new float[16]);
        float[][] rows = new float[4][4];
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                rows[row][col] = flat[col * 4 + row];
            }
        }
        float[] planes = new float[FrustumPlanes.FLOAT_COUNT];
        writePlane(planes, FrustumPlanes.LEFT, rows[3], rows[0], 1.0f);
        writePlane(planes, FrustumPlanes.RIGHT, rows[3], rows[0], -1.0f);
        writePlane(planes, FrustumPlanes.BOTTOM, rows[3], rows[1], 1.0f);
        writePlane(planes, FrustumPlanes.TOP, rows[3], rows[1], -1.0f);
        writePlane(planes, FrustumPlanes.NEAR, rows[3], rows[2], 1.0f);
        writePlane(planes, FrustumPlanes.FAR, rows[3], rows[2], -1.0f);
        return planes;
    }

    private static void writePlane(float[] out, int plane, float[] row3, float[] rowN, float sign) {
        float a = row3[0] + sign * rowN[0];
        float b = row3[1] + sign * rowN[1];
        float c = row3[2] + sign * rowN[2];
        float d = row3[3] + sign * rowN[3];
        float length = (float) Math.sqrt(a * a + b * b + c * c);
        if (length > 0.0f) {
            a /= length;
            b /= length;
            c /= length;
            d /= length;
        }
        int offset = plane * 4;
        out[offset] = a;
        out[offset + 1] = b;
        out[offset + 2] = c;
        out[offset + 3] = d;
    }

    /** projection * view for a perspective camera, the feeder's main-view construction. */
    private static Matrix4f perspectiveCombined(Vector3f eye, Vector3f target) {
        return perspectiveCombined(eye, target, 0.05f, 320f);
    }

    private static Matrix4f perspectiveCombined(Vector3f eye, Vector3f target,
                                                float near, float far) {
        Matrix4f view = new Matrix4f().lookAt(eye, target, new Vector3f(0f, 1f, 0f));
        Matrix4f projection = new Matrix4f().perspective(
                (float) Math.toRadians(70.0), 16f / 9f, near, far);
        return new Matrix4f(projection).mul(view);
    }

    @Test
    void extractedPlanesMatchTheFlatReadReferenceOnAnAsymmetricMatrix() {
        // Translation plus rotation, deliberately non-symmetric in every component, so a
        // transposed read changes the planes materially rather than cosmetically.
        Matrix4f combined = perspectiveCombined(
                new Vector3f(1000f, 64f, -2000f), new Vector3f(1013f, 51f, -1990f));
        float[] expected = referencePlanes(combined);
        FrustumPlanes actual = ShaderFrameStateFeeder.frustumFromMatrix(combined);
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            for (int component = 0; component < 4; component++) {
                assertEquals(expected[plane * 4 + component], actual.coefficient(plane, component),
                        1e-5f, "plane " + plane + " component " + component);
            }
        }
    }

    @Test
    void extractedFrustumClassifiesPointsLikeTheClipSpaceReference() {
        Vector3f eye = new Vector3f(1000f, 64f, -2000f);
        // A near plane of 5 rather than the production 0.05: with near << depth, the
        // clip z of every visible point compresses to within a fraction of a percent
        // of w, and no point would clear the decisiveness margin below on the z axis.
        // The contract under test is the extraction, not the projection parameters.
        Matrix4f combined = perspectiveCombined(eye, new Vector3f(1000f, 60f, -1990f), 5f, 300f);
        FrustumPlanes frustum = ShaderFrameStateFeeder.frustumFromMatrix(combined);

        int decisiveInside = 0;
        int decisiveOutside = 0;
        for (int dx = -160; dx <= 160; dx += 40) {
            for (int dy = -80; dy <= 80; dy += 40) {
                for (int dz = -160; dz <= 240; dz += 40) {
                    double x = eye.x + dx;
                    double y = eye.y + dy;
                    double z = eye.z + dz;
                    // Independent reference: clip coordinates via transform alone.
                    Vector4f clip = combined.transform(new Vector4f((float) x, (float) y, (float) z, 1f));
                    float w = clip.w;
                    Boolean clipInside = null;
                    if (w > 0.5f && Math.abs(clip.x) <= 0.97f * w
                            && Math.abs(clip.y) <= 0.97f * w && Math.abs(clip.z) <= 0.97f * w) {
                        clipInside = Boolean.TRUE;
                    } else if (w <= 0.0f || Math.abs(clip.x) >= 1.03f * w
                            || Math.abs(clip.y) >= 1.03f * w || Math.abs(clip.z) >= 1.03f * w) {
                        clipInside = Boolean.FALSE;
                    }
                    if (clipInside == null) {
                        continue; // within the boundary margin; float noise decides, not the contract
                    }
                    boolean frustumInside = true;
                    for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
                        if (frustum.signedDistance(plane, x, y, z) < 0.0f) {
                            frustumInside = false;
                            break;
                        }
                    }
                    if (clipInside) {
                        decisiveInside++;
                    } else {
                        decisiveOutside++;
                    }
                    assertEquals(clipInside, frustumInside,
                            "classification mismatch at (" + x + ", " + y + ", " + z + ")");
                }
            }
        }
        // The grid must actually exercise both sides decisively, or the agreement above
        // proves nothing.
        assertTrue(decisiveInside >= 25, "only " + decisiveInside + " decisive inside points");
        assertTrue(decisiveOutside >= 25, "only " + decisiveOutside + " decisive outside points");
    }

    @Test
    void shadowFrustumAtLargeCoordinatesIsCentredOnTheCamera() {
        // The feeder's shadow construction (ortho * lookAt from sun-offset eye to the
        // camera position) with the camera at large world coordinates - the shape the
        // transposed read broke, returning a frustum around the origin instead.
        Vector3f camera = new Vector3f(29_999_900f, 70f, -29_999_900f);
        Vector3f sunDir = new Vector3f(0.55f, 0.75f, 0.37f).normalize();
        Vector3f eye = new Vector3f(camera).add(new Vector3f(sunDir).mul(160f));
        Matrix4f view = new Matrix4f().lookAt(eye, camera, new Vector3f(0f, 1f, 0f));
        Matrix4f projection = new Matrix4f().ortho(-160f, 160f, -160f, 160f, 0.05f, 320f);
        Matrix4f combined = new Matrix4f(projection).mul(view);
        FrustumPlanes frustum = ShaderFrameStateFeeder.frustumFromMatrix(combined);

        // The camera position is the centre of the shadow frustum: every plane must
        // clear it by roughly the half-extent (160), allowing a few units of float
        // error at 3e7 coordinate scale.
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            float distance = frustum.signedDistance(plane, camera.x, camera.y, camera.z);
            assertTrue(distance >= 100f,
                    "plane " + plane + " clears the camera by only " + distance);
        }
        // A point near the camera is inside on every plane too.
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            float distance = frustum.signedDistance(plane,
                    camera.x + 16f, camera.y - 8f, camera.z + 24f);
            assertTrue(distance >= 90f,
                    "plane " + plane + " clears a near-camera point by only " + distance);
        }
        // The world origin, ~4.2e7 away, is decisively outside: at least one plane
        // rejects it by a wide margin. The transposed read inverted exactly this.
        float worst = Float.POSITIVE_INFINITY;
        for (int plane = 0; plane < FrustumPlanes.PLANE_COUNT; plane++) {
            worst = Math.min(worst, frustum.signedDistance(plane, 0.0, 70.0, 0.0));
        }
        assertTrue(worst < -1_000_000f, "origin's worst plane distance is " + worst);
    }
}
