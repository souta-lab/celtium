package org.soutalab.celtium.vulkan;

/**
 * The vertex layouts the immediate path recognises, in one place.
 * <p>
 * The id set is defined by the mapping in the uploader mixin, and everything that has to agree with it - the acceptance mask,
 * the range check, the stride table, and every counter that reports per-format activity - used to carry its own idea of how
 * many there were. Three counters in a row were found to be hiding an id behind their own length: the end-of-run acceptance
 * report stopped at 6 and hid 7, the arrival census stopped at 8 and hid 9, and the incremental acceptance table stopped at 7
 * and hid 8 and 9. Each was invisible until someone widened it.
 * <p>
 * So the count lives here, and the counters are sized from it rather than from whichever ids happened to exist on the day they
 * were written.
 */
public final class VulkanImmediateFormats {

    /** Colour only: the plain immediate quads. */
    public static final int POSITION_COLOR = 1;
    /** Position, texture and colour. */
    public static final int POSITION_TEX_COLOR = 2;
    /** The block layout, with its packed light and normal. */
    public static final int BLOCK = 3;
    /** The item and entity layout: position, texture, colour and a normal the shaders do not read. */
    public static final int ITEM = 4;
    /** Particles: position, texture, colour and a light map. */
    public static final int PARTICLE = 5;
    /** Colourless position and texture: the sky, the clouds and the water surface. */
    public static final int POSITION_TEX = 6;
    /** Position only: twelve bytes, with the colour the game had current passed in the push block. */
    public static final int POSITION = 7;
    /** Position, texture, colour and a normal, in the wider standard layout. */
    public static final int POSITION_TEX_COLOR_NORMAL = 8;
    /** Position, texture and a normal: the final composite's layout. */
    public static final int POSITION_TEX_NORMAL = 9;

    /** One past the highest id, for every array, mask and range check that has to cover them all. */
    public static final int COUNT = POSITION_TEX_NORMAL + 1;

    /** Ids the acceptance mask admits by default: everything mapped except the composite, which the present path keys on. */
    public static final String DEFAULT_ACCEPTED = "1,2,3,4,5,6,7,8,9";

    private VulkanImmediateFormats() {
    }
}
