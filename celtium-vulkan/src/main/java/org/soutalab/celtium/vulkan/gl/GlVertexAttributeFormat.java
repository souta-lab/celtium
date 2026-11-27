package org.soutalab.celtium.vulkan.gl;

/**
 * One GL vertex attribute component type (S2-b relocation: celtium's own copy of the data type
 * the retired Celeritas vertex-format classes carried, kept because the mod-program pipeline
 * builder still describes pack vertex layouts with it). {@code typeId} is the GL enum value.
 */
public record GlVertexAttributeFormat(int typeId, int size) {

    public static final GlVertexAttributeFormat FLOAT = new GlVertexAttributeFormat(0x1406, 4);
    public static final GlVertexAttributeFormat SHORT = new GlVertexAttributeFormat(0x1402, 2);
    public static final GlVertexAttributeFormat UNSIGNED_SHORT = new GlVertexAttributeFormat(0x1403, 2);
    public static final GlVertexAttributeFormat BYTE = new GlVertexAttributeFormat(0x1400, 1);
    public static final GlVertexAttributeFormat UNSIGNED_BYTE = new GlVertexAttributeFormat(0x1401, 1);
    public static final GlVertexAttributeFormat UNSIGNED_INT = new GlVertexAttributeFormat(0x1405, 4);
}
