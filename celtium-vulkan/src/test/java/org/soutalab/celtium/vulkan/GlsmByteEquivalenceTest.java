package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.soutalab.celtium.vulkan.gl.GlsmPackState;
import org.soutalab.celtium.vulkan.gl.ModGlslTranslator;
import org.soutalab.celtium.vulkan.gl.ModProgramRegistry;

import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GLSM stage 1 landing condition [b] (glsm-stage1-design.md §3, alpha-glsm-stage1-impl.md):
 * the three byte-equivalence properties, at the levels the JVM can decide without a device.
 * <ol>
 * <li>The DYNAMIC incremental pack (member dirty bitset) produces the same block bytes as the
 * full walk, over a seeded random write sequence applied to twin programs - one packed only
 * through the incremental path, one only through the public full walk.</li>
 * <li>The PER_FRAME shared block: its std140 layout is the contractual one, and the harvester's
 * first-writer rule keeps one member-consistent value set per epoch (a later program offering
 * different bits is counted as a divergence and never mixed in).</li>
 * <li>The REGION table's value column: the floats a region write records - which the backend
 * writes into the {@code CeltiumRegionOffsets} slot as (x, y, z, 0) - are bit-identical to the
 * slice the old per-region uniform delivery packed into the block at the member's offset.</li>
 * </ol>
 * Runs in the flags-off test JVM: the full walk is {@link VulkanModProgramPipeline#packUniforms}'
 * off route, and the incremental packer is invoked directly (it is the same code the flags select).
 */
class GlsmByteEquivalenceTest {

    private static final Method INCREMENTAL = incrementalMethod();

    private static Method incrementalMethod() {
        try {
            Method method = VulkanModProgramPipeline.class.getDeclaredMethod(
                    "packUniformsIncremental", ModProgramRegistry.Program.class);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static ByteBuffer packIncremental(ModProgramRegistry.Program program) {
        try {
            return (ByteBuffer) INCREMENTAL.invoke(null, program);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("incremental pack failed", failure);
        }
    }

    @BeforeEach
    void reset() {
        ModProgramRegistry.clear();
    }

    private static byte[] blockBytes(ByteBuffer buffer, int bytes) {
        byte[] out = new byte[bytes];
        for (int i = 0; i < bytes; i++) {
            out[i] = buffer.get(i);
        }
        return out;
    }

    // ---- (1) DYNAMIC incremental vs full walk ---------------------------------------------------

    private static final String VERTEX = """
            #version 120
            attribute vec2 position;
            void main() { gl_Position = vec4(position, 0.0, 1.0); }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform float fScalar;
            uniform vec2 v2;
            uniform vec3 v3;
            uniform vec4 v4;
            uniform mat3 m3;
            uniform mat4 m4;
            uniform int iScalar;
            uniform ivec3 iv3;
            uniform float fArr[3];
            uniform vec4 vArr[2];
            uniform float fDefault = 2.5;
            uniform sampler2D tex;
            void main() {
                float s = fScalar + v2.x + v3.y + v4.z + m3[1][2] + m4[2][1]
                        + float(iScalar) + float(iv3.x) + fArr[1] + vArr[1].w + fDefault;
                gl_FragColor = texture2D(tex, vec2(s));
            }
            """;

    /** Member name -> the float count a full write carries (array members: element count x width). */
    private static final Map<String, Integer> FLOAT_MEMBERS = Map.ofEntries(
            Map.entry("fScalar", 1), Map.entry("v2", 2), Map.entry("v3", 3), Map.entry("v4", 4),
            Map.entry("m3", 9), Map.entry("m4", 16), Map.entry("fArr", 3), Map.entry("vArr", 8),
            Map.entry("fDefault", 1));
    private static final Map<String, Integer> INT_MEMBERS = Map.of("iScalar", 1, "iv3", 3);

    private static ModProgramRegistry.Program linkPair(int programId, int vertexId, int fragmentId) {
        ModProgramRegistry.shaderSource(vertexId, VERTEX);
        ModProgramRegistry.shaderSource(fragmentId, FRAGMENT);
        ModProgramRegistry.attach(programId, vertexId);
        ModProgramRegistry.attach(programId, fragmentId);
        ModProgramRegistry.Program program = ModProgramRegistry.link(programId);
        assertTrue(program.lowered().ok(), String.valueOf(program.lowered().failure()));
        return program;
    }

    private static void writeFloats(int program, String name, float[] values) {
        ModProgramRegistry.uniformFloats(program, ModProgramRegistry.location(name), values);
    }

    private static void writeInts(int program, String name, int[] values) {
        ModProgramRegistry.uniformInts(program, ModProgramRegistry.location(name), values);
    }

    private static float[] randomFloats(Random random, int length) {
        float[] values = new float[length];
        for (int i = 0; i < length; i++) {
            // Small integers and halves are exact in float, so a mismatch is a packing bug, not noise.
            values[i] = random.nextInt(2001) - 1000 + (random.nextBoolean() ? 0.5f : 0.0f);
        }
        return values;
    }

    @Test
    void incrementalPackMatchesFullWalkOverRandomWriteSequences() {
        ModProgramRegistry.Program incremental = linkPair(101, 111, 112);
        ModProgramRegistry.Program full = linkPair(102, 113, 114);
        int blockBytes = incremental.lowered().uniformBlockSize();
        assertEquals(blockBytes, full.lowered().uniformBlockSize());
        assertArrayEquals(new float[] {2.5f}, incremental.lowered().uniformDefaults().get("fDefault"),
                "precondition: the declared default is part of the packed state");

        // Step 0: no writes at all - defaults and zeros, packed from scratch by both routes.
        assertArrayEquals(blockBytes(VulkanModProgramPipeline.packUniforms(full), blockBytes),
                blockBytes(packIncremental(incremental), blockBytes), "initial pack");

        Random random = new Random(0xCE17_1AB1L);
        String[] floatNames = FLOAT_MEMBERS.keySet().toArray(new String[0]);
        String[] intNames = INT_MEMBERS.keySet().toArray(new String[0]);
        for (int step = 0; step < 240; step++) {
            int writes = 1 + random.nextInt(3);
            for (int w = 0; w < writes; w++) {
                int kind = random.nextInt(10);
                if (kind < 6) {
                    String name = floatNames[random.nextInt(floatNames.length)];
                    int fullLength = FLOAT_MEMBERS.get(name);
                    // Mostly full writes; sometimes a shorter recorded value for the same member
                    // (a legal glUniform1fv-style partial write of an array/vector member).
                    int length = random.nextInt(4) == 0 ? 1 + random.nextInt(fullLength) : fullLength;
                    float[] values = randomFloats(random, length);
                    writeFloats(101, name, values);
                    writeFloats(102, name, values);
                } else if (kind < 9) {
                    String name = intNames[random.nextInt(intNames.length)];
                    int[] values = new int[INT_MEMBERS.get(name)];
                    for (int i = 0; i < values.length; i++) {
                        values[i] = random.nextInt(2001) - 1000;
                    }
                    writeInts(101, name, values);
                    writeInts(102, name, values);
                } else {
                    // A sampler write takes a location and a value but owns no block bytes.
                    writeInts(101, "tex", new int[] {random.nextInt(8)});
                    writeInts(102, "tex", new int[] {random.nextInt(8)});
                }
            }
            byte[] expected = blockBytes(VulkanModProgramPipeline.packUniforms(full), blockBytes);
            byte[] actual = blockBytes(packIncremental(incremental), blockBytes);
            assertArrayEquals(expected, actual, "block bytes diverged at step " + step);
        }
    }

    @Test
    void incrementalPackMatchesFullWalkWhenAWriteShortensAMember() {
        ModProgramRegistry.Program incremental = linkPair(121, 131, 132);
        ModProgramRegistry.Program full = linkPair(122, 133, 134);
        int blockBytes = incremental.lowered().uniformBlockSize();
        writeFloats(121, "v4", new float[] {1f, 2f, 3f, 4f});
        writeFloats(122, "v4", new float[] {1f, 2f, 3f, 4f});
        packIncremental(incremental);
        VulkanModProgramPipeline.packUniforms(full);
        // The same member is rewritten with fewer components: the tail the first write left in the
        // retained block must not survive - the full walk's fresh buffer reads it as zero.
        writeFloats(121, "v4", new float[] {9f});
        writeFloats(122, "v4", new float[] {9f});
        assertArrayEquals(blockBytes(VulkanModProgramPipeline.packUniforms(full), blockBytes),
                blockBytes(packIncremental(incremental), blockBytes),
                "a shortened rewrite must zero the member's tail, as the full walk does");
    }

    @Test
    void incrementalPackMatchesFullWalkAfterRelink() {
        ModProgramRegistry.Program incremental = linkPair(141, 151, 152);
        ModProgramRegistry.Program full = linkPair(142, 153, 154);
        writeFloats(141, "v3", new float[] {5f, 6f, 7f});
        writeFloats(142, "v3", new float[] {5f, 6f, 7f});
        packIncremental(incremental);
        VulkanModProgramPipeline.packUniforms(full);
        // A re-link starts the values and the retained block over; the first pack under it is full.
        ModProgramRegistry.link(141);
        ModProgramRegistry.link(142);
        int blockBytes = incremental.lowered().uniformBlockSize();
        writeFloats(141, "fScalar", new float[] {3.5f});
        writeFloats(142, "fScalar", new float[] {3.5f});
        assertArrayEquals(blockBytes(VulkanModProgramPipeline.packUniforms(full), blockBytes),
                blockBytes(packIncremental(incremental), blockBytes), "first pack after relink");
    }

    // ---- (2) PER_FRAME shared block --------------------------------------------------------------

    private static final String PER_FRAME_FRAGMENT = """
            #version 120
            uniform float frameTime;
            uniform vec3 cameraPosition;
            uniform float sunAngle;
            uniform int frameCounter;
            uniform float other;
            void main() { gl_FragColor = vec4(frameTime + sunAngle + other) + vec4(cameraPosition, float(frameCounter)); }
            """;

    private static ModProgramRegistry.Program linkPerFrame(int programId, int vertexId, int fragmentId) {
        ModProgramRegistry.shaderSource(vertexId, VERTEX);
        ModProgramRegistry.shaderSource(fragmentId, PER_FRAME_FRAGMENT);
        ModProgramRegistry.attach(programId, vertexId);
        ModProgramRegistry.attach(programId, fragmentId);
        ModProgramRegistry.Program program = ModProgramRegistry.link(programId);
        assertTrue(program.lowered().ok(), String.valueOf(program.lowered().failure()));
        return program;
    }

    @Test
    void sharedBlockLayoutIsTheContractualStd140Layout() {
        // Independent std140 recomputation: mat4 align 16 size 64, mat3 align 16 size 48 (three
        // 16-byte columns), vec3 align 16 size 12, vec2 align 8 size 8, scalar align 4 size 4.
        int offset = 0;
        for (Map.Entry<String, String> member : GlsmPackState.PER_FRAME_MEMBERS.entrySet()) {
            int align = switch (member.getValue()) {
                case "mat4", "mat3", "vec3", "vec4" -> 16;
                case "vec2" -> 8;
                default -> 4;
            };
            int size = switch (member.getValue()) {
                case "mat4" -> 64;
                case "mat3" -> 48;
                case "vec4" -> 16;
                case "vec3" -> 12;
                case "vec2" -> 8;
                default -> 4;
            };
            offset = (offset + align - 1) / align * align;
            int[] layout = GlsmPackState.perFrameLayout(member.getKey());
            assertNotNull(layout, member.getKey());
            assertEquals(offset, layout[0], member.getKey() + " offset");
            assertEquals(size, layout[1], member.getKey() + " size");
            offset += size;
        }
        assertEquals((offset + 15) / 16 * 16, GlsmPackState.PER_FRAME_BLOCK_BYTES);
    }

    @Test
    void classificationFollowsTheContract() {
        assertEquals(GlsmPackState.Bucket.PER_FRAME, GlsmPackState.classify("frameTime", "float", 0));
        assertEquals(GlsmPackState.Bucket.PER_FRAME, GlsmPackState.classify("cameraPosition", "vec3", 0));
        // A same-named member of a non-contractual type is not shared: it stays program-private.
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("frameTime", "vec3", 0));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("frameTime", "float", 2));
        assertEquals(GlsmPackState.Bucket.PER_PASS, GlsmPackState.classify("shadowProjection", "mat4", 0));
        assertEquals(GlsmPackState.Bucket.PER_TICK, GlsmPackState.classify("worldTime", "float", 0));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("anythingElse", "float", 0));
    }

    @Test
    void harvestKeepsTheFirstWritersValuesForTheEpoch() {
        ModProgramRegistry.Program first = linkPerFrame(161, 171, 172);
        ModProgramRegistry.Program second = linkPerFrame(162, 173, 174);
        writeFloats(161, "frameTime", new float[] {1.5f});
        writeFloats(161, "cameraPosition", new float[] {10f, 20f, 30f});
        writeInts(161, "frameCounter", new int[] {7});
        writeFloats(162, "frameTime", new float[] {1.5f});
        writeFloats(162, "cameraPosition", new float[] {-1f, -2f, -3f});
        writeFloats(162, "sunAngle", new float[] {0.25f});
        writeInts(162, "frameCounter", new int[] {99});

        long epoch = 9_000_000L + System.nanoTime() % 1_000_000L;
        long harvestsBefore = GlsmPackState.harvests();
        long divergencesBefore = GlsmPackState.divergences();
        Map<String, float[]> harvest = GlsmPackState.harvest(first, epoch);
        assertEquals(harvestsBefore + 1, GlsmPackState.harvests(), "one harvest per epoch");
        assertArrayEquals(new float[] {1.5f}, harvest.get("frameTime"));
        assertArrayEquals(new float[] {10f, 20f, 30f}, harvest.get("cameraPosition"));
        assertArrayEquals(new float[] {7f}, harvest.get("frameCounter"),
                "an int member is harvested as its float bits on the first offer");

        Map<String, float[]> again = GlsmPackState.harvest(second, epoch);
        assertEquals(harvestsBefore + 1, GlsmPackState.harvests(), "later offers do not re-harvest");
        assertEquals(divergencesBefore + 1, GlsmPackState.divergences(),
                "exactly one member diverged (cameraPosition); the identical frameTime did not");
        assertArrayEquals(new float[] {10f, 20f, 30f}, again.get("cameraPosition"),
                "first writer stands for the epoch");
        assertArrayEquals(new float[] {0.25f}, again.get("sunAngle"),
                "a member the first writer never wrote is adopted from a later offer");
        assertArrayEquals(new float[] {7f}, again.get("frameCounter"),
                "the harvested int member is not overwritten by a later offer");

        GlsmPackState.harvest(second, epoch + 1);
        assertEquals(harvestsBefore + 2, GlsmPackState.harvests(), "a new epoch harvests again");
        assertArrayEquals(new float[] {-1f, -2f, -3f},
                GlsmPackState.harvest(second, epoch + 1).get("cameraPosition"),
                "the new epoch's first writer is the offering program");
    }

    // ---- (3) REGION table value column -------------------------------------------------------------

    private static final String REGION_VERTEX = """
            #version 120
            uniform vec3 chunkOffset;
            uniform mat4 dynM;
            attribute vec2 position;
            void main() { gl_Position = dynM * vec4(position + chunkOffset.xy, chunkOffset.z, 1.0); }
            """;

    @Test
    void regionClassificationRequiresTheTableShape() {
        assertEquals(GlsmPackState.Bucket.REGION, GlsmPackState.classify("chunkOffset", "vec3", 0));
        assertEquals(GlsmPackState.Bucket.REGION, GlsmPackState.classify("u_RegionOffset", "vec4", 0));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("chunkOffset", "vec3", 4));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("chunkOffset", "mat3", 0));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, GlsmPackState.classify("otherOffset", "vec3", 0));
    }

    @Test
    void regionTableSlotCarriesTheSameValueColumnAsTheOldBlockSlice() {
        ModProgramRegistry.shaderSource(191, REGION_VERTEX);
        ModProgramRegistry.shaderSource(192, "#version 120\nvoid main() { gl_FragColor = vec4(1.0); }\n");
        ModProgramRegistry.attach(181, 191);
        ModProgramRegistry.attach(181, 192);
        ModProgramRegistry.Program program = ModProgramRegistry.link(181);
        assertTrue(program.lowered().ok(), String.valueOf(program.lowered().failure()));
        float[] offset = {10.5f, -3.25f, 7.75f};
        float[] matrix = new float[16];
        Arrays.setAll(matrix, i -> i * 0.5f);
        writeFloats(181, "chunkOffset", offset);
        writeFloats(181, "dynM", matrix);

        // The old delivery: the member's slice of the fully walked block.
        ByteBuffer block = VulkanModProgramPipeline.packUniforms(program);
        ModGlslTranslator.UniformMember member = program.lowered().uniforms().stream()
                .filter(m -> m.name().equals("chunkOffset")).findFirst().orElseThrow();
        byte[] slice = new byte[12];
        for (int i = 0; i < 12; i++) {
            slice[i] = block.get(member.offset() + i);
        }
        for (int i = 0; i < 3; i++) {
            assertEquals(Float.floatToRawIntBits(offset[i]),
                    Float.floatToRawIntBits(block.getFloat(member.offset() + i * 4)),
                    "old block slice component " + i);
        }

        // The new delivery: one table slot as celtium$writeRegionOffset writes it - the recorded
        // floats at 16-byte stride, w = 0 (VulkanModProgramPipeline.celtium$writeRegionOffset).
        float[] recorded = program.floats("chunkOffset");
        assertArrayEquals(offset, recorded, "the recorded value column is the written one");
        ByteBuffer slot = ByteBuffer.allocateDirect(16).order(ByteOrder.nativeOrder());
        slot.putFloat(0, recorded[0]);
        slot.putFloat(4, recorded[1]);
        slot.putFloat(8, recorded[2]);
        slot.putFloat(12, 0.0f);
        byte[] slotBytes = new byte[16];
        for (int i = 0; i < 16; i++) {
            slotBytes[i] = slot.get(i);
        }
        assertArrayEquals(slice, Arrays.copyOf(slotBytes, 12),
                "the table slot's xyz bytes are the old block slice's bytes");
        assertEquals(0, Float.floatToRawIntBits(slot.getFloat(12)), "the slot's w is zero");
    }
}
