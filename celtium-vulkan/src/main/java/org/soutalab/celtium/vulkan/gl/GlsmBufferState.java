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
    private final Map<Integer, ByteBuffer> store = new HashMap<Integer, ByteBuffer>();
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

    /** The live storage map BufferStorageRecord operates on. */
    public Map<Integer, ByteBuffer> store() { return this.store; }

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

    public void pixelStore(int pname, int value) {
        switch (pname) {
            case 0x0CF5: this.unpackAlignment = value; break;   // GL_UNPACK_ALIGNMENT
            case 0x0CF7: this.unpackRowLength = value; break;   // GL_UNPACK_ROW_LENGTH
            case 0x0CF4: this.unpackSkipPixels = value; break;  // GL_UNPACK_SKIP_PIXELS
            case 0x0CF3: this.unpackSkipRows = value; break;    // GL_UNPACK_SKIP_ROWS
            case 0x0D05: this.packAlignment = value; break;     // GL_PACK_ALIGNMENT
            case 0x0D02: this.packRowLength = value; break;     // GL_PACK_ROW_LENGTH
            case 0x0D04: this.packSkipPixels = value; break;    // GL_PACK_SKIP_PIXELS
            case 0x0D03: this.packSkipRows = value; break;      // GL_PACK_SKIP_ROWS
            default: break;
        }
        this.version++;
    }

    public int pixelStore(int pname) {
        switch (pname) {
            case 0x0CF5: return this.unpackAlignment;
            case 0x0CF7: return this.unpackRowLength;
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
