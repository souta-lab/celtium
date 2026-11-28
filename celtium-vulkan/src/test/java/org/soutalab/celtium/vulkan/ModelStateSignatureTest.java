package org.soutalab.celtium.vulkan;

import org.soutalab.celtium.vulkan.lane2.Lane2ImmediateState;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The state a batch is keyed by must be exactly the state its signature maps to, because the model replay path keeps
 * the array instead of capturing it again. These cases pin the mapping's inputs to their slots and the two rules a
 * signature cannot see on its own: the window target masks the alpha write, and the alpha test's three parts move
 * together.
 * <p>
 * The class under test is the backend's, so the mapping is called directly: it is pure and static, and nothing here
 * needs a device.
 */
class ModelStateSignatureTest {

    private static int[] signature() {
        int[] signature = new int[ModelReplayFastPath.SIGNATURE];
        // GL's initial state as the mirror reports it: no blending, no alpha test, cull and depth on, depth write on,
        // GL_LEQUAL, no lighting, unit 0 MODULATE with a TEXTURE source, and the window (identity 0) bound.
        signature[Lane2ImmediateRecorder.SIGNATURE_BLEND] = 0;
        signature[Lane2ImmediateRecorder.SIGNATURE_SRC_RGB] = Lane2ImmediateState.BLEND_SRC_ALPHA;
        signature[Lane2ImmediateRecorder.SIGNATURE_DST_RGB] = Lane2ImmediateState.BLEND_ONE_MINUS_SRC_ALPHA;
        signature[Lane2ImmediateRecorder.SIGNATURE_SRC_ALPHA] = Lane2ImmediateState.BLEND_ONE;
        signature[Lane2ImmediateRecorder.SIGNATURE_DST_ALPHA] = Lane2ImmediateState.BLEND_ONE_MINUS_SRC_ALPHA;
        signature[Lane2ImmediateRecorder.SIGNATURE_EQ_RGB] = Lane2ImmediateState.BLEND_FUNC_ADD;
        signature[Lane2ImmediateRecorder.SIGNATURE_EQ_ALPHA] = Lane2ImmediateState.BLEND_FUNC_ADD;
        signature[Lane2ImmediateRecorder.SIGNATURE_COLOR_MASK] = Lane2ImmediateState.COLOR_CHANNEL_ALL;
        signature[Lane2ImmediateRecorder.SIGNATURE_ALPHA_TEST] = 0;
        signature[Lane2ImmediateRecorder.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(0.1f);
        signature[Lane2ImmediateRecorder.SIGNATURE_ALPHA_FUNC] = Lane2ImmediateState.ALPHA_GREATER;
        signature[Lane2ImmediateRecorder.SIGNATURE_CULL] = 1;
        signature[Lane2ImmediateRecorder.SIGNATURE_DEPTH_TEST] = 1;
        signature[Lane2ImmediateRecorder.SIGNATURE_DEPTH_WRITE] = 1;
        signature[Lane2ImmediateRecorder.SIGNATURE_DEPTH_FUNC] = 515;
        signature[Lane2ImmediateRecorder.SIGNATURE_LIGHTING] = 0;
        signature[Lane2ImmediateRecorder.SIGNATURE_ENV_MODE] = NativeTextureEnvironment.MODULATE;
        signature[Lane2ImmediateRecorder.SIGNATURE_ENV_RGB] = 0x1702;
        signature[Lane2ImmediateRecorder.SIGNATURE_WINDOW_TARGET] = 0;
        // The cull face and the winding GL starts with; the enable is above.
        signature[Lane2ImmediateRecorder.SIGNATURE_CULL_FACE] = VulkanCullState.GL_BACK;
        signature[Lane2ImmediateRecorder.SIGNATURE_FRONT_FACE] = VulkanCullState.GL_CCW;
        return signature;
    }

    private static int[] state(int[] signature) {
        int[] state = new int[16];
        Lane2ImmediateRecorder.celtium$stateFromSignature(signature, state);
        return state;
    }

    @Test void theDefaultStateIsWhatTheSlotsSay() {
        int[] state = state(signature());
        assertEquals(0, state[0], "no blending");
        assertEquals(0, state[1], "a disabled blend zeros its factors, like the capture always did");
        assertEquals(0, state[2]);
        assertEquals(1, state[3], "depth test on");
        assertEquals(1, state[4], "depth write on");
        assertEquals(VulkanCullState.BACK, state[5],
                "cull on with GL_BACK and GL_CCW is the encoding the pipelines always baked");
        assertEquals(0, state[6], "an alpha test that is off captures a reference of zero");
        assertEquals(515, state[7], "the GL depth function travels unchanged");
        assertEquals(0, state[8], "lighting off");
        assertEquals(Lane2ImmediateState.COLOR_CHANNEL_ALL, state[11]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, state[12]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, state[13]);
        assertEquals(0, state[14], "the function is zero while the test is off");
        assertEquals(0, state[15], "unit 0 MODULATE is not the combine the shader evaluates");
    }

    @Test void theCullSlotCarriesTheFaceAndTheWinding() {
        int[] front = signature();
        front[Lane2ImmediateRecorder.SIGNATURE_CULL_FACE] = VulkanCullState.GL_FRONT;
        assertEquals(VulkanCullState.FRONT, state(front)[5]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_CULL_MODE_FRONT_BIT,
                VulkanCullState.vkCullMode(state(front)[5]));

        int[] clockwise = signature();
        clockwise[Lane2ImmediateRecorder.SIGNATURE_FRONT_FACE] = VulkanCullState.GL_CW;
        assertEquals(org.lwjgl.vulkan.VK10.VK_FRONT_FACE_COUNTER_CLOCKWISE,
                VulkanCullState.vkFrontFace(state(clockwise)[5]));

        // With the enable off the face and the winding are not state: two of the three inputs may differ and the
        // captured array must not, or a disabled-cull batch would split into pipelines that draw identically.
        int[] off = signature();
        int[] otherOff = signature();
        off[Lane2ImmediateRecorder.SIGNATURE_CULL] = 0;
        otherOff[Lane2ImmediateRecorder.SIGNATURE_CULL] = 0;
        otherOff[Lane2ImmediateRecorder.SIGNATURE_CULL_FACE] = VulkanCullState.GL_FRONT_AND_BACK;
        otherOff[Lane2ImmediateRecorder.SIGNATURE_FRONT_FACE] = VulkanCullState.GL_CW;
        assertEquals(VulkanCullState.NONE, state(off)[5]);
        assertEquals(state(off)[5], state(otherOff)[5]);
    }

    @Test void theWindowTargetMasksTheAlphaWrite() {
        int[] window = signature();
        window[Lane2ImmediateRecorder.SIGNATURE_WINDOW_TARGET] = 1;
        int[] state = state(window);
        assertEquals(Lane2ImmediateState.COLOR_CHANNEL_ALL & ~0x8, state[11],
                "the delivered frame has no alpha bits, so the write is masked there");
    }

    @Test void theAlphaTestMovesAsOne() {
        int[] enabled = signature();
        enabled[Lane2ImmediateRecorder.SIGNATURE_ALPHA_TEST] = 1;
        int[] state = state(enabled);
        assertEquals(Float.floatToIntBits(0.1f), state[6], "the reference is captured as its own bits");
        assertEquals(Lane2ImmediateState.ALPHA_GREATER,
                state[14], "GL_GREATER is 0x204, which is what the audit's default compares");

        // The reference and the function are ignored while the test is off: the signature may carry them, the state
        // must not, or a batch with the test disabled would key itself by values it does not use.
        int[] off = signature();
        int[] otherOff = signature();
        otherOff[Lane2ImmediateRecorder.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(0.9f);
        otherOff[Lane2ImmediateRecorder.SIGNATURE_ALPHA_FUNC] = Lane2ImmediateState.ALPHA_LESS;
        assertEquals(0, state(off)[6], "the test is off, so no reference is captured");
        assertEquals(0, state(off)[14], "the test is off, so no function is captured");
        assertEquals(state(off)[6], state(otherOff)[6]);
        assertEquals(state(off)[14], state(otherOff)[14]);
    }

    @Test void blendingTranslatesTheFactorsAndTheEquations() {
        int[] blend = signature();
        blend[Lane2ImmediateRecorder.SIGNATURE_BLEND] = 1;
        blend[Lane2ImmediateRecorder.SIGNATURE_SRC_RGB] = Lane2ImmediateState.BLEND_ONE;
        blend[Lane2ImmediateRecorder.SIGNATURE_DST_RGB] = Lane2ImmediateState.BLEND_ZERO;
        blend[Lane2ImmediateRecorder.SIGNATURE_EQ_RGB] = Lane2ImmediateState.BLEND_FUNC_SUBTRACT;
        int[] state = state(blend);
        assertEquals(1, state[0]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE, state[1]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ZERO, state[2]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_OP_SUBTRACT, state[12], "GL's SUBTRACT is not Vulkan's ordinal");
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE, state[9]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA, state[10]);
    }

    @Test void everyCapturedSlotHasASignatureInput() {
        // Two states that differ only in one signature slot must differ in the state: a slot no state reads would be a
        // comparison the guard pays for and a change it cannot see.
        int[] base = state(signature());
        int[] changes = {
                Lane2ImmediateRecorder.SIGNATURE_BLEND,
                Lane2ImmediateRecorder.SIGNATURE_SRC_RGB,
                Lane2ImmediateRecorder.SIGNATURE_DST_RGB,
                Lane2ImmediateRecorder.SIGNATURE_SRC_ALPHA,
                Lane2ImmediateRecorder.SIGNATURE_DST_ALPHA,
                Lane2ImmediateRecorder.SIGNATURE_EQ_RGB,
                Lane2ImmediateRecorder.SIGNATURE_EQ_ALPHA,
                Lane2ImmediateRecorder.SIGNATURE_COLOR_MASK,
                Lane2ImmediateRecorder.SIGNATURE_ALPHA_TEST,
                Lane2ImmediateRecorder.SIGNATURE_ALPHA_REF,
                Lane2ImmediateRecorder.SIGNATURE_ALPHA_FUNC,
                Lane2ImmediateRecorder.SIGNATURE_CULL,
                Lane2ImmediateRecorder.SIGNATURE_DEPTH_TEST,
                Lane2ImmediateRecorder.SIGNATURE_DEPTH_WRITE,
                Lane2ImmediateRecorder.SIGNATURE_DEPTH_FUNC,
                Lane2ImmediateRecorder.SIGNATURE_LIGHTING,
                Lane2ImmediateRecorder.SIGNATURE_ENV_MODE,
                Lane2ImmediateRecorder.SIGNATURE_ENV_RGB,
                Lane2ImmediateRecorder.SIGNATURE_WINDOW_TARGET,
                Lane2ImmediateRecorder.SIGNATURE_CULL_FACE,
                Lane2ImmediateRecorder.SIGNATURE_FRONT_FACE,
        };
        // Each slot is driven by a change that is meaningful for it: enabling the term it belongs to, or moving the
        // value it carries. The alpha reference and function need the test on, the factors and equations blending on.
        for (int slot : changes) {
            int[] changed = signature();
            switch (slot) {
                case Lane2ImmediateRecorder.SIGNATURE_BLEND:
                case Lane2ImmediateRecorder.SIGNATURE_SRC_RGB:
                case Lane2ImmediateRecorder.SIGNATURE_DST_RGB:
                case Lane2ImmediateRecorder.SIGNATURE_SRC_ALPHA:
                case Lane2ImmediateRecorder.SIGNATURE_DST_ALPHA:
                case Lane2ImmediateRecorder.SIGNATURE_EQ_RGB:
                case Lane2ImmediateRecorder.SIGNATURE_EQ_ALPHA:
                    if (slot != Lane2ImmediateRecorder.SIGNATURE_BLEND) {
                        changed[Lane2ImmediateRecorder.SIGNATURE_BLEND] = 1;
                    }
                    changed[slot] = changed[slot] == Lane2ImmediateState.BLEND_ONE
                            ? Lane2ImmediateState.BLEND_DST_COLOR : Lane2ImmediateState.BLEND_ONE;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_COLOR_MASK:
                    changed[slot] = Lane2ImmediateState.COLOR_CHANNEL_ALL & ~0x8;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_ALPHA_TEST:
                case Lane2ImmediateRecorder.SIGNATURE_ALPHA_REF:
                case Lane2ImmediateRecorder.SIGNATURE_ALPHA_FUNC:
                    changed[Lane2ImmediateRecorder.SIGNATURE_ALPHA_TEST] = 1;
                    if (slot == Lane2ImmediateRecorder.SIGNATURE_ALPHA_FUNC) {
                        changed[slot] = Lane2ImmediateState.ALPHA_LESS;
                    } else if (slot == Lane2ImmediateRecorder.SIGNATURE_ALPHA_REF) {
                        changed[slot] = Float.floatToIntBits(0.9f);
                    }
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_CULL:
                case Lane2ImmediateRecorder.SIGNATURE_DEPTH_TEST:
                case Lane2ImmediateRecorder.SIGNATURE_DEPTH_WRITE:
                case Lane2ImmediateRecorder.SIGNATURE_LIGHTING:
                case Lane2ImmediateRecorder.SIGNATURE_WINDOW_TARGET:
                    changed[slot] = changed[slot] == 0 ? 1 : 0;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_DEPTH_FUNC:
                    changed[slot] = Lane2ImmediateState.DEPTH_ALWAYS;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_CULL_FACE:
                    // The cull enable is already on in the base signature, so moving the face has to move the state.
                    changed[slot] = VulkanCullState.GL_FRONT;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_FRONT_FACE:
                    changed[slot] = VulkanCullState.GL_CW;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_ENV_MODE:
                    changed[slot] = NativeTextureEnvironment.COMBINE;
                    changed[Lane2ImmediateRecorder.SIGNATURE_ENV_RGB] = 0x84E7;
                    break;
                case Lane2ImmediateRecorder.SIGNATURE_ENV_RGB:
                    changed[Lane2ImmediateRecorder.SIGNATURE_ENV_MODE] = NativeTextureEnvironment.COMBINE;
                    changed[slot] = 0x84E7;
                    break;
                default:
                    fail("unhandled slot " + slot);
            }
            assertFalse(java.util.Arrays.equals(base, state(changed)),
                    "signature slot " + slot + " must reach the state the key is built from");
        }
    }

    @Test void theSameSignatureAlwaysMapsToTheSameState() {
        assertEquals(java.util.Arrays.toString(state(signature())), java.util.Arrays.toString(state(signature())));
        // The key is a pure function of the array, which is why a cached state and a cached key can be kept together.
        assertEquals(Lane2ImmediateRecorder.celtium$stateKey(VulkanImmediateFormats.ITEM, state(signature())),
                Lane2ImmediateRecorder.celtium$stateKey(VulkanImmediateFormats.ITEM, state(signature())));
    }
}
