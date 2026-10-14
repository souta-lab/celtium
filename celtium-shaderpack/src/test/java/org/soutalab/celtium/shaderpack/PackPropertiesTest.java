package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** properties-file parsing: comments, continuations, escapes, key=value. */
class PackPropertiesTest {

    private static PackProperties parse(String... lines) {
        List<SourcePreprocessor.ExpandedLine> in = new java.util.ArrayList<>();
        int i = 0;
        for (String line : lines) {
            in.add(new SourcePreprocessor.ExpandedLine(
                    PackPath.absoluteUnchecked("/shaders/shaders.properties"), ++i, line));
        }
        return PackProperties.parse(PackPath.absoluteUnchecked("/shaders/x.properties"),
                in, new java.util.ArrayList<>());
    }

    @Test
    void basicKeyValueAndComments() {
        PackProperties p = parse(
                "# a comment",
                "! another comment",
                "key.one = value1",
                "key.two:value2",
                "key.three   value3   ",
                "");
        assertEquals("value1", p.asMap().get("key.one"));
        assertEquals("value2", p.asMap().get("key.two"));
        assertEquals("value3", p.asMap().get("key.three"));
        assertEquals(3, p.entries().size());
    }

    @Test
    void backslashContinuesLines() {
        PackProperties p = parse(
                "sliders = A \\",
                "          B \\",
                "          C");
        assertEquals("A B C", p.asMap().get("sliders"));
    }

    @Test
    void oddBackslashDoesNotContinue() {
        PackProperties p = parse("key = a\\\\", "next = b");
        assertEquals("a\\", p.asMap().get("key"));
        assertEquals("b", p.asMap().get("next"));
    }

    @Test
    void unicodeEscapesDecode() {
        PackProperties p = parse("greeting = hell\\u006f", "space = a\\ b");
        assertEquals("hello", p.asMap().get("greeting"));
        assertEquals("a b", p.asMap().get("space"));
    }

    @Test
    void lastValueWins() {
        PackProperties p = parse("k = first", "k = second");
        assertEquals("second", p.asMap().get("k"));
        assertEquals(2, p.entries().size()); // both entries kept for census
    }

    @Test
    void bareKeyGetsEmptyValue() {
        PackProperties p = parse("justAKey");
        assertEquals("", p.asMap().get("justAKey"));
    }

    @Test
    void entriesKeepLocations() {
        PackProperties p = parse("#c", "", "a = 1");
        assertEquals(3, p.entries().get(0).location().line());
    }
}
