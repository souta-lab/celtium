package org.soutalab.celtium.vulkan.gl;

import org.lwjgl.system.MemoryUtil;
import org.soutalab.celtium.vulkan.NativeGeometryBuffer;

import java.nio.ByteBuffer;

/**
 * The buffer-object storage record, extracted from {@code GlsmFrontend} unchanged in behaviour
 * (refactor/giant-split-2): what {@code glBufferData} allocates, the logical size each buffer was last told it
 * holds, and what {@code glMapBuffer}/{@code glMapBufferRange} answer live here, while the stub keeps the
 * binding resolution that names the buffer a call applies to and the static doors the transformer route and
 * the census read through.
 * <p>
 * The store map itself is not owned here: it is the stub's field, handed in at construction, because the
 * pixel-buffer, texture-upload and draw paths across the stub read a buffer's bytes directly from it. What
 * this record owns is the allocation policy over that store (reuse while large enough, so a persistently
 * mapped address stays stable), the per-name logical sizes, and the mapping counters.
 */
final class BufferStorageRecord {

    /** The stub's buffer store: name to backing bytes. Owned by the stub; operated on here. */
    private final it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store;

    /**
     * The size GL was last told a buffer holds, per buffer name.
     * <p>
     * The storage is reused when it is already large enough, so its capacity is not the buffer's size: after
     * {@code glBufferData(size=16)} then {@code glBufferData(size=4)} the capacity is still 16, and a range check against
     * it accepted a mapping of bytes 8..12 that the 4-byte buffer does not have (#387). Everything that asks "how big is
     * this buffer" reads this map; the capacity only decides when the allocation grows.
     */
    private final it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap logicalSize =
            new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();

    /** Calls and refusals of {@link #mapBufferFor}, so an invalid range is a counted event rather than a silent clamp. */
    private long mapBufferCalls;
    private long mapBufferRefusals;

    BufferStorageRecord(it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<ByteBuffer> store) {
        this.store = store;
    }

    /** The one buffer-data producer: the dispatch case and the transformer's route both land here. */
    void bufferDataFor(int name, long size, long data) {
        if (name == 0 || size < 0 || size > Integer.MAX_VALUE) {
            return;
        }
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            nativeBuffer.replaceStorage(data == 0L ? null : MemoryUtil.memByteBuffer(data, (int) size), (int) size);
            return;
        }
        if (size == 0) {
            // glBufferData(size=0) is a legal redefinition that leaves the buffer empty; the old code returned early and
            // kept the previous storage and size, so the buffer still mapped and still answered the old extent (#387).
            this.store.remove(name);
            this.logicalSize.put(name, 0);
            return;
        }
        this.logicalSize.put(name, (int) size);
        // The storage is reused whenever it is already large enough, rather than replaced. The game's staging buffers
        // are mapped persistently - the mirror this renderer reads its meshes from is one - and a new allocation moves
        // the address the caller is still writing to, which shows up as geometry built from whatever the old storage
        // happened to hold. Keeping the block makes the address a buffer name returns stable for the name's lifetime,
        // which is the property a persistent mapping needs; a larger request still has to move it.
        ByteBuffer storage = this.store.get(name);
        if (storage == null || storage.capacity() < size) {
            storage = ByteBuffer.allocateDirect((int) size).order(java.nio.ByteOrder.nativeOrder());
            this.store.put(name, storage);
        }
        if (data != 0L) {
            ByteBuffer source = MemoryUtil.memByteBuffer(data, (int) size);
            int position = storage.position();
            storage.clear();
            storage.put(source);
            storage.clear();
            storage.position(position);
        } else {
            storage.clear();
        }
    }

    /**
     * {@return the size GL last gave this buffer, falling back to the storage's capacity when nothing was recorded}.
     *
     * @param name     the buffer name
     * @param capacity the backing storage's size
     */
    int logicalSizeOf(int name, int capacity) {
        // containsKey, not get: a buffer redefined to zero is a recorded size, not an absent one.
        return this.logicalSize.containsKey(name) ? this.logicalSize.get(name) : capacity;
    }

    /**
     * {@return the byte offset a {@code glMapBufferRange} may map}, or -1 when the range is not within the buffer.
     * <p>
     * GL's contract: {@code offset} and {@code length} are non-negative, {@code length > 0}, and the range must lie
     * inside the buffer. Anything else is an error the caller has to see, not a range quietly moved to zero (audit #361:
     * offset -1 was answered as 0, and a length running past the end was not looked at).
     */
    static long mapRangeOffset(long offset, long length, long capacity) {
        if (offset < 0L || length <= 0L || capacity < 0L) {
            return -1L;
        }
        // Written this way round so the sum cannot overflow: length is bounded by the capacity instead.
        if (length > capacity || offset > capacity - length) {
            return -1L;
        }
        return offset;
    }

    /** {@return the map calls and how many were refused for an out-of-range request}, for the verification line. */
    String census() {
        return "calls=" + this.mapBufferCalls + " refusals=" + this.mapBufferRefusals;
    }

    /**
     * Drops this name's recorded logical size, the delete path's share of this record: the stub's
     * {@code forgetBuffer} removes the store entry and the bindings, which are its own fields.
     */
    void forget(int name) {
        this.logicalSize.remove(name);
    }

    /** {@code glUnmapBuffer} on a named buffer: the storage is the caller's memory already, so there is nothing to flush. */
    static boolean unmap(int name) {
        NativeGeometryBuffer mapped = NativeGeometryBuffer.find(name);
        return mapped == null || mapped.unmap();
    }

    long mapBufferFor(int name, boolean range, long rangeOffset, long rangeLength, long access) {
        NativeGeometryBuffer nativeBuffer = NativeGeometryBuffer.find(name);
        if (nativeBuffer != null) {
            this.mapBufferCalls++;
            // VertexBuffer API storage is transiently mappable, not persistent/coherent mapped storage.
            long address = range && (access & 0xC0L) != 0L ? 0L
                    : nativeBuffer.map(range ? rangeOffset : 0, range ? rangeLength : nativeBuffer.byteSize());
            if (address == 0L) this.mapBufferRefusals++;
            return address;
        }
        ByteBuffer storage = this.store.get(name);
        if (storage == null) {
            // Mapping a buffer with no allocation: GL would fault - an empty block keeps the caller alive while
            // making writes visibly land nowhere.
            storage = ByteBuffer.allocateDirect(0);
        }
        long base = MemoryUtil.memAddress(storage);
        if (range) {
            // glMapBufferRange(target, offset, length, access): the range is validated, not corrected. Returning base +
            // a clamped offset turned an invalid request into a valid-looking mapping (audit #361 measured offset = -1
            // answered as 0, and a length past the end simply ignored), which lets a caller write outside the buffer it
            // thinks it mapped. GL returns NULL for a range that is not within the buffer, and so does this.
            long offset = mapRangeOffset(rangeOffset, rangeLength, this.logicalSizeOf(name, storage.capacity()));
            this.mapBufferCalls++;
            if (offset < 0L) {
                this.mapBufferRefusals++;
                if (this.mapBufferRefusals <= 3) {
                    System.out.println("[Celtium] CELTIUM_MAP_RANGE_REFUSED buffer=" + name + " offset=" + rangeOffset
                            + " length=" + rangeLength + " capacity=" + storage.capacity());
                }
                return 0L;
            }
            return base + offset;
        }
        this.mapBufferCalls++;
        return base;
    }
}
