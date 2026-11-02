package org.soutalab.celtium.vulkan.gl;

/**
 * One {@code glPushAttrib} snapshot: every state group the renderer can put back, plus the mask that says which of
 * them the caller asked to save.
 *
 * <p>Why this is a class and not the {@code long[]} it replaces. GL's attribute stack is <em>mask-selective</em>:
 * {@code glPushAttrib(GL_ENABLE_BIT)} saves the capability bits and nothing else, so a {@code glColorMask} written
 * inside that push survives the {@code glPopAttrib} - that is correct GL, and a mod that relies on the pop to undo it
 * is broken on every driver. The old stack ignored the mask and put back everything it tracked, which is a superset of
 * what GL does: for vanilla's own {@code GlStateManager.pushAttrib()} (mask {@code 8256} =
 * {@code GL_ENABLE_BIT|GL_LIGHTING_BIT}) it restored the current colour, line width and point size that the mask never
 * covered. Worse, the groups it did <em>not</em> track at all - the blend factors, the colour write mask, the alpha
 * test's comparison, the depth write mask and comparison, the cull mode, the scissor box, the polygon offset, the bound
 * texture and {@code glListBase} - stayed at whatever the pushed block left them, which is the shape of a leak: a mod
 * that disables depth writes or narrows the colour mask inside a push, and expects the pop to undo it, leaks that state
 * into the next vanilla pass.
 *
 * <p>The entries are <em>values</em>, not references: a snapshot taken now is what the pop restores, so a nested push /
 * pop pair cannot see a later write through a shared array. That property is what the unit tests pin.
 *
 * <p>{@link #restores(int)} is the mask rule and the only place that decides it. {@code GL_ALL_ATTRIB_BITS} is
 * {@code 0xFFFFFFFF}, which as a Java {@code int} is {@code -1}; {@code (mask & group) != 0} answers it correctly
 * without a special case, which is why the constants below are plain ints.
 */
public final class LegacyAttribSnapshot {

    // ---- GL attrib group bits (GL/gl.h). A mask is the OR of the groups a push saves.
    public static final int CURRENT_BIT = 0x00000001;
    public static final int POINT_BIT = 0x00000002;
    public static final int LINE_BIT = 0x00000004;
    public static final int POLYGON_BIT = 0x00000008;
    public static final int POLYGON_STIPPLE_BIT = 0x00000010;
    public static final int PIXEL_MODE_BIT = 0x00000020;
    public static final int LIGHTING_BIT = 0x00000040;
    public static final int FOG_BIT = 0x00000080;
    public static final int DEPTH_BUFFER_BIT = 0x00000100;
    public static final int ACCUM_BUFFER_BIT = 0x00000200;
    public static final int STENCIL_BUFFER_BIT = 0x00000400;
    public static final int VIEWPORT_BIT = 0x00000800;
    public static final int TRANSFORM_BIT = 0x00001000;
    public static final int ENABLE_BIT = 0x00002000;
    public static final int COLOR_BUFFER_BIT = 0x00004000;
    public static final int HINT_BIT = 0x00008000;
    public static final int EVAL_BIT = 0x00010000;
    public static final int LIST_BIT = 0x00020000;
    public static final int TEXTURE_BIT = 0x00040000;
    public static final int SCISSOR_BIT = 0x00080000;
    public static final int MULTISAMPLE_BIT = 0x20000000;
    public static final int ALL_ATTRIB_BITS = 0xFFFFFFFF;

    /** The groups this stack puts back. Kept beside {@link #UNMODELLED_GROUPS} so the census names a real gap. */
    public static final int MODELLED_GROUPS = CURRENT_BIT | POINT_BIT | LINE_BIT | POLYGON_BIT | DEPTH_BUFFER_BIT
            | ENABLE_BIT | COLOR_BUFFER_BIT | LIST_BIT | TEXTURE_BIT | SCISSOR_BIT | VIEWPORT_BIT;
    /**
     * The groups a push may select that this renderer cannot restore.
     *
     * <p>{@code GL_TRANSFORM_BIT} is the one that matters and it is not an oversight: the matrix stacks here are the
     * backend's mirror, and {@code glPushMatrix}/{@code glPopMatrix} are the calls mods use for them (both are routed).
     * A caller that saves the matrix stack with {@code glPushAttrib} alone gets the matrices back unchanged - counted,
     * so the day a pack depends on it the run says so instead of the picture going quietly wrong.
     *
     * <p>{@code GL_VIEWPORT_BIT} is <em>not</em> in this list any more, and the reason is measured rather than assumed.
     * A scan of the 193 mod jars in the Supersymmetry instance found its push sites and their masks: three
     * Supersymmetry space renderers ({@code SunGlowRenderer}, {@code PlanetSurfaceRenderer}, {@code RingFeatureRenderer})
     * push {@code 0xFFFFF} and call {@code glViewport} inside the block, relying on the pop to put the rectangle back
     * (they run in the space dimensions, not in the calibrated overworld scene); BetterQuesting's {@code RenderUtils}
     * pushes {@code 0x40B71}, which also selects the group. {@code GL_LIGHTING_BIT}
     * stays unmodelled for the opposite reason: no mod in the pack calls {@code glLightfv}, {@code glLightModelfv},
     * {@code glMaterialfv} or {@code glColorMaterial} at all, so the only values a pop could protect are the two constant
     * light sets vanilla re-applies. {@code GL_FOG_BIT} has no in-pack provider either (only BiomesOPlenty references
     * {@code glFog}, and no {@code 0xFFFFF} pusher changes fog state).
     */
    public static final int UNMODELLED_GROUPS = POLYGON_STIPPLE_BIT | PIXEL_MODE_BIT | LIGHTING_BIT | FOG_BIT
            | ACCUM_BUFFER_BIT | STENCIL_BUFFER_BIT | TRANSFORM_BIT | HINT_BIT | EVAL_BIT
            | MULTISAMPLE_BIT;

    /** The mask the caller passed to {@code glPushAttrib}. */
    public int mask;
    /** Capability bits, indexed by GL enum, as {@code glIsEnabled} would answer them at the push. */
    public final boolean[] capabilities;
    public float red = 1.0f;
    public float green = 1.0f;
    public float blue = 1.0f;
    public float alpha = 1.0f;
    public float lineWidth = 1.0f;
    public float pointSize = 1.0f;
    /** {@code GL_DEPTH_WRITEMASK}. */
    public boolean depthWrites = true;
    /** {@code GL_DEPTH_FUNC}, GL_LESS by default. */
    public int depthFunc = 0x0201;
    /** {@code GL_ALPHA_TEST_FUNC}, GL_ALWAYS by default, and its reference. */
    public int alphaFunc = 0x0207;
    public float alphaReference;
    public int blendSourceRgb = 0x0302;   // GL_SRC_ALPHA
    public int blendDestinationRgb = 0x0303; // GL_ONE_MINUS_SRC_ALPHA
    public int blendSourceAlpha = 0x0302;
    public int blendDestinationAlpha = 0x0303;
    public int blendEquationRgb = 0x8006; // GL_FUNC_ADD
    public int blendEquationAlpha = 0x8006;
    /** {@code GL_COLOR_WRITEMASK}, one bit per channel, GL's default being all four. */
    public int colorMask = 0xF;
    public int cullFace = 0x0405;   // GL_BACK
    public int frontFace = 0x0901;  // GL_CCW
    public int scissorX;
    public int scissorY;
    public int scissorWidth;
    public int scissorHeight;
    public float polygonOffsetFactor;
    public float polygonOffsetUnits;
    /**
     * {@code GL_VIEWPORT}'s rectangle, in GL's bottom-left pixels.
     * <p>
     * This is the group a push is most likely to protect without knowing it: a renderer that sets a sub-rectangle for
     * one pass (a render target, a GUI preview, a full-screen shader quad at the window size) pushes the attribute block
     * first and lets the pop undo the rectangle. The renderer reads the rectangle per offer from
     * {@code celtium$viewportPacked}, so a pop that only wrote the stub's fields would leave the packed value - the one
     * the group key and the capture use - describing the popped block's rectangle.
     */
    public int viewportX;
    public int viewportY;
    public int viewportWidth;
    public int viewportHeight;
    /** {@code GL_LIST_BASE}. */
    public int listBase;
    /** {@code GL_ACTIVE_TEXTURE}'s unit index and the texture bound to {@code GL_TEXTURE_2D} on it. */
    public int activeTextureUnit;
    public int texture2d;

    public LegacyAttribSnapshot(int capabilityCount) {
        this.capabilities = new boolean[Math.max(0, capabilityCount)];
    }

    /** {@return whether this push saves {@code group}}, which is the whole pop rule. */
    public boolean restores(int group) {
        return (this.mask & group) != 0;
    }

    /** {@return whether {@code mask} selects {@code group}} - the static form, for tests and the census. */
    public static boolean selects(int mask, int group) {
        return (mask & group) != 0;
    }

    /** {@return the selected groups this renderer cannot put back}, for the census. */
    public static int unmodelled(int mask) {
        return mask & UNMODELLED_GROUPS;
    }

    /** {@return the selected groups this renderer can put back}. */
    public static int modelled(int mask) {
        return mask & MODELLED_GROUPS;
    }
}
