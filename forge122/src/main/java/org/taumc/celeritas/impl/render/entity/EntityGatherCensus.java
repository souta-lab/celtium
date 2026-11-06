package org.taumc.celeritas.impl.render.entity;

/**
 * Counters for the per-chunk entity cache, and the audit that checks its one assumption.
 * <p>
 * {@code -Dceltium.vulkan.entityGatherCensus=true} prints one line every
 * {@code -Dceltium.vulkan.entityGatherCensusEvery} gathers (default 200, i.e. one per ten seconds at 20 TPS):
 * {@code gathers}, {@code chunks} visited, {@code entities} appended, {@code rebuilds} (chunks whose membership
 * changed since their last gather) and the audit totals. The rebuild count is the direct evidence that the cache is
 * doing its job: with 1 369 loaded chunks and a handful of entity mutations a tick, a working cache rebuilds a few
 * chunks per gather, not all of them.
 * <p>
 * {@code -Dceltium.vulkan.entityCacheAudit=true} re-counts every visited chunk's sixteen section maps and compares the
 * sum with the cached count. A mismatch means some code mutated a chunk's entity maps without going through
 * {@code Chunk.addEntity} / {@code removeEntity} / {@code removeEntityAtIndex} / {@code onUnload}, which is the one
 * assumption the cache makes - the same assumption {@link EntityMembershipEpoch} already makes for its tick cache. The
 * audit is off by default because it costs sixteen {@code size()} calls per chunk per gather.
 * <p>
 * Render thread only, so the counters are plain longs.
 */
public final class EntityGatherCensus {
    private static final boolean ENABLED = Boolean.getBoolean("celtium.vulkan.entityGatherCensus");
    private static final boolean AUDIT = Boolean.getBoolean("celtium.vulkan.entityCacheAudit");
    private static final int EVERY = Math.max(1,
            Integer.getInteger("celtium.vulkan.entityGatherCensusEvery", 200).intValue());

    /** How often the audit re-walks every loaded chunk to check the index; the walk it costs is amortised away. */
    private static final int AUDIT_INDEX_EVERY = 50;

    private static long gathers;
    private static long chunks;
    private static long entities;
    private static long rebuilds;
    private static long auditedChunks;
    private static long auditMismatches;
    private static long auditedIndexes;
    private static long indexMismatches;

    private EntityGatherCensus() {
    }

    /** {@return whether the audit is on}; asked once per gather, not per chunk. */
    public static boolean audit() {
        return AUDIT;
    }

    /** {@return whether this gather should re-walk every loaded chunk to check the index}. */
    public static boolean auditIndexDue() {
        return AUDIT && gathers % AUDIT_INDEX_EVERY == 0;
    }

    /** One index audit: {@code indexed} chunks in the index, {@code actual} chunks with entities in a full walk. */
    public static void auditedIndex(int indexed, int actual) {
        auditedIndexes++;
        if (indexed == actual) {
            return;
        }
        indexMismatches++;
        if (indexMismatches <= 8) {
            System.out.println("[Celtium] CELTIUM_ENTITY_INDEX_MISMATCH indexed=" + indexed + " actual=" + actual
                    + " (the entity-bearing chunk index disagrees with a full walk)");
        }
    }

    /** One chunk rebuilt its cached membership. */
    public static void rebuilt() {
        rebuilds++;
    }

    /** One gather finished: {@code chunkCount} chunks visited, {@code entityCount} entities in the membership. */
    public static void gather(int chunkCount, int entityCount) {
        if (!ENABLED && !AUDIT) {
            return;
        }
        gathers++;
        chunks += chunkCount;
        entities += entityCount;
        if (gathers % EVERY == 0) {
            print();
        }
    }

    /** One audited chunk: {@code actual} is the sum of its section sizes, {@code cached} what the cache returned. */
    public static void audited(int actual, int cached) {
        auditedChunks++;
        if (actual == cached) {
            return;
        }
        auditMismatches++;
        if (auditMismatches <= 8) {
            System.out.println("[Celtium] CELTIUM_ENTITY_CACHE_MISMATCH actual=" + actual + " cached=" + cached
                    + " (a chunk's entity maps changed outside the original Chunk methods)");
        }
    }

    private static void print() {
        System.out.println("[Celtium] CELTIUM_ENTITY_GATHER gathers=" + gathers + " chunks=" + chunks
                + " entities=" + entities + " rebuilds=" + rebuilds + " auditedChunks=" + auditedChunks
                + " auditMismatches=" + auditMismatches + " auditedIndexes=" + auditedIndexes
                + " indexMismatches=" + indexMismatches);
    }
}
