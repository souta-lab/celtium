package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeRasterState;
import org.soutalab.celtium.vulkan.NativeTextureBindings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code glPushAttrib}/{@code glPopAttrib} against the real dispatch entry points, without a graphics context.
 *
 * <p>The rule being pinned is GL's, and it is the one the old stack got wrong in both directions: a push saves the
 * groups its <em>mask</em> selects and the pop restores exactly those. The old implementation ignored the mask (so
 * {@code glPushAttrib(GL_ENABLE_BIT)}, which vanilla's own {@code GlStateManager.pushAttrib} uses with
 * {@code GL_LIGHTING_BIT}, put back the current colour, the line width and the point size as well) and tracked only
 * capabilities, the colour and the two sizes - so the blend factors, the colour write mask, the alpha test, the depth
 * write mask and comparison, the cull mode, the scissor box, the polygon offset, the bound texture and {@code glListBase}
 * all leaked out of a pushed block. Both halves are tested here: the group that the mask names is restored, and the
 * group it does not name is left in force.
 *
 * <p>Values are read back through the queries a mod would use ({@code glGetBooleanv}, {@code glGetIntegerv},
 * {@code glIsEnabled}), not from private fields, so a restore that updated a field but not the query path would fail.
 * The stub is installed as {@code installed} because the push/pop routes are static: that is the same object the
 * transformer and the stub's own trampolines reach.
 */
class LegacyAttribStackTest {
    private static final int GL_BLEND = 0x0BE2;
    private static final int GL_DEPTH_TEST = 0x0B71;
    private static final int GL_SRC_ALPHA = 0x0302;
    private static final int GL_ONE_MINUS_SRC_ALPHA = 0x0303;
    private static final int GL_BACK = 0x0405;
    private static final int GL_FRONT = 0x0404;
    private static final int GL_DEPTH_WRITEMASK = 0x0B72;
    private static final int GL_DEPTH_FUNC = 0x0B74;
    private static final int GL_ALPHA_TEST_FUNC = 0x0BC1;
    private static final int GL_LESS = 0x0201;
    private static final int GL_ALWAYS = 0x0207;
    private static final int GL_GREATER = 0x0204;

    private final NativeRasterState raster = new NativeRasterState();
    private final NativeTextureBindings textures = new NativeTextureBindings(this.raster);
    private final CeltiumGlStub adapter;
    private final Method dispatch;
    private final Map<?, ?> ids;
    private final Field installedField;
    private CeltiumGlStub previousInstalled;

    LegacyAttribStackTest() throws Exception {
        var constructor = CeltiumGlStub.class.getDeclaredConstructor(
                NativeRasterState.class, NativeTextureBindings.class);
        constructor.setAccessible(true);
        this.adapter = constructor.newInstance(this.raster, this.textures);
        this.dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        var table = CeltiumGlStub.class.getDeclaredField("IDS");
        table.setAccessible(true);
        this.ids = (Map<?, ?>) table.get(null);
        this.installedField = CeltiumGlStub.class.getDeclaredField("installed");
        this.installedField.setAccessible(true);
    }

    @BeforeEach
    void installStub() throws Exception {
        this.previousInstalled = (CeltiumGlStub) this.installedField.get(null);
        this.installedField.set(null, this.adapter);
    }

    @AfterEach
    void uninstallStub() throws Exception {
        this.installedField.set(null, this.previousInstalled);
    }

    private long call(String name, long... args) throws Exception {
        assertTrue(this.ids.containsKey(name), "must use the real adapter entry: " + name);
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return ((Long) this.dispatch.invoke(this.adapter, this.ids.get(name), slots)).longValue();
    }

    private void callFloats(String name, float first, float second) throws Exception {
        call(name, Float.floatToRawIntBits(first), Float.floatToRawIntBits(second));
    }

    private long queryBoolean(int pname) throws Exception {
        var out = MemoryUtil.memAlloc(8);
        try {
            call("glGetBooleanv", pname, MemoryUtil.memAddress(out));
            return MemoryUtil.memGetByte(MemoryUtil.memAddress(out)) & 0xFFL;
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    /** One tracked query, as a mod's own save/restore reads it: the vector form, count ints wide. */
    private int[] queryInts(int pname, int count) throws Exception {
        var out = MemoryUtil.memAlloc(16 * Math.max(1, count));
        try {
            call("glGetIntegerv", pname, MemoryUtil.memAddress(out));
            int[] values = new int[count];
            for (int i = 0; i < count; i++) {
                values[i] = MemoryUtil.memGetInt(MemoryUtil.memAddress(out) + 4L * i);
            }
            return values;
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private long queryInt(int pname) throws Exception {
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetIntegerv", pname, MemoryUtil.memAddress(out));
            return MemoryUtil.memGetInt(MemoryUtil.memAddress(out));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    /**
     * The half the old stack could not represent: {@code GL_ENABLE_BIT} saves capabilities and nothing else.
     *
     * <p>A renderer that restores "everything it knows" puts the colour mask back here, which is a superset of GL and
     * hides the caller's leak inside the push. The mask is the contract; the assertion is the contract.
     */
    @Test
    void aPushThatDoesNotSelectTheColourBufferLeavesTheColourMaskItFoundInForce() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glColorMask", 1, 0, 0, 0);
        assertEquals(0x1, this.raster.colorMask(), "the write inside the push has to reach the renderer");
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(0x1, this.raster.colorMask(),
                "GL_ENABLE_BIT does not save GL_COLOR_WRITEMASK (that is GL_COLOR_BUFFER_BIT), so the pop must not "
                        + "put it back");
    }

    /** The group the mask does name: the blend factors, the equation and the colour write mask all come back. */
    @Test
    void aPushThatSelectsTheColourBufferPutsTheBlendStateBack() throws Exception {
        call("glBlendFunc", GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        call("glColorMask", 1, 1, 1, 1);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glBlendFunc", 1, 0);
        call("glColorMask", 1, 0, 0, 0);
        assertEquals(1, this.raster.blendSrcRgb());
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(GL_SRC_ALPHA, this.raster.blendSrcRgb(), "the source factor is GL_COLOR_BUFFER_BIT state");
        assertEquals(GL_ONE_MINUS_SRC_ALPHA, this.raster.blendDstRgb());
        assertEquals(0xF, this.raster.colorMask());
    }

    /** Capabilities are their own group, and the restore has to work in both directions, not only "re-enable". */
    @Test
    void theEnableBitPutsCapabilitiesBackInBothDirections() throws Exception {
        call("glDisable", GL_BLEND);
        call("glEnable", GL_DEPTH_TEST);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glEnable", GL_BLEND);
        call("glDisable", GL_DEPTH_TEST);
        assertEquals(1, call("glIsEnabled", GL_BLEND));
        assertEquals(0, call("glIsEnabled", GL_DEPTH_TEST));
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(0, call("glIsEnabled", GL_BLEND), "a capability that was disabled at the push comes back off");
        assertEquals(1, call("glIsEnabled", GL_DEPTH_TEST), "and one that was enabled comes back on");
    }

    /**
     * The depth group: the write mask and the comparison, read back through the queries GL defines for them.
     *
     * <p>These two are the state that leaves the most visible trace when it leaks - a mod that turns depth writes off
     * inside a push and expects the pop to restore them makes every later pass non-depth-writing - so the answer comes
     * from {@code glGetBooleanv(GL_DEPTH_WRITEMASK)} and {@code glGetIntegerv(GL_DEPTH_FUNC)}, the calls a mod's own
     * save/restore code uses.
     */
    @Test
    void theDepthBufferBitPutsTheWriteMaskAndTheComparisonBack() throws Exception {
        CeltiumGlStub.celtium$noteDepthMask(true);
        CeltiumGlStub.celtium$noteDepthFunc(GL_LESS);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.DEPTH_BUFFER_BIT);
        CeltiumGlStub.celtium$noteDepthMask(false);
        CeltiumGlStub.celtium$noteDepthFunc(GL_GREATER);
        assertEquals(0, queryBoolean(GL_DEPTH_WRITEMASK));
        assertEquals(GL_GREATER, queryInt(GL_DEPTH_FUNC));
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(1, queryBoolean(GL_DEPTH_WRITEMASK), "GL_DEPTH_WRITEMASK is GL_DEPTH_BUFFER_BIT state");
        assertEquals(GL_LESS, queryInt(GL_DEPTH_FUNC));
    }

    /** The polygon and scissor groups: the cull mode, the box and the offset are all saved by their bits. */
    @Test
    void thePolygonAndScissorBitsPutTheModeTheBoxAndTheOffsetBack() throws Exception {
        call("glCullFace", GL_BACK);
        this.raster.scissor(0, 0, 1280, 720);
        this.raster.polygonOffset(0.0f, 0.0f);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.POLYGON_BIT | LegacyAttribSnapshot.SCISSOR_BIT);
        call("glCullFace", GL_FRONT);
        call("glScissor", 1, 2, 3, 4);
        callFloats("glPolygonOffset", 7.0f, 8.0f);
        assertEquals(GL_FRONT, this.raster.cullFace());
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(GL_BACK, this.raster.cullFace(), "GL_CULL_FACE_MODE is GL_POLYGON_BIT state");
        assertEquals(0, this.raster.scissorX());
        assertEquals(0, this.raster.scissorY());
        assertEquals(1280, this.raster.scissorWidth());
        assertEquals(720, this.raster.scissorHeight());
        assertEquals(0.0f, this.raster.polygonOffsetFactor(), 0.0f);
        assertEquals(0.0f, this.raster.polygonOffsetUnits(), 0.0f);
    }

    /** An alpha comparison changed inside a colour-buffer push comes back, not just its enable bit. */
    @Test
    void theColourBufferBitPutsTheAlphaComparisonBack() throws Exception {
        CeltiumGlStub.celtium$noteAlphaFunc(GL_ALWAYS, 0.0f);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        CeltiumGlStub.celtium$noteAlphaFunc(GL_GREATER, 0.9f);
        assertEquals(GL_GREATER, queryInt(GL_ALPHA_TEST_FUNC));
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(GL_ALWAYS, queryInt(GL_ALPHA_TEST_FUNC), "GL_ALPHA_TEST_FUNC is GL_COLOR_BUFFER_BIT state");
    }

    /** Nested pushes restore in order, which is what a stack is for and what a single-value save cannot do. */
    @Test
    void nestedPushesRestoreInTheOrderTheyWerePushed() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glColorMask", 1, 1, 1, 0);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glColorMask", 0, 0, 0, 0);
        CeltiumGlStub.celtium$popAttrib();
        // glColorMask(true,true,true,false) is red|green|blue = 0x7, not 0xE: the bits are channel order, low to high.
        assertEquals(0x7, this.raster.colorMask(), "the inner pop restores what the inner push saved");
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(0xF, this.raster.colorMask(), "and the outer pop restores the value from before the inner push");
    }

    /** A pop with nothing pushed changes nothing and is counted, rather than restoring a stale snapshot. */
    @Test
    void aPopWithoutAPushChangesNothingAndIsCounted() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        CeltiumGlStub.celtium$popAttrib();
        assertEquals(0xF, this.raster.colorMask());
        String census = CeltiumGlStub.attribCensus();
        assertTrue(census.contains("unmatchedPops=1"), census);
        assertTrue(census.contains("masks=true"), census);
    }

    /** An unbalanced push cannot grow the stack without bound; the overflow is visible in the census. */
    @Test
    void anUnbalancedPushIsBoundedAndCounted() throws Exception {
        int pushes = 70;
        for (int i = 0; i < pushes; i++) {
            CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        }
        for (int i = 0; i < pushes; i++) {
            CeltiumGlStub.celtium$popAttrib();
        }
        String census = CeltiumGlStub.attribCensus();
        assertTrue(census.contains("pushes=" + pushes), census);
        assertTrue(census.contains("overflow=" + (pushes - 64)), census);
        assertTrue(census.contains("unmatchedPops=" + (pushes - 64)), census);
    }

    /**
     * The readback the fixture uses has to answer at all: a query that writes nothing reads as "the mask is off" and
     * would make every restore look vacuous. This pins the two boolean vectors the display-list scene reads.
     */
    @Test
    void theColourWriteMaskBooleanQueryWritesAllFourChannels() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        assertEquals("1111", queryBooleanVector(0x0C23, 4), "GL_COLOR_WRITEMASK is a four-byte vector, not one byte");
        call("glColorMask", 1, 0, 1, 0);
        assertEquals("1010", queryBooleanVector(0x0C23, 4));
        assertEquals(1, queryBoolean(GL_DEPTH_WRITEMASK), "and the scalar boolean query answers from the same record");
        CeltiumGlStub.celtium$noteDepthMask(false);
        assertEquals(0, queryBoolean(GL_DEPTH_WRITEMASK));
    }

    private String queryBooleanVector(int pname, int count) throws Exception {
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetBooleanv", pname, MemoryUtil.memAddress(out));
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < count; i++) {
                text.append(MemoryUtil.memGetByte(MemoryUtil.memAddress(out) + i) != 0 ? '1' : '0');
            }
            return text.toString();
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    /** The mask rule itself, as arithmetic: no GL, so the bit meanings are asserted directly for every group. */
    @Test
    void theMaskRuleSelectsOnlyTheGroupsTheCallerPushed() {
        LegacyAttribSnapshot snapshot = new LegacyAttribSnapshot(0);
        snapshot.mask = LegacyAttribSnapshot.ENABLE_BIT;
        assertTrue(snapshot.restores(LegacyAttribSnapshot.ENABLE_BIT));
        assertFalse(snapshot.restores(LegacyAttribSnapshot.COLOR_BUFFER_BIT));
        assertFalse(snapshot.restores(LegacyAttribSnapshot.TRANSFORM_BIT));
        snapshot.mask = LegacyAttribSnapshot.ALL_ATTRIB_BITS;
        int[] groups = {
                LegacyAttribSnapshot.CURRENT_BIT, LegacyAttribSnapshot.POINT_BIT, LegacyAttribSnapshot.LINE_BIT,
                LegacyAttribSnapshot.POLYGON_BIT, LegacyAttribSnapshot.LIGHTING_BIT, LegacyAttribSnapshot.FOG_BIT,
                LegacyAttribSnapshot.DEPTH_BUFFER_BIT, LegacyAttribSnapshot.STENCIL_BUFFER_BIT,
                LegacyAttribSnapshot.VIEWPORT_BIT, LegacyAttribSnapshot.TRANSFORM_BIT,
                LegacyAttribSnapshot.ENABLE_BIT, LegacyAttribSnapshot.COLOR_BUFFER_BIT,
                LegacyAttribSnapshot.LIST_BIT, LegacyAttribSnapshot.TEXTURE_BIT, LegacyAttribSnapshot.SCISSOR_BIT,
        };
        for (int group : groups) {
            assertTrue(snapshot.restores(group), "GL_ALL_ATTRIB_BITS must select 0x" + Integer.toHexString(group));
        }
        // The groups that are named, selected and not restorable are counted rather than silently dropped: the matrix
        // stack is the one that matters (mods save it with glPushMatrix, which is routed).
        assertEquals(LegacyAttribSnapshot.UNMODELLED_GROUPS, LegacyAttribSnapshot.unmodelled(snapshot.mask));
        assertEquals(0, LegacyAttribSnapshot.unmodelled(
                LegacyAttribSnapshot.ENABLE_BIT | LegacyAttribSnapshot.COLOR_BUFFER_BIT
                        | LegacyAttribSnapshot.DEPTH_BUFFER_BIT | LegacyAttribSnapshot.POLYGON_BIT
                        | LegacyAttribSnapshot.SCISSOR_BIT));
        assertEquals(LegacyAttribSnapshot.TRANSFORM_BIT | LegacyAttribSnapshot.LIGHTING_BIT,
                LegacyAttribSnapshot.unmodelled(LegacyAttribSnapshot.TRANSFORM_BIT
                        | LegacyAttribSnapshot.LIGHTING_BIT | LegacyAttribSnapshot.ENABLE_BIT));
        // Vanilla's own mask is 8256 = GL_ENABLE_BIT | GL_LIGHTING_BIT: it selects one modelled and one unmodelled
        // group, which is why the lighting state is named in the census rather than guessed at.
        assertEquals(LegacyAttribSnapshot.ENABLE_BIT, LegacyAttribSnapshot.modelled(8256));
    }

    /**
     * {@code GL_VIEWPORT_BIT}: the group this pack's own mods select and change.
     *
     * <p>The provider is measured rather than invented: three Supersymmetry space renderers push {@code 0xFFFFF}, call
     * {@code glViewport} to the window size inside the block and rely on the pop to undo it, and BetterQuesting's
     * {@code RenderUtils} pushes {@code 0x40B71}, which selects the same group. The renderer reads the rectangle per
     * offer from {@code celtium$viewportPacked}, so the assertion is on the query a mod's save/restore uses and on the
     * packed value the capture uses - a restore that wrote only one of the two would leave the other describing the
     * popped block.
     */
    @Test
    void aPushThatSelectsTheViewportPutsTheRectangleBack() throws Exception {
        call("glViewport", 0, 0, 800, 600);
        assertArrayEquals(new int[] {0, 0, 800, 600}, queryInts(0x0BA2, 4), "the caller's rectangle, through GL_VIEWPORT");
        assertEquals(((long) 0 << 48) | ((long) 0 << 32) | ((long) 800 << 16) | 600L,
                CeltiumGlStub.celtium$viewportPacked(), "and the packed rectangle the capture reads");
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.VIEWPORT_BIT);
        call("glViewport", 11, 13, 17, 19);
        assertArrayEquals(new int[] {11, 13, 17, 19}, queryInts(0x0BA2, 4),
                "the block's own rectangle is in force inside the push");
        assertEquals(((long) 11 << 48) | ((long) 13 << 32) | ((long) 17 << 16) | 19L,
                CeltiumGlStub.celtium$viewportPacked());
        CeltiumGlStub.celtium$popAttrib();
        assertArrayEquals(new int[] {0, 0, 800, 600}, queryInts(0x0BA2, 4), "the pop put the caller's rectangle back");
        assertEquals(((long) 0 << 48) | ((long) 0 << 32) | ((long) 800 << 16) | 600L,
                CeltiumGlStub.celtium$viewportPacked(),
                "and the packed rectangle the group key and the capture read - not only the query's fields");
    }

    /** The mask rule for the same group: a push that does not select it leaves the block's rectangle in force. */
    @Test
    void aPushWithoutTheViewportGroupLeavesTheBlockRectangleInForce() throws Exception {
        call("glViewport", 0, 0, 800, 600);
        CeltiumGlStub.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glViewport", 23, 29, 31, 37);
        CeltiumGlStub.celtium$popAttrib();
        assertArrayEquals(new int[] {23, 29, 31, 37}, queryInts(0x0BA2, 4),
                "GL_ENABLE_BIT saves capabilities, not GL_VIEWPORT");
        assertEquals(((long) 23 << 48) | ((long) 29 << 32) | ((long) 31 << 16) | 37L,
                CeltiumGlStub.celtium$viewportPacked());
    }

    /** Two pushes own separate records, so the second push cannot overwrite what the first one saved. */
    @Test
    void eachPushOwnsItsRecord() {
        LegacyAttribSnapshot first = new LegacyAttribSnapshot(2);
        LegacyAttribSnapshot second = new LegacyAttribSnapshot(2);
        first.mask = LegacyAttribSnapshot.ENABLE_BIT;
        first.capabilities[0] = true;
        first.colorMask = 0x1;
        assertFalse(second.capabilities[0], "each push allocates its own capability record");
        assertFalse(second.restores(LegacyAttribSnapshot.ENABLE_BIT), "and its own mask");
        assertEquals(0xF, second.colorMask, "and its own values");
        assertTrue(first.restores(LegacyAttribSnapshot.ENABLE_BIT));
        assertEquals(0x1, first.colorMask);
    }
}
