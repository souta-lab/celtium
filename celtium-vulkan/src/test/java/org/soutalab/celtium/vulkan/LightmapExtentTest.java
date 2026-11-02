package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The light map's extent comes from the pixels that will fill it, and a record of 1 is not an extent: these cases pin
 * both, because an image created at the wrong size is either recreated behind the registry entry (the entry's extent
 * then disagrees with its image) or content-less forever (a 1x1 placeholder nothing can fill).
 */
class LightmapExtentTest {
    @Test
    void theOwnersArrayIsTheWholeLevelZeroImage() {
        // 1.12.2's light map is 16x16 ARGB ints (EntityRenderer: new DynamicTexture(16, 16)).
        assertEquals(16, LightmapExtent.squareSide(new int[256]));
        assertEquals(8, LightmapExtent.squareSide(new int[64]));
        assertEquals(32, LightmapExtent.squareSide(new int[1024]), "a resized light map is still square");
        assertEquals(2, LightmapExtent.squareSide(new int[4]), "the smallest extent this class will build");
    }

    @Test
    void anArrayThatIsNotASquareIsNotAnImage() {
        assertEquals(0, LightmapExtent.squareSide(null));
        assertEquals(0, LightmapExtent.squareSide(new int[0]));
        assertEquals(0, LightmapExtent.squareSide(new int[1]), "a single texel cannot be a level-0 image here");
        assertEquals(0, LightmapExtent.squareSide(new int[12]), "a rectangle cannot be one square extent");
        assertEquals(0, LightmapExtent.squareSide(new int[255]), "one texel short of 16x16 is not 15x17 either");
        assertEquals(0, LightmapExtent.squareSide(new int[257]));
    }

    @Test
    void thePixelsOutrankTheStubsRecord() {
        // The array is the content that is about to be uploaded; the stub's record is an inference from an allocation.
        assertArrayEquals(new int[] { 16, 16 }, LightmapExtent.resolve(32, 32, 16));
        assertArrayEquals(new int[] { 16, 16 }, LightmapExtent.resolve(0, 0, 16),
                "a name the stub recorded no extent for is still sized by its pixels");
    }

    @Test
    void theStubsRecordAnswersOnlyWhenNoArrayDoes() {
        assertArrayEquals(new int[] { 32, 32 }, LightmapExtent.resolve(32, 32, 0));
        assertArrayEquals(new int[] { 0, 0 }, LightmapExtent.resolve(0, 0, 0),
                "nothing knows the size, so no image is created at all");
    }

    @Test
    void aRecordOfOneIsNoRecord() {
        // The registry's capture rule already treats a dimension of 1 as "no extent" rather than a 1-pixel texture.
        assertArrayEquals(new int[] { 0, 0 }, LightmapExtent.resolve(1, 1, 0));
        assertArrayEquals(new int[] { 0, 0 }, LightmapExtent.resolve(1, 16, 0), "a half-recorded extent is no extent");
        assertArrayEquals(new int[] { 16, 16 }, LightmapExtent.resolve(1, 1, 16),
                "and the pixels still answer when the record is the unknown sentinel");
    }
}
