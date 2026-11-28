package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.glcompat.capture.DirectGlCaptureEntry;

import static org.junit.jupiter.api.Assertions.*;

class RawFramebufferIdentityTest {
    @Test
    void reverseAttachmentLookupAddressesTheDrawTarget() throws Exception {
        var constructor = DirectGlCaptureEntry.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var installed = DirectGlCaptureEntry.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, constructor.newInstance());
        try {
            int framebuffer = 71;
            int colorTexture = 301;
            DirectGlCaptureEntry.bindFramebufferRoute(0x8D40, framebuffer);
            DirectGlCaptureEntry.framebufferTexture2DRoute(0x8D40, 0x8CE0, 0x0DE1, colorTexture, 0);

            RenderTargetRegistry targets = new RenderTargetRegistry();
            long drawn = RenderTargetRegistry.rawFramebufferIdentity(framebuffer);
            targets.noteBind(drawn, false, "raw");
            int resolvedName = FramebufferAttachmentTable.current().framebufferForColorTexture(colorTexture);
            long sampled = RenderTargetRegistry.rawFramebufferIdentity(resolvedName);
            PublishedFrameTextures published = new PublishedFrameTextures();
            published.bindColorTexture(sampled, colorTexture, 320, 177);

            assertEquals(drawn, targets.currentIdentity());
            assertEquals(targets.currentIdentity(), published.identityForColorTexture(colorTexture));
            assertEquals(RenderTargetRegistry.ImageSlot.CHAIN_PAIR, targets.slotFor(sampled));
            assertEquals(0, RenderTargetRegistry.rawFramebufferIdentity(0));
            assertNotEquals(RenderTargetRegistry.rawFramebufferIdentity(1),
                    RenderTargetRegistry.rawFramebufferIdentity(-1));
        } finally {
            installed.set(null, previous);
            // The attachment record is a global seat now, not the entry instance's.
            FramebufferAttachmentTable.current().framebufferDeleted(71);
            FramebufferBindings.current().reset();
        }
    }
}
