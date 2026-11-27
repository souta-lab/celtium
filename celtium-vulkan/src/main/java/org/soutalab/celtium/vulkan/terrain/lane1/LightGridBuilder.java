package org.soutalab.celtium.vulkan.terrain.lane1;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;

/**
 * Builds the V2-5 dynamic-light grid the terrain shaders sample (design V040-FINAL-PLAN section
 * 2.1): up to 64 point lights in a 2 KiB uniform block, plus a 16 x 8 x 16 cluster mask volume of
 * 16 KiB (one uint64 bitmask per cluster naming the lights that reach it). The vertex stage walks
 * a fragment's cluster mask with {@code findLSB} and evaluates only the lights that reach the
 * cluster, so the per-vertex cost tracks local light density, not the global light count.
 * <p>
 * Cluster cells are one section wide (16 blocks) horizontally and vertically: the grid covers
 * 256 x 128 x 256 blocks around a grid origin the caller snaps to section boundaries near the
 * camera. A light that reaches no cluster is dropped from the UBO entirely, because a mask bit
 * for it could never be set; the builder keeps the strongest 64 lights by intensity-over-distance
 * to the grid centre when over-subscribed.
 * <p>
 * UBO light record (32 B): position xyz + radius, colour rgb + intensity. Pure CPU code over
 * caller-owned segments; unit-testable without a device.
 */
public final class LightGridBuilder {

    public static final int MAX_LIGHTS = 64;
    public static final int LIGHT_RECORD_BYTES = 32;
    public static final int UBO_BYTES = MAX_LIGHTS * LIGHT_RECORD_BYTES; // 2048

    public static final int CLUSTER_X = 16;
    public static final int CLUSTER_Y = 8;
    public static final int CLUSTER_Z = 16;
    public static final int CLUSTER_COUNT = CLUSTER_X * CLUSTER_Y * CLUSTER_Z; // 2048
    public static final int CLUSTER_MASK_BYTES = CLUSTER_COUNT * 8; // 16384
    public static final int CELL_BLOCKS = 16;

    /** One dynamic point light in world space. */
    public record PointLight(double x, double y, double z, float radius,
                             float red, float green, float blue, float intensity) {
    }

    /** The build result: how many lights were placed, and the grid origin (block coordinates). */
    public record Result(int lightCount, int originX, int originY, int originZ) {
    }

    private LightGridBuilder() {
    }

    /**
     * Writes the UBO image into {@code ubo} (at least {@link #UBO_BYTES}) and the cluster masks
     * into {@code masks} (at least {@link #CLUSTER_MASK_BYTES}), both zeroed first. The grid origin
     * is the section boundary at or below {@code centerX/Y/Z} minus half the grid extent, so the
     * camera's section sits at the grid's centre cluster.
     */
    public static Result build(List<PointLight> lights, double centerX, double centerY, double centerZ,
                               MemorySegment ubo, MemorySegment masks) {
        if (ubo.byteSize() < UBO_BYTES || masks.byteSize() < CLUSTER_MASK_BYTES) {
            throw new IllegalArgumentException("light grid targets too small: ubo " + ubo.byteSize()
                    + " (need " + UBO_BYTES + "), masks " + masks.byteSize()
                    + " (need " + CLUSTER_MASK_BYTES + ")");
        }
        ubo.fill((byte) 0);
        masks.fill((byte) 0);
        int originX = sectionFloor(centerX) - (CLUSTER_X / 2) * CELL_BLOCKS;
        int originY = sectionFloor(centerY) - (CLUSTER_Y / 2) * CELL_BLOCKS;
        int originZ = sectionFloor(centerZ) - (CLUSTER_Z / 2) * CELL_BLOCKS;

        // Rank by intensity at the grid centre, strongest first, so truncation drops the lights
        // that matter least; then keep only lights reaching at least one cluster.
        List<PointLight> ranked = lights.stream()
                .sorted((a, b) -> Float.compare(score(b, centerX, centerY, centerZ),
                        score(a, centerX, centerY, centerZ)))
                .toList();
        int placed = 0;
        for (PointLight light : ranked) {
            if (placed >= MAX_LIGHTS) {
                break;
            }
            // A light occupies exactly one bit; cluster membership is recorded per cluster below by
            // setting this light's bit in every cluster it reaches. The bit index is the slot.
            int slot = placed;
            boolean reaches = false;
            for (int cy = 0; cy < CLUSTER_Y; cy++) {
                for (int cz = 0; cz < CLUSTER_Z; cz++) {
                    for (int cx = 0; cx < CLUSTER_X; cx++) {
                        double cellMinX = originX + cx * CELL_BLOCKS;
                        double cellMinY = originY + cy * CELL_BLOCKS;
                        double cellMinZ = originZ + cz * CELL_BLOCKS;
                        if (sphereIntersectsCell(light, cellMinX, cellMinY, cellMinZ)) {
                            int cluster = (cy * CLUSTER_Z + cz) * CLUSTER_X + cx;
                            long existing = masks.get(ValueLayout.JAVA_LONG,
                                    (long) cluster * 8L);
                            masks.set(ValueLayout.JAVA_LONG, (long) cluster * 8L,
                                    existing | (1L << slot));
                            reaches = true;
                        }
                    }
                }
            }
            if (!reaches) {
                continue;
            }
            long base = (long) slot * LIGHT_RECORD_BYTES;
            ubo.set(ValueLayout.JAVA_FLOAT, base, (float) light.x());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 4L, (float) light.y());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 8L, (float) light.z());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 12L, light.radius());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 16L, light.red());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 20L, light.green());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 24L, light.blue());
            ubo.set(ValueLayout.JAVA_FLOAT, base + 28L, light.intensity());
            placed++;
        }
        return new Result(placed, originX, originY, originZ);
    }

    private static float score(PointLight light, double cx, double cy, double cz) {
        double dx = light.x() - cx;
        double dy = light.y() - cy;
        double dz = light.z() - cz;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        return (float) (light.intensity() / (1.0 + distance));
    }

    private static boolean sphereIntersectsCell(PointLight light, double minX, double minY, double minZ) {
        double maxX = minX + CELL_BLOCKS;
        double maxY = minY + CELL_BLOCKS;
        double maxZ = minZ + CELL_BLOCKS;
        double nearestX = Math.max(minX, Math.min(light.x(), maxX));
        double nearestY = Math.max(minY, Math.min(light.y(), maxY));
        double nearestZ = Math.max(minZ, Math.min(light.z(), maxZ));
        double dx = light.x() - nearestX;
        double dy = light.y() - nearestY;
        double dz = light.z() - nearestZ;
        return dx * dx + dy * dy + dz * dz <= (double) light.radius() * light.radius();
    }

    private static int sectionFloor(double coordinate) {
        return (int) Math.floor(coordinate / CELL_BLOCKS) * CELL_BLOCKS;
    }
}
