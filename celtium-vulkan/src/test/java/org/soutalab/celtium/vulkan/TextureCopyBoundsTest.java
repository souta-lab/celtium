package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextureCopyBoundsTest {
    @Test void oneToThreePixelsAreValidDiagnosticInputs() {
        assertEquals("[ff123456]", TextureCopyService.firstPixels(new int[]{0xff123456}));
        assertEquals("[1,2]", TextureCopyService.firstPixels(new int[]{1, 2}));
        assertEquals("[1,2,3]", TextureCopyService.firstPixels(new int[]{1, 2, 3}));
        assertEquals("[]", TextureCopyService.firstPixels(new int[0]));
    }

    @Test void fourPixelPreviewStaysByteForByteCompatibleAndBounded() {
        assertEquals("[1,2,3,4]", TextureCopyService.firstPixels(new int[]{1, 2, 3, 4, 5}));
    }

    @Test void exactEdgesAndSmallCopiesFit() {
        assertTrue(TextureCopyService.rectangleFits(1279, 719, 1, 1, 1280, 720));
        assertTrue(TextureCopyService.rectangleFits(0, 0, 1280, 720, 1280, 720));
        assertTrue(TextureCopyService.rectangleFits(Integer.MAX_VALUE - 1, 0, 1, 1,
                Integer.MAX_VALUE, 1));
    }

    @Test void overflowCannotTurnAnOutOfRangeCopyIntoAValidOne() {
        assertFalse(TextureCopyService.rectangleFits(Integer.MAX_VALUE, 0, 2, 1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(0, Integer.MAX_VALUE, 1, 2, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(100, 0, Integer.MAX_VALUE, 1, 1280, 720));
    }

    @Test void emptyNegativeAndOutsideRectanglesAreRefused() {
        assertFalse(TextureCopyService.rectangleFits(-1, 0, 1, 1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(0, -1, 1, 1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(0, 0, 0, 1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(0, 0, 1, -1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(1280, 0, 1, 1, 1280, 720));
        assertFalse(TextureCopyService.rectangleFits(0, 0, 1, 1, 0, 0));
    }
}
