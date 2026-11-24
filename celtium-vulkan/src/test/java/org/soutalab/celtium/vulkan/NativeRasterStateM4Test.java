package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.NativeRasterAdapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM M-4 (INT-2 remainder): the stencil family, polygon mode, sample coverage and the constant
 * blend colour recorded in {@link NativeRasterState}, plus the sampler ledger in
 * {@link NativeTextureBindings}. Fresh owners per test, so the GL initial values are observable.
 * Not run by the alpha lane (alpha rules); the merge phase executes it.
 */
class NativeRasterStateM4Test {

    @Test
    void stencilDefaultsAreGlInitialValues() {
        NativeRasterState state = new NativeRasterState();
        assertEquals(0x0207, state.stencilFunc(true));
        assertEquals(0x0207, state.stencilFunc(false));
        assertEquals(0, state.stencilRef(true));
        assertEquals(~0, state.stencilValueMask(false));
        assertEquals(0x1E00, state.stencilFail(true));
        assertEquals(0x1E00, state.stencilDepthFail(false));
        assertEquals(0x1E00, state.stencilDepthPass(true));
        assertEquals(~0, state.stencilWriteMask(true));
        assertEquals(0x1B02, state.polygonMode(true));
        assertEquals(1.0f, state.sampleCoverageValue());
        assertFalse(state.sampleCoverageInvert());
        assertEquals(0.0f, state.blendColorAlpha());
    }

    @Test
    void nonSeparateStencilFuncWritesBothFaces() {
        NativeRasterState state = new NativeRasterState();
        state.stencilFunc(0x0206, 7, 0x0F);
        assertEquals(0x0206, state.stencilFunc(true));
        assertEquals(0x0206, state.stencilFunc(false));
        assertEquals(7, state.stencilRef(false));
        assertEquals(0x0F, state.stencilValueMask(true));
    }

    @Test
    void separateStencilFuncChangesOnlyTheNamedFace() {
        NativeRasterState state = new NativeRasterState();
        state.stencilFuncSeparate(0x0405, 0x0203, 3, 0x03); // GL_BACK
        assertEquals(0x0207, state.stencilFunc(true));
        assertEquals(0x0203, state.stencilFunc(false));
        assertEquals(3, state.stencilRef(false));
        assertEquals(0, state.stencilRef(true));
    }

    @Test
    void stencilOpAndMaskFollowTheSameFaceRule() {
        NativeRasterState state = new NativeRasterState();
        state.stencilOp(0x1E01, 0x1C00, 0x1D00);
        assertEquals(0x1E01, state.stencilFail(false));
        assertEquals(0x1D00, state.stencilDepthPass(true));
        state.stencilOpSeparate(0x0404, 0x150A, 0x150A, 0x150A); // GL_FRONT
        assertEquals(0x150A, state.stencilFail(true));
        assertEquals(0x1E01, state.stencilFail(false));
        state.stencilMask(0x55);
        assertEquals(0x55, state.stencilWriteMask(false));
        state.stencilMaskSeparate(0x0405, 0x2A);
        assertEquals(0x55, state.stencilWriteMask(true));
        assertEquals(0x2A, state.stencilWriteMask(false));
    }

    @Test
    void polygonModeSampleCoverageAndBlendColorAreKept() {
        NativeRasterState state = new NativeRasterState();
        state.polygonMode(0x0408, 0x1B01); // GL_FRONT_AND_BACK, GL_LINE
        assertEquals(0x1B01, state.polygonMode(true));
        assertEquals(0x1B01, state.polygonMode(false));
        state.polygonMode(0x0405, 0x1B00); // GL_BACK, GL_POINT
        assertEquals(0x1B01, state.polygonMode(true));
        assertEquals(0x1B00, state.polygonMode(false));
        state.sampleCoverage(0.5f, true);
        assertEquals(0.5f, state.sampleCoverageValue());
        assertTrue(state.sampleCoverageInvert());
        state.blendColor(0.25f, 0.5f, 0.75f, 1.0f);
        assertEquals(0.25f, state.blendColorRed());
        assertEquals(0.75f, state.blendColorBlue());
        assertEquals(1.0f, state.blendColorAlpha());
    }

    @Test
    void adapterDeliversToTheSharedOwnerAndVersionMovesOnlyOnChange() {
        NativeRasterState state = NativeRasterState.current();
        long before = state.version();
        NativeRasterAdapter.glStencilFunc(0x0204, 11, 0x1F);
        assertEquals(0x0204, state.stencilFunc(true));
        assertEquals(11, state.stencilRef(false));
        assertTrue(state.version() > before);
        long settled = state.version();
        NativeRasterAdapter.glStencilFunc(0x0204, 11, 0x1F);
        assertEquals(settled, state.version());
        NativeRasterAdapter.glBlendColor(0.1f, 0.2f, 0.3f, 0.4f);
        assertEquals(0.3f, state.blendColorBlue());
    }

    @Test
    void samplerLedgerKeepsUnitsApartAndIgnoresOutOfRangeWrites() {
        NativeTextureBindings bindings = new NativeTextureBindings(new NativeRasterState());
        bindings.bindSampler(3, 777);
        bindings.bindSampler(4, 888);
        assertEquals(777, bindings.sampler(3));
        assertEquals(888, bindings.sampler(4));
        assertEquals(0, bindings.sampler(5));
        bindings.bindSampler(NativeTextureBindings.UNIT_COUNT, 999);
        bindings.bindSampler(-1, 999);
        assertEquals(0, bindings.sampler(0));
        bindings.bindSampler(3, 0);
        assertEquals(0, bindings.sampler(3));
    }
}
