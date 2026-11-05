package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The raw-framebuffer and colour-texture identity spaces cannot meet each other or a framebuffer object's own. */
class RawTargetIdentityTest {
    @Test void defaultKeysByTheFramebuffer() {
        assertFalse(RenderTargetRegistry.RAW_TEXTURE_IDENTITY, "the switch is off unless asked for");
        assertEquals(RenderTargetRegistry.rawFramebufferIdentity(7), RenderTargetRegistry.rawTargetIdentity(7, 42));
        assertEquals(0L, RenderTargetRegistry.rawTargetIdentity(0, 42), "the default framebuffer stays identity 0");
    }

    @Test void spacesAreDisjoint() {
        int[] names = { 1, 2, 7, 42, 65535, Integer.MAX_VALUE, -1 };
        for (int a : names) {
            long framebuffer = RenderTargetRegistry.rawFramebufferIdentity(a);
            long texture = RenderTargetRegistry.rawColorTextureIdentity(a);
            assertNotEquals(framebuffer, texture);
            // A framebuffer object's identity is an int identity hash, so both raw spaces sit above 32 bits.
            assertTrue((framebuffer >>> 32) != 0 && (texture >>> 32) != 0);
            for (int b : names) {
                assertNotEquals(RenderTargetRegistry.rawFramebufferIdentity(a), RenderTargetRegistry.rawColorTextureIdentity(b));
            }
        }
    }
}
