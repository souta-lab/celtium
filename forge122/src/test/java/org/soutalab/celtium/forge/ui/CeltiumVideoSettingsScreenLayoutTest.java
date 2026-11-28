package org.soutalab.celtium.forge.ui;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.forge.ui.CeltiumVideoSettingsScreen.PlaceholderLayout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout contracts for the two rough edges found by the 2026-11-28 visual leg
 * (VIDEO-SETTINGS-VISUAL-LEG-2026-11-28 §9): the Shaders placeholder page must sit on the
 * same grid as the option pages, and a tooltip must never slide over the tab bar.
 */
class CeltiumVideoSettingsScreenLayoutTest {

    // Fixture geometry of the leg screenshots: 426x240 GUI units, panel 402 wide at left=12,
    // list top at 40, tab bar occupying y 10..32.
    private static final int LIST_TOP = 40;
    private static final int TAB_BAR_BOTTOM = 32;

    @Test
    void placeholderUsesTheOptionPageGrid() {
        PlaceholderLayout layout = CeltiumVideoSettingsScreen.placeholderLayout(100, 40, 440, 2);

        // The option pages draw rows in the box (left + 4, width - 8); the placeholder
        // heading divider and its button must span that same box.
        assertEquals(104, layout.contentX());
        assertEquals(432, layout.contentW());
        assertEquals(104, layout.buttonX());
        assertEquals(432, layout.buttonW());
        assertEquals(106, layout.dividerLeft());
        assertEquals(534, layout.dividerRight());
        assertEquals(layout.buttonX() + 2, layout.dividerLeft());
        assertEquals(layout.buttonX() + layout.buttonW() - 2, layout.dividerRight());
    }

    @Test
    void placeholderButtonIsARowBelowTheNotice() {
        PlaceholderLayout layout = CeltiumVideoSettingsScreen.placeholderLayout(100, 40, 440, 3);

        assertEquals(22, layout.buttonH());
        assertEquals(layout.noticeY() + 3 * 11 + 10, layout.buttonY());
    }

    @Test
    void tooltipWidthIsCappedBelowTheOldFootprint() {
        assertEquals(230, CeltiumVideoSettingsScreen.TOOLTIP_MAX_WIDTH);
        assertTrue(CeltiumVideoSettingsScreen.TOOLTIP_MAX_WIDTH < 260);
    }

    @Test
    void tooltipSitsBelowTheCursorWhenItFits() {
        assertEquals(72, CeltiumVideoSettingsScreen.tooltipBoxY(60, 50, 720, LIST_TOP));
    }

    @Test
    void tooltipFlipsAboveTheCursorAtTheScreenBottom() {
        assertEquals(592, CeltiumVideoSettingsScreen.tooltipBoxY(700, 100, 720, LIST_TOP));
    }

    @Test
    void tallTooltipFlippedAboveALowRowNeverCoversTheTabBar() {
        // The leg's case: the Leaf Culling tooltip (box height ~120) hovered near the bottom
        // of a 240-high GUI flipped above the cursor and landed on the tab row.
        int by = CeltiumVideoSettingsScreen.tooltipBoxY(130, 120, 240, LIST_TOP);

        assertEquals(LIST_TOP - 2, by);
        assertTrue(by > TAB_BAR_BOTTOM);
    }

    @Test
    void tooltipTallerThanTheListPinsToThePanelTop() {
        int by = CeltiumVideoSettingsScreen.tooltipBoxY(100, 300, 240, LIST_TOP);

        assertEquals(LIST_TOP - 2, by);
    }
}
