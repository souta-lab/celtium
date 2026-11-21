package org.soutalab.celtium.vulkan;

import java.util.Iterator;
import java.util.List;

/**
 * The ownership rule for the immediate batch queue: a pass retires the groups it handled and leaves the rest.
 *
 * <p>This exists as a separate, dependency-free class for two reasons. The off-target pass runs before the main pass and
 * filters to a single render target, and the code used to retire **every** group there - which destroyed the main pass's
 * accepted geometry before it could be drawn (audit A32). And the rule is small enough to be checked without a device, which
 * is what {@code ImmediateGroupQueueTest} does with two independent targets.
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
        int size = groups.size();
        int write = 0;
        for (int read = 0; read < size; read++) {
            ImmediateGroup group = groups.get(read);
            if (group == null || group.target != targetIdentity) {
                // Anything the queue cannot attribute to a target is kept: a null group must never be the reason
                // another pass's geometry disappears.
                if (write != read) {
                    groups.set(write, group);
                }
                write++;
            }
        }
        if (write < size) {
            groups.subList(write, size).clear();
        }
        return write;
    }
}
