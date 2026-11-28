package org.soutalab.celtium.vulkan;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.soutalab.celtium.core.frame.ActiveRenderTargets;
import org.soutalab.celtium.core.glcompat.ComputeProgramHandle;
import org.soutalab.celtium.core.glcompat.DirectGlDrawState;
import org.soutalab.celtium.core.glcompat.DrawCommand;
import org.soutalab.celtium.core.glcompat.PrimitiveTopology;
import org.soutalab.celtium.core.glcompat.VertexBindingSpan;
import org.soutalab.celtium.core.state.RenderStateKey;
import org.soutalab.celtium.glcompat.backend.DirectGlRefusalCensus;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The frame-gate contract of the production sink {@link VulkanDirectGlSink}, against the
 * real {@link DirectGlFrameBridge} publication - the half the layer-A contract tests never
 * covered, because they drove the runtime against a fake sink and so left the bridge's
 * missing writer invisible (GLPROBE-NOFRAME-DIAGNOSIS section 5). The gate is an AND of two
 * halves: the bridge's published buffer and the context's direct-GL frame slot (written in
 * production by the coordinator's template pick; written here directly, as the seat's
 * equivalent). With both halves live, a main-target draw must pass the gate and reach the
 * program check; with the publication withdrawn, the NO_FRAME refusal must return.
 *
 * <p>The dummy command buffer is an address-only wrapper: every path asserted here refuses
 * or returns before any native Vulkan call is made on it (the program/geometry check reads
 * only the sink's inline-initialised maps). Census counts are static and cumulative, so
 * every assertion is a before/after delta, as in {@link DirectGlUniformSnapshotTest}.
 */
class DirectGlFrameGateContractTest {

    @BeforeEach
    void unbindBefore() {
        // The bridge is a single static: start from the unpublished state whatever an
        // earlier test in this JVM left behind.
        DirectGlFrameBridge.unbindFrame();
    }

    @AfterEach
    void unbindAfter() {
        // Never leak a publication into another test: the existing snapshot test asserts
        // the unpublished half of this same gate and would read this test's frame.
        DirectGlFrameBridge.unbindFrame();
    }

    @Test
    void boundFramePassesTheGate() throws Exception {
        VulkanContext context = newContext();
        var sink = new VulkanDirectGlSink(context, null, null);
        context.setDirectGlFrameSlot(0);
        DirectGlFrameBridge.bindFrame(new VkCommandBuffer(1L, null), mainTargets());

        long noFrameBefore = DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME);
        long incompatibleBefore = DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.TARGET_INCOMPATIBLE);
        sink.submitDraw(mainTargetDraw(), new VertexBindingSpan(3, 0, 16, 3), null, DrawCommand.draw(3, 0));

        // The gate passed: no NO_FRAME, and the draw reached the program check, which
        // refuses the unregistered program handle - the proof of passage.
        assertEquals(noFrameBefore, DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME));
        assertEquals(incompatibleBefore + 1,
                DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.TARGET_INCOMPATIBLE));
    }

    @Test
    void unbindRestoresTheRefusal() throws Exception {
        VulkanContext context = newContext();
        var sink = new VulkanDirectGlSink(context, null, null);
        context.setDirectGlFrameSlot(0);
        DirectGlFrameBridge.bindFrame(new VkCommandBuffer(1L, null), mainTargets());
        DirectGlFrameBridge.unbindFrame();

        long noFrameBefore = DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME);
        sink.submitDraw(mainTargetDraw(), new VertexBindingSpan(3, 0, 16, 3), null, DrawCommand.draw(3, 0));

        // The slot half is still published on the context, but with the bridge withdrawn
        // the gate reads closed again: the stale-buffer protection survives the wiring.
        assertEquals(noFrameBefore + 1, DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME));
    }

    @Test
    void computeEntranceSharesTheGate() throws Exception {
        VulkanContext context = newContext();
        var sink = new VulkanDirectGlSink(context, null, null);
        context.setDirectGlFrameSlot(0);

        long noFrameBefore = DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME);
        sink.dispatchCompute(new ComputeProgramHandle(7L), 1, 1, 1);
        assertEquals(noFrameBefore + 1, DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME));

        // Bound: the compute entrance passes the same gate, and an unregistered program
        // then returns silently (the current form - nothing further is asserted).
        // The clear/blit entrances drop silently when unpublished, leaving no census
        // trace, so they stand outside this contract.
        DirectGlFrameBridge.bindFrame(new VkCommandBuffer(1L, null), mainTargets());
        sink.dispatchCompute(new ComputeProgramHandle(7L), 1, 1, 1);
        assertEquals(noFrameBefore + 1, DirectGlRefusalCensus.count(DirectGlRefusalCensus.Reason.NO_FRAME));
    }

    private static VulkanContext newContext() throws Exception {
        var constructor = VulkanContext.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static ActiveRenderTargets mainTargets() {
        return new ActiveRenderTargets(
                List.of(new ActiveRenderTargets.RenderTargetView(11L, 44,
                        ActiveRenderTargets.LoadOp.LOAD, ActiveRenderTargets.StoreOp.STORE)),
                null, 4, 4);
    }

    private static DirectGlDrawState mainTargetDraw() {
        // Program handle 5 is registered nowhere: the draw's renderTargetId 0 names the
        // main target, so passage is proven by reaching the program check.
        return new DirectGlDrawState(RenderStateKey.DEFAULT, 5, PrimitiveTopology.TRIANGLES, 0);
    }
}
