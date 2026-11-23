package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure-Java unit tests for the mesh limits judgements (item 652, alpha-mesh-next2).
 * The RADV fixture values are the vulkaninfo measurements of mesh-shader-652
 * section 1.1 (RX 6400 / NAVI24); the spec-minimum and below-minimum fixtures are
 * synthetic devices the feature-pair-only gate used to admit unconditionally.
 */
class MeshCapabilityTest {

    /** RX 6400 / RADV: the mesh-side values and maxTaskWorkGroupInvocations are the
     *  vulkaninfo measurements of mesh-shader-652 section 1.1. The remaining task-side
     *  values were NOT in that table; they are placeholders at or above the stage-2
     *  requirements, so this fixture proves the judgement arithmetic, not RADV's exact
     *  task limits (the first leg on the fixture will print the real ones via
     *  CELTIUM_MESH_PROPERTIES). */
    private static MeshCapability radvFixture() {
        return new MeshCapability(
                4194304,   // maxTaskWorkGroupTotalCount (placeholder, >= stage-2 need)
                65535,     // maxTaskWorkGroupCount[0] (placeholder)
                1024,      // maxTaskWorkGroupInvocations (measured)
                1024,      // maxTaskWorkGroupSize[0] (placeholder)
                16384,     // maxTaskPayloadSize (placeholder)
                4194304,   // maxMeshWorkGroupTotalCount (measured)
                65535,     // maxMeshWorkGroupCount[0] (placeholder)
                256,       // maxMeshWorkGroupInvocations (measured)
                256,       // maxMeshOutputVertices (measured)
                256);      // maxMeshOutputPrimitives (measured)
    }

    @Test
    void absentCapabilityAdmitsNothing() {
        assertFalse(MeshCapability.ABSENT.queried());
        assertFalse(MeshCapability.ABSENT.stage1Capable());
        assertFalse(MeshCapability.ABSENT.stage2Capable());
        assertFalse(MeshCapability.ABSENT.stage1DispatchFits(1L));
        assertFalse(MeshCapability.ABSENT.stage2TaskGroupsFit(1));
    }

    @Test
    void radvFixtureAdmitsBothStages() {
        MeshCapability cap = radvFixture();
        assertTrue(cap.queried());
        assertTrue(cap.stage1Capable());
        assertTrue(cap.stage2Capable());
    }

    @Test
    void smallMeshOutputRejectsStage2ButAdmitsStage1() {
        // A device whose mesh workgroup may emit only 128 vertices: stage 2's compiled
        // 256-vertex output cannot run, stage 1's 6-vertex output still can. The old
        // feature-pair gate admitted both.
        MeshCapability cap = new MeshCapability(
                4194304, 65535, 1024, 1024, 16384, 4194304, 65535, 256, 128, 256);
        assertFalse(cap.stage2Capable());
        assertTrue(cap.stage1Capable());
    }

    @Test
    void smallTaskPayloadRejectsStage2() {
        // 512-byte payload ceiling vs the 644-byte CeltiumSectionTaskPayload.
        MeshCapability cap = new MeshCapability(
                4194304, 65535, 1024, 1024, 512, 4194304, 65535, 256, 256, 256);
        assertFalse(cap.stage2Capable());
    }

    @Test
    void stage2TaskGroupsFitUsesBothTaskLimits() {
        MeshCapability cap = radvFixture();
        assertTrue(cap.stage2TaskGroupsFit(65535));
        // Above the per-dimension x cap even though below the total cap.
        assertFalse(cap.stage2TaskGroupsFit(65536));
        assertFalse(cap.stage2TaskGroupsFit(0));
        MeshCapability smallTotal = new MeshCapability(
                100, 65535, 1024, 1024, 16384, 4194304, 65535, 256, 256, 256);
        assertTrue(smallTotal.stage2TaskGroupsFit(100));
        assertFalse(smallTotal.stage2TaskGroupsFit(101));
    }

    @Test
    void stage1DispatchFitsUsesBothMeshLimits() {
        MeshCapability cap = radvFixture();
        assertTrue(cap.stage1DispatchFits(65535L));
        // Above the per-dimension x cap even though below the total cap.
        assertFalse(cap.stage1DispatchFits(65536L));
        assertFalse(cap.stage1DispatchFits(0L));
        MeshCapability smallTotal = new MeshCapability(
                4194304, 65535, 1024, 1024, 16384, 1000, 65535, 256, 256, 256);
        assertTrue(smallTotal.stage1DispatchFits(1000L));
        assertFalse(smallTotal.stage1DispatchFits(1001L));
    }
}
