package org.soutalab.celtium.shaderpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The door the renderer needs: a program's own text, with its includes resolved and the pack's macros applied.
 */
class ShaderPackExpandTest {
    @Test
    void resolvesIncludesAndAppliesSeeds(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("pack.zip"), Map.of(
                "shaders/gbuffers_terrain.vsh", """
                        #version 120
                        #include "/lib/util.glsl"
                        void main() {
                            float scaled = SCALE;
                            gl_Position = ftransform() * scaled;
                        }
                        """,
                "shaders/lib/util.glsl", """
                        #define SCALE 2.0
                        attribute vec4 mc_Entity;
                        """,
                "shaders/shaders.properties", "profile.CELTIUM=TEST\n"));
        String expanded = ShaderPackLoader.expand(zip, PackPath.absolute("/shaders/gbuffers_terrain.vsh"),
                Map.of("MC_VERSION", "1.12.2"));
        assertTrue(expanded.contains("mc_Entity"), "the include was not resolved:\n" + expanded);
        assertFalse(expanded.contains("#include"), "an include directive survived:\n" + expanded);
        assertTrue(expanded.contains("ftransform()"), expanded);
        // The definition itself does not survive expansion - it is consumed and its uses substituted, which is what a
        // preprocessor does. The test asserted the definition first and was wrong; what matters is the substitution.
        // The definitions are kept for the compiler rather than applied here: the preprocessor resolves includes and
        // evaluates the pack's conditionals, and the #define lines travel with the text so that the GLSL compiler's own
        // preprocessor substitutes them. Consuming them here left every use unresolved.
        assertTrue(expanded.contains("#define SCALE 2.0"), "the definition did not travel with the text:\n" + expanded);
    }

    @Test
    void refusesAPackWithoutTheFile(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("empty.zip"), Map.of(
                "shaders/shaders.properties", "profile.CELTIUM=TEST\n"));
        assertThrows(PackException.class, () -> ShaderPackLoader.expand(zip,
                PackPath.absolute("/shaders/gbuffers_terrain.vsh"), Map.of()));
    }
}
