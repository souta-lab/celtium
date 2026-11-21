package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The wiring of the item-312 P0 flush fit, pinned as a source contract plus the counting rule's
 * behaviour.
 * <p>
 * The defect this pins against (leg-hud312, measured): the fit's only recording point gated on the
 * {@code gui} bracket still being open when a flush ran. The HUD's groups close inside the bracket
 * but are drained by the frame-boundary flush after it closes, so over 300 measured frames the
 * regression received zero windows and G1/G2 had no value at all. The fix identifies a gui window
 * by the provenance of the groups it drains - each group carries the bracket state of its own close
 * - and pairs windows at both points that call {@code flushImmediateWindow}. These tests fail on
 * the pre-fix tree: the counting rule does not exist there, and the flush gate is the bracket read.
 */
class HudP0FitWiringTest {

    private static final String MAIN = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";

    private static ImmediateGroup group(boolean closedInGuiSection) {
        ImmediateGroup group = ImmediateGroup.obtain(null, null, null, 0, 0, 0L, null, 0L);
        group.closedInGuiSection = closedInGuiSection;
        return group;
    }

    @Test
    void guiClosedGroupsCountsOnlyTheStampedGroups() {
        List<ImmediateGroup> pending = new ArrayList<>();
        assertEquals(0, HudP0Census.guiClosedGroups(pending));
        pending.add(group(false));
        pending.add(group(true));
        pending.add(group(true));
        pending.add(group(false));
        assertEquals(2, HudP0Census.guiClosedGroups(pending));
    }

    @Test
    void aWindowDrainedAfterTheBracketClosedStillEntersTheFit() {
        // The leg's shape: both HUD groups closed inside the bracket, the flush that drains them
        // runs at the frame boundary with the bracket already closed. Under the old gate this list
        // produced no sample; under the provenance rule it must pair like any other gui window.
        List<ImmediateGroup> pending = new ArrayList<>();
        pending.add(group(true));
        pending.add(group(true));
        HudP0Census.FlushRegression regression = new HudP0Census.FlushRegression();
        if (HudP0Census.guiClosedGroups(pending) > 0) {
            regression.note(pending.size(), 3_000L);
        }
        pending.clear();
        pending.add(group(true));
        pending.add(group(true));
        pending.add(group(true));
        pending.add(group(true));
        if (HudP0Census.guiClosedGroups(pending) > 0) {
            regression.note(pending.size(), 5_000L);
        }
        assertEquals(2, regression.windows());
        assertEquals(6, regression.totalGroups());
        assertEquals(1_000.0, regression.slopeNanosPerGroup(), 1e-6);
    }

    @Test
    void aWindowOfWorldGroupsAloneStaysOutOfTheFit() {
        List<ImmediateGroup> pending = new ArrayList<>();
        pending.add(group(false));
        pending.add(group(false));
        assertEquals(0, HudP0Census.guiClosedGroups(pending));
    }

    @Test
    void theGroupCarriesItsCloseTimeBracketStateAndThePoolClearsIt() throws IOException {
        String group = Files.readString(findRepoFile(MAIN + "ImmediateGroup.java"));
        assertTrue(group.contains("public boolean closedInGuiSection;"),
                "the group carries the bracket state of its close");
        String clear = methodSlice(group, "private void clearForReuse() {", 1600);
        assertTrue(clear.contains("this.closedInGuiSection = false;"),
                "a recycled group must not keep the previous batch's stamp");
    }

    @Test
    void theCloseSiteStampsTheGroupBesideTheCensusNote() throws IOException {
        String backend = Files.readString(findRepoFile(MAIN + "VulkanTerrainGpuBackend.java"));
        String close = methodSlice(backend, "private void closeCurrentImmediateGroupBody() {", 30000);
        int note = close.indexOf("celtium$noteHudP0Group();");
        assertTrue(note >= 0, "the census note still runs at the close site");
        String after = close.substring(note, Math.min(close.length(), note + 4000));
        assertTrue(after.contains(
                        "group.closedInGuiSection = HUD_P0_FRAMES > 0 && FramePassTimings.guiSectionActive();"),
                "the stamp is written at the close, under the same bracket read as the census note");
    }

    @Test
    void theOrderingFlushPairsByProvenanceNotByTheLiveBracket() throws IOException {
        String backend = Files.readString(findRepoFile(MAIN + "VulkanTerrainGpuBackend.java"));
        String flush = methodSlice(backend, "void flushPendingBeforeGlDraw() {", 12000);
        assertFalse(flush.contains("HUD_P0_FRAMES > 0 && guiFlush ? System.nanoTime()"),
                "the bracket-at-flush-time gate is the defect and must not come back");
        assertTrue(flush.contains("HudP0Census.guiClosedGroups(this.immediateGroups)"),
                "the window is identified by the groups it drains");
        assertTrue(flush.contains("long hudP0FlushStartedNanos = hudP0GuiWindow ? System.nanoTime() : 0L;"),
                "the fit timer starts for provenance-gui windows");
        assertTrue(flush.contains("this.hudP0FlushFit.note(hudP0FlushGroups,"),
                "the pair is still noted with the window's whole pending count");
    }

    @Test
    void theSizeTriggeredWindowPairsItsGuiWindowsToo() throws IOException {
        String backend = Files.readString(findRepoFile(MAIN + "VulkanTerrainGpuBackend.java"));
        int at = backend.indexOf("this.pendingImmediateIndices >= IMMEDIATE_FLUSH_INDICES");
        assertTrue(at >= 0, "the size-triggered window still exists");
        String window = backend.substring(at, Math.min(backend.length(), at + 9000));
        assertTrue(window.contains("HudP0Census.guiClosedGroups(this.immediateGroups)"),
                "the accept-path window identifies gui windows by the same stamp");
        assertTrue(window.contains("this.hudP0FlushFit.note(hudP0WindowGroups,"),
                "the accept-path window pairs into the same regression");
    }

    /** A slice of one method, from its signature, long enough to cover the lines this pin reads. */
    private static String methodSlice(String source, String signature, int length) {
        int start = source.indexOf(signature);
        assertTrue(start >= 0, "cannot find " + signature);
        return source.substring(start, Math.min(source.length(), start + length));
    }

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
