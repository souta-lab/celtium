package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A group must own its captured state. The accept path captures into one reusable scratch array, and the model path
 * hands that array straight to the group - so a group that kept the reference was rewritten by every later capture,
 * and every pending model group drew with the last captured state instead of its own.
 */
class ImmediateGroupStateOwnershipTest {

    private static ImmediateGroup groupWith(int[] state) {
        return ImmediateGroup.obtain(null, new float[16], new float[16], VulkanImmediateFormats.ITEM, 7, 11L,
                state, 0L);
    }

    @Test
    void aLaterCaptureDoesNotRewriteASubmittedGroupsState() {
        int[] scratch = {1, 0, 1, 1, 1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 0};
        ImmediateGroup first = groupWith(scratch);
        // The next batch captures into the same scratch, as the accept path does.
        scratch[0] = 0;
        scratch[5] = 0;
        assertEquals(1, first.state[0], "the first group kept its own blend enable");
        assertEquals(1, first.state[5], "the first group kept its own cull enable");
        assertNotSame(scratch, first.state, "the group must not alias the capture scratch");
    }

    @Test
    void twoGroupsFromTheSameScratchKeepDifferentStates() {
        int[] scratch = new int[15];
        scratch[3] = 1;
        ImmediateGroup a = groupWith(scratch);
        scratch[3] = 0;
        scratch[4] = 1;
        ImmediateGroup b = groupWith(scratch);
        assertEquals(1, a.state[3], "the first group kept depth test on");
        assertEquals(0, a.state[4], "the first group kept depth write off");
        assertEquals(0, b.state[3], "the second group sees its own capture");
        assertEquals(1, b.state[4]);
    }

    @Test
    void aRecycledGroupCopiesRatherThanAliasesAndANullStateStaysNull() {
        int[] scratch = new int[15];
        scratch[0] = 1;
        ImmediateGroup group = groupWith(scratch);
        ImmediateGroup.recycle(group);
        scratch[0] = 0;
        ImmediateGroup reused = groupWith(scratch);
        assertEquals(0, reused.state[0], "the pooled group holds the new capture, not the old one");
        assertNotSame(scratch, reused.state);
        assertNull(groupWith(null).state);
    }
}
