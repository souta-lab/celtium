package org.soutalab.celtium.vulkan.terrain.lane1;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LightGridBuilderTest {

    private static LightGridBuilder.Result build(List<LightGridBuilder.PointLight> lights,
                                                 double cx, double cy, double cz,
                                                 MemorySegment ubo, MemorySegment masks) {
        return LightGridBuilder.build(lights, cx, cy, cz, ubo, masks);
    }

    @Test
    void oneLightLandsInTheUboAndItsCluster() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(LightGridBuilder.UBO_BYTES);
            MemorySegment masks = arena.allocate(LightGridBuilder.CLUSTER_MASK_BYTES);
            LightGridBuilder.PointLight light =
                    new LightGridBuilder.PointLight(8, 64, 8, 12f, 1f, 0.5f, 0.25f, 2f);
            LightGridBuilder.Result result = build(List.of(light), 8, 64, 8, ubo, masks);
            assertEquals(1, result.lightCount());
            assertEquals(8f, ubo.get(ValueLayout.JAVA_FLOAT, 0L), 0.001f);
            assertEquals(12f, ubo.get(ValueLayout.JAVA_FLOAT, 12L), 0.001f);
            assertEquals(2f, ubo.get(ValueLayout.JAVA_FLOAT, 28L), 0.001f);
            // The centre cluster of the grid (camera section) is (8, 4, 8) in cluster coords.
            int cluster = (4 * LightGridBuilder.CLUSTER_Z + 8) * LightGridBuilder.CLUSTER_X + 8;
            long mask = masks.get(ValueLayout.JAVA_LONG, (long) cluster * 8L);
            assertEquals(1L, mask, "the centre cluster sees light 0");
        }
    }

    @Test
    void lightOutsideTheGridIsDropped() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(LightGridBuilder.UBO_BYTES);
            MemorySegment masks = arena.allocate(LightGridBuilder.CLUSTER_MASK_BYTES);
            LightGridBuilder.PointLight far =
                    new LightGridBuilder.PointLight(10000, 64, 10000, 8f, 1f, 1f, 1f, 1f);
            LightGridBuilder.Result result = build(List.of(far), 0, 64, 0, ubo, masks);
            assertEquals(0, result.lightCount());
        }
    }

    @Test
    void radiusReachesNeighbouringClusters() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(LightGridBuilder.UBO_BYTES);
            MemorySegment masks = arena.allocate(LightGridBuilder.CLUSTER_MASK_BYTES);
            // At the centre cluster's edge with a radius spanning into the +x neighbour.
            LightGridBuilder.PointLight light =
                    new LightGridBuilder.PointLight(15, 64, 8, 10f, 1f, 1f, 1f, 1f);
            build(List.of(light), 8, 64, 8, ubo, masks);
            int centre = (4 * LightGridBuilder.CLUSTER_Z + 8) * LightGridBuilder.CLUSTER_X + 8;
            int neighbour = (4 * LightGridBuilder.CLUSTER_Z + 8) * LightGridBuilder.CLUSTER_X + 9;
            assertEquals(1L, masks.get(ValueLayout.JAVA_LONG, (long) centre * 8L));
            assertEquals(1L, masks.get(ValueLayout.JAVA_LONG, (long) neighbour * 8L));
        }
    }

    @Test
    void oversubscriptionKeepsTheStrongest() {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ubo = arena.allocate(LightGridBuilder.UBO_BYTES);
            MemorySegment masks = arena.allocate(LightGridBuilder.CLUSTER_MASK_BYTES);
            List<LightGridBuilder.PointLight> lights = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                lights.add(new LightGridBuilder.PointLight(8, 64, 8, 6f, 1f, 1f, 1f, 0.1f));
            }
            lights.add(new LightGridBuilder.PointLight(8, 64, 8, 6f, 1f, 0f, 0f, 100f));
            LightGridBuilder.Result result = build(lights, 8, 64, 8, ubo, masks);
            assertEquals(LightGridBuilder.MAX_LIGHTS, result.lightCount());
            assertEquals(100f, ubo.get(ValueLayout.JAVA_FLOAT, 28L), 0.001f,
                    "the strongest light takes slot 0");
            assertTrue(LightGridBuilder.UBO_BYTES == 2048 && LightGridBuilder.CLUSTER_MASK_BYTES == 16384,
                    "the plan's 2 KiB UBO and 16 KiB mask sizes");
        }
    }
}
