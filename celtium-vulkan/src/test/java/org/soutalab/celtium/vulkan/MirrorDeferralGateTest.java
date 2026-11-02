package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The registry's deferral is "no source at this content", not "no source": these cases pin the difference, because a
 * boolean deferral is exactly the state the content-less mirror was in - it existed, so nothing asked again and a late
 * upload could never reach the name.
 */
class MirrorDeferralGateTest {
    @Test
    void anUnknownNameIsAskedEveryTime() {
        MirrorDeferralGate gate = new MirrorDeferralGate();
        assertFalse(gate.isCurrent(300, 7L), "nothing has been decided about this name yet");
        assertFalse(gate.isCurrent(300, Long.MIN_VALUE), "and a name the stub has no record of is never deferred");
        assertEquals(0, gate.namesDeferred());
    }

    @Test
    void aDeferredNameIsCurrentOnlyAtTheContentItWasDeferredAt() {
        MirrorDeferralGate gate = new MirrorDeferralGate();
        gate.note(300, 7L);
        assertTrue(gate.isCurrent(300, 7L), "the same level content answers from the record");
        assertFalse(gate.isCurrent(300, 8L), "a newer write to the level is a new question");
        assertFalse(gate.isCurrent(301, 7L), "the record is per name, not per serial");
        assertFalse(gate.isCurrent(300, Long.MIN_VALUE), "a level whose record vanished is not 'unchanged'");
        assertEquals(1, gate.namesDeferred());
    }

    @Test
    void rememberingASerialOfNothingIsNotRemembering() {
        MirrorDeferralGate gate = new MirrorDeferralGate();
        gate.note(300, Long.MIN_VALUE);
        assertFalse(gate.isCurrent(300, Long.MIN_VALUE),
                "no record of the level is not evidence that a later write cannot arrive");
        assertEquals(0, gate.namesDeferred(), "and it is not counted as a deferred name");
    }

    @Test
    void forgettingReleasesTheNameForItsNextOwner() {
        MirrorDeferralGate gate = new MirrorDeferralGate();
        gate.note(300, 7L);
        gate.forget(300);
        assertFalse(gate.isCurrent(300, 7L), "a re-created GL name must not inherit the answer about other pixels");
        gate.note(300, 7L);
        assertTrue(gate.isCurrent(300, 7L));
    }

    @Test
    void askingIsPureSoASecondAskCannotDifferFromTheFirst() {
        // The batch path resolved the same bound name twice per prepared material, one statement apart: the memo asked
        // the registry, and the uncached path the memo called asked it again. Handing the first answer to the second
        // call is only sound if the question is a read - the answer about one name at one content serial cannot move
        // between two asks with no table write between them, and asking records nothing. If this gate ever records
        // while answering, the two calls are not the same question and the handoff is wrong.
        MirrorDeferralGate gate = new MirrorDeferralGate();
        gate.note(300, 7L);
        int deferred = gate.namesDeferred();
        boolean first = gate.isCurrent(300, 7L);
        boolean second = gate.isCurrent(300, 7L);
        assertTrue(first, "the name was deferred at this content");
        assertEquals(first, second, "the same question at the same content has one answer");
        assertEquals(deferred, gate.namesDeferred(), "answering must not record a deferral of its own");
        assertFalse(gate.isCurrent(300, 9L), "asking still answers about the content it was asked about");
        assertEquals(deferred, gate.namesDeferred(), "a different content is a question, not a new deferral");
    }

    @Test
    void theTableGrowsPastItsFirstFrameAndCountsNamesOnce() {
        MirrorDeferralGate gate = new MirrorDeferralGate();
        int far = 5000;
        gate.note(far, 11L);
        assertTrue(gate.isCurrent(far, 11L));
        gate.note(far, 12L);
        gate.note(far, 13L);
        assertTrue(gate.isCurrent(far, 13L));
        assertFalse(gate.isCurrent(far, 12L));
        assertEquals(1, gate.namesDeferred(), "one name, however many times its content changed");
        gate.note(2, 11L);
        assertEquals(2, gate.namesDeferred());
        assertFalse(gate.isCurrent(0, 11L), "GL name 0 is never a texture");
    }
}
