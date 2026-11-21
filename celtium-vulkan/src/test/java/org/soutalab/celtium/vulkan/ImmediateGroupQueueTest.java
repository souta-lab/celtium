package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Audit A32: one pass retires its own target's groups and leaves the rest.
 * <p>
 * The off-target pass runs before the main pass and filters to a single render target; the code used to retire
 * <em>every</em> group there, which destroyed the main pass's accepted geometry before it could be drawn.
 * {@link ImmediateGroupQueue#retireGroupsForTarget} is the rule that replaced it, and a group the queue cannot
 * attribute to a target (a null entry) must be kept, never retired: it must not be the reason another pass's
 * geometry disappears.
 * <p>
 * This is the JUnit form of the queue section of {@code scripts/cpu-contract/TextureContractCheck.java}
 * (QUEUE 640); the standalone check retired when this landed.
 */
class ImmediateGroupQueueTest {

    private static ImmediateGroup group(long target) {
        return new ImmediateGroup(null, null, null, 0, 0, 0L, null, target);
    }

    @Test
    void retiringOneTargetLeavesTheOthersAndTheUnattributable() {
        long targetA = 0xAAAAL;
        long targetB = 0xBBBBL;
        List<ImmediateGroup> groups = new ArrayList<>();
        groups.add(group(targetA));
        groups.add(group(targetB));
        groups.add(group(targetA));
        groups.add(group(targetB));
        groups.add(null); // a group the queue cannot attribute must be kept, not retired

        int kept = ImmediateGroupQueue.retireGroupsForTarget(groups, targetA);
        assertEquals(3, kept, "groups kept after retiring A");
        assertEquals(3, groups.size(), "groups left in the queue");
        for (ImmediateGroup group : groups) {
            if (group != null) {
                assertEquals(targetB, group.target, "only B remains");
            }
        }
        int keptB = ImmediateGroupQueue.retireGroupsForTarget(groups, targetB);
        assertEquals(1, keptB, "groups kept after retiring B");
        assertEquals(1, groups.size(), "the unattributable group survives");
    }
}
