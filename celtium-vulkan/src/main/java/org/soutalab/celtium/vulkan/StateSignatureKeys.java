package org.soutalab.celtium.vulkan;

/**
 * The state signature's layout and the pure key machinery built on it, moved out of
 * {@code VulkanTerrainGpuBackend} (Stage 6, slice 4a-7).
 * <p>
 * Everything here is a constant or a pure function of its arguments: the signature slot layout, the
 * state key hash, the format-class fold, the chain-pass key, and the two cache-hit comparisons with
 * the offer cache's slot function. The caches themselves - the captured signature and the per-slot
 * offer keys - are mutable instance state of the backend and stay there, together with the capture
 * and offer paths that read the live state to fill them; this class owns only what a test can pin
 * without a device.
 */
final class StateSignatureKeys {

    private StateSignatureKeys() {
    }

    /**
     * The sixteen integers a batch's pipeline is keyed by, plus the raw inputs a replay can compare instead.
     * <p>
     * Split out of {@link VulkanTerrainGpuBackend#celtium$captureState()} for the model replay path: the replay asks whether the state is the
     * one it resolved last time, and answering that from the raw inputs costs a comparison per input instead of the
     * whole capture. {@link VulkanTerrainGpuBackend#celtium$stateFromSignature} is the only place the mapping lives, so a captured state and a
     * signature can never disagree about what the state would be - which is what lets a hit reuse the array.
     */
    static final int SIGNATURE_BLEND = 0;
    static final int SIGNATURE_SRC_RGB = 1;
    static final int SIGNATURE_DST_RGB = 2;
    static final int SIGNATURE_SRC_ALPHA = 3;
    static final int SIGNATURE_DST_ALPHA = 4;
    static final int SIGNATURE_EQ_RGB = 5;
    static final int SIGNATURE_EQ_ALPHA = 6;
    static final int SIGNATURE_COLOR_MASK = 7;
    static final int SIGNATURE_ALPHA_TEST = 8;
    static final int SIGNATURE_ALPHA_REF = 9;
    static final int SIGNATURE_ALPHA_FUNC = 10;
    static final int SIGNATURE_CULL = 11;
    static final int SIGNATURE_DEPTH_TEST = 12;
    static final int SIGNATURE_DEPTH_WRITE = 13;
    static final int SIGNATURE_DEPTH_FUNC = 14;
    static final int SIGNATURE_LIGHTING = 15;
    static final int SIGNATURE_ENV_MODE = 16;
    static final int SIGNATURE_ENV_RGB = 17;
    static final int SIGNATURE_WINDOW_TARGET = 18;
    /**
     * The culled face and the front-face winding, appended so the state's cull slot can carry all three parts.
     * <p>
     * The enable alone was enough while every pipeline baked GL's default pair (back, counter-clockwise). A mod that
     * asks for {@code GL_FRONT} or {@code glFrontFace(GL_CW)} needs its own pipeline, and a value the state does not
     * read would be a comparison the replay guard pays for and a change it cannot see.
     */
    static final int SIGNATURE_CULL_FACE = 19;
    static final int SIGNATURE_FRONT_FACE = 20;

    /**
     * The bit of a group's state key that marks a line batch ({@code GL_LINES} / {@code GL_LINE_STRIP}).
     * <p>
     * The bit is set by the accept path for a line draw and read by the prepare path to choose the line-list pipeline.
     * It is therefore <em>not</em> part of {@link #celtium$stateKey}'s hash: see that method.
     */
    static final long LINE_KEY_BIT = 1L << 61;

    /**
     * The offer state-key cache's slot count, sized here because the slot function below is the
     * contract: the backend's cache arrays are allocated with this many slots, one per layout id.
     */
    static final int OFFER_KEY_CACHE_SLOTS = 16;

    /**
     * {@return the variant key of a chain-pair render pass}: the (colour attachment count, Vk colour format)
     * pair, packed so the two dimensions cannot collide. Pure and static so the key is pinned without a
     * device (QUEUE 404, S4).
     */
    static long celtium$chainPassKey(int colorCount, int vkFormat) {
        return ((long) colorCount << 32) | (vkFormat & 0xFFFF_FFFFL);
    }

    /**
     * {@return the pipeline cache key for a batch offered under {@code classOrdinal}}: the state key with the
     * format class folded in (QUEUE 404, S4). Pure and static so the fold is pinned without a device.
     * <p>
     * The default class (ordinal 0) is the identity - the key's value does not change, so every cache that
     * compares keys (the pipeline caches, {@code prepareCache}'s entry key) hits exactly as before (rule R6).
     * A non-default class folds below {@link #LINE_KEY_BIT}: the bit is saved, the fold runs with it clear,
     * and it is restored afterwards, so the fold can neither set the line flag on a triangle batch nor clear
     * it on a line batch - the measured failure the bit exists to prevent.
     */
    static long celtium$pipelineKey(long stateKey, int classOrdinal) {
        if (classOrdinal == 0) {
            return stateKey;
        }
        long flag = stateKey & LINE_KEY_BIT;
        long mixed = ((stateKey & ~LINE_KEY_BIT) * 31L + classOrdinal) & ~LINE_KEY_BIT;
        return mixed | flag;
    }

    /**
     * {@return the identity of one drawing state: a 64-bit hash of the format and the captured state array}.
     * <p>
     * The hash never sets {@link #LINE_KEY_BIT}. That bit is a flag the accept path adds ({@code stateKey |=
     * LINE_KEY_BIT}) and the prepare path reads to pick the line-list pipeline, so a hash that happened to set it turned
     * a triangle batch into three line segments - and half of all states do, because the bit is as likely as any other.
     * Measured on the creative inventory (leg r24d): the hovered slot's half-white highlight ({@code POSITION_COLOR},
     * colour {@code 0x80FFFFFF}, state key {@code 0x364d31f191fb43aa}, bit 61 set) was drawn with
     * {@code VK_PRIMITIVE_TOPOLOGY_LINE_LIST}, so the quad reached the image as three one-pixel lines - its top edge,
     * its bottom edge and its diagonal - and the slot looked unhighlighted. Poisoning the same quad's vertices to opaque
     * magenta made exactly those three lines visible, which is how the pipeline was identified.
     * <p>
     * Masking the bit costs one collision between two states that differ only in it, which a 64-bit hash already permits;
     * leaving it set costs a silently misdrawn batch whenever it happens.
     */
    static long celtium$stateKey(int formatId, int[] state) {
        long key = formatId;
        for (int value : state) {
            key = (key * 31) + value;
        }
        return key & ~LINE_KEY_BIT;
    }

    /**
     * Whether the cached signature was taken under exactly these three keys.
     * <p>
     * Extracted so the arithmetic can be exercised without a device: the capture itself needs the live state objects,
     * but "which keys make a hit" is a comparison, and a green test here is not evidence that a capture was skipped.
     * The initial cache is {@code (-1, -1, -1)}, which no live key can equal: both version counters start at zero and
     * only increase, and the window-target bit is 0 or 1.
     */
    static boolean celtium$signatureCacheHit(long stateVersion, long environmentVersion, int windowTarget,
            long cachedStateVersion, long cachedEnvironmentVersion, int cachedWindowTarget) {
        return stateVersion == cachedStateVersion
                && environmentVersion == cachedEnvironmentVersion
                && windowTarget == cachedWindowTarget;
    }

    /**
     * The offer state-key cache slot a layout id uses.
     * <p>
     * Sixteen slots, indexed by the low four bits of the id, and the id is stored in the slot and compared on every
     * lookup - so the index is a hint and the comparison is the identity. Every layout the table maps is below sixteen
     * ({@link VulkanImmediateFormats#COUNT}), so in practice no two ids share a slot; an id outside that range would
     * share one and simply miss more often, never answer wrongly.
     * <p>
     * Extracted so the property that matters can be asserted without a device: the two ids the merge census names as
     * the dominant alternation (2 and 6, 52% of the format splits) must not land in the same slot, or the change would
     * be a rename of the single entry rather than a fix for it.
     */
    static int celtium$offerKeyCacheSlot(int formatId) {
        return formatId & (OFFER_KEY_CACHE_SLOTS - 1);
    }

    /**
     * Whether the cached key was taken under exactly these four keys.
     * <p>
     * Extracted so the arithmetic can be exercised without a device, the same shape as
     * {@link #celtium$signatureCacheHit}: the capture needs the live state objects, but "which keys make a hit" is a
     * comparison, and a green test here is not evidence that a capture was skipped. The initial cache is invalid, so
     * no live key can match it - a live key can legitimately be {@code (0, 0, 0, 0)} on the first offer of a session.
     */
    static boolean celtium$stateKeyCacheHit(long stateVersion, long environmentVersion, int windowTarget,
            int formatId, long cachedStateVersion, long cachedEnvironmentVersion, int cachedWindowTarget,
            int cachedFormat, boolean cachedValid) {
        return cachedValid
                && stateVersion == cachedStateVersion
                && environmentVersion == cachedEnvironmentVersion
                && windowTarget == cachedWindowTarget
                && formatId == cachedFormat;
    }
}
