package org.soutalab.celtium.vulkan;

/**
 * One font page's rectangle inside the celtium font atlas, as an affine UV transform.
 * <p>
 * A glyph segment appended while a per-page texture is bound carries UVs in that page's own space (0..1 across the
 * page). The same glyph lives in the atlas at {@code u' = u * uScale + uOffset}, {@code v' = v * vScale + vOffset},
 * so a run keyed by the atlas texture can hold the segment once its UVs are rewritten - which is what lets
 * {@link FontBatch} fold a page-bound segment into an atlas run instead of closing the run at the texture witness.
 * <p>
 * Immutable and allocation-free to read: the registry hands out the same instance for every glyph of a page.
 */
public final class FontAtlasRemap {

    /** The GL name of the atlas texture the page was rasterised into. */
    public final int atlasTexture;
    public final float uScale;
    public final float uOffset;
    public final float vScale;
    public final float vOffset;

    public FontAtlasRemap(int atlasTexture, float uScale, float uOffset, float vScale, float vOffset) {
        this.atlasTexture = atlasTexture;
        this.uScale = uScale;
        this.uOffset = uOffset;
        this.vScale = vScale;
        this.vOffset = vOffset;
    }

    /** {@return the atlas-space U for a page-space {@code u}}. */
    public float remapU(float u) {
        return u * this.uScale + this.uOffset;
    }

    /** {@return the atlas-space V for a page-space {@code v}}. */
    public float remapV(float v) {
        return v * this.vScale + this.vOffset;
    }
}
