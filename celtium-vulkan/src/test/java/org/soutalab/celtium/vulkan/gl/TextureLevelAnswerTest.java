package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The texture-level answer, checked without a GPU.
 * <p>
 * The transform answered a fixed 256 for every {@code glGetTexLevelParameteri} caller. That is the right number for the
 * two callers that motivated the route (the light map and Forge's cloud texture are 256 square) and the wrong one for
 * every other texture whose caller divides by it: CustomMainMenu's title screen computes a {@code MODE.FILL} cover
 * rectangle from the bound image's size, so a 1920x1080 background was asked to be 256x256. These cases pin the split
 * between "measured" and "fallback", which is the part that can be decided without a device.
 */
class TextureLevelAnswerTest {

    @Test
    void aRecordedExtentAnswersItsOwnWidthAndHeight() {
        assertEquals(1920, TextureLevelAnswer.answer(1920, 1080, TextureLevelAnswer.GL_TEXTURE_WIDTH),
                "the width the uploads recorded");
        assertEquals(1080, TextureLevelAnswer.answer(1920, 1080, TextureLevelAnswer.GL_TEXTURE_HEIGHT),
                "the height the uploads recorded");
    }

    @Test
    void theTwoSquareCallersStillGetTheirOwnNumber() {
        assertEquals(256, TextureLevelAnswer.answer(256, 256, TextureLevelAnswer.GL_TEXTURE_WIDTH),
                "the light map and the cloud texture are 256 square");
        assertEquals(256, TextureLevelAnswer.answer(256, 256, TextureLevelAnswer.GL_TEXTURE_HEIGHT));
    }

    @Test
    void aQueryThisDoesNotAnswerGetsNoExtentNumber() {
        assertEquals(0, TextureLevelAnswer.answer(1920, 1080, 0x1003),
                "GL_TEXTURE_INTERNAL_FORMAT is not a size");
        assertEquals(0, TextureLevelAnswer.answer(1920, 1080, 0),
                "and neither is an unknown name");
    }

    @Test
    void anExtentWithNoUsableDimensionIsNotAnAnswer() {
        assertEquals(0, TextureLevelAnswer.answer(0, 1080, TextureLevelAnswer.GL_TEXTURE_WIDTH),
                "zero width cannot be divided by");
        assertEquals(0, TextureLevelAnswer.answer(1920, 0, TextureLevelAnswer.GL_TEXTURE_HEIGHT));
    }

    @Test
    void anUnrecordedTextureKeepsTheFixedFallback() {
        assertEquals(256, TextureLevelAnswer.withFallback(0, TextureLevelAnswer.GL_TEXTURE_WIDTH),
                "a caller that divides must not receive zero");
        assertEquals(256, TextureLevelAnswer.withFallback(0, TextureLevelAnswer.GL_TEXTURE_HEIGHT));
    }

    @Test
    void aMeasuredAnswerIsNeverReplacedByTheFallback() {
        assertEquals(1920, TextureLevelAnswer.withFallback(1920, TextureLevelAnswer.GL_TEXTURE_WIDTH));
        assertEquals(1080, TextureLevelAnswer.withFallback(1080, TextureLevelAnswer.GL_TEXTURE_HEIGHT));
        assertEquals(1, TextureLevelAnswer.withFallback(1, TextureLevelAnswer.GL_TEXTURE_WIDTH),
                "a 1x1 texture is still its own size");
    }

    @Test
    void aNameThatIsNotASizeHasNoFallbackEither() {
        assertEquals(0, TextureLevelAnswer.withFallback(0, 0x1003),
                "the fixed answer used to invent a 256 for these as well");
        assertEquals(0, TextureLevelAnswer.withFallback(0, 0));
    }
}
