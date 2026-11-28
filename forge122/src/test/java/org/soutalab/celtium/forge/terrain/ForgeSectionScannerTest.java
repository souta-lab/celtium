package org.soutalab.celtium.forge.terrain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The liquid tint decision of {@link ForgeSectionScanner}: a {@code BlockColors} answer
 * is a liquid's tint as-is, and -1 (no handler registered for the block, or the handler
 * declining) is {@link WorldSectionSnapshot#NO_TINT} - never the grass colour the
 * model-tinted path falls back to.
 */
class ForgeSectionScannerTest {

    @Test
    void liquidTintKeepsBiomeWaterColour() {
        // The swamp water colour, as the water handler's 3x3 biome average answers it.
        assertEquals(0x617B64, ForgeSectionScanner.liquidTintFromColor(0x617B64));
    }

    @Test
    void liquidTintKeepsPlainsWhite() {
        // The default water colour is white, so plains water resolves to white: the
        // multiply changes nothing there, as before this fix.
        assertEquals(0xFFFFFF, ForgeSectionScanner.liquidTintFromColor(0xFFFFFF));
    }

    @Test
    void liquidTintWithoutHandlerAnswerIsNoTint() {
        assertEquals(WorldSectionSnapshot.NO_TINT, ForgeSectionScanner.liquidTintFromColor(-1));
    }
}
