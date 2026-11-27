package org.soutalab.celtium.vulkan.gl;

/**
 * One named attribute inside a {@link GlVertexFormat} (S2-b relocation: celtium's own copy of
 * the data type, semantics unchanged - offsets and sizes are bytes inside the vertex stride).
 */
public class GlVertexAttribute {

    private final GlVertexAttributeFormat format;
    private final int count;
    private final int pointer;
    private final int size;
    private final int stride;
    private final boolean normalized;
    private final boolean intType;
    private final String name;

    public GlVertexAttribute(GlVertexAttributeFormat format, String name, int count,
                             boolean normalized, int pointer, int stride, boolean intType) {
        this(format, format.size() * count, count, name, normalized, pointer, stride, intType);
    }

    protected GlVertexAttribute(GlVertexAttributeFormat format, int size, int count, String name,
                                boolean normalized, int pointer, int stride, boolean intType) {
        this.format = format;
        this.size = size;
        this.count = count;
        this.normalized = normalized;
        this.pointer = pointer;
        this.stride = stride;
        this.intType = intType;
        this.name = name;
    }

    public GlVertexAttributeFormat getFormat() {
        return this.format;
    }

    public int getCount() {
        return this.count;
    }

    public int getPointer() {
        return this.pointer;
    }

    public int getSize() {
        return this.size;
    }

    public int getStride() {
        return this.stride;
    }

    public String getName() {
        return this.name;
    }

    public boolean isNormalized() {
        return this.normalized;
    }

    public boolean isIntType() {
        return this.intType;
    }
}
