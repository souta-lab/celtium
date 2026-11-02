package org.soutalab.celtium.vulkan;

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
        signature[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = VulkanGlState.BLEND_SRC_ALPHA;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DST_RGB] = VulkanGlState.BLEND_ONE_MINUS_SRC_ALPHA;
        signature[VulkanTerrainGpuBackend.SIGNATURE_SRC_ALPHA] = VulkanGlState.BLEND_ONE;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DST_ALPHA] = VulkanGlState.BLEND_ONE_MINUS_SRC_ALPHA;
        signature[VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB] = VulkanGlState.BLEND_FUNC_ADD;
        signature[VulkanTerrainGpuBackend.SIGNATURE_EQ_ALPHA] = VulkanGlState.BLEND_FUNC_ADD;
        signature[VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK] = VulkanGlState.COLOR_CHANNEL_ALL;
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST] = 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(0.1f);
        signature[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC] = VulkanGlState.ALPHA_GREATER;
        signature[VulkanTerrainGpuBackend.SIGNATURE_CULL] = 1;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_TEST] = 1;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_WRITE] = 1;
        signature[VulkanTerrainGpuBackend.SIGNATURE_DEPTH_FUNC] = 515;
        signature[VulkanTerrainGpuBackend.SIGNATURE_LIGHTING] = 0;
        signature[VulkanTerrainGpuBackend.SIGNATURE_ENV_MODE] = NativeTextureEnvironment.MODULATE;
        signature[VulkanTerrainGpuBackend.SIGNATURE_ENV_RGB] = 0x1702;
        signature[VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET] = 0;
        return signature;
    }

    private static int[] state(int[] signature) {
        int[] state = new int[16];
        VulkanTerrainGpuBackend.celtium$stateFromSignature(signature, state);
        return state;
    }

    @Test void theDefaultStateIsWhatTheSlotsSay() {
        int[] state = state(signature());
        assertEquals(0, state[0], "no blending");
        assertEquals(0, state[1], "a disabled blend zeros its factors, like the capture always did");
        assertEquals(0, state[2]);
        assertEquals(1, state[3], "depth test on");
        assertEquals(1, state[4], "depth write on");
        assertEquals(1, state[5], "cull on");
        assertEquals(0, state[6], "an alpha test that is off captures a reference of zero");
        assertEquals(515, state[7], "the GL depth function travels unchanged");
        assertEquals(0, state[8], "lighting off");
        assertEquals(VulkanGlState.COLOR_CHANNEL_ALL, state[11]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, state[12]);
        assertEquals(org.lwjgl.vulkan.VK10.VK_BLEND_OP_ADD, state[13]);
        assertEquals(0, state[14], "the function is zero while the test is off");
        assertEquals(0, state[15], "unit 0 MODULATE is not the combine the shader evaluates");
    }

    @Test void theWindowTargetMasksTheAlphaWrite() {
        int[] window = signature();
        window[VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET] = 1;
        int[] state = state(window);
        assertEquals(VulkanGlState.COLOR_CHANNEL_ALL & ~0x8, state[11],
                "the delivered frame has no alpha bits, so the write is masked there");
    }

    @Test void theAlphaTestMovesAsOne() {
        int[] enabled = signature();
        enabled[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST] = 1;
        int[] state = state(enabled);
        assertEquals(Float.floatToIntBits(0.1f), state[6], "the reference is captured as its own bits");
        assertEquals(VulkanGlState.ALPHA_GREATER,
                state[14], "GL_GREATER is 0x204, which is what the audit's default compares");

        // The reference and the function are ignored while the test is off: the signature may carry them, the state
        // must not, or a batch with the test disabled would key itself by values it does not use.
        int[] off = signature();
        int[] otherOff = signature();
        otherOff[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF] = Float.floatToIntBits(0.9f);
        otherOff[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC] = VulkanGlState.ALPHA_LESS;
        assertEquals(0, state(off)[6], "the test is off, so no reference is captured");
        assertEquals(0, state(off)[14], "the test is off, so no function is captured");
        assertEquals(state(off)[6], state(otherOff)[6]);
        assertEquals(state(off)[14], state(otherOff)[14]);
    }

    @Test void blendingTranslatesTheFactorsAndTheEquations() {
        int[] blend = signature();
        blend[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 1;
        blend[VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB] = VulkanGlState.BLEND_ONE;
        blend[VulkanTerrainGpuBackend.SIGNATURE_DST_RGB] = VulkanGlState.BLEND_ZERO;
        blend[VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB] = VulkanGlState.BLEND_FUNC_SUBTRACT;
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
                VulkanTerrainGpuBackend.SIGNATURE_BLEND,
                VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB,
                VulkanTerrainGpuBackend.SIGNATURE_DST_RGB,
                VulkanTerrainGpuBackend.SIGNATURE_SRC_ALPHA,
                VulkanTerrainGpuBackend.SIGNATURE_DST_ALPHA,
                VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB,
                VulkanTerrainGpuBackend.SIGNATURE_EQ_ALPHA,
                VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK,
                VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST,
                VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF,
                VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC,
                VulkanTerrainGpuBackend.SIGNATURE_CULL,
                VulkanTerrainGpuBackend.SIGNATURE_DEPTH_TEST,
                VulkanTerrainGpuBackend.SIGNATURE_DEPTH_WRITE,
                VulkanTerrainGpuBackend.SIGNATURE_DEPTH_FUNC,
                VulkanTerrainGpuBackend.SIGNATURE_LIGHTING,
                VulkanTerrainGpuBackend.SIGNATURE_ENV_MODE,
                VulkanTerrainGpuBackend.SIGNATURE_ENV_RGB,
                VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET,
        };
        // Each slot is driven by a change that is meaningful for it: enabling the term it belongs to, or moving the
        // value it carries. The alpha reference and function need the test on, the factors and equations blending on.
        for (int slot : changes) {
            int[] changed = signature();
            switch (slot) {
                case VulkanTerrainGpuBackend.SIGNATURE_BLEND:
                case VulkanTerrainGpuBackend.SIGNATURE_SRC_RGB:
                case VulkanTerrainGpuBackend.SIGNATURE_DST_RGB:
                case VulkanTerrainGpuBackend.SIGNATURE_SRC_ALPHA:
                case VulkanTerrainGpuBackend.SIGNATURE_DST_ALPHA:
                case VulkanTerrainGpuBackend.SIGNATURE_EQ_RGB:
                case VulkanTerrainGpuBackend.SIGNATURE_EQ_ALPHA:
                    if (slot != VulkanTerrainGpuBackend.SIGNATURE_BLEND) {
                        changed[VulkanTerrainGpuBackend.SIGNATURE_BLEND] = 1;
                    }
                    changed[slot] = changed[slot] == VulkanGlState.BLEND_ONE
                            ? VulkanGlState.BLEND_DST_COLOR : VulkanGlState.BLEND_ONE;
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_COLOR_MASK:
                    changed[slot] = VulkanGlState.COLOR_CHANNEL_ALL & ~0x8;
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST:
                case VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF:
                case VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC:
                    changed[VulkanTerrainGpuBackend.SIGNATURE_ALPHA_TEST] = 1;
                    if (slot == VulkanTerrainGpuBackend.SIGNATURE_ALPHA_FUNC) {
                        changed[slot] = VulkanGlState.ALPHA_LESS;
                    } else if (slot == VulkanTerrainGpuBackend.SIGNATURE_ALPHA_REF) {
                        changed[slot] = Float.floatToIntBits(0.9f);
                    }
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_CULL:
                case VulkanTerrainGpuBackend.SIGNATURE_DEPTH_TEST:
                case VulkanTerrainGpuBackend.SIGNATURE_DEPTH_WRITE:
                case VulkanTerrainGpuBackend.SIGNATURE_LIGHTING:
                case VulkanTerrainGpuBackend.SIGNATURE_WINDOW_TARGET:
                    changed[slot] = changed[slot] == 0 ? 1 : 0;
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_DEPTH_FUNC:
                    changed[slot] = VulkanGlState.DEPTH_ALWAYS;
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_ENV_MODE:
                    changed[slot] = NativeTextureEnvironment.COMBINE;
                    changed[VulkanTerrainGpuBackend.SIGNATURE_ENV_RGB] = 0x84E7;
                    break;
                case VulkanTerrainGpuBackend.SIGNATURE_ENV_RGB:
                    changed[VulkanTerrainGpuBackend.SIGNATURE_ENV_MODE] = NativeTextureEnvironment.COMBINE;
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
        assertEquals(VulkanTerrainGpuBackend.celtium$stateKey(VulkanImmediateFormats.ITEM, state(signature())),
                VulkanTerrainGpuBackend.celtium$stateKey(VulkanImmediateFormats.ITEM, state(signature())));
    }
}
