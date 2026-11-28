package org.soutalab.celtium.forge;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

/**
 * The raw-FBO identity probe's reflective seats, pinned at the source seam the offline suite can
 * check.
 * <p>
 * {@code RawFboIdentityProbe} reaches the live backend reflectively: a class-name gate, then
 * fields and methods resolved by name. The E7 dismantling of the {@code VulkanTerrainGpuBackend}
 * monolith moved most of those seats without moving the probe, so its gate never matched the
 * installed {@code VulkanBackendAdapter} and the probe reported FAILED unconditionally - a dead
 * verification that still printed into leg output. The re-seat points the gate at the adapter and
 * resolves the moved seats (target registry, reported framebuffer identity, off-target target,
 * raw-framebuffer adoption/publication) through the adapter's {@code funnel} field onto
 * {@code Lane2ImmediateRecorder}, where they now live; the publication map is read off the
 * {@code PresentSwapchain} instance that owns it. These pins tie both halves - the probe's
 * reflective names and the production declarations they name - so the next dismantling move
 * fails here instead of silently killing the probe again. The probe's behaviour in a running
 * game is covered by the fixture legs that opt into it ({@code rawFboIdentityProbe}).
 */
class RawFboIdentityProbeSeatPinTest {

    private static final String PROBE =
            "validation/client/src/main/java/org/soutalab/celtium/validation/RawFboIdentityProbe.java";
    private static final String BACKEND_ADAPTER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanBackendAdapter.java";
    private static final String RECORDER =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/Lane2ImmediateRecorder.java";
    private static final String TARGET_REGISTRY =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/RenderTargetRegistry.java";
    private static final String PRESENT_SWAPCHAIN =
            "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/PresentSwapchain.java";

    @Test
    void theProbeGateNamesTheInstalledBackendClass() throws Exception {
        String probe = Files.readString(findRepoFile(PROBE, "../" + PROBE));
        assertTrue(probe.contains("endsWith(\"VulkanBackendAdapter\")"),
                "the probe gate compares the live backend's class name against the installed adapter");
        assertFalse(probe.contains("VulkanTerrainGpuBackend"),
                "the deleted monolith's class name no longer appears in the probe at all");
        assertFalse(probe.contains("import org.soutalab.celtium.vulkan.VulkanBackendAdapter;"),
                "the gate stays a string comparison; the adapter type is not compile-linked into the probe");
    }

    @Test
    void theProbeResolvesTheMovedSeatsThroughTheFunnel() throws Exception {
        String probe = Files.readString(findRepoFile(PROBE, "../" + PROBE));
        assertTrue(probe.contains("backendClass.getDeclaredField(\"funnel\")"),
                "the probe reaches the recorder through the adapter's funnel field");
        assertTrue(probe.contains("recorderClass.getDeclaredField(\"targetRegistry\")"),
                "the target registry resolves on the recorder, not the backend");
        assertTrue(probe.contains("recorderClass.getDeclaredField(\"currentFramebufferIdentity\")"),
                "the reported framebuffer identity resolves on the recorder, not the backend");
        assertTrue(probe.contains("recorderClass.getDeclaredMethod(\"offTargetTarget\")"),
                "the off-target target resolves on the recorder, not the backend");
        assertTrue(probe.contains(
                "recorderClass.getDeclaredMethod(\"celtium$adoptRawFramebufferColorTexture\", int.class)"),
                "raw-framebuffer adoption resolves on the recorder, not the backend");
        assertTrue(probe.contains(
                "recorderClass.getDeclaredMethod(\"celtium$publishFramebufferColorTexture\", int.class)"),
                "raw-framebuffer publication resolves on the recorder, not the backend");
        assertFalse(probe.contains("backendClass.getDeclaredField(\"targetRegistry\")"),
                "the registry is no longer looked up on the backend class");
        assertFalse(probe.contains("backendClass.getDeclaredField(\"currentFramebufferIdentity\")"),
                "the reported identity is no longer looked up on the backend class");
    }

    @Test
    void theProbeReadsThePublicationMapOffTheSwapchain() throws Exception {
        String probe = Files.readString(findRepoFile(PROBE, "../" + PROBE));
        assertTrue(probe.contains("images.get(presentSwapchain)"),
                "the published-frame-images map is read off the PresentSwapchain instance that owns it");
        assertFalse(probe.contains("images.get(backend)"),
                "the map field is never read off the backend object (its declaring class is the swapchain)");
    }

    @Test
    void theProductionSeatsTheProbeNamesExist() throws Exception {
        String adapter = Files.readString(findRepoFile(BACKEND_ADAPTER, "../" + BACKEND_ADAPTER));
        assertTrue(adapter.contains("final Lane2ImmediateRecorder funnel;"),
                "the adapter holds the recorder in the funnel field the probe resolves");
        assertTrue(adapter.contains("final PresentSwapchain presentSwapchain;"),
                "the adapter holds the present swapchain the probe resolves directly");
        assertTrue(adapter.contains("final TextureCopyService textureCopyService;"),
                "the adapter holds the texture copy service the probe resolves directly");
        assertTrue(adapter.contains("public void bindFramebuffer(long targetIdentity, boolean isMain)"),
                "the adapter exposes the bindFramebuffer restore call the probe's cleanup uses");
        assertTrue(adapter.contains("public void celtium$forgetDeletedFramebuffer(long identity)"),
                "the adapter exposes the framebuffer retirement call the probe uses");

        String recorder = Files.readString(findRepoFile(RECORDER, "../" + RECORDER));
        assertTrue(recorder.contains("final RenderTargetRegistry targetRegistry"),
                "the recorder owns the target registry the probe resolves through the funnel");
        assertTrue(recorder.contains("long currentFramebufferIdentity;"),
                "the recorder owns the reported framebuffer identity the probe reads and restores");
        assertTrue(recorder.contains("GllessFrameTarget offTargetTarget()"),
                "the recorder owns the off-target target the probe resolves through the funnel");
        assertTrue(recorder.contains("boolean celtium$adoptRawFramebufferColorTexture(int glTextureName)"),
                "the recorder owns raw-framebuffer adoption");
        assertTrue(recorder.contains("boolean celtium$publishFramebufferColorTexture(int glTextureName)"),
                "the recorder owns raw-framebuffer publication");

        String registry = Files.readString(findRepoFile(TARGET_REGISTRY, "../" + TARGET_REGISTRY));
        assertTrue(registry.contains("public static long rawFramebufferIdentity(int framebuffer)"),
                "the registry keeps the static raw identity derivation the probe calls");

        String swapchain = Files.readString(findRepoFile(PRESENT_SWAPCHAIN, "../" + PRESENT_SWAPCHAIN));
        assertTrue(swapchain.contains("publishedFrameImages"),
                "the present swapchain owns the published-frame-images map the probe inspects");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative, String moduleRelative) {
        for (String candidate : new String[] {repoRelative, moduleRelative, "../" + repoRelative}) {
            Path path = Paths.get(candidate);
            if (Files.exists(path)) {
                return path;
            }
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from "
                + Paths.get("").toAbsolutePath());
    }
}
