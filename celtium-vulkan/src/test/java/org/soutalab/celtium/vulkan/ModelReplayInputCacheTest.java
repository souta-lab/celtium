package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The replay input cache may only skip work, never change a draw: a hit must mean the inputs of the value are exactly
 * the ones that value was built from. These cases are the input list of each value - the nine rotation floats the lit
 * payload reads, the light generation, and the sixteen projection floats - and the counters that make the hit rate
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

    @Test void projectionIsReusedOnlyForTheSameSixteenFloats() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        float[] projection = new float[16];
        projection[0] = 1.5f;
        projection[5] = 2.5f;
        projection[10] = -1.001f;
        projection[11] = -1.0f;
        assertFalse(cache.reuseProjection(projection));

        cache.noteProjection(projection);
        assertTrue(cache.reuseProjection(projection));
        assertTrue(cache.reuseProjection(projection.clone()), "equal contents, a different array");

        for (int index = 0; index < 16; index++) {
            float[] changed = projection.clone();
            changed[index] += 0.25f;
            assertFalse(cache.reuseProjection(changed), "projection slot " + index + " is part of the remap");
        }
        assertFalse(cache.reuseProjection(null));
        assertFalse(cache.reuseProjection(new float[15]));
    }

    @Test void notingAProjectionCopiesIt() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        float[] projection = new float[16];
        projection[0] = 4;
        cache.noteProjection(projection);
        projection[0] = 5;
        assertFalse(cache.reuseProjection(projection));
        assertTrue(cache.describe().contains("projection[hit=0 miss=1]"), cache.describe());
    }

    @Test void anUnsetProjectionStaysUnset() {
        ModelReplayInputCache cache = new ModelReplayInputCache();
        cache.noteProjection(new float[16]);
        cache.noteProjection(null);
        assertFalse(cache.reuseProjection(new float[16]));
    }
}
