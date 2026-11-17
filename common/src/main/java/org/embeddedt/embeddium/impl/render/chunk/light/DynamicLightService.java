package org.embeddedt.embeddium.impl.render.chunk.light;

/**
 * The shading-time dynamic light sources of the current frame: light that exists only because an entity
 * carries or emits it (a held torch, a dropped glowing item, a burning mob), which the world's baked
 * lightmap cannot know about because nothing was remeshed.
 * <p>
 * The mechanism is the one VulkanMod-Next (LGPL-3.0-or-later) implements for the same game version,
 * reimplemented here in this tree's shape: once per frame the implementation collects the sources
 * around the camera into a flat array of vec4s - xyz the camera-relative position, w the light level
 * on vanilla's 0..15 scale - and the terrain shader raises a vertex's baked block light by
 * {@code level - distance} per source, taking the maximum, through the game's own lightmap. The
 * falloff is therefore vanilla's own (one level per block) and the colour is the lightmap's, not an
 * added white.
 * <p>
 * A backend that draws terrain itself reads this service the way it reads {@link
 * org.embeddedt.embeddium.impl.render.chunk.fog.FogService}: from the renderer, once per frame. An
 * implementation must be cheap enough to call every frame and must tolerate being called with no
 * world loaded (it then reports no sources).
 */
public interface DynamicLightService {

    /** The most sources one frame may carry. The shader block is sized by this number. */
    int MAX_LIGHTS = 32;

    /**
     * How far from the camera a source may be and still be collected, in blocks. The radius has to
     * cover how far <em>lit ground can be seen</em>, not how far light reaches (a level-15 source
     * lights ground 15 blocks away, and that ground is visible from much farther); VulkanMod-Next's
     * recorded lesson, from shipping 24 and finding pools clipped at the edge of view.
     */
    double GATHER_RADIUS = 32.0D;

    /** A service that never reports a source: the vanilla look, and the default everywhere. */
    DynamicLightService EMPTY = (out, cameraX, cameraY, cameraZ) -> 0;

    /**
     * Collects this frame's sources around the camera.
     *
     * @param out     receives {@code 4 * MAX_LIGHTS} floats: per source x, y, z (camera-relative) and
     *                the level; only the first {@code 4 * count} entries are written
     * @param cameraX the camera's world x for this frame
     * @param cameraY the camera's world y for this frame
     * @param cameraZ the camera's world z for this frame
     * @return how many sources were collected, {@code 0..MAX_LIGHTS}; the nearest sources win when
     *         more than {@link #MAX_LIGHTS} are in range
     */
    int gatherLights(float[] out, double cameraX, double cameraY, double cameraZ);
}
