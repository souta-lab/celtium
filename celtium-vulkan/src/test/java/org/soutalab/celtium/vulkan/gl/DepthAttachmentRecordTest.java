package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The stub's depth-attachment record, checked without a GPU (SHADOW-NATIVE-DESIGN-2026-11-17).
 * <p>
 * GL keys depth storage by the texture: every Iris shadow framebuffer attaches the same mainDepth
 * texture, so the record must keep one forward entry per framebuffer over a shared texture, clear an
 * entry when the attachment is detached (texture 0), and ignore attachments that name no depth storage.
 * The backend keys its shared depth store from this record; a wrong entry here is a shadow depth that
 * fragments across pairs or a copy refused as unowned.
 */
class DepthAttachmentRecordTest {

    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;

    @Test
    void depthAttachmentEnumsAreRecognised() {
        assertTrue(GlsmFrontend.isDepthAttachment(GL_DEPTH_ATTACHMENT));
        assertTrue(GlsmFrontend.isDepthAttachment(GL_DEPTH_STENCIL_ATTACHMENT),
                "the depth/stencil attachment's depth half is the same storage");
        assertFalse(GlsmFrontend.isDepthAttachment(GL_COLOR_ATTACHMENT0));
        assertFalse(GlsmFrontend.isDepthAttachment(0));
    }

    @Test
    void aSharedDepthTextureKeepsOneForwardEntryPerFramebuffer() {
        var forward = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        var reverse = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        GlsmFrontend.updateDepthAttachment(forward, reverse, 183, GL_DEPTH_ATTACHMENT, 179);
        GlsmFrontend.updateDepthAttachment(forward, reverse, 184, GL_DEPTH_ATTACHMENT, 179);
        assertEquals(179, forward.get(183));
        assertEquals(179, forward.get(184), "the family's second framebuffer attaches the same texture");
        // Detaching one framebuffer must not drop the other's record.
        GlsmFrontend.updateDepthAttachment(forward, reverse, 183, GL_DEPTH_ATTACHMENT, 0);
        assertEquals(0, forward.get(183));
        assertEquals(179, forward.get(184));
    }

    @Test
    void nonDepthAttachmentsAndTheDefaultFramebufferAreIgnored() {
        var forward = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        var reverse = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        GlsmFrontend.updateDepthAttachment(forward, reverse, 183, GL_COLOR_ATTACHMENT0, 179);
        GlsmFrontend.updateDepthAttachment(forward, reverse, 0, GL_DEPTH_ATTACHMENT, 179);
        assertEquals(0, forward.size());
        assertEquals(0, reverse.size());
    }
}
