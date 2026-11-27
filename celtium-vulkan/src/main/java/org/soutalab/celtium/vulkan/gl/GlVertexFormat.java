package org.soutalab.celtium.vulkan.gl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An immutable GL vertex format: a stride plus its named attributes (S2-b relocation:
 * celtium's own copy of the data type, semantics unchanged).
 */
public class GlVertexFormat {

    public static final int NEXT_ALIGNED_POINTER = -1;

    private static final int ATTRIBUTE_ALIGNMENT = 4;

    private final Map<String, GlVertexAttribute> attributesKeyed;
    private final int stride;

    private GlVertexFormat(Map<String, GlVertexAttribute> attributesKeyed, int stride) {
        this.attributesKeyed = attributesKeyed;
        this.stride = stride;
    }

    public static Builder builder(int stride) {
        return new Builder(stride);
    }

    public GlVertexAttribute getAttribute(String name) {
        GlVertexAttribute attr = this.attributesKeyed.get(name);

        if (attr == null) {
            throw new NullPointerException("No attribute exists for " + name);
        }

        return attr;
    }

    public Collection<GlVertexAttribute> getAttributes() {
        return Collections.unmodifiableCollection(this.attributesKeyed.values());
    }

    public int getStride() {
        return this.stride;
    }

    @Override
    public String toString() {
        return String.format("GlVertexFormat{attributes=%d,stride=%d}",
                this.attributesKeyed.size(), this.stride);
    }

    public static class Builder {
        private final Map<String, GlVertexAttribute> attributes;
        private final int stride;

        public Builder(int stride) {
            this.attributes = new LinkedHashMap<>();
            this.stride = stride;
        }

        public Builder addAllElements(GlVertexFormat otherFormat) {
            return this.addElements(otherFormat.getAttributes());
        }

        public Builder addElements(Collection<GlVertexAttribute> attributes) {
            for (var attribute : attributes) {
                this.addElement(new GlVertexAttribute(attribute.getFormat(), attribute.getName(),
                        attribute.getCount(), attribute.isNormalized(), attribute.getPointer(),
                        this.stride, attribute.isIntType()));
            }
            return this;
        }

        private int findNextPointer() {
            int nextPtr = this.attributes.values().stream()
                    .mapToInt(a -> a.getPointer() + a.getSize()).max().orElse(0);
            return (nextPtr + (ATTRIBUTE_ALIGNMENT - 1)) & ~(ATTRIBUTE_ALIGNMENT - 1);
        }

        public Builder addElement(String name, int pointer, GlVertexAttributeFormat format,
                                  int count, boolean normalized, boolean intType) {
            if (pointer == NEXT_ALIGNED_POINTER) {
                pointer = this.findNextPointer();
            }
            return this.addElement(new GlVertexAttribute(format, name, count, normalized,
                    pointer, this.stride, intType));
        }

        private Builder addElement(GlVertexAttribute attribute) {
            if (attribute.getPointer() >= this.stride) {
                throw new IllegalArgumentException("Element " + attribute.getName()
                        + " starts outside vertex format (" + attribute.getPointer()
                        + ", stride is " + this.stride + ")");
            }

            if (attribute.getPointer() + attribute.getSize() > this.stride) {
                throw new IllegalArgumentException("Element extends outside vertex format");
            }

            if (this.attributes.put(attribute.getName(), attribute) != null) {
                throw new IllegalStateException("Generic attribute " + attribute.getName()
                        + " already defined in vertex format");
            }

            return this;
        }

        public GlVertexFormat build() {
            if (this.attributes.isEmpty()) {
                throw new IllegalArgumentException(
                        "Attempted to construct vertex format with no attributes");
            }

            ArrayList<GlVertexAttribute> allAttributes = new ArrayList<>(this.attributes.values());
            allAttributes.sort(Comparator.comparingInt(GlVertexAttribute::getPointer));

            GlVertexAttribute prevAttribute = null;
            for (var attribute : allAttributes) {
                if (prevAttribute != null && attribute.getPointer()
                        < (prevAttribute.getPointer() + prevAttribute.getSize())) {
                    throw new IllegalArgumentException("Attribute " + attribute.getName()
                            + " overlaps with " + prevAttribute.getName());
                }
                prevAttribute = attribute;
            }

            return new GlVertexFormat(new LinkedHashMap<>(this.attributes), this.stride);
        }
    }
}
