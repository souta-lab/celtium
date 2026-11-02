package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R32G32B32_SFLOAT;
import static org.lwjgl.vulkan.VK10.VK_FORMAT_R8G8B8A8_UNORM;

/**
 * The terrain pipeline declares where each vertex attribute lives, and the colour offset is load-bearing: {@code a_Color}
 * at byte 0 makes the shader read the first position float's low byte as the colour, which draws the chunk terrain black
 * while every other path (TESRs, sky, HUD) stays lit.
 * <p>
 * This shipped on 2026-11-01 in "twelve startup properties are resolved once": the colour offset became a static final
 * declared <em>after</em> the offsets array, so the array captured the field's default 0. It could not be seen while
 * every clean build died at the first GUI texture draw (the fastutil linkage bug, fixed in round 9); with that fixed,
 * the leg's {@code CELTIUM_TERRAIN_LAYOUT colorOffset=0} against rc26's {@code 12} was the black floor covering 20.02%
 * of the presented frame. The offset is now built when a layout is asked for, and this checks the answer rather than
 * the source.
 */
class TerrainVertexLayoutTest {
    @Test
    void theVanillaLayoutReadsColourAfterThePosition() {
        VulkanTerrainPipeline.VertexLayout layout =
                VulkanTerrainPipeline.VertexLayout.forVertexStride(32, "solid");
        assertEquals(32, layout.stride, "1.12.2's vanilla-like BLOCK layout is a 32-byte vertex");
        assertEquals(SpirvShaders.SOLID_VERTEX_VANILLA, layout.vertexModule,
                "the float-positioned layout needs the vanilla vertex module");
        assertArrayEquals(new int[] { 0, 12, 16, 24, 28 }, layout.offsets,
                "a_Color is the byte after the 3-float position; offset 0 reads the position and the terrain draws black");
        assertEquals(VK_FORMAT_R32G32B32_SFLOAT, layout.formats[0]);
        assertEquals(VK_FORMAT_R8G8B8A8_UNORM, layout.formats[1]);
    }

    @Test
    void theCompactLayoutKeepsItsOwnOffsets() {
        VulkanTerrainPipeline.VertexLayout layout =
                VulkanTerrainPipeline.VertexLayout.forVertexStride(24, "solid");
        assertEquals(24, layout.stride);
        assertEquals(SpirvShaders.SOLID_VERTEX, layout.vertexModule);
        assertArrayEquals(new int[] { 0, 8, 12, 16, 20 }, layout.offsets,
                "the packed layout is not affected by the vanilla colour offset");
    }

    @Test
    void anUnimplementedStrideIsRefusedByName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> VulkanTerrainPipeline.VertexLayout.forVertexStride(28, "translucent"),
                "a vertex format this backend does not implement must be refused, not drawn wrong");
        assertTrue(refused.getMessage().contains("'translucent'"), refused.getMessage());
        assertTrue(refused.getMessage().contains("28 byte"), refused.getMessage());
        assertTrue(refused.getMessage().contains("OpenGL stays active"), refused.getMessage());
    }
}
