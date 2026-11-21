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
    /** Bytes in one {@code VkDrawIndexedIndirectCommand}; the renderer's packet stride. */
    static final int INDIRECT_COMMAND_BYTES = 20;

    private final int[] baseVertices;
    private final int[] elementCounts;
    private final long[] elementPointers;

    /**
     * The retained indirect packets (item 350, VulkanMod-Next D25's draw-list retention in celtium's
     * shape): a batch is immutable once the assembler has filled it - a rebuild is a new object - so its
     * packet block is a pure function of its commands and is encoded once, then copied into each frame's
     * recording page instead of being re-encoded per offer. Two variants exist because the packet's
     * firstIndex field depends on which index buffer the pass reads: the shared quad buffer (firstIndex
     * always 0) or the region's own arena (firstIndex = elementPointer / 4). Native buffers, freed by
     * {@link #delete()} - the storage deletes a cached batch when it replaces it, so the retention's
     * lifetime is the batch's own.
     */
    private java.nio.ByteBuffer sharedPackets;
    private java.nio.ByteBuffer regionPackets;
    private boolean sharedPacketsEncoded;
    private boolean regionPacketsEncoded;
    private boolean unsortedOffsetsChecked;
    /** Whether every command reads the shared quad buffer at offset 0; computed once on demand. */
    private boolean unsortedOffsetsClean;

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
        // The arrays are owned by the Java heap and released with this object; the retained packets are
        // native memory and are freed here.
        if (this.sharedPackets != null) {
            org.lwjgl.system.MemoryUtil.memFree(this.sharedPackets);
            this.sharedPackets = null;
        }
        if (this.regionPackets != null) {
            org.lwjgl.system.MemoryUtil.memFree(this.regionPackets);
            this.regionPackets = null;
        }
        this.sharedPacketsEncoded = false;
        this.regionPacketsEncoded = false;
        this.unsortedOffsetsChecked = false;
    }

    /**
     * Lazily encodes only the requested packet variant (shared-index OR region-index). Unsorted passes only ever
     * read {@code sharedPackets} and sorted passes only ever read {@code regionPackets}, so encoding both eagerly
     * doubled native memory allocations and CPU encoding work for every cached terrain batch.
     */
    private void encodeSharedPackets() {
        int size = this.size();
        boolean clean = true;
        if (size > 0) {
            this.sharedPackets = org.lwjgl.system.MemoryUtil.memAlloc(size * INDIRECT_COMMAND_BYTES);
            long shared = org.lwjgl.system.MemoryUtil.memAddress(this.sharedPackets);
            for (int i = 0; i < size; i++) {
                if (this.elementPointers[i] != 0L) {
                    clean = false;
                }
                long s = shared + (long) i * INDIRECT_COMMAND_BYTES;
                org.lwjgl.system.MemoryUtil.memPutInt(s, this.elementCounts[i]);
                org.lwjgl.system.MemoryUtil.memPutInt(s + 4, 1);
                org.lwjgl.system.MemoryUtil.memPutInt(s + 8, 0);
                org.lwjgl.system.MemoryUtil.memPutInt(s + 12, this.baseVertices[i]);
                org.lwjgl.system.MemoryUtil.memPutInt(s + 16, 0);
            }
        }
        this.unsortedOffsetsClean = clean;
        this.unsortedOffsetsChecked = true;
        this.sharedPacketsEncoded = true;
    }

    private void encodeRegionPackets() {
        int size = this.size();
        boolean clean = true;
        if (size > 0) {
            this.regionPackets = org.lwjgl.system.MemoryUtil.memAlloc(size * INDIRECT_COMMAND_BYTES);
            long region = org.lwjgl.system.MemoryUtil.memAddress(this.regionPackets);
            for (int i = 0; i < size; i++) {
                long byteOffset = this.elementPointers[i];
                if (byteOffset != 0L) {
                    clean = false;
                }
                long r = region + (long) i * INDIRECT_COMMAND_BYTES;
                org.lwjgl.system.MemoryUtil.memPutInt(r, this.elementCounts[i]);
                org.lwjgl.system.MemoryUtil.memPutInt(r + 4, 1);
                org.lwjgl.system.MemoryUtil.memPutInt(r + 8, (int) (byteOffset / 4));
                org.lwjgl.system.MemoryUtil.memPutInt(r + 12, this.baseVertices[i]);
                org.lwjgl.system.MemoryUtil.memPutInt(r + 16, 0);
            }
        }
        this.unsortedOffsetsClean = clean;
        this.unsortedOffsetsChecked = true;
        this.regionPacketsEncoded = true;
    }

    /**
     * {@return the native address of this batch's retained packet block for the given index source,
     * encoding only that variant on first use}. Only meaningful for a non-empty batch; the renderer's offer path
     * returns before this point for an empty one.
     */
    long retainedPacketsAddress(boolean regionIndex) {
        if (regionIndex) {
            if (!this.regionPacketsEncoded) {
                encodeRegionPackets();
            }
            return this.regionPackets == null ? 0L : org.lwjgl.system.MemoryUtil.memAddress(this.regionPackets);
        }
        if (!this.sharedPacketsEncoded) {
            encodeSharedPackets();
        }
        return this.sharedPackets == null ? 0L : org.lwjgl.system.MemoryUtil.memAddress(this.sharedPackets);
    }

    /** {@return whether every command's element pointer is 0 (the unsorted indirect path's condition),
     * computed once instead of by a per-offer walk}. */
    boolean unsortedOffsetsClean() {
        if (!this.unsortedOffsetsChecked) {
            boolean clean = true;
            for (int i = 0, size = this.size(); i < size; i++) {
                if (this.elementPointers[i] != 0L) {
                    clean = false;
                    break;
                }
            }
            this.unsortedOffsetsClean = clean;
            this.unsortedOffsetsChecked = true;
        }
        return this.unsortedOffsetsClean;
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
