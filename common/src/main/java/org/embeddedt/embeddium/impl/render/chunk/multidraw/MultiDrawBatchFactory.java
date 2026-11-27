package org.embeddedt.embeddium.impl.render.chunk.multidraw;

@FunctionalInterface
public interface MultiDrawBatchFactory {
    MultiDrawBatch create(int capacity);
}
