package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The condition under which the accept path may reuse the texture wrapper it already resolved.
 * <p>
 * The accept path resolves the bound name once, before its merge decision, because the predicate's identity term
 * compares the live wrapper with the one the open group captured. It then runs one of two supplies, and only
 * {@code celtium$supplyMirror} can create a wrapper ({@code imageFor}); {@code celtium$refreshRawWrites} uploads into
 * the wrapper it is given. So the wrapper stays current exactly when the mirror was already complete, and that is the
 * condition the preflight and the group open reuse it under - two registry lookups per slow-path offer that the same
 * offer had already answered.
 * <p>
 * The registry serves ~636 lookups a frame on the shipped profile (leg {@code r40a-census}:
 * {@code memo[hits=4240645 misses=290341 slots=256]} over 7,118 frames) for ~237 offers, and a miss is the whole
 * capture path, so the redundant asks are not free even when the memo answers them.
 * <p>
 * What these tests can prove: the truth table the reuse rests on. They prove nothing about a frame - a green test here
 * is not evidence that any lookup was saved, and a wrong answer in the other direction would hand a group a retired
 * wrapper, which is why the rule is a named function rather than an inline expression.
 */
class SupplyWrapperReuseTest {

    @Test
    void aCompleteMirrorCannotBeReplacedByTheSupply() {
        // The refresh branch runs, and it uploads into the wrapper it was handed.
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyMayReplaceWrapper(42, true));
    }

    @Test
    void aMissingMirrorCanBeCreated() {
        // No wrapper at all: celtium$supplyMirror may call imageFor, which replaces the name's wrapper.
        assertTrue(VulkanTerrainGpuBackend.celtium$supplyMayReplaceWrapper(42, false));
    }

    @Test
    void aPartialMirrorCanBeCompleted() {
        // A mirror that only ever received a patch has no full content, so the supply branch runs and may create.
        assertTrue(VulkanTerrainGpuBackend.celtium$supplyMayReplaceWrapper(7, false));
    }

    @Test
    void aTexturelessBatchHasNoWrapperToReplace() {
        // Name 0 is "no texture": the accept path resolved null and neither supply branch runs, so the answer is the
        // one it already holds whatever the completeness flag says.
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyMayReplaceWrapper(0, false));
        assertFalse(VulkanTerrainGpuBackend.celtium$supplyMayReplaceWrapper(0, true));
    }
}
