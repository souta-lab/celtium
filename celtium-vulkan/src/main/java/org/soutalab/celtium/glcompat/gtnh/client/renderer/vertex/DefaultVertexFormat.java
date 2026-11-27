package org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex;
/*
 * Minimal subset carried into celtium from GTNHLib (LGPL-3.0),
 * GTNewHorizons/GTNHLib, as vendored by Actinium-Dev/Actinium @ ce77eed9.
 * Only the types the converted GLSM front references are carried; the library
 * as a whole is NOT imported. See THIRD_PARTY_NOTICES.md.
 */


import static org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFlags.*;

import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormatElement.Type;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.VertexFormatElement.Usage;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers.ColorVertexAttributeWriter;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers.LightVertexAttributeWriter;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers.NormalVertexAttributeWriter;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers.PositionVertexAttributeWriter;
import org.soutalab.celtium.glcompat.gtnh.client.renderer.vertex.writers.TextureVertexAttributeWriter;

public final class DefaultVertexFormat {

    private DefaultVertexFormat() {
        // non-instantiable class
    }

    public static final VertexFormat[] ALL_FORMATS = new VertexFormat[BITSET_SIZE];

    public static final VertexFormatElement POSITION_ELEMENT = new VertexFormatElement(
            0,
            Type.FLOAT,
            Usage.POSITION,
            3,
            POSITION_BIT,
            new PositionVertexAttributeWriter());
    public static final VertexFormatElement COLOR_ELEMENT = new VertexFormatElement(
            0,
            Type.UBYTE,
            Usage.COLOR,
            4,
            COLOR_BIT,
            new ColorVertexAttributeWriter());
    public static final VertexFormatElement TEXTURE_ELEMENT = new VertexFormatElement(
            0,
            Type.FLOAT,
            Usage.PRIMARY_UV,
            2,
            TEXTURE_BIT,
            new TextureVertexAttributeWriter());
    public static final VertexFormatElement LIGHT_ELEMENT = new VertexFormatElement(
            1,
            Type.SHORT,
            Usage.SECONDARY_UV,
            2,
            BRIGHTNESS_BIT,
            new LightVertexAttributeWriter());
    public static final VertexFormatElement NORMAL_ELEMENT = new VertexFormatElement(
            0,
            Type.BYTE,
            Usage.NORMAL,
            3,
            NORMAL_BIT,
            new NormalVertexAttributeWriter(),
            1);

    public static final VertexFormat POSITION = new VertexFormat(POSITION_ELEMENT);

    // 2
    public static final VertexFormat POSITION_TEXTURE = new VertexFormat(POSITION_ELEMENT, TEXTURE_ELEMENT);
    public static final VertexFormat POSITION_COLOR = new VertexFormat(POSITION_ELEMENT, COLOR_ELEMENT);
    public static final VertexFormat POSITION_NORMAL = new VertexFormat(POSITION_ELEMENT, NORMAL_ELEMENT);
    public static final VertexFormat POSITION_LIGHT = new VertexFormat(POSITION_ELEMENT, LIGHT_ELEMENT);

    // 3
    public static final VertexFormat POSITION_TEXTURE_NORMAL = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            NORMAL_ELEMENT);
    public static final VertexFormat POSITION_TEXTURE_COLOR = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            COLOR_ELEMENT);
    public static final VertexFormat POSITION_TEXTURE_LIGHT = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            LIGHT_ELEMENT);
    public static final VertexFormat POSITION_COLOR_LIGHT = new VertexFormat(
            POSITION_ELEMENT,
            COLOR_ELEMENT,
            LIGHT_ELEMENT);
    public static final VertexFormat POSITION_NORMAL_COLOR = new VertexFormat(
            POSITION_ELEMENT,
            NORMAL_ELEMENT,
            COLOR_ELEMENT);
    public static final VertexFormat POSITION_NORMAL_LIGHT = new VertexFormat(
            POSITION_ELEMENT,
            NORMAL_ELEMENT,
            LIGHT_ELEMENT);

    // 4
    public static final VertexFormat POSITION_TEXTURE_LIGHT_NORMAL = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            LIGHT_ELEMENT,
            NORMAL_ELEMENT);
    public static final VertexFormat POSITION_TEXTURE_COLOR_LIGHT = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            COLOR_ELEMENT,
            LIGHT_ELEMENT);
    public static final VertexFormat POSITION_TEXTURE_COLOR_NORMAL = new VertexFormat(
            POSITION_ELEMENT,
            TEXTURE_ELEMENT,
            COLOR_ELEMENT,
            NORMAL_ELEMENT);
    public static final VertexFormat POSITION_COLOR_LIGHT_NORMAL = new VertexFormat(
            POSITION_ELEMENT,
            COLOR_ELEMENT,
            LIGHT_ELEMENT,
            NORMAL_ELEMENT);

    // All
    public static final VertexFormat POSITION_COLOR_TEXTURE_LIGHT_NORMAL = new VertexFormat(
            POSITION_ELEMENT,
            COLOR_ELEMENT,
            TEXTURE_ELEMENT,
            LIGHT_ELEMENT,
            NORMAL_ELEMENT);

    // Duplicate of POSITION_TEXTURE_COLOR
    @Deprecated
    public static final VertexFormat POSITION_COLOR_TEXTURE = POSITION_TEXTURE_COLOR;

}
