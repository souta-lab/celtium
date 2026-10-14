package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Include expansion, conditionals, macros and their bounds. */
class SourcePreprocessorTest {

    @TempDir
    Path tmp;

    private PackSource packOf(Map<String, ?> files) throws IOException, PackException {
        return PackSource.open(FixturePack.writeZip(tmp.resolve("p.zip"), files),
                PackLimits.DEFAULT, new ArrayList<>());
    }

    private SourcePreprocessor.ExpandedSource expand(Map<String, ?> files, String entry,
                                                     Map<String, String> seeds, List<Diagnostic> diags)
            throws IOException, PackException {
        PackSource pack = packOf(files);
        return new SourcePreprocessor(pack, PackLimits.DEFAULT, diags, SourcePreprocessor.Mode.GLSL)
                .expand(PackPath.absolute(entry), seeds);
    }

    @Test
    void defineUndefAndIfdef() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#define FOO 5\n"
                        + "#ifdef FOO\nyes\n#endif\n"
                        + "#ifdef BAR\nno\n#endif\n"
                        + "#undef FOO\n"
                        + "#ifndef FOO\nyes2\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        List<String> text = out.lines.stream().map(l -> l.text()).toList();
        assertEquals(List.of("yes", "yes2"), text);
        assertTrue(diags.isEmpty());
    }

    @Test
    void elifChainsTakeFirstLiveBranch() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#define N 2\n"
                        + "#if N == 1\none\n#elif N == 2\ntwo\n#elif N == 3\nthree\n#else\nother\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("two"), out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void macroValuesExpandInConditions() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#define INNER 7\n"
                        + "#define OUTER INNER + 1\n"
                        + "#if OUTER == 8\nok\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("ok"), out.lines.stream().map(l -> l.text()).toList());
        assertTrue(diags.isEmpty());
    }

    @Test
    void definedOperatorBothForms() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#define SEED yes\n"
                        + "#if defined SEED || defined(MISSING)\nboth-forms\n#endif\n"
                        + "#if defined(MISSING) && defined(SEED)\nnever\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("both-forms"), out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void includesResolveAbsoluteAndFallback() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of(
                "shaders/x.vsh", "#include <lib/util.glsl>\n#include \"abs.glsl\"\n",
                "shaders/lib/util.glsl", "util-content\n",
                "shaders/abs.glsl", "abs-content\n"),
                "/shaders/x.vsh", Map.of(), diags);
        List<String> text = out.lines.stream().map(l -> l.text()).toList();
        assertTrue(text.contains("util-content"));
        assertTrue(text.contains("abs-content"));
        assertTrue(out.filesTouched.contains(PackPath.absolute("/shaders/lib/util.glsl")));
        assertTrue(diags.isEmpty());
    }

    @Test
    void quotedIncludeFallsBackToShadersRoot() throws Exception {
        // BSL pattern: program files do #include "settings.glsl" resolving to /shaders/lib/... via ../
        // and a quoted include that misses locally retries rooted at shaders/.
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of(
                "shaders/x.vsh", "#include \"not-local.glsl\"\n",
                "shaders/not-local.glsl", "found-it\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("found-it"), out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void includeCycleIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of(
                "shaders/x.vsh", "#include \"a.glsl\"\n",
                "shaders/a.glsl", "#include \"b.glsl\"\n",
                "shaders/b.glsl", "#include \"a.glsl\"\ncontent\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.INCLUDE_CYCLE));
    }

    @Test
    void selfIncludeIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of("shaders/x.vsh", "#include \"x.vsh\"\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.INCLUDE_CYCLE));
    }

    @Test
    void missingIncludeIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of("shaders/x.vsh", "#include \"nope.glsl\"\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.INCLUDE_NOT_FOUND));
    }

    @Test
    void includeEscapingShadersIsRejected() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of(
                "shaders/x.vsh", "#include \"../outside.glsl\"\n",
                "outside-landed.glsl", "never"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.INCLUDE_ESCAPE_REJECTED
                || d.code() == DiagnosticCode.ENTRY_TRAVERSAL_REJECTED
                || d.code() == DiagnosticCode.INCLUDE_NOT_FOUND));
        // must never read /outside.glsl
        assertTrue(diags.stream().allMatch(d -> d.severity() == Severity.ERROR));
    }

    @Test
    void includeDepthIsBounded() throws Exception {
        // x.vsh -> a1.glsl -> a2.glsl ... chain of 70 includes exceeds depth 64
        FixturePack.Builder b = FixturePack.pack()
                .file("shaders/x.vsh", "#include \"a1.glsl\"\n");
        for (int i = 1; i < 70; i++) {
            b.file("shaders/a" + i + ".glsl",
                    "#include \"" + (i < 69 ? "a" + (i + 1) : "end") + ".glsl\"\n");
        }
        b.file("shaders/end.glsl", "done\n");
        List<Diagnostic> diags = new ArrayList<>();
        expand(b.files(), "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.INCLUDE_DEPTH_EXCEEDED));
    }

    @Test
    void expansionCountIsBounded() throws Exception {
        // one file including the same file repeatedly -> expansion cap, not a cycle
        FixturePack.Builder b = FixturePack.pack().file("shaders/leaf.glsl", "x\n");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            sb.append("#include \"leaf.glsl\"\n");
        }
        b.file("shaders/x.vsh", sb.toString());
        PackLimits limits = new PackLimits(16384, 512L << 20, 16L << 20, 250, 1 << 20,
                64, 8 /* only 8 expansions */, 8L << 20, 65536);
        Path zip = b.zip(tmp.resolve("p.zip"));
        List<Diagnostic> diags = new ArrayList<>();
        PackSource pack = PackSource.open(zip, limits, diags);
        new SourcePreprocessor(pack, limits, diags, SourcePreprocessor.Mode.GLSL)
                .expand(PackPath.absolute("/shaders/x.vsh"), Map.of());
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.EXPANSION_LIMIT_EXCEEDED));
    }

    @Test
    void unbalancedConditionalIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of("shaders/x.vsh", "#ifdef FOO\nnever closed\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.UNBALANCED_CONDITIONAL));
    }

    @Test
    void packErrorDirectiveIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of("shaders/x.vsh", "#error this pack needs OptiFine\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.PACK_ERROR_DIRECTIVE));
    }

    @Test
    void packErrorInsideDeadConditionalIsIgnored() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#ifdef NOTHING\n#error dead\n#endif\nfine\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().noneMatch(d -> d.code() == DiagnosticCode.PACK_ERROR_DIRECTIVE));
        assertEquals(List.of("fine"), out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void definesInsideBlockCommentsAreDocumentation() throws Exception {
        // the doc-comment trap found in real packs: example #define lines inside
        // a /* */ block must not define macros
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "/*\n#define FAKE_OPTION 1\nmulti-line doc\n*/\n"
                        + "#ifdef FAKE_OPTION\nleaked\n#else\nclean\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("/*", "#define FAKE_OPTION 1", "multi-line doc", "*/", "clean"),
                out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void defineValueStripsTrailingComment() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#define X 1 //[0 1]\n#if X == 1\nok\n#endif\n"),
                "/shaders/x.vsh", Map.of(), diags);
        assertEquals(List.of("ok"), out.lines.stream().map(l -> l.text()).toList());
        assertTrue(diags.isEmpty());
    }

    @Test
    void binaryIncludeIsRejected() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of(
                "shaders/x.vsh", "#include \"tex/lookup.png\"\n",
                "shaders/tex/lookup.png", new byte[]{1, 2, 3}),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.BINARY_INCLUDE));
    }

    @Test
    void nonUtf8SourceIsAnError() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        expand(Map.of("shaders/x.vsh", new byte[]{'#', 'i', 'f', 'n', 'd', 'e', 'f', ' ',
                        'X', '\n', (byte) 0xC3, 0x28, '\n'}),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.NON_UTF8_SOURCE));
    }

    @Test
    void bomIsStrippedWithWarning() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                        new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'h', 'i', '\n'}),
                "/shaders/x.vsh", Map.of(), diags);
        assertTrue(diags.stream().anyMatch(d -> d.code() == DiagnosticCode.BOM_PRESENT
                && d.severity() == Severity.WARNING));
        assertEquals(List.of("hi"), out.lines.stream().map(l -> l.text()).toList());
    }

    @Test
    void conditionIdentifiersAreCollected() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#ifdef SNEEZE\na\n#endif\n#if defined KAZOO || MC_VERSION > 0\nb\n#endif\n"),
                "/shaders/x.vsh", Map.of("MC_VERSION", "11202"), diags);
        assertTrue(out.conditionIdentifiers.contains("SNEEZE"));
        assertTrue(out.conditionIdentifiers.contains("KAZOO"));
        assertTrue(out.conditionIdentifiers.contains("MC_VERSION"));
    }

    @Test
    void seedsFlowIntoConditions() throws Exception {
        List<Diagnostic> diags = new ArrayList<>();
        var out = expand(Map.of("shaders/x.vsh",
                "#if MC_VERSION >= 11300\nnew\n#else\nold\n#endif\n"),
                "/shaders/x.vsh", Map.of("MC_VERSION", "11202"), diags);
        assertEquals(List.of("old"), out.lines.stream().map(l -> l.text()).toList());
    }
}
