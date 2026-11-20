package org.embeddedt.embeddium.impl.render.chunk.occlusion;

import grondag.bitraster.Matrix4L;
import grondag.bitraster.PackedBox;
import org.embeddedt.embeddium.impl.render.chunk.RenderSection;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.soutalab.celtium.harness.HarnessConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Item 386's two frame instruments: the block-entity census and the LOS dry-run probe.
 * Both are measurement only - neither changes what is drawn.
 *
 * <p>The census (harness key {@code beCensus=true}) counts what
 * {@code SimpleWorldRenderer.renderBlockEntities} actually drew on its latest call: how many
 * sections contributed, how many block entities came through the visibility-tested (culled)
 * path, how many through the untested global path, and the five fullest sections. That count
 * is the denominator the entity-cost work was missing - the per-block-entity price of the
 * {@code entBlockEntities} bracket is its microseconds divided by this census.
 *
 * <p>The LOS probe (harness key {@code beLos=off|dryRun}, default off) answers, for every
 * block entity the culled path is about to draw, whether a software rasterizer holding the
 * visible sections' occluder boxes would call its render box hidden. It keeps its own
 * {@link RasterOccluder} and follows the production search's own discipline, because the
 * rasterizer's buffer is coverage only, with no depth ({@code isProjectedPointClear} reads
 * the tile bit alone): sections are walked in ascending distance order, and each section's
 * block entities are queried <em>before</em> that section's {@code occluderBoxes} are drawn,
 * so a box is only ever judged against strictly nearer occluders and a section never hides
 * its own block entities. Drawing everything first and querying afterwards - the shape that
 * looks natural - measurably fails the feasibility contract's in-front-of-the-wall case: the
 * wall's coverage claims the nearer box's pixels too. In {@code dryRun} the answers are
 * counted and discarded; drawing is untouched. The counters separate the three ways a box
 * can fail to be judged: an axis span over 32 blocks or a non-finite (INFINITY-class) box is
 * skipped as {@code skippedSpan} because {@link PackedBox#pack} does not mask its 5-bit
 * fields and would silently judge a different box; a box no part of which lies inside the
 * probe's tested distance is {@code skippedDist}.
 *
 * <p>Coverage (harness key {@code beLosCoverage=true}, default off, meaningful only with
 * {@code beLos=dryRun}) extends the probe from "hidden or not" to "how much of each judged
 * box the drawn coverage actually claims" - the question a dry run that hides nothing
 * leaves behind: were the boxes barely missed, or never close? For every tested section
 * clip of a box it measures the share of the clip's projected silhouette the buffer already
 * covered, over the same pixels the visibility test judges, and keeps the minimum over the
 * box's clips: the least-covered clip is the one that decides hidden, so an average would
 * hide exactly the shortfall being looked for. The frame snapshot bands the judged boxes
 * by that minimum and names the five worst, each with the section whose clip fell
 * shortest. A box no tested clip of which has a measurable silhouette (the camera inside
 * it) counts as unmeasured rather than being guessed at. Enabling coverage also makes the
 * query walk every clip of a box instead of stopping at the first visible one; the
 * tested/hidden counts are unchanged, and the query-nanos reading then includes the
 * counting.
 *
 * <p>Both instruments keep a latest-frame snapshot in statics, which the validation fixture
 * reads reflectively when it writes its report - the same hand-off {@code FramePassTimings}
 * uses, so a leg without these classes on its classpath still loads. Harness reads sit in
 * this one class, behind {@code HarnessConfig} like every other instrument's.
 */
public final class BeVisibilityProbe {
    private BeVisibilityProbe() {
    }

    // ---------------------------------------------------------------- census

    /** The census switch, read once. Off costs one static-final check at the draw sites. */
    public static final boolean CENSUS_ENABLED = HarnessConfig.bool("beCensus", false);

    private static int censusSections;
    private static int censusCulledSections;
    private static int censusDrawn;
    private static int censusGlobal;
    private static final List<int[]> censusSectionCounts = new ArrayList<>();

    /** Starts a fresh census snapshot; called at the top of every {@code renderBlockEntities}. */
    public static void censusBegin() {
        censusSections = 0;
        censusCulledSections = 0;
        censusDrawn = 0;
        censusGlobal = 0;
        censusSectionCounts.clear();
    }

    /**
     * Records one section's contribution to the current snapshot.
     *
     * @param global whether the section came through the untested global path
     * @param count  how many block entities the section drew (its {@code renderSectionBlockEntities} return)
     */
    public static void censusSection(boolean global, int chunkX, int chunkY, int chunkZ, int count) {
        censusSections++;
        if (global) {
            censusGlobal += count;
        } else {
            censusCulledSections++;
            censusDrawn += count;
        }
        censusSectionCounts.add(new int[] { chunkX, chunkY, chunkZ, count });
    }

    /** Sections that drew at least one block entity on the latest census frame, both paths. */
    public static int censusSections() {
        return censusSections;
    }

    /** Block entities drawn through the visibility-tested path on the latest census frame. */
    public static int censusDrawn() {
        return censusDrawn;
    }

    /** Block entities drawn through the untested global path on the latest census frame. */
    public static int censusGlobal() {
        return censusGlobal;
    }

    /** Sections drawn through the visibility-tested path on the latest census frame. */
    public static int censusCulled() {
        return censusCulledSections;
    }

    /** The five fullest sections of the latest census frame as {@code x,y,z=n} pairs, or {@code "none"}. */
    public static String censusTopSections() {
        if (censusSectionCounts.isEmpty()) {
            return "none";
        }
        var sorted = new ArrayList<>(censusSectionCounts);
        sorted.sort((a, b) -> Integer.compare(b[3], a[3]));
        var out = new StringBuilder();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            int[] s = sorted.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append(s[0]).append(',').append(s[1]).append(',').append(s[2]).append('=').append(s[3]);
        }
        return out.toString();
    }

    // ---------------------------------------------------------------- LOS probe

    private static final int LOS_OFF = 0;
    private static final int LOS_DRY_RUN = 1;

    /** The probe mode, read once: {@code dryRun} runs it, anything else is off. */
    private static final int LOS_MODE = parseLosMode();

    private static int parseLosMode() {
        String raw = HarnessConfig.string("beLos", "off");
        int mode = "dryRun".equals(raw) ? LOS_DRY_RUN : LOS_OFF;
        if (!"off".equals(raw)) {
            // One line, so a leg log says which mode it actually ran - an unrecognised value is off,
            // and without this line that would read as a probe that judged nothing.
            System.out.println("[Celtium] CELTIUM_BE_LOS mode=" + raw + " parsed=" + (mode == LOS_DRY_RUN ? "dryRun" : "off"));
        }
        return mode;
    }

    public static boolean losEnabled() {
        return LOS_MODE != LOS_OFF;
    }

    private static RasterOccluder losRaster;
    private static int losViewVersion;
    private static boolean losFrameActive;
    private static float losTestDistance;
    private static int losCamBlockX;
    private static int losCamBlockY;
    private static int losCamBlockZ;

    private static int losTested;
    private static int losHidden;
    private static int losSkippedSpan;
    private static int losSkippedDist;
    private static int losOccluderSections;
    private static long losQueryNanos;
    private static long losOccluderNanos;

    private static int losReports;
    private static String losLastLine;

    // ---------------------------------------------------------------- LOS coverage

    /** The coverage switch, read once. Only consulted while the LOS probe judges a box. */
    public static final boolean COVERAGE_ENABLED = HarnessConfig.bool("beLosCoverage", false);

    private static int losCoverageFull;
    private static int losCoverageNear;
    private static int losCoverageMid;
    private static int losCoverageLow;
    private static int losCoverageNone;
    private static int losCoverageUnmeasured;
    private static final List<int[]> losCoverageWorstEntries = new ArrayList<>();

    private static void resetCoverage() {
        losCoverageFull = 0;
        losCoverageNear = 0;
        losCoverageMid = 0;
        losCoverageLow = 0;
        losCoverageNone = 0;
        losCoverageUnmeasured = 0;
        losCoverageWorstEntries.clear();
    }

    private static RasterOccluder losRaster() {
        if (losRaster == null) {
            losRaster = new RasterOccluder();
        }
        return losRaster;
    }

    /**
     * Prepares the probe's own rasterizer for this frame's camera and resets the per-frame
     * counters. A viewport without a view-projection matrix leaves the probe inactive for the
     * frame rather than judging boxes against a stale scene.
     */
    public static void losBeginFrame(Viewport viewport, float testDistance) {
        losTested = 0;
        losHidden = 0;
        losSkippedSpan = 0;
        losSkippedDist = 0;
        losOccluderSections = 0;
        losQueryNanos = 0;
        losOccluderNanos = 0;
        resetCoverage();
        losFrameActive = viewport != null && viewport.getVpMatrix() != null;
        if (!losFrameActive) {
            return;
        }
        var block = viewport.getBlockCoord();
        losCamBlockX = block.x();
        losCamBlockY = block.y();
        losCamBlockZ = block.z();
        losTestDistance = testDistance;
        losRaster().prepareScene(++losViewVersion, viewport, testDistance);
    }

    /**
     * Test-facing form of {@link #losBeginFrame} on the raw rasterizer entry, for geometry
     * tests that build their own matrices instead of a {@link Viewport}.
     */
    public static void losBeginFrameRaw(int viewVersion, double cameraX, double cameraY, double cameraZ,
                                        float testDistance,
                                        Consumer<Matrix4L> modelMatrixSetter,
                                        Consumer<Matrix4L> projectionMatrixSetter) {
        losTested = 0;
        losHidden = 0;
        losSkippedSpan = 0;
        losSkippedDist = 0;
        losOccluderSections = 0;
        losQueryNanos = 0;
        losOccluderNanos = 0;
        resetCoverage();
        losCamBlockX = (int) Math.floor(cameraX);
        losCamBlockY = (int) Math.floor(cameraY);
        losCamBlockZ = (int) Math.floor(cameraZ);
        losTestDistance = testDistance;
        // The return is "the buffer was cleared", not "a scene is prepared": an unchanged version
        // keeps the previous preparation, which is still a scene to judge against. Callers pass a
        // fresh version per frame, so the buffer is cleared on every frame this instrument runs.
        losRaster().prepareScene(viewVersion, cameraX, cameraY, cameraZ,
                modelMatrixSetter, projectionMatrixSetter);
        losFrameActive = true;
    }

    /**
     * Draws one section's occluder boxes into the probe's buffer. The caller walks sections in
     * ascending distance order and queries each section's block entities before drawing that
     * section (see the class doc): the buffer holds coverage with no depth, so anything drawn
     * claims its pixels against every later query regardless of distance. Sections whose built
     * context carries no boxes yet (the supply side is separate work) are passed over and do
     * not count.
     */
    public static void losDrawOccluderSection(int originX, int originY, int originZ, int @org.jetbrains.annotations.Nullable [] occluderBoxes) {
        if (!losFrameActive || occluderBoxes == null) {
            return;
        }
        long t0 = System.nanoTime();
        losRaster().occludeSectionAt(originX, originY, originZ, occluderBoxes);
        losOccluderNanos += System.nanoTime() - t0;
        losOccluderSections++;
    }

    /** {@link #losDrawOccluderSection} over a section's built context, for the renderer's driver. */
    public static void losDrawOccluderSection(RenderSection section) {
        var built = section.getBuiltContext();
        losDrawOccluderSection(section.getChunkX() << 4, section.getChunkY() << 4, section.getChunkZ() << 4,
                built != null ? built.occluderBoxes : null);
    }

    /**
     * Judges one block entity's render box against the coverage drawn so far. The result is
     * counted and discarded (dry-run); nothing about drawing changes.
     *
     * <p>The box is asked section by section over the sections it overlaps, clipped to each
     * section's own volume, and counts as hidden only when at least one overlapped section was
     * actually tested and every tested one reports it hidden - a section beyond the tested
     * distance is no evidence either way. Fuzz is one block, the feasibility contract's value:
     * a box whose projection is thinner than the raster's sampling must not read as hidden.
     */
    public static void losQueryBlockEntity(double minX, double minY, double minZ,
                                           double maxX, double maxY, double maxZ) {
        if (!losFrameActive) {
            return;
        }
        long t0 = System.nanoTime();
        try {
            if (!Double.isFinite(minX) || !Double.isFinite(minY) || !Double.isFinite(minZ)
                    || !Double.isFinite(maxX) || !Double.isFinite(maxY) || !Double.isFinite(maxZ)
                    || (maxX - minX) > 32.0 || (maxY - minY) > 32.0 || (maxZ - minZ) > 32.0) {
                // PackedBox.pack does not mask: a span over 32 blocks, or an INFINITY-class box from a
                // TESR that claims the whole world, would be judged as some other box entirely.
                losSkippedSpan++;
                return;
            }

            int bx0 = (int) Math.floor(minX);
            int by0 = (int) Math.floor(minY);
            int bz0 = (int) Math.floor(minZ);
            int bx1 = Math.max(bx0, (int) Math.ceil(maxX) - 1);
            int by1 = Math.max(by0, (int) Math.ceil(maxY) - 1);
            int bz1 = Math.max(bz0, (int) Math.ceil(maxZ) - 1);

            // With coverage on, every clip is walked even after one reports the box visible:
            // the box's coverage is its least-covered clip, which the short-circuit would miss.
            final boolean coverage = COVERAGE_ENABLED;
            boolean anyTested = false;
            boolean visible = false;
            int worstPermille = Integer.MAX_VALUE;
            int worstSectionX = 0;
            int worstSectionY = 0;
            int worstSectionZ = 0;
            for (int sx = bx0 >> 4; sx <= (bx1 >> 4) && (!visible || coverage); sx++) {
                for (int sy = by0 >> 4; sy <= (by1 >> 4) && (!visible || coverage); sy++) {
                    for (int sz = bz0 >> 4; sz <= (bz1 >> 4) && (!visible || coverage); sz++) {
                        int originX = sx << 4;
                        int originY = sy << 4;
                        int originZ = sz << 4;
                        double dx = (originX + 8) - losCamBlockX;
                        double dy = (originY + 8) - losCamBlockY;
                        double dz = (originZ + 8) - losCamBlockZ;
                        if (Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) > losTestDistance) {
                            continue;
                        }
                        int lx0 = Math.max(0, Math.min(16, bx0 - originX));
                        int ly0 = Math.max(0, Math.min(16, by0 - originY));
                        int lz0 = Math.max(0, Math.min(16, bz0 - originZ));
                        int lx1 = Math.max(0, Math.min(16, (bx1 + 1) - originX));
                        int ly1 = Math.max(0, Math.min(16, (by1 + 1) - originY));
                        int lz1 = Math.max(0, Math.min(16, (bz1 + 1) - originZ));
                        if (lx1 <= lx0 || ly1 <= ly0 || lz1 <= lz0) {
                            continue;
                        }
                        int chunkDx = sx - (losCamBlockX >> 4);
                        int chunkDy = sy - (losCamBlockY >> 4);
                        int chunkDz = sz - (losCamBlockZ >> 4);
                        int squaredChunkDist = (chunkDx * chunkDx) + (chunkDy * chunkDy) + (chunkDz * chunkDz);
                        var raster = losRaster();
                        raster.prepareRegion(originX, originY, originZ,
                                PackedBox.rangeFromSquareChunkDist(squaredChunkDist), squaredChunkDist);
                        int packed = PackedBox.pack(lx0, ly0, lz0, lx1, ly1, lz1,
                                PackedBox.rangeFromSquareChunkDist(squaredChunkDist));
                        anyTested = true;
                        if (raster.isBoxVisible(packed, 1)) {
                            visible = true;
                        }
                        if (coverage) {
                            int permille = raster.boxCoveragePermille(packed, 1);
                            if (permille >= 0 && permille < worstPermille) {
                                worstPermille = permille;
                                worstSectionX = originX;
                                worstSectionY = originY;
                                worstSectionZ = originZ;
                            }
                        }
                    }
                }
            }

            if (!anyTested) {
                losSkippedDist++;
                return;
            }
            losTested++;
            if (!visible) {
                losHidden++;
            }
            if (coverage) {
                if (worstPermille == Integer.MAX_VALUE) {
                    losCoverageUnmeasured++;
                } else if (worstPermille >= 1000) {
                    losCoverageFull++;
                } else {
                    if (worstPermille >= 900) {
                        losCoverageNear++;
                    } else if (worstPermille >= 500) {
                        losCoverageMid++;
                    } else if (worstPermille >= 1) {
                        losCoverageLow++;
                    } else {
                        losCoverageNone++;
                    }
                    losCoverageWorstEntries.add(new int[] { bx0, by0, bz0, worstPermille,
                            worstSectionX, worstSectionY, worstSectionZ });
                }
            }
        } finally {
            losQueryNanos += System.nanoTime() - t0;
        }
    }

    /**
     * Closes the frame's probe and prints the bounded summary line - only when the readings
     * changed, at most twelve times, the shape {@code OcclusionCuller}'s visibility line uses.
     */
    public static void losEndFrame() {
        if (!losFrameActive) {
            return;
        }
        losFrameActive = false;
        String line = "[Celtium] CELTIUM_BE_LOS tested=" + losTested + " hidden=" + losHidden
                + " skippedSpan=" + losSkippedSpan + " skippedDist=" + losSkippedDist
                + " occluderSections=" + losOccluderSections
                + " queryUs=" + (losQueryNanos / 1000) + " occluderUs=" + (losOccluderNanos / 1000);
        if (!line.equals(losLastLine) && losReports < 12) {
            losReports++;
            losLastLine = line;
            System.out.println(line);
        }
    }

    /** Block entities judged on the latest probe frame. */
    public static int losTested() {
        return losTested;
    }

    /** Judged block entities whose boxes the coverage fully hid on the latest probe frame. */
    public static int losHidden() {
        return losHidden;
    }

    /** Boxes skipped for span or non-finite extent on the latest probe frame. */
    public static int losSkippedSpan() {
        return losSkippedSpan;
    }

    /** Boxes no part of which lay inside the tested distance on the latest probe frame. */
    public static int losSkippedDist() {
        return losSkippedDist;
    }

    /** Sections whose occluder boxes were drawn into the probe's buffer on the latest frame. */
    public static int losOccluderSections() {
        return losOccluderSections;
    }

    /** Nanoseconds spent judging boxes on the latest probe frame. */
    public static long losQueryNanos() {
        return losQueryNanos;
    }

    /** Nanoseconds spent drawing occluder boxes on the latest probe frame. */
    public static long losOccluderNanos() {
        return losOccluderNanos;
    }

    /** Judged block entities whose least-covered clip was fully covered on the latest frame. */
    public static int losCoverageFull() {
        return losCoverageFull;
    }

    /** Judged block entities whose least-covered clip was 900-999 permille covered. */
    public static int losCoverageNear() {
        return losCoverageNear;
    }

    /** Judged block entities whose least-covered clip was 500-899 permille covered. */
    public static int losCoverageMid() {
        return losCoverageMid;
    }

    /** Judged block entities whose least-covered clip was 1-499 permille covered. */
    public static int losCoverageLow() {
        return losCoverageLow;
    }

    /** Judged block entities whose least-covered clip had no coverage at all. */
    public static int losCoverageNone() {
        return losCoverageNone;
    }

    /** Judged block entities no tested clip of which had a measurable silhouette. */
    public static int losCoverageUnmeasured() {
        return losCoverageUnmeasured;
    }

    /**
     * The five least-covered judged block entities of the latest probe frame as
     * {@code bx,by,bz=permille@sx,sy,sz} pairs - the box's minimum corner, its least-covered
     * clip's coverage, and that clip's section origin - or {@code "none"}.
     */
    public static String losCoverageWorst() {
        if (losCoverageWorstEntries.isEmpty()) {
            return "none";
        }
        var sorted = new ArrayList<>(losCoverageWorstEntries);
        sorted.sort((a, b) -> Integer.compare(a[3], b[3]));
        var out = new StringBuilder();
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            int[] e = sorted.get(i);
            if (i > 0) {
                out.append(',');
            }
            out.append(e[0]).append(',').append(e[1]).append(',').append(e[2])
                    .append('=').append(e[3])
                    .append('@').append(e[4]).append(',').append(e[5]).append(',').append(e[6]);
        }
        return out.toString();
    }
}
