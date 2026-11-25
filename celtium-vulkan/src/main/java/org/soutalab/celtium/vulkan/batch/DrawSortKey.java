package org.soutalab.celtium.vulkan.batch;

/**
 * The 64-bit sort key every deferred immediate draw is filed under (new-architecture design
 * section 2.2, non-terrain submission model). The batcher sorts opaque draws by this key so that
 * draws sharing a pipeline land next to each other and can be issued as one indirect call, while
 * the sequence field keeps the original capture order as the final tiebreak.
 *
 * <p>Bit layout, most significant first so a plain signed {@code long} comparison orders by the
 * fields in priority order: pass (8), pipeline (16), state (8), layer (8), sequence (24). The
 * packed value is XORed with {@link Long#MIN_VALUE} so the unsigned field order is also the
 * signed order even when the pass field uses its top bit.
 */
public final class DrawSortKey {

    private DrawSortKey() {
    }

    /** Packs the five fields into one sortable key. Out-of-range field values are masked. */
    public static long encode(int passId, int pipelineKey, int stateKey, int layer, int sequence) {
        long packed = ((long) (passId & 0xFF) << 56)
                | ((long) (pipelineKey & 0xFFFF) << 40)
                | ((long) (stateKey & 0xFF) << 32)
                | ((long) (layer & 0xFF) << 24)
                | (sequence & 0xFFFFFFL);
        return packed ^ Long.MIN_VALUE;
    }

    public static int passId(long key) {
        return (int) ((key ^ Long.MIN_VALUE) >>> 56) & 0xFF;
    }

    public static int pipelineKey(long key) {
        return (int) ((key ^ Long.MIN_VALUE) >>> 40) & 0xFFFF;
    }

    public static int stateKey(long key) {
        return (int) ((key ^ Long.MIN_VALUE) >>> 32) & 0xFF;
    }

    public static int layer(long key) {
        return (int) ((key ^ Long.MIN_VALUE) >>> 24) & 0xFF;
    }

    public static int sequence(long key) {
        return (int) (key ^ Long.MIN_VALUE) & 0xFFFFFF;
    }
}
