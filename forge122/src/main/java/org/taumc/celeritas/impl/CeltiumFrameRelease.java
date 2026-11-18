package org.taumc.celeritas.impl;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.soutalab.celtium.vulkan.gl.CeltiumGlStub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Item 387 (361-B, absorption rank #9 - the TexFix rule): give back the CPU-side texture data that nothing
 * reads again once every atlas has loaded.
 * <p>
 * Three holders are covered, in the disposition's order. First the census: every sprite the atlas loader path
 * saw is registered by {@link #noteSprite} and measured at the end of a resource reload - how many sprites,
 * how many are animated, how many frame elements the static ones hold and what those elements weigh. That
 * census is what decides the release scope: the survey's "a static sprite holds exactly one frame element"
 * was an inference from the {@code framesTextureData} structure, and the census prints the measured
 * distribution (single-frame vs multi-frame static sprites) next to the byte counts on every reload, release
 * on or off.
 * <p>
 * Second, the sprite frames themselves. The release runs only from {@link #onResourceReloadComplete} - after
 * {@code Minecraft.refreshResources} has run every reload listener, so every {@code TextureMap} has finished
 * loading, never per atlas: a custom loader in a later atlas may still read an earlier sprite's frame 0 while
 * its own atlas loads (the Tinkers coloured-parts case {@link CeltiumAtlasLoader} documents). Animated
 * sprites are never released - {@code updateAnimation} reads their frames every tick. Static sprites are
 * safe to clear because a reload rebuilds them from the resource: {@code loadTexture} calls
 * {@code initSprite}, which resets the frame list, before {@code loadSpriteFrames} reads the PNG again.
 * A mod that reads frame data after the load is the known counterexample, so the release starts default-off
 * ({@code -Dceltium.releaseTextureCpuData=true} to enable) with an exclusion list
 * ({@code -Dceltium.releaseTextureCpuData.exclude=name,name} - icon-name substrings never released).
 * Sprites whose custom loader succeeded take neither the census nor the release: they never reach the
 * {@code generateMipmaps} call this class is fed from, which is the conservative direction.
 * <p>
 * Third, the GL stub's own captures, through {@link CeltiumGlStub#releaseSettledTextureCaptures} under the
 * same switch: the stub keeps an RGBA8 copy of every upload as the mirrors' supply source, and the settled
 * rule there drops only the copies a mirror has already taken in full and nothing has patched since.
 */
public final class CeltiumFrameRelease {
    /** Master switch for both releases; the census runs either way. Default off (361-B condition 3). */
    public static final boolean RELEASE = Boolean.getBoolean("celtium.releaseTextureCpuData");

    /** Icon-name substrings that are never released, from {@code -Dceltium.releaseTextureCpuData.exclude}. */
    private static final String[] EXCLUDED = parseExcluded();

    /**
     * Every sprite the loader path has seen, by identity. Weak: a sprite its map drops is nobody's release
     * target, and the maps hold the live ones strongly, so the set tracks the current generation on its own.
     * Sprites are reused across reloads (the map's registered set persists), so entries stay valid targets
     * for the next pass without re-registration.
     */
    private static final Set<TextureAtlasSprite> SPRITES =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static long passes;

    private CeltiumFrameRelease() {
    }

    private static String[] parseExcluded() {
        String raw = System.getProperty("celtium.releaseTextureCpuData.exclude", "");
        List<String> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out.toArray(new String[0]);
    }

    /** Registers a sprite the atlas load path just handled. Called on the render thread from the load redirect. */
    public static void noteSprite(TextureAtlasSprite sprite) {
        if (sprite != null) {
            SPRITES.add(sprite);
        }
    }

    /** The retained frame bytes of one sprite: every frame element's whole mip chain, ints at 4 bytes. */
    private static long frameBytes(TextureAtlasSprite sprite) {
        long bytes = 0L;
        int frames = sprite.getFrameCount();
        for (int f = 0; f < frames; f++) {
            int[][] frame = sprite.getFrameTextureData(f);
            if (frame == null) {
                continue;
            }
            for (int[] level : frame) {
                if (level != null) {
                    bytes += 4L * level.length;
                }
            }
        }
        return bytes;
    }

    private static boolean excluded(String iconName) {
        if (iconName == null) {
            return false;
        }
        for (String token : EXCLUDED) {
            if (iconName.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The end-of-reload pass, called from {@code Minecraft.refreshResources} at RETURN - the one point where
     * every reload listener (and with it every {@code TextureMap} load) has completed. Prints the census line
     * on every pass; with the release switch on, also clears the static sprites' frames and asks the stub to
     * drop its settled captures, then prints what was given back.
     */
    public static void onResourceReloadComplete() {
        passes++;
        List<TextureAtlasSprite> snapshot;
        synchronized (SPRITES) {
            snapshot = new ArrayList<>(SPRITES);
        }
        long sprites = 0L;
        long animated = 0L;
        long animatedBytes = 0L;
        long staticSprites = 0L;
        long staticBytes = 0L;
        long singleFrameSprites = 0L;
        long singleFrameBytes = 0L;
        for (TextureAtlasSprite sprite : snapshot) {
            int frames = sprite.getFrameCount();
            if (frames == 0) {
                continue;
            }
            sprites++;
            long bytes = frameBytes(sprite);
            if (sprite.hasAnimationMetadata()) {
                animated++;
                animatedBytes += bytes;
            } else {
                staticSprites++;
                staticBytes += bytes;
                if (frames == 1) {
                    singleFrameSprites++;
                    singleFrameBytes += bytes;
                }
            }
        }
        long captureBytes = CeltiumGlStub.textureCaptureBytes();
        System.out.println("[Celtium] CELTIUM_FRAME_CENSUS pass=" + passes
                + " sprites=" + sprites
                + " animated=" + animated + " animatedBytes=" + animatedBytes
                + " static=" + staticSprites + " staticBytes=" + staticBytes
                + " singleFrameStatic=" + singleFrameSprites + " singleFrameBytes=" + singleFrameBytes
                + " captureBytes=" + captureBytes
                + " releaseEnabled=" + RELEASE);
        if (!RELEASE) {
            return;
        }
        long releasedSprites = 0L;
        long releasedBytes = 0L;
        long excludedSprites = 0L;
        for (TextureAtlasSprite sprite : snapshot) {
            if (sprite.hasAnimationMetadata() || sprite.getFrameCount() == 0) {
                continue;
            }
            if (excluded(sprite.getIconName())) {
                excludedSprites++;
                continue;
            }
            releasedBytes += frameBytes(sprite);
            sprite.clearFramesTextureData();
            releasedSprites++;
        }
        long[] captures = CeltiumGlStub.releaseSettledTextureCaptures();
        System.out.println("[Celtium] CELTIUM_FRAME_RELEASE pass=" + passes
                + " spritesReleased=" + releasedSprites + " spriteBytesFreed=" + releasedBytes
                + " excluded=" + excludedSprites
                + " capturesReleased=" + captures[0] + " captureBytesFreed=" + captures[1]);
    }
}
