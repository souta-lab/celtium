package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The rule that decides whether a sampled colour name is an unpublished framebuffer attachment: the case
 * the sampler resolution must not mistake for an ordinary texture.
 * <p>
 * What this proves is the decision table of {@code ModPrograms.celtium$unpublishedColorAttachment} - a
 * framebuffer's colour name counts through either census record (slot 0's, or an extra slot's, which the
 * identity lookup cannot see), and a name an image was already published for is not unpublished whatever
 * records it carries. The measured failure this pins is a shadow framebuffer's second colour target
 * (shadowcolor1, texture 184 in the item 537 leg): recorded only as an extra slot, it was treated as an
 * ordinary texture, its empty mirror was returned, and every deferred program sampling it was refused
 * for having no native image.
 */
class ModProgramColorPublishTest {

    @Test
    void slotZeroRecordCounts() {
        assertTrue(ModPrograms.celtium$unpublishedColorAttachment(true, true, false));
    }

    @Test
    void extraSlotRecordCounts() {
        assertTrue(ModPrograms.celtium$unpublishedColorAttachment(true, false, true),
                "a multi-target framebuffer's second colour target carries only this record");
    }

    @Test
    void noRecordIsAnOrdinaryTexture() {
        assertFalse(ModPrograms.celtium$unpublishedColorAttachment(true, false, false));
    }

    @Test
    void aPublishedNameIsNotUnpublished() {
        assertFalse(ModPrograms.celtium$unpublishedColorAttachment(false, true, true));
        assertFalse(ModPrograms.celtium$unpublishedColorAttachment(false, false, false));
    }
}
