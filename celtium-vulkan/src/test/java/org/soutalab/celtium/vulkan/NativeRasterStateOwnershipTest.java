package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.TextureCaptureStore;
import org.soutalab.celtium.vulkan.VulkanTextureRegistry;
import org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;
import org.soutalab.celtium.glcompat.capture.GlFn;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises real raw-GL dispatch against the same owner the native frontend reads, without any graphics context.
 * <p>
 * E4-c form: the monolith took the owner pair by constructor injection, so each test drove a
 * fresh pair; the capture entry reads the singleton owners ({@link NativeRasterState#current()},
 * {@link NativeTextureBindings#current()}), so the suite drives those and restores the GL
 * defaults it touches before each test. The contract is unchanged: one owner, read by both the
 * wrapper and the raw dispatch.
 */
class NativeRasterStateOwnershipTest {
    private final NativeRasterState raster = NativeRasterState.current();
    private final NativeTextureBindings textures = NativeTextureBindings.current();
    private final Lane2ImmediateState nativeState = new Lane2ImmediateState(this.raster, this.textures);
    private final DirectGlCaptureEntry adapter;
    private final Method dispatch;
    private final Map<String, Integer> ids;
    private Object previousEntry;

    NativeRasterStateOwnershipTest() throws Exception {
        var constructor = DirectGlCaptureEntry.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        this.adapter = constructor.newInstance();
        this.dispatch = DirectGlCaptureEntry.class.getDeclaredMethod("dispatchArgs", GlFn.class, long[].class);
        this.dispatch.setAccessible(true);
        this.ids = DirectGlCaptureEntry.ids();
    }

    @BeforeEach
    void installAdapterAndResetSeats() throws Exception {
        Field installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        this.previousEntry = installed.get(null);
        installed.set(null, this.adapter);
        // The seats are shared now; put back the GL defaults the tests read as their start state.
        this.nativeState.blendFunc(Lane2ImmediateState.BLEND_SRC_ALPHA,
                Lane2ImmediateState.BLEND_ONE_MINUS_SRC_ALPHA, Lane2ImmediateState.BLEND_ONE,
                Lane2ImmediateState.BLEND_ZERO);
        this.nativeState.blendEnabled(false);
        this.nativeState.colorMask(0xF);
        this.nativeState.cullEnabled(false);
        this.nativeState.cullFace(Lane2ImmediateState.CULL_BACK);
        this.raster.scissorTest(false);
        this.raster.polygonOffsetFillEnabled(false);
        this.nativeState.activeTextureUnit(0);
        this.nativeState.bindTexture(0);
        this.nativeState.texture2dEnabled(true);
    }

    @AfterEach
    void restoreAdapter() throws Exception {
        Field installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, this.previousEntry);
        this.raster.beforeChange(null);
    }

    private long call(String name, long... args) throws Exception {
        assertTrue(this.ids.containsKey(name), "must use the real adapter entry: " + name);
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.adapter, DirectGlCaptureEntry.row(this.ids.get(name)), slots);
    }

    @Test
    void nativeStateCannotBeOverwrittenByAnOlderRawGlRecord() throws Exception {
        // E4-c narrowing: the raw blend-factors call routes to the converter's own stack now
        // (the entry's dispatch serves GLSMBlend, whose factors the executor consumes), so the
        // shared-record half of this contract is the enable and query surface below. The
        // factor record itself is the wrapper's; a raw call must not move it.
        this.nativeState.blendFunc(1, 0, 1, 0);
        call("glBlendFuncSeparate", 0x0302, 0x0303, 1, 0);
        call("glEnable", 0x0BE2);
        assertTrue(this.nativeState.blendEnabled());
        assertEquals(1, this.nativeState.blendSrcRgb());
        // A vanilla write AFTER a raw call must win, including when the wrapper's cached value skips real GL.
        this.nativeState.blendFunc(1, 0, 1, 0);
        this.nativeState.blendEnabled(false);
        assertEquals(0, call("glIsEnabled", 0x0BE2));
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetIntegerv", 0x80C9, MemoryUtil.memAddress(out));
            assertEquals(1, out.getInt(0));
            call("glGetIntegerv", 0x80C8, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0));
            call("glEnable", 0x0BE2);
            assertEquals(1, this.nativeState.blendSrcRgb(), "enabling must not re-import the old factors");
        } finally { MemoryUtil.memFree(out); }
    }

    @Test
    void nativeMaskAndCullChangesAreVisibleToModSaveRestoreQueries() throws Exception {
        this.nativeState.colorMask(0x5); // red and blue only; asymmetric channels catch reversed boolean queries
        this.nativeState.cullEnabled(true);
        this.nativeState.cullFace(0x0404);
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetBooleanv", 0x0C23, MemoryUtil.memAddress(out));
            assertArrayEquals(new byte[] {1, 0, 1, 0}, new byte[] {out.get(0), out.get(1), out.get(2), out.get(3)});
            assertEquals(1, call("glIsEnabled", 0x0B44));
            call("glGetIntegerv", 0x0B45, MemoryUtil.memAddress(out));
            assertEquals(0x0404, out.getInt(0));
            // E4-c narrowing: a raw glColorMask lands in the converter's stack, not this record;
            // the save/restore direction pinned here is the query answering the wrapper's mask.
            this.nativeState.colorMask(0xA);
            call("glGetBooleanv", 0x0C23, MemoryUtil.memAddress(out));
            assertArrayEquals(new byte[] {0, 1, 0, 1}, new byte[] {out.get(0), out.get(1), out.get(2), out.get(3)});
            call("glDisable", 0x0B44);
            assertEquals(0xA, this.nativeState.colorMask());
            assertFalse(this.nativeState.cullEnabled());
        } finally { MemoryUtil.memFree(out); }
    }

    /**
     * The cull mode starts at GL's own enum, not at an internal index.
     *
     * <p>The owner used to start at {@code Lane2ImmediateState.CULL_BACK}, an internal {@code 1}, while every writer - the raw
     * {@code glCullFace} adapter, the managed setter and the stub - stored GL's enum. A save/restore that read the mode
     * before vanilla had set one got {@code 1}, which is not a GL enum at all: the display-list leg measured
     * {@code cf=1} against a Celeritas reference answering {@code 1029} (GL_BACK). One encoding for a value the GL
     * query answers is the fix; the constants are GL's enums so a future comparison cannot mix them again.
     */
    @Test
    void theCullModeStartsAtGlBackAndTheConstantsAreGlEnums() throws Exception {
        assertEquals(0x0405, new NativeRasterState().cullFace(), "GL's initial cull mode is GL_BACK");
        assertEquals(0x0405, this.raster.cullFace(), "the shared owner is reset to GL's initial cull mode");
        assertEquals(0x0404, Lane2ImmediateState.CULL_FRONT);
        assertEquals(0x0405, Lane2ImmediateState.CULL_BACK);
        assertEquals(0x0408, Lane2ImmediateState.CULL_BOTH);
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetIntegerv", 0x0B45, MemoryUtil.memAddress(out));
            assertEquals(0x0405, out.getInt(0),
                    "the query must answer the enum GL answers before any caller sets a face");
        } finally { MemoryUtil.memFree(out); }
    }

    @Test
    void deferredGeometryObservesOldStateAndSnapshotsOutliveSubsequentChanges() throws Exception {
        var observed = new ArrayList<Integer>();
        this.raster.beforeChange(() -> observed.add(this.nativeState.colorMask()));
        // E4-c narrowing: the raw call that still lands in this record is glScissor (a raw
        // glColorMask routes to the converter's stack); the boundary contract is unchanged.
        call("glScissor", 1, 1, 10, 10);
        this.nativeState.colorMask(2);
        this.nativeState.colorMask(2);
        assertEquals(java.util.List.of(15, 15), observed, "one boundary before each actual change");
        call("glScissor", 3, 4, 50, 60);
        int[] snapshot = this.raster.scissorBox();
        call("glScissor", 7, 8, 90, 100);
        assertArrayEquals(new int[] {3, 4, 50, 60}, snapshot);
        call("glEnable", 0x0C11);
        assertTrue(this.raster.scissorTest());
        // E4-c narrowing: the raw glPolygonOffset call routes to the converter's rasterizer;
        // the factors are written through the owner here, the enable still arrives raw.
        this.raster.polygonOffset(-1.0f, -3.0f);
        call("glEnable", 0x8037);
        assertEquals(-1.0f, this.raster.polygonOffsetFactor());
        assertEquals(-3.0f, this.raster.polygonOffsetUnits());
        assertTrue(this.raster.polygonOffsetFillEnabled());
    }

    @Test
    void textureBindingsAndActiveUnitRoundTripWithoutPerDrawReconciliation() throws Exception {
        call("glActiveTexture", 0x84C1);
        call("glBindTexture", 0x0DE1, 91);
        assertEquals(1, this.nativeState.activeTextureUnit());
        assertEquals(91, this.nativeState.boundTextureName(1));
        assertEquals(0, this.nativeState.boundTextureName());
        this.nativeState.activeTextureUnit(0);
        this.nativeState.bindTexture(73);
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glGetIntegerv", 0x8069, MemoryUtil.memAddress(out));
            assertEquals(73, out.getInt(0));
            call("glActiveTexture", 0x84C1);
            call("glGetIntegerv", 0x8069, MemoryUtil.memAddress(out));
            assertEquals(91, out.getInt(0));
            call("glDisable", 0x0DE1);
            assertFalse(this.nativeState.texture2dEnabled(1));
            assertTrue(this.nativeState.texture2dEnabled());
            this.nativeState.texture2dEnabled(true);
            assertEquals(1, call("glIsEnabled", 0x0DE1));
            this.textures.delete(91);
            call("glGetIntegerv", 0x8069, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0));
            assertEquals(73, this.nativeState.boundTextureName());
        } finally { MemoryUtil.memFree(out); }
    }

    @Test
    void uploadsAndQueriesFollowNativeBindingAfterAnOlderRawBind() throws Exception {
        call("glBindTexture", 0x0DE1, 41);
        this.nativeState.bindTexture(73);
        var data = MemoryUtil.memCalloc(24);
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glTexImage2D", 0x0DE1, 0, 0x8058, 2, 3, 0, 0x1908, 0x1401, MemoryUtil.memAddress(data));
            call("glGetTexLevelParameteriv", 0x0DE1, 0, 0x1000, MemoryUtil.memAddress(out));
            assertEquals(2, out.getInt(0));
            call("glBindTexture", 0x0DE1, 41);
            call("glGetTexLevelParameteriv", 0x0DE1, 0, 0x1000, MemoryUtil.memAddress(out));
            assertEquals(0, out.getInt(0), "upload must not be filed under the stale raw-GL binding");
            this.nativeState.bindTexture(73);
            call("glGetTexLevelParameteriv", 0x0DE1, 0, 0x1001, MemoryUtil.memAddress(out));
            assertEquals(3, out.getInt(0));
        } finally { MemoryUtil.memFree(data); MemoryUtil.memFree(out); }
    }

    @Test
    void allocatingReplacementStorageKeepsItsBindingButDeletingTheNameUnbindsIt() throws Exception {
        // TextureUtil.allocateTextureImpl deletes/rebinds first and records storage at TAIL. Treating that storage
        // notification as a second deletion silently unbound the atlas before every sprite upload.
        var out = MemoryUtil.memAlloc(16);
        try {
            this.nativeState.bindTexture(73);
            VulkanTextureRegistry.captureStore().recordAllocation(73, 2, 8, 4);
            assertEquals(73, this.nativeState.boundTextureName());
            call("glGetIntegerv", 0x8069, MemoryUtil.memAddress(out));
            assertEquals(73, out.getInt(0));
            assertEquals(8, VulkanTextureRegistry.captureStore().textureLevelParameter(73, 0, 0x1000));
            VulkanTextureRegistry.captureStore().forgetAllocation(73);
            assertEquals(0, this.nativeState.boundTextureName());
            assertEquals(0, VulkanTextureRegistry.captureStore().textureLevelParameter(73, 0, 0x1000));
        } finally { MemoryUtil.memFree(out); }
    }

    @Test
    void boundaryFailureDoesNotPartiallyApplyTheNewState() {
        NativeRasterState fresh = new NativeRasterState();
        Lane2ImmediateState freshState = new Lane2ImmediateState(fresh, new NativeTextureBindings(fresh));
        fresh.beforeChange(() -> { throw new IllegalStateException("cannot submit old glyphs"); });
        assertThrows(IllegalStateException.class, () -> freshState.blendFunc(1, 0, 1, 0));
        assertEquals(Lane2ImmediateState.BLEND_SRC_ALPHA, freshState.blendSrcRgb());
        assertEquals(Lane2ImmediateState.BLEND_ONE_MINUS_SRC_ALPHA, freshState.blendDstRgb());
    }
    @Test
    void rawModFogAndNativeTerrainShareStateWhileQueuedDrawsKeepTheirSnapshot() throws Exception {
        ImmediateFogState fog = ImmediateFogState.current();
        var out = MemoryUtil.memAlloc(16);
        try {
            call("glEnable", 0x0B60);
            call("glFogi", 0x0B65, 9729);
            call("glFogf", 0x0B64, Float.floatToRawIntBits(128.0f));
            out.putFloat(0, 0.2f).putFloat(4, 0.4f).putFloat(8, 0.6f).putFloat(12, 0.7f);
            call("glFogfv", 0x0B66, MemoryUtil.memAddress(out));
            float[] queued = fog.snapshot();
            assertEquals(128, fog.getFogCutoff());
            assertEquals(0.2f, queued[4]);
            assertEquals(0.7f, fog.colorAlpha());
            fog.color(0.9f, 0.8f, 0.7f, 0.6f);
            call("glGetFloatv", 0x0B66, MemoryUtil.memAddress(out));
            assertEquals(0.9f, out.getFloat(0));
            assertEquals(0.6f, out.getFloat(12));
            assertEquals(0.2f, queued[4]);
            fog.enabled(false);
            assertEquals(0, call("glIsEnabled", 0x0B60));
            call("glGetIntegerv", 0x0B65, MemoryUtil.memAddress(out));
            assertEquals(9729, out.getInt(0));
        } finally {
            fog.enabled(false); fog.parameter(0x0B65, 2048); fog.parameter(0x0B64, 1);
            fog.color(0, 0, 0, 0); MemoryUtil.memFree(out);
        }
    }

}
