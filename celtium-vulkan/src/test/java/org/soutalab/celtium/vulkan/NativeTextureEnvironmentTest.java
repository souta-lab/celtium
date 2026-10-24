package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.soutalab.celtium.vulkan.NativeTextureEnvironment.*;

class NativeTextureEnvironmentTest {
    @Test void vanillaOutlineReplacesRgbRegardlessOfConstantAlphaAndRestoresModulate() {
        NativeRasterState raster = new NativeRasterState();
        NativeTextureBindings bindings = new NativeTextureBindings(raster);
        NativeTextureEnvironment env = new NativeTextureEnvironment(bindings, raster);
        env.color(TARGET, COLOR, 0.2F, 0.4F, 0.6F, 0); // vanilla team colours have no alpha byte
        env.parameter(TARGET, MODE, COMBINE);
        env.parameter(TARGET, RGB, REPLACE);
        assertNull(env.combinedColor(), "TEXTURE is not the outline's constant colour");
        env.parameter(TARGET, SOURCE0_RGB, CONSTANT);
        env.parameter(TARGET, OPERAND0_RGB, SRC_COLOR);
        env.parameter(TARGET, ALPHA, REPLACE);
        env.parameter(TARGET, 0x8588, 0x1702); // alpha remains TEXTURE
        env.parameter(TARGET, 0x8598, 0x0302); // SRC_ALPHA
        float[] outline = env.combinedColor();
        assertArrayEquals(new float[] {0.2F, 0.4F, 0.6F, 1}, outline);
        assertSame(outline, env.combinedColor(), "unchanged native state reuses one immutable material");
        env.parameter(TARGET, MODE, MODULATE);
        assertNull(env.combinedColor());
        assertArrayEquals(new float[] {0.2F, 0.4F, 0.6F, 1}, outline, "queued draws keep the old material");
    }

    @Test void hurtTintKeepsItsUnitAndWeightWithoutChangingActiveBindings() {
        NativeRasterState raster = new NativeRasterState();
        NativeTextureBindings bindings = new NativeTextureBindings(raster);
        NativeTextureEnvironment env = new NativeTextureEnvironment(bindings, raster);
        bindings.activeUnit(1);
        env.color(TARGET, COLOR, 1, 0, 0, 0.3F);
        env.parameter(TARGET, MODE, COMBINE);
        env.parameter(TARGET, RGB, INTERPOLATE);
        bindings.activeUnit(0);
        assertArrayEquals(new float[] {1, 0, 0, 0.3F}, env.combinedColor());
        assertEquals(0, bindings.activeUnit());
        bindings.activeUnit(1);
        env.parameter(TARGET, RGB, MODULATE);
        assertNull(env.combinedColor());
    }

    @Test void stateChangesFlushOldWorkBeforeMutationAndQueriesRetainSources() {
        NativeRasterState raster = new NativeRasterState();
        NativeTextureBindings bindings = new NativeTextureBindings(raster);
        NativeTextureEnvironment env = new NativeTextureEnvironment(bindings, raster);
        int[] flushes = {0};
        raster.beforeChange(() -> { assertEquals(MODULATE, env.activeParameter(TARGET, MODE)); flushes[0]++; });
        env.parameter(TARGET, MODE, COMBINE);
        env.parameter(TARGET, MODE, COMBINE);
        assertEquals(1, flushes[0]);
        raster.beforeChange(() -> {});
        env.parameter(TARGET, SOURCE0_RGB, CONSTANT);
        assertEquals(CONSTANT, env.activeParameter(TARGET, SOURCE0_RGB));
        assertEquals(0, env.activeParameter(0, MODE));
    }
}
