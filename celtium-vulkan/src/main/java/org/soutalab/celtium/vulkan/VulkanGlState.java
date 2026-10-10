package org.soutalab.celtium.vulkan;

import java.util.ArrayDeque;

/**
 * The renderer state that Minecraft's and Forge's {@code GlStateManager} calls are translated into.
 * <p>
 * This is the state half of the Forge API swap ({@code docs/forge-api-vulkan-inventory.md}). A mod calls
 * {@code GlStateManager.func_179147_l()} (blend on) or pushes a matrix; those calls must not reach OpenGL. They land
 * here instead, and the draw entry turns the accumulated state plus the batch's vertex format into a pipeline key,
 * so a pipeline is only created when a draw actually needs it - the deferred selection the policy asks for.
 * <p>
 * Matrices are kept as {@code float[16]} column-major arrays, matching what the terrain path already uploads into its
 * push-constant block, so no conversion is needed at draw time and this class carries no matrix library dependency
 * that the shadow step would have to relocate.
 * <p>
 * Threading: render thread only, like the rest of the backend.
 */
public final class VulkanGlState {
    private static final int MATRIX_FLOATS = 16;

    /** The default state Minecraft starts a frame with, as far as this renderer tracks it. */
    private final ArrayDeque<float[]> modelViewStack = new ArrayDeque<float[]>();
    private final ArrayDeque<float[]> projectionStack = new ArrayDeque<float[]>();
    // Texture matrices are per texture unit in the fixed pipeline: glMatrixMode(GL_TEXTURE) addresses the matrix of
    // whichever unit setActiveTexture last selected. A single shared stack let the lightmap's unit-1 transform
    // (scale 1/256, translate 8, written every frame by enableLightmap) leak onto unit 0, where a texture-matrix
    // read for the item glint would then report the lightmap's matrix instead. Vanilla uses units 0 and 1; other
    // units clamp to 0, which is what an out-of-range setActiveTexture would hit anyway.
    private final ArrayDeque<float[]> textureStack0 = new ArrayDeque<float[]>();
    private final ArrayDeque<float[]> textureStack1 = new ArrayDeque<float[]>();

    private int matrixMode = MATRIX_MODE_MODELVIEW;
    private boolean blendEnabled;
    private int blendSrcRgb = BLEND_SRC_ALPHA;
    private int blendDstRgb = BLEND_ONE_MINUS_SRC_ALPHA;
    private int blendSrcAlpha = BLEND_ONE;
    private int blendDstAlpha = BLEND_ONE_MINUS_SRC_ALPHA;
    private boolean depthTestEnabled = true;
    private int depthFunc = DEPTH_LEQUAL;
    private boolean depthMask = true;
    private boolean cullEnabled;
    private int cullFace = CULL_BACK;
    private boolean alphaTestEnabled;
    private int alphaFunc = ALPHA_GREATER;
    private float alphaRef = 0.1f;
    private boolean lightingEnabled;
    private boolean texture2dEnabled = true;
    /** The GL name of the bound texture; 0 means none, which is what the immediate path refuses to draw with. */
    private int boundTextureName;
    private float alphaFuncReference = 0.1f;
    private int shadeModel = SHADE_SMOOTH;
    private float colorRed = 1.0f;
    private float colorGreen = 1.0f;
    private float colorBlue = 1.0f;
    private float colorAlpha = 1.0f;
    private long boundTexture;
    private int activeTextureUnit;

    /** Matrix mode identifiers, matching the values Minecraft passes around. */
    public static final int MATRIX_MODE_MODELVIEW = 0;
    public static final int MATRIX_MODE_PROJECTION = 1;
    public static final int MATRIX_MODE_TEXTURE = 2;

    /** Blend factor identifiers, matching the {@code GlStateManager.SourceFactor}/{@code DestFactor} ordinals. */
    public static final int BLEND_ZERO = 0;
    public static final int BLEND_ONE = 1;
    public static final int BLEND_SRC_ALPHA = 2;
    public static final int BLEND_ONE_MINUS_SRC_ALPHA = 3;
    public static final int BLEND_DST_COLOR = 4;
    public static final int BLEND_ONE_MINUS_DST_COLOR = 5;
    public static final int BLEND_SRC_COLOR = 6;
    public static final int BLEND_ONE_MINUS_SRC_COLOR = 7;
    public static final int BLEND_DST_ALPHA = 8;
    public static final int BLEND_ONE_MINUS_DST_ALPHA = 9;

    /** Depth and alpha function identifiers, matching the values Minecraft passes around. */
    public static final int DEPTH_NEVER = 0;
    public static final int DEPTH_LESS = 1;
    public static final int DEPTH_EQUAL = 2;
    public static final int DEPTH_LEQUAL = 3;
    public static final int DEPTH_GREATER = 4;
    public static final int DEPTH_NOTEQUAL = 5;
    public static final int DEPTH_GEQUAL = 6;
    public static final int DEPTH_ALWAYS = 7;
    public static final int ALPHA_NEVER = 0;
    public static final int ALPHA_LESS = 1;
    public static final int ALPHA_EQUAL = 2;
    public static final int ALPHA_LEQUAL = 3;
    public static final int ALPHA_GREATER = 4;
    public static final int ALPHA_NOTEQUAL = 5;
    public static final int ALPHA_GEQUAL = 6;
    public static final int ALPHA_ALWAYS = 7;

    /** Cull face identifiers. */
    public static final int CULL_FRONT = 0;
    public static final int CULL_BACK = 1;
    public static final int CULL_BOTH = 2;

    /** Shade model identifiers. */
    public static final int SHADE_FLAT = 0;
    public static final int SHADE_SMOOTH = 1;

    public VulkanGlState() {
        this.modelViewStack.push(identity());
        this.projectionStack.push(identity());
        this.textureStack0.push(identity());
        this.textureStack1.push(identity());
    }

    /** Pushes the current matrix of the active mode; paired with {@link #popMatrix()}. */
    public void pushMatrix() {
        stack(this.matrixMode).push(copy(top(this.matrixMode)));
    }

    public void popMatrix() {
        ArrayDeque<float[]> stack = stack(this.matrixMode);
        if (stack.size() > 1) {
            stack.pop();
        }
    }

    /** Replaces the active matrix with the identity, the {@code glLoadIdentity} case. */
    public void loadIdentity() {
        System.arraycopy(identity(), 0, top(this.matrixMode), 0, MATRIX_FLOATS);
    }

    /**
     * Applies the same transform {@code glTranslate}/{@code glRotate}/{@code glScale} would, by multiplying the
     * active matrix in place. The argument order and the sign of the rotation follow OpenGL so a mirrored matrix is
     * numerically what the GL call would have produced.
     */
    public void translate(double x, double y, double z) {
        float[] matrix = identity();
        matrix[12] = (float) x;
        matrix[13] = (float) y;
        matrix[14] = (float) z;
        multiplyMatrix(matrix);
    }

    public void scale(double x, double y, double z) {
        float[] matrix = identity();
        matrix[0] = (float) x;
        matrix[5] = (float) y;
        matrix[10] = (float) z;
        multiplyMatrix(matrix);
    }

    public void rotate(double angleDegrees, double x, double y, double z) {
        double radians = Math.toRadians(angleDegrees);
        double length = Math.sqrt((x * x) + (y * y) + (z * z));
        if (length == 0.0) {
            return;
        }
        double nx = x / length;
        double ny = y / length;
        double nz = z / length;
        float c = (float) Math.cos(radians);
        float s = (float) Math.sin(radians);
        float t = 1.0f - c;
        float[] matrix = identity();
        // Column-major rotation matrix around the normalised axis, as glRotatef builds it.
        matrix[0] = (float) ((nx * nx * t) + c);
        matrix[1] = (float) ((ny * nx * t) + (nz * s));
        matrix[2] = (float) ((nz * nx * t) - (ny * s));
        matrix[4] = (float) ((nx * ny * t) - (nz * s));
        matrix[5] = (float) ((ny * ny * t) + c);
        matrix[6] = (float) ((nz * ny * t) + (nx * s));
        matrix[8] = (float) ((nx * nz * t) + (ny * s));
        matrix[9] = (float) ((ny * nz * t) - (nx * s));
        matrix[10] = (float) ((nz * nz * t) + c);
        multiplyMatrix(matrix);
    }

    /** Replaces the active matrix from a column-major buffer, the {@code glLoadMatrix} case. */
    public void loadMatrixBuffer(java.nio.FloatBuffer buffer) {
        if (buffer == null || buffer.remaining() < MATRIX_FLOATS) {
            return;
        }
        float[] matrix = new float[MATRIX_FLOATS];
        int position = buffer.position();
        for (int i = 0; i < MATRIX_FLOATS; i++) {
            matrix[i] = buffer.get(position + i);
        }
        loadMatrix(matrix);
    }

    /** Multiplies the active matrix by a column-major buffer, the {@code glMultMatrix} case. */
    public void multiplyMatrixBuffer(java.nio.FloatBuffer buffer) {
        if (buffer == null || buffer.remaining() < MATRIX_FLOATS) {
            return;
        }
        float[] matrix = new float[MATRIX_FLOATS];
        int position = buffer.position();
        for (int i = 0; i < MATRIX_FLOATS; i++) {
            matrix[i] = buffer.get(position + i);
        }
        multiplyMatrix(matrix);
    }

    /** Replaces the active matrix; Minecraft uses this for {@code glLoadIdentity} and ortho/projection setup. */
    public void loadMatrix(float[] matrix) {
        if (matrix == null || matrix.length < MATRIX_FLOATS) {
            throw new IllegalArgumentException("a matrix must have at least " + MATRIX_FLOATS + " floats");
        }
        System.arraycopy(matrix, 0, top(this.matrixMode), 0, MATRIX_FLOATS);
    }

    /** Multiplies the active matrix in place, the {@code glMultMatrixf} case. */
    public void multiplyMatrix(float[] matrix) {
        if (matrix == null || matrix.length < MATRIX_FLOATS) {
            throw new IllegalArgumentException("a matrix must have at least " + MATRIX_FLOATS + " floats");
        }
        float[] result = new float[MATRIX_FLOATS];
        multiply(top(this.matrixMode), matrix, result);
        System.arraycopy(result, 0, top(this.matrixMode), 0, MATRIX_FLOATS);
    }

    /** {@return a copy of the active matrix}, for the push-constant block a draw uploads. */
    public float[] matrix(int mode) {
        return copy(top(mode));
    }

    /**
     * {@return a copy of the named texture unit's matrix}, regardless of which unit is active: the immediate path's
     * draws all sample unit 0, so the unit-1 lightmap matrix - which the game rewrites every frame - must not read
     * as theirs.
     */
    public float[] textureMatrix(int unit) {
        return copy((unit == 1 ? this.textureStack1 : this.textureStack0).peek());
    }

    /**
     * Writes sixteen values into the current top of a stack.
     * <p>
     * {@link #matrix(int)} returns a **copy**, so writing into what it returns - which two earlier changes did - silently
     * discarded the values. The counters caught it: the array written to and the array read back had different identities
     * while nothing reset either of them. Anything that means to change the mirrored state has to go through here.
     */
    public void setMatrix(int mode, float[] values) {
        System.arraycopy(values, 0, top(mode), 0, 16);
    }

    public int matrixMode() {
        return this.matrixMode;
    }

    /**
     * Seeds the projection and modelview stacks from OpenGL once, at installation.
     * <p>
     * The mirrored stack starts empty, so everything Minecraft set up before the hooks were installed is missing from it -
     * the first dump showed exactly that as a modelview without its translation. This is a single read used as a
     * bootstrap, not a per-batch one, and it happens before any mirror comparison is recorded.
     */
    public void copyMatricesFrom(float[] projection, float[] modelView) {
        setMatrix(MATRIX_MODE_PROJECTION, projection);
        setMatrix(MATRIX_MODE_MODELVIEW, modelView);
    }

    public void matrixMode(int mode) {
        this.matrixMode = mode;
    }

    public boolean blendEnabled() {
        return this.blendEnabled;
    }

    public void blendEnabled(boolean enabled) {
        this.blendEnabled = enabled;
    }

    /**
     * {@return whether the current factors actually mix anything}
     * <p>
     * Minecraft 1.12.2 leaves blending enabled and expresses "no blending" as the {@code ONE, ZERO} function rather than by
     * disabling it - which is why counting the enable bit reported every batch as translucent. The function is the honest
     * question to ask.
     */
    public boolean blendMixes() {
        return this.blendSrcRgb != BLEND_ONE || this.blendDstRgb != BLEND_ZERO;
    }

    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.blendSrcRgb = srcRgb;
        this.blendDstRgb = dstRgb;
        this.blendSrcAlpha = srcAlpha;
        this.blendDstAlpha = dstAlpha;
    }

    public int blendSrcRgb() {
        return this.blendSrcRgb;
    }

    public int blendDstRgb() {
        return this.blendDstRgb;
    }

    public int blendSrcAlpha() {
        return this.blendSrcAlpha;
    }

    public int blendDstAlpha() {
        return this.blendDstAlpha;
    }

    public boolean depthTestEnabled() {
        return this.depthTestEnabled;
    }

    public void depthTestEnabled(boolean enabled) {
        this.depthTestEnabled = enabled;
    }

    public int depthFunc() {
        return this.depthFunc;
    }

    public void depthFunc(int func) {
        this.depthFunc = func;
    }

    public boolean depthMask() {
        return this.depthMask;
    }

    public void depthMask(boolean mask) {
        this.depthMask = mask;
    }

    /** The reference value of the alpha test, as the fragment shader compares against it. */
    public void alphaFuncReference(float reference) {
        this.alphaFuncReference = reference;
    }

    public float alphaFuncReference() {
        return this.alphaFuncReference;
    }

    public boolean cullEnabled() {
        return this.cullEnabled;
    }

    public void cullEnabled(boolean enabled) {
        this.cullEnabled = enabled;
    }

    public int cullFace() {
        return this.cullFace;
    }

    public void cullFace(int face) {
        this.cullFace = face;
    }

    public boolean alphaTestEnabled() {
        return this.alphaTestEnabled;
    }

    public void alphaTestEnabled(boolean enabled) {
        this.alphaTestEnabled = enabled;
    }

    public int alphaFunc() {
        return this.alphaFunc;
    }

    public float alphaRef() {
        return this.alphaRef;
    }

    public void alphaFunc(int func, float reference) {
        this.alphaFunc = func;
        this.alphaRef = reference;
    }

    public boolean lightingEnabled() {
        return this.lightingEnabled;
    }

    public void lightingEnabled(boolean enabled) {
        this.lightingEnabled = enabled;
    }

    /** Records the GL name of the bound texture, as {@code GlStateManager.func_179144_i} reports it. */
    public void bindTexture(int glTextureId) {
        this.boundTextureName = glTextureId;
    }

    /** {@return the GL name of the bound texture}, or 0 when nothing is bound */
    public int boundTextureName() {
        return this.boundTextureName;
    }

    public boolean texture2dEnabled() {
        return this.texture2dEnabled;
    }

    public void texture2dEnabled(boolean enabled) {
        this.texture2dEnabled = enabled;
    }

    public int shadeModel() {
        return this.shadeModel;
    }

    public void shadeModel(int model) {
        this.shadeModel = model;
    }

    public void color(float red, float green, float blue, float alpha) {
        this.colorRed = red;
        this.colorGreen = green;
        this.colorBlue = blue;
        this.colorAlpha = alpha;
    }

    public float colorRed() {
        return this.colorRed;
    }

    public float colorGreen() {
        return this.colorGreen;
    }

    public float colorBlue() {
        return this.colorBlue;
    }

    public float colorAlpha() {
        return this.colorAlpha;
    }

    public long boundTexture() {
        return this.boundTexture;
    }

    public void boundTexture(long texture) {
        this.boundTexture = texture;
    }

    public int activeTextureUnit() {
        return this.activeTextureUnit;
    }

    public void activeTextureUnit(int unit) {
        this.activeTextureUnit = unit;
    }

    /**
     * {@return a key that changes exactly when a different pipeline is required}.
     * <p>
     * Blend factors, depth state, culling, alpha test, lighting and the shade model all bake into the pipeline
     * object, while matrices, colour and texture bindings do not (they are push constants and descriptors), so they
     * are deliberately left out of the key.
     */
    public long pipelineKey(int vertexFormatHash) {
        long key = vertexFormatHash;
        key = (key * 31) + (this.blendEnabled ? 1 : 0);
        key = (key * 31) + (this.blendEnabled ? this.blendSrcRgb : 0);
        key = (key * 31) + (this.blendEnabled ? this.blendDstRgb : 0);
        key = (key * 31) + (this.blendEnabled ? this.blendSrcAlpha : 0);
        key = (key * 31) + (this.blendEnabled ? this.blendDstAlpha : 0);
        key = (key * 31) + (this.depthTestEnabled ? this.depthFunc + 1 : 0);
        key = (key * 31) + (this.depthMask ? 1 : 0);
        key = (key * 31) + (this.cullEnabled ? this.cullFace + 1 : 0);
        key = (key * 31) + (this.alphaTestEnabled ? this.alphaFunc + 1 : 0);
        key = (key * 31) + (this.lightingEnabled ? 1 : 0);
        key = (key * 31) + this.shadeModel;
        return key;
    }

    /** {@return a one-line description of the state that affects pipeline selection}, for diagnostics */
    public String describe() {
        return "blend=" + this.blendEnabled + "(" + this.blendSrcRgb + "," + this.blendDstRgb + ","
                + this.blendSrcAlpha + "," + this.blendDstAlpha + ") depthTest=" + this.depthTestEnabled
                + " depthFunc=" + this.depthFunc + " depthMask=" + this.depthMask + " cull=" + this.cullEnabled
                + "(" + this.cullFace + ") alphaTest=" + this.alphaTestEnabled + "(" + this.alphaFunc + ","
                + this.alphaRef + ") lighting=" + this.lightingEnabled + " shade=" + this.shadeModel;
    }

    private ArrayDeque<float[]> stack(int mode) {
        switch (mode) {
            case MATRIX_MODE_PROJECTION:
                return this.projectionStack;
            case MATRIX_MODE_TEXTURE:
                return this.activeTextureUnit == 1 ? this.textureStack1 : this.textureStack0;
            case MATRIX_MODE_MODELVIEW:
            default:
                return this.modelViewStack;
        }
    }

    private float[] top(int mode) {
        return stack(mode).peek();
    }

    private static float[] identity() {
        float[] matrix = new float[MATRIX_FLOATS];
        matrix[0] = 1.0f;
        matrix[5] = 1.0f;
        matrix[10] = 1.0f;
        matrix[15] = 1.0f;
        return matrix;
    }

    private static float[] copy(float[] source) {
        float[] copy = new float[MATRIX_FLOATS];
        System.arraycopy(source, 0, copy, 0, MATRIX_FLOATS);
        return copy;
    }

    /** Column-major {@code result = left * right}, the order Minecraft's matrix stack implies. */
    private static void multiply(float[] left, float[] right, float[] result) {
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += left[k * 4 + row] * right[column * 4 + k];
                }
                result[column * 4 + row] = sum;
            }
        }
    }
}
