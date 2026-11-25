package org.soutalab.celtium.vulkan;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 537, the terrain shadow lookup: the pure rules the backend applies before a non-routed terrain pass samples
 * the frame's shadow capture, and the uniform-block offsets the shaders and {@code TerrainRenderer} must agree on.
 * A wrong offset reads a neighbour's bytes with no Vulkan error, so the numbers are pinned against the sources.
 */
class TerrainShadowLookupTest {

    @Test
    void theLookupNeedsTheRouteAndACaptureFromThisFrame() {
        assertEquals("switch-off",
                PackTerrainJudgments.celtium$terrainShadowRefusal(false, true, 7L, 7L, 5, true));
        assertEquals("route-off",
                PackTerrainJudgments.celtium$terrainShadowRefusal(true, false, 7L, 7L, 5, true));
        // A capture from the previous frame names a matrix and a depth the current camera did not draw.
        assertEquals("no-capture-this-frame",
                PackTerrainJudgments.celtium$terrainShadowRefusal(true, true, 6L, 7L, 5, true));
        assertEquals("no-capture-this-frame",
                PackTerrainJudgments.celtium$terrainShadowRefusal(true, true, Long.MIN_VALUE, 7L, 5, true));
        assertEquals("no-depth-texture",
                PackTerrainJudgments.celtium$terrainShadowRefusal(true, true, 7L, 7L, 0, true));
        assertEquals("depth-not-published",
                PackTerrainJudgments.celtium$terrainShadowRefusal(true, true, 7L, 7L, 5, false));
        assertNull(PackTerrainJudgments.celtium$terrainShadowRefusal(true, true, 7L, 7L, 5, true));
    }

    @Test
    void theDarknessPropertyIsClampedAndDefaulted() {
        assertEquals(0.45f, PackTerrainJudgments.celtium$shadowDarkness(null));
        assertEquals(0.45f, PackTerrainJudgments.celtium$shadowDarkness(" "));
        assertEquals(0.45f, PackTerrainJudgments.celtium$shadowDarkness("dark"));
        assertEquals(0.45f, PackTerrainJudgments.celtium$shadowDarkness("NaN"));
        assertEquals(0.0f, PackTerrainJudgments.celtium$shadowDarkness("-1"));
        assertEquals(1.0f, PackTerrainJudgments.celtium$shadowDarkness("3"));
        assertEquals(0.6f, PackTerrainJudgments.celtium$shadowDarkness("0.6"));
    }

    @Test
    void theShadersDeclareTheOffsetsTheRendererWrites() throws Exception {
        // TerrainRenderer.writePassUniforms writes u_ShadowEnabled at 84, u_ShadowDarkness at 88 and the matrix at 96,
        // inside the 256-byte block; every terrain module must declare the same members in that order.
        Path shaders = locateShaders();
        for (String name : new String[] {"terrain_solid.vert", "terrain_solid_vanilla.vert",
                "terrain_solid_extended.vert", "terrain_solid.frag"}) {
            String source = Files.readString(shaders.resolve(name), StandardCharsets.UTF_8);
            int solid = source.indexOf("int   u_SolidTerrain;");
            int enabled = source.indexOf("int   u_ShadowEnabled;          // offset 84");
            int darkness = source.indexOf("float u_ShadowDarkness;         // offset 88");
            int matrix = source.indexOf("mat4  u_ShadowMatrix;           // offset 96");
            assertTrue(solid > 0 && enabled > solid && darkness > enabled && matrix > darkness,
                    name + " declares the shadow members after u_SolidTerrain in block order");
        }
        assertTrue(96 + 64 <= VulkanTerrainPipeline.UNIFORM_BLOCK_SIZE, "the matrix fits the uniform block");
        String fragment = Files.readString(shaders.resolve("terrain_solid.frag"), StandardCharsets.UTF_8);
        assertTrue(fragment.contains("layout(set = 0, binding = " + VulkanTerrainPipeline.SHADOW_MAP_BINDING
                + ") uniform sampler2D u_ShadowMap;"), "the fragment samples the shadow map at SHADOW_MAP_BINDING");
    }

    private static Path locateShaders() {
        for (Path candidate : new Path[] {Path.of("src/main/shaders"), Path.of("celtium-vulkan/src/main/shaders")}) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("terrain shader sources not found from " + Path.of("").toAbsolutePath());
    }
}
