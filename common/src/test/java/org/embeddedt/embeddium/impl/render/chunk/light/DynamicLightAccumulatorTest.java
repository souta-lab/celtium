package org.embeddedt.embeddium.impl.render.chunk.light;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightAccumulatorTest {

    private static float[] gather(DynamicLightAccumulator accumulator) {
        float[] out = new float[DynamicLightService.MAX_LIGHTS * 4];
        int count = accumulator.finish(out);
        assertEquals(accumulator.count(), count);
        return out;
    }

    @Test
    void positionsComeOutCameraRelativeWithTheirLevel() {
        DynamicLightAccumulator accumulator = new DynamicLightAccumulator();
        accumulator.begin(100.0D, 64.0D, -50.0D);
        accumulator.offer(102.5D, 65.0D, -47.5D, 14.0F);

        float[] out = gather(accumulator);
        assertEquals(1, accumulator.count());
        assertEquals(2.5F, out[0]);
        assertEquals(1.0F, out[1]);
        assertEquals(2.5F, out[2]);
        assertEquals(14.0F, out[3]);
    }

    @Test
    void nonPositiveLevelsAndOutOfRadiusSourcesAreIgnored() {
        DynamicLightAccumulator accumulator = new DynamicLightAccumulator();
        accumulator.begin(0.0D, 0.0D, 0.0D);
        accumulator.offer(1.0D, 0.0D, 0.0D, 0.0F);
        accumulator.offer(1.0D, 0.0D, 0.0D, -3.0F);
        accumulator.offer(DynamicLightService.GATHER_RADIUS + 1.0D, 0.0D, 0.0D, 15.0F);
        assertEquals(0, accumulator.count());

        // Exactly on the radius is in range: the comparison is against the squared radius.
        accumulator.offer(DynamicLightService.GATHER_RADIUS, 0.0D, 0.0D, 15.0F);
        assertEquals(1, accumulator.count());
    }

    @Test
    void aFullAccumulatorKeepsTheNearestSources() {
        DynamicLightAccumulator accumulator = new DynamicLightAccumulator();
        accumulator.begin(0.0D, 0.0D, 0.0D);
        // Fill every slot with sources 20 blocks out, then offer one 1 block out: the near one must
        // displace a far one, because the nearest pools are the ones on screen.
        for (int i = 0; i < DynamicLightService.MAX_LIGHTS; i++) {
            accumulator.offer(20.0D, 0.0D, i * 0.1D, 15.0F);
        }
        assertEquals(DynamicLightService.MAX_LIGHTS, accumulator.count());
        accumulator.offer(1.0D, 0.0D, 0.0D, 9.0F);

        float[] out = gather(accumulator);
        boolean nearPresent = false;
        int farCount = 0;
        for (int i = 0; i < DynamicLightService.MAX_LIGHTS; i++) {
            if (out[i * 4 + 3] == 9.0F && out[i * 4] == 1.0F) {
                nearPresent = true;
            }
            if (out[i * 4 + 3] == 15.0F) {
                farCount++;
            }
        }
        assertTrue(nearPresent, "the near source displaced a far one");
        assertEquals(DynamicLightService.MAX_LIGHTS - 1, farCount);
    }

    @Test
    void aFullAccumulatorRefusesACandidateFartherThanItsFarthest() {
        DynamicLightAccumulator accumulator = new DynamicLightAccumulator();
        accumulator.begin(0.0D, 0.0D, 0.0D);
        for (int i = 0; i < DynamicLightService.MAX_LIGHTS; i++) {
            accumulator.offer(2.0D, 0.0D, i * 0.1D, 15.0F);
        }
        accumulator.offer(10.0D, 0.0D, 0.0D, 7.0F);

        float[] out = gather(accumulator);
        for (int i = 0; i < DynamicLightService.MAX_LIGHTS; i++) {
            assertEquals(15.0F, out[i * 4 + 3], "no stored source was displaced by the farther candidate");
        }
    }

    @Test
    void beginDiscardsThePreviousGather() {
        DynamicLightAccumulator accumulator = new DynamicLightAccumulator();
        accumulator.begin(0.0D, 0.0D, 0.0D);
        accumulator.offer(1.0D, 0.0D, 0.0D, 15.0F);
        accumulator.begin(500.0D, 0.0D, 0.0D);
        assertEquals(0, accumulator.count());
        accumulator.offer(501.0D, 0.0D, 0.0D, 12.0F);
        float[] out = gather(accumulator);
        assertEquals(1.0F, out[0]);
        assertEquals(12.0F, out[3]);
    }
}
