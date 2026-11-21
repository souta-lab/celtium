package org.soutalab.celtium.vulkan;

/**
 * The measurement half of item 312 (the HUD cache survey's rank-1 remainder): a per-frame census of the
 * groups the HUD section closes, classified against the cache design's exclusion rules, plus the run
 * partition the layer would have to draw and a flush-cost regression. This class draws nothing and
 * changes nothing; it only counts, so the G1-G4 gates fixed in advance by the 2026-11-18 judgment can be
 * read off a real leg instead of argued from the design's estimates.
 *
 * <p>The classification reads the captured state array in {@code celtium$captureState}'s own layout
 * ({@code {blend, srcColorVk, dstColorVk, depthTest, depthWrite, cull, alphaRefBits, depthFunc, lighting,
 * srcAlphaVk, dstAlphaVk, colorWriteMask, colorOpVk, alphaOpVk, alphaFuncGl, envCombine}}):
 *
 * <ul>
 *   <li><b>E1 blend class.</b> Over-class is blend on with the RGB pair
 *       {@code SRC_ALPHA / ONE_MINUS_SRC_ALPHA}, both equations ADD, and the alpha pair either
 *       {@code ONE / ZERO} or {@code ONE / ONE_MINUS_SRC_ALPHA} (the judgment's widening of the design's
 *       original alpha term). Blend off is replace-class. Anything else is live.</li>
 *   <li><b>E2 depth.</b> Depth test or depth write on is live: a cached layer cannot reproduce a test
 *       against a depth buffer it does not own.</li>
 *   <li><b>E3 write mask.</b> A mask whose RGB bits are not all set is live. The window target's own
 *       alpha-write drop makes the frame image's full mask read {@code 0x7} rather than {@code 0xF}, so
 *       fullness is judged on the RGB bits, not on equality with {@code 0xF}.</li>
 *   <li><b>E4 sampled image.</b> A group sampling a published framebuffer colour texture is counted
 *       separately and treated as live: the per-target write counter the design's F2 fix requires does
 *       not exist yet, so celtium cannot yet prove such an image did not change under the group.</li>
 *   <li><b>E5 target.</b> A group whose target is not the game's main framebuffer identity is live.</li>
 *   <li><b>F1 replace handling.</b> A replace-class draw composites by overwriting, so a run of them
 *       would have to reproduce gaps and partial-alpha edges exactly. The primary partition therefore
 *       gives every replace-class group a run of its own (the judgment's per-group-scissor reading); the
 *       strict partition treats replace-class as live, and both numbers are reported so the verdict does
 *       not depend on which reading is kinder.</li>
 * </ul>
 */
final class HudP0Census {

    /** Category of one HUD group in the run partition. */
    static final int LIVE = 0;
    static final int OVER = 1;
    static final int REPLACE = 2;

    // Vulkan blend constants, named here because the captured state stores Vulkan codes, not GL ones.
    private static final int VK_BLEND_FACTOR_ZERO = 0;
    private static final int VK_BLEND_FACTOR_ONE = 1;
    private static final int VK_BLEND_FACTOR_SRC_ALPHA = 6;
    private static final int VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA = 7;
    private static final int VK_BLEND_OP_ADD = 0;

    private HudP0Census() {
    }

    /** {@return whether the state is the over blend class of E1}. */
    static boolean isOver(int[] state) {
        if (state == null || state.length < 15 || state[0] == 0) {
            return false;
        }
        if (state[1] != VK_BLEND_FACTOR_SRC_ALPHA || state[2] != VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA) {
            return false;
        }
        if (state[12] != VK_BLEND_OP_ADD || state[13] != VK_BLEND_OP_ADD) {
            return false;
        }
        boolean alphaOneZero = state[9] == VK_BLEND_FACTOR_ONE && state[10] == VK_BLEND_FACTOR_ZERO;
        boolean alphaLegacy = state[9] == VK_BLEND_FACTOR_ONE
                && state[10] == VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA;
        return alphaOneZero || alphaLegacy;
    }

    /** {@return the E1 blend class name for the census line: {@code off}, {@code over} or {@code other}}. */
    static String blendClass(int[] state) {
        if (state == null || state.length < 15 || state[0] == 0) {
            return "off";
        }
        return isOver(state) ? "over" : "other";
    }

    /**
     * Classifies one closed HUD group. The exclusion counters on {@link Frame} are incremented by the
     * caller from the same facts, so a group failing two rules is visible in both counts while its
     * category follows the precedence here (depth, mask, target, sampled image, then blend class).
     */
    static int classify(int[] state, boolean isMain, boolean samplesPublishedTarget) {
        if (state == null || state.length < 15) {
            return LIVE;
        }
        if (state[3] != 0 || state[4] != 0) {
            return LIVE;
        }
        if ((state[11] & 0x7) != 0x7) {
            return LIVE;
        }
        if (!isMain || samplesPublishedTarget) {
            return LIVE;
        }
        if (state[0] == 0) {
            return REPLACE;
        }
        return isOver(state) ? OVER : LIVE;
    }

    /** One frame's HUD groups, in close order, with the partition the layer would face. */
    static final class Frame {
        private static final int MAX_SEQUENCE = 8192;

        int groups;
        int batches;
        long vertices;
        long vertexBytes;
        int depthExcluded;
        int maskPartial;
        int offTarget;
        int samplesTarget;
        int blendOff;
        int blendOver;
        int blendOther;
        boolean sequenceOverflow;
        private byte[] sequence = new byte[256];
        private int sequenceLength;

        void note(int[] state, boolean isMain, boolean samplesPublishedTarget,
                  int groupBatches, int groupVertices, long groupVertexBytes) {
            this.groups++;
            this.batches += groupBatches;
            this.vertices += groupVertices;
            this.vertexBytes += groupVertexBytes;
            if (state != null && state.length >= 15) {
                if (state[3] != 0 || state[4] != 0) {
                    this.depthExcluded++;
                }
                if ((state[11] & 0x7) != 0x7) {
                    this.maskPartial++;
                }
                String blend = blendClass(state);
                if ("off".equals(blend)) {
                    this.blendOff++;
                } else if ("over".equals(blend)) {
                    this.blendOver++;
                } else {
                    this.blendOther++;
                }
            }
            if (!isMain) {
                this.offTarget++;
            }
            if (samplesPublishedTarget) {
                this.samplesTarget++;
            }
            int category = classify(state, isMain, samplesPublishedTarget);
            if (this.sequenceLength == MAX_SEQUENCE) {
                this.sequenceOverflow = true;
                return;
            }
            if (this.sequenceLength == this.sequence.length) {
                byte[] grown = new byte[this.sequence.length * 2];
                System.arraycopy(this.sequence, 0, grown, 0, this.sequenceLength);
                this.sequence = grown;
            }
            this.sequence[this.sequenceLength++] = (byte) category;
        }

        int cacheable() {
            int count = 0;
            for (int i = 0; i < this.sequenceLength; i++) {
                if (this.sequence[i] != LIVE) {
                    count++;
                }
            }
            return count;
        }

        int cacheableStrict() {
            int count = 0;
            for (int i = 0; i < this.sequenceLength; i++) {
                if (this.sequence[i] == OVER) {
                    count++;
                }
            }
            return count;
        }

        /** Runs under the F1 primary reading: over runs merge, each replace group is its own run. */
        int runs() {
            return runs(false);
        }

        /** Runs under the F1 strict reading: replace-class is live, so only over runs remain. */
        int runsStrict() {
            return runs(true);
        }

        private int runs(boolean strict) {
            int runs = 0;
            boolean inOverRun = false;
            for (int i = 0; i < this.sequenceLength; i++) {
                int category = this.sequence[i];
                if (category == OVER) {
                    if (!inOverRun) {
                        runs++;
                        inOverRun = true;
                    }
                } else if (category == REPLACE && !strict) {
                    runs++;
                    inOverRun = false;
                } else {
                    inOverRun = false;
                }
            }
            return runs;
        }
    }

    /**
     * Least-squares fit of flush wall time against the number of groups the flush recorded, over the
     * bounded window sample. The slope is the flush's group-proportional cost (G1's input); the
     * intercept is its fixed cost. Windows are the unit, not frames, because one frame flushes several
     * times and each flush's group count is known exactly at its entry.
     */
    static final class FlushRegression {
        private static final int MAX_WINDOWS = 8192;

        private final long[] nanos = new long[MAX_WINDOWS];
        private final int[] groups = new int[MAX_WINDOWS];
        private int size;
        private long totalNanos;
        private long totalGroups;

        void note(int windowGroups, long windowNanos) {
            if (this.size == MAX_WINDOWS) {
                return;
            }
            this.groups[this.size] = windowGroups;
            this.nanos[this.size] = windowNanos;
            this.size++;
            this.totalGroups += windowGroups;
            this.totalNanos += windowNanos;
        }

        int windows() {
            return this.size;
        }

        long totalGroups() {
            return this.totalGroups;
        }

        /** {@return the fitted nanoseconds per group, or NaN when the sample cannot support a fit}. */
        double slopeNanosPerGroup() {
            if (this.size < 2) {
                return Double.NaN;
            }
            double meanGroups = (double) this.totalGroups / this.size;
            double meanNanos = (double) this.totalNanos / this.size;
            double covariance = 0.0;
            double variance = 0.0;
            for (int i = 0; i < this.size; i++) {
                double dg = this.groups[i] - meanGroups;
                covariance += dg * (this.nanos[i] - meanNanos);
                variance += dg * dg;
            }
            return variance == 0.0 ? Double.NaN : covariance / variance;
        }

        /** {@return the fitted fixed nanoseconds per window, or NaN alongside the slope}. */
        double interceptNanos() {
            double slope = slopeNanosPerGroup();
            if (Double.isNaN(slope)) {
                return Double.NaN;
            }
            double meanGroups = (double) this.totalGroups / this.size;
            double meanNanos = (double) this.totalNanos / this.size;
            return meanNanos - slope * meanGroups;
        }
    }
}
