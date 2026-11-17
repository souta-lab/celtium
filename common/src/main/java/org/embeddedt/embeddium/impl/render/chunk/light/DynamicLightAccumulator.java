package org.embeddedt.embeddium.impl.render.chunk.light;

/**
 * The selection half of a {@link DynamicLightService} gather, kept free of game types so it can be
 * unit-tested: which sources survive the radius filter and the {@link DynamicLightService#MAX_LIGHTS}
 * cap, and the conversion of the survivors to camera-relative coordinates.
 * <p>
 * The cap rule is VulkanMod-Next's: when more sources are in range than the shader block holds, the
 * <em>farthest</em> are dropped - the nearest sources are the ones whose pools are on screen. A
 * candidate that is not nearer than the current farthest survivor is refused once the accumulator is
 * full, so a full accumulator's contents only ever improve.
 * <p>
 * Not thread-safe; one accumulator serves one gather at a time, and {@link #begin} resets it.
 */
public final class DynamicLightAccumulator {

    private final double[] world = new double[DynamicLightService.MAX_LIGHTS * 3];
    private final double[] distanceSq = new double[DynamicLightService.MAX_LIGHTS];
    private final float[] levels = new float[DynamicLightService.MAX_LIGHTS];

    private double cameraX;
    private double cameraY;
    private double cameraZ;
    private int count;

    /** Starts a gather around the given camera position, discarding the previous frame's sources. */
    public void begin(double cameraX, double cameraY, double cameraZ) {
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.count = 0;
    }

    /**
     * Offers one source at a world position. Sources with a non-positive level, and sources outside
     * {@link DynamicLightService#GATHER_RADIUS} of the camera, are ignored.
     */
    public void offer(double x, double y, double z, float level) {
        if (level <= 0.0F) {
            return;
        }
        double dx = x - this.cameraX;
        double dy = y - this.cameraY;
        double dz = z - this.cameraZ;
        double candidateSq = dx * dx + dy * dy + dz * dz;
        double radius = DynamicLightService.GATHER_RADIUS;
        if (candidateSq > radius * radius) {
            return;
        }
        if (this.count < DynamicLightService.MAX_LIGHTS) {
            store(this.count++, x, y, z, level, candidateSq);
            return;
        }
        int farthest = 0;
        for (int i = 1; i < this.count; i++) {
            if (this.distanceSq[i] > this.distanceSq[farthest]) {
                farthest = i;
            }
        }
        if (candidateSq < this.distanceSq[farthest]) {
            store(farthest, x, y, z, level, candidateSq);
        }
    }

    private void store(int slot, double x, double y, double z, float level, double candidateSq) {
        this.world[slot * 3] = x;
        this.world[slot * 3 + 1] = y;
        this.world[slot * 3 + 2] = z;
        this.levels[slot] = level;
        this.distanceSq[slot] = candidateSq;
    }

    /**
     * Writes the surviving sources into {@code out} as camera-relative vec4s (x, y, z, level) and
     * returns how many were written.
     */
    public int finish(float[] out) {
        for (int i = 0; i < this.count; i++) {
            out[i * 4] = (float) (this.world[i * 3] - this.cameraX);
            out[i * 4 + 1] = (float) (this.world[i * 3 + 1] - this.cameraY);
            out[i * 4 + 2] = (float) (this.world[i * 3 + 2] - this.cameraZ);
            out[i * 4 + 3] = this.levels[i];
        }
        return this.count;
    }

    /** {@return how many sources the current gather holds so far}. */
    public int count() {
        return this.count;
    }
}
