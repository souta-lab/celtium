package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay input cache may only skip work, never change a draw: a hit must mean the inputs of the value are exactly
 * the ones that value was built from. These cases are the input list of each value - the nine rotation floats the lit
 * payload reads, the light generation, and the projection stack's version - and the counters that make the hit rate
 * measurable on a leg.
 */
class ModelReplayInputCacheTest {

    private static float[] view(float a, float b, float c, float d) {
        // A column-major modelview: 0..2 the first basis vector, 4..6 the second, 8..10 the third, 12..14 the
        // translation. Only the first three columns are part of the light payload's input.
        return new float[] {a, 0, 0, 0, 0, b, 0, 0, 0, 0, c, 0, d, d + 1, d + 2, 1};
    }

    @Test void lightPayloadIsReusedOnlyForTheSameRotationAndGeneration() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        float[] first = view(1, 2, 3, 4);
        assertFalse(cache.reuseLightPayload(7L, first), "an empty cache cannot answer a hit");

        cache.noteLightPayload(7L, first);
        assertTrue(cache.reuseLightPayload(7L, first));

        // The translation is not an input: the payload is the inverse of the upper 3x3 only.
        float[] moved = view(1, 2, 3, 40);
        assertTrue(cache.reuseLightPayload(7L, moved));

        // Every one of the nine rotation slots is one: a change anywhere must miss.
        for (int index : new int[] {0, 1, 2, 4, 5, 6, 8, 9, 10}) {
            float[] changed = view(1, 2, 3, 4);
            changed[index] += 0.5f;
            assertFalse(cache.reuseLightPayload(7L, changed), "rotation slot " + index + " is part of the payload");
        }

        assertFalse(cache.reuseLightPayload(8L, first), "a light setter bumps the generation");
        assertFalse(cache.reuseLightPayload(7L, null));
        assertFalse(cache.reuseLightPayload(7L, new float[10]), "a short matrix has no rotation to compare");
    }

    @Test void notingALightPayloadCopiesTheRotationItKeysOn() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        float[] view = view(1, 2, 3, 4);
        cache.noteLightPayload(3L, view);
        // The caller reuses its scratch array every replay; a cache that kept the reference would "hit" on the new
        // contents without having built them.
        view[0] = 9;
        assertFalse(cache.reuseLightPayload(3L, view));
    }

    @Test void lightCacheCountsEveryQuestionAsExactlyOneHitOrMiss() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        float[] view = view(1, 2, 3, 4);
        assertFalse(cache.reuseLightPayload(1L, view));
        cache.noteLightPayload(1L, view);
        assertTrue(cache.reuseLightPayload(1L, view));
        assertFalse(cache.reuseLightPayload(2L, view));
        assertTrue(cache.describe().contains("light[hit=1 miss=2]"), cache.describe());
    }

    @Test void projectionIsReusedOnlyForTheSameVersion() {
        // The remap's key is the projection stack's version, not its contents: every mutation of the stack bumps it
        // (VulkanGlState.projectionVersion), so one compare proves the sixteen floats the remap was built from. Reading
        // all sixteen back was a second copy of the same answer.
        ModelReplayInputCache cache = new ModelReplayInputCache();
        assertFalse(cache.reuseProjection(11L), "an empty cache cannot answer a hit");

        cache.noteProjection(11L);
        assertTrue(cache.reuseProjection(11L));

        assertFalse(cache.reuseProjection(12L), "every mutation of the projection stack bumps the version");
        cache.noteProjection(12L);
        assertTrue(cache.reuseProjection(12L));
        assertFalse(cache.reuseProjection(11L), "a version that moved is not a hit even when the number comes back");
        assertTrue(cache.describe().contains("projection[hit=2 miss=3]"), cache.describe());
    }

    @Test void aProjectionIsOnlyReusedAfterTheVersionItWasRemappedFrom() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        cache.noteProjection(7L);
        // Two stacks that alternate versions (a pass that restores the previous projection) never share the remap:
        // Long.MIN_VALUE is the "never noted" marker, so it must not answer a hit either.
        assertFalse(cache.reuseProjection(Long.MIN_VALUE));
        assertTrue(cache.reuseProjection(7L));
    }

    @Test void theRotationHelpersNameTheSameNineSlotsAsTheCacheKey() {
        // The fast path proves a cached lit payload with these two helpers instead of comparing its twenty floats, so a
        // change to the cache key that the helpers did not follow would turn a real payload change into a false hit.
        float[] view = view(1, 2, 3, 4);
        float[] rotation = new float[9];
        ModelReplayInputCache.copyRotation(view, rotation);
        assertTrue(ModelReplayInputCache.sameRotation(view, rotation));
        assertTrue(ModelReplayInputCache.sameRotation(view(1, 2, 3, 90), rotation),
                "the translation is not an input of the payload");
        for (int index : new int[] {0, 1, 2, 4, 5, 6, 8, 9, 10}) {
            float[] changed = view(1, 2, 3, 4);
            changed[index] += 0.5f;
            assertFalse(ModelReplayInputCache.sameRotation(changed, rotation),
                    "rotation slot " + index + " is part of the payload");
        }
        assertFalse(ModelReplayInputCache.sameRotation(null, rotation));
        assertFalse(ModelReplayInputCache.sameRotation(new float[10], rotation));
        assertFalse(ModelReplayInputCache.sameRotation(view, null));
        // The copy is a copy: the caller rewrites its scratch every replay.
        float[] copied = new float[9];
        float[] scratch = view(1, 2, 3, 4);
        ModelReplayInputCache.copyRotation(scratch, copied);
        scratch[0] = 9;
        assertTrue(ModelReplayInputCache.sameRotation(view(1, 2, 3, 4), copied));
    }
}
