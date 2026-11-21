package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The swapchain creation escalation, pinned without a surface.
 * <p>
 * The backend's {@code ensureSwapchain()} refuses fail-closed with reasons, and this latch is the only
 * consumer that can turn a permanently refusing surface into the crash report the window-renderer path
 * already produces, instead of an endless black window. What a unit test can pin is the counting rule
 * itself: only refused creations count, the count is consecutive rather than cumulative, the reason the
 * report will name follows the latest refusal, and the limit is the constant the design fixed. The wiring
 * (a record at the {@code create(...) == null} branch, a reset at its success, the mixin's poll outside
 * the swap point's swallowing catch) is the backend's and the mixin's, and is not reachable from here.
 */
class SwapchainCreationEscalationTest {

    @Test
    void belowTheLimitNothingIsDue() {
        SwapchainCreationEscalation escalation = new SwapchainCreationEscalation();
        for (int i = 0; i < SwapchainCreationEscalation.FAILURE_LIMIT - 1; i++) {
            escalation.recordRefusal("refusal " + i);
        }
        assertEquals(SwapchainCreationEscalation.FAILURE_LIMIT - 1, escalation.consecutiveRefusals());
        assertFalse(escalation.escalationDue());
    }

    @Test
    void theLimitItselfIsDue() {
        SwapchainCreationEscalation escalation = new SwapchainCreationEscalation();
        for (int i = 0; i < SwapchainCreationEscalation.FAILURE_LIMIT; i++) {
            escalation.recordRefusal("refusal " + i);
        }
        assertEquals(SwapchainCreationEscalation.FAILURE_LIMIT, escalation.consecutiveRefusals());
        assertTrue(escalation.escalationDue());
    }

    @Test
    void aSuccessResetsTheRunSoTheCountIsConsecutiveNotCumulative() {
        SwapchainCreationEscalation escalation = new SwapchainCreationEscalation();
        // One short of the limit, then a creation lands, then the same run again: cumulatively that is
        // nearly twice the limit, and it must still not be due - only the unbroken run counts.
        for (int i = 0; i < SwapchainCreationEscalation.FAILURE_LIMIT - 1; i++) {
            escalation.recordRefusal("first run " + i);
        }
        escalation.recordSuccess();
        assertEquals(0, escalation.consecutiveRefusals());
        for (int i = 0; i < SwapchainCreationEscalation.FAILURE_LIMIT - 1; i++) {
            escalation.recordRefusal("second run " + i);
        }
        assertFalse(escalation.escalationDue());
        escalation.recordRefusal("the one that completes the second run");
        assertTrue(escalation.escalationDue());
    }

    @Test
    void theNamedReasonFollowsTheLatestRefusal() {
        SwapchainCreationEscalation escalation = new SwapchainCreationEscalation();
        escalation.recordRefusal("the surface was refused");
        assertEquals("the surface was refused", escalation.lastRefusal());
        escalation.recordRefusal("vkCreateSwapchainKHR returned -1000000000");
        assertEquals("vkCreateSwapchainKHR returned -1000000000", escalation.lastRefusal());
    }

    @Test
    void theLimitIsOneHundredTwenty() {
        // The number is the design's conversion of the renderer path's bounded retry window into
        // unthrottled per-present attempts; a silent retune would change when a black window becomes a
        // crash report, so the constant itself is pinned rather than read.
        assertEquals(120, SwapchainCreationEscalation.FAILURE_LIMIT);
    }
}
