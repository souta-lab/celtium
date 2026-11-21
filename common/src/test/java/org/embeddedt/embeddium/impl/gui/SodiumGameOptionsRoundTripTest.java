package org.embeddedt.embeddium.impl.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fast block renderer toggle must survive a save/load round-trip under its config key.
 *
 * <p>The value used to live only in a static field seeded from
 * {@code -Dceleritas.useVintageFastBlockRenderer}, so the persisted form did not exist: these
 * tests pin the key name the production Gson shape writes for
 * {@code performance.useFastBlockRenderer} and that the same shape reads it back, using the
 * production parser's shape exactly as {@link SodiumGameOptionsLoadTest} does.
 */
class SodiumGameOptionsRoundTripTest {

    /** The production parser's shape (see SodiumGameOptions' GSON), so the round-trip below is the loader's own. */
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    @Test
    void fastBlockRendererSurvivesSerialization() {
        SodiumGameOptions config = new SodiumGameOptions();
        config.performance.useFastBlockRenderer = true;

        String json = GSON.toJson(config);

        assertTrue(json.contains("\"use_fast_block_renderer\":true"),
                "the persisted key must be use_fast_block_renderer, got: " + json);
        assertTrue(GSON.fromJson(json, SodiumGameOptions.class).performance.useFastBlockRenderer,
                "the written value must parse back to true");
    }

    @Test
    void fastBlockRendererLoadsFromAPersistedFile() {
        SodiumGameOptions parsed = GSON.fromJson(
                "{\"performance\": {\"use_fast_block_renderer\": true}}", SodiumGameOptions.class);

        assertTrue(parsed.performance.useFastBlockRenderer);
    }

    @Test
    void aMissingKeyKeepsThePropertySeed() {
        SodiumGameOptions parsed = GSON.fromJson("{}", SodiumGameOptions.class);

        assertEquals(Boolean.getBoolean("celeritas.useVintageFastBlockRenderer"),
                parsed.performance.useFastBlockRenderer,
                "a config without the key must start from the -D seed, not a hardcoded default");
    }
}
