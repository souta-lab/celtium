package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The pipeline cache registry's device ownership (BH10-06): a pipeline cache is a child object of the device
 * that created it, so a lookup must only ever return the cache registered for the device being created on.
 * The single static slot this replaces paired the most recently created context's cache with whatever device
 * the caller passed, so two live contexts created each other's pipelines through the wrong device's cache.
 * <p>
 * Every test uses its own fake device addresses, so the static registry's state never leaks between tests.
 */
class PipelineCacheRegistryTest {

    private static final long NO_CACHE = 0L; // VK_NULL_HANDLE

    private static long device(int n) {
        return 0xDE71CE000000L + n;
    }

    private static long cache(int n) {
        return 0xCA5E00000000L + n;
    }

    @Test
    void lookupReturnsOnlyTheDevicesOwnCache() {
        long mine = device(1);
        long other = device(2);
        try {
            VulkanContext.registerPipelineCache(mine, cache(1));
            VulkanContext.registerPipelineCache(other, cache(2));

            assertEquals(cache(1), VulkanContext.pipelineCacheFor(mine));
            assertEquals(cache(2), VulkanContext.pipelineCacheFor(other),
                    "a device must never be handed another device's cache");
        } finally {
            VulkanContext.dropPipelineCache(mine);
            VulkanContext.dropPipelineCache(other);
        }
    }

    @Test
    void lookupForAnUnregisteredDeviceIsNoCache() {
        assertEquals(NO_CACHE, VulkanContext.pipelineCacheFor(device(3)));
    }

    @Test
    void unregisterRemovesOnlyTheEntryNamingThatCache() {
        long device = device(4);
        try {
            VulkanContext.registerPipelineCache(device, cache(4));

            VulkanContext.unregisterPipelineCache(device, cache(99));
            assertEquals(cache(4), VulkanContext.pipelineCacheFor(device),
                    "an unregister naming a different cache must not drop the live entry");

            VulkanContext.unregisterPipelineCache(device, cache(4));
            assertEquals(NO_CACHE, VulkanContext.pipelineCacheFor(device));
        } finally {
            VulkanContext.dropPipelineCache(device);
        }
    }

    @Test
    void dropRemovesTheEntryWhateverItNames() {
        long device = device(5);
        VulkanContext.registerPipelineCache(device, cache(5));

        VulkanContext.dropPipelineCache(device);

        assertEquals(NO_CACHE, VulkanContext.pipelineCacheFor(device),
                "a dead device's cache must not be served to a successor reusing the address");
    }

    @Test
    void reregistrationReplacesThePreviousCache() {
        long device = device(6);
        try {
            VulkanContext.registerPipelineCache(device, cache(6));
            VulkanContext.registerPipelineCache(device, cache(7));

            assertEquals(cache(7), VulkanContext.pipelineCacheFor(device));
        } finally {
            VulkanContext.dropPipelineCache(device);
        }
    }
}
