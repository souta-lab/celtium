package org.soutalab.celtium.vulkan.batch;

/**
 * Flat primitive-array storage for the frame's deferred draw items (new-architecture design
 * section 2.2). One item is the 32-byte {@code DrawItem} of the design - sort key, vertex offset,
 * first quad, quad count, instance-data index, texture slot - kept in parallel arrays so capturing
 * thousands of small draws allocates nothing per item. The store is reused across frames:
 * {@link #clear()} only resets the size.
 *
 * <p>Threading: render thread only, like the capture path it serves.
 */
public final class DrawItemStore {

    /** Flag bit in {@link #flags(int)}: the item must keep capture order (translucent / GUI). */
    public static final int FLAG_ORDERED = 1;

    private long[] sortKeys;
    private int[] vertexOffsets;
    private int[] firstQuads;
    private int[] quadCounts;
    private int[] instanceDataIndices;
    private int[] textureSlots;
    private int[] flags;
    private int size;

    public DrawItemStore() {
        this(1024);
    }

    public DrawItemStore(int initialCapacity) {
        int capacity = Math.max(16, initialCapacity);
        this.sortKeys = new long[capacity];
        this.vertexOffsets = new int[capacity];
        this.firstQuads = new int[capacity];
        this.quadCounts = new int[capacity];
        this.instanceDataIndices = new int[capacity];
        this.textureSlots = new int[capacity];
        this.flags = new int[capacity];
    }

    public int append(long sortKey, int vertexOffset, int firstQuad, int quadCount,
                      int instanceDataIndex, int textureSlot, int itemFlags) {
        if (this.size == this.sortKeys.length) {
            grow();
        }
        int index = this.size++;
        this.sortKeys[index] = sortKey;
        this.vertexOffsets[index] = vertexOffset;
        this.firstQuads[index] = firstQuad;
        this.quadCounts[index] = quadCount;
        this.instanceDataIndices[index] = instanceDataIndex;
        this.textureSlots[index] = textureSlot;
        this.flags[index] = itemFlags;
        return index;
    }

    private void grow() {
        int capacity = this.sortKeys.length * 2;
        this.sortKeys = java.util.Arrays.copyOf(this.sortKeys, capacity);
        this.vertexOffsets = java.util.Arrays.copyOf(this.vertexOffsets, capacity);
        this.firstQuads = java.util.Arrays.copyOf(this.firstQuads, capacity);
        this.quadCounts = java.util.Arrays.copyOf(this.quadCounts, capacity);
        this.instanceDataIndices = java.util.Arrays.copyOf(this.instanceDataIndices, capacity);
        this.textureSlots = java.util.Arrays.copyOf(this.textureSlots, capacity);
        this.flags = java.util.Arrays.copyOf(this.flags, capacity);
    }

    public int size() {
        return this.size;
    }

    public void clear() {
        this.size = 0;
    }

    public long sortKey(int index) {
        return this.sortKeys[index];
    }

    public int vertexOffset(int index) {
        return this.vertexOffsets[index];
    }

    public int firstQuad(int index) {
        return this.firstQuads[index];
    }

    public int quadCount(int index) {
        return this.quadCounts[index];
    }

    public int instanceDataIndex(int index) {
        return this.instanceDataIndices[index];
    }

    public int textureSlot(int index) {
        return this.textureSlots[index];
    }

    public int flags(int index) {
        return this.flags[index];
    }
}
