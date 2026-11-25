package org.soutalab.celtium.vulkan.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 4 MiB/frame upload budget: bursts defer whole uploads, never split them. */
class TerrainUploadBudgetTest {

    @Test
    void admissionStopsAtTheFrameCap() {
        TerrainUploadBudget budget = new TerrainUploadBudget(1000);
        assertTrue(budget.tryAdmit(600));
        assertTrue(budget.tryAdmit(400));
        assertFalse(budget.tryAdmit(1));
        assertEquals(0, budget.remainingThisFrame());
        assertEquals(1000, budget.admittedThisFrame());
        assertEquals(1, budget.pendingBytes());
        assertEquals(1, budget.pendingUploads());
    }

    @Test
    void deferredUploadsDrainAcrossFrames() {
        TerrainUploadBudget budget = new TerrainUploadBudget(1000);
        assertFalse(budget.tryAdmit(2500));
        assertEquals(2500, budget.pendingBytes());
        assertEquals(3, budget.framesToDrain());
        budget.beginFrame();
        assertTrue(budget.tryAdmitDeferred(1000));
        assertEquals(1500, budget.pendingBytes());
        budget.beginFrame();
        assertTrue(budget.tryAdmitDeferred(1000));
        budget.beginFrame();
        assertFalse(budget.tryAdmitDeferred(1000), "only 500 of the backlog remains");
        assertTrue(budget.tryAdmitDeferred(500));
        assertEquals(0, budget.pendingBytes());
        assertEquals(0, budget.framesToDrain());
    }

    @Test
    void aFailedDeferredAdmitDoesNotDoubleCount() {
        TerrainUploadBudget budget = new TerrainUploadBudget(100);
        assertFalse(budget.tryAdmit(150));
        assertEquals(150, budget.pendingBytes());
        budget.beginFrame();
        assertTrue(budget.tryAdmit(100));
        assertFalse(budget.tryAdmitDeferred(150));
        assertEquals(150, budget.pendingBytes(), "the backlog must still hold the upload exactly once");
        assertEquals(1, budget.pendingUploads());
    }
}
