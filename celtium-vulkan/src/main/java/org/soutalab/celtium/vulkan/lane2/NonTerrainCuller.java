package org.soutalab.celtium.vulkan.lane2;

/**
 * NA-2 NEC: the non-terrain coarse culler (design V040-FINAL-PLAN section 4.2, stage S3-a
 * item 4). Vanilla entity, tile-entity, item and particle draws are culled coarsely before
 * capture, using two conservative signals: Lane 1's visible section set (a candidate whose
 * bounds overlap no visible section is out of the frame's terrain view) and the Hi-Z
 * occlusion verdict (a candidate the depth pyramid proves fully occluded is out). Both
 * signals only ever <em>remove</em> a candidate when they are certain:
 *
 * <ul>
 *   <li>a candidate whose bounds are unknown - the case for most mod renderers, whose
 *       extent celtium cannot know - is <b>always visible</b>;</li>
 *   <li>a candidate flagged always-visible by its owner is always visible;</li>
 *   <li>Hi-Z culls only on a positive occlusion proof for that candidate, never on the
 *       absence of a verdict.</li>
 * </ul>
 *
 * <p>The culler is pure: it decides over caller-supplied bounds and callbacks, so the
 * section set's own key format stays Lane 1's business - the caller answers visibility per
 * section coordinate (sections are 16 blocks, as everywhere in the game).
 *
 * <p>Threading: render thread only, like its consumers.
 */
public final class NonTerrainCuller {

    /** What a candidate is; the cull rules are the same for all four, the kind is census. */
    public enum Kind {
        ENTITY,
        TILE_ENTITY,
        ITEM,
        PARTICLE
    }

    /** Lane 1's visible section set, answered per section coordinate. */
    @FunctionalInterface
    public interface SectionVisibility {
        boolean isVisible(int sectionX, int sectionY, int sectionZ);
    }

    /** The Hi-Z verdict for one candidate: true only on a positive occlusion proof. */
    @FunctionalInterface
    public interface HiZVerdict {
        boolean provenOccluded(int candidateIndex);
    }

    /**
     * One cull candidate. Bounds are world-space; {@code boundsKnown == false} is the
     * unknown-extent case (the bounds fields are then ignored) and always draws.
     */
    public record Candidate(Kind kind, boolean boundsKnown, boolean alwaysVisible,
                            double minX, double minY, double minZ,
                            double maxX, double maxY, double maxZ) {

        /** A candidate with unknown bounds: always drawn. */
        public static Candidate unknown(Kind kind) {
            return new Candidate(kind, false, false, 0, 0, 0, 0, 0, 0);
        }

        /** A candidate with known world-space bounds. */
        public static Candidate bounded(Kind kind,
                                        double minX, double minY, double minZ,
                                        double maxX, double maxY, double maxZ) {
            return new Candidate(kind, true, false, minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    /** The outcome for a candidate set: one visibility flag per candidate, plus counts. */
    public record Result(boolean[] visible, int culledBySection, int culledByHiZ) {

        public int visibleCount() {
            int count = 0;
            for (boolean flag : this.visible) {
                if (flag) {
                    count++;
                }
            }
            return count;
        }
    }

    private NonTerrainCuller() {
    }

    /**
     * Decides every candidate. {@code hiZ} may be {@code null} (no pyramid this frame), in
     * which case only the section test applies.
     */
    public static Result cull(Candidate[] candidates, SectionVisibility sections, HiZVerdict hiZ) {
        boolean[] visible = new boolean[candidates.length];
        int culledBySection = 0;
        int culledByHiZ = 0;
        for (int i = 0; i < candidates.length; i++) {
            Candidate candidate = candidates[i];
            if (candidate.alwaysVisible() || !candidate.boundsKnown()) {
                visible[i] = true;
                continue;
            }
            if (!overlapsVisibleSection(candidate, sections)) {
                culledBySection++;
                continue;
            }
            if (hiZ != null && hiZ.provenOccluded(i)) {
                culledByHiZ++;
                continue;
            }
            visible[i] = true;
        }
        return new Result(visible, culledBySection, culledByHiZ);
    }

    /** Whether any 16-block section the bounds overlap is in the visible set. */
    static boolean overlapsVisibleSection(Candidate candidate, SectionVisibility sections) {
        int minSectionX = sectionOf(candidate.minX());
        int minSectionY = sectionOf(candidate.minY());
        int minSectionZ = sectionOf(candidate.minZ());
        int maxSectionX = sectionOf(candidate.maxX());
        int maxSectionY = sectionOf(candidate.maxY());
        int maxSectionZ = sectionOf(candidate.maxZ());
        for (int x = minSectionX; x <= maxSectionX; x++) {
            for (int y = minSectionY; y <= maxSectionY; y++) {
                for (int z = minSectionZ; z <= maxSectionZ; z++) {
                    if (sections.isVisible(x, y, z)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** The section coordinate a world coordinate falls in (sections are 16 blocks). */
    static int sectionOf(double coordinate) {
        return (int) Math.floor(coordinate / 16.0);
    }
}
