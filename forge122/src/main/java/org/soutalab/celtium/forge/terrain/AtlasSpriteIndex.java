package org.soutalab.celtium.forge.terrain;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Assigns each atlas sprite its bindless texture-table index and its atlas cell origin (the
 * {@code PackedAxisQuad} cell fields, in 16-pixel cells). Indices are handed out sequentially
 * in first-seen order and are stable for the lifetime of the instance; the terrain HAL builds
 * its texture table in this same order. When no atlas size has been set, cells resolve to
 * (0, 0) - the index still identifies the sprite, and cell addressing is the atlas path's
 * refinement.
 */
public final class AtlasSpriteIndex {
    private final Map<TextureAtlasSprite, Integer> indices = new IdentityHashMap<>();
    private final Map<TextureAtlasSprite, Integer> cells = new IdentityHashMap<>();
    private int atlasWidthPx;
    private int atlasHeightPx;
    private int nextIndex;

    /** Records the block atlas' pixel size, from which sprite cells are computed. */
    public synchronized void setAtlasSize(int widthPx, int heightPx) {
        this.atlasWidthPx = widthPx;
        this.atlasHeightPx = heightPx;
        cells.clear();
    }

    public synchronized int indexFor(TextureAtlasSprite sprite) {
        if (sprite == null) {
            return 0;
        }
        Integer existing = indices.get(sprite);
        if (existing != null) {
            return existing;
        }
        int index = ++nextIndex;
        indices.put(sprite, index);
        return index;
    }

    /** The sprite's atlas cell, packed {@code cellU | (cellV << 8)}; 0 when unknown. */
    public synchronized int cellFor(TextureAtlasSprite sprite) {
        if (sprite == null || atlasWidthPx <= 0 || atlasHeightPx <= 0) {
            return 0;
        }
        Integer existing = cells.get(sprite);
        if (existing != null) {
            return existing;
        }
        int cellU = Math.round(sprite.getMinU() * atlasWidthPx / 16.0f);
        int cellV = Math.round(sprite.getMinV() * atlasHeightPx / 16.0f);
        int packed = (cellU & 0xFF) | ((cellV & 0xFF) << 8);
        cells.put(sprite, packed);
        return packed;
    }

    public synchronized int size() {
        return indices.size();
    }
}
