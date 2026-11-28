package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the frame template machinery of plan section 2.2 as seated on
 * {@link FrameCoordinator} by S3-b: the O(1) template pick (a pack frame goes to the
 * isolated executor, everything else runs the fixed four stages; a mod FBO request
 * alone never switches the template) and the per-frame stage record the legs and
 * instruments read.
 */
class FrameTemplateContractTest {

    @Test
    void templatePickFollowsThePlan() {
        assertEquals(FrameCoordinator.FrameTemplate.PACK_EXECUTOR,
                FrameCoordinator.selectTemplate(true, false));
        assertEquals(FrameCoordinator.FrameTemplate.PACK_EXECUTOR,
                FrameCoordinator.selectTemplate(true, true));
        assertEquals(FrameCoordinator.FrameTemplate.FIXED_FOUR_STAGE,
                FrameCoordinator.selectTemplate(false, false));
        // A mod custom FBO is absorbed inside the fixed template's main stage.
        assertEquals(FrameCoordinator.FrameTemplate.FIXED_FOUR_STAGE,
                FrameCoordinator.selectTemplate(false, true));
    }

    @Test
    void stageRecordIsPerFrame() {
        FrameCoordinator coordinator = new FrameCoordinator(null, null);
        coordinator.pickFrameTemplate(false, true);
        assertEquals(FrameCoordinator.FrameTemplate.FIXED_FOUR_STAGE, coordinator.frameTemplate());
        assertTrue(coordinator.frameModFboRequested());
        for (FrameCoordinator.Stage stage : FrameCoordinator.Stage.values()) {
            assertFalse(coordinator.stageEntered(stage), "no stage entered yet: " + stage);
        }
        coordinator.markStage(FrameCoordinator.Stage.UPLOAD);
        coordinator.markStage(FrameCoordinator.Stage.MAIN_RENDER);
        coordinator.markStage(FrameCoordinator.Stage.MAIN_RENDER);
        assertTrue(coordinator.stageEntered(FrameCoordinator.Stage.UPLOAD));
        assertTrue(coordinator.stageEntered(FrameCoordinator.Stage.MAIN_RENDER));
        assertFalse(coordinator.stageEntered(FrameCoordinator.Stage.HIZ_BUILD));
        assertEquals("template=FIXED_FOUR_STAGE UPLOAD MAIN_RENDER", coordinator.stageSummary());

        // The next frame's record starts clean, with its own template pick.
        coordinator.pickFrameTemplate(true, false);
        assertEquals(FrameCoordinator.FrameTemplate.PACK_EXECUTOR, coordinator.frameTemplate());
        assertFalse(coordinator.frameModFboRequested());
        assertFalse(coordinator.stageEntered(FrameCoordinator.Stage.UPLOAD));
    }
}
