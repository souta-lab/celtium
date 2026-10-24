package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises real raw-GL dispatch against the same owner the native frontend reads, without any graphics context. */
class NativeRasterStateOwnershipTest {
    private final NativeRasterState raster = new NativeRasterState();
    private final NativeTextureBindings textures = new NativeTextureBindings(this.raster);
    private final VulkanGlState nativeState = new VulkanGlState(this.raster, this.textures);
    private final CeltiumGlStub adapter;
    private final Method dispatch;
    private final Map<?, ?> ids;

    NativeRasterStateOwnershipTest() throws Exception {
        var constructor = CeltiumGlStub.class.getDeclaredConstructor(NativeRasterState.class, NativeTextureBindings.class);
        constructor.setAccessible(true);
        this.adapter = constructor.newInstance(this.raster, this.textures);
        this.dispatch = CeltiumGlStub.class.getDeclaredMethod("dispatchArgs", int.class, long[].class);
        this.dispatch.setAccessible(true);
        var table = CeltiumGlStub.class.getDeclaredField("IDS");
        table.setAccessible(true);
        this.ids = (Map<?, ?>) table.get(null);
    }

    private long call(String name, long... args) throws Exception {
        assertTrue(this.ids.containsKey(name), "must use the real adapter entry: " + name);
        long[] slots = new long[16];
        System.arraycopy(args, 0, slots, 0, args.length);
        return (Long) this.dispatch.invoke(this.adapter, this.ids.get(name), slots);
    }

    @Test
    void nativeStateCannotBeOverwrittenByAnOlderRawGlRecord() throws Exception {
        call("glBlendFuncSeparate", 0x0302, 0x0303, 1, 0);
        call("glEnable", 0x0BE2);
        assertTrue(this.nativeState.blendEnabled());
        assertEquals(0x0302, this.nativeState.blendSrcRgb());
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
            call("glColorMask", 0, 1, 0, 1);
            call("glDisable", 0x0B44);
            assertEquals(0xA, this.nativeState.colorMask());
            assertFalse(this.nativeState.cullEnabled());
        } finally { MemoryUtil.memFree(out); }
    }

    @Test
    void deferredGeometryObservesOldStateAndSnapshotsOutliveSubsequentChanges() throws Exception {
        var observed = new ArrayList<Integer>();
        this.raster.beforeChange(() -> observed.add(this.nativeState.colorMask()));
        call("glColorMask", 1, 0, 0, 0);
        this.nativeState.colorMask(2);
        this.nativeState.colorMask(2);
        assertEquals(java.util.List.of(15, 1), observed, "one boundary before each actual change");
        call("glScissor", 3, 4, 50, 60);
        int[] snapshot = this.raster.scissorBox();
        call("glScissor", 7, 8, 90, 100);
        assertArrayEquals(new int[] {3, 4, 50, 60}, snapshot);
        call("glEnable", 0x0C11);
        assertTrue(this.raster.scissorTest());
        call("glPolygonOffset", Float.floatToRawIntBits(-1), Float.floatToRawIntBits(-3));
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
        var installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        var out = MemoryUtil.memAlloc(16);
        try {
            installed.set(null, this.adapter);
            this.nativeState.bindTexture(73);
            CeltiumGlStub.recordTextureAllocation(73, 2, 8, 4);
            assertEquals(73, this.nativeState.boundTextureName());
            call("glGetIntegerv", 0x8069, MemoryUtil.memAddress(out));
            assertEquals(73, out.getInt(0));
            assertEquals(8, CeltiumGlStub.textureLevelParameter(73, 0, 0x1000));
            CeltiumGlStub.forgetTextureAllocation(73);
            assertEquals(0, this.nativeState.boundTextureName());
            assertEquals(0, CeltiumGlStub.textureLevelParameter(73, 0, 0x1000));
        } finally { installed.set(null, previous); MemoryUtil.memFree(out); }
    }

    @Test
    void boundaryFailureDoesNotPartiallyApplyTheNewState() {
        this.raster.beforeChange(() -> { throw new IllegalStateException("cannot submit old glyphs"); });
        assertThrows(IllegalStateException.class, () -> this.nativeState.blendFunc(1, 0, 1, 0));
        assertEquals(VulkanGlState.BLEND_SRC_ALPHA, this.nativeState.blendSrcRgb());
        assertEquals(VulkanGlState.BLEND_ONE_MINUS_SRC_ALPHA, this.nativeState.blendDstRgb());
    }
}
