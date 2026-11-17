package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The colour-attachment tuple record an MRT window sizes itself by: slots beyond slot 0, the leading-run count
 * rule, and the reverse lookup the publication bridge uses to declare a slot image's layout.
 * <p>
 * What this proves is the record's own arithmetic, without a device: a tuple is stored per identity, slot 0 stays
 * owned by the single-texture record, a gap ends the drawable count (the renderer cannot build a framebuffer for
 * a tuple GL would call incomplete), and forgetting a deleted texture's name clears exactly its slot.
 */
class PublishedFrameTexturesSlotsTest {

    @Test
    void aSingleTargetFramebufferHasCountOneAndNoTuple() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        assertEquals(1, record.colorAttachmentCount(42L));
        assertEquals(100, record.colorTextureAt(42L, 0));
        assertEquals(0, record.colorTextureAt(42L, 1));
        assertEquals(0, record.attachmentSlotForTexture(100));
        assertEquals(0, record.colorAttachmentCount(43L), "an unknown identity has no attachments");
    }

    @Test
    void aTupleRecordsEverySlotAndCountsTheLeadingRun() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        record.bindColorAttachments(42L, new int[] {100, 101, 102, 0, 0, 0, 0, 0});
        assertEquals(3, record.colorAttachmentCount(42L));
        assertEquals(101, record.colorTextureAt(42L, 1));
        assertEquals(102, record.colorTextureAt(42L, 2));
        assertEquals(1, record.attachmentSlotForTexture(101));
        assertEquals(2, record.attachmentSlotForTexture(102));
        assertEquals(-1, record.attachmentSlotForTexture(999), "a name no framebuffer attached fills no slot");
    }

    @Test
    void aGapEndsTheCount() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        record.bindColorAttachments(42L, new int[] {100, 0, 102, 0, 0, 0, 0, 0});
        assertEquals(1, record.colorAttachmentCount(42L),
                "slot 2 without slot 1 is not a drawable three-target framebuffer");
        assertEquals(102, record.colorTextureAt(42L, 2), "the record still says what was attached");
    }

    @Test
    void rebindingATupleReplacesItAndDetachingClearsIt() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        record.bindColorAttachments(42L, new int[] {100, 101, 102, 0, 0, 0, 0, 0});
        record.bindColorAttachments(42L, new int[] {100, 201, 0, 0, 0, 0, 0, 0});
        assertEquals(2, record.colorAttachmentCount(42L));
        assertEquals(201, record.colorTextureAt(42L, 1));
        assertEquals(-1, record.attachmentSlotForTexture(101), "the replaced name fills no slot anymore");
        assertEquals(-1, record.attachmentSlotForTexture(102));
        record.bindColorAttachments(42L, new int[] {100, 0, 0, 0, 0, 0, 0, 0});
        assertEquals(1, record.colorAttachmentCount(42L), "a tuple with nothing beyond slot 0 is no tuple");
        assertEquals(-1, record.attachmentSlotForTexture(201));
    }

    @Test
    void forgettingADeletedTextureClearsOnlyItsSlot() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        record.bindColorAttachments(42L, new int[] {100, 101, 102, 0, 0, 0, 0, 0});
        record.forgetColorTexture(101);
        assertEquals(0, record.colorTextureAt(42L, 1));
        assertEquals(102, record.colorTextureAt(42L, 2), "the other slots survive");
        assertEquals(1, record.colorAttachmentCount(42L), "the gap the deletion left ends the count");
        record.forgetColorTexture(102);
        assertEquals(1, record.colorAttachmentCount(42L));
        assertEquals(-1, record.attachmentSlotForTexture(102));
    }

    @Test
    void tuplesArePerIdentity() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindColorTexture(42L, 100, 256, 256);
        record.bindColorTexture(43L, 200, 128, 128);
        record.bindColorAttachments(42L, new int[] {100, 101, 0, 0, 0, 0, 0, 0});
        assertEquals(2, record.colorAttachmentCount(42L));
        assertEquals(1, record.colorAttachmentCount(43L));
        assertEquals(0, record.colorTextureAt(43L, 1));
    }
}
