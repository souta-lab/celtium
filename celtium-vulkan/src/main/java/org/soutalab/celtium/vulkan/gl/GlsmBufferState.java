package org.soutalab.celtium.vulkan.gl;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * GLSM stage 3, GAP-ST-2 (census S-3, plus S-5 pixel store and the S-24 readable-range
 * record as its in-count sub-parts, and the Q-7 map answers that read this storage):
 * the owner of buffer bindings and buffer storage.
 * <p>
 * Bindings keep the stub's flat shape (target constants are dense, well under 0x10000;
 * index 0 = unbound name 0). Storage is the same name -&gt; ByteBuffer store the stub's
 * BufferStorageRecord operates on; the record class keeps working on the map this owner
 * hands out, so the move changes ownership, not the storage mechanics. Pixel-store state
 * (S-5) and the readable-range record (S-24, the decode basis ReadableMemoryRanges reads)
 * move with it because every consumer of both is a buffer/pixel transfer.
 */
public final class GlsmBufferState {
    private static final int FLAT_MAP_SIZE = 0x10000;
    private static final GlsmBufferState CURRENT = new GlsmBufferState();

    public static GlsmBufferState current() { return CURRENT; }

    private final int[] bindings = new int[FLAT_MAP_SIZE];
    /**
     * Indexed bindings from {@code glBindBufferBase}/{@code glBindBufferRange} (GLSM M-4), keyed by
     * (target, index) packed into one long. Kept apart from the flat {@link #bindings}: folding an
     * indexed binding into the flat map would drop the index, which is the whole content of the call.
     * A base binding is recorded with offset 0, size -1 (the whole buffer) and ranged=false.
     */
    private final Map<Long, IndexedBinding> indexedBindings = new HashMap<Long, IndexedBinding>();

    /** One indexed binding record: the buffer name plus the range a Range call named. */
    public static final class IndexedBinding {
        public final int buffer;
        public final long offset;
        /** The range length, or -1 when the binding covers the whole buffer (a Base binding). */
        public final long size;
        public final boolean ranged;

        IndexedBinding(int buffer, long offset, long size, boolean ranged) {
            this.buffer = buffer; this.offset = offset; this.size = size; this.ranged = ranged;
        }
    }

    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store =
            new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer>();
    /** Readable ranges per buffer name: {address, length} pairs, S-24. */
    private final Map<Integer, long[]> readableRanges = new HashMap<Integer, long[]>();

    // Pixel store (S-5), at GL's initial values as the stub records them.
    private int unpackAlignment = 4;
    private int unpackRowLength;
    private int unpackSkipPixels, unpackSkipRows;
    private int packAlignment = 4;
    private int packRowLength;
    private int packSkipPixels, packSkipRows;

    private long version;

    private GlsmBufferState() {
    }

    public void bind(int target, int name) {
        if (target < 0 || target >= FLAT_MAP_SIZE) return;
        if (this.bindings[target] != name) {
            this.bindings[target] = name;
            this.version++;
        }
    }

    public int bound(int target) {
        return target >= 0 && target < FLAT_MAP_SIZE ? this.bindings[target] : 0;
    }

    private static long indexedKey(int target, int index) {
        return ((long) target << 32) | (index & 0xFFFFFFFFL);
    }

    /** {@code glBindBufferBase}: binds the whole buffer at (target, index). Name 0 unbinds the slot. */
    public void bindBufferBase(int target, int index, int buffer) {
        bindIndexed(target, index, buffer, 0L, -1L, false);
    }

    /** {@code glBindBufferRange}: binds [offset, offset+size) of the buffer at (target, index). */
    public void bindBufferRange(int target, int index, int buffer, long offset, long size) {
        bindIndexed(target, index, buffer, offset, size, true);
    }

    private void bindIndexed(int target, int index, int buffer, long offset, long size, boolean ranged) {
        if (target < 0 || index < 0) return;
        long key = indexedKey(target, index);
        if (buffer == 0) {
            if (this.indexedBindings.remove(key) != null) this.version++;
            return;
        }
        IndexedBinding next = new IndexedBinding(buffer, offset, size, ranged);
        IndexedBinding previous = this.indexedBindings.put(key, next);
        if (previous == null || previous.buffer != buffer || previous.offset != offset
                || previous.size != size || previous.ranged != ranged) this.version++;
    }

    /** @return the indexed binding at (target, index), or null when the slot is unbound. */
    public IndexedBinding indexedBinding(int target, int index) {
        if (target < 0 || index < 0) return null;
        return this.indexedBindings.get(indexedKey(target, index));
    }

    /** The live storage map BufferStorageRecord operates on (the stub's fastutil shape). */
    public it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store() { return this.store; }

    public void putStorage(int name, ByteBuffer buffer) { this.store.put(name, buffer); this.version++; }

    public ByteBuffer storage(int name) { return this.store.get(name); }

    public ByteBuffer removeStorage(int name) {
        ByteBuffer removed = this.store.remove(name);
        this.readableRanges.remove(name);
        if (removed != null) this.version++;
        return removed;
    }

    public void readableRange(int name, long address, long length) {
        this.readableRanges.put(name, new long[] {address, length});
    }

    /** @return {address, length} for the name, or null when no range was recorded. */
    public long[] readableRange(int name) { return this.readableRanges.get(name); }

    /**
     * Records a pixel-store value with the stub record's exact semantics: unpack alignment is
     * sanitised to a legal GL value (1, 2 or 8, else 4), lengths and skips clamp at zero, and
     * GL_UNPACK_ROW_LENGTH is 0x0CF2 (the landed owner keyed it at 0x0CF7, which is
     * GL_UNPACK_IMAGE_HEIGHT - corrected here at the rewiring, before any route read it).
     * PACK_* pnames keep their own fields, as landed.
     */
    public void pixelStore(int pname, int value) {
        switch (pname) {
            case 0x0CF5: this.unpackAlignment = value == 1 || value == 2 || value == 8 ? value : 4; break;
            case 0x0CF2: this.unpackRowLength = Math.max(0, value); break;
            case 0x0CF4: this.unpackSkipPixels = Math.max(0, value); break;
            case 0x0CF3: this.unpackSkipRows = Math.max(0, value); break;
            case 0x0D05: this.packAlignment = value; break;     // GL_PACK_ALIGNMENT
            case 0x0D02: this.packRowLength = value; break;     // GL_PACK_ROW_LENGTH
            case 0x0D04: this.packSkipPixels = value; break;    // GL_PACK_SKIP_PIXELS
            case 0x0D03: this.packSkipRows = value; break;      // GL_PACK_SKIP_ROWS
            default: break;
        }
        this.version++;
    }

    public int unpackAlignment() { return this.unpackAlignment; }
    public int unpackRowLength() { return this.unpackRowLength; }
    public int unpackSkipPixels() { return this.unpackSkipPixels; }
    public int unpackSkipRows() { return this.unpackSkipRows; }

    /** Clears every target binding that names {@code name} (the stub forgetBuffer sweep). */
    public void unbindName(int name) {
        for (int target = 0; target < FLAT_MAP_SIZE; target++) {
            if (this.bindings[target] == name) {
                this.bindings[target] = 0;
                this.version++;
            }
        }
        // The indexed ledger names the same buffers: a deleted name must fall out of it too, or a
        // later query would answer a binding to an object that no longer exists (GLSM M-4).
        java.util.Iterator<Map.Entry<Long, IndexedBinding>> sweep = this.indexedBindings.entrySet().iterator();
        while (sweep.hasNext()) {
            if (sweep.next().getValue().buffer == name) {
                sweep.remove();
                this.version++;
            }
        }
    }

    public int pixelStore(int pname) {
        switch (pname) {
            case 0x0CF5: return this.unpackAlignment;
            case 0x0CF2: return this.unpackRowLength;
            case 0x0CF4: return this.unpackSkipPixels;
            case 0x0CF3: return this.unpackSkipRows;
            case 0x0D05: return this.packAlignment;
            case 0x0D02: return this.packRowLength;
            case 0x0D04: return this.packSkipPixels;
            case 0x0D03: return this.packSkipRows;
            default: return 0;
        }
    }

    public long version() { return this.version; }
}
