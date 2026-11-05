package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay path proves a cached tuple's non-matrix values with four version numbers instead of capturing and
 * comparing seventy-one of them. That is only sound if every mutator of a group moves its version - and only useful if
 * a mutator of one group does <em>not</em> move another, or a modelview edit would refuse the state half of every
 * replay and the proof would never be granted.
 * <p>
 * These cases are that contract, driven through the real setters the GL receiver calls, not through the versions
 * themselves: the state half (blend, depth, alpha, cull, colorMask, lighting, the current colour), the projection
 * stack, the texture-matrix stacks and the texture environment. The class under test is the mirror itself, so nothing
 * here needs a device.
 */
class ReplayValueVersionTest {

    private interface Step {
        void run();
    }

    /** Asserts a setter moves the state version and neither matrix version, and says which one failed. */
    private static void stateMoves(VulkanGlState state, String what, Step step) {
        long stateVersion = state.stateVersion();
        long projection = state.projectionVersion();
        long texture = state.textureMatrixVersion();
        step.run();
        assertTrue(state.stateVersion() > stateVersion, what + " must move the state version");
        assertEquals(projection, state.projectionVersion(), what + " is not a projection change");
        assertEquals(texture, state.textureMatrixVersion(), what + " is not a texture-matrix change");
    }

    /** Asserts a matrix edit moves exactly the version of its stack. */
    private static void matrixMoves(VulkanGlState state, String what, long expectedProjection, long expectedTexture,
            Step step) {
        long stateVersion = state.stateVersion();
        long projection = state.projectionVersion();
        long texture = state.textureMatrixVersion();
        step.run();
        assertEquals(stateVersion, state.stateVersion(), what + " does not change the state half");
        assertEquals(expectedProjection, state.projectionVersion() - projection, what + " projection version");
        assertEquals(expectedTexture, state.textureMatrixVersion() - texture, what + " texture-matrix version");
    }

    @Test void everyStateSetterMovesTheStateVersion() {
        VulkanGlState state = new VulkanGlState();
        stateMoves(state, "blendEnabled", () -> state.blendEnabled(true));
        stateMoves(state, "blendFunc", () -> state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO,
                VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO));
        stateMoves(state, "blendEquation", () -> state.blendEquation(VulkanGlState.BLEND_FUNC_SUBTRACT,
                VulkanGlState.BLEND_FUNC_ADD));
        stateMoves(state, "colorMask", () -> state.colorMask(VulkanGlState.COLOR_CHANNEL_ALL & ~0x8));
        stateMoves(state, "depthTestEnabled", () -> state.depthTestEnabled(false));
        stateMoves(state, "depthFunc", () -> state.depthFunc(VulkanGlState.DEPTH_ALWAYS));
        stateMoves(state, "depthMask", () -> state.depthMask(false));
        stateMoves(state, "alphaFuncReference", () -> state.alphaFuncReference(0.5f));
        stateMoves(state, "alphaTestEnabled", () -> state.alphaTestEnabled(true));
        stateMoves(state, "alphaFunc", () -> state.alphaFunc(VulkanGlState.ALPHA_LESS, 0.3f));
        stateMoves(state, "cullEnabled", () -> state.cullEnabled(!state.cullEnabled()));
        stateMoves(state, "cullFace", () -> state.cullFace(1028));
        stateMoves(state, "lightingEnabled", () -> state.lightingEnabled(true));
        stateMoves(state, "lightEnabled", () -> state.lightEnabled(0, true));
        stateMoves(state, "color", () -> state.color(0.25f, 0.5f, 0.75f, 0.5f));
    }

    /** An unchanged value is not a mutation: the GUI repeats colours and flags around nearly every draw. */
    @Test void anUnchangedValueMovesNoVersion() {
        VulkanGlState state = new VulkanGlState();
        state.color(0.25f, 0.5f, 0.75f, 0.5f);
        state.alphaTestEnabled(true);
        state.alphaFunc(VulkanGlState.ALPHA_LESS, 0.3f);
        state.depthMask(false);
        state.blendEnabled(true);
        state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO, VulkanGlState.BLEND_ONE,
                VulkanGlState.BLEND_ZERO);
        long version = state.stateVersion();
        state.color(0.25f, 0.5f, 0.75f, 0.5f);
        state.alphaTestEnabled(true);
        state.alphaFunc(VulkanGlState.ALPHA_LESS, 0.3f);
        state.alphaFuncReference(0.3f);
        state.depthMask(false);
        state.blendEnabled(true);
        state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO, VulkanGlState.BLEND_ONE,
                VulkanGlState.BLEND_ZERO);
        state.cullEnabled(state.cullEnabled());
        state.lightingEnabled(state.lightingEnabled());
        state.lightEnabled(0, state.lightEnabled(0));
        state.lightEnabled(5, true);
        assertEquals(version, state.stateVersion());
        state.color(0.25f, 0.5f, 0.75f, 0.25f);
        assertTrue(state.stateVersion() > version, "a changed colour still moves it");
    }

    /** The colour and the per-light enables are replay-visible but not part of the immediate state key. */
    @Test void colourAndLightEnablesMoveTheStateButNotTheKeyVersion() {
        VulkanGlState state = new VulkanGlState();
        long key = state.keyStateVersion();
        long all = state.stateVersion();
        state.color(0.1f, 0.2f, 0.3f, 0.4f);
        state.lightEnabled(1, true);
        assertEquals(key, state.keyStateVersion());
        assertTrue(state.stateVersion() > all);
        state.blendEnabled(true);
        assertTrue(state.keyStateVersion() > key, "a keyed value still moves the key version");
    }

    @Test void aModelviewEditMovesNoVersionAtAll() {
        // The modelview is the one input a repeated replay is allowed to change. A version that moved on it would
        // refuse the state half of every replay, so the acceleration would exist and never be granted.
        VulkanGlState state = new VulkanGlState();
        matrixMoves(state, "modelview loadIdentity", 0, 0, state::loadIdentity);
        matrixMoves(state, "modelview pushMatrix", 0, 0, state::pushMatrix);
        matrixMoves(state, "modelview translate", 0, 0, () -> state.translate(1, 2, 3));
        matrixMoves(state, "modelview rotate", 0, 0, () -> state.rotate(45, 0, 1, 0));
        matrixMoves(state, "modelview scale", 0, 0, () -> state.scale(2, 2, 2));
        matrixMoves(state, "modelview loadMatrix", 0, 0, () -> state.loadMatrix(identity()));
        matrixMoves(state, "modelview multiplyMatrix", 0, 0, () -> state.multiplyMatrix(identity()));
        matrixMoves(state, "modelview popMatrix", 0, 0, state::popMatrix);
    }

    @Test void theProjectionAndTextureStacksHaveTheirOwnVersions() {
        VulkanGlState state = new VulkanGlState();
        state.matrixMode(VulkanGlState.MATRIX_MODE_PROJECTION);
        matrixMoves(state, "projection loadIdentity", 1, 0, state::loadIdentity);
        matrixMoves(state, "projection loadMatrix", 1, 0, () -> state.loadMatrix(identity()));
        matrixMoves(state, "projection multiplyMatrix", 1, 0, () -> state.multiplyMatrix(identity()));
        matrixMoves(state, "projection pushMatrix", 1, 0, state::pushMatrix);
        matrixMoves(state, "projection popMatrix", 1, 0, state::popMatrix);

        state.matrixMode(VulkanGlState.MATRIX_MODE_TEXTURE);
        matrixMoves(state, "texture loadIdentity", 0, 1, state::loadIdentity);
        matrixMoves(state, "texture loadMatrix", 0, 1, () -> state.loadMatrix(identity()));
        matrixMoves(state, "texture multiplyMatrix", 0, 1, () -> state.multiplyMatrix(identity()));
        matrixMoves(state, "texture pushMatrix", 0, 1, state::pushMatrix);
        matrixMoves(state, "texture popMatrix", 0, 1, state::popMatrix);
    }

    @Test void theTextureEnvironmentMovesItsVersionOnlyWhenAValueChanges() {
        NativeRasterState raster = new NativeRasterState();
        NativeTextureEnvironment environment = new NativeTextureEnvironment(new NativeTextureBindings(raster), raster);
        assertEquals(0L, environment.version());
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.MODE,
                NativeTextureEnvironment.COMBINE);
        assertEquals(1L, environment.version(), "a mode that changed moves the version");
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.MODE,
                NativeTextureEnvironment.COMBINE);
        assertEquals(1L, environment.version(), "setting the value it already holds is not a change");
        environment.parameter(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.RGB,
                NativeTextureEnvironment.REPLACE);
        assertEquals(2L, environment.version());
        environment.color(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.COLOR, 0.25f, 0.5f, 0.75f, 1.0f);
        assertEquals(3L, environment.version(), "the combine colour is read from the same environment");
        environment.color(NativeTextureEnvironment.TARGET, NativeTextureEnvironment.COLOR, 0.25f, 0.5f, 0.75f, 1.0f);
        assertEquals(3L, environment.version(), "the same colour again is not a change");
    }

    @Test void aStateChangeIsVisibleThroughTheSignatureTheVersionReplaces() {
        // The version is only a shorthand for "the signature is unchanged": the same edit must be visible in both, or
        // the proof would be granted for a value the capture reads. The bridge between them is the state's own
        // accessors, which is what the signature is built from.
        VulkanGlState state = new VulkanGlState();
        long version = state.stateVersion();
        state.depthMask(false);
        assertFalse(state.depthMask());
        assertTrue(state.stateVersion() > version, "the depth write is part of the captured state");
        state.alphaFunc(VulkanGlState.ALPHA_LESS, 0.4f);
        assertEquals(VulkanGlState.ALPHA_LESS, state.alphaFunc());
        assertEquals(0.4f, state.alphaFuncReference());
        state.color(0.125f, 0.25f, 0.375f, 0.5f);
        assertEquals(0.125f, state.colorRed());
        assertEquals(0.5f, state.colorAlpha());
    }

    private static float[] identity() {
        float[] identity = new float[16];
        identity[0] = 1;
        identity[5] = 1;
        identity[10] = 1;
        identity[15] = 1;
        return identity;
    }
}
