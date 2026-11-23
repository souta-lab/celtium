package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The item-695 (E2) census' counting, driven through its Ledger directly: a window is filed
 * under exactly the cause its entrance stamped, the window line carries the per-cause split
 * and the since-start totals, the totals survive a window reset while the window counts do
 * not, and an out-of-range cause is filed as other rather than lost - the exemption design
 * reads this split, so a cause that silently vanished would read as a boundary that never
 * fires.
 */
class GlDrawWindowCensusTest {

    @Test void windowLineCarriesTheCauseSplitAndResetsTheWindow() {
        GlDrawWindowCensus.Ledger ledger = new GlDrawWindowCensus.Ledger();
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_TARGET);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_TARGET);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_TEXTURE_ANNOUNCE);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BATCH_SUBMIT);
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size());
        assertEquals("CELTIUM_GLDRAW_WINDOW_CENSUS frames=200"
                + " window[windows=4 bind-framebuffer-target=2 texture-announce=1 batch-submit=1]"
                + " total[windows=4 bind-framebuffer-target=2 texture-announce=1 batch-submit=1]",
                lines.get(0));
        assertTrue(ledger.windowLines(200).isEmpty(), "an idle window prints nothing");
    }

    @Test void totalsSurviveTheWindowReset() {
        GlDrawWindowCensus.Ledger ledger = new GlDrawWindowCensus.Ledger();
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_SIZE);
        ledger.windowLines(200);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_SIZE);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_COPY_ORDER);
        List<String> lines = ledger.windowLines(200);
        assertEquals(1, lines.size());
        assertEquals("CELTIUM_GLDRAW_WINDOW_CENSUS frames=200"
                + " window[windows=2 copy-order=1 size=1]"
                + " total[windows=3 copy-order=1 size=2]",
                lines.get(0));
        assertEquals(3L, ledger.total());
        assertEquals(2L, ledger.totalFor(GlDrawWindowCensus.CAUSE_SIZE));
    }

    @Test void everyCauseIsNamedAndCountedSeparately() {
        GlDrawWindowCensus.Ledger ledger = new GlDrawWindowCensus.Ledger();
        for (int cause = 0; cause < GlDrawWindowCensus.CAUSE_NAMES.length; cause++) {
            ledger.noteWindow(cause);
        }
        assertEquals(GlDrawWindowCensus.CAUSE_NAMES.length, ledger.total());
        for (int cause = 0; cause < GlDrawWindowCensus.CAUSE_NAMES.length; cause++) {
            assertEquals(1L, ledger.totalFor(cause), "cause " + cause);
        }
        String line = ledger.windowLines(512).get(0);
        for (String name : GlDrawWindowCensus.CAUSE_NAMES) {
            assertTrue(line.contains(name + "=1"), "line names " + name + ": " + line);
        }
    }

    @Test void outOfRangeCausesFileAsOther() {
        GlDrawWindowCensus.Ledger ledger = new GlDrawWindowCensus.Ledger();
        ledger.noteWindow(-1);
        ledger.noteWindow(9999);
        assertEquals(2L, ledger.totalFor(GlDrawWindowCensus.CAUSE_OTHER));
        assertEquals(2L, ledger.totalFor(-1), "an out-of-range read is the other bucket too");
        assertEquals(2L, ledger.total());
    }

    @Test void bindKindsStayDistinct() {
        // The E2 question the census exists for: a main-target bind, a mod FBO bind and a
        // raw-register bind are three different exemption candidates, never one bind bucket.
        GlDrawWindowCensus.Ledger ledger = new GlDrawWindowCensus.Ledger();
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_MAIN);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_TARGET);
        ledger.noteWindow(GlDrawWindowCensus.CAUSE_BIND_FRAMEBUFFER_RAW);
        String line = ledger.windowLines(200).get(0);
        assertTrue(line.contains("bind-framebuffer-main=1"), line);
        assertTrue(line.contains("bind-framebuffer-target=1"), line);
        assertTrue(line.contains("bind-framebuffer-raw=1"), line);
    }
}
