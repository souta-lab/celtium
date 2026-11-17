package org.embeddedt.embeddium.impl.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The safe-config migration (item 327, Vulcanizator V-02): a stale value the current renderer mishandles is
 * moved to its safe value at load and the move is described, so the load log can name it. Values the user
 * chose deliberately are not the migration's business.
 */
class SodiumGameOptionsMigrationTest {

    @Test
    void theDefaultsMigrateNothing() {
        SodiumGameOptions config = new SodiumGameOptions();

        List<String> applied = SodiumGameOptions.migrateUnsafeValues(config);

        assertTrue(applied.isEmpty(), "a fresh config must not be 'migrated': " + applied);
        assertEquals(0, config.quality.chunkFadeInDuration);
    }

    @Test
    void aStaleNonZeroFadeDurationIsMovedToZeroAndDescribed() {
        SodiumGameOptions config = new SodiumGameOptions();
        config.quality.chunkFadeInDuration = 5;

        List<String> applied = SodiumGameOptions.migrateUnsafeValues(config);

        assertEquals(0, config.quality.chunkFadeInDuration,
                "BH-33: under the Vulkan renderer the value would be reported active while doing nothing");
        assertEquals(1, applied.size(), "exactly one migration is described: " + applied);
        assertTrue(applied.get(0).contains("chunk_fade_in_duration"), applied.get(0));
        assertTrue(applied.get(0).contains("5 -> 0"), "the description names the move: " + applied.get(0));
    }

    @Test
    void deliberateValuesElsewhereArePreserved() {
        SodiumGameOptions config = new SodiumGameOptions();
        config.quality.chunkFadeInDuration = 5;
        config.quality.legacyBiomeBlendRadius = 3;
        config.quality.enableVignette = false;

        SodiumGameOptions.migrateUnsafeValues(config);

        assertEquals(3, config.quality.legacyBiomeBlendRadius,
                "the migration touches only the value its rule names");
        assertFalse(config.quality.enableVignette);
    }
}
