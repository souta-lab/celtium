package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The publication record's depth half, checked without a GPU (SHADOW-NATIVE-DESIGN-2026-11-17).
 * <p>
 * The window head asks identity → depth texture (which shared depth image the pair borrows), the
 * sampling and copy paths ask texture → identity. A framebuffer family sharing one depth texture
 * (Iris's shadow targets over one mainDepth) keeps one forward entry per identity; the reverse names
 * one identity, which is enough — every family member resolves to the same shared image.
 */
class DepthTextureRecordTest {

    @Test
    void depthRecordsRoundTripAndClear() {
        PublishedFrameTextures record = new PublishedFrameTextures();
        record.bindDepthTexture(0x1000_0000_00B7L, 179);
        record.bindDepthTexture(0x1000_0000_00B8L, 179);
        assertEquals(179, record.depthTexture(0x1000_0000_00B7L));
        assertEquals(179, record.depthTexture(0x1000_0000_00B8L),
                "the family's second identity attaches the same depth texture");
        long owner = record.identityForDepthTexture(179);
        assertTrue(owner == 0x1000_0000_00B7L || owner == 0x1000_0000_00B8L,
                "the reverse names one of the family's identities");
        record.bindDepthTexture(0x1000_0000_00B7L, 0);
        assertEquals(0, record.depthTexture(0x1000_0000_00B7L), "a zero name clears the entry");
        assertEquals(179, record.depthTexture(0x1000_0000_00B8L),
                "clearing one identity leaves the family's other entry standing");
        record.forgetDepthTexture(179);
        assertEquals(0, record.depthTexture(0x1000_0000_00B8L));
        assertEquals(Long.MIN_VALUE, record.identityForDepthTexture(179));
        assertEquals(Long.MIN_VALUE, record.identityForDepthTexture(0));
    }
}
