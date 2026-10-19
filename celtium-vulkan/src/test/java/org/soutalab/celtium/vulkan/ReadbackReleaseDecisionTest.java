package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Who owns a readback's buffer when its submission may not have completed (audit #364). A fence wait that times out
 * quarantines the command buffer; the destination it was copying into must be held for the same reason instead of being
 * freed by a finally block while the queue may still be reading it.
 */
class ReadbackReleaseDecisionTest {
    @Test
    void freesOnlyAConfirmedSubmission() {
        assertEquals("free", TextureCopyService.readbackReleaseDecision(0x1000L, 0x2000L, true));
    }

    @Test
    void quarantinesAnUnconfirmedSubmission() {
        assertEquals("quarantine", TextureCopyService.readbackReleaseDecision(0x1000L, 0x2000L, false));
        // The timed-out case is exactly this one: the fence never signalled, so nothing was confirmed.
        assertEquals("quarantine", TextureCopyService.readbackReleaseDecision(0x1000L, 0L, false));
    }

    @Test
    void nothingToDoWhenNothingWasAllocated() {
        assertEquals("nothing", TextureCopyService.readbackReleaseDecision(0L, 0L, true));
        assertEquals("nothing", TextureCopyService.readbackReleaseDecision(0L, 0L, false));
    }

    @Test
    void anUnconfirmedSubmissionIsNeverFreed() {
        for (long buffer : new long[] {0L, 1L, 0x7fff_0000_0000L}) {
            for (long memory : new long[] {0L, 1L, 0x7fff_0000_0000L}) {
                if (buffer == 0L && memory == 0L) {
                    continue;
                }
                assertEquals("quarantine", TextureCopyService.readbackReleaseDecision(buffer, memory, false),
                        "buffer=" + buffer + " memory=" + memory);
            }
        }
    }
}
