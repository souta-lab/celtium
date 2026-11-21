package org.embeddedt.embeddium.impl.gui;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loader's usability gate: Gson returns null for an empty or "null" config file and
 * honours an explicit null section, and the loader dereferences every section right after
 * parsing. Before the gate, such a file surfaced to the caller as a null-pointer failure,
 * whose fallback is a read-only default config for the whole session - while a syntax
 * error in the same file gets writable defaults with the file left untouched. The gate
 * puts the null shapes on the syntax-error path.
 */
class SodiumGameOptionsLoadTest {

    /** The production parser's shape (see SodiumGameOptions' GSON), so the parses below are the loader's own. */
    private static final Gson GSON = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .excludeFieldsWithModifiers(Modifier.PRIVATE)
            .create();

    @Test
    void aFreshConfigIsUsable() {
        assertTrue(SodiumGameOptions.isUsable(new SodiumGameOptions()));
    }

    @Test
    void aNullConfigIsNotUsable() {
        assertFalse(SodiumGameOptions.isUsable(null));
    }

    @Test
    void anEmptyFileParsesToNullAndIsNotUsable() {
        SodiumGameOptions parsed = GSON.fromJson("", SodiumGameOptions.class);

        assertFalse(SodiumGameOptions.isUsable(parsed),
                "an empty config file parses to null, which the loader must not dereference");
    }

    @Test
    void aNullLiteralFileParsesToNullAndIsNotUsable() {
        SodiumGameOptions parsed = GSON.fromJson("null", SodiumGameOptions.class);

        assertFalse(SodiumGameOptions.isUsable(parsed));
    }

    @Test
    void anExplicitNullSectionIsNotUsable() {
        SodiumGameOptions parsed = GSON.fromJson("{\"quality\": null}", SodiumGameOptions.class);

        assertFalse(SodiumGameOptions.isUsable(parsed),
                "an explicit \"quality\": null nulls a section the loader dereferences");
    }

    @Test
    void aPartialFileKeepsItsSectionsAndIsUsable() {
        SodiumGameOptions parsed = GSON.fromJson("{}", SodiumGameOptions.class);

        assertTrue(SodiumGameOptions.isUsable(parsed),
                "a file naming no sections keeps the constructed defaults and loads normally");
    }
}
