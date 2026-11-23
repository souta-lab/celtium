package org.embeddedt.embeddium.impl.render.chunk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the stream-spread token bucket's carry-over semantics (QUEUE 728, F1): a burst spends banked credit, a
 * heavy slice creates debt that refills must repay before new credit accrues, and long idleness restores at most
 * the capacity - never more, however long the pause.
 */
class StreamSpreadBudgetTest {
    private static final long T0 = 1_000_000_000L;
    private static final long WINDOW = StreamSpreadBudget.REFILL_WINDOW_NANOS;

    @Test
    void startsFullAtTwiceTheBudget() {
        StreamSpreadBudget budget = new StreamSpreadBudget(1200, T0);
        assertEquals(2400, budget.tokensMicros());
        assertEquals(2400, budget.capacityMicros());
        assertTrue(budget.hasCredit());
    }

    @Test
    void rejectsANonPositiveBudget() {
        assertThrows(IllegalArgumentException.class, () -> new StreamSpreadBudget(0, T0));
    }

    @Test
    void refillEarnsOneBudgetPerWindow() {
        StreamSpreadBudget budget = new StreamSpreadBudget(1200, T0);
        budget.debit(2400);
        assertEquals(0, budget.tokensMicros());
        budget.refill(T0 + WINDOW);
        assertEquals(1200, budget.tokensMicros());
    }

    @Test
    void refillNeverExceedsCapacity() {
        StreamSpreadBudget budget = new StreamSpreadBudget(1200, T0);
        budget.refill(T0 + WINDOW * 10L);
        assertEquals(2400, budget.tokensMicros());
    }

    @Test
    void aHeavySliceCreatesDebtThatRefillRepaysFirst() {
        StreamSpreadBudget budget = new StreamSpreadBudget(1200, T0);
        budget.debit(5000);
        assertEquals(-2600, budget.tokensMicros());
        assertFalse(budget.hasCredit());
        budget.refill(T0 + WINDOW);
        assertEquals(-1400, budget.tokensMicros());
        budget.refill(T0 + WINDOW * 3L);
        assertEquals(1000, budget.tokensMicros());
        assertTrue(budget.hasCredit());
    }

    @Test
    void aBackwardsOrStillClockEarnsNothing() {
        StreamSpreadBudget budget = new StreamSpreadBudget(1200, T0);
        budget.debit(2400);
        budget.refill(T0);
        assertEquals(0, budget.tokensMicros());
        budget.refill(T0 - WINDOW);
        assertEquals(0, budget.tokensMicros());
    }
}
