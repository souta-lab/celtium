package org.soutalab.celtium.vulkan.lane2;

import org.soutalab.celtium.core.state.LightStateRegister;
import org.soutalab.celtium.core.state.RenderStateKey;

/**
 * Lane 2's state mirror (design V040-FINAL-PLAN section 4.2, stage S3-a item 1; interface 3
 * contract in section 2.3): the GlStateManager hooks in forge122 write every lightweight
 * fixed-function state change here, and this mirror folds it into the one
 * {@link LightStateRegister} that Lane 2 reads and the direct-GL converter syncs from. From
 * this stage on, the register - not the backend SPI's per-setter fields, and not a re-read of
 * GlStateManager's own statics - is the single source of truth for the state Lane 2 draws
 * with: blend, depth, cull, colour mask, active texture unit, the bound texture and the
 * matrix stack.
 *
 * <p>What the 32-bit {@link RenderStateKey} cannot express (blend factors, the depth and
 * alpha functions, the current colour, texture-2D enable) is kept as plain fields beside the
 * register; the key is rebuilt from the mirrored fields on every key-affecting write, so the
 * key and the fields can never drift apart. The matrix stack lives in the register's
 * off-heap segment for the modelview mode - the stack entity and item renderers transform -
 * while the projection stack, which changes a handful of times per frame, is kept in a small
 * heap array beside it. Texture-matrix mode writes are counted and otherwise ignored: no
 * Lane 2 consumer reads a texture matrix.
 *
 * <p>Threading: render thread only, like every consumer of the register.
 */
public final class Lane2StateMirror {

    // GL constants, as ABI values: this class deliberately does not take an LWJGL dependency
    // for a dozen stable numbers.
    public static final int GL_ZERO = 0;
    public static final int GL_ONE = 1;
    public static final int GL_ALWAYS = 519;
    public static final int GL_LEQUAL = 515;
    public static final int GL_GREATER = 516;
    public static final int GL_FRONT = 1028;
    public static final int GL_BACK = 1029;
    public static final int GL_FRONT_AND_BACK = 1032;
    public static final int GL_MODELVIEW = 5888;
    public static final int GL_PROJECTION = 5889;
    public static final int GL_TEXTURE = 5890;

    private static final Lane2StateMirror CURRENT = new Lane2StateMirror();
    private static final int PROJECTION_STACK_DEPTH = 8;

    private final LightStateRegister register = new LightStateRegister();

    // Key-carried state.
    private boolean blendEnabled;
    private boolean depthTestEnabled = true;
    private boolean depthWriteEnabled = true;
    private int cullMode = RenderStateKey.CULL_BACK;
    private int colorWriteMask = 0xF;
    private int activeTextureUnit;

    // State the key does not carry.
    private int blendSrcRgb = GL_ONE;
    private int blendDstRgb = GL_ZERO;
    private int blendSrcAlpha = GL_ONE;
    private int blendDstAlpha = GL_ZERO;
    private int depthFunc = GL_LEQUAL;
    private int alphaFunc = GL_ALWAYS;
    private float alphaRef;
    private boolean texture2dEnabled = true;
    private float colorRed = 1.0f;
    private float colorGreen = 1.0f;
    private float colorBlue = 1.0f;
    private float colorAlpha = 1.0f;

    // Matrix state: modelview rides the register's stack; projection rides this small stack. The
    // matrix mode itself is owned by the immediate state (E5 seat A, Neon E4 ruling 2026-11-28):
    // the accessors below delegate to it, so the mode the funnel's stack machinery selects by and
    // the mode this mirror's own stack ops select by are one field.
    private final float[][] projectionStack = new float[PROJECTION_STACK_DEPTH][16];
    private int projectionDepth;
    private long textureModeMatrixOps;

    // The immediate funnel's working state (the former VulkanGlState), owned here since the E5
    // seat A move: one production instance, reached through immediate(). Its raster and texture
    // bindings are the shared singletons the backend's own field used to inject.
    private final Lane2ImmediateState immediate = new Lane2ImmediateState(
            org.soutalab.celtium.vulkan.NativeRasterState.current(),
            org.soutalab.celtium.vulkan.NativeTextureBindings.current());

    private Lane2StateMirror() {
        writeIdentity(this.projectionStack[0]);
        this.projectionDepth = 1;
        publishKey();
    }

    public static Lane2StateMirror current() {
        return CURRENT;
    }

    /** The register Lane 2 and the converter read. The mirror is its only writer. */
    public LightStateRegister register() {
        return this.register;
    }

    // -- blend ---------------------------------------------------------------

    public void setBlendEnabled(boolean enabled) {
        this.blendEnabled = enabled;
        publishKey();
    }

    public boolean blendEnabled() {
        return this.blendEnabled;
    }

    /** {@code GlStateManager.blendFunc}: one factor pair for RGB and alpha alike. */
    public void setBlendFunc(int srcFactor, int dstFactor) {
        setBlendFuncSeparate(srcFactor, dstFactor, srcFactor, dstFactor);
    }

    public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
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

    // -- depth / alpha ---------------------------------------------------------

    public void setDepthTestEnabled(boolean enabled) {
        this.depthTestEnabled = enabled;
        publishKey();
    }

    public void setDepthWriteEnabled(boolean enabled) {
        this.depthWriteEnabled = enabled;
        publishKey();
    }

    public void setDepthFunc(int glFunc) {
        this.depthFunc = glFunc;
    }

    public int depthFunc() {
        return this.depthFunc;
    }

    public void setAlphaFunc(int glFunc, float reference) {
        this.alphaFunc = glFunc;
        this.alphaRef = reference;
    }

    public int alphaFunc() {
        return this.alphaFunc;
    }

    public float alphaRef() {
        return this.alphaRef;
    }

    // -- cull / colour mask / texture unit --------------------------------------

    /** {@code GlStateManager.cullFace}: the GL face constant becomes the key's cull mode. */
    public void setCullFace(int glFace) {
        switch (glFace) {
            case GL_FRONT:
                this.cullMode = RenderStateKey.CULL_FRONT;
                break;
            case GL_BACK:
                this.cullMode = RenderStateKey.CULL_BACK;
                break;
            case GL_FRONT_AND_BACK:
                this.cullMode = RenderStateKey.CULL_FRONT_AND_BACK;
                break;
            default:
                throw new IllegalArgumentException("not a cull face: " + glFace);
        }
        publishKey();
    }

    public void setCullEnabled(boolean enabled) {
        if (!enabled) {
            this.cullMode = RenderStateKey.CULL_OFF;
            publishKey();
        } else if (this.cullMode == RenderStateKey.CULL_OFF) {
            // GL's default face is BACK; re-enabling after a disable restores it.
            this.cullMode = RenderStateKey.CULL_BACK;
            publishKey();
        }
    }

    public int cullMode() {
        return this.cullMode;
    }

    public void setColorMask(boolean red, boolean green, boolean blue, boolean alpha) {
        this.colorWriteMask = (red ? 1 : 0) | (green ? 2 : 0) | (blue ? 4 : 0) | (alpha ? 8 : 0);
        publishKey();
    }

    public int colorWriteMask() {
        return this.colorWriteMask;
    }

    public void setActiveTextureUnit(int unit) {
        if (unit < 0 || unit > 15) {
            throw new IllegalArgumentException("activeTextureUnit must be 0..15");
        }
        this.activeTextureUnit = unit;
        publishKey();
    }

    public int activeTextureUnit() {
        return this.activeTextureUnit;
    }

    // -- texture binding / current colour ----------------------------------------

    public void setTexture2dEnabled(boolean enabled) {
        this.texture2dEnabled = enabled;
    }

    public boolean texture2dEnabled() {
        return this.texture2dEnabled;
    }

    /** {@code GlStateManager.bindTexture}: the game's texture name, as bound. */
    public void bindTexture(int glTextureId) {
        this.register.setBoundTextureId(glTextureId);
    }

    public void setColor(float red, float green, float blue, float alpha) {
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

    // -- matrices ------------------------------------------------------------------

    public void setMatrixMode(int glMode) {
        if (glMode != GL_MODELVIEW && glMode != GL_PROJECTION && glMode != GL_TEXTURE) {
            throw new IllegalArgumentException("not a matrix mode: " + glMode);
        }
        this.immediate.matrixModeGl(glMode);
    }

    public int matrixMode() {
        return this.immediate.matrixModeGl();
    }

    /** {@return the immediate funnel's working state}, owned by this mirror (E5 seat A). */
    public Lane2ImmediateState immediate() {
        return this.immediate;
    }

    public void pushMatrix() {
        switch (matrixMode()) {
            case GL_MODELVIEW:
                this.register.pushMatrix();
                break;
            case GL_PROJECTION:
                if (this.projectionDepth >= PROJECTION_STACK_DEPTH) {
                    throw new IllegalStateException("projection stack overflow");
                }
                System.arraycopy(this.projectionStack[this.projectionDepth - 1], 0,
                        this.projectionStack[this.projectionDepth], 0, 16);
                this.projectionDepth++;
                break;
            default:
                this.textureModeMatrixOps++;
        }
    }

    public void popMatrix() {
        switch (matrixMode()) {
            case GL_MODELVIEW:
                this.register.popMatrix();
                break;
            case GL_PROJECTION:
                if (this.projectionDepth <= 1) {
                    throw new IllegalStateException("projection stack underflow");
                }
                this.projectionDepth--;
                break;
            default:
                this.textureModeMatrixOps++;
        }
    }

    public void loadIdentity() {
        switch (matrixMode()) {
            case GL_MODELVIEW:
                this.register.loadIdentity();
                break;
            case GL_PROJECTION:
                writeIdentity(this.projectionStack[this.projectionDepth - 1]);
                break;
            default:
                this.textureModeMatrixOps++;
        }
    }

    /** Post-multiplies the current matrix by the given column-major 4x4 ({@code glMultMatrix}). */
    public void multMatrix(float[] matrix) {
        if (matrix == null || matrix.length != 16) {
            throw new IllegalArgumentException("a matrix is exactly 16 floats");
        }
        switch (matrixMode()) {
            case GL_MODELVIEW: {
                float[] top = new float[16];
                this.register.currentMatrix(top);
                this.register.setCurrentMatrix(multiply(top, matrix));
                break;
            }
            case GL_PROJECTION: {
                float[] top = this.projectionStack[this.projectionDepth - 1];
                System.arraycopy(multiply(top, matrix), 0, top, 0, 16);
                break;
            }
            default:
                this.textureModeMatrixOps++;
        }
    }

    public void translate(float x, float y, float z) {
        float[] m = new float[16];
        writeIdentity(m);
        m[12] = x;
        m[13] = y;
        m[14] = z;
        multMatrix(m);
    }

    public void scale(float x, float y, float z) {
        float[] m = new float[16];
        m[0] = x;
        m[5] = y;
        m[10] = z;
        m[15] = 1.0f;
        multMatrix(m);
    }

    /** {@code GlStateManager.rotate}: angle in degrees around the (unnormalised) axis. */
    public void rotate(float angleDegrees, float x, float y, float z) {
        double length = Math.sqrt(x * x + y * y + z * z);
        if (length == 0.0) {
            return;
        }
        double nx = x / length;
        double ny = y / length;
        double nz = z / length;
        double radians = Math.toRadians(angleDegrees);
        double c = Math.cos(radians);
        double s = Math.sin(radians);
        double t = 1.0 - c;
        float[] m = new float[16];
        m[0] = (float) (t * nx * nx + c);
        m[1] = (float) (t * nx * ny + s * nz);
        m[2] = (float) (t * nx * nz - s * ny);
        m[4] = (float) (t * nx * ny - s * nz);
        m[5] = (float) (t * ny * ny + c);
        m[6] = (float) (t * ny * nz + s * nx);
        m[8] = (float) (t * nx * nz + s * ny);
        m[9] = (float) (t * ny * nz - s * nx);
        m[10] = (float) (t * nz * nz + c);
        m[15] = 1.0f;
        multMatrix(m);
    }

    /** {@code GlStateManager.ortho}: replaces the current matrix with the projection. */
    public void ortho(double left, double right, double bottom, double top,
                      double near, double far) {
        float[] m = new float[16];
        m[0] = (float) (2.0 / (right - left));
        m[5] = (float) (2.0 / (top - bottom));
        m[10] = (float) (-2.0 / (far - near));
        m[12] = (float) (-(right + left) / (right - left));
        m[13] = (float) (-(top + bottom) / (top - bottom));
        m[14] = (float) (-(far + near) / (far - near));
        m[15] = 1.0f;
        switch (matrixMode()) {
            case GL_MODELVIEW:
                this.register.setCurrentMatrix(m);
                break;
            case GL_PROJECTION:
                System.arraycopy(m, 0, this.projectionStack[this.projectionDepth - 1], 0, 16);
                break;
            default:
                this.textureModeMatrixOps++;
        }
    }

    /** The current modelview top (the register's top), 16 floats column-major. */
    public void modelViewMatrix(float[] out) {
        this.register.currentMatrix(out);
    }

    /** The current projection top, 16 floats column-major. */
    public void projectionMatrix(float[] out) {
        if (out == null || out.length != 16) {
            throw new IllegalArgumentException("out must hold exactly 16 floats");
        }
        System.arraycopy(this.projectionStack[this.projectionDepth - 1], 0, out, 0, 16);
    }

    /** Matrix writes that arrived in texture mode and were not applied anywhere. */
    public long textureModeMatrixOps() {
        return this.textureModeMatrixOps;
    }

    /** Restores the shipping default state. For tests and world-unload resets. */
    public void reset() {
        this.blendEnabled = false;
        this.depthTestEnabled = true;
        this.depthWriteEnabled = true;
        this.cullMode = RenderStateKey.CULL_BACK;
        this.colorWriteMask = 0xF;
        this.activeTextureUnit = 0;
        this.blendSrcRgb = GL_ONE;
        this.blendDstRgb = GL_ZERO;
        this.blendSrcAlpha = GL_ONE;
        this.blendDstAlpha = GL_ZERO;
        this.depthFunc = GL_LEQUAL;
        this.alphaFunc = GL_ALWAYS;
        this.alphaRef = 0.0f;
        this.texture2dEnabled = true;
        this.colorRed = 1.0f;
        this.colorGreen = 1.0f;
        this.colorBlue = 1.0f;
        this.colorAlpha = 1.0f;
        setMatrixMode(GL_MODELVIEW);
        while (this.register.matrixDepth() > 1) {
            this.register.popMatrix();
        }
        this.register.loadIdentity();
        this.register.setBoundTextureId(0);
        this.register.setCompilingListId(0);
        this.projectionDepth = 1;
        writeIdentity(this.projectionStack[0]);
        publishKey();
    }

    private void publishKey() {
        this.register.setKey(new RenderStateKey.Builder()
                .blendEnabled(this.blendEnabled)
                .depthTestEnabled(this.depthTestEnabled)
                .depthWriteEnabled(this.depthWriteEnabled)
                .cullMode(this.cullMode)
                .colorWriteMask(this.colorWriteMask)
                .activeTextureUnit(this.activeTextureUnit)
                .build());
    }

    /** Column-major {@code a * b}: the composite that applies {@code b} first, then {@code a}. */
    static float[] multiply(float[] a, float[] b) {
        float[] out = new float[16];
        for (int col = 0; col < 4; col++) {
            for (int row = 0; row < 4; row++) {
                float sum = 0.0f;
                for (int k = 0; k < 4; k++) {
                    sum += a[k * 4 + row] * b[col * 4 + k];
                }
                out[col * 4 + row] = sum;
            }
        }
        return out;
    }

    private static void writeIdentity(float[] m) {
        for (int i = 0; i < 16; i++) {
            m[i] = (i % 5 == 0) ? 1.0f : 0.0f;
        }
    }
}
