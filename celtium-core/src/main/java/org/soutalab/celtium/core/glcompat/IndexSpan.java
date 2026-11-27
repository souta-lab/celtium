package org.soutalab.celtium.core.glcompat;

import java.util.Objects;

/** A span of index data inside a HAL buffer, with the index element type. */
public record IndexSpan(long bufferHandle, long offsetBytes, int indexCount, IndexType indexType) {

    public IndexSpan {
        if (bufferHandle == 0L) {
            throw new IllegalArgumentException("bufferHandle must name a buffer");
        }
        if (offsetBytes < 0L) {
            throw new IllegalArgumentException("offsetBytes must not be negative");
        }
        if (indexCount < 0) {
            throw new IllegalArgumentException("indexCount must not be negative");
        }
        Objects.requireNonNull(indexType, "indexType");
    }

    public long byteLength() {
        return (long) indexCount * indexType.byteSize();
    }

    public enum IndexType {
        UINT16(2),
        UINT32(4);

        private final int byteSize;

        IndexType(int byteSize) {
            this.byteSize = byteSize;
        }

        public int byteSize() {
            return byteSize;
        }
    }
}
