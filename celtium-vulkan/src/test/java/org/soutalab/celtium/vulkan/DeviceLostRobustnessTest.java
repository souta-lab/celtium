package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.VK10;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two robustness rules of item 416 (issue #425): a device loss must degrade the renderer, not crash it.
 * <p>
 * The reporter's session produced two crash reports from one device loss. The first was the reload teardown:
 * {@code settleGpu}'s {@code vkDeviceWaitIdle} returned {@code VK_ERROR_DEVICE_LOST} and the wait threw, so a
 * scheduled task that only <em>detected</em> the loss died reporting it. The second, three seconds later, was
 * the stall guard: the latch was up, a sky draw was refused, and {@code CeltiumNativeDraw} threw the refusal
 * into {@code RenderGlobal.renderSky}. Both fixes are decisions over values that exist without a device - the
 * wait's result code, and the latch's state at the draw entry - so they are pinned here as a pure function and
 * as source contracts over the two call sites.
 * <p>
 * What these tests prove and what they do not: they prove which results settleGpu absorbs, that settleGpu
 * consults its rule before its throw, and that the draw entry decides a refusal by the latch (a counted
 * skip since DP-02, for live-backend refusals too). They prove nothing about a lost device itself - that
 * the reload now completes and the session degrades is the reporting user's NVIDIA session, which is not
 * runnable here.
 */
class DeviceLostRobustnessTest {

    /** Only the device-loss report is absorbed; every other result keeps settleGpu's existing contract. */
    @Test
    void onlyDeviceLossIsAbsorbedByTheSettle() {
        assertTrue(VulkanTerrainGpuBackend.celtium$settleReportsDeviceLoss(VK10.VK_ERROR_DEVICE_LOST),
                "VK_ERROR_DEVICE_LOST (-4) is the report the reporter's teardown died on");
        assertFalse(VulkanTerrainGpuBackend.celtium$settleReportsDeviceLoss(VK10.VK_SUCCESS),
                "success is not a failure to absorb (the settle does not consult the rule on success)");
        assertFalse(VulkanTerrainGpuBackend.celtium$settleReportsDeviceLoss(VK10.VK_TIMEOUT),
                "a timeout is a busy device, not a gone one: it keeps the throw");
        assertFalse(VulkanTerrainGpuBackend.celtium$settleReportsDeviceLoss(VK10.VK_ERROR_OUT_OF_DEVICE_MEMORY),
                "out-of-memory is a different failure with a live device: it keeps the throw");
        assertFalse(VulkanTerrainGpuBackend.celtium$settleReportsDeviceLoss(VK10.VK_ERROR_INITIALIZATION_FAILED),
                "an initialization failure is not a lost device: it keeps the throw");
    }

    /**
     * The settle itself: the absorbed result returns false (every caller already reads false as "release
     * nothing") and the throw survives for the results the rule does not absorb, in that order.
     */
    @Test
    void theSettleConsultsTheRuleBeforeItsThrow() throws IOException {
        String source = Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanTerrainGpuBackend.java"));
        int settleStart = source.indexOf("private boolean settleGpu(String reason)");
        assertTrue(settleStart >= 0, "settleGpu is declared");
        String settle = source.substring(settleStart, settleStart + 6000);
        int rule = settle.indexOf("celtium$settleReportsDeviceLoss(result)");
        int throwSite = settle.indexOf("throw new IllegalStateException(\"settleGpu(\"");
        assertTrue(rule >= 0, "settleGpu consults the device-loss rule on a failed wait");
        assertTrue(throwSite > rule, "the throw remains, after the rule, for results the rule does not absorb");
        String absorbed = settle.substring(rule, throwSite);
        assertTrue(absorbed.contains("return false"),
                "the absorbed branch returns false: the callers' fail-closed reading does the rest");
        assertTrue(absorbed.contains("noteStalled") || settle.indexOf("noteStalled") < rule,
                "the stall latch is set on the failure path, so later draws refuse instead of recording");
    }

    /**
     * The native draw entry: a refusal while the latch is up is a counted, skipped draw - and since the
     * drawpath audit's DP-02 disposition, so is a refusal on a live backend. The caller enumeration
     * behind DP-02 showed the entry's one caller sits inside {@code VertexBuffer.drawArrays}, whose
     * vanilla and Forge callers hold no catch, so the live-backend throw this test used to pin was the
     * same propagation shape as the stall crash above it. The only throw that survives is the
     * no-Vulkan-backend invariant, checked before any submission happens.
     */
    @Test
    void theNativeDrawEntrySkipsWhileStalledAndOnLiveRefusals() throws IOException {
        String source = Files.readString(findRepoFile(
                "forge122/src/main/java/org/soutalab/celtium/core/CeltiumNativeDraw.java"));
        int stallCheck = source.indexOf("celtium$isGpuStalled()");
        int refusedNote = source.indexOf("NativeDrawSkipCensus.noteRefusedSkip()");
        assertTrue(stallCheck >= 0, "the entry consults the backend's stall latch on a refusal");
        assertTrue(refusedNote > stallCheck,
                "a live-backend refusal is counted as a skip after the stall check, not thrown");
        String skipped = source.substring(stallCheck, refusedNote);
        assertTrue(skipped.contains("NativeDrawSkipCensus.noteStalledSkip()"),
                "the stalled skip is counted in its own population");
        assertTrue(skipped.contains("return;"),
                "the stalled branch returns: the draw is skipped and the frame continues degraded");
        assertFalse(source.contains("native VertexBuffer draw refused"),
                "no refusal throw remains: a refusal degrades to the counted skip (DP-02)");
        int invariant = source.indexOf("native VertexBuffer draw without an active Vulkan backend");
        int submit = source.indexOf("submitRetainedGeometry");
        assertTrue(invariant >= 0 && invariant < submit,
                "the no-backend invariant throw remains, ahead of the submission");
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
