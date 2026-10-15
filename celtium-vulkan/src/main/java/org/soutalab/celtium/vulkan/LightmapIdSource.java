package org.soutalab.celtium.vulkan;

import java.util.function.IntSupplier;

/**
 * Where the terrain backend learns the light map's GL texture name.
 * <p>
 * Measured 2026-10-15 (leg {@code gv-cohort-vp1}): the lightmapped batches were refused with
 * {@code lightmapped batch with no lightmap texture mirrored (id=-1 unit=33985)} - the unit was right, the id was
 * not. The backend learns the id by asking GL for the texture bound on that unit, and in 1.12.2 the bind goes
 * through {@code GlStateManager.bindTexture}, which skips the GL call entirely when its own per-unit cache already
 * holds the name. Nothing here can see a call that was never made, so the id has to come from the object that owns
 * it: {@code EntityRenderer.lightmapTexture}, a {@code DynamicTexture} whose {@code getGlTextureId()} is the
 * authoritative answer.
 * <p>
 * That object lives in the game, which this module does not depend on, so the game side installs a supplier here -
 * the same direction the GL-less stub already travels, with {@code forge122} depending on this module rather than
 * the reverse.
 */
public final class LightmapIdSource {
    private static volatile IntSupplier source;
    private static volatile java.util.function.Supplier<int[]> pixelSource;

    private LightmapIdSource() {
    }

    /** Installs the supplier the game side provides; the last installation wins. */
    public static void install(IntSupplier supplier) {
        source = supplier;
    }

    /** Installs the pixel supplier the game side provides; the last installation wins. */
    public static void installPixels(java.util.function.Supplier<int[]> supplier) {
        pixelSource = supplier;
    }

    /**
     * {@return the light map's pixels as the game's own ARGB ints, or null when none are available}.
     * <p>
     * The mirror needs the texels and cannot get them from the stub: 1.12.2 uploads the light map through
     * GlStateManager, which skips the GL calls when its cache already holds the state, so the stub captures no
     * upload for that level and uploadFromGlTexture throws 'the stub captured no upload for this texture level'
     * (measured on leg gv-cohort-lm5). The DynamicTexture that owns the pixels always has them.
     */
    public static int[] lightmapPixels() {
        java.util.function.Supplier<int[]> current = pixelSource;
        if (current == null) {
            return null;
        }
        try {
            return current.get();
        } catch (RuntimeException | LinkageError error) {
            return null;
        }
    }

    /** {@return the light map's GL texture name, or -1 when the game side has not installed a source} */
    public static int lightmapTextureId() {
        IntSupplier current = source;
        if (current == null) {
            return -1;
        }
        try {
            return current.getAsInt();
        } catch (RuntimeException | LinkageError error) {
            // A source that cannot answer must not take the frame down with it; -1 keeps the existing refusal path.
            return -1;
        }
    }
}
