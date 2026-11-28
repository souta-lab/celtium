package org.soutalab.celtium.vulkan;

/**
 * The capture entrance's live records the texture capture store's producers read while filing an
 * upload: the extent record, the bound-name resolution, the level key (with its max-level side
 * record), the bound pixel unpack buffer and the bounded diagnostic counters. The store took the
 * entrance itself until E4-b1 (Neon door ruling, family 3): it now takes this narrow context,
 * which the entrance implements, so the store holds no reference to the entrance class.
 */
public interface TextureUploadContext {

    /** Records an uploaded level's extent under the name its target resolves to. */
    void recordExtent(int target, int level, int internal, int width, int height);

    /** {@return the texture name an upload to {@code target} files under}, 0 when unbound. */
    int uploadTargetName(int target);

    /** {@return the shared level key} for a name and level, noting the level in the max record. */
    long putTexLevelKey(int name, int level);

    /** {@return the bound pixel unpack buffer's name}, 0 when none is bound. */
    int pixelUnpackBuffer();

    /** Rebinds the pixel unpack buffer record (the PBO upload path clears and restores it). */
    void pixelUnpackBuffer(int buffer);

    /** {@return the size GL last gave a buffer}, falling back to the storage's capacity. */
    int logicalSizeOf(int bufferName, int capacity);

    /** Counts one bounded PBO upload log line; {@return false} once the bound is spent. */
    boolean countPboUploadLog();

    /** Counts one bounded pixel-buffer refusal line; {@return false} once the bound is spent. */
    boolean countPixelBufferRefusal();

    /**
     * Settles a name's filed records before a fresh allocation replaces them: the parameter
     * record, the extent records and the captures (the allocation producer's forget half).
     */
    void forgetTextureRecords(int name);

    /**
     * The full deletion settle for a name: bindings, backend notification and filed records
     * (the TextureUtil deletion route's half, which native deletion dispatch also runs).
     */
    void deleteTextureRecords(int name);
}
