package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The serial the registry's deferral is built on, asked of the stub the same way the lookup asks it.
 * <p>
 * {@code VulkanTextureRegistry} remembers "there is no mirror source for this name" against
 * {@code CeltiumGlStub.textureUploadSerial} and only asks the capture again when that number moves. That is only sound
 * if the serial moves for every way a level can gain pixels and stays put when nothing was written - which is what
 * these cases fix, on the real stub rather than on a model of it.
 */
class TextureContentSerialTest {
    private static Object install() throws Exception {
        var ctor = CeltiumGlStub.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, ctor.newInstance());
        return previous;
    }

    private static void restore(Object previous) throws Exception {
        var installed = CeltiumGlStub.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, previous);
    }

    @Test
    void aNameNothingHasWrittenHasNoSerialAtAll() throws Exception {
        Object previous = install();
        try {
            assertNull(CeltiumGlStub.textureUploadRgba8(500, 0, new int[2]),
                    "no capture, so the lookup's question answers 'nothing' rather than an empty surface");
            assertEquals(Long.MIN_VALUE, CeltiumGlStub.textureUploadSerial(500, 0),
                    "and no serial, which the deferral treats as 'not known' rather than 'unchanged'");
            assertFalse(CeltiumGlStub.textureLevelHasGamePixels(500, 0));
        } finally {
            restore(previous);
        }
    }

    @Test
    void everyWriteToALevelMovesItsSerial() throws Exception {
        Object previous = install();
        try {
            int texture = 500;
            CeltiumGlStub.celtium$glBindTexture(0x0DE1, texture);
            // The render-target idiom: glTexImage2D with a NULL data pointer. A surface exists (zeros) and is refused
            // as content, but the level's content changed, so the record the lookup made is no longer current.
            CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 32, 16, 0, 0x1908, 0x1401, 0L);
            long allocated = CeltiumGlStub.textureUploadSerial(texture, 0);
            assertNotEquals(Long.MIN_VALUE, allocated, "an allocation is a write to the level");
            assertNotNull(CeltiumGlStub.textureUploadRgba8(texture, 0, new int[2]));
            assertFalse(CeltiumGlStub.textureLevelHasGamePixels(texture, 0),
                    "the zero surface is not game content, which is why the lookup defers rather than filling");
            assertTrue(CeltiumGlStub.textureAllocatedWithoutData(texture));
            assertEquals(32, CeltiumGlStub.textureLevelParameter(texture, 0, 0x1000));
            assertEquals(16, CeltiumGlStub.textureLevelParameter(texture, 0, 0x1001));

            // The same extent written again, this time with pixels: the serial must move or a mirror that deferred on
            // the dry allocation would never be built.
            ByteBuffer pixels = MemoryUtil.memAlloc(32 * 16 * 4);
            try {
                MemoryUtil.memSet(MemoryUtil.memAddress(pixels), 0x5A, 32 * 16 * 4L);
                CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 32, 16, 0, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
            } finally {
                MemoryUtil.memFree(pixels);
            }
            long uploaded = CeltiumGlStub.textureUploadSerial(texture, 0);
            assertTrue(uploaded > allocated, "serial " + uploaded + " did not move past the allocation " + allocated);
            assertTrue(CeltiumGlStub.textureLevelHasGamePixels(texture, 0),
                    "a real upload is content, so the next lookup fills the mirror");
            assertFalse(CeltiumGlStub.textureAllocatedWithoutData(texture),
                    "the name is no longer 'allocated with no data', so the deferral reason cannot be reused");
        } finally {
            restore(previous);
        }
    }

    @Test
    void aPatchAfterTheDeferralAlsoMovesTheSerial() throws Exception {
        Object previous = install();
        try {
            int texture = 500;
            CeltiumGlStub.celtium$glBindTexture(0x0DE1, texture);
            CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 16, 16, 0, 0x1908, 0x1401, 0L);
            long allocated = CeltiumGlStub.textureUploadSerial(texture, 0);
            // The mod path the deferral must not swallow: a late glTexSubImage2D into a level allocated without data,
            // with no TextureUtil upload hook anywhere in it.
            ByteBuffer patch = MemoryUtil.memAlloc(4 * 4 * 4);
            try {
                MemoryUtil.memSet(MemoryUtil.memAddress(patch), 0x7F, 4 * 4 * 4L);
                CeltiumGlStub.celtium$texSubImage2D(0x0DE1, 0, 0, 0, 4, 4, 0x1908, 0x1401,
                        MemoryUtil.memAddress(patch));
            } finally {
                MemoryUtil.memFree(patch);
            }
            assertTrue(CeltiumGlStub.textureUploadSerial(texture, 0) > allocated,
                    "a patch is a write to the level, so the deferred answer is asked again");
            assertTrue(CeltiumGlStub.textureLevelHasGamePixels(texture, 0),
                    "and it is game content the capture can be filled from");
        } finally {
            restore(previous);
        }
    }
}
