package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The depth-attachment record, checked without a GPU (SHADOW-NATIVE-DESIGN-2026-11-17).
 * <p>
 * GL keys depth storage by the texture: every Iris shadow framebuffer attaches the same mainDepth
 * texture, so the record must keep one forward entry per framebuffer over a shared texture, clear an
 * entry when the attachment is detached (texture 0), and ignore attachments that name no depth storage.
 * The backend keys its shared depth store from this record; a wrong entry here is a shadow depth that
 * fragments across pairs or a copy refused as unowned.
 * <p>
 * E4-c: the record is {@link FramebufferAttachmentTable}'s, the enum recognition is the capture
 * entry's, and the fbo-0 / non-depth guards are the entry's named-route shape - the monolith's
 * static map updaters this test used to drive retired with it.
 */
class DepthAttachmentRecordTest {

    private static final int GL_DEPTH_ATTACHMENT = 0x8D00;
    private static final int GL_DEPTH_STENCIL_ATTACHMENT = 0x821A;
    private static final int GL_COLOR_ATTACHMENT0 = 0x8CE0;

    @BeforeEach
    void resetTable() {
        FramebufferAttachmentTable.current().framebufferDeleted(183);
        FramebufferAttachmentTable.current().framebufferDeleted(184);
    }

    @Test
    void depthAttachmentEnumsAreRecognised() throws Exception {
        Method isDepth = DirectGlCaptureEntry.class.getDeclaredMethod("isDepthAttachment", int.class);
        isDepth.setAccessible(true);
        assertTrue((Boolean) isDepth.invoke(null, GL_DEPTH_ATTACHMENT));
        assertTrue((Boolean) isDepth.invoke(null, GL_DEPTH_STENCIL_ATTACHMENT),
                "the depth/stencil attachment's depth half is the same storage");
        assertFalse((Boolean) isDepth.invoke(null, GL_COLOR_ATTACHMENT0));
        assertFalse((Boolean) isDepth.invoke(null, 0));
    }

    @Test
    void aSharedDepthTextureKeepsOneForwardEntryPerFramebuffer() {
        FramebufferAttachmentTable table = FramebufferAttachmentTable.current();
        table.attachDepth(183, 179);
        table.attachDepth(184, 179);
        assertEquals(179, table.depthTexture(183));
        assertEquals(179, table.depthTexture(184), "the family's second framebuffer attaches the same texture");
        // Detaching one framebuffer must not drop the other's record.
        table.attachDepth(183, 0);
        assertEquals(0, table.depthTexture(183));
        assertEquals(179, table.depthTexture(184));
    }

    @Test
    void nonDepthAttachmentsAndTheDefaultFramebufferAreIgnored() {
        FramebufferAttachmentTable table = FramebufferAttachmentTable.current();
        DirectGlCaptureEntry.namedFramebufferTexture(183, GL_COLOR_ATTACHMENT0, 179, 0);
        DirectGlCaptureEntry.namedFramebufferTexture(0, GL_DEPTH_ATTACHMENT, 179, 0);
        assertEquals(0, table.depthTexture(183), "a colour attachment files no depth record");
        assertEquals(0, table.framebufferForDepthTexture(179),
                "neither the colour route nor framebuffer 0 records the texture as depth");
        table.framebufferDeleted(183);
    }
}
