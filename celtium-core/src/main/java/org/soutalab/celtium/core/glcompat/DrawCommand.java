package org.soutalab.celtium.core.glcompat;

/**
 * One draw's parameters, in the shape of the underlying indirect-draw arguments: whether it is
 * indexed, the counts, and the first-index / vertex-offset / instance offsets. Built through
 * {@link #draw} / {@link #drawIndexed} so the unused half of an unindexed (resp. indexed) command
 * is always zero rather than whatever the caller happened to leave in it.
 */
public record DrawCommand(
        boolean indexed,
        int vertexCount,
        int indexCount,
        int instanceCount,
        int firstVertex,
        int firstIndex,
        int vertexOffset,
        int firstInstance) {

    public DrawCommand {
        if (vertexCount < 0 || indexCount < 0 || instanceCount < 0
                || firstVertex < 0 || firstIndex < 0 || firstInstance < 0) {
            throw new IllegalArgumentException("draw counts and firsts must not be negative");
        }
        if (indexed && indexCount == 0) {
            throw new IllegalArgumentException("an indexed draw needs at least one index");
        }
        if (!indexed && vertexCount == 0) {
            throw new IllegalArgumentException("an unindexed draw needs at least one vertex");
        }
    }

    public static DrawCommand draw(int vertexCount, int firstVertex) {
        return new DrawCommand(false, vertexCount, 0, 1, firstVertex, 0, 0, 0);
    }

    public static DrawCommand drawInstanced(int vertexCount, int firstVertex, int instanceCount, int firstInstance) {
        return new DrawCommand(false, vertexCount, 0, instanceCount, firstVertex, 0, 0, firstInstance);
    }

    public static DrawCommand drawIndexed(int indexCount, int firstIndex, int vertexOffset) {
        return new DrawCommand(true, 0, indexCount, 1, 0, firstIndex, vertexOffset, 0);
    }

    public static DrawCommand drawIndexedInstanced(int indexCount, int firstIndex, int vertexOffset,
                                                   int instanceCount, int firstInstance) {
        return new DrawCommand(true, 0, indexCount, instanceCount, 0, firstIndex, vertexOffset, firstInstance);
    }
}
