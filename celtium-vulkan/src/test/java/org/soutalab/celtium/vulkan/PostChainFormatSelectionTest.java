package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PostChainFormatSelectionTest {
    @Test
    void routesOnlyOversizeBlocksThroughTheirBundledUniformBufferVariants() {
        assertTrue(VanillaPostExecutor.usesUniformBuffer("color_convolve", 192, 128));
        assertTrue(VanillaPostExecutor.usesUniformBuffer("spider", 144, 128));
        assertFalse(VanillaPostExecutor.usesUniformBuffer("color_convolve", 192, 192));
        assertFalse(VanillaPostExecutor.usesUniformBuffer("spider", 144, 256));
        assertFalse(VanillaPostExecutor.usesUniformBuffer("blit", 84, 128));
        assertThrows(RuntimeException.class, () -> VanillaPostExecutor.usesUniformBuffer("blit", 132, 128));
    }
}
