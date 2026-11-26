package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stage 5 S5-3 (the particle family's record-reading migration), pinned at the layers a CPU gate can
 * reach: the repack table's byte-level equivalence for the four arrival forms, the records shader pair's
 * equivalence points against the deleted push variant (read as source, the shape the sibling contract
 * tests established for device-bound mechanisms), the order classification's pure decision exercised
 * directly, and the wiring - the probe's moved vehicle, the twin factory's state coverage, and the
 * dedicated accessor on {@code IndirectImmediateRuns} the stage-6 hand-off names.
 */
class ParticleRecordsTwinContractTest {

    private static final String BACKEND =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java";
    private static final String INDIRECT =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/IndirectImmediateRuns.java";
    private static final String TWIN =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateParticleRecordsPipeline.java";
    private static final String RECORDS_VERT =
            "celtium-vulkan/src/main/shaders/immediate_particle_records.vert";
    private static final String RECORDS_FRAG =
            "celtium-vulkan/src/main/shaders/immediate_particle_records.frag";

    // ---- (i) the repack's arrival forms reach the twin's stride unchanged ----------------------------

    /** Applies a repack segment table the way the uploader does: pairs copied in order, target packed. */
    private static byte[] applySegments(byte[] source, int[] segments) {
        byte[] target = new byte[28];
        int at = 0;
        for (int i = 0; i < segments.length; i += 2) {
            System.arraycopy(source, segments[i], target, at, segments[i + 1]);
            at += segments[i + 1];
        }
        assertEquals(28, at, "the segments rebuild exactly one canonical vertex");
        return target;
    }

    private static byte[] numberedVertex(int size) {
        byte[] vertex = new byte[size];
        for (int i = 0; i < size; i++) {
            vertex[i] = (byte) i;
        }
        return vertex;
    }

    @Test
    @DisplayName("the canonical particle form arrives unrepacked, at the stride the twin and the accept path agree on")
    void canonicalFormArrivesUnrepacked() {
        assertNull(VulkanImmediateFormats.particleRepack(VulkanImmediateFormats.LAYOUT_PARTICLE, 28),
                "PARTICLE as it arrives is never repacked: the vertex bytes pass through unchanged");
        assertEquals(VulkanImmediateFormats.PARTICLE,
                VulkanImmediateFormats.layout(VulkanImmediateFormats.LAYOUT_PARTICLE, 28, 4));
        assertEquals(28, VulkanImmediateParticleRecordsPipeline.VERTEX_BYTES,
                "the twin's vertex width is the canonical particle stride");
        assertEquals(VulkanImmediateParticleRecordsPipeline.VERTEX_BYTES,
                VulkanTerrainGpuBackend.celtium$expectedStride(VulkanImmediateFormats.PARTICLE),
                "the accept path's stride answer for format 5 is the twin's width");
    }

    @Test
    @DisplayName("the lightmap-before-colour form repacks by swapping the two four-byte elements back")
    void lmapColorFormSwapsColourAndLightmap() {
        int[] segments = VulkanImmediateFormats.particleRepack(
                VulkanImmediateFormats.LAYOUT_PARTICLE_LMAP_COLOR, 28);
        assertArrayEquals(new int[] {0, 20, 24, 4, 20, 4}, segments);
        byte[] arrived = numberedVertex(28);
        byte[] canonical = applySegments(arrived, segments);
        // Position and UV (0..20) are shared; the colour (arrived at 24..28) lands at 20..24 and the
        // light map (arrived at 20..24) at 24..28 - PARTICLE's own order.
        for (int i = 0; i < 20; i++) {
            assertEquals(arrived[i], canonical[i], "the shared prefix is byte-identical at " + i);
        }
        for (int i = 0; i < 4; i++) {
            assertEquals(arrived[24 + i], canonical[20 + i], "the colour element moves to offset 20");
            assertEquals(arrived[20 + i], canonical[24 + i], "the light map element moves to offset 24");
        }
    }

    @Test
    @DisplayName("the 31-byte normal form repacks to its own first 28 bytes")
    void normalFormKeepsTheFirst28Bytes() {
        int[] segments = VulkanImmediateFormats.particleRepack(
                VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL, 31);
        assertArrayEquals(new int[] {0, 28}, segments);
        byte[] arrived = numberedVertex(31);
        assertArrayEquals(java.util.Arrays.copyOf(arrived, 28), applySegments(arrived, segments),
                "the trailing normal is not part of the picture and not part of the canonical vertex");
    }

    @Test
    @DisplayName("the 32-byte normal-and-pad form repacks to its own first 28 bytes")
    void normalPadFormKeepsTheFirst28Bytes() {
        int[] segments = VulkanImmediateFormats.particleRepack(
                VulkanImmediateFormats.LAYOUT_PARTICLE_NORMAL_PAD, 32);
        assertArrayEquals(new int[] {0, 28}, segments);
        byte[] arrived = numberedVertex(32);
        assertArrayEquals(java.util.Arrays.copyOf(arrived, 28), applySegments(arrived, segments));
    }

    // ---- (ii) the records shaders' equivalence points -------------------------------------------------

    @Test
    @DisplayName("the light map coordinate keeps vanilla's scale and bias")
    void lightmapScaleAndBias() throws IOException {
        assertTrue(read(RECORDS_VERT).contains("(vec2(a_LightCoord) + 8.0) / 256.0"),
                "scale 1/256 with the +8 texel-centre bias, the push variant's own conversion");
    }

    @Test
    @DisplayName("the texture transform is the record's affine under the same selector")
    void textureTransformFromTheRecord() throws IOException {
        String vert = read(RECORDS_VERT);
        assertTrue(vert.contains("if (record.u_TexB.z > 0.5) {"),
                "the transform selector is the record's, as it was the push block's");
        assertTrue(vert.contains("record.u_TexA.x * a_TexCoord.x + record.u_TexA.z * a_TexCoord.y"
                + " + record.u_TexB.x"), "the affine is evaluated exactly as the push variant evaluated it");
        assertTrue(vert.contains("v_Color = a_Color;"),
                "the vertex colour passes through unmodulated, as the push variant passed it");
    }

    @Test
    @DisplayName("the fragment modulation order is texture times colour times light map, alpha from texture and colour")
    void fragmentModulationOrder() throws IOException {
        String frag = read(RECORDS_FRAG);
        assertTrue(frag.contains("textureColour * v_Color * texture(u_Lightmap, v_Lightmap)"),
                "the multiplication order is the push variant's");
        assertTrue(frag.contains("colour.a = textureColour.a * v_Color.a;"),
                "the alpha product does not include the light map, as before");
    }

    @Test
    @DisplayName("the alpha reference arrives per record and discards below itself")
    void alphaReferenceFromTheRecord() throws IOException {
        assertTrue(read(RECORDS_VERT).contains("v_AlphaRef = record.u_AlphaColour.x;"),
                "the reference is read from the record's alpha slot");
        assertTrue(read(RECORDS_FRAG).contains("if (v_AlphaRef > 0.0 && colour.a < v_AlphaRef) {"),
                "the discard condition is the push variant's: a positive reference, a strict comparison");
    }

    @Test
    @DisplayName("a missing light map refuses the descriptor by name, as the prepare's fallback requires")
    void lightmapAbsenceRefusesByName() throws IOException {
        String twin = read(TWIN);
        int descriptor = twin.indexOf("public long descriptorSetFor(");
        int absent = twin.indexOf("return fail(\"lightmap object absent\");", descriptor);
        assertTrue(descriptor >= 0 && absent > descriptor,
                "the twin's descriptor query names the absent light map instead of building a set without it");
    }

    // ---- (iii) the order classification ---------------------------------------------------------------

    private static ImmediateGroup groupWithState(int blend, int depthTest, int depthWrite) {
        int[] state = new int[16];
        state[0] = blend;
        state[3] = depthTest;
        state[4] = depthWrite;
        return new ImmediateGroup(null, null, null, VulkanImmediateFormats.PARTICLE, 0, 0L, state, 1L);
    }

    @Test
    @DisplayName("an opaque particle group (no blend, depth test on, depth writes on) is order-free")
    void opaqueParticleOrderIsFree() {
        assertTrue(VulkanTerrainGpuBackend.celtium$batchOrderFree(groupWithState(0, 1, 1)));
    }

    @Test
    @DisplayName("a blended particle group is not order-free and keeps its capture order")
    void blendedParticleOrderIsNotFree() {
        assertFalse(VulkanTerrainGpuBackend.celtium$batchOrderFree(groupWithState(1, 1, 1)));
    }

    @Test
    @DisplayName("a group that does not write depth is not order-free")
    void depthWriteOffIsNotFree() {
        assertFalse(VulkanTerrainGpuBackend.celtium$batchOrderFree(groupWithState(0, 1, 0)));
        assertFalse(VulkanTerrainGpuBackend.celtium$batchOrderFree(groupWithState(0, 0, 1)),
                "and neither is one that does not test depth");
    }

    @Test
    @DisplayName("the classification is what the capture hands the batcher as its ordered flag")
    void classificationFeedsTheCapture() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("boolean ordered = !particle || !celtium$batchOrderFree(group);"),
                "only a particle group may be captured unordered, and only when its order is free");
        assertTrue(backend.contains("recordIndex, ordered, vertices"),
                "the verdict reaches captureQuads as the ordered argument");
    }

    // ---- (iv) the probe's moved vehicle ----------------------------------------------------------------

    @Test
    @DisplayName("the descriptor probe's vehicle is the record-reading twin")
    void probeVehicleIsTheTwin() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains("private VulkanImmediateParticleRecordsPipeline descriptorProbe;"),
                "the probe field holds a twin");
        assertTrue(backend.contains("VulkanImmediateParticleRecordsPipeline.create(this.context, renderPass,"),
                "the probe is built through the twin's factory, against the batch's own pass as before");
        assertTrue(backend.contains("this.drawRecords.descriptorSetLayout());"),
                "the probe's twin is built against the record store's layout");
    }

    @Test
    @DisplayName("the probe gate's question and its failure surface are the ones the gate always had")
    void probeGateAndFailureSurfaceUnchanged() throws IOException {
        String backend = read(BACKEND);
        assertTrue(backend.contains(
                        "this.descriptorProbe.descriptorSetFor(texture, this.lightmapTexture), \"probe\") != 0L;"),
                "the gate still asks the probe for the batch's (texture, light map) set");
        assertTrue(backend.contains(
                        "this.immediateStatistics.setParticleDescriptorFailureReason(this.descriptorProbe.lastFailure());"),
                "the failure reason still reaches the same statistics surface");
    }

    @Test
    @DisplayName("the probe is released with the immediate pipelines")
    void probeIsReleasedWithThePipelines() throws IOException {
        assertTrue(read(BACKEND).contains(
                        "releaseDirect(this.descriptorProbe == null ? null : () -> this.descriptorProbe.delete())"),
                "the release seat moved with the vehicle");
    }

    // ---- (v) the twin factory's state coverage -----------------------------------------------------------

    @Test
    @DisplayName("the blend state the twin is built with is the batch's captured own (BH-07)")
    void blendComesFromTheCapturedState() throws IOException {
        String backend = read(BACKEND);
        int branch = backend.indexOf("celtium$recordsParticlePipeline(");
        assertTrue(branch >= 0, "the creation path resolves the twin through the dedicated accessor");
        assertTrue(backend.substring(branch, branch + 300).contains("celtium$blendState(state)"),
                "the blend handed to the factory is mapped from the captured state, never asserted");
    }

    @Test
    @DisplayName("the accessor builds with the family's depth and cull constants and the record store's layout")
    void accessorBuildsWithTheFamilyConstants() throws IOException {
        String indirect = read(INDIRECT);
        assertTrue(indirect.contains("celtium$recordsParticlePipeline(int format, long key, long pass,"),
                "the dedicated accessor exists on the runs class (the stage-6 relocation hand-off)");
        assertTrue(indirect.contains("celtium$immediateBranchKey(format, key, pass)"),
                "the accessor derives its cache key through the pass-keyed branch fold (R-06)");
        assertTrue(indirect.contains("true, false, VulkanCullState.NONE, depthCompareOp,"),
                "depth test on, depth writes off, no culling: the family's asserted constants");
        assertTrue(indirect.contains("b.drawRecords.descriptorSetLayout(), colorAttachments);"),
                "the twin is built against the record store's layout with the pass's colour count");
    }

    private static String read(String repoRelative) throws IOException {
        return Files.readString(findRepoFile(repoRelative));
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
