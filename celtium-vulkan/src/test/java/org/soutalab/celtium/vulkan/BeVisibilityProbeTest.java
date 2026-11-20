package org.soutalab.celtium.vulkan;

import grondag.bitraster.Matrix4L;
import grondag.bitraster.PackedBox;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.BeVisibilityProbe;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Item 386's instrument self-checks, on the feasibility contract's geometry (plan P101 M0):
 * a wall section's occluder box is drawn into the probe's own rasterizer, and boxes behind it,
 * in front of it and in an empty scene are judged. The queries follow the probe's discipline -
 * per section in ascending distance order, query before draw - because the rasterizer's
 * buffer is coverage only: drawn all at once, the wall would also claim the in-front box's
 * pixels (measured; the in-front case is the one that pins this). No game, no GPU - the
 * matrices are built by hand, the camera looks down -Z from block (8, 8, 56), and the wall
 * fills section (0, 0, 0).
 */
class BeVisibilityProbeTest {
    private static int viewVersion;

    private static void beginFrame() {
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(90.0), 1.0f, 0.1f, 400.0f);
        BeVisibilityProbe.losBeginFrameRaw(++viewVersion, 8.0, 8.0, 56.0, 128.0f,
                Matrix4L::loadIdentity,
                m -> m.set(projection.m00(), projection.m10(), projection.m20(), projection.m30(),
                        projection.m01(), projection.m11(), projection.m21(), projection.m31(),
                        projection.m02(), projection.m12(), projection.m22(), projection.m32(),
                        projection.m03(), projection.m13(), projection.m23(), projection.m33()));
    }

    /** Occluder data as the builder writes it: element 0 is the section's bounds box, boxes follow. */
    private static int[] wallData() {
        return new int[] { PackedBox.FULL_BOX, PackedBox.FULL_BOX };
    }

    @Test
    void wallHidesTheBoxBehindItAndNotTheBoxInFront() {
        beginFrame();
        // Distance order from the camera's chunk (0, 0, 3): the in-front box's section (0, 0, 1)
        // at d2=4, the wall's section (0, 0, 0) at d2=9, the behind box's section (0, 0, -1) at
        // d2=16. Per section: query first, then draw.
        // In front of the wall (z 24..28, between wall and camera): no coverage exists yet.
        BeVisibilityProbe.losQueryBlockEntity(6, 6, 24, 10, 10, 28);
        assertEquals(1, BeVisibilityProbe.losTested());
        assertEquals(0, BeVisibilityProbe.losHidden());

        BeVisibilityProbe.losDrawOccluderSection(0, 0, 0, wallData());
        assertEquals(1, BeVisibilityProbe.losOccluderSections());

        // Behind the wall (z -10..-6, through the wall's z 0..16 from the camera at z 56).
        BeVisibilityProbe.losQueryBlockEntity(6, 6, -10, 10, 10, -6);
        assertEquals(2, BeVisibilityProbe.losTested());
        assertEquals(1, BeVisibilityProbe.losHidden());
        BeVisibilityProbe.losEndFrame();
    }

    @Test
    void emptyOccluderBufferHidesNothing() {
        beginFrame();
        // The M0 case-8 contract: with nothing drawn, even the behind-the-wall position is visible.
        BeVisibilityProbe.losQueryBlockEntity(6, 6, -10, 10, 10, -6);
        assertEquals(1, BeVisibilityProbe.losTested());
        assertEquals(0, BeVisibilityProbe.losHidden());
        assertEquals(0, BeVisibilityProbe.losOccluderSections());
        BeVisibilityProbe.losEndFrame();
    }

    @Test
    void unjudgeableBoxesAreSkippedAndCounted() {
        beginFrame();
        BeVisibilityProbe.losDrawOccluderSection(0, 0, 0, wallData());

        // One axis over 32 blocks: PackedBox.pack would silently judge a different box.
        BeVisibilityProbe.losQueryBlockEntity(0, 0, -40, 40, 4, -36);
        // An INFINITY-class render box, the kind a TESR claiming the whole world returns.
        BeVisibilityProbe.losQueryBlockEntity(Double.NEGATIVE_INFINITY, 0, 0,
                Double.POSITIVE_INFINITY, 16, 16);
        assertEquals(2, BeVisibilityProbe.losSkippedSpan());
        assertEquals(0, BeVisibilityProbe.losTested());

        // Beyond the tested distance (128 blocks from the camera): no section is in range.
        BeVisibilityProbe.losQueryBlockEntity(6, 6, -300, 10, 10, -296);
        assertEquals(1, BeVisibilityProbe.losSkippedDist());
        assertEquals(0, BeVisibilityProbe.losTested());
        BeVisibilityProbe.losEndFrame();
    }

    @Test
    void coverageStaysSilentWhileItsSwitchIsOff() {
        // The test JVM carries no celtium-harness.properties, so beLosCoverage is off:
        // the probe judges exactly as before and every coverage reading stays at zero.
        beginFrame();
        BeVisibilityProbe.losDrawOccluderSection(0, 0, 0, wallData());
        BeVisibilityProbe.losQueryBlockEntity(6, 6, -10, 10, 10, -6);
        assertEquals(1, BeVisibilityProbe.losHidden());
        assertEquals(0, BeVisibilityProbe.losCoverageFull());
        assertEquals(0, BeVisibilityProbe.losCoverageNear());
        assertEquals(0, BeVisibilityProbe.losCoverageMid());
        assertEquals(0, BeVisibilityProbe.losCoverageLow());
        assertEquals(0, BeVisibilityProbe.losCoverageNone());
        assertEquals(0, BeVisibilityProbe.losCoverageUnmeasured());
        assertEquals("none", BeVisibilityProbe.losCoverageWorst());
        BeVisibilityProbe.losEndFrame();
    }

    @Test
    void censusSnapshotCountsBothPathsAndNamesTheFullestSections() {
        BeVisibilityProbe.censusBegin();
        BeVisibilityProbe.censusSection(false, 1, 2, 3, 4);
        BeVisibilityProbe.censusSection(false, 4, 5, 6, 1);
        BeVisibilityProbe.censusSection(true, 7, 8, 9, 2);
        assertEquals(3, BeVisibilityProbe.censusSections());
        assertEquals(2, BeVisibilityProbe.censusCulled());
        assertEquals(5, BeVisibilityProbe.censusDrawn());
        assertEquals(2, BeVisibilityProbe.censusGlobal());
        assertEquals("1,2,3=4,7,8,9=2,4,5,6=1", BeVisibilityProbe.censusTopSections());

        BeVisibilityProbe.censusBegin();
        assertEquals(0, BeVisibilityProbe.censusSections());
        assertEquals("none", BeVisibilityProbe.censusTopSections());
    }
}
