package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The descriptor-set cache's key contract, checked input by input, in the shape of
 * {@link PrepareCacheKeyContractTest}.
 * <p>
 * A mod program's descriptor set is cached under a 64-bit hash of the handles it is written from. Two rules
 * keep that cache honest, and each is pinned here: the hash covers <em>exactly</em> the entries the set is
 * written from (all {@code samplerCount} of them - hashing only the caller's head left the tail bindings,
 * which are whatever the caller's scratch arrays still held, outside the key), and a hit is verified
 * against a snapshot of those inputs, because a bare hash has no equality check behind it and a collision
 * would bind another draw's textures for the rest of the pipeline's life.
 */
class DescriptorSetKeyContractTest {

    @Test
    @DisplayName("the sampler-set hash covers the tail entries the set is written from")
    void hashCoversTheWrittenRange() {
        long[] views = {10L, 20L, 30L};
        long[] samplers = {1L, 2L, 3L};
        int[] layouts = {7, 7, 7};
        long[] generations = {5L, 5L, 5L};
        long base = VulkanModProgramPipeline.samplerSetHash(views, samplers, layouts, generations, 3);
        assertEquals(base, VulkanModProgramPipeline.samplerSetHash(views, samplers, layouts, generations, 3),
                "the same inputs hash to the same key");
        assertNotEquals(base, VulkanModProgramPipeline.samplerSetHash(
                new long[] {10L, 20L, 31L}, samplers, layouts, generations, 3),
                "a difference in the last view - past any head a caller might name - changes the key");
        assertNotEquals(base, VulkanModProgramPipeline.samplerSetHash(
                views, samplers, layouts, new long[] {5L, 5L, 6L}, 3),
                "a difference in the last generation changes the key");
        assertNotEquals(base, VulkanModProgramPipeline.samplerSetHash(
                views, samplers, new int[] {7, 7, 9}, generations, 3),
                "a difference in the last layout changes the key");
    }

    @Test
    @DisplayName("a cached set is verified against every input it was written from")
    void setInputsAreVerifiedInputByInput() {
        long[] views = {10L, 20L};
        long[] samplers = {1L, 2L};
        int[] layouts = {7, 8};
        long[] imageViews = {30L};
        long[] snapshot = VulkanModProgramPipeline.celtium$packSetInputs(views, samplers, layouts, imageViews, 2, 1, 4);
        assertTrue(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot, views, samplers, layouts, imageViews, 2, 1, 4),
                "the inputs the set was written from match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot,
                new long[] {11L, 20L}, samplers, layouts, imageViews, 2, 1, 4), "a different view must not match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot,
                views, new long[] {1L, 3L}, layouts, imageViews, 2, 1, 4), "a different sampler must not match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot,
                views, samplers, new int[] {7, 9}, imageViews, 2, 1, 4), "a different layout must not match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot,
                views, samplers, layouts, new long[] {31L}, 2, 1, 4), "a different image view must not match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(snapshot,
                views, samplers, layouts, imageViews, 2, 1, 5), "a different uniform slot must not match");
        assertFalse(VulkanModProgramPipeline.celtium$setInputsMatch(null,
                views, samplers, layouts, imageViews, 2, 1, 4), "no snapshot never matches");
    }
}
