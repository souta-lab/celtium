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
    void stripsOptifineOptionAnnotations(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("annotations.zip"), Map.of(
                "shaders/gbuffers_basic.fsh", """
                        #version 120
                        #define CG_GM 0 //[0 4 8 12]
                        void main() {
                            gl_FragColor = vec4(float(CG_GM));
                        }
                        """,
                "shaders/shaders.properties", "profile.CELTIUM=TEST\n"));
        String expanded = ShaderPackLoader.expand(zip, PackPath.absolute("/shaders/gbuffers_basic.fsh"), Map.of());
        // The annotation is metadata for the options screen, and it is what made shaderc's preprocessor overflow on a
        // real pack: the same program compiles with the annotations gone and not with only the brackets neutralised.
        assertFalse(expanded.contains("//[0 4 8 12]"), "the option annotation reached the compiler:\n" + expanded);
        assertTrue(expanded.contains("#define CG_GM 0"), "the definition was lost:\n" + expanded);
    }

    @Test
    void refusesAPackWithoutTheFile(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("empty.zip"), Map.of(
                "shaders/shaders.properties", "profile.CELTIUM=TEST\n"));
        assertThrows(PackException.class, () -> ShaderPackLoader.expand(zip,
                PackPath.absolute("/shaders/gbuffers_terrain.vsh"), Map.of()));
    }

    /**
     * The seeds decide the {@code #if} lines, so the compiler has to see the same environment: a shader that reads a seed
     * reached shaderc as an undefined identifier (audit #372). The define goes after {@code #version}, and a name the
     * shader defines itself is left to the shader.
     */
    @Test
    void seedsReachTheCompiledTextAsDefines(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("pack.zip"), Map.of(
                "shaders/gbuffers_terrain.vsh", """
                        #version 120
                        #if FLAG
                        const int k = MC_VERSION;
                        #endif
                        void main() {
                            gl_Position = vec4(float(k));
                        }
                        """));
        String expanded = ShaderPackLoader.expand(zip, PackPath.absolute("/shaders/gbuffers_terrain.vsh"),
                Map.of("FLAG", "1", "MC_VERSION", "11202"));
        String[] lines = expanded.split("\n");
        int versionLine = -1;
        int seedLine = -1;
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].trim().startsWith("#version")) {
                versionLine = index;
            }
            if (lines[index].contains("#define MC_VERSION 11202")) {
                seedLine = index;
            }
        }
        org.junit.jupiter.api.Assertions.assertTrue(versionLine >= 0, expanded);
        org.junit.jupiter.api.Assertions.assertTrue(seedLine > versionLine,
                "the seed define must follow #version: " + expanded);
        org.junit.jupiter.api.Assertions.assertTrue(expanded.contains("const int k = MC_VERSION;"), expanded);
    }

    /**
     * A {@code #define / #undef / #define} sequence has to reach the compiler with both its defines and the removal: the
     * preprocessor used to drop the {@code #undef} (audit #372), so the compiler kept the first value.
     */
    @Test
    void undefSurvivesOnTheCompilationPath(@TempDir Path temp) throws Exception {
        Path zip = FixturePack.writeZip(temp.resolve("pack.zip"), Map.of(
                "shaders/gbuffers_basic.fsh", """
                        #version 120
                        #define X 7
                        #undef X
                        #define X 9
                        void main() {
                            gl_FragColor = vec4(float(X));
                        }
                        """));
        String expanded = ShaderPackLoader.expand(zip, PackPath.absolute("/shaders/gbuffers_basic.fsh"), Map.of());
        int first = expanded.indexOf("#define X 7");
        int undef = expanded.indexOf("#undef X");
        int second = expanded.indexOf("#define X 9");
        org.junit.jupiter.api.Assertions.assertTrue(first >= 0, expanded);
        org.junit.jupiter.api.Assertions.assertTrue(undef > first, "the #undef must be kept, in order: " + expanded);
        org.junit.jupiter.api.Assertions.assertTrue(second > undef, expanded);
        org.junit.jupiter.api.Assertions.assertTrue(expanded.contains("float(X)"), expanded);
    }
}
