package org.soutalab.celtium.vulkan.lane2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.soutalab.celtium.core.state.RenderStateKey;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Lane2StateMirrorTest {

    private Lane2StateMirror mirror;

    @BeforeEach
    void resetMirror() {
        this.mirror = Lane2StateMirror.current();
        this.mirror.reset();
    }

    @Test
    void defaultStatePublishesTheDefaultKey() {
        assertEquals(RenderStateKey.DEFAULT, this.mirror.register().key());
        assertEquals(0, this.mirror.register().boundTextureId());
        assertEquals(1, this.mirror.register().matrixDepth());
    }

    @Test
    void blendAndDepthWritesFoldIntoTheKey() {
        this.mirror.setBlendEnabled(true);
        assertTrue(this.mirror.register().key().blendEnabled());
        assertEquals(Lane2FixedPipelines.VARIANT_BLEND_DEPTH_WRITE,
                Lane2FixedPipelines.variantFor(this.mirror.register().key()));

        this.mirror.setDepthWriteEnabled(false);
        assertFalse(this.mirror.register().key().depthWriteEnabled());
        assertEquals(Lane2FixedPipelines.VARIANT_BLEND_NO_DEPTH_WRITE,
                Lane2FixedPipelines.variantFor(this.mirror.register().key()));

        this.mirror.setDepthTestEnabled(false);
        assertFalse(this.mirror.register().key().depthTestEnabled());
    }

    @Test
    void blendFactorsAndFunctionsAreKeptBesideTheKey() {
        this.mirror.setBlendFunc(770, 771);
        assertEquals(770, this.mirror.blendSrcRgb());
        assertEquals(771, this.mirror.blendDstRgb());
        assertEquals(770, this.mirror.blendSrcAlpha());
        this.mirror.setBlendFuncSeparate(1, 0, 770, 771);
        assertEquals(1, this.mirror.blendSrcRgb());
        assertEquals(770, this.mirror.blendSrcAlpha());

        this.mirror.setDepthFunc(516);
        assertEquals(516, this.mirror.depthFunc());
        this.mirror.setAlphaFunc(516, 0.5f);
        assertEquals(516, this.mirror.alphaFunc());
        assertEquals(0.5f, this.mirror.alphaRef());
    }

    @Test
    void colorMaskCullAndUnitFoldIntoTheKey() {
        this.mirror.setColorMask(true, false, true, false);
        assertEquals(0b0101, this.mirror.register().key().colorWriteMask());

        this.mirror.setCullFace(Lane2StateMirror.GL_FRONT);
        assertEquals(RenderStateKey.CULL_FRONT, this.mirror.register().key().cullMode());
        this.mirror.setCullEnabled(false);
        assertEquals(RenderStateKey.CULL_OFF, this.mirror.register().key().cullMode());
        this.mirror.setCullEnabled(true);
        assertEquals(RenderStateKey.CULL_BACK, this.mirror.register().key().cullMode());

        this.mirror.setActiveTextureUnit(3);
        assertEquals(3, this.mirror.register().key().activeTextureUnit());
    }

    @Test
    void boundTextureLandsInTheRegister() {
        this.mirror.bindTexture(107);
        assertEquals(107, this.mirror.register().boundTextureId());
    }

    @Test
    void activeUnitComposesTheGlEnumFromTheRegisterKey() {
        this.mirror.setActiveTextureUnit(5);
        // The uploader's census read (G2-07, NEON ruling 2026-11-28): the GL enum is
        // composed from the register key - the same representation the replay half of
        // the display-list census speaks, with no second source of truth.
        assertEquals(org.lwjgl.opengl.GL13.GL_TEXTURE0 + 5,
                org.lwjgl.opengl.GL13.GL_TEXTURE0 + this.mirror.register().key().activeTextureUnit());
    }

    @Test
    void resetReturnsTheCompilingListToZero() {
        this.mirror.register().setCompilingListId(77);
        assertEquals(77, this.mirror.register().compilingListId());
        this.mirror.reset();
        assertEquals(0, this.mirror.register().compilingListId());
    }

    @Test
    void resetReturnsTheActiveProgramToZero() {
        this.mirror.register().setActiveProgramId(31);
        assertEquals(31, this.mirror.register().activeProgramId());
        this.mirror.reset();
        assertEquals(0, this.mirror.register().activeProgramId());
    }

    @Test
    void modelViewTransformsComposeOnTheRegisterStack() {
        float[] out = new float[16];
        this.mirror.translate(2.0f, 3.0f, 4.0f);
        this.mirror.modelViewMatrix(out);
        assertEquals(2.0f, out[12]);
        assertEquals(3.0f, out[13]);
        assertEquals(4.0f, out[14]);

        this.mirror.pushMatrix();
        this.mirror.scale(2.0f, 2.0f, 2.0f);
        this.mirror.modelViewMatrix(out);
        // translate * scale: the translation column is untouched, the basis is doubled.
        assertEquals(2.0f, out[0]);
        assertEquals(2.0f, out[12]);

        this.mirror.popMatrix();
        this.mirror.modelViewMatrix(out);
        assertEquals(1.0f, out[0]);
        assertEquals(2.0f, out[12]);
        assertEquals(1, this.mirror.register().matrixDepth());
    }

    @Test
    void rotateNinetyDegreesAboutZMapsXToY() {
        float[] out = new float[16];
        this.mirror.rotate(90.0f, 0.0f, 0.0f, 1.0f);
        this.mirror.modelViewMatrix(out);
        // Column 0 is the image of the x axis: (cos90, sin90, 0) = (0, 1, 0).
        assertEquals(0.0f, out[0], 1.0e-6f);
        assertEquals(1.0f, out[1], 1.0e-6f);
        assertEquals(0.0f, out[2], 1.0e-6f);
    }

    @Test
    void projectionModeHasItsOwnStack() {
        float[] out = new float[16];
        this.mirror.setMatrixMode(Lane2StateMirror.GL_PROJECTION);
        this.mirror.ortho(-1, 1, -1, 1, 0.1, 100);
        this.mirror.projectionMatrix(out);
        assertEquals(1.0f, out[0]);
        assertEquals(1.0f, out[5]);
        // The modelview top is untouched by projection work.
        this.mirror.modelViewMatrix(out);
        assertEquals(1.0f, out[0]);
        assertEquals(0.0f, out[12]);

        this.mirror.setMatrixMode(Lane2StateMirror.GL_MODELVIEW);
        this.mirror.translate(5.0f, 0.0f, 0.0f);
        this.mirror.modelViewMatrix(out);
        assertEquals(5.0f, out[12]);
    }

    @Test
    void textureModeMatrixOpsAreCountedNotApplied() {
        long before = this.mirror.textureModeMatrixOps();
        this.mirror.setMatrixMode(Lane2StateMirror.GL_TEXTURE);
        this.mirror.pushMatrix();
        this.mirror.loadIdentity();
        assertEquals(before + 2, this.mirror.textureModeMatrixOps());
        assertEquals(1, this.mirror.register().matrixDepth());
    }

    @Test
    void multMatrixPostMultiplies() {
        float[] scale = new float[16];
        scale[0] = 3.0f;
        scale[5] = 3.0f;
        scale[10] = 3.0f;
        scale[15] = 1.0f;
        this.mirror.translate(1.0f, 0.0f, 0.0f);
        this.mirror.multMatrix(scale);
        float[] out = new float[16];
        this.mirror.modelViewMatrix(out);
        assertEquals(3.0f, out[0]);
        assertEquals(1.0f, out[12]);
    }
}
