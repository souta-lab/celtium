package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stub's record of a mod's program: what a link lowers and which values a location lands on. */
class ModProgramRegistryTest {

    @BeforeEach
    void reset() {
        ModProgramRegistry.clear();
    }

    @Test
    void aLinkedProgramMapsTheStubsLocationsBackToItsUniforms() {
        // Attached fragment-first: the stage is read from the source, not the attachment order.
        ModProgramRegistry.shaderSource(11, "uniform sampler2D tex; uniform float strength; varying vec2 uv;\n"
                + "void main() { gl_FragColor = texture2D(tex, uv) * strength; }");
        ModProgramRegistry.shaderSource(10, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(5, 11);
        ModProgramRegistry.attach(5, 10);
        ModProgramRegistry.Program program = ModProgramRegistry.link(5);
        assertTrue(program.lowered().ok(), program.lowered().failure());

        ModProgramRegistry.uniformFloats(5, ModProgramRegistry.location("strength"), new float[] {0.75f});
        ModProgramRegistry.uniformInts(5, ModProgramRegistry.location("tex"), new int[] {2});
        assertArrayEquals(new float[] {0.75f}, program.floats("strength"));
        assertArrayEquals(new int[] {2}, program.ints("tex"));
        assertEquals(2, program.uniformWrites());

        // A location no uniform of the bound program has, and a write with no program bound, are counted, not lost.
        ModProgramRegistry.uniformFloats(5, ModProgramRegistry.location("missing"), new float[] {1f});
        ModProgramRegistry.uniformFloats(0, ModProgramRegistry.location("strength"), new float[] {1f});
        assertEquals(2, ModProgramRegistry.unmatchedUniformWrites());
        assertArrayEquals(new float[] {0.75f}, program.floats("strength"));
    }

    @Test
    void theLocationIsTheStubsNameHash() {
        // CeltiumGllessHint.glUniformLocation: 31-hash of the name's bytes, masked to 30 bits.
        int hash = 0;
        for (byte b : "bloomStrength".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            hash = 31 * hash + b;
        }
        assertEquals(hash & 0x3FFFFFFF, ModProgramRegistry.location("bloomStrength"));
    }

    @Test
    void aLocationCollisionBetweenTwoNamesIsCounted() {
        // Locations are 30-bit name hashes, so a colliding pair exists; find one rather than hard-coding it.
        // The candidates are random letter strings from a fixed seed: sequential names ("u0", "u1", ...) hash
        // to distinct values by construction (their digit strings are unique base-31 representations), while
        // random names collide at the birthday bound, tens of thousands in. java.util.Random's sequence is
        // specified, so the search is deterministic.
        java.util.Map<Integer, String> byLocation = new java.util.HashMap<>();
        java.util.Random random = new java.util.Random(42);
        String first = null;
        String second = null;
        for (int i = 0; i < 2_000_000 && second == null; i++) {
            StringBuilder name = new StringBuilder("u");
            for (int c = 0; c < 8; c++) {
                name.append((char) ('a' + random.nextInt(26)));
            }
            String other = byLocation.putIfAbsent(ModProgramRegistry.location(name.toString()), name.toString());
            if (other != null && !other.contentEquals(name)) {
                first = other;
                second = name.toString();
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(second, "a colliding pair has to exist among random names");
        org.junit.jupiter.api.Assertions.assertEquals(
                ModProgramRegistry.location(first), ModProgramRegistry.location(second));
        int collisionsBefore = ModProgramRegistry.locationNameCollisions();
        ModProgramRegistry.shaderSource(31,
                "attribute vec2 position;\nvoid main() { gl_Position = vec4(position, 0.0, 1.0); }");
        ModProgramRegistry.shaderSource(32, "uniform float " + first + "; uniform float " + second + ";\n"
                + "void main() { gl_FragColor = vec4(" + first + " + " + second + "); }");
        ModProgramRegistry.attach(9, 31);
        ModProgramRegistry.attach(9, 32);
        ModProgramRegistry.Program program = ModProgramRegistry.link(9);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        assertTrue(ModProgramRegistry.locationNameCollisions() > collisionsBefore,
                "the second name's mapping replaced the first's at their shared location, and that is counted");
    }

    void aFragmentStageThatMentionsVertexOutputInACommentStillLinks() {
        // The fallback stage heuristic reads the comment-stripped source: with the raw text, the words
        // gl_Position inside this comment made the fragment stage a second "vertex" stage and the link
        // failed with no fragment stage attached.
        ModProgramRegistry.shaderSource(31, "uniform sampler2D tex; varying vec2 uv;\n"
                + "// the vertex stage writes gl_Position; this stage only shades\n"
                + "void main() { gl_FragColor = texture2D(tex, uv); }");
        ModProgramRegistry.shaderSource(30, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(6, 31);
        ModProgramRegistry.attach(6, 30);
        ModProgramRegistry.Program program = ModProgramRegistry.link(6);
        assertTrue(program.lowered().ok(), program.lowered().failure());
    }

    @Test
    void theRecordedStageWinsOverTheSourceText() {
        // The stages are recorded the other way round from what the sources suggest: the link must follow
        // glCreateShader's arguments, so the vertex-looking source is lowered as the fragment stage and the
        // attribute it declares never becomes an attribute of the program. (The pair still lowers - a vertex
        // stage that writes no gl_Position compiles - so the classification is pinned by the interface, not
        // by a failure.)
        ModProgramRegistry.shaderStage(41, 0x8B30);
        ModProgramRegistry.shaderSource(41, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.shaderStage(40, 0x8B31);
        ModProgramRegistry.shaderSource(40, "varying vec2 uv;\n"
                + "void main() { gl_FragColor = vec4(uv, 0.0, 1.0); }");
        ModProgramRegistry.attach(9, 41);
        ModProgramRegistry.attach(9, 40);
        ModProgramRegistry.Program program = ModProgramRegistry.link(9);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        assertTrue(program.lowered().attributes().isEmpty());
    }

    @Test
    void twoShadersCreatedForOneStageFailTheLink() {
        // GL rejects the link; silently keeping the second stage (the old last-wins) hid the mod's bug and
        // dropped the first stage's code without a word.
        ModProgramRegistry.shaderStage(51, 0x8B31);
        ModProgramRegistry.shaderSource(51, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.shaderStage(50, 0x8B31);
        ModProgramRegistry.shaderSource(50, "void main() { gl_Position = vec4(1.0); }");
        ModProgramRegistry.attach(12, 51);
        ModProgramRegistry.attach(12, 50);
        ModProgramRegistry.Program program = ModProgramRegistry.link(12);
        assertTrue(program.lowered().failure().contains("two vertex stages"), program.lowered().failure());
    }

    @Test
    void aRelinkStartsTheUniformValuesOver() {
        ModProgramRegistry.shaderSource(61, "uniform float strength; varying vec2 uv;\n"
                + "void main() { gl_FragColor = vec4(strength); }");
        ModProgramRegistry.shaderSource(60, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(15, 61);
        ModProgramRegistry.attach(15, 60);
        ModProgramRegistry.Program program = ModProgramRegistry.link(15);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        ModProgramRegistry.uniformFloats(15, ModProgramRegistry.location("strength"), new float[] {0.5f});
        assertArrayEquals(new float[] {0.5f}, program.floats("strength"));
        ModProgramRegistry.link(15);
        assertNull(program.floats("strength"));
    }

    @Test
    void aPushSnapshotKeepsTheValuesFromItsOwnPrepare() {
        // The defect this pins: packUniforms returns the program's one reusable buffer, so a snapshot taken
        // for the first of two prepares in one window read the second prepare's values at flush time.
        ModProgramRegistry.shaderSource(71, "uniform float strength; varying vec2 uv;\n"
                + "void main() { gl_FragColor = vec4(strength); }");
        ModProgramRegistry.shaderSource(70, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(18, 71);
        ModProgramRegistry.attach(18, 70);
        ModProgramRegistry.Program program = ModProgramRegistry.link(18);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        assertTrue(!program.lowered().uniformsViaBuffer(), "this program must take the push route");
        int offset = program.lowered().uniforms().get(0).offset();

        int strength = ModProgramRegistry.location("strength");
        ModProgramRegistry.uniformFloats(18, strength, new float[] {1.0f});
        java.nio.ByteBuffer first = org.soutalab.celtium.vulkan.VulkanModProgramPipeline
                .packUniformsSnapshot(program);
        ModProgramRegistry.uniformFloats(18, strength, new float[] {2.0f});
        java.nio.ByteBuffer second = org.soutalab.celtium.vulkan.VulkanModProgramPipeline.packUniforms(program);
        assertEquals(1.0f, first.getFloat(offset));
        assertEquals(2.0f, second.getFloat(offset));
    }

    @Test
    void anUnwrittenUniformPacksItsDeclaredInitializer() {
        ModProgramRegistry.shaderSource(81, "varying vec2 uv;\n"
                + "void main() { gl_FragColor = vec4(1.0); }");
        ModProgramRegistry.shaderSource(80, "uniform float gain = 2.0; attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position * gain, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(21, 81);
        ModProgramRegistry.attach(21, 80);
        ModProgramRegistry.Program program = ModProgramRegistry.link(21);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        java.nio.ByteBuffer packed = org.soutalab.celtium.vulkan.VulkanModProgramPipeline.packUniforms(program);
        int offset = program.lowered().uniforms().get(0).offset();
        assertEquals(2.0f, packed.getFloat(offset));
    }

    @Test
    void aLoweredNormalScalePacksOneUntilWritten() {
        // Item 643: gl_NormalScale lowers to a block member whose default is GL's no-rescale value, so a
        // program nobody writes it for (Twilight Forest's shape) packs 1.0, and a write under the lowered
        // name replaces the default like any other uniform's first write.
        ModProgramRegistry.shaderSource(91,
                "varying vec2 uv; void main() { gl_FragColor = vec4(1.0); }");
        ModProgramRegistry.shaderSource(90, "varying vec2 uv; varying float scale;"
                + " void main() { gl_Position = vec4(0.0, 0.0, 0.0, 1.0); uv = vec2(0.0);"
                + " scale = gl_NormalScale; }");
        ModProgramRegistry.attach(31, 91);
        ModProgramRegistry.attach(31, 90);
        ModProgramRegistry.Program program = ModProgramRegistry.link(31);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        int offset = program.lowered().uniforms().stream()
                .filter(u -> u.name().equals("celtium_NormalScale")).findFirst().orElseThrow().offset();

        java.nio.ByteBuffer packed = org.soutalab.celtium.vulkan.VulkanModProgramPipeline.packUniforms(program);
        assertEquals(1.0f, packed.getFloat(offset));

        ModProgramRegistry.uniformFloats(31, ModProgramRegistry.location("celtium_NormalScale"),
                new float[] {2.5f});
        java.nio.ByteBuffer rewritten = org.soutalab.celtium.vulkan.VulkanModProgramPipeline
                .packUniforms(program);
        assertEquals(2.5f, rewritten.getFloat(offset));
    }

    @Test
    void aProgramWithoutAFragmentStageSaysSo() {
        ModProgramRegistry.shaderSource(20, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7, 20);
        ModProgramRegistry.Program program = ModProgramRegistry.link(7);
        assertTrue(program.lowered().failure().contains("no fragment stage"));
        assertNull(ModProgramRegistry.program(8));
    }

    @Test
    void linkStatusIsUnrecordedUntilALinkRuns() {
        // An id the registry never saw, and a program that was created and fed but never linked: neither
        // has a recorded result, so the query surfaces must keep their default answer for both.
        assertNull(ModProgramRegistry.linkStatus(4242));
        assertNull(ModProgramRegistry.linkInfoLog(4242));
        ModProgramRegistry.shaderSource(20, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7, 20);
        assertNull(ModProgramRegistry.linkStatus(7));
        assertNull(ModProgramRegistry.linkInfoLog(7));
        assertEquals(1, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B82));
        assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B84));
    }

    @Test
    void aLinkThatFailsToLowerAnswersFalseWithTheReason() {
        ModProgramRegistry.shaderSource(20, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7, 20);
        ModProgramRegistry.link(7);
        assertEquals(Boolean.FALSE, ModProgramRegistry.linkStatus(7));
        String log = ModProgramRegistry.linkInfoLog(7);
        assertTrue(log != null && log.contains("no fragment stage"), String.valueOf(log));
        // The query answers follow the record: link and validate false, the log length is the reason's,
        // and compile status keeps its default - no per-shader result exists in a link-time model.
        assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B82));
        assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B83));
        assertEquals(log.length() + 1, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B84));
        assertEquals(1, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B81));
        assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(7, 0x8B80));
    }

    @Test
    void theCrossModuleFacadeAnswersTheSameTruth() {
        // The LWJGL3 native route reaches the shared answer only through the public facade the hint
        // calls; it must state the same truth as the package answer for an unrecorded id and for a
        // recorded failure alike, or the route a caller actually takes would drift from the dispatch.
        assertEquals(1, CeltiumGlStub.celtium$shaderProgramQueryAnswer(4242, 0x8B82));
        assertEquals(0, CeltiumGlStub.celtium$shaderProgramQueryAnswer(4242, 0x8B84));
        ModProgramRegistry.shaderSource(20, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7, 20);
        ModProgramRegistry.link(7);
        String log = ModProgramRegistry.linkInfoLog(7);
        assertTrue(log != null && log.contains("no fragment stage"), String.valueOf(log));
        assertEquals(0, CeltiumGlStub.celtium$shaderProgramQueryAnswer(7, 0x8B82));
        assertEquals(0, CeltiumGlStub.celtium$shaderProgramQueryAnswer(7, 0x8B83));
        assertEquals(log.length() + 1, CeltiumGlStub.celtium$shaderProgramQueryAnswer(7, 0x8B84));
        assertEquals(1, CeltiumGlStub.celtium$shaderProgramQueryAnswer(7, 0x8B81));
    }

    @Test
    void aRecordedLinkAnswersConsistentlyWithItsCompiledState() {
        ModProgramRegistry.shaderSource(11, "varying vec2 uv;\n"
                + "void main() { gl_FragColor = vec4(1.0); }");
        ModProgramRegistry.shaderSource(10, "attribute vec2 position; varying vec2 uv;\n"
                + "void main() { gl_Position = vec4(position, 0.0, 1.0); uv = position; }");
        ModProgramRegistry.attach(5, 11);
        ModProgramRegistry.attach(5, 10);
        ModProgramRegistry.Program program = ModProgramRegistry.link(5);
        assertTrue(program.lowered().ok(), program.lowered().failure());
        // Whether both stages compiled depends on shaderc being present in the test environment; the
        // recorded answer must match the recorded state either way.
        assertEquals(Boolean.valueOf(program.compiled()), ModProgramRegistry.linkStatus(5));
        if (program.compiled()) {
            assertNull(ModProgramRegistry.linkInfoLog(5));
            assertEquals(1, CeltiumGlStub.shaderProgramQueryAnswer(5, 0x8B82));
            assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(5, 0x8B84));
        } else {
            assertEquals(0, CeltiumGlStub.shaderProgramQueryAnswer(5, 0x8B82));
            assertTrue(ModProgramRegistry.linkInfoLog(5) != null);
        }
    }
}
