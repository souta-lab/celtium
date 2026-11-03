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

    /**
     * The predicate the upload sites ask before they ask the capture: "would this upload have anything to read".
     * <p>
     * It exists because the answer "no" used to be an exception per bind - 28,272 of them in one world leg, with every
     * batch still accepted - so the two states it must separate are "no record at all" (nothing to read) and "a record
     * that holds zeros" (something to read, and the zeros are the content the mirror is meant to hold).
     */
    @Test
    void nothingToReadIsTheAbsentRecordAndNotTheZeroSurface() throws Exception {
        Object previous = install();
        try {
            int texture = 501;
            assertTrue(CeltiumGlStub.celtium$levelHasNothingToRead(texture, 0),
                    "a name nothing has written has no record and no backlog, so an upload would throw");
            CeltiumGlStub.celtium$glBindTexture(0x0DE1, texture);
            CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401, 0L);
            assertFalse(CeltiumGlStub.celtium$levelHasNothingToRead(texture, 0),
                    "the render-target idiom records a zero surface, which the upload reads - not 'nothing'");
            assertFalse(CeltiumGlStub.textureLevelHasGamePixels(texture, 0),
                    "and it is still not game content, so the two questions are not the same one");
            ByteBuffer pixels = MemoryUtil.memAlloc(8 * 8 * 4);
            try {
                MemoryUtil.memSet(MemoryUtil.memAddress(pixels), 0x33, 8 * 8 * 4L);
                CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
            } finally {
                MemoryUtil.memFree(pixels);
            }
            assertFalse(CeltiumGlStub.celtium$levelHasNothingToRead(texture, 0),
                    "a real upload is content, so the upload must run");
        } finally {
            restore(previous);
        }
    }

    /**
     * A whole image re-uploaded unchanged must not move the serial, and one that changed must.
     * <p>
     * The serial is what makes a mirror re-supply, and a re-supply of identical bytes is pure cost: the shipped
     * profile pays 15.2 of them a world frame at ~9 us each. The patch path already had this rule (OPT-21); this is
     * the whole-image half, and the two states it must separate are "the same bytes again" (no serial) and "one byte
     * different" (a new serial, because the mirror's image is now stale).
     */
    @Test
    void aWholeImageUploadedAgainUnchangedDoesNotMoveTheSerial() throws Exception {
        Object previous = install();
        try {
            int texture = 502;
            CeltiumGlStub.celtium$glBindTexture(0x0DE1, texture);
            ByteBuffer pixels = MemoryUtil.memAlloc(8 * 8 * 4);
            try {
                MemoryUtil.memSet(MemoryUtil.memAddress(pixels), 0x41, 8 * 8 * 4L);
                CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
                long first = CeltiumGlStub.textureUploadSerial(texture, 0);
                assertNotEquals(Long.MIN_VALUE, first, "the first upload is content and takes a serial");

                // The same bytes again: GL's content did not change, so neither may the serial.
                CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
                assertEquals(first, CeltiumGlStub.textureUploadSerial(texture, 0),
                        "an identical whole image is not a write to the level's content");
                assertTrue(CeltiumGlStub.textureLevelHasGamePixels(texture, 0),
                        "and the record it kept is still game content");

                // One byte different: the mirror's image is stale, so the serial has to move.
                MemoryUtil.memPutByte(MemoryUtil.memAddress(pixels) + 17L, (byte) 0x42);
                CeltiumGlStub.celtium$texImage2D(0x0DE1, 0, 0x8058, 8, 8, 0, 0x1908, 0x1401,
                        MemoryUtil.memAddress(pixels));
                assertTrue(CeltiumGlStub.textureUploadSerial(texture, 0) > first,
                        "a changed whole image is a write, so the mirror must re-supply");
            } finally {
                MemoryUtil.memFree(pixels);
            }
        } finally {
            restore(previous);
        }
    }
}
