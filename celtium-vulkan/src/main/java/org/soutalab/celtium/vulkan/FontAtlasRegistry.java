package org.soutalab.celtium.vulkan;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Which per-page font textures live inside the current celtium font atlas, and where (QUEUE 631).
 * <p>
 * The atlas is built and published on the game side ({@code CeltiumFontAtlas} in forge122), which is also the side
 * that knows the page textures' GL names; the run keying that can use the mapping lives here, in {@link FontBatch}.
 * This registry is the meeting point: the game side registers a {@link FontAtlasRemap} per page texture whenever an
 * atlas is (re)built, and the backend's font state view answers {@code FontBatch.FontRunState.atlasRemap} from it.
 * <p>
 * A registration is only honoured while its atlas is the current one. An atlas rebuild deletes the old GL texture
 * and publishes a new name; a page entry left pointing at the dead name must go inert rather than key a run by a
 * texture that no longer holds the atlas, so {@link #remapFor} compares against {@link #currentAtlasTexture},
 * which the game side moves (and {@link #invalidate()} zeroes when a build fails).
 * <p>
 * Static because the game side registers from static atlas code that holds no backend instance; the render thread
 * is the only writer in practice, and the map is concurrent so a stray read can never corrupt it.
 */
public final class FontAtlasRegistry {

    private FontAtlasRegistry() {
    }

    private static final ConcurrentHashMap<Integer, FontAtlasRemap> BY_PAGE_TEXTURE =
            new ConcurrentHashMap<Integer, FontAtlasRemap>();

    /** The GL name of the atlas registrations currently describe, or 0 when no atlas is live. */
    private static volatile int currentAtlasTexture;

    /** Names {@code atlasTexture} as the atlas the registrations describe. Called on every atlas (re)build. */
    public static void setCurrentAtlas(int atlasTexture) {
        currentAtlasTexture = atlasTexture;
    }

    /** Registers (or replaces) the atlas rectangle for one page texture's GL name. */
    public static void register(int pageTexture, FontAtlasRemap remap) {
        if (pageTexture > 0 && remap != null && remap.atlasTexture > 0) {
            BY_PAGE_TEXTURE.put(Integer.valueOf(pageTexture), remap);
        }
    }

    /** Drops every registration: the atlas build failed, so no page may be keyed into it. */
    public static void invalidate() {
        currentAtlasTexture = 0;
        BY_PAGE_TEXTURE.clear();
    }

    /**
     * {@return the remap for {@code texture} when it is a registered page of the current atlas, else {@code null}}.
     * The atlas texture itself is not a key, so a segment already bound with it answers null and keeps its UVs.
     */
    public static FontAtlasRemap remapFor(int texture) {
        int atlas = currentAtlasTexture;
        if (atlas == 0 || texture == atlas) {
            return null;
        }
        FontAtlasRemap remap = BY_PAGE_TEXTURE.get(Integer.valueOf(texture));
        return remap != null && remap.atlasTexture == atlas ? remap : null;
    }
}
