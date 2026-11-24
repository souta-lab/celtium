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
    private final GlsmFrontend adapter;
    private final Method dispatch;
    private final Map<?, ?> ids;
    private final Field installedField;
    private GlsmFrontend previousInstalled;

    LegacyAttribStackTest() throws Exception {
        var constructor = GlsmFrontend.class.getDeclaredConstructor(
                NativeRasterState.class, NativeTextureBindings.class);
        constructor.setAccessible(true);
        this.adapter = constructor.newInstance(this.raster, this.textures);
        this.dispatch = GlsmFrontend.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        var table = GlsmFrontend.class.getDeclaredField("IDS");
        table.setAccessible(true);
        this.ids = (Map<?, ?>) table.get(null);
        this.installedField = GlsmFrontend.class.getDeclaredField("installed");
        this.installedField.setAccessible(true);
    }

    @BeforeEach
    void installStub() throws Exception {
        this.previousInstalled = (GlsmFrontend) this.installedField.get(null);
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
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glColorMask", 1, 0, 0, 0);
        assertEquals(0x1, this.raster.colorMask(), "the write inside the push has to reach the renderer");
        GlsmFrontend.celtium$popAttrib();
        assertEquals(0x1, this.raster.colorMask(),
                "GL_ENABLE_BIT does not save GL_COLOR_WRITEMASK (that is GL_COLOR_BUFFER_BIT), so the pop must not "
                        + "put it back");
    }

    /** The group the mask does name: the blend factors, the equation and the colour write mask all come back. */
    @Test
    void aPushThatSelectsTheColourBufferPutsTheBlendStateBack() throws Exception {
        call("glBlendFunc", GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        call("glColorMask", 1, 1, 1, 1);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glBlendFunc", 1, 0);
        call("glColorMask", 1, 0, 0, 0);
        assertEquals(1, this.raster.blendSrcRgb());
        GlsmFrontend.celtium$popAttrib();
        assertEquals(GL_SRC_ALPHA, this.raster.blendSrcRgb(), "the source factor is GL_COLOR_BUFFER_BIT state");
        assertEquals(GL_ONE_MINUS_SRC_ALPHA, this.raster.blendDstRgb());
        assertEquals(0xF, this.raster.colorMask());
    }

    /** Capabilities are their own group, and the restore has to work in both directions, not only "re-enable". */
    @Test
    void theEnableBitPutsCapabilitiesBackInBothDirections() throws Exception {
        call("glDisable", GL_BLEND);
        call("glEnable", GL_DEPTH_TEST);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glEnable", GL_BLEND);
        call("glDisable", GL_DEPTH_TEST);
        assertEquals(1, call("glIsEnabled", GL_BLEND));
        assertEquals(0, call("glIsEnabled", GL_DEPTH_TEST));
        GlsmFrontend.celtium$popAttrib();
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
        GlsmFrontend.celtium$noteDepthMask(true);
        GlsmFrontend.celtium$noteDepthFunc(GL_LESS);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.DEPTH_BUFFER_BIT);
        GlsmFrontend.celtium$noteDepthMask(false);
        GlsmFrontend.celtium$noteDepthFunc(GL_GREATER);
        assertEquals(0, queryBoolean(GL_DEPTH_WRITEMASK));
        assertEquals(GL_GREATER, queryInt(GL_DEPTH_FUNC));
        GlsmFrontend.celtium$popAttrib();
        assertEquals(1, queryBoolean(GL_DEPTH_WRITEMASK), "GL_DEPTH_WRITEMASK is GL_DEPTH_BUFFER_BIT state");
        assertEquals(GL_LESS, queryInt(GL_DEPTH_FUNC));
    }

    /** The polygon and scissor groups: the cull mode, the box and the offset are all saved by their bits. */
    @Test
    void thePolygonAndScissorBitsPutTheModeTheBoxAndTheOffsetBack() throws Exception {
        call("glCullFace", GL_BACK);
        this.raster.scissor(0, 0, 1280, 720);
        this.raster.polygonOffset(0.0f, 0.0f);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.POLYGON_BIT | LegacyAttribSnapshot.SCISSOR_BIT);
        call("glCullFace", GL_FRONT);
        call("glScissor", 1, 2, 3, 4);
        callFloats("glPolygonOffset", 7.0f, 8.0f);
        assertEquals(GL_FRONT, this.raster.cullFace());
        GlsmFrontend.celtium$popAttrib();
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
        GlsmFrontend.celtium$noteAlphaFunc(GL_ALWAYS, 0.0f);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        GlsmFrontend.celtium$noteAlphaFunc(GL_GREATER, 0.9f);
        assertEquals(GL_GREATER, queryInt(GL_ALPHA_TEST_FUNC));
        GlsmFrontend.celtium$popAttrib();
        assertEquals(GL_ALWAYS, queryInt(GL_ALPHA_TEST_FUNC), "GL_ALPHA_TEST_FUNC is GL_COLOR_BUFFER_BIT state");
    }

    /** Nested pushes restore in order, which is what a stack is for and what a single-value save cannot do. */
    @Test
    void nestedPushesRestoreInTheOrderTheyWerePushed() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glColorMask", 1, 1, 1, 0);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        call("glColorMask", 0, 0, 0, 0);
        GlsmFrontend.celtium$popAttrib();
        // glColorMask(true,true,true,false) is red|green|blue = 0x7, not 0xE: the bits are channel order, low to high.
        assertEquals(0x7, this.raster.colorMask(), "the inner pop restores what the inner push saved");
        GlsmFrontend.celtium$popAttrib();
        assertEquals(0xF, this.raster.colorMask(), "and the outer pop restores the value from before the inner push");
    }

    /** A pop with nothing pushed changes nothing and is counted, rather than restoring a stale snapshot. */
    @Test
    void aPopWithoutAPushChangesNothingAndIsCounted() throws Exception {
        call("glColorMask", 1, 1, 1, 1);
        GlsmFrontend.celtium$popAttrib();
        assertEquals(0xF, this.raster.colorMask());
        String census = GlsmFrontend.attribCensus();
        assertTrue(census.contains("unmatchedPops=1"), census);
        assertTrue(census.contains("masks=true"), census);
    }

    /** An unbalanced push cannot grow the stack without bound; the overflow is visible in the census. */
    @Test
    void anUnbalancedPushIsBoundedAndCounted() throws Exception {
        int pushes = 70;
        for (int i = 0; i < pushes; i++) {
            GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.COLOR_BUFFER_BIT);
        }
        for (int i = 0; i < pushes; i++) {
            GlsmFrontend.celtium$popAttrib();
        }
        String census = GlsmFrontend.attribCensus();
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
        GlsmFrontend.celtium$noteDepthMask(false);
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
        assertEquals(LegacyAttribSnapshot.TRANSFORM_BIT,
                LegacyAttribSnapshot.unmodelled(LegacyAttribSnapshot.TRANSFORM_BIT
                        | LegacyAttribSnapshot.LIGHTING_BIT | LegacyAttribSnapshot.ENABLE_BIT),
                "the lighting group is restored in part now, so only the matrix stack is left in the gap");
        // Vanilla's own mask is 8256 = GL_ENABLE_BIT | GL_LIGHTING_BIT: both groups are restored, and the lighting one
        // is reported as partial because the material and spot terms GL keeps in it are not modelled here.
        assertEquals(LegacyAttribSnapshot.ENABLE_BIT | LegacyAttribSnapshot.LIGHTING_BIT,
                LegacyAttribSnapshot.modelled(8256));
        assertEquals(LegacyAttribSnapshot.LIGHTING_BIT, LegacyAttribSnapshot.partial(8256));
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
                GlsmFrontend.celtium$viewportPacked(), "and the packed rectangle the capture reads");
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.VIEWPORT_BIT);
        call("glViewport", 11, 13, 17, 19);
        assertArrayEquals(new int[] {11, 13, 17, 19}, queryInts(0x0BA2, 4),
                "the block's own rectangle is in force inside the push");
        assertEquals(((long) 11 << 48) | ((long) 13 << 32) | ((long) 17 << 16) | 19L,
                GlsmFrontend.celtium$viewportPacked());
        GlsmFrontend.celtium$popAttrib();
        assertArrayEquals(new int[] {0, 0, 800, 600}, queryInts(0x0BA2, 4), "the pop put the caller's rectangle back");
        assertEquals(((long) 0 << 48) | ((long) 0 << 32) | ((long) 800 << 16) | 600L,
                GlsmFrontend.celtium$viewportPacked(),
                "and the packed rectangle the group key and the capture read - not only the query's fields");
    }

    /** The mask rule for the same group: a push that does not select it leaves the block's rectangle in force. */
    @Test
    void aPushWithoutTheViewportGroupLeavesTheBlockRectangleInForce() throws Exception {
        call("glViewport", 0, 0, 800, 600);
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glViewport", 23, 29, 31, 37);
        GlsmFrontend.celtium$popAttrib();
        assertArrayEquals(new int[] {23, 29, 31, 37}, queryInts(0x0BA2, 4),
                "GL_ENABLE_BIT saves capabilities, not GL_VIEWPORT");
        assertEquals(((long) 23 << 48) | ((long) 29 << 32) | ((long) 31 << 16) | 37L,
                GlsmFrontend.celtium$viewportPacked());
    }

    /**
     * {@code GL_LIGHTING_BIT} is in the modelled set <em>and</em> in the partial one, and the unmodelled set must not
     * still claim it.
     *
     * <p>The classification is what the census reports, and the census is how the next round decides what to work on:
     * leaving the group in {@code UNMODELLED_GROUPS} while also restoring it would count one pop twice - once as
     * restored, once as a gap - and a later round reading {@code selectedUnrestoredGroups} would go looking for a group
     * that is already handled. The mask is GregTech's, the one the calibrated world leg measured 1038 times.
     */
    @Test
    void theLightingGroupIsRestoredInPartAndNotCountedAsTheGap() {
        int gtMask = 0x40;
        assertEquals(LegacyAttribSnapshot.LIGHTING_BIT, LegacyAttribSnapshot.modelled(gtMask),
                "the group is restored");
        assertEquals(LegacyAttribSnapshot.LIGHTING_BIT, LegacyAttribSnapshot.partial(gtMask),
                "and reported as restored in part, because the material and spot terms are not modelled");
        assertEquals(0, LegacyAttribSnapshot.unmodelled(gtMask),
                "a group that is restored must not also be counted as the unmodelled gap");
        assertEquals(0, LegacyAttribSnapshot.partial(
                        LegacyAttribSnapshot.ENABLE_BIT | LegacyAttribSnapshot.SCISSOR_BIT),
                "neither of these groups is partial - they are restored whole");
    }

    /** Two pushes own separate lighting records, so the second push cannot overwrite what the first one saved. */
    @Test
    void eachPushOwnsItsLightingRecord() {
        LegacyAttribSnapshot first = new LegacyAttribSnapshot(2);
        LegacyAttribSnapshot second = new LegacyAttribSnapshot(2);
        first.lightDirections[0] = 0.25f;
        first.lightDiffuse[2] = 0.5f;
        first.lightAmbient[1] = 0.75f;
        first.shadeModel = 0x1D00;
        assertEquals(0.0f, second.lightDirections[0], "each push allocates its own direction array");
        assertEquals(0.0f, second.lightDiffuse[2]);
        assertEquals(0.0f, second.lightAmbient[1]);
        assertEquals(0x1D01, second.shadeModel, "and its own shade model, GL_SMOOTH by default");
        assertFalse(second.lightingCaptured, "and its own capture flag");
        assertEquals(0.25f, first.lightDirections[0]);
    }

    /**
     * Without a lighting mirror a push must not claim the group.
     *
     * <p>The restore writes the backend's immediate-state mirror, which is the only authority for the lighting payload;
     * this stub keeps no second copy. A push taken before that mirror exists carries GL's initial values, and its pop
     * must not write them over a mirror that has since started tracking - and it must not report the group as restored
     * either, or the census would say the pack's {@code 0x40} pushes are handled when they are not.
     */
    @Test
    void withoutALightingMirrorTheGroupIsNotCountedAsRestored() {
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.LIGHTING_BIT);
        GlsmFrontend.celtium$popAttrib();
        String census = GlsmFrontend.attribCensus();
        assertTrue(census.contains("selectedPartialGroups=1"), census);
        assertTrue(census.contains("lightingRestores=0"), census);
        assertTrue(census.contains("lightingRestoresChanged=0"), census);
        assertTrue(census.contains(" restoredGroups=0"), census);
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

    private static final int GL_FOG = 0x0B60;
    private static final int GL_FOG_MODE = 0x0B65;
    private static final int GL_FOG_DENSITY = 0x0B62;
    private static final int GL_FOG_START = 0x0B63;
    private static final int GL_FOG_END = 0x0B64;
    private static final int GL_FOG_COLOR = 0x0B66;
    private static final int GL_LINEAR = 9729;
    private static final int GL_EXP = 2048;

    /** One tracked float query, through {@code glGetFloatv} as a mod's own save/restore reads it. */
    private float queryFloat(int pname) throws Exception {
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetFloatv", pname, MemoryUtil.memAddress(out));
            return MemoryUtil.memGetFloat(MemoryUtil.memAddress(out));
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    /** The fog colour is a four-float vector; the scalar helper above cannot read it. */
    private float[] queryFloats(int pname, int count) throws Exception {
        var out = MemoryUtil.memAlloc(16 * Math.max(1, count));
        try {
            call("glGetFloatv", pname, MemoryUtil.memAddress(out));
            float[] values = new float[count];
            for (int i = 0; i < count; i++) {
                values[i] = MemoryUtil.memGetFloat(MemoryUtil.memAddress(out) + 4L * i);
            }
            return values;
        } finally {
            MemoryUtil.memFree(out);
        }
    }

    private void setFogColour(float red, float green, float blue, float alpha) throws Exception {
        var values = MemoryUtil.memAlloc(16);
        try {
            MemoryUtil.memPutFloat(MemoryUtil.memAddress(values), red);
            MemoryUtil.memPutFloat(MemoryUtil.memAddress(values) + 4L, green);
            MemoryUtil.memPutFloat(MemoryUtil.memAddress(values) + 8L, blue);
            MemoryUtil.memPutFloat(MemoryUtil.memAddress(values) + 12L, alpha);
            call("glFogfv", GL_FOG_COLOR, MemoryUtil.memAddress(values));
        } finally {
            MemoryUtil.memFree(values);
        }
    }

    /** The fog owner is one JVM-wide record, so a fog test leaves GL's defaults behind for the next one. */
    private void resetFog() throws Exception {
        call("glDisable", GL_FOG);
        call("glFogi", GL_FOG_MODE, GL_EXP);
        call("glFogf", GL_FOG_DENSITY, Float.floatToRawIntBits(1.0f));
        call("glFogf", GL_FOG_START, Float.floatToRawIntBits(0.0f));
        call("glFogf", GL_FOG_END, Float.floatToRawIntBits(1.0f));
        setFogColour(0.0f, 0.0f, 0.0f, 0.0f);
    }

    /**
     * {@code GL_FOG_BIT}: the measured provider's own shape (item 420, from the ATM3 scan's section 3.1).
     *
     * <p>AdvancedRocketry's {@code RocketEventHandler.onPostWorldRender} pushes {@code 0xBC1} - the fog group
     * without {@code GL_ENABLE_BIT} - writes {@code disableFog} and then {@code enableFog} inside the block and
     * lets the pop undo them. GL keeps the fog enable in the fog group, so the pop has to put it back even though
     * the mask never selected the capabilities; the parameters are the rest of the same group and come back with
     * it. The assertions read the state back through the queries a mod's own save/restore uses.
     */
    @Test
    void theFogBitPutsTheEnableAndTheParametersBack() throws Exception {
        resetFog();
        call("glFogi", GL_FOG_MODE, GL_LINEAR);
        call("glFogf", GL_FOG_DENSITY, Float.floatToRawIntBits(0.05f));
        call("glFogf", GL_FOG_START, Float.floatToRawIntBits(10.0f));
        call("glFogf", GL_FOG_END, Float.floatToRawIntBits(90.0f));
        setFogColour(0.25f, 0.5f, 0.75f, 0.5f);
        int mask = LegacyAttribSnapshot.CURRENT_BIT | LegacyAttribSnapshot.LIGHTING_BIT
                | LegacyAttribSnapshot.FOG_BIT | LegacyAttribSnapshot.DEPTH_BUFFER_BIT
                | LegacyAttribSnapshot.ACCUM_BUFFER_BIT | LegacyAttribSnapshot.VIEWPORT_BIT;
        assertEquals(0xBC1, mask, "the provider's mask, named group by group");
        GlsmFrontend.celtium$pushAttrib(mask);
        call("glEnable", GL_FOG);
        call("glFogi", GL_FOG_MODE, GL_EXP);
        call("glFogf", GL_FOG_DENSITY, Float.floatToRawIntBits(0.5f));
        call("glFogf", GL_FOG_START, Float.floatToRawIntBits(1.0f));
        call("glFogf", GL_FOG_END, Float.floatToRawIntBits(2.0f));
        setFogColour(1.0f, 0.0f, 0.0f, 1.0f);
        assertEquals(1, call("glIsEnabled", GL_FOG), "the block's own fog state is in force inside the push");
        GlsmFrontend.celtium$popAttrib();
        assertEquals(0, call("glIsEnabled", GL_FOG),
                "GL_FOG is GL_FOG_BIT state, so the pop puts the enable back without GL_ENABLE_BIT");
        assertEquals(GL_LINEAR, queryInt(GL_FOG_MODE), "GL_FOG_MODE is GL_FOG_BIT state");
        assertEquals(0.05f, queryFloat(GL_FOG_DENSITY), 0.0f);
        assertEquals(10.0f, queryFloat(GL_FOG_START), 0.0f);
        assertEquals(90.0f, queryFloat(GL_FOG_END), 0.0f);
        assertArrayEquals(new float[] {0.25f, 0.5f, 0.75f, 0.5f}, queryFloats(GL_FOG_COLOR, 4), 0.0f,
                "GL_FOG_COLOR is GL_FOG_BIT state");
        resetFog();
    }

    /**
     * The mask rule for the same state, split the way GL splits it: the enable is a capability as well as fog
     * state, so an {@code GL_ENABLE_BIT} pop puts it back - but the parameters belong to the fog group alone, so
     * a push that does not select that group leaves the block's parameters in force. The provider's leak lived
     * in the complementary shape (fog group without the enable group), which the test above pins.
     */
    @Test
    void aPushThatDoesNotSelectTheFogGroupLeavesTheParametersItFoundInForce() throws Exception {
        resetFog();
        GlsmFrontend.celtium$pushAttrib(LegacyAttribSnapshot.ENABLE_BIT);
        call("glEnable", GL_FOG);
        call("glFogf", GL_FOG_DENSITY, Float.floatToRawIntBits(0.5f));
        GlsmFrontend.celtium$popAttrib();
        assertEquals(0, call("glIsEnabled", GL_FOG),
                "the enable is restored here too, through the capability the enable group saved");
        assertEquals(0.5f, queryFloat(GL_FOG_DENSITY), 0.0f,
                "but the parameters are the fog group's alone, so the block's density stays in force");
        resetFog();
    }

    /**
     * The fog group is in the modelled set <em>and</em> in the partial one, and the unmodelled set must not still
     * claim it - the same census rule the lighting test pins, for the second group that joined both sets.
     *
     * <p>The mask is the provider's {@code 0xBC1}: of its six groups only the accum group stays in the gap, and
     * the partial count names the two groups restored in part (lighting for its material terms, fog for
     * {@code GL_FOG_INDEX}).
     */
    @Test
    void theFogGroupIsRestoredInPartAndNotCountedAsTheGap() {
        int arMask = 0xBC1;
        assertEquals(LegacyAttribSnapshot.FOG_BIT,
                LegacyAttribSnapshot.modelled(arMask) & LegacyAttribSnapshot.FOG_BIT,
                "the group is restored");
        assertEquals(LegacyAttribSnapshot.LIGHTING_BIT | LegacyAttribSnapshot.FOG_BIT,
                LegacyAttribSnapshot.partial(arMask),
                "and reported as restored in part, because GL_FOG_INDEX is not modelled");
        assertEquals(LegacyAttribSnapshot.ACCUM_BUFFER_BIT, LegacyAttribSnapshot.unmodelled(arMask),
                "only the accum group of the provider's mask is left in the gap");
    }

    /** Two pushes own separate fog records, so the second push cannot overwrite what the first one saved. */
    @Test
    void eachPushOwnsItsFogRecord() {
        LegacyAttribSnapshot first = new LegacyAttribSnapshot(2);
        LegacyAttribSnapshot second = new LegacyAttribSnapshot(2);
        first.fogEnabled = true;
        first.fogDensity = 0.5f;
        first.fogColor[0] = 0.25f;
        assertFalse(second.fogEnabled, "each push allocates its own fog record");
        assertEquals(1.0f, second.fogDensity, "and its own density, GL's default");
        assertEquals(0.0f, second.fogColor[0], "and its own colour");
        assertEquals(GL_EXP, second.fogMode, "and its own mode, GL_EXP by default");
        assertEquals(1.0f, second.fogEnd, "and its own end, GL's default");
        assertTrue(first.fogEnabled);
        assertEquals(0.5f, first.fogDensity);
    }
}
