package org.soutalab.celtium.forge.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The brightness slider's percent &lt;-&gt; gamma conversion must round-trip exactly for all 101 values.
 */
class MinecraftOptionsStorageGammaTest {

    @Test
    void everySliderPercentRoundTrips() {
        for (int percent = 0; percent <= 100; percent++) {
            assertEquals(percent,
                    MinecraftOptionsStorage.gammaToPercent(MinecraftOptionsStorage.percentToGamma(percent)),
                    "percent " + percent + " must read back as itself");
        }
    }

    @Test
    void aStoredGammaReadsBackAsItsPercent() {
        assertEquals(29, MinecraftOptionsStorage.gammaToPercent(0.29f),
                "0.29f is the stored form of 29 percent and must display as 29, not 28");
        assertEquals(0.29f, MinecraftOptionsStorage.percentToGamma(29), 0.0f);
        assertEquals(0, MinecraftOptionsStorage.gammaToPercent(0.0f));
        assertEquals(100, MinecraftOptionsStorage.gammaToPercent(1.0f));
    }
}
