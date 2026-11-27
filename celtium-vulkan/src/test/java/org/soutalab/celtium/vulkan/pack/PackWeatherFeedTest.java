package org.soutalab.celtium.vulkan.pack;

import org.junit.jupiter.api.Test;
import org.soutalab.celtium.core.shader.PackExecutionPlan;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The weather feed's byte-level contract (Neon ruling, 2026-11-27): all sixteen components
 * land at the layout's field offsets, the provider inverse radius rides in params3.x (yzw
 * stay zero), and both ticks wrap at 1024.
 */
class PackWeatherFeedTest {

    private static PackExecutionPlan.PackUniformLayout layout() {
        return new PackExecutionPlan.PackUniformLayout(List.of(
                new PackExecutionPlan.PackUniformLayout.UniformField(
                        "iris_WeatherParams0", PackExecutionPlan.PackUniformLayout.UniformType.VEC4, 0, 16),
                new PackExecutionPlan.PackUniformLayout.UniformField(
                        "iris_WeatherParams1", PackExecutionPlan.PackUniformLayout.UniformType.VEC4, 16, 16),
                new PackExecutionPlan.PackUniformLayout.UniformField(
                        "iris_WeatherParams2", PackExecutionPlan.PackUniformLayout.UniformType.VEC4, 32, 16),
                new PackExecutionPlan.PackUniformLayout.UniformField(
                        "iris_WeatherParams3", PackExecutionPlan.PackUniformLayout.UniformType.VEC4, 48, 16)),
                64);
    }

    @Test
    void writesAllSixteenComponentsAtLayoutOffsets() {
        PackWeatherFeed.WeatherState state = new PackWeatherFeed.WeatherState(
                -0.25f, -64.0f, 0.5f, 0.1f,     // translate xyz, inverse radius
                0.25f, 0.75f, 0.5f, 123.5f,     // camera frac x/z, partial ticks, age
                2050.0f, -1.0f, 0.8f, 55.25f,   // vanilla tick, provider tick, rain, provider age
                0.05f);                          // provider inverse radius
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slot = arena.allocate(64);
            PackWeatherFeed.writeInto(layout(), slot, state);

            assertVec4(slot, 0, -0.25f, -64.0f, 0.5f, 0.1f);
            assertVec4(slot, 16, 0.25f, 0.75f, 0.5f, 123.5f);
            // Ticks wrap at 1024: 2050 -> 2, -1 -> 1023.
            assertVec4(slot, 32, 2.0f, 1023.0f, 0.8f, 55.25f);
            // The provider inverse radius is params3.x; yzw are reserved and stay zero.
            assertVec4(slot, 48, 0.05f, 0.0f, 0.0f, 0.0f);
        }
    }

    @Test
    void leavesLayoutsWithoutWeatherFieldsUntouched() {
        PackExecutionPlan.PackUniformLayout bare = new PackExecutionPlan.PackUniformLayout(List.of(
                new PackExecutionPlan.PackUniformLayout.UniformField(
                        "sunAngle", PackExecutionPlan.PackUniformLayout.UniformType.FLOAT, 0, 4)),
                16);
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment slot = arena.allocate(16);
            PackWeatherFeed.writeInto(bare, slot, PackWeatherFeed.WeatherState.calm(0.5f));
            assertEquals(0.0f, slot.get(ValueLayout.JAVA_FLOAT, 0), 0.0f);
        }
    }

    private static void assertVec4(MemorySegment slot, long offset,
                                   float x, float y, float z, float w) {
        assertEquals(x, slot.get(ValueLayout.JAVA_FLOAT, offset), 1e-6f);
        assertEquals(y, slot.get(ValueLayout.JAVA_FLOAT, offset + 4), 1e-6f);
        assertEquals(z, slot.get(ValueLayout.JAVA_FLOAT, offset + 8), 1e-6f);
        assertEquals(w, slot.get(ValueLayout.JAVA_FLOAT, offset + 12), 1e-6f);
    }
}
