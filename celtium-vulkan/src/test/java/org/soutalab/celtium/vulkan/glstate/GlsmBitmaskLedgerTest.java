package org.soutalab.celtium.vulkan.glstate;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GlsmBitmaskLedgerTest {

    @Test
    void pipelineStateKeyAllFieldsRoundTripWithoutOverlap() {
        long k = 0L;
        k = PipelineStateKey.withBlendEnable(k, true);
        k = PipelineStateKey.withBlendFuncSeparate(
                k,
                PipelineStateKey.BLEND_SRC_ALPHA,
                PipelineStateKey.BLEND_ONE_MINUS_SRC_ALPHA,
                PipelineStateKey.BLEND_ONE,
                PipelineStateKey.BLEND_DST_ALPHA);
        k = PipelineStateKey.withDepthTest(k, true);
        k = PipelineStateKey.withDepthWrite(k, false);
        k = PipelineStateKey.withDepthFunc(k, PipelineStateKey.CMP_LEQUAL);
        k = PipelineStateKey.withCullEnable(k, true);
        k = PipelineStateKey.withCullFace(k, PipelineStateKey.CULL_FRONT);
        k = PipelineStateKey.withAlphaTest(k, true);
        k = PipelineStateKey.withAlphaFunc(k, PipelineStateKey.CMP_GREATER);
        k = PipelineStateKey.withPolygonOffsetFill(k, true);
        k = PipelineStateKey.withColorMask(k, 0b1010);
        k = PipelineStateKey.withTopology(k, PipelineStateKey.TOPO_TRIANGLE_STRIP);
        k = PipelineStateKey.withVertexFormat(k, 11);
        k = PipelineStateKey.withTexture2d(k, true);
        k = PipelineStateKey.withLightmap(k, true);
        k = PipelineStateKey.withFog(k, true);
        k = PipelineStateKey.withLighting(k, true);
        k = PipelineStateKey.withScissor(k, true);
        k = PipelineStateKey.withBlendEquation(k, 2);
        k = PipelineStateKey.withProgramId(k, 0xBEEF);

        assertTrue(PipelineStateKey.blendEnable(k));
        assertEquals(PipelineStateKey.BLEND_SRC_ALPHA, PipelineStateKey.blendSrcRgb(k));
        assertEquals(PipelineStateKey.BLEND_ONE_MINUS_SRC_ALPHA, PipelineStateKey.blendDstRgb(k));
        assertEquals(PipelineStateKey.BLEND_ONE, PipelineStateKey.blendSrcAlpha(k));
        assertEquals(PipelineStateKey.BLEND_DST_ALPHA, PipelineStateKey.blendDstAlpha(k));
        assertTrue(PipelineStateKey.depthTest(k));
        assertFalse(PipelineStateKey.depthWrite(k));
        assertEquals(PipelineStateKey.CMP_LEQUAL, PipelineStateKey.depthFunc(k));
        assertTrue(PipelineStateKey.cullEnable(k));
        assertEquals(PipelineStateKey.CULL_FRONT, PipelineStateKey.cullFace(k));
        assertTrue(PipelineStateKey.alphaTest(k));
        assertEquals(PipelineStateKey.CMP_GREATER, PipelineStateKey.alphaFunc(k));
        assertTrue(PipelineStateKey.polygonOffsetFill(k));
        assertEquals(0b1010, PipelineStateKey.colorMask(k));
        assertEquals(PipelineStateKey.TOPO_TRIANGLE_STRIP, PipelineStateKey.topology(k));
        assertEquals(11, PipelineStateKey.vertexFormat(k));
        assertTrue(PipelineStateKey.texture2d(k));
        assertTrue(PipelineStateKey.lightmap(k));
        assertTrue(PipelineStateKey.fog(k));
        assertTrue(PipelineStateKey.lighting(k));
        assertTrue(PipelineStateKey.scissor(k));
        assertEquals(2, PipelineStateKey.blendEquation(k));
        assertEquals(0xBEEF, PipelineStateKey.programId(k));
        assertTrue(PipelineStateKey.isTranslucent(k));
        assertEquals(PipelineStateKey.toCompact16BitKey(k), PipelineStateKey.toCompact16BitKey(k) & 0xFFFF);
    }

    @Test
    void ledgerElidesRedundantStateChangesAndRestoresPartialPushPop() {
        GlsmBitmaskLedger ledger = new GlsmBitmaskLedger();
        ledger.setBlendEnable(true);
        ledger.setBlendFuncSeparate(
                PipelineStateKey.BLEND_SRC_ALPHA,
                PipelineStateKey.BLEND_ONE_MINUS_SRC_ALPHA,
                PipelineStateKey.BLEND_ONE,
                PipelineStateKey.BLEND_ZERO);
        ledger.setDepthTest(true);
        ledger.bindTexture(0, 42);

        long changesBefore = ledger.stateChanges();
        ledger.setBlendEnable(true); // redundant
        ledger.bindTexture(0, 42);   // redundant
        assertEquals(changesBefore, ledger.stateChanges());
        assertEquals(2L, ledger.redundantElisions());

        // Push only COLOR_BUFFER_BIT: blend & alpha should restore, depth & texture should NOT
        ledger.pushAttrib(GlsmBitmaskLedger.GL_COLOR_BUFFER_BIT);
        ledger.setBlendEnable(false);
        ledger.setDepthTest(false);
        ledger.bindTexture(0, 99);
        ledger.popAttrib();

        assertTrue(PipelineStateKey.blendEnable(ledger.stateKey()));
        assertFalse(PipelineStateKey.depthTest(ledger.stateKey()));
        assertEquals(99, ledger.boundTexture0());
        assertEquals(0, ledger.stackDepth());
    }

    @Test
    void dynamicPassTriggersStayClearOnVanillaSwapchainAndFireOnCustomFboOrCopy() {
        GlsmBitmaskLedger ledger = new GlsmBitmaskLedger();
        ledger.noteDrawEmitted();
        assertFalse(ledger.requiresDynamicGraphPass());

        ledger.bindDrawFramebuffer(7);
        ledger.noteDrawEmitted();
        assertTrue(ledger.requiresDynamicGraphPass());
        assertEquals(GlsmBitmaskLedger.TRIGGER_CUSTOM_FBO_DRAW, ledger.frameTriggers());

        ledger.noteCopyTexSubImage();
        assertEquals(
                GlsmBitmaskLedger.TRIGGER_CUSTOM_FBO_DRAW | GlsmBitmaskLedger.TRIGGER_COPY_TEX_SUB_IMAGE,
                ledger.frameTriggers());

        ledger.resetFrameTriggers();
        assertFalse(ledger.requiresDynamicGraphPass());
    }
}
