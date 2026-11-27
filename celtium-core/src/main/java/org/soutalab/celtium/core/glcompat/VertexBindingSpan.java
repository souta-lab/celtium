package org.soutalab.celtium.core.glcompat;

/**
 * A span of vertex data inside a HAL buffer: the buffer's handle, the byte offset the span starts
 * at, the vertex stride, and how many vertices the span holds. Spans are how the converter hands
 * the sink vertex data without copying it - the sink binds the buffer at the offset.
 */
public record VertexBindingSpan(long bufferHandle, long offsetBytes, int strideBytes, int vertexCount) {

    public VertexBindingSpan {
        if (bufferHandle == 0L) {
            throw new IllegalArgumentException("bufferHandle must name a buffer");
        }
        if (offsetBytes < 0L) {
            throw new IllegalArgumentException("offsetBytes must not be negative");
        }
        if (strideBytes <= 0) {
            throw new IllegalArgumentException("strideBytes must be positive");
        }
        if (vertexCount < 0) {
            throw new IllegalArgumentException("vertexCount must not be negative");
        }
    }

    /** The byte length of the span. */
    public long byteLength() {
        return (long) strideBytes * vertexCount;
    }
}
