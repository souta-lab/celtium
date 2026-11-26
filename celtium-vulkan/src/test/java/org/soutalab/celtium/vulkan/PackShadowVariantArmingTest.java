package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.render.chunk.ChunkRenderMatrices;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * R-17 increment (iv): the pack variant arms in the shadow stage. U3 is
 * determined (OVERHAUL-R17-U3-DETERMINATION-2026-11-26): the shadow stage's
 * matrices are the light's projection x modelView and its occlusion camera is
 * the shadow viewport's transform, so the variant's prepareFrame receives the
 * light viewpoint through the same call shape the main stage uses — with the
 * one change that its view-projection comes from a stage-keyed cache slot, so
 * the light VP never displaces the camera VP under the shared frame id. The
 * composition is exercised directly; the device-bound wiring (the arming
 * condition, the cache's slot selection, the E10 shadow issuance stand-down)
 * is pinned as source contracts.
 */
class PackShadowVariantArmingTest {

    /** The VP composition both cache slots use: the handed projection x modelView, column-major. */
    @Test
    void theCensusVpCompositionIsProjectionTimesModelView() {
        Matrix4f projection = new Matrix4f().perspective(1.1f, 1.4f, 0.05f, 900.0f);
        Matrix4f modelView = new Matrix4f().rotateX(0.35f).rotateY(-0.8f)
                .translate(-120.5f, -64.25f, 340.75f);
        float[] expected = new Matrix4f(projection).mul(modelView).get(new float[16]);
        float[] actual = VulkanTerrainGpuBackend.celtium$composeCensusVp(
                new ChunkRenderMatrices(projection, modelView));
        assertArrayEquals(expected, actual, 0.0f,
                "the composed VP is the handed matrices' product, nothing stage-specific");
    }

    /** A different stage's matrices compose to that stage's VP: the slots differ by input, not by a flag inside the composition. */
    @Test
    void theCompositionFollowsTheHandedMatrices() {
        Matrix4f cameraVp = new Matrix4f().perspective(1.2f, 1.6f, 0.1f, 1000.0f)
                .mul(new Matrix4f().translate(-10.0f, -70.0f, -20.0f));
        Matrix4f lightVp = new Matrix4f().ortho(-120.0f, 120.0f, -120.0f, 120.0f, 0.5f, 400.0f)
                .mul(new Matrix4f().rotateX(0.9f).translate(3.0f, -70.0f, -8.0f));
        float[] camera = VulkanTerrainGpuBackend.celtium$composeCensusVp(
                new ChunkRenderMatrices(cameraVp, new Matrix4f()));
        float[] light = VulkanTerrainGpuBackend.celtium$composeCensusVp(
                new ChunkRenderMatrices(lightVp, new Matrix4f()));
        assertFalse(java.util.Arrays.equals(camera, light),
                "the light's matrices compose to a different VP than the camera's");
    }

    /** The arming condition no longer excludes the shadow stage; the remaining gates are the GLSM ones the main stage has. */
    @Test
    void theVariantArmingDoesNotExcludeTheShadowStage() throws IOException {
        String source = backendSource();
        int armed = source.indexOf("boolean packVariantArmed = false;");
        int candidate = source.indexOf("TerrainPackEngine candidate", armed);
        assertTrue(armed >= 0 && candidate > armed, "the arming block is present");
        String condition = source.substring(armed, candidate);
        assertFalse(condition.contains("!shadowStage"),
                "the shadow stage is not excluded from the variant arming");
        assertTrue(condition.contains("GlsmPackState.ENABLED")
                        && condition.contains("celtium$glsmRegionTable()"),
                "the GLSM gates the main stage arms under are unchanged");
    }

    /** prepareFrame is handed the stage-keyed VP and the occlusion camera's position, the main stage's call shape. */
    @Test
    void theArmingCallHandsTheStageKeyedVpAndTheOcclusionCamera() throws IOException {
        String source = backendSource();
        int call = source.indexOf("candidate.prepareFrame(");
        assertTrue(call >= 0, "the variant's prepareFrame call is present");
        String callSite = source.substring(call, call + 320);
        assertTrue(callSite.contains("celtium$gpuTerrainCensusVp(matrices, shadowStage)"),
                "the VP is read from the stage-keyed cache, keyed by this execution's stage");
        assertTrue(callSite.contains("occlusionCamera.x, occlusionCamera.y, occlusionCamera.z"),
                "the camera argument is the occlusion camera, whose transform is the stage's viewpoint");
    }

    /** The census VP cache keeps a slot per stage, each with its own frame stamp; the main-stage form delegates to the main slot. */
    @Test
    void theCensusVpCacheIsStageKeyed() throws IOException {
        String source = backendSource();
        assertTrue(source.contains("return celtium$gpuTerrainCensusVp(matrices, false);"),
                "the main-stage consumers keep the main slot");
        assertTrue(source.contains("gpuTerrainCensusVpShadowFrame != this.frameId")
                        && source.contains("gpuTerrainCensusVpShadow = celtium$composeCensusVp(matrices);"),
                "the shadow stage fills its own slot under its own frame stamp");
        assertTrue(source.contains("gpuTerrainCensusVp = celtium$composeCensusVp(matrices);"),
                "the main slot is filled from the main stage's matrices only");
    }

    /** When the variant arms the shadow stage, the E10 shadow issuance stands down instead of issuing its generated list as well. */
    @Test
    void theShadowIssuanceStandsDownWhenTheVariantArms() throws IOException {
        String source = backendSource();
        int block = source.indexOf("boolean shadowIssuanceArmed = false;");
        int generatedReset = source.indexOf("this.shadowGpuTerrainDrawGenerated = false;", block);
        assertTrue(block >= 0 && generatedReset > block, "the shadow issuance decision block is present");
        String decision = source.substring(block, generatedReset);
        int variantGate = decision.indexOf("!packVariantArmed");
        int arm = decision.indexOf("shadowIssuanceArmed = true;");
        assertTrue(variantGate >= 0 && arm > variantGate,
                "the issuance arms only when the pack variant did not");
        assertTrue(decision.contains("this.shadowGpuTerrainDraw.cancelArmed();"),
                "the stood-down arm is cancelled, not left live for the loop's end");
    }

    private static String backendSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
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
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
