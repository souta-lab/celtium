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
    private final java.util.List<TextureAtlasSprite> byIndex = new java.util.ArrayList<>();
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
        while (byIndex.size() <= index) {
            byIndex.add(null);
        }
        byIndex.set(index, sprite);
        return index;
    }

    /** The number of table entries a {@link #cellTable()} snapshot carries (index 0 included). */
    public synchronized int size() {
        return nextIndex + 1;
    }

    /**
     * A snapshot of the index-to-cell table, packed {@code cellU | (cellV << 8)} per index, for the
     * backend's texture-cell buffer. Entry 0 (the unknown sprite) is cell 0.
     */
    public synchronized int[] cellTable() {
        int[] table = new int[nextIndex + 1];
        for (int i = 1; i <= nextIndex; i++) {
            TextureAtlasSprite sprite = i < byIndex.size() ? byIndex.get(i) : null;
            table[i] = sprite != null ? cellFor(sprite) : 0;
        }
        return table;
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

    /**
     * The registered sprite whose atlas rectangle contains the point {@code (u, v)}, or null
     * when none does. When several rectangles contain the point the smallest wins (the most
     * specific sprite). Used by the face table's defensive correction for baked quads whose
     * declared sprite does not match the UVs they carry.
     */
    public synchronized TextureAtlasSprite spriteContaining(float u, float v) {
        TextureAtlasSprite best = null;
        float bestArea = Float.POSITIVE_INFINITY;
        for (TextureAtlasSprite sprite : indices.keySet()) {
            float du = sprite.getMaxU() - sprite.getMinU();
            float dv = sprite.getMaxV() - sprite.getMinV();
            if (du <= 0.0f || dv <= 0.0f) {
                continue;
            }
            if (u < sprite.getMinU() || u > sprite.getMaxU()
                    || v < sprite.getMinV() || v > sprite.getMaxV()) {
                continue;
            }
            float area = du * dv;
            if (area < bestArea) {
                bestArea = area;
                best = sprite;
            }
        }
        return best;
    }
}
