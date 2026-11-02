package org.soutalab.celtium.vulkan.gl;

/**
 * What the {@code glGetTexLevelParameteri} family answers for a texture's level-0 extent.
 * <p>
 * The transform's fixed answer used to be 256 for every call, chosen because the two callers that motivated the route
 * divide by it and both textures are 256 square (the light map and Forge's cloud texture). Every other caller divides
 * by it too, and one of them is the title screen: CustomMainMenu measures the bound image to compute a {@code MODE.FILL}
 * cover rectangle and the texture coordinates of the quad, so a 1920x1080 slide that is answered as 256 square is
 * drawn with the wrong extent. The extent the uploads recorded for the name is the number GL would answer, and the
 * fixed 256 stays as the fallback for a name with no record - a proxy target, a level above 0, a texture the stub has
 * never seen - because a usable divisor is what those callers assume and a zero is not.
 * <p>
 * No GL dependency, so the two names and the fallback are pinned by a plain JVM test rather than by a leg.
 */
public final class TextureLevelAnswer {

    /** GL's name for a texture level's width in a level query. */
    public static final int GL_TEXTURE_WIDTH = 0x1000;
    /** GL's name for a texture level's height in a level query. */
    public static final int GL_TEXTURE_HEIGHT = 0x1001;
    /** The answer for a texture whose extent the stub never recorded; see the class note for why it is not zero. */
    public static final int UNRECORDED_FALLBACK = 256;

    private TextureLevelAnswer() {
    }

    /**
     * {@return the recorded extent's width or height, or 0 when the query asks for something else or the extent is not
     * usable}. A query this does not answer must not be given a number from the extent.
     */
    public static int answer(int width, int height, int pname) {
        if (pname == GL_TEXTURE_WIDTH) {
            return width > 0 ? width : 0;
        }
        if (pname == GL_TEXTURE_HEIGHT) {
            return height > 0 ? height : 0;
        }
        return 0;
    }

    /**
     * {@return the value a caller receives: the measured answer when there is one, the fixed fallback otherwise}.
     * <p>
     * A parameter name that is neither the width nor the height has no fallback - the fixed answer invented a 256 for it
     * as well, which claimed a value for a query nobody had measured.
     */
    public static int withFallback(int measured, int pname) {
        if (measured > 0) {
            return measured;
        }
        return (pname == GL_TEXTURE_WIDTH || pname == GL_TEXTURE_HEIGHT) ? UNRECORDED_FALLBACK : 0;
    }
}
