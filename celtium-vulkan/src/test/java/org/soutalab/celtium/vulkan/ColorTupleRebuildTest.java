package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a chain pair's extra colour images (slots 1.. of a multi-target framebuffer)
 * must be rebuilt for a draw, exercised without a device.
 * <p>
 * The extras are exactly slots 1..attachments-1 at the pair's own extent: a pair resize retires them with the
 * pair, so the only question this rule answers is the count. Getting it wrong in one direction leaks images on
 * every window (rebuild when nothing changed); in the other it attaches views a framebuffer was not built over
 * (keep a tuple whose count no longer matches the framebuffer's attachments).
 */
class ColorTupleRebuildTest {

    @Test
    void aSingleTargetDrawNeedsNoExtras() {
        assertFalse(GllessFrameTarget.celtium$extrasNeedRebuild(0, 1), "no extras, one target: nothing to do");
        assertTrue(GllessFrameTarget.celtium$extrasNeedRebuild(2, 1),
                "extras live but the draw is single-target now: they must be dropped");
    }

    @Test
    void theExtrasAreExactlyTheSlotsBeyondZero() {
        assertTrue(GllessFrameTarget.celtium$extrasNeedRebuild(0, 3), "a 3-target draw with no extras builds two");
        assertFalse(GllessFrameTarget.celtium$extrasNeedRebuild(2, 3), "two extras for three targets is the tuple");
        assertTrue(GllessFrameTarget.celtium$extrasNeedRebuild(1, 3), "one extra is one short");
        assertTrue(GllessFrameTarget.celtium$extrasNeedRebuild(3, 3), "three extras is one too many");
        assertFalse(GllessFrameTarget.celtium$extrasNeedRebuild(7, 8), "the full ledger: seven extras, eight targets");
    }
}
