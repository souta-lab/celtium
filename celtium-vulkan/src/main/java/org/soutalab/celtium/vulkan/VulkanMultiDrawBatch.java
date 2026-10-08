package org.soutalab.celtium.vulkan;

import org.embeddedt.embeddium.impl.gl.device.CommandList;
import org.embeddedt.embeddium.impl.gl.device.MultiDrawBatch;
import org.embeddedt.embeddium.impl.gl.tessellation.GlPrimitiveType;
import org.embeddedt.embeddium.impl.gl.tessellation.GlTessellation;

/**
 * Records the same draw commands {@link org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler}
 * produces for OpenGL, in a form the Vulkan backend can issue with {@code vkCmdDrawIndexed}.
 * <p>
 * The assembly, culling, merging and caching logic is shared with the OpenGL backend; only the storage and the
 * execution differ. The batch is installed through
 * {@link org.embeddedt.embeddium.impl.render.chunk.multidraw.BatchAssembler#setBatchFactory} while the Vulkan
 * backend is active, so the OpenGL renderer never sees it.
 * <p>
 * A command with {@code elementPointer == 0} uses the shared quad index buffer at element 0, which is how the
 * unsorted passes are drawn. Sorted passes supply a byte offset into the region's own index buffer.
 */
public final class VulkanMultiDrawBatch extends MultiDrawBatch {
    private final int[] baseVertices;
    private final int[] elementCounts;
    private final long[] elementPointers;

    public VulkanMultiDrawBatch(int capacity) {
        super(capacity);
        int slots = this.capacity();
        this.baseVertices = new int[slots];
        this.elementCounts = new int[slots];
        this.elementPointers = new long[slots];
    }

    @Override
    public void appendDrawCommand(int baseVertex, int elementCount, long elementPointer) {
        int index = this.size;

        this.baseVertices[index] = baseVertex;
        this.elementCounts[index] = elementCount;
        this.elementPointers[index] = elementPointer;

        // Element counts are never negative, so the sign bit of the negation is set iff the command is non-empty.
        this.size = index + ((-elementCount) >>> 31);
        this.maxElementCount = Math.max(this.maxElementCount, elementCount);
    }

    @Override
    public void mergeIntoLastCommand(int additionalElementCount) {
        int index = this.size - 1;
        int merged = this.elementCounts[index] + additionalElementCount;
        this.elementCounts[index] = merged;
        this.maxElementCount = Math.max(this.maxElementCount, merged);
    }

    /** Nothing to upload: the arrays stay in CPU memory and are read while recording the frame's commands. */
    @Override
    public void upload(CommandList commandList) {
        // no-op
    }

    /** Vulkan commands are issued by {@code VulkanTerrainGpuBackend}; there is no OpenGL tessellation to bind. */
    @Override
    public void execute(CommandList commandList, GlTessellation tessellation, GlPrimitiveType primitiveType) {
        throw new UnsupportedOperationException("Vulkan batches are executed by VulkanTerrainGpuBackend");
    }

    @Override
    public void delete() {
        // The arrays are owned by the Java heap and released with this object.
    }

    public int baseVertex(int command) {
        return this.baseVertices[command];
    }

    public int elementCount(int command) {
        return this.elementCounts[command];
    }

    /** Byte offset of the first index inside the region's index buffer; 0 means the shared quad index buffer. */
    public long indexByteOffset(int command) {
        return this.elementPointers[command];
    }
}
