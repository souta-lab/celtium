package org.soutalab.celtium.vulkan.glstate;

/**
 * Zero-allocation primitive/bitmask GL state ledger for the redefined {@code GlsmFrontend}
 * (spec §2.6, Module Migration Map M13).
 *
 * <p>All GL state mutations update {@link #stateKey} via single bitwise operations on a primitive
 * {@code long} plus flat primitive fields and a fixed-depth primitive attribute stack (no boxed
 * objects, no per-call allocations). Also tracks dynamic FrameGraph pass triggers (custom FBO
 * writes, screen copies, readbacks) so the vanilla direct-to-swapchain graph only inserts extra
 * passes on frames that actually request them (spec §2.1).
 */
public final class GlsmBitmaskLedger {

    /** Maximum {@code glPushAttrib} nesting depth (OpenGL 2.1 requires >= 16; 32 matches MC mods). */
    public static final int MAX_ATTRIB_STACK = 32;

    // OpenGL glPushAttrib bitmasks (subset relevant to MC 1.12.2 / Forge mods)
    public static final int GL_CURRENT_BIT = 0x00000001;
    public static final int GL_DEPTH_BUFFER_BIT = 0x00000100;
    public static final int GL_ENABLE_BIT = 0x00002000;
    public static final int GL_COLOR_BUFFER_BIT = 0x00004000;
    public static final int GL_TEXTURE_BIT = 0x00040000;
    public static final int GL_ALL_ATTRIB_BITS = 0x000FFFFF;

    // Frame-level dynamic graph trigger flags
    public static final int TRIGGER_CUSTOM_FBO_DRAW = 1;
    public static final int TRIGGER_COPY_TEX_SUB_IMAGE = 1 << 1;
    public static final int TRIGGER_FBO_BLIT = 1 << 2;
    public static final int TRIGGER_READ_PIXELS = 1 << 3;
    public static final int TRIGGER_FOREIGN_DEPTH_CLEAR = 1 << 4;

    private long stateKey = PipelineStateKey.DEFAULT_GL_KEY;
    private int boundTexture0;
    private int boundTexture1;
    private int boundDrawFbo;
    private int boundReadFbo;
    private float alphaRef;
    private float polyOffsetFactor;
    private float polyOffsetUnits;
    private int viewportX;
    private int viewportY;
    private int viewportW;
    private int viewportH;
    private int scissorX;
    private int scissorY;
    private int scissorW;
    private int scissorH;

    // Zero-allocation primitive attrib stack
    private int stackDepth;
    private final int[] maskStack = new int[MAX_ATTRIB_STACK];
    private final long[] keyStack = new long[MAX_ATTRIB_STACK];
    private final int[] tex0Stack = new int[MAX_ATTRIB_STACK];
    private final int[] tex1Stack = new int[MAX_ATTRIB_STACK];
    private final float[] alphaRefStack = new float[MAX_ATTRIB_STACK];

    // Dynamic FrameGraph pass triggers for the current frame
    private int frameTriggers;

    // Telemetry counters
    private long stateChanges;
    private long redundantElisions;

    public long stateKey() {
        return stateKey;
    }

    private void applyKey(long nextKey) {
        if (nextKey == stateKey) {
            redundantElisions++;
        } else {
            stateKey = nextKey;
            stateChanges++;
        }
    }

    public void setBlendEnable(boolean enable) {
        applyKey(PipelineStateKey.withBlendEnable(stateKey, enable));
    }

    public void setBlendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        applyKey(PipelineStateKey.withBlendFuncSeparate(stateKey, srcRgb, dstRgb, srcAlpha, dstAlpha));
    }

    public void setDepthTest(boolean enable) {
        applyKey(PipelineStateKey.withDepthTest(stateKey, enable));
    }

    public void setDepthWrite(boolean enable) {
        applyKey(PipelineStateKey.withDepthWrite(stateKey, enable));
    }

    public void setDepthFunc(int cmp) {
        applyKey(PipelineStateKey.withDepthFunc(stateKey, cmp));
    }

    public void setCullEnable(boolean enable) {
        applyKey(PipelineStateKey.withCullEnable(stateKey, enable));
    }

    public void setCullFace(int face) {
        applyKey(PipelineStateKey.withCullFace(stateKey, face));
    }

    public void setAlphaTest(boolean enable) {
        applyKey(PipelineStateKey.withAlphaTest(stateKey, enable));
    }

    public void setAlphaFunc(int cmp, float ref) {
        long next = PipelineStateKey.withAlphaFunc(stateKey, cmp);
        if (next == stateKey && Float.floatToRawIntBits(ref) == Float.floatToRawIntBits(alphaRef)) {
            redundantElisions++;
        } else {
            stateKey = next;
            alphaRef = ref;
            stateChanges++;
        }
    }

    public void setPolygonOffsetFill(boolean enable) {
        applyKey(PipelineStateKey.withPolygonOffsetFill(stateKey, enable));
    }

    public void setPolygonOffset(float factor, float units) {
        if (Float.floatToRawIntBits(factor) == Float.floatToRawIntBits(polyOffsetFactor)
                && Float.floatToRawIntBits(units) == Float.floatToRawIntBits(polyOffsetUnits)) {
            redundantElisions++;
        } else {
            polyOffsetFactor = factor;
            polyOffsetUnits = units;
            stateChanges++;
        }
    }

    public void setColorMask(boolean r, boolean g, boolean b, boolean a) {
        int mask = (r ? 1 : 0) | (g ? 2 : 0) | (b ? 4 : 0) | (a ? 8 : 0);
        applyKey(PipelineStateKey.withColorMask(stateKey, mask));
    }

    public void setTopology(int topology) {
        applyKey(PipelineStateKey.withTopology(stateKey, topology));
    }

    public void setVertexFormat(int formatId) {
        applyKey(PipelineStateKey.withVertexFormat(stateKey, formatId));
    }

    public void setTexture2d(boolean enable) {
        applyKey(PipelineStateKey.withTexture2d(stateKey, enable));
    }

    public void setLightmap(boolean enable) {
        applyKey(PipelineStateKey.withLightmap(stateKey, enable));
    }

    public void setFog(boolean enable) {
        applyKey(PipelineStateKey.withFog(stateKey, enable));
    }

    public void setLighting(boolean enable) {
        applyKey(PipelineStateKey.withLighting(stateKey, enable));
    }

    public void setScissorEnable(boolean enable) {
        applyKey(PipelineStateKey.withScissor(stateKey, enable));
    }

    public void setProgramId(int programId) {
        applyKey(PipelineStateKey.withProgramId(stateKey, programId));
    }

    public void bindTexture(int unit, int textureId) {
        if (unit == 0) {
            if (boundTexture0 == textureId) {
                redundantElisions++;
            } else {
                boundTexture0 = textureId;
                stateChanges++;
            }
        } else if (unit == 1) {
            if (boundTexture1 == textureId) {
                redundantElisions++;
            } else {
                boundTexture1 = textureId;
                stateChanges++;
            }
        }
    }

    public void bindDrawFramebuffer(int fboId) {
        if (boundDrawFbo == fboId) {
            redundantElisions++;
        } else {
            boundDrawFbo = fboId;
            stateChanges++;
        }
    }

    public void bindReadFramebuffer(int fboId) {
        if (boundReadFbo == fboId) {
            redundantElisions++;
        } else {
            boundReadFbo = fboId;
            stateChanges++;
        }
    }

    public void setViewport(int x, int y, int w, int h) {
        if (viewportX == x && viewportY == y && viewportW == w && viewportH == h) {
            redundantElisions++;
        } else {
            viewportX = x;
            viewportY = y;
            viewportW = w;
            viewportH = h;
            stateChanges++;
        }
    }

    public void setScissorRect(int x, int y, int w, int h) {
        if (scissorX == x && scissorY == y && scissorW == w && scissorH == h) {
            redundantElisions++;
        } else {
            scissorX = x;
            scissorY = y;
            scissorW = w;
            scissorH = h;
            stateChanges++;
        }
    }

    /** Zero-allocation {@code glPushAttrib(mask)}. */
    public void pushAttrib(int mask) {
        if (stackDepth >= MAX_ATTRIB_STACK) {
            throw new IllegalStateException("glPushAttrib stack overflow (depth=" + stackDepth + ")");
        }
        int idx = stackDepth++;
        maskStack[idx] = mask;
        keyStack[idx] = stateKey;
        tex0Stack[idx] = boundTexture0;
        tex1Stack[idx] = boundTexture1;
        alphaRefStack[idx] = alphaRef;
    }

    /** Zero-allocation {@code glPopAttrib()} restoring only the bits captured in {@code mask}. */
    public void popAttrib() {
        if (stackDepth <= 0) {
            throw new IllegalStateException("glPopAttrib stack underflow");
        }
        int idx = --stackDepth;
        int mask = maskStack[idx];
        long saved = keyStack[idx];
        long restoreMask = 0L;

        if ((mask & GL_COLOR_BUFFER_BIT) != 0) {
            restoreMask |= PipelineStateKey.MASK_BLEND_BITS
                    | PipelineStateKey.MASK_ALPHA_BITS
                    | PipelineStateKey.MASK_COLOR_MASK;
            alphaRef = alphaRefStack[idx];
        }
        if ((mask & GL_DEPTH_BUFFER_BIT) != 0) {
            restoreMask |= PipelineStateKey.MASK_DEPTH_BITS;
        }
        if ((mask & GL_ENABLE_BIT) != 0) {
            restoreMask |= PipelineStateKey.BIT_BLEND_ENABLE
                    | PipelineStateKey.BIT_DEPTH_TEST
                    | PipelineStateKey.BIT_CULL_ENABLE
                    | PipelineStateKey.BIT_ALPHA_TEST
                    | PipelineStateKey.BIT_POLY_OFFSET
                    | PipelineStateKey.BIT_TEX2D
                    | PipelineStateKey.BIT_LIGHTMAP
                    | PipelineStateKey.BIT_FOG
                    | PipelineStateKey.BIT_LIGHTING
                    | PipelineStateKey.BIT_SCISSOR;
        }
        if ((mask & GL_TEXTURE_BIT) != 0) {
            boundTexture0 = tex0Stack[idx];
            boundTexture1 = tex1Stack[idx];
        }

        if (restoreMask != 0L) {
            applyKey((stateKey & ~restoreMask) | (saved & restoreMask));
        }
    }

    // --- Dynamic FrameGraph pass trigger detection (spec §2.1, M13/E2) ---

    /** Called by GLSM when a draw is emitted; flags custom FBO usage if {@code boundDrawFbo != 0}. */
    public void noteDrawEmitted() {
        if (boundDrawFbo != 0) {
            frameTriggers |= TRIGGER_CUSTOM_FBO_DRAW;
        }
    }

    /** Called on {@code glCopyTexSubImage2D}. */
    public void noteCopyTexSubImage() {
        frameTriggers |= TRIGGER_COPY_TEX_SUB_IMAGE;
    }

    /** Called on {@code glBlitFramebuffer}. */
    public void noteFramebufferBlit() {
        frameTriggers |= TRIGGER_FBO_BLIT;
    }

    /** Called on {@code glReadPixels}. */
    public void noteReadPixels() {
        frameTriggers |= TRIGGER_READ_PIXELS;
    }

    /** Called on a depth-only clear targeting a foreign FBO (preserves B5 fix semantics). */
    public void noteForeignDepthClear() {
        if (boundDrawFbo != 0) {
            frameTriggers |= TRIGGER_FOREIGN_DEPTH_CLEAR;
        }
    }

    public boolean requiresDynamicGraphPass() {
        return frameTriggers != 0;
    }

    public int frameTriggers() {
        return frameTriggers;
    }

    public void resetFrameTriggers() {
        frameTriggers = 0;
    }

    public int boundTexture0() {
        return boundTexture0;
    }

    public int boundTexture1() {
        return boundTexture1;
    }

    public int boundDrawFbo() {
        return boundDrawFbo;
    }

    public int boundReadFbo() {
        return boundReadFbo;
    }

    public float alphaRef() {
        return alphaRef;
    }

    public float polyOffsetFactor() {
        return polyOffsetFactor;
    }

    public float polyOffsetUnits() {
        return polyOffsetUnits;
    }

    public int viewportX() {
        return viewportX;
    }

    public int viewportY() {
        return viewportY;
    }

    public int viewportW() {
        return viewportW;
    }

    public int viewportH() {
        return viewportH;
    }

    public int scissorX() {
        return scissorX;
    }

    public int scissorY() {
        return scissorY;
    }

    public int scissorW() {
        return scissorW;
    }

    public int scissorH() {
        return scissorH;
    }

    public int stackDepth() {
        return stackDepth;
    }

    public long stateChanges() {
        return stateChanges;
    }

    public long redundantElisions() {
        return redundantElisions;
    }
}
