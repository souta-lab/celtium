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
    /** GL's two blend equations; {@code GL_FUNC_ADD} is GL's initial state and what the pipelines baked alone. */
    /** Which channels a write may touch: bit 0 red, 1 green, 2 blue, 3 alpha (Vulkan's own component bits). */
    private boolean depthTestEnabled = true;
    /** The OpenGL depth-function constant (GL_LEQUAL &c.), not an ordinal: the pipeline maps it to a compare op. */
    private int depthFunc = 515;
    private boolean depthMask = true;
    private boolean alphaTestEnabled;
    private int alphaFunc = ALPHA_GREATER;
    private float alphaRef = 0.1f;
    private boolean lightingEnabled;
    /** Eye-space direction each enabled light points, captured the way GL stores it: modelview times the position. */
    private final float[] lightDirections = new float[6];
    /** Per-light diffuse colour; GL_DIFFUSE at call time, RGB triples for lights 0 and 1. */
    private final float[] lightDiffuse = new float[6];
    /** The scene ambient term GL_LIGHT_MODEL_AMBIENT carries. */
    private final float[] lightAmbient = new float[3];
    /** Bit i set when light i is enabled; vanilla item lighting enables exactly lights 0 and 1. */
    private int lightsEnabledMask;
    /** Bumped by every lighting setter; see {@link #lightGeneration()}. */
    private long lightGeneration;
    /** The GL name of the bound texture; 0 means none, which is what the immediate path refuses to draw with. */
    private float alphaFuncReference = 0.1f;
    private int shadeModel = SHADE_SMOOTH;
    private float colorRed = 1.0f;
    private float colorGreen = 1.0f;
    private float colorBlue = 1.0f;
    private float colorAlpha = 1.0f;
    private long boundTexture;
    /**
     * The first caller this state model saw each texture name bound through. The stub's bind-site map covers the two
     * routes its own records know (the {@code GlStateManager.bindTexture} hook and the function-table dispatch), but
     * the state model is also written through {@code immediateState.bindTexture} directly - the E2E pack's texture
     * 506 reached the "no mirror" refusal with no bind site anywhere, and this map is the layer that cannot be
     * bypassed. One stack walk per new name; repeat binds cost a map lookup.
     */
    private final java.util.Map<Integer, String> firstBindSites = new java.util.HashMap<Integer, String>();
    private static final int MAX_BIND_SITES = 4096;

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

    /**
     * GL's blend equations, as the constants GL itself defines them (these are not ordinals: the backend maps each
     * one to its Vulkan counterpart). {@code BLEND_FUNC_ADD} is GL's initial state.
     */
    public static final int BLEND_FUNC_ADD = 0x8006;
    public static final int BLEND_FUNC_SUBTRACT = 0x800A;
    public static final int BLEND_FUNC_REVERSE_SUBTRACT = 0x800B;
    public static final int BLEND_MIN = 0x8007;
    public static final int BLEND_MAX = 0x8008;

    /** Every colour channel writable: bit 0 red, 1 green, 2 blue, 3 alpha - Vulkan's own component bits. */
    public static final int COLOR_CHANNEL_ALL = 0xF;

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

    private final NativeRasterState raster;
    private final NativeTextureBindings textures;

    public VulkanGlState() {
        this(new NativeRasterState());
    }

    VulkanGlState(NativeRasterState raster) {
        this(raster, new NativeTextureBindings(raster));
    }

    VulkanGlState(NativeRasterState raster, NativeTextureBindings textures) {
        this.raster = raster;
        this.textures = textures;
        this.modelViewStack.push(newIdentity());
        this.projectionStack.push(newIdentity());
        this.textureStack0.push(newIdentity());
        this.textureStack1.push(newIdentity());
    }

    /**
     * Pushes the current matrix of the active mode; paired with {@link #popMatrix()}.
     * <p>
     * The pushed slot comes from a small pool that {@link #popMatrix()} refills, because this runs for every glPushMatrix the game makes -
     * an entity or item draw pushes and pops several times - and a fresh float[16] each time was 977 MB of allocation in one JFR of the
     * world window, the largest single site in this class. Reuse is safe under the same rule that already applied to the stack: a matrix
     * belongs to whoever holds the top, and nothing may keep a reference to it after popping.
     */
    public void pushMatrix() {
        ArrayDeque<float[]> stack = stack(this.matrixMode);
        float[] pushed = this.matrixScratchPool.poll();
        if (pushed == null) {
            pushed = new float[MATRIX_FLOATS];
        }
        System.arraycopy(top(this.matrixMode), 0, pushed, 0, MATRIX_FLOATS);
        stack.push(pushed);
    }

    public void popMatrix() {
        ArrayDeque<float[]> stack = stack(this.matrixMode);
        if (stack.size() > 1) {
            float[] popped = stack.pop();
            if (popped != null && this.matrixScratchPool.size() < MATRIX_POOL_LIMIT) {
                this.matrixScratchPool.push(popped);
            }
        }
    }

    /** Replaces the active matrix with the identity, the {@code glLoadIdentity} case. */
    public void loadIdentity() {
        System.arraycopy(IDENTITY, 0, top(this.matrixMode), 0, MATRIX_FLOATS);
    }

    /**
     * Applies the same transform {@code glTranslate}/{@code glRotate}/{@code glScale} would, by multiplying the
     * active matrix in place. The argument order and the sign of the rotation follow OpenGL so a mirrored matrix is
     * numerically what the GL call would have produced.
     */
    public void translate(double x, double y, double z) {
        // Applied directly rather than by building a translation matrix and running the 4x4 multiply: in column-major
        // order a translation only rewrites column 3, and the three columns before it are unchanged. This is the call a
        // tile entity renderer makes per part, so the 64 multiply-adds it used to spend are worth not spending.
        float[] m = top(this.matrixMode);
        float tx = (float) x;
        float ty = (float) y;
        float tz = (float) z;
        float m12 = (m[0] * tx) + (m[4] * ty) + (m[8] * tz) + m[12];
        float m13 = (m[1] * tx) + (m[5] * ty) + (m[9] * tz) + m[13];
        float m14 = (m[2] * tx) + (m[6] * ty) + (m[10] * tz) + m[14];
        float m15 = (m[3] * tx) + (m[7] * ty) + (m[11] * tz) + m[15];
        m[12] = m12;
        m[13] = m13;
        m[14] = m14;
        m[15] = m15;
    }

    public void scale(double x, double y, double z) {
        // Same argument as translate: M * S with a diagonal S scales each of the first three columns and leaves the
        // fourth alone, so the product is three column writes per column rather than a full multiply.
        float[] m = top(this.matrixMode);
        float sx = (float) x;
        float sy = (float) y;
        float sz = (float) z;
        for (int row = 0; row < 4; row++) {
            m[row] *= sx;
            m[4 + row] *= sy;
            m[8 + row] *= sz;
        }
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
        // Column-major rotation matrix around the normalised axis, as glRotatef builds it.
        float r00 = (float) ((nx * nx * t) + c);
        float r01 = (float) ((ny * nx * t) + (nz * s));
        float r02 = (float) ((nz * nx * t) - (ny * s));
        float r10 = (float) ((nx * ny * t) - (nz * s));
        float r11 = (float) ((ny * ny * t) + c);
        float r12 = (float) ((nz * ny * t) + (nx * s));
        float r20 = (float) ((nx * nz * t) + (ny * s));
        float r21 = (float) ((ny * nz * t) - (nx * s));
        float r22 = (float) ((nz * nz * t) + c);
        // Applied in place rather than through the general 4x4 product, for the same reason translate and scale are:
        // a rotation's fourth column is exactly (0,0,0,1), so only the first three columns of the product can differ
        // from the matrix's own. That is 36 multiply-adds instead of 64, on a call a tile entity renderer makes per
        // part - rotate is 1.1% of the frame's samples in the warm profile.
        float[] m = top(this.matrixMode);
        for (int row = 0; row < 4; row++) {
            float x0 = m[row];
            float x1 = m[4 + row];
            float x2 = m[8 + row];
            // Column j of the product takes the coefficients of column j of the rotation: r0j, r1j, r2j in this
            // naming, because the rotation's own array is column-major and rIJ is element (row I + 1, column J + 1).
            m[row] = (x0 * r00) + (x1 * r01) + (x2 * r02);
            m[4 + row] = (x0 * r10) + (x1 * r11) + (x2 * r12);
            m[8 + row] = (x0 * r20) + (x1 * r21) + (x2 * r22);
        }
    }

    /**
     * The per-call scratch for the buffer forms and the multiply's result: the callers each used to allocate a fresh
     * {@code float[16]} per call, on a path the matrix census measures at hundreds of calls per frame. The scratch is
     * an instance field because this class already documents the render-thread confinement its stacks rely on; a
     * shared array is safe because every consumer below copies the values out before returning (loadMatrix's
     * arraycopy, multiply's read into {@link #multiplyResult}), and no caller may hold the reference.
     */
    private final float[] matrixScratch = new float[MATRIX_FLOATS];
    private final float[] multiplyResult = new float[MATRIX_FLOATS];

    /** Replaces the active matrix from a column-major buffer, the {@code glLoadMatrix} case. */
    public void loadMatrixBuffer(java.nio.FloatBuffer buffer) {
        if (buffer == null || buffer.remaining() < MATRIX_FLOATS) {
            return;
        }
        float[] matrix = this.matrixScratch;
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
        float[] matrix = this.matrixScratch;
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
        multiply(top(this.matrixMode), matrix, this.multiplyResult);
        System.arraycopy(this.multiplyResult, 0, top(this.matrixMode), 0, MATRIX_FLOATS);
    }

    /** {@return a copy of the active matrix}, for the push-constant block a draw uploads. */
    public float[] matrix(int mode) {
        return copy(top(mode));
    }

    /**
     * {@return the active matrix's elements without copying}, for a caller that copies them out or reads a few and does
     * not need the sixteen-float snapshot {@link #matrix(int)} allocates. The batch-accept path reads the projection
     * and modelview tops into {@code glProjection}/{@code glModelView} per batch - a copy fed straight to
     * {@code arraycopy} was a wasted allocation on a ~3600-batch frame. The returned array is the stack's own - do not
     * write into it.
     */
    public float[] peekMatrix(int mode) {
        return top(mode);
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
     * {@return the named texture unit's matrix elements without copying}, for a caller that reads a few elements and
     * does not need the sixteen-float snapshot {@link #textureMatrix(int)} allocates. The immediate path's
     * {@code textureAffine} reads six elements once per closed group (~3600/frame); the copy was pure allocation on
     * the 99%-identity path. The returned array is the stack's own - do not write into it.
     */
    public float[] peekTextureMatrix(int unit) {
        return (unit == 1 ? this.textureStack1 : this.textureStack0).peek();
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
        return this.raster.blendEnabled();
    }

    public void blendEnabled(boolean enabled) {
        this.raster.blendEnabled(enabled);
    }

    /**
     * {@return whether the current factors actually mix anything}
     * <p>
     * Minecraft 1.12.2 leaves blending enabled and expresses "no blending" as the {@code ONE, ZERO} function rather than by
     * disabling it - which is why counting the enable bit reported every batch as translucent. The function is the honest
     * question to ask.
     */
    public boolean blendMixes() {
        return this.raster.blendSrcRgb() != BLEND_ONE || this.raster.blendDstRgb() != BLEND_ZERO;
    }

    public void blendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        this.raster.blendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public int blendSrcRgb() {
        return this.raster.blendSrcRgb();
    }

    public int blendDstRgb() {
        return this.raster.blendDstRgb();
    }

    public int blendSrcAlpha() {
        return this.raster.blendSrcAlpha();
    }

    public int blendDstAlpha() {
        return this.raster.blendDstAlpha();
    }

    /**
     * {@return the RGB blend equation}, as GL's own constant.
     * <p>
     * BH-07: the equation bakes into the pipeline's colour-blend attachment beside the factors, so it belongs in the
     * same mirror and the same key. {@code JsonBlendingMode} is the caller that can ask for a non-ADD equation
     * ({@code GlStateManager.glBlendEquation} -> {@code GL14.glBlendEquation} -> this process's GL entry, which used
     * to drop the call); ADD is GL's initial state and what every pipeline baked alone before this field existed.
     */
    public int blendEquationRgb() {
        return this.raster.blendEquationRgb();
    }

    public int blendEquationAlpha() {
        return this.raster.blendEquationAlpha();
    }

    public void blendEquation(int rgb, int alpha) {
        this.raster.blendEquation(rgb, alpha);
    }

    /**
     * {@return which channels a write may touch}, as the {@code VK_COLOR_COMPONENT_*} nibble (R=1, G=2, B=4, A=8).
     * <p>
     * BH-10: all four by default - GL's own initial state and what every pipeline baked before this field existed.
     * The Fancy-cloud depth-only pass clears all four ({@code RenderGlobal:1655
     * colorMask(false,false,false,false)}); without the mask in the key the depth-only batch and the visible batch
     * that follows it are the same pipeline, and the depth pass paints colour.
     */
    public int colorMask() {
        return this.raster.colorMask();
    }

    public void colorMask(int mask) {
        this.raster.colorMask(mask);
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
        return this.raster.cullEnabled();
    }

    public void cullEnabled(boolean enabled) {
        this.raster.cullEnabled(enabled);
    }

    public int cullFace() {
        return this.raster.cullFace();
    }

    public void cullFace(int face) {
        this.raster.cullFace(face);
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

    /**
     * {@code GlStateManager.alphaFunc}: the comparison function and the reference value of the alpha test.
     * <p>
     * BH-03: this setter used to write {@code alphaRef} while the capture read {@code alphaFuncReference}, a second
     * field nothing on the game path ever wrote - it started at 0.1 and stayed there unless the font path's temporary
     * override touched it. Every {@code alphaFunc} the game made was therefore invisible: the batch was tested against
     * 0.1 whatever reference the caller asked for, and cutout content whose alpha sits between the two kept or lost
     * fragments against the wrong threshold. Both fields are written here so the two spellings of the same state cannot
     * diverge again while the callers of the older accessor still work.
     */
    public void alphaFunc(int func, float reference) {
        this.alphaFunc = func;
        this.alphaRef = reference;
        this.alphaFuncReference = reference;
    }

    public boolean lightingEnabled() {
        return this.lightingEnabled;
    }

    public void lightingEnabled(boolean enabled) {
        this.lightingEnabled = enabled;
        this.lightGeneration++;
    }

    /**
     * {@return a counter that changes whenever any value of the fixed-function lighting payload changes}.
     * <p>
     * The payload is five vec4 of light directions, ambient and diffuse, captured once when an immediate group opens
     * (BH-05). The enable bit rides the state key, so toggling lighting closes a group - but a caller that changes a
     * light's direction or colour <em>without</em> toggling the enable used to leave the group open, and every batch
     * appended after the change was drawn with the earlier payload. Comparing a counter is the cheap form of the same
     * "state must reach the group" rule the blend factors, the write mask and the clip rectangle now follow: no
     * twenty-float array is built per batch just to notice that nothing moved.
     */
    public long lightGeneration() {
        return this.lightGeneration;
    }

    public void lightEnabled(int index, boolean enabled) {
        if (index < 0 || index >= 2) {
            return;
        }
        int previous = this.lightsEnabledMask;
        if (enabled) {
            this.lightsEnabledMask |= 1 << index;
        } else {
            this.lightsEnabledMask &= ~(1 << index);
        }
        if (this.lightsEnabledMask != previous) {
            this.lightGeneration++;
        }
    }

    public boolean lightEnabled(int index) {
        return index >= 0 && index < 2 && (this.lightsEnabledMask & (1 << index)) != 0;
    }

    /**
     * Stores a light direction exactly as the fixed pipeline keeps it: the position the caller passed, multiplied by
     * the modelview in force at the call. A {@code w} of zero marks a directional light - which is all vanilla's item
     * lighting uses - so only the three direction components of the transform are needed and no translation applies.
     */
    public void setLightPosition(int index, float x, float y, float z) {
        if (index < 0 || index >= 2) {
            return;
        }
        float[] m = matrix(MATRIX_MODE_MODELVIEW);
        int base = index * 3;
        float nx = m[0] * x + m[4] * y + m[8] * z;
        float ny = m[1] * x + m[5] * y + m[9] * z;
        float nz = m[2] * x + m[6] * y + m[10] * z;
        // Bumped only when the stored value really moves: the generation closes an immediate group, and vanilla
        // re-sets the same two lights every time enableStandardItemLighting runs, so a bump per call would split
        // groups that are identical - a cost with no correctness behind it.
        if (nx != this.lightDirections[base] || ny != this.lightDirections[base + 1]
                || nz != this.lightDirections[base + 2]) {
            this.lightDirections[base] = nx;
            this.lightDirections[base + 1] = ny;
            this.lightDirections[base + 2] = nz;
            this.lightGeneration++;
        }
    }

    public void setLightDiffuse(int index, float r, float g, float b) {
        if (index < 0 || index >= 2) {
            return;
        }
        int base = index * 3;
        if (r != this.lightDiffuse[base] || g != this.lightDiffuse[base + 1] || b != this.lightDiffuse[base + 2]) {
            this.lightDiffuse[base] = r;
            this.lightDiffuse[base + 1] = g;
            this.lightDiffuse[base + 2] = b;
            this.lightGeneration++;
        }
    }

    public void setLightAmbient(float r, float g, float b) {
        if (r != this.lightAmbient[0] || g != this.lightAmbient[1] || b != this.lightAmbient[2]) {
            this.lightAmbient[0] = r;
            this.lightAmbient[1] = g;
            this.lightAmbient[2] = b;
            this.lightGeneration++;
        }
    }

    /** {@return the eye-space direction of light index}, three floats starting at index*3. */
    public float[] lightDirections() {
        return this.lightDirections;
    }

    public float[] lightDiffuse() {
        return this.lightDiffuse;
    }

    public float[] lightAmbient() {
        return this.lightAmbient;
    }

    public int lightsEnabledMask() {
        return this.lightsEnabledMask;
    }

    /**
     * Per-texture-unit binding and {@code TEXTURE_2D} enable.
     * <p>
     * BH-06: one field for both meant that a bind on unit 1 or 2 (the light map unit, a mod's own unit) replaced the
     * name the immediate path reads for its base sampler, and a {@code disableTexture2D} on one unit disabled it for
     * every unit. GL keeps both per unit, and vanilla's entity brightness path binds another unit and then returns to
     * unit 0 without re-binding - which is exactly the sequence that made unit 0 read the wrong texture. The arrays
     * are indexed by the unit number {@code activeTextureUnit} already tracks; unit 0 is the base sampler the
     * immediate path and {@code invalidateBoundTexture} read, and it keeps the old no-argument accessors' meaning.
     */


    /** Binds seen on a unit other than 0, and binds seen on unit 0 - the BH-06 observable in a leg. */
    private static long bindsOnOtherUnits;
    private static long bindsOnUnitZero;

    /** Bounded log of the first unit switches and the first binds that carry their unit, so BH-06's half is named. */
    private static int unitLogs;
    /** Its own budget: the bind log filled the shared one first, which suppressed every switch line (BH-06 re-take). */
    private static int unitSwitchLogs;
    private static final int UNIT_LOG_LIMIT = 24;
    /**
     * The panorama-id bind log gets its own smaller budget: ids in the 40-60 range were named unconditionally, but
     * textures 43 and 45 are real content textures that bind ~4 times a frame, and the unbounded line cost 3,402
     * printlns in a 400-tick leg - several times the cost of the bind it describes.
     */
    private static int unitRangeLogs;
    private static final int UNIT_RANGE_LOG_LIMIT = 40;

    /** {@return how many binds arrived on a unit other than 0, against how many on unit 0}. */
    public static String perUnitBindCensus() {
        return "bindsOnUnit0=" + bindsOnUnitZero + " bindsOnOtherUnits=" + bindsOnOtherUnits
                + " (before BH-06 the second kind overwrote the name unit 0 reads; the entity brightness path is the"
                + " caller that binds the light map unit and returns to unit 0 without re-binding)";
    }

    /** Records the GL name of the bound texture on the <em>active</em> unit, as {@code GlStateManager.func_179144_i} does. */
    public void bindTexture(int glTextureId) {
        if (this.textures.activeUnit() == 0) {
            bindsOnUnitZero++;
        } else {
            bindsOnOtherUnits++;
        }
        // Raised bound plus a separately bounded line for the panorama ids (45-49): the first ~22 binds never
        // reached them, which is the one measurement left between the panorama's mirror and the faces' group.
        boolean panoramaRange = glTextureId >= 40 && glTextureId <= 60;
        if ((panoramaRange && unitRangeLogs < UNIT_RANGE_LOG_LIMIT)
                || (!panoramaRange && unitLogs < 96)) {
            if (panoramaRange) {
                unitRangeLogs++;
            } else {
                unitLogs++;
            }
            System.out.println("[Celtium] CELTIUM_UNIT_BIND bind texture=" + glTextureId
                    + " activeUnit=" + this.textures.activeUnit() + " bindsOnUnit0=" + bindsOnUnitZero
                    + " bindsOnOtherUnits=" + bindsOnOtherUnits);
        }
        this.textures.bindTexture2d(glTextureId);
        // A diagnostic, and not a free one: for a name it has not seen, this walks the stack. It used to run on every
        // bind - including the ones the font path issues per glyph - and the size check came *after* the lookup, so the
        // cost stayed after the table was full. Off unless diagnostics are asked for, and the size check first.
        if (glTextureId != 0 && VulkanDiagnostics.enabled() && this.firstBindSites.size() < MAX_BIND_SITES
                && !this.firstBindSites.containsKey(Integer.valueOf(glTextureId))) {
            this.firstBindSites.put(Integer.valueOf(glTextureId), bindSite());
        }
    }

    /**
     * {@return the caller that first bound this texture name through the state model, or {@code "none"} when it never
     * saw one}. Read by the "no mirror" refusal's {@code stateBoundBy} - the producer for a name the stub's own bind
     * records never saw.
     */
    public String firstBindSite(int glTextureId) {
        String site = this.firstBindSites.get(Integer.valueOf(glTextureId));
        return site == null ? "none" : site;
    }

    /** {@return how many distinct texture names the state model has seen bound} - a full map explains a "none". */
    public int bindSiteCount() {
        return this.firstBindSites.size();
    }

    /** {@return the first few frames above the state-model writer}, so the bind's producer is named. */
    private static String bindSite() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        int shown = 0;
        for (int i = 2; i < stack.length && shown < 5; i++) {
            String className = stack[i].getClassName();
            // The state model's own frames and the backend's forwarding say nothing about the game/mod caller.
            if (className.startsWith("org.soutalab.celtium.vulkan.")) {
                continue;
            }
            if (shown++ > 0) {
                frames.append(" <- ");
            }
            frames.append(className).append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
        }
        return frames.length() == 0 ? "(no frame above the state model)" : frames.toString();
    }

    /** {@return the GL name bound on {@code unit}}, or 0 when nothing is bound there. */
    public int boundTextureName(int unit) {
        return this.textures.texture2d(unit);
    }

    /** {@return the GL name bound on unit 0}, the base sampler the immediate path reads. */
    public int boundTextureName() {
        return this.textures.texture2d(0);
    }

    /**
     * {@return whether binding {@code glTextureId} on the active unit would leave the mirror exactly as it is}.
     * <p>
     * This is what lets the {@code GlStateManager.bindTexture} hook skip a bind the mirror already holds. The game's own
     * per-unit cache ({@code GlStateManager.java:481-488}) skips the {@code GL11.glBindTexture} call for a repeated
     * name, but the hook is injected at the method's HEAD, so a cache hit still runs it - measured on an F3 leg:
     * 1,628,711 arrivals at this mirror for 1,280,000 glyphs, i.e. once per glyph, of which the overwhelming majority
     * bind the same font sheet again. Comparing per unit is what makes the skip exact: the value the mirror would be
     * set to is the value it already holds, so the call is a no-op and vanilla's filter - which sits upstream of the
     * hook - is not duplicated by it.
     */
    public boolean bindWouldChangeNothing(int glTextureId) {
        return this.textures.unchanged(glTextureId);
    }

    /** {@return whether {@code TEXTURE_2D} is enabled on unit 0}, the base sampler's unit. */
    public boolean texture2dEnabled() {
        return this.textures.enabled2d(0);
    }

    /** {@return whether {@code TEXTURE_2D} is enabled on {@code unit}}. */
    public boolean texture2dEnabled(int unit) {
        return this.textures.enabled2d(unit);
    }

    /** Enables or disables {@code TEXTURE_2D} on the <em>active</em> unit, as {@code GlStateManager} does. */
    public void texture2dEnabled(boolean enabled) {
        this.textures.enabled2d(enabled);
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
        return this.textures.activeUnit();
    }

    public void activeTextureUnit(int unit) {
        this.textures.activeUnit(unit);
        if (unitSwitchLogs < UNIT_LOG_LIMIT) {
            unitSwitchLogs++;
            System.out.println("[Celtium] CELTIUM_UNIT_SWITCH unit=" + unit
                    + " (the mirror's active unit; a switch the game makes that is absent here is a hook the"
                    + " mirror does not see)");
        }
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
        key = (key * 31) + (this.raster.blendEnabled() ? 1 : 0);
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendSrcRgb() : 0);
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendDstRgb() : 0);
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendSrcAlpha() : 0);
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendDstAlpha() : 0);
        // BH-07, BH-10: the equations and the colour write mask bake into the same attachment and must therefore
        // close a pipeline the way the factors do. Before this they were not in the mirror at all, so the Fancy
        // cloud's depth-only pass and the visible pass shared one pipeline and the first pass painted colour.
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendEquationRgb() : 0);
        key = (key * 31) + (this.raster.blendEnabled() ? this.raster.blendEquationAlpha() : 0);
        key = (key * 31) + this.raster.colorMask();
        key = (key * 31) + (this.depthTestEnabled ? this.depthFunc + 1 : 0);
        key = (key * 31) + (this.depthMask ? 1 : 0);
        key = (key * 31) + (this.raster.cullEnabled() ? this.raster.cullFace() + 1 : 0);
        key = (key * 31) + (this.alphaTestEnabled ? this.alphaFunc + 1 : 0);
        key = (key * 31) + (this.lightingEnabled ? 1 : 0);
        key = (key * 31) + this.shadeModel;
        return key;
    }

    /** {@return a one-line description of the state that affects pipeline selection}, for diagnostics */
    public String describe() {
        return "blend=" + this.raster.blendEnabled() + "(" + this.raster.blendSrcRgb() + "," + this.raster.blendDstRgb() + ","
                + this.raster.blendSrcAlpha() + "," + this.raster.blendDstAlpha() + ") eq=" + this.raster.blendEquationRgb() + "/"
                + this.raster.blendEquationAlpha() + " writeMask=0x" + Integer.toHexString(this.raster.colorMask())
                + " depthTest=" + this.depthTestEnabled
                + " depthFunc=" + this.depthFunc + " depthMask=" + this.depthMask + " cull=" + this.raster.cullEnabled()
                + "(" + this.raster.cullFace() + ") alphaTest=" + this.alphaTestEnabled + "(" + this.alphaFunc + ","
                + this.alphaRef + ") lighting=" + this.lightingEnabled + " shade=" + this.shadeModel;
    }

    private ArrayDeque<float[]> stack(int mode) {
        switch (mode) {
            case MATRIX_MODE_PROJECTION:
                return this.projectionStack;
            case MATRIX_MODE_TEXTURE:
                return this.textures.activeUnit() == 1 ? this.textureStack1 : this.textureStack0;
            case MATRIX_MODE_MODELVIEW:
            default:
                return this.modelViewStack;
        }
    }

    /** Recycled matrix slots for {@link #pushMatrix()}, bounded so a deep stack cannot pin memory. */
    private final ArrayDeque<float[]> matrixScratchPool = new ArrayDeque<float[]>();
    private static final int MATRIX_POOL_LIMIT = 64;

    private float[] top(int mode) {
        return stack(mode).peek();
    }

    /**
     * The identity, column-major, never written to.
     * <p>
     * {@code identity()} used to build a fresh sixteen-float array on every call, and it is called by
     * {@code glLoadIdentity} and by every translate, scale and rotate - the calls a tile entity renderer makes per
     * part. On the warm profile the method itself was 3.4% of the frame's samples, all of it allocation. Anything that
     * needs to modify a matrix copies from here into its own array.
     */
    private static final float[] IDENTITY = {
        1.0f, 0.0f, 0.0f, 0.0f,
        0.0f, 1.0f, 0.0f, 0.0f,
        0.0f, 0.0f, 1.0f, 0.0f,
        0.0f, 0.0f, 0.0f, 1.0f,
    };

    /**
     * Scratch for building one transform. Only ever filled, mutated and handed straight to
     * {@link #multiplyMatrix}, which reads it - nothing retains it, which is why it can be reused across calls.
     */
    private final float[] transformScratch = new float[MATRIX_FLOATS];

    /** {@return a fresh identity matrix}, for the stack entries, which are written to and so cannot share one. */
    private static float[] newIdentity() {
        float[] matrix = new float[MATRIX_FLOATS];
        System.arraycopy(IDENTITY, 0, matrix, 0, MATRIX_FLOATS);
        return matrix;
    }

    /** {@return the scratch reset to the identity}, for a caller that is about to overwrite parts of it. */
    private float[] identityScratch() {
        System.arraycopy(IDENTITY, 0, this.transformScratch, 0, MATRIX_FLOATS);
        return this.transformScratch;
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
