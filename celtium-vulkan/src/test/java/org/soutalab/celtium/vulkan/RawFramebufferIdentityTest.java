package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

import static org.junit.jupiter.api.Assertions.*;

class RawFramebufferIdentityTest {
    @Test
    void reverseAttachmentLookupAddressesTheDrawTarget() throws Exception {
        var constructor = CeltiumGlStub.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        var installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, constructor.newInstance());
        try {
            int framebuffer = 71;
            int colorTexture = 301;
            CeltiumGlStub.celtium$bindFramebuffer(0x8D40, framebuffer);
            CeltiumGlStub.celtium$framebufferTexture2D(0x8D40, 0x8CE0, 0x0DE1, colorTexture, 0);

            RenderTargetRegistry targets = new RenderTargetRegistry();
            long drawn = RenderTargetRegistry.rawFramebufferIdentity(framebuffer);
            targets.noteBind(drawn, false, "raw");
            int resolvedName = CeltiumGlStub.celtium$framebufferForColorTexture(colorTexture);
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
        }
    }
}
