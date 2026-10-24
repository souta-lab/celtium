package org.soutalab.celtium.vulkan;

/**
 * Texture-coordinate generation (GL's {@code glTexGen}) owned by the Vulkan frontend, mirrored at
 * {@code GlStateManager}'s texgen entry points.
 * <p>
 * Measured need: the raw-GL stub treated {@code glTexGen*} as no-ops, so every texgen draw arrived as plain
 * {@code POSITION_COLOR} and drew untextured. The end portal and end gateway are vanilla's users: each of their fifteen
 * layers generates S, T and R from eye-linear planes and then multiplies by a texture matrix that ends in
 * {@code PROJECTION * MODELVIEW}, which after the divide by q makes the coordinate a function of the fragment's screen
 * position.
 * <p>
 * Only {@code GL_EYE_LINEAR} is modelled, which is what vanilla uses. A coordinate whose generation is disabled takes
 * the current texture coordinate, which for a layout without one is GL's initial {@code (0, 0, 0, 1)}.
 * <p>
 * Matrices are column-major, as GL and {@link VulkanGlState} store them. Threading: render thread only.
 */
public final class NativeTexGen {
    private static final NativeTexGen CURRENT = new NativeTexGen();

    public static NativeTexGen current() { return CURRENT; }

    public static final int S = 0;
    public static final int T = 1;
    public static final int R = 2;
    public static final int Q = 3;
    public static final int GL_EYE_LINEAR = 0x2400;
    public static final int GL_OBJECT_LINEAR = 0x2401;
    public static final int GL_OBJECT_PLANE = 0x2501;
    public static final int GL_EYE_PLANE = 0x2502;

    private final boolean[] enabled = new boolean[4];
    private final int[] mode = {GL_EYE_LINEAR, GL_EYE_LINEAR, GL_EYE_LINEAR, GL_EYE_LINEAR};
    /** Eye planes as GL stores them: the given plane multiplied by the inverse of the model view at the call. */
    private final float[][] eyePlane = {{1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 0, 0}, {0, 0, 0, 0}};
    private final float[] inverseScratch = new float[16];

    /** The matrix mirror whose model view an eye plane is fixed under; set by the backend that owns the mirror. */
    private VulkanGlState matrices;

    public void bindMatrices(VulkanGlState state) {
        this.matrices = state;
    }

    /** {@link #setPlane(int, int, float, float, float, float, float[])} under the bound mirror's live model view. */
    public void setPlane(int coord, int pname, float a, float b, float c, float d) {
        VulkanGlState state = this.matrices;
        setPlane(coord, pname, a, b, c, d,
                state == null ? null : state.peekMatrix(VulkanGlState.MATRIX_MODE_MODELVIEW));
    }

    public void setEnabled(int coord, boolean on) {
        this.enabled[coord] = on;
    }

    public void setMode(int coord, int glMode) {
        this.mode[coord] = glMode;
    }

    /**
     * Records a plane. {@code GL_EYE_PLANE} is transformed by the inverse of {@code modelView} now, exactly as GL does
     * at the call, so later model-view changes do not move it. Object planes are not used by any modelled mode.
     */
    public void setPlane(int coord, int pname, float a, float b, float c, float d, float[] modelView) {
        if (pname != GL_EYE_PLANE) {
            return;
        }
        float[] inv = this.inverseScratch;
        if (modelView == null || !invert(modelView, inv)) {
            setIdentity(inv);
        }
        // Row vector times matrix: p'[j] = sum_i p[i] * inv[i][j], with inv[i][j] at inv[j * 4 + i].
        float[] out = this.eyePlane[coord];
        for (int j = 0; j < 4; j++) {
            out[j] = a * inv[j * 4] + b * inv[j * 4 + 1] + c * inv[j * 4 + 2] + d * inv[j * 4 + 3];
        }
    }

    /** {@return whether a draw now generates S and T from eye-linear planes}, the case the texgen pipeline draws. */
    public boolean eyeLinearST() {
        return this.enabled[S] && this.enabled[T] && this.mode[S] == GL_EYE_LINEAR && this.mode[T] == GL_EYE_LINEAR
                && (!this.enabled[R] || this.mode[R] == GL_EYE_LINEAR)
                && (!this.enabled[Q] || this.mode[Q] == GL_EYE_LINEAR);
    }

    /**
     * Writes the S, T and Q rows of {@code textureMatrix * G} into {@code out[0..11]}, where row k of G is the
     * generated coordinate k as a function of the homogeneous eye position. Multiplying these rows by a position-to-eye
     * matrix ({@link #rowsForPosition}) gives what a vertex shader needs: {@code (s, t, q)}, and the texture is sampled
     * at {@code (s / q, t / q)}.
     */
    public void eyeRows(float[] textureMatrix, float[] out) {
        float[][] g = new float[4][];
        for (int k = 0; k < 4; k++) {
            if (this.enabled[k]) {
                g[k] = this.eyePlane[k];
            } else {
                g[k] = k == Q ? CONSTANT_ONE : ZERO;
            }
        }
        int[] rows = {0, 1, 3};
        for (int r = 0; r < 3; r++) {
            int row = rows[r];
            for (int col = 0; col < 4; col++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    float m = textureMatrix == null ? (row == k ? 1.0f : 0.0f) : textureMatrix[k * 4 + row];
                    sum += m * g[k][col];
                }
                out[r * 4 + col] = sum;
            }
        }
    }

    /** {@code out = rows * positionToEye}: the eye rows re-expressed over the positions a group's vertices carry. */
    public static void rowsForPosition(float[] eyeRows, float[] positionToEye, float[] out) {
        for (int r = 0; r < 3; r++) {
            for (int col = 0; col < 4; col++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    float e = positionToEye == null ? (k == col ? 1.0f : 0.0f) : positionToEye[col * 4 + k];
                    sum += eyeRows[r * 4 + k] * e;
                }
                out[r * 4 + col] = sum;
            }
        }
    }

    private static final float[] ZERO = {0, 0, 0, 0};
    /** The constant 1, as a plane over homogeneous positions whose w is 1. */
    private static final float[] CONSTANT_ONE = {0, 0, 0, 1};

    private static void setIdentity(float[] m) {
        java.util.Arrays.fill(m, 0.0f);
        m[0] = m[5] = m[10] = m[15] = 1.0f;
    }

    /** General 4x4 inverse (column-major in and out). {@return false when singular}. */
    static boolean invert(float[] m, float[] inv) {
        float[] t = new float[16];
        t[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        t[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        t[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        t[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        t[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        t[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        t[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        t[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        t[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        t[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        t[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        t[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        t[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        t[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        t[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        t[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        float det = m[0] * t[0] + m[1] * t[4] + m[2] * t[8] + m[3] * t[12];
        if (det == 0.0f || Float.isNaN(det)) {
            return false;
        }
        float inverse = 1.0f / det;
        for (int i = 0; i < 16; i++) {
            inv[i] = t[i] * inverse;
        }
        return true;
    }
}
