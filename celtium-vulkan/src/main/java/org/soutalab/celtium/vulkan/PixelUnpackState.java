package org.soutalab.celtium.vulkan;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * The pixel-unpack record the texture capture store reads (S3-b E4-b2, door ruling family 3):
 * the bound {@code GL_PIXEL_UNPACK_BUFFER} name, the {@code GL_UNPACK_*} pixel-store values and
 * the CPU byte storage of pixel buffers whose bytes an upload reads back.
 *
 * <p>Until E4-b2 this record lived inside {@code GlsmBufferState} in {@code vulkan.gl}, and the
 * capture store - a HAL class that must not import the capture layer - reached across the
 * package boundary for it. The record now sits in this package beside the store. Its writers
 * are the capture routes that own the calls: the capture entry's dispatch and the hint doors
 * write the binding and the pixel-store values (the converter keeps its own pixel-store state
 * for Lane 3 through {@code GLSMTextures.glPixelStorei}; this record is the upload reader's
 * copy), and the same two routes file a pixel buffer's bytes here when they write them
 * ({@link #fileFallbackStorage}), which is the function boundary the door ruling §4.2 prescribes
 * for the PBO read: the store asks this record, never the capture layer. A storage supplier may
 * be registered to answer from another backing store instead; none is in the shipped path.
 */
public final class PixelUnpackState {

    private static final PixelUnpackState CURRENT = new PixelUnpackState();

    public static PixelUnpackState current() {
        return CURRENT;
    }

    private int pixelUnpackBuffer;
    private int unpackAlignment = 4;
    private int unpackRowLength;
    private int unpackSkipPixels;
    private int unpackSkipRows;
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

    /** Records one {@code glPixelStorei} value under its {@code GL_UNPACK_*} pname. */
    public void pixelStore(int pname, int value) {
        switch (pname) {
            case 0x0CF5: this.unpackAlignment = value == 1 || value == 2 || value == 8 ? value : 4; break;
            case 0x0CF2: this.unpackRowLength = Math.max(0, value); break;
            case 0x0CF4: this.unpackSkipPixels = Math.max(0, value); break;
            case 0x0CF3: this.unpackSkipRows = Math.max(0, value); break;
            default: break;
        }
    }

    /** {@return a recorded {@code GL_UNPACK_*} value}, or 0 for a pname this record does not hold. */
    public int pixelStoreValue(int pname) {
        switch (pname) {
            case 0x0CF5: return this.unpackAlignment;
            case 0x0CF2: return this.unpackRowLength;
            case 0x0CF4: return this.unpackSkipPixels;
            case 0x0CF3: return this.unpackSkipRows;
            default: return 0;
        }
    }

    public int unpackAlignment() { return this.unpackAlignment; }
    public int unpackRowLength() { return this.unpackRowLength; }
    public int unpackSkipPixels() { return this.unpackSkipPixels; }
    public int unpackSkipRows() { return this.unpackSkipRows; }

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
     * installed, otherwise the fallback store's, otherwise {@code null}.
     */
    public ByteBuffer storage(int buffer) {
        java.util.function.IntFunction<ByteBuffer> supplier = this.storageSupplier;
        if (supplier != null) {
            ByteBuffer supplied = supplier.apply(buffer);
            if (supplied != null) {
                return supplied;
            }
        }
        return this.fallbackStorage.get(Integer.valueOf(buffer));
    }
}
