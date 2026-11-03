package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The version contract the per-offer state-signature cache depends on, checked input by input.
 * <p>
 * {@code VulkanTerrainGpuBackend.celtium$stateSignature} reads twenty-one inputs on every offer and now skips the
 * capture when three versions are the ones the cached signature was taken under:
 * {@link VulkanGlState#stateVersion()} (the wrapper's own fields plus the raster mirror),
 * {@link NativeTextureEnvironment#version()} (the two environment parameters) and the window-target bit. That is only
 * exact if every input is owned by one of those versions. A setter that moved an input without moving its version
 * would make the cache serve a stale signature, and a stale signature is a wrong pipeline - a wrong picture, not a
 * slow frame - so the contract is enumerated here rather than trusted from the comment on {@code stateVersion}.
 * <p>
 * The test is deliberately one assertion per signature slot, named by the slot: when a future setter forgets its bump,
 * the failure names the input instead of saying "the signature moved".
 * <p>
 * The window-target bit is not a version: the cache keys it directly, because the registry's identity is the value
 * itself and there is nothing cheaper to compare it against.
 */
class StateSignatureVersionContractTest {

    /** The wrapper's own fields and the raster mirror, through the one number the cache compares. */
    @Test
    void everyWrapperAndRasterInputMovesTheStateVersion() {
        VulkanGlState state = new VulkanGlState();

        // SIGNATURE_BLEND
        long before = state.stateVersion();
        state.blendEnabled(!state.blendEnabled());
        assertMoved("blend enable", before, state.stateVersion());

        // SIGNATURE_SRC_RGB / DST_RGB / SRC_ALPHA / DST_ALPHA
        before = state.stateVersion();
        state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO,
                VulkanGlState.BLEND_DST_COLOR, VulkanGlState.BLEND_SRC_COLOR);
        assertMoved("blend factors", before, state.stateVersion());

        // SIGNATURE_EQ_RGB / EQ_ALPHA
        before = state.stateVersion();
        state.blendEquation(VulkanGlState.BLEND_FUNC_SUBTRACT, VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT);
        assertMoved("blend equations", before, state.stateVersion());

        // SIGNATURE_COLOR_MASK
        before = state.stateVersion();
        state.colorMask(0x7);
        assertMoved("colour mask", before, state.stateVersion());

        // SIGNATURE_ALPHA_TEST
        before = state.stateVersion();
        state.alphaTestEnabled(!state.alphaTestEnabled());
        assertMoved("alpha test enable", before, state.stateVersion());

        // SIGNATURE_ALPHA_REF
        before = state.stateVersion();
        state.alphaFuncReference(0.25f);
        assertMoved("alpha reference", before, state.stateVersion());

        // SIGNATURE_ALPHA_FUNC
        before = state.stateVersion();
        state.alphaFunc(0x0201, 0.25f);
        assertMoved("alpha function", before, state.stateVersion());

        // SIGNATURE_CULL
        before = state.stateVersion();
        state.cullEnabled(!state.cullEnabled());
        assertMoved("cull enable", before, state.stateVersion());

        // SIGNATURE_CULL_FACE
        before = state.stateVersion();
        state.cullFace(VulkanGlState.CULL_FRONT);
        assertMoved("culled face", before, state.stateVersion());

        // SIGNATURE_FRONT_FACE
        before = state.stateVersion();
        state.frontFace(0x0900);
        assertMoved("front face", before, state.stateVersion());

        // SIGNATURE_DEPTH_TEST
        before = state.stateVersion();
        state.depthTestEnabled(!state.depthTestEnabled());
        assertMoved("depth test enable", before, state.stateVersion());

        // SIGNATURE_DEPTH_WRITE
        before = state.stateVersion();
        state.depthMask(!state.depthMask());
        assertMoved("depth write", before, state.stateVersion());

        // SIGNATURE_DEPTH_FUNC
        before = state.stateVersion();
        state.depthFunc(0x0201);
        assertMoved("depth function", before, state.stateVersion());

        // SIGNATURE_LIGHTING
        before = state.stateVersion();
        state.lightingEnabled(!state.lightingEnabled());
        assertMoved("lighting enable", before, state.stateVersion());
    }

    /**
     * The two environment parameters, through the environment's own version.
     * <p>
     * {@code unitParameter} answers a default for an unset pname, so the test sets a value that differs from the
     * default and then a second one: both have to move the version, because the cache cannot tell a write that
     * changed the answer from one that did not.
     */
    @Test
    void everyEnvironmentInputMovesTheEnvironmentVersion() {
        NativeTextureEnvironment environment = new NativeTextureEnvironment(
                new NativeTextureBindings(new NativeRasterState()), new NativeRasterState());

        // SIGNATURE_ENV_MODE
        long before = environment.version();
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.MODE,
                NativeTextureEnvironment.REPLACE);
        assertMoved("environment mode", before, environment.version());

        // SIGNATURE_ENV_RGB
        before = environment.version();
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.RGB,
                NativeTextureEnvironment.CONSTANT);
        assertMoved("environment rgb", before, environment.version());

        // A write of the value already held does not move the version, and that is still exact: the parameter's answer
        // did not change, so the signature it feeds did not either. The direction that would be unsafe is the other
        // one - a changed value that leaves the version alone - and the two assertions above cover it.
        before = environment.version();
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.RGB,
                NativeTextureEnvironment.CONSTANT);
        assertEquals(before, environment.version(), "an unchanged parameter must not move the version");
    }

    /**
     * The cache predicate itself: a hit needs all three keys, and any one of them moving is a miss.
     * <p>
     * This is the arithmetic half of the change, exercised without a device. It cannot prove the capture is skipped -
     * only a leg's hit counter can - but it does prove the predicate cannot answer "hit" for a signature taken under a
     * different version, which is the failure that would be a wrong pipeline.
     */
    @Test
    void theCacheHitsOnlyWhenAllThreeKeysAreUnchanged() {
        assertTrue(VulkanTerrainGpuBackend.celtium$signatureCacheHit(7L, 3L, 1, 7L, 3L, 1));
        assertFalse(VulkanTerrainGpuBackend.celtium$signatureCacheHit(8L, 3L, 1, 7L, 3L, 1), "state version moved");
        assertFalse(VulkanTerrainGpuBackend.celtium$signatureCacheHit(7L, 4L, 1, 7L, 3L, 1), "environment moved");
        assertFalse(VulkanTerrainGpuBackend.celtium$signatureCacheHit(7L, 3L, 0, 7L, 3L, 1), "window target moved");
        // The initial cache is "no signature yet": -1 for both versions and -1 for the target, which no live version
        // can equal because both counters start at zero and only increase.
        assertFalse(VulkanTerrainGpuBackend.celtium$signatureCacheHit(0L, 0L, 0, -1L, -1L, -1));
    }

    private static void assertMoved(String input, long before, long after) {
        assertTrue(after > before, input + " did not move its version (" + before + " -> " + after
                + "); the signature cache would serve a stale signature for it");
    }

    /** The version is monotonic, which is what makes "unchanged" a safe answer for "the same signature". */
    @Test
    void theVersionNeverGoesBackwards() {
        VulkanGlState state = new VulkanGlState();
        long previous = state.stateVersion();
        for (int i = 0; i < 8; i++) {
            state.blendEnabled(i % 2 == 0);
            state.depthMask(i % 2 == 0);
            state.cullEnabled(i % 2 == 0);
            long now = state.stateVersion();
            assertTrue(now >= previous, "state version went backwards: " + previous + " -> " + now);
            previous = now;
        }
        assertEquals(previous, state.stateVersion());
    }
}
