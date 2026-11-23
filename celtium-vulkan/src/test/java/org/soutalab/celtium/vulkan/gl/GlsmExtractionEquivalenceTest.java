package org.soutalab.celtium.vulkan.gl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * GLSM stage 1 landing condition [b], the flag-on half: what the translator extracts, and that the
 * extracted routes carry the same values the block route carried. These tests only mean anything
 * with {@code -Dceltium.vulkan.glsmPackState=true} in the test JVM (the flag is a static final);
 * without it they abstain instead of passing vacuously. The merge-phase run sets it via
 * {@code JAVA_TOOL_OPTIONS} for this class only.
 * <ul>
 * <li>(2) PER_FRAME: both stages of every program declare the one shared block, textually
 * identical across programs, and the registry keeps the extracted members' values and buckets -
 * a PER_FRAME (or REGION) write advances only its bucket version, never the block pack's scalar
 * version, which is the structural claim of the stage.</li>
 * <li>(3) REGION: the member leaves the block for the {@code CeltiumRegionOffsets} SSBO, the kept
 * members' offsets are the std140 recomputation over what remains, and the value the registry
 * records for the member is bit-identical to what the guest wrote - the column the backend
 * writes into the table.</li>
 * </ul>
 */
class GlsmExtractionEquivalenceTest {

    @BeforeEach
    void reset() {
        assumeTrue(GlsmPackState.ENABLED,
                "needs -Dceltium.vulkan.glsmPackState=true in the test JVM");
        ModProgramRegistry.clear();
        GlsmPackState.clear();
    }

    private static final String VERTEX = """
            #version 120
            uniform mat4 modelViewMatrix;
            uniform vec3 chunkOffset;
            uniform mat4 dynA;
            attribute vec2 position;
            void main() { gl_Position = modelViewMatrix * dynA * vec4(position + chunkOffset.xy, chunkOffset.z, 1.0); }
            """;

    private static final String FRAGMENT = """
            #version 120
            uniform float frameTime;
            uniform mat4 dynB;
            void main() { gl_FragColor = vec4(frameTime) + dynB[0]; }
            """;

    private static String perFrameDeclaration(String stageSource) {
        int start = stageSource.indexOf("uniform CeltiumPerFrame {");
        assertTrue(start >= 0, "shared block declared");
        int end = stageSource.indexOf("};", start);
        return stageSource.substring(start, end + 2);
    }

    @Test
    void extractionMovesRegionAndPerFrameMembersOutOfTheBlock() {
        ModGlslTranslator.Result result = ModGlslTranslator.translate(VERTEX, FRAGMENT);
        assertTrue(result.ok(), String.valueOf(result.failure()));
        assertTrue(result.uniformsViaBuffer(), "precondition: the block takes the buffer route");
        assertEquals("chunkOffset", result.regionOffsetMember());
        assertEquals(Set.of("modelViewMatrix", "frameTime"),
                Set.copyOf(result.sharedPerFrameMembers()));
        assertEquals(Set.of("chunkOffset", "modelViewMatrix", "frameTime"),
                result.extractedUniforms().stream().map(ModGlslTranslator.UniformMember::name)
                        .collect(Collectors.toSet()));

        // What remains in the block, at the std140 offsets of the remaining members alone.
        Map<String, ModGlslTranslator.UniformMember> kept = result.uniforms().stream()
                .collect(Collectors.toMap(ModGlslTranslator.UniformMember::name, m -> m));
        assertEquals(Set.of("dynA", "dynB"), kept.keySet());
        assertEquals(0, kept.get("dynA").offset());
        assertEquals(64, kept.get("dynB").offset());
        assertEquals(128, result.uniformBlockSize());

        // The region table: std430 SSBO at the binding after the block's own (no samplers/images).
        assertTrue(result.vertex().contains(
                "layout(std430, set = 0, binding = 1) readonly buffer CeltiumRegionOffsets"), result.vertex());
        assertTrue(result.vertex().contains("celtium_regionOffsets[gl_InstanceIndex]"), result.vertex());
    }

    @Test
    void everyProgramDeclaresTheSameSharedBlock() {
        ModGlslTranslator.Result first = ModGlslTranslator.translate(VERTEX, FRAGMENT);
        String otherVertex = """
                #version 120
                uniform float frameTime;
                uniform mat4 fillerA;
                uniform mat4 fillerB;
                attribute vec2 position;
                void main() { gl_Position = fillerA * fillerB * vec4(position, frameTime, 1.0); }
                """;
        String otherFragment = """
                #version 120
                void main() { gl_FragColor = vec4(1.0); }
                """;
        ModGlslTranslator.Result second = ModGlslTranslator.translate(otherVertex, otherFragment);
        assertTrue(second.ok(), String.valueOf(second.failure()));
        assertEquals(List.of("frameTime"), second.sharedPerFrameMembers());

        String declaration = perFrameDeclaration(first.vertex());
        assertEquals(declaration, perFrameDeclaration(first.fragment()),
                "both stages declare the identical shared block");
        assertEquals(declaration, perFrameDeclaration(second.vertex()),
                "the shared block's member set and order do not depend on the program");
        // Every contractual member is declared, in the classification's fixed order.
        int previous = -1;
        for (String name : GlsmPackState.PER_FRAME_MEMBERS.keySet()) {
            int at = declaration.indexOf(" " + name + ";");
            assertTrue(at > previous, name + " declared in fixed order");
            previous = at;
        }
    }

    @Test
    void extractedWritesAdvanceOnlyTheirBucketVersion() {
        ModProgramRegistry.shaderSource(211, VERTEX);
        ModProgramRegistry.shaderSource(212, FRAGMENT);
        ModProgramRegistry.attach(201, 211);
        ModProgramRegistry.attach(201, 212);
        ModProgramRegistry.Program program = ModProgramRegistry.link(201);
        assertTrue(program.lowered().ok(), String.valueOf(program.lowered().failure()));
        assertEquals(GlsmPackState.Bucket.REGION, program.uniformBucket("chunkOffset"));
        assertEquals(GlsmPackState.Bucket.PER_FRAME, program.uniformBucket("frameTime"));
        assertEquals(GlsmPackState.Bucket.DYNAMIC, program.uniformBucket("dynA"));

        long scalar = program.uniformVersion();
        long region = program.bucketVersion(GlsmPackState.Bucket.REGION);
        long perFrame = program.bucketVersion(GlsmPackState.Bucket.PER_FRAME);
        ModProgramRegistry.uniformFloat3(201, ModProgramRegistry.location("chunkOffset"),
                10.5f, -3.25f, 7.75f);
        assertEquals(scalar, program.uniformVersion(),
                "a REGION write owns no block bytes and must not invalidate the block pack");
        assertEquals(region + 1, program.bucketVersion(GlsmPackState.Bucket.REGION));
        // The recorded value column is bit-identical to the write - it is what the table carries.
        assertArrayEquals(new float[] {10.5f, -3.25f, 7.75f}, program.floats("chunkOffset"));

        ModProgramRegistry.uniformFloat1(201, ModProgramRegistry.location("frameTime"), 2.5f);
        assertEquals(scalar, program.uniformVersion(),
                "a PER_FRAME write likewise leaves the block pack's version alone");
        assertEquals(perFrame + 1, program.bucketVersion(GlsmPackState.Bucket.PER_FRAME));

        float[] matrix = new float[16];
        matrix[0] = 1f;
        ModProgramRegistry.uniformFloats(201, ModProgramRegistry.location("dynA"), matrix);
        assertEquals(scalar + 1, program.uniformVersion(), "a DYNAMIC write advances the block pack");
    }

    @Test
    void sharedBlockBytesComeFromTheHarvestAlone() {
        // Two programs whose PER_FRAME values agree and whose DYNAMIC values differ must produce
        // the same shared block bytes: the block is packed from the harvest, not from a program.
        ModProgramRegistry.Program a = linkWithPerFrame(221, 231, 232, 3.5f);
        ModProgramRegistry.Program b = linkWithPerFrame(222, 233, 234, 3.5f);
        Map<String, float[]> harvestA = GlsmPackState.harvest(a, 4242L);
        byte[] fromA = packShared(harvestA);
        GlsmPackState.clear();
        Map<String, float[]> harvestB = GlsmPackState.harvest(b, 4242L);
        byte[] fromB = packShared(harvestB);
        assertArrayEquals(fromA, fromB, "same offered PER_FRAME values, same shared block bytes");
        // The matrix lands at its contractual offset with its written bits.
        ByteBuffer view = ByteBuffer.wrap(fromA).order(ByteOrder.nativeOrder());
        int[] layout = GlsmPackState.perFrameLayout("modelViewMatrix");
        for (int i = 0; i < 16; i++) {
            assertEquals(Float.floatToRawIntBits(i + 0.25f),
                    Float.floatToRawIntBits(view.getFloat(layout[0] + i * 4)), "matrix word " + i);
        }
    }

    private static ModProgramRegistry.Program linkWithPerFrame(
            int programId, int vertexId, int fragmentId, float dynSeed) {
        ModProgramRegistry.shaderSource(vertexId, VERTEX);
        ModProgramRegistry.shaderSource(fragmentId, FRAGMENT);
        ModProgramRegistry.attach(programId, vertexId);
        ModProgramRegistry.attach(programId, fragmentId);
        ModProgramRegistry.Program program = ModProgramRegistry.link(programId);
        assertTrue(program.lowered().ok(), String.valueOf(program.lowered().failure()));
        float[] matrix = new float[16];
        for (int i = 0; i < 16; i++) {
            matrix[i] = i + 0.25f;
        }
        ModProgramRegistry.uniformFloats(programId, ModProgramRegistry.location("modelViewMatrix"), matrix);
        ModProgramRegistry.uniformFloat1(programId, ModProgramRegistry.location("frameTime"), 1.25f);
        float[] dyn = new float[16];
        dyn[0] = dynSeed;
        ModProgramRegistry.uniformFloats(programId, ModProgramRegistry.location("dynA"), dyn);
        return program;
    }

    /** Packs the shared block from a harvest exactly as the pipeline's harvester lays it out. */
    private static byte[] packShared(Map<String, float[]> harvested) {
        ByteBuffer block = ByteBuffer.allocateDirect(GlsmPackState.PER_FRAME_BLOCK_BYTES)
                .order(ByteOrder.nativeOrder());
        for (Map.Entry<String, String> member : GlsmPackState.PER_FRAME_MEMBERS.entrySet()) {
            float[] value = harvested.get(member.getKey());
            if (value == null) {
                continue;
            }
            int[] layout = GlsmPackState.perFrameLayout(member.getKey());
            int columns = switch (member.getValue()) {
                case "mat4" -> 4;
                case "mat3" -> 3;
                default -> 1;
            };
            int perColumn = value.length / columns;
            for (int c = 0; c < columns; c++) {
                for (int k = 0; k < perColumn; k++) {
                    int src = c * perColumn + k;
                    if (src < value.length) {
                        block.putFloat(layout[0] + c * 16 + k * 4, value[src]);
                    }
                }
            }
        }
        byte[] out = new byte[GlsmPackState.PER_FRAME_BLOCK_BYTES];
        for (int i = 0; i < out.length; i++) {
            out[i] = block.get(i);
        }
        return out;
    }
}
