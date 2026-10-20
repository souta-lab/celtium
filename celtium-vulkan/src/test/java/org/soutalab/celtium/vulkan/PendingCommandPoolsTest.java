package org.soutalab.celtium.vulkan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The pending-pool bookkeeping that #403 is about, checked without a device.
 * <p>
 * The rule under test is the one {@code VUID-commandPool-00041} states: a pool may only be destroyed when none of its
 * command buffers is pending. A fence wait that timed out does not establish that, so the pool has to be retained until
 * something proves the queue is done - a signalled fence, or a device teardown where nothing can complete any more.
 * <p>
 * The device is a null handle here on purpose: {@link PendingCommandPools#retained()} and the ordering rules are what a
 * CPU test can check, and the Vulkan calls the two release paths make are the ones the game legs exercise on both GPUs.
 */
class PendingCommandPoolsTest {

    @Test
    @DisplayName("a retained pool is remembered, not dropped")
    void retainRemembers() {
        PendingCommandPools pools = new PendingCommandPools();
        assertEquals(0, pools.retained(), "a fresh owner holds nothing");
        pools.retain(0x1000L, 0x2000L);
        pools.retain(0x1001L, 0x2001L);
        assertEquals(2, pools.retained(), "a timed-out submission moves ownership here");
    }

    @Test
    @DisplayName("retention survives until something proves completion")
    void retentionIsNotImplicitRelease() {
        PendingCommandPools pools = new PendingCommandPools();
        pools.retain(0x1000L, 0x2000L);
        // Nothing here can signal a fence, which is the point: the owner keeps the pool rather than guessing.
        assertEquals(1, pools.retained(), "holding is the default; releasing needs evidence");
    }
}
