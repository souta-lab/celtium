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
 * <h2>The mapping against {@code DefaultVertexFormats}, enumerated</h2>
 * Every format vanilla declares is listed below with the id it maps to. The element lists and the widths are from the
 * decompiled sources, not from a sample of what one run happened to draw: a layout is identified by its element list
 * <em>and</em> its byte width, because two of these formats differ in nothing else.
 * <pre>
 * DefaultVertexFormats             elements                                          width  id  pipeline
 * POSITION_COLOR                   pos3f col4ub                                       16    1  VulkanImmediatePipeline
 * POSITION_TEX_COLOR               pos3f tex2f col4ub                                 24    2  VulkanImmediateTexturePipeline
 * BLOCK                            pos3f col4ub tex2f lightmap2s                      28    3  VulkanImmediateBlockPipeline
 * ITEM                             pos3f col4ub tex2f nrm3b pad1b                     28    4  VulkanImmediateItemPipeline
 * PARTICLE_POSITION_TEX_COLOR_LMAP pos3f tex2f col4ub lightmap2s                     28    5  VulkanImmediateParticlePipeline
 * POSITION_TEX                     pos3f tex2f                                        20    6  VulkanImmediateTexturePipeline, colourless
 * POSITION                         pos3f                                              12    7  VulkanImmediateTexturePipeline, one attribute
 * POSITION_TEX_COLOR_NORMAL        pos3f tex2f col4ub nrm3b pad1b                     28    8  VulkanImmediateTexturePipeline, tex+colour
 * POSITION_TEX_NORMAL              pos3f tex2f nrm3b pad1b                            24    9  VulkanImmediateTexturePipeline, colourless+tex
 * OLDMODEL_POSITION_TEX_NORMAL     pos3f tex2f nrm3b pad1b                            24    9  the same; the element lists are identical
 * POSITION_NORMAL                  pos3f nrm3b pad1b                                  16   10  VulkanImmediateTexturePipeline, position only
 * POSITION_TEX_LMAP_COLOR          pos3f tex2f lightmap2s col4ub                      28   -   refused by name, unused in vanilla
 *
 * The widths are the runtime ones: vanilla's {@code VertexFormat.addElement} advances by the element's own
 * {@code getSize()} and pads nothing, so a 3-byte normal and a 1-byte padding make {@code POSITION_NORMAL} 16 bytes with
 * no rounding at all, {@code POSITION_TEX_NORMAL} 24 and {@code ITEM} 28 rather than the multiples of four an
 * aligned-stride table would give them. They were read back from the game's own {@code DefaultVertexFormats} through its
 * {@code getSize()}, not computed from the element list here.
 * </pre>
 * Three rows are worth a sentence each, because all three were wrong before this table existed:
 * <ul>
 * <li>{@code POSITION_NORMAL} had no branch in the mapping at all, so it fell through to id 0 and was refused. Its only user
 * is {@code Render.renderOffsetAABB}, the white box {@code RenderEntity.doRender} draws for an entity whose renderer is
 * missing - which is why that box is the visible symptom of a missing row in this table. It is 16 bytes: 12 for the
 * position, 3 for the normal and 1 of padding, with no colour and no texture, so the colour is whatever the game had
 * current (white, in that call).</li>
 * <li>{@code POSITION_TEX_NORMAL} and {@code OLDMODEL_POSITION_TEX_NORMAL} carry the same element list as each other -
 * position, texture, a 3-byte normal and a padding byte, 24 bytes - and differ from {@code POSITION_TEX_COLOR_NORMAL} only in
 * that they have no colour element and are four bytes narrower, 24 against 28. A comparison that stopped at the element list
 * answered the 24-byte form with the id-8 layout and then refused it for carrying 24 bytes where 28 were expected, which is
 * what kept the fishing line, both fireballs, the dragon fireball and the painting off the native path. Four call sites in
 * vanilla, not a hypothetical.</li>
 * <li>{@code POSITION_TEX_LMAP_COLOR} is the one vanilla format with no id. It is declared in {@code DefaultVertexFormats}
 * and referenced nowhere in Minecraft or Forge, and its element list is the particle layout with the last two elements
 * swapped; answering a 28-byte vertex with the particle pipeline because the width matches - and it does match, 28 - is
 * exactly the kind of guess this table exists to stop. The uploader refuses it by name.</li>
 * </ul>
 * <h2>The sweep: what the game actually hands to {@code BufferBuilder.begin}</h2>
 * The table above is every format {@code DefaultVertexFormats} declares. This is what the decompiled Minecraft and Forge
 * sources pass to {@code begin}/{@code func_181668_a}, counted, so a row that is declared but never drawn is not
 * confused with one that is drawn:
 * <pre>
 * format                            Minecraft call sites  Forge call sites  id
 * POSITION_TEX_COLOR                29 + 1 (mode 5)       5                  2
 * POSITION_TEX                      25                    2                  6
 * POSITION_COLOR                    12+8+8+3+2 (modes 5/7/3/6/1)  1            1
 * POSITION                          6                     0                  7
 * POSITION_TEX_NORMAL               4                     0                  9
 * BLOCK                             4                     2                  3
 * PARTICLE_POSITION_TEX_COLOR_LMAP  3                     0                  5
 * POSITION_TEX_COLOR_NORMAL         2                     0                  8
 * ITEM                              2                     1                  4
 * POSITION_NORMAL                   1 ({@code Render.renderOffsetAABB})  0    10
 * OLDMODEL_POSITION_TEX_NORMAL      1                     0                  9
 * POSITION_TEX_LMAP_COLOR           0                     0                  refused by name
 * the 32-byte particle layout       2 ({@code ParticleExplosionLarge},
 *                                     {@code ParticleSweepAttack})           refused by name
 * </pre>
 * The last two rows are the refusals, and both are refused rather than answered with a pipeline that only has the width
 * in common with them:
 * <ul>
 * <li>{@code POSITION_TEX_LMAP_COLOR} is declared and referenced nowhere (above).</li>
 * <li>The 32-byte particle layout is built inline -
 * {@code new VertexFormat().addElement(POSITION_3F).addElement(TEX_2F).addElement(COLOR_4UB).addElement(TEX_2S)
 * .addElement(NORMAL_3B).addElement(PADDING_1B)}, 12+8+4+4+3+1 - by {@code ParticleExplosionLarge} and
 * {@code ParticleSweepAttack} and by nothing else. It is the particle layout with a normal and its padding appended,
 * so the particle pipeline's four attributes would land on the right offsets at the wrong stride; it is left refused
 * until something draws it, and no scene in this workspace does.</li>
 * <li>The {@code RealmsDefaultVertexFormat} copies resolve through the same lists as their standard namesakes -
 * {@code RealmsBufferBuilder.begin} forwards to the real {@code BufferBuilder} - except
 * {@code POSITION_TEX2_COLOR}, whose second UV element is {@code TEX_2S} at index 1, which is text-identical to
 * {@code POSITION_TEX_LMAP_COLOR} and refused with it.</li>
 * </ul>
 * One thing this key does not see, stated so it is not mistaken for a complete identity: {@code toString()} omits an
 * element's index, so a mod format carrying a second <em>float</em> UV element at index 1 would read exactly like
 * {@code POSITION_TEX} at the same width. No vanilla format does that, the two {@code TEX_2S} lists above are the ones
 * that differ only by index and both are refused, and the uploader's unmapped-shape dump prints the indices beside the
 * text so such a format would be visible if one ever arrives.
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
    /** Position, texture, colour and a normal: 28 bytes, the widest of the standard formats. */
    public static final int POSITION_TEX_COLOR_NORMAL = 8;
    /**
     * Position, texture, a normal and a padding byte: 24 bytes, colourless.
     * <p>
     * Carries both {@code POSITION_TEX_NORMAL} and {@code OLDMODEL_POSITION_TEX_NORMAL}, whose element lists are
     * byte-for-byte the same. The composite batch the present path keys on has this layout, and so do the fireball, the
     * fishing line, the dragon fireball and the painting.
     */
    public static final int POSITION_TEX_NORMAL = 9;
    /**
     * Position, a normal and a padding byte: 16 bytes, no colour and no texture.
     * <p>
     * {@code DefaultVertexFormats.POSITION_NORMAL}. Its one user is {@code Render.renderOffsetAABB}, the white box drawn
     * for an entity whose renderer is missing. Nothing in this renderer evaluates fixed-function lighting, so the normal is
     * not read; it is carried for the stride, because the vertices arrive with it in them.
     */
    public static final int POSITION_NORMAL = 10;

    /** One past the highest id, for every array, mask and range check that has to cover them all. */
    public static final int COUNT = POSITION_NORMAL + 1;

    /** Ids the acceptance mask admits by default: every layout this class names. */
    public static final String DEFAULT_ACCEPTED = "1,2,3,4,5,6,7,8,9,10";

    /**
     * {@return the id whose element list is exactly the given one at the given width, or 0 when no known layout matches}.
     * <p>
     * The one table the uploader's mapping consults. It used to be a chain of {@code sameElements} calls in the mixin, which
     * is why the two formats that differ only in width could not be told apart: the chain asked whether the element list
     * matched and never asked how wide the vertex was. The width is part of the identity of a layout, so it is part of this
     * comparison.
     *
     * @param elements the element list, each element written as {@code VertexFormatElement.toString()} does
     * @param size     the format's byte width, which is also the stride the uploader passes with it
     * @param count    the element count, so a list with the same text and a different length cannot match
     */
    /*
     * The element lists, written exactly as VertexFormatElement.toString() writes them - count, usage display name, type
     * display name, one element after another separated by a space. Read from the sources rather than transcribed:
     * DefaultVertexFormats' static block gives each format's element order, and VertexFormatElement gives the names
     * (usage: Position, Normal, Vertex Color, UV, Padding; type: Float, Unsigned Byte, Byte, Unsigned Short).
     *
     * One thing about the text is worth stating because it was got wrong once and it cost two layouts their ids. The
     * light map element is {@code TEX_2S}, built as {@code new VertexFormatElement(1, EnumType.SHORT, EnumUsage.UV, 2)};
     * its usage is UV (there is no "Lightmap" usage in this class, so the first name is "UV") and its type is
     * <b>SHORT</b>, whose display name is "Short" - not USHORT, whose display name is "Unsigned Short". So it reads
     * "2,UV,Short". The two constants below said "2,UV,Unsigned Short" from the day the table was written, which meant
     * BLOCK and PARTICLE could never match: both arrivals were answered id 0 and refused, measured as
     * {@code id0=138/0} with the shape dumped as {@code 2,UV,Short} while no id3 or id5 appeared at all. The element
     * index that distinguishes TEX_2S from TEX_2F is not part of toString(), so BLOCK and PARTICLE are told apart by
     * the order of their elements rather than by that one.
     */
    public static final String LAYOUT_POSITION = "3,Position,Float";
    public static final String LAYOUT_POSITION_COLOR =
            "3,Position,Float 4,Vertex Color,Unsigned Byte";
    public static final String LAYOUT_POSITION_TEX = "3,Position,Float 2,UV,Float";
    public static final String LAYOUT_POSITION_NORMAL =
            "3,Position,Float 3,Normal,Byte 1,Padding,Byte";
    public static final String LAYOUT_POSITION_TEX_COLOR =
            "3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte";
    public static final String LAYOUT_POSITION_TEX_NORMAL =
            "3,Position,Float 2,UV,Float 3,Normal,Byte 1,Padding,Byte";
    public static final String LAYOUT_POSITION_TEX_COLOR_NORMAL =
            "3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte 3,Normal,Byte 1,Padding,Byte";
    public static final String LAYOUT_BLOCK =
            "3,Position,Float 4,Vertex Color,Unsigned Byte 2,UV,Float 2,UV,Short";
    public static final String LAYOUT_ITEM =
            "3,Position,Float 4,Vertex Color,Unsigned Byte 2,UV,Float 3,Normal,Byte 1,Padding,Byte";
    public static final String LAYOUT_PARTICLE =
            "3,Position,Float 2,UV,Float 4,Vertex Color,Unsigned Byte 2,UV,Short";

    public static int layout(String elements, int size, int count) {
        if (elements == null) {
            return 0;
        }
        if (elements.equals(LAYOUT_POSITION_COLOR) && size == 16 && count == 2) return POSITION_COLOR;
        if (elements.equals(LAYOUT_POSITION_TEX_COLOR) && size == 24 && count == 3) return POSITION_TEX_COLOR;
        if (elements.equals(LAYOUT_BLOCK) && size == 28 && count == 4) return BLOCK;
        if (elements.equals(LAYOUT_ITEM) && size == 28 && count == 5) return ITEM;
        if (elements.equals(LAYOUT_PARTICLE) && size == 28 && count == 4) return PARTICLE;
        if (elements.equals(LAYOUT_POSITION_TEX) && size == 20 && count == 2) return POSITION_TEX;
        if (elements.equals(LAYOUT_POSITION) && size == 12 && count == 1) return POSITION;
        // The order matters here and only here: this element list is a prefix of nothing, but the 24-byte form of it is
        // POSITION_TEX_NORMAL and OLDMODEL_POSITION_TEX_NORMAL. Comparing the colour-carrying 28-byte one first is what
        // makes the 24-byte one reachable; the two used to be one branch and the 24 was answered with the 28.
        if (elements.equals(LAYOUT_POSITION_TEX_COLOR_NORMAL) && size == 28 && count == 5) {
            return POSITION_TEX_COLOR_NORMAL;
        }
        if (elements.equals(LAYOUT_POSITION_TEX_NORMAL) && size == 24 && count == 4) return POSITION_TEX_NORMAL;
        // 16 bytes: 12 of position, 3 of normal and 1 of padding. No colour and no texture, so it is its own pipeline.
        if (elements.equals(LAYOUT_POSITION_NORMAL) && size == 16 && count == 3) return POSITION_NORMAL;
        return 0;
    }

    /**
     * {@return the name of the layout an id stands for}, so a refusal or a log line can say {@code POSITION_NORMAL} instead
     * of {@code id 10}.
     * <p>
     * The library has no {@code DefaultVertexFormats} to ask - this module does not depend on Minecraft - so the names are
     * this table's own. They are the names vanilla, the brief and the audit use.
     */
    public static String nameOf(int id) {
        switch (id) {
            case POSITION_COLOR: return "POSITION_COLOR";
            case POSITION_TEX_COLOR: return "POSITION_TEX_COLOR";
            case BLOCK: return "BLOCK";
            case ITEM: return "ITEM";
            case PARTICLE: return "PARTICLE_POSITION_TEX_COLOR_LMAP";
            case POSITION_TEX: return "POSITION_TEX";
            case POSITION: return "POSITION";
            case POSITION_TEX_COLOR_NORMAL: return "POSITION_TEX_COLOR_NORMAL";
            case POSITION_TEX_NORMAL: return "POSITION_TEX_NORMAL/OLDMODEL_POSITION_TEX_NORMAL";
            case POSITION_NORMAL: return "POSITION_NORMAL";
            default: return id == 0 ? "unmapped" : "unknown id " + id;
        }
    }

    /**
     * {@return what to print for a format with no id}, which is the format's own shape rather than a number.
     * <p>
     * The refusal path used to report "id 0", and a reader then had to re-derive which of the twelve standard formats (or
     * which mod format) that was from an element dump printed somewhere else. The element list and the width are the only
     * things that identify a layout, so they are what the name is made of.
     */
    public static String describeUnknown(String elements, int size, int count) {
        return "no native layout for a " + size + "-byte vertex with " + count + " element(s) [" + elements + "]";
    }

    private VulkanImmediateFormats() {
    }
}
