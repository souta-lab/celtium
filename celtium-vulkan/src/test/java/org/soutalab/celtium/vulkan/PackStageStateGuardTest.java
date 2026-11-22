package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pack-stage state bracket, exercised without a device: a guest setup that disturbs the mirror (the
 * shadow stage's cull disable is the headline case) must leave the mirror exactly as the execution found it
 * once the bracket closes, and an execution that disturbs nothing must not move a single version counter -
 * the replay caches key on those numbers, so a restore that bumps them for nothing would refuse replays
 * frame-wide. The framebuffer/viewport half of the bracket reads the stub, which no test installs; it is
 * inert here (the getters answer -1) and is covered by the in-game census leg instead.
 */
class PackStageStateGuardTest {

    /** The backend's depth/alpha setters, faked over the same state model the guard captured from. */
    private static final class RecordingSink implements PackStageStateGuard.DepthAlphaSink {
        private final VulkanGlState state;
        private int calls;

        RecordingSink(VulkanGlState state) {
            this.state = state;
        }

        @Override
        public void setDepthTest(boolean enabled) {
            this.calls++;
            this.state.depthTestEnabled(enabled);
        }

        @Override
        public void depthFunc(int func) {
            this.calls++;
            this.state.depthFunc(func);
        }

        @Override
        public void setDepthMask(boolean mask) {
            this.calls++;
            this.state.depthMask(mask);
        }

        @Override
        public void setAlphaTestEnabled(boolean enabled) {
            this.calls++;
            this.state.alphaTestEnabled(enabled);
        }

        @Override
        public void setAlphaFunc(int glFunc, float reference) {
            this.calls++;
            this.state.alphaFunc(glFunc, reference);
        }
    }

    /** A pre-execution state no default produces, so a restore to defaults instead of the snapshot fails. */
    private static VulkanGlState distinctiveState() {
        VulkanGlState state = new VulkanGlState();
        state.cullEnabled(true);
        state.cullFace(VulkanGlState.CULL_FRONT);
        state.frontFace(0x0900);
        state.blendEnabled(true);
        state.blendFunc(VulkanGlState.BLEND_SRC_COLOR, VulkanGlState.BLEND_DST_COLOR,
                VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO);
        state.blendEquation(VulkanGlState.BLEND_FUNC_SUBTRACT, VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT);
        state.colorMask(0x7);
        state.depthTestEnabled(false);
        state.depthFunc(0x0200);
        state.depthMask(false);
        state.alphaTestEnabled(true);
        state.alphaFunc(2, 0.5f);
        return state;
    }

    /** What a guest setup is free to do to the mirror when its restore does not cover it. */
    private static void disturb(VulkanGlState state) {
        state.cullEnabled(false);
        state.cullFace(VulkanGlState.CULL_BACK);
        state.frontFace(0x0901);
        state.blendEnabled(false);
        state.blendFunc(VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO,
                VulkanGlState.BLEND_ONE, VulkanGlState.BLEND_ZERO);
        state.blendEquation(VulkanGlState.BLEND_FUNC_ADD, VulkanGlState.BLEND_FUNC_ADD);
        state.colorMask(VulkanGlState.COLOR_CHANNEL_ALL);
        state.depthTestEnabled(true);
        state.depthFunc(515);
        state.depthMask(true);
        state.alphaTestEnabled(false);
        state.alphaFunc(4, 0.1f);
    }

    private static void assertDistinctive(VulkanGlState state) {
        assertTrue(state.cullEnabled(), "the shadow stage's disableCull must not survive the bracket");
        assertEquals(VulkanGlState.CULL_FRONT, state.cullFace());
        assertEquals(0x0900, state.frontFace());
        assertTrue(state.blendEnabled());
        assertEquals(VulkanGlState.BLEND_SRC_COLOR, state.blendSrcRgb());
        assertEquals(VulkanGlState.BLEND_DST_COLOR, state.blendDstRgb());
        assertEquals(VulkanGlState.BLEND_ONE, state.blendSrcAlpha());
        assertEquals(VulkanGlState.BLEND_ZERO, state.blendDstAlpha());
        assertEquals(VulkanGlState.BLEND_FUNC_SUBTRACT, state.blendEquationRgb());
        assertEquals(VulkanGlState.BLEND_FUNC_REVERSE_SUBTRACT, state.blendEquationAlpha());
        assertEquals(0x7, state.colorMask());
        assertFalse(state.depthTestEnabled());
        assertEquals(0x0200, state.depthFunc());
        assertFalse(state.depthMask());
        assertTrue(state.alphaTestEnabled());
        assertEquals(2, state.alphaFunc());
        assertEquals(0.5f, state.alphaFuncReference());
    }

    @Test
    void restorePutsBackEveryMirroredFieldTheGuestDisturbed() {
        VulkanGlState state = distinctiveState();
        PackStageStateGuard guard = new PackStageStateGuard();
        guard.capture(state);
        disturb(state);
        guard.restore(state, new RecordingSink(state));
        assertDistinctive(state);
    }

    @Test
    void anUndisturbedExecutionMovesNoVersionAndCallsNoSetter() {
        VulkanGlState state = distinctiveState();
        PackStageStateGuard guard = new PackStageStateGuard();
        guard.capture(state);
        long stateVersion = state.stateVersion();
        long keyVersion = state.keyStateVersion();
        RecordingSink sink = new RecordingSink(state);
        guard.restore(state, sink);
        assertEquals(0, sink.calls, "nothing disturbed: the depth/alpha sink must stay silent");
        assertEquals(stateVersion, state.stateVersion(), "a no-op restore must not refuse replay tuples");
        assertEquals(keyVersion, state.keyStateVersion());
        assertDistinctive(state);
    }

    @Test
    void aCullOnlyDisturbanceRestoresCullAndNothingElse() {
        VulkanGlState state = distinctiveState();
        PackStageStateGuard guard = new PackStageStateGuard();
        guard.capture(state);
        state.cullEnabled(false);
        RecordingSink sink = new RecordingSink(state);
        guard.restore(state, sink);
        assertTrue(state.cullEnabled());
        assertEquals(0, sink.calls, "depth/alpha were never disturbed: no setter may fire for them");
        assertDistinctive(state);
    }
}
