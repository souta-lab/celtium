package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import grondag.bitraster.PackedBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 386's M1.5 supply contract: what the meshing task's builder feeding must produce for
 * {@code BuiltRenderSectionData.occluderBoxes}. The format is the generator's own - index 0 is
 * the renderable bounds, boxes follow in descending range order - and the empty section stays
 * null, which is what keeps {@code HAS_OCCLUDER_DATA} off for air.
 */
class OccluderSupplyTest {
    @Test
    void emptyBuilderProducesNull() {
        assertNull(new SectionVisibilityBuilder().computeOccluderBoxes());
    }

    @Test
    void opaqueWallProducesBoundsAndRangeOrderedBoxes() {
        SectionVisibilityBuilder builder = new SectionVisibilityBuilder();
        for (int x = 0; x < 16; x++) {
            for (int y = 0; y < 16; y++) {
                builder.markRenderable(x, y, 7);
                builder.markOpaque(x, y, 7);
            }
        }

        int[] data = builder.computeOccluderBoxes();
        assertNotNull(data);
        assertTrue(data.length > 1, "a full opaque plane must yield at least one box");

        int bounds = SectionVisibilityBuilder.bounds(data);
        assertNotNull(bounds);
        // Bounds are the renderable extent expanded by one block: the z=7 plane widens to 6..9,
        // while the full-width x/y marks clamp at the section faces.
        assertEquals(6, PackedBox.z0(bounds));
        assertEquals(9, PackedBox.z1(bounds));
        assertEquals(0, PackedBox.x0(bounds));
        assertEquals(16, PackedBox.x1(bounds));

        int previousRange = Integer.MAX_VALUE;
        for (int i = 1; i < data.length; i++) {
            int range = PackedBox.range(data[i]);
            assertTrue(range <= previousRange, "boxes must be ordered by descending range");
            previousRange = range;
        }
    }

    @Test
    void renderableOnlyProducesBoundsWithoutBoxes() {
        SectionVisibilityBuilder builder = new SectionVisibilityBuilder();
        builder.markRenderable(3, 4, 5);
        builder.markRenderable(9, 4, 5);

        int[] data = builder.computeOccluderBoxes();
        assertNotNull(data);
        assertEquals(1, data.length, "no opaque cubes means no occluder boxes, bounds only");
    }

    @Test
    void opaqueWithoutRenderableFallsBackToFullBoxBounds() {
        SectionVisibilityBuilder builder = new SectionVisibilityBuilder();
        for (int x = 4; x < 8; x++) {
            for (int y = 4; y < 8; y++) {
                for (int z = 4; z < 8; z++) {
                    builder.markOpaque(x, y, z);
                }
            }
        }

        int[] data = builder.computeOccluderBoxes();
        assertNotNull(data);
        assertTrue(data.length > 1, "a solid 4x4x4 cube must yield an occluder box");
        // With no renderable marks the bounds half is the generator's full-box fallback.
        assertEquals(PackedBox.FULL_BOX, SectionVisibilityBuilder.bounds(data));
    }

    @Test
    void supplyStatsCountAndPercentiles() {
        OccluderSupplyStats.resetForTest();
        assertEquals(0, OccluderSupplyStats.occluderBoxSections());
        assertEquals(0, OccluderSupplyStats.occluderBoxNanosP50());
        assertEquals(0, OccluderSupplyStats.occluderBoxNanosP99());

        OccluderSupplyStats.record(100, true);
        OccluderSupplyStats.record(200, true);
        OccluderSupplyStats.record(300, false);
        OccluderSupplyStats.record(400, true);

        assertEquals(4, OccluderSupplyStats.occluderBoxSections());
        assertEquals(1, OccluderSupplyStats.occluderBoxNull());
        assertEquals(200, OccluderSupplyStats.occluderBoxNanosP50());
        assertEquals(400, OccluderSupplyStats.occluderBoxNanosP99());
        OccluderSupplyStats.resetForTest();
    }
}
