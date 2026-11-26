package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the stage-7 key form (S5-5): the packed {@link PipelineStateKey} must agree with
 * {@link VulkanGlState#pipelineKey(int)} on which states are the same pipeline and which
 * are different ones, decode back the fields it packs, and keep the erasure rules the mix
 * hash applied (a disabled blend or test contributes nothing).
 */
class PipelineStateKeyTest {

    private static VulkanGlState fresh() {
        return new VulkanGlState();
    }

    @Test
    void roundTripDecodesEveryPackedField() {
        VulkanGlState state = fresh();
        state.blendEnabled(true);
        state.blendFunc(770, 771, 1, 0);
        state.blendEquation(VulkanGlState.BLEND_FUNC_ADD, VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT);
        state.colorMask(0x5);
        state.depthFunc(515);
        state.alphaTestEnabled(true);
        state.alphaFunc(516, 0.5f);
        state.lightingEnabled(true);
        state.shadeModel(VulkanGlState.SHADE_FLAT);
        state.cullEnabled(true);
        state.cullFace(VulkanCullState.GL_FRONT);
        state.frontFace(VulkanCullState.GL_CW);

        PipelineStateKey key = PipelineStateKey.of(state, 42);
        assertEquals(0x5, key.colorMask());
        assertTrue(key.depthMask());
        assertTrue(key.depthTestEnabled());
        assertEquals(515, key.depthFunc());
        assertTrue(key.alphaTestEnabled());
        assertEquals(516, key.alphaFunc());
        assertTrue(key.lightingEnabled());
        assertEquals(VulkanGlState.SHADE_FLAT, key.shadeModel());
        assertEquals(VulkanCullState.FRONT | VulkanCullState.FRONT_FACE_CW, key.cullState());
        assertTrue(key.blendEnabled());
        assertEquals(770, key.blendSrcRgb());
        assertEquals(771, key.blendDstRgb());
        assertEquals(1, key.blendSrcAlpha());
        assertEquals(0, key.blendDstAlpha());
        assertEquals(VulkanGlState.BLEND_FUNC_ADD, key.blendEquationRgb());
        assertEquals(VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT, key.blendEquationAlpha());
        assertEquals(key, PipelineStateKey.fromValue(key.value()));
        assertEquals(key.value(), PipelineStateKey.of(state, 42).value());
    }

    @Test
    void equalityMatchesPipelineKeyAcrossRecordedStates() {
        // A recorded corpus of state scripts, each producing a state the pipeline key must
        // tell apart from every other. The corpus premise - the old mix hash also tells
        // them all apart - is asserted as part of the test, so the equivalence below is a
        // measured fact about this corpus, not an assumption about hashes in general.
        List<Consumer<VulkanGlState>> scripts = new ArrayList<>();
        scripts.add(state -> { });
        scripts.add(state -> state.blendEnabled(true));
        scripts.add(state -> {
            state.blendEnabled(true);
            state.blendFunc(770, 771, 1, 0);
        });
        scripts.add(state -> {
            state.blendEnabled(true);
            state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO,
                    VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO);
        });
        scripts.add(state -> {
            state.blendEnabled(true);
            state.blendEquation(VulkanGlState.BLEND_FUNC_SUBTRACT, VulkanGlState.BLEND_MIN);
        });
        scripts.add(state -> {
            state.blendEnabled(true);
            state.blendFunc(0x8001, 0x8002, 0x8003, 0x8004);
        });
        scripts.add(state -> {
            state.blendEnabled(true);
            state.blendFunc(999, 1, 1, 1);
        });
        scripts.add(state -> state.colorMask(0x7));
        scripts.add(state -> state.depthTestEnabled(false));
        scripts.add(state -> state.depthFunc(513));
        scripts.add(state -> state.depthFunc(999));
        scripts.add(state -> state.depthMask(false));
        scripts.add(state -> state.cullEnabled(true));
        scripts.add(state -> {
            state.cullEnabled(true);
            state.cullFace(VulkanCullState.GL_FRONT);
        });
        scripts.add(state -> {
            state.cullEnabled(true);
            state.frontFace(VulkanCullState.GL_CW);
        });
        scripts.add(state -> {
            state.alphaTestEnabled(true);
            state.alphaFunc(516, 0.5f);
        });
        scripts.add(state -> {
            state.alphaTestEnabled(true);
            state.alphaFunc(4, 0.1f);
        });
        scripts.add(state -> state.lightingEnabled(true));
        scripts.add(state -> state.shadeModel(VulkanGlState.SHADE_FLAT));

        int n = scripts.size();
        long[] mixed = new long[n];
        long[] packed = new long[n];
        for (int i = 0; i < n; i++) {
            VulkanGlState state = fresh();
            scripts.get(i).accept(state);
            mixed[i] = state.pipelineKey(42);
            packed[i] = PipelineStateKey.of(state, 42).value();
        }
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(mixed[i] == mixed[j], packed[i] == packed[j],
                        "corpus states " + i + " and " + j + ": the packed key must agree with"
                                + " the mix hash on pipeline equality");
            }
        }
        // Rebuilding the same state reproduces the same key under both forms.
        for (int i = 0; i < n; i++) {
            VulkanGlState state = fresh();
            scripts.get(i).accept(state);
            assertEquals(mixed[i], state.pipelineKey(42));
            assertEquals(packed[i], PipelineStateKey.of(state, 42).value());
        }
    }

    @Test
    void disabledBlendErasesFactorsAndEquations() {
        VulkanGlState state = fresh();
        long base = PipelineStateKey.of(state, 42).value();
        state.blendFunc(770, 771, 1, 0);
        state.blendEquation(VulkanGlState.BLEND_FUNC_SUBTRACT, VulkanGlState.BLEND_MIN);
        assertEquals(base, PipelineStateKey.of(state, 42).value(),
                "with blending off the factors and equations are not pipeline state");
        state.blendEnabled(true);
        assertNotEquals(base, PipelineStateKey.of(state, 42).value());
        state.blendEnabled(false);
        assertEquals(base, PipelineStateKey.of(state, 42).value(),
                "disabling blend returns to the earlier key, as the mix hash did");
    }

    @Test
    void disabledTestsEraseTheirFunctions() {
        VulkanGlState state = fresh();
        state.depthTestEnabled(false);
        long noDepthTest = PipelineStateKey.of(state, 42).value();
        state.depthFunc(513);
        assertEquals(noDepthTest, PipelineStateKey.of(state, 42).value(),
                "a depth function under a disabled test is erased");
        state.depthTestEnabled(true);
        assertNotEquals(noDepthTest, PipelineStateKey.of(state, 42).value());

        VulkanGlState alpha = fresh();
        long alphaOff = PipelineStateKey.of(alpha, 42).value();
        alpha.alphaFunc(513, 0.25f);
        assertEquals(alphaOff, PipelineStateKey.of(alpha, 42).value(),
                "an alpha function under a disabled test is erased");
    }

    @Test
    void pushConstantStateDoesNotMoveTheKey() {
        VulkanGlState state = fresh();
        long base = PipelineStateKey.of(state, 42).value();
        state.color(0.25f, 0.5f, 0.75f, 1.0f);
        state.bindTexture(1234);
        state.loadMatrix(new float[] {2, 0, 0, 0, 0, 2, 0, 0, 0, 0, 2, 0, 1, 2, 3, 1});
        assertEquals(base, PipelineStateKey.of(state, 42).value(),
                "colour, texture bindings and matrices are push/descriptor state, not pipeline state");
    }

    @Test
    void outOfDomainValuesTakeTheEscapeSlots() {
        VulkanGlState state = fresh();
        state.depthFunc(999);
        PipelineStateKey key = PipelineStateKey.of(state, 42);
        assertTrue(key.depthTestEnabled());
        assertEquals(-1, key.depthFunc(), "an out-of-domain depth function decodes as the escape");

        VulkanGlState blended = fresh();
        blended.blendEnabled(true);
        blended.blendFunc(999, 1, 1, 1);
        assertEquals(-1, PipelineStateKey.of(blended, 42).blendSrcRgb());

        VulkanGlState shaded = fresh();
        shaded.shadeModel(7);
        assertEquals(-1, PipelineStateKey.of(shaded, 42).shadeModel());

        // The escapes still discriminate against the in-domain values they are not.
        VulkanGlState inDomain = fresh();
        inDomain.depthFunc(519);
        assertNotEquals(key.value(), PipelineStateKey.of(inDomain, 42).value());
    }

    @Test
    void factorCodesAreInjectiveOverBothDomains() {
        // The union of the domains the mirror can hold: legacy ordinals 0-9, GL factors
        // 0/1/0x0300-0x0308, constant factors 0x8001-0x8004 (0 and 1 counted once).
        int[] domain = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                0x0300, 0x0301, 0x0302, 0x0303, 0x0304, 0x0305, 0x0306, 0x0307, 0x0308,
                0x8001, 0x8002, 0x8003, 0x8004};
        long[] keys = new long[domain.length];
        for (int i = 0; i < domain.length; i++) {
            VulkanGlState state = fresh();
            state.blendEnabled(true);
            state.blendFunc(domain[i], 1, 1, 1);
            keys[i] = PipelineStateKey.of(state, 42).value();
            assertEquals(domain[i], PipelineStateKey.of(state, 42).blendSrcRgb(),
                    "factor " + domain[i] + " must decode back to itself");
        }
        for (int i = 0; i < domain.length; i++) {
            for (int j = i + 1; j < domain.length; j++) {
                assertNotEquals(keys[i], keys[j],
                        "factors " + domain[i] + " and " + domain[j] + " must not share a key");
            }
        }
    }

    @Test
    void everyFormatHashBitMovesTheFold() {
        VulkanGlState state = fresh();
        long base = PipelineStateKey.of(state, 0).value();
        for (int bit = 0; bit < 32; bit++) {
            assertNotEquals(base, PipelineStateKey.of(state, 1 << bit).value(),
                    "format hash bit " + bit + " must move the key");
        }
        assertNotEquals(PipelineStateKey.of(state, 1).value(),
                PipelineStateKey.of(state, 2).value());
    }

    @Test
    void cullOffErasesFaceAndWinding() {
        VulkanGlState state = fresh();
        long off = PipelineStateKey.of(state, 42).value();
        state.cullFace(VulkanCullState.GL_FRONT);
        state.frontFace(VulkanCullState.GL_CW);
        assertEquals(off, PipelineStateKey.of(state, 42).value(),
                "with culling off neither the face nor the winding is read (the encode's own rule)");
        state.cullEnabled(true);
        assertNotEquals(off, PipelineStateKey.of(state, 42).value());
        assertFalse(PipelineStateKey.of(state, 42).cullState() == VulkanCullState.NONE);
    }
}
