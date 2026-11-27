package org.taumc.celeritas.mixin;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The L-F drawpath dispositions (DP-01/02/03/27/28/29/30), pinned at the source seams the offline
 * suite can check, in the shape {@code CeltiumFunnelSummaryPrintSiteTest} established: the behaviour
 * that needs a running game (the counters moving in a leg, the stall-injection positive control) is
 * covered by the confirmation legs; what must not silently regress is the wiring - an unconditional
 * count at each seat, and the censuses' presence in the verification summary.
 */
class DrawPathLossWiringTest {

    private static String read(String repoRelative, String moduleRelative) throws Exception {
        return Files.readString(findRepoFile(repoRelative, moduleRelative)).replace("\r\n", "\n");
    }

    private static Path findRepoFile(String repoRelative, String moduleRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get(moduleRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        Path up = Paths.get("..").resolve(repoRelative);
        if (Files.exists(up)) {
            return up;
        }
        return direct;
    }

    /** DP-01: all three pre-backend deaths are noted, and the swallowed count is outside its print gate. */
    @Test
    void offerFunnelDeathsAreCountedUnconditionally() throws Exception {
        String mixin = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/WorldVertexBufferUploaderMixin.java");
        assertTrue(mixin.contains("OfferFunnelCensus.noteNoData()"), "the no-buffer return is counted");
        assertTrue(mixin.contains("OfferFunnelCensus.noteNoAddress()"), "the address==0 return is counted");
        int catchAt = mixin.indexOf("catch (Throwable error)");
        int noteAt = mixin.indexOf("OfferFunnelCensus.noteSwallowed()", catchAt);
        int gateAt = mixin.indexOf("if (celtium$swallowed < 3)", catchAt);
        assertTrue(catchAt >= 0 && noteAt > catchAt, "the broad catch notes the swallowed offer");
        assertTrue(gateAt > noteAt,
                "the count is taken before (outside) the print budget gate, so it never stops at three");
    }

    /** DP-01/03/29: the three censuses are aggregated into the verification summary. */
    @Test
    void censusesReachTheVerificationSummary() throws Exception {
        String diagnostics = read(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java",
                "../celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/BackendDiagnostics.java");
        assertTrue(diagnostics.contains("celtium/offerDeaths["), "offer funnel deaths are in VERIFY");
        assertTrue(diagnostics.contains("celtium/nativeDrawSkips["), "native draw skips are in VERIFY");
        assertTrue(diagnostics.contains("celtium/noCameraSkips["), "no-camera skips are in VERIFY");
    }

    /** DP-02/DP-03: a refused native draw is a counted skip; only the no-backend invariant still throws. */
    @Test
    void nativeDrawRefusalSkipsInsteadOfThrowing() throws Exception {
        String draw = read(
                "forge122/src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java",
                "src/main/java/org/taumc/celeritas/core/CeltiumNativeDraw.java");
        assertTrue(draw.contains("NativeDrawSkipCensus.noteStalledSkip()"), "the stall skip is counted");
        assertTrue(draw.contains("NativeDrawSkipCensus.noteRefusedSkip()"), "the refusal skip is counted");
        assertFalse(draw.contains("native VertexBuffer draw refused"),
                "the refusal no longer throws into uncaught callers");
        assertTrue(draw.contains("native VertexBuffer draw without an active Vulkan backend"),
                "the no-backend invariant still throws");
    }

    /** DP-27: the loading progress stop at three failures names itself. */
    @Test
    void loadingProgressStopIsNamed() throws Exception {
        String progress = read(
                "forge122/src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java",
                "src/main/java/org/taumc/celeritas/core/CeltiumLoadingProgress.java");
        assertTrue(progress.contains("native loading progress disabled after"),
                "the three-failure stop prints a line naming the stop");
    }

    /** DP-28: the item-mesh probe covers all seven lists, and the old two-list stamp shape is gone. */
    @Test
    void itemMeshProbeCoversAllSevenLists() throws Exception {
        String mixin = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/RenderItemMixin.java");
        assertTrue(mixin.contains("celtium$captureProbe(model, probeFirst, probeLast, probeCounts)"),
                "the build records stamps for every list");
        assertTrue(mixin.contains("plan.probeCounts[i]") && mixin.contains("plan.probeLast[i]"),
                "the hit path re-verifies size and last-quad identity per list");
        assertFalse(mixin.contains("faceProbeQuad"),
                "the null+UP-only probe shape (DP-28's hole) does not come back");
    }

    /** DP-29: each no-camera guard notes its skip in the shared census. */
    @Test
    void noCameraGuardsAreCounted() throws Exception {
        String flag = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/MinecraftSkipWorldWithoutCameraMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/MinecraftSkipWorldWithoutCameraMixin.java");
        assertTrue(flag.contains("NoCameraSkipCensus.noteFlagTrue()"), "flag-true frames are counted");
        String world = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/EntityRendererSkipWorldWithoutCameraMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/EntityRendererSkipWorldWithoutCameraMixin.java");
        assertTrue(world.contains("NoCameraSkipCensus.noteWorldSkip()"), "world skips are counted");
        assertTrue(world.contains("NoCameraSkipCensus.noteWorldRendered()"),
                "a rendered frame closes the consecutive run");
        String hud = read(
                "forge122/src/main/java/org/taumc/celeritas/mixin/core/GuiIngameForgeNoPlayerGuardMixin.java",
                "src/main/java/org/taumc/celeritas/mixin/core/GuiIngameForgeNoPlayerGuardMixin.java");
        assertTrue(hud.contains("NoCameraSkipCensus.noteHudSkip()"), "HUD skips are counted");
    }

    /** DP-30: an unflushed collection is counted and named at the next window's begin. */
    @Test
    void endPortalLeftoversAreCountedAtBegin() throws Exception {
        String producer = read(
                "forge122/src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java",
                "src/main/java/org/taumc/celeritas/impl/render/producer/EndPortalProducer.java");
        int beginAt = producer.indexOf("public static void begin()");
        int leftoverAt = producer.indexOf("leftoverWindows++", beginAt);
        assertTrue(beginAt >= 0 && leftoverAt > beginAt, "begin() counts the previous window's leftovers");
        assertTrue(producer.contains("leftoverWindows="), "describe() reports the leftover count");
    }
}
