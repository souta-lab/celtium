package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * F2 (QUEUE item 729): the contract of {@link VulkanTextureRegistry#announcementHash} - the pure function the
 * identical-announcement skip in {@code announceFullPixels} rests on. Identical announcements must hash
 * equal (that is the skip), and any difference the application would convert - a texel at any applied level,
 * or any geometry field - must change the hash (that is the safety). Levels past the applied depth are the
 * producer's surplus and must not change the hash, because the application never reads them.
 */
class AnimAnnouncementHashTest {

    private static int[][] sprite(int base) {
        int[][] data = new int[3][];
        data[0] = new int[16 * 16];
        data[1] = new int[8 * 8];
        data[2] = new int[4 * 4];
        for (int level = 0; level < data.length; level++) {
            for (int i = 0; i < data[level].length; i++) {
                data[level][i] = base + level * 1000 + i;
            }
        }
        return data;
    }

    @Test
    void identicalAnnouncementsHashEqual() {
        long a = VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 3);
        long b = VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 3);
        assertEquals(a, b);
        assertNotEquals(0L, a);
    }

    @Test
    void oneTexelDifferenceAtAnyAppliedLevelChangesTheHash() {
        int[][] changed = sprite(100);
        changed[0][200] ^= 0x1;
        assertNotEquals(
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 3),
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, changed, 3));
        int[][] changedDeep = sprite(100);
        changedDeep[2][3] ^= 0x80000000;
        assertNotEquals(
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 3),
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, changedDeep, 3));
    }

    @Test
    void geometryDifferenceChangesTheHash() {
        long base = VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 3);
        assertNotEquals(base, VulkanTextureRegistry.announcementHash(8, 32, 48, 16, 16, false, sprite(100), 3));
        assertNotEquals(base, VulkanTextureRegistry.announcementHash(7, 33, 48, 16, 16, false, sprite(100), 3));
        assertNotEquals(base, VulkanTextureRegistry.announcementHash(7, 32, 49, 16, 16, false, sprite(100), 3));
        assertNotEquals(base, VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, true, sprite(100), 3));
        assertNotEquals(base, VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 1));
    }

    @Test
    void levelsPastTheAppliedDepthDoNotChangeTheHash() {
        int[][] changed = sprite(100);
        changed[2][3] ^= 0x1;
        assertEquals(
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, sprite(100), 1),
                VulkanTextureRegistry.announcementHash(7, 32, 48, 16, 16, false, changed, 1));
    }

    @Test
    void shortLevelDataHashesByTheSameZeroRuleAsConversion() {
        int[][] shortData = new int[][] { new int[10] };
        int[][] zeroPadded = new int[][] { new int[16 * 16] };
        System.arraycopy(shortData[0], 0, zeroPadded[0], 0, shortData[0].length);
        assertEquals(
                VulkanTextureRegistry.announcementHash(7, 0, 0, 16, 16, false, shortData, 1),
                VulkanTextureRegistry.announcementHash(7, 0, 0, 16, 16, false, zeroPadded, 1));
    }
}
