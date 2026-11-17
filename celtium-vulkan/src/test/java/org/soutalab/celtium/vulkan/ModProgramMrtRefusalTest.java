package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The rule that decides whether a mod program's fragment outputs fit the framebuffer it is prepared for.
 * <p>
 * A pipeline is built against one render pass, and a pass's colour attachment count is part of its identity: a
 * program writing three outputs drawn through a one-colour pipeline into a three-target framebuffer - or the
 * reverse - is an invalid pipeline, not a wrong picture. The refusal is what stands in for that invalidity, and
 * it is counted per program like every other mod-program refusal, so a leg can read which case fired.
 */
class ModProgramMrtRefusalTest {

    @Test
    void matchingCountsDraw() {
        assertNull(ModPrograms.celtium$mrtRefusal(1, 1));
        assertNull(ModPrograms.celtium$mrtRefusal(3, 3), "a gbuffer program in its own three-target framebuffer");
        assertNull(ModPrograms.celtium$mrtRefusal(8, 8), "the full ledger");
    }

    @Test
    void fewerOutputsThanTargetsDraw() {
        // A composite-style pass over a multi-target framebuffer writes the outputs it has; the pass loads the
        // rest, so their content survives - the same statement GL makes for a draw buffer the shader skips.
        assertNull(ModPrograms.celtium$mrtRefusal(1, 3));
        assertNull(ModPrograms.celtium$mrtRefusal(2, 3));
    }

    @Test
    void moreOutputsThanTargetsRefuse() {
        assertNotNull(ModPrograms.celtium$mrtRefusal(2, 1),
                "two outputs into the frame image have nowhere to write the second");
        assertNotNull(ModPrograms.celtium$mrtRefusal(3, 2));
        assertNotNull(ModPrograms.celtium$mrtRefusal(9, 8), "past the ledger is refused at any target");
    }
}
