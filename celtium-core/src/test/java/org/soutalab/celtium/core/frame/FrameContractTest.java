package org.soutalab.celtium.core.frame;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** The frame-side value contracts: frustum shape, target validation, frame context ranges. */
class FrameContractTest {

    @Test
    void frustumRequiresExactlyTwentyFourCoefficients() {
        assertThrows(IllegalArgumentException.class, () -> FrustumPlanes.of(new float[23]));
        float[] planes = new float[FrustumPlanes.FLOAT_COUNT];
        planes[0] = 1.0f;
        FrustumPlanes frustum = FrustumPlanes.of(planes);
        planes[0] = 9.0f;
        assertEquals(1.0f, frustum.coefficient(FrustumPlanes.LEFT, 0), 0.0f);
        assertEquals(3.0f, frustum.signedDistance(FrustumPlanes.LEFT, 3.0, 0.0, 0.0), 0.0f);
        assertThrows(IndexOutOfBoundsException.class, () -> frustum.coefficient(6, 0));
    }

    @Test
    void activeRenderTargetsValidateAreaAndCopyLists() {
        ActiveRenderTargets.RenderTargetView view = new ActiveRenderTargets.RenderTargetView(
                11L, 37, ActiveRenderTargets.LoadOp.CLEAR, ActiveRenderTargets.StoreOp.STORE);
        ActiveRenderTargets targets = new ActiveRenderTargets(List.of(view), null, 1920, 1080);
        assertEquals(1, targets.colorAttachments().size());
        assertThrows(UnsupportedOperationException.class, () -> targets.colorAttachments().add(view));
        assertThrows(IllegalArgumentException.class,
                () -> new ActiveRenderTargets(List.of(), null, 0, 1080));
    }

    @Test
    void frameContextRejectsOutOfRangeValues() {
        FrameContext ctx = new FrameContext(12L, 400, 0.5f);
        assertEquals(12L, ctx.frameId());
        assertThrows(IllegalArgumentException.class, () -> new FrameContext(0L, 0, 1.0f));
        assertThrows(IllegalArgumentException.class, () -> new FrameContext(-1L, 0, 0.0f));
        assertTrue(new CameraPose(1.0, 2.0, 3.0, 90f, -10f).yawDegrees() == 90f);
    }
}
