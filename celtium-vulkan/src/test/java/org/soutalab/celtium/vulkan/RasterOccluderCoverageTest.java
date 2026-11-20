package org.soutalab.celtium.vulkan;

import grondag.bitraster.Matrix4L;
import grondag.bitraster.PackedBox;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.RasterOccluder;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Item 386's coverage measurement, on the same hand-built geometry as
 * {@link BeVisibilityProbeTest}: the camera looks down -Z from block (8, 8, 56), and a
 * wall fills section (0, 0, 0). The queries are packed exactly as the LOS probe packs a
 * block entity's per-section clip, so the permille readings here are the ones the probe's
 * bands are built from. The probe's discipline - query a section's boxes before drawing
 * that section - is followed, because the buffer is coverage only: drawn first, the wall
 * would also claim the in-front box's pixels.
 */
class RasterOccluderCoverageTest {
    private static int viewVersion;

    private static RasterOccluder beginScene() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(90.0), 1.0f, 0.1f, 400.0f);
        RasterOccluder raster = new RasterOccluder();
        raster.prepareScene(++viewVersion, 8.0, 8.0, 56.0,
                Matrix4L::loadIdentity,
                m -> m.set(projection.m00(), projection.m10(), projection.m20(), projection.m30(),
                        projection.m01(), projection.m11(), projection.m21(), projection.m31(),
                        projection.m02(), projection.m12(), projection.m22(), projection.m32(),
                        projection.m03(), projection.m13(), projection.m23(), projection.m33()));
        return raster;
    }

    /** The behind-the-wall clip: box (6, 6, -10)-(10, 10, -6) clipped to section (0, 0, -1). */
    private static int behindClip() {
        return PackedBox.pack(6, 6, 6, 10, 10, 10, PackedBox.rangeFromSquareChunkDist(16));
    }

    private static void prepareBehindRegion(RasterOccluder raster) {
        raster.prepareRegion(0, 0, -16, PackedBox.rangeFromSquareChunkDist(16), 16);
    }

    @Test
    void fullWallCoversTheBoxBehindItCompletely() {
        RasterOccluder raster = beginScene();
        raster.occludeSectionAt(0, 0, 0, new int[] { PackedBox.FULL_BOX, PackedBox.FULL_BOX });

        prepareBehindRegion(raster);
        assertEquals(1000, raster.boxCoveragePermille(behindClip(), 1));
    }

    @Test
    void halfWallCoversTheBoxBehindItPartially() {
        RasterOccluder raster = beginScene();
        // An occluder box over the x 0..8 half of the wall section only.
        int half = PackedBox.pack(0, 0, 0, 8, 16, 16, PackedBox.RANGE_EXTREME);
        raster.occludeSectionAt(0, 0, 0, new int[] { PackedBox.FULL_BOX, half });

        prepareBehindRegion(raster);
        int permille = raster.boxCoveragePermille(behindClip(), 1);
        assertTrue(permille > 0 && permille < 1000,
                "half wall should cover part of the box, got " + permille);
        assertTrue(raster.isBoxVisible(behindClip(), 1));
    }

    @Test
    void emptyBufferCoversNothing() {
        RasterOccluder raster = beginScene();

        prepareBehindRegion(raster);
        assertEquals(0, raster.boxCoveragePermille(behindClip(), 1));
        assertTrue(raster.isBoxVisible(behindClip(), 1));
    }

    @Test
    void boxInFrontOfTheWallIsUncoveredWhenJudgedBeforeTheWallIsDrawn() {
        RasterOccluder raster = beginScene();
        // The in-front clip: box (6, 6, 24)-(10, 10, 28) clipped to section (0, 0, 1),
        // judged first, in the probe's distance order.
        int inFront = PackedBox.pack(6, 6, 8, 10, 10, 12, PackedBox.rangeFromSquareChunkDist(4));
        raster.prepareRegion(0, 0, 16, PackedBox.rangeFromSquareChunkDist(4), 4);
        assertEquals(0, raster.boxCoveragePermille(inFront, 1));

        raster.occludeSectionAt(0, 0, 0, new int[] { PackedBox.FULL_BOX, PackedBox.FULL_BOX });
        prepareBehindRegion(raster);
        assertEquals(1000, raster.boxCoveragePermille(behindClip(), 1));
    }
}
