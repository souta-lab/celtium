package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The end portal's texgen, reduced to the identity it relies on, and the GL rules the rows are built from. */
class NativeTexGenTest {

    /** Column-major rotation about Y by {@code degrees} followed by a translation. */
    private static float[] affine(double degrees, float tx, float ty, float tz) {
        float c = (float) Math.cos(Math.toRadians(degrees));
        float s = (float) Math.sin(Math.toRadians(degrees));
        return new float[] {c, 0, -s, 0, 0, 1, 0, 0, s, 0, c, 0, tx, ty, tz, 1};
    }

    private static float[] arbitraryTextureMatrix() {
        return new float[] {0.3f, -0.2f, 0.05f, 0.01f, 0.4f, 0.7f, -0.1f, 0.02f,
                0.11f, 0.13f, 0.9f, -0.5f, 0.25f, 1.5f, 0.2f, 1.1f};
    }

    /**
     * Vanilla's TileEntityEndPortalRenderer: S, T, R from the eye planes (1,0,0,0), (0,1,0,0), (0,0,1,0) set under the
     * same model view the quads are drawn with, Q not generated. The generated (s, t, r, q) is then the vertex's own
     * position with w = 1, so the rows over that position are the texture matrix's rows 0, 1 and 3.
     */
    @Test
    void endPortalPlanesUnderTheDrawModelViewReduceToTheTextureMatrixRows() {
        NativeTexGen texGen = new NativeTexGen();
        float[] modelView = affine(37.0, 3.5f, -2.0f, 11.0f);
        texGen.setMode(NativeTexGen.S, NativeTexGen.GL_EYE_LINEAR);
        texGen.setMode(NativeTexGen.T, NativeTexGen.GL_EYE_LINEAR);
        texGen.setMode(NativeTexGen.R, NativeTexGen.GL_EYE_LINEAR);
        texGen.setPlane(NativeTexGen.S, NativeTexGen.GL_EYE_PLANE, 1, 0, 0, 0, modelView);
        texGen.setPlane(NativeTexGen.T, NativeTexGen.GL_EYE_PLANE, 0, 1, 0, 0, modelView);
        texGen.setPlane(NativeTexGen.R, NativeTexGen.GL_EYE_PLANE, 0, 0, 1, 0, modelView);
        texGen.setEnabled(NativeTexGen.S, true);
        texGen.setEnabled(NativeTexGen.T, true);
        texGen.setEnabled(NativeTexGen.R, true);
        assertTrue(texGen.eyeLinearST());

        float[] texture = arbitraryTextureMatrix();
        float[] eyeRows = new float[12];
        texGen.eyeRows(texture, eyeRows);
        float[] rows = new float[12];
        NativeTexGen.rowsForPosition(eyeRows, modelView, rows);
        int[] textureRows = {0, 1, 3};
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(texture[col * 4 + textureRows[r]], rows[r * 4 + col], 1e-4f,
                        "row " + r + " column " + col);
            }
        }
    }

    /** A disabled coordinate is the current texture coordinate, GL's initial (0, 0, 0, 1) for a layout without one. */
    @Test
    void disabledCoordinatesTakeTheInitialTextureCoordinate() {
        NativeTexGen texGen = new NativeTexGen();
        assertFalse(texGen.eyeLinearST());
        float[] eyeRows = new float[12];
        texGen.eyeRows(null, eyeRows);
        assertArrayEquals(new float[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1}, eyeRows, 0.0f);
    }

    /** The eye plane is fixed when it is specified: changing the model view afterwards must not move it. */
    @Test
    void eyePlaneIsTransformedAtSpecificationTime() {
        NativeTexGen texGen = new NativeTexGen();
        float[] modelView = affine(90.0, 0, 0, 0);
        texGen.setPlane(NativeTexGen.S, NativeTexGen.GL_EYE_PLANE, 1, 0, 0, 0, modelView);
        texGen.setEnabled(NativeTexGen.S, true);
        texGen.setEnabled(NativeTexGen.T, true);
        float[] eyeRows = new float[12];
        texGen.eyeRows(null, eyeRows);
        // Rotating Y by 90 degrees maps object +X to eye -Z, so the object plane x = 0 is the eye plane -z = 0.
        assertEquals(0.0f, eyeRows[0], 1e-6f);
        assertEquals(0.0f, eyeRows[1], 1e-6f);
        assertEquals(-1.0f, eyeRows[2], 1e-6f);
        assertEquals(0.0f, eyeRows[3], 1e-6f);
    }

    @Test
    void inverseOfAnAffineMatrixRoundTrips() {
        float[] m = affine(23.0, 4, 5, 6);
        float[] inv = new float[16];
        assertTrue(NativeTexGen.invert(m, inv));
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0;
                for (int k = 0; k < 4; k++) {
                    sum += m[k * 4 + row] * inv[col * 4 + k];
                }
                assertEquals(row == col ? 1.0f : 0.0f, sum, 1e-5f);
            }
        }
    }
}
