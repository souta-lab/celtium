package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The pixel-unpack record the texture capture store reads (S3-b E4-b2, door ruling family 3):
 * the bound {@code GL_PIXEL_UNPACK_BUFFER} name and the CPU byte storage of pixel buffers whose
 * bytes an upload reads back.
 *
 * <p>The {@code GL_UNPACK_*} pixel-store values themselves live in {@link GlsmBufferState} - the
 * record the dispatch and the hint doors have always written, and the one the {@code glGet*}
 * answers read - so this class delegates to it rather than keeping a second copy that could
 * drift. What this class owns is the PBO binding (which the buffer state never held; the old
 * stub kept it as a bare field) and the fallback byte filing: the capture routes file a pixel
 * buffer's bytes here when they write them ({@link #fileFallbackStorage}), which is the narrow
 * function boundary the door ruling §4.2 prescribes for the PBO read - the store asks this
 * record, never the capture layer. A storage supplier may be registered to answer from another
 * backing store instead; none is in the shipped path, and the last fallback is the buffer
 * state's own byte store, where the buffer-data routes file the same bytes.
 */
public final class PixelUnpackState {

    private static final PixelUnpackState CURRENT = new PixelUnpackState();

    public static PixelUnpackState current() {
        return CURRENT;
    }

    private int pixelUnpackBuffer;
    private final Map<Integer, ByteBuffer> fallbackStorage = new HashMap<>();
    private java.util.function.IntFunction<ByteBuffer> storageSupplier;

    /** {@return the bound pixel unpack buffer's name}, 0 when none is bound. */
    public int pixelUnpackBuffer() {
        return this.pixelUnpackBuffer;
    }

    /** Rebinds the pixel unpack buffer record. */
    public void pixelUnpackBuffer(int buffer) {
        this.pixelUnpackBuffer = buffer;
    }

    /** Records one {@code glPixelStorei} value; the record is {@link GlsmBufferState}'s. */
    public void pixelStore(int pname, int value) {
        GlsmBufferState.current().pixelStore(pname, value);
    }

    /** {@return a recorded pixel-store value} from {@link GlsmBufferState}'s record. */
    public int pixelStoreValue(int pname) {
        return GlsmBufferState.current().pixelStore(pname);
    }

    public int unpackAlignment() { return GlsmBufferState.current().unpackAlignment(); }
    public int unpackRowLength() { return GlsmBufferState.current().unpackRowLength(); }
    public int unpackSkipPixels() { return GlsmBufferState.current().unpackSkipPixels(); }
    public int unpackSkipRows() { return GlsmBufferState.current().unpackSkipRows(); }

    /**
     * Installs a storage supplier that answers a buffer's current CPU bytes from another
     * backing store, or {@code null} when that store holds none. The shipped path files bytes
     * directly (see the class doc); this hook is the override point for a runtime-backed reader.
     */
    public void storageSupplier(java.util.function.IntFunction<ByteBuffer> supplier) {
        this.storageSupplier = supplier;
    }

    /** Files bytes under a buffer name in the fallback store (see the class doc for when this is read). */
    public void fileFallbackStorage(int buffer, ByteBuffer bytes) {
        this.fallbackStorage.put(Integer.valueOf(buffer), bytes);
    }

    /** Drops a buffer's fallback bytes (the deletion settle). */
    public void forgetFallbackStorage(int buffer) {
        this.fallbackStorage.remove(Integer.valueOf(buffer));
    }

    /**
     * {@return the CPU bytes of a buffer}: the registered supplier's answer when one is
     * installed, otherwise the fallback store's, otherwise the buffer state's byte store,
     * otherwise {@code null}.
     */
    public ByteBuffer storage(int buffer) {
        java.util.function.IntFunction<ByteBuffer> supplier = this.storageSupplier;
        if (supplier != null) {
            ByteBuffer supplied = supplier.apply(buffer);
            if (supplied != null) {
                return supplied;
            }
        }
        ByteBuffer filed = this.fallbackStorage.get(Integer.valueOf(buffer));
        return filed != null ? filed : GlsmBufferState.current().storage(buffer);
    }
}
