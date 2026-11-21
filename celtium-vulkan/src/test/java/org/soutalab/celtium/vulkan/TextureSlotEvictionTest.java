package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slot-clearing placement in the textured pipeline's eviction, pinned as a source contract because the
 * failure needs a device-bound allocation sequence to reproduce at runtime.
 * <p>
 * The descriptor cache's eviction retires the eldest entry's set into the completion-gated free (audit D2).
 * The single-slot cache in front of the map is consulted before the map, so if the slot's own key ages into
 * the eldest entry and is evicted, a slot left pointing at the retired set hands back a set that has been
 * freed - and, once the pool reallocates it, a set belonging to another texture. The item and block families
 * clear their slots on eviction; these assertions pin the textured family to the same discipline: inside the
 * eviction loop, a removed set equal to the slot's set clears the slot before the set is retired.
 */
class TextureSlotEvictionTest {

    private static String texturePipelineSource() throws IOException {
        return Files.readString(findRepoFile(
                "celtium-vulkan/src/main/java/org/soutalab/celtium/vulkan/VulkanImmediateTexturePipeline.java"));
    }

    /** The eviction loop: from its head to the miss-allocation counter that follows it. */
    private static String evictionBody(String source) {
        int loop = source.indexOf("while (this.setsByTextureView.size() >= DESCRIPTOR_CACHE_LIMIT");
        assertTrue(loop >= 0, "the eviction loop is present");
        int end = source.indexOf("DESCRIPTOR_MISSES++", loop);
        assertTrue(end > loop, "the miss counter follows the eviction loop");
        return source.substring(loop, end);
    }

    @Test
    void evictingTheSlotsSetClearsTheSlotBeforeTheSetIsRetired() throws IOException {
        String body = evictionBody(texturePipelineSource());
        int removed = body.indexOf("long removed = this.setsByTextureView.remove(eldest);");
        int slotCheck = body.indexOf("if (removed == this.lastDescriptorSet)");
        int slotCleared = body.indexOf("this.lastDescriptorSet = 0L;", slotCheck);
        int retired = body.indexOf("this.retiredSets.add(pool, removed);");
        assertTrue(removed >= 0, "the loop removes the eldest entry");
        assertTrue(slotCheck > removed, "the removed set is compared against the slot's set");
        assertTrue(slotCleared > slotCheck, "the slot is cleared when its set is evicted");
        assertTrue(retired > slotCleared, "the slot is cleared before the set is handed to the retired store");
    }

    /** Test working directories differ between the module gate and a repo-root run; both shapes are tried. */
    private static Path findRepoFile(String repoRelative) {
        Path direct = Paths.get(repoRelative);
        if (Files.exists(direct)) {
            return direct;
        }
        Path fromModule = Paths.get("..").resolve(repoRelative);
        if (Files.exists(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("cannot locate " + repoRelative + " from " + Paths.get("").toAbsolutePath());
    }
}
