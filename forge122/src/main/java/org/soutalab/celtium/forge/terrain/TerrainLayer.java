package org.soutalab.celtium.forge.terrain;

/**
 * The render-layer bucket a terrain face belongs to. The mesher buckets its output by layer so
 * the draw stage can issue each layer with its own pipeline state; the values line up with the
 * main-view half of {@code TerrainPassKind} (SOLID, CUTOUT, TRANSLUCENT) - the shadow kinds are
 * the same buckets drawn from the shadow camera, not separate geometry.
 */
public enum TerrainLayer {
    SOLID,
    CUTOUT,
    TRANSLUCENT;

    public static final int COUNT = 3;
}
