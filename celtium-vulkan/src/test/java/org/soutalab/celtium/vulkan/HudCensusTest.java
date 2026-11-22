package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The HUD census' counting rules, plus the wiring that feeds them.
 * <p>
 * The defect class this pins against is the one leg-hud312 measured for the item-312 fit: an
 * instrument gated on the bracket still being open at flush time received zero windows, because the
 * HUD's drain runs at the frame boundary after the bracket closes. The census therefore attributes
 * a window by entry state <em>or</em> by the provenance of the groups it drains, and these tests
 * fail if either half is dropped - and if the screen's batches and flushes stop being counted,
 * which is the gap the census was written to close (an open container screen was in no HUD
 * population at all).
 */
class HudCensusTest {

    private static final String MAIN = "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/";
    private static final String FORGE = "forge122/src/main/java/org/taumc/celeritas/mixin/core/";

    private static ImmediateGroup group(boolean closedInUiSection) {
        ImmediateGroup group = ImmediateGroup.obtain(null, null, null, 0, 0, 0L, null, 0L);
        group.closedInUiSection = closedInUiSection;
        return group;
    }

    @Test
    void uiClosedGroupsCountsOnlyTheStampedGroups() {
        List<ImmediateGroup> pending = new ArrayList<>();
        assertEquals(0, HudCensus.uiClosedGroups(pending));
        pending.add(group(false));
        pending.add(group(true));
        pending.add(group(true));
        assertEquals(2, HudCensus.uiClosedGroups(pending));
    }

    @Test
    void aDrainWindowAfterTheBracketClosedIsAttributedToTheUi() {
        HudCensus.Ledger ledger = new HudCensus.Ledger(10, 0L);
        ledger.noteOffer(1L);
        ledger.noteGroupClosed(1L);
        ledger.noteGroupClosed(1L);
        // Entered outside the brackets, draining two UI-closed groups: a drain window.
        ledger.noteActualFlush(1L, false, 2, 5_000L);
        ledger.noteFrame(2L);
        assertEquals(1L, ledger.framesFiled());
        assertEquals(1L, ledger.totalActualFlushesUi());
        assertEquals(1L, ledger.totalDrainWindows());
        assertEquals(5_000L, ledger.totalFlushNanosUi());
        assertTrue(ledger.summary().contains("drainWindows=1.0"));
        assertTrue(ledger.summary().contains("groupsDrainedUi=2.0"));
    }

    @Test
    void aWorldOnlyWindowIsNotAttributedToTheUi() {
        HudCensus.Ledger ledger = new HudCensus.Ledger(10, 0L);
        ledger.noteActualFlush(1L, false, 0, 7_000L);
        ledger.noteFrame(2L);
        assertEquals(1L, ledger.framesFiled());
        assertEquals(0L, ledger.totalActualFlushesUi());
        assertEquals(0L, ledger.totalDrainWindows());
        assertEquals(0L, ledger.totalFlushNanosUi());
        assertTrue(ledger.summary().contains("flushMsTotal=0.0") || ledger.summary().contains("actualFlushes=1.0"));
    }

    @Test
    void framesBelowTheMinFrameAndPastTheBoundAreNotFiled() {
        HudCensus.Ledger ledger = new HudCensus.Ledger(1, 100L);
        ledger.noteOffer(50L);
        ledger.noteFrame(101L);
        assertEquals(0L, ledger.framesFiled());
        ledger.noteOffer(101L);
        ledger.noteFrame(102L);
        assertEquals(1L, ledger.framesFiled());
        ledger.noteOffer(102L);
        ledger.noteFrame(103L);
        assertEquals(1L, ledger.framesFiled());
    }

    @Test
    void theScreenBracketAndCensusNotesAreWired() throws IOException {
        String timings = read(MAIN + "FramePassTimings.java");
        assertTrue(timings.contains("\"screen\""), "the screen bracket must be a top-level pass");
        assertTrue(timings.contains("\"screenBatch\""), "the accept path must time the screen's share");
        assertTrue(timings.contains("\"screenFlush\""), "the flush must time the screen's share");
        assertTrue(timings.contains("screenSectionActive()"), "the backend reads the screen bracket state");
        assertTrue(timings.contains("uiSectionActive()"), "the census reads both UI brackets as one section");

        String backend = read(MAIN + "VulkanTerrainGpuBackend.java");
        assertTrue(backend.contains("HudCensus.noteOffer"), "offers must be counted at the accept site");
        assertTrue(backend.contains("HudCensus.noteGroupClosed"), "closes must be counted at the close site");
        assertTrue(backend.contains("HudCensus.noteFlushCall"), "flush entries must be counted");
        assertTrue(backend.contains("HudCensus.noteActualFlush"), "windows must be paired at the flush site");
        assertTrue(backend.contains("closedInUiSection = HudCensus.enabled()"),
                "the UI stamp must be written at the close, beside the gui stamp");

        String screenMixin = read(FORGE + "GuiScreenDrawMixin.java");
        assertTrue(screenMixin.contains("func_73863_a"), "GuiScreen.drawScreen is the screen bracket's target");
        String containerMixin = read(FORGE + "GuiContainerDrawMixin.java");
        assertTrue(containerMixin.contains("func_73863_a"),
                "GuiContainer.drawScreen is the bracket's outer half: the item rendering lives there");
    }

    private static String read(String path) throws IOException {
        Path file = Paths.get(path);
        if (!Files.exists(file)) {
            file = Paths.get("..").resolve(path);
        }
        return new String(Files.readAllBytes(file), java.nio.charset.StandardCharsets.UTF_8);
    }
}
