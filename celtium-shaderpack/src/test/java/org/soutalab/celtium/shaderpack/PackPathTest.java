package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canonicalization rules for in-pack paths. */
class PackPathTest {

    @Test
    void canonicalizesDotSegmentsAndDupSeparators() throws PackException {
        assertEquals("/shaders/lib/util.glsl",
                PackPath.absolute("/shaders//lib/./util.glsl").toString());
        assertEquals("/shaders/lib/util.glsl",
                PackPath.absolute("/shaders/x/../lib/util.glsl").toString());
        // backslashes normalize during canonicalization - reached through the
        // include path (zip entries get '/' prepended before absolute())
        assertEquals("/shaders/lib/util.glsl",
                PackPath.resolve(PackPath.absolute("/s.vsh"), "shaders\\lib\\util.glsl").toString());
        // a non-absolute raw string is malformed, never silently treated as root-relative
        assertThrows(PackException.class, () -> PackPath.absolute("\\shaders\\lib\\x.glsl"));
    }

    @Test
    void rejectsTraversalAboveRoot() throws PackException {
        PackException e = assertThrows(PackException.class,
                () -> PackPath.absolute("/shaders/../../etc/passwd"));
        assertEquals(DiagnosticCode.ENTRY_TRAVERSAL_REJECTED, e.code());
        assertThrows(PackException.class, () -> PackPath.absolute("/../shaders/x"));
        // ".." that lands exactly on the root is fine; going above is the error
        assertEquals("/", PackPath.absolute("/shaders/..").toString());
        assertThrows(PackException.class, () -> PackPath.absolute("/shaders/../.."));
    }

    @Test
    void rejectsNulAndNonAbsolute() throws PackException {
        assertThrows(PackException.class, () -> PackPath.absolute("/shaders/a\u0000b"));
        assertThrows(PackException.class, () -> PackPath.absolute("shaders/x"));
        assertThrows(PackException.class, () -> PackPath.absolute(""));
        assertEquals("/", PackPath.absolute("/").toString()); // the root itself is valid
    }

    @Test
    void resolvesIncludesRelativeThenUnderShaders() throws PackException {
        PackPath includer = PackPath.absolute("/shaders/world0/composite.vsh");
        assertEquals("/shaders/world0/lib/a.glsl",
                PackPath.resolve(includer, "lib/a.glsl").toString());
        // ".." that stays under the pack root canonicalizes fine - the include
        // layer rejects it (INCLUDE_ESCAPE_REJECTED) because it leaves shaders/
        assertEquals("/escape.glsl", PackPath.resolve(includer, "../../escape.glsl").toString());
        // ".." above the root is outright traversal
        assertThrows(PackException.class, () -> PackPath.resolve(includer, "../../../../escape.glsl"));
        PackPath world = PackPath.absolute("/shaders/world-1/x.vsh");
        assertEquals("/shaders/world-1/a.glsl", PackPath.resolve(world, "./a.glsl").toString());
    }

    @Test
    void orderingIsDeterministic() throws PackException {
        List<PackPath> paths = IntStream.range(0, 20)
                .mapToObj(i -> {
                    try {
                        return PackPath.absolute("/shaders/f" + i + ".vsh");
                    } catch (PackException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());
        List<PackPath> sorted = paths.stream().sorted().toList();
        // second sort must produce the identical order (stable + total order)
        List<PackPath> sortedTwice = sorted.stream().sorted().toList();
        assertEquals(sorted, sortedTwice);
        assertTrue(sorted.indexOf(PackPath.absolute("/shaders/f10.vsh"))
                < sorted.indexOf(PackPath.absolute("/shaders/f2.vsh")));
    }

    @Test
    void predicates() throws PackException {
        PackPath p = PackPath.absolute("/shaders/world0/lib/x.glsl");
        assertEquals("x.glsl", p.name());
        assertEquals("/shaders/world0/lib", p.parent().toString());
        assertTrue(p.isUnder("/shaders/"));
        assertTrue(!p.isUnder("/shaders/world1/"));
    }
}
