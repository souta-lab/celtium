package org.soutalab.celtium.vulkan.hal;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.RenderTargetRegistry.TargetFormatClass;
import org.soutalab.celtium.vulkan.VulkanContext;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The borrowed-colour half of {@link FrameTargetPair} (coord ruling, lanechat #8830): a pair may name a
 * swapchain colour image lent by the present engine, but it never owns it - borrowing and releasing the
 * borrow touch no Vulkan handle, and the pair's own images are unaffected. Pinned without a device: the
 * stub host answers only what constructing a pair needs.
 */
class FrameTargetPairBorrowTest {
    private static FrameTargetPair newPair() {
        FrameTargetPair.Host host = new FrameTargetPair.Host() {
            @Override public VulkanContext context() {
                return null;
            }

            @Override public long[] allocateImage(boolean color, int width, int height,
                    TargetFormatClass colorClass) {
                throw new UnsupportedOperationException("the borrow tests allocate nothing");
            }

            @Override public void checkAlive() {
            }

            @Override public SharedDepthStore sharedDepthStore() {
                return null;
            }

            @Override public long nextPairGeneration() {
                return 1L;
            }
        };
        return new FrameTargetPair("borrow-test", host);
    }

    @Test
    void aFreshPairBorrowsNothing() {
        FrameTargetPair pair = newPair();
        assertFalse(pair.hasBorrowedColorImage());
        assertEquals(0L, pair.borrowedColorImage());
        assertEquals(-1, pair.borrowedColorIndex());
    }

    @Test
    void borrowingNamesTheLentImageAndReleaseForgetsIt() {
        FrameTargetPair pair = newPair();
        pair.borrowColorImage(0x123, 0x456, 2);
        assertTrue(pair.hasBorrowedColorImage());
        assertEquals(0x123, pair.borrowedColorImage());
        assertEquals(0x456, pair.borrowedColorView());
        assertEquals(2, pair.borrowedColorIndex());
        pair.releaseBorrowedColorImage();
        assertFalse(pair.hasBorrowedColorImage());
        assertEquals(0L, pair.borrowedColorImage());
        assertEquals(0L, pair.borrowedColorView());
        assertEquals(-1, pair.borrowedColorIndex());
    }

    @Test
    void aNewBorrowReplacesThePreviousOne() {
        FrameTargetPair pair = newPair();
        pair.borrowColorImage(0x123, 0x456, 0);
        pair.borrowColorImage(0x789, 0xABC, 1);
        assertEquals(0x789, pair.borrowedColorImage());
        assertEquals(1, pair.borrowedColorIndex());
    }

    @Test
    void borrowingANullImageIsRefused() {
        FrameTargetPair pair = newPair();
        assertThrows(IllegalArgumentException.class, () -> pair.borrowColorImage(0L, 0L, 0));
    }
}
