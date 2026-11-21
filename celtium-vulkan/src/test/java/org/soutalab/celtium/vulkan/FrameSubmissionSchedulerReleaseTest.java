package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A throwing {@code release()} must not corrupt the retirement ledger: every retirement is released
 * exactly once, the ones after the failure are not stranded behind it, and a later reset or
 * {@code releaseAll} never releases the same resource twice (a double destroy of a Vulkan handle).
 */
class FrameSubmissionSchedulerReleaseTest {

    private static final class Counting implements FrameSubmissionScheduler.Retirable {
        int releases;
        RuntimeException failure;

        @Override
        public void release() {
            releases++;
            if (failure != null) {
                throw failure;
            }
        }
    }

    @Test
    void throwingReleaseNeitherStrandsTheRestNorDoubleReleases() {
        FrameSubmissionScheduler scheduler = new FrameSubmissionScheduler();
        Counting first = new Counting();
        Counting failing = new Counting();
        failing.failure = new IllegalStateException("boom");
        Counting last = new Counting();
        scheduler.retireToSlot(0, first, "first");
        scheduler.retireToSlot(0, failing, "failing");
        scheduler.retireToSlot(0, last, "last");

        IllegalStateException thrown =
                assertThrows(IllegalStateException.class, () -> scheduler.releaseOnSlotReset(0));

        assertSame(failing.failure, thrown);
        assertEquals(1, first.releases);
        assertEquals(1, failing.releases);
        assertEquals(1, last.releases);
        assertEquals(0, scheduler.pendingRetirements(0));
        // A second reset releases nothing again.
        assertEquals(0, scheduler.releaseOnSlotReset(0));
        assertEquals(1, first.releases);
        assertEquals(1, last.releases);
    }

    @Test
    void releaseAllContinuesPastAFailingSlot() {
        FrameSubmissionScheduler scheduler = new FrameSubmissionScheduler();
        Counting failing = new Counting();
        failing.failure = new IllegalStateException("boom");
        Counting other = new Counting();
        scheduler.retireToSlot(0, failing, "failing");
        scheduler.retireToSlot(1, other, "other");

        assertThrows(IllegalStateException.class, scheduler::releaseAll);

        assertEquals(1, failing.releases);
        assertEquals(1, other.releases);
        assertTrue(scheduler.pendingRetirements().isEmpty());
    }

    @Test
    void happyPathReleasesEverythingExactlyOnce() {
        FrameSubmissionScheduler scheduler = new FrameSubmissionScheduler();
        Counting a = new Counting();
        Counting b = new Counting();
        scheduler.retireToSlot(2, a, "a");
        scheduler.retireToSlot(2, b, "b");

        assertEquals(2, scheduler.releaseOnSlotReset(2));
        assertEquals(1, a.releases);
        assertEquals(1, b.releases);
    }
}
