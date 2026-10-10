package org.soutalab.celtium.vulkan;

import java.util.Iterator;
import java.util.List;

/**
 * The ownership rule for the immediate batch queue: a pass retires the groups it handled and leaves the rest.
 *
 * <p>This exists as a separate, dependency-free class for two reasons. The off-target pass runs before the main pass and
 * filters to a single render target, and the code used to retire **every** group there - which destroyed the main pass's
 * accepted geometry before it could be drawn (audit A32). And the rule is small enough to be checked without a device, which
 * is what {@code scripts/cpu-contract/TextureContractCheck.java} does with two independent targets.
 */
public final class ImmediateGroupQueue {

    private ImmediateGroupQueue() {
    }

    /**
     * Removes every group belonging to {@code targetIdentity} and keeps the others in the queue.
     *
     * @return how many groups were left behind for other targets
     */
    public static int retireGroupsForTarget(List<ImmediateGroup> groups, long targetIdentity) {
        int kept = 0;
        Iterator<ImmediateGroup> iterator = groups.iterator();
        while (iterator.hasNext()) {
            ImmediateGroup group = iterator.next();
            if (group != null && group.target == targetIdentity) {
                iterator.remove();
            } else {
                // Anything the queue cannot attribute to a target is kept: a null group must never be the reason
                // another pass's geometry disappears.
                kept++;
            }
        }
        return kept;
    }
}
