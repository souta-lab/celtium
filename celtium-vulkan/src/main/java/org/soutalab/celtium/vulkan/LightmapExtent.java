package org.soutalab.celtium.vulkan;

/**
 * The extent a light map mirror may be created at, and the shape of the pixel array that fills it.
 * <p>
 * <b>Why the pixels decide the size.</b> The light map is a {@code DynamicTexture}: its pixels are one {@code int[]}
 * holding the whole of level 0, and the array is what will be uploaded into the mirror. An image created at any other
 * size makes {@code uploadFromImage} recreate its resources behind the registry entry's record - the entry keeps the
 * old extent while the image has a new one - so the size and the content have to be decided together, from the source
 * that supplies the content.
 * <p>
 * <b>Why the stub's record is the fallback, not the first answer.</b> The light map's level is allocated with a NULL
 * data pointer ({@code TextureUtil.allocateTexture}) and written through {@code TextureUtil.uploadTexture}, whose bind
 * goes through {@code GlStateManager}. When the state manager's own cache already holds the name, no GL call is made,
 * so the stub may hold no extent for the name at all - the state its capture rule documents as "no upload has written
 * it". A recorded extent is still the best answer when no array is available, because it is the allocation the game
 * asked for.
 * <p>
 * <b>What is not an extent.</b> A side of 0 or 1 is the "unknown" answer, not a one-texel light map: the registry's
 * capture rule already treats a dimension of 1 as a non-record ({@code fillFromCapture}: "A dimension of 1 is not a
 * record"), and creating a 1x1 mirror is the placeholder the atlas path refuses to build for exactly this reason.
 */
final class LightmapExtent {
    /**
     * The smallest side an extent here may have. A 1x1 record is "unknown" (see the class note), so an image is only
     * built at 2 or more.
     */
    static final int MINIMUM_SIDE = 2;

    private LightmapExtent() {
    }

    /**
     * {@return the side of the square image this array holds, or 0 when it is not one}.
     * <p>
     * The light map's array is {@code width * height} ARGB ints with width equal to height, so a perfect square is the
     * whole level-0 image and nothing less is. A non-square array is not refused as "bad pixels" - it is refused as
     * an extent nothing can be built from, which is the honest answer: the caller leaves the mirror absent rather than
     * copying the first square-sized part of it and calling that the image.
     */
    static int squareSide(int[] pixels) {
        if (pixels == null) {
            return 0;
        }
        int length = pixels.length;
        if (length < MINIMUM_SIDE * MINIMUM_SIDE) {
            return 0;
        }
        int side = (int) Math.sqrt((double) length);
        // Math.sqrt of an int is exact enough here, but one step each way makes the answer independent of that.
        while ((long) side * side > length) {
            side--;
        }
        while ((long) (side + 1) * (side + 1) <= length) {
            side++;
        }
        return side >= MINIMUM_SIDE && side * side == length ? side : 0;
    }

    /**
     * {@return {@code {width, height}} to create the mirror at, or {@code {0, 0}} when no source answers}.
     *
     * @param stubWidth  the extent the stub recorded for level 0, or 0 when it recorded none
     * @param stubHeight the same for the height
     * @param ownerSide  the square side of the owner's whole-level pixel array, or 0 when it is not one; this outranks
     *                   the stub because it sizes the upload that is about to fill the image
     */
    static int[] resolve(int stubWidth, int stubHeight, int ownerSide) {
        if (ownerSide >= MINIMUM_SIDE) {
            return new int[] { ownerSide, ownerSide };
        }
        if (stubWidth >= MINIMUM_SIDE && stubHeight >= MINIMUM_SIDE) {
            return new int[] { stubWidth, stubHeight };
        }
        return new int[] { 0, 0 };
    }
}
