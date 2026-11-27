package org.soutalab.celtium.forge.ui;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.forge.ui.OptionEntry.ConfigTarget;
import org.soutalab.celtium.forge.ui.OptionEntry.CycleOption;
import org.soutalab.celtium.forge.ui.OptionEntry.SliderOption;
import org.soutalab.celtium.forge.ui.OptionEntry.ToggleOption;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the three native option controls: Slider, Toggle, and Cycle,
 * including pending-change tracking, Undo (reset), Apply, and side-effect flags.
 */
class OptionEntryTest {

    @Test
    void sliderSnapsClampsAndSupportsUndoApply() {
        AtomicInteger backing = new AtomicInteger(30);
        SliderOption slider = new SliderOption(
                "test.slider", "test.tooltip", ConfigTarget.CELTIUM, OptionImpact.LOW,
                EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD),
                10, 260, 10, v -> v + " FPS",
                backing::get, backing::set);

        assertFalse(slider.hasChanged());
        assertEquals(30, slider.getValue());
        assertEquals("30 FPS", slider.getDisplayValue());

        slider.setFromFraction(0.5f); // 10 + 125 = 135 -> snaps to 140
        assertTrue(slider.hasChanged());
        assertEquals(140, slider.getValue());
        assertEquals(30, backing.get(), "backing storage must not change before apply");

        slider.reset();
        assertFalse(slider.hasChanged());
        assertEquals(30, slider.getValue());

        slider.setFromFraction(1.5f); // clamped to 260
        assertEquals(260, slider.getValue());
        slider.apply();
        assertFalse(slider.hasChanged());
        assertEquals(260, backing.get());
        assertTrue(slider.getFlags().contains(OptionFlag.REQUIRES_RENDERER_RELOAD));
    }

    @Test
    void toggleTracksPendingStateAndApplies() {
        AtomicBoolean backing = new AtomicBoolean(true);
        ToggleOption toggle = new ToggleOption(
                "test.toggle", "test.tooltip", ConfigTarget.CELTIUM, OptionImpact.MEDIUM,
                EnumSet.of(OptionFlag.REQUIRES_RENDERER_UPDATE),
                backing::get, backing::set);

        assertFalse(toggle.hasChanged());
        toggle.toggle();
        assertTrue(toggle.hasChanged());
        assertFalse(toggle.getValue());
        assertTrue(backing.get());

        toggle.reset();
        assertFalse(toggle.hasChanged());
        assertTrue(toggle.getValue());

        toggle.toggle();
        toggle.apply();
        assertFalse(toggle.hasChanged());
        assertFalse(backing.get());
    }

    @Test
    void cycleWrapsBothDirectionsAndApplies() {
        AtomicReference<CeltiumConfig.LeafCullingMode> backing =
                new AtomicReference<>(CeltiumConfig.LeafCullingMode.SOLID);
        CycleOption<CeltiumConfig.LeafCullingMode> cycle = new CycleOption<>(
                "test.cycle", "test.tooltip", ConfigTarget.CELTIUM, OptionImpact.MEDIUM,
                EnumSet.of(OptionFlag.REQUIRES_RENDERER_RELOAD),
                List.of(CeltiumConfig.LeafCullingMode.values()),
                Enum::name,
                backing::get, backing::set);

        assertFalse(cycle.hasChanged());
        cycle.cycle(1); // SOLID -> wraps to NONE
        assertEquals(CeltiumConfig.LeafCullingMode.NONE, cycle.getValue());
        assertTrue(cycle.hasChanged());

        cycle.cycle(-1); // NONE -> wraps back to SOLID
        assertEquals(CeltiumConfig.LeafCullingMode.SOLID, cycle.getValue());
        assertFalse(cycle.hasChanged());

        cycle.cycle(-1); // SOLID -> SOLID_AGGRESSIVE
        cycle.apply();
        assertEquals(CeltiumConfig.LeafCullingMode.SOLID_AGGRESSIVE, backing.get());
        assertFalse(cycle.hasChanged());
    }
}
