package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The rule that decides which owned image pair an off-target identity's draws render into, exercised without a device
 * (audit N04).
 * <p>
 * The defect it guards against is the one the audit measured: the entity silhouette (the game's own
 * {@code entityOutlineFramebuffer}, a non-zero identity) and the game's finished-frame composite (issued with no
 * framebuffer bound, identity 0) both rendered into the one owned pair, and the outline chain - whose input is that
 * pair - therefore blurred a per-frame copy of the whole scene instead of the silhouette. The rule that separates them
 * is by identity: identity 0 keeps the default pair, every other non-main identity gets the chain pair.
 * <p>
 * What this proves and what it does not: it proves the predicate {@link GllessFrameTarget#pairLabelForIdentity(long)} that the backend routes and reports
 * by, including that it is not "everything into the new pair" (which would move the composite again - the three
 * measured one-line attempts) and not "identity 0 into the chain pair" (which is the pre-change sharing in the other
 * direction). The barrier and image work each pair performs needs a device and is covered by the validation-clean
 * runs; this check does not stand in for them.
 */
class OffTargetPairRoutingTest {

    @Test
    void identityZeroKeepsTheDefaultPairAndEveryOtherIdentityGetsTheChainPair() {
        assertEquals("default-pair", GllessFrameTarget.pairLabelForIdentity(0L));
        // The entity outline's target, as the frame mixin reports it (0x15f76244 in the measured glow run), and the
        // main framebuffer's own identity, which never reaches this rule because main draws go to the frame image.
        assertEquals("chain-pair", GllessFrameTarget.pairLabelForIdentity(0x15f76244L));
        assertEquals("chain-pair", GllessFrameTarget.pairLabelForIdentity(1540653856L));
        // Negative identities cannot occur (the mixin reports System.identityHashCode), but the rule must be total:
        // anything that is not identity 0 is a game framebuffer and belongs to the chain pair.
        assertEquals("chain-pair", GllessFrameTarget.pairLabelForIdentity(-1L));
    }
}
