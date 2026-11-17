package org.embeddedt.embeddium.impl.render.chunk;

/**
 * Bit positions of the per-section visuals flags word, under the names upstream Celeritas
 * publishes them.
 *
 * <p>Part of celtium's Demonica compatibility surface (design: COMPAT-SURFACE-DESIGN-2026-11-17.md,
 * Tier B). Demonica's ChunkAnimator integration (its patch C2) builds render lists with
 * {@code 1 << HAS_BLOCK_GEOMETRY} and hands them to celtium's {@code ChunkRenderList}, which
 * reads the same bit position. The values here were verified against the upstream Celeritas
 * jar Demonica pins (2.5.0-autobuild.d94ec1e5) before being written down.
 *
 * <p>Caution: celtium's own flags word is laid out by {@code RenderVisualsService}, whose
 * positions for the other flags differ from upstream's ({@code HAS_BLOCK_ENTITIES} is bit 2
 * there, not bit 1). Only {@link #HAS_BLOCK_GEOMETRY} coincides, and it is the only constant
 * Demonica reads. Nothing inside celtium should use this class for its own flags word.
 */
public class RenderSectionFlags {
    public static final int HAS_BLOCK_GEOMETRY = 0;
    public static final int HAS_BLOCK_ENTITIES = 1;
    public static final int HAS_ANIMATED_SPRITES = 2;
    public static final int NONE = 0;
}
