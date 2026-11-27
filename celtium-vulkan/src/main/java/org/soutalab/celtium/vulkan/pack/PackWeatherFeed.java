/*
 * Built-in shader pack engine for Celtium (LGPL-3.0).
 */
package org.soutalab.celtium.vulkan.pack;

import org.soutalab.celtium.core.shader.PackExecutionPlan;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Optional;

/**
 * The executor's weather feed, per the weather contract ruling (Neon, 2026-11-27; plan
 * section 8.3 item 7, option (a)): the transform declares the per-vertex corner, the five
 * per-instance attributes and these four per-frame uniforms, and emits the reconstruction
 * prologue; what the four vectors carry is defined here, with the proven glcompat
 * arrangement's semantics:
 *
 * <ul>
 *   <li>params0: layer translation xyz (floor(view) - view per axis, y negated), inverse
 *       layer radius w</li>
 *   <li>params1: camera fractional position x/z in xy, partialTicks z, layer age w</li>
 *   <li>params2: vanilla tick x, provider tick y, rain strength z, provider age w
 *       (ticks wrapped at 1024)</li>
 *   <li>params3: provider inverse radius in x (yzw reserved, zero)</li>
 * </ul>
 *
 * The feed writes only the fields the plan's uniform layout actually carries, so packs
 * without weather programs are untouched.
 */
public final class PackWeatherFeed {

    /** One frame of weather facts, supplied by the frame environment. */
    public record WeatherState(
            float translateX, float translateY, float translateZ, float inverseRadius,
            float cameraFracX, float cameraFracZ, float partialTicks, float layerAge,
            float vanillaTick, float providerTick, float rainStrength, float providerAge,
            float providerInverseRadius) {

        public static WeatherState calm(float partialTicks) {
            return new WeatherState(0f, 0f, 0f, 0f, 0f, 0f, partialTicks, 0f,
                    0f, 0f, 0f, 0f, 0f);
        }
    }

    private PackWeatherFeed() {
    }

    /** Writes the four weather vectors into {@code slot} at the layout's field offsets. */
    public static void writeInto(PackExecutionPlan.PackUniformLayout layout, MemorySegment slot,
                                 WeatherState state) {
        writeVec4(layout, slot, "iris_WeatherParams0",
                state.translateX(), state.translateY(), state.translateZ(), state.inverseRadius());
        writeVec4(layout, slot, "iris_WeatherParams1",
                state.cameraFracX(), state.cameraFracZ(), state.partialTicks(), state.layerAge());
        writeVec4(layout, slot, "iris_WeatherParams2",
                wrapTick(state.vanillaTick()), wrapTick(state.providerTick()),
                state.rainStrength(), state.providerAge());
        writeVec4(layout, slot, "iris_WeatherParams3",
                state.providerInverseRadius(), 0f, 0f, 0f);
    }

    private static float wrapTick(float tick) {
        float wrapped = tick % 1024.0f;
        return wrapped < 0.0f ? wrapped + 1024.0f : wrapped;
    }

    private static void writeVec4(PackExecutionPlan.PackUniformLayout layout, MemorySegment slot,
                                  String fieldName, float x, float y, float z, float w) {
        Optional<PackExecutionPlan.PackUniformLayout.UniformField> field = layout.fieldNamed(fieldName);
        if (field.isEmpty()) {
            return;
        }
        long offset = field.get().offsetBytes();
        slot.set(ValueLayout.JAVA_FLOAT, offset, x);
        slot.set(ValueLayout.JAVA_FLOAT, offset + 4L, y);
        slot.set(ValueLayout.JAVA_FLOAT, offset + 8L, z);
        slot.set(ValueLayout.JAVA_FLOAT, offset + 12L, w);
    }
}
