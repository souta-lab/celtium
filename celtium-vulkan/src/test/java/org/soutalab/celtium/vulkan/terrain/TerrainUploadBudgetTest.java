package org.soutalab.celtium.vulkan.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertTrue(budget.tryAdmitDeferred(500), "the caller admits the head upload's actual size");
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

    @Test
    void bytePropertyWinsOverMiBProperty() {
        withBudgetProperties("262144", "64", () ->
                assertEquals(262144, TerrainUploadBudget.fromSystemProperties().bytesPerFrame()));
    }

    @Test
    void miBPropertyAppliesWhenBytePropertyIsUnset() {
        withBudgetProperties(null, "2", () ->
                assertEquals(2L * 1024 * 1024,
                        TerrainUploadBudget.fromSystemProperties().bytesPerFrame()));
    }

    @Test
    void defaultIsFourMiBWhenNeitherPropertyIsSet() {
        withBudgetProperties(null, null, () ->
                assertEquals(TerrainUploadBudget.IGPU_BYTES_PER_FRAME,
                        TerrainUploadBudget.fromSystemProperties().bytesPerFrame()));
    }

    @Test
    void nonPositiveBytePropertyIsRejected() {
        withBudgetProperties("0", null, () ->
                assertThrows(IllegalArgumentException.class,
                        TerrainUploadBudget::fromSystemProperties));
        withBudgetProperties("-1", null, () ->
                assertThrows(IllegalArgumentException.class,
                        TerrainUploadBudget::fromSystemProperties));
    }

    /** Sets (or clears, on null) both budget properties for one probe, then restores them. */
    private static void withBudgetProperties(String bytes, String mib, Runnable probe) {
        String oldBytes = System.getProperty(TerrainUploadBudget.BYTES_PROPERTY);
        String oldMib = System.getProperty(TerrainUploadBudget.MIB_PROPERTY);
        try {
            setOrClear(TerrainUploadBudget.BYTES_PROPERTY, bytes);
            setOrClear(TerrainUploadBudget.MIB_PROPERTY, mib);
            probe.run();
        } finally {
            setOrClear(TerrainUploadBudget.BYTES_PROPERTY, oldBytes);
            setOrClear(TerrainUploadBudget.MIB_PROPERTY, oldMib);
        }
    }

    private static void setOrClear(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }
}
