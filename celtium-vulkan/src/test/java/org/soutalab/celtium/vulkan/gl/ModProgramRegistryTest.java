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

    @Test
    void aProgramWithoutAFragmentStageSaysSo() {
        ModProgramRegistry.shaderSource(20, "void main() { gl_Position = vec4(0.0); }");
        ModProgramRegistry.attach(7, 20);
        ModProgramRegistry.Program program = ModProgramRegistry.link(7);
        assertTrue(program.lowered().failure().contains("no fragment stage"));
        assertNull(ModProgramRegistry.program(8));
    }
}
