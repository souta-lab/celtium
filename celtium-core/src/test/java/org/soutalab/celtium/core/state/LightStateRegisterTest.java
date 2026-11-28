package org.soutalab.celtium.core.state;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The shared state register of interface 3's contract: key packing, the off-heap matrix stack,
 * and the lane-3 dirty discipline.
 */
class LightStateRegisterTest {

    @Test
    void defaultKeyMatchesTheDocumentedShippingState() {
        RenderStateKey key = RenderStateKey.DEFAULT;
        assertFalse(key.blendEnabled());
        assertTrue(key.depthTestEnabled());
        assertTrue(key.depthWriteEnabled());
        assertEquals(RenderStateKey.CULL_BACK, key.cullMode());
        assertEquals(RenderStateKey.POLYGON_FILL, key.polygonMode());
        assertEquals(0xF, key.colorWriteMask());
        assertEquals(0, key.activeTextureUnit());
    }

    @Test
    void keyBuilderRoundTripsEveryField() {
        RenderStateKey key = RenderStateKey.builder()
                .blendEnabled(true)
                .depthWriteEnabled(false)
                .stencilTestEnabled(true)
                .cullMode(RenderStateKey.CULL_FRONT)
                .polygonMode(RenderStateKey.POLYGON_LINE)
                .colorWriteMask(0x5)
                .activeTextureUnit(7)
                .build();
        assertTrue(key.blendEnabled());
        assertFalse(key.depthWriteEnabled());
        assertTrue(key.stencilTestEnabled());
        assertEquals(RenderStateKey.CULL_FRONT, key.cullMode());
        assertEquals(RenderStateKey.POLYGON_LINE, key.polygonMode());
        assertEquals(0x5, key.colorWriteMask());
        assertEquals(7, key.activeTextureUnit());
        assertThrows(IllegalArgumentException.class, () -> new RenderStateKey(1 << 20));
        assertThrows(IllegalArgumentException.class,
                () -> RenderStateKey.builder().activeTextureUnit(16));
    }

    @Test
    void matrixStackStartsAtIdentityAndPushPopRestores() {
        try (LightStateRegister register = new LightStateRegister()) {
            float[] out = new float[16];
            register.currentMatrix(out);
            assertEquals(1.0f, out[0], 0.0f);
            assertEquals(1.0f, out[5], 0.0f);
            assertEquals(1.0f, out[10], 0.0f);
            assertEquals(1.0f, out[15], 0.0f);
            assertEquals(0.0f, out[1], 0.0f);

            float[] custom = new float[16];
            custom[0] = 2.0f;
            custom[12] = 30.0f;
            register.setCurrentMatrix(custom);
            register.pushMatrix();
            register.loadIdentity();
            register.currentMatrix(out);
            assertEquals(1.0f, out[0], 0.0f);
            register.popMatrix();
            register.currentMatrix(out);
            assertEquals(2.0f, out[0], 0.0f);
            assertEquals(30.0f, out[12], 0.0f);
            assertEquals(1, register.matrixDepth());
        }
    }

    @Test
    void matrixStackBoundsAreEnforced() {
        try (LightStateRegister register = new LightStateRegister()) {
            assertThrows(IllegalStateException.class, register::popMatrix);
            for (int i = 1; i < LightStateRegister.MAX_MATRIX_DEPTH; i++) {
                register.pushMatrix();
            }
            assertEquals(LightStateRegister.MAX_MATRIX_DEPTH, register.matrixDepth());
            assertThrows(IllegalStateException.class, register::pushMatrix);
        }
    }

    @Test
    void compilingListIdDefaultsToZeroRoundTripsAndRejectsNegatives() {
        try (LightStateRegister register = new LightStateRegister()) {
            assertEquals(0, register.compilingListId());
            int keyBefore = register.key().value();
            register.setCompilingListId(42);
            assertEquals(42, register.compilingListId());
            register.setCompilingListId(0);
            assertEquals(0, register.compilingListId());
            assertThrows(IllegalArgumentException.class, () -> register.setCompilingListId(-1));
            // The compiling id is not pipeline state: the key - and with it the fixed
            // pipeline census (K5) - must not move when the id changes (G2-07 ruling).
            assertEquals(keyBefore, register.key().value());
        }
    }

    @Test
    void lane3DirtyDisciplineFollowsTheContract() {
        try (LightStateRegister register = new LightStateRegister()) {
            assertFalse(register.isLane3Dirty());
            register.markLane3Returned();
            assertTrue(register.isLane3Dirty());
            register.markLane3Entered();
            assertFalse(register.isLane3Dirty());
            register.setBoundTextureId(12);
            assertEquals(12, register.boundTextureId());
            register.setKey(RenderStateKey.builder().blendEnabled(true).build());
            assertTrue(register.key().blendEnabled());
        }
    }
}
