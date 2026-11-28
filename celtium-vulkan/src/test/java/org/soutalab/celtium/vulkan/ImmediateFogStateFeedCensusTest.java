package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The R-105 fog feed census contract: a fresh owner counts nothing, the mixin's feed notes and the
 * backend's latch notes advance only their own counters, the adapter path ({@code enabled(...)}) is not
 * a feed, and the first fed disable keeps its caller stack exactly once. The census is observation
 * only - none of these notes may move the fog state itself.
 */
class ImmediateFogStateFeedCensusTest {

    private static ImmediateFogState fresh() {
        return new ImmediateFogState(NativeRasterState.current());
    }

    @Test
    void freshStateCountsNothing() {
        ImmediateFogState state = fresh();
        assertEquals(0, state.feedEnableCount());
        assertEquals(0, state.feedDisableCount());
        assertEquals(0, state.latchEnabledCount());
        assertEquals(0, state.latchDisabledCount());
        assertNull(state.firstDisableCaller());
    }

    @Test
    void feedNotesAdvanceOnlyTheFeedCounters() {
        ImmediateFogState state = fresh();
        state.noteFeedEnable();
        state.noteFeedEnable();
        state.noteFeedDisable();
        assertEquals(2, state.feedEnableCount());
        assertEquals(1, state.feedDisableCount());
        assertEquals(0, state.latchEnabledCount());
        assertEquals(0, state.latchDisabledCount());
    }

    @Test
    void latchNotesAdvanceOnlyTheLatchCounters() {
        ImmediateFogState state = fresh();
        state.noteLatchRead(true);
        state.noteLatchRead(true);
        state.noteLatchRead(false);
        assertEquals(2, state.latchEnabledCount());
        assertEquals(1, state.latchDisabledCount());
        assertEquals(0, state.feedEnableCount());
        assertEquals(0, state.feedDisableCount());
    }

    @Test
    void feedAndLatchNotesDoNotMoveTheFogState() {
        ImmediateFogState state = fresh();
        state.noteFeedEnable();
        state.noteLatchRead(true);
        assertTrue(!state.enabled(), "a feed note is not an enable: the state stays as it was");
    }

    @Test
    void adapterWritesAreNotFeeds() {
        ImmediateFogState state = fresh();
        // Same value as the fresh default, so no boundary flush is involved: the point is only that
        // the setter the adapter path uses never touches the feed census.
        state.enabled(false);
        assertEquals(0, state.feedEnableCount());
        assertEquals(0, state.feedDisableCount());
    }

    @Test
    void firstDisableKeepsItsCallerStackOnce() {
        ImmediateFogState state = fresh();
        state.noteFeedDisable();
        String first = state.firstDisableCaller();
        assertNotNull(first);
        assertTrue(first.contains("ImmediateFogStateFeedCensusTest"),
                "the first disable's caller names itself: " + first);
        state.noteFeedDisable();
        assertEquals(first, state.firstDisableCaller(), "the stack is kept once, not overwritten");
    }

    @Test
    void stateIdIsTheIdentityHash() {
        ImmediateFogState state = fresh();
        assertEquals(System.identityHashCode(state), state.stateId());
    }
}
