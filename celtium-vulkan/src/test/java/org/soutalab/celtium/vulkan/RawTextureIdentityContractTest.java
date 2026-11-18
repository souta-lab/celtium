package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Item 365 M-B: the C7 premise and its fix as a CPU contract, no GPU and no leg (ITEM365-AUDIT
 * section 6). One framebuffer (Xaero's atlas framebuffer, per the M-A static recheck) has its
 * colour attachment swapped between two atlas textures on atlas rollover.
 * <ul>
 *   <li>Switch off (the default): both textures' draws land on the framebuffer identity, and
 *   binding the second texture evicts the first texture's reverse lookup - the documented
 *   collision, pinned here as the expectation it is, not as desired behaviour.</li>
 *   <li>Switch on: each texture owns its identity and both names stay resolvable. That arm runs
 *   in a forked JVM ({@link RawTextureIdentityForkMain}) because
 *   {@link RenderTargetRegistry#RAW_TEXTURE_IDENTITY} is a static final read at class init.</li>
 * </ul>
 * Whether the fix is sufficient in a real leg (the section 4.2 re-latch question M-A raised) is
 * M-C's question, not this test's; this test pins only the census-level contract both arms make.
 */
class RawTextureIdentityContractTest {

    @Test
    void switchOffCollapsesBothAtlasTexturesOntoTheFramebufferIdentity() {
        assertFalse(RenderTargetRegistry.RAW_TEXTURE_IDENTITY,
                "the test JVM must not set -Dceltium.vulkan.rawFboTextureIdentity; the on arm is forked");
        int framebuffer = RawTextureIdentityForkMain.FRAMEBUFFER;
        int atlasA = RawTextureIdentityForkMain.ATLAS_A;
        int atlasB = RawTextureIdentityForkMain.ATLAS_B;
        int extent = RawTextureIdentityForkMain.EXTENT;
        long identity = RenderTargetRegistry.rawFramebufferIdentity(framebuffer);
        assertEquals(identity, RenderTargetRegistry.rawTargetIdentity(framebuffer, atlasA));
        assertEquals(identity, RenderTargetRegistry.rawTargetIdentity(framebuffer, atlasB));

        PublishedFrameTextures published = new PublishedFrameTextures();
        published.bindColorTexture(identity, atlasA, extent, extent);
        assertEquals(identity, published.identityForColorTexture(atlasA));
        published.bindColorTexture(identity, atlasB, extent, extent);
        assertEquals(Long.MIN_VALUE, published.identityForColorTexture(atlasA),
                "switch off: the older atlas name is evicted by the swap (the C7 collision)");
        assertEquals(identity, published.identityForColorTexture(atlasB));
        assertEquals(atlasB, published.colorTextureName(identity));
    }

    @Test
    void switchOnKeepsBothAtlasNamesResolvable() throws Exception {
        String java = System.getProperty("java.home") + "/bin/java";
        Process process = new ProcessBuilder(java,
                "-Dceltium.vulkan.rawFboTextureIdentity=true",
                "-cp", System.getProperty("java.class.path"),
                "org.soutalab.celtium.vulkan.RawTextureIdentityForkMain")
                .redirectErrorStream(true)
                .start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }
        assertTrue(process.waitFor(60, TimeUnit.SECONDS), "forked switch-on arm timed out");
        assertEquals(0, process.exitValue(), "forked switch-on arm failed: " + output);
        assertTrue(output.toString().contains("CELTIUM_RAW_TEXTURE_IDENTITY_FORK PASS"),
                "forked switch-on arm did not report PASS: " + output);
    }
}
