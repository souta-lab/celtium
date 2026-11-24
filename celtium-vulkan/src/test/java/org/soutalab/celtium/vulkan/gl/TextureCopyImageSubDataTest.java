package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code glCopyImageSubData} between two captured levels, asked of the stub the same way the mirror asks it.
 * <p>
 * The caller this route exists for is FoamFix's fast animation path: a frame strip texture of its own (every
 * frame side by side, uploaded through TextureUtil) and a per-tick copy of the current frame into the atlas
 * level. Before the route, the call fell to the no-op table and the atlas kept its first frame forever, so
 * these cases fix the copy's contract on the real stub: the destination level gains exactly the source
 * rectangle's captured bytes, the write moves the level's serial the way a {@code glTexSubImage2D} patch does,
 * an unchanged copy does not, and the shapes the route declines leave the destination alone.
 */
class TextureCopyImageSubDataTest {
    private static final int GL_TEXTURE_2D = 0x0DE1;
    private static final int GL_TEXTURE_3D = 0x806F;
    private static final int RGBA8 = 0x8058;
    private static final int RGBA = 0x1908;
    private static final int UNSIGNED_BYTE = 0x1401;

    private static Object install() throws Exception {
        var ctor = GlsmFrontend.class.getDeclaredConstructor();
        ctor.setAccessible(true);
        var installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        Object previous = installed.get(null);
        installed.set(null, ctor.newInstance());
        return previous;
    }

    private static void restore(Object previous) throws Exception {
        var installed = GlsmFrontend.class.getDeclaredField("installed");
        installed.setAccessible(true);
        installed.set(null, previous);
    }

    /** Uploads a {@code width}x{@code height} RGBA level whose byte at index {@code i} is {@code i % 251}. */
    private static void uploadPattern(int texture, int width, int height) {
        GlsmFrontend.celtium$glBindTexture(GL_TEXTURE_2D, texture);
        ByteBuffer pixels = MemoryUtil.memAlloc(width * height * 4);
        try {
            long address = MemoryUtil.memAddress(pixels);
            for (int i = 0; i < width * height * 4; i++) {
                MemoryUtil.memPutByte(address + i, (byte) (i % 251));
            }
            GlsmFrontend.celtium$texImage2D(GL_TEXTURE_2D, 0, RGBA8, width, height, 0, RGBA, UNSIGNED_BYTE,
                    address);
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /** Allocates a level with no data, the render-target idiom the atlas starts from. */
    private static void allocate(int texture, int width, int height) {
        GlsmFrontend.celtium$glBindTexture(GL_TEXTURE_2D, texture);
        GlsmFrontend.celtium$texImage2D(GL_TEXTURE_2D, 0, RGBA8, width, height, 0, RGBA, UNSIGNED_BYTE, 0L);
    }

    private static void copy(int src, int srcX, int dst, int dstX, int width, int height) {
        GlsmFrontend.celtium$copyImageSubData(src, GL_TEXTURE_2D, 0, srcX, 0, 0,
                dst, GL_TEXTURE_2D, 0, dstX, 0, 0, width, height, 1);
    }

    @Test
    void aCopyMovesTheSourceRectangleIntoTheDestinationLevel() throws Exception {
        Object previous = install();
        try {
            uploadPattern(600, 32, 16);
            allocate(601, 16, 16);
            long allocated = GlsmFrontend.textureUploadSerial(601, 0);
            assertFalse(GlsmFrontend.textureLevelHasGamePixels(601, 0),
                    "the destination starts as an allocation with no game pixels");

            copy(600, 16, 601, 0, 16, 16);

            assertTrue(GlsmFrontend.textureLevelHasGamePixels(601, 0),
                    "the copied rectangle is game content in the destination level");
            assertTrue(GlsmFrontend.textureUploadSerial(601, 0) > allocated,
                    "a copy is a write to the level, so a mirror that deferred on the allocation is asked again");
            ByteBuffer source = GlsmFrontend.textureUploadRgba8(600, 0, new int[2]);
            ByteBuffer dest = GlsmFrontend.textureUploadRgba8(601, 0, new int[2]);
            for (int row = 0; row < 16; row++) {
                for (int i = 0; i < 16 * 4; i++) {
                    assertEquals(source.get(row * 32 * 4 + 16 * 4 + i), dest.get(row * 16 * 4 + i),
                            "destination row " + row + " byte " + i + " is the source rectangle's byte");
                }
            }
        } finally {
            restore(previous);
        }
    }

    @Test
    void theSameCopyAgainDoesNotMoveTheDestinationSerial() throws Exception {
        Object previous = install();
        try {
            uploadPattern(600, 32, 16);
            allocate(601, 16, 16);
            copy(600, 16, 601, 0, 16, 16);
            long afterFirst = GlsmFrontend.textureUploadSerial(601, 0);

            // FoamFix copies the current frame every tick, and a frame lasts several ticks: the repeats carry
            // the same bytes, and the serial is what makes a mirror re-supply, so they must not move it.
            copy(600, 16, 601, 0, 16, 16);
            assertEquals(afterFirst, GlsmFrontend.textureUploadSerial(601, 0),
                    "an identical copy is not a write to the level's content");

            // A different frame is a different rectangle: that one is a write.
            copy(600, 0, 601, 0, 16, 16);
            assertTrue(GlsmFrontend.textureUploadSerial(601, 0) > afterFirst,
                    "a copy of different bytes moves the serial, so the mirror re-supplies");
        } finally {
            restore(previous);
        }
    }

    @Test
    void aCopyFromAnUnrecordedSourceLeavesTheDestinationAlone() throws Exception {
        Object previous = install();
        try {
            allocate(601, 16, 16);
            long allocated = GlsmFrontend.textureUploadSerial(601, 0);

            copy(999, 0, 601, 0, 16, 16);

            assertEquals(allocated, GlsmFrontend.textureUploadSerial(601, 0),
                    "nothing was captured for the source, so nothing was written");
            assertFalse(GlsmFrontend.textureLevelHasGamePixels(601, 0),
                    "and the destination did not gain game pixels from an empty source");
        } finally {
            restore(previous);
        }
    }

    @Test
    void aCopyOutsideTheSourceRecordIsRefused() throws Exception {
        Object previous = install();
        try {
            uploadPattern(600, 32, 16);
            allocate(601, 16, 16);
            long allocated = GlsmFrontend.textureUploadSerial(601, 0);

            // The rectangle starts inside the source level but runs past its captured rows.
            copy(600, 24, 601, 0, 16, 16);

            assertEquals(allocated, GlsmFrontend.textureUploadSerial(601, 0),
                    "a copy the source record cannot cover writes nothing, the way GL leaves the level alone");
            assertFalse(GlsmFrontend.textureLevelHasGamePixels(601, 0));
        } finally {
            restore(previous);
        }
    }

    @Test
    void aShapeTheRouteDoesNotServeIsRefused() throws Exception {
        Object previous = install();
        try {
            uploadPattern(600, 32, 16);
            allocate(601, 16, 16);
            long allocated = GlsmFrontend.textureUploadSerial(601, 0);

            // A depth other than 1 is a 3D copy, which a 2D capture cannot serve.
            GlsmFrontend.celtium$copyImageSubData(600, GL_TEXTURE_2D, 0, 0, 0, 0,
                    601, GL_TEXTURE_2D, 0, 0, 0, 0, 16, 16, 2);
            // A non-2D target on either side is likewise outside the route.
            GlsmFrontend.celtium$copyImageSubData(600, GL_TEXTURE_3D, 0, 0, 0, 0,
                    601, GL_TEXTURE_2D, 0, 0, 0, 0, 16, 16, 1);

            assertEquals(allocated, GlsmFrontend.textureUploadSerial(601, 0),
                    "neither refused shape wrote into the destination level");
            assertFalse(GlsmFrontend.textureLevelHasGamePixels(601, 0));
        } finally {
            restore(previous);
        }
    }
}
